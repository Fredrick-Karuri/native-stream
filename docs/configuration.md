# Configuration

The server provides sensible defaults and runs with zero config file. All settings live at:

```
~/.config/nativestream/config.yaml
```

Format: flat `key: value`, parsed by a small stdlib-only parser (no YAML library, to keep the server free of external dependencies — see [architecture.md](architecture.md#key-decisions)).

## Full Reference

```yaml
# Server host: 127.0.0.1
port: 8888
api_token: ""                  # required if host is set to anything other than 127.0.0.1

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
| `host` | `127.0.0.1` | Loopback-only by default. Setting this to anything else (e.g. `0.0.0.0` for a hosted deployment) requires `api_token` to also be set — the server refuses to start otherwise. Every `/api/*`, `/playlist.m3u`, `/epg.xml`, and `/stream/:id/proxy` request then requires `Authorization: Bearer <api_token>`; `/ws` stays unauthenticated regardless. |
| `api_token` | _(empty)_ | Bearer token required once `host` is exposed beyond loopback. Can also be set via the `NATIVESTREAM_API_TOKEN` env var (and `host` via `NATIVESTREAM_SERVER_HOST`) — the env vars are how the hosted deployment on Coolify is configured, since secrets belong in the platform's secret store rather than a file on disk. See [hosted deployment notes] below. |
| `port` | `8888` | Dev server runs on `8889` **only** when no config file/env override is present at all — this is `Defaults()`'s fallback, not a deliberate dev-vs-prod split |
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

## Hosted Deployment

A hosted instance runs on Azure via Coolify (a self-hosted PaaS layer, chosen over Azure-specific tooling to avoid lock-in — the whole stack is portable to any VPS via Docker if the Azure student credit expires).

**Current hosted default:**
nativestream.duckdns.org

Originally deployed on a Coolify-generated `sslip.io` domain, which turned out to fail the actual requirement: `sslip.io`'s shared certificate infrastructure produced an invalid/untrusted TLS cert, breaking `wss://` connections (Local Media Connect) outright and requiring manual trust exceptions for HTTPS — unacceptable for a client that needs to "just work" with no customer-side cert configuration.

Replaced with a free DuckDNS subdomain (`nativestream.duckdns.org`), pointed at the VM's public IP, with Coolify issuing a proper Let's Encrypt certificate against it. This is a genuine, owned domain (not borrowed shared infrastructure like sslip.io), which resolves the trust problem and — as a side effect — also reduces the IP-coupling risk: if the VM is recreated with a new IP, only the DuckDNS A record needs updating, not the domain itself or any client's baked-in default. A small cron job on the VM (`~/duckdns/update.sh`, every 5 min) keeps the DNS record synced to the VM's current IP automatically, so this survives VM recreation without manual intervention — a lingering risk from the original design doc, incidentally resolved by this fix rather than the fix's original purpose.

**Admin access note:** Coolify's dashboard (`:8000`) and SSH (`:22`) are no longer exposed to the public internet at all — see the Tailscale note in the operations/security section below. Only `nativestream.duckdns.org` on `80`/`443` is publicly reachable.

Config is injected via environment variables, not a mounted `config.yaml` — `NATIVESTREAM_SERVER_HOST=0.0.0.0` and `NATIVESTREAM_API_TOKEN=<generated>`, set in Coolify's environment variable panel (Coolify's secret store, not plaintext on the VM disk).

### Admin Access — Tailscale, not IP Allowlisting

The VM's admin surfaces (SSH `:22`, Coolify dashboard `:8000`) are **not** exposed to the public internet. They were originally restricted via Azure NSG rules allowlisting a specific home IP — this broke the first time that IP rotated (ISP-assigned IPs aren't stable), causing a full lockout.

Root cause: IP allowlisting was answering the wrong question. An IP address identifies a network location, not a device or a person — using it as an identity proxy breaks the moment the network location changes, which is normal and expected behavior for a home ISP connection.

Fixed by installing Tailscale on both the VM and admin devices, then removing the public NSG rules for `22`/`8000` entirely:
Internet → 80/443 → public app traffic
Tailscale → 22/8000 → admin access only
Azure NSG → 80/443 open, everything else blocked

Same principle as the auth split in [api.md](api.md#auth-requirements) (`/ws` open, everything else gated) applied one layer down: the set of things that should be *reachable* should match the set of things that should be *trusted*, and a public IP was never the right proxy for "trusted."