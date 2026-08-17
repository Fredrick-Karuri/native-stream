// packages/discovery/ports.go
//
// Local interfaces discovery depends on instead of concrete server types.
// packages/discovery must not import server/store or server/validator
// — the server supplies adapters over its real
// store.Store and validator.Validator that satisfy these at construction
// time (see cmd/main.go). This is the same pattern packages/mediaplane
// uses for the control-plane/media-plane boundary, applied one level
// deeper: discovery doesn't need the whole store, just channel lookup.
package discovery

// ChannelLookup is the subset of channel data the matcher needs: read
// existing channels to match against, and register new ones for
// auto-registered direct candidates. Satisfied by an adapter over
// server/store.Store.
type ChannelLookup interface {
	All() []LookupChannel
	Get(id string) *LookupChannel
	Add(ch LookupChannel)
}

// LookupChannel is the minimal channel shape discovery needs — a subset
// of store.Channel's fields, expressed independently so this package
// never imports server/store.
type LookupChannel struct {
	ID         string
	Name       string
	GroupTitle string
	TvgID      string
	LogoURL    string
	Keywords   []string
}

// CandidateSubmitter accepts a matched, ready-to-score candidate. Satisfied
// by an adapter over server/validator.Validator.
type CandidateSubmitter interface {
	Submit(c SubmittedCandidate)
}

// SubmittedCandidate is what discovery hands off after matching — mirrors
// validator.Candidate's fields without importing server/validator.
type SubmittedCandidate struct {
	URL       string
	ChannelID string
	SourceURL string
	Headers   map[string]string
}