# Architecture

**Last updated:** 2026-05-09.

This is the cross-cutting view: how the pieces fit together and why. For internals specific to one platform, see:

- [server-architecture.md](server-architecture.md) — Go server internals
- [mac-architecture.md](mac-architecture.md) — Swift/macOS internals
- [android-architecture.md](android-architecture.md) — Kotlin/Android internals

## Overview

NativeStream is a two-process system per client platform. Each client is a thin, stateless-ish poller; all intelligence — discovery, validation, scoring, self-healing — lives in the server.

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
│  ├── Link Validator (20-worker probe pool)       │
│  ├── Discovery Engine (5 crawler types)          │
│  ├── EPG Engine (XMLTV generator)                │
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

## Data Flows

These sequences are documented here rather than in a platform-specific file because each one crosses the client/server boundary.

### Startup

```
Server starts
  → store.Load() from channels.json
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
  → matched → validator.Submit()
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
├── Dockerfile · docker-compose.yml · ordo.yaml   ← container deployment path, undocumented — see note below
├── Makefile · release.sh
├── app/
│   ├── server/     ← Go backend, see server-architecture.md
│   ├── macos/      ← Swift Mac app, see mac-architecture.md
│   └── android/    ← Kotlin Android app, see android-architecture.md
├── docs/
└── scripts/
    ├── install.sh
    ├── brew-release.sh
    └── release.sh
```

**Open gap:** the repo root now has `Dockerfile`, `docker-compose.yml`, and `ordo.yaml` alongside the Homebrew path. None of these are covered anywhere in the current docs — it's not clear from the file names alone what `ordo.yaml` configures or whether the Docker path is the recommended one going forward. This needs a pass from whoever added them; once confirmed, it belongs as a "Run with Docker" section in [README_SERVER.md](../README_SERVER.md), not invented here.

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