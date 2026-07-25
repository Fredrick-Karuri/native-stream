# NativeStream Proxy — Technical Documentation

> `app/server/proxy/` — HLS proxy with header injection, playlist rewriting, and runtime toggle.

---

## What It Does

The proxy sits between the NativeStream client (Android/macOS) and upstream CDN stream URLs. It solves a specific problem: some HLS streams require custom HTTP headers (`Referer`, `User-Agent`, `Origin`) on **every request** — not just the initial playlist fetch, but every segment `.ts` file too. Native players (ExoPlayer, AVPlayer) fetch segments directly and have no mechanism to inject per-stream headers on those requests. The proxy handles this transparently.

---

## Architecture

```
Client (ExoPlayer / AVPlayer)
        │
        │  GET /stream/{channelId}/proxy
        ▼
┌─────────────────────────────────┐
│  proxy/proxy.go — ServeHTTP     │
│                                 │
│  1. Fetch upstream playlist     │──── injectHeaders() ────► CDN
│  2. Detect master vs media      │
│  3. Rewrite URLs in playlist    │
│  4. Return rewritten playlist   │
└─────────────────────────────────┘
        │
        │  GET /stream/{channelId}/proxy/seg/{hash}.ts
        ▼
┌─────────────────────────────────┐
│  Segment cache lookup           │
│  (hash → original URL + hdrs)  │
│                                 │
│  Fetch segment with headers     │──── injectHeaders() ────► CDN
│  Stream bytes to client         │
└─────────────────────────────────┘
```

---

## Request Routing

Three distinct request types are handled in `ServeHTTP`:

### 1. Segment requests
```
GET /stream/{channelId}/proxy/seg/{hash}.ts
```
Hash is looked up in `segmentCache` (`sync.Map`). The cached entry contains the original CDN URL and per-stream headers. Headers are injected and bytes are streamed to the client.

### 2. Variant playlist requests (master playlist children)
```
GET /stream/{channelId}/proxy?url={encoded-url}
```
When a master playlist is rewritten, variant playlist URLs are routed here. The server fetches the variant, rewrites its segments, and returns the rewritten media playlist.

### 3. Main playlist request
```
GET /stream/{channelId}/proxy
GET /stream/{channelId}/proxy/
```
Fetches the channel's active link URL, detects playlist type, and routes to the appropriate rewriter.

---

## Playlist Rewriting

`proxy/rewriter.go` handles two cases.

### Master playlist
Identified by presence of `#EXT-X-STREAM-INF`. Variant URLs are rewritten to route through `?url=` so the proxy can fetch and rewrite them with headers:

```
https://cdn.example.com/hls/720p/index.m3u8
  →  /stream/{id}/proxy?url=https%3A%2F%2Fcdn.example.com%2Fhls%2F720p%2Findex.m3u8
```

### Media playlist
Segment URLs (both absolute and relative) and `URI=` attributes (used in `#EXT-X-KEY`, `#EXT-X-MAP`) are rewritten to segment cache routes:

```
https://cdn.example.com/seg/001.ts
  →  /stream/{id}/proxy/seg/{md5(full-url)}.ts
```

Relative URLs are resolved against the playlist's base URL before hashing.

### Segment cache
`segmentCache` is a `sync.Map[string, cachedSegment]`. Each entry stores:
```go
type cachedSegment struct {
    TargetURL string
    Headers   map[string]string
}
```
Entries expire after 8 minutes via a goroutine timer. The key is `md5(full-url)` — hashing the **full** resolved URL (not just the path) avoids collisions between segments from different CDN hosts with identical paths.

---

## Header Injection

`proxy/headers.go` injects three classes of headers:

| Header | Source | Notes |
|---|---|---|
| `Referer` | `Config.Referer` | Set from `config.yaml` |
| `User-Agent` | `Config.UserAgent` | Falls back to a default macOS UA if blank |
| `Origin` | `Config.Origin` | Only set if configured |
| `Range` | Forwarded from client | Important for segment byte-range requests |
| Per-stream headers | `ActiveLink.Headers` | Set via `InjectFromMap()`, overrides static config |

Per-stream headers (from `ActiveLink.Headers`) override static config values for the same key. This allows per-channel `User-Agent` or auth tokens without changing global config.

---

## Runtime Toggle

The proxy `enabled` state is an `atomic.Bool` — not a static config value. This allows toggling at runtime without a server restart.

### API

```bash
# Read current state
GET /api/proxy/config
→ { "enabled": false }

# Toggle
PUT /api/proxy/config
Body: { "enabled": true }
→ { "enabled": true }
```

The toggle affects playlist generation immediately — `handlePlaylist` reads `proxy.IsEnabled()` on every request. It does **not** persist across server restarts; the startup value comes from `config.yaml`. Clients own the preference and sync it on launch.

### Playlist generation

When enabled, `playlist.Generate()` rewrites stream URLs in the served `.m3u` to point through the proxy:
```
https://cdn.example.com/live/channel.m3u8
  →  http://{serverAddr}/stream/{channelId}/proxy
```

When disabled, original CDN URLs are served directly.

---

## Configuration

Static config in `~/.config/nativestream/config.yaml`:

```yaml
proxy:
  enabled: false          # runtime-overridable via PUT /api/proxy/config
  referer: ""             # e.g. https://example.com
  user_agent: ""          # defaults to Mozilla/5.0 macOS UA if blank
  origin: ""              # e.g. https://example.com
```

Config fields map to `proxy.Config`:
```go
type Config struct {
    Enabled   bool
    Referer   string
    UserAgent string
    Origin    string
}
```

---

## File Reference

| File | Responsibility |
|---|---|
| `proxy.go` | `ServeHTTP` — request routing, upstream fetch, response streaming |
| `rewriter.go` | Playlist detection, URL rewriting, segment cache management |
| `headers.go` | Static header injection (`injectHeaders`) and per-stream override (`InjectFromMap`) |
| `client.go` | Shared `*http.Client` with redirect limit (max 5) |

---

## Key Decisions

| Decision | Chosen | Rejected | Why |
|---|---|---|---|
| Segment identity key | `md5(full URL)` | `md5(url.Path)` | Path-only hashing caused collisions between CDN hosts with identical paths |
| Toggle mechanism | `atomic.Bool` in `Proxy` struct | Static config field | Allows runtime toggle via API without server restart |
| Variant playlist routing | `?url=` query param | Separate cache entry like segments | Variants are playlists that need their own rewrite pass; routing through the same handler keeps the logic unified |
| Header precedence | Per-stream headers override static config | Merge/append | Streams may need conflicting header values; last-write-wins is simpler and safer |
| Segment TTL | 8 minutes fixed | `#EXTINF` duration × N | Parsing segment duration adds complexity; 8m covers ~48 × 10s segments safely |
| Proxy state persistence | Client-owned (DataStore / UserDefaults) | Server-persisted | Server restarts reset to config.yaml; clients sync on launch |

---

## Troubleshooting

### Streams 403 / auth failures through proxy
- Check `Referer` and `User-Agent` are set in `config.yaml`
- Verify `ActiveLink.Headers` contains any required auth headers (set via `PUT /api/channels/{id}`)
- Enable debug logging: set log level to `debug` in config — proxy logs each upstream request

### Segment cache miss (410 Gone)
- Segment token expired (8-minute TTL)
- This happens if the player pauses for >8 minutes, then resumes
- Client should re-request the playlist to get fresh segment URLs

### Master playlist quality variants not working
- Verify the upstream URL is a master playlist (`#EXT-X-STREAM-INF` present)
- The proxy rewrites variant URLs as `?url=` routes — check player logs for requests to `/stream/{id}/proxy?url=`
- If the variant URL is relative and resolution fails, check the base URL is correct in the channel's `ActiveLink.URL`

### Toggle not persisting across server restart
- Expected behavior — the server reads `config.yaml` on startup
- Set `proxy.enabled: true` in `config.yaml` if you want it on by default
- Clients sync state from `GET /api/proxy/config` on launch and re-apply their stored preference