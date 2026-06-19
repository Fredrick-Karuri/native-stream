# NativeStream Android — Design Document

**Version:** 1.0  
**Last Updated:** 2026-06-07  
**Status:** In Development  
**Authors:** Fredrick Karuri

---

## 1. Context & Goals

### Problem Statement

IPTV users who self-host streams via NativeStream Server have no high-quality native Android client. Existing options are either generic M3U players with no EPG awareness, Mac-only, or require cloud dependencies that break the self-hosted model. The result: users with working infrastructure have a poor viewing experience on Android.

### Goals

- Deliver a native Android client that connects to a self-hosted NativeStream Server over LAN
- Surface live EPG content (matches, on-air, upcoming) on the home screen without configuration beyond a server IP
- Play HLS streams with hardware decode, header injection, and automatic retry
- Support background playback, Picture-in-Picture, and Chromecast

### Non-Goals

- No stream acquisition — users provide their own M3U sources
- No cloud dependency or user accounts
- No transcoding — server handles stream proxying
- No casting to non-Chromecast devices (AirPlay is macOS client concern)
- No tablet-optimised layout in v1.0 (tracked separately in AND-TABLET tickets)

### Success Metrics

| Metric | Target |
|---|---|
| Time from app open → populated Now screen (post-onboarding) | < 5s on LAN |
| Stream playback start time (tap → first frame) | < 3s |
| EPG match rate on a typical playlist | ≥ 85% |
| Crash-free sessions | ≥ 99% |
| Onboarding completion rate | ≥ 80% |

---

## 2. User Stories & Personas

### Personas

**Alex — The Self-Hoster**  
32, software engineer. Runs NativeStream Server on a home server. Has a curated M3U playlist and XMLTV EPG. Watches live sport on weekends. Currently uses a generic IPTV app on Android that requires manual channel searching with no programme info.

**Sam — The Household User**  
Alex's partner. Non-technical. Wants to watch TV on their phone without configuration. Will not troubleshoot a broken stream. Expects the app to "just work."

---

### User Stories

#### Onboarding

| ID | Story | Acceptance |
|---|---|---|
| US-01 | As **Alex**, I want to connect the app to my server by entering only its LAN IP, so I can start watching without configuring individual URLs. | One field. Auto-derives playlist and EPG endpoints. |
| US-02 | As **Alex**, I want the app to test my server connection before proceeding, so I know it's working before I reach the Now screen. | Connection result shown inline. Clear error if unreachable. |

#### Now Screen

| ID | Story | Acceptance |
|---|---|---|
| US-03 | As **Sam**, I want to open the app and immediately see what live sport is on, so I don't have to search for it. | Now screen populated within 5s. Live matches shown first. |
| US-04 | As **Alex**, I want live matches to show the current score, so I can decide whether to tune in without opening the stream. | Score extracted from EPG title when `" vs "` present. |
| US-05 | As **Sam**, I want to tap any programme and start watching immediately, so there's no friction between discovery and playback. | Tap → stream plays in < 3s. No additional confirmation. |

#### Browse

| ID | Story | Acceptance |
|---|---|---|
| US-06 | As **Alex**, I want to filter channels by sport type, so I can find football quickly without scrolling through everything. | Sport chips filter to EPG-matched channels. |
| US-07 | As **Alex**, I want to search channels by name or group, so I can find a specific channel fast. | Search filters live as I type. |
| US-08 | As **Alex**, I want to favourite channels, so they surface quickly. | Star persists across restarts. Favs chip filters to starred. |

#### Player

| ID | Story | Acceptance |
|---|---|---|
| US-09 | As **Sam**, I want the stream to automatically recover from a brief dropout, so I don't have to restart it manually. | Up to 3 auto-retries with 2s delay before error overlay shown. |
| US-10 | As **Alex**, I want to lock my screen without stopping the stream, so I can listen while doing something else. | Background playback via MediaSessionService. Lock screen controls shown. |
| US-11 | As **Alex**, I want to use Picture-in-Picture while browsing other apps, so I can keep the stream visible. | PiP activates on home button press. Play/pause works in PiP. |
| US-12 | As **Alex**, I want to cast to my TV from the player, so I can move from phone to big screen seamlessly. | Cast button visible when receiver on network. Stream loads on receiver. |
| US-13 | As **Alex**, I want to see what else is on without leaving the player, so I can switch channels without going back to Browse. | Sidebar shows On Now + Schedule tabs. Channel switch without leaving player. |

#### Settings

| ID | Story | Acceptance |
|---|---|---|
| US-14 | As **Alex**, I want to add multiple M3U playlist sources, so I can aggregate channels from different providers. | Multiple sources supported. Each has health dot + refresh interval. |
| US-15 | As **Alex**, I want streams that need custom headers to work without manual fiddling, so protected streams play transparently. | Referer/User-Agent injected per channel via `streamHeaders`. |

---

## 3. Feature Definitions (MoSCoW)

### Must Have (v1.0)

| Feature | Components |
|---|---|
| **Onboarding** | Single-field server setup, connection test, playlist auto-derive, EPG auto-derive, completion flag |
| **Now Screen** | EPG bucketing (matches / on-air / soon), hero match card with score, small match grid, LiveOnAirRow, StartingSoonCard, empty + loading states |
| **Browse Screen** | Sport chip filters, group chip filters, 2-column channel grid, search, ChannelCard with LIVE/NOW/star badges |
| **Player** | HLS via ExoPlayer, hardware decode, header injection, auto-retry (×3), controls overlay, score overlay, background playback, lock screen controls |
| **Settings** | Server URL, playlist sources CRUD, EPG URL, buffer preset, proxy toggle, DataStore persistence |
| **EPG Engine** | XMLTV parser, M3U parser, TVG-ID case-insensitive matching, match rate diagnostic |

### Should Have (v1.0)

| Feature | Components |
|---|---|
| **Favourites** | Star toggle on ChannelCard, DataStore-persisted Set, Favs chip filter |
| **Player Sidebar** | On Now tab (sorted playing→live→upcoming), Schedule tab (12h window), channel switch without leaving player |
| **Picture-in-Picture** | 16:9 PiP params, play/pause remote action, return on tap |
| **Chromecast** | Cast button in player, MediaRouter detection, RemoteMediaClient stream load |
| **Mini Player** | 64dp strip above bottom nav, swipe-up expand, score/live overlay, progress, controls |

### Could Have (v1.1)

| Feature | Notes |
|---|---|
| **Add Channel Sheet** | POST to server API, reload playlist on success |
| **Play URL Sheet** | Ad-hoc URL playback with optional headers, not persisted |
| **Auto-refresh scheduling** | Shortest interval across sources drives refresh timer |
| **Tablet layout** | See AND-TABLET tickets |

### Won't Have (v1.0)

- VPN / WireGuard tunnel integration (tracked for Pro tier)
- Video quality switching (multi-bitrate HLS)
- DVR / timeshift
- User accounts or cloud sync

---

## 4. Acceptance Criteria

### AC-01 — Onboarding

**Given** the app is launched for the first time  
**When** the user enters a valid server IP and taps "Check Connection"  
**Then** the app reaches `GET /api/health`, shows success, and advances to step 2

**Given** the server is unreachable  
**When** the user taps "Check Connection"  
**Then** an inline error is shown; the user is not advanced; no crash occurs

**Given** the user completes all 3 steps  
**When** "Start Watching" is tapped  
**Then** `onboardingComplete = true` is persisted; the Now screen is shown on all subsequent launches

**Given** the user taps "Skip" on any step  
**When** they reach the Now screen  
**Then** all previously entered values are persisted; skipped fields use empty defaults

---

### AC-02 — Now Screen Bucketing

**Given** a channel has a live programme whose title contains `" vs "` and matches a sport keyword  
**When** the Now screen loads  
**Then** it appears in "Matches live", not "Live on air"

**Given** a channel has a live programme that does not match any sport keyword  
**When** the Now screen loads  
**Then** it appears in "Live on air", not "Matches live"

**Given** a channel has no current programme but a next programme starting within 2 hours  
**When** the Now screen loads  
**Then** it appears in "Starting soon"

**Given** all three buckets are empty  
**When** the Now screen loads  
**Then** the empty state "Nothing on right now" is shown; no section headers rendered

---

### AC-03 — Player Retry

**Given** a stream fails to load  
**When** the first failure occurs  
**Then** the player retries after 2s, re-fetching the active link from the server

**Given** 3 consecutive failures  
**When** the third retry fails  
**Then** the error overlay is shown with a "Retry" button; no further automatic retries occur

**Given** the server is unreachable during retry link re-fetch  
**When** the retry fires  
**Then** the cached `streamUrl` is used as fallback; the retry still proceeds

---

### AC-04 — Background Playback

**Given** a stream is playing  
**When** the screen is locked  
**Then** audio continues; lock screen media controls (play/pause, channel name) are visible

**Given** a stream is playing  
**When** the home button is pressed  
**Then** PiP activates; stream video continues in the PiP window; play/pause works

**Given** the app is in PiP  
**When** the PiP window is tapped  
**Then** the app returns to full-screen player

---

### AC-05 — EPG Matching

**Given** an XMLTV file where a channel ID is `"BBC.ONE"`  
**When** the playlist has `tvg-id="bbc.one"`  
**Then** the programme is matched via lowercase fallback; `currentProgramme()` returns correctly

**Given** a channel with no matching EPG entry  
**When** `programmesFor(tvgId)` is called  
**Then** an empty list is returned; no crash or null pointer

---

### AC-06 — Favourites

**Given** a user stars a channel  
**When** the app is restarted  
**Then** the channel remains starred; the Favs chip filters to it

**Given** a user un-stars a channel  
**When** the star is tapped again  
**Then** it is removed from favourites immediately; DataStore updated

---

## 5. System Architecture

```
┌─────────────────────────────────────────────────────────┐
│                    Android App                          │
│                                                         │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐              │
│  │   Now    │  │  Browse  │  │ Settings │  ← Screens   │
│  │ Screen   │  │  Screen  │  │  Screen  │              │
│  └────┬─────┘  └────┬─────┘  └────┬─────┘              │
│       │              │              │                    │
│  ┌────▼──────────────▼──────────────▼─────┐             │
│  │           ViewModels (Hilt)            │             │
│  │  Playlist · EPG · Player · Settings    │             │
│  │  Favourites · ChannelManager · Cast    │             │
│  └────┬──────────────┬────────────────────┘             │
│       │              │                                  │
│  ┌────▼────┐   ┌──────▼──────┐                         │
│  │ Parsers │   │  DataStore  │  ← Local persistence    │
│  │ M3U EPG │   │ Preferences │                         │
│  └─────────┘   └─────────────┘                         │
│       │                                                 │
│  ┌────▼────────────────────┐                           │
│  │      ApiClient (Ktor)   │                           │
│  └────────────┬────────────┘                           │
│               │                                         │
│  ┌────────────▼────────────┐                           │
│  │  Media3 ExoPlayer       │                           │
│  │  + MediaSessionService  │  ← Background playback   │
│  └─────────────────────────┘                           │
└─────────────────────────────────────────────────────────┘
                    │ LAN (HTTP)
┌───────────────────▼─────────────────────────────────────┐
│              NativeStream Server (Go)                   │
│  /playlist.m3u · /epg.xml · /api/channels · /api/probe │
└─────────────────────────────────────────────────────────┘
```

### Data Flow — Startup

```
App launch
  → SettingsDataStore.serverUrl
  → ApiClient.setBaseUrl()
  → parallel: PlaylistViewModel.loadAll() + EpgViewModel.load()
      → M3uParser.parse(bytes)       → List<Channel>
      → EpgParser.parse(stream)      → EpgStore
  → NowScreen.recompose()
      → NowBuckets.liveMatches()
      → NowBuckets.liveOnAir()
      → NowBuckets.startingSoon()
```

### Data Flow — Playback

```
User taps channel
  → PlayerViewModel.play(channel)
  → DefaultHttpDataSource.Factory (injects streamHeaders)
  → HlsMediaSource → ExoPlayer
  → MediaSessionService (background, lock screen)
  → PlayerScreen renders PlayerView
```

---

## 6. Technical Specifications

### Tech Stack

| Component | Choice | Justification |
|---|---|---|
| Language | Kotlin 2.1.20 | First-class Android, coroutines, serialization |
| UI | Jetpack Compose + Material3 | Declarative, no XML layouts, state-driven |
| Player | Media3 ExoPlayer + HLS | Native HLS support, hardware decode, MediaSession integration |
| DI | Hilt | Compile-time verified, AndroidViewModel support |
| Networking | Ktor | Kotlin-native, multiplatform-ready, lightweight |
| Persistence | DataStore Preferences | Coroutine-native, replaces SharedPreferences |
| Image loading | Coil | Compose-native, coroutine-native |
| Serialization | kotlinx.serialization | Compile-time, no reflection |
| Cast | Google Cast SDK | Only supported path for Chromecast |

### Data Schemas

#### Channel
```kotlin
data class Channel(
    val id: String,           // tvgId if present, else streamUrl
    val tvgId: String,
    val name: String,
    val groupTitle: String,   // default: "Uncategorised"
    val logoUrl: String?,
    val streamUrl: String,
    val streamHeaders: Map<String, String>,  // Referer, User-Agent etc.
)
```

#### Programme
```kotlin
data class Programme(
    val channelId: String,
    val title: String,
    val startEpochMs: Long,
    val stopEpochMs: Long,
)
// Computed: id, progress, isNow, startTimeString, isSportMatch, timeRemainingString
```

#### PlaylistSource
```kotlin
data class PlaylistSource(
    val id: String,           // UUID
    val name: String,
    val url: String,
    val refreshIntervalHours: Int,  // 0 = manual
)
```

### API Contracts

All calls go to `{serverUrl}/` over LAN HTTP.

| Method | Endpoint | Request | Response | Used by |
|---|---|---|---|---|
| GET | `/api/health` | — | `{status, uptime, channels, healthy}` | Onboarding, Settings health dot |
| GET | `/playlist.m3u` | — | Raw M3U bytes | PlaylistViewModel |
| GET | `/epg.xml` | — | Raw XMLTV bytes | EpgViewModel |
| GET | `/api/channels` | — | `{channels: [ChannelResponse]}` | ChannelManager |
| GET | `/api/channels/{id}` | — | `ChannelDetailResponse` | Player retry |
| POST | `/api/channels` | `CreateChannelRequest` | `ChannelDetailResponse` | AddChannelSheet |
| PUT | `/api/channels/{id}` | `UpdateChannelRequest` | `{status}` | ChannelManager |
| DELETE | `/api/channels/{id}` | — | `{status}` | ChannelManager |
| POST | `/api/probe` | — | `{status}` | Settings |

### DataStore Keys

| Key | Type | Default |
|---|---|---|
| `server_url` | String | `"http://192.168.1.42:8888"` |
| `epg_url` | String | `""` |
| `buffer_preset` | String | `"DEFAULT"` |
| `onboarding_complete` | Boolean | `false` |
| `playlist_sources` | String (JSON) | `"[]"` |
| `favourite_channel_ids` | StringSet | `{}` |

---

## 7. Implementation & Risk

### Milestones

| Milestone | Tickets | Target |
|---|---|---|
| M1 — Bootstrap + data layer | AND-001–007 | Week 1 |
| M2 — Navigation shell + settings | AND-008–009, AND-023–025 | Week 2 |
| M3 — Now screen complete | AND-010–012 | Week 2 |
| M4 — Browse + player core | AND-013–018 | Week 3 |
| M5 — Player features (sidebar, PiP, Cast) | AND-019–022 | Week 3–4 |
| M6 — Polish + diagnostics | AND-026–028 | Week 4 |
| M7 — Tablet layout | AND-TABLET | Week 5 |

### Dependencies

| Dependency | Risk | Mitigation |
|---|---|---|
| NativeStream Server running on LAN | High — app is non-functional without it | Onboarding connection test; clear error messaging |
| EPG data quality | Medium — poor TVG-IDs break Now screen | Case-insensitive fallback matching; match rate logged |
| Google Cast SDK availability | Low — device may lack GMS | `CastContext` initialisation wrapped in try/catch; button hidden gracefully |
| M3U source reliability | Medium — sources go stale | Configurable refresh intervals; health dot in Settings |

### Security Considerations

- Cleartext HTTP restricted to RFC 1918 LAN ranges via `network_security_config.xml`
- No credentials or API keys stored — server is unauthenticated by design
- `streamHeaders` stored in DataStore (unencrypted) — acceptable for LAN-only use; revisit if cloud sync added
- Cast: stream URL transmitted to receiver unencrypted — same threat model as local network

### Test Strategy

See `TESTING.md` for the full 28-test spec.

| Layer | Framework | Coverage target |
|---|---|---|
| Domain models | JUnit4 | 100% |
| Parsers | JUnit4 | Happy path + key edge cases |
| ViewModels | Coroutines-test + Turbine + MockK | Core state transitions |
| UI | Compose instrumented | Critical user flows |
| E2E | Device + live server | Smoke only |

### Rollback Plan

- All feature state lives in `SettingsDataStore` — clearing app data returns to onboarding
- `onboardingComplete = false` in DataStore triggers fresh onboarding on next launch
- Server-side changes are independent — client update does not require server update for core playback
- APK versioning follows `versionCode` increment; previous APK can be sideloaded if needed