// server/control/protocol.go
//
// LMC message protocol — envelope construction/decoding helpers built on
// the generated streamv1.Envelope proto type. Replaces the old hand-written
// Envelope/payload structs and their encoding/json-based NewEnvelope and
// DecodePayload helpers.
//
// SessionInfo and DeviceKind remain here as internal Go types — they back
// Hub's session registry and are converted to/from their proto equivalents
// at the boundary (see convert.go), not used directly on the wire.

package control

import (
	streamv1 "github.com/fredrick-karuri/nativestream/sdk-gen/go/stream/v1"
	"google.golang.org/protobuf/encoding/protojson"
	"google.golang.org/protobuf/proto"
	"time"
)

// DeviceKind describes the role a device plays in the control network.
type DeviceKind string

const (
	KindController DeviceKind = "controller"
	KindTarget     DeviceKind = "target"
	KindTV         DeviceKind = "tv" // reserved for future TV client
)

// SessionInfo is the canonical internal representation of a connected
// device. Converted to *streamv1.SessionInfo at the wire boundary.
type SessionInfo struct {
	DeviceID    string
	Name        string
	Kind        DeviceKind
	ChannelID   string
	ChannelName string
	StreamURL   string
	Playing     bool
	Volume      float64
	ConnectedAt time.Time
}

var envelopeMarshaler = protojson.MarshalOptions{UseProtoNames: true}

// NewProtoEnvelope builds a streamv1.Envelope with an optional typed
// payload marshalled to its JSON string form. A nil payload produces
// an empty PayloadJson, matching messages that carry no data
// (stop, ping).
func NewProtoEnvelope(msgType streamv1.MessageType, from, to string, payload proto.Message) (*streamv1.Envelope, error) {
	env := &streamv1.Envelope{
		Type: msgType,
		From: from,
		To:   to,
	}
	if payload == nil {
		return env, nil
	}
	data, err := envelopeMarshaler.Marshal(payload)
	if err != nil {
		return nil, err
	}
	env.PayloadJson = string(data)
	return env, nil
}

// DecodeProtoPayload unmarshals a streamv1.Envelope's PayloadJson into
// the target proto message. Caller must pass a pre-constructed pointer,
// e.g. DecodeProtoPayload(env, &streamv1.RegisterPayload{}).
func DecodeProtoPayload[T proto.Message](env *streamv1.Envelope, out T) error {
	return protojson.Unmarshal([]byte(env.PayloadJson), out)
}