# NativeStream Server

The content orchestration engine behind NativeStream.

NativeStream Server manages content ingestion, stream reliability, metadata, and delivery APIs. It continuously validates stream availability, maintains healthy playback paths, generates EPG data, and provides a consistent interface for NativeStream clients and integrations.

---

# Key Capabilities

* **Content Management** — Organize and manage sports channels and content sources.
* **Stream Reliability** — Continuously monitor stream health and automatically switch to healthier playback paths.
* **Metadata & EPG** — Generate electronic programme guides and enrich the viewing experience with schedules.
* **Content Discovery** — Integrate with configured content sources and automatically maintain available streams.
* **Playback Delivery** — Serve standardized playlists, EPG data, and proxy endpoints when required.
* **Platform APIs** — Manage content, monitor health, and control server operations through a REST API.

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

# Playback Endpoints

| Endpoint                | Purpose                                 |
| ----------------------- | --------------------------------------- |
| `GET /playlist.m3u`     | Generated live playlist                 |
| `GET /epg.xml`          | Electronic programme guide              |
| `GET /stream/:id/proxy` | Stream proxy with custom header support |

---

# Configuration

Configuration is located at:

```
~/.config/nativestream/config.yaml
```

The server provides sensible defaults and can run without manual configuration.

Common options include:

* Server networking
* Health probe intervals
* EPG providers
* Proxy behavior
* Discovery services

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

---

# Architecture

The server is designed as a modular content orchestration layer.

```
Content Sources
        |
        v
+--------------------+
| NativeStream       |
|                    |
| Content Management |
| Discovery          |
| Validation         |
| Health Monitoring  |
| Metadata & EPG     |
| Proxy Services     |
+--------------------+
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
