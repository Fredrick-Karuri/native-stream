// store/store_test.go
package store

import (
	"os"
	"path/filepath"
	"testing"
	"time"
)

func newTestStore(t *testing.T) *Store {
	t.Helper()
	tmp := filepath.Join(t.TempDir(), "channels.json")
	return New(tmp)
}

func sampleChannel(id string) *Channel {
	return &Channel{
		ID:         id,
		Name:       "Test " + id,
		GroupTitle: "Sports",
		TvgID:      id,
	}
}

func sampleLink(url, channelID string, score float64) *LinkScore {
	return &LinkScore{
		URL:          url,
		ChannelID:    channelID,
		Score:        score,
		State:        StateCandidate,
		DiscoveredAt: time.Now(),
	}
}

// ── CRUD ──────────────────────────────────────────────────────────────────────

func TestAddAndGet(t *testing.T) {
	s := newTestStore(t)
	ch := sampleChannel("sky-1")
	s.Add(ch)

	got := s.Get("sky-1")
	if got == nil {
		t.Fatal("expected channel, got nil")
	}
	if got.Name != ch.Name {
		t.Errorf("name mismatch: got %q want %q", got.Name, ch.Name)
	}
}

func TestDelete(t *testing.T) {
	s := newTestStore(t)
	s.Add(sampleChannel("ch1"))
	if err := s.Delete("ch1"); err != nil {
		t.Fatalf("delete: %v", err)
	}
	if s.Get("ch1") != nil {
		t.Error("expected nil after delete")
	}
}

func TestDeleteNotFound(t *testing.T) {
	s := newTestStore(t)
	if err := s.Delete("ghost"); err == nil {
		t.Error("expected error deleting missing channel")
	}
}

func TestHealthyChannels(t *testing.T) {
	s := newTestStore(t)
	ch := sampleChannel("ch1")
	ch.ActiveLink = sampleLink("https://good.m3u8", "ch1", 0.9)
	ch.ActiveLink.State = StateActive
	s.Add(ch)

	ch2 := sampleChannel("ch2") // no active link
	s.Add(ch2)

	healthy := s.HealthyChannels()
	if len(healthy) != 1 {
		t.Errorf("expected 1 healthy channel, got %d", len(healthy))
	}
	if healthy[0].ID != "ch1" {
		t.Errorf("wrong healthy channel: %s", healthy[0].ID)
	}
}

// ── Promote ───────────────────────────────────────────────────────────────────

func TestPromoteIfBetter(t *testing.T) {
	s := newTestStore(t)
	ch := sampleChannel("ch1")
	ch.ActiveLink = sampleLink("https://old.m3u8", "ch1", 0.5)
	ch.ActiveLink.State = StateActive
	s.Add(ch)

	better := sampleLink("https://new.m3u8", "ch1", 0.9)
	s.PromoteIfBetter(better)

	got := s.Get("ch1")
	if got.ActiveLink.URL != "https://new.m3u8" {
		t.Errorf("expected new link promoted, got %s", got.ActiveLink.URL)
	}
	if got.ActiveLink.State != StateActive {
		t.Errorf("expected StateActive, got %s", got.ActiveLink.State)
	}
	// Old link should be in candidates
	found := false
	for _, c := range got.Candidates {
		if c.URL == "https://old.m3u8" {
			found = true
		}
	}
	if !found {
		t.Error("old active link not demoted to candidates")
	}
}

func TestPromoteIfBetterDoesNotDowngrade(t *testing.T) {
	s := newTestStore(t)
	ch := sampleChannel("ch1")
	ch.ActiveLink = sampleLink("https://good.m3u8", "ch1", 0.9)
	ch.ActiveLink.State = StateActive
	s.Add(ch)

	worse := sampleLink("https://worse.m3u8", "ch1", 0.4)
	s.PromoteIfBetter(worse)

	got := s.Get("ch1")
	if got.ActiveLink.URL != "https://good.m3u8" {
		t.Error("worse link should not have replaced better active link")
	}
}

// ── Self-healing ──────────────────────────────────────────────────────────────

func TestSelfHealingPromotesCandidate(t *testing.T) {
	s := newTestStore(t)
	ch := sampleChannel("ch1")
	active := sampleLink("https://dying.m3u8", "ch1", 0.9)
	active.State = StateActive
	ch.ActiveLink = active

	backup := sampleLink("https://backup.m3u8", "ch1", 0.7)
	backup.State = StateCandidate
	ch.Candidates = []*LinkScore{backup}
	s.Add(ch)

	// Simulate active going dead
	dead := sampleLink("https://dying.m3u8", "ch1", 0.1)
	dead.State = StateQuarantine
	s.UpdateScore("https://dying.m3u8", dead)

	got := s.Get("ch1")
	if got.ActiveLink == nil {
		t.Fatal("expected a promoted active link")
	}
	if got.ActiveLink.URL != "https://backup.m3u8" {
		t.Errorf("expected backup promoted, got %s", got.ActiveLink.URL)
	}
}

// ── Snapshot round-trip ───────────────────────────────────────────────────────

func TestSnapshotRoundTrip(t *testing.T) {
	tmp := filepath.Join(t.TempDir(), "snap.json")
	s := New(tmp)

	for i := 0; i < 5; i++ {
		ch := sampleChannel("ch" + string(rune('0'+i)))
		ch.ActiveLink = sampleLink("https://stream"+string(rune('0'+i))+".m3u8", ch.ID, 0.8)
		ch.ActiveLink.State = StateActive
		s.Add(ch)
	}

	if err := s.Snapshot(); err != nil {
		t.Fatalf("snapshot: %v", err)
	}
	if _, err := os.Stat(tmp); err != nil {
		t.Fatalf("snapshot file missing: %v", err)
	}

	s2 := New(tmp)
	if err := s2.Load(); err != nil {
		t.Fatalf("load: %v", err)
	}
	total, _ := s2.Count()
	if total != 5 {
		t.Errorf("expected 5 channels after load, got %d", total)
	}
}

func TestSnapshotAtomicWrite(t *testing.T) {
	tmp := filepath.Join(t.TempDir(), "snap.json")
	s := New(tmp)
	s.Add(sampleChannel("ch1"))

	// Write a first snapshot
	if err := s.Snapshot(); err != nil {
		t.Fatal(err)
	}
	// A partial .tmp file should not exist after successful snapshot
	if _, err := os.Stat(tmp + ".tmp"); !os.IsNotExist(err) {
		t.Error(".tmp file should be cleaned up after successful snapshot")
	}
}

// ── Count ─────────────────────────────────────────────────────────────────────

func TestCount(t *testing.T) {
	s := newTestStore(t)
	ch1 := sampleChannel("ch1")
	ch1.ActiveLink = sampleLink("https://a.m3u8", "ch1", 0.8)
	ch1.ActiveLink.State = StateActive
	s.Add(ch1)
	s.Add(sampleChannel("ch2")) // no active link

	total, healthy := s.Count()
	if total != 2 {
		t.Errorf("expected total=2, got %d", total)
	}
	if healthy != 1 {
		t.Errorf("expected healthy=1, got %d", healthy)
	}
}