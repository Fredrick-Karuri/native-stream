# NativeStream Server

The content orchestration engine behind NativeStream.

NativeStream Server manages content ingestion, stream reliability, metadata, delivery APIs, and the Local Media Connect control plane. It continuously validates stream availability, maintains healthy playback paths, generates EPG data, and provides a consistent interface for NativeStream clients and integrations.

---

# Key Capabilities

* **Content Management** — Organize and manage sports channels and content sources.
* **Stream Reliability** — Continuously monitor stream health and automatically switch to healthier playback paths.
* **Metadata & EPG** — Generate electronic programme guides and enrich the viewing experience with schedules.
* **Content Discovery** — Integrate with configured content sources and automatically maintain available streams.
* **Playback Delivery** — Serve standardized playlists, EPG data, and proxy endpoints when required.
* **Platform APIs** — Manage content, monitor health, and control server operations through a REST API.
* **Local Media Connect** — WebSocket broker for real-time control plane between devices on the local network.

---

# Installation

## Homebrew (recommended)

```bash
brew tap yourname/nativestream
brew install nativestream-server
brew services start nativestream-server
```

The server starts automatically on login.

## Build from Source

```bash
git clone https://github.com/yourname/nativestream.git
cd nativestream
make build-server
```

## Run as a Service

```bash
make install-service
```

Remove the service:

```bash
make uninstall-service
```

---

# Getting Started

Start the server:

```bash
make run-server
```

Verify health:

```bash
curl http://localhost:8888/api/health
```

The health response includes server metadata used by clients for auto-configuration:

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

---

# Content Management

Channels are managed through the REST API.

Create a channel:

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

View all channels:

```bash
curl http://localhost:8888/api/channels
```

Update a channel:

```bash
curl -X PUT http://localhost:8888/api/channels/{id}
```

Delete a channel:

```bash
curl -X DELETE http://localhost:8888/api/channels/{id}
```

---

# Self-Healing Stream Reliability

NativeStream continuously evaluates stream health using metrics such as availability, latency, and quality.

When an active playback path degrades or fails:

1. The stream is marked unhealthy.
2. Available alternatives are evaluated.
3. The best candidate is promoted automatically.
4. Clients receive an updated playback path.

This allows playback experiences to remain reliable without manual intervention.

---

# Content Discovery

Discovery can be enabled to allow the server to monitor configured content sources, validate available streams, and maintain healthy playback options.

Enable discovery:

```yaml
discovery_enabled: true
```

Additional discovery configuration is documented in `docs/CONTENT_SOURCES.md`.

---

# Local Media Connect

The server acts as the WebSocket broker for the Local Media Connect control plane. Devices connect, register, and exchange typed JSON messages routed by the server hub. No peer-to-peer connection between devices is required.

## Connect

```bash
wscat -c ws://localhost:8888/ws
```

## Register

First message after connecting must be a `register` envelope:

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

## Message types

| Type | Direction | Purpose |
|---|---|---|
| `register` | device → server | Announce device name and kind |
| `session_list` | server → device | Current connected sessions, broadcast on any change |
| `play` | controller → target | Instruct target to play a channel |
| `stop` | controller → target | Instruct target to stop playback |
| `pull_back` | controller → server | Request target's active stream |
| `pull_back_ack` | server → controller | Target's channel ID and stream URL |
| `state_update` | target → server | Target reports current playback state |
| `ping` / `pong` | bidirectional | Heartbeat, 30s interval |

## Pull-back flow

```
Phone → server: pull_back { from_device: mac_id }
server reads sessions[mac_id].channel_id + stream_url
server → phone: pull_back_ack { channel_id, stream_url }
server → mac:   stop {}
```

## Auth

The `auth` field is present in every envelope but currently `null` (zero-auth, LAN trust model). The field is reserved for a future token-based pairing model without requiring a protocol change.

## Session list (HTTP fallback)

```bash
curl http://localhost:8888/api/sessions
```

---

# Playback Endpoints

| Endpoint                | Purpose                                 |
| ----------------------- | --------------------------------------- |
| `GET /playlist.m3u`     | Generated live playlist                 |
| `GET /epg.xml`          | Electronic programme guide              |
| `GET /stream/:id/proxy` | Stream proxy with custom header support |

---

# mDNS Advertisement

The server advertises two mDNS services on startup:

| Service type | Purpose |
|---|---|
| `_nativestream._tcp` | Media service — clients discover the server for health/playlist/EPG |
| `_nativestream-ctrl._tcp` | Control service — clients discover the WebSocket endpoint |

The control service TXT record includes `version=1` and `ws=/ws` so clients resolve the WebSocket path without hardcoding.

Verify:
```bash
dns-sd -B _nativestream._tcp local
dns-sd -B _nativestream-ctrl._tcp local
```

---

# Full API Reference

| Endpoint | Method | Purpose |
|---|---|---|
| `/api/health` | GET | Server health + metadata |
| `/api/channels` | GET | List all channels |
| `/api/channels` | POST | Create channel |
| `/api/channels/{id}` | GET | Get channel detail |
| `/api/channels/{id}` | PUT | Update channel |
| `/api/channels/{id}` | DELETE | Delete channel |
| `/api/channels` | DELETE | Delete all channels |
| `/api/probe` | POST | Trigger stream re-validation |
| `/api/discovery/status` | GET | Discovery engine status |
| `/api/discovery/unmatched` | GET | Unmatched stream links |
| `/api/sessions` | GET | Connected LMC device sessions |
| `/ws` | WS | LMC control plane WebSocket |
| `/playlist.m3u` | GET | Generated M3U playlist |
| `/epg.xml` | GET | XMLTV EPG data |
| `/stream/{id}/proxy` | GET | HLS proxy with header injection |

---

# Configuration

Configuration is located at:

```
~/.config/nativestream/config.yaml
```

The server provides sensible defaults and can run without manual configuration.

Common options include:

* Server networking (host, port)
* Health probe intervals and concurrency
* EPG providers and refresh interval
* Proxy behavior (Referer, User-Agent injection)
* Discovery services (Reddit, GitHub Gist, Telegram, direct M3U)

See `docs/CONFIGURATION.md` for the full configuration reference.

---

# Operations

## Health

```bash
curl http://localhost:8888/api/health
```

## Force Health Check

```bash
curl -X POST http://localhost:8888/api/probe
```

## Discovery Status

```bash
curl http://localhost:8888/api/discovery/status
```

## Connected Devices

```bash
curl http://localhost:8888/api/sessions
```

---

# Development

Run tests:

```bash
make test-server
```

Static analysis:

```bash
make vet-server
```

Build:

```bash
make build-server
```

Dev server (port 8889, separate config):

```bash
make server-dev
```

---

# Architecture

```
Content Sources
        |
        v
+---------------------------+
| NativeStream Server       |
|                           |
| Content Management        |
| Discovery                 |
| Validation                |
| Health Monitoring         |
| Metadata & EPG            |
| Proxy Services            |
| LMC WebSocket Hub  ←──────┼── Android (controller)
|                    ←──────┼── macOS (target)
|                    ←──────┼── TV (future)
+---------------------------+
        |
        v
NativeStream Clients
and External Players
```

---

# Documentation

* `docs/SELF_HOSTING.md`
* `docs/CONFIGURATION.md`
* `docs/API.md`
* `docs/SYSTEM_DESIGN.md`
* `docs/CONTENT_SOURCES.md`