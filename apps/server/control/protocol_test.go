// control/protocol_test.go
//
// NewProtoEnvelope and DecodeProtoPayload are pure functions — encode a
// payload to JSON-in-a-string, decode it back. No Hub, no Client, no
// network involved.

package control

import (
	"testing"

	streamv1 "github.com/fredrick-karuri/nativestream/sdk-gen/go/stream/v1"
)

func TestNewProtoEnvelope_NilPayload_ProducesEmptyPayloadJSON(t *testing.T) {
	env, err := NewProtoEnvelope(streamv1.MessageType_MESSAGE_TYPE_PING, "server", "broadcast", nil)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if env.PayloadJson != "" {
		t.Errorf("expected empty PayloadJson for nil payload, got %q", env.PayloadJson)
	}
	if env.Type != streamv1.MessageType_MESSAGE_TYPE_PING {
		t.Errorf("expected type PING, got %v", env.Type)
	}
	if env.From != "server" || env.To != "broadcast" {
		t.Errorf("expected From=server To=broadcast, got From=%q To=%q", env.From, env.To)
	}
}

func TestNewProtoEnvelope_WithPayload_RoundTripsThroughDecode(t *testing.T) {
	original := &streamv1.PullBackAckPayload{
		ChannelId:   "ch42",
		ChannelName: "Test Channel",
		StreamUrl:   "https://example.com/stream.m3u8",
	}

	env, err := NewProtoEnvelope(streamv1.MessageType_MESSAGE_TYPE_PULL_BACK_ACK, "server", "device1", original)
	if err != nil {
		t.Fatalf("unexpected error building envelope: %v", err)
	}
	if env.PayloadJson == "" {
		t.Fatal("expected non-empty PayloadJson for a real payload")
	}

	var decoded streamv1.PullBackAckPayload
	if err := DecodeProtoPayload(env, &decoded); err != nil {
		t.Fatalf("unexpected error decoding: %v", err)
	}

	if decoded.ChannelId != original.ChannelId ||
		decoded.ChannelName != original.ChannelName ||
		decoded.StreamUrl != original.StreamUrl {
		t.Errorf("round-trip mismatch: got %+v, want fields matching %+v", decoded, original)
	}
}

func TestDecodeProtoPayload_MalformedJSON_ReturnsError(t *testing.T) {
	env := &streamv1.Envelope{
		Type:        streamv1.MessageType_MESSAGE_TYPE_STATE_UPDATE,
		PayloadJson: `{not valid json`,
	}

	var out streamv1.StateUpdatePayload
	if err := DecodeProtoPayload(env, &out); err == nil {
		t.Fatal("expected error decoding malformed JSON, got nil")
	}
}
