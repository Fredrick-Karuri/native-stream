// discovery/types.go
// Shared interfaces and types for the discovery engine.

package discovery

import (
	"context"
	"time"
)

// Crawler is implemented by each source (Gist, Reddit, Telegram, DirectM3U).
type Crawler interface {
	Name() string
	Fetch(ctx context.Context) ([]RawItem, error)
}

// RawItem is raw content returned by a crawler before link extraction.
type RawItem struct {
	SourceURL string
	Content   string
	Timestamp time.Time
}

// CandidateLink is a stream URL extracted from raw content, ready for validation.
type CandidateLink struct {
	URL            string
	ChannelID      string // populated after ChannelMatcher runs
	SourceURL      string
	ContextText    string // surrounding text used for channel matching
	Found          time.Time
	NeedsExpansion bool // true if URL is a .m3u that needs fetching and re-parsing
	Headers        map[string]string 
}

// State tracks per-source discovery state (last seen timestamps etc).
type SourceState struct {
	Name        string    `json:"name"`
	LastFetch   time.Time `json:"last_fetch"`
	LinksFound  int       `json:"links_found"`
	LastError   string    `json:"last_error,omitempty"`
	Suspended   bool      `json:"suspended"`
	SuspendedAt time.Time `json:"suspended_at,omitempty"`
}

// DirectCandidate is a fully-resolved stream candidate produced by a
// DirectFetcher. It bypasses LinkExtractor since the URL and metadata
// are already known.
type DirectCandidate struct {
	URL         string
	ChannelName string
	GroupTitle  string
	TvgID       string
	LogoURL     string
	Headers     map[string]string
	ChannelID   string // populated after ChannelMatcher runs
	SourceURL   string
}

// DirectFetcher produces pre-resolved candidates that skip extraction.
// Implement this instead of Crawler when your source already knows the
// final stream URL and metadata (e.g. a local sidecar script).
type DirectFetcher interface {
	Name() string
	FetchDirect(ctx context.Context) ([]DirectCandidate, error)
}