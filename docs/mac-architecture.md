# Mac Architecture

Swift/SwiftUI internals for the macOS client. For how this fits with the server, see [architecture.md](architecture.md). For the Go and Android equivalents, see [server-architecture.md](server-architecture.md) and [android-architecture.md](android-architecture.md).

## App Structure

The root scene is `AppShell` — a custom `VStack` with a 44pt tab bar and swappable content. No `NavigationSplitView`.

```
NativeStreamApp (@main)
└── AppShell
    ├── AppTabBar           (44pt — Now / Browse / Match Day / Schedule / Favourites)
    └── tab content
        ├── NowScreen        (EPG-first home — live matches, on-air, starting soon)
        ├── BrowserScreen
        ├── MatchDayScreen
        ├── ScheduleScreen   (formerly documented as EPGGridScreen)
        ├── FavouritesScreen
        └── PlayerScreen     (full-window, shown on channel select)

    MiniPlayerWidget        (floating overlay, z-order above all tabs)
    OnboardingView          (shown once on first launch)
```

## ViewModels

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

BrowserViewModel
  searchText, selectedGroup, selectedSubGroup, selectedSource, showFavouritesOnly: state
  groupedSections: [ChannelSection]   // derived, recomputed off-main-actor
  recomputeSections(channels:)        // debounced 150ms on search, versioned to discard stale tasks
  selectSource(_:channels:) / selectGroup(_:) / selectSubGroup(_:) / toggleFavourites(...)
  // Owns All Channels browse filtering — mirrors Android's ChannelFilterViewModel

ChannelManagerViewModel
  channels: [ChannelResponse]
  discoveryStatus: DiscoveryStatusResponse?
  unmatched: [UnmatchedLink]
  loadChannels() / addChannel(...) / updateStreamURL(...) / deleteChannel(id:)
  triggerProbe() / loadDiscoveryStatus() / triggerDiscovery() / loadUnmatched() / assignUnmatched(...)
  // Thin wrapper over APIClient for server-side channel + discovery admin (used by Settings)
  // Mirrors Android's ChannelManagerViewModel, minus the discovery/unmatched surface (Android's is add-only so far)

ControlViewModel
  sessions: [SessionInfo]             // connected controllers only
  connected: Bool
  start(serverURL:deviceID:playerVM:) // connects as LMC target, listens for envelopes
  broadcastState(channelID:...:)      // sends state_update on every playback change
  // Mac is LMC target-only — handles inbound play/stop/volumeSet, never sends commands itself

NowScreenViewModel
  liveMatches / liveOnAir / startingSoon: [(channel, programme)]
  recompute(channels:epgVM:)          // buckets by isSportMatch + 2h startingSoon lookahead
  // Stateless between calls — NowScreen drives refresh via .task + a 60s clock tick
```

## Parsers

**M3UParser** — Swift actor. Parses `#EXTM3U` / `#EXTINF` attributes (`tvg-id`, `tvg-logo`, `group-title`, display name). Handles local `file://` and remote `https://` URLs. Malformed entries are skipped with a warning; never throws on bad input.

**EPGParser** — Swift actor implementing `XMLParserDelegate`. SAX-style (no full DOM load). Parses XMLTV `<channel>` and `<programme>` elements. Produces `EPGStore` — a `[String: [Programme]]` keyed by `tvg-id`. Supports files >100MB.

## Playback Engine

```swift
let item = AVPlayerItem(url: streamURL)
item.preferredForwardBufferDuration = bufferPreset.seconds  // 2 / 8 / 30
player.automaticallyWaitsToMinimizeStalling = true
// VideoToolbox hardware decode: automatic on Apple Silicon via AVFoundation
// Quality lock: item.preferredPeakBitRate = 8_000_000 (1080p) / 4M / 1.5M
```

| Feature | API |
|---|---|
| PiP | `AVPictureInPictureController` |
| AirPlay | `AVRoutePickerView` |
| Now Playing | `MPNowPlayingInfoCenter` |
| Media keys | `MPRemoteCommandCenter` |
| Background audio | `AVAudioSession` category `.playback` |

## Design System

All colours, typography, and spacing live in `DesignSystem.swift` as `NS.*` constants. No hardcoded values elsewhere.

```
NS.bg        #060810    NS.surface   #0d1120    NS.surface2  #131826
NS.accent    #0ea5e9    NS.accent2   #38bdf8    NS.text      #e8eaf0
NS.green     #10b981    NS.live      #ef4444    NS.amber     #f59e0b
```

Fonts: Syne (display/headings) · Instrument Sans (body) · DM Mono (numbers/timestamps/code)

---

## Data Model

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

`AppError`, `PlayerError`, and `StreamQuality` also live in `Models/` — see source for their cases.

---

## Storage Design

```
~/Library/Application Support/NativeStreamMac/
└── playlist_sources.json    ← [PlaylistSource] (Codable JSON)

UserDefaults keys:
  serverURL, epgURL, epgRefreshInterval,
  bufferPreset, favouriteChannelIDs,
  onboardingComplete
```

No database. No CoreData.

---

## Concurrency Model

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

## Technology Stack

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

---

## Module Responsibility

Deliberately a responsibility table, not a file tree — a tree needs an edit every time a file is added; this only needs one when a responsibility moves. For the literal current layout, browse `NativeStream/NativeStream/` in the repo.

| Module | Owns |
|---|---|
| `API/` | `APIClient` + DTOs for talking to the server's REST endpoints |
| `App/` | App entry point (`NativeStreamApp`), `AppShell` tab routing, keyboard shortcut handling |
| `Core/` | `M3UParser`, `EPGParser`, `EPGStore` — the parsing layer |
| `DesignSystem/` | `NS.*` tokens — colours, fonts, spacing |
| `Models/` | `Channel`, `Programme`, `PlaylistSource`, `BufferPreset`, `StreamQuality`, `RefreshInterval`, `AppError`, `PlayerError` |
| `Protocol/` | `Envelope` — the Local Media Connect message shape, mirroring [api.md](api.md#local-media-connect-websocket-control-plane) |
| `Services/` | `ControlSession` (LMC WebSocket client), `FavouritesManager`, `MediaKeyHandler`, `MenuBarManager`, `NowPlayingService`, `RefreshScheduler`, `ServerDiscoveryService` (mDNS), `SettingsStore` |
| `ViewModels/` | `PlaylistViewModel`, `EPGViewModel`, `PlayerViewModel`, `ServerHealthViewModel`, `BrowserViewModel`, `ChannelManagerViewModel`, `ControlViewModel`, `NowScreenViewModel` — all detailed above |
| `Views/Browser/` | Channel grid, filter chips, add-channel sheet, sport nav rail |
| `Views/Components/` | Shared atomic components (`NSComponents`, source pickers, toasts) |
| `Views/Favourites/` | `FavouritesScreen` — starred channels bucketed into live-now, on-air, up-next, and no-EPG sections; empty state when nothing is starred yet |
| `Views/Help/` | In-app user guide and developer guide |
| `Views/Logo/` | Logo mark rendering |
| `Views/Match/` | Match Day screen |
| `Views/Now/` | `NowScreen` — the EPG-first home tab, delegating bucketing to `NowScreenViewModel` and layout to per-section sub-views (live matches, live on-air, starting soon); mirrors Android's Now screen ([android-architecture.md](android-architecture.md)) |
| `Views/Onboarding/` | Splash → Server → Playlist → EPG onboarding flow |
| `Views/Player/` | Full player screen, controls, mini player, score overlay, AirPlay/PiP wiring |
| `Views/Schedule/` | Programme schedule screen — appears to be the current name for what older docs called the "TV Guide" / `EPGGridScreen` |
| `Views/Settings/` | Settings screen and sections |