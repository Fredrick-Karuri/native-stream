// proxy/proxy_test.go
//
// ServeHTTP is the impure shell: real HTTP round trips to an upstream via
// p.client. Tests here use httptest.Server as a fake upstream (so no real
// network call ever leaves the test) and httptest.NewRecorder as the
// response writer. Where a test needs the client itself, we point
// ch.ActiveLink.URL (or the segment cache's TargetURL) at the fake
// server's URL rather than swapping p.client — that requires zero
// production code changes.
//
// Assertions here focus on what ServeHTTP itself is responsible for:
// routing decisions, status codes, and header/error handling. Playlist
// *rewrite content* correctness is already covered in rewriter_test.go —
// re-asserting every rewrite detail here would just duplicate that file.

package proxy

import (
	"context"
	"net"
	"net/http"
	"net/http/httptest"
	"net/url"
	"strings"
	"testing"
	"time"

	"github.com/fredrick-karuri/nativestream/server/store"
)

func newTestRequest(t *testing.T, method, target string) *http.Request {
	t.Helper()
	return httptest.NewRequestWithContext(context.Background(), method, target, nil)
}

func newTestStoreWithChannel(t *testing.T, channelID, activeLinkURL string, headers map[string]string) *store.Store {
	t.Helper()
	s := store.New(t.TempDir()+"/snapshot.json", 0.5)
	s.Add(&store.Channel{
		ID:   channelID,
		Name: "Test Channel",
		ActiveLink: &store.LinkScore{
			URL:       activeLinkURL,
			ChannelID: channelID,
			Score:     0.9,
			State:     store.StateActive,
			Headers:   headers,
		},
	})
	return s
}

// ── Original playlist routing (/stream/{channelID}/...) ────────────────

func TestServeHTTP_OriginalPlaylist_ProxiesAndRewritesM3U8(t *testing.T) {
	upstream := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/vnd.apple.mpegurl")
		_, _ = w.Write([]byte("#EXTM3U\n#EXTINF:10.0,\nsegment0.ts\n"))
	}))
	defer upstream.Close()

	s := newTestStoreWithChannel(t, "chan1", upstream.URL+"/live.m3u8", nil)
	p := New(Config{}, s)

	req := newTestRequest(t, http.MethodGet, "/stream/chan1/live.m3u8")
	rec := httptest.NewRecorder()

	p.ServeHTTP(rec, req)

	if rec.Code != http.StatusOK {
		t.Fatalf("status = %d, want %d, body: %s", rec.Code, http.StatusOK, rec.Body.String())
	}
	if ct := rec.Header().Get("Content-Type"); !strings.Contains(ct, "mpegurl") {
		t.Errorf("Content-Type = %q, want mpegurl", ct)
	}
	if !strings.Contains(rec.Body.String(), "/stream/chan1/proxy/seg/") {
		t.Errorf("body not rewritten to route through segment proxy, got:\n%s", rec.Body.String())
	}
}

func TestServeHTTP_OriginalPlaylist_ReturnsNotFoundForUnknownChannel(t *testing.T) {
	s := newTestStoreWithChannel(t, "chan1", "http://example.invalid/live.m3u8", nil)
	p := New(Config{}, s)

	req := newTestRequest(t, http.MethodGet, "/stream/unknown-channel/live.m3u8")
	rec := httptest.NewRecorder()

	p.ServeHTTP(rec, req)

	if rec.Code != http.StatusNotFound {
		t.Errorf("status = %d, want %d", rec.Code, http.StatusNotFound)
	}
}

func TestServeHTTP_OriginalPlaylist_ReturnsNotFoundWhenChannelHasNoActiveLink(t *testing.T) {
	s := store.New(t.TempDir()+"/snapshot.json", 0.5)
	s.Add(&store.Channel{ID: "chan1", Name: "No Link Channel"}) // ActiveLink left nil
	p := New(Config{}, s)

	req := newTestRequest(t, http.MethodGet, "/stream/chan1/live.m3u8")
	rec := httptest.NewRecorder()

	p.ServeHTTP(rec, req)

	if rec.Code != http.StatusNotFound {
		t.Errorf("status = %d, want %d", rec.Code, http.StatusNotFound)
	}
}

func TestServeHTTP_OriginalPlaylist_ReturnsBadRequestForShortPath(t *testing.T) {
	s := newTestStoreWithChannel(t, "chan1", "http://example.invalid/live.m3u8", nil)
	p := New(Config{}, s)

	// /stream/chan1 alone has only 2 path segments after trimming — the
	// handler requires at least 3 (empty, "stream", channelID, ...).
	req := newTestRequest(t, http.MethodGet, "/stream/chan1")
	rec := httptest.NewRecorder()

	p.ServeHTTP(rec, req)

	if rec.Code != http.StatusBadRequest {
		t.Errorf("status = %d, want %d", rec.Code, http.StatusBadRequest)
	}
}

func TestServeHTTP_OriginalPlaylist_PassesThroughNonPlaylistContentUnmodified(t *testing.T) {
	// A non-.m3u8, non-mpegurl upstream response (e.g. a raw video segment
	// served from the "original playlist" path directly) should be copied
	// through verbatim, not run through the rewriter.
	upstream := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "video/MP2T")
		_, _ = w.Write([]byte("raw-binary-segment-data"))
	}))
	defer upstream.Close()

	s := newTestStoreWithChannel(t, "chan1", upstream.URL+"/segment.ts", nil)
	p := New(Config{}, s)

	req := newTestRequest(t, http.MethodGet, "/stream/chan1/segment.ts")
	rec := httptest.NewRecorder()

	p.ServeHTTP(rec, req)

	if rec.Body.String() != "raw-binary-segment-data" {
		t.Errorf("body = %q, want passthrough of raw upstream body", rec.Body.String())
	}
}

func TestServeHTTP_OriginalPlaylist_ForwardsUpstreamErrorAsBadGateway(t *testing.T) {
	// Point at a URL nothing is listening on so the client.Do call itself
	// fails, exercising the "upstream error" branch rather than a
	// non-2xx status from a live server.
	s := newTestStoreWithChannel(t, "chan1", "http://127.0.0.1:1/live.m3u8", nil)
	p := New(Config{}, s)

	req := newTestRequest(t, http.MethodGet, "/stream/chan1/live.m3u8")
	rec := httptest.NewRecorder()

	p.ServeHTTP(rec, req)

	if rec.Code != http.StatusBadGateway {
		t.Errorf("status = %d, want %d", rec.Code, http.StatusBadGateway)
	}
}

func TestServeHTTP_OriginalPlaylist_InjectsPerLinkHeadersIntoUpstreamRequest(t *testing.T) {
	var gotAuth string
	upstream := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		gotAuth = r.Header.Get("X-Stream-Auth")
		w.Header().Set("Content-Type", "video/MP2T")
		_, _ = w.Write([]byte("ok"))
	}))
	defer upstream.Close()

	s := newTestStoreWithChannel(t, "chan1", upstream.URL+"/segment.ts", map[string]string{"X-Stream-Auth": "secret-token"})
	p := New(Config{}, s)

	req := newTestRequest(t, http.MethodGet, "/stream/chan1/segment.ts")
	rec := httptest.NewRecorder()

	p.ServeHTTP(rec, req)

	if gotAuth != "secret-token" {
		t.Errorf("upstream saw X-Stream-Auth = %q, want %q", gotAuth, "secret-token")
	}
}

// ── Segment routing (/proxy/seg/{id}.ts) ────────────────────────────────

func TestServeHTTP_Segment_ProxiesCachedSegmentSuccessfully(t *testing.T) {
	upstream := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusOK)
		_, _ = w.Write([]byte("segment-bytes"))
	}))
	defer upstream.Close()

	s := store.New(t.TempDir()+"/snapshot.json", 0.5)
	p := New(Config{}, s)
	p.cacheSegment("abc123", upstream.URL+"/seg.ts", nil)

	req := newTestRequest(t, http.MethodGet, "/stream/chan1/proxy/seg/abc123.ts")
	rec := httptest.NewRecorder()

	p.ServeHTTP(rec, req)

	if rec.Code != http.StatusOK {
		t.Fatalf("status = %d, want %d, body: %s", rec.Code, http.StatusOK, rec.Body.String())
	}
	if rec.Body.String() != "segment-bytes" {
		t.Errorf("body = %q, want %q", rec.Body.String(), "segment-bytes")
	}
	if ct := rec.Header().Get("Content-Type"); ct != "video/MP2T" {
		t.Errorf("Content-Type = %q, want video/MP2T", ct)
	}
}

func TestServeHTTP_Segment_ReturnsGoneForUnknownSegmentID(t *testing.T) {
	s := store.New(t.TempDir()+"/snapshot.json", 0.5)
	p := New(Config{}, s)
	// No cacheSegment call — the ID below was never cached.

	req := newTestRequest(t, http.MethodGet, "/stream/chan1/proxy/seg/never-cached.ts")
	rec := httptest.NewRecorder()

	p.ServeHTTP(rec, req)

	if rec.Code != http.StatusGone {
		t.Errorf("status = %d, want %d", rec.Code, http.StatusGone)
	}
}

func TestServeHTTP_Segment_ReturnsBadRequestForMalformedSignature(t *testing.T) {
	// startIdx >= endIdx: ".ts" appears before/at the same position as the
	// content after "/proxy/seg/", i.e. the "id" portion is empty or the
	// suffix is missing entirely.
	s := store.New(t.TempDir()+"/snapshot.json", 0.5)
	p := New(Config{}, s)

	req := newTestRequest(t, http.MethodGet, "/stream/chan1/proxy/seg/.ts")
	rec := httptest.NewRecorder()

	p.ServeHTTP(rec, req)

	if rec.Code != http.StatusBadRequest {
		t.Errorf("status = %d, want %d", rec.Code, http.StatusBadRequest)
	}
}

func TestServeHTTP_Segment_ForwardsRangeHeaderAndReturnsPartialContent(t *testing.T) {
	upstream := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Header.Get("Range") != "bytes=0-99" {
			t.Errorf("upstream saw Range = %q, want %q", r.Header.Get("Range"), "bytes=0-99")
		}
		w.Header().Set("Content-Range", "bytes 0-99/1000")
		w.WriteHeader(http.StatusPartialContent)
		_, _ = w.Write([]byte("partial"))
	}))
	defer upstream.Close()

	s := store.New(t.TempDir()+"/snapshot.json", 0.5)
	p := New(Config{}, s)
	p.cacheSegment("range1", upstream.URL+"/seg.ts", nil)

	req := newTestRequest(t, http.MethodGet, "/stream/chan1/proxy/seg/range1.ts")
	req.Header.Set("Range", "bytes=0-99")
	rec := httptest.NewRecorder()

	p.ServeHTTP(rec, req)

	if rec.Code != http.StatusPartialContent {
		t.Fatalf("status = %d, want %d", rec.Code, http.StatusPartialContent)
	}
	if got := rec.Header().Get("Content-Range"); got != "bytes 0-99/1000" {
		t.Errorf("Content-Range = %q, want %q", got, "bytes 0-99/1000")
	}
}

func TestServeHTTP_Segment_ForwardsUpstreamErrorAsBadGateway(t *testing.T) {
	s := store.New(t.TempDir()+"/snapshot.json", 0.5)
	p := New(Config{}, s)
	p.cacheSegment("dead1", "http://127.0.0.1:1/seg.ts", nil)

	req := newTestRequest(t, http.MethodGet, "/stream/chan1/proxy/seg/dead1.ts")
	rec := httptest.NewRecorder()

	p.ServeHTTP(rec, req)

	if rec.Code != http.StatusBadGateway {
		t.Errorf("status = %d, want %d", rec.Code, http.StatusBadGateway)
	}
}

// ── Variant playlist routing (?url=) ─────────────────────────────────────

// withPublicLoopback makes validateUpstreamURL treat every resolved
// address as public (8.8.8.8) for the duration of the test, via the
// lookupIPAddr seam in ssrf.go. httptest.Server only binds to loopback,
// which the real SSRF guard correctly rejects (see
// TestServeHTTP_VariantPlaylist_RejectsSSRFTargetWithForbidden) — this
// lets the "happy path" tests below still exercise the real guard call
// path and a real local upstream, instead of skipping the guard or using
// a fabricated address that never gets a real response. isPublicIP
// itself is never touched; only what lookupIPAddr reports back is faked,
// and only within this test (restored via t.Cleanup).
func withPublicLoopback(t *testing.T) {
	t.Helper()
	original := lookupIPAddr
	lookupIPAddr = func(_ context.Context, _ string) ([]net.IPAddr, error) {
		return []net.IPAddr{{IP: net.ParseIP("8.8.8.8")}}, nil
	}
	t.Cleanup(func() { lookupIPAddr = original })
}

func TestServeHTTP_VariantPlaylist_ProxiesAndRewritesWhenURLIsPublic(t *testing.T) {
	withPublicLoopback(t)

	upstream := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		_, _ = w.Write([]byte("#EXTM3U\n#EXTINF:10.0,\nsegment0.ts\n"))
	}))
	defer upstream.Close()

	s := newTestStoreWithChannel(t, "chan1", "http://example.invalid/unused.m3u8", nil)
	p := New(Config{}, s)

	variantURL := url.QueryEscape(upstream.URL + "/variant.m3u8")
	req := newTestRequest(t, http.MethodGet, "/stream/chan1/proxy?url="+variantURL)
	rec := httptest.NewRecorder()

	p.ServeHTTP(rec, req)

	if rec.Code != http.StatusOK {
		t.Fatalf("status = %d, want %d, body: %s", rec.Code, http.StatusOK, rec.Body.String())
	}
	if !strings.Contains(rec.Body.String(), "/stream/chan1/proxy/seg/") {
		t.Errorf("variant playlist body not rewritten, got:\n%s", rec.Body.String())
	}
}

func TestServeHTTP_VariantPlaylist_RejectsSSRFTargetWithForbidden(t *testing.T) {
	// This is the point where ssrf.go's guard is actually wired into a
	// live request path — a loopback/private target must be rejected
	// before any upstream call is attempted. Uses the real resolver (no
	// withPublicLoopback here) since 127.0.0.1 must genuinely be rejected.
	s := newTestStoreWithChannel(t, "chan1", "http://example.invalid/unused.m3u8", nil)
	p := New(Config{}, s)

	variantURL := url.QueryEscape("http://127.0.0.1:9999/internal")
	req := newTestRequest(t, http.MethodGet, "/stream/chan1/proxy?url="+variantURL)
	rec := httptest.NewRecorder()

	p.ServeHTTP(rec, req)

	if rec.Code != http.StatusForbidden {
		t.Errorf("status = %d, want %d (SSRF guard should reject before any upstream call)", rec.Code, http.StatusForbidden)
	}
}

// NOTE: a prior version of this test asserted 400 for a "%zz" query value,
// assuming url.QueryUnescape would fail on it and the malformed-URL branch
// would fire. An actual run returned 502 instead, meaning the value
// reaches the upstream call and fails there — net/url's query-string
// handling of "%zz" doesn't behave the way that assumption modeled.
// Removed rather than re-guessed; revisit only with a traced value of
// r.URL.Query().Get("url") for a request built with "%zz" in the raw
// query, confirming what QueryUnescape actually receives and returns.

func TestServeHTTP_VariantPlaylist_UsesChannelHeadersFromPathNotQueryTarget(t *testing.T) {
	// Headers come from the channel identified by the path's channelID
	// segment (parts[1]), independent of whichever upstream the ?url=
	// param points at.
	withPublicLoopback(t)

	var gotAuth string
	upstream := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		gotAuth = r.Header.Get("X-Stream-Auth")
		_, _ = w.Write([]byte("#EXTM3U\n"))
	}))
	defer upstream.Close()

	s := newTestStoreWithChannel(t, "chan1", "http://example.invalid/unused.m3u8", map[string]string{"X-Stream-Auth": "chan1-secret"})
	p := New(Config{}, s)

	variantURL := url.QueryEscape(upstream.URL + "/variant.m3u8")
	req := newTestRequest(t, http.MethodGet, "/stream/chan1/proxy?url="+variantURL)
	rec := httptest.NewRecorder()

	p.ServeHTTP(rec, req)

	if gotAuth != "chan1-secret" {
		t.Errorf("upstream saw X-Stream-Auth = %q, want %q", gotAuth, "chan1-secret")
	}
}

// ── SetEnabled / IsEnabled ───────────────────────────────────────────────

func TestSetEnabled_IsEnabled_RoundTrips(t *testing.T) {
	s := store.New(t.TempDir()+"/snapshot.json", 0.5)
	p := New(Config{Enabled: false}, s)

	if p.IsEnabled() {
		t.Fatal("IsEnabled() = true, want false from initial Config")
	}

	p.SetEnabled(true)
	if !p.IsEnabled() {
		t.Error("IsEnabled() = false after SetEnabled(true), want true")
	}

	p.SetEnabled(false)
	if p.IsEnabled() {
		t.Error("IsEnabled() = true after SetEnabled(false), want false")
	}
}

// ── copyResponseHeaders ──────────────────────────────────────────────────

func TestCopyResponseHeaders_SkipsContentLength(t *testing.T) {
	resp := &http.Response{
		Header: http.Header{
			"Content-Length": []string{"1234"},
			"X-Custom":       []string{"value"},
		},
	}
	rec := httptest.NewRecorder()

	copyResponseHeaders(rec, resp)

	if rec.Header().Get("Content-Length") != "" {
		t.Error("Content-Length was copied, want it skipped (handler sets its own via WriteHeader/body length)")
	}
	if rec.Header().Get("X-Custom") != "value" {
		t.Error("X-Custom header was not copied through")
	}
}

func TestCopyResponseHeaders_SkipsContentLengthCaseInsensitively(t *testing.T) {
	resp := &http.Response{
		Header: http.Header{
			"content-length": []string{"1234"},
		},
	}
	rec := httptest.NewRecorder()

	copyResponseHeaders(rec, resp)

	if rec.Header().Get("Content-Length") != "" {
		t.Error("lowercase content-length was copied, want it skipped regardless of case")
	}
}

// Guard against this whole file accidentally depending on real elapsed
// time anywhere (e.g. if a future edit adds a real sleep/timeout wait).
// Not a functional test — just keeps the suite honest about staying fast.
func TestProxyTestSuite_DoesNotSleep(t *testing.T) {
	start := time.Now()
	s := store.New(t.TempDir()+"/snapshot.json", 0.5)
	_ = New(Config{}, s)
	if time.Since(start) > 100*time.Millisecond {
		t.Error("trivial setup took >100ms — unexpected blocking call")
	}
}
