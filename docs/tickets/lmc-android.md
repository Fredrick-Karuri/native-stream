# NativeStream Android — Local Media Connect Tickets

**Platform:** Android 8.0+ (API 26+)
**Stack:** Jetpack Compose · Hilt · OkHttp WebSocket · NsdManager
**Feature:** Controller role — discover targets, send play/stop/pull-back commands
**Last Updated:** 2026-06-11
**Role:** Android phone acts as controller. Pull-back brings stream to phone.

---

## How to Read This

- **ID** — `LMC-AND-001` etc.
- **Effort** — S (< 2hrs), M (half day), L (full day)
- **Needs** — files to read before starting
- **Done when** — observable acceptance criteria

---

## Dependency Order

```
LMC-AND-001   ControlSession (WebSocket client)          ← unblocks everything
LMC-AND-002   ControlViewModel                           ← unblocks 003, 004
LMC-AND-003   Device discovery (NsdManager ctrl service) ← unblocks 004
LMC-AND-004   Connect UI — session sheet                 ← unblocks 005
LMC-AND-005   Pull-back — transfer stream to phone       ← closes epic
```

---

## Summary

| Epic | Tickets | Effort |
|---|---|---|
| LMC-AND-E01 Core | 001–002 | ~1 day |
| LMC-AND-E02 UI | 003–005 | ~1 day |
| **Total** | **5 tickets** | **~2 days** |

---

## LMC-AND-E01 — Core

### LMC-AND-001 · ControlSession (WebSocket client) · M

**Needs:**
- `LMC-SRV-001` (protocol)
- `ApiClient.kt`

**Description:**
`@Singleton` WebSocket client that connects to `ws://server:8888/ws`,
registers the device, and exposes incoming messages as a `SharedFlow`.
Uses OkHttp WebSocket (already a transitive dependency via Ktor).

```kotlin
@Singleton
class ControlSession @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
) {
    private val _messages = MutableSharedFlow<Envelope>()
    val messages: SharedFlow<Envelope> = _messages

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected

    private var ws: WebSocket? = null
    private val client = OkHttpClient.Builder()
        .pingInterval(30, TimeUnit.SECONDS)
        .build()

    fun connect(serverUrl: String, deviceName: String) { ... }
    fun send(envelope: Envelope) { ... }
    fun disconnect() { ... }
}
```

**Registration flow on connect:**
```kotlin
// on WebSocket open:
send(Envelope(
    type    = MsgType.REGISTER,
    from    = deviceId,   // stable UUID stored in DataStore
    to      = "server",
    payload = RegisterPayload(name = deviceName, kind = "controller")
))
```

**Reconnect:** exponential backoff (1s, 2s, 4s, max 30s) on unexpected close.
Do not reconnect on explicit `disconnect()` call.

**Done when:**
- `ControlSession.connect()` establishes WebSocket and sends `register`
- `messages` flow emits every inbound `Envelope`
- `connected` flips `true` on open, `false` on close
- Reconnects automatically after network interruption
- No crash on `disconnect()` called before `connect()`

---

### LMC-AND-002 · ControlViewModel · M

**Needs:**
- `LMC-AND-001`
- `LMC-SRV-001` (message types)
- `PlayerViewModel.kt`

**Description:**
`@HiltViewModel` that owns the control session lifecycle, exposes session
list, and provides command methods for the UI.

```kotlin
@HiltViewModel
class ControlViewModel @Inject constructor(
    private val controlSession: ControlSession,
    private val settingsDataStore: SettingsDataStore,
) : ViewModel() {

    val connected: StateFlow<Boolean> = controlSession.connected
    
    private val _sessions = MutableStateFlow<List<SessionInfo>>(emptyList())
    val sessions: StateFlow<List<SessionInfo>> = _sessions

    private val _pullBackState = MutableStateFlow<PullBackState>(PullBackState.Idle)
    val pullBackState: StateFlow<PullBackState> = _pullBackState

    init {
        viewModelScope.launch {
            controlSession.messages.collect { envelope ->
                when (envelope.type) {
                    MsgType.SESSION_LIST -> handleSessionList(envelope)
                    MsgType.PULL_BACK_ACK -> handlePullBackAck(envelope)
                    else -> {}
                }
            }
        }
    }

    fun connect() { ... }
    fun play(targetDeviceId: String, channelId: String, streamUrl: String) { ... }
    fun stop(targetDeviceId: String) { ... }
    fun pullBack(fromDeviceId: String) { ... }
}

sealed class PullBackState {
    object Idle : PullBackState()
    object Requesting : PullBackState()
    data class Ready(val channelId: String, val streamUrl: String) : PullBackState()
}
```

**Done when:**
- `sessions` updates whenever `session_list` message arrives
- `play()` sends correct `Envelope` to target device
- `stop()` sends correct `Envelope` to target device
- `pullBack()` sends `pull_back` and `pullBackState` transitions
  `Idle → Requesting → Ready` on `pull_back_ack`
- ViewModel connects session on init, disconnects on `onCleared()`

---

## LMC-AND-E02 — UI

### LMC-AND-003 · Device discovery (NsdManager ctrl service) · S

**Needs:**
- `ServerDiscoveryService.kt` (existing pattern)
- `LMC-AND-001`

**Description:**
Extend `ServerDiscoveryService` or create a parallel
`ControlDiscoveryService` that scans for `_nativestream-ctrl._tcp` and
extracts the WebSocket URL from the TXT record (`ws=/ws`).

On resolve:
```kotlin
val wsUrl = "ws://${host}:${port}${txtRecord["ws"] ?: "/ws"}"
```

Expose `controlServerUrl: StateFlow<String?>` — consumed by
`ControlViewModel.connect()`.

**Done when:**
- Service emits WebSocket URL within 5s when server is running
- TXT record `ws` key used for path — not hardcoded
- Null when no control service found

---

### LMC-AND-004 · Connect UI — session sheet · M

**Needs:**
- `LMC-AND-002`
- `LMC-AND-003`
- Design system (`NSColors`, `NSType`, `PhosphorIcons`)

**Description:**
Bottom sheet accessible from the Now screen (or player controls) showing
connected devices and allowing the user to cast or pull back.

**Sheet states:**

**Scanning:**
```
[Cast to a device]
Scanning your network…  ← ProgressIndicator
```

**Devices found:**
```
[Cast to a device]

● NativeStream @ Fredrick's Mac    [Play here]
  Playing: Arsenal vs Tottenham

● NativeStream @ Living Room TV    [Play here]
  Idle
```

**Playing on target:**
```
● NativeStream @ Fredrick's Mac    [Pull Back]  [Stop]
  Playing: Arsenal vs Tottenham · 42:17
```

Entry point: add a cast icon button to `PlayerControls` and/or `NowScreen`
top bar. Use `PhosphorIcons.Regular.CastTv` or similar.

**Done when:**
- Sheet shows within 1s of open on same network as server
- "Play here" sends `play` command with current channel to target
- "Stop" sends `stop` command
- "Pull Back" visible when target is playing
- Sheet dismisses after successful command

---

### LMC-AND-005 · Pull-back — transfer stream to phone · S

**Needs:**
- `LMC-AND-002` (`pullBackState`)
- `PlayerViewModel.kt`

**Description:**
When `pullBackState` transitions to `Ready`, automatically start local
playback on the phone using the `channelId` and `streamUrl` from
`pull_back_ack`.

```kotlin
// in ControlViewModel or session sheet composable
LaunchedEffect(pullBackState) {
    if (pullBackState is PullBackState.Ready) {
        playerViewModel.playFromRemote(
            channelId = pullBackState.channelId,
            streamUrl = pullBackState.streamUrl,
        )
    }
}
```

Add `playFromRemote(channelId, streamUrl)` to `PlayerViewModel` —
resolves channel from `channelId`, sets stream URL, starts playback.

Show a brief "Transferred to this device" snackbar on success.

**Done when:**
- Tapping "Pull Back" in the sheet starts playback on phone within 3s
- Player shows correct channel name and EPG data after pull-back
- "Transferred to this device" snackbar shown
- Target device stops playing after pull-back (server sends `stop` to target)