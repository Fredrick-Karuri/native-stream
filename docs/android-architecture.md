# NativeStream Android — Architecture

> How the Android client is structured, why decisions were made, and how data flows from network to screen.

---

## Overview

The Android client follows **MVVM with unidirectional data flow**. ViewModels own all state as `StateFlow`. Compose screens are pure collectors — they never mutate state directly, only call ViewModel functions. No `MutableState` crosses a ViewModel boundary.

```
Network / Disk
     ↓
  Parsers          (M3uParser, EpgParser — pure functions, no DI, no side effects)
     ↓
  DataSources      (ApiClient, SettingsDataStore, ChannelCache, EpgIndexCache)
     ↓
  ViewModels       (PlaylistViewModel, EpgViewModel, PlayerViewModel, …)
     ↓
  StateFlow        (single source of truth per feature)
     ↓
  Compose UI       (collectAsState — read only)
```

---

## ViewModels

Each ViewModel owns one feature domain. They do not call each other directly — communication happens via shared data passed at the call-site (e.g. `NowScreen` passes `channels` to `epgViewModel.updateChannels()`).

| ViewModel | Owns |
|---|---|
| `PlaylistViewModel` | Channel list, source CRUD, filter state, search, `filteredSections` |
| `EpgViewModel` | EPG stores, programme index, Now screen buckets, `nowMs` timer |
| `PlayerViewModel` | Playback state, active channel, PiP, channel list for sidebar |
| `FavouritesViewModel` | Starred channel IDs (persisted via DataStore) |
| `SettingsViewModel` | Server URL, EPG URL, buffer preset, health check, discovery |
| `CastViewModel` | Chromecast session, remote media client |

### StateFlow ownership map

```
PlaylistViewModel
  _channels           → raw parsed channels (all sources merged)
  _sources            → configured PlaylistSource list
  _selectedSource     → active source filter (persisted)
  filteredChannels    → _channels filtered by _selectedSource
  filteredSections    → filteredChannels + all filter state → List<ChannelSection>
  _searchQuery        → debounced 150ms before filteredSections recomputes
  _selectedGroup      → group chip selection
  _selectedSubGroup   → sub-group chip selection
  _selectedSport      → sport chip selection
  _showFavouritesOnly → favourites filter toggle
  subGroups           → sub-group chip labels derived from filteredChannels
  _isLoading          → cold boot / manual refresh
  _isRefreshing       → background refresh (warm boot, auto-refresh)

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
```

---

## EPG Pipeline

The EPG pipeline is the most complex part of the codebase. Understanding it prevents O(n) regressions.

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
     → _nowMs.value = nowMs                   // cards use pre-captured timestamp
     → writeIndex to cache
     → rebuildBuckets() for Now screen
```

### FX-002 matching

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
   PlaylistViewModel.fetchAllSourcesInParallel()
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

## Cold Boot vs Warm Boot

One of the most important runtime behaviours to understand.

### Cold boot (no cache)
```
App opens
  → SettingsDataStore emits sources
  → ChannelCache.read() → null (no file)
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
  → SettingsDataStore emits sources
  → ChannelCache.read() → List<Channel>    (~10ms)
  → _channels emits immediately → grid populates
  → _isLoading stays false — no spinner
  → EpgIndexCache.readIndex() → EpgIndexSnapshot  (~20ms)
  → synthetic EpgStore injected → _isReady = true
  → cards show EPG immediately
  → Background: M3U fetch → _isRefreshing = true → subtle spinner in top bar
  → Background: EPG fetch → fresh index built → cache updated
  → _isRefreshing = false → spinner disappears
```

---

## Adaptive Layout

Three breakpoints driven by `WindowSizeClass`. The key invariant: **phones never reach `Expanded` in both dimensions**.

```
isTablet = widthSizeClass == Expanded || heightSizeClass == Expanded
```

| Screen | Compact (phone portrait) | isTablet=true |
|---|---|---|
| Now | Single column LazyColumn | Two-column Row (matches left, live/soon right) |
| Browse | Full-screen grid | Master-detail (list pane + detail pane) |
| Settings | Single scrollable column | Two-pane (sidebar nav + content pane) |
| Rail | Hidden (bottom nav) | Visible left rail |

### Browse master-detail layout
```
Row(fillMaxSize) {
  Column(width=320dp) {                    ← list pane
    BrowseFilterRow(chips)
    LazyColumn(channels)
  }
  Box(width=0.5dp, border)                 ← divider
  Box(weight=1f) {                         ← detail pane
    BrowseDetailPane OR BrowseEmptyView
  }
}
```

The filter row (chips) renders in the **list pane header** on tablet, and **below the top bar** on phone. The source pill always renders in the top bar.

---

## Recomposition Strategy

Key decisions that prevent jank with 700+ channel cards:

### ChannelCard EPG reads are cached
```kotlin
val programme = remember(channel.id, epgReady) {
    epgViewModel.currentProgramme(channel)   // O(1) after AND-PERF-001
}
```
EPG is not re-queried on every unrelated state change — only when `epgReady` changes (index rebuild).

### Filter computation is off the main thread
`filteredSections` is a `StateFlow` computed in `PlaylistViewModel` via `combine`. Search is debounced 150ms. The composition thread only collects the result.

### `derivedStateOf` for list-derived values
```kotlin
val groups by remember { derivedStateOf { channels.map { it.groupTitle }.distinct().sorted() } }
```
Recomposition fires only when the derived value changes, not when `channels` reference changes.

### `nowMs` is pre-captured
`EpgViewModel` emits `nowMs` every 30s. Cards call `programme.progress(nowMs)` not `programme.progress` — avoids `System.currentTimeMillis()` per card per frame.

---

## Navigation

Single `NavHost` in `AppNavHost`. The player is **not** a separate destination — it's an `AnimatedVisibility` overlay at the root `Box` level, outside `safeDrawing` insets, so it truly fills the screen including notch and nav bar areas.

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

Player forces landscape via `requestedOrientation = SCREEN_ORIENTATION_LANDSCAPE`. On dismiss, restores `SCREEN_ORIENTATION_UNSPECIFIED` (not portrait) so free rotation resumes.

---

## Persistence

All persistence uses DataStore Preferences (`ns_settings`) except caches which use raw files in `cacheDir`.

| Key | Type | Owner |
|---|---|---|
| `server_url` | String | SettingsDataStore |
| `epg_url` | String | SettingsDataStore |
| `buffer_preset` | String (enum name) | SettingsDataStore |
| `onboarding_complete` | Boolean | SettingsDataStore |
| `playlist_sources` | String (JSON) | SettingsDataStore |
| `selected_source_id` | String | SettingsDataStore |
| `favourite_channel_ids` | Set\<String\> | FavouritesViewModel (separate DataStore) |
| `channels_{id}.json` | File | ChannelCache |
| `channels_meta_{id}.json` | File | ChannelCache |
| `epg_index_{id}.json` | File | EpgIndexCache |

---

## Design System

All design tokens live in the `ui/theme/` package. **Never use hardcoded dp or hex values in view code.**

| Token file | Contains |
|---|---|
| `NSColors` | All colours as `Color` constants |
| `NSDimens` | All spacing, radius, component sizes as `Dp` tokens via `NSDimens.current` |
| `NSType` | All text styles as `@Composable` functions (scaled by `NSScale`) |

Components in `NSComponents.kt`: `NSSourcePill`, `NSSourcePickerSheet`, `NSSourceBadge`, `NSLiveBadge`, `NSProgressBar`, `NSIconButton`, `NSChip`.