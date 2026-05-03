// store/store.go — NS-101, NS-102, NS-103
// In-memory channel store with JSON snapshot persistence and auto-snapshotting.

package store

import (
	"context"
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"sort"
	"sync"
	"time"
)

// ── Types ────────────────────────────────────────────────────────────────────

type LinkState string

const (
	StateCandidate  LinkState = "candidate"
	StateActive     LinkState = "active"
	StateQuarantine LinkState = "quarantine"
	StateEvicted    LinkState = "evicted"
)

type LinkScore struct {
	URL          string    `json:"url"`
	ChannelID    string    `json:"channel_id"`
	SourceURL    string    `json:"source_url"`
	Score        float64   `json:"score"`
	LatencyMS    int64     `json:"latency_ms"`
	EstBitrateKbps int     `json:"est_bitrate_kbps"`
	State        LinkState `json:"state"`
	FailCount    int       `json:"fail_count"`
	LastChecked  time.Time `json:"last_checked"`
	DiscoveredAt time.Time `json:"discovered_at"`
}

type Channel struct {
	ID         string     `json:"id"`
	Name       string     `json:"name"`
	GroupTitle string     `json:"group_title"`
	TvgID      string     `json:"tvg_id"`
	LogoURL    string     `json:"logo_url"`
	Keywords   []string   `json:"keywords"`
	ActiveLink *LinkScore `json:"active_link"`
	Candidates []*LinkScore `json:"candidates"`
	CreatedAt  time.Time  `json:"created_at"`
	UpdatedAt  time.Time  `json:"updated_at"`
}

// ── Store ─────────────────────────────────────────────────────────────────────

type Store struct {
	mu       sync.RWMutex
	channels map[string]*Channel
	path     string
}

func New(snapshotPath string) *Store {
	return &Store{
		channels: make(map[string]*Channel),
		path:     snapshotPath,
	}
}

// ── CRUD ──────────────────────────────────────────────────────────────────────

func (s *Store) Get(id string) *Channel {
	s.mu.RLock()
	defer s.mu.RUnlock()
	ch := s.channels[id]
	if ch == nil {
		return nil
	}
	cp := *ch
	return &cp
}

func (s *Store) All() []*Channel {
	s.mu.RLock()
	defer s.mu.RUnlock()
	out := make([]*Channel, 0, len(s.channels))
	for _, ch := range s.channels {
		cp := *ch
		out = append(out, &cp)
	}
	sort.Slice(out, func(i, j int) bool { return out[i].Name < out[j].Name })
	return out
}

func (s *Store) HealthyChannels() []*Channel {
	s.mu.RLock()
	defer s.mu.RUnlock()
	var out []*Channel
	for _, ch := range s.channels {
		if ch.ActiveLink != nil && ch.ActiveLink.Score >= 0.3 {
			cp := *ch
			out = append(out, &cp)
		}
	}
	sort.Slice(out, func(i, j int) bool { return out[i].Name < out[j].Name })
	return out
}

func (s *Store) Add(ch *Channel) {
	s.mu.Lock()
	defer s.mu.Unlock()
	if ch.CreatedAt.IsZero() {
		ch.CreatedAt = time.Now()
	}
	ch.UpdatedAt = time.Now()
	s.channels[ch.ID] = ch
}

func (s *Store) Update(id string, updates map[string]any) error {
	s.mu.Lock()
	defer s.mu.Unlock()
	ch, ok := s.channels[id]
	if !ok {
		return fmt.Errorf("channel %q not found", id)
	}
	if name, ok := updates["name"].(string); ok {
		ch.Name = name
	}
	if group, ok := updates["group_title"].(string); ok {
		ch.GroupTitle = group
	}
	if url, ok := updates["stream_url"].(string); ok {
		link := &LinkScore{
			URL:          url,
			ChannelID:    id,
			State:        StateCandidate,
			DiscoveredAt: time.Now(),
		}
		ch.Candidates = append(ch.Candidates, link)
	}
	ch.UpdatedAt = time.Now()
	return nil
}

func (s *Store) Delete(id string) error {
	s.mu.Lock()
	defer s.mu.Unlock()
	if _, ok := s.channels[id]; !ok {
		return fmt.Errorf("channel %q not found", id)
	}
	delete(s.channels, id)
	return nil
}

// ── Link management ───────────────────────────────────────────────────────────

// UpdateScore applies a new score to a link identified by URL across all channels.
func (s *Store) UpdateScore(url string, score *LinkScore) {
	s.mu.Lock()
	defer s.mu.Unlock()

	ch, ok := s.channels[score.ChannelID]
	if !ok {
		return
	}

	// Update active link
	if ch.ActiveLink != nil && ch.ActiveLink.URL == url {
		ch.ActiveLink = score
		if score.Score < 0.3 {
			ch.ActiveLink.State = StateQuarantine
			s.promoteNext(ch)
		}
		return
	}

	// Update in candidates
	for i, c := range ch.Candidates {
		if c.URL == url {
			ch.Candidates[i] = score
			return
		}
	}

	// New candidate
	score.State = StateCandidate
	ch.Candidates = append(ch.Candidates, score)
}

// PromoteIfBetter promotes a link to active if its score beats the current active.
func (s *Store) PromoteIfBetter(link *LinkScore) {
	s.mu.Lock()
	defer s.mu.Unlock()

	ch, ok := s.channels[link.ChannelID]
	if !ok {
		return
	}

	if ch.ActiveLink == nil || link.Score > ch.ActiveLink.Score {
		if ch.ActiveLink != nil {
			ch.ActiveLink.State = StateCandidate
			ch.Candidates = append(ch.Candidates, ch.ActiveLink)
		}
		link.State = StateActive
		ch.ActiveLink = link
		// Remove from candidates if present
		var filtered []*LinkScore
		for _, c := range ch.Candidates {
			if c.URL != link.URL {
				filtered = append(filtered, c)
			}
		}
		ch.Candidates = filtered
		ch.UpdatedAt = time.Now()
	}
}

// promoteNext finds the best candidate and promotes it. Caller must hold write lock.
func (s *Store) promoteNext(ch *Channel) {
	var best *LinkScore
	for _, c := range ch.Candidates {
		if c.State != StateEvicted && (best == nil || c.Score > best.Score) {
			best = c
		}
	}
	if best != nil && best.Score >= 0.5 {
		best.State = StateActive
		ch.ActiveLink = best
		var filtered []*LinkScore
		for _, c := range ch.Candidates {
			if c.URL != best.URL {
				filtered = append(filtered, c)
			}
		}
		ch.Candidates = filtered
	}
}

// Count returns total and healthy channel counts.
func (s *Store) Count() (total, healthy int) {
	s.mu.RLock()
	defer s.mu.RUnlock()
	total = len(s.channels)
	for _, ch := range s.channels {
		if ch.ActiveLink != nil && ch.ActiveLink.Score >= 0.3 {
			healthy++
		}
	}
	return
}

// ── Persistence ───────────────────────────────────────────────────────────────

type snapshot struct {
	Version   int                 `json:"version"`
	UpdatedAt time.Time           `json:"updated_at"`
	Channels  map[string]*Channel `json:"channels"`
}

// Snapshot atomically writes the store to disk.
func (s *Store) Snapshot() error {
	s.mu.RLock()
	snap := snapshot{
		Version:   3,
		UpdatedAt: time.Now(),
		Channels:  make(map[string]*Channel, len(s.channels)),
	}
	for k, v := range s.channels {
		cp := *v
		snap.Channels[k] = &cp
	}
	s.mu.RUnlock()

	data, err := json.MarshalIndent(snap, "", "  ")
	if err != nil {
		return fmt.Errorf("marshal snapshot: %w", err)
	}

	if err := os.MkdirAll(filepath.Dir(s.path), 0o755); err != nil {
		return fmt.Errorf("create snapshot dir: %w", err)
	}

	// Atomic write: write to .tmp then rename
	tmp := s.path + ".tmp"
	if err := os.WriteFile(tmp, data, 0o644); err != nil {
		return fmt.Errorf("write snapshot tmp: %w", err)
	}
	if err := os.Rename(tmp, s.path); err != nil {
		return fmt.Errorf("rename snapshot: %w", err)
	}
	return nil
}

// Load reads a snapshot from disk into the store.
func (s *Store) Load() error {
	data, err := os.ReadFile(s.path)
	if os.IsNotExist(err) {
		return nil // Fresh start — not an error
	}
	if err != nil {
		return fmt.Errorf("read snapshot: %w", err)
	}

	var snap snapshot
	if err := json.Unmarshal(data, &snap); err != nil {
		return fmt.Errorf("parse snapshot: %w", err)
	}

	s.mu.Lock()
	defer s.mu.Unlock()
	s.channels = snap.Channels
	if s.channels == nil {
		s.channels = make(map[string]*Channel)
	}
	return nil
}

// RunSnapshotter periodically snapshots the store. Runs until ctx is cancelled.
func (s *Store) RunSnapshotter(ctx context.Context, interval time.Duration) {
	ticker := time.NewTicker(interval)
	defer ticker.Stop()
	for {
		select {
		case <-ticker.C:
			if err := s.Snapshot(); err != nil {
				fmt.Fprintf(os.Stderr, "[store] snapshot failed: %v\n", err)
			}
		case <-ctx.Done():
			// Final snapshot on shutdown
			_ = s.Snapshot()
			return
		}
	}
}