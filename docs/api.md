# API Reference

All endpoints are served by the Go server at `http://localhost:8888` and are **localhost-only** — there is no authentication, since any process on the machine is trusted by design. This is a personal, single-user tool, not a multi-tenant service.

## Response Formats

| Prefix | Content-Type |
|---|---|
| `/api/*` | `application/json` |
| `/playlist.m3u` | `application/x-mpegurl` |
| `/epg.xml` | `application/xml` |

## Endpoints

| Method | Path | Description |
|---|---|---|
| GET | `/playlist.m3u` | M3U of all channels with a healthy active link. Proxy URLs if proxy enabled. |
| GET | `/epg.xml` | XMLTV EPG for the next 48 hours. Served from in-memory cache — never blocks on network. |
| GET | `/stream/:id/proxy` | Transparent HLS proxy with header injection (`Referer`, `User-Agent`). Streams, not buffered. |
| GET | `/api/health` | Server status: uptime, total channels, healthy count, last probe time. |
| GET | `/api/channels` | JSON array of all channels with health status and candidate count. |
| GET | `/api/channels/:id` | Single channel detail, including all link scores and states. |
| POST | `/api/channels` | Add a channel. Accepts `name`, `group_title`, `tvg_id`, `logo_url`, `stream_url`, `keywords`. |
| PUT | `/api/channels/:id` | Update a channel. Accepts `stream_url` (triggers immediate probe), `name`, `group_title`. |
| DELETE | `/api/channels/:id` | Remove a channel. Disappears from the playlist immediately. |
| DELETE | `/api/channels` | Delete all channels. |
| POST | `/api/probe` | Trigger an immediate out-of-schedule probe of all active links. |
| GET | `/api/discovery/status` | Per-source: last fetch time, links found, errors, suspension state. |
| POST | `/api/discovery/run` | Trigger an immediate discovery cycle. Returns `{"status":"triggered"}`. |
| GET | `/api/discovery/unmatched` | Last 50 unmatched candidate URLs, with source and context. |
| GET | `/api/sessions` | Connected Local Media Connect device sessions (HTTP fallback for `/ws`). |
| WS | `/ws` | Local Media Connect control plane — see below. |

### Health response

```json
{
  "status": "ok",
  "uptime": "2m30s",
  "channels": 142,
  "healthy": 138,
  "version": "4.0",
  "server_name": "NativeStream @ Fredricks-MacBook-Pro.local",
  "addr": "http://192.168.100.40:8888"
}
```

### Create a channel

```bash
curl -X POST http://localhost:8888/api/channels \
-H "Content-Type: application/json" \
-d '{
  "name": "Example Sports Channel",
  "group_title": "Football",
  "tvg_id": "example",
  "keywords": ["example"],
  "stream_url": "https://stream.example.m3u8"
}'
```

## Which client calls what

| Endpoint | Used by |
|---|---|
| `GET /api/health` | Both clients — onboarding, health indicator, periodic polling |
| `GET /playlist.m3u` | Both clients — channel fetch |
| `GET /epg.xml` | Both clients — EPG fetch |
| `GET /api/channels` | Both clients — channel list |
| `POST /api/channels` | Both clients — add channel |
| `POST /api/probe` | Both clients — re-validate stream links |
| `GET /api/channels/{id}` | Android — re-fetch active link on player retry |
| `GET /api/sessions` | Both clients — LMC session list, HTTP fallback |
| `WS /ws` | Both clients — LMC control plane |

---

## Local Media Connect (WebSocket Control Plane)

The server is the WebSocket broker for real-time control between devices on the local network. Devices connect, register, and exchange typed JSON envelopes routed by the server hub — there is no peer-to-peer connection.

### Connect and register

```bash
wscat -c ws://localhost:8888/ws
```

The first message after connecting must be a `register` envelope:

```json
{
  "type": "register",
  "from": "device-uuid",
  "to": "server",
  "auth": null,
  "payload": "{\"name\": \"Fredrick's Phone\", \"kind\": \"controller\"}"
}
```

Device kinds: `controller` (Android phone), `target` (Mac), `tv` (future).

### Envelope shape

```json
{
  "type":    "<MessageType>",
  "from":    "<device-uuid>",
  "to":      "<device-uuid> | broadcast | server",
  "auth":    null,
  "payload": { }
}
```

`auth` is present in every envelope but currently always `null` (zero-auth, LAN trust model). The field is reserved for a future token-based pairing model without requiring a protocol change.

### Message types

| Type | Direction | Payload fields |
|---|---|---|
| `register` | device → server | `name: string, kind: string` |
| `session_list` | server → device | `sessions: SessionInfo[]` — broadcast on any session change |
| `play` | controller → target | `channel_id: string, stream_url: string` |
| `stop` | controller → target | _(empty)_ |
| `pull_back` | controller → server | `from_device: string` |
| `pull_back_ack` | server → controller | `channel_id: string, stream_url: string` |
| `state_update` | target → server | `channel_id: string, stream_url: string, playing: bool` |
| `ping` / `pong` | bidirectional | _(empty)_ — 30s heartbeat interval |

### Pull-back flow

```
Phone → server: pull_back { from_device: mac_id }
server reads sessions[mac_id].channel_id + stream_url
server → phone: pull_back_ack { channel_id, stream_url }
server → mac:   stop {}
```

For the design rationale behind Local Media Connect, see [local-media-connect.md](local-media-connect.md).

---

## mDNS Advertisement

The server advertises two mDNS services on startup:

| Service type | Purpose |
|---|---|
| `_nativestream._tcp` | Media service — clients discover the server for health/playlist/EPG |
| `_nativestream-ctrl._tcp` | Control service — clients discover the WebSocket endpoint |

The control service TXT record includes `version=1` and `ws=/ws` so clients resolve the WebSocket path without hardcoding.

```bash
dns-sd -B _nativestream._tcp local
dns-sd -B _nativestream-ctrl._tcp local
```