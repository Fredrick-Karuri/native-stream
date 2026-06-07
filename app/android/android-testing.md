# NativeStream Android — Test Suite Spec

**Platform:** Android 8.0+ (API 26+)  
**Stack:** JUnit4 · Kotlin Coroutines Test · Turbine · MockK · Compose UI Test · Robolectric  
**Principle:** Test behaviour, not implementation. Each suite maps to a production file.

---

## How to Read This

- **ID** — `AND-T001` etc., grouped by epic
- **Type** — `Unit` (JVM, no device) · `Integration` (JVM + Robolectric) · `UI` (instrumented Compose)
- **Needs** — production files the test author must read before writing
- **Done when** — observable pass criteria

---

## Dependencies to Add

```kotlin
// build.gradle.kts (app)

// Unit + coroutines
testImplementation("junit:junit:4.13.2")
testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
testImplementation("app.cash.turbine:turbine:1.1.0")          // StateFlow testing
testImplementation("io.mockk:mockk:1.13.11")
testImplementation("org.robolectric:robolectric:4.12.2")

// Compose UI tests
androidTestImplementation("androidx.compose.ui:ui-test-junit4")
androidTestImplementation("androidx.test.ext:junit:1.2.1")
androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")

// Hilt testing
testImplementation("com.google.dagger:hilt-android-testing:2.55")
kspTest("com.google.dagger:hilt-compiler:2.55")
androidTestImplementation("com.google.dagger:hilt-android-testing:2.55")
kspAndroidTest("com.google.dagger:hilt-compiler:2.55")
```

---

## SUITE AND-T01 — Domain Models

### AND-T001 — Channel identity
- **Type:** Unit
- **Needs:** `Channel.kt`
- **Cases:**
  - `create()` with non-empty `tvgId` → `id == tvgId`
  - `create()` with empty `tvgId` → `id == streamUrl`
  - Two channels with same `id` are `==` regardless of other fields
  - Two channels with different `id` are not `==`

### AND-T002 — Programme computed properties
- **Type:** Unit
- **Needs:** `Programme.kt`
- **Cases:**
  - `progress` returns `0.0` before `startEpochMs`
  - `progress` returns `1.0` after `stopEpochMs`
  - `progress` clamps to `[0.0, 1.0]` mid-programme
  - `isNow` true when `now` in `[start, stop)`
  - `isNow` false when `now >= stop`
  - `timeRemainingString` returns `"Ending"` when stop is past
  - `id` is stable — same for two instances with same `channelId` + `startEpochMs`

### AND-T003 — SportCategory EPG keywords
- **Type:** Unit
- **Needs:** `SportCategory.kt`
- **Cases:**
  - `allKeywords` contains no duplicates
  - `FOOTBALL.epgKeywords` contains `"premier league"`
  - `GOLF.epgKeywords` contains `"pga tour live"`
  - Each category's keywords are lowercase

---

## SUITE AND-T02 — Parsers

### AND-T004 — M3uParser: happy path
- **Type:** Unit
- **Needs:** `M3uParser.kt`, `M3uParseResult.kt`
- **Fixture:** in-memory M3U string with 3 `#EXTINF` entries
- **Cases:**
  - Correct channel count returned
  - `tvgId` extracted from `tvg-id="…"`
  - `groupTitle` extracted from `group-title="…"`
  - `logoUrl` extracted from `tvg-logo="…"`
  - `name` taken from after the last comma
  - Missing `tvg-id` → channel gets empty string (no crash)

### AND-T005 — M3uParser: malformed input
- **Type:** Unit
- **Needs:** `M3uParser.kt`, `M3uParseWarning.kt`
- **Cases:**
  - `#EXTINF` with no comma → warning emitted, entry skipped
  - Line that is not a URL after `#EXTINF` → warning emitted
  - Empty input → returns empty list, no exception
  - Latin-1 encoded bytes → decoded without replacement characters
  - 10,000-channel fixture parses in < 500ms

### AND-T006 — M3uParser: EPG URL detection
- **Type:** Unit
- **Needs:** `M3uParser.kt`
- **Cases:**
  - `#EXTM3U url-tvg="http://…"` → `result.epgUrl` is populated
  - `#EXTM3U` with no `url-tvg` → `result.epgUrl` is null

### AND-T007 — EpgParser: happy path
- **Type:** Unit
- **Needs:** `EpgParser.kt`, `EpgStore.kt`
- **Fixture:** minimal valid XMLTV XML with 2 channels, 4 programmes
- **Cases:**
  - Correct channel count in returned `EpgStore`
  - Programme title, start, stop parsed correctly
  - Programmes keyed by channel `tvg-id`
  - XMLTV date `"20250101120000 +0000"` parses to correct epoch

### AND-T008 — EpgParser: malformed input
- **Type:** Unit
- **Needs:** `EpgParser.kt`
- **Cases:**
  - Missing `stop` attribute → entry skipped, no crash
  - Malformed date string → entry skipped, logged
  - Partial XML (truncated) → returns whatever was successfully parsed

### AND-T009 — EpgStore: lookup + FX-002 fallback
- **Type:** Unit
- **Needs:** `EpgStore.kt`
- **Cases:**
  - Exact `tvgId` match returns correct programmes
  - Lowercase fallback: `tvgId = "BBC.ONE"` matches key `"bbc.one"`
  - Unknown `tvgId` returns empty list (not null, no crash)
  - `currentProgramme()` returns the programme where `isNow == true`
  - `nextProgramme()` returns earliest programme with `startEpochMs > now`
  - `schedule(from, to)` filters correctly by time window

---

## SUITE AND-T03 — Networking

### AND-T010 — ApiClient: endpoint mapping
- **Type:** Unit (MockK mock of Ktor `HttpClient`)
- **Needs:** `ApiClient.kt`, `ApiDtos.kt`
- **Cases:**
  - `health()` hits `GET /api/health`
  - `listChannels()` hits `GET /api/channels` and returns `response.channels`
  - `createChannel()` hits `POST /api/channels` with correct JSON body
  - `triggerProbe()` hits `POST /api/probe`
  - `deleteChannel(id)` hits `DELETE /api/channels/{id}`

### AND-T011 — ApiClient: error mapping
- **Type:** Unit
- **Needs:** `ApiClient.kt`, `ApiError.kt`
- **Cases:**
  - Connection refused → throws `ApiError.ServerUnreachable`
  - HTTP 404 response → throws `ApiError.HttpError(404, …)`
  - Malformed JSON response → throws `ApiError.DecodingFailed`
  - `setBaseUrl()` updates subsequent request URLs

---

## SUITE AND-T04 — ViewModels

### AND-T012 — PlaylistViewModel: load lifecycle
- **Type:** Unit (coroutines-test `StandardTestDispatcher`)
- **Needs:** `PlaylistViewModel.kt`, `M3uParser.kt`, `SettingsDataStore.kt`
- **Mocks:** `ApiClient`, `M3uParser`, `SettingsDataStore`
- **Cases:**
  - `loadAll()` while already loading → second call is a no-op
  - Successful load → `channels` emits parsed list, `isLoading` goes false
  - Parser error → `error` emits message, `channels` unchanged
  - `addSource()` persists via `SettingsDataStore.addSource()`
  - `removeSource(id)` removes correct entry
  - Auto-refresh schedules correctly from shortest interval

### AND-T013 — EpgViewModel: load + queries
- **Type:** Unit
- **Needs:** `EpgViewModel.kt`, `EpgParser.kt`, `SettingsDataStore.kt`
- **Mocks:** `ApiClient`, `EpgParser`, `SettingsDataStore`
- **Cases:**
  - `load()` sets `isLoading` true during fetch, false after
  - `currentProgramme(channel)` delegates to store and returns correctly
  - `nextProgramme(channel)` returns earliest future programme
  - `schedule(channel, hours)` deduplicates across stores by `Programme.id`
  - `logMatchDiagnostic([])` → no crash on empty input
  - `activeSports(channels)` sorted by live count descending

### AND-T014 — EpgViewModel: sport helpers
- **Type:** Unit
- **Needs:** `EpgViewModel.kt`, `SportCategory.kt`
- **Cases:**
  - `matchesSport(FOOTBALL, programme)` true when title contains `"premier league"`
  - `hasContent(GOLF, channels)` false when no live or upcoming golf
  - `activeSports` excludes sports with no content

### AND-T015 — PlayerViewModel: playback state
- **Type:** Unit
- **Needs:** `PlayerViewModel.kt`
- **Mocks:** `ApiClient` (for retry re-fetch), `ExoPlayer` replaced by test double
- **Cases:**
  - `play(channel)` → `activeChannel` emits channel, `isPlayerVisible` true
  - `togglePlayback()` flips `isPlaying`
  - `toggleMute()` flips `isMuted`
  - `stop()` → `activeChannel` null, `isPlayerVisible` false, `isPlaying` false
  - `playUrl(url, headers)` → creates temporary channel with correct `streamUrl` and `streamHeaders`
  - Controls auto-hide after 3s (use `advanceTimeBy`)

### AND-T016 — PlayerViewModel: retry logic
- **Type:** Unit
- **Needs:** `PlayerViewModel.kt`
- **Cases:**
  - First failure → retries after 2s delay
  - Three consecutive failures → `playerError` emits message, no further retries
  - Retry re-fetches active link from `ApiClient.getChannel()`
  - Server unreachable during retry → falls back to cached `streamUrl`
  - `retryManually()` resets `retryCount` to 0 and re-attempts

### AND-T017 — NowBuckets: bucketing logic
- **Type:** Unit
- **Needs:** `NowBuckets.kt`, `Programme.kt`
- **Cases:**
  - Live sport programme with `" vs "` → appears in `liveMatches`, not `liveOnAir`
  - Live non-sport programme → appears in `liveOnAir`, not `liveMatches`
  - No current programme, next within 2h → appears in `startingSoon`
  - No current programme, next > 2h away → excluded from all buckets
  - Channel with current programme → excluded from `startingSoon`

### AND-T018 — FavouritesViewModel: persistence
- **Type:** Integration (Robolectric)
- **Needs:** `FavouritesViewModel.kt`
- **Cases:**
  - `toggle(channel)` adds ID to `favouriteIds`
  - Second `toggle(channel)` removes it
  - `isFavourite(channel)` reflects current state
  - IDs survive ViewModel recreation (DataStore read on init)

### AND-T019 — SettingsDataStore: round-trip
- **Type:** Integration (Robolectric)
- **Needs:** `SettingsDataStore.kt`
- **Cases:**
  - `setServerUrl(url)` → `serverUrl.first() == url`
  - `setBufferPreset(HIGH)` → `bufferPreset.first() == HIGH`
  - `setOnboardingComplete(true)` → `onboardingComplete.first() == true`
  - `addSource()` → `sources.first()` contains the added source
  - `removeSource(id)` → source absent from subsequent emission
  - `updateSource()` → updated fields reflected in next emission
  - Unknown `bufferPreset` string in store → defaults to `DEFAULT` without crash

---

## SUITE AND-T05 — UI (Compose instrumented)

### AND-T020 — NowScreen: section visibility
- **Type:** UI
- **Needs:** `NowScreen.kt`, `NowBuckets.kt`
- **Setup:** inject fake `PlaylistViewModel` + `EpgViewModel` with controlled state
- **Cases:**
  - Empty channels → "Nothing on right now" empty state shown
  - Loading state → `CircularProgressIndicator` visible
  - Live matches present → "MATCHES LIVE" section header visible
  - No matches, on-air only → matches section absent
  - On-air count > 10 → "Show all N" button visible; tap expands list
  - Starting soon section absent when list is empty

### AND-T021 — BrowseScreen: chip filtering
- **Type:** UI
- **Needs:** `BrowseScreen.kt`
- **Cases:**
  - "All" chip selected by default
  - Tapping a sport chip → `MatchDayScreen` shown
  - Tapping a group chip → filters grid to that group only
  - Grid shows correct channel count label after filter

### AND-T022 — ChannelCard: playing state
- **Type:** UI
- **Needs:** `ChannelCard.kt`, `ChannelLogoView.kt`
- **Cases:**
  - Playing channel → `▶NOW` badge visible, accent border applied
  - Non-playing channel → star icon visible
  - Star tapped → `FavouritesViewModel.toggle()` called
  - LIVE badge visible when programme `isSportMatch == true`
  - Progress bar visible when current programme exists

### AND-T023 — PlayerScreen: controls auto-hide
- **Type:** UI
- **Needs:** `PlayerScreen.kt`, `PlayerControls.kt`
- **Cases:**
  - Tap on player area → controls become visible
  - Controls auto-hide after 3s idle
  - Error overlay visible when `playerError` is non-null
  - Retry button in error overlay triggers `retryManually()`
  - Score overlay visible when programme title contains `" vs "`

### AND-T024 — SettingsScreen: server URL update
- **Type:** UI
- **Needs:** `SettingsScreen.kt`, `SettingsViewModel.kt`
- **Cases:**
  - Server URL row displays current value from `SettingsViewModel.serverUrl`
  - Buffer preset segmented picker reflects `bufferPreset` state
  - Proxy toggle starts off; toggling calls `SettingsViewModel` (or local state)
  - Source rows show health dot and refresh interval

### AND-T025 — OnboardingScreen: step progression
- **Type:** UI
- **Needs:** `OnboardingScreen.kt`
- **Cases:**
  - Step 1 visible on first composition
  - "Skip" → advances to step 2
  - "Check Connection" with unreachable URL → error text visible
  - Step 2 "Add & Continue" disabled when URL field empty
  - Step 4 "Start Watching" → `SettingsViewModel.setOnboardingComplete(true)` called

### AND-T026 — AddChannelSheet: validation
- **Type:** UI
- **Needs:** `AddChannelSheet.kt`, `ChannelManagerViewModel.kt`
- **Cases:**
  - "Add Channel" button disabled when name or URL empty
  - Valid submission → `ChannelManagerViewModel.addChannel()` called with correct args
  - Server error → inline error text visible
  - Cancel → sheet dismissed, no API call

---

## SUITE AND-T06 — End-to-End (optional, device required)

### AND-T027 — Cold start loads channels
- **Type:** UI (device)
- **Needs:** Running NativeStream Server on LAN
- **Done when:** NowScreen renders at least one section within 10s of launch after onboarding

### AND-T028 — Stream plays end-to-end
- **Type:** UI (device)
- **Needs:** Running NativeStream Server with at least one healthy stream
- **Done when:** Tapping any channel card results in `PlayerScreen` visible with `ExoPlayer` in `STATE_READY` within 5s

---

## What to Ask For (Needs Files)

When implementing tests, provide the following to the engineer writing them:

| Test suite | Needs files |
|---|---|
| AND-T01 (Models) | `Channel.kt`, `Programme.kt`, `SportCategory.kt` |
| AND-T02 (Parsers) | `M3uParser.kt`, `EpgParser.kt`, `EpgStore.kt`, fixture `.m3u` + `.xml` files |
| AND-T03 (Networking) | `ApiClient.kt`, `ApiDtos.kt`, `ApiError.kt` |
| AND-T04 (ViewModels) | All `*ViewModel.kt` files, `NowBuckets.kt`, `SettingsDataStore.kt` |
| AND-T05 (UI) | All screen files, all viewmodel files, design screenshots |

---

## Test File Locations

```
app/src/
├── test/java/com/nativestream/android/        ← Unit + Integration (JVM)
│   ├── domain/
│   │   ├── ChannelTest.kt                     AND-T001
│   │   ├── ProgrammeTest.kt                   AND-T002
│   │   └── SportCategoryTest.kt               AND-T003
│   ├── data/parser/
│   │   ├── M3uParserTest.kt                   AND-T004, T005, T006
│   │   ├── EpgParserTest.kt                   AND-T007, T008
│   │   └── EpgStoreTest.kt                    AND-T009
│   ├── data/remote/
│   │   └── ApiClientTest.kt                   AND-T010, T011
│   ├── data/local/
│   │   └── SettingsDataStoreTest.kt           AND-T019
│   └── ui/viewmodel/
│       ├── PlaylistViewModelTest.kt            AND-T012
│       ├── EpgViewModelTest.kt                AND-T013, T014
│       ├── PlayerViewModelTest.kt             AND-T015, T016
│       ├── NowBucketsTest.kt                  AND-T017
│       └── FavouritesViewModelTest.kt         AND-T018
└── androidTest/java/com/nativestream/android/ ← Compose UI (instrumented)
    ├── NowScreenTest.kt                        AND-T020
    ├── BrowseScreenTest.kt                    AND-T021
    ├── ChannelCardTest.kt                     AND-T022
    ├── PlayerScreenTest.kt                    AND-T023
    ├── SettingsScreenTest.kt                  AND-T024
    ├── OnboardingScreenTest.kt               AND-T025
    ├── AddChannelSheetTest.kt                AND-T026
    └── e2e/
        ├── ColdStartTest.kt                   AND-T027
        └── StreamPlaybackTest.kt             AND-T028
```

---

## Summary

| Suite | Tickets | Type |
|---|---|---|
| AND-T01 Domain | T001–T003 | Unit |
| AND-T02 Parsers | T004–T009 | Unit |
| AND-T03 Networking | T010–T011 | Unit |
| AND-T04 ViewModels | T012–T019 | Unit / Integration |
| AND-T05 UI | T020–T026 | Compose instrumented |
| AND-T06 E2E | T027–T028 | Device |
| **Total** | **28 tests** | |