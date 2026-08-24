// api/handlers.go
// HTTP API: playlist, EPG, channel management, health, probe endpoints.

package api

import (
	"fmt"
	"log/slog"
	"net/http"
	"os"
	"strings"
	"time"

	"github.com/coder/websocket"
	"github.com/fredrick-karuri/nativestream/packages/mediaplane"
	"github.com/fredrick-karuri/nativestream/packages/proxy"
	streamv1 "github.com/fredrick-karuri/nativestream/sdk-gen/go/stream/v1"
	"github.com/fredrick-karuri/nativestream/server/control"
	"github.com/fredrick-karuri/nativestream/server/epg"
	"github.com/fredrick-karuri/nativestream/server/httpx"
	"github.com/fredrick-karuri/nativestream/server/intsafe"
	"github.com/fredrick-karuri/nativestream/server/playlist"
	"github.com/fredrick-karuri/nativestream/server/store"
	"github.com/fredrick-karuri/nativestream/server/validator"
	"github.com/google/uuid"
	"google.golang.org/protobuf/encoding/protojson"
	"google.golang.org/protobuf/proto"
	"google.golang.org/protobuf/types/known/timestamppb"
)

type Handler struct {
	store      *store.Store
	epg        *epg.Engine
	proxy      mediaplane.StreamProxy
	validator  *validator.Validator
	startTime  time.Time
	proxyCfg   proxy.Config
	serverAddr string
	serverName string
	hub        *control.Hub
	version    string

	pairing           *store.PairingSessionStore
	credentials       *store.CredentialStore
	pairStartLimiter  *ipRateLimiter
	pairStatusLimiter *ipRateLimiter
}

const (
	pairStartRateLimit   = 10 // requests per window
	pairStartRateWindow  = time.Minute
	pairStatusRateLimit  = 60 // ~2s client poll interval → ~30/min per device, headroom for jitter
	pairStatusRateWindow = time.Minute
)

func New(
	s *store.Store,
	e *epg.Engine,
	px mediaplane.StreamProxy,
	v *validator.Validator,
	proxyCfg proxy.Config,
	serverAddr string,
	hub *control.Hub,
	version string,
	pairing *store.PairingSessionStore,
	credentials *store.CredentialStore,
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
		version:    version,

		pairing:           pairing,
		credentials:       credentials,
		pairStartLimiter:  newIPRateLimiter(pairStartRateLimit, pairStartRateWindow),
		pairStatusLimiter: newIPRateLimiter(pairStatusRateLimit, pairStatusRateWindow),
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

	// Proxy config
	mux.HandleFunc("GET /api/proxy/config", h.handleGetProxyConfig)
	mux.HandleFunc("PUT /api/proxy/config", h.handlePutProxyConfig)

	// Local Media Connect — /ws itself is registered separately by main.go

	mux.HandleFunc("GET /api/sessions", h.handleSessions)

	// Pairing (admin) — authenticated, same mux as everything above
	mux.HandleFunc("GET /api/pair/pending", h.handlePairPending)
	mux.HandleFunc("POST /api/pair/approve/{session_id}", h.handlePairApprove)
	mux.HandleFunc("POST /api/pair/deny/{session_id}", h.handlePairDeny)

	// Credentials (admin) — authenticated, same mux as everything above
	mux.HandleFunc("GET /api/credentials", h.handleListCredentials)
	mux.HandleFunc("POST /api/credentials/revoke", h.handleRevokeCredential)

}

// RegisterWebSocketRoute exposes handleWebSocket for mounting outside the
// authenticated mux. /ws stays token-free — LAN-only casting clients don't
// carry the hosted API token, and LMC auth is explicitly out of scope here.
func (h *Handler) RegisterWebSocketRoute(w http.ResponseWriter, r *http.Request) {
	h.handleWebSocket(w, r)
}

// RegisterPairingDeviceRoutes exposes the two device-facing pairing
// endpoints for mounting outside the authenticated mux, alongside /ws.
// Unauthenticated by design — a device with no credential
// yet must be able to start and poll a pairing handshake.
func (h *Handler) RegisterPairingDeviceRoutes(mux *http.ServeMux) {
	mux.HandleFunc("POST /api/pair/start", h.handlePairStart)
	mux.HandleFunc("GET /api/pair/status/{session_id}", h.handlePairStatus)
}

// RegisterAdminPageRoute exposes the admin page shell for mounting outside
// the authenticated mux, alongside /ws and the pairing device routes. The
// HTML itself is unauthenticated, but every API call it makes is not — the
// token gate lives in the page's own JS, checked against real endpoints,
// not against the page load.
func (h *Handler) RegisterAdminPageRoute(mux *http.ServeMux) {
	mux.HandleFunc("GET /admin", h.handleAdminPage)
}

// ── Playlist ──────────────────────────────────────────────────────────────────

func (h *Handler) handlePlaylist(w http.ResponseWriter, r *http.Request) {
	channels := h.store.ChannelsWithLink()
	cfg := playlist.Config{
		ProxyEnabled: h.proxy.IsEnabled(),
		ServerAddr:   h.serverAddr,
	}
	w.Header().Set("Content-Type", "application/x-mpegurl; charset=utf-8")
	w.Header().Set("Cache-Control", "no-cache")
	if _, err := fmt.Fprint(w, playlist.Generate(channels, cfg)); err != nil {
		slog.Warn("handlePlaylist: write failed", "err", err)
	}
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
	if _, err := w.Write(data); err != nil {
		slog.Warn("handleEPG: write failed", "err", err)
	}
}

// ── Channel management ────────────────────────────────────────────────────────

func (h *Handler) handleListChannels(w http.ResponseWriter, r *http.Request) {
	channels := h.store.All()
	resp := &streamv1.ChannelListResponse{
		Channels: make([]*streamv1.ChannelResponse, len(channels)),
	}
	for i, ch := range channels {
		resp.Channels[i] = toProtoChannelResponse(ch)
	}
	httpx.WriteProtoJSON(w, http.StatusOK, resp)
}

func (h *Handler) handleGetChannel(w http.ResponseWriter, r *http.Request) {
	id := r.PathValue("id")
	ch := h.store.Get(id)
	if ch == nil {
		httpx.WriteProtoJSON(w, http.StatusNotFound, &streamv1.ErrorResponse{Error: "channel not found"})
		return
	}
	httpx.WriteProtoJSON(w, http.StatusOK, toProtoChannelDetail(ch))
}

func (h *Handler) handleCreateChannel(w http.ResponseWriter, r *http.Request) {
	var body streamv1.CreateChannelRequest
	if err := httpx.ReadProtoJSON(r, &body); err != nil {
		httpx.WriteProtoJSON(w, http.StatusBadRequest, &streamv1.ErrorResponse{Error: "invalid JSON"})
		return
	}
	if body.Name == "" {
		httpx.WriteProtoJSON(w, http.StatusBadRequest, &streamv1.ErrorResponse{Error: "name required"})
		return
	}

	id := slugify(body.Name)

	ch := &store.Channel{
		ID:         id,
		Name:       body.Name,
		GroupTitle: body.GroupTitle,
		TvgID:      body.TvgId,
		LogoURL:    body.LogoUrl,
		Keywords:   body.Keywords,
	}

	if body.StreamUrl != "" {
		link := &store.LinkScore{
			URL:       body.StreamUrl,
			ChannelID: id,
			State:     store.StateCandidate,
			Headers:   body.StreamHeaders,
		}
		ch.Candidates = []*store.LinkScore{link}
		h.validator.Submit(validator.Candidate{
			URL:       body.StreamUrl,
			ChannelID: id,
			Headers:   body.StreamHeaders,
		})
	}

	h.store.Add(ch)
	httpx.WriteProtoJSON(w, http.StatusCreated, toProtoChannelDetail(ch))
}

func (h *Handler) handleUpdateChannel(w http.ResponseWriter, r *http.Request) {
	id := r.PathValue("id")

	var body streamv1.UpdateChannelRequest
	if err := httpx.ReadProtoJSON(r, &body); err != nil {
		httpx.WriteProtoJSON(w, http.StatusBadRequest, &streamv1.ErrorResponse{Error: "invalid JSON"})
		return
	}

	updates := map[string]any{}
	if body.Name != nil {
		updates["name"] = *body.Name
	}
	if body.GroupTitle != nil {
		updates["group_title"] = *body.GroupTitle
	}
	if body.StreamUrl != nil {
		updates["stream_url"] = *body.StreamUrl
	}
	if body.Keywords != nil {
		kws := make([]interface{}, len(body.Keywords))
		for i, k := range body.Keywords {
			kws[i] = k
		}
		updates["keywords"] = kws
	}
	if body.StreamHeaders != nil {
		headers := make(map[string]interface{}, len(body.StreamHeaders))
		for k, v := range body.StreamHeaders {
			headers[k] = v
		}
		updates["stream_headers"] = headers
	}

	if err := h.store.Update(id, updates); err != nil {
		httpx.WriteProtoJSON(w, http.StatusNotFound, &streamv1.ErrorResponse{Error: err.Error()})
		return
	}

	if body.StreamUrl != nil && *body.StreamUrl != "" {
		h.validator.Submit(validator.Candidate{URL: *body.StreamUrl, ChannelID: id})
	}

	httpx.WriteProtoJSON(w, http.StatusOK, &streamv1.StatusResponse{Status: "updated"})
}

func (h *Handler) handleDeleteChannel(w http.ResponseWriter, r *http.Request) {
	id := r.PathValue("id")
	if err := h.store.Delete(id); err != nil {
		httpx.WriteProtoJSON(w, http.StatusNotFound, &streamv1.ErrorResponse{Error: err.Error()})
		return
	}
	httpx.WriteProtoJSON(w, http.StatusOK, &streamv1.StatusResponse{Status: "deleted"})
}

// ── Health & probe ────────────────────────────────────────────────────────────

func (h *Handler) handleHealth(w http.ResponseWriter, r *http.Request) {
	total, healthy := h.store.Count()

	resp := &streamv1.HealthResponse{
		Status:     "ok",
		Uptime:     time.Since(h.startTime).Round(time.Second).String(),
		Channels:   proto.Int32(intsafe.ToInt32(total)),
		Healthy:    proto.Int32(intsafe.ToInt32(healthy)),
		Version:    h.version,
		ServerName: proto.String(h.serverName),
		Addr:       proto.String(h.serverAddr),
	}
	if lp := h.validator.LastProbeTime(); !lp.IsZero() {
		resp.LastProbe = timestamppb.New(lp)
	}

	httpx.WriteProtoJSON(w, http.StatusOK, resp)
}

func (h *Handler) handleProbe(w http.ResponseWriter, r *http.Request) {
	h.validator.TriggerProbeAll(r.Context())
	httpx.WriteProtoJSON(w, http.StatusOK, &streamv1.StatusResponse{Status: "triggered"})
}

// ── Helpers ───────────────────────────────────────────────────────────────────

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
	httpx.WriteProtoJSON(w, http.StatusOK, &streamv1.StatusResponse{Status: "deleted"})
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
		if cerr := conn.CloseNow(); cerr != nil {
			slog.Debug("lmc: close after read failure", "err", cerr)
		}
		return
	}

	var env streamv1.Envelope
	if err := protojson.Unmarshal(data, &env); err != nil || env.Type != streamv1.MessageType_MESSAGE_TYPE_REGISTER {
		slog.Warn("lmc: first message was not register")
		if cerr := conn.CloseNow(); cerr != nil {
			slog.Debug("lmc: close after bad register type", "err", cerr)
		}
		return
	}

	var payload streamv1.RegisterPayload
	if err := protojson.Unmarshal([]byte(env.PayloadJson), &payload); err != nil {
		slog.Warn("lmc: bad register payload", "err", err)
		if cerr := conn.CloseNow(); cerr != nil {
			slog.Debug("lmc: close after bad payload", "err", cerr)
		}
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
			Kind:        control.ProtoKindToControlKind(payload.Kind),
			ConnectedAt: time.Now(),
		},
	}

	h.hub.Register(client)

	// Send current session list immediately after registration
	sessions := h.hub.Sessions()
	pbSessions := make([]*streamv1.SessionInfo, len(sessions))
	for i, s := range sessions {
		pbSessions[i] = toProtoSessionInfo(s)
	}
	sessionEnv, _ := control.NewProtoEnvelope(
		streamv1.MessageType_MESSAGE_TYPE_SESSION_LIST,
		"server",
		deviceID,
		&streamv1.SessionListPayload{Sessions: pbSessions},
	)
	if err := client.Send(r.Context(), sessionEnv); err != nil {
		slog.Warn("lmc: failed to send session list", "device_id", deviceID, "err", err)
	}

	// Block until connection closes
	control.ReadLoop(r.Context(), h.hub, client)
}

func (h *Handler) handleSessions(w http.ResponseWriter, r *http.Request) {
	sessions := h.hub.Sessions()
	pbSessions := make([]*streamv1.SessionInfo, len(sessions))
	for i, s := range sessions {
		pbSessions[i] = toProtoSessionInfo(s)
	}
	httpx.WriteProtoJSON(w, http.StatusOK, &streamv1.SessionsResponse{Sessions: pbSessions})
}

// ── Proxy config ──────────────────────────────────────────────────────────────

func (h *Handler) handleGetProxyConfig(w http.ResponseWriter, r *http.Request) {
	httpx.WriteProtoJSON(w, http.StatusOK, &streamv1.ProxyConfigResponse{
		Enabled: h.proxy.IsEnabled(),
	})
}

func (h *Handler) handlePutProxyConfig(w http.ResponseWriter, r *http.Request) {
	var req streamv1.UpdateProxyConfigRequest
	if err := httpx.ReadProtoJSON(r, &req); err != nil {
		httpx.WriteProtoJSON(w, http.StatusBadRequest, &streamv1.ErrorResponse{Error: "invalid JSON"})
		return
	}
	h.proxy.SetEnabled(req.Enabled)
	slog.Info("proxy toggled", "enabled", req.Enabled)
	httpx.WriteProtoJSON(w, http.StatusOK, &streamv1.ProxyConfigResponse{
		Enabled: h.proxy.IsEnabled(),
	})
}
