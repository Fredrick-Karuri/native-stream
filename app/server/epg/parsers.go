// epg/parsers.go
// Parses JSON responses from ESPN and football-data.org into []Match.

package epg

import (
	"encoding/json"
	"time"
)

// ── ESPN parser ───────────────────────────────────────────────────────────────

type espnResponse struct {
	Events []struct {
		Name        string `json:"name"`
		Date        string `json:"date"`
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
			Duration:    110 * time.Minute,
		})
	}
	return matches
}

// ── football-data.org parser ──────────────────────────────────────────────────

type fdResponse struct {
	Matches []struct {
		ID          int    `json:"id"`
		Competition struct {
			Name string `json:"name"`
		} `json:"competition"`
		UtcDate string `json:"utcDate"`
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
			Duration:    110 * time.Minute,
		})
	}
	return matches
}