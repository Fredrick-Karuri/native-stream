// packages/mediaplane/mediaplane.go
//
// Media plane interface contract. Defines what the control
// plane needs FROM a media plane implementation, expressed as Go
// interfaces.
//

package mediaplane

import (
	"context"
	"net/http"
	"time"
)

// PlayableLink is a candidate stream URL with the metadata a media plane
// implementation can offer about it. Mirrors the fields the control plane
// already persists on store.LinkScore, but expressed independently of the
// store package so a media plane implementation has no import dependency
// on control-plane persistence types.
type PlayableLink struct {
	URL            string
	SourceURL      string
	Headers        map[string]string
	Score          float64
	LatencyMS      int64
	EstBitrateKbps int
}

// ChannelQuery describes what the control plane is asking discovery to
// find links for.
type ChannelQuery struct {
	ChannelID string
	Name      string
	Keywords  []string
}

// DiscoveryProvider is what the control plane needs from discovery: given
// a channel query, return playable links with health scores. Everything
// about *how* those links are found (crawlers, matcher, circuit breaker)
// is the implementation's business, not the control plane's.
type DiscoveryProvider interface {
	// FindLinks returns candidate playable links for the given channel
	// query. Implementations may return an empty slice if nothing is
	// found; a non-nil error indicates the provider itself failed
	// (network, parse, etc.), not "no results."
	FindLinks(ctx context.Context, query ChannelQuery) ([]PlayableLink, error)

	// PerformsSSRFFiltering reports whether this implementation validates
	// outbound URLs before fetching them (see SafetyDeclaration below).
	// The control plane does not re-implement SSRF checks itself — that
	// stays the media plane's job, since it protects the box making the
	// outbound request, which is the implementation's own infrastructure.
	// The control plane uses this declaration only to decide whether to
	// route to the implementation at all.
	PerformsSSRFFiltering() bool
}

// StreamProxy is what the control plane needs from proxying: given a
// stream ID, proxy it with the right headers. ServeHTTP is deliberately
// the same shape as http.Handler so an implementation can be mounted
// directly into the control plane's mux.
type StreamProxy interface {
	http.Handler

	// PerformsSSRFFiltering reports whether this implementation validates
	// upstream URLs before proxying to them. See DiscoveryProvider's
	// method of the same name — the declaration, not enforcement, is
	// what crosses the interface.
	PerformsSSRFFiltering() bool

	// SetEnabled and IsEnabled toggle whether the media plane implementation
	// actively proxies requests. This is control-plane-exposed operator
	// state (api/handlers.go's /api/proxy/config endpoint), not a safety
	// declaration — every implementation must support it since the control
	// plane's admin API assumes it exists.
	SetEnabled(enabled bool)
	IsEnabled() bool
}

// Match is a sourced schedule entry, with no channel assignment. Channel
// assignment is a control-plane concern (see package doc comment above)
// and happens after FindMatches returns, using the control plane's own
// store — never inside a media plane implementation.
type Match struct {
	ID          string
	HomeTeam    string
	AwayTeam    string
	Competition string
	Sport       string
	KickOff     time.Time
	Duration    time.Duration
}

// MatchProvider is what the control plane needs from EPG sourcing: given
// a lookahead window, return upcoming matches. Caching, XMLTV generation,
// and channel assignment all stay server-side.
type MatchProvider interface {
	// FindMatches returns matches with a kickoff within the given
	// lookahead window of now.
	FindMatches(ctx context.Context, lookahead time.Duration) ([]Match, error)
}

// SafetyDeclaration is satisfied by any interface in this package that
// exposes PerformsSSRFFiltering. The control plane can type-assert to
// this to gate routing decisions without depending on which specific
// interface (DiscoveryProvider vs StreamProxy) it's holding.
type SafetyDeclaration interface {
	PerformsSSRFFiltering() bool
}