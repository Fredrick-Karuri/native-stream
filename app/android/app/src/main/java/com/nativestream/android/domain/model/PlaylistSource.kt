// app/src/main/java/com/nativestream/android/domain/model/PlaylistSource.kt

package com.nativestream.android.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class PlaylistSource(
    val id: String,
    val name: String,
    val colorHex: String,
    val url: String,
    val channelCount: Int = 0,
    val epgUrl: String? = null,
    /** Refresh interval in hours. 0 = manual refresh only. */
    val refreshIntervalHours: Int = DEFAULT_REFRESH_INTERVAL_HOURS,
) {
    companion object {
        private const val DEFAULT_REFRESH_INTERVAL_HOURS = 6

        /** Sentinel — represents the merged view of all sources. */
        val AllSources = PlaylistSource(
            id           = "",
            name         = "All Sources",
            colorHex     = "",
            url          = "",
            channelCount = 0,
        )
    }
}

/** Convenience — true when this represents the merged/all-sources state. */
val PlaylistSource.isAll: Boolean get() = id.isEmpty()