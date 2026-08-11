/*
playlist/generator_test.go

Tests for Generate. Table-driven where the differences between cases are
purely in the Channel/Config inputs and the expected output string;
separate focused tests where a single channel's shape needs multiple
assertions (e.g. checking both what's present and what's absent).

Covers: ActiveLink-over-Candidates priority, the two independent triggers
for proxy-URL rewriting (cfg.ProxyEnabled and local-script detection),
silent skip of channels with no resolvable URL, conditional tvg-logo
attribute, and %q quoting of tvg-id/group-title/name.
*/
package playlist

import (
	"strings"
	"testing"

	"github.com/fredrick-karuri/nativestream/server/store"
)

const testServerAddr = "http://127.0.0.1:8888"

func TestGenerate_EmitsHeader(t *testing.T) {
	got := Generate(nil, Config{})
	if !strings.HasPrefix(got, "#EXTM3U\n") {
		t.Errorf("output missing #EXTM3U header, got: %q", got)
	}
}

func TestGenerate_ActiveLinkTakesPriorityOverCandidates(t *testing.T) {
	ch := &store.Channel{
		ID:         "ch-1",
		Name:       "Test Channel",
		GroupTitle: "News",
		TvgID:      "test.channel",
		ActiveLink: &store.LinkScore{URL: "http://active.example/stream.m3u8"},
		Candidates: []*store.LinkScore{
			{URL: "http://candidate.example/stream.m3u8"},
		},
	}

	got := Generate([]*store.Channel{ch}, Config{})

	if !strings.Contains(got, "http://active.example/stream.m3u8") {
		t.Errorf("output missing ActiveLink URL, got: %q", got)
	}
	if strings.Contains(got, "http://candidate.example/stream.m3u8") {
		t.Errorf("output should not contain Candidates URL when ActiveLink is set, got: %q", got)
	}
}

func TestGenerate_FallsBackToFirstCandidateWhenNoActiveLink(t *testing.T) {
	ch := &store.Channel{
		ID:         "ch-1",
		Name:       "Test Channel",
		ActiveLink: nil,
		Candidates: []*store.LinkScore{
			{URL: "http://first.example/stream.m3u8"},
			{URL: "http://second.example/stream.m3u8"},
		},
	}

	got := Generate([]*store.Channel{ch}, Config{})

	if !strings.Contains(got, "http://first.example/stream.m3u8") {
		t.Errorf("output missing first candidate URL, got: %q", got)
	}
	if strings.Contains(got, "http://second.example/stream.m3u8") {
		t.Errorf("output should only use first candidate, got: %q", got)
	}
}

func TestGenerate_SkipsChannelWithNoResolvableURL(t *testing.T) {
	channels := []*store.Channel{
		{ID: "ch-empty", Name: "No Source", ActiveLink: nil, Candidates: nil},
		{ID: "ch-ok", Name: "Has Source", ActiveLink: &store.LinkScore{URL: "http://ok.example/stream.m3u8"}},
	}

	got := Generate(channels, Config{})

	if strings.Contains(got, "No Source") {
		t.Errorf("channel with no resolvable URL should be skipped entirely, got: %q", got)
	}
	if !strings.Contains(got, "Has Source") {
		t.Errorf("channel with a resolvable URL is missing from output, got: %q", got)
	}
}

// TestGenerate_ProxyURLRewriting covers the two independent conditions
// that force streamURL to become a /stream/{id}/proxy URL: cfg.ProxyEnabled
// being true, and the channel's ActiveLink looking like a local script
// (non-empty SourceURL that doesn't start with "http"). Either one alone
// must trigger rewriting.
func TestGenerate_ProxyURLRewriting(t *testing.T) {
	tests := []struct {
		name         string
		proxyEnabled bool
		activeLink   *store.LinkScore
		wantProxyURL bool
	}{
		{
			name:         "proxy disabled, http source, no rewrite",
			proxyEnabled: false,
			activeLink:   &store.LinkScore{URL: "http://origin.example/s.m3u8", SourceURL: "http://origin.example/playlist"},
			wantProxyURL: false,
		},
		{
			name:         "proxy enabled forces rewrite regardless of source shape",
			proxyEnabled: true,
			activeLink:   &store.LinkScore{URL: "http://origin.example/s.m3u8", SourceURL: "http://origin.example/playlist"},
			wantProxyURL: true,
		},
		{
			name:         "local script source forces rewrite even with proxy disabled",
			proxyEnabled: false,
			activeLink:   &store.LinkScore{URL: "http://origin.example/s.m3u8", SourceURL: "/opt/scripts/fetch.sh"},
			wantProxyURL: true,
		},
		{
			name:         "empty source URL is not treated as local script",
			proxyEnabled: false,
			activeLink:   &store.LinkScore{URL: "http://origin.example/s.m3u8", SourceURL: ""},
			wantProxyURL: false,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			ch := &store.Channel{ID: "ch-1", Name: "Test", ActiveLink: tt.activeLink}
			cfg := Config{ProxyEnabled: tt.proxyEnabled, ServerAddr: testServerAddr}

			got := Generate([]*store.Channel{ch}, cfg)

			wantURL := testServerAddr + "/stream/ch-1/proxy"
			if tt.wantProxyURL {
				if !strings.Contains(got, wantURL) {
					t.Errorf("expected proxy URL %q in output, got: %q", wantURL, got)
				}
			} else {
				if strings.Contains(got, wantURL) {
					t.Errorf("did not expect proxy URL rewriting, got: %q", got)
				}
				if !strings.Contains(got, tt.activeLink.URL) {
					t.Errorf("expected original URL %q preserved, got: %q", tt.activeLink.URL, got)
				}
			}
		})
	}
}

func TestGenerate_LogoAttributePresentOnlyWhenSet(t *testing.T) {
	channels := []*store.Channel{
		{ID: "ch-with-logo", Name: "Has Logo", LogoURL: "http://logo.example/a.png", ActiveLink: &store.LinkScore{URL: "http://a.example/s.m3u8"}},
		{ID: "ch-no-logo", Name: "No Logo", LogoURL: "", ActiveLink: &store.LinkScore{URL: "http://b.example/s.m3u8"}},
	}

	got := Generate(channels, Config{})

	lines := strings.Split(got, "\n")
	var withLogoLine, noLogoLine string
	for _, line := range lines {
		if strings.Contains(line, "Has Logo") {
			withLogoLine = line
		}
		if strings.Contains(line, "No Logo") {
			noLogoLine = line
		}
	}

	if !strings.Contains(withLogoLine, `tvg-logo="http://logo.example/a.png"`) {
		t.Errorf("expected tvg-logo attribute on channel with LogoURL set, got line: %q", withLogoLine)
	}
	if strings.Contains(noLogoLine, "tvg-logo") {
		t.Errorf("did not expect tvg-logo attribute on channel with empty LogoURL, got line: %q", noLogoLine)
	}
}

func TestGenerate_QuotesFieldsContainingSpecialCharacters(t *testing.T) {
	ch := &store.Channel{
		ID:         "ch-1",
		Name:       `Channel "Prime"`,
		GroupTitle: "News & Sports",
		TvgID:      `id.with"quote`,
		ActiveLink: &store.LinkScore{URL: "http://a.example/s.m3u8"},
	}

	got := Generate([]*store.Channel{ch}, Config{})

	// %q escapes embedded double quotes with a backslash rather than
	// breaking the M3U attribute's quoting, so the escaped form is what
	// must appear in valid output.
	if !strings.Contains(got, `tvg-id="id.with\"quote"`) {
		t.Errorf("tvg-id not properly quoted/escaped, got: %q", got)
	}
	if !strings.Contains(got, `group-title="News & Sports"`) {
		t.Errorf("group-title not properly quoted, got: %q", got)
	}
	if !strings.Contains(got, `Channel "Prime"`) {
		t.Errorf("channel name missing from output, got: %q", got)
	}
}