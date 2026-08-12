// api/convert_test.go
//
// convert.go is pure domain-to-protobuf mapping: struct in, struct out, no
// I/O. The field-copy paths (Name -> Name, etc.) are the least interesting
// part of this file — a typo there would be caught by literally any
// caller. Most of the coverage below targets the parts that actually
// branch: nil ActiveLink handling, the FailureReasonNone sentinel check,
// the exact healthyScoreThreshold boundary (>= means equal-to-threshold
// counts as healthy), and the two saturating int conversions at their
// clamp points (verified against intsafe.go's actual behavior, not
// assumed).

package api

import (
	"math"
	"testing"
	"time"

	streamv1 "github.com/fredrick-karuri/nativestream/sdk-gen/go/stream/v1"
	"github.com/fredrick-karuri/nativestream/server/control"
	"github.com/fredrick-karuri/nativestream/server/store"
)

// ── toProtoDeviceKind ────────────────────────────────────────────────────

func TestToProtoDeviceKind(t *testing.T) {
	tests := []struct {
		name string
		in   control.DeviceKind
		want streamv1.DeviceKind
	}{
		{"controller", control.KindController, streamv1.DeviceKind_DEVICE_KIND_CONTROLLER},
		{"target", control.KindTarget, streamv1.DeviceKind_DEVICE_KIND_TARGET},
		{"tv", control.KindTV, streamv1.DeviceKind_DEVICE_KIND_TV},
		{"unknown kind falls back to unspecified", control.DeviceKind("something-unrecognized"), streamv1.DeviceKind_DEVICE_KIND_UNSPECIFIED},
		{"zero value falls back to unspecified", control.DeviceKind(""), streamv1.DeviceKind_DEVICE_KIND_UNSPECIFIED},
	}

	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			if got := toProtoDeviceKind(tc.in); got != tc.want {
				t.Errorf("toProtoDeviceKind(%v) = %v, want %v", tc.in, got, tc.want)
			}
		})
	}
}

// ── toProtoSessionInfo ───────────────────────────────────────────────────

func TestToProtoSessionInfo_MapsAllFields(t *testing.T) {
	connectedAt := time.Date(2026, 8, 12, 10, 0, 0, 0, time.UTC)
	session := control.SessionInfo{
		DeviceID:    "dev-1",
		Name:        "Living Room TV",
		Kind:        control.KindTV,
		ChannelID:   "chan-1",
		ChannelName: "ESPN",
		StreamURL:   "https://cdn.example.com/live.m3u8",
		Playing:     true,
		Volume:      75,
		ConnectedAt: connectedAt,
	}

	got := toProtoSessionInfo(session)

	if got.DeviceId != "dev-1" {
		t.Errorf("DeviceId = %q, want %q", got.DeviceId, "dev-1")
	}
	if got.Name != "Living Room TV" {
		t.Errorf("Name = %q, want %q", got.Name, "Living Room TV")
	}
	if got.Kind != streamv1.DeviceKind_DEVICE_KIND_TV {
		t.Errorf("Kind = %v, want DEVICE_KIND_TV", got.Kind)
	}
	if got.ChannelId != "chan-1" {
		t.Errorf("ChannelId = %q, want %q", got.ChannelId, "chan-1")
	}
	if got.ChannelName != "ESPN" {
		t.Errorf("ChannelName = %q, want %q", got.ChannelName, "ESPN")
	}
	if got.StreamUrl != "https://cdn.example.com/live.m3u8" {
		t.Errorf("StreamUrl = %q, want %q", got.StreamUrl, "https://cdn.example.com/live.m3u8")
	}
	if !got.Playing {
		t.Error("Playing = false, want true")
	}
	if got.Volume != 75 {
		t.Errorf("Volume = %v, want 75", got.Volume)
	}
	if !got.ConnectedAt.AsTime().Equal(connectedAt) {
		t.Errorf("ConnectedAt = %v, want %v", got.ConnectedAt.AsTime(), connectedAt)
	}
}

func TestToProtoSessionInfo_ZeroValueSessionDoesNotPanic(t *testing.T) {
	got := toProtoSessionInfo(control.SessionInfo{})

	if got == nil {
		t.Fatal("toProtoSessionInfo(zero value) = nil, want non-nil result")
	}
	if got.Kind != streamv1.DeviceKind_DEVICE_KIND_UNSPECIFIED {
		t.Errorf("Kind = %v, want DEVICE_KIND_UNSPECIFIED for zero-value Kind", got.Kind)
	}
}

// ── toProtoChannelResponse ───────────────────────────────────────────────

func TestToProtoChannelResponse_MapsSummaryFields(t *testing.T) {
	ch := &store.Channel{
		ID:         "chan-1",
		Name:       "ESPN",
		GroupTitle: "Sports",
		TvgID:      "espn.us",
		LogoURL:    "https://example.com/logo.png",
		Candidates: []*store.LinkScore{{URL: "a"}, {URL: "b"}, {URL: "c"}},
	}

	got := toProtoChannelResponse(ch)

	if got.Id != "chan-1" || got.Name != "ESPN" || got.GroupTitle != "Sports" || got.TvgId != "espn.us" || got.LogoUrl != "https://example.com/logo.png" {
		t.Errorf("summary fields mismatch, got %+v", got)
	}
	if got.CandidateCount != 3 {
		t.Errorf("CandidateCount = %d, want 3", got.CandidateCount)
	}
}

func TestToProtoChannelResponse_NilActiveLink_HasActiveLinkFalseAndScoreFieldsZero(t *testing.T) {
	ch := &store.Channel{ID: "chan-1", ActiveLink: nil}

	got := toProtoChannelResponse(ch)

	if got.HasActiveLink {
		t.Error("HasActiveLink = true, want false when ActiveLink is nil")
	}
	if got.Healthy {
		t.Error("Healthy = true, want false when there is no active link to evaluate")
	}
	if got.ActiveScore != 0 {
		t.Errorf("ActiveScore = %v, want 0 when ActiveLink is nil (untouched zero value)", got.ActiveScore)
	}
}

func TestToProtoChannelResponse_NonNilActiveLink_SetsHasActiveLinkTrue(t *testing.T) {
	ch := &store.Channel{ID: "chan-1", ActiveLink: &store.LinkScore{Score: 0.7}}

	got := toProtoChannelResponse(ch)

	if !got.HasActiveLink {
		t.Error("HasActiveLink = false, want true when ActiveLink is non-nil")
	}
	if got.ActiveScore != 0.7 {
		t.Errorf("ActiveScore = %v, want 0.7", got.ActiveScore)
	}
}

func TestToProtoChannelResponse_HealthyThreshold(t *testing.T) {
	// healthyScoreThreshold is 0.3, checked with >=. The boundary case
	// (score exactly equal to the threshold) is the one worth pinning —
	// off-by-one-direction bugs (> vs >=) live exactly there.
	tests := []struct {
		name  string
		score float64
		want  bool
	}{
		{"just below threshold", 0.29999, false},
		{"exactly at threshold", 0.3, true},
		{"just above threshold", 0.30001, true},
		{"well below threshold", 0.0, false},
		{"well above threshold", 1.0, true},
	}

	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			ch := &store.Channel{ID: "chan-1", ActiveLink: &store.LinkScore{Score: tc.score}}
			got := toProtoChannelResponse(ch)
			if got.Healthy != tc.want {
				t.Errorf("Healthy for score %v = %v, want %v", tc.score, got.Healthy, tc.want)
			}
		})
	}
}

func TestToProtoChannelResponse_ZeroCandidatesGivesZeroCount(t *testing.T) {
	ch := &store.Channel{ID: "chan-1", Candidates: nil}

	got := toProtoChannelResponse(ch)

	if got.CandidateCount != 0 {
		t.Errorf("CandidateCount = %d, want 0 for nil Candidates slice", got.CandidateCount)
	}
}

// ── toProtoLinkScore ─────────────────────────────────────────────────────

func TestToProtoLinkScore_MapsFieldsAndOmitsFailureReasonWhenNone(t *testing.T) {
	link := &store.LinkScore{
		URL:           "https://cdn.example.com/a.m3u8",
		Score:         0.85,
		LatencyMS:     120,
		State:         store.StateActive,
		FailCount:     2,
		Headers:       map[string]string{"X-Auth": "token"},
		FailureReason: store.FailureReasonNone,
	}

	got := toProtoLinkScore(link)

	if got.Url != "https://cdn.example.com/a.m3u8" {
		t.Errorf("Url = %q, want %q", got.Url, "https://cdn.example.com/a.m3u8")
	}
	if got.Score != 0.85 {
		t.Errorf("Score = %v, want 0.85", got.Score)
	}
	if got.LatencyMs != 120 {
		t.Errorf("LatencyMs = %v, want 120", got.LatencyMs)
	}
	if got.State != string(store.StateActive) {
		t.Errorf("State = %q, want %q", got.State, store.StateActive)
	}
	if got.FailCount != 2 {
		t.Errorf("FailCount = %v, want 2", got.FailCount)
	}
	if got.Headers["X-Auth"] != "token" {
		t.Errorf("Headers[X-Auth] = %q, want %q", got.Headers["X-Auth"], "token")
	}
	if got.FailureReason != nil {
		t.Errorf("FailureReason = %v, want nil (omitted) when store.FailureReasonNone", got.FailureReason)
	}
}

func TestToProtoLinkScore_SetsFailureReasonWhenPresent(t *testing.T) {
	link := &store.LinkScore{
		URL:           "https://cdn.example.com/a.m3u8",
		FailureReason: store.FailureReasonTimeout,
	}

	got := toProtoLinkScore(link)

	if got.FailureReason == nil {
		t.Fatal("FailureReason = nil, want set pointer when FailureReason is not FailureReasonNone")
	}
	if *got.FailureReason != string(store.FailureReasonTimeout) {
		t.Errorf("*FailureReason = %q, want %q", *got.FailureReason, store.FailureReasonTimeout)
	}
}

func TestToProtoLinkScore_AllFailureReasonVariants(t *testing.T) {
	// Every named FailureReason except the None sentinel should round-trip
	// as a non-nil pointer with the matching string value.
	reasons := []store.FailureReason{
		store.FailureReasonForbidden,
		store.FailureReasonUnreachable,
		store.FailureReasonTimeout,
		store.FailureReasonBadContent,
	}

	for _, reason := range reasons {
		t.Run(string(reason), func(t *testing.T) {
			link := &store.LinkScore{FailureReason: reason}
			got := toProtoLinkScore(link)
			if got.FailureReason == nil || *got.FailureReason != string(reason) {
				t.Errorf("FailureReason for %q = %v, want pointer to %q", reason, got.FailureReason, reason)
			}
		})
	}
}

func TestToProtoLinkScore_LatencyMsSaturatesOnInt64Overflow(t *testing.T) {
	// FromInt64ToInt32 saturates rather than wraps (verified against
	// intsafe.go directly, not assumed) — a latency value beyond int32
	// range should clamp to math.MaxInt32, not silently truncate/wrap to
	// a small or negative number.
	link := &store.LinkScore{LatencyMS: math.MaxInt64}

	got := toProtoLinkScore(link)

	if got.LatencyMs != math.MaxInt32 {
		t.Errorf("LatencyMs = %d, want saturated %d for LatencyMS = math.MaxInt64", got.LatencyMs, int32(math.MaxInt32))
	}
}

func TestToProtoLinkScore_LatencyMsSaturatesOnNegativeInt64Overflow(t *testing.T) {
	link := &store.LinkScore{LatencyMS: math.MinInt64}

	got := toProtoLinkScore(link)

	if got.LatencyMs != math.MinInt32 {
		t.Errorf("LatencyMs = %d, want saturated %d for LatencyMS = math.MinInt64", got.LatencyMs, int32(math.MinInt32))
	}
}

func TestToProtoLinkScore_LatencyMsPassesThroughInRangeValueUnchanged(t *testing.T) {
	link := &store.LinkScore{LatencyMS: 42}

	got := toProtoLinkScore(link)

	if got.LatencyMs != 42 {
		t.Errorf("LatencyMs = %d, want 42 (no saturation for in-range values)", got.LatencyMs)
	}
}

// ── toProtoChannelDetail ─────────────────────────────────────────────────

func TestToProtoChannelDetail_MapsFieldsAndKeywords(t *testing.T) {
	ch := &store.Channel{
		ID:         "chan-1",
		Name:       "ESPN",
		GroupTitle: "Sports",
		TvgID:      "espn.us",
		LogoURL:    "https://example.com/logo.png",
		Keywords:   []string{"espn", "sports"},
	}

	got := toProtoChannelDetail(ch)

	if got.Id != "chan-1" || got.Name != "ESPN" || got.GroupTitle != "Sports" || got.TvgId != "espn.us" || got.LogoUrl != "https://example.com/logo.png" {
		t.Errorf("summary fields mismatch, got %+v", got)
	}
	if len(got.Keywords) != 2 || got.Keywords[0] != "espn" || got.Keywords[1] != "sports" {
		t.Errorf("Keywords = %v, want [espn sports]", got.Keywords)
	}
}

func TestToProtoChannelDetail_NilActiveLinkLeavesActiveLinkNil(t *testing.T) {
	ch := &store.Channel{ID: "chan-1", ActiveLink: nil}

	got := toProtoChannelDetail(ch)

	if got.ActiveLink != nil {
		t.Errorf("ActiveLink = %+v, want nil when store.Channel.ActiveLink is nil", got.ActiveLink)
	}
}

func TestToProtoChannelDetail_NonNilActiveLinkIsConverted(t *testing.T) {
	ch := &store.Channel{
		ID:         "chan-1",
		ActiveLink: &store.LinkScore{URL: "https://cdn.example.com/active.m3u8", Score: 0.9},
	}

	got := toProtoChannelDetail(ch)

	if got.ActiveLink == nil {
		t.Fatal("ActiveLink = nil, want converted LinkScoreResponse")
	}
	if got.ActiveLink.Url != "https://cdn.example.com/active.m3u8" {
		t.Errorf("ActiveLink.Url = %q, want %q", got.ActiveLink.Url, "https://cdn.example.com/active.m3u8")
	}
}

func TestToProtoChannelDetail_ConvertsAllCandidatesInOrder(t *testing.T) {
	ch := &store.Channel{
		ID: "chan-1",
		Candidates: []*store.LinkScore{
			{URL: "https://cdn.example.com/c1.m3u8"},
			{URL: "https://cdn.example.com/c2.m3u8"},
			{URL: "https://cdn.example.com/c3.m3u8"},
		},
	}

	got := toProtoChannelDetail(ch)

	if len(got.Candidates) != 3 {
		t.Fatalf("len(Candidates) = %d, want 3", len(got.Candidates))
	}
	for i, wantURL := range []string{
		"https://cdn.example.com/c1.m3u8",
		"https://cdn.example.com/c2.m3u8",
		"https://cdn.example.com/c3.m3u8",
	} {
		if got.Candidates[i].Url != wantURL {
			t.Errorf("Candidates[%d].Url = %q, want %q", i, got.Candidates[i].Url, wantURL)
		}
	}
}

func TestToProtoChannelDetail_EmptyCandidatesGivesEmptySlice(t *testing.T) {
	ch := &store.Channel{ID: "chan-1", Candidates: nil}

	got := toProtoChannelDetail(ch)

	if len(got.Candidates) != 0 {
		t.Errorf("len(Candidates) = %d, want 0 for nil input Candidates", len(got.Candidates))
	}
}
