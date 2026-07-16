# Local Media Connect — Design

**Stack:** Go 1.22+ · Jetpack Compose (Android) · SwiftUI (macOS) · WebSocket · mDNS/DNS-SD

For the wire protocol (message envelope, types, mDNS record), see [api.md](api.md#local-media-connect-websocket-control-plane). For where this fits in the overall system, see [architecture.md](architecture.md).

---

## Problem Statement

NativeStream users run a self-hosted media server on their home network and watch live TV on multiple devices — an Android phone and a macOS desktop client. Today, these devices are completely independent: there is no way to control one from another, transfer a stream between them, or even know what the other is playing.

This creates friction in natural multi-device scenarios:

- You start watching a match on your Mac, then want to continue on your phone while moving away from your desk. You have to manually find the channel on the phone.
- You pick up your phone and want to push a match to your Mac for a better viewing experience. There is no mechanism to do this.
- There is no visibility into what other devices on the network are playing.

If we don't solve this, NativeStream remains a single-screen experience despite the user having a capable multi-device home setup.

---

## Goals

- **G1** — A controller device (Android phone) can discover target devices (Mac) on the local network without configuration.
- **G2** — Controller can instruct a target to play or stop a specific channel.
- **G3** — Controller can pull an active stream from a target back to itself ("Pull-Back") in under 3 seconds.
- **G4** — All devices see real-time session state (who is playing what).
- **G5** — Protocol is TV-client-compatible by design, even though TV is not implemented in this iteration.
- **G6** — Zero configuration for the user. Works on existing local network setup.

---

## Non-Goals (Explicit Out of Scope)

- **Smart TV client implementation** — protocol is TV-compatible but no TV client is built in this iteration.
- **Cloud brokering** — all communication is local network only. No cloud relay.
- **Authentication / pairing** — zero-auth in this iteration. Auth field is present in the protocol envelope for future upgrade but not enforced.
- **Seek / DVR control** — live TV has no seek position. Stream transfer means "play this channel", not "resume at this timestamp".
- **Multi-server scenarios** — one Go server per local network. Cross-server discovery is out of scope.
- **Android as target** — Android is controller-only in this iteration. Pull-back brings the stream to Android; it does not accept remote play commands.
- **iOS client** — not in scope for this iteration.

---

## Proposed Solution

Introduce a WebSocket-based control plane brokered through the existing Go server. Devices connect to a `/ws` endpoint on the server, register themselves with a name and role (`controller` or `target`), and exchange typed JSON command messages routed by the server hub.

Device discovery uses mDNS — a second service type `_nativestream-ctrl._tcp` is advertised alongside the existing `_nativestream._tcp` media service. Controllers scan for this service to find the WebSocket endpoint.

The Go server acts as the message broker. Devices never connect peer-to-peer. This keeps the protocol simple, avoids NAT traversal complexity, and gives the server a complete view of session state — which is what makes Pull-Back possible.

---

### Key Design Decisions

| Decision | Option Chosen | Reason | Alternatives Rejected |
|---|---|---|---|
| Broker topology | Server-brokered WebSocket | Server already has complete session state; simpler than P2P NAT traversal; single source of truth | Peer-to-peer WebRTC data channel — too complex, overkill for LAN |
| Transport | WebSocket over HTTP | Persistent, bidirectional, works over existing HTTP server; no new port needed | raw TCP socket — no framing; SSE — unidirectional only |
| Discovery | mDNS `_nativestream-ctrl._tcp` | Already using zeroconf for media discovery; reuses existing infrastructure; TXT record carries WS path for TV compatibility | Manual IP entry — defeats zero-config goal; UDP broadcast — less reliable, not mDNS-compatible |
| Auth model | Zero-auth now, envelope-ready for upgrade | Home network trust model; `auth` field present in every envelope so pairing can be added without protocol change | Token auth now — adds friction with no security benefit on a trusted LAN |
| Pull-back mechanism | Server reads target's last `state_update`, sends `pull_back_ack` to controller | Server is source of truth; no direct device-to-device communication needed | Controller asks target directly — requires P2P connection |
| Android role | Controller only (not target) | Phone is the natural remote; adding target role on Android requires background foreground service — complexity not justified yet | Full bidirectional — deferred |
| macOS role | Target only (not controller) | Mac is the natural screen; controlling other devices from Mac is a less common pattern | Full bidirectional — deferred |
| TV compatibility | Protocol contract TV-safe | TXT record carries `ws=/ws` path; `kind` field in register supports `"tv"` as a future value | TV-specific protocol — would require migration later |

---

## Architecture / Component Overview

```
┌─────────────────────────────────────────────────────────────┐
│                    Local Network                            │
│                                                             │
│  ┌─────────────┐   WebSocket    ┌──────────────────────┐   │
│  │ Android     │◄──────────────►│  Go Server           │   │
│  │ (Controller)│                │                      │   │
│  └─────────────┘                │  ┌────────────────┐  │   │
│                                 │  │  WebSocket Hub │  │   │
│  ┌─────────────┐   WebSocket    │  │  (broker)      │  │   │
│  │ macOS       │◄──────────────►│  │                │  │   │
│  │ (Target)    │                │  │  Session       │  │   │
│  └─────────────┘                │  │  Registry      │  │   │
│                                 │  └────────────────┘  │   │
│  ┌─────────────┐   WebSocket    │                      │   │
│  │ TV (future) │◄──────────────►│  /ws endpoint        │   │
│  │ (Target)    │                │  /api/sessions       │   │
│  └─────────────┘                └──────────────────────┘   │
│                                          ▲                  │
│                          mDNS            │                  │
│                  _nativestream-ctrl._tcp │                  │
│                          ▼              │                  │
│                  All devices discover   │                  │
│                  WebSocket endpoint     │                  │
└─────────────────────────────────────────────────────────────┘
```

### Component Responsibilities

**Go Server — WebSocket Hub (`control/hub.go`)**
- Accepts WebSocket connections at `GET /ws`
- Maintains session registry: `device_id → SessionInfo`
- Routes `Envelope` by `to` field (unicast or broadcast)
- Handles `pull_back`: reads target's `state_update`, sends `pull_back_ack` to controller, sends `stop` to target
- Broadcasts `session_list` on any session change
- Pings all clients every 30s; removes unresponsive clients after 2 missed pongs

**Android — `ControlSession` + `ControlViewModel`**
- Scans for `_nativestream-ctrl._tcp` via `NsdManager`
- Connects WebSocket, registers as `controller`
- Sends `play`, `stop`, `pull_back` commands
- On `pull_back_ack`: calls `PlayerViewModel.playFromRemote()`

**macOS — `ControlSession` + `ControlViewModel`**
- Connects WebSocket on app launch, registers as `target`
- Receives `play` command → resolves channel → calls `PlayerViewModel.play()`
- Receives `stop` command → calls `PlayerViewModel.stop()`
- Broadcasts `state_update` on every playback state change
- Shows subtle UI indicator when a controller is connected

---

## Data Model

### SessionInfo
```go
type SessionInfo struct {
    DeviceID    string    `json:"device_id"`
    Name        string    `json:"name"`
    Kind        string    `json:"kind"`         // "controller" | "target" | "tv" (future)
    ChannelID   string    `json:"channel_id"`   // "" if idle
    StreamURL   string    `json:"stream_url"`   // last known stream URL
    Playing     bool      `json:"playing"`
    ConnectedAt time.Time `json:"connected_at"`
}
```

### Device Identity
Each device generates a stable UUID on first launch and persists it locally (`UserDefaults` on macOS, `DataStore` on Android). This UUID is the `device_id` in all messages.

---

## Pull-Back Flow

```
Phone                    Server                   Mac
  │                         │                      │
  │  pull_back              │                      │
  │  { from_device: mac_id }│                      │
  │────────────────────────►│                      │
  │                         │ read sessions[mac_id] │
  │                         │ .channel_id           │
  │                         │ .stream_url           │
  │                         │                      │
  │  pull_back_ack          │                      │
  │  { channel_id,          │                      │
  │    stream_url }         │                      │
  │◄────────────────────────│                      │
  │                         │  stop                │
  │                         │────────────────────►│
  │                         │                      │
  │ playFromRemote()        │                      │ stop()
  │ (local playback starts) │                      │
```

---

## Success Metrics

| Metric | Target |
|---|---|
| Time from "Pull Back" tap to phone playback start | ≤ 3s on local network |
| Time from "Play here" tap to Mac playback start | ≤ 2s on local network |
| Device discovery time after app launch | ≤ 5s |
| Session list accuracy after device disconnect | ≤ 60s (ping timeout) |
| Protocol envelope backward compatibility | All v1 messages parseable by future TV client |

---

## Risks & Open Questions

| Risk / Question | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Android WebSocket killed by Doze/battery optimization | Medium | High | Use `PARTIAL_WAKE_LOCK` in controller session; reconnect on resume |
| `stream_url` in `state_update` is a proxied server URL not playable on phone | Medium | High | Server resolves direct stream URL from `channel_id` if proxy URL detected in pull_back_ack |
| Two controllers send conflicting `play` commands simultaneously | Low | Medium | Last-write-wins; server routes both; target plays last received |
| Mac registers before server WebSocket endpoint is ready | Low | Low | Client reconnect backoff handles this |
| mDNS not working on some corporate/guest WiFi networks | Medium | Medium | Fall back to manual WebSocket URL entry in Settings |
| Device UUID collision | Very Low | Low | UUID v4 collision probability negligible |

---

## Out of Scope (Parking Lot)

- **Android as target** — accept `play` commands on Android. Deferred pending foreground service UX decisions.
- **Smart TV client** — protocol is TV-compatible but client implementation deferred.
- **Pairing / token auth** — `auth` field present in envelope; enforcement deferred.
- **Volume / mute control** — additional command types for remote volume. Deferred.
- **Queue management** — controller queues multiple channels on target. Deferred.
- **Cross-server** — discovery and control across multiple Go server instances. Deferred.
- **iOS controller** — same pattern as Android controller. Deferred pending iOS client.
- **Playback position sync for VOD** — not applicable to live TV; relevant if VOD is added later.