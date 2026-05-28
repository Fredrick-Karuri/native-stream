# NativeStream Android — UI Specification

**Version:** 1.0  
**Last updated:** May 2026  
**Platform:** Android 8.0+ (API 26+)  
**Language:** Kotlin · Jetpack Compose · Material 3  
**Principle:** Lean and functional. Tap and watch. One thumb, one hand.

---

## Global chrome

**Min supported screen:** 360dp wide  
**Navigation:** Bottom navigation bar, 3 tabs — persistent except in PlayerScreen  
**Mini player:** Persistent bottom sheet (56dp tall) above bottom nav when a channel is playing, except in PlayerScreen  
**Status bar:** Transparent, dark icons on light / light icons on dark  

---

## Bottom Navigation

| Icon | Destination | Label |
|------|-------------|-------|
| `Home` | Now | "Now" |
| `GridView` | Browse | "Browse" |
| `Settings` | Settings | "Settings" |

Active state: `NS.accent` icon + `NS.accentGlow` indicator pill.  
Player is a full-screen Activity over the top — not a nav tab.

---

## Design Tokens (Compose)

```kotlin
object NS {
    // Backgrounds
    val bg       = Color(0xFF060810)
    val surface  = Color(0xFF0D1120)
    val surface2 = Color(0xFF131826)
    val surface3 = Color(0xFF1A2035)

    // Borders
    val border   = Color(0x0BFFFFFF)
    val border2  = Color(0x16FFFFFF)
    val border3  = Color(0x24FFFFFF)

    // Text
    val text     = Color(0xFFE8EAF0)
    val text2    = Color(0xFF8890B0)
    val text3    = Color(0xFF444E70)

    // Accent — Sky Blue
    val accent       = Color(0xFF0EA5E9)
    val accent2      = Color(0xFF38BDF8)
    val accentGlow   = Color(0x220EA5E9)
    val accentBorder = Color(0x380EA5E9)

    // Status
    val green  = Color(0xFF10B981)
    val live   = Color(0xFFEF4444)
    val amber  = Color(0xFFF59E0B)
}
```

**Typography:** `Syne` (headings) · `Instrument Sans` (body) · `DM Mono` (numbers/timestamps)  
Both available via Google Fonts in Compose.

---

## Mini Player

Persistent above bottom nav when playing. Tapping expands to PlayerScreen.

```
┌─────────────────────────────────────────────────┐
│ [logo 32dp]  Channel name        [⏸]  [✕]      │
│              Programme · 47'   [━━●━━━━━━━]     │
└─────────────────────────────────────────────────┘
```

- Height: 64dp
- Background: `NS.surface` + top border `NS.border`
- Progress bar: 2dp, `NS.accent` fill
- Swipe up → expands to PlayerScreen
- Swipe down → stops playback (with confirmation snackbar)
- Drag handle: optional, 32dp wide hairline

---

## Screen 1 — Now

**Job:** Show everything live right now without browsing.

### Top bar
- Logo mark 28dp + "What's on"
- Right: live count chip e.g. `11 live · 5 soon` in `NS.accent`

### Sections
All sections hidden when empty.

**Matches live**
- `NSPulseDot` + section header + count badge
- First live match: `MatchHeroCard` (full width, 200dp tall)
- Remaining: `LazyRow` of `MatchSmallCard` (180dp wide)
- Each card: team initials circles, score, live minute, channel name, progress bar

**Live on air**
- TV icon + section header
- `LazyColumn` of `LiveOnAirRow`
- Row: 48dp square channel logo, programme title, channel name, progress bar, LIVE badge

**Starting soon**
- Clock icon + section header
- `LazyRow` of `StartingSoonCard` (160dp wide)
- Card: kick-off time in `NS.accent`, team labels, event title, channel name

### Empty state
📺 + "Nothing on right now" + "Add a playlist source in Settings."

---

## Screen 2 — Browse

**Job:** Find a channel by sport or search. Tap to watch.

### Top bar
- "Browse"
- Search icon → expands to full-width `SearchBar` (Compose M3)

## Sport chips

`LazyRow` of filter chips below top bar:

| Chip | Icon | Filter logic |
|------|------|--------------|
| Football | `ti-ball-football` | groupTitle contains football keywords |
| Favourites | `ti-star` | `isFavourite == true` |
| Rugby | `ti-ball-rugby` | groupTitle contains rugby keywords |
| Tennis | `ti-tennis` | groupTitle contains tennis keywords |
| Basketball | `ti-ball-basketball` | groupTitle contains basketball/nba |
| Cricket | `ti-ball-cricket` | groupTitle contains cricket/ipl |
| All | `ti-layout-grid` | no filter |

Each chip: vertical stack — icon (20dp) above label (10sp). No emoji prefix.

Chip active state: `NS.accentGlow` bg + `NS.accentBorder` stroke + `NS.accent2` icon and label.
Chip inactive state: `NS.surface` bg + `NS.border` stroke + `NS.text2` icon and label.

### Content

`LazyVerticalGrid(columns = GridCells.Adaptive(minSize = 160.dp))`  
Grouped by `channel.groupTitle`, with `NSGroupHeader` sticky headers.

**`ChannelCard`**

```
┌──────────────────────────┐
│  [LIVE]            [★]   │  ← overlaid on artwork area
│                          │
│   16:9 logo / artwork    │
│                          │
│  [progress bar]          │
├──────────────────────────┤
│  Channel name            │  body2 / captionMed
│  Programme or time       │  caption / accent2 or text3
└──────────────────────────┘
```

- Playing state: `NS.accentBorder` stroke 1dp + ▶ NOW badge
- Live state: `NS.live` border 1dp + LIVE badge
- Corner radius: 10dp
- Ripple on press: `NS.accent` at 12% opacity

### Empty state
📺 + "No channels found" + contextual hint.

---

## Screen 3 — Player

**Job:** Watch a channel. Landscape-first. Switch channels from sidebar.

### Orientation
Forces landscape (`ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE`) on entry.  
Back button or swipe-down returns to previous screen and restores portrait.

### Layout
`Row` — video area (weight 1f) + sidebar (240dp, collapsible)

### Video area
- `AndroidView` wrapping `PlayerView` (Media3/ExoPlayer)
- Black background, fills parent
- Tap anywhere → show/hide controls (auto-hide after 3s)

**Top gradient overlay** (shown with controls):
```
[←]  Sky Sports 1 · Arsenal vs Chelsea — PL    [LIVE] [720p]
```

**Bottom gradient overlay** (shown with controls):
```
● LIVE                                              47:32
[━━━━━━━━━━●━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━]
[⏮]  [⏸]  [⏭]    [quality▾]  [cast]  [PiP]  [⛶]
```

- Progress track: 3dp, `NS.accent` fill, white thumb
- Play/pause: 48dp, `NS.accent` bg, `NS.bg` icon
- ⛶ sidebar toggle: hides/shows 240dp sidebar

**Match score overlay** (centre, always visible when EPG title matches "X vs Y"):
```
PREMIER LEAGUE
     Arsenal  1 – 0  Chelsea
              67'
```
- Score: Syne 52sp bold white
- Minute badge: DM Mono, `NS.accentGlow` bg, `NS.accentBorder` border

### Sidebar (240dp)
Two tabs: **On now** · **Schedule** via `TabRow`

**On now tab**
- `LazyColumn` of `PlayerSidebarRow`
- Sorted: playing → live → upcoming
- Row: 32dp square logo, channel name, programme or time
- Active row: `NS.accentGlow` bg + `NS.accent` left border 3dp
- Tap → switches channel immediately (no buffering indicator, just replace `MediaItem`)

**Schedule tab**
- EPG timeline for current channel only
- Past rows: `alpha(0.4f)`
- Current row: `NS.accentGlow` bg + progress bar + "Now" label
- Future rows: full opacity, kick-off time in `NS.accent`

### Picture-in-Picture
- Enters PiP on home button press while playing (Android 8+)
- PiP params: `16:9` ratio, play/pause remote action only
- Returns to full player on tap

### Cast (Chromecast)
- Cast button appears in controls when a receiver is on the network
- Uses `MediaRouter` + Cast SDK
- Replaces AirPlay equivalent from Mac app

### Fullscreen
Sidebar toggle in controls collapses the 240dp sidebar. Video fills window. Toggle restores it.

---

## Screen 4 — Settings

**Job:** Configure server, sources, and playback. No dead ends.

### Layout
Single `LazyColumn` with `ListItem` groups. No nested navigation — all sections inline with `ExpandableSection` pattern.

### Sections

**Server**
- Server URL — `OutlinedTextField`
- `NSCodeBlock` hints for quick start
- Server health row: `NSHealthDot` + status + uptime

**Playlist Sources**
- Source rows: health dot · label · URL (truncated) · refresh interval · delete icon
- Stale source: amber dot + amber tint
- "+ Add Source" row with dashed border button

**EPG / TV Guide**
- EPG URL field
- Refresh interval selector (`DropdownMenuBox`)
- Last fetched timestamp

**Playback**
- Buffer preset: segmented button group `Low · Default · High`
- Hardware decoding: toggle (disabled, always on — shown as info row)

**Proxy** (collapsed by default)
- Enable toggle
- Referer + User-Agent fields shown when enabled

**Discovery** (collapsed by default)
- Enable toggle
- Config hint + `NSCodeBlock` when enabled

**About**
- App version from `BuildConfig.VERSION_NAME`
- LogoMark 48dp centred + "NativeStream" wordmark
- Links: GitHub · Docs

---

## Components

| Component | Description |
|---|---|
| `ChannelCard` | Grid card with 16:9 artwork, LIVE/playing state, progress bar |
| `ChannelLogoSquare` | Square logo thumbnail, configurable size, fallback initials |
| `MatchHeroCard` | Full-width featured live match card with teams, score, progress |
| `MatchSmallCard` | Compact match card for horizontal scroll row |
| `LiveOnAirRow` | Row for non-match live content |
| `StartingSoonCard` | Compact upcoming match card |
| `PlayerSidebarRow` | Channel row in player sidebar |
| `NSLiveBadge` | Red pulsing "LIVE" pill |
| `NSProgressBar` | Thin progress indicator, optional glow |
| `NSChip` | Filter chip with active/inactive state |
| `NSGroupHeader` | Section header with rule line and count badge |
| `NSCodeBlock` | Monospace code block with copy button |
| `NSHealthDot` | 6dp circle, green/accent/red by score |
| `NSPulseDot` | 5dp red pulsing dot for live sections |
| `NSToggle` | Styled `Switch` using `NS.*` colours |
| `MiniPlayer` | Persistent bottom sheet above bottom nav |

---

## Architecture

```
:app
├── ui/
│   ├── now/          NowScreen + NowViewModel
│   ├── browse/       BrowseScreen + BrowseViewModel
│   ├── player/       PlayerActivity + PlayerViewModel
│   └── settings/     SettingsScreen + SettingsViewModel
├── data/
│   ├── api/          APIClient (Ktor) — same contract as Swift APIClient
│   ├── model/        Channel, Programme, PlaylistSource (data classes)
│   └── store/        SettingsDataStore (DataStore Preferences)
└── player/           ExoPlayerManager, CastManager
```

**State management:** `StateFlow` + `collectAsStateWithLifecycle()` in Compose  
**DI:** Hilt  
**Navigation:** Compose Navigation — `NavHost` for tabs, `Intent` for PlayerActivity  
**Network:** Ktor client with JSON serialization (mirrors Swift `APIClient` endpoints exactly)  
**Player:** Media3 ExoPlayer — `HlsMediaSource`, adaptive bitrate, hardware decode automatic  
**Cast:** `play-services-cast-framework`  
**Persistence:** DataStore Preferences for settings  

---

## APIClient (Kotlin/Ktor)

Mirrors the Swift `APIClient` exactly — same endpoints, same request/response shapes.

```kotlin
class APIClient(private val baseUrl: String = "http://192.168.1.x:8888") {

    // Health
    suspend fun health(): HealthResponse

    // Playlist & EPG (raw bytes — parsed by M3UParser / XmlParser)
    suspend fun playlistData(): ByteArray
    suspend fun epgData(): ByteArray

    // Channels
    suspend fun listChannels(): List<ChannelResponse>
    suspend fun getChannel(id: String): ChannelDetailResponse
    suspend fun createChannel(req: CreateChannelRequest): ChannelDetailResponse
    suspend fun updateChannel(id: String, req: UpdateChannelRequest)
    suspend fun deleteChannel(id: String)

    // Probe & discovery
    suspend fun triggerProbe()
    suspend fun discoveryStatus(): DiscoveryStatusResponse
    suspend fun triggerDiscovery()
    suspend fun unmatchedLinks(limit: Int = 50): UnmatchedResponse
}
```

**Server URL note:** Android connects to the server over LAN (not localhost).  
Set `host: 0.0.0.0` in `~/.config/nativestream/config.yaml` on the Mac/server.  
User enters the server's LAN IP in Settings (e.g. `http://192.168.1.42:8888`).

---

## M3U + EPG Parsers (Android)

**M3UParser** — pure Kotlin, line-by-line, mirrors Swift `M3UParser` logic:
- Parses `#EXTM3U` / `#EXTINF` attributes: `tvg-id`, `tvg-logo`, `group-title`
- Handles `InputStream` directly — no full-file buffering
- Malformed entries skipped with log warning

**EPGParser** — uses Android's `XmlPullParser` (SAX-equivalent, memory-efficient):
- Parses `<channel>` and `<programme>` elements from XMLTV
- Produces `Map<String, List<Programme>>` keyed by `tvg-id`
- Supports files >100MB

---

## Performance targets

| Metric | Target |
|---|---|
| Channel list load | < 1s on LAN |
| Stream start (tap → first frame) | < 3s |
| App cold start | < 2s |
| RAM steady-state during playback | < 120 MB |
| CPU during HLS playback | < 8% (hardware decode) |

---

## What this app does not do

- No stream discovery or channel management — server handles that
- No VOD or catch-up — live only
- No user accounts or cloud sync
- No notifications (bell icon reserved for future)
- No tablet-specific layout in MVP (phone-first, tablet inherits)
- No iOS — KMP shared data layer is Phase 2