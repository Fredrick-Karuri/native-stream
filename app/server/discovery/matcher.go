// discovery/matcher.go
// Matches candidate links to channels using keyword sets.

package discovery

import (
	"strings"
	"github.com/fredrick-karuri/nativestream/server/store"
)

type ChannelMatcher struct {
	store *store.Store
}

func NewMatcher(s *store.Store) *ChannelMatcher {
	return &ChannelMatcher{store: s}
}

// Match attempts to assign a CandidateLink to a channel.
// Returns the matched channel ID, or "" if no match.
func (m *ChannelMatcher) Match(link *CandidateLink) string {
	channels := m.store.All()
	combined := strings.ToLower(link.URL + " " + link.ContextText)

	for _, ch := range channels {
		for _, kw := range ch.Keywords {
			if kw == "" {
				continue
			}
			if strings.Contains(combined, strings.ToLower(kw)) {
				return ch.ID
			}
		}
		// Fallback: fuzzy match on channel name words
		nameParts := strings.Fields(strings.ToLower(ch.Name))
		matchCount := 0
		for _, part := range nameParts {
			if len(part) >= 3 && strings.Contains(combined, part) {
				matchCount++
			}
		}
		if matchCount >= 2 || (len(nameParts) == 1 && matchCount == 1) {
			return ch.ID
		}
	}

	return ""
}

func (m *ChannelMatcher) AutoRegister(candidate DirectCandidate) string {
	id := slugify(candidate.ChannelName)
	if id == "" {
		return ""
	}

	// Don't duplicate if already registered
	if ch := m.store.Get(id); ch != nil {
		return ch.ID
	}

	ch := &store.Channel{
		ID:         id,
		Name:       candidate.ChannelName,
		GroupTitle: candidate.GroupTitle,
		TvgID:      candidate.TvgID,
		LogoURL:    candidate.LogoURL,
		Keywords:   []string{strings.ToLower(candidate.ChannelName)},
	}
	m.store.Add(ch)
	return id
}

func slugify(name string) string {
	name = strings.ToLower(strings.TrimSpace(name))
	words := strings.Fields(name)
	return strings.Join(words, "-")
}