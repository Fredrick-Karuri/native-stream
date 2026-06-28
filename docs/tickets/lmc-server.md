# NativeStream — Local Media Connect: Server Tickets

**Platform:** Go 1.22+
**Stack:** net/http · gorilla/websocket (or nhooyr.io/websocket) · mDNS (zeroconf)
**Feature:** WebSocket broker + control service advertisement for Local Media Connect
**Last Updated:** 2026-06-11
**Principle:** Go server is the broker. Devices never connect peer-to-peer.

---

## How to Read This

- **ID** — `LMC-SRV-001` etc.
- **Effort** — S (< 2hrs), M (half day), L (full day)
- **Needs** — files to read before starting
- **Done when** — observable acceptance criteria

---

## Dependency Order

```
LMC-SRV-001   Message protocol + envelope definition     ← unblocks everything
LMC-SRV-002   WebSocket hub (broker)                     ← unblocks 003, 004
LMC-SRV-003   /ws endpoint + session registry            ← unblocks 004
LMC-SRV-004   mDNS control service advertisement        ← unblocks client work
LMC-SRV-005   /api/sessions endpoint                     ← unblocks client discovery UI
```

---

## Summary

| Epic | Tickets | Effort |
|---|---|---|
| LMC-SRV-E01 Protocol | 001 | ~2hrs |
| LMC-SRV-E02 Broker | 002–003 | ~1 day |
| LMC-SRV-E03 Discovery | 004–005 | ~0.5 days |
| **Total** | **5 tickets** | **~1.5 days** |

---

## LMC-SRV-E01 — Protocol

### LMC-SRV-001 · Message protocol + envelope definition · S

**Needs:**
- `api/handlers.go`

**Description:**
Define the JSON message envelope used for all control messages. Every message
type — now and future (including TV) — uses this envelope. The `auth` field
is optional and ignored in zero-auth mode but present in the contract.

**Envelope:**
```json
{
  "type":    "play",
  "from":    "device-uuid",
  "to":      "device-uuid | broadcast",
  "auth":    null,
  "payload": { ... }
}
```

**Message types (v1):**

| type | direction | payload |
|---|---|---|
| `register` | device → server | `{ "name": "Fredrick's Phone", "kind": "controller \| target" }` |
| `session_list` | server → device | `{ "sessions": [SessionInfo] }` |
| `play` | controller → target | `{ "channel_id": "...", "stream_url": "..." }` |
| `stop` | controller → target | `{}` |
| `pull_back` | controller → server | `{ "from_device": "device-uuid" }` |
| `pull_back_ack` | server → controller | `{ "channel_id": "...", "stream_url": "..." }` |
| `state_update` | target → server (broadcast) | `{ "channel_id": "...", "playing": true }` |
| `ping` / `pong` | bidirectional | `{}` |

**Go types — new file `control/protocol.go`:**
```go
package control

type MessageType string

const (
    MsgRegister     MessageType = "register"
    MsgSessionList  MessageType = "session_list"
    MsgPlay         MessageType = "play"
    MsgStop         MessageType = "stop"
    MsgPullBack     MessageType = "pull_back"
    MsgPullBackAck  MessageType = "pull_back_ack"
    MsgStateUpdate  MessageType = "state_update"
    MsgPing         MessageType = "ping"
    MsgPong         MessageType = "pong"
)

type Envelope struct {
    Type    MessageType     `json:"type"`
    From    string          `json:"from"`
    To      string          `json:"to"`      // device UUID or "broadcast"
    Auth    *string         `json:"auth"`    // nil = zero-auth; string = token (future)
    Payload json.RawMessage `json:"payload"`
}

type SessionInfo struct {
    DeviceID   string `json:"device_id"`
    Name       string `json:"name"`
    Kind       string `json:"kind"`        // "controller" | "target"
    ChannelID  string `json:"channel_id"`  // "" if idle
    Playing    bool   `json:"playing"`
    ConnectedAt time.Time `json:"connected_at"`
}
```

**Done when:**
- `Envelope` marshals/unmarshals correctly with `json.RawMessage` payload
- All message type constants defined
- `SessionInfo` includes all fields needed by client discovery UI
- `auth` field present but nullable — zero-auth works with `auth: null`

---

## LMC-SRV-E02 — Broker

### LMC-SRV-002 · WebSocket hub · M

**Needs:**
- `LMC-SRV-001`
- `cmd/main.go`

**Description:**
Central broker that manages connected WebSocket clients, routes messages,
and maintains session state. New file `control/hub.go`.

**Responsibilities:**
- Register/unregister clients on connect/disconnect
- Route `Envelope` by `to` field: unicast (device UUID) or broadcast
- Handle `pull_back`: read target's current `SessionInfo`, build
  `pull_back_ack` with stream URL, send to requesting controller
- Broadcast `session_list` to all clients on any session state change
- Ping all clients every 30s, remove unresponsive ones after 2 missed pongs

```go
type Hub struct {
    mu       sync.RWMutex
    clients  map[string]*Client   // device_id → Client
    sessions map[string]*SessionInfo
    broadcast chan Envelope
    register  chan *Client
    unregister chan *Client
}

func (h *Hub) Run(ctx context.Context) { ... }
func (h *Hub) route(env Envelope) { ... }
func (h *Hub) handlePullBack(env Envelope) { ... }
func (h *Hub) broadcastSessionList() { ... }
```

**Done when:**
- Messages with `to: "broadcast"` reach all connected clients
- Messages with `to: "<device-id>"` reach only that client
- `pull_back` triggers `pull_back_ack` to requesting controller with
  current target's channel_id and stream_url
- Session list broadcast fires on every register/unregister/state_update
- Disconnected clients are removed within 60s via ping timeout

---

### LMC-SRV-003 · /ws endpoint + session registry · M

**Needs:**
- `LMC-SRV-002`
- `api/handlers.go`

**Description:**
HTTP upgrade endpoint at `GET /ws` that accepts WebSocket connections,
assigns a device UUID if not provided, and hands the connection to the Hub.

Add WebSocket dependency:
```bash
go get nhooyr.io/websocket
```

**Client lifecycle:**
1. Connect to `ws://server:8888/ws`
2. Send `register` message with `name` and `kind`
3. Server responds with `session_list`
4. Client sends/receives messages via hub routing
5. On disconnect: hub removes client, broadcasts updated session list

**Handler:**
```go
func (h *Handler) handleWebSocket(w http.ResponseWriter, r *http.Request) {
    conn, err := websocket.Accept(w, r, &websocket.AcceptOptions{
        InsecureSkipVerify: true, // LAN only, zero-auth phase
    })
    // assign device_id, create Client, register with hub
}
```

Register route in `RegisterRoutes`:
```go
mux.HandleFunc("GET /ws", h.handleWebSocket)
```

**Done when:**
- `wscat -c ws://localhost:8888/ws` connects successfully
- Sending `register` returns `session_list`
- Two clients connected: message from A with `to: B's device_id` arrives at B only
- Disconnecting A removes it from session list broadcast to B

---

## LMC-SRV-E03 — Discovery

### LMC-SRV-004 · mDNS control service advertisement · S

**Needs:**
- `cmd/main.go` (existing zeroconf registration)

**Description:**
Register a second mDNS service `_nativestream-ctrl._tcp` so controller
devices can discover the WebSocket endpoint independently of the media
service. TV clients will use the same service type.

Add alongside existing `_nativestream._tcp` registration in `main.go`:

```go
ctrlServer, err := zeroconf.Register(
    "NativeStream Control",
    "_nativestream-ctrl._tcp",
    "local.",
    cfg.Server.Port,
    []string{"version=1", "ws=/ws"},
    nil,
)
if err != nil {
    slog.Warn("mDNS control registration failed", "err", err)
} else {
    defer ctrlServer.Shutdown()
    slog.Info("mDNS control advertised", "service", "_nativestream-ctrl._tcp")
}
```

TXT record includes `ws=/ws` so clients know the WebSocket path without
hardcoding — TV-compatible.

**Done when:**
- `dns-sd -B _nativestream-ctrl._tcp local` shows the service
- TXT record contains `version=1` and `ws=/ws`

---

### LMC-SRV-005 · /api/sessions endpoint · S

**Needs:**
- `LMC-SRV-002` (Hub sessions map)
- `api/handlers.go`

**Description:**
REST endpoint that returns current connected sessions for clients that
prefer HTTP polling over WebSocket (e.g. initial load, TV fallback).

```go
// GET /api/sessions
func (h *Handler) handleSessions(w http.ResponseWriter, r *http.Request) {
    sessions := h.hub.Sessions()
    writeJSON(w, http.StatusOK, map[string]any{"sessions": sessions})
}
```

Register:
```go
mux.HandleFunc("GET /api/sessions", h.handleSessions)
```

**Done when:**
- `curl /api/sessions` returns list of connected devices with their state
- List updates within 5s of a device connecting or disconnecting
- Empty list `{"sessions": []}` when no devices connected