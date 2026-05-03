// epg/engine.go — NS-131, NS-132, NS-133, NS-134
// Fetches match schedules, generates XMLTV, caches, and refreshes on a timer.

package epg

import (
	"context"
	"encoding/xml"
	"fmt"
	"io"
	"net/http"
	"os"
	"path/filepath"
	"sync"
	"time"

	"github.com/fredrick-karuri/nativestream/server/store"
)

// ── Data types ────────────────────────────────────────────────────────────────

type Match struct {
	ID          string
	HomeTeam    string
	AwayTeam    string
	Competition string
	Sport       string
	KickOff     time.Time
	Duration    time.Duration
	ChannelIDs  []string
}

type PriorityChannel struct {
	ChannelID  string
	MatchTitle string
	KickOff    time.Time
}

// ── XMLTV structs for marshalling ─────────────────────────────────────────────

type xmlTV struct {
	XMLName    xml.Name       `xml:"tv"`
	Channels   []xmlChannel   `xml:"channel"`
	Programmes []xmlProgramme `xml:"programme"`
}

type xmlChannel struct {
	ID          string `xml:"id,attr"`
	DisplayName string `xml:"display-name"`
}

type xmlProgramme struct {
	Start   string `xml:"start,attr"`
	Stop    string `xml:"stop,attr"`
	Channel string `xml:"channel,attr"`
	Title   string `xml:"title"`
}

// ── Engine ────────────────────────────────────────────────────────────────────

type Config struct {
	Enabled         bool
	RefreshInterval time.Duration
	LookaheadHours  int
	CachePath       string
	ESPNEnabled     bool
	FootballDataKey string
}

type Engine struct {
	cfg       Config
	store     *store.Store
	mu        sync.RWMutex
	cached    []byte
	matches   []Match
	lastFetch time.Time
	client    *http.Client
}

func New(cfg Config, s *store.Store) *Engine {
	return &Engine{
		cfg:   cfg,
		store: s,
		client: &http.Client{Timeout: 15 * time.Second},
	}
}

// ServeXMLTV returns the cached XMLTV bytes, generating if needed.
func (e *Engine) ServeXMLTV() []byte {
	e.mu.RLock()
	defer e.mu.RUnlock()
	return e.cached
}

// UpcomingMatchChannels returns channels with a match starting within `within`.
func (e *Engine) UpcomingMatchChannels(within time.Duration) []PriorityChannel {
	e.mu.RLock()
	defer e.mu.RUnlock()

	now := time.Now()
	var out []PriorityChannel
	for _, m := range e.matches {
		diff := m.KickOff.Sub(now)
		if diff > 0 && diff <= within {
			title := fmt.Sprintf("%s vs %s", m.HomeTeam, m.AwayTeam)
			for _, chID := range m.ChannelIDs {
				out = append(out, PriorityChannel{
					ChannelID:  chID,
					MatchTitle: title,
					KickOff:    m.KickOff,
				})
			}
		}
	}
	return out
}

// RunRefresher periodically refreshes EPG data. Serves stale cache immediately on start.
func (e *Engine) RunRefresher(ctx context.Context) {
	// Load cache from disk immediately so first request is fast
	e.loadCacheFromDisk()

	// Fetch fresh data
	e.refresh()

	ticker := time.NewTicker(e.cfg.RefreshInterval)
	defer ticker.Stop()
	for {
		select {
		case <-ticker.C:
			e.refresh()
		case <-ctx.Done():
			return
		}
	}
}

func (e *Engine) refresh() {
	matches := e.fetchMatches()

	e.mu.Lock()
	e.matches = matches
	e.lastFetch = time.Now()
	e.mu.Unlock()

	xml := e.generateXMLTV(matches)

	e.mu.Lock()
	e.cached = xml
	e.mu.Unlock()

	e.saveCacheToDisk(xml)
}

// ── Match fetching (NS-131: ESPN, NS-132: football-data.org) ──────────────────

func (e *Engine) fetchMatches() []Match {
	var all []Match
	if e.cfg.ESPNEnabled {
		if m, err := e.fetchESPN(); err == nil {
			all = append(all, m...)
		} else {
			fmt.Fprintf(os.Stderr, "[epg] ESPN fetch failed: %v\n", err)
		}
	}
	if e.cfg.FootballDataKey != "" {
		if m, err := e.fetchFootballData(); err == nil {
			all = append(all, m...)
		} else {
			fmt.Fprintf(os.Stderr, "[epg] football-data fetch failed: %v\n", err)
		}
	}
	return all
}

func (e *Engine) fetchESPN() ([]Match, error) {
	// ESPN public scoreboard API — no key required
	url := "https://site.api.espn.com/apis/site/v2/sports/soccer/all/scoreboard"
	resp, err := e.client.Get(url)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()
	body, _ := io.ReadAll(resp.Body)
	return parseESPNResponse(body), nil
}

func (e *Engine) fetchFootballData() ([]Match, error) {
	// football-data.org — requires free API key
	today := time.Now().Format("2006-01-02")
	url := fmt.Sprintf("https://api.football-data.org/v4/matches?dateFrom=%s&dateTo=%s", today, today)
	req, _ := http.NewRequest("GET", url, nil)
	req.Header.Set("X-Auth-Token", e.cfg.FootballDataKey)
	resp, err := e.client.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()
	body, _ := io.ReadAll(resp.Body)
	return parseFootballDataResponse(body), nil
}

// ── XMLTV generation (NS-133) ─────────────────────────────────────────────────

func (e *Engine) generateXMLTV(matches []Match) []byte {
	channels := e.store.HealthyChannels()

	tv := xmlTV{}
	seen := map[string]bool{}

	for _, ch := range channels {
		if !seen[ch.TvgID] {
			tv.Channels = append(tv.Channels, xmlChannel{
				ID:          ch.TvgID,
				DisplayName: ch.Name,
			})
			seen[ch.TvgID] = true
		}
	}

	for _, m := range matches {
		title := fmt.Sprintf("%s vs %s — %s", m.HomeTeam, m.AwayTeam, m.Competition)
		dur := m.Duration
		if dur == 0 {
			dur = 110 * time.Minute
		}
		stop := m.KickOff.Add(dur)
		for _, chID := range m.ChannelIDs {
			// Find tvg-id for this store channel id
			ch := e.store.Get(chID)
			if ch == nil {
				continue
			}
			tv.Programmes = append(tv.Programmes, xmlProgramme{
				Start:   m.KickOff.UTC().Format("20060102150405 +0000"),
				Stop:    stop.UTC().Format("20060102150405 +0000"),
				Channel: ch.TvgID,
				Title:   title,
			})
		}
	}

	out, err := xml.MarshalIndent(tv, "", "  ")
	if err != nil {
		return []byte(`<?xml version="1.0"?><tv></tv>`)
	}
	return append([]byte(xml.Header), out...)
}

// ── Cache persistence ─────────────────────────────────────────────────────────

func (e *Engine) loadCacheFromDisk() {
	data, err := os.ReadFile(e.cfg.CachePath)
	if err != nil {
		return
	}
	e.mu.Lock()
	e.cached = data
	e.mu.Unlock()
}

func (e *Engine) saveCacheToDisk(data []byte) {
	if e.cfg.CachePath == "" {
		return
	}
	_ = os.MkdirAll(filepath.Dir(e.cfg.CachePath), 0o755)
	_ = os.WriteFile(e.cfg.CachePath, data, 0o644)
}