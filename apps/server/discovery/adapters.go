// Adapters over the control plane's real store.Store and
// validator.Validator, satisfying packages/discovery's ChannelLookup and
// CandidateSubmitter interfaces (see packages/discovery/ports.go).
// packages/discovery never imports server/store or server/validator
// directly — the server wires these adapters in at construction, in
// cmd/main.go.
package discovery

import (
	"github.com/fredrick-karuri/nativestream/packages/discovery"
	"github.com/fredrick-karuri/nativestream/server/store"
	"github.com/fredrick-karuri/nativestream/server/validator"
)

// StoreChannelLookup adapts *store.Store to discovery.ChannelLookup.
type StoreChannelLookup struct {
	Store *store.Store
}

func NewStoreChannelLookup(s *store.Store) *StoreChannelLookup {
	return &StoreChannelLookup{Store: s}
}

func (a *StoreChannelLookup) All() []discovery.LookupChannel {
	channels := a.Store.All()
	out := make([]discovery.LookupChannel, len(channels))
	for i, ch := range channels {
		out[i] = toLookupChannel(ch)
	}
	return out
}

func (a *StoreChannelLookup) Get(id string) *discovery.LookupChannel {
	ch := a.Store.Get(id)
	if ch == nil {
		return nil
	}
	lc := toLookupChannel(ch)
	return &lc
}

func (a *StoreChannelLookup) Add(lc discovery.LookupChannel) {
	a.Store.Add(&store.Channel{
		ID:         lc.ID,
		Name:       lc.Name,
		GroupTitle: lc.GroupTitle,
		TvgID:      lc.TvgID,
		LogoURL:    lc.LogoURL,
		Keywords:   lc.Keywords,
	})
}

func toLookupChannel(ch *store.Channel) discovery.LookupChannel {
	return discovery.LookupChannel{
		ID:         ch.ID,
		Name:       ch.Name,
		GroupTitle: ch.GroupTitle,
		TvgID:      ch.TvgID,
		LogoURL:    ch.LogoURL,
		Keywords:   ch.Keywords,
	}
}

// ValidatorSubmitter adapts *validator.Validator to discovery.CandidateSubmitter.
type ValidatorSubmitter struct {
	Validator *validator.Validator
}

func NewValidatorSubmitter(v *validator.Validator) *ValidatorSubmitter {
	return &ValidatorSubmitter{Validator: v}
}

func (a *ValidatorSubmitter) Submit(c discovery.SubmittedCandidate) {
	a.Validator.Submit(validator.Candidate{
		URL:       c.URL,
		ChannelID: c.ChannelID,
		SourceURL: c.SourceURL,
		Headers:   c.Headers,
	})
}
