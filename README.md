# NativeStream Mac

A self-healing, native macOS live sports TV client. Hardware-decoded HLS playback, auto-discovering stream links, EPG guide, PiP, and AirPlay — no emulators, no browser wrappers.

## Architecture

```
NativeStream Mac (Swift/SwiftUI)
    ↕ localhost:8888
StreamServer (Go)
  ├── Channel Store (in-memory + JSON snapshot)
  ├── Validator (20-worker probe pool, self-healing)
  ├── Discovery Engine
  │   ├── Gist Crawler      (community M3U playlists)
  │   ├── Reddit Crawler    (subreddit posts)
  │   ├── Telegram Crawler  (public channels)
  │   └── DirectM3U Crawler (stable M3U URLs)
  ├── EPG Engine (ESPN + football-data.org → XMLTV)
  └── HLS Proxy (optional header injection)
```

## Prerequisites

| | Version |
|---|---|
| macOS | 14 (Sonoma)+ |
| Xcode | 15+ |
| Go | 1.22+ |

## Quick Start

```bash
git clone https://github.com/yourname/nativestream.git
cd nativestream
./scripts/install.sh      # creates ~/.config/nativestream/config.yaml
make build-server
make run-server           # starts at http://127.0.0.1:8888
```

Open `app/macos/NativeStreamMac.xcodeproj` → ⌘R

## First Channel

```bash
curl -X POST http://localhost:8888/api/channels \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Sky Sports 1",
    "group_title": "Football",
    "tvg_id": "SkySports1",
    "keywords": ["sky","skysports","sky1","skysports1"],
    "stream_url": "https://your-found-stream.m3u8"
  }'
```

Then in the Mac app: Settings → Sources → `http://localhost:8888/playlist.m3u`

## Enabling Auto-Discovery

Edit `~/.config/nativestream/config.yaml`:

```yaml
discovery_enabled: true
```

Add sources — any combination of Gist IDs, subreddits, Telegram channels, or direct M3U URLs — and the server will find and validate stream links automatically, replacing dead ones before you notice.

## Development

```bash
make build-server           # compile Go server
make run-server             # run locally
make test-server            # go test -race ./...
make install-service        # register as launchd service
make clean
```

## Distribution

```bash
# Server — build release binaries
VERSION=4.0.0 ./scripts/brew-release.sh

# Mac app — build notarised DMG
APPLE_ID=you@example.com TEAM_ID=XXXXXXXXXX ./scripts/release.sh
```

## API Reference

| Endpoint | Description |
|---|---|
| `GET /playlist.m3u` | M3U of all healthy channels |
| `GET /epg.xml` | XMLTV TV guide (48h) |
| `GET /api/health` | Server status |
| `POST /api/channels` | Add a channel |
| `PUT /api/channels/:id` | Update stream URL |
| `POST /api/probe` | Trigger immediate health check |
| `GET /api/discovery/status` | Discovery engine status |
| `POST /api/discovery/run` | Trigger immediate discovery cycle |
| `GET /api/discovery/unmatched` | Unmatched candidate links |

## Performance Targets

| Metric | Target |
|---|---|
| Boot to live video | < 2s |
| CPU at 1080p/60fps | < 10% (M1) |
| RAM steady-state | < 200 MB |
| Server memory | < 30 MB |
| Playlist response | < 5ms |

## Project Structure

```
nativestream/
├── app/
│   ├── server/           Go backend
│   │   ├── api/          HTTP handlers + middleware
│   │   ├── config/       Configuration loader
│   │   ├── discovery/    Engine, crawlers, extractor, matcher
│   │   ├── epg/          XMLTV generation + priority escalation
│   │   ├── logging/      slog setup
│   │   ├── playlist/     M3U generator
│   │   ├── proxy/        HLS transparent proxy
│   │   ├── service/      launchd install/uninstall
│   │   ├── shutdown/     Graceful shutdown handler
│   │   ├── store/        Channel store + snapshots
│   │   └── validator/    Link scoring + self-healing
│   └── macos/            Swift/SwiftUI Mac app
│       ├── App/          Entry point, ContentView
│       ├── Core/         Models, Parsers, Services
│       ├── ViewModels/   Observable state
│       └── Views/        Sidebar, Player, EPG grid, Settings
├── config/               Example config
├── homebrew/             Homebrew formula
└── scripts/              Build, install, release, test notes
```

## Commit Convention

```
NS-{ticket-id}: description
# e.g. NS-211: implement GitHub Gist crawler with conditional fetch
```