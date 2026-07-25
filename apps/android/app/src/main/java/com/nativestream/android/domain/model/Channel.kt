// app/src/main/java/com/nativestream/android/domain/model/Channel.kt
//
// Channel
// Core data model representing a single TV channel in the playlist.

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
    val sourceId: String = "",   // populated by M3uParser
    val subGroupTitle: String = "",   // e.g. league name within a group
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
            sourceId: String = "",
        ): Channel = Channel(
            id            = "${sourceId}_${tvgId.ifEmpty { streamUrl }}",
            tvgId         = tvgId,
            name          = name,
            groupTitle    = groupTitle,
            logoUrl       = logoUrl,
            streamUrl     = streamUrl,
            streamHeaders = streamHeaders,
        )
    }

    // Equality and hashing keyed on id only
    override fun equals(other: Any?): Boolean =
        other is Channel && other.id == id

    override fun hashCode(): Int = id.hashCode()
}