package com.nativestream.android.data.player

import androidx.media3.session.MediaSessionService

/**
 * Background playback service stub.
 * Full implementation in AND-017 (PlayerScreen / ExoPlayer).
 */
class NativeStreamPlaybackService : MediaSessionService() {

    override fun onGetSession(
        controllerInfo: androidx.media3.session.MediaSession.ControllerInfo
    ): androidx.media3.session.MediaSession? = null // TODO AND-017
}