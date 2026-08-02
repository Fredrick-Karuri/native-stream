// app/src/main/java/com/nativestream/android/domain/model/control/EnvelopeProto.kt
//
// Bridges com.stream.v1.Envelope's opaque payload_json string field to
// typed proto payload messages

package com.nativestream.android.domain.model.control

import com.google.protobuf.Message
import com.google.protobuf.util.JsonFormat
import com.stream.v1.Envelope

private val printer = JsonFormat.printer().preservingProtoFieldNames()

fun <T : Message> buildEnvelope(
    type: com.stream.v1.MessageType,
    from: String,
    to: String,
    payload: T? = null,
): Envelope {
    val builder = Envelope.newBuilder()
        .setType(type)
        .setFrom(from)
        .setTo(to)
    if (payload != null) {
        builder.setPayloadJson(printer.print(payload))
    }
    return builder.build()
}

fun <T : Message> Envelope.decodePayload(builder: Message.Builder): T? =
    try {
        JsonFormat.parser().ignoringUnknownFields().merge(payloadJson, builder)
        @Suppress("UNCHECKED_CAST")
        builder.build() as T
    } catch (e: Exception) {
        null
    }