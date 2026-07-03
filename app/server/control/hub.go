// server/control/hub.go
//
// WebSocket hub — central broker for all LMC control messages.
// Routes envelopes by To field, maintains session registry,
// handles pull-back, and pings clients on a 30s heartbeat.

package control

import (
	"context"
	"encoding/json"
	"log/slog"
	"sync"
	"time"

	"nhooyr.io/websocket"
)

const (
	pingInterval        = 30 * time.Second
	pongDeadline        = 10 * time.Second
	maxMissedPongs      = 2
	serverDeviceID      = "server"
)

// Client represents a single connected WebSocket device.
type Client struct {
	DeviceID string
	Conn     *websocket.Conn
	Session  SessionInfo
	missedPongs int
	mu       sync.Mutex
}

// send writes an Envelope to the client connection.
func (c *Client) Send(ctx context.Context, env Envelope) error {
	data, err := json.Marshal(env)
	if err != nil {
		return err
	}
	c.mu.Lock()
	defer c.mu.Unlock()
	return c.Conn.Write(ctx, websocket.MessageText, data)
}

// Hub brokers all control messages between connected clients.
type Hub struct {
	mu         sync.RWMutex
	clients    map[string]*Client // device_id → Client
	register   chan *Client
	unregister chan *Client
	route      chan Envelope
}

// NewHub constructs a ready Hub.
func NewHub() *Hub {
	return &Hub{
		clients:    make(map[string]*Client),
		register:   make(chan *Client, 16),
		unregister: make(chan *Client, 16),
		route:      make(chan Envelope, 256),
	}
}

// Register queues a client for registration.
func (h *Hub) Register(c *Client) { h.register <- c }

// Unregister queues a client for removal.
func (h *Hub) Unregister(c *Client) { h.unregister <- c }

// Dispatch queues an envelope for routing.
func (h *Hub) Dispatch(env Envelope) { h.route <- env }

// Sessions returns a snapshot of current session list.
func (h *Hub) Sessions() []SessionInfo {
	h.mu.RLock()
	defer h.mu.RUnlock()
	sessions := make([]SessionInfo, 0, len(h.clients))
	for _, c := range h.clients {
		sessions = append(sessions, c.Session)
	}
	return sessions
}

// Run starts the hub event loop. Blocks until ctx is cancelled.
func (h *Hub) Run(ctx context.Context) {
	ticker := time.NewTicker(pingInterval)
	defer ticker.Stop()

	for {
		select {
		case <-ctx.Done():
			return

		case c := <-h.register:
			h.mu.Lock()
			h.clients[c.DeviceID] = c
			h.mu.Unlock()
			slog.Info("lmc: device registered",
				"device_id", c.DeviceID,
				"name", c.Session.Name,
				"kind", c.Session.Kind,
			)
			h.broadcastSessionList(ctx)

		case c := <-h.unregister:
			h.mu.Lock()
			delete(h.clients, c.DeviceID)
			h.mu.Unlock()
			slog.Info("lmc: device disconnected", "device_id", c.DeviceID)
			h.broadcastSessionList(ctx)

		case env := <-h.route:
			h.handleEnvelope(ctx, env)

		case <-ticker.C:
			h.pingAll(ctx)
		}
	}
}

// handleEnvelope routes or processes an inbound envelope.
func (h *Hub) handleEnvelope(ctx context.Context, env Envelope) {
	switch env.Type {
	case MsgStateUpdate:
		h.applyStateUpdate(env)
		h.broadcastSessionList(ctx)
		// state_update is hub-internal — no forwarding needed
	case MsgPullBack:
		h.handlePullBack(ctx, env)
	case MsgPong:
		h.handlePong(env)
	case MsgPing:
		// clients should not send ping — ignore
	case MsgVolumeSet:
		h.forwardEnvelope(ctx, env) // unicast to target,
	default:
		h.forwardEnvelope(ctx, env)
	}
}

// forwardEnvelope delivers an envelope to its target(s).
func (h *Hub) forwardEnvelope(ctx context.Context, env Envelope) {
	h.mu.RLock()
	defer h.mu.RUnlock()

	if env.To == "broadcast" {
		for _, c := range h.clients {
			if c.DeviceID == env.From {
				continue // don't echo back to sender
			}
			if err := c.Send(ctx, env); err != nil {
				slog.Warn("lmc: forward failed", "to", c.DeviceID, "err", err)
			}
		}
		return
	}

	target, ok := h.clients[env.To]
	if !ok {
		slog.Warn("lmc: target not found", "to", env.To)
		return
	}
	if err := target.Send(ctx, env); err != nil {
		slog.Warn("lmc: unicast failed", "to", env.To, "err", err)
	}
}

// applyStateUpdate refreshes the session registry from a target's report.
func (h *Hub) applyStateUpdate(env Envelope) {
	payload, err := DecodePayload[StateUpdatePayload](env)
	if err != nil {
		slog.Warn("lmc: bad state_update payload", "err", err)
		return
	}
	h.mu.Lock()
	if c, ok := h.clients[env.From]; ok {
		c.Session.ChannelID   = payload.ChannelID
		c.Session.ChannelName = payload.ChannelName
		c.Session.StreamURL   = payload.StreamURL
		c.Session.Playing     = payload.Playing
		c.Session.Volume      = payload.Volume
	}
	h.mu.Unlock()
}

// handlePullBack reads the target's session and sends pull_back_ack to requester.
// Then sends stop to the target so both devices don't play simultaneously.
func (h *Hub) handlePullBack(ctx context.Context, env Envelope) {
	payload, err := DecodePayload[PullBackPayload](env)
	if err != nil {
		slog.Warn("lmc: bad pull_back payload", "err", err)
		return
	}

	h.mu.RLock()
	target, ok := h.clients[payload.FromDevice]
	h.mu.RUnlock()

	if !ok {
		slog.Warn("lmc: pull_back target not found", "from_device", payload.FromDevice)
		return
	}

	// Build ack with target's current stream state
	ack, err := NewEnvelope(MsgPullBackAck, serverDeviceID, env.From, PullBackAckPayload{
		ChannelID:   target.Session.ChannelID,
		ChannelName: target.Session.ChannelName,
		StreamURL:   target.Session.StreamURL,
	})
	if err != nil {
		slog.Warn("lmc: pull_back_ack build failed", "err", err)
		return
	}

	h.mu.RLock()
	controller, controllerFound := h.clients[env.From]
	h.mu.RUnlock()

	if !controllerFound {
		return
	}

	// Send ack to controller
	if err := controller.Send(ctx, ack); err != nil {
		slog.Warn("lmc: pull_back_ack send failed", "err", err)
		return
	}

	// Stop the target
	stop, _ := NewEnvelope(MsgStop, serverDeviceID, payload.FromDevice, struct{}{})
	if err := target.Send(ctx, stop); err != nil {
		slog.Warn("lmc: stop after pull_back failed", "err", err)
	}

	slog.Info("lmc: pull_back completed",
		"controller", env.From,
		"target", payload.FromDevice,
		"channel_id", target.Session.ChannelID,
	)
}

// handlePong resets the missed pong counter for the sending device.
func (h *Hub) handlePong(env Envelope) {
	h.mu.Lock()
	if c, ok := h.clients[env.From]; ok {
		c.missedPongs = 0
	}
	h.mu.Unlock()
}

// broadcastSessionList sends the current session list to all connected clients.
func (h *Hub) broadcastSessionList(ctx context.Context) {
	h.mu.RLock()
	sessions := make([]SessionInfo, 0, len(h.clients))
	for _, c := range h.clients {
		sessions = append(sessions, c.Session)
	}
	clients := make([]*Client, 0, len(h.clients))
	for _, c := range h.clients {
		clients = append(clients, c)
	}
	h.mu.RUnlock()

	env, err := NewEnvelope(MsgSessionList, serverDeviceID, "broadcast", SessionListPayload{
		Sessions: sessions,
	})
	if err != nil {
		return
	}

	for _, c := range clients {
		if err := c.Send(ctx, env); err != nil {
			slog.Warn("lmc: session_list send failed", "to", c.DeviceID, "err", err)
		}
	}
}

// pingAll sends a ping to every client and removes those that missed too many.
func (h *Hub) pingAll(ctx context.Context) {
	h.mu.Lock()
	var stale []string
	for id, c := range h.clients {
		if c.missedPongs >= maxMissedPongs {
			stale = append(stale, id)
			continue
		}
		c.missedPongs++
	}
	for _, id := range stale {
		slog.Info("lmc: removing unresponsive client", "device_id", id)
		delete(h.clients, id)
	}
	clients := make([]*Client, 0, len(h.clients))
	for _, c := range h.clients {
		clients = append(clients, c)
	}
	h.mu.Unlock()

	ping, _ := NewEnvelope(MsgPing, serverDeviceID, "broadcast", struct{}{})
	for _, c := range clients {
		if err := c.Send(ctx, ping); err != nil {
			slog.Warn("lmc: ping failed", "device_id", c.DeviceID, "err", err)
		}
	}

	if len(stale) > 0 {
		h.broadcastSessionList(ctx)
	}
}