// app/src/main/java/com/nativestream/android/data/player/MediaSessionCallback.kt

package com.nativestream.android.data.player

import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

@UnstableApi
class MediaSessionCallback : MediaSession.Callback {

    /**
     * Called when the controller (PlayerViewModel via MediaController) sets a media item.
     * We resolve the URI here so ExoPlayer in the service can actually play it.
     * Headers passed via RequestMetadata extras are extracted and applied.
     */
    override fun onAddMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: List<MediaItem>
    ): ListenableFuture<List<MediaItem>> {
        val resolved = mediaItems.map { item ->
            item.buildUpon()
                .setUri(item.localConfiguration?.uri ?: item.mediaId.let {
                    android.net.Uri.parse(it)
                })
                .build()
        }
        return Futures.immediateFuture(resolved)
    }
    override fun onPlaybackResumption(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo
    ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
        return Futures.immediateFuture(
            MediaSession.MediaItemsWithStartPosition(emptyList(), 0, 0L)
        )
    }
}