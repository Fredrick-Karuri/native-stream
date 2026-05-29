// app/src/main/java/com/nativestream/android/domain/model/Channel.kt
//
// NS-011: Channel
// Core data model representing a single TV channel in the playlist.
// Mirrors Channel.swift exactly — including id derivation logic and
// streamHeaders for Referer/User-Agent injection (AND-018).

package com.nativestream.android.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Channel(
    val id: String,
    val tvgId: String,
    val name: String,
    val groupTitle: String,
    val logoUrl: String?,           // serialised as String; convert to Uri at use-site
    val streamUrl: String,          // serialised as String; convert to Uri at use-site
    val streamHeaders: Map<String, String>,
) {
    companion object {
        private const val FALLBACK_GROUP = "Uncategorised"

        /**
         * Primary constructor matching Channel.swift init — derives [id] from
         * [tvgId] when present, otherwise falls back to [streamUrl].
         */
        fun create(
            tvgId: String,
            name: String,
            groupTitle: String = FALLBACK_GROUP,
            logoUrl: String? = null,
            streamUrl: String,
            streamHeaders: Map<String, String> = emptyMap(),
        ): Channel = Channel(
            id            = tvgId.ifEmpty { streamUrl },
            tvgId         = tvgId,
            name          = name,
            groupTitle    = groupTitle,
            logoUrl       = logoUrl,
            streamUrl     = streamUrl,
            streamHeaders = streamHeaders,
        )
    }

    // Equality and hashing keyed on id only — mirrors Swift Hashable impl
    override fun equals(other: Any?): Boolean =
        other is Channel && other.id == id

    override fun hashCode(): Int = id.hashCode()
}