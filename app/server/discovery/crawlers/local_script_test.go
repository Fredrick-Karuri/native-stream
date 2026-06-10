// package crawlers
// discovery/crawlers/local_script_test.go
// Acceptance smoke tests for NS-305 LocalScriptCrawler.
//
// NS-014 — AC-1: absent script → zero errors, nil candidates
// NS-015 — AC-2: valid stub script → candidates reach the store after one cycle

package crawlers_test

import (
	"context"
	"encoding/json"
	"os"
	"path/filepath"
	"testing"
	"time"

	"github.com/fredrick-karuri/nativestream/server/discovery"
	"github.com/fredrick-karuri/nativestream/server/discovery/crawlers"
	"github.com/fredrick-karuri/nativestream/server/store"
	"github.com/fredrick-karuri/nativestream/server/validator"
)

// ── Helpers ───────────────────────────────────────────────────────────────────

// syncValidator wraps validator.Validator but processes Submit calls
// synchronously so tests don't race the async worker pool.
type syncValidator struct {
	store *store.Store
	cfg   validator.Config
}

func newSyncValidator(s *store.Store) *syncValidator {
	cfg := validator.DefaultConfig()
	cfg.MinScorePromote = 0 // promote everything — we're not testing HTTP reachability
	return &syncValidator{store: s, cfg: cfg}
}

// Submit immediately promotes the candidate with a synthetic passing score,
// bypassing HTTP. This makes NS-015 deterministic without network access.
func (sv *syncValidator) Submit(c validator.Candidate) {
	link := &store.LinkScore{
		URL:          c.URL,
		ChannelID:    c.ChannelID,
		SourceURL:    c.SourceURL,
		Headers:      c.Headers,
		Score:        0.9, // well above any MinScore threshold
		State:        store.StateActive,
		DiscoveredAt: time.Now(),
		LastChecked:  time.Now(),
	}
	sv.store.PromoteIfBetter(link)
}

// stubEngine builds a discovery engine wired with the given DirectFetcher and
// a real store + sync validator. Returns engine + store for assertions.
func stubEngine(t *testing.T, fetcher discovery.DirectFetcher) (*discovery.Engine, *store.Store) {
	t.Helper()

	s := store.New(filepath.Join(t.TempDir(), "snap.json"), 0.3)

	// Pre-seed one channel so the matcher has something to match against.
	s.Add(&store.Channel{
		ID:         "cricket-live",
		Name:       "Cricket Live",
		GroupTitle: "Sports",
		Keywords:   []string{"cricket", "cricket live"},
	})

	matcher := discovery.NewMatcher(s)

	// Real validator (not used — engine calls our syncValidator.Submit via
	// the wrapped engine below). We pass it only to satisfy NewEngine signature.
	v := validator.New(validator.DefaultConfig(), s, "", "", "")

	eng := discovery.NewEngine(
		discovery.Config{
			Enabled:         true,
			DefaultInterval: 2 * time.Second,
		},
		nil, // no Crawler sources
		matcher,
		v,
	)

	// Replace engine's validator submissions with our sync shim.
	// We do this by wrapping the direct fetcher loop via a custom DirectFetcher
	// adapter that calls syncValidator.Submit after FetchDirect.
	sv := newSyncValidator(s)
	wrapped := &syncFetcherAdapter{inner: fetcher, sv: sv, store: s, matcher: matcher}
	eng.WithDirectFetchers([]discovery.DirectFetcher{wrapped})

	return eng, s
}

// syncFetcherAdapter calls inner.FetchDirect then immediately promotes
// candidates via syncValidator — so runCycle's built-in async Submit is
// bypassed and the store is updated before runCycle returns.
type syncFetcherAdapter struct {
	inner   discovery.DirectFetcher
	sv      *syncValidator
	store   *store.Store
	matcher *discovery.ChannelMatcher
}

func (a *syncFetcherAdapter) Name() string { return a.inner.Name() }

func (a *syncFetcherAdapter) FetchDirect(ctx context.Context) ([]discovery.DirectCandidate, error) {
	candidates, err := a.inner.FetchDirect(ctx)
	if err != nil || len(candidates) == 0 {
		return candidates, err
	}

	// Mirror what engine.runCycle does, but submit via syncValidator.
	for i := range candidates {
		channelID := a.matcher.Match(&discovery.CandidateLink{
			URL:         candidates[i].URL,
			ContextText: candidates[i].ChannelName + " " + candidates[i].GroupTitle,
			SourceURL:   candidates[i].SourceURL,
		})
		if channelID == "" {
			continue
		}
		candidates[i].ChannelID = channelID
		a.sv.Submit(validator.Candidate{
			URL:       candidates[i].URL,
			ChannelID: channelID,
			SourceURL: candidates[i].SourceURL,
			Headers:   candidates[i].Headers,
		})
	}

	// Return empty so the engine's own submit path is a no-op.
	return nil, nil
}

// writePythonStub writes a minimal Python script that prints one valid JSON
// candidate and exits 0.
func writePythonStub(t *testing.T, dir string) string {
	t.Helper()

	payload, _ := json.Marshal([]map[string]interface{}{
		{
			"url":          "http://stub.local/cricket.m3u8",
			"channel_name": "Cricket Live",
			"group_title":  "Sports",
			"tvg_id":       "cricket-live",
			"logo_url":     "",
			"headers":      map[string]string{"User-Agent": "TestAgent/1.0"},
		},
	})
	script := "#!/usr/bin/env python3\nimport sys\nprint('" + string(payload) + "')\n"

	path := filepath.Join(dir, "stub_scraper.py")
	if err := os.WriteFile(path, []byte(script), 0o755); err != nil {
		t.Fatalf("writePythonStub: %v", err)
	}
	return path
}

// ── NS-014 ────────────────────────────────────────────────────────────────────

// AC-1: Server starts with local_script_path pointing to a non-existent file.
// Expectation: FetchDirect returns (nil, nil) — zero errors, engine cycle clean.
func TestNS014_AbsentScript_NoErrors(t *testing.T) {
	nonExistentPath := filepath.Join(t.TempDir(), "does_not_exist.py")

	crawler := crawlers.NewLocalScriptCrawler(nonExistentPath)

	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	candidates, err := crawler.FetchDirect(ctx)

	if err != nil {
		t.Errorf("NS-014: expected nil error for absent script, got: %v", err)
	}
	if len(candidates) != 0 {
		t.Errorf("NS-014: expected 0 candidates for absent script, got %d", len(candidates))
	}
}

// AC-1 (engine level): runCycle with absent script doesn't crash or corrupt state.
func TestNS014_AbsentScript_EngineCycleClean(t *testing.T) {
	nonExistentPath := filepath.Join(t.TempDir(), "does_not_exist.py")
	crawler := crawlers.NewLocalScriptCrawler(nonExistentPath)

	eng, s := stubEngine(t, crawler)

	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	// Drive one cycle directly — should not panic or produce errors.
	eng.TriggerRun(ctx)
	time.Sleep(200 * time.Millisecond) // allow goroutine to complete

	// Store must be in clean state — only the pre-seeded channel present.
	all := s.All()
	if len(all) != 1 {
		t.Errorf("NS-014: expected 1 channel (seeded), got %d", len(all))
	}

	// Pre-seeded channel must have no active link (nothing was promoted).
	ch := s.Get("cricket-live")
	if ch == nil {
		t.Fatal("NS-014: seeded channel missing from store")
	}
	if ch.ActiveLink != nil {
		t.Errorf("NS-014: expected no active link after absent-script cycle, got %+v", ch.ActiveLink)
	}
}

// ── NS-015 ────────────────────────────────────────────────────────────────────

// AC-2: After script is written to the configured path, the next cycle
// ingests its candidate and promotes it to ActiveLink on the matched channel.
func TestNS015_HotMountScript_CandidateIngested(t *testing.T) {
	dir := t.TempDir()

	// Start with absent script — mirrors "server started before script exists".
	scriptPath := filepath.Join(dir, "stub_scraper.py")
	crawler := crawlers.NewLocalScriptCrawler(scriptPath)

	eng, s := stubEngine(t, crawler)

	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()

	// Cycle 1: script absent — channel has no active link.
	eng.TriggerRun(ctx)
	time.Sleep(200 * time.Millisecond)

	if ch := s.Get("cricket-live"); ch != nil && ch.ActiveLink != nil {
		t.Fatal("NS-015: expected no active link before script exists")
	}

	// Hot-mount: write the stub script.
	writePythonStub(t, dir)

	// Cycle 2: script present — candidate should be ingested and promoted.
	eng.TriggerRun(ctx)
	time.Sleep(500 * time.Millisecond) // allow goroutine + sync promotion

	ch := s.Get("cricket-live")
	if ch == nil {
		t.Fatal("NS-015: cricket-live channel missing from store")
	}
	if ch.ActiveLink == nil {
		t.Fatal("NS-015: expected ActiveLink after script cycle, got nil")
	}
	if ch.ActiveLink.URL != "http://stub.local/cricket.m3u8" {
		t.Errorf("NS-015: unexpected ActiveLink URL: %s", ch.ActiveLink.URL)
	}

	// Headers must be preserved end-to-end.
	if ch.ActiveLink.Headers["User-Agent"] != "TestAgent/1.0" {
		t.Errorf("NS-015: expected header User-Agent=TestAgent/1.0, got %v", ch.ActiveLink.Headers)
	}
}

// AC-2 (playlist level): HealthyChannels returns the ingested channel
// after hot-mount, which is what handlePlaylist uses.
func TestNS015_HotMountScript_AppearsInHealthyChannels(t *testing.T) {
	dir := t.TempDir()
	scriptPath := filepath.Join(dir, "stub_scraper.py")
	crawler := crawlers.NewLocalScriptCrawler(scriptPath)

	eng, s := stubEngine(t, crawler)

	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()

	// Before script: no healthy channels.
	if healthy := s.HealthyChannels(); len(healthy) != 0 {
		t.Fatalf("NS-015: expected 0 healthy channels before script, got %d", len(healthy))
	}

	writePythonStub(t, dir)

	eng.TriggerRun(ctx)
	time.Sleep(500 * time.Millisecond)

	healthy := s.HealthyChannels()
	if len(healthy) == 0 {
		t.Fatal("NS-015: expected ≥1 healthy channel after script cycle, got 0")
	}

	found := false
	for _, ch := range healthy {
		if ch.ID == "cricket-live" {
			found = true
			break
		}
	}
	if !found {
		t.Error("NS-015: cricket-live not in HealthyChannels after ingestion")
	}
}