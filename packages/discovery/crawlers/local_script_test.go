// packages/discovery/crawlers/local_script_test.go

package crawlers_test

import (
	"context"
	"encoding/json"
	"os"
	"path/filepath"
	"testing"
	"time"

	"github.com/fredrick-karuri/nativestream/packages/discovery"
	"github.com/fredrick-karuri/nativestream/packages/discovery/crawlers"
)

// ── Fakes ─────────────────────────────────────────────────────────────────────

// fakeLookup is a minimal in-memory discovery.ChannelLookup.
type fakeLookup struct {
	channels map[string]discovery.LookupChannel
}

func newFakeLookup() *fakeLookup {
	return &fakeLookup{channels: make(map[string]discovery.LookupChannel)}
}

func (f *fakeLookup) All() []discovery.LookupChannel {
	out := make([]discovery.LookupChannel, 0, len(f.channels))
	for _, ch := range f.channels {
		out = append(out, ch)
	}
	return out
}

func (f *fakeLookup) Get(id string) *discovery.LookupChannel {
	ch, ok := f.channels[id]
	if !ok {
		return nil
	}
	return &ch
}

func (f *fakeLookup) Add(ch discovery.LookupChannel) {
	f.channels[ch.ID] = ch
}

// fakeSubmitter is an in-memory discovery.CandidateSubmitter — captures
// every submitted candidate for assertions, no scoring/HTTP involved.
type fakeSubmitter struct {
	submitted []discovery.SubmittedCandidate
}

func newFakeSubmitter() *fakeSubmitter {
	return &fakeSubmitter{}
}

func (f *fakeSubmitter) Submit(c discovery.SubmittedCandidate) {
	f.submitted = append(f.submitted, c)
}

// byChannelID returns the last submitted candidate for a channel ID, or
// nil if none was submitted.
func (f *fakeSubmitter) byChannelID(id string) *discovery.SubmittedCandidate {
	for i := len(f.submitted) - 1; i >= 0; i-- {
		if f.submitted[i].ChannelID == id {
			return &f.submitted[i]
		}
	}
	return nil
}

// testEngine builds a discovery.Engine wired with the given DirectFetcher
// and fresh in-memory fakes. Returns the engine and the submitter so tests
// can assert on what was matched and submitted.
func testEngine(fetcher discovery.DirectFetcher) (*discovery.Engine, *fakeSubmitter) {
	lookup := newFakeLookup()
	lookup.Add(discovery.LookupChannel{
		ID:       "cricket-live",
		Name:     "Cricket Live",
		Keywords: []string{"cricket", "cricket live"},
	})

	matcher := discovery.NewMatcher(lookup)
	submitter := newFakeSubmitter()

	eng := discovery.NewEngine(
		discovery.Config{
			Enabled:         true,
			DefaultInterval: 2 * time.Second,
		},
		nil, // no Crawler sources
		matcher,
		submitter,
	)
	eng.WithDirectFetchers([]discovery.DirectFetcher{fetcher})

	return eng, submitter
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
// Expectation: FetchDirect returns (nil, nil) — zero errors.
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

// AC-1 (engine level): runCycle with absent script doesn't crash or submit
// anything.
func TestNS014_AbsentScript_EngineCycleClean(t *testing.T) {
	nonExistentPath := filepath.Join(t.TempDir(), "does_not_exist.py")
	crawler := crawlers.NewLocalScriptCrawler(nonExistentPath)

	eng, submitter := testEngine(crawler)

	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	eng.TriggerRun(ctx)
	time.Sleep(200 * time.Millisecond) // allow goroutine to complete

	if got := submitter.byChannelID("cricket-live"); got != nil {
		t.Errorf("NS-014: expected no submission after absent-script cycle, got %+v", got)
	}
}

// ── NS-015 ────────────────────────────────────────────────────────────────────

// AC-2: After script is written to the configured path, the next cycle
// ingests its candidate and submits it for the matched channel.
func TestNS015_HotMountScript_CandidateIngested(t *testing.T) {
	dir := t.TempDir()

	// Start with absent script — mirrors "server started before script exists".
	scriptPath := filepath.Join(dir, "stub_scraper.py")
	crawler := crawlers.NewLocalScriptCrawler(scriptPath)

	eng, submitter := testEngine(crawler)

	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()

	// Cycle 1: script absent — nothing submitted.
	eng.TriggerRun(ctx)
	time.Sleep(200 * time.Millisecond)

	if got := submitter.byChannelID("cricket-live"); got != nil {
		t.Fatal("NS-015: expected no submission before script exists")
	}

	// Hot-mount: write the stub script.
	writePythonStub(t, dir)

	// Cycle 2: script present — candidate should be ingested and submitted.
	eng.TriggerRun(ctx)
	time.Sleep(500 * time.Millisecond) // allow goroutine to complete

	got := submitter.byChannelID("cricket-live")
	if got == nil {
		t.Fatal("NS-015: expected a submitted candidate after script cycle, got none")
	}
	if got.URL != "http://stub.local/cricket.m3u8" {
		t.Errorf("NS-015: unexpected submitted URL: %s", got.URL)
	}

	// Headers must be preserved end-to-end.
	if got.Headers["User-Agent"] != "TestAgent/1.0" {
		t.Errorf("NS-015: expected header User-Agent=TestAgent/1.0, got %v", got.Headers)
	}
}