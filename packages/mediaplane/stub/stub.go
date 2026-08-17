// packages/mediaplane/stub/stub.go
//
// Stub media plane implementation (CPMP-007). Proves the CPMP-005/006
// interface boundary is real: this satisfies mediaplane.StreamProxy and
// mediaplane.DiscoveryProvider with a hardcoded channel list and no
// dependency on the control plane, packages/proxy, or packages/discovery.
// Swapping this in for Fredrick's implementation requires no changes to
// api/ or cmd/main.go beyond configuration.
//
// Not production media plane logic — deliberately the smallest thing that
// compiles against the interface.
package stub

import (
	"context"
	"encoding/json"
	"net/http"
	"sync/atomic"

	"github.com/fredrick-karuri/nativestream/packages/mediaplane"
)

// hardcodedChannel is a single stubbed entry, returned regardless of the
// incoming ChannelQuery. Enough to prove the interface is satisfiable, not
// meant to imply real matching logic.
var hardcodedChannel = mediaplane.PlayableLink{
	URL:            "https://example.invalid/stub-stream.m3u8",
	SourceURL:      "stub://hardcoded",
	Headers:        map[string]string{},
	Score:          1.0,
	LatencyMS:      0,
	EstBitrateKbps: 0,
}

// StreamProxy is a minimal mediaplane.StreamProxy. It does not proxy
// anything real — it responds to every request with a fixed body,
// confirming only that requests reach an independent implementation.
type StreamProxy struct {
	enabled atomic.Bool
}

// NewStreamProxy returns a stub proxy, enabled by default.
func NewStreamProxy() *StreamProxy {
	sp := &StreamProxy{}
	sp.enabled.Store(true)
	return sp
}

func (sp *StreamProxy) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	if !sp.enabled.Load() {
		http.Error(w, "stub proxy disabled", http.StatusServiceUnavailable)
		return
	}
	w.Header().Set("Content-Type", "text/plain; charset=utf-8")
	_, _ = w.Write([]byte("stub media plane: no real proxying implemented\n"))
}

func (sp *StreamProxy) SetEnabled(enabled bool) {
	sp.enabled.Store(enabled)
}

func (sp *StreamProxy) IsEnabled() bool {
	return sp.enabled.Load()
}

// PerformsSSRFFiltering is false: the stub never makes an outbound request
// on anyone's behalf (ServeHTTP never dials out), so there's nothing to
// filter. Declaring true here would be inaccurate, not conservative.
func (sp *StreamProxy) PerformsSSRFFiltering() bool {
	return false
}

var _ mediaplane.StreamProxy = (*StreamProxy)(nil)

// DiscoveryProvider is a minimal mediaplane.DiscoveryProvider. FindLinks
// always returns the same hardcoded channel, ignoring the query — real
// matching is explicitly out of scope for this ticket.
type DiscoveryProvider struct{}

// NewDiscoveryProvider returns a stub discovery provider.
func NewDiscoveryProvider() *DiscoveryProvider {
	return &DiscoveryProvider{}
}

func (dp *DiscoveryProvider) FindLinks(ctx context.Context, query mediaplane.ChannelQuery) ([]mediaplane.PlayableLink, error) {
	return []mediaplane.PlayableLink{hardcodedChannel}, nil
}

// PerformsSSRFFiltering is false for the same reason as StreamProxy's:
// FindLinks never fetches query.ChannelID or query.Name from anywhere, so
// there's no outbound request to filter.
func (dp *DiscoveryProvider) PerformsSSRFFiltering() bool {
	return false
}

var _ mediaplane.DiscoveryProvider = (*DiscoveryProvider)(nil)

// MarshalHardcodedChannel is a small debug helper so a self-run instance
// using this stub can inspect what it returns without any control-plane
// tooling — proves the implementation is independently runnable.
func MarshalHardcodedChannel() ([]byte, error) {
	return json.MarshalIndent(hardcodedChannel, "", "  ")
}