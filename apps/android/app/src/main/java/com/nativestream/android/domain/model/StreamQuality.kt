// app/src/main/java/com/nativestream/android/domain/model/StreamQuality.kt
//
// NS-014: StreamQuality
// Represents a detected or available stream quality variant.
// Used by PlayerViewModel (AND-017) to expose quality selection.

package com.nativestream.android.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class StreamQuality(
    val label: String,          // e.g. "HD", "SD", "4K"
    val streamUrl: String,
    val bitrate: Int? = null,   // kbps — null if unknown
    val width: Int?  = null,
    val height: Int? = null,
) {
    /** Short display label including resolution when available, e.g. "HD 1080p". */
    val displayLabel: String get() = when {
        height != null -> "$label ${height}p"
        else           -> label
    }
}