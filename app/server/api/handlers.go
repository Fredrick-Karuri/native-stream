// api/handlers.go
// HTTP API: playlist, EPG, channel management, health, probe endpoints.

package api

import (
	"encoding/json"
	"fmt"
	"net/http"
	"strings"
	"time"
	"os"
	"log/slog"

	"github.com/fredrick-karuri/nativestream/server/epg"
	"github.com/fredrick-karuri/nativestream/server/playlist"
	"github.com/fredrick-karuri/nativestream/server/proxy"
	"github.com/fredrick-karuri/nativestream/server/store"
	"github.com/fredrick-karuri/nativestream/server/validator"
	"github.com/fredrick-karuri/nativestream/server/control"
	"nhooyr.io/websocket"
	"github.com/google/uuid"
)

type Handler struct {
	store     *store.Store
	epg       *epg.Engine
	proxy     *proxy.Proxy
	validator *validator.Validator
	startTime time.Time
	proxyCfg  proxy.Config
	serverAddr  string
	serverName  string
	hub        *control.Hub
}

func New(
	s *store.Store,
	e *epg.Engine,
	px *proxy.Proxy,
	v *validator.Validator,
	proxyCfg proxy.Config,
	serverAddr string,
	hub *control.Hub,
) *Handler {
	return &Handler{
		store:      s,
		epg:        e,
		proxy:      px,
		validator:  v,
		startTime:  time.Now(),
		proxyCfg:   proxyCfg,
		serverAddr: serverAddr,
		serverName: func() string { h, _ := os.Hostname(); return "NativeStream @ " + h }(),
		hub:        hub,
	}
}

// Router registers all routes and returns the mux.
func (h *Handler) RegisterRoutes(mux *http.ServeMux) {
	// Playlist & EPG
	mux.HandleFunc("GET /playlist.m3u", h.handlePlaylist)
	mux.HandleFunc("GET /epg.xml", h.handleEPG)

	// Proxy
	mux.HandleFunc("GET /stream/{id}/proxy/", h.proxy.ServeHTTP)
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

	// Local Media Connect
	mux.HandleFunc("GET /ws", h.handleWebSocket)
	mux.HandleFunc("GET /api/sessions", h.handleSessions)

}

// ── Playlist ──────────────────────────────────────────────────────────────────

func (h *Handler) handlePlaylist(w http.ResponseWriter, r *http.Request) {
	channels := h.store.ChannelsWithLink()
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
		"version":      "4.0",
		"server_name":  h.serverName,
		"addr":         h.serverAddr,
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
    h.store.DeleteAll()
    writeJSON(w, http.StatusOK, map[string]string{"status": "deleted"})
}

// ── Local Media Connect ───────────────────────────────────────────────────────

func (h *Handler) handleWebSocket(w http.ResponseWriter, r *http.Request) {
	conn, err := websocket.Accept(w, r, &websocket.AcceptOptions{
		InsecureSkipVerify: true, // LAN only — zero-auth phase
	})
	if err != nil {
		slog.Warn("lmc: websocket accept failed", "err", err)
		return
	}

	// Read registration message — first message must be register
	_, data, err := conn.Read(r.Context())
	if err != nil {
		slog.Warn("lmc: no register message received", "err", err)
		conn.CloseNow()
		return
	}

	var env control.Envelope
	if err := json.Unmarshal(data, &env); err != nil || env.Type != control.MsgRegister {
		slog.Warn("lmc: first message was not register")
		conn.CloseNow()
		return
	}

	payload, err := control.DecodePayload[control.RegisterPayload](env)
	if err != nil {
		slog.Warn("lmc: bad register payload", "err", err)
		conn.CloseNow()
		return
	}

	// Use client-supplied device_id from From field, or generate one
	deviceID := env.From
	if deviceID == "" {
		deviceID = uuid.NewString()
	}

	client := &control.Client{
		DeviceID: deviceID,
		Conn:     conn,
		Session: control.SessionInfo{
			DeviceID:    deviceID,
			Name:        payload.Name,
			Kind:        payload.Kind,
			ConnectedAt: time.Now(),
		},
	}

	h.hub.Register(client)

	// Send current session list immediately after registration
	sessions := h.hub.Sessions()
	sessionEnv, _ := control.NewEnvelope(
		control.MsgSessionList,
		"server",
		deviceID,
		control.SessionListPayload{Sessions: sessions},
	)
	client.Send(r.Context(), sessionEnv)

	// Block until connection closes
	control.ReadLoop(r.Context(), h.hub, client)
}

func (h *Handler) handleSessions(w http.ResponseWriter, r *http.Request) {
	writeJSON(w, http.StatusOK, map[string]any{
		"sessions": h.hub.Sessions(),
	})
}