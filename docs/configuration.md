# Configuration

The server provides sensible defaults and runs with zero config file. All settings live at:

```
~/.config/nativestream/config.yaml
```

Format: flat `key: value`, parsed by a small stdlib-only parser (no YAML library, to keep the server free of external dependencies — see [architecture.md](architecture.md#key-decisions)).

## Full Reference

```yaml
# Server
host: 127.0.0.1
port: 8888

# Store
snapshot_path: ~/.config/nativestream/channels.json
snapshot_interval: 5m

# Probe
probe_interval: 10m
probe_timeout: 5s
probe_concurrency: 20

# EPG
epg_enabled: true
epg_refresh_interval: 6h
espn_enabled: true
football_data_key: ""          # free at football-data.org

# Proxy (optional)
proxy_enabled: false
proxy_referer: ""
proxy_user_agent: "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7)"

# Discovery
discovery_enabled: false       # set to true to enable auto-discovery
# After enabling, add sources:
# gist_ids, subreddits, telegram channels configured via channels added
# to channels.json or POST /api/channels with keywords

# Seed (optional — import existing .m3u on first run)
seed_m3u_path: ""
```

## Option Reference

| Key | Default | Notes |
|---|---|---|
| `host` | `127.0.0.1` | Server never binds to `0.0.0.0` — see [architecture.md](architecture.md#http-middleware-stack) |
| `port` | `8888` | Dev server runs on `8889` with a separate config via `make server-dev` |
| `snapshot_interval` | `5m` | How often `channels.json` is written; also written on `SIGTERM` |
| `probe_interval` | `10m` | Re-probe cadence for **active** links. Candidate links probe every 30m (not configurable) |
| `probe_timeout` | `5s` | Per-probe HTTP timeout |
| `probe_concurrency` | `20` | Worker pool size for the link validator |
| `epg_refresh_interval` | `6h` | How often EPG sources are re-fetched |
| `football_data_key` | _(empty)_ | Optional — unlocks EPL, UCL, La Liga, Bundesliga, Serie A schedules. ESPN works without a key |
| `proxy_enabled` | `false` | When enabled, `/playlist.m3u` serves proxy URLs (`/stream/:id/proxy`) instead of direct stream URLs |
| `discovery_enabled` | `false` | Enables the four crawlers described in [architecture.md](architecture.md#component-discovery-engine) |
| `seed_m3u_path` | _(empty)_ | One-time import of an existing `.m3u` file on first run |

Additional discovery source configuration (Gist IDs, subreddits, Telegram channels) is set per-channel via `keywords` on `POST /api/channels` rather than in `config.yaml` — see [api.md](api.md#create-a-channel).

## Client-Side Settings

Client settings (server URL, buffer preset, favourites, etc.) are not part of the server config — they're stored locally per client. See:

- Mac: [architecture.md — Storage Design](architecture.md#storage-design)
- Android: [android-architecture.md — Persistence](android-architecture.md#persistence)