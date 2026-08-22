// packages/epg-sourcing/sourcing_test.go
//
// Unit tests for the ESPN and football-data parsers. Both are pure:
// []byte in, []Match out, no store, no network. That makes them the
// cheapest tests in the package — but the interesting behavior is all in
// the silent-skip paths (bad JSON, bad dates, missing team data), not the
// happy path, so most cases here target those.
//
// Moved from epg/parsers_test.go — assertions unchanged, only
// the package and (implicitly) import path changed.

package epgsourcing

import (
	"testing"
	"time"
)

// ── parseESPNResponse ────────────────────────────────────────────────────

func TestParseESPNResponse_ValidEventProducesOneMatch(t *testing.T) {
	body := `{
		"events": [{
			"name": "Arsenal at Chelsea",
			"date": "2026-08-12T15:00:00Z",
			"competitions": [{
				"competitors": [
					{"homeAway": "home", "team": {"displayName": "Chelsea"}},
					{"homeAway": "away", "team": {"displayName": "Arsenal"}}
				]
			}]
		}]
	}`

	got := parseESPNResponse([]byte(body))

	if len(got) != 1 {
		t.Fatalf("parseESPNResponse() returned %d matches, want 1", len(got))
	}
	m := got[0]
	if m.HomeTeam != "Chelsea" || m.AwayTeam != "Arsenal" {
		t.Errorf("teams = %q vs %q, want Chelsea vs Arsenal", m.HomeTeam, m.AwayTeam)
	}
	if m.Competition != "ESPN" {
		t.Errorf("Competition = %q, want %q", m.Competition, "ESPN")
	}
	if m.Sport != "football" {
		t.Errorf("Sport = %q, want %q", m.Sport, "football")
	}
	wantKickoff := time.Date(2026, 8, 12, 15, 0, 0, 0, time.UTC)
	if !m.KickOff.Equal(wantKickoff) {
		t.Errorf("KickOff = %v, want %v", m.KickOff, wantKickoff)
	}
	if m.Duration != defaultMatchDuration {
		t.Errorf("Duration = %v, want %v", m.Duration, defaultMatchDuration)
	}
}

func TestParseESPNResponse_MalformedJSONReturnsNilNotError(t *testing.T) {
	got := parseESPNResponse([]byte(`{not valid json`))

	if got != nil {
		t.Fatalf("parseESPNResponse() = %v, want nil on malformed JSON", got)
	}
}

func TestParseESPNResponse_EmptyEventsReturnsNil(t *testing.T) {
	got := parseESPNResponse([]byte(`{"events": []}`))

	if got != nil {
		t.Fatalf("parseESPNResponse() = %v, want nil for empty events", got)
	}
}

func TestParseESPNResponse_SkipsEventWithUnparsableDate(t *testing.T) {
	body := `{
		"events": [
			{
				"date": "not-a-real-date",
				"competitions": [{"competitors": [
					{"homeAway": "home", "team": {"displayName": "Chelsea"}},
					{"homeAway": "away", "team": {"displayName": "Arsenal"}}
				]}]
			},
			{
				"date": "2026-08-12T15:00:00Z",
				"competitions": [{"competitors": [
					{"homeAway": "home", "team": {"displayName": "Liverpool"}},
					{"homeAway": "away", "team": {"displayName": "Everton"}}
				]}]
			}
		]
	}`

	got := parseESPNResponse([]byte(body))

	if len(got) != 1 {
		t.Fatalf("parseESPNResponse() returned %d matches, want 1 (bad-date event skipped)", len(got))
	}
	if got[0].HomeTeam != "Liverpool" {
		t.Errorf("surviving match HomeTeam = %q, want Liverpool", got[0].HomeTeam)
	}
}

func TestParseESPNResponse_SkipsEventWithNoCompetitions(t *testing.T) {
	body := `{
		"events": [{
			"date": "2026-08-12T15:00:00Z",
			"competitions": []
		}]
	}`

	got := parseESPNResponse([]byte(body))

	if got != nil {
		t.Fatalf("parseESPNResponse() = %v, want nil when competitions is empty (home/away both blank)", got)
	}
}

func TestParseESPNResponse_SkipsEventMissingAwayTeam(t *testing.T) {
	body := `{
		"events": [{
			"date": "2026-08-12T15:00:00Z",
			"competitions": [{"competitors": [
				{"homeAway": "home", "team": {"displayName": "Chelsea"}}
			]}]
		}]
	}`

	got := parseESPNResponse([]byte(body))

	if got != nil {
		t.Fatalf("parseESPNResponse() = %v, want nil when away team is missing", got)
	}
}

func TestParseESPNResponse_MultipleEventsAllValid(t *testing.T) {
	body := `{
		"events": [
			{
				"date": "2026-08-12T15:00:00Z",
				"competitions": [{"competitors": [
					{"homeAway": "home", "team": {"displayName": "Chelsea"}},
					{"homeAway": "away", "team": {"displayName": "Arsenal"}}
				]}]
			},
			{
				"date": "2026-08-13T18:30:00Z",
				"competitions": [{"competitors": [
					{"homeAway": "home", "team": {"displayName": "Liverpool"}},
					{"homeAway": "away", "team": {"displayName": "Everton"}}
				]}]
			}
		]
	}`

	got := parseESPNResponse([]byte(body))

	if len(got) != 2 {
		t.Fatalf("parseESPNResponse() returned %d matches, want 2", len(got))
	}
}

// ── parseFootballDataResponse ────────────────────────────────────────────

func TestParseFootballDataResponse_ValidMatchProducesOneMatch(t *testing.T) {
	body := `{
		"matches": [{
			"id": 1,
			"competition": {"name": "Premier League"},
			"utcDate": "2026-08-12T15:00:00Z",
			"homeTeam": {"name": "Chelsea"},
			"awayTeam": {"name": "Arsenal"}
		}]
	}`

	got := parseFootballDataResponse([]byte(body))

	if len(got) != 1 {
		t.Fatalf("parseFootballDataResponse() returned %d matches, want 1", len(got))
	}
	m := got[0]
	if m.HomeTeam != "Chelsea" || m.AwayTeam != "Arsenal" {
		t.Errorf("teams = %q vs %q, want Chelsea vs Arsenal", m.HomeTeam, m.AwayTeam)
	}
	if m.Competition != "Premier League" {
		t.Errorf("Competition = %q, want %q", m.Competition, "Premier League")
	}
	if m.Duration != defaultMatchDuration {
		t.Errorf("Duration = %v, want %v", m.Duration, defaultMatchDuration)
	}
}

func TestParseFootballDataResponse_MalformedJSONReturnsNil(t *testing.T) {
	got := parseFootballDataResponse([]byte(`not json at all`))

	if got != nil {
		t.Fatalf("parseFootballDataResponse() = %v, want nil on malformed JSON", got)
	}
}

func TestParseFootballDataResponse_SkipsMatchWithUnparsableDate(t *testing.T) {
	body := `{
		"matches": [
			{
				"utcDate": "garbage",
				"competition": {"name": "Premier League"},
				"homeTeam": {"name": "Chelsea"},
				"awayTeam": {"name": "Arsenal"}
			},
			{
				"utcDate": "2026-08-12T15:00:00Z",
				"competition": {"name": "La Liga"},
				"homeTeam": {"name": "Barcelona"},
				"awayTeam": {"name": "Real Madrid"}
			}
		]
	}`

	got := parseFootballDataResponse([]byte(body))

	if len(got) != 1 {
		t.Fatalf("parseFootballDataResponse() returned %d matches, want 1 (bad-date entry skipped)", len(got))
	}
	if got[0].Competition != "La Liga" {
		t.Errorf("surviving match Competition = %q, want La Liga", got[0].Competition)
	}
}

func TestParseFootballDataResponse_EmptyMatchesReturnsNil(t *testing.T) {
	got := parseFootballDataResponse([]byte(`{"matches": []}`))

	if got != nil {
		t.Fatalf("parseFootballDataResponse() = %v, want nil for empty matches", got)
	}
}

func TestParseFootballDataResponse_MissingTeamNamesYieldEmptyStringsNotSkip(t *testing.T) {
	body := `{
		"matches": [{
			"utcDate": "2026-08-12T15:00:00Z",
			"competition": {"name": "Premier League"},
			"homeTeam": {"name": ""},
			"awayTeam": {"name": ""}
		}]
	}`

	got := parseFootballDataResponse([]byte(body))

	if len(got) != 1 {
		t.Fatalf("parseFootballDataResponse() returned %d matches, want 1 (not skipped)", len(got))
	}
	if got[0].HomeTeam != "" || got[0].AwayTeam != "" {
		t.Errorf("teams = %q vs %q, want both empty", got[0].HomeTeam, got[0].AwayTeam)
	}
}

func TestParseFootballDataResponse_MultipleMatchesAllValid(t *testing.T) {
	body := `{
		"matches": [
			{
				"utcDate": "2026-08-12T15:00:00Z",
				"competition": {"name": "Premier League"},
				"homeTeam": {"name": "Chelsea"},
				"awayTeam": {"name": "Arsenal"}
			},
			{
				"utcDate": "2026-08-13T18:30:00Z",
				"competition": {"name": "La Liga"},
				"homeTeam": {"name": "Barcelona"},
				"awayTeam": {"name": "Real Madrid"}
			}
		]
	}`

	got := parseFootballDataResponse([]byte(body))

	if len(got) != 2 {
		t.Fatalf("parseFootballDataResponse() returned %d matches, want 2", len(got))
	}
}