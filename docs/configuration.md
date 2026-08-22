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
# api_token is deprecated as a live auth mechanism — see Credentials below.
# Still accepted on first boot for one-time migration into the credential store.
api_token: ""

# Store
snapshot_path: ~/.config/nativestream/channels.json
snapshot_interval: 5m
credentials_path: ~/.config/nativestream/credentials.json

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
| `host` | `127.0.0.1` | Loopback-only by default. Setting this to anything else (e.g. `0.0.0.0` for a hosted deployment) requires at least one credential to exist — see [Credentials](#credentials) below. Every `/api/*`, `/playlist.m3u`, `/epg.xml`, and `/stream/:id/proxy` request then requires `Authorization: Bearer <token>`; `/ws` stays unauthenticated regardless. |
| `api_token` | _(empty)_ | Legacy single-token config value. Read once on first boot and migrated into the credential store as one row labeled `"Migrated from single api_token"` — see [Credentials](#credentials). Not read again after migration. Can also be set via the `NATIVESTREAM_API_TOKEN` env var (and `host` via `NATIVESTREAM_SERVER_HOST`) for platforms where secrets belong in a secret store rather than a file on disk. |
| `credentials_path` | `~/.config/nativestream/credentials.json` | Where the credential store persists. |
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

## Credentials

Auth is a table of independently revocable credentials, not a single shared
secret. Each row is `{token, label, created_at, revoked_at}` — `label` is a
free-text operator note (e.g. `"Fredrick's Mac"`, `"Demo — Jane"`), not an
identity system; there's no signup flow, email, or password attached to it.

**Creating a credential:** no CLI command exists yet for creation — the
first credential comes from migrating `api_token` on first boot. Additional
credentials currently require direct manipulation of `credentials.json` or
a future admin endpoint.

**Revoking a credential:**

```bash
nativestream-server --revoke-token <label>
```

Revocation is immediate — no restart required — and affects only the named
credential; every other credential keeps working. A revoked token returns
the same `401 unauthorized` response as a token that never existed, so a
client can't distinguish "your access was revoked" from "that token is
wrong."

See [architecture.md — Credential Model](architecture.md#credential-model)
for the design rationale.

## Media Plane Selection

| Env var | Default | Notes |
|---|---|---|
| `NATIVESTREAM_MEDIA_PLANE` | _(unset — uses the default proxy implementation)_ | Set to `stub` to run the proof-of-boundary stub implementation instead (`packages/mediaplane/stub`) — not for production use. See [architecture.md — The interface boundary](architecture.md#the-interface-boundary). |

## Client-Side Settings

Client settings (server URL, buffer preset, favourites, etc.) are not part of the server config — they're stored locally per client. See:

- Mac: [architecture.md — Storage Design](architecture.md#storage-design)
- Android: [android-architecture.md — Persistence](android-architecture.md#persistence)

## Hosted Deployment

A hosted instance runs on Azure via Coolify (a self-hosted PaaS layer,
chosen to keep the stack portable to any VPS via Docker rather than tied to
Azure-specific tooling).

The public hostname is a DuckDNS subdomain (a genuinely owned domain, not
shared infrastructure), with Coolify issuing a Let's Encrypt certificate
against it. A cron job on the VM keeps the DNS A record synced to the VM's
current public IP every 5 minutes, so the domain survives VM recreation
without manual intervention.

**Publicly reachable:** the hostname on ports `80`/`443` only.

**Admin access:** SSH (`22`) and the Coolify dashboard (`8000`) are not
exposed to the public internet at all — they're reachable only over
Tailscale, from devices enrolled in the tailnet.

```
Internet  → 80/443 → public app traffic
Tailscale → 22/8000 → admin access only
Azure NSG → 80/443 open, everything else blocked
```

Config is injected via environment variables in Coolify's environment
variable panel (Coolify's own secret store, not a file on the VM disk) —
`NATIVESTREAM_SERVER_HOST=0.0.0.0` and `NATIVESTREAM_API_TOKEN=<generated>`
for the first boot's credential migration.

This mirrors the same principle as the auth split in
[api.md](api.md#auth-requirements) (`/ws` open, everything else gated),
applied one layer down: the set of things reachable from the public
internet should match the set of things that are meant to be trusted from
there.