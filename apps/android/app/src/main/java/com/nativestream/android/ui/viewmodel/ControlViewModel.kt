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
import com.nativestream.android.domain.model.control.buildEnvelope
import com.nativestream.android.domain.model.control.decodePayload
import com.stream.v1.DeviceKind
import com.stream.v1.Envelope
import com.stream.v1.MessageType
import com.stream.v1.PlayPayload
import com.stream.v1.PullBackAckPayload
import com.stream.v1.PullBackPayload
import com.stream.v1.SessionInfo
import com.stream.v1.SessionListPayload
import com.stream.v1.VolumeSetPayload
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

sealed class PullBackState {
    object Idle      : PullBackState()
    object Requesting: PullBackState()
    data class Ready(val channelId: String, val channelName: String, val streamUrl: String) : PullBackState()
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

    private val _pullBackReady = MutableSharedFlow<PullBackState.Ready>(
        replay = 0,
        extraBufferCapacity = 1,
    )
    val pullBackReady = _pullBackReady.asSharedFlow()

    private val _isPullingBack = MutableStateFlow(false)
    val isPullingBack: StateFlow<Boolean> = _isPullingBack

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
        val serverUrl     = settingsDataStore.serverUrl.first() ?: return
        val deviceName    = android.os.Build.MODEL
        controlSession.connect(serverUrl, deviceId, deviceName)
    }

    private suspend fun resolveDeviceId(): String {
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
                MessageType.MESSAGE_TYPE_SESSION_LIST  -> handleSessionList(envelope)
                MessageType.MESSAGE_TYPE_PULL_BACK_ACK -> handlePullBackAck(envelope)
                MessageType.MESSAGE_TYPE_PING          -> sendPong(envelope)
                else                                    -> Unit
            }
        }
    }

    private suspend fun observeDiscovery() {
        controlDiscovery.controlServerUrl.collect { wsUrl ->
            wsUrl ?: return@collect
            val httpUrl = wsUrl
                .removePrefix("ws://")
                .let { "http://$it" }
                .removeSuffix("/ws")
            settingsDataStore.setServerUrl(httpUrl)
            controlSession.disconnect()
            controlSession.connect(httpUrl, deviceId, android.os.Build.MODEL)
        }
    }

    fun retryConnection() {
        viewModelScope.launch {
            val serverUrl  = settingsDataStore.serverUrl.first() ?: return@launch
            controlSession.retryNow(serverUrl, deviceId, android.os.Build.MODEL)
        }
    }

    fun startDiscovery() = controlDiscovery.scan()
    fun stopDiscovery()  = controlDiscovery.stop()

    private fun handleSessionList(envelope: Envelope) {
        runCatching {
            val payload: SessionListPayload? = envelope.decodePayload(SessionListPayload.newBuilder())
            _sessions.value = payload?.sessionsList?.filter {
                it.kind != DeviceKind.DEVICE_KIND_CONTROLLER
            } ?: emptyList()
        }
    }

    private fun handlePullBackAck(envelope: Envelope) {
        runCatching {
            val payload: PullBackAckPayload? = envelope.decodePayload(PullBackAckPayload.newBuilder())
            payload ?: return
            _isPullingBack.value = false
            _pullBackReady.tryEmit(PullBackState.Ready(
                channelId   = payload.channelId,
                channelName = payload.channelName,
                streamUrl   = payload.streamUrl,
            ))
        }
    }

    private fun sendPong(pingEnvelope: Envelope) {
        controlSession.send(
            Envelope.newBuilder()
                .setType(MessageType.MESSAGE_TYPE_PONG)
                .setFrom(deviceId)
                .setTo("server")
                .build()
        )
    }

    // ── Outbound commands ─────────────────────────────────────────────────────

    fun play(targetDeviceId: String, channelId: String, channelName: String, streamUrl: String) {
        val payload = PlayPayload.newBuilder()
            .setChannelId(channelId)
            .setChannelName(channelName)
            .setStreamUrl(streamUrl)
            .build()
        controlSession.send(
            buildEnvelope(
                type    = MessageType.MESSAGE_TYPE_PLAY,
                from    = deviceId,
                to      = targetDeviceId,
                payload = payload,
            )
        )
    }

    fun stop(targetDeviceId: String) {
        controlSession.send(
            Envelope.newBuilder()
                .setType(MessageType.MESSAGE_TYPE_STOP)
                .setFrom(deviceId)
                .setTo(targetDeviceId)
                .build()
        )
    }

    fun pullBack(fromDeviceId: String) {
        _isPullingBack.value = true
        val payload = PullBackPayload.newBuilder().setFromDevice(fromDeviceId).build()
        controlSession.send(
            buildEnvelope(
                type    = MessageType.MESSAGE_TYPE_PULL_BACK,
                from    = deviceId,
                to      = "server",
                payload = payload,
            )
        )
    }

    fun setVolume(targetDeviceId: String, level: Float) {
        val payload = VolumeSetPayload.newBuilder().setLevel(level.toDouble()).build()
        controlSession.send(
            buildEnvelope(
                type    = MessageType.MESSAGE_TYPE_VOLUME_SET,
                from    = deviceId,
                to      = targetDeviceId,
                payload = payload,
            )
        )
    }

    // ── Targets convenience ───────────────────────────────────────────────────

    val targets: StateFlow<List<SessionInfo>> = _sessions

    override fun onCleared() {
        super.onCleared()
        controlSession.disconnect()
    }
}