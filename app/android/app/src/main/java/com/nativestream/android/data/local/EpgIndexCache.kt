// app/src/main/java/com/nativestream/android/data/local/EpgIndexCache.kt
//
// EPG Index Cache
// Persists precomputed current/next programme index to cacheDir as JSON.
// AND-CACHE-004: write after index build, read before EPG fetch on warm boot.
// AND-CACHE-006: 2-hour TTL, clear on source removal.

package com.nativestream.android.data.local

import android.app.Application
import android.util.Log
import com.nativestream.android.domain.model.Programme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG               = "EpgIndexCache"
private const val INDEX_FILE_PREFIX = "epg_index_"
private const val INDEX_FILE_SUFFIX = ".json"
private const val EPG_CACHE_TTL_MS  = 2 * 3_600_000L  // 2 hours — matches ApiClient max-age

@Serializable
data class EpgIndexSnapshot(
    val cachedAt:     Long,
    val currentIndex: Map<String, Programme?>,
    val nextIndex:    Map<String, Programme?>,
)

@Singleton
class EpgIndexCache @Inject constructor(
    private val application: Application,
) {
    private val cacheDir: File get() = application.cacheDir

    private fun indexFile(sourceId: String) =
        File(cacheDir, "$INDEX_FILE_PREFIX${sourceId}$INDEX_FILE_SUFFIX")

    /**
     * Persist [currentIndex] and [nextIndex] for [sourceId] to disk.
     * Called by EpgViewModel after every index rebuild.
     */
    suspend fun writeIndex(
        sourceId:     String,
        currentIndex: Map<String, Programme?>,
        nextIndex:    Map<String, Programme?>,
    ) {
        withContext(Dispatchers.IO) {
            runCatching {
                val snapshot = EpgIndexSnapshot(
                    cachedAt     = System.currentTimeMillis(),
                    currentIndex = currentIndex,
                    nextIndex    = nextIndex,
                )
                indexFile(sourceId).writeText(Json.encodeToString(snapshot))
                Log.d(TAG, "Wrote EPG index for $sourceId (${currentIndex.size} entries)")
            }.onFailure {
                Log.w(TAG, "EPG index write failed for $sourceId: ${it.message}")
            }
        }
    }

    /**
     * Read cached EPG index for [sourceId].
     * Returns null if: file missing or older than [EPG_CACHE_TTL_MS].
     */
    suspend fun readIndex(sourceId: String): EpgIndexSnapshot? =
        withContext(Dispatchers.IO) {
            runCatching {
                val file = indexFile(sourceId).takeIf { it.exists() }
                    ?: return@withContext null

                val snapshot = Json.decodeFromString<EpgIndexSnapshot>(file.readText())

                val ageMs = System.currentTimeMillis() - snapshot.cachedAt
                if (ageMs > EPG_CACHE_TTL_MS) {
                    Log.d(TAG, "EPG index expired for $sourceId (age ${ageMs}ms)")
                    return@withContext null
                }

                Log.d(TAG, "EPG index cache hit for $sourceId (${snapshot.currentIndex.size} entries)")
                snapshot
            }.getOrElse {
                Log.w(TAG, "EPG index read failed for $sourceId: ${it.message}")
                null
            }
        }

    /**
     * Delete index file for [sourceId].
     * Called on source removal — AND-CACHE-006.
     */
    suspend fun clear(sourceId: String) {
        withContext(Dispatchers.IO) {
            indexFile(sourceId).delete()
            Log.d(TAG, "EPG index cleared for $sourceId")
        }
    }
}