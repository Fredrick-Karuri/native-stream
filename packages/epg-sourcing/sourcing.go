// packages/epg-sourcing/sourcing.go
//
// Fetches match schedules from ESPN and football-data.org. Sourcing only —
// caching and channel assignment stay server-side (see CPMP-005's
// classification table: caching is needed regardless of source, and
// assignChannels reads store.Channel.Keywords, control-plane state this
// package must not depend on). apps/server/epg calls FetchESPN/
// FetchFootballData, then does its own caching and assignment over the
// unassigned Match values returned here.
package epgsourcing

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"log/slog"
	"net/http"
	"time"
)

const defaultMatchDuration = 110 * time.Minute

// Match is a sourced schedule entry with no channel assignment — that's
// added by the control plane after FetchESPN/FetchFootballData returns.
type Match struct {
	HomeTeam    string
	AwayTeam    string
	Competition string
	Sport       string
	KickOff     time.Time
	Duration    time.Duration
}

// Client wraps the shared http.Client used for both sources — kept as a
// small struct rather than package-level functions taking *http.Client so
// callers aren't forced to pass one on every call.
type Client struct {
	HTTPClient *http.Client
}

// NewClient returns a Client with a 15s timeout, matching the original
// epg.Engine's client configuration.
func NewClient() *Client {
	return &Client{HTTPClient: &http.Client{Timeout: 15 * time.Second}}
}

// FetchESPN fetches and parses ESPN's public soccer scoreboard. No API key
// required.
func (c *Client) FetchESPN(ctx context.Context) ([]Match, error) {
	url := "https://site.api.espn.com/apis/site/v2/sports/soccer/all/scoreboard"
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, url, nil)
	if err != nil {
		return nil, err
	}
	resp, err := c.HTTPClient.Do(req)
	if err != nil {
		return nil, err
	}
	defer func() {
		if cerr := resp.Body.Close(); cerr != nil {
			slog.Debug("epg-sourcing: close ESPN response body", "err", cerr)
		}
	}()
	body, _ := io.ReadAll(resp.Body)
	return parseESPNResponse(body), nil
}

// FetchFootballData fetches and parses today's matches from
// football-data.org. Requires a free API key.
func (c *Client) FetchFootballData(ctx context.Context, apiKey string) ([]Match, error) {
	today := time.Now().Format("2006-01-02")
	url := fmt.Sprintf("https://api.football-data.org/v4/matches?dateFrom=%s&dateTo=%s", today, today)
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, url, nil)
	if err != nil {
		return nil, err
	}
	req.Header.Set("X-Auth-Token", apiKey)
	resp, err := c.HTTPClient.Do(req)
	if err != nil {
		return nil, err
	}
	defer func() {
		if cerr := resp.Body.Close(); cerr != nil {
			slog.Debug("epg-sourcing: close football-data response body", "err", cerr)
		}
	}()
	body, _ := io.ReadAll(resp.Body)
	return parseFootballDataResponse(body), nil
}

// ── ESPN parser ───────────────────────────────────────────────────────────────

type espnResponse struct {
	Events []struct {
		Name         string `json:"name"`
		Date         string `json:"date"`
		Competitions []struct {
			Competitors []struct {
				HomeAway string `json:"homeAway"`
				Team     struct {
					DisplayName string `json:"displayName"`
				} `json:"team"`
			} `json:"competitors"`
		} `json:"competitions"`
	} `json:"events"`
}

func parseESPNResponse(data []byte) []Match {
	var r espnResponse
	if err := json.Unmarshal(data, &r); err != nil {
		return nil
	}

	var matches []Match
	for _, ev := range r.Events {
		t, err := time.Parse(time.RFC3339, ev.Date)
		if err != nil {
			continue
		}
		var home, away string
		if len(ev.Competitions) > 0 {
			for _, comp := range ev.Competitions[0].Competitors {
				if comp.HomeAway == "home" {
					home = comp.Team.DisplayName
				} else {
					away = comp.Team.DisplayName
				}
			}
		}
		if home == "" || away == "" {
			continue
		}
		matches = append(matches, Match{
			HomeTeam:    home,
			AwayTeam:    away,
			Competition: "ESPN",
			Sport:       "football",
			KickOff:     t,
			Duration:    defaultMatchDuration,
		})
	}
	return matches
}

// ── football-data.org parser ──────────────────────────────────────────────────

type fdResponse struct {
	Matches []struct {
		ID          int `json:"id"`
		Competition struct {
			Name string `json:"name"`
		} `json:"competition"`
		UtcDate  string `json:"utcDate"`
		HomeTeam struct {
			Name string `json:"name"`
		} `json:"homeTeam"`
		AwayTeam struct {
			Name string `json:"name"`
		} `json:"awayTeam"`
	} `json:"matches"`
}

func parseFootballDataResponse(data []byte) []Match {
	var r fdResponse
	if err := json.Unmarshal(data, &r); err != nil {
		return nil
	}

	var matches []Match
	for _, m := range r.Matches {
		t, err := time.Parse(time.RFC3339, m.UtcDate)
		if err != nil {
			continue
		}
		matches = append(matches, Match{
			HomeTeam:    m.HomeTeam.Name,
			AwayTeam:    m.AwayTeam.Name,
			Competition: m.Competition.Name,
			Sport:       "football",
			KickOff:     t,
			Duration:    defaultMatchDuration,
		})
	}
	return matches
}