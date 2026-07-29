/*
api/convert.go

Converts internal Go domain types (control.SessionInfo, control.DeviceKind)
into their generated protobuf equivalents. Kept separate from handlers.go
so HTTP request/response wiring stays free of type-mapping detail.
*/
package api

import (
	streamv1 "github.com/fredrick-karuri/nativestream/sdk-gen/go/stream/v1"
	"github.com/fredrick-karuri/nativestream/server/control"
	"google.golang.org/protobuf/types/known/timestamppb"
)

func toProtoDeviceKind(kind control.DeviceKind) streamv1.DeviceKind {
	switch kind {
	case control.KindController:
		return streamv1.DeviceKind_DEVICE_KIND_CONTROLLER
	case control.KindTarget:
		return streamv1.DeviceKind_DEVICE_KIND_TARGET
	case control.KindTV:
		return streamv1.DeviceKind_DEVICE_KIND_TV
	default:
		return streamv1.DeviceKind_DEVICE_KIND_UNSPECIFIED
	}
}

func toProtoSessionInfo(session control.SessionInfo) *streamv1.SessionInfo {
	return &streamv1.SessionInfo{
		DeviceId:    session.DeviceID,
		Name:        session.Name,
		Kind:        toProtoDeviceKind(session.Kind),
		ChannelId:   session.ChannelID,
		ChannelName: session.ChannelName,
		StreamUrl:   session.StreamURL,
		Playing:     session.Playing,
		Volume:      session.Volume,
		ConnectedAt: timestamppb.New(session.ConnectedAt),
	}
}