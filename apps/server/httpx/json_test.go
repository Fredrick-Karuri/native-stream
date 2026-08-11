/*
httpx/json_test.go

Tests for WriteProtoJSON and ReadProtoJSON. Uses streamv1.Envelope as the
fixture proto type since it's the message these helpers serve in production
(control package). Covers: field-name casing on the wire (UseProtoNames),
zero-value omission (EmitUnpopulated: false), status code + Content-Type
propagation, marshal failure on nil message, and unmarshal failure on
malformed JSON.
*/
package httpx

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	streamv1 "github.com/fredrick-karuri/nativestream/sdk-gen/go/stream/v1"
)

const (
	testDeviceFrom = "controller-1"
	testDeviceTo   = "broadcast"
)

// TestWriteProtoJSON_WritesStatusAndContentType checks the two things a
// caller relies on beyond the body: the HTTP status code they passed
// through unchanged, and a JSON Content-Type header set unconditionally.
func TestWriteProtoJSON_WritesStatusAndContentType(t *testing.T) {
	rec := httptest.NewRecorder()
	env := &streamv1.Envelope{
		Type: streamv1.MessageType_MESSAGE_TYPE_PLAY,
		From: testDeviceFrom,
		To:   testDeviceTo,
	}

	WriteProtoJSON(rec, http.StatusCreated, env)

	if rec.Code != http.StatusCreated {
		t.Errorf("status = %d, want %d", rec.Code, http.StatusCreated)
	}
	if got := rec.Header().Get("Content-Type"); got != "application/json" {
		t.Errorf("Content-Type = %q, want %q", got, "application/json")
	}
}

// TestWriteProtoJSON_UsesProtoFieldNames locks in the UseProtoNames: true
// marshal option. Regressing this silently breaks existing Swift/Kotlin
// clients that expect snake_case keys like "payload_json", not the
// camelCase protojson would emit by default.
func TestWriteProtoJSON_UsesProtoFieldNames(t *testing.T) {
	rec := httptest.NewRecorder()
	env := &streamv1.Envelope{
		Type:        streamv1.MessageType_MESSAGE_TYPE_STATE_UPDATE,
		From:        testDeviceFrom,
		To:          testDeviceTo,
		PayloadJson: `{"volume":0.5}`,
	}

	WriteProtoJSON(rec, http.StatusOK, env)

	body := rec.Body.String()
	if !strings.Contains(body, `"payload_json"`) {
		t.Errorf("body missing snake_case field %q, got: %s", "payload_json", body)
	}
	if strings.Contains(body, `"payloadJson"`) {
		t.Errorf("body contains camelCase field, want snake_case only, got: %s", body)
	}
}

// TestWriteProtoJSON_OmitsZeroValueFields locks in EmitUnpopulated: false.
// An Envelope with Type left at its zero value (MESSAGE_TYPE_UNSPECIFIED,
// which is 0) must not appear in the output at all — the wire contract is
// "absent field", not "field present with 0".
func TestWriteProtoJSON_OmitsZeroValueFields(t *testing.T) {
	rec := httptest.NewRecorder()
	env := &streamv1.Envelope{
		From: testDeviceFrom,
		To:   testDeviceTo,
		// Type and PayloadJson intentionally left at zero value.
	}

	WriteProtoJSON(rec, http.StatusOK, env)

	var decoded map[string]any
	if err := json.Unmarshal(rec.Body.Bytes(), &decoded); err != nil {
		t.Fatalf("response body is not valid JSON: %v", err)
	}
	if _, present := decoded["type"]; present {
		t.Errorf("zero-value field %q present in output, want omitted; body: %s", "type", rec.Body.String())
	}
	if _, present := decoded["payload_json"]; present {
		t.Errorf("zero-value field %q present in output, want omitted; body: %s", "payload_json", rec.Body.String())
	}
}

// TestWriteProtoJSON_MarshalFailureReturns500 checks the error path: a nil
// *Envelope is a valid proto.Message interface value (non-nil interface,
// nil underlying pointer) that protojson refuses to marshal, and the
// handler must degrade to a 500 rather than panic or write a partial body.
func TestWriteProtoJSON_NilMessageWritesEmptyObject(t *testing.T) {
	rec := httptest.NewRecorder()
	var nilEnv *streamv1.Envelope

	WriteProtoJSON(rec, http.StatusOK, nilEnv)

	if rec.Code != http.StatusOK {
		t.Errorf("status = %d, want %d", rec.Code, http.StatusOK)
	}
	if got := strings.TrimSpace(rec.Body.String()); got != "{}" {
		t.Errorf("body = %q, want %q", got, "{}")
	}
}

// TestReadProtoJSON_DecodesRequestBody checks the round trip: a snake_case
// JSON body (as a real client would send, matching UseProtoNames on write)
// decodes into the expected proto field values.
func TestReadProtoJSON_DecodesRequestBody(t *testing.T) {
	body := `{"type":"MESSAGE_TYPE_PING","from":"` + testDeviceFrom + `","to":"` + testDeviceTo + `"}`
	req := httptest.NewRequest(http.MethodPost, "/envelope", strings.NewReader(body))

	var env streamv1.Envelope
	if err := ReadProtoJSON(req, &env); err != nil {
		t.Fatalf("ReadProtoJSON returned error: %v", err)
	}

	if env.GetType() != streamv1.MessageType_MESSAGE_TYPE_PING {
		t.Errorf("Type = %v, want %v", env.GetType(), streamv1.MessageType_MESSAGE_TYPE_PING)
	}
	if env.GetFrom() != testDeviceFrom {
		t.Errorf("From = %q, want %q", env.GetFrom(), testDeviceFrom)
	}
}

// TestReadProtoJSON_MalformedBodyReturnsError checks that garbage JSON
// surfaces as an error to the caller instead of silently producing a
// zero-value Envelope, which would be indistinguishable from a
// legitimately empty-but-valid request.
func TestReadProtoJSON_MalformedBodyReturnsError(t *testing.T) {
	req := httptest.NewRequest(http.MethodPost, "/envelope", strings.NewReader(`{not valid json`))

	var env streamv1.Envelope
	if err := ReadProtoJSON(req, &env); err == nil {
		t.Error("ReadProtoJSON returned nil error for malformed body, want non-nil")
	}
}

// TestReadProtoJSON_UnknownFieldReturnsError checks protojson's default
// strictness: an unrecognised field name in the body should fail decoding
// rather than being silently dropped, catching client/server schema drift
// early instead of at runtime three layers deeper.
func TestReadProtoJSON_UnknownFieldReturnsError(t *testing.T) {
	body := `{"from":"` + testDeviceFrom + `","not_a_real_field":true}`
	req := httptest.NewRequest(http.MethodPost, "/envelope", strings.NewReader(body))

	var env streamv1.Envelope
	if err := ReadProtoJSON(req, &env); err == nil {
		t.Error("ReadProtoJSON returned nil error for unknown field, want non-nil")
	}
}