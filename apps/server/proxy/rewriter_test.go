// proxy/rewriter_test.go
//
// rewriter.go rewrites HLS playlist bodies so segment/variant URLs route
// back through this proxy. Most of it is pure string transformation
// (resolveURL, isMasterPlaylist, rewriteMasterPlaylist). wrapURLInProxy and
// proxySecureURIAttr are the exception: they call p.cacheSegment, which
// does a sync.Map.Store and spawns a goroutine that sleeps 8 minutes then
// deletes the entry. Tests below never wait on or assert anything about
// that goroutine/timer — only the synchronous return value and immediate
// segmentCache state are checked, so no test leaks a dependency on timing.

package proxy

import (
	"strings"
	"testing"
)

func newTestProxy() *Proxy {
	// store is unused by every method under test here (rewriteMasterPlaylist,
	// rewriteMediaPlaylist, wrapURLInProxy, proxySecureURIAttr, cacheSegment
	// only touch p.segmentCache) — nil is safe.
	return New(Config{}, nil)
}

// ── isMasterPlaylist ─────────────────────────────────────────────────────

func TestIsMasterPlaylist_TrueWhenStreamInfPresent(t *testing.T) {
	body := "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1280000\nlow.m3u8\n"
	if !isMasterPlaylist(body) {
		t.Error("isMasterPlaylist() = false, want true for body containing #EXT-X-STREAM-INF")
	}
}

func TestIsMasterPlaylist_FalseForMediaPlaylist(t *testing.T) {
	body := "#EXTM3U\n#EXTINF:10.0,\nsegment0.ts\n"
	if isMasterPlaylist(body) {
		t.Error("isMasterPlaylist() = true, want false for a media (segment-level) playlist")
	}
}

// ── resolveURL ───────────────────────────────────────────────────────────

func TestResolveURL_ReturnsAbsoluteURLUnchanged(t *testing.T) {
	got := resolveURL("https://cdn.example.com/seg1.ts", "https://cdn.example.com/base.m3u8")
	want := "https://cdn.example.com/seg1.ts"
	if got != want {
		t.Errorf("resolveURL() = %q, want %q (absolute URL passed through)", got, want)
	}
}

func TestResolveURL_ResolvesRelativePathAgainstBase(t *testing.T) {
	got := resolveURL("seg1.ts", "https://cdn.example.com/live/base.m3u8")
	want := "https://cdn.example.com/live/seg1.ts"
	if got != want {
		t.Errorf("resolveURL() = %q, want %q", got, want)
	}
}

func TestResolveURL_ResolvesRootRelativePathAgainstBase(t *testing.T) {
	got := resolveURL("/live2/seg1.ts", "https://cdn.example.com/live/base.m3u8")
	want := "https://cdn.example.com/live2/seg1.ts"
	if got != want {
		t.Errorf("resolveURL() = %q, want %q", got, want)
	}
}

func TestResolveURL_FallsBackToRawURLWhenBaseUnparsable(t *testing.T) {
	got := resolveURL("seg1.ts", "://not a valid url")
	if got != "seg1.ts" {
		t.Errorf("resolveURL() = %q, want raw input returned unchanged on unparsable base", got)
	}
}

// ── rewriteMasterPlaylist ────────────────────────────────────────────────

func TestRewriteMasterPlaylist_RewritesVariantLinesToProxyURL(t *testing.T) {
	p := newTestProxy()
	body := "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1280000\nlow.m3u8\n"

	got := p.rewriteMasterPlaylist(body, "https://cdn.example.com/live/master.m3u8", "chan1")

	if !strings.Contains(got, "/stream/chan1/proxy?url=") {
		t.Errorf("rewriteMasterPlaylist() output missing proxy route, got:\n%s", got)
	}

	lines := strings.Split(got, "\n")
	if len(lines) >= 3 && lines[2] == "low.m3u8" {
		t.Errorf("rewriteMasterPlaylist() left original relative URL unrewritten, got:\n%s", got)
	}
}

func TestRewriteMasterPlaylist_LeavesCommentAndBlankLinesUntouched(t *testing.T) {
	p := newTestProxy()
	body := "#EXTM3U\n#EXT-X-VERSION:3\n\n#EXT-X-STREAM-INF:BANDWIDTH=1280000\nlow.m3u8\n"

	got := p.rewriteMasterPlaylist(body, "https://cdn.example.com/master.m3u8", "chan1")

	if !strings.Contains(got, "#EXTM3U") || !strings.Contains(got, "#EXT-X-VERSION:3") {
		t.Errorf("rewriteMasterPlaylist() altered comment lines, got:\n%s", got)
	}
}

func TestRewriteMasterPlaylist_EncodesResolvedURLInQueryParam(t *testing.T) {
	p := newTestProxy()
	body := "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1280000\nvariant.m3u8\n"

	got := p.rewriteMasterPlaylist(body, "https://cdn.example.com/live/master.m3u8", "chan1")

	// The resolved absolute URL, once query-escaped, must appear verbatim —
	// this catches a regression where resolution or escaping is skipped.
	if !strings.Contains(got, "url=https%3A%2F%2Fcdn.example.com%2Flive%2Fvariant.m3u8") {
		t.Errorf("rewriteMasterPlaylist() did not correctly resolve+escape variant URL, got:\n%s", got)
	}
}

// ── rewriteMediaPlaylist / wrapURLInProxy ───────────────────────────────

func TestRewriteMediaPlaylist_RewritesSegmentLinesToSegmentProxyRoute(t *testing.T) {
	p := newTestProxy()
	body := "#EXTM3U\n#EXTINF:10.0,\nsegment0.ts\n"

	got := p.rewriteMediaPlaylist(body, "https://cdn.example.com/live/base.m3u8", "chan1", nil)

	if !strings.Contains(got, "/stream/chan1/proxy/seg/") {
		t.Errorf("rewriteMediaPlaylist() missing segment proxy route, got:\n%s", got)
	}
	if !strings.HasSuffix(strings.TrimSpace(strings.Split(got, "\n")[2]), ".ts") {
		t.Errorf("rewriteMediaPlaylist() segment line should still end in .ts, got:\n%s", got)
	}
}

func TestRewriteMediaPlaylist_LeavesCommentLinesWithoutURIUnchanged(t *testing.T) {
	p := newTestProxy()
	body := "#EXTM3U\n#EXT-X-TARGETDURATION:10\n#EXTINF:10.0,\nsegment0.ts\n"

	got := p.rewriteMediaPlaylist(body, "https://cdn.example.com/base.m3u8", "chan1", nil)

	if !strings.Contains(got, "#EXT-X-TARGETDURATION:10") {
		t.Errorf("rewriteMediaPlaylist() altered a plain comment tag, got:\n%s", got)
	}
}

func TestRewriteMediaPlaylist_RewritesURIAttributeInKeyTag(t *testing.T) {
	p := newTestProxy()
	body := `#EXTM3U` + "\n" + `#EXT-X-KEY:METHOD=AES-128,URI="https://cdn.example.com/key1"` + "\n" + `#EXTINF:10.0,` + "\n" + `segment0.ts` + "\n"

	got := p.rewriteMediaPlaylist(body, "https://cdn.example.com/base.m3u8", "chan1", nil)

	if !strings.Contains(got, `URI="/stream/chan1/proxy/seg/`) {
		t.Errorf("rewriteMediaPlaylist() did not rewrite URI= attribute, got:\n%s", got)
	}
	if strings.Contains(got, `URI="https://cdn.example.com/key1"`) {
		t.Errorf("rewriteMediaPlaylist() left original key URI unrewritten, got:\n%s", got)
	}
}

func TestRewriteMediaPlaylist_EmptyLinesSkippedWithoutPanic(t *testing.T) {
	p := newTestProxy()
	body := "#EXTM3U\n\n\n#EXTINF:10.0,\nsegment0.ts\n"

	got := p.rewriteMediaPlaylist(body, "https://cdn.example.com/base.m3u8", "chan1", nil)

	if got == "" {
		t.Error("rewriteMediaPlaylist() returned empty output")
	}
}

func TestWrapURLInProxy_ResolvesRelativeSegmentAgainstBase(t *testing.T) {
	p := newTestProxy()

	got := p.wrapURLInProxy("segment0.ts", "chan1", "https://cdn.example.com/live/base.m3u8", nil)

	if !strings.HasPrefix(got, "/stream/chan1/proxy/seg/") || !strings.HasSuffix(got, ".ts") {
		t.Errorf("wrapURLInProxy() = %q, want /stream/chan1/proxy/seg/<hash>.ts shape", got)
	}
}

func TestWrapURLInProxy_SameTargetURLProducesSameHashKey(t *testing.T) {
	// Deterministic hashing means repeated references to the same segment
	// URL within a playlist collapse to the same cache key/proxy path.
	p := newTestProxy()

	first := p.wrapURLInProxy("https://cdn.example.com/seg1.ts", "chan1", "https://cdn.example.com/base.m3u8", nil)
	second := p.wrapURLInProxy("https://cdn.example.com/seg1.ts", "chan1", "https://cdn.example.com/base.m3u8", nil)

	if first != second {
		t.Errorf("wrapURLInProxy() not deterministic: first=%q second=%q", first, second)
	}
}

func TestWrapURLInProxy_DifferentTargetURLsProduceDifferentHashKeys(t *testing.T) {
	p := newTestProxy()

	a := p.wrapURLInProxy("https://cdn.example.com/seg1.ts", "chan1", "https://cdn.example.com/base.m3u8", nil)
	b := p.wrapURLInProxy("https://cdn.example.com/seg2.ts", "chan1", "https://cdn.example.com/base.m3u8", nil)

	if a == b {
		t.Errorf("wrapURLInProxy() produced same path for different segment URLs: %q", a)
	}
}

func TestWrapURLInProxy_StoresTargetURLAndHeadersInSegmentCache(t *testing.T) {
	// Read segmentCache synchronously, right after the call — before the
	// 8-minute cleanup goroutine spawned inside cacheSegment could ever
	// fire. No dependency on that goroutine's timing is introduced.
	p := newTestProxy()
	headers := map[string]string{"X-Auth": "secret"}

	path := p.wrapURLInProxy("https://cdn.example.com/seg1.ts", "chan1", "https://cdn.example.com/base.m3u8", headers)

	// Extract the hash key from the returned path: /stream/chan1/proxy/seg/<key>.ts
	key := strings.TrimSuffix(strings.TrimPrefix(path, "/stream/chan1/proxy/seg/"), ".ts")

	val, ok := p.segmentCache.Load(key)
	if !ok {
		t.Fatalf("segmentCache has no entry for key %q derived from returned path %q", key, path)
	}
	seg, ok := val.(cachedSegment)
	if !ok {
		t.Fatalf("segmentCache entry is not a cachedSegment: %T", val)
	}
	if seg.TargetURL != "https://cdn.example.com/seg1.ts" {
		t.Errorf("cached TargetURL = %q, want %q", seg.TargetURL, "https://cdn.example.com/seg1.ts")
	}
	if seg.Headers["X-Auth"] != "secret" {
		t.Errorf("cached Headers[X-Auth] = %q, want %q", seg.Headers["X-Auth"], "secret")
	}
}

// ── proxySecureURIAttr ───────────────────────────────────────────────────

func TestProxySecureURIAttr_ReplacesOnlyTheURIValue(t *testing.T) {
	p := newTestProxy()
	line := `#EXT-X-KEY:METHOD=AES-128,URI="https://cdn.example.com/key1",IV=0x1234`

	got := p.proxySecureURIAttr(line, "chan1", "https://cdn.example.com/base.m3u8", nil)

	if !strings.HasPrefix(got, `#EXT-X-KEY:METHOD=AES-128,URI="/stream/chan1/proxy/seg/`) {
		t.Errorf("proxySecureURIAttr() = %q, want prefix preserved with rewritten URI", got)
	}
	if !strings.HasSuffix(got, `",IV=0x1234`) {
		t.Errorf("proxySecureURIAttr() = %q, want suffix after URI= preserved", got)
	}
}

func TestProxySecureURIAttr_ReturnsLineUnchangedWhenNoURIAttr(t *testing.T) {
	p := newTestProxy()
	line := `#EXT-X-TARGETDURATION:10`

	got := p.proxySecureURIAttr(line, "chan1", "https://cdn.example.com/base.m3u8", nil)

	if got != line {
		t.Errorf("proxySecureURIAttr() = %q, want unchanged %q", got, line)
	}
}

// ── rewritePlaylist (dispatcher) ─────────────────────────────────────────

func TestRewritePlaylist_DispatchesToMasterRewriterForMasterPlaylist(t *testing.T) {
	p := newTestProxy()
	body := "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1280000\nlow.m3u8\n"

	got := p.rewritePlaylist(body, "https://cdn.example.com/master.m3u8", "chan1", nil)

	if !strings.Contains(got, "/stream/chan1/proxy?url=") {
		t.Errorf("rewritePlaylist() did not route through master rewriter for master playlist, got:\n%s", got)
	}
}

func TestRewritePlaylist_DispatchesToMediaRewriterForMediaPlaylist(t *testing.T) {
	p := newTestProxy()
	body := "#EXTM3U\n#EXTINF:10.0,\nsegment0.ts\n"

	got := p.rewritePlaylist(body, "https://cdn.example.com/base.m3u8", "chan1", nil)

	if !strings.Contains(got, "/stream/chan1/proxy/seg/") {
		t.Errorf("rewritePlaylist() did not route through media rewriter for media playlist, got:\n%s", got)
	}
}
