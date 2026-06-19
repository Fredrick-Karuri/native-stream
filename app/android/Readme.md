# NativeStream Android

The Android client for NativeStream — a lean, EPG-first live TV player that connects to your self-hosted [NativeStream Server](../server).

> **Principle:** Tap and watch.

---

## What This Does

NativeStream Android turns your phone or tablet into a live TV remote. Connect it to a NativeStream Server on your LAN and it will:

- Show you what's on **right now** — live sport matches, news, entertainment — bucketed and sorted before you open the app
- Let you **browse 1,000+ channels** by category, sport, or playlist source with instant search
- Play any stream in **full-screen with EPG overlay** — score updates, programme progress, what's on next
- Work on **phones and tablets** — adaptive layout with master-detail on tablet, optimised grid on phone
- Feel **instant on every launch** — channels and EPG data load from cache before any network request

---

## Requirements

| Requirement | Version |
|---|---|
| Android | 8.0+ (API 26+) |
| Kotlin | 2.1.20 |
| Android Gradle Plugin | 8.9.1 |
| Gradle | 8.11.1 |
| Compile SDK | 35 |
| Target SDK | 35 |

---

## Stack

| Layer | Library |
|---|---|
| UI | Jetpack Compose + Material3 |
| Navigation | Navigation Compose |
| Player | Media3 ExoPlayer (HLS) |
| Networking | Ktor client |
| DI | Hilt |
| Persistence | DataStore Preferences + cacheDir JSON |
| Image loading | Coil |
| Serialization | kotlinx.serialization |
| Cast | Google Cast SDK |

---

## Getting Started

### 1. Clone and open

```bash
git clone <repo>
# Open app/android in Android Studio Ladybug or later
```

### 2. Run NativeStream Server on your LAN

```bash
# From the repo root
make run-server
```

The server defaults to port `8888`. Note your machine's LAN IP.

### 3. Build and run

```bash
cd app/android
./gradlew assembleDebug
# or Run from Android Studio with a device/emulator on the same LAN
```

### 4. Onboarding

On first launch, the app walks through:

1. **Server** — enter your LAN IP e.g. `http://192.168.1.42:8888` and test the connection
2. **Playlist** — add your M3U source (defaults to `{serverUrl}/playlist.m3u`)
3. **TV Guide** — add your XMLTV EPG URL (defaults to `{serverUrl}/epg.xml`)

---

## Project Structure

```
app/android/
├── gradle/
│   └── libs.versions.toml          # Version catalog
├── app/src/main/
│   ├── AndroidManifest.xml
│   └── java/com/nativestream/android/
│       ├── data/
│       │   ├── cast/               # Chromecast SDK wiring
│       │   ├── local/              # SettingsDataStore, ChannelCache, EpgIndexCache
│       │   ├── parser/             # M3uParser, EpgParser, EpgStore
│       │   ├── player/             # Media3 MediaSessionService
│       │   └── remote/             # ApiClient (Ktor), DTOs, ApiError
│       ├── di/                     # Hilt AppModule
│       ├── domain/model/           # Channel, Programme, SportCategory, PlaylistSource
│       └── ui/
│           ├── components/         # NSSourcePill, NSSourcePickerSheet, NSChip, …
│           ├── navigation/         # AppNavHost, NSNavRail, NSBottomNavBar
│           ├── screens/
│           │   ├── browse/         # BrowseScreen, BrowseMasterDetail, ChannelCard
│           │   ├── now/            # NowScreen, NowBuckets, match cards
│           │   ├── onboarding/     # 3-step first-launch flow
│           │   ├── player/         # PlayerScreen, controls, score overlay, PiP
│           │   └── settings/       # SettingsScreen, SettingsTwoPane, AddSourceSheet
│           ├── theme/              # NSColors, NSDimens, NSType
│           └── viewmodel/          # PlaylistViewModel, EpgViewModel, PlayerViewModel, …
```

---

## Screens

### Now
EPG-first home screen. On open, channels are pre-bucketed into:
- **Matches live** — sport matches with ` vs ` in the EPG title
- **Live on air** — everything else currently airing
- **Starting soon** — next programme within 2 hours

Bucket computation runs on the IO dispatcher and is exposed as `StateFlow` — the screen is a pure collector.

### Browse
Adaptive channel grid grouped by `groupTitle`:
- **Phone** — `LazyVerticalGrid` with `GridCells.Adaptive(180dp)`
- **Tablet** — master-detail with 320dp list pane and full detail pane
- Source pill in the top bar filters by playlist source
- Group chips → sub-group chips → sport chips in the filter row
- Search is debounced 150ms via `PlaylistViewModel.setSearchQuery()`

### Player
- Full-screen `PlayerView` wrapping Media3 ExoPlayer
- HLS with hardware decode (always on)
- Header injection per-stream (`Referer`, `User-Agent`)
- Retry on failure — up to 3 attempts with 2s delay
- Score overlay when EPG title contains ` vs `
- Collapsible sidebar: **On Now** + **Schedule** tabs
- Picture-in-Picture (API 26+)
- Chromecast via `RemoteMediaClient`
- Forces landscape on open, restores free rotation on dismiss

### Settings
- **Server** — URL + health check + stream probe trigger
- **Sources** — add/remove/refresh M3U playlist sources with color coding
- **Playback** — buffer preset (Low / Default / High)
- **Proxy** — Referer / User-Agent injection toggle

---

## Architecture

See [`docs/android-architecture.md`](../../docs/android-architecture.md) for:
- Full data flow diagram
- StateFlow ownership map
- EPG pipeline (fetch → parse → sort → index → cache → serve)
- Cold boot vs warm boot flow
- Adaptive layout decision tree
- Recomposition strategy

---

## Performance

See [`docs/android-performance.md`](../../docs/android-performance.md) for:
- Why EPG lookups are O(1) (precomputed index)
- Why filter computation is off the main thread (debounced StateFlow)
- Recomposition guards (`remember`, `derivedStateOf`)
- Rules for new features

**Key numbers:**
- `currentProgramme()` — O(1) map lookup (was O(n) scan)
- Channels visible on warm boot — ~10ms (was 3–8s)
- EPG in cards on warm boot — ~500ms (was 5–15s)
- Search recompute — debounced 150ms, IO dispatcher

---

## EPG Matching

`EpgStore` uses **FX-002 case-insensitive fallback matching** — exact TVG-ID match first, lowercase fallback second. Match rate logged on every load:

```
I/EpgViewModel: EPG match rate: 94% (719/764)
D/EpgViewModel: Unmatched channel: 'BBC One HD' tvgId='bbcone.hd.uk'
```

---

## Network Security

Cleartext HTTP permitted only for RFC 1918 private ranges (LAN server). All public internet traffic requires HTTPS. Configured in `res/xml/network_security_config.xml`.

---

## Persistence

| Key | Type | Notes |
|---|---|---|
| `server_url` | String | DataStore |
| `epg_url` | String | DataStore |
| `buffer_preset` | String | DataStore |
| `onboarding_complete` | Boolean | DataStore |
| `playlist_sources` | JSON | DataStore |
| `selected_source_id` | String | DataStore |
| `favourite_channel_ids` | Set\<String\> | DataStore |
| `channels_{id}.json` | File | cacheDir, 6h TTL |
| `epg_index_{id}.json` | File | cacheDir, 2h TTL |

---

## Build Variants

| Variant | Notes |
|---|---|
| `debug` | `applicationIdSuffix = ".debug"`, debuggable |
| `release` | Minified + resource shrunk, ProGuard rules in `proguard-rules.pro` |

---

## Useful Gradle Tasks

```bash
./gradlew assembleDebug          # Build debug APK
./gradlew assembleRelease        # Build release APK
./gradlew test                   # Unit tests
./gradlew connectedAndroidTest   # Instrumented tests
./gradlew lint                   # Lint
```

---

## Server Endpoints Used

| Endpoint | Used by |
|---|---|
| `GET /api/health` | Onboarding, Settings health dot |
| `GET /playlist.m3u` | M3U channel fetch |
| `GET /epg.xml` | XMLTV EPG fetch |
| `GET /api/channels` | Channel list |
| `POST /api/channels` | Add channel |
| `POST /api/probe` | Re-validate stream links |
| `GET /api/channels/{id}` | Re-fetch active link on player retry |

Full API docs: [`docs/server.md`](../../docs/server.md)

---

## Related

- [`app/macos`](../macos) — macOS client (SwiftUI)
- [`app/server`](../server) — Go server
- [`docs/android-architecture.md`](../../docs/android-architecture.md) — Architecture and data flow
- [`docs/android-performance.md`](../../docs/android-performance.md) — Performance decisions and rules