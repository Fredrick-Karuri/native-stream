// packages/proxy/ports.go
//
// Local interface proxy depends on instead of a concrete server type.

package proxy

// ActiveLinkSource is the subset of channel data proxy needs: given a
// channel ID, the currently active stream link (if any). Satisfied by an
// adapter over server/store.Store.
type ActiveLinkSource interface {
	ActiveLink(channelID string) *ActiveLink
}

// ActiveLink is the minimal shape proxy needs — a subset of
// store.LinkScore's fields, expressed independently so this package never
// imports server/store.
type ActiveLink struct {
	URL     string
	Headers map[string]string
}