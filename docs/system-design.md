# NativeStream — System Design

**Last Updated:** 2026-05-09  
**Status:** Current — supersedes all prior versions

---

## Table of Contents

1. [Overview](#1-overview)
2. [Full Architecture](#2-full-architecture)
3. [Component: Mac App](#3-component-mac-app)
4. [Component: StreamServer](#4-component-streamserver)
5. [Component: Discovery Engine](#5-component-discovery-engine)
6. [Component: Link Validator](#6-component-link-validator)
7. [Component: EPG Engine](#7-component-epg-engine)
8. [Data Model](#8-data-model)
9. [Data Flows](#9-data-flows)
10. [API Reference](#10-api-reference)
11. [Storage Design](#11-storage-design)
12. [Concurrency Model](#12-concurrency-model)
13. [Configuration](#13-configuration)
14. [Technology Stack](#14-technology-stack)
15. [Folder Structure](#15-folder-structure)

---

## 1. Overview

NativeStream is a two-process system. The Mac app is a SwiftUI live TV client. The server is a Go binary running on localhost. They communicate over HTTP on port 8888.

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
│  ├── Discovery Engine (4 crawler types)          │
│  ├── EPG Engine (XMLTV generator)                │
│  └── HTTP API (REST + playlist + EPG endpoints)  │
└────────────────────┬─────────────────────────────┘
                     │
     ┌───────────────┼───────────────┐
     ▼               ▼               ▼
 GitHub Gists    Reddit/Telegram  ESPN/football-data.org
 (stream links)  (stream links)   (match schedules)
```

The app never knows where stream links come from. It polls one URL (`/playlist.m3u`) and plays whatever is there. All intelligence — discovery, validation, scoring, self-healing — lives in the server.

---

## 2. Full Architecture

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
║                                                                  ║
║  ┌──────────────────────────────────────────────────────────┐   ║
║  │  DISCOVERY ENGINE                                         │   ║
║  │  GistCrawler · RedditCrawler · TelegramCrawler · DirectM3U│  ║
║  │  LinkExtractor (regex) → ChannelMatcher (keywords)       │   ║
║  │  CircuitBreaker (5 failures → suspend 1h)                │   ║
║  └─────────────────────────┬────────────────────────────────┘   ║
║                             │ candidate links                    ║
║  ┌──────────────────────────▼────────────────────────────────┐  ║
║  │  LINK VALIDATOR                                            │  ║
║  │  20 goroutine worker pool                                  │  ║
║  │  HEAD probe → latency score                               │  ║
║  │  Partial GET → bitrate estimate + HLS format check        │  ║
║  │  Score = latency×0.4 + reachability×0.4 + bitrate×0.2    │  ║
║  │  States: candidate → active → quarantine → evicted        │  ║
║  │  Self-healing: score < 0.3 → promote best candidate       │  ║
║  └─────────────────────────┬────────────────────────────────┘   ║
║                             │ scored links                       ║
║  ┌──────────────────────────▼────────────────────────────────┐  ║
║  │  CHANNEL STORE                                             │  ║
║  │  sync.RWMutex in-memory map[channelID]*Channel            │  ║
║  │  Atomic JSON snapshot every 5 min                         │  ║
║  │  One channel → N candidate links, ranked by score         │  ║
║  └─────────────────────────┬────────────────────────────────┘   ║
║                             │                                    ║
║  ┌──────────────────────────▼────────────────────────────────┐  ║
║  │  EPG ENGINE                                                │  ║
║  │  ESPN public API + football-data.org (optional key)       │  ║
║  │  Generates XMLTV · caches to disk · 6h refresh cycle      │  ║
║  │  Match-aware: escalates discovery priority pre-kickoff    │  ║
║  └─────────────────────────┬────────────────────────────────┘   ║
║                             │                                    ║
║  ┌──────────────────────────▼────────────────────────────────┐  ║
║  │  HTTP API                                                  │  ║
║  │  GET /playlist.m3u  GET /epg.xml                          │  ║
║  │  GET /stream/:id/proxy  (optional HLS proxy)              │  ║
║  │  GET/POST/PUT/DEL /api/channels                           │  ║
║  │  GET /api/health  POST /api/probe                         │  ║
║  │  GET /api/discovery/status  POST /api/discovery/run       │  ║
║  │  Middleware: request logging (slog) + panic recovery       │  ║
║  └──────────────────────────┬─────────────────────────────── ┘  ║
╚═════════════════════════════╪════════════════════════════════════╝
                              │ HTTP localhost:8888
╔═════════════════════════════▼════════════════════════════════════╗
║  NATIVESTREAM MAC (Swift / SwiftUI)                              ║
║                                                                  ║
║  AppShell (custom tab bar — no NavigationSplitView)              ║
║  ├── Browse tab     BrowserScreen                                ║
║  │   ├── SportNavRail (64pt icon strip)                          ║
║  │   ├── FilterBar (search + chips + view toggle)                ║
║  │   └── ChannelCard grid (live/active/default states)           ║
║  ├── Match Day tab  MatchDayScreen                               ║
║  │   ├── Live Now section (MatchCard with pulsing dot + score)   ║
║  │   └── Up Next section (upcoming matches from EPG)             ║
║  ├── TV Guide tab   EPGGridScreen                                ║
║  │   ├── Sticky channel column (172pt)                           ║
║  │   ├── Horizontal time scroll (30min slots, 150pt each)        ║
║  │   └── Now-line (animated, updates every 60s)                  ║
║  ├── Player         PlayerScreen (full window)                   ║
║  │   ├── AVPlayerRepresentable (AVFoundation / VideoToolbox)     ║
║  │   ├── Match score overlay (parsed from EPG title)             ║
║  │   ├── Controls overlay (auto-hide 3s)                         ║
║  │   └── PiP · AirPlay · Now Playing · Background Audio          ║
║  └── Mini Player    MiniPlayerWidget (floating, bottom-right)    ║
║                                                                  ║
║  ViewModels: PlaylistVM · EPGViewModel · PlayerViewModel         ║
║              ServerHealthViewModel · FavouritesManager           ║
║  Services:   SettingsStore · NowPlayingService · RefreshScheduler║
║  Parsers:    M3UParser (actor) · EPGParser (XMLParser, SAX)      ║
╚══════════════════════════════════════════════════════════════════╝
                              │
╔═════════════════════════════▼════════════════════════════════════╗
║  HLS STREAM CDN (external)                                       ║
║  .m3u8 master playlists + .ts segments                           ║
╚══════════════════════════════════════════════════════════════════╝
```

---

## 3. Component: Mac App

### 3.1 App Structure

The root scene is `AppShell` — a custom VStack with a 44pt tab bar and swappable content. No `NavigationSplitView`.

```
NativeStreamApp (@main)
└── AppShell
    ├── AppTabBar           (44pt — Browse / Match Day / TV Guide)
    └── tab content
        ├── BrowserScreen
        ├── MatchDayScreen
        ├── EPGGridScreen
        └── PlayerScreen    (full-window, shown on channel select)
        
    MiniPlayerWidget        (floating overlay, z-order above all tabs)
    OnboardingView          (shown once on first launch)
```

### 3.2 ViewModels

All `@Observable`, all `@MainActor`. State mutation only on main thread.

```swift
PlaylistViewModel
  channels: [Channel]          // from /playlist.m3u
  sources: [PlaylistSource]    // persisted to disk
  loadAll() async              // parallel fetch from all sources
  scheduleAutoRefresh()        // background Task.sleep loop

EPGViewModel
  store: EPGStore?             // from /epg.xml
  currentProgramme(channel)   -> Programme?
  nextProgramme(channel)      -> Programme?
  schedule(channel, hours:)   -> [Programme]

PlayerViewModel
  currentChannel: Channel?
  player: AVPlayer?
  isPlaying: Bool
  quality: StreamQuality
  play(channel:)              // creates AVPlayerItem, observes status
  retry()                     // up to 3x with 2s delay
  togglePlayback()
  setQuality(_:)              // adjusts preferredPeakBitRate
  enterPiP()                  // posts notification for PlayerScreen

ServerHealthViewModel
  status: ServerStatus        // .unknown / .connected(total,healthy) / .unreachable
  check(serverURL:) async     // GET /api/health
  startPolling(serverURL:, interval:)  // 30s background loop
```

### 3.3 Parsers

**M3UParser** — Swift actor. Parses `#EXTM3U` / `#EXTINF` attributes (`tvg-id`, `tvg-logo`, `group-title`, display name). Handles local `file://` and remote `https://` URLs. Malformed entries are skipped with a warning; never throws on bad input.

**EPGParser** — Swift actor implementing `XMLParserDelegate`. SAX-style (no full DOM load). Parses XMLTV `<channel>` and `<programme>` elements. Produces `EPGStore` — a `[String: [Programme]]` keyed by `tvg-id`. Supports files >100MB.

### 3.4 Playback Engine

```swift
// AVPlayer configuration
let item = AVPlayerItem(url: streamURL)
item.preferredForwardBufferDuration = bufferPreset.seconds  // 2 / 8 / 30
player.automaticallyWaitsToMinimizeStalling = true
// VideoToolbox hardware decode: automatic on Apple Silicon via AVFoundation
// Quality lock: item.preferredPeakBitRate = 8_000_000 (1080p) / 4M / 1.5M
```

Platform integrations:

| Feature | API |
|---|---|
| PiP | `AVPictureInPictureController` |
| AirPlay | `AVRoutePickerView` |
| Now Playing | `MPNowPlayingInfoCenter` |
| Media keys | `MPRemoteCommandCenter` |
| Background audio | `AVAudioSession` category `.playback` |

### 3.5 Design System

All colours, typography, and spacing live in `DesignSystem.swift` as `NS.*` constants. No hardcoded values anywhere else.

**Palette:**
```
NS.bg        #060810    NS.surface   #0d1120    NS.surface2  #131826
NS.accent    #0ea5e9    NS.accent2   #38bdf8    NS.text      #e8eaf0
NS.green     #10b981    NS.live      #ef4444    NS.amber     #f59e0b
```

**Fonts:** Syne (display/headings) · Instrument Sans (body) · DM Mono (numbers/timestamps/code)

### 3.6 Persistence

| Data | Storage |
|---|---|
| Playlist sources | `~/Application Support/NativeStreamMac/playlist_sources.json` |
| Settings (server URL, EPG URL, buffer preset) | `UserDefaults` |
| Favourites | `UserDefaults` |
| Onboarding complete flag | `UserDefaults` |

---

## 4. Component: StreamServer

### 4.1 Entry Point

```go
// cmd/main.go wiring order:
config.Load()
store.New() → store.Load()
validator.New()
epg.New()
proxy.New()
discovery.NewEngine() + crawlers
api.New() → Mux() → discovery.RegisterRoutes()
// Middleware: LoggingMiddleware(RecoveryMiddleware(mux))
// Background goroutines:
go store.RunSnapshotter()
go validator.RunProber()
go epg.RunRefresher()
go discEngine.Run()
go priorityEscalationLoop()   // checks EPG every 15min, adjusts discovery interval
// HTTP server with graceful shutdown on SIGTERM/SIGINT (10s drain)
```

### 4.2 Channel Store

```go
type Store struct {
    mu       sync.RWMutex
    channels map[string]*Channel   // keyed by channel ID (slug)
    path     string                // snapshot path
}

type Channel struct {
    ID, Name, GroupTitle, TvgID, LogoURL string
    Keywords   []string
    ActiveLink *LinkScore
    Candidates []*LinkScore        // ranked by score desc
    CreatedAt, UpdatedAt time.Time
}
```

Snapshot: atomic write via `.tmp` rename. Loaded on startup. Written every 5 min and on SIGTERM.

### 4.3 HTTP Middleware Stack

```
request → LoggingMiddleware → RecoveryMiddleware → mux router
```

`LoggingMiddleware`: logs method, path, status, duration_ms via `log/slog`.  
`RecoveryMiddleware`: catches panics, returns 500, server continues running.  
Server binds to `127.0.0.1` only — never `0.0.0.0`.

---

## 5. Component: Discovery Engine

### 5.1 Pipeline

```
Every N minutes (default 30, priority 5 for pre-match channels):

fetchAll(ctx) → parallel crawl all sources → []RawItem
LinkExtractor.Extract(items) → []CandidateLink  (regex: .m3u8 URLs)
  → .m3u URLs: fetch + re-parse for individual streams
deduplicate(candidates)
for each candidate:
  ChannelMatcher.Match(&link) → channelID or ""
  if matched: validator.Submit(Candidate{URL, ChannelID, SourceURL})
  if not matched: append to unmatched pool (capped at 200)
```

### 5.2 Crawlers

**GistCrawler** — `GET https://api.github.com/gists/:id` for each configured Gist ID. Fetches raw file content. Uses `If-Modified-Since` (conditional GET) to skip unchanged gists. Respects `X-RateLimit-Remaining` header. Optional GitHub token for 5000 req/hr vs 60.

**RedditCrawler** — `GET https://www.reddit.com/r/{sub}/new.json?limit=25`. Extracts post `selftext` + `title` + `url`. Tracks newest post fullname (`t3_xxx`) to avoid re-processing. Rate limited to 1 req/sec per subreddit.

**TelegramCrawler** — `GET https://t.me/s/{channel}` (public web preview, no API key). Parses `<div class="tgme_widget_message_text">` elements with regex. No account required.

**DirectM3UCrawler** — fetches full `.m3u` content from stable URLs (e.g. `github.com/iptv-org/iptv`). Uses `ETag` / `Last-Modified` conditional fetches. Returns content as `NeedsExpansion: true` RawItem — extractor fetches and parses inline.

### 5.3 Channel Matching

```go
func (m *ChannelMatcher) Match(link *CandidateLink) string {
    combined := lower(link.URL + " " + link.ContextText)
    for _, ch := range store.All() {
        // 1. Keyword match (configured per channel)
        for _, kw := range ch.Keywords {
            if contains(combined, lower(kw)) { return ch.ID }
        }
        // 2. Fuzzy name match (≥2 word parts of length ≥3)
        if fuzzyNameMatch(combined, ch.Name) { return ch.ID }
    }
    return ""
}
```

### 5.4 Circuit Breaker

After 5 consecutive failures per source key, the source is suspended for 1 hour. Auto-recovers after suspension expires. Status visible at `GET /api/health`.

### 5.5 Match-Aware Priority

Every 15 minutes, `epg.PriorityChannelIDs(2 * time.Hour)` returns channel IDs with a match starting within 2 hours. `discEngine.SetPriorityChannels(ids, matchEnd)` marks those channels — the discovery loop uses `PriorityInterval` (default 5 min) for the next cycle instead of `DefaultInterval` (default 30 min). Reverts 30 min after match end.

---

## 6. Component: Link Validator

### 6.1 Scoring Formula

```
score = latency_score × 0.4 + reachability × 0.4 + bitrate_score × 0.2

latency_score:  <300ms→1.0  <800ms→0.7  <2000ms→0.4  else→0.1
reachability:   200/206→1.0  3xx→0.6  error/timeout→0.0
bitrate_score:  unknown→0.5  <500kbps→0.2  <1Mbps→0.4  <3Mbps→0.7  >3Mbps→1.0
```

Bitrate estimated from a 10KB partial GET: `kbps = bytes / elapsed_seconds × 8 / 1000`.

### 6.2 State Machine

```
[candidate] → probe → score ≥ 0.5 → PromoteIfBetter()
                                         if score > active.score → [active]
                                         else stay [candidate]

[active] → probe → score < 0.3 → [quarantine]
                                     → promote best candidate with score ≥ 0.5 → [active]
                                     → if no healthy candidate: trigger immediate Discovery.Run()

[quarantine] → retry probe → score ≥ 0.5 → [active]
             → fail count ≥ 3 → [evicted]
```

### 6.3 Probe Schedule

- New candidates from discovery: immediate probe (buffered channel, capacity 500)
- Active links: every 10 min
- Candidate links: every 30 min
- Worker pool: 20 goroutines, configurable

---

## 7. Component: EPG Engine

### 7.1 Data Sources

**ESPN** (`site.api.espn.com`) — public, no key required. Returns scoreboard with team names and start times.

**football-data.org** — free tier (10 req/min). Requires API key. Covers EPL, UCL, La Liga, Bundesliga, Serie A.

Both sources return `[]Match` → merged, deduplicated by match ID where possible.

### 7.2 XMLTV Generation

```go
// Programme title format:
"{HomeTeam} vs {AwayTeam} — {Competition}"
// e.g. "Arsenal vs Chelsea — Premier League"

// Start/stop times in XMLTV format:
"20260509150000 +0000"

// Default match duration: 110 minutes (90 + stoppage)
```

The Mac app's `EPGParser` and `PlayerScreen` score overlay both parse this title format. Score overlay regex: `(\d+)\s*[–\-]\s*(\d+)`.

### 7.3 Caching

On startup: serve `epg_cache.xml` from disk immediately (fast first render). Fetch fresh data in background. Cache written to disk after each successful refresh. `epg.xml` endpoint always serves the in-memory copy — never blocks on network.

---

## 8. Data Model

### Swift (Mac App)

```swift
struct Channel: Identifiable, Codable, Sendable, Hashable {
    let id: UUID
    let tvgId: String          // links to EPG
    let name: String
    let groupTitle: String
    let logoURL: URL?
    let streamURL: URL
}

struct Programme: Codable, Sendable {
    let channelId: String
    let title: String          // "Home vs Away — Competition"
    let start: Date
    let stop: Date
    var progress: Double       // elapsed / duration, clamped 0–1
    var isNow: Bool            // start ≤ now < stop
}

struct PlaylistSource: Identifiable, Codable, Sendable {
    let id: UUID
    var label: String
    var url: URL
    var refreshInterval: RefreshInterval   // manual/1h/6h/daily
    var lastFetched: Date?
}

enum BufferPreset: String { case low, balanced, reliable }
// .seconds: 2 / 8 / 30
```

### Go (Server)

```go
type Channel struct {
    ID, Name, GroupTitle, TvgID, LogoURL string
    Keywords   []string
    ActiveLink *LinkScore
    Candidates []*LinkScore
    CreatedAt, UpdatedAt time.Time
}

type LinkScore struct {
    URL, ChannelID, SourceURL  string
    Score          float64        // 0.0–1.0
    LatencyMS      int64
    EstBitrateKbps int
    State          LinkState      // candidate/active/quarantine/evicted
    FailCount      int
    LastChecked    time.Time
    DiscoveredAt   time.Time
}

type Match struct {
    HomeTeam, AwayTeam, Competition, Sport string
    KickOff  time.Time
    Duration time.Duration    // default 110 min
    ChannelIDs []string
}
```

---

## 9. Data Flows

### 9.1 Startup

```
Server starts
  → store.Load() from channels.json
  → epg.loadCacheFromDisk() → serve cached EPG immediately
  → validator.RunProber() starts worker pool
  → discEngine.Run() starts crawl loop
  → epg.RunRefresher() fetches fresh schedule
  → HTTP server accepts connections

Mac app starts
  → serverHealth.check(localhost:8888/api/health)
  → playlistVM.loadAll() → GET /playlist.m3u → M3UParser
  → epgVM.load() → GET /epg.xml → EPGParser
  → render BrowserScreen with channels + EPG data
```

### 9.2 Normal Discovery Cycle

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
    → Mac app picks up on next 60s refresh
```

### 9.3 Self-Healing

```
validator.probeAll() (every 10 min)
  → active link score < 0.3
  → mark → StateQuarantine
  → best candidate with score ≥ 0.5 → promote to StateActive
  → /playlist.m3u updated
  → Mac app refresh (up to 60s) → AVPlayer loads new URL

No healthy candidate?
  → trigger discEngine.TriggerRun() immediately
  → surface warning at GET /api/health
```

### 9.4 Pre-Match Escalation

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

### 9.5 Mac App Playback

```
User taps channel card in BrowserScreen
  → AppShell.selectChannel(channel)
  → playerVM.play(channel)
  → AVPlayerItem(url: channel.streamURL)
  → item.preferredForwardBufferDuration = bufferPreset.seconds
  → player.play()
  → observe item.status (async KVO)
    → .readyToPlay → isPlaying = true → PlayerScreen renders
    → .failed → retry (×3, 2s delay)
    → maxRetriesExceeded → error overlay
  → setupNowPlaying() → MPNowPlayingInfoCenter
  → PlayerScreen shown (AppShell.showPlayer = true)
```

---

## 10. API Reference

| Method | Path | Description |
|---|---|---|
| GET | `/playlist.m3u` | M3U of all channels with a healthy active link. Proxy URLs if proxy enabled. |
| GET | `/epg.xml` | XMLTV EPG for the next 48 hours. Served from in-memory cache. |
| GET | `/stream/:id/proxy` | Transparent HLS proxy with header injection. Streams, not buffered. |
| GET | `/api/channels` | JSON array of all channels with health status and candidate count. |
| GET | `/api/channels/:id` | Single channel detail including all link scores and states. |
| POST | `/api/channels` | Add a channel. Accepts name, group_title, tvg_id, logo_url, stream_url, keywords. |
| PUT | `/api/channels/:id` | Update channel. Accepts stream_url (triggers immediate probe), name, group_title. |
| DELETE | `/api/channels/:id` | Remove channel. Disappears from playlist immediately. |
| GET | `/api/health` | Server status: uptime, total channels, healthy count, last probe time. |
| POST | `/api/probe` | Trigger immediate out-of-schedule probe of all active links. |
| GET | `/api/discovery/status` | Per-source: last fetch time, links found, errors, suspension state. |
| POST | `/api/discovery/run` | Trigger immediate discovery cycle. Returns `{"status":"triggered"}`. |
| GET | `/api/discovery/unmatched` | Last 50 unmatched candidate URLs with source and context. |

**All endpoints** are localhost-only. No authentication — any process on the Mac can reach them. Intentional: this is a personal tool.

**Response format:** all `/api/*` endpoints return `application/json`. `/playlist.m3u` returns `application/x-mpegurl`. `/epg.xml` returns `application/xml`.

---

## 11. Storage Design

### Server

```
~/.config/nativestream/
├── config.yaml              ← user config (flat key: value parser, no yaml lib)
├── channels.json            ← channel store snapshot
│   {version, updated_at, channels: {id: Channel}}
├── epg_cache.xml            ← last generated XMLTV (served on startup)
└── discovery_state.json     ← last-seen timestamps per source (avoids re-processing)
```

`channels.json` atomic write: write to `.tmp` then `os.Rename()`. On partial write / crash, existing snapshot is preserved.

### Mac App

```
~/Library/Application Support/NativeStreamMac/
└── playlist_sources.json    ← [PlaylistSource] (Codable JSON)

UserDefaults keys:
  serverURL, epgURL, epgRefreshInterval,
  bufferPreset, favouriteChannelIDs,
  onboardingComplete
```

No database. No CoreData. Total on-disk state per user: < 1 MB.

---

## 12. Concurrency Model

### Go Server

```
main goroutine → HTTP server (goroutine-per-request via net/http)

Background goroutines (all cancelled via context.Context):
├── store.RunSnapshotter()     → ticker 5min, writes channels.json
├── validator.RunProber()      → 20-worker pool draining buffered channel
│                                + ticker 10min for periodic probes
├── epg.RunRefresher()         → ticker 6h, fetches schedule, regenerates XMLTV
├── discEngine.Run()           → main discovery loop with variable interval
└── priorityEscalationLoop()   → ticker 15min, reads EPG, calls SetPriorityChannels()

Synchronisation:
  store.mu          sync.RWMutex   (high read frequency, low write frequency)
  validator.queue   chan Candidate  (buffered, capacity 500)
  epg.mu            sync.RWMutex   (protects cached []byte)
  discEngine.mu     sync.Mutex     (protects source states + unmatched pool)
```

Graceful shutdown: `SIGTERM/SIGINT` → cancel context → all goroutines stop → `server.Shutdown(10s timeout)` → final snapshot written → exit.

### Swift App

```
@MainActor (all ViewModels — state mutation on main thread)

Background:
  M3UParser      actor   (parses off main, results delivered to main)
  EPGParser      actor   (SAX XMLParser, off main)
  RefreshScheduler actor (Task.sleep loop, triggers main-actor loadAll())

AVPlayer observation: async KVO via publisher(for: \.status).values
Timer publishers: .publish(every: 30/60) for progress bar + now-line updates
```

---

## 13. Configuration

`~/.config/nativestream/config.yaml` — flat `key: value` format, stdlib parser, no external dependencies.

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

All fields have defaults — server starts with zero config file.

---

## 14. Technology Stack

### Mac App

| Layer | Technology |
|---|---|
| Language | Swift 5.9+ |
| UI | SwiftUI (macOS 14+) |
| Player | AVFoundation / AVPlayer |
| Video decode | VideoToolbox (automatic via AVFoundation on Apple Silicon) |
| PiP | AVPictureInPictureController |
| AirPlay | AVRoutePickerView |
| Now Playing | MediaPlayer framework |
| XML parsing | Foundation XMLParser (SAX) |
| Persistence | UserDefaults + JSONEncoder/Decoder |
| Networking | URLSession async/await |
| Concurrency | Swift Structured Concurrency (actors, async/await, Task) |
| Minimum OS | macOS 14 Sonoma |
| Distribution | Notarised DMG (outside App Store) |

### Server

| Layer | Technology |
|---|---|
| Language | Go 1.22+ |
| HTTP | `net/http` stdlib |
| Logging | `log/slog` (Go 1.21+ stdlib) |
| Config | Custom flat-file parser (no yaml lib — avoids external dependency) |
| Concurrency | goroutines, sync.RWMutex, buffered channels, context cancellation |
| Persistence | `encoding/json` + atomic file writes |
| XML generation | `encoding/xml` stdlib |
| Process management | launchd (macOS) |
| Distribution | Homebrew tap + notarised binary |

**Zero runtime dependencies** beyond the Go standard library.

---

## 15. Folder Structure

```
nativestream/
├── app/
│   ├── server/                          ← Go backend
│   │   ├── cmd/main.go                  ← entry point, wiring
│   │   ├── api/
│   │   │   ├── handlers.go              ← all HTTP handlers
│   │   │   └── middleware.go            ← logging + recovery
│   │   ├── config/config.go             ← config loader + types
│   │   ├── discovery/
│   │   │   ├── types.go                 ← Crawler interface, RawItem, CandidateLink
│   │   │   ├── engine.go                ← main orchestration loop
│   │   │   ├── extractor.go             ← regex link extraction + M3U expansion
│   │   │   ├── matcher.go               ← keyword + fuzzy channel matching
│   │   │   ├── circuitbreaker.go        ← per-source failure/suspend logic
│   │   │   ├── api.go                   ← /api/discovery/* handlers
│   │   │   └── crawlers/
│   │   │       ├── gist.go
│   │   │       ├── reddit.go
│   │   │       ├── telegram.go
│   │   │       └── direct_m3u.go
│   │   ├── epg/
│   │   │   ├── engine.go                ← fetch, XMLTV generation, caching
│   │   │   ├── parsers.go               ← ESPN + football-data response parsers
│   │   │   └── priority.go              ← PriorityChannelIDs() for escalation
│   │   ├── logging/logger.go            ← slog setup (text/JSON switchable)
│   │   ├── playlist/generator.go        ← M3U output generator
│   │   ├── proxy/proxy.go               ← HLS transparent proxy
│   │   ├── service/service.go           ← launchd plist install/uninstall
│   │   ├── shutdown/shutdown.go         ← SIGTERM/SIGINT graceful handler
│   │   ├── store/
│   │   │   ├── store.go                 ← channel store + snapshot
│   │   │   └── store_test.go
│   │   ├── validator/
│   │   │   ├── validator.go             ← probe pool + scoring + self-healing
│   │   │   └── validator_test.go
│   │   └── go.mod
│   │
│   └── macos/NativeStreamMac/           ← Swift Mac app
│       ├── App/
│       │   ├── NativeStreamApp.swift    ← @main, environment wiring
│       │   ├── AppShell.swift           ← custom tab bar + screen routing
│       │   └── ContentView.swift        ← legacy (replaced by AppShell)
│       ├── Core/
│       │   ├── Models/
│       │   │   ├── DesignSystem.swift   ← NS.* tokens, fonts, spacing
│       │   │   ├── Channel.swift
│       │   │   ├── Programme.swift
│       │   │   ├── PlaylistSource.swift
│       │   │   └── Errors.swift
│       │   ├── Parsers/
│       │   │   ├── M3UParser.swift
│       │   │   ├── M3UParserTests.swift
│       │   │   └── EPGParser.swift
│       │   └── Services/
│       │       ├── SettingsStore.swift
│       │       ├── FavouritesManager.swift
│       │       ├── NowPlayingService.swift
│       │       ├── RefreshScheduler.swift
│       │       ├── MediaKeyHandler.swift
│       │       └── MenuBarManager.swift
│       ├── ViewModels/
│       │   ├── PlaylistViewModel.swift
│       │   ├── EPGViewModel.swift
│       │   ├── PlayerViewModel.swift
│       │   └── ServerHealthViewModel.swift
│       └── Views/
│           ├── NSComponents.swift       ← atomic component library
│           ├── Sidebar/
│           │   └── BrowserScreen.swift  ← UX-010–013
│           ├── Player/
│           │   ├── PlayerScreen.swift   ← UX-020–021
│           │   ├── MatchDayScreen.swift ← UX-030–032
│           │   ├── MiniPlayerWidget.swift ← UX-040
│           │   └── AVPlayerRepresentable.swift
│           ├── EPGGrid/
│           │   └── EPGGridScreen.swift  ← UX-050–051
│           └── Settings/
│               ├── SettingsScreenV4.swift ← UX-060–062
│               ├── OnboardingView.swift
│               └── ServerUnavailableView.swift
│
├── docs/
│   ├── PRODUCT.md                       ← product definition
│   ├── SYSTEM_DESIGN.md                 ← this document
│   ├── README_SERVER.md                 ← server setup + operation guide
│   └── README_CLIENT.md                 ← Mac app setup + usage guide
├── homebrew/
│   └── nativestream-server.rb           ← Homebrew formula
├── config/
│   └── config.example.yaml
├── scripts/
│   ├── install.sh
│   ├── release.sh
│   ├── brew-release.sh
│   └── test-notes-phase{1-4}.md
├── Makefile
├── .gitignore
└── README.md                            ← top-level quick-start only