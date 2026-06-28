# NativeStream macOS — Local Media Connect Tickets

**Platform:** macOS 13+ (Ventura+)
**Stack:** SwiftUI · Observation · URLSessionWebSocketTask · Network.framework
**Feature:** Target role — receives play/stop commands, reports state, supports pull-back
**Last Updated:** 2026-06-11
**Role:** Mac acts as the target (renderer). Phone controls it.

---

## How to Read This

- **ID** — `LMC-MAC-001` etc.
- **Effort** — S (< 2hrs), M (half day), L (full day)
- **Needs** — files to read before starting
- **Done when** — observable acceptance criteria

---

## Dependency Order

```
LMC-MAC-001   ControlSession (WebSocket client)         ← unblocks everything
LMC-MAC-002   ControlViewModel                          ← unblocks 003, 004
LMC-MAC-003   State reporting (state_update broadcasts) ← unblocks pull-back
LMC-MAC-004   Pull-back — transfer stream to phone      ← unblocks 005
LMC-MAC-005   Connect UI — session indicator            ← closes epic
```

---

## Summary

| Epic | Tickets | Effort |
|---|---|---|
| LMC-MAC-E01 Core | 001–002 | ~1 day |
| LMC-MAC-E02 State & Transfer | 003–004 | ~0.5 days |
| LMC-MAC-E03 UI | 005 | ~0.5 days |
| **Total** | **5 tickets** | **~2 days** |

---

## LMC-MAC-E01 — Core

### LMC-MAC-001 · ControlSession (WebSocket client) · M

**Needs:**
- `LMC-SRV-001` (protocol)
- `APIClient.swift`
- `SettingsStore.swift`

**Description:**
`@Observable` WebSocket client using `URLSessionWebSocketTask` that
connects to `ws://server:8888/ws`, registers the Mac as a target, and
exposes incoming messages via `AsyncStream`.

```swift
@Observable
@MainActor
final class ControlSession {
    var connected = false
    private(set) var incomingMessages: AsyncStream<Envelope>!
    private var continuation: AsyncStream<Envelope>.Continuation?
    private var task: URLSessionWebSocketTask?

    func connect(serverURL: URL, deviceName: String) async { ... }
    func send(_ envelope: Envelope) async { ... }
    func disconnect() { ... }
    private func receive() async { ... } // recursive receive loop
    private func scheduleReconnect() { ... } // exponential backoff
}
```

**Registration on connect:**
```swift
await send(Envelope(
    type:    .register,
    from:    deviceID,   // stable UUID from UserDefaults
    to:      "server",
    payload: RegisterPayload(name: Host.current().localizedName ?? "Mac", kind: "target")
))
```

**Reconnect:** exponential backoff (1s, 2s, 4s, max 30s) on unexpected close.

**Done when:**
- `connect()` establishes WebSocket and sends `register`
- `incomingMessages` stream emits every inbound `Envelope`
- `connected` flips on open/close
- Reconnects automatically after server restart
- `disconnect()` is idempotent

---

### LMC-MAC-002 · ControlViewModel · M

**Needs:**
- `LMC-MAC-001`
- `LMC-SRV-001`
- `PlayerViewModel.swift`

**Description:**
`@Observable` ViewModel that owns session lifecycle, processes inbound
commands, and dispatches to `PlayerViewModel`.

```swift
@Observable
@MainActor
final class ControlViewModel {
    var sessions: [SessionInfo] = []
    var connected: Bool = false

    private let controlSession: ControlSession
    private let playerVM: PlayerViewModel

    func start(serverURL: URL) async {
        await controlSession.connect(serverURL: serverURL, deviceName: hostName)
        for await envelope in controlSession.incomingMessages {
            await handle(envelope)
        }
    }

    private func handle(_ envelope: Envelope) async {
        switch envelope.type {
        case .play:         await handlePlay(envelope)
        case .stop:         handleStop()
        case .sessionList:  handleSessionList(envelope)
        case .pullBack:     await handlePullBack(envelope)
        case .ping:         await controlSession.send(pong(envelope))
        default:            break
        }
    }
}
```

**`handlePlay`:** decode `PlayPayload`, resolve channel from `channelId`
via `APIClient.shared.getChannel(id:)`, call `playerVM.play(channel:)`.

**`handleStop`:** call `playerVM.stop()`.

**Done when:**
- `play` command from phone starts playback on Mac within 2s
- `stop` command stops playback
- `session_list` updates `sessions` array
- Unknown message types are ignored without crash

---

## LMC-MAC-E02 — State & Transfer

### LMC-MAC-003 · State reporting (state_update broadcasts) · S

**Needs:**
- `LMC-MAC-002`
- `PlayerViewModel.swift`

**Description:**
Mac broadcasts `state_update` to the server whenever playback state
changes so the server's session registry stays accurate. This is what
enables pull-back — the server knows what the Mac is playing.

Observe `playerVM.currentChannel` and `playerVM.isPlaying`:

```swift
.onChange(of: playerVM.currentChannel) { _, channel in
    Task {
        await controlSession.send(Envelope(
            type: .stateUpdate,
            from: deviceID,
            to:   "broadcast",
            payload: StateUpdatePayload(
                channelID: channel?.id ?? "",
                streamURL: channel?.streamURL?.absoluteString ?? "",
                playing:   playerVM.isPlaying
            )
        ))
    }
}
```

**Done when:**
- `state_update` sent within 1s of playback start/stop/channel change
- `channel_id` and `stream_url` accurate in the broadcast
- `playing: false` broadcast on `playerVM.stop()`

---

### LMC-MAC-004 · Pull-back handler · S

**Needs:**
- `LMC-MAC-003`
- `LMC-SRV-001` (`pull_back_ack`)

**Description:**
When the server routes a `pull_back` command to the Mac (because the
phone requested the Mac's stream), the Mac does not need to act — the
server reads the Mac's last `state_update` and sends `pull_back_ack`
directly to the controller.

However, the Mac should stop playing after pull-back is acknowledged
so both devices don't play simultaneously. The server sends a `stop`
command to the Mac after routing `pull_back_ack` to the controller.

Mac-side handling is therefore just `handleStop()` — already implemented
in `LMC-MAC-002`. No additional code needed.

**Verify in `handlePlay` that stream URL is included in `state_update`**
so the server has it available for `pull_back_ack`. This is the only
requirement beyond `LMC-MAC-003`.

**Done when:**
- Phone taps "Pull Back" → Mac stops playing within 3s
- Phone starts playing the same channel within 3s of pull-back
- No double-play state (both devices playing simultaneously)

---

## LMC-MAC-E03 — UI

### LMC-MAC-005 · Connect UI — session indicator · M

**Needs:**
- `LMC-MAC-002`
- `AppShell.swift`
- Design system (`NS` tokens)

**Description:**
Subtle session indicator in the macOS UI showing when a controller
is connected and has sent commands. Not a full sheet — a status
indicator in the sidebar or top bar is sufficient since the Mac is
the passive target.

**Indicator states:**

**No controller connected:**
Nothing shown — no clutter when not in use.

**Controller connected (idle):**
```
● Phone connected     ← small dot + label in sidebar footer
```

**Controller active (sent play command):**
```
📱 Playing via Fredrick's iPhone
```
Shown as a dismissable banner at the top of the content area,
similar to `ServerUnreachableBanner` pattern.

**Implementation:**
- Add `ControlViewModel` to `AppShell` environment
- Observe `controlVM.sessions` for controllers (`kind == "controller"`)
- Show indicator when any controller is connected
- Show banner when `playerVM.currentChannel` was set via a `play` command
  (track with a `@State var playedViaRemote: Bool` flag in `ControlViewModel`)

**Done when:**
- No UI shown when no controller connected
- Subtle indicator shown when controller is connected
- Banner shown when Mac is playing a channel via remote command
- Banner dismisses when playback stops or player is closed