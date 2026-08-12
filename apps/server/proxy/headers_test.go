// proxy/headers_test.go
//
// Both functions here are pure: (*http.Request, config-or-map) in, header
// mutation out, no I/O. Build a request, call the function, assert on
// req.Header.

package proxy

import (
	"net/http"
	"testing"
)

// ── injectHeaders ────────────────────────────────────────────────────────

func TestInjectHeaders_SetsRefererWhenConfigured(t *testing.T) {
	req := mustRequest(t)
	incoming := mustRequest(t)

	injectHeaders(req, incoming, Config{Referer: "https://example.com"})

	if got := req.Header.Get("Referer"); got != "https://example.com" {
		t.Errorf("Referer = %q, want %q", got, "https://example.com")
	}
}

func TestInjectHeaders_OmitsRefererWhenNotConfigured(t *testing.T) {
	req := mustRequest(t)
	incoming := mustRequest(t)

	injectHeaders(req, incoming, Config{})

	if got := req.Header.Get("Referer"); got != "" {
		t.Errorf("Referer = %q, want empty when not configured", got)
	}
}

func TestInjectHeaders_UsesConfiguredUserAgent(t *testing.T) {
	req := mustRequest(t)
	incoming := mustRequest(t)

	injectHeaders(req, incoming, Config{UserAgent: "MyCustomAgent/1.0"})

	if got := req.Header.Get("User-Agent"); got != "MyCustomAgent/1.0" {
		t.Errorf("User-Agent = %q, want %q", got, "MyCustomAgent/1.0")
	}
}

func TestInjectHeaders_FallsBackToDefaultUserAgentWhenUnconfigured(t *testing.T) {
	req := mustRequest(t)
	incoming := mustRequest(t)

	injectHeaders(req, incoming, Config{})

	got := req.Header.Get("User-Agent")
	if got == "" {
		t.Fatal("User-Agent = empty, want a default fallback value")
	}
	want := "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7)"
	if got != want {
		t.Errorf("User-Agent = %q, want default %q", got, want)
	}
}

func TestInjectHeaders_SetsOriginWhenConfigured(t *testing.T) {
	req := mustRequest(t)
	incoming := mustRequest(t)

	injectHeaders(req, incoming, Config{Origin: "https://example.com"})

	if got := req.Header.Get("Origin"); got != "https://example.com" {
		t.Errorf("Origin = %q, want %q", got, "https://example.com")
	}
}

func TestInjectHeaders_ForwardsRangeHeaderFromIncomingRequest(t *testing.T) {
	req := mustRequest(t)
	incoming := mustRequest(t)
	incoming.Header.Set("Range", "bytes=0-1023")

	injectHeaders(req, incoming, Config{})

	if got := req.Header.Get("Range"); got != "bytes=0-1023" {
		t.Errorf("Range = %q, want %q", got, "bytes=0-1023")
	}
}

func TestInjectHeaders_OmitsRangeWhenIncomingHasNone(t *testing.T) {
	req := mustRequest(t)
	incoming := mustRequest(t)

	injectHeaders(req, incoming, Config{})

	if got := req.Header.Get("Range"); got != "" {
		t.Errorf("Range = %q, want empty when incoming request had no Range header", got)
	}
}

// ── InjectFromMap ────────────────────────────────────────────────────────

func TestInjectFromMap_SetsAllProvidedHeaders(t *testing.T) {
	req := mustRequest(t)

	InjectFromMap(req, map[string]string{
		"X-Custom-Auth": "token123",
		"Referer":       "https://per-stream-referer.example",
	})

	if got := req.Header.Get("X-Custom-Auth"); got != "token123" {
		t.Errorf("X-Custom-Auth = %q, want %q", got, "token123")
	}
	if got := req.Header.Get("Referer"); got != "https://per-stream-referer.example" {
		t.Errorf("Referer = %q, want %q", got, "https://per-stream-referer.example")
	}
}

func TestInjectFromMap_OverridesStaticConfigValues(t *testing.T) {
	// This is the documented purpose in the doc comment: per-link headers
	// take priority over static Config values for the same key.
	req := mustRequest(t)
	injectHeaders(req, mustRequest(t), Config{UserAgent: "StaticAgent/1.0"})

	InjectFromMap(req, map[string]string{"User-Agent": "PerStreamAgent/2.0"})

	if got := req.Header.Get("User-Agent"); got != "PerStreamAgent/2.0" {
		t.Errorf("User-Agent = %q, want per-link override %q", got, "PerStreamAgent/2.0")
	}
}

func TestInjectFromMap_EmptyMapIsNoOp(t *testing.T) {
	req := mustRequest(t)
	injectHeaders(req, mustRequest(t), Config{UserAgent: "StaticAgent/1.0"})

	InjectFromMap(req, map[string]string{})

	if got := req.Header.Get("User-Agent"); got != "StaticAgent/1.0" {
		t.Errorf("User-Agent = %q, want unchanged %q after empty map", got, "StaticAgent/1.0")
	}
}

// ── shared helper ────────────────────────────────────────────────────────

func mustRequest(t *testing.T) *http.Request {
	t.Helper()
	req, err := http.NewRequest(http.MethodGet, "http://example.invalid/x", nil)
	if err != nil {
		t.Fatalf("http.NewRequest() error = %v", err)
	}
	return req
}
