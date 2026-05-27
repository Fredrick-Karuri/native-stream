# NativeStream Mac

Native macOS live sports TV client. Hardware-decoded HLS playback, EPG guide, PiP, AirPlay, and full macOS integration.

---

## Prerequisites

- macOS 14 (Sonoma) or later
- Apple Silicon recommended (M1–M4)
- NativeStream Server running on localhost (see `README_SERVER.md`)
- Xcode 15+ (if building from source)

---

## Installation

### From DMG (recommended)

1. Download `NativeStreamMac.dmg`
2. Open → drag **NativeStream** to `/Applications`
3. Launch — macOS may prompt to allow the app (it is notarised, not App Store)

### From source

```bash
git clone https://github.com/yourname/nativestream.git
open app/macos/NativeStreamMac.xcodeproj
# ⌘R to build and run
```

**Font requirement:** The app uses Syne, Instrument Sans, and DM Mono. Download from Google Fonts and add to the Xcode target, or the app falls back gracefully to system font.

---

## First Launch

The onboarding flow runs once:

**Step 1 — Server check**
Make sure `nativestream-server` is running (`make run-server` or `brew services start nativestream-server`), then click **Check Connection**. If connected, advances automatically.

**Step 2 — Playlist source**
Add your server's playlist URL: `http://localhost:8888/playlist.m3u`
(Settings → Sources → Add Source if you skip this step)

**Step 3 — TV Guide**
Add your EPG URL: `http://localhost:8888/epg.xml`
Public alternative: `https://epghub.xyz`

After onboarding completes it never shows again. Reset it by clearing `onboardingComplete` from UserDefaults if needed.

---

## Interface Overview

### Tab Bar

Three tabs across the top of the window:

| Tab | What it shows |
|---|---|
| ⊞ Browse | Channel grid grouped by sport, searchable, filterable |
| ⚽ Match Day | Live and upcoming matches from EPG, grouped by status |
| 📺 TV Guide | Horizontal EPG grid with 6-hour lookahead and now-line |

### Browse Tab

- **Sport nav rail** (left edge icons) — filter by sport category: Favourites, Football, Rugby, Tennis, Basketball, Cricket, Regions
- **Filter bar** — search box + quick-filter chips (All / Live now / UK / US / 1080p)
- **Channel cards** — logo, name, current programme, progress bar (if live), health indicator

Card states:
- **Default** — dark surface, subtle border
- **Live** — red-tinted border, red LIVE badge
- **Playing** — sky blue border and gradient highlight

Star a channel to pin it to the **Favourites** group at the top.

### Match Day Tab

Two sections auto-populated from EPG:
- **🔴 Live now** — matches currently in progress, with animated pulse dot, score box, and progress bar
- **⏰ Up next** — matches starting within 4 hours, sorted by kick-off time

Card variants: live (red tint), featured Premier League/major league (blue tint), Champions League (steel blue gradient).

Tap any card to open that channel in the player.

### TV Guide Tab

Horizontal timeline EPG grid:
- Channel column pinned on the left (172pt)
- Time axis scrolls horizontally (30-minute slots)
- Red vertical **now-line** marks current time
- Currently-live programmes highlighted in sky blue with bottom progress bar
- Past programmes dimmed to 45% opacity
- Tap any programme cell to switch to that channel

### Player

Full-window view. Appears when you select a channel.

- **Back arrow** — returns to the last Browse/Match Day/TV Guide tab
- **Top bar** — channel name, programme title, LIVE badge, quality badge
- **Score overlay** — when EPG title contains "Home vs Away — Competition" format, score and match minute are shown centred on screen
- **Controls** — appear on mouse hover, auto-hide after 3 seconds
- **Progress bar** — shows live position; "● LIVE" label on left
- **Quality menu** — Auto / 1080p / 720p / 480p (adjusts `preferredPeakBitRate`)
- **PiP button** — floats video above all other windows
- **AirPlay button** — routes to Apple TV or AirPlay 2 receiver

### Mini Player

While a stream is playing and you navigate away from the player, a floating 256pt widget appears in the bottom-right corner:

- Shows stream video area with score (if available) and LIVE badge
- Play/pause, previous/next channel controls
- Programme progress bar
- **Expand button (⤢)** — returns to full player

---

## Settings

Open with **⌘,** or the gear button in the tab bar.

### Sources

Manage M3U playlist sources. Each source shows:
- Health indicator: green (OK), amber (stale/never fetched), red (failed)
- Refresh interval
- Channel count

The primary source should be your server: `http://localhost:8888/playlist.m3u`

### Playback

**Buffer preset** — tradeoff between latency and stability:
- **Low (2s)** — lowest latency; best for live sports
- **Default (8s)** — balanced; good for most connections
- **High (30s)** — most stable; for slow or variable connections

**Hardware Decoding** — always on for Apple Silicon (VideoToolbox). Shows as locked.

### TV Guide

Set your XMLTV EPG URL. The server's EPG endpoint (`/epg.xml`) is recommended — it is match-aware and updates before kick-off.

### Server

The URL the app uses to reach the stream server. Default: `http://localhost:8888`. Change only if running the server on a different machine (not recommended — server is designed for localhost).

### Proxy

Enable only if streams require specific `Referer` or `User-Agent` headers. When enabled, the server proxies all stream requests and injects the configured headers. Configure the headers on the server side in `config.yaml`.

### Discovery

Toggle and status display for the server's auto-discovery engine. Actual discovery source configuration (Gist IDs, subreddits, Telegram channels) is done in `~/.config/nativestream/config.yaml` and takes effect server-side.

---

## macOS Integration

| Feature | How to use |
|---|---|
| Picture-in-Picture | PiP button in player controls, or ⌘⇧P |
| AirPlay | AirPlay button in player controls |
| Media keys | Play/pause key works globally (any app in foreground) |
| Next/previous channel | Next/previous track keys on keyboard |
| Now Playing | Shows in Control Center and lock screen while playing |
| Background audio | Stream audio continues when app is hidden or screen is locked |
| Stage Manager | App participates natively — no special configuration |
| Spaces | Move window between Spaces normally |
| Refresh playlist | ⌘R |

---

## Troubleshooting

**"StreamServer Not Running" screen on launch**
→ Start the server: `make run-server` or `brew services start nativestream-server`
→ Check it responds: `curl http://localhost:8888/api/health`

**No channels shown after connecting**
→ No channels have been added to the server yet. See `README_SERVER.md` → Adding Your First Channel.

**Stream plays in IINA but buffers here**
→ Try switching buffer preset to **High** in Settings → Playback.
→ If still buffering, the upstream source is the problem. Find a different stream URL for that channel.

**EPG not showing for channels**
→ The `tvg-id` in your M3U must match the channel `id` in the XMLTV file. Check the server's channel `tvg_id` field matches what your EPG source uses.
→ If using the server's EPG (`/epg.xml`), it is generated from match schedule APIs and only contains matches for channels that have a `tvg_id` configured. Update via `PUT /api/channels/:id`.

**Score overlay not appearing during a match**
→ The overlay parses the EPG programme title. It must contain "Home vs Away" format. Titles from football-data.org use this format; ESPN titles vary.
→ Verify the programme is showing in the TV Guide tab for that channel.

**PiP not available**
→ PiP requires the stream to be playing. If the stream has not started (buffering or error state), PiP is unavailable.

**AirPlay not showing receivers**
→ Mac and Apple TV must be on the same Wi-Fi network. Check AirPlay is enabled on the Apple TV (Settings → AirPlay and HomeKit).

**App says server connected but channels are 0/0**
→ Server is running but has no channels. Add channels via the API or enable discovery with configured sources.

---

## Keyboard Shortcuts

| Action | Shortcut |
|---|---|
| Settings | ⌘, |
| Refresh playlist | ⌘R |
| Play / Pause | Space |
| Picture-in-Picture | ⌘⇧P |