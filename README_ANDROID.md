# NativeStream Android

The Android client for NativeStream — a lean, EPG-first live TV player that connects to your self-hosted [NativeStream Server](README_SERVER.md).

> **Principle:** Tap and watch.

## What You Can Do

- See what's on right now — live matches, news, entertainment — bucketed and sorted before you even open the app
- Browse 1,000+ channels by category, sport, or playlist source with instant search
- Play any stream full-screen with an EPG overlay: score updates, programme progress, what's on next
- Use it comfortably on both phones and tablets — adaptive layout with master-detail on tablet
- Launch to something on screen instantly — channels and EPG load from cache before any network request
- Cast to any device on your LAN via Local Media Connect — send a stream to your Mac, or pull one back to your phone

## Screenshots

| Now (Android) | Explore (Android) | Explore (Tablet) |
|:---:|:---:|:---:|
| ![Now - Phone](screenshots/now-phone.png) | ![Explore - Phone](screenshots/explore-phone.png) | ![Explore - Tablet](screenshots/explore-tablet.png) |

## Quick Start

1. Run NativeStream Server on your LAN (see [README_SERVER.md](README_SERVER.md))
2. Clone and open `app/android` in Android Studio Ladybug or later
3. Build and run:

```bash
cd app/android
./gradlew assembleDebug
# or Run from Android Studio with a device/emulator on the same LAN
```

4. On first launch, the app scans your network via mDNS for a NativeStream Server and pre-fills the URL automatically. If discovery fails, enter it manually in Settings → Server.

For app structure, data flow, and the EPG pipeline, see [docs/android-architecture.md](docs/android-architecture.md). For performance rules when adding features, see [docs/android-performance.md](docs/android-performance.md). For how device-to-device casting works, see [docs/local-media-connect.md](docs/local-media-connect.md). For unit/UI test commands and linting, see [docs/development.md](docs/development.md).

## Requirements

| Requirement | Version |
|---|---|
| Android | 8.0+ (API 26+) |
| Kotlin | 2.1.20 |
| Android Gradle Plugin | 8.9.1 |
| Gradle | 8.11.1 |
| Compile / Target SDK | 35 |

## Useful Gradle Tasks

```bash
./gradlew assembleDebug          # Build debug APK
./gradlew assembleRelease        # Build release APK
./gradlew test                   # Unit tests
./gradlew connectedAndroidTest   # Instrumented tests
./gradlew lint                   # Lint
```