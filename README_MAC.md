# NativeStream for Mac

A native sports viewing experience built for macOS, connected to your self-hosted [NativeStream Server](README_SERVER.md).

## What You Can Do

- Browse channels grouped by sport and category, search, filter, and favorite the ones you watch most
- Follow Match Day — live and upcoming matches with context, schedules, and scores
- Explore an interactive TV Guide timeline and switch channels directly from it
- Watch with hardware-accelerated native playback, Picture-in-Picture, and AirPlay
- Keep a floating Mini Player going while you browse other sections
- Receive and pull back streams from your Android phone over the local network (Local Media Connect)
- Use native macOS integration — media keys, Now Playing/Control Center, Stage Manager, Spaces

## Screenshots

**Now** — live matches and what's currently on air
![Now - macOS](screenshots/now-mac.png)

**Schedule** — full programme guide with date navigation
![Schedule - macOS](screenshots/schedule-mac.png)

**Favourites** — pinned channels and programmes
![Favourites - macOS](screenshots/favorites-mac.png)

## Quick Start

1. Run NativeStream Server on your LAN (see [README_SERVER.md](README_SERVER.md))
2. Download `NativeStreamMac.dmg` and drag **NativeStream** into Applications
3. Launch the app — it scans for the server automatically via mDNS on launch

Building from source instead:

```bash
git clone https://github.com/yourname/nativestream.git
cd nativestream
ordo mac:run   # or: make run-app
# or manually:
open app/macos/NativeStream/NativeStream.xcodeproj
```

Press `⌘R` in Xcode to build and run, or use the command above, which also strips the extended attribute Gatekeeper otherwise adds to locally-built apps.

For view model structure and how playback works internally, see [docs/mac-architecture.md](docs/mac-architecture.md). For Local Media Connect, see [docs/local-media-connect.md](docs/local-media-connect.md). For the cross-platform system view, see [docs/architecture.md](docs/architecture.md). For the full dev command reference (build, lint, combined dev env), see [docs/development.md](docs/development.md). If something isn't working, see [docs/troubleshooting.md](docs/troubleshooting.md).

## Requirements

| Requirement | Version/Detail |
|---|---|
| macOS | 14 (Sonoma) or later, for both running the app and building from source |
| Xcode | 15+ (build from source only) |
| Distribution | Notarized DMG, outside the Mac App Store |

## Keyboard Shortcuts

| Action | Shortcut |
|---|---|
| Open Settings | ⌘, |
| Play / Pause | Space |
| Picture-in-Picture | P |