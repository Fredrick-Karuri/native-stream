// server/control/protocol.go
//
// LMC message protocol — envelope definition and all message types.
// Every message across all clients (Android, macOS, TV future) uses
// this envelope. The auth field is present but nullable for zero-auth
// upgrade path.

package control

import (
	"encoding/json"
	"time"
)

// MessageType identifies the intent of an Envelope.
type MessageType string

const (
	MsgRegister    MessageType = "register"
	MsgSessionList MessageType = "session_list"
	MsgPlay        MessageType = "play"
	MsgStop        MessageType = "stop"
	MsgPullBack    MessageType = "pull_back"
	MsgPullBackAck MessageType = "pull_back_ack"
	MsgStateUpdate MessageType = "state_update"
	MsgPing        MessageType = "ping"
	MsgPong        MessageType = "pong"
)

// DeviceKind describes the role a device plays in the control network.
type DeviceKind string

const (
	KindController DeviceKind = "controller"
	KindTarget     DeviceKind = "target"
	KindTV         DeviceKind = "tv" // reserved for future TV client
)

// Envelope is the top-level wrapper for every LMC message.
// To: device UUID for unicast, "broadcast" for all, "server" for hub-only.
// Auth: nil in zero-auth mode; populated string for future token auth.
type Envelope struct {
	Type    MessageType     `json:"type"`
	From    string          `json:"from"`
	To      string          `json:"to"`
	Auth    *string         `json:"auth"`
	Payload json.RawMessage `json:"payload"`
}

// SessionInfo is the canonical representation of a connected device.
// Broadcast to all clients on any session change.
type SessionInfo struct {
	DeviceID    string     `json:"device_id"`
	Name        string     `json:"name"`
	Kind        DeviceKind `json:"kind"`
	ChannelID   string     `json:"channel_id"`
	StreamURL   string     `json:"stream_url"`
	Playing     bool       `json:"playing"`
	ConnectedAt time.Time  `json:"connected_at"`
}

// ── Payload types ─────────────────────────────────────────────────────────────

// RegisterPayload is sent by a device immediately after connecting.
type RegisterPayload struct {
	Name string     `json:"name"`
	Kind DeviceKind `json:"kind"`
}

// PlayPayload instructs a target to begin playback.
type PlayPayload struct {
	ChannelID   string `json:"channel_id"`
	ChannelName string `json:"channel_name"`
	StreamURL   string `json:"stream_url"`
}

// PullBackPayload is sent by a controller to request the target's stream.
type PullBackPayload struct {
	FromDevice string `json:"from_device"`
}

// PullBackAckPayload is sent by the server to the requesting controller.
type PullBackAckPayload struct {
	ChannelID string `json:"channel_id"`
	StreamURL string `json:"stream_url"`
}

// StateUpdatePayload is broadcast by a target on every playback state change.
type StateUpdatePayload struct {
	ChannelID string `json:"channel_id"`
	StreamURL string `json:"stream_url"`
	Playing   bool   `json:"playing"`
}

// SessionListPayload wraps the current session list for broadcast.
type SessionListPayload struct {
	Sessions []SessionInfo `json:"sessions"`
}

// ── Envelope constructors ─────────────────────────────────────────────────────

// NewEnvelope builds an Envelope with a typed payload marshalled to RawMessage.
func NewEnvelope(msgType MessageType, from, to string, payload any) (Envelope, error) {
	raw, err := json.Marshal(payload)
	if err != nil {
		return Envelope{}, err
	}
	return Envelope{
		Type:    msgType,
		From:    from,
		To:      to,
		Auth:    nil,
		Payload: raw,
	}, nil
}

// DecodePayload unmarshals the Envelope payload into the target type.
func DecodePayload[T any](env Envelope) (T, error) {
	var v T
	err := json.Unmarshal(env.Payload, &v)
	return v, err
}