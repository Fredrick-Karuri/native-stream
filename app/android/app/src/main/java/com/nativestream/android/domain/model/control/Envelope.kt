// app/src/main/java/com/nativestream/android/domain/model/control/Envelope.kt
//
// LMC protocol types — mirrors server/control/protocol.go.
// All WebSocket messages between Android and the Go broker use these types.

package com.nativestream.android.domain.model.control

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class MessageType {
    @SerialName("register")     REGISTER,
    @SerialName("session_list") SESSION_LIST,
    @SerialName("play")         PLAY,
    @SerialName("stop")         STOP,
    @SerialName("pull_back")    PULL_BACK,
    @SerialName("pull_back_ack") PULL_BACK_ACK,
    @SerialName("state_update") STATE_UPDATE,
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
    val payload: String = "{}",
)

@Serializable
data class SessionInfo(
    @SerialName("device_id")    val deviceId:    String,
    val name:                                    String,
    val kind:                                    DeviceKind,
    @SerialName("channel_id")   val channelId:   String,
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
    @SerialName("channel_id") val channelId: String,
    @SerialName("stream_url") val streamUrl: String,
)

@Serializable
data class PullBackPayload(
    @SerialName("from_device") val fromDevice: String,
)

@Serializable
data class PullBackAckPayload(
    @SerialName("channel_id") val channelId: String,
    @SerialName("stream_url") val streamUrl: String,
)

@Serializable
data class StateUpdatePayload(
    @SerialName("channel_id") val channelId: String,
    @SerialName("stream_url") val streamUrl: String,
    val playing: Boolean,
)

@Serializable
data class SessionListPayload(
    val sessions: List<SessionInfo>,
)