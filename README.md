[![Server CI](https://github.com/fredrick-karuri/native-stream/actions/workflows/ci-server.yml/badge.svg)](https://github.com/fredrick-karuri/native-stream/actions/workflows/ci-server.yml)
[![Mac Client CI](https://github.com/fredrick-karuri/native-stream/actions/workflows/ci-macos.yml/badge.svg)](https://github.com/fredrick-karuri/native-stream/actions/workflows/ci-macos.yml)
[![Android Client CI](https://github.com/fredrick-karuri/native-stream/actions/workflows/ci-android.yml/badge.svg)](https://github.com/fredrick-karuri/native-stream/actions/workflows/ci-android.yml)
[![Server Release](https://img.shields.io/github/v/tag/fredrick-karuri/native-stream?filter=server%2Fv*&label=server)](https://github.com/fredrick-karuri/native-stream/releases?q=server)
[![Android Release](https://img.shields.io/github/v/tag/fredrick-karuri/native-stream?filter=android%2Fv*&label=android)](https://github.com/fredrick-karuri/native-stream/releases?q=android)
[![macOS Release](https://img.shields.io/github/v/tag/fredrick-karuri/native-stream?filter=macos%2Fv*&label=macos)](https://github.com/fredrick-karuri/native-stream/releases?q=macos)

# NativeStream

A self-hosted live TV platform for Mac and Android that plays it, or tells you why it can't.

## Why

Live TV fails quietly. Streams expire, degrade, or disappear mid-match with no explanation. Schedules are scattered across apps, players have no sports context, and when something breaks there's no way to know whether it's the stream, your source, or your network. NativeStream exists to remove that guesswork: a server that continuously validates and heals stream links, paired with native clients that surface exactly what's playable right now.

## Who It's For

People running a personal, self-hosted live-sports setup at home — comfortable running a local server on a Mac, who want a native (not browser-based) viewing experience on both macOS and Android, with the two devices aware of each other.

## Experience Promise

Open the app, tap a channel, and it plays. If it can't, the app tells you why instead of leaving you to guess. Start watching on your phone, pull the stream to your Mac (or push it back) without re-finding the channel. The server handles discovery, validation, and self-healing in the background — the client just shows you what's on and what's live.

---

## Components

| Component | Description |
|-----------|-------------|
| [README_SERVER.md](README_SERVER.md) | Go content orchestration server — discovery, validation, EPG, delivery APIs |
| [README_ANDROID.md](README_ANDROID.md) | Android client — EPG-first browsing and playback |
| [README_MAC.md](README_MAC.md) | macOS client — native sports viewing experience |

## Deeper Docs

See [docs/](docs/) for architecture, the full API reference, configuration options, and troubleshooting.