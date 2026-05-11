# NativeStream Server

A self-healing live sports stream server. Discovers M3U8 links automatically, validates them continuously, and serves a live M3U playlist + XMLTV EPG to the Mac app over localhost.

---

## Prerequisites

- macOS 14 (Sonoma) or later
- Go 1.22 or later (`brew install go`)

---

## Installation

### Option A — Homebrew (recommended)

```bash
brew tap yourname/nativestream
brew install nativestream-server
brew services start nativestream-server
```

Server starts automatically on login. Logs at `/usr/local/var/log/nativestream.log`.

### Option B — Build from source

```bash
git clone https://github.com/yourname/nativestream.git
cd nativestream
make build-server          # → app/server/nativestream-server
```

### Option C — Install as launchd service (from source)

```bash
make install-service
# Copies binary to /usr/local/bin and registers as launchd service
# Auto-starts on login, survives reboots
```

To remove: `make uninstall-service`

---

## First-Time Setup

```bash
# Create config directory and copy example config
./scripts/install.sh

# Edit your config
nano ~/.config/nativestream/config.yaml
```

The server starts with zero config — all fields have defaults. You only need to edit config to add discovery sources or an EPG API key.

---

## Starting the Server

```bash
# Development (foreground, logs to stdout)
make run-server

# Or directly
./app/server/nativestream-server

# Check it's running
curl http://localhost:8888/api/health
```

Expected response:
```json
{
  "status": "ok",
  "uptime": "2m14s",
  "channels": 0,
  "healthy": 0,
  "last_probe": "2026-05-09T15:00:00Z"
}
```

---

## Adding Your First Channel

```bash
curl -X POST http://localhost:8888/api/channels \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Sky Sports Football",
    "group_title": "Football",
    "tvg_id": "SkySportsFoot",
    "keywords": ["sky", "skysports", "skyfootball", "sky sports foot"],
    "stream_url": "https://your-found-stream-url.m3u8"
  }'
```

The server immediately probes the stream URL and scores it. Check the result:

```bash
curl http://localhost:8888/api/channels/sky-sports-football | jq .
```

View the generated playlist:

```bash
curl http://localhost:8888/playlist.m3u
```

Point the Mac app at: `http://localhost:8888/playlist.m3u`

---

## Finding Stream URLs

The server manages links — you need to supply the first one per channel. Find M3U8 URLs by:

1. Open Safari → go to a streaming site → enable **Develop menu** (Safari → Settings → Advanced)
2. Open **Develop → Show Web Inspector → Network tab**
3. Start playing a stream
4. Filter by `.m3u8` — copy the URL of the `index.m3u8` or `master.m3u8` request

Test the link in IINA or VLC first. If it buffers there, it will buffer everywhere — the problem is the source, not the app.

---

## Enabling Auto-Discovery

Once you have channels with keywords configured, enable discovery to find replacement links automatically:

```yaml
# ~/.config/nativestream/config.yaml
discovery_enabled: true
```

Restart the server. Then add sources:

**GitHub Gists** — find public Gists containing M3U playlists (search GitHub for `filename:playlist.m3u` or `filename:streams.m3u8`). Add their IDs to config:

```yaml
# Add these lines to config.yaml:
# gist IDs are the hex string in the Gist URL: gist.github.com/user/{ID}
```

Or use the API to watch channels and let the Reddit/Telegram crawlers find links automatically from configured subreddits and channels.

Trigger a manual discovery run at any time:

```bash
curl -X POST http://localhost:8888/api/discovery/run
curl http://localhost:8888/api/discovery/status | jq .
```

View links the matcher couldn't assign to a channel (add keywords to fix these):

```bash
curl http://localhost:8888/api/discovery/unmatched | jq .
```

---

## API Reference

### Playlist & EPG

| Endpoint | Description |
|---|---|
| `GET /playlist.m3u` | Live M3U of all healthy channels |
| `GET /epg.xml` | XMLTV EPG for next 48 hours |
| `GET /stream/:id/proxy` | HLS proxy with header injection (if proxy enabled) |

### Channel Management

```bash
# List all channels
curl http://localhost:8888/api/channels

# Get single channel
curl http://localhost:8888/api/channels/sky-sports-football

# Add channel
curl -X POST http://localhost:8888/api/channels \
  -H "Content-Type: application/json" \
  -d '{"name":"...", "group_title":"...", "tvg_id":"...", "keywords":[...], "stream_url":"..."}'

# Update stream URL (triggers immediate probe)
curl -X PUT http://localhost:8888/api/channels/sky-sports-football \
  -H "Content-Type: application/json" \
  -d '{"stream_url":"https://new-url.m3u8"}'

# Delete channel
curl -X DELETE http://localhost:8888/api/channels/sky-sports-football
```

### Health & Control

```bash
# Server health
curl http://localhost:8888/api/health

# Force probe all links now
curl -X POST http://localhost:8888/api/probe

# Discovery status
curl http://localhost:8888/api/discovery/status

# Force discovery cycle
curl -X POST http://localhost:8888/api/discovery/run

# Unmatched links (for debugging keyword config)
curl http://localhost:8888/api/discovery/unmatched
```

---

## Configuration Reference

Full config at `~/.config/nativestream/config.yaml`. All fields optional — server starts with defaults.

```yaml
# Server binding — never change host to 0.0.0.0
host: 127.0.0.1
port: 8888

# How often to write channels.json snapshot
snapshot_interval: 5m

# Link health probing
probe_interval: 10m      # how often to re-check active links
probe_timeout: 5s        # per-link timeout
probe_concurrency: 20    # parallel probe workers

# EPG / TV guide
epg_enabled: true
epg_refresh_interval: 6h
espn_enabled: true
football_data_key: ""    # free key from football-data.org (optional)

# HLS proxy (enable only if streams need Referer/User-Agent injection)
proxy_enabled: false
proxy_referer: ""
proxy_user_agent: "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7)"

# Auto-discovery
discovery_enabled: false
```

---

## Link Health & Self-Healing

The server scores each stream link from 0.0 to 1.0:

| Score | Meaning |
|---|---|
| ≥ 0.7 | Healthy — fast, HD quality |
| 0.4–0.7 | Acceptable — may be slower or lower bitrate |
| < 0.4 | Unhealthy — quarantined, backup promoted |
| 0.0 | Dead — evicted after 3 failures |

When an active link's score drops below 0.3, the server automatically promotes the best candidate. The playlist updates within seconds. The Mac app picks up the new link on its next 60-second refresh.

To see current health:
```bash
curl http://localhost:8888/api/channels | jq '[.channels[] | {name, healthy, active_score}]'
```

---

## Logs

```bash
# If running as launchd service (Homebrew)
tail -f /usr/local/var/log/nativestream.log

# If running manually
./nativestream-server      # logs to stdout
```

Key log events to watch:
- `store loaded` — channels snapshot loaded on startup
- `crawler enabled` — discovery sources active
- `link promoted` — self-healing fired, new active link
- `priority escalation` — pre-match crawl frequency increased

---

## Troubleshooting

**No channels in playlist**
→ Check `curl http://localhost:8888/api/health` — if unhealthy/0, no channels added yet.
→ Add a channel via `POST /api/channels` with a known-good stream URL.

**Stream URL works in IINA but not in the app**
→ Some streams require `Referer` or `User-Agent` headers. Enable the proxy: `proxy_enabled: true`.

**Discovery not finding links**
→ Check `curl http://localhost:8888/api/discovery/status` for errors.
→ View unmatched: `curl http://localhost:8888/api/discovery/unmatched` — if URLs appear here, add keywords to the relevant channel.

**Channels disappear from playlist**
→ Their active link died and no candidate was available. Check `api/health`. Manually supply a new URL via `PUT /api/channels/:id`.

**Server won't start**
→ Port conflict: `lsof -i :8888` — kill the conflicting process.
→ Bad config: check `~/.config/nativestream/config.yaml` syntax.

---

## Building for Release

```bash
# Build universal macOS binary (arm64 + amd64)
VERSION=1.0.0 ./scripts/brew-release.sh
# Outputs: dist/nativestream-server-darwin-arm64.tar.gz
#          dist/nativestream-server-darwin-amd64.tar.gz
# Prints SHA256 values for Homebrew formula
```

---

## Development

```bash
make test-server           # go test -race ./...
make vet-server            # go vet ./...
make build-server          # compile binary
make clean                 # remove binary
```

Tests cover: store CRUD, snapshot round-trip, self-healing promote logic, validator scoring formula, discovery extractor regex, channel matcher keyword/fuzzy logic.