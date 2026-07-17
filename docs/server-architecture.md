# Server Architecture

Go internals for the StreamServer. For how this fits with the clients, see [architecture.md](architecture.md). For the Swift and Android equivalents, see [mac-architecture.md](mac-architecture.md) and [android-architecture.md](android-architecture.md).

## Entry Point

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

## Channel Store

```go
type Store struct {
    mu       sync.RWMutex
    channels map[string]*Channel   // keyed by channel ID (slug)
    path     string                // snapshot path
}
```

Snapshot: atomic write via `.tmp` rename. Loaded on startup. Written every 5 min and on SIGTERM.

## HTTP Middleware Stack

```
request → LoggingMiddleware → RecoveryMiddleware → mux router
```

`LoggingMiddleware` logs method, path, status, duration_ms via `log/slog`. `RecoveryMiddleware` catches panics, returns 500, server continues running. Server binds to `127.0.0.1` only — never `0.0.0.0`.

---

## Discovery Engine

### Pipeline

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

### Crawlers

**GistCrawler** — `GET https://api.github.com/gists/:id` for each configured Gist ID. Fetches raw file content. Uses `If-Modified-Since` (conditional GET) to skip unchanged gists. Respects `X-RateLimit-Remaining`. Optional GitHub token for 5000 req/hr vs 60.

**RedditCrawler** — `GET https://www.reddit.com/r/{sub}/new.json?limit=25`. Extracts post `selftext` + `title` + `url`. Tracks newest post fullname (`t3_xxx`) to avoid re-processing. Rate limited to 1 req/sec per subreddit.

**TelegramCrawler** — `GET https://t.me/s/{channel}` (public web preview, no API key). Parses `<div class="tgme_widget_message_text">` elements with regex. No account required.

**DirectM3UCrawler** — fetches full `.m3u` content from stable URLs (e.g. `github.com/iptv-org/iptv`). Uses `ETag` / `Last-Modified` conditional fetches. Returns content as `NeedsExpansion: true` RawItem — extractor fetches and parses inline.

**LocalScriptCrawler** — runs a user-provided sidecar script (`.py` via `python3`, anything else via `/bin/bash`) as a subprocess with a 3-minute hard deadline. The script prints a JSON array of candidates to stdout; the crawler decodes it against a private schema (URL, channel name, group title, tvg-id, logo URL, custom headers) and maps it to `discovery.DirectCandidate`. If the configured script path doesn't exist, it logs at debug level and returns no candidates rather than erroring — the crawler is silently inert until a script is provided. This is the escape hatch for sources that don't fit the other four crawlers (e.g. a personal scraper, an authenticated feed, a local file transform).

### Channel Matching

```go
func (m *ChannelMatcher) Match(link *CandidateLink) string {
    combined := lower(link.URL + " " + link.ContextText)
    for _, ch := range store.All() {
        for _, kw := range ch.Keywords {
            if contains(combined, lower(kw)) { return ch.ID }
        }
        if fuzzyNameMatch(combined, ch.Name) { return ch.ID }
    }
    return ""
}
```

### Circuit Breaker

After 5 consecutive failures per source key, the source is suspended for 1 hour. Auto-recovers after suspension expires. Status visible at `GET /api/health`.

### Match-Aware Priority

Every 15 minutes, `epg.PriorityChannelIDs(2 * time.Hour)` returns channel IDs with a match starting within 2 hours. `discEngine.SetPriorityChannels(ids, matchEnd)` marks those channels — the discovery loop uses `PriorityInterval` (default 5 min) for the next cycle instead of `DefaultInterval` (default 30 min). Reverts 30 min after match end.

---

## Link Validator

### Scoring Formula

```
score = latency_score × 0.4 + reachability × 0.4 + bitrate_score × 0.2

latency_score:  <300ms→1.0  <800ms→0.7  <2000ms→0.4  else→0.1
reachability:   200/206→1.0  3xx→0.6  error/timeout→0.0
bitrate_score:  unknown→0.5  <500kbps→0.2  <1Mbps→0.4  <3Mbps→0.7  >3Mbps→1.0
```

Bitrate estimated from a 10KB partial GET: `kbps = bytes / elapsed_seconds × 8 / 1000`.

### State Machine

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

### Probe Schedule

- New candidates from discovery: immediate probe (buffered channel, capacity 500)
- Active links: every 10 min
- Candidate links: every 30 min
- Worker pool: 20 goroutines, configurable

---

## EPG Engine

### Data Sources

**ESPN** (`site.api.espn.com`) — public, no key required. Returns scoreboard with team names and start times.

**football-data.org** — free tier (10 req/min). Requires API key. Covers EPL, UCL, La Liga, Bundesliga, Serie A.

Both sources return `[]Match` → merged, deduplicated by match ID where possible.

### XMLTV Generation

```go
"{HomeTeam} vs {AwayTeam} — {Competition}"
// e.g. "Arsenal vs Chelsea — Premier League"

"20260509150000 +0000"   // start/stop time format

// Default match duration: 110 minutes (90 + stoppage)
```

The Mac app's `EPGParser` and score overlay both parse this title format. Score overlay regex: `(\d+)\s*[–\-]\s*(\d+)`.

### Caching

On startup: serve `epg_cache.xml` from disk immediately (fast first render). Fetch fresh data in background. Cache written to disk after each successful refresh. `epg.xml` endpoint always serves the in-memory copy — never blocks on network.

---

## Data Model

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

## Storage Design

```
~/.config/nativestream/
├── config.yaml              ← user config (flat key: value parser, no yaml lib)
├── channels.json            ← channel store snapshot
│   {version, updated_at, channels: {id: Channel}}
├── epg_cache.xml            ← last generated XMLTV (served on startup)
└── discovery_state.json     ← last-seen timestamps per source (avoids re-processing)
```

`channels.json` atomic write: write to `.tmp` then `os.Rename()`. On partial write / crash, existing snapshot is preserved. See [configuration.md](configuration.md) for the config file's full option reference.

---

## Concurrency Model

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

---

## Technology Stack

| Layer | Technology |
|---|---|
| Language | Go 1.22+ — the Dockerfile builds with `golang:1.25-alpine`; unclear if the non-Docker minimum has moved too or the Dockerfile is just ahead |
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

## Package Responsibility

Deliberately a responsibility table, not a file tree — a tree needs an edit every time a file is added; this only needs one when a responsibility moves. For the literal current layout, run `tree app/server` or browse the repo.

| Package | Owns |
|---|---|
| `cmd/` | Entry point and dependency wiring (`main.go`) |
| `api/` | HTTP handlers and middleware (logging, panic recovery) |
| `control/` | Local Media Connect: WebSocket hub (`hub.go`), envelope/message types (`protocol.go`), connection handling (`websocket.go`) |
| `discovery/` | Discovery engine orchestration, link extraction, channel matching, circuit breaker, and the `crawlers/` sub-package (Gist, Reddit, Telegram, Direct M3U, Local Script) |
| `epg/` | EPG fetch, XMLTV generation, caching, and pre-match priority calculation |
| `logging/` | `slog` setup |
| `playlist/` | M3U output generation |
| `proxy/` | HLS transparent proxy — client, header injection, and URL rewriting split across `client.go` / `headers.go` / `proxy.go` / `rewriter.go` |
| `service/` | launchd plist install/uninstall |
| `shutdown/` | `SIGTERM`/`SIGINT` graceful handler |
| `store/` | Channel store and snapshotting |
| `validator/` | Probe pool, scoring, self-healing state machine |
| `VERSION` | Current release version, read at build/release time |