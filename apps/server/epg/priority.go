// epg/priority.go — NS-221
// Match-aware priority escalation: tells the discovery engine which channels
// need aggressive crawling because a match is starting within 2 hours.

package epg

import "time"

// PriorityChannelIDs returns channel IDs with a match starting within `within`.
func (e *Engine) PriorityChannelIDs(within time.Duration) ([]string, time.Time) {
	e.mu.RLock()
	defer e.mu.RUnlock()

	now := time.Now()
	seen := map[string]bool{}
	var ids []string
	var latestEnd time.Time

	for _, m := range e.matches {
		diff := m.KickOff.Sub(now)
		if diff <= 0 || diff > within {
			continue
		}
		matchEnd := m.KickOff.Add(m.Duration)
		if matchEnd.After(latestEnd) {
			latestEnd = matchEnd
		}
		for _, chID := range m.ChannelIDs {
			if !seen[chID] {
				seen[chID] = true
				ids = append(ids, chID)
			}
		}
	}

	return ids, latestEnd
}