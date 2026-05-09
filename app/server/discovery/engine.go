// discovery/engine.go — NS-202, NS-221, NS-222
// Orchestrates crawlers → extractor → matcher → validator pipeline.
// Match-aware: escalates crawl priority for channels with imminent kickoffs.

package discovery

import (
	"context"
	"fmt"
	"os"
	"sync"
	"time"

	"github.com/fredrick-karuri/nativestream/server/validator"
)

// EPGAdvisor is satisfied by epg.Engine — avoids circular import.
type EPGAdvisor interface {
	UpcomingMatchChannels(within time.Duration) []interface{ GetChannelID() string }
}

// Config for the discovery engine.
type Config struct {
	Enabled          bool
	DefaultInterval  time.Duration
	PriorityInterval time.Duration
}

// Engine orchestrates all crawlers and feeds candidates to the validator.
type Engine struct {
	cfg       Config
	crawlers  []Crawler
	extractor *LinkExtractor
	matcher   *ChannelMatcher
	validator *validator.Validator

	mu            sync.Mutex
	sourceStates  map[string]*SourceState
	unmatched     []CandidateLink // capped at 200
	lastRun       time.Time
	promotedToday int
	foundToday    int

	// Per-channel priority overrides: channelID → elevated interval
	priorityChannels map[string]time.Time // channelID → when priority expires
}

func NewEngine(
	cfg Config,
	crawlers []Crawler,
	matcher *ChannelMatcher,
	v *validator.Validator,
) *Engine {
	e := &Engine{
		cfg:              cfg,
		crawlers:         crawlers,
		extractor:        NewExtractor(),
		matcher:          matcher,
		validator:        v,
		sourceStates:     make(map[string]*SourceState),
		priorityChannels: make(map[string]time.Time),
	}
	for _, c := range crawlers {
		e.sourceStates[c.Name()] = &SourceState{Name: c.Name()}
	}
	return e
}

// Run starts the discovery loop. Blocks until ctx is cancelled.
func (e *Engine) Run(ctx context.Context) {
	if !e.cfg.Enabled {
		fmt.Fprintln(os.Stderr, "[discovery] disabled — set discovery_enabled: true in config")
		return
	}

	for {
		e.runCycle(ctx)

		interval := e.cfg.DefaultInterval
		// Use priority interval if any priority channels are active
		e.mu.Lock()
		for _, expires := range e.priorityChannels {
			if time.Now().Before(expires) {
				interval = e.cfg.PriorityInterval
				break
			}
		}
		e.mu.Unlock()

		select {
		case <-time.After(interval):
		case <-ctx.Done():
			return
		}
	}
}

// SetPriorityChannels escalates crawl interval for channels with imminent matches.
// Called by the EPG engine when a match is within 2 hours. (NS-222)
func (e *Engine) SetPriorityChannels(channelIDs []string, matchEnd time.Time) {
	e.mu.Lock()
	defer e.mu.Unlock()
	for _, id := range channelIDs {
		e.priorityChannels[id] = matchEnd.Add(30 * time.Minute)
	}
}

// Status returns a snapshot of discovery state for the API.
func (e *Engine) Status() map[string]interface{} {
	e.mu.Lock()
	defer e.mu.Unlock()

	states := make(map[string]interface{})
	for name, st := range e.sourceStates {
		states[name] = map[string]interface{}{
			"last_fetch":  st.LastFetch,
			"links_found": st.LinksFound,
			"last_error":  st.LastError,
			"suspended":   st.Suspended,
		}
	}
	return map[string]interface{}{
		"last_run":        e.lastRun,
		"sources":         states,
		"found_today":     e.foundToday,
		"promoted_today":  e.promotedToday,
		"unmatched_count": len(e.unmatched),
	}
}

// Unmatched returns the last N unmatched candidate links.
func (e *Engine) Unmatched(limit int) []CandidateLink {
	e.mu.Lock()
	defer e.mu.Unlock()
	if limit > len(e.unmatched) {
		limit = len(e.unmatched)
	}
	out := make([]CandidateLink, limit)
	copy(out, e.unmatched[len(e.unmatched)-limit:])
	return out
}

// TriggerRun triggers an immediate discovery cycle.
func (e *Engine) TriggerRun(ctx context.Context) {
	go e.runCycle(ctx)
}

// ── Internal ──────────────────────────────────────────────────────────────────

func (e *Engine) runCycle(ctx context.Context) {
	e.mu.Lock()
	e.lastRun = time.Now()
	// Reset daily counters at midnight
	e.mu.Unlock()

	// 1. Fetch from all crawlers in parallel
	items := e.fetchAll(ctx)

	// 2. Extract candidate links
	candidates := e.extractor.Extract(ctx, items)
	fmt.Fprintf(os.Stderr, "[debug] items=%d candidates=%d\n", len(items), len(candidates))


	// 3. Deduplicate
	candidates = deduplicate(candidates)

	e.mu.Lock()
	e.foundToday += len(candidates)
	e.mu.Unlock()

	// 4. Match to channels and submit to validator
	for i := range candidates {
		channelID := e.matcher.Match(&candidates[i])
		if channelID == "" {
			e.mu.Lock()
			e.unmatched = append(e.unmatched, candidates[i])
			if len(e.unmatched) > 200 {
				e.unmatched = e.unmatched[len(e.unmatched)-200:]
			}
			e.mu.Unlock()
			continue
		}
		candidates[i].ChannelID = channelID
		e.validator.Submit(validator.Candidate{
			URL:       candidates[i].URL,
			ChannelID: channelID,
			SourceURL: candidates[i].SourceURL,
		})
	}
}

func (e *Engine) fetchAll(ctx context.Context) []RawItem {
	var (
		mu    sync.Mutex
		all   []RawItem
		wg    sync.WaitGroup
	)

	for _, crawler := range e.crawlers {
		wg.Add(1)
		go func(c Crawler) {
			defer wg.Done()

			items, err := c.Fetch(ctx)
			fmt.Fprintf(os.Stderr, "[debug/%s] fetched items=%d err=%v\n", c.Name(), len(items), err)

			e.mu.Lock()
			st := e.sourceStates[c.Name()]
			if err != nil {
				st.LastError = err.Error()
				fmt.Fprintf(os.Stderr, "[discovery/%s] fetch error: %v\n", c.Name(), err)
			} else {
				st.LastFetch = time.Now()
				st.LinksFound += len(items)
				st.LastError = ""
			}
			e.mu.Unlock()

			if len(items) > 0 {
				mu.Lock()
				all = append(all, items...)
				mu.Unlock()
			}
		}(crawler)
		
	}

	wg.Wait()
	return all
}

func deduplicate(links []CandidateLink) []CandidateLink {
	seen := make(map[string]bool, len(links))
	out := links[:0]
	for _, l := range links {
		if !seen[l.URL] {
			seen[l.URL] = true
			out = append(out, l)
		}
	}
	return out
}