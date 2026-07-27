// api/handlers_test.go

package api

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/fredrick-karuri/nativestream/server"
	"github.com/fredrick-karuri/nativestream/server/control"
	"github.com/fredrick-karuri/nativestream/server/epg"
	"github.com/fredrick-karuri/nativestream/server/proxy"
	"github.com/fredrick-karuri/nativestream/server/store"
	"github.com/fredrick-karuri/nativestream/server/validator"
)

func newTestHandler() *Handler {
	s := store.New("", 0.3)
	e := epg.New(epg.Config{}, s)
	px := proxy.New(proxy.Config{}, s)
	v := validator.New(validator.DefaultConfig(), s, "", "", "")
	hub := control.NewHub()

	return New(s, e, px, v, proxy.Config{}, "http://localhost:8889", hub,server.Version)
}

func TestHandleHealth(t *testing.T) {
	h := newTestHandler()

	req := httptest.NewRequest(http.MethodGet, "/api/health", nil)
	rec := httptest.NewRecorder()

	h.handleHealth(rec, req)

	if rec.Code != http.StatusOK {
		t.Fatalf("expected status 200, got %d", rec.Code)
	}

	var body map[string]any
	if err := json.Unmarshal(rec.Body.Bytes(), &body); err != nil {
		t.Fatalf("response is not valid JSON: %v", err)
	}

	// Field names must stay snake_case — this is the exact contract every
	// existing client (Swift/Kotlin) depends on. protojson defaults to
	// camelCase; UseProtoNames must be set to prevent silently breaking
	// every client on the first migrated endpoint.
	wantFields := []string{"status", "uptime", "channels", "healthy", "version", "server_name", "addr"}
	for _, f := range wantFields {
		if _, ok := body[f]; !ok {
			t.Errorf("expected field %q in response, got: %v", f, body)
		}
	}

	if _, ok := body["lastProbe"]; ok {
		t.Errorf("unexpected camelCase field 'lastProbe' — protojson must use UseProtoNames")
	}

	// No probe has run yet in this fresh Handler — last_probe must be
	// entirely absent, not null or a zero-date string.
	if _, ok := body["last_probe"]; ok {
		t.Errorf("expected last_probe to be absent when no probe has run, got: %v", body["last_probe"])
	}

	if got := body["status"]; got != "ok" {
		t.Errorf("expected status 'ok', got %v", got)
	}

}