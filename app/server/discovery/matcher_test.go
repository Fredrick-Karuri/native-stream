// discovery/matcher_test.go
package discovery

import (
	"testing"
	"time"

	"github.com/fredrick-karuri/nativestream/server/store"
)

func newStoreWithChannel(id, name string, keywords []string) *store.Store {
	s := store.New("/tmp/test-matcher.json")
	s.Add(&store.Channel{
		ID:        id,
		Name:      name,
		Keywords:  keywords,
		CreatedAt: time.Now(),
		UpdatedAt: time.Now(),
	})
	return s
}

func TestMatchByKeyword(t *testing.T) {
	s := newStoreWithChannel("sky-sports-1", "Sky Sports 1", []string{"skysports1", "sky1", "skysports"})
	m := NewMatcher(s)

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
	s := newStoreWithChannel("bein-sports-1", "beIN Sports 1", []string{})
	m := NewMatcher(s)

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
	s := newStoreWithChannel("sky-sports-1", "Sky Sports 1", []string{"sky", "skysports"})
	m := NewMatcher(s)

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
	s := newStoreWithChannel("supersport", "SuperSport Football", []string{"supersport", "ss"})
	m := NewMatcher(s)

	link := &CandidateLink{
		URL:         "https://cdn.example.com/SUPERSPORT1.m3u8",
		ContextText: "",
	}
	got := m.Match(link)
	if got != "supersport" {
		t.Errorf("expected supersport, got %q", got)
	}
}