// app/src/main/java/com/nativestream/android/data/remote/ControlSession.kt
//
// WebSocket client for Local Media Connect control plane.
// Connects to ws://server/ws, registers as controller, and exposes
// inbound envelopes as a SharedFlow for ControlViewModel to consume.
// Reconnects automatically on unexpected close with exponential backoff.

package com.nativestream.android.data.remote

import android.util.Log
import com.nativestream.android.data.local.SettingsDataStore
import com.nativestream.android.domain.model.control.DeviceKind
import com.nativestream.android.domain.model.control.Envelope
import com.nativestream.android.domain.model.control.MessageType
import com.nativestream.android.domain.model.control.RegisterPayload
import com.nativestream.android.domain.model.control.buildEnvelope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG                     = "ControlSession"
private const val PING_INTERVAL_SECONDS   = 30L
private const val RECONNECT_BASE_DELAY_MS = 1_000L
private const val RECONNECT_MAX_DELAY_MS  = 30_000L
private const val NORMAL_CLOSE_CODE       = 1000

@Singleton
class ControlSession @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _messages = MutableSharedFlow<Envelope>(extraBufferCapacity = 64)
    val messages: SharedFlow<Envelope> = _messages

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected

    private var webSocket: WebSocket? = null
    private var explicitDisconnect = false
    private var reconnectDelayMs   = RECONNECT_BASE_DELAY_MS

    private val client = OkHttpClient.Builder()
        .pingInterval(PING_INTERVAL_SECONDS, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    // ── Public API ────────────────────────────────────────────────────────────

    fun connect(serverUrl: String, deviceId: String, deviceName: String) {
        explicitDisconnect = false
        val wsUrl = serverUrl
            .removePrefix("http://")
            .removePrefix("https://")
            .let { "ws://$it/ws" }

        Log.d(TAG, "Connecting to $wsUrl")

        val request = Request.Builder().url(wsUrl).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {

            override fun onOpen(ws: WebSocket, response: Response) {
                _connected.value  = true
                reconnectDelayMs  = RECONNECT_BASE_DELAY_MS
                Log.d(TAG, "WebSocket connected")

                // Register as controller immediately on open
                val envelope = buildEnvelope(
                    type    = MessageType.REGISTER,
                    from    = deviceId,
                    to      = "server",
                    payload = RegisterPayload(name = deviceName, kind = DeviceKind.CONTROLLER),
                )
                ws.send(json.encodeToString(envelope))
            }

            override fun onMessage(ws: WebSocket, text: String) {
                runCatching {
                    val envelope = json.decodeFromString<Envelope>(text)
                    scope.launch { _messages.emit(envelope) }
                }.onFailure {
                    Log.w(TAG, "Failed to parse envelope: $text", it)
                }
            }

            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                ws.close(NORMAL_CLOSE_CODE, null)
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                _connected.value = false
                Log.d(TAG, "WebSocket closed: $code $reason")
                if (!explicitDisconnect) scheduleReconnect(serverUrl, deviceId, deviceName)
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                _connected.value = false
                Log.w(TAG, "WebSocket failure", t)
                if (!explicitDisconnect) scheduleReconnect(serverUrl, deviceId, deviceName)
            }
        })
    }

    fun send(envelope: Envelope) {
        val text = runCatching { json.encodeToString(envelope) }.getOrNull() ?: return
        webSocket?.send(text)
    }

    fun disconnect() {
        explicitDisconnect = true
        webSocket?.close(NORMAL_CLOSE_CODE, "explicit disconnect")
        webSocket      = null
        _connected.value = false
    }

    // ── Reconnect ─────────────────────────────────────────────────────────────

    private fun scheduleReconnect(serverUrl: String, deviceId: String, deviceName: String) {
        scope.launch {
            Log.d(TAG, "Reconnecting in ${reconnectDelayMs}ms")
            delay(reconnectDelayMs)
            reconnectDelayMs = minOf(reconnectDelayMs * 2, RECONNECT_MAX_DELAY_MS)
            connect(serverUrl, deviceId, deviceName)
        }
    }
}