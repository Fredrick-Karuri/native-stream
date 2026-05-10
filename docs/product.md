# NativeStream — Product Definition

**Product:** NativeStream  
**Type:** Native macOS application + local stream server  
**Status:** Active development  
**Last Updated:** 2026-05-09

---

## What It Is

NativeStream is a two-part system for watching live sports on macOS without emulators, browser wrappers, or third-party streaming apps.

**NativeStream Mac** is a native SwiftUI application. It presents a live TV channel guide, an EPG (TV guide), and a hardware-decoded video player with full macOS integration — Picture-in-Picture, AirPlay, Stage Manager, media keys, and Now Playing.

**NativeStream Server** is a lightweight Go binary that runs silently in the background. It discovers stream links automatically from configured sources, validates and scores them, replaces dead links before the user notices, and serves a live M3U playlist and XMLTV EPG to the Mac app over localhost.

The user experience goal: open the app, select a match, watch. No manual link hunting, no buffering from software decode, no browser pop-ups.

---

## The Problem It Solves

| Current approach | Problem |
|---|---|
| Scrcpy (mirror phone) | Two decode pipelines — phone + Mac. Heavy CPU, latency, no macOS features |
| Kodi | Non-native UI, slow boot (8–15s), no Stage Manager/Spaces/AirPlay |
| Browser streaming | 25–45% CPU (software decode), ads, no PiP, no EPG, no Now Playing |
| Manual M3U players (IINA/VLC) | No EPG, no channel guide, no auto link refresh |

NativeStream combines hardware-decoded playback (AVFoundation/VideoToolbox) with automatic link management into a single native app.

---

## Target User

**Primary:** macOS user on Apple Silicon who watches live football, cricket, or rugby regularly. Technically comfortable — can run a terminal command, is not afraid of a config file. Currently using one of the workarounds above and finds it frustrating.

**Secondary:** Same user who wants to share the setup with one or two friends.

---

## Core Features

### Mac App
- **Channel browser** — grid layout grouped by sport, with logos, live indicators, and EPG now/next info
- **Match Day tab** — date-grouped view of live and upcoming matches from EPG data
- **TV Guide tab** — horizontal EPG grid with 6-hour lookahead, now-line, programme progress bars
- **Player** — full-window HLS playback via AVFoundation; hardware decode via VideoToolbox; score overlay when EPG title contains match data
- **Mini player** — floating widget while browsing other tabs during active playback
- **macOS integration** — PiP, AirPlay, Now Playing (Control Center + media keys), Background Audio, Stage Manager, Spaces
- **Favourites** — starred channels pinned at top of browser
- **Settings** — playlist sources, EPG URL, buffer preset, server URL

### Server
- **Playlist serving** — `GET /localhost:8888/playlist.m3u` — dynamically generated M3U of all healthy channels
- **EPG serving** — `GET /localhost:8888/epg.xml` — XMLTV generated from match schedule APIs
- **Link validation** — continuous 20-worker probe pool; scores each link on latency, reachability, and bitrate
- **Self-healing** — when an active link's score drops below threshold, the best candidate is promoted automatically
- **Auto-discovery** — crawls GitHub Gists, Reddit subreddits, public Telegram channels, and direct M3U URLs for fresh stream links
- **Match-aware priority** — escalates crawl frequency for channels with a match starting within 2 hours
- **HLS proxy** — optional transparent forwarder for streams requiring custom HTTP headers
- **Channel management API** — REST endpoints for adding, updating, and removing channels

---

## Design Principles

**Server is the brain; app is the face.** All link intelligence lives in the server. The Mac app never knows where links come from — it polls one URL and plays what it gets.

**Zero manual intervention on match day.** The system should have validated, scored links ready before kickoff, discovered automatically.

**Native or nothing.** No Electron, no emulator, no web view. AVFoundation for decode, SwiftUI for UI, VideoToolbox for hardware acceleration. The app must behave like a first-class macOS citizen.

**Localhost only.** The server binds to `127.0.0.1` exclusively. It is a personal tool, not a network service.

**Zero third-party dependencies in the app.** All parsers (M3U, XMLTV) written in Swift using Foundation only. One external Go dependency (`gopkg.in/yaml.v3`) in the server — replaced with stdlib parser in production.

---

## Design System

**Palette:** Deep Navy + Sky Blue  
**Background:** `#060810` → `#0d1120` → `#131826`  
**Accent:** `#0ea5e9` (sky blue) / `#38bdf8` (light)  
**Text:** `#e8eaf0` / `#8890b0` / `#444e70`  
**Status:** `#10b981` healthy · `#ef4444` live/error · `#f59e0b` warning  

**Typography:** Syne (display/headings) · Instrument Sans (body) · DM Mono (numbers/code/timestamps)

---

## Success Metrics

| Metric | Target |
|---|---|
| Boot to live video | < 2 seconds |
| CPU during 1080p/60fps playback | < 10% on M1 |
| RAM at steady state | < 200 MB (app) + < 30 MB (server) |
| Server playlist response time | < 5ms |
| Link death detection | Within one probe interval (10 min default) |
| Self-healing (dead → backup promoted) | Automatic, within probe cycle |
| Discovery → validated link available | Before kickoff when match is in EPG |

---

## Explicit Non-Goals

- DVR or stream recording
- Multi-user or multi-device (server is localhost only)
- Windows or Linux support
- iOS or iPadOS companion app
- Built-in stream scraping from any specific website
- Hosting, caching, or retransmitting video content
- DRM circumvention
- App Store distribution (sandbox restrictions conflict with AVFoundation custom header use)

---

## Legal Position

NativeStream is a media player and playlist manager. It does not host, cache, proxy (unless the user explicitly enables the optional proxy), or retransmit video content. It occupies the same legal position as VLC or IINA.

The discovery engine reads only publicly accessible web content (public GitHub Gists, public subreddit JSON, public Telegram channel web previews). It does not scrape authenticated content or bypass paywalls.

Users are responsible for ensuring stream sources they configure comply with applicable law and the terms of service of those providers. A first-launch disclaimer makes this explicit.

---

## Distribution

**Server:** Distributed as a notarised binary via a Homebrew tap. `brew install yourname/nativestream/nativestream-server`. Registers as a launchd service for auto-start on login.

**Mac app:** Distributed as a notarised DMG outside the App Store. Drag-to-Applications install.

---

## Versioning

The system is built in four phases, each independently usable:

| Phase | Deliverable | Unlocks |
|---|---|---|
| 1 | Mac app with manual M3U source | Replaces scrcpy/browser, hardware decode |
| 2 | Local Go server with health probing | Self-healing links, single playlist URL |
| 3 | Discovery engine | Auto link finding, no manual hunting |
| 4 | Hardening + UX v4 polish | Production quality, Homebrew distribution |