# NativeStream for Mac

A native sports viewing experience built for macOS.

NativeStream for Mac brings live sports, schedules, and channels together in a single experience designed around the way fans follow matches. Watch with native playback, continue viewing with Picture-in-Picture, cast with AirPlay, and control playback using familiar macOS features.

---

# Features

## Sports-first browsing

Discover and organize content through an experience designed for live sports:

- Browse channels grouped by sport and category
- Search and filter available content
- Favorite frequently watched channels
- See live indicators and current programme information

---

## Match Day

Stay on top of what matters most:

- Follow live and upcoming matches
- View match context, schedules, and scores when available
- Jump directly into live streams

---

## TV Guide

Explore sports schedules with a native guide experience:

- Browse current and upcoming programmes
- Navigate an interactive timeline
- Switch channels directly from the guide

---

## Native Player

Watch sports with a playback experience built specifically for macOS:

- Hardware-accelerated video playback
- Full-window immersive viewing
- Picture-in-Picture support
- AirPlay support
- Quality selection
- Live programme information and score overlays
- Media key integration

---

## Mini Player

Keep watching while exploring the app:

- Floating player while browsing other sections
- Quick playback controls
- Current programme information
- One-click return to the full player

---

## Deep macOS Integration

NativeStream feels at home on macOS.

| Feature | Experience |
|---|---|
| Picture-in-Picture | Continue watching above other apps |
| AirPlay | Send streams to supported receivers |
| Media Keys | Control playback from your keyboard |
| Now Playing | View and control streams from Control Center |
| Background Audio | Continue listening while the app is in the background |
| Stage Manager | Works naturally with modern macOS window management |
| Spaces | Move and manage the app across desktop spaces |

---

# Installation

## Download DMG (Recommended)

1. Download `NativeStreamMac.dmg`
2. Drag **NativeStream** into the Applications folder
3. Launch the application

The application is distributed as a notarized macOS application and runs outside the Mac App Store to support the advanced networking and playback capabilities required for a high-quality sports viewing experience.

---

## Build from Source

Requirements:

- macOS 14 or later
- Xcode 15 or later

```bash
git clone https://github.com/yourname/nativestream.git
cd nativestream
open app/macos/NativeStreamMac.xcodeproj
````

Press `⌘R` in Xcode to build and run.

---

# Getting Started

1. Launch NativeStream for Mac
2. Connect to your NativeStream Server
3. Sync your available channels, schedules, and playback configuration
4. Start watching

Advanced source configuration and server setup are available in the server documentation.

---

# Settings

Customize your viewing experience.

## Sources

Manage available content sources and monitor their availability.

## Playback

Configure playback preferences including buffering behavior and stream quality.

## TV Guide

Configure programme guide preferences and available schedule sources.

## Server

Manage the connection to your NativeStream Server.

## Advanced

Configure additional network and playback options when required.

---

# Keyboard Shortcuts

| Action             | Shortcut |
| ------------------ | -------- |
| Open Settings      | ⌘,       |
| Refresh Content    | ⌘R       |
| Play / Pause       | Space    |
| Picture-in-Picture | P      |

---

# Troubleshooting

Common issues:

### Unable to connect to the server

Ensure your NativeStream Server is running and reachable from the application.

### No content available

Verify that content sources have been configured and synchronized.

### Playback issues

Check your network connection or adjust playback settings for improved stability.

### Missing schedule information

Verify that programme guide data is available for the selected content.

For advanced diagnostics and troubleshooting, see `docs/TROUBLESHOOTING.md`.

---

# Development

Additional documentation:

* `docs/UI.md` — interface behavior and detailed interactions
* `docs/TROUBLESHOOTING.md` — advanced diagnostics
* `docs/SYSTEM_DESIGN.md` — architecture and data flow