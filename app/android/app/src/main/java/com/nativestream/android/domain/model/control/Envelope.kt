// app/src/main/java/com/nativestream/android/domain/model/control/Envelope.kt
//
// LMC protocol types — mirrors server/control/protocol.go.
// All WebSocket messages between Android and the Go broker use these types.

package com.nativestream.android.domain.model.control

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
enum class MessageType {
    @SerialName("register")     REGISTER,
    @SerialName("session_list") SESSION_LIST,
    @SerialName("play")         PLAY,
    @SerialName("stop")         STOP,
    @SerialName("pull_back")    PULL_BACK,
    @SerialName("pull_back_ack") PULL_BACK_ACK,
    @SerialName("state_update") STATE_UPDATE,
    @SerialName("volume_set")   VOLUME_SET,
    @SerialName("ping")         PING,
    @SerialName("pong")         PONG,
}

@Serializable
enum class DeviceKind {
    @SerialName("controller") CONTROLLER,
    @SerialName("target")     TARGET,
    @SerialName("tv")         TV,
}

@Serializable
data class Envelope(
    val type:    MessageType,
    val from:    String,
    val to:      String,
    val auth:    String? = null,
    // JsonElement round-trips as a real nested JSON value (object/array/etc.)
    // matching Go's json.RawMessage on the wire, instead of being
    // double-encoded as a JSON string.
    val payload: JsonElement = JsonObject(emptyMap()),
)

@Serializable
data class SessionInfo(
    @SerialName("device_id")    val deviceId:    String,
    val name:                                    String,
    val kind:                                    DeviceKind,
    @SerialName("channel_id")   val channelId:   String,
    @SerialName("channel_name") val channelName: String,
    @SerialName("stream_url")   val streamUrl:   String,
    val playing:                                 Boolean,
    @SerialName("connected_at") val connectedAt: String,
)

// ── Payload types ─────────────────────────────────────────────────────────────

@Serializable
data class RegisterPayload(
    val name: String,
    val kind: DeviceKind,
)

@Serializable
data class PlayPayload(
    @SerialName("channel_id")   val channelId: String,
    @SerialName("channel_name") val channelName: String,
    @SerialName("stream_url")   val streamUrl: String,
)

@Serializable
data class PullBackPayload(
    @SerialName("from_device") val fromDevice: String,
)

@Serializable
data class PullBackAckPayload(
    @SerialName("channel_id")   val channelId: String,
    @SerialName("channel_name") val channelName: String,
    @SerialName("stream_url")   val streamUrl: String,
)

@Serializable
data class StateUpdatePayload(
    @SerialName("channel_id")   val channelId: String,
    @SerialName("channel_name") val channelName: String,
    @SerialName("stream_url")   val streamUrl: String,
    val playing: Boolean,
)

@Serializable
data class VolumeSetPayload(
    val level: Float, // 0.0 – 1.0
)

@Serializable
data class SessionListPayload(
    val sessions: List<SessionInfo>,
)

// ── Envelope helpers ─────────────────────────────────────────────────────────

/**
 * Builds an Envelope with [payload] encoded into a real JsonElement so it
 * matches Go's json.RawMessage on the wire instead of being stringified.
 */
inline fun <reified T> buildEnvelope(
    type: MessageType,
    from: String,
    to: String,
    payload: T,
    auth: String? = null,
): Envelope {
    val element = kotlinx.serialization.json.Json.encodeToJsonElement(
        kotlinx.serialization.serializer<T>(), payload
    )
    return Envelope(type = type, from = from, to = to, auth = auth, payload = element)
}

/**
 * Decodes the Envelope's payload into [T].
 */
inline fun <reified T> Envelope.decodePayload(): T? =
    try {
        kotlinx.serialization.json.Json.decodeFromJsonElement(
            kotlinx.serialization.serializer<T>(), payload
        )
    } catch (e: Exception) {
        null
    }