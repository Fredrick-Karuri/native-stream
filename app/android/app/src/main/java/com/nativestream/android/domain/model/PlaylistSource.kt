// app/src/main/java/com/nativestream/android/domain/model/PlaylistSource.kt
//
// NS-013: PlaylistSource
// Represents a configured M3U playlist source URL with its refresh cadence.
// Used by SettingsDataStore (AND-024) and PlaylistViewModel (AND-007).

package com.nativestream.android.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class PlaylistSource(
    val id: String,
    val name: String,
    val url: String,
    /** Refresh interval in hours. 0 = manual refresh only. */
    val refreshIntervalHours: Int = DEFAULT_REFRESH_INTERVAL_HOURS,
) {
    companion object {
        private const val DEFAULT_REFRESH_INTERVAL_HOURS = 6
    }
}