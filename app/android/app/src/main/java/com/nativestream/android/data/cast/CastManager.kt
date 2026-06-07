// app/src/main/java/com/nativestream/android/data/cast/CastManager.kt
//
// Cast Manager
// Wraps MediaRouter + Cast SDK. Exposes cast availability and provides
// castStream() for the player controls. Injected as singleton via Hilt.

package com.nativestream.android.data.cast

import android.content.Context
import android.util.Log
import androidx.mediarouter.media.MediaControlIntent
import androidx.mediarouter.media.MediaRouter
import androidx.mediarouter.media.MediaRouteSelector
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "CastManager"
private const val STREAM_TYPE_LIVE = 2
private const val RECEIVER_APP_ID  = "CC1AD845"

@Singleton
class CastManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val _isCastAvailable = MutableStateFlow(false)
    val isCastAvailable: StateFlow<Boolean> = _isCastAvailable.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private var castContext: CastContext? = null
    private var currentSession: CastSession? = null

    private val castSelector = MediaRouteSelector.Builder()
        .addControlCategory(MediaControlIntent.CATEGORY_REMOTE_PLAYBACK)
        .build()

    private val routerCallback = object : MediaRouter.Callback() {
        override fun onRouteAdded(router: MediaRouter, route: MediaRouter.RouteInfo)   = updateAvailability()
        override fun onRouteRemoved(router: MediaRouter, route: MediaRouter.RouteInfo) = updateAvailability()
        override fun onRouteChanged(router: MediaRouter, route: MediaRouter.RouteInfo) = updateAvailability()
    }

    private val sessionListener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarted(session: CastSession, sessionId: String) {
            currentSession     = session
            _isConnected.value = true
            Log.d(TAG, "Cast session started")
        }
        override fun onSessionEnded(session: CastSession, error: Int) {
            currentSession     = null
            _isConnected.value = false
            Log.d(TAG, "Cast session ended")
        }
        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
            currentSession     = session
            _isConnected.value = true
        }
        override fun onSessionStarting(session: CastSession)                          {}
        override fun onSessionStartFailed(session: CastSession, error: Int)           { _isConnected.value = false }
        override fun onSessionEnding(session: CastSession)                            {}
        override fun onSessionResuming(session: CastSession, sessionId: String)       {}
        override fun onSessionResumeFailed(session: CastSession, error: Int)          {}
        override fun onSessionSuspended(session: CastSession, reason: Int)            {}
    }

    fun initialise() {
        try {
            castContext = CastContext.getSharedInstance(context)
            castContext?.sessionManager?.addSessionManagerListener(sessionListener, CastSession::class.java)

            MediaRouter.getInstance(context).addCallback(
                castSelector,
                routerCallback,
                MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY,
            )
            updateAvailability()
        } catch (e: Exception) {
            Log.w(TAG, "Cast SDK not available: ${e.message}")
        }
    }

    private fun updateAvailability() {
        val routes = MediaRouter.getInstance(context).routes
        _isCastAvailable.value = routes.any { route ->
            !route.isDefault && route.matchesSelector(castSelector)
        }
    }

    fun castStream(streamUrl: String, title: String) {
        val client = currentSession?.remoteMediaClient ?: return

        val metadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE).apply {
            putString(MediaMetadata.KEY_TITLE, title)
        }
        val mediaInfo = MediaInfo.Builder(streamUrl)
            .setStreamType(STREAM_TYPE_LIVE)
            .setContentType("application/x-mpegURL")
            .setMetadata(metadata)
            .build()

        client.load(
            MediaLoadRequestData.Builder()
                .setMediaInfo(mediaInfo)
                .setAutoplay(true)
                .build()
        ).setResultCallback { result ->
            if (!result.status.isSuccess) {
                Log.e(TAG, "Cast load failed: ${result.status.statusMessage}")
            }
        }
        Log.d(TAG, "Casting: $title → $streamUrl")
    }

    fun stopCasting() {
        currentSession?.remoteMediaClient?.stop()
    }

    fun release() {
        castContext?.sessionManager?.removeSessionManagerListener(sessionListener, CastSession::class.java)
        MediaRouter.getInstance(context).removeCallback(routerCallback)
    }
}