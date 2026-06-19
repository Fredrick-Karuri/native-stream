// app/src/main/java/com/nativestream/android/domain/model/Programme.kt
//
// Programme model
// Represents a single EPG programme entry linked to a channel.
// including computed properties for
// progress, display strings, isNow, and sport matching.

package com.nativestream.android.domain.model

import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Serializable
data class Programme(
    val channelId: String,
    val title: String,
    val startEpochMs: Long,     // stored as epoch millis; Date at use-site
    val stopEpochMs: Long,
) {
    // ── Identity — mirrors Programme.id in updated Swift spec ────────────────
    val id: String get() = "${channelId}_$startEpochMs"

    // ── Convenience accessors ─────────────────────────────────────────────────

    val startDate: Date get() = Date(startEpochMs)
    val stopDate:  Date get() = Date(stopEpochMs)

    // ── Computed properties mirroring Programme.swift ─────────────────────────

    /**
     * Elapsed fraction of the programme duration, clamped 0–1.
     * Returns 0 before start, 1 after stop.
     */
    val progress: Double get() {
        val nowMs    = System.currentTimeMillis()
        if (nowMs < startEpochMs) return 0.0
        if (nowMs >= stopEpochMs) return 1.0
        val duration = (stopEpochMs - startEpochMs).toDouble()
        if (duration <= 0) return 0.0
        return ((nowMs - startEpochMs) / duration).coerceIn(0.0, 1.0)
    }

    /** Human-readable start time, e.g. "15:00". */
    val startTimeString: String get() = timeFormatter.format(startDate)

    /** Human-readable stop time, e.g. "16:45". */
    val stopTimeString: String get() = timeFormatter.format(stopDate)

    /** Whether this programme is currently airing. */
    val isNow: Boolean get() {
        val nowMs = System.currentTimeMillis()
        return nowMs in startEpochMs until stopEpochMs
    }

    /** Remaining duration as a human-readable string, e.g. "45m left". */
    val timeRemainingString: String get() {
        val remainingMinutes = ((stopEpochMs - System.currentTimeMillis()) / MS_PER_MINUTE).toInt()
        return if (remainingMinutes > 0) "${remainingMinutes}m left" else "Ending"
    }

    /**
     * Whether this programme title matches any known sport EPG keyword.
     * Case-insensitive
     */
    val isSportMatch: Boolean get() {
        val lowercaseTitle = title.lowercase()
        return SportCategory.allKeywords.any { keyword -> lowercaseTitle.contains(keyword) }
    }

    private companion object {
        private const val MS_PER_MINUTE = 60_000L
        private val timeFormatter = SimpleDateFormat("HH:mm", Locale.getDefault())
    }

    /** Progress using pre-captured [nowMs] — avoids System.currentTimeMillis() per card. */
    fun progress(nowMs: Long): Double {
        if (nowMs < startEpochMs) return 0.0
        if (nowMs >= stopEpochMs) return 1.0
        val duration = (stopEpochMs - startEpochMs).toDouble()
        if (duration <= 0) return 0.0
        return ((nowMs - startEpochMs) / duration).coerceIn(0.0, 1.0)
    }

    /** Whether airing at [nowMs] — avoids System.currentTimeMillis() per card. */
    fun isNow(nowMs: Long): Boolean = nowMs in startEpochMs until stopEpochMs

    /** Time remaining string using pre-captured [nowMs]. */
    fun timeRemainingString(nowMs: Long): String {
        val remainingMinutes = ((stopEpochMs - nowMs) / MS_PER_MINUTE).toInt()
        return if (remainingMinutes > 0) "${remainingMinutes}m left" else "Ending"
    }
}