# NativeStream Android — Implementation Tickets

**Platform:** Android 8.0+ (API 26+)  
**Stack:** Kotlin · Jetpack Compose · Media3 ExoPlayer · Hilt · Ktor  
**Last Updated:** 2026-05-08  
**Principle:** Lean and functional. Tap and watch.

---

## How to Read This

- **ID** — `AND-001` etc.
- **Effort** — S (< 2hrs), M (half day), L (full day), XL (2+ days)
- **Needs** — files or context to provide at start of ticket
- **Done when** — observable acceptance criteria

---

## EPIC AND-E01 — Project bootstrap

---

### AND-001 — Project setup and dependency config
- **Effort:** M
- **Needs:** nothing
- **Description:** Create Android module in the monorepo. Configure `build.gradle.kts` with all required dependencies.
- **Dependencies:**
  ```kotlin
  // Compose + Material 3
  implementation("androidx.compose.material3:material3")
  implementation("androidx.navigation:navigation-compose")
  // Media3
  implementation("androidx.media3:media3-exoplayer")
  implementation("androidx.media3:media3-exoplayer-hls")
  implementation("androidx.media3:media3-ui")
  // Hilt
  implementation("com.google.dagger:hilt-android")
  ksp("com.google.dagger:hilt-compiler")
  // Ktor
  implementation("io.ktor:ktor-client-android")
  implementation("io.ktor:ktor-client-content-negotiation")
  implementation("io.ktor:ktor-serialization-kotlinx-json")
  // DataStore
  implementation("androidx.datastore:datastore-preferences")
  // Cast
  implementation("com.google.android.gms:play-services-cast-framework")
  // Coil (images)
  implementation("io.coil-kt:coil-compose")
  // Google Fonts
  implementation("androidx.compose.ui:ui-text-google-fonts")
  ```
- **Done when:** Project builds, empty `MainActivity` launches, Hilt compiles.

---

### AND-002 — Design system (NS tokens in Compose)
- **Effort:** M
- **Needs:** `DesignSystem.swift`
- **Description:** Implement `NSTheme.kt` with all colour, typography, and spacing tokens matching the Mac design system exactly.
- **Done when:** All `NS.*` tokens from the spec exist in Kotlin. Syne, Instrument Sans, DM Mono load via Google Fonts. No hardcoded colour values anywhere in the codebase.

---

### AND-003 — Data models
- **Effort:** S
- **Needs:** `Channel.swift`, `Programme.swift`
- **Description:** Implement Kotlin data classes: `Channel`, `Programme`, `PlaylistSource`, `StreamQuality`. Mirror Swift models exactly including `tvgId`, `groupTitle`, `streamHeaders`.
- **Done when:** Models are `@Serializable`, compile, and round-trip through JSON correctly.

---

## EPIC AND-E02 — Networking + parsing

---

### AND-004 — APIClient (Ktor)
- **Effort:** M
- **Needs:** `APIClient.swift`
- **Description:** Implement Kotlin `APIClient` with Ktor. Mirrors Swift `APIClient` endpoints exactly. Server URL configurable (not hardcoded — Android connects over LAN, not localhost).
- **Endpoints:** `health`, `playlistData`, `epgData`, `listChannels`, `getChannel`, `createChannel`, `updateChannel`, `deleteChannel`, `triggerProbe`, `discoveryStatus`, `triggerDiscovery`, `unmatchedLinks`.
- **Done when:** All endpoints callable. `playlistData()` returns raw bytes. JSON decoding matches `ChannelResponse`, `ChannelDetailResponse` etc.

---

### AND-005 — M3U parser
- **Effort:** M
- **Needs:** `M3UParser.swift`
- **Description:** Pure Kotlin M3U parser. Parses `InputStream` line by line — no full-file buffering. Extracts `tvg-id`, `tvg-logo`, `group-title` from `#EXTINF`. Skips malformed entries with log warning.
- **Done when:** Parses a 10,000-channel M3U in < 500ms. Produces correct `List<Channel>`. Channels with missing `tvg-id` get empty string (not crash).

---

### AND-006 — EPG parser
- **Effort:** M
- **Needs:** `EPGParser.swift`
- **Description:** Kotlin EPG parser using `XmlPullParser` (SAX-equivalent). Produces `Map<String, List<Programme>>` keyed by channel `tvg-id`. Supports files > 100MB without OOM.
- **Critical:** Implement case-insensitive fallback matching (same as Mac FX-002) — exact match first, lowercase fallback second.
- **Done when:** Parses a real XMLTV file. Match rate logged on load. Known channels match correctly. Parser does not hold full file in memory.

---

### AND-007 — PlaylistViewModel + EPGViewModel
- **Effort:** M
- **Needs:** `PlaylistViewModel.swift`, `EPGViewModel.swift`
- **Description:** Kotlin `PlaylistViewModel` and `EPGViewModel` backed by `StateFlow`. Mirror Mac logic: parallel load on startup, EPG URL from `SettingsDataStore`, case-insensitive TVG-ID matching, `schedule(from, to)` for date-range queries.
- **Done when:** Both VMs expose `StateFlow<List<Channel>>` and EPG query methods. Parallel load fires on app start. EPG and playlist each load exactly once on cold start (no double-load).

---

## EPIC AND-E03 — Navigation + shell

---

### AND-008 — Bottom navigation shell
- **Effort:** M
- **Needs:** nothing
- **Description:** `MainActivity` with `NavHost` and 3-tab bottom nav: Now · Browse · Settings. Mini player slot above bottom nav. Player launches as full-screen composable over tabs (not a separate Activity, contrary to spec — composable is simpler and avoids Activity/Fragment lifecycle issues).
- **Done when:** Tabs navigate correctly. Back button on Now tab exits app. Player full-screens over tabs without flickering.

---

### AND-009 — Mini player
- **Effort:** M
- **Needs:** `MiniPlayerWidget.swift` 
- **Description:** 64dp persistent bottom sheet above bottom nav. Shows when a channel is playing, hidden in full player. Logo, channel name, programme title + elapsed minute, progress bar, pause/close buttons. Swipe up → opens full player.
- **Done when:** Mini player appears when playback starts. Disappears in full player. Swipe up expands to full player. Close stops playback.

---

## EPIC AND-E04 — Now screen

---

### AND-010 — NowScreen
- **Effort:** L
- **Needs:** `NowScreen.swift`, `EPGViewModel.swift` 
- **Description:** Three sections — Matches live, Live on air, Starting soon. Sections hidden when empty. Bucketing logic: sport keyword match for matches, non-match live for on-air, 2-hour window for starting soon. Same logic as Mac `NowScreen`.
- **Done when:** All three sections render from live EPG data. Empty sections hidden. Tapping any item starts playback.

---

### AND-011 — Match cards (hero + small)
- **Effort:** M
- **Needs:** `MatchHeroCard.swift`, `MatchSmallCard.swift` 
- **Description:** `MatchHeroCard` — full width, 200dp tall, team initials, score, minute, progress bar. `MatchSmallCard` — 180dp wide in `LazyRow`. Both gracefully handle missing score data.
- **Done when:** Hero card renders at full width. Small cards scroll horizontally. Tapping either starts playback.

---

### AND-012 — LiveOnAirRow + StartingSoonCard
- **Effort:** S
- **Needs:** `LiveOnAirRow.swift`, `StartingSoonCard.swift` 
- **Description:** `LiveOnAirRow` — 48dp square logo, programme, channel, progress, LIVE badge. `StartingSoonCard` — 160dp wide horizontal scroll card, kick-off time, teams, channel.
- **Done when:** Rows and cards render with correct EPG data. Tapping starts playback.

---

## EPIC AND-E05 — Browse screen

---

### AND-013 — BrowseScreen with sport chips
- **Effort:** L
- **Needs:** `BrowserScreen.swift`, `MatchDayScreen.swift` 
- **Description:** Top bar with search. Sport filter chips in `LazyRow`. Channel grid below (`LazyVerticalGrid`, `GridCells.Adaptive(160.dp)`). Grouped by `groupTitle` with sticky headers. Chips filter by EPG programme title + channel name + groupTitle (same FX-009 logic).
- **Done when:** Chips filter correctly. Search filters name and groupTitle. Grid reflows on rotation. Tapping a channel starts playback.

---

### AND-014 — ChannelCard
- **Effort:** M
- **Needs:** `ChannelCard.swift`, `ChannelLogoView` 
- **Description:** Image-first card. 16:9 artwork via Coil. LIVE badge top-left, star/▶NOW top-right, progress bar bottom edge. Channel name + programme below artwork. Playing state: `NS.accentBorder` 1dp stroke + ▶NOW badge.
- **Done when:** Card renders correctly in grid. Playing state visually distinct. Logo loads via Coil with initials fallback. Ripple on press.

---

### AND-015 — Add Channel sheet
- **Effort:** M
- **Needs:** `AddChannelSheet` 
- **Description:** Bottom sheet triggered from Browse FAB. Fields: stream URL, name, group, TVG ID. Submits via `APIClient.createChannel()`. On success reloads playlist.
- **Done when:** Sheet opens from FAB. Validation prevents empty submit. Success reloads channel list. Error shown inline.

---

### AND-016 — Play URL sheet
- **Effort:** M
- **Needs:** `PlayURLSheet.swift` 
- **Description:** Bottom sheet triggered from FAB long-press or dedicated icon. URL field + optional Referer/User-Agent headers (collapsed by default). Tapping Play creates temporary channel and starts playback immediately. Not persisted.
- **Done when:** Sheet opens. Invalid URL shows error. Valid URL starts playback in < 3s. Channel not added to playlist.

---

## EPIC AND-E06 — Player

---

### AND-017 — PlayerScreen (ExoPlayer)
- **Effort:** XL
- **Needs:** `PlayerScreen.swift`, `PlayerViewModel.swift` 
- **Description:** Full-screen composable using `AndroidView` wrapping `PlayerView`. Media3 ExoPlayer with HLS source. Landscape forced on entry. Controls auto-hide after 3s. Top + bottom gradient overlays. Match score overlay when EPG title contains "vs".
- **Done when:** Stream plays in landscape. Controls show/hide on tap. Score overlay renders when applicable. Back button exits player and restores portrait.

---

### AND-018 — Header injection (FX-017 Android equivalent)
- **Effort:** M
- **Needs:** `PlayerViewModel.swift` 
- **Description:** Streams requiring `Referer` or `User-Agent` headers must play without failure. Implement custom `DefaultHttpDataSource.Factory` that injects headers per `MediaItem`. Headers stored in `Channel.streamHeaders`.
- **Done when:** A stream requiring `Referer: https://example.com` plays correctly. Header-free streams unaffected.

---

### AND-019 — Player sidebar (On now + Schedule tabs)
- **Effort:** M
- **Needs:** `PlayerScreen.swift` 
- **Description:** 240dp collapsible sidebar. Two tabs: On now (filtered to EPG-aware channels, sorted playing→live→upcoming) and Schedule (current channel EPG timeline). Sidebar toggle in controls. Tap row → switches channel immediately via `ExoPlayer.setMediaItem()`.
- **Done when:** Sidebar shows correct channels. Switching channels from sidebar works without leaving player. Sidebar collapse/expand animates.

---

### AND-020 — Picture in Picture
- **Effort:** M
- **Needs:** nothing
- **Description:** Enter PiP on home button press while playing (API 26+). `PictureInPictureParams` with 16:9 ratio. Play/pause remote action. Return to full player on tap.
- **Done when:** PiP activates on home press. Play/pause works in PiP. Tapping PiP returns to full player.

---

### AND-021 — Chromecast
- **Effort:** L
- **Needs:** nothing
- **Description:** Cast button in player controls when a receiver is on the network. Uses `MediaRouter` + Cast SDK. `RemoteMediaClient` takes the current stream URL. Basic play/pause/stop from phone while casting.
- **Done when:** Cast button appears when receiver available. Stream casts successfully. Phone shows cast controls.

---

### AND-022 — Retry + error overlay
- **Effort:** S
- **Needs:** `PlayerViewModel.swift` 
- **Description:** On stream failure: retry up to 3 times with 2s delay, re-fetching active link from server each time. After max retries: show error overlay with Retry button. Same logic as Mac `PlayerViewModel`.
- **Done when:** Failed stream retries automatically. Error overlay shows after 3 failures. Manual Retry re-attempts. `noActiveLink` falls back to `channel.streamURL`.

---

## EPIC AND-E07 — Settings screen

---

### AND-023 — SettingsScreen
- **Effort:** L
- **Needs:** `SettingsScreen.swift` 
- **Description:** Single `LazyColumn` with inline expandable sections: Server · Playlist Sources · EPG / TV Guide · Playback · Proxy · Discovery · About. Server URL field is critical — Android connects over LAN, not localhost. `NSHealthDot` beside server status.
- **Done when:** All sections render. Settings persist via DataStore. Server URL change triggers EPG + playlist reload. Health dot updates reactively.

---

### AND-024 — SettingsDataStore
- **Effort:** S
- **Needs:** `SettingsStore.swift` 
- **Description:** DataStore Preferences backing for: `serverUrl`, `epgUrl`, `bufferPreset`, `epgRefreshInterval`, `onboardingComplete`. Exposed as `StateFlow` so compose collects reactively.
- **Done when:** All settings persist across app restarts. Changes observed in ViewModels without manual refresh.

---

## EPIC AND-E08 — Onboarding

---

### AND-025 — Onboarding flow
- **Effort:** M
- **Needs:** `OnboardingView.swift` 
- **Description:** 3-step flow shown on first launch: Server setup (enter LAN IP e.g. `192.168.1.42:8888`, test connection) → Playlist source (M3U URL field, add inline) → EPG source (XMLTV URL field). Completion sets `onboardingComplete = true`.
- **Note:** Mac uses `localhost` — Android must emphasise LAN IP. Auto-detect via server mDNS discovery is a nice-to-have.
- **Done when:** First launch shows onboarding. Server connection test works. Playlist source added and loads on completion. Channels appear on Now screen after finish.

---

## EPIC AND-E09 — Polish + functional correctness

---

### AND-026 — EPG TVG-ID match diagnostic
- **Effort:** S
- **Needs:** `EPGViewModel.swift` 
- **Description:** After EPG + playlist load, log match rate to console. Unmatched channels logged with name + tvgId. Same as Mac `logMatchDiagnostic`.
- **Done when:** Match rate logged on every cold start. Unmatched channels visible in Logcat.

---

### AND-027 — Sport chip dynamic visibility
- **Effort:** S
- **Needs:** `EPGViewModel.swift`, `SportNavRail.swift` 
- **Description:** Sport chips only enabled when EPG has content for that sport. Chips dimmed (alpha 0.4) while EPG is loading. Same logic as Mac FX-013.
- **Done when:** Chips reflect actual EPG content. Loading state communicated. No jarring reorder after EPG loads.

---

### AND-028 — FavouritesManager (Android)
- **Effort:** S
- **Needs:** `FavouritesManager.swift` 
- **Description:** Persist starred channel IDs in DataStore as a `Set<String>`. Expose as `StateFlow<Set<String>>`. `toggle(channel)` writes immediately. Used by `ChannelCard` star icon and Browse chip filter.
- **Done when:** Stars persist across restarts. Toggling updates UI immediately. Favourites chip in Browse filters correctly.

---

## Summary

| Epic | Tickets | Effort |
|------|---------|--------|
| AND-E01 Bootstrap | AND-001 – AND-003 | ~1.5 days |
| AND-E02 Networking | AND-004 – AND-007 | ~2.5 days |
| AND-E03 Shell | AND-008 – AND-009 | ~1 day |
| AND-E04 Now screen | AND-010 – AND-012 | ~1.5 days |
| AND-E05 Browse | AND-013 – AND-016 | ~2 days |
| AND-E06 Player | AND-017 – AND-022 | ~3.5 days |
| AND-E07 Settings | AND-023 – AND-024 | ~1 day |
| AND-E08 Onboarding | AND-025 | ~0.5 days |
| AND-E09 Polish | AND-026 – AND-028 | ~0.5 days |
| **Total** | **28 tickets** | **~14 days** |

---

## Recommended order

```
AND-001  Bootstrap
AND-002  Design system         ← unblocks all UI
AND-003  Data models           ← unblocks networking
AND-004  APIClient
AND-005  M3U parser
AND-006  EPG parser            
AND-007  ViewModels
AND-008  Shell + navigation
AND-009  Mini player
AND-024  SettingsDataStore    
AND-023  SettingsScreen
AND-025  Onboarding
AND-010  NowScreen
AND-011  Match cards
AND-012  LiveOnAir + StartingSoon
AND-013  BrowseScreen
AND-014  ChannelCard
AND-017  PlayerScreen          
AND-018  Header injection
AND-019  Player sidebar
AND-015  Add Channel sheet
AND-016  Play URL sheet
AND-020  PiP
AND-021  Chromecast
AND-022  Retry + error
AND-026  EPG diagnostic
AND-027  Sport chip dynamic
AND-028  FavouritesManager
```