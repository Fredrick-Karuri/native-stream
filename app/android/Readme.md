# NativeStream Android

The Android client for NativeStream — a lean, EPG-first live TV player that connects to your self-hosted [NativeStream Server](../server).

> **Principle:** Tap and watch.

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
| Persistence | DataStore Preferences |
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

The server defaults to port `8888`. Note your machine's LAN IP — you'll need it in the next step.

### 3. Build and run

```bash
cd app/android
./gradlew assembleDebug
# or Run from Android Studio with a device/emulator on the same LAN
```

### 4. Onboarding

On first launch, the app walks through:

1. **Server** — enter your LAN IP, e.g. `http://192.168.1.42:8888`, and test the connection
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
│       │   ├── local/              # SettingsDataStore (DataStore Preferences)
│       │   ├── parser/             # M3uParser, EpgParser, EpgStore
│       │   ├── player/             # Media3 MediaSessionService
│       │   └── remote/             # ApiClient (Ktor), DTOs, ApiError
│       ├── di/                     # Hilt AppModule
│       ├── domain/model/           # Channel, Programme, SportCategory, …
│       └── ui/
│           ├── components/         # MiniPlayer, NSComponents, NSGroupHeader, …
│           ├── navigation/         # AppNavHost, NSBottomNavBar
│           ├── screens/
│           │   ├── browse/         # BrowseScreen, MatchDayScreen, ChannelCard, …
│           │   ├── now/            # NowScreen, match cards, live/soon rows
│           │   ├── onboarding/     # 3-step first-launch flow
│           │   ├── player/         # PlayerScreen, sidebar, controls, overlays
│           │   └── settings/       # SettingsScreen, section panels
│           ├── theme/              # NSTheme, NSColors, NSDimens, NSType, …
│           └── viewmodel/          # One VM per feature
```

---

## Architecture

**MVVM + unidirectional data flow.**

- `ViewModel` exposes `StateFlow` — Compose collects with `collectAsState()`
- `SettingsDataStore` is the single source of truth for all persisted preferences
- `ApiClient` (Ktor) is a `@Singleton`; base URL is updated at runtime from settings
- Parsers (`M3uParser`, `EpgParser`) are pure functions — no network, no DI
- Navigation is handled by a single `NavHost` in `AppNavHost`; the player overlays as a full-screen composable rather than a separate Activity

---

## Key Features

### Now Screen
EPG-first home. Channels bucketed into three sections:
- **Matches live** — sport keyword match + `" vs "` in title
- **Live on air** — anything else currently airing
- **Starting soon** — next programme within 2 hours

### Browse
Adaptive 2-column channel grid grouped by `groupTitle`. Sport filter chips delegate to `MatchDayScreen` which shows live + upcoming matches with competition-aware card variants (UCL, featured, plain).

### Player
- Full-screen `PlayerView` wrapping Media3 ExoPlayer
- HLS with hardware decode (always on)
- Header injection per-stream (`Referer`, `User-Agent`) via `DefaultHttpDataSource.Factory`
- Retry on failure — up to 3 attempts with 2s delay, re-fetching active link from server
- Score overlay when EPG title contains `" vs "`
- Collapsible sidebar: **On Now** (sorted playing → live → upcoming) + **Schedule** tabs
- Picture-in-Picture (API 26+)
- Chromecast via `RemoteMediaClient`

### Settings
Persisted via DataStore Preferences:

| Key | Default |
|---|---|
| `server_url` | `http://192.168.1.42:8888` |
| `epg_url` | *(empty)* |
| `buffer_preset` | `DEFAULT` |
| `onboarding_complete` | `false` |
| `playlist_sources` | `[]` (JSON) |
| `favourite_channel_ids` | `{}` (Set) |

---

## EPG Matching

The parser uses **FX-002 case-insensitive fallback matching** — exact TVG-ID match first, lowercase fallback second. Match rate is logged to Logcat on every cold start:

```
I/EpgViewModel: EPG match rate: 94% (423/450)
D/EpgViewModel: Unmatched channel: 'BBC One HD' tvgId='bbcone.hd.uk'
```

---

## Network Security

Cleartext HTTP is permitted only for RFC 1918 private ranges (LAN server). All public internet traffic requires HTTPS. Configured in `res/xml/network_security_config.xml`.

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

## Connecting to the Server

The app communicates with the NativeStream Server over LAN. All endpoints are documented in [`docs/server.md`](../../docs/server.md). Key ones:

| Endpoint | Used by |
|---|---|
| `GET /api/health` | Onboarding connection check, Settings health dot |
| `GET /playlist.m3u` | M3U playlist fetch |
| `GET /epg.xml` | XMLTV EPG fetch |
| `GET /api/channels` | Channel list |
| `POST /api/channels` | Add channel (AddChannelSheet) |
| `POST /api/probe` | Re-validate all stream links |
| `GET /api/channels/{id}` | Re-fetch active link on player retry |

---

## Related

- [`app/macos`](../macos) — macOS client (SwiftUI)
- [`app/server`](../server) — Go server
- [`docs/android-tickets.md`](../../docs/android-tickets.md) — Full implementation tickets (AND-001–028)