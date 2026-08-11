// epg/engine_test.go
//
// Unit tests for engine.go. Covers the pure/deterministic surface:
// XMLTV generation against a real *store.Store, channel-keyword assignment,
// disk cache round-tripping, and the priority-window queries in priority.go.
//
// Explicitly NOT covered here: fetchESPN / fetchFootballData. Both build
// requests against hardcoded hostnames (site.api.espn.com, api.football-data.org)
// with no seam to redirect to an httptest.Server. See the note above
// TestFetchers_NotUnitTestable for what would need to change to close that gap.

package epg

import (
	"os"
	"path/filepath"
	"testing"
	"time"

	"github.com/fredrick-karuri/nativestream/server/store"
)

// ── Test fixtures ──────────────────────────────────────────────────────────

const healthyThreshold = 0.5

// newTestStore builds a *store.Store through its public API only — Store has
// no exported way to seed its internal map directly, so fixtures go through
// New + Add, same path production code uses.
func newTestStore(t *testing.T) *store.Store {
	t.Helper()
	s := store.New(filepath.Join(t.TempDir(), "snapshot.json"), healthyThreshold)
	return s
}

func addChannel(s *store.Store, id, name, tvgID string, keywords []string, activeScore float64) {
	ch := &store.Channel{
		ID:       id,
		Name:     name,
		TvgID:    tvgID,
		Keywords: keywords,
	}
	if activeScore > 0 {
		ch.ActiveLink = &store.LinkScore{
			URL:       "http://example.invalid/" + id + ".m3u8",
			ChannelID: id,
			Score:     activeScore,
			State:     store.StateActive,
		}
	}
	s.Add(ch)
}

func newTestEngine(t *testing.T, s *store.Store) *Engine {
	t.Helper()
	cfg := Config{
		CachePath: filepath.Join(t.TempDir(), "epg-cache.xml"),
	}
	return New(cfg, s)
}

// ── generateXMLTV ────────────────────────────────────────────────────────

func TestGenerateXMLTV_IncludesOnlyHealthyChannelsInChannelList(t *testing.T) {
	s := newTestStore(t)
	addChannel(s, "espn1", "ESPN", "espn.us", []string{"espn"}, 0.9)   // healthy
	addChannel(s, "fs1", "Fox Sports", "fs1.us", []string{"fox"}, 0.1) // below threshold

	e := newTestEngine(t, s)
	out := e.generateXMLTV(nil)

	assertContains(t, out, `id="espn.us"`)
	assertNotContains(t, out, `id="fs1.us"`)
}

func TestGenerateXMLTV_DropsProgrammeWhenChannelIDUnknownToStore(t *testing.T) {
	// A match can reference a channel ID that assignChannels found via keyword
	// match, but that ID might not resolve via store.Get if the channel was
	// deleted between assignment and generation. generateXMLTV must skip it
	// silently rather than emit a programme with an empty tvg-id.
	s := newTestStore(t)
	addChannel(s, "espn1", "ESPN", "espn.us", []string{"espn"}, 0.9)

	e := newTestEngine(t, s)
	matches := []Match{
		{
			HomeTeam:    "Arsenal",
			AwayTeam:    "Chelsea",
			Competition: "Premier League",
			KickOff:     time.Date(2026, 8, 12, 15, 0, 0, 0, time.UTC),
			ChannelIDs:  []string{"espn1", "ghost-channel"},
		},
	}

	out := e.generateXMLTV(matches)

	assertContains(t, out, `channel="espn.us"`)
	assertContains(t, out, "Arsenal vs Chelsea")
	// ghost-channel has no store entry, so it must not produce a second
	// programme block with an empty channel attribute.
	assertNotContains(t, out, `channel=""`)
}

func TestGenerateXMLTV_UsesDefaultDurationWhenMatchDurationIsZero(t *testing.T) {
	s := newTestStore(t)
	addChannel(s, "espn1", "ESPN", "espn.us", []string{"espn"}, 0.9)

	e := newTestEngine(t, s)
	kickoff := time.Date(2026, 8, 12, 15, 0, 0, 0, time.UTC)
	matches := []Match{
		{
			HomeTeam:   "Arsenal",
			AwayTeam:   "Chelsea",
			KickOff:    kickoff,
			Duration:   0, // must fall back to defaultMatchDuration (110m)
			ChannelIDs: []string{"espn1"},
		},
	}

	out := e.generateXMLTV(matches)

	wantStop := kickoff.Add(defaultMatchDuration).UTC().Format("20060102150405 +0000")
	assertContains(t, out, `stop="`+wantStop+`"`)
}

func TestGenerateXMLTV_EmptyMatchesStillEmitsValidChannelList(t *testing.T) {
	s := newTestStore(t)
	addChannel(s, "espn1", "ESPN", "espn.us", []string{"espn"}, 0.9)

	e := newTestEngine(t, s)
	out := e.generateXMLTV(nil)

	assertContains(t, out, "<tv>")
	assertContains(t, out, `id="espn.us"`)
	assertNotContains(t, out, "<programme")
}

// ── assignChannels ───────────────────────────────────────────────────────

func TestAssignChannels_MatchesOnCaseInsensitiveKeyword(t *testing.T) {
	s := newTestStore(t)
	addChannel(s, "espn1", "ESPN", "espn.us", []string{"Premier League"}, 0.9)

	e := newTestEngine(t, s)
	matches := []Match{
		{HomeTeam: "Arsenal", AwayTeam: "Chelsea", Competition: "premier league"},
	}

	got := e.assignChannels(matches)

	if len(got[0].ChannelIDs) != 1 || got[0].ChannelIDs[0] != "espn1" {
		t.Fatalf("assignChannels() ChannelIDs = %v, want [espn1]", got[0].ChannelIDs)
	}
}

func TestAssignChannels_MatchWithNoKeywordHitGetsNoChannels(t *testing.T) {
	s := newTestStore(t)
	addChannel(s, "espn1", "ESPN", "espn.us", []string{"basketball"}, 0.9)

	e := newTestEngine(t, s)
	matches := []Match{
		{HomeTeam: "Arsenal", AwayTeam: "Chelsea", Competition: "Premier League", Sport: "football"},
	}

	got := e.assignChannels(matches)

	if len(got[0].ChannelIDs) != 0 {
		t.Fatalf("assignChannels() ChannelIDs = %v, want empty", got[0].ChannelIDs)
	}
}

func TestAssignChannels_OneMatchCanFanOutToMultipleChannels(t *testing.T) {
	s := newTestStore(t)
	addChannel(s, "espn1", "ESPN", "espn.us", []string{"arsenal"}, 0.9)
	addChannel(s, "sky1", "Sky Sports", "sky.uk", []string{"arsenal"}, 0.9)

	e := newTestEngine(t, s)
	matches := []Match{
		{HomeTeam: "Arsenal", AwayTeam: "Chelsea"},
	}

	got := e.assignChannels(matches)

	if len(got[0].ChannelIDs) != 2 {
		t.Fatalf("assignChannels() ChannelIDs = %v, want 2 entries", got[0].ChannelIDs)
	}
}

func TestAssignChannels_ChannelMatchedByAtMostOneKeywordPerChannel(t *testing.T) {
	// A channel with two keywords that both hit the same match text should
	// only be appended once — the inner loop `break`s after the first hit.
	s := newTestStore(t)
	addChannel(s, "espn1", "ESPN", "espn.us", []string{"arsenal", "chelsea"}, 0.9)

	e := newTestEngine(t, s)
	matches := []Match{
		{HomeTeam: "Arsenal", AwayTeam: "Chelsea"},
	}

	got := e.assignChannels(matches)

	if len(got[0].ChannelIDs) != 1 {
		t.Fatalf("assignChannels() ChannelIDs = %v, want exactly 1 (deduped by break)", got[0].ChannelIDs)
	}
}

// ── Cache persistence (saveCacheToDisk / loadCacheFromDisk) ────────────────

func TestSaveAndLoadCacheFromDisk_RoundTrips(t *testing.T) {
	s := newTestStore(t)
	e := newTestEngine(t, s)

	want := []byte(`<?xml version="1.0"?><tv><channel id="x"/></tv>`)
	e.saveCacheToDisk(want)

	// Reset in-memory cache to prove loadCacheFromDisk actually reads the file
	// rather than returning a value already sitting in the struct.
	e.mu.Lock()
	e.cached = nil
	e.mu.Unlock()

	e.loadCacheFromDisk()

	got := e.ServeXMLTV()
	if string(got) != string(want) {
		t.Fatalf("loadCacheFromDisk() = %q, want %q", got, want)
	}
}

func TestSaveCacheToDisk_NoOpWhenCachePathEmpty(t *testing.T) {
	s := newTestStore(t)
	e := New(Config{CachePath: ""}, s)

	// Must not panic on empty path, and must not create a file at cwd.
	e.saveCacheToDisk([]byte("<tv></tv>"))
}

func TestLoadCacheFromDisk_LeavesCacheNilWhenFileMissing(t *testing.T) {
	s := newTestStore(t)
	e := newTestEngine(t, s) // CachePath points at a file that doesn't exist yet

	e.loadCacheFromDisk()

	if got := e.ServeXMLTV(); got != nil {
		t.Fatalf("ServeXMLTV() = %v, want nil when no cache file exists", got)
	}
}

func TestSaveCacheToDisk_CreatesParentDirectoriesIfMissing(t *testing.T) {
	s := newTestStore(t)
	nestedPath := filepath.Join(t.TempDir(), "a", "b", "c", "epg-cache.xml")
	e := New(Config{CachePath: nestedPath}, s)

	e.saveCacheToDisk([]byte("<tv></tv>"))

	if _, err := os.Stat(nestedPath); err != nil {
		t.Fatalf("expected cache file at %s, got error: %v", nestedPath, err)
	}
}

// ── UpcomingMatchChannels / PriorityChannelIDs (priority.go) ───────────────

func TestUpcomingMatchChannels_OnlyReturnsMatchesWithinWindow(t *testing.T) {
	s := newTestStore(t)
	e := newTestEngine(t, s)

	now := time.Now()
	e.mu.Lock()
	e.matches = []Match{
		{HomeTeam: "A", AwayTeam: "B", KickOff: now.Add(30 * time.Minute), ChannelIDs: []string{"c1"}}, // in window
		{HomeTeam: "C", AwayTeam: "D", KickOff: now.Add(3 * time.Hour), ChannelIDs: []string{"c2"}},     // outside window
		{HomeTeam: "E", AwayTeam: "F", KickOff: now.Add(-1 * time.Hour), ChannelIDs: []string{"c3"}},    // already started
	}
	e.mu.Unlock()

	got := e.UpcomingMatchChannels(2 * time.Hour)

	if len(got) != 1 || got[0].ChannelID != "c1" {
		t.Fatalf("UpcomingMatchChannels() = %+v, want single entry for c1", got)
	}
	if got[0].MatchTitle != "A vs B" {
		t.Fatalf("MatchTitle = %q, want %q", got[0].MatchTitle, "A vs B")
	}
}

func TestUpcomingMatchChannels_FansOutAcrossMultipleChannelsOnOneMatch(t *testing.T) {
	s := newTestStore(t)
	e := newTestEngine(t, s)

	now := time.Now()
	e.mu.Lock()
	e.matches = []Match{
		{HomeTeam: "A", AwayTeam: "B", KickOff: now.Add(10 * time.Minute), ChannelIDs: []string{"c1", "c2"}},
	}
	e.mu.Unlock()

	got := e.UpcomingMatchChannels(time.Hour)

	if len(got) != 2 {
		t.Fatalf("UpcomingMatchChannels() returned %d entries, want 2", len(got))
	}
}

func TestPriorityChannelIDs_DedupesChannelAcrossMultipleMatches(t *testing.T) {
	s := newTestStore(t)
	e := newTestEngine(t, s)

	now := time.Now()
	e.mu.Lock()
	e.matches = []Match{
		{HomeTeam: "A", AwayTeam: "B", KickOff: now.Add(10 * time.Minute), Duration: 90 * time.Minute, ChannelIDs: []string{"shared"}},
		{HomeTeam: "C", AwayTeam: "D", KickOff: now.Add(20 * time.Minute), Duration: 90 * time.Minute, ChannelIDs: []string{"shared"}},
	}
	e.mu.Unlock()

	ids, _ := e.PriorityChannelIDs(time.Hour)

	if len(ids) != 1 || ids[0] != "shared" {
		t.Fatalf("PriorityChannelIDs() = %v, want single deduped [shared]", ids)
	}
}

func TestPriorityChannelIDs_LatestEndReflectsLatestFinishingMatchInWindow(t *testing.T) {
	s := newTestStore(t)
	e := newTestEngine(t, s)

	now := time.Now()
	firstKickoff := now.Add(10 * time.Minute)
	secondKickoff := now.Add(15 * time.Minute)
	e.mu.Lock()
	e.matches = []Match{
		{HomeTeam: "A", AwayTeam: "B", KickOff: firstKickoff, Duration: 60 * time.Minute, ChannelIDs: []string{"c1"}},
		{HomeTeam: "C", AwayTeam: "D", KickOff: secondKickoff, Duration: 120 * time.Minute, ChannelIDs: []string{"c2"}},
	}
	e.mu.Unlock()

	_, latestEnd := e.PriorityChannelIDs(time.Hour)

	wantEnd := secondKickoff.Add(120 * time.Minute)
	if !latestEnd.Equal(wantEnd) {
		t.Fatalf("latestEnd = %v, want %v", latestEnd, wantEnd)
	}
}

func TestPriorityChannelIDs_EmptyWhenNoMatchesInWindow(t *testing.T) {
	s := newTestStore(t)
	e := newTestEngine(t, s)

	now := time.Now()
	e.mu.Lock()
	e.matches = []Match{
		{HomeTeam: "A", AwayTeam: "B", KickOff: now.Add(5 * time.Hour), ChannelIDs: []string{"c1"}},
	}
	e.mu.Unlock()

	ids, latestEnd := e.PriorityChannelIDs(time.Hour)

	if len(ids) != 0 {
		t.Fatalf("PriorityChannelIDs() ids = %v, want empty", ids)
	}
	if !latestEnd.IsZero() {
		t.Fatalf("latestEnd = %v, want zero value", latestEnd)
	}
}

// ── Fetchers: documented as not unit-testable in current form ──────────────

// TestFetchers_NotUnitTestable is not a real test — it documents a gap.
//
// fetchESPN and fetchFootballData construct requests against hardcoded
// hostnames (site.api.espn.com, api.football-data.org) instead of an
// injectable base URL. There is no seam to redirect e.client at an
// httptest.Server, so these two functions cannot be unit tested as written.
//
// To close this gap without changing behavior, engine.go would need two
// unexported fields (e.g. espnBaseURL, footballDataBaseURL string) defaulted
// to the real hosts in New(), overridable only from within the package —
// see the debugging skill for the current/replace diff format if this gets
// picked up as a follow-up ticket.
func TestFetchers_NotUnitTestable(t *testing.T) {
	t.Skip("fetchESPN/fetchFootballData hit hardcoded hostnames with no injectable base URL — see comment above this test")
}

// ── Test helpers ─────────────────────────────────────────────────────────

func assertContains(t *testing.T, haystack []byte, needle string) {
	t.Helper()
	if !contains(haystack, needle) {
		t.Fatalf("expected output to contain %q, got:\n%s", needle, haystack)
	}
}

func assertNotContains(t *testing.T, haystack []byte, needle string) {
	t.Helper()
	if contains(haystack, needle) {
		t.Fatalf("expected output NOT to contain %q, got:\n%s", needle, haystack)
	}
}

func contains(haystack []byte, needle string) bool {
	return len(needle) == 0 || indexOf(string(haystack), needle) >= 0
}

func indexOf(s, substr string) int {
	for i := 0; i+len(substr) <= len(s); i++ {
		if s[i:i+len(substr)] == substr {
			return i
		}
	}
	return -1
}