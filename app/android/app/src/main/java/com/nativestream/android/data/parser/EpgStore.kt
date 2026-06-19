// app/src/main/java/com/nativestream/android/data/parser/EpgStore.kt
//
// EPG Store
// Holds parsed programme data keyed by channel tvg-id.
// AND-PERF-001: precomputed current/next index — O(1) lookups
// AND-PERF-002: programmeCount cached at construction
// AND-PERF-010: programmes sorted by startEpochMs at construction

package com.nativestream.android.data.parser

import com.nativestream.android.domain.model.Programme

class EpgStore(rawProgrammes: Map<String, List<Programme>>) {

    // Programmes sorted ascending by startEpochMs per channel — AND-PERF-010
    private val programmesByChannelId: Map<String, List<Programme>> =
        rawProgrammes.mapValues { (_, progs) -> progs.sortedBy { it.startEpochMs } }

    // Lowercase shadow map for FX-002 fallback matching
    private val programmesByLowercaseId: Map<String, List<Programme>> =
        programmesByChannelId.entries.associate { (key, value) -> key.lowercase() to value }

    // AND-PERF-002: cached at construction — no sumOf on every access
    val channelCount: Int = programmesByChannelId.size
    val programmeCount: Int = programmesByChannelId.values.sumOf { it.size }

    // AND-PERF-001: precomputed current/next index
    // Rebuilt via rebuildIndex() called by EpgViewModel on a 30s timer
    internal var currentIndex: Map<String, Programme?> = emptyMap()
    internal var nextIndex: Map<String, Programme?> = emptyMap()

    init {
        rebuildIndex(System.currentTimeMillis())
    }

    companion object {
        /**
         * Creates a lightweight EpgStore from precomputed index maps.
         * Used on warm boot to serve cached EPG before full parse completes.
         * Full programme data is unavailable — schedule() returns empty.
         */
        fun fromIndex(
            currentIndex: Map<String, Programme?>,
            nextIndex:    Map<String, Programme?>,
        ): EpgStore {
            val empty = EpgStore(emptyMap())
            empty.currentIndex = currentIndex.toMutableMap()
            empty.nextIndex    = nextIndex.toMutableMap()
            return empty
        }
    }

    /**
     * Rebuilds the current and next programme index for the given [nowMs].
     * Called once at construction and every 30s by EpgViewModel.
     * O(n total programmes) — runs on IO dispatcher in EpgViewModel.
     */
    fun rebuildIndex(nowMs: Long) {
        val current = HashMap<String, Programme?>(programmesByChannelId.size)
        val next    = HashMap<String, Programme?>(programmesByChannelId.size)

        programmesByChannelId.forEach { (tvgId, programmes) ->
            var currentProg: Programme? = null
            var nextProg: Programme?    = null

            // Programmes are sorted — early exit once past nowMs
            for (prog in programmes) {
                if (prog.stopEpochMs <= nowMs) continue          // already ended
                if (prog.startEpochMs <= nowMs) {
                    currentProg = prog                           // airing now
                } else {
                    nextProg = prog                              // first future — early exit
                    break
                }
            }
            current[tvgId] = currentProg
            next[tvgId]    = nextProg
        }

        currentIndex = current
        nextIndex    = next
    }

    /**
     * Returns programmes for [tvgId].
     * FX-002: exact match first, lowercase fallback second.
     */
    fun programmesFor(tvgId: String): List<Programme> =
        programmesByChannelId[tvgId]
            ?: programmesByLowercaseId[tvgId.lowercase()]
            ?: emptyList()

    /**
     * O(1) — looks up precomputed current index.
     * Falls back to lowercase id if exact match misses.
     */
    fun currentProgramme(tvgId: String): Programme? =
        currentIndex[tvgId]
            ?: currentIndex[tvgId.lowercase()]

    /**
     * O(1) — looks up precomputed next index.
     * Falls back to lowercase id if exact match misses.
     */
    fun nextProgramme(tvgId: String): Programme? =
        nextIndex[tvgId]
            ?: nextIndex[tvgId.lowercase()]

    /**
     * O(n slice) with early exit — programmes are sorted so we stop
     * at the first programme starting after toEpochMs.
     */
    fun schedule(tvgId: String, fromEpochMs: Long, toEpochMs: Long): List<Programme> {
        val result = mutableListOf<Programme>()
        for (prog in programmesFor(tvgId)) {
            if (prog.startEpochMs >= toEpochMs) break   // sorted — no more in range
            if (prog.stopEpochMs > fromEpochMs) result.add(prog)
        }
        return result
    }

    /** Returns a snapshot of the current programme index for cache persistence. */
    fun currentIndexSnapshot(): Map<String, Programme?> = currentIndex

    /** Returns a snapshot of the next programme index for cache persistence. */
    fun nextIndexSnapshot(): Map<String, Programme?> = nextIndex

    /** All distinct channel IDs present in this store. */
    fun allChannelIds(): Set<String> = programmesByChannelId.keys
}