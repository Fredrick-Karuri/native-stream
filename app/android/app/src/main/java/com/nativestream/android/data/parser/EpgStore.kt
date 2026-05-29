// app/src/main/java/com/nativestream/android/data/parser/EpgStore.kt
//
// NS-EPG: EPG Store
// Holds parsed programme data keyed by channel tvg-id.
// Implements FX-002 case-insensitive fallback matching — exact match first,
// lowercase fallback second — mirroring Mac EPGStore behaviour.

package com.nativestream.android.data.parser

import com.nativestream.android.domain.model.Programme

class EpgStore(rawProgrammes: Map<String, List<Programme>>) {

    // Primary map — preserves original casing from XMLTV file
    private val programmesByChannelId: Map<String, List<Programme>> = rawProgrammes

    // Lowercase shadow map for FX-002 fallback matching
    private val programmesByLowercaseId: Map<String, List<Programme>> =
        rawProgrammes.entries.associate { (key, value) -> key.lowercase() to value }

    /** Total number of distinct channel IDs in this store. */
    val channelCount: Int get() = programmesByChannelId.size

    /** Total programme entries across all channels. */
    val programmeCount: Int get() = programmesByChannelId.values.sumOf { it.size }

    /**
     * Returns programmes for [tvgId].
     * FX-002: exact match first, lowercase fallback second.
     * Returns empty list (not null) when no match found.
     */
    fun programmesFor(tvgId: String): List<Programme> =
        programmesByChannelId[tvgId]
            ?: programmesByLowercaseId[tvgId.lowercase()]
            ?: emptyList()

    /**
     * Returns the currently-airing programme for [tvgId], or null.
     */
    fun currentProgramme(tvgId: String): Programme? =
        programmesFor(tvgId).firstOrNull { it.isNow }

    /**
     * Returns the next upcoming programme for [tvgId] after now, or null.
     * Mirrors EPGViewModel.nextProgramme(for:) in EPGViewModel.swift.
     */
    fun nextProgramme(tvgId: String): Programme? {
        val nowMs = System.currentTimeMillis()
        return programmesFor(tvgId)
            .filter { it.startEpochMs > nowMs }
            .minByOrNull { it.startEpochMs }
    }

    /**
     * Returns programmes for [tvgId] within [fromEpochMs]..[toEpochMs].
     * Mirrors Mac EPGViewModel.schedule(from:to:) date-range query.
     */
    fun schedule(tvgId: String, fromEpochMs: Long, toEpochMs: Long): List<Programme> =
        programmesFor(tvgId).filter { programme ->
            programme.stopEpochMs > fromEpochMs && programme.startEpochMs < toEpochMs
        }

    /** All distinct channel IDs present in this store. */
    fun allChannelIds(): Set<String> = programmesByChannelId.keys
}