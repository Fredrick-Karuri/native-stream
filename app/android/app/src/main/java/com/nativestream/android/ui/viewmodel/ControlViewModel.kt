// app/src/main/java/com/nativestream/android/ui/viewmodel/ControlViewModel.kt
//
// Owns the LMC control session lifecycle, processes inbound envelopes,
// and exposes session state + command methods to the UI.
// Controller role only — sends play, stop, pull_back commands.

package com.nativestream.android.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nativestream.android.data.local.SettingsDataStore
import com.nativestream.android.data.remote.ControlDiscoveryService
import com.nativestream.android.data.remote.ControlSession
import com.nativestream.android.domain.model.control.DeviceKind
import com.nativestream.android.domain.model.control.Envelope
import com.nativestream.android.domain.model.control.MessageType
import com.nativestream.android.domain.model.control.PlayPayload
import com.nativestream.android.domain.model.control.PullBackAckPayload
import com.nativestream.android.domain.model.control.PullBackPayload
import com.nativestream.android.domain.model.control.SessionInfo
import com.nativestream.android.domain.model.control.SessionListPayload
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject

private const val DEVICE_ID_KEY = "lmc_device_id"

sealed class PullBackState {
    object Idle      : PullBackState()
    object Requesting: PullBackState()
    data class Ready(val channelId: String, val streamUrl: String) : PullBackState()
}

@HiltViewModel
class ControlViewModel @Inject constructor(
    private val controlSession: ControlSession,
    private val controlDiscovery: ControlDiscoveryService,
    private val settingsDataStore: SettingsDataStore,
) : ViewModel() {

    val connected: StateFlow<Boolean> = controlSession.connected

    private val _sessions = MutableStateFlow<List<SessionInfo>>(emptyList())
    val sessions: StateFlow<List<SessionInfo>> = _sessions

    private val _pullBackState = MutableStateFlow<PullBackState>(PullBackState.Idle)
    val pullBackState: StateFlow<PullBackState> = _pullBackState

    private val json = Json { ignoreUnknownKeys = true }

    private var deviceId: String = ""

    val controlServerUrl: StateFlow<String?> = controlDiscovery.controlServerUrl
    val discoveryScanning: StateFlow<Boolean> = controlDiscovery.scanning

    init {
        viewModelScope.launch { startSession() }
        viewModelScope.launch { observeMessages() }
        viewModelScope.launch { observeDiscovery() }
    }

    // ── Session lifecycle ─────────────────────────────────────────────────────

    private suspend fun startSession() {
        deviceId          = resolveDeviceId()
        val serverUrl     = settingsDataStore.serverUrl.first()
        val deviceName    = android.os.Build.MODEL
        controlSession.connect(serverUrl, deviceId, deviceName)
    }

    private suspend fun resolveDeviceId(): String {
        // Reuse stored ID or generate a stable one
        val stored = settingsDataStore.getControlDeviceId()
        if (stored.isNotBlank()) return stored
        val generated = UUID.randomUUID().toString()
        settingsDataStore.setControlDeviceId(generated)
        return generated
    }

    // ── Inbound message handling ──────────────────────────────────────────────

    private suspend fun observeMessages() {
        controlSession.messages.collect { envelope ->
            when (envelope.type) {
                MessageType.SESSION_LIST  -> handleSessionList(envelope)
                MessageType.PULL_BACK_ACK -> handlePullBackAck(envelope)
                MessageType.PING          -> sendPong(envelope)
                else                      -> Unit
            }
        }
    }

    private suspend fun observeDiscovery() {
        controlDiscovery.controlServerUrl.collect { wsUrl ->
            wsUrl ?: return@collect
            // Discovery found control server — reconnect to confirmed URL
            val httpUrl = wsUrl
                .removePrefix("ws://")
                .let { "http://$it" }
                .removeSuffix("/ws")
            settingsDataStore.setServerUrl(httpUrl)
            controlSession.disconnect()
            controlSession.connect(httpUrl, deviceId, android.os.Build.MODEL)
        }
    }

    fun startDiscovery() = controlDiscovery.scan()
    fun stopDiscovery()  = controlDiscovery.stop()

    private fun handleSessionList(envelope: Envelope) {
        runCatching {
            val payload = json.decodeFromString<SessionListPayload>(envelope.payload)
            // Exclude self and other controllers — show only targets
            _sessions.value = payload.sessions.filter { it.kind != DeviceKind.CONTROLLER }
        }
    }

    private fun handlePullBackAck(envelope: Envelope) {
        runCatching {
            val payload = json.decodeFromString<PullBackAckPayload>(envelope.payload)
            _pullBackState.value = PullBackState.Ready(
                channelId = payload.channelId,
                streamUrl = payload.streamUrl,
            )
        }
    }

    private fun sendPong(pingEnvelope: Envelope) {
        controlSession.send(
            Envelope(
                type    = MessageType.PONG,
                from    = deviceId,
                to      = "server",
                payload = "{}",
            )
        )
    }

    // ── Outbound commands ─────────────────────────────────────────────────────

    fun play(targetDeviceId: String, channelId: String, streamUrl: String) {
        val payload = json.encodeToString(PlayPayload(channelId, streamUrl))
        controlSession.send(
            Envelope(
                type    = MessageType.PLAY,
                from    = deviceId,
                to      = targetDeviceId,
                payload = payload,
            )
        )
    }

    fun stop(targetDeviceId: String) {
        controlSession.send(
            Envelope(
                type    = MessageType.STOP,
                from    = deviceId,
                to      = targetDeviceId,
                payload = "{}",
            )
        )
    }

    fun pullBack(fromDeviceId: String) {
        _pullBackState.value = PullBackState.Requesting
        val payload = json.encodeToString(PullBackPayload(fromDevice = fromDeviceId))
        controlSession.send(
            Envelope(
                type    = MessageType.PULL_BACK,
                from    = deviceId,
                to      = "server",
                payload = payload,
            )
        )
    }

    fun resetPullBackState() {
        _pullBackState.value = PullBackState.Idle
    }

    // ── Targets convenience ───────────────────────────────────────────────────

    val targets: StateFlow<List<SessionInfo>> = _sessions // alias for UI clarity

    override fun onCleared() {
        super.onCleared()
        controlSession.disconnect()
    }
}