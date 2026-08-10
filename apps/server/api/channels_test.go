/*
api/channels_test.go

Tests for channel CRUD handlers migrated to the generated proto SDK.
Asserts wire-format contract (snake_case field names, status codes,
error shape) rather than internal store behavior, which is covered
separately in store/store_test.go.
*/
package api

import (
	"bytes"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"

	streamv1 "github.com/fredrick-karuri/nativestream/sdk-gen/go/stream/v1"
	"github.com/fredrick-karuri/nativestream/server/store"
	"google.golang.org/protobuf/encoding/protojson"
)

func TestHandleListChannels_Empty(t *testing.T) {
	h := newTestHandler()

	req := httptest.NewRequestWithContext(t.Context(), http.MethodGet, "/api/channels", nil)
	rec := httptest.NewRecorder()
	h.handleListChannels(rec, req)

	if rec.Code != http.StatusOK {
		t.Fatalf("expected 200, got %d", rec.Code)
	}
	// An empty repeated field is correctly omitted by protojson under
	// EmitUnpopulated:false — absent and empty-array are equivalent on
	// the wire. Assert valid JSON rather than requiring the key present.
	var body map[string]any
	if err := json.Unmarshal(rec.Body.Bytes(), &body); err != nil {
		t.Fatalf("response is not valid JSON: %v", err)
	}
}

func TestHandleListChannels_WithChannels(t *testing.T) {
	h := newTestHandler()
	h.store.Add(&store.Channel{ID: "ch1", Name: "Test Channel"})

	req := httptest.NewRequestWithContext(t.Context(), http.MethodGet, "/api/channels", nil)
	rec := httptest.NewRecorder()
	h.handleListChannels(rec, req)

	if rec.Code != http.StatusOK {
		t.Fatalf("expected 200, got %d", rec.Code)
	}
	var body map[string]any
	json.Unmarshal(rec.Body.Bytes(), &body)
	channels, ok := body["channels"].([]any)
	if !ok || len(channels) != 1 {
		t.Errorf("expected 1 channel in response, got: %v", body)
	}
}

func TestHandleGetChannel_NotFound(t *testing.T) {
	h := newTestHandler()

	req := httptest.NewRequestWithContext(t.Context(), http.MethodGet, "/api/channels/missing", nil)
	req.SetPathValue("id", "missing")
	rec := httptest.NewRecorder()
	h.handleGetChannel(rec, req)

	if rec.Code != http.StatusNotFound {
		t.Fatalf("expected 404, got %d", rec.Code)
	}
	var body map[string]any
	json.Unmarshal(rec.Body.Bytes(), &body)
	if _, ok := body["error"]; !ok {
		t.Errorf("expected 'error' field, got: %v", body)
	}
}

func TestHandleCreateChannel_Success(t *testing.T) {
	h := newTestHandler()

	reqBody := &streamv1.CreateChannelRequest{
		Name:          "BBC One",
		GroupTitle:    "News",
		TvgId:         "bbc.one",
		StreamUrl:     "http://example.com/bbc1.m3u8",
		Keywords:      []string{"bbc", "news"},
		StreamHeaders: map[string]string{"Referer": "http://example.com"},
	}
	data, _ := protojson.MarshalOptions{UseProtoNames: true}.Marshal(reqBody)

	req := httptest.NewRequestWithContext(
		t.Context(), http.MethodPost, "/api/channels", bytes.NewReader(data))
	rec := httptest.NewRecorder()
	h.handleCreateChannel(rec, req)

	if rec.Code != http.StatusCreated {
		t.Fatalf("expected 201, got %d: %s", rec.Code, rec.Body.String())
	}
	var body map[string]any
	json.Unmarshal(rec.Body.Bytes(), &body)
	if got := body["name"]; got != "BBC One" {
		t.Errorf("expected name 'BBC One', got %v", got)
	}
	if got := body["id"]; got != "bbc-one" {
		t.Errorf("expected slugified id 'bbc-one', got %v", got)
	}
}

func TestHandleCreateChannel_MissingName(t *testing.T) {
	h := newTestHandler()

	data, _ := protojson.MarshalOptions{UseProtoNames: true}.Marshal(&streamv1.CreateChannelRequest{})
	req := httptest.NewRequestWithContext(
		t.Context(), http.MethodPost, "/api/channels", bytes.NewReader(data))
	rec := httptest.NewRecorder()
	h.handleCreateChannel(rec, req)

	if rec.Code != http.StatusBadRequest {
		t.Fatalf("expected 400, got %d", rec.Code)
	}
}

func TestHandleUpdateChannel_Success(t *testing.T) {
	h := newTestHandler()
	h.store.Add(&store.Channel{ID: "ch1", Name: "Old Name"})

	newName := "New Name"
	reqBody := &streamv1.UpdateChannelRequest{Name: &newName}
	data, _ := protojson.MarshalOptions{UseProtoNames: true}.Marshal(reqBody)

	req := httptest.NewRequestWithContext(
		t.Context(), http.MethodPut, "/api/channels/ch1", bytes.NewReader(data))
	req.SetPathValue("id", "ch1")
	rec := httptest.NewRecorder()
	h.handleUpdateChannel(rec, req)

	if rec.Code != http.StatusOK {
		t.Fatalf("expected 200, got %d: %s", rec.Code, rec.Body.String())
	}
	if got := h.store.Get("ch1").Name; got != "New Name" {
		t.Errorf("expected store to reflect updated name, got %q", got)
	}
}

func TestHandleUpdateChannel_NotFound(t *testing.T) {
	h := newTestHandler()

	data, _ := protojson.MarshalOptions{UseProtoNames: true}.Marshal(&streamv1.UpdateChannelRequest{})
	req := httptest.NewRequestWithContext(
		t.Context(), http.MethodPut, "/api/channels/missing", bytes.NewReader(data))
	req.SetPathValue("id", "missing")
	rec := httptest.NewRecorder()
	h.handleUpdateChannel(rec, req)

	if rec.Code != http.StatusNotFound {
		t.Fatalf("expected 404, got %d", rec.Code)
	}
}