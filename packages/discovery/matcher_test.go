// discovery/matcher_test.go
package discovery

import (
	"testing"
)

// fakeChannelLookup is a minimal in-package ChannelLookup for testing
type fakeChannelLookup struct {
	channels map[string]LookupChannel
}

func newFakeChannelLookup() *fakeChannelLookup {
	return &fakeChannelLookup{channels: make(map[string]LookupChannel)}
}

func (f *fakeChannelLookup) All() []LookupChannel {
	out := make([]LookupChannel, 0, len(f.channels))
	for _, ch := range f.channels {
		out = append(out, ch)
	}
	return out
}

func (f *fakeChannelLookup) Get(id string) *LookupChannel {
	ch, ok := f.channels[id]
	if !ok {
		return nil
	}
	return &ch
}

func (f *fakeChannelLookup) Add(ch LookupChannel) {
	f.channels[ch.ID] = ch
}

func newLookupWithChannel(id, name string, keywords []string) *fakeChannelLookup {
	l := newFakeChannelLookup()
	l.Add(LookupChannel{
		ID:       id,
		Name:     name,
		Keywords: keywords,
	})
	return l
}

func TestMatchByKeyword(t *testing.T) {
	l := newLookupWithChannel("sky-sports-1", "Sky Sports 1", []string{"skysports1", "sky1", "skysports"})
	m := NewMatcher(l)

	link := &CandidateLink{
		URL:         "https://cdn.example.com/skysports1/index.m3u8",
		ContextText: "sky sports stream",
	}
	got := m.Match(link)
	if got != "sky-sports-1" {
		t.Errorf("expected sky-sports-1, got %q", got)
	}
}

func TestMatchByFuzzyName(t *testing.T) {
	l := newLookupWithChannel("bein-sports-1", "beIN Sports 1", []string{})
	m := NewMatcher(l)

	link := &CandidateLink{
		URL:         "https://cdn.example.com/stream.m3u8",
		ContextText: "bein sports channel 1 hd",
	}
	got := m.Match(link)
	if got != "bein-sports-1" {
		t.Errorf("expected bein-sports-1, got %q", got)
	}
}

func TestNoMatchReturnsEmpty(t *testing.T) {
	l := newLookupWithChannel("sky-sports-1", "Sky Sports 1", []string{"sky", "skysports"})
	m := NewMatcher(l)

	link := &CandidateLink{
		URL:         "https://cdn.example.com/unknown.m3u8",
		ContextText: "some random content",
	}
	got := m.Match(link)
	if got != "" {
		t.Errorf("expected no match, got %q", got)
	}
}

func TestCaseInsensitiveMatch(t *testing.T) {
	l := newLookupWithChannel("supersport", "SuperSport Football", []string{"supersport", "ss"})
	m := NewMatcher(l)

	link := &CandidateLink{
		URL:         "https://cdn.example.com/SUPERSPORT1.m3u8",
		ContextText: "",
	}
	got := m.Match(link)
	if got != "supersport" {
		t.Errorf("expected supersport, got %q", got)
	}
}