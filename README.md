# NativeStream Mac

A native macOS live sports TV client + self-healing stream server. Replaces browser-based streaming and scrcpy with hardware-decoded HLS playback, a real EPG guide, PiP, and AirPlay.

## Architecture

```
NativeStream Mac (Swift/SwiftUI)  ←→  StreamServer (Go, localhost:8888)  ←→  Stream CDNs
```

- **Mac App** — channel sidebar, EPG grid, AVFoundation player, PiP, AirPlay, Now Playing
- **StreamServer** — playlist serving, link health probing, EPG generation, auto-discovery (Phase 3)

## Prerequisites

| Tool | Version |
|---|---|
| macOS | 14 (Sonoma) or later |
| Xcode | 15 or later |
| Go | 1.22 or later |
| Apple Silicon | Recommended (M1–M4) |

## Quick Start

### 1. Clone and set up

```bash
git clone https://github.com/yourname/nativestream.git
cd nativestream
./scripts/install.sh
```

### 2. Build and run the server

```bash
make build-server
make run-server
# Server starts at http://127.0.0.1:8888
```

### 3. Open the Mac app

Open `app/macos/NativeStreamMac.xcodeproj` in Xcode and press **⌘R**, or build a release DMG:

```bash
make build-app
```

### 4. Add a playlist source

In the app: **⌘,** → Sources → Add Source

- **URL**: point at `http://localhost:8888/playlist.m3u` (once server has channels) or paste a direct M3U URL
- **EPG URL**: `http://localhost:8888/epg.xml` or a public XMLTV source (e.g. `https://epghub.xyz`)

## Finding Your First M3U8 Link (Phase 1 manual step)

Before the discovery engine (Phase 3) is running, you need to find stream links manually:

1. Open **Safari** on your Mac
2. Enable **Develop menu**: Safari → Settings → Advanced → Show Develop menu
3. Go to the sports streaming site you normally use on your phone
4. Open **Develop → Show Web Inspector → Network**
5. Start playing a stream
6. Filter by `.m3u8` — you'll see requests like `index.m3u8` or `master.m3u8`
7. Right-click → **Copy URL**
8. Paste into IINA first to verify it plays smoothly, then add to NativeStream

> **Tip**: Test the link in IINA before doing anything else. If it buffers in IINA, the upstream source is the problem — no amount of app polish fixes that.

## Development

```bash
# Run server tests
make test-server

# Run Go vet
make vet-server

# Clean build artefacts
make clean
```

## Project Structure

```
nativestream/
├── app/
│   ├── server/          ← Go backend (cmd/, api/, store/, validator/, discovery/, epg/)
│   └── macos/           ← Swift Mac app (App/, Core/, ViewModels/, Views/)
├── config/
│   └── config.example.yaml
├── scripts/
│   ├── install.sh
│   └── release.sh
├── Makefile
└── README.md
```

## Commit Convention

```
NS-{ticket-id}: short description
# e.g. NS-021: implement M3U parser with EXTINF attribute extraction
```

## Branch Strategy

```
main                 ← stable
├── phase/1-mac-app
├── phase/2-server
├── phase/3-discovery
└── feature/NS-{id}
```

## Performance Targets (Phase 1)

| Metric | Target |
|---|---|
| Boot to live video | < 2 seconds |
| CPU during 1080p/60fps | < 10% (M1) |
| RAM steady-state | < 200 MB |

## FAQ

**Streams are buffering** — Test in IINA first. If it buffers there too, the problem is the stream source, not the app. Try a different link or source.

**EPG not showing** — Check your EPG URL in Settings. The `tvg-id` in your M3U must match the channel `id` in the XMLTV file. Mismatches are common — Phase 3 adds fuzzy matching.

**App says "StreamServer not running"** — Run `make run-server` in a terminal. For permanent background operation: `make install-service`.

**Link died mid-match** — In Phase 1/2, find a new link and update it via `PUT /api/channels/:id`. Phase 3 auto-discovery handles this automatically.