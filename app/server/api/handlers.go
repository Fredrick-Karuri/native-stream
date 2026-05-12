// api/handlers.go — NS-121, NS-122, NS-123, NS-124, NS-125
// HTTP API: playlist, EPG, channel management, health, probe endpoints.

package api

import (
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"strings"
	"time"

	"github.com/fredrick-karuri/nativestream/server/epg"
	"github.com/fredrick-karuri/nativestream/server/playlist"
	"github.com/fredrick-karuri/nativestream/server/proxy"
	"github.com/fredrick-karuri/nativestream/server/store"
	"github.com/fredrick-karuri/nativestream/server/validator"
)

type Handler struct {
	store     *store.Store
	epg       *epg.Engine
	proxy     *proxy.Proxy
	validator *validator.Validator
	startTime time.Time
	proxyCfg  proxy.Config
	serverAddr string
}

func New(
	s *store.Store,
	e *epg.Engine,
	px *proxy.Proxy,
	v *validator.Validator,
	proxyCfg proxy.Config,
	serverAddr string,
) *Handler {
	return &Handler{
		store:      s,
		epg:        e,
		proxy:      px,
		validator:  v,
		startTime:  time.Now(),
		proxyCfg:   proxyCfg,
		serverAddr: serverAddr,
	}
}

// Router registers all routes and returns the mux.
func (h *Handler) RegisterRoutes(mux *http.ServeMux) {
	mux.HandleFunc("POST /api/import/m3u", h.handleImportM3U)

	// Playlist & EPG
	mux.HandleFunc("GET /playlist.m3u", h.handlePlaylist)
	mux.HandleFunc("GET /epg.xml", h.handleEPG)
	mux.HandleFunc("GET /api/epg/matches", h.handleEPGMatches)

	// Proxy
	mux.HandleFunc("GET /stream/{id}/proxy", h.proxy.ServeHTTP)

	// Channel management
	mux.HandleFunc("GET /api/channels", h.handleListChannels)
	mux.HandleFunc("GET /api/channels/{id}", h.handleGetChannel)
	mux.HandleFunc("POST /api/channels", h.handleCreateChannel)
	mux.HandleFunc("PUT /api/channels/{id}", h.handleUpdateChannel)
	mux.HandleFunc("DELETE /api/channels/{id}", h.handleDeleteChannel)
	mux.HandleFunc("DELETE /api/channels", h.handleDeleteAllChannels)

	// Health & probe
	mux.HandleFunc("GET /api/health", h.handleHealth)
	mux.HandleFunc("POST /api/probe", h.handleProbe)


}

// ── Playlist ──────────────────────────────────────────────────────────────────

func (h *Handler) handlePlaylist(w http.ResponseWriter, r *http.Request) {
	channels := h.store.HealthyChannels()
	cfg := playlist.Config{
		ProxyEnabled: h.proxyCfg.Enabled,
		ServerAddr:   h.serverAddr,
	}
	w.Header().Set("Content-Type", "application/x-mpegurl; charset=utf-8")
	w.Header().Set("Cache-Control", "no-cache")
	fmt.Fprint(w, playlist.Generate(channels, cfg))
}

// ── EPG ───────────────────────────────────────────────────────────────────────

func (h *Handler) handleEPG(w http.ResponseWriter, r *http.Request) {
	data := h.epg.ServeXMLTV()
	if len(data) == 0 {
		http.Error(w, "EPG not yet available", http.StatusServiceUnavailable)
		return
	}
	w.Header().Set("Content-Type", "application/xml; charset=utf-8")
	w.Header().Set("Cache-Control", "max-age=3600")
	w.Write(data)
}

// ── Channel management ────────────────────────────────────────────────────────

func (h *Handler) handleListChannels(w http.ResponseWriter, r *http.Request) {
	channels := h.store.All()
	type row struct {
		ID             string  `json:"id"`
		Name           string  `json:"name"`
		GroupTitle     string  `json:"group_title"`
		TvgID          string  `json:"tvg_id"`
		LogoURL        string  `json:"logo_url"`
		Healthy        bool    `json:"healthy"`
		ActiveScore    float64 `json:"active_score"`
		CandidateCount int     `json:"candidate_count"`
	}
	rows := make([]row, len(channels))
	for i, ch := range channels {
		r := row{
			ID:             ch.ID,
			Name:           ch.Name,
			GroupTitle:     ch.GroupTitle,
			TvgID:          ch.TvgID,
			LogoURL:        ch.LogoURL,
			CandidateCount: len(ch.Candidates),
		}
		if ch.ActiveLink != nil {
			r.ActiveScore = ch.ActiveLink.Score
			r.Healthy = ch.ActiveLink.Score >= 0.3
		}
		rows[i] = r
	}
	writeJSON(w, http.StatusOK, map[string]any{"channels": rows})
}

func (h *Handler) handleGetChannel(w http.ResponseWriter, r *http.Request) {
	id := r.PathValue("id")
	ch := h.store.Get(id)
	if ch == nil {
		writeJSON(w, http.StatusNotFound, map[string]string{"error": "channel not found"})
		return
	}
	writeJSON(w, http.StatusOK, ch)
}

func (h *Handler) handleCreateChannel(w http.ResponseWriter, r *http.Request) {
	var body struct {
		ID         string   `json:"id"`
		Name       string   `json:"name"`
		GroupTitle string   `json:"group_title"`
		TvgID      string   `json:"tvg_id"`
		LogoURL    string   `json:"logo_url"`
		StreamURL  string   `json:"stream_url"`
		Keywords   []string `json:"keywords"`
	}
	if err := json.NewDecoder(r.Body).Decode(&body); err != nil {
		writeJSON(w, http.StatusBadRequest, map[string]string{"error": "invalid JSON"})
		return
	}
	if body.Name == "" {
		writeJSON(w, http.StatusBadRequest, map[string]string{"error": "name required"})
		return
	}

	// Auto-generate ID from name if not provided
	id := body.ID
	if id == "" {
		id = slugify(body.Name)
	}

	ch := &store.Channel{
		ID:         id,
		Name:       body.Name,
		GroupTitle: body.GroupTitle,
		TvgID:      body.TvgID,
		LogoURL:    body.LogoURL,
		Keywords:   body.Keywords,
	}

	if body.StreamURL != "" {
		link := &store.LinkScore{
			URL:       body.StreamURL,
			ChannelID: id,
			State:     store.StateCandidate,
		}
		ch.Candidates = []*store.LinkScore{link}
		// Submit for immediate validation
		h.validator.Submit(validator.Candidate{
			URL:       body.StreamURL,
			ChannelID: id,
		})
	}

	h.store.Add(ch)
	writeJSON(w, http.StatusCreated, ch)
}

func (h *Handler) handleUpdateChannel(w http.ResponseWriter, r *http.Request) {
	id := r.PathValue("id")

	var body map[string]any
	if err := json.NewDecoder(r.Body).Decode(&body); err != nil {
		writeJSON(w, http.StatusBadRequest, map[string]string{"error": "invalid JSON"})
		return
	}

	if err := h.store.Update(id, body); err != nil {
		writeJSON(w, http.StatusNotFound, map[string]string{"error": err.Error()})
		return
	}

	// If a new stream_url was given, submit immediately for validation
	if url, ok := body["stream_url"].(string); ok && url != "" {
		h.validator.Submit(validator.Candidate{URL: url, ChannelID: id})
	}

	writeJSON(w, http.StatusOK, map[string]string{"status": "updated"})
}

func (h *Handler) handleDeleteChannel(w http.ResponseWriter, r *http.Request) {
	id := r.PathValue("id")
	if err := h.store.Delete(id); err != nil {
		writeJSON(w, http.StatusNotFound, map[string]string{"error": err.Error()})
		return
	}
	writeJSON(w, http.StatusOK, map[string]string{"status": "deleted"})
}

// ── Health & probe ────────────────────────────────────────────────────────────

func (h *Handler) handleHealth(w http.ResponseWriter, r *http.Request) {
	total, healthy := h.store.Count()
	writeJSON(w, http.StatusOK, map[string]any{
		"status":       "ok",
		"uptime":       time.Since(h.startTime).Round(time.Second).String(),
		"channels":     total,
		"healthy":      healthy,
		"last_probe":   h.validator.LastProbeTime(),
	})
}

func (h *Handler) handleProbe(w http.ResponseWriter, r *http.Request) {
	h.validator.TriggerProbeAll()
	writeJSON(w, http.StatusOK, map[string]string{"status": "triggered"})
}

// ── Helpers ───────────────────────────────────────────────────────────────────

func writeJSON(w http.ResponseWriter, status int, v any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	json.NewEncoder(w).Encode(v)
}

func slugify(s string) string {
	s = strings.ToLower(s)
	var out strings.Builder
	for _, r := range s {
		if r >= 'a' && r <= 'z' || r >= '0' && r <= '9' {
			out.WriteRune(r)
		} else if r == ' ' || r == '_' {
			out.WriteRune('-')
		}
	}
	return strings.Trim(out.String(), "-")
}

func (h *Handler) handleDeleteAllChannels(w http.ResponseWriter, r *http.Request) {
    channels := h.store.All()
    for _, ch := range channels {
        h.store.Delete(ch.ID)
    }
    writeJSON(w, http.StatusOK, map[string]any{"deleted": len(channels)})
}

func (h *Handler) handleImportM3U(w http.ResponseWriter, r *http.Request) {
    var body struct {
        URL string `json:"url"`
    }
    if err := json.NewDecoder(r.Body).Decode(&body); err != nil || body.URL == "" {
        writeJSON(w, http.StatusBadRequest, map[string]string{"error": "url required"})
        return
    }

    resp, err := http.Get(body.URL)
    if err != nil || resp.StatusCode != 200 {
        writeJSON(w, http.StatusBadGateway, map[string]string{"error": "failed to fetch M3U"})
        return
    }
    defer resp.Body.Close()

    entries, err := parseM3U(resp.Body)
    if err != nil {
        writeJSON(w, http.StatusUnprocessableEntity, map[string]string{"error": err.Error()})
        return
    }

    imported := 0
    for _, e := range entries {
        id := slugify(e.name)
        if id == "" { continue }
        ch := &store.Channel{
            ID:         id,
            Name:       e.name,
            GroupTitle: e.groupTitle,
            TvgID:      e.tvgID,
            LogoURL:    e.logoURL,
            Keywords:   autoKeywords(e.name, e.groupTitle),
        }
        if e.streamURL != "" {
            link := &store.LinkScore{URL: e.streamURL, ChannelID: id, State: store.StateCandidate}
            ch.Candidates = []*store.LinkScore{link}
            h.validator.Submit(validator.Candidate{URL: e.streamURL, ChannelID: id})
        }
        h.store.Add(ch)
        imported++
    }

    writeJSON(w, http.StatusOK, map[string]any{"imported": imported})
}

type m3uEntry struct {
    name, groupTitle, tvgID, logoURL, streamURL string
}

func parseM3U(r io.Reader) ([]m3uEntry, error) {
    data, err := io.ReadAll(r)
    if err != nil { return nil, err }
    lines := strings.Split(string(data), "\n")

    var entries []m3uEntry
    var pending *m3uEntry

    for _, raw := range lines {
        line := strings.TrimSpace(raw)
        if line == "" || line == "#EXTM3U" { continue }

        if strings.HasPrefix(line, "#EXTINF:") {
            pending = &m3uEntry{}
            pending.name = extinfName(line)
            pending.tvgID = extinfAttr("tvg-id", line)
            pending.groupTitle = extinfAttr("group-title", line)
            pending.logoURL = extinfAttr("tvg-logo", line)
            if pending.groupTitle == "" { pending.groupTitle = "Uncategorised" }
            continue
        }

        if strings.HasPrefix(line, "#") { continue }

        if pending != nil {
            pending.streamURL = line
            entries = append(entries, *pending)
            pending = nil
        }
    }
    return entries, nil
}

func extinfName(line string) string {
    if i := strings.LastIndex(line, ","); i >= 0 {
        return strings.TrimSpace(line[i+1:])
    }
    return ""
}

func extinfAttr(key, line string) string {
    prefix := key + `="`
    i := strings.Index(line, prefix)
    if i < 0 { return "" }
    rest := line[i+len(prefix):]
    if j := strings.Index(rest, `"`); j >= 0 { return rest[:j] }
    return ""
}

// autoKeywords seeds keywords from name and group for EPG matching.
func autoKeywords(name, group string) []string {
    seen := map[string]bool{}
    var kws []string
    for _, s := range []string{name, group} {
        w := strings.ToLower(strings.TrimSpace(s))
        if w != "" && !seen[w] {
            seen[w] = true
            kws = append(kws, w)
        }
    }
    return kws
}

func (h *Handler) handleEPGMatches(w http.ResponseWriter, r *http.Request) {
    matches := h.epg.Matches()
    type matchRow struct {
        ID          string    `json:"id"`
        HomeTeam    string    `json:"home_team"`
        AwayTeam    string    `json:"away_team"`
        Competition string    `json:"competition"`
        Sport       string    `json:"sport"`
        KickOff     time.Time `json:"kick_off"`
        DurationMin int       `json:"duration_min"`
    }
    rows := make([]matchRow, len(matches))
    for i, m := range matches {
        dur := m.Duration
        if dur == 0 { dur = 110 * time.Minute }
        rows[i] = matchRow{
            ID:          m.ID,
            HomeTeam:    m.HomeTeam,
            AwayTeam:    m.AwayTeam,
            Competition: m.Competition,
            Sport:       m.Sport,
            KickOff:     m.KickOff,
            DurationMin: int(dur.Minutes()),
        }
    }
    writeJSON(w, http.StatusOK, map[string]any{"matches": rows})
}