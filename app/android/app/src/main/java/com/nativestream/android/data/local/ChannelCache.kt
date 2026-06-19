// app/src/main/java/com/nativestream/android/data/local/ChannelCache.kt
//
// Channel Cache
// Persists parsed List<Channel> to cacheDir as JSON per source.
// AND-CACHE-001: write after parse, read before network on warm boot.
// AND-CACHE-003: TTL + source URL invalidation via sidecar metadata file.

package com.nativestream.android.data.local

import android.app.Application
import android.util.Log
import com.nativestream.android.domain.model.Channel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.Serializable
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG                  = "ChannelCache"
private const val CACHE_FILE_PREFIX    = "channels_"
private const val META_FILE_PREFIX     = "channels_meta_"
private const val CACHE_FILE_SUFFIX    = ".json"

@Serializable
private data class ChannelCacheMeta(
    val cachedAt:  Long,
    val sourceUrl: String,
)

@Singleton
class ChannelCache @Inject constructor(
    private val application: Application,
) {
    private val cacheDir: File get() = application.cacheDir

    private fun cacheFile(sourceId: String) =
        File(cacheDir, "$CACHE_FILE_PREFIX${sourceId}$CACHE_FILE_SUFFIX")

    private fun metaFile(sourceId: String) =
        File(cacheDir, "$META_FILE_PREFIX${sourceId}$CACHE_FILE_SUFFIX")

    /**
     * Write [channels] for [sourceId] to disk.
     * Also writes metadata sidecar with timestamp and source URL.
     */
    suspend fun write(sourceId: String, sourceUrl: String, channels: List<Channel>) {
        withContext(Dispatchers.IO) {
            runCatching {
                cacheFile(sourceId).writeText(Json.encodeToString(channels))
                metaFile(sourceId).writeText(
                    Json.encodeToString(
                        ChannelCacheMeta(
                            cachedAt  = System.currentTimeMillis(),
                            sourceUrl = sourceUrl,
                        )
                    )
                )
                Log.d(TAG, "Wrote ${channels.size} channels for source $sourceId")
            }.onFailure {
                Log.w(TAG, "Cache write failed for $sourceId: ${it.message}")
            }
        }
    }

    /**
     * Read cached channels for [sourceId].
     * Returns null if: cache missing, TTL exceeded, or source URL changed.
     */
    suspend fun read(
        sourceId: String,
        sourceUrl: String,
        ttlMs: Long,
    ): List<Channel>? = withContext(Dispatchers.IO) {
        runCatching {
            val meta = metaFile(sourceId).takeIf { it.exists() }?.let {
                Json.decodeFromString<ChannelCacheMeta>(it.readText())
            } ?: return@withContext null

            // Invalidate on URL change
            if (meta.sourceUrl != sourceUrl) {
                Log.d(TAG, "Cache invalidated — URL changed for $sourceId")
                clear(sourceId)
                return@withContext null
            }

            // Invalidate on TTL
            val ageMs = System.currentTimeMillis() - meta.cachedAt
            if (ageMs > ttlMs) {
                Log.d(TAG, "Cache expired for $sourceId (age ${ageMs}ms > ttl ${ttlMs}ms)")
                return@withContext null
            }

            val file = cacheFile(sourceId).takeIf { it.exists() }
                ?: return@withContext null

            val channels = Json.decodeFromString<List<Channel>>(file.readText())
            Log.d(TAG, "Cache hit: ${channels.size} channels for $sourceId")
            channels
        }.getOrElse {
            Log.w(TAG, "Cache read failed for $sourceId: ${it.message}")
            null
        }
    }

    /**
     * Delete cache and metadata files for [sourceId].
     * Called on source removal (AND-CACHE-003).
     */
    suspend fun clear(sourceId: String) {
        withContext(Dispatchers.IO) {
            cacheFile(sourceId).delete()
            metaFile(sourceId).delete()
            Log.d(TAG, "Cache cleared for $sourceId")
        }
    }
}