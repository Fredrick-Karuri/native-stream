# NativeStream Android — Architecture

> How the Android client is structured, why decisions were made, and how data flows from network to screen.

For the server-side architecture this client talks to, see [architecture.md](architecture.md). For the full API surface, see [api.md](api.md).

---

## Overview

The Android client follows **MVVM with unidirectional data flow**. ViewModels own all state as `StateFlow`. Compose screens are pure collectors — they never mutate state directly, only call ViewModel functions. No `MutableState` crosses a ViewModel boundary.

```
Network / Disk
     ↓
  Parsers          (M3uParser, EpgParser — pure functions, no DI, no side effects)
     ↓
  DataSources      (ApiClient, ControlSession, SettingsDataStore, ChannelCache, EpgIndexCache)
     ↓
  ViewModels       (EpgViewModel, PlayerViewModel, ControlViewModel, …)
     ↓
  StateFlow        (single source of truth per feature)
     ↓
  Compose UI       (collectAsState — read only)
```

---

## ViewModels

Each ViewModel owns one feature domain. They do not call each other directly — communication happens via shared data passed at the call-site.

| ViewModel | Owns |
|---|---|
| `SourceViewModel` | Source CRUD, selected source, `sources: StateFlow<List<PlaylistSource>>` |
| `EpgViewModel` | EPG stores, programme index, Now screen buckets, `nowMs` timer |
| `PlayerViewModel` | Playback state, active channel, PiP, channel list for sidebar, `playFromRemote` |
| `SettingsViewModel` | Server URL, EPG URL, buffer preset, health check, discovery, connection state |
| `ControlViewModel` | LMC session lifecycle, inbound command handling, `pullBackReady` SharedFlow |
| `FavouritesViewModel` | Starred channel IDs (persisted via DataStore) |
| `CastViewModel` | Chromecast session, remote media client |
| `ChannelLoadingViewModel` | Orchestrates channel fetch + cache across all sources |

> **Known drift:** `BrowseViewModel`, `ChannelFilterViewModel`, and `ChannelManagerViewModel` also exist in `ui/viewmodel/` and aren't described above or in the StateFlow ownership map below. They likely split responsibility that used to sit in `PlaylistViewModel` (browse-screen state, filter computation, and channel CRUD respectively, judging by name) — but that's an inference, not confirmed. Needs a pass from whoever owns this code.

### StateFlow ownership map

```
SourceViewModel
  _sources            → configured PlaylistSource list (persisted)
  _selectedSource     → active source filter (persisted)

EpgViewModel
  stores              → Map<sourceId, EpgStore> — one per EPG URL
  currentIndex        → Map<tvgId, Programme?> — rebuilt every 30s
  nextIndex           → Map<tvgId, Programme?> — rebuilt every 30s
  _nowMs              → System.currentTimeMillis() captured every 30s
  liveMatches         → ChannelWithProgramme list for Now screen
  liveOnAir           → ChannelWithProgramme list for Now screen
  startingSoon        → ChannelWithProgramme list for Now screen
  _isReady            → true after first index build (cache or network)
  _isRefreshing       → background EPG refresh in progress

SettingsViewModel
  serverUrl           → current server URL (DataStore-backed)
  onboardingComplete  → onboarding gate
  connectionState     → OnboardingConnectionState (parallel health+playlist+EPG check)
  serverReachable     → Boolean, checked on onResume via /api/health (5s timeout)
  discoveredUrl       → mDNS-discovered server URL (from ServerDiscoveryService)
  scanning            → mDNS scan in progress

ControlViewModel
  sessions            → List<SessionInfo> — target devices only (controllers filtered out)
  connected           → Boolean (WebSocket connection state)
  isPullingBack       → Boolean (pull_back sent, ack pending)
  pullBackReady       → SharedFlow<PullBackState.Ready> — replay=0, fires once per ack
  controlServerUrl    → ws:// URL from ControlDiscoveryService
  discoveryScanning   → NsdManager scan in progress

PlayerViewModel
  activeChannel / currentChannel → active Channel (same StateFlow, two aliases)
  isPlaying           → Boolean
  isPlayerVisible     → Boolean
  controlsVisible     → Boolean (auto-hides after 3s)
  playerError         → String? (shown in error overlay)
  isInPip             → Boolean
  resizeMode          → AspectRatio fit/fill toggle
  videoQuality        → "4K" | "FHD" | "HD" | "SD" | null
  channelList         → all channels from ChannelRepository (for sidebar)
  sidebarVisible      → Boolean
```

---

## EPG Pipeline

The EPG pipeline is the most complex part of the codebase. Understanding it prevents O(n) regressions — see [android-performance.md](android-performance.md) for the performance rationale.

```
1. FETCH
   EpgViewModel.load()
     → ApiClient.fetchRawUrl(epgUrl)          // Ktor, disk-cached 2h
     → decompress if .gz

2. PARSE
   EpgParser.parse(InputStream)               // SAX-style XmlPullParser
     → Map<tvgId, List<Programme>>            // keyed by XMLTV channel id
     → EpgStore(rawProgrammes)

3. SORT  (at EpgStore construction)
   programmes sorted by startEpochMs per channel
   → enables early-exit scans in schedule() and nextProgramme()

4. INDEX  (at EpgStore construction + every 30s)
   EpgStore.rebuildIndex(nowMs)
     → currentIndex: Map<tvgId, Programme?>   // O(n total) once → O(1) per lookup
     → nextIndex:    Map<tvgId, Programme?>

5. CACHE  (after index build)
   EpgIndexCache.writeIndex(sourceId, currentIndex, nextIndex)
     → cacheDir/epg_index_{sourceId}.json
     → 2h TTL

6. SERVE  (per card, per query)
   EpgViewModel.currentProgramme(channel)     // O(1) — index lookup
   EpgViewModel.nextProgramme(channel)        // O(1) — index lookup

7. REBUILD TIMER
   Every 30s on IO dispatcher:
     → rebuildIndex(nowMs)
     → _nowMs.value = nowMs
     → writeIndex to cache
     → rebuildBuckets() for Now screen
```

### EPG matching

XMLTV `channel` attributes rarely match M3U `tvg-id` exactly. `EpgStore` implements two-level matching:

1. Exact match: `programmesByChannelId[tvgId]`
2. Lowercase fallback: `programmesByLowercaseId[tvgId.lowercase()]`

Match rate is logged on every load:
```
I/EpgViewModel: EPG match rate: 94% (719/764)
```

---

## Channel Pipeline

```
1. FETCH
   ChannelLoadingViewModel.loadAll()
     → ApiClient.fetchRawUrl(source.url)      // per source, parallel

2. PARSE
   M3uParser.parse(ByteArray)                 // line-by-line, no buffering
     → List<Channel>

3. TAG
   channel.copy(sourceId = source.id)         // enables per-source filtering
   channel.copy(id = "${source.id}_${tvgId}") // stable unique ID across sources

4. CACHE
   ChannelCache.write(sourceId, sourceUrl, channels)
     → cacheDir/channels_{sourceId}.json
     → sidecar: channels_meta_{sourceId}.json (cachedAt, sourceUrl)
     → TTL = source.refreshIntervalHours

5. FILTER  (StateFlow, reactive)
   filteredChannels = combine(_channels, _selectedSource)
   filteredSections = combine(filteredChannels, searchQuery[debounced 150ms],
                              selectedGroup, selectedSubGroup,
                              selectedSport, showFavouritesOnly, favouriteIds)
```

---

## Local Media Connect (LMC) Architecture

Android is the **controller** — it discovers targets, sends commands, and initiates pull-back. See [local-media-connect.md](local-media-connect.md) for the cross-platform design and [api.md](api.md#local-media-connect-websocket-control-plane) for the wire protocol.

```
Android (controller)
  ControlDiscoveryService          NsdManager scans _nativestream-ctrl._tcp
        ↓ ws:// URL
  ControlSession (OkHttp WS)       connects ws://server/ws, registers as controller
        ↓ Envelope
  ControlViewModel                 routes inbound messages, exposes sessions + pullBackReady
        ↓ commands
  CastSheet                        UI: device list, play/stop/pull-back buttons
        ↓ onPullBackReady
  PlayerViewModel.playFromRemote() resolves channel by ID → local playback
```

### Pull-back state management

`pullBackReady` is a `SharedFlow(replay=0)` — it has no stored value, only delivers to active collectors. This prevents stale pull-back acks from firing when the `CastSheet` recomposes. The `LaunchedEffect(Unit)` in `CastSheet` collects it as a stream: only a genuine fresh ack while the sheet is open triggers `onPullBackReady`.

### Device identity

Each device generates a stable UUID on first launch, stored in `DataStore` under `control_device_id`. This key is **excluded from `resetAll()`** — device identity intentionally survives factory reset so the server session registry can recognize returning devices.

---

## Onboarding Flow

```
SPLASH (2s, starts mDNS scan)
  ↓
SERVER (mDNS auto-fills URL → auto-triggers parallel check)
  parallel: /api/health + /playlist.m3u + /epg.xml
  ↓ success
  hasEpg=true  → PLAYLIST → Now screen
  hasEpg=false → PLAYLIST → EPG → Now screen
  ↓ failure
  error state with actionable suggestions → retry
```

`OnboardingConnectionState` is a sealed class: `Idle | Checking | Success(channels, healthy, hasEpg, epgFromPlaylist) | Failure(reason)`. Owned by `SettingsViewModel`, consumed by `OnboardingScreen`.

If onboarding fails, see [troubleshooting.md — Server unreachable](troubleshooting.md#server-unreachable).

---

## Cold Boot vs Warm Boot

### Cold boot (no cache)
```
App opens
  → SettingsDataStore emits sources
  → ChannelCache.read() → null
  → _isLoading = true → BrowseScreen shows spinner
  → M3U fetch + parse (~3-8s)
  → _channels emits → grid populates
  → EpgIndexCache.readIndex() → null
  → _isReady = false → cards show no EPG
  → EPG fetch + parse (~5-15s)
  → index built → _isReady = true → cards update
```

### Warm boot (cache present, < TTL)
```
App opens
  → ChannelCache.read() → List<Channel>    (~10ms)
  → _channels emits immediately → grid populates
  → _isLoading stays false
  → EpgIndexCache.readIndex() → EpgIndexSnapshot  (~20ms)
  → synthetic EpgStore injected → _isReady = true
  → cards show EPG immediately
  → Background: M3U fetch → _isRefreshing = true
  → Background: EPG fetch → fresh index → cache updated
  → _isRefreshing = false
```

---

## Adaptive Layout

Three breakpoints driven by `WindowSizeClass`.

```
isTablet = widthSizeClass == Expanded && heightSizeClass != Compact
```

| Screen | Compact (phone) | isTablet=true |
|---|---|---|
| Now | Single column LazyColumn | Two-column Row |
| Browse | Full-screen grid | Master-detail (320dp list + detail) |
| Settings | Single scrollable column | Two-pane (sidebar nav + content) |
| Rail | Hidden (bottom nav) | Visible left rail |

---

## Navigation

Single `NavHost` in `AppNavHost`. The player is an `AnimatedVisibility` overlay at the root `Box` level, outside `safeDrawing` insets.

```
Box(fillMaxSize) {                          ← outer: true window bounds
  Box(windowInsetsPadding(safeDrawing)) {  ← inner: nav shell
    Row {
      NSNavRail (if isTablet)
      Column {
        NavHost { Now / Browse / Settings }
        MiniPlayer (AnimatedVisibility)
        NSBottomNavBar (if !isTablet)
      }
    }
  }
  PlayerScreen (AnimatedVisibility)         ← outside safeDrawing padding
}
```

---

## Persistence

| Key | Type | Notes |
|---|---|---|
| `server_url` | String | DataStore |
| `epg_url` | String | DataStore |
| `buffer_preset` | String (enum name) | DataStore |
| `onboarding_complete` | Boolean | DataStore |
| `playlist_sources` | String (JSON) | DataStore |
| `selected_source_id` | String | DataStore |
| `control_device_id` | String | DataStore — excluded from resetAll() |
| `favourite_channel_ids` | Set\<String\> | DataStore |
| `channels_{id}.json` | File | cacheDir, per-source TTL |
| `channels_meta_{id}.json` | File | cacheDir, sidecar metadata |
| `epg_index_{id}.json` | File | cacheDir, 2h TTL |

---

## Recomposition Strategy

Key decisions that prevent jank with 700+ channel cards:

### ChannelCard EPG reads are cached
```kotlin
val programme = remember(channel.id, epgReady) {
    epgViewModel.currentProgramme(channel)   // O(1)
}
```

### Filter computation is off the main thread
`filteredSections` is a `StateFlow` computed in `PlaylistViewModel` via `combine`. Search is debounced 150ms.

### `derivedStateOf` for list-derived values
```kotlin
val groups by remember { derivedStateOf { channels.map { it.groupTitle }.distinct().sorted() } }
```

### `nowMs` is pre-captured
`EpgViewModel` emits `nowMs` every 30s. Cards call `programme.progress(nowMs)` — avoids `System.currentTimeMillis()` per card per frame.

---

## Package Responsibility

Deliberately a responsibility table, not a file tree — a tree needs an edit every time a file is added; this only needs one when a responsibility moves. For the literal current layout, browse `app/android/app/src/main/java/com/nativestream/android/` in the repo.

| Package | Owns |
|---|---|
| `data/cast/` | Chromecast SDK wiring (`CastManager`, `CastOptionsProvider`) |
| `data/local/` | On-disk cache and settings: `ChannelCache`, `EpgIndexCache`, `SettingsDataStore` |
| `data/parser/` | `M3uParser`, `EpgParser`, `EpgStore` — the parsing layer, mirroring `data/parser/` responsibilities on the other clients |
| `data/player/` | Media3 wiring: header-aware media source factory, media session callback, playback service |
| `data/remote/` | `ApiClient` (Ktor), `ControlSession` + `ControlDiscoveryService` (LMC), `ServerDiscoveryService` (mDNS), DTOs |
| `data/repository/` | `ChannelRepositoryImpl` — the concrete implementation behind `domain/repository/ChannelRepository` |
| `di/` | Hilt modules — app-wide, DataStore, dispatchers, repository bindings |
| `domain/model/` | `Channel`, `Programme`, `PlaylistSource`, `SportCategory`, `StreamQuality`, `LiveEligibility`, plus `domain/model/control/` for LMC payload types |
| `ui/components/` | Shared Compose components — chips, badges, mini player, source pickers, text fields |
| `ui/navigation/` | `AppNavHost`, bottom nav bar, nav rail, destination definitions |
| `ui/screens/` | Screen composables, grouped by feature: `browse/`, `now/`, `onboarding/`, `player/`, `remote/`, `settings/` |
| `ui/theme/` | `NSColors`, `NSDimens`, `NSGradients`, `NSScale`, `NSType` design tokens |
| `ui/viewmodel/` | See the drift note above — three of the eleven ViewModels here aren't yet described in prose |
| `ui/foldable/` | Foldable-device window/hinge utilities |

---

## Design System

All design tokens live in `ui/theme/`. Never use hardcoded dp or hex values in view code.

| Token file | Contains |
|---|---|
| `NSColors` | All colours as `Color` constants |
| `NSDimens` | All spacing, radius, component sizes via `NSDimens.current` |
| `NSType` | All text styles as `@Composable` functions |

Shared components: `NSSourcePill`, `NSSourcePickerSheet`, `NSLiveBadge`, `NSProgressBar`, `NSHealthDot`, `NSIconButton`, `NSChip`, `NSToggle`, `NSTextField`.