// server/proxy/adapters.go
//
// Adapter over the control plane's real store.Store, satisfying
// packages/proxy's ActiveLinkSource interface (see
// packages/proxy/ports.go). packages/proxy never imports server/store
// directly — the server wires this adapter in at construction, in
// cmd/main.go.
package proxy

import (
	"github.com/fredrick-karuri/nativestream/packages/proxy"
	"github.com/fredrick-karuri/nativestream/server/store"
)

// StoreActiveLinkSource adapts *store.Store to proxy.ActiveLinkSource.
type StoreActiveLinkSource struct {
	Store *store.Store
}

func NewStoreActiveLinkSource(s *store.Store) *StoreActiveLinkSource {
	return &StoreActiveLinkSource{Store: s}
}

func (a *StoreActiveLinkSource) ActiveLink(channelID string) *proxy.ActiveLink {
	ch := a.Store.Get(channelID)
	if ch == nil || ch.ActiveLink == nil {
		return nil
	}
	return &proxy.ActiveLink{
		URL:     ch.ActiveLink.URL,
		Headers: ch.ActiveLink.Headers,
	}
}
