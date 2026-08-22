# Architecture

**Last updated:** 2026-08-22.

This is the cross-cutting view: how the pieces fit together and why. For internals specific to one platform, see:

- [server-architecture.md](server-architecture.md) — Go server internals
- [mac-architecture.md](mac-architecture.md) — Swift/macOS internals
- [android-architecture.md](android-architecture.md) — Kotlin/Android internals
- [development.md](development.md) — local dev commands
- [releasing.md](releasing.md) — how a release ships

## Overview

NativeStream is a two-process system per client platform. Each client is a thin, stateless-ish poller; all intelligence — discovery, validation, scoring, self-healing — lives in the server.

The server itself splits into two logical halves: a **control plane** (identity, device/session coordination, config, channel state — centralized, one instance) and a **media plane** (discovery, proxying, EPG sourcing — pluggable, swappable per deployment). See [Control Plane / Media Plane Split](#control-plane--media-plane-split) below.

```
┌──────────────────────────────────────────────────┐
│  NativeStream Mac (Swift)                        │
│  Polls localhost:8888 every 60s                  │
│  Plays HLS via AVFoundation / VideoToolbox        │
└────────────────────┬─────────────────────────────┘
                     │ HTTP localhost:8888
┌────────────────────▼─────────────────────────────┐
│  StreamServer (Go)                               │
│  ├── Channel Store (in-memory + JSON snapshot)   │
│  ├── Credential Store (per-person, revocable)    │
│  ├── Link Validator (20-worker probe pool)       │
│  ├── Discovery Engine (5 crawler types)          │
│  ├── EPG Engine (XMLTV generator)                │
│  ├── Proxy (HLS header injection + rewriting)    │
│  └── HTTP API (REST + playlist + EPG endpoints)  │
└────────────────────┬─────────────────────────────┘
                     │
     ┌───────────────┼───────────────┐
     ▼               ▼               ▼
 GitHub Gists    Reddit/Telegram  ESPN/football-data.org
 (stream links)  (stream links)   (match schedules)
```

Neither client knows where stream links come from. Each polls one URL (`/playlist.m3u`) and plays whatever is there.

A fuller visual version of the diagram below is at `screenshots/architecture.svg`.

## Full System Diagram

```
╔═══════════════════════════════════════════════════════════════════╗
║  EXTERNAL SOURCES                                                 ║
║  ┌─────────────┐ ┌──────────────┐ ┌─────────┐ ┌──────────────┐  ║
║  │ GitHub Gists│ │Reddit/r/sport│ │Telegram │ │Direct .m3u   │  ║
║  │ (M3U files) │ │ (posts+cmts) │ │(public) │ │(stable URLs) │  ║
║  └──────┬──────┘ └──────┬───────┘ └────┬────┘ └──────┬───────┘  ║
╚═════════╪══════════════╪══════════════╪══════════════╪══════════╝
          │              │              │              │
╔═════════▼══════════════▼══════════════▼══════════════▼══════════╗
║  STREAMSERVER (Go — localhost:8888)                              ║
║  Discovery Engine → Link Validator → Channel Store → EPG Engine  ║
║  → HTTP API   (full breakdown: server-architecture.md)           ║
╚═════════════════════════════╪════════════════════════════════════╝
                              │ HTTP localhost:8888
              ┌───────────────┴───────────────┐
              ▼                               ▼
╔═════════════════════════╗      ╔═════════════════════════╗
║  NATIVESTREAM MAC        ║      ║  NATIVESTREAM ANDROID    ║
║  (Swift / SwiftUI)       ║      ║  (Kotlin / Compose)      ║
║  Target for LMC          ║◄────►║  Controller for LMC      ║
║  (mac-architecture.md)   ║  WS  ║  (android-architecture)  ║
╚═════════════════════════╝      ╚═════════════════════════╝
              │                               │
              ▼                               ▼
╔══════════════════════════════════════════════════════════════════╗
║  HLS STREAM CDN (external)                                       ║
║  .m3u8 master playlists + .ts segments                           ║
╚══════════════════════════════════════════════════════════════════╝
```

The Mac and Android boxes connect to each other only through the server's WebSocket hub (Local Media Connect) — never peer-to-peer. See [local-media-connect.md](local-media-connect.md).

---

## Control Plane / Media Plane Split

The server binary is one process, but its code is organized around a hard
interface boundary between two roles:

**Control plane** — identity, device/session coordination, config, and
channel state. Centralized: one instance serves every customer. Cheap to
run and needs to be trusted regardless of scale.

**Media plane** — discovery (finding stream links), proxying (fetching and
rewriting stream traffic), and EPG sourcing (fetching match schedules).
Pluggable: a deployment can use the default implementation, a self-run
implementation, or none at all (direct-URL-only playback needs no media
plane).

The split exists because the two roles have different centralization
requirements. Identity and coordination only make sense with one source of
truth. Discovery, proxying, and EPG sourcing carry cost and legal exposure
that scale with usage, and a self-run deployment should be able to supply
its own without touching control-plane code.

```
Control Plane (apps/server/, centralized)
├── Identity — per-person credentials (store/credentials.go)
├── Device/session coordination (control/)
├── Config, bind/token guard (config/)
└── Adapters — satisfy the media plane interfaces over store.Store
    (server/discovery/adapters.go, server/proxy/adapters.go)

Media Plane (packages/, pluggable)
├── packages/mediaplane      — the interface contract
├── packages/mediaplane/stub — proof implementation, no control-plane dependency
├── packages/discovery       — crawlers, extractor, circuit breaker, matcher
├── packages/proxy           — header injection, SSRF guard, rewriter
└── packages/epg-sourcing    — ESPN / football-data fetch and parse
```

### The interface boundary

`packages/mediaplane` defines what the control plane needs *from* a media
plane, as Go interfaces (`DiscoveryProvider`, `StreamProxy`,
`MatchProvider`). The control plane calls through these interfaces only —
never against a concrete implementation directly.

Every implementation declares whether it performs SSRF filtering on
outbound requests via `PerformsSSRFFiltering() bool`. The control plane
does not re-implement SSRF checks itself — that stays the media plane's
job, since it protects the implementation's own outbound traffic — but it
can refuse to route through an implementation that doesn't claim to
filter.

`packages/mediaplane/stub` is a second, independent implementation with no
control-plane dependency, selectable via `NATIVESTREAM_MEDIA_PLANE=stub`
with zero source changes elsewhere. It exists to prove the boundary is
real, not for production use — if a future change ever breaks the stub
build, that's a signal the interface has started leaking control-plane
assumptions back in.

### Extracted packages and their ports

Two of the three extracted packages need a narrow slice of control-plane
data (which channels exist, what a channel's active stream link is)
without depending on control-plane storage directly. Each defines a small
local interface — a "port" — for exactly that slice; the control plane
supplies an adapter satisfying it over the real `store.Store`.

| Package | Needs from control plane | Port | Adapter |
|---|---|---|---|
| `packages/discovery` | Read/register channels by keyword | `ChannelLookup` | `server/discovery/adapters.go` |
| `packages/discovery` | Submit a matched candidate for scoring | `CandidateSubmitter` | `server/discovery/adapters.go` |
| `packages/proxy` | Look up a channel's active stream link | `ActiveLinkSource` | `server/proxy/adapters.go` |
| `packages/epg-sourcing` | None | — | — |

`packages/epg-sourcing` needs no port — fetching and parsing match
schedules touches no control-plane state.

### Credential model

The single shared `api_token` is replaced by a persisted table of
independently revocable credentials. A row is `{token, label, created_at,
revoked_at}` — no user profile, no email, no password. `AuthMiddleware`
checks token membership against the non-revoked set, with a constant-time
comparison per row so timing doesn't leak which stored token a supplied one
is close to. Revoked and invalid tokens return the same 401 shape, so the
response never reveals whether a given token ever existed. See
[configuration.md](configuration.md#credentials) for the operator-facing
side of this.

### Known gaps

- `packages/proxy` declares `PerformsSSRFFiltering() true`, accurate for
  the variant-playlist routing path but not yet enforced at
  channel-create/update time for the original-playlist path.
- `packages/epg-sourcing`'s fetch methods hit hardcoded hostnames with no
  injectable base URL, so only the parsers are unit tested, not the
  fetchers themselves.
- `packages/mediaplane/stub` has no ongoing runtime purpose beyond proving
  the interface — a candidate for removal once a real second
  implementation exists to serve as the live proof instead.

---

## Data Flows

These sequences are documented here rather than in a platform-specific file because each one crosses the client/server boundary.

### Startup

```
Server starts
  → store.Load() from channels.json
  → credentialStore.Load() + migrate legacy api_token if present
  → epg.loadCacheFromDisk() → serve cached EPG immediately
  → validator.RunProber() starts worker pool
  → discEngine.Run() starts crawl loop
  → epg.RunRefresher() fetches fresh schedule
  → HTTP server accepts connections

Client starts (Mac or Android)
  → check server health → GET /api/health
  → load channels → GET /playlist.m3u → M3U parser
  → load EPG → GET /epg.xml → EPG parser
  → render channel browse screen with channels + EPG data
```

### Normal discovery cycle

```
discEngine.Run() wakes (every 30min default)
  → fetchAll(ctx): all crawlers in parallel → []RawItem
  → LinkExtractor.Extract() → []CandidateLink
  → deduplicate
  → ChannelMatcher.Match() per candidate
  → matched → submitter.Submit()
  → unmatched → unmatched pool

validator.probe(candidate)
  → HEAD request → latency, status code
  → partial GET 10KB → bitrate estimate
  → compute score
  → score ≥ 0.5 → store.PromoteIfBetter()
    → if better than active → promote to active
    → /playlist.m3u updated immediately
    → clients pick up on next poll (≤60s)
```

### Self-healing

```
validator.probeAll() (every 10 min)
  → active link score < 0.3
  → mark → StateQuarantine
  → best candidate with score ≥ 0.5 → promote to StateActive
  → /playlist.m3u updated
  → client refresh (up to 60s) → player loads new URL

No healthy candidate?
  → trigger discEngine.TriggerRun() immediately
  → surface warning at GET /api/health
```

### Pre-match escalation

```
Priority loop (every 15 min):
  → epg.PriorityChannelIDs(2h) → channels with match in <2h
  → discEngine.SetPriorityChannels(ids, matchEnd)

discEngine.Run():
  → checks priority channels set
  → crawl interval → 5 min (vs 30 min default)
  → aggressive crawl ensures validated links ready before kickoff

At kickoff:
  → user opens app → channel has healthy, scored, validated link
  → playback starts immediately
```

### Client playback

```
User taps a channel card
  → client resolves channel.streamURL
  → player buffers per bufferPreset, begins playback
  → on failure: retry ×3 with 2s delay, then error overlay
  → playback state surfaces to OS-level Now Playing / media session
```

Platform-specific playback wiring (AVPlayer vs Media3 ExoPlayer) is in [mac-architecture.md](mac-architecture.md#playback-engine) and [android-architecture.md](android-architecture.md).

---

## Repository Layout

```
nativestream/
├── README.md · README_SERVER.md · README_ANDROID.md · README_MAC.md
├── ordo.yaml · Makefile          ← two equivalent command runners, see development.md
├── Dockerfile · docker-compose.yml   ← server container build, unverified — see development.md
├── release.sh                    ← per-component version bump + tag + push, see releasing.md
├── apps/
│   ├── server/     ← Go backend (control plane), see server-architecture.md
│   ├── macos/      ← Swift Mac app, see mac-architecture.md
│   └── android/    ← Kotlin Android app, see android-architecture.md
├── packages/
│   ├── mediaplane/     ← media plane interface contract + stub
│   ├── discovery/      ← extracted discovery (crawlers, extractor, matcher)
│   ├── proxy/           ← extracted proxy (headers, rewriter, SSRF guard)
│   ├── epg-sourcing/    ← extracted EPG fetch/parse
│   └── sdk-gen/          ← shared protobuf contract, generated per-language
├── docs/
└── scripts/
    ├── install.sh
    ├── brew-release.sh
    └── release.sh
```

For local dev commands (`ordo server:dev`, `make build-server`, etc.), see [development.md](development.md). For how `release.sh` and CI turn a tag into a published artifact, see [releasing.md](releasing.md).

For per-platform package/module structure, see the responsibility tables in [server-architecture.md](server-architecture.md#package-responsibility), [mac-architecture.md](mac-architecture.md#module-responsibility), and [android-architecture.md](android-architecture.md). Those are deliberately responsibility tables rather than file trees — a tree needs editing every time a file is added or moved; a responsibility table only needs editing when responsibility itself moves, which is far rarer. Prefer that pattern for future structural docs over embedding `tree` output.

---

## Key Decisions

| Decision | Chosen | Rejected | Why |
|---|---|---|---|
| Where intelligence lives | Server (discovery, scoring, healing) | Client-side scoring | Clients stay thin and swappable; one source of truth for link health |
| Client-server protocol | Plain HTTP polling (60s) | WebSocket push for playlist/EPG | Simplicity; 60s staleness is acceptable for a channel list that changes on the order of minutes |
| Server config format | Custom flat-file parser | YAML library | Keeps the server dependency-free |
| Snapshot strategy | Periodic + atomic rename | Write-through on every mutation | Bounds disk I/O; atomic rename avoids partial-write corruption |
| Self-healing trigger | Score threshold (< 0.3) | Fixed failure count | Score already blends latency/reachability/bitrate — reusing it avoids a second signal to maintain |
| Cross-device control | Server-brokered WebSocket, no P2P | Peer-to-peer WebRTC | Server already has complete session state; simpler than NAT traversal — see [local-media-connect.md](local-media-connect.md) |
| Server centralization model | Split by role: control plane centralized, media plane pluggable | Fully centralized (media plane shared too) / fully self-hosted (whole stack per user) | Media plane cost/legal exposure scale with usage; fully self-hosted makes app-store review surface unbounded |
| Auth model | Per-person revocable credentials, issuance UX deferred | Full accounts now / single shared token | Removes the actual ceiling (unrevoked shared access) without committing to an onboarding shape that isn't decided yet |
| Media plane package boundary | Extract only what's genuinely swappable, behind an interface the control plane calls through | Config-flag pluggability on the existing monolith / full microservices split | A flag without a real interface is a fake seam. Microservices are premature at current scale |
| Discovery matching / EPG channel assignment | Stay control-plane-side, behind narrow ports (`ChannelLookup`) | Extract alongside crawlers/parsers | Both read channel keyword state directly — genuinely control-plane data, not sourcing |
| SSRF filtering | Declared by each media plane implementation, not enforced by the control plane | Control plane re-validates every implementation's outbound URLs | SSRF protection belongs to whoever's infrastructure makes the outbound request |
| Proof of the media plane interface | A second implementation (`packages/mediaplane/stub`) that must compile and swap in with zero control-plane source changes | Trusting the interface definition alone | An interface can look decoupled while the only real implementation still assumes control-plane internals |