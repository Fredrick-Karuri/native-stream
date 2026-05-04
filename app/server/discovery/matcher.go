// discovery/matcher.go — NS-204
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