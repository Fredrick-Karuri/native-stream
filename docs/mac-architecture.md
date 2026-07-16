# Mac Architecture

Swift/SwiftUI internals for the macOS client. For how this fits with the server, see [architecture.md](architecture.md). For the Go and Android equivalents, see [server-architecture.md](server-architecture.md) and [android-architecture.md](android-architecture.md).

> **Known drift:** the current Xcode project has screens and ViewModels (`NowScreen`, `FavouriteScreen`, `HelpScreen`, `ChannelManagerViewModel`, `ControlViewModel`, `BrowserViewModel`, `NowScreenViewModel`) that predate this doc's last real pass. The sections below describe the four ViewModels and the tab structure as previously documented — treat those as possibly stale until someone who owns this code confirms or updates them. The **Module Responsibility** table at the bottom reflects the actual current file tree and is the more trustworthy source right now.

## App Structure

The root scene is `AppShell` — a custom `VStack` with a 44pt tab bar and swappable content. No `NavigationSplitView`.

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

*(This tab list doesn't account for the `Now/` and `Favourites/` view folders that now exist — see the responsibility table below.)*

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
```

`BrowserViewModel`, `ChannelManagerViewModel`, `ControlViewModel`, and `NowScreenViewModel` also exist in `ViewModels/` and aren't documented above — `ControlViewModel` is presumably the Local Media Connect counterpart to Android's (see [android-architecture.md](android-architecture.md#local-media-connect-lmc-architecture) and [local-media-connect.md](local-media-connect.md)), but that's an inference, not a confirmed description.

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
| `ViewModels/` | See the flagged section above — four of the eight files here aren't yet described in prose |
| `Views/Browser/` | Channel grid, filter chips, add-channel sheet, sport nav rail |
| `Views/Components/` | Shared atomic components (`NSComponents`, source pickers, toasts) |
| `Views/Favourites/` | Favourited channel list — **not currently described anywhere in these docs** |
| `Views/Help/` | In-app user guide and developer guide — **not currently described anywhere in these docs** |
| `Views/Logo/` | Logo mark rendering |
| `Views/Match/` | Match Day screen |
| `Views/Now/` | Now screen — live-on-air and starting-soon sections, match hero/small cards — **not currently described anywhere in these docs; this looks like the Mac equivalent of Android's Now screen** ([android-architecture.md](android-architecture.md)) |
| `Views/Onboarding/` | Splash → Server → Playlist → EPG onboarding flow |
| `Views/Player/` | Full player screen, controls, mini player, score overlay, AirPlay/PiP wiring |
| `Views/Schedule/` | Programme schedule screen — appears to be the current name for what older docs called the "TV Guide" / `EPGGridScreen` |
| `Views/Settings/` | Settings screen and sections |