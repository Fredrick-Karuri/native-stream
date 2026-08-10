// api/middleware_test.go
//
// Tests for LoggingMiddleware and RecoveryMiddleware. Both are pure
// http.Handler -> http.Handler wrappers, so they're tested in isolation
// with hand-built inner handlers rather than through newTestHandler() —
// no store/epg/proxy machinery is needed to prove the wrapping behavior.

package api

import (
	"net/http"
	"net/http/httptest"
	"testing"
)

func TestLoggingMiddleware_CapturesStatus(t *testing.T) {
	// The inner handler is entirely under our control — it does nothing
	// except set a distinctive, non-default status code. If the wrapper
	// is transparent, the recorder must see this exact code afterward.
	inner := http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusTeapot) // 418 — deliberately not 200, not a common default
	})

	wrapped := LoggingMiddleware(inner)

	req := httptest.NewRequestWithContext(t.Context(), http.MethodGet, "/anything", nil)
	rec := httptest.NewRecorder()

	wrapped.ServeHTTP(rec, req)

	if rec.Code != http.StatusTeapot {
		t.Fatalf("expected status %d to pass through middleware, got %d", http.StatusTeapot, rec.Code)
	}
}

func TestLoggingMiddleware_DefaultsTo200WhenHandlerWritesNoHeader(t *testing.T) {
	// Some handlers never explicitly call WriteHeader — they just Write
	// body bytes, and Go's http package implicitly sends 200. Our
	// responseWriter initializes status: http.StatusOK for exactly this
	// case. Worth locking in as its own case, since it's a silent default
	// that would be easy to get wrong (e.g. defaulting to 0).
	inner := http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Write([]byte("ok"))
	})

	wrapped := LoggingMiddleware(inner)

	req := httptest.NewRequestWithContext(t.Context(), http.MethodGet, "/anything", nil)
	rec := httptest.NewRecorder()

	wrapped.ServeHTTP(rec, req)

	if rec.Code != http.StatusOK {
		t.Fatalf("expected default status 200, got %d", rec.Code)
	}
}

func TestRecoveryMiddleware_RecoversFromPanic(t *testing.T) {
	// The inner handler panics unconditionally. If recovery works, the
	// test reaches the assertions below at all (a failed recovery would
	// crash the test binary) AND the response is a clean 500, not a
	// half-written or empty response.
	inner := http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		panic("boom")
	})

	wrapped := RecoveryMiddleware(inner)

	req := httptest.NewRequestWithContext(t.Context(), http.MethodGet, "/anything", nil)
	rec := httptest.NewRecorder()

	wrapped.ServeHTTP(rec, req)

	if rec.Code != http.StatusInternalServerError {
		t.Fatalf("expected status 500 after panic recovery, got %d", rec.Code)
	}
}

func TestRecoveryMiddleware_PassesThroughNormalResponses(t *testing.T) {
	// Guard against an overzealous recover() implementation that somehow
	// interferes with the non-panicking path too.
	inner := http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusAccepted)
	})

	wrapped := RecoveryMiddleware(inner)

	req := httptest.NewRequestWithContext(t.Context(), http.MethodGet, "/anything", nil)
	rec := httptest.NewRecorder()

	wrapped.ServeHTTP(rec, req)

	if rec.Code != http.StatusAccepted {
		t.Fatalf("expected status 202 for non-panicking handler, got %d", rec.Code)
	}
}
