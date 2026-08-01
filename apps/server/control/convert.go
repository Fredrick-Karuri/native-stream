// server/control/convert.go
//
// Converts between the control package's internal SessionInfo/DeviceKind
// and their generated proto equivalents. Duplicates api/convert.go's
// toProtoDeviceKind/toProtoSessionInfo — control can't import api (api
// already imports control), so a shared package would need to become a
// new import for both; not worth introducing for two small functions.
// If these drift, that's the tell it's time to extract one.

package control

import (
	streamv1 "github.com/fredrick-karuri/nativestream/sdk-gen/go/stream/v1"
	"google.golang.org/protobuf/types/known/timestamppb"
)

// ProtoKindToControlKind converts an inbound proto DeviceKind (e.g. from
// a register payload) to the internal DeviceKind used by SessionInfo.
func ProtoKindToControlKind(kind streamv1.DeviceKind) DeviceKind {
	switch kind {
	case streamv1.DeviceKind_DEVICE_KIND_CONTROLLER:
		return KindController
	case streamv1.DeviceKind_DEVICE_KIND_TARGET:
		return KindTarget
	case streamv1.DeviceKind_DEVICE_KIND_TV:
		return KindTV
	default:
		return ""
	}
}

func controlKindToProtoKind(kind DeviceKind) streamv1.DeviceKind {
	switch kind {
	case KindController:
		return streamv1.DeviceKind_DEVICE_KIND_CONTROLLER
	case KindTarget:
		return streamv1.DeviceKind_DEVICE_KIND_TARGET
	case KindTV:
		return streamv1.DeviceKind_DEVICE_KIND_TV
	default:
		return streamv1.DeviceKind_DEVICE_KIND_UNSPECIFIED
	}
}

func toProtoSessionInfo(s SessionInfo) *streamv1.SessionInfo {
	return &streamv1.SessionInfo{
		DeviceId:    s.DeviceID,
		Name:        s.Name,
		Kind:        controlKindToProtoKind(s.Kind),
		ChannelId:   s.ChannelID,
		ChannelName: s.ChannelName,
		StreamUrl:   s.StreamURL,
		Playing:     s.Playing,
		Volume:      s.Volume,
		ConnectedAt: timestamppb.New(s.ConnectedAt),
	}
}