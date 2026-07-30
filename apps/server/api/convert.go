/*
api/convert.go

Converts internal Go domain types (control.SessionInfo, control.DeviceKind,
store.Channel, store.LinkScore) into their generated protobuf equivalents.
Kept separate from handlers.go so HTTP request/response wiring stays free
of type-mapping detail.
*/
package api

import (
	streamv1 "github.com/fredrick-karuri/nativestream/sdk-gen/go/stream/v1"
	"github.com/fredrick-karuri/nativestream/server/control"
	"github.com/fredrick-karuri/nativestream/server/store"
	"google.golang.org/protobuf/proto"
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

// healthyScoreThreshold matches the threshold already hardcoded in the
// pre-migration handler. store.Store doesn't expose its own
// minScoreHealthy, so this stays a local constant until that's addressed.
const healthyScoreThreshold = 0.3

// toProtoChannelResponse builds the list-view shape: summary fields plus
// derived healthy/active_score/candidate_count, matching the pre-migration
// inline row struct in handleListChannels.
func toProtoChannelResponse(ch *store.Channel) *streamv1.ChannelResponse {
	resp := &streamv1.ChannelResponse{
		Id:             ch.ID,
		Name:           ch.Name,
		GroupTitle:     ch.GroupTitle,
		TvgId:          ch.TvgID,
		LogoUrl:        ch.LogoURL,
		CandidateCount: int32(len(ch.Candidates)),
		HasActiveLink:  ch.ActiveLink != nil,
	}
	if ch.ActiveLink != nil {
		resp.ActiveScore = ch.ActiveLink.Score
		resp.Healthy = ch.ActiveLink.Score >= healthyScoreThreshold
	}
	return resp
}

func toProtoLinkScore(link *store.LinkScore) *streamv1.LinkScoreResponse {
	resp := &streamv1.LinkScoreResponse{
		Url:       link.URL,
		Score:     link.Score,
		LatencyMs: int32(link.LatencyMS),
		State:     string(link.State),
		FailCount: int32(link.FailCount),
		Headers:   link.Headers,
	}
	if link.FailureReason != store.FailureReasonNone {
		resp.FailureReason = proto.String(string(link.FailureReason))
	}
	return resp
}

func toProtoChannelDetail(ch *store.Channel) *streamv1.ChannelDetailResponse {
	resp := &streamv1.ChannelDetailResponse{
		Id:         ch.ID,
		Name:       ch.Name,
		GroupTitle: ch.GroupTitle,
		TvgId:      ch.TvgID,
		LogoUrl:    ch.LogoURL,
		Keywords:   ch.Keywords,
	}
	if ch.ActiveLink != nil {
		resp.ActiveLink = toProtoLinkScore(ch.ActiveLink)
	}
	for _, c := range ch.Candidates {
		resp.Candidates = append(resp.Candidates, toProtoLinkScore(c))
	}
	return resp
}