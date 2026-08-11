// control/hub_test.go
//
// applyStateUpdate and handlePong mutate Hub state directly and never
// call Client.Send, so they're testable without a real websocket
// connection or the Run() event loop. We register a Client directly
// into hub.clients (legal — same package, unexported field visible)
// rather than going through the register channel, to isolate the
// mutation logic from the concurrency plumbing.

package control

import (
	"testing"

	streamv1 "github.com/fredrick-karuri/nativestream/sdk-gen/go/stream/v1"
)

func newHubWithClient(deviceID string) (*Hub, *Client) {
	h := &Hub{clients: make(map[string]*Client)}
	c := &Client{
		DeviceID: deviceID,
		Session:  SessionInfo{DeviceID: deviceID},
	}
	h.clients[deviceID] = c
	return h, c
}

func TestApplyStateUpdate_UpdatesRegisteredClientSession(t *testing.T) {
	h, c := newHubWithClient("device1")

	env, err := NewProtoEnvelope(streamv1.MessageType_MESSAGE_TYPE_STATE_UPDATE, "device1", "server", &streamv1.StateUpdatePayload{
		ChannelId:   "ch7",
		ChannelName: "Sports HD",
		StreamUrl:   "https://example.com/ch7.m3u8",
		Playing:     true,
		Volume:      0.8,
	})
	if err != nil {
		t.Fatalf("unexpected error building envelope: %v", err)
	}

	h.applyStateUpdate(env)

	if c.Session.ChannelID != "ch7" {
		t.Errorf("expected ChannelID ch7, got %q", c.Session.ChannelID)
	}
	if c.Session.ChannelName != "Sports HD" {
		t.Errorf("expected ChannelName 'Sports HD', got %q", c.Session.ChannelName)
	}
	if !c.Session.Playing {
		t.Error("expected Playing to be true")
	}
	if c.Session.Volume != 0.8 {
		t.Errorf("expected Volume 0.8, got %v", c.Session.Volume)
	}
}

func TestApplyStateUpdate_UnknownDevice_NoOp(t *testing.T) {
	// env.From doesn't match any registered client — this should not
	// panic or create a phantom entry, just silently skip.
	h, _ := newHubWithClient("device1")

	env, _ := NewProtoEnvelope(streamv1.MessageType_MESSAGE_TYPE_STATE_UPDATE, "unknown-device", "server", &streamv1.StateUpdatePayload{
		ChannelId: "ch7",
	})

	h.applyStateUpdate(env)

	if len(h.clients) != 1 {
		t.Errorf("expected client map to remain size 1, got %d", len(h.clients))
	}
}

func TestApplyStateUpdate_MalformedPayload_LeavesSessionUnchanged(t *testing.T) {
	h, c := newHubWithClient("device1")
	c.Session.ChannelID = "original-channel"

	env := &streamv1.Envelope{
		Type:        streamv1.MessageType_MESSAGE_TYPE_STATE_UPDATE,
		From:        "device1",
		PayloadJson: `{not valid json`,
	}

	h.applyStateUpdate(env)

	if c.Session.ChannelID != "original-channel" {
		t.Errorf("expected session to remain unchanged on decode failure, got %q", c.Session.ChannelID)
	}
}

func TestHandlePong_ResetsMissedPongCounter(t *testing.T) {
	h, c := newHubWithClient("device1")
	c.missedPongs = 2

	env := &streamv1.Envelope{From: "device1"}
	h.handlePong(env)

	if c.missedPongs != 0 {
		t.Errorf("expected missedPongs reset to 0, got %d", c.missedPongs)
	}
}

func TestHandlePong_UnknownDevice_NoOp(t *testing.T) {
	h, _ := newHubWithClient("device1")

	env := &streamv1.Envelope{From: "unknown-device"}
	h.handlePong(env) // must not panic

	if len(h.clients) != 1 {
		t.Errorf("expected client map to remain size 1, got %d", len(h.clients))
	}
}
