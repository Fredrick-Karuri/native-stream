package com.nativestream.android.data.player

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import org.json.JSONObject
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy

@UnstableApi
class HeaderAwareMediaSourceFactory : MediaSource.Factory {

    private var drmSessionManagerProvider: DrmSessionManagerProvider? = null
    private var loadErrorHandlingPolicy: LoadErrorHandlingPolicy? = null

    override fun setDrmSessionManagerProvider(p0: DrmSessionManagerProvider): MediaSource.Factory {
        drmSessionManagerProvider = p0
        return this
    }

    override fun setLoadErrorHandlingPolicy(p0: LoadErrorHandlingPolicy): MediaSource.Factory {
        loadErrorHandlingPolicy = p0
        return this
    }

    override fun getSupportedTypes() = intArrayOf(C.CONTENT_TYPE_HLS, C.CONTENT_TYPE_OTHER)

    override fun createMediaSource(mediaItem: MediaItem): MediaSource {
        val headers = parseHeaders(mediaItem.mediaId)
        val dataSourceFactory = DefaultHttpDataSource.Factory().apply {
            if (headers.isNotEmpty()) setDefaultRequestProperties(headers)
        }
        return HlsMediaSource.Factory(dataSourceFactory).apply {
            drmSessionManagerProvider?.let { setDrmSessionManagerProvider(it) }
            loadErrorHandlingPolicy?.let { setLoadErrorHandlingPolicy(it) }
        }.createMediaSource(mediaItem)
    }

    private fun parseHeaders(mediaId: String): Map<String, String> {
        if (mediaId.isBlank()) return emptyMap()
        return try {
            val json = JSONObject(mediaId)
            json.keys().asSequence().associateWith { json.getString(it) }
        } catch (e: Exception) { emptyMap() }
    }
}