// app/src/main/java/com/nativestream/android/ui/viewmodel/PlayerViewModel.kt
//
// Player ViewModel
// ExoPlayer-backed. Manages playback state, HLS stream loading, retry logic ,
// header injection (AND-018), PiP state (AND-020), and player visibility.

package com.nativestream.android.ui.viewmodel

import android.app.Application
import android.app.PictureInPictureParams
import android.util.Log
import android.util.Rational
import androidx.annotation.OptIn
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import com.nativestream.android.data.remote.ApiClient
import com.nativestream.android.domain.model.Channel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

private const val TAG = "PlayerViewModel"

private const val CONTROLS_AUTO_HIDE_MS    = 3_000L
private const val RETRY_DELAY_MS           = 2_000L
private const val MAX_RETRY_ATTEMPTS       = 3
private const val PIP_ASPECT_RATIO_NUM     = 16
private const val PIP_ASPECT_RATIO_DEN     = 9
private val SCORE_REGEX                    = Regex("""(\d+)\s*[–\-]\s*(\d+)""")


@OptIn(UnstableApi::class)
@HiltViewModel
class PlayerViewModel @Inject constructor(
    application: Application,
    private val apiClient: ApiClient,
) : AndroidViewModel(application) {

    // ── ExoPlayer ─────────────────────────────────────────────────────────────

    val exoPlayer: ExoPlayer = ExoPlayer.Builder(application).build().also { player ->
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                _isPlaying.value = player.isPlaying
                if (state == Player.STATE_READY) {
                    _playerError.value = null
                    retryCount = 0
                    _videoQuality.value = mapHeightToQuality(player.videoSize.height)
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                _videoQuality.value = mapHeightToQuality(videoSize.height)
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e(TAG, "Playback error: ${error.message}")
                _videoQuality.value = null
                scheduleRetry()
            }
        })
    }

    // ── Public state ──────────────────────────────────────────────────────────

    private val _isPlayerVisible = MutableStateFlow(false)
    val isPlayerVisible: StateFlow<Boolean> = _isPlayerVisible.asStateFlow()

    private val _activeChannel = MutableStateFlow<Channel?>(null)
    val activeChannel: StateFlow<Channel?> = _activeChannel.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val _controlsVisible = MutableStateFlow(true)
    val controlsVisible: StateFlow<Boolean> = _controlsVisible.asStateFlow()

    private val _playerError = MutableStateFlow<String?>(null)
    val playerError: StateFlow<String?> = _playerError.asStateFlow()

    private val _isInPip = MutableStateFlow(false)
    val isInPip: StateFlow<Boolean> = _isInPip.asStateFlow()

    val hasActiveChannel: StateFlow<Boolean> = _activeChannel
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _resizeMode = MutableStateFlow(AspectRatioFrameLayout.RESIZE_MODE_FIT)

    val resizeMode: StateFlow<Int> = _resizeMode.asStateFlow()

    private val _videoQuality = MutableStateFlow<String?>(null)
    val videoQuality: StateFlow<String?> = _videoQuality.asStateFlow()

    // ── Retry state ───────────────────────────────────────────────────────────

    private var retryCount = 0
    private var retryJob: Job? = null
    private var controlsHideJob: Job? = null

    // ── Playback controls ─────────────────────────────────────────────────────

    fun play(channel: Channel) {
        _activeChannel.value = channel
        _isPlayerVisible.value = true
        _playerError.value = null
        retryCount = 0
        loadStream(channel)
        scheduleControlsHide()
    }

    fun playUrl(url: String, headers: Map<String, String> = emptyMap()) {
        val temporaryChannel = Channel.create(
            tvgId = "",
            name = url.substringAfterLast("/").ifEmpty { url },
            streamUrl = url,
            streamHeaders = headers,
        )
        play(temporaryChannel)
    }

    fun showPlayer() {
        _isPlayerVisible.value = true
    }

    fun hidePlayer() {
        _isPlayerVisible.value = false
    }

    fun togglePlayback() {
        if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
        _isPlaying.value = exoPlayer.isPlaying
    }

    fun toggleMute() {
        val muted = !_isMuted.value
        exoPlayer.volume = if (muted) 0f else 1f
        _isMuted.value = muted
    }

    fun toggleResizeMode() {
        _resizeMode.value = if (_resizeMode.value == AspectRatioFrameLayout.RESIZE_MODE_FIT)
            AspectRatioFrameLayout.RESIZE_MODE_FILL
        else
            AspectRatioFrameLayout.RESIZE_MODE_FIT
    }

    fun stop() {
        exoPlayer.stop()
        _isPlayerVisible.value = false
        _isPlaying.value = false
        _activeChannel.value = null
        _playerError.value = null
        retryJob?.cancel()
    }

    fun retryManually() {
        retryCount = 0
        _activeChannel.value?.let { play(it) }
    }

    // ── Controls visibility ───────────────────────────────────────────────────

    fun onPlayerTapped() {
        _controlsVisible.value = !_controlsVisible.value
        if (_controlsVisible.value) scheduleControlsHide()
    }

    private fun scheduleControlsHide() {
        controlsHideJob?.cancel()
        controlsHideJob = viewModelScope.launch {
            delay(CONTROLS_AUTO_HIDE_MS)
            _controlsVisible.value = false
        }
    }

    // ── Stream loading with header injection (AND-018) ────────────────────────

    @OptIn(UnstableApi::class)
    private fun loadStream(channel: Channel) {
        val dataSourceFactory = DefaultHttpDataSource.Factory().apply {
            if (channel.streamHeaders.isNotEmpty()) {
                setDefaultRequestProperties(channel.streamHeaders)
            }
        }
        val mediaSource = HlsMediaSource.Factory(dataSourceFactory)
            .createMediaSource(MediaItem.fromUri(channel.streamUrl))

        exoPlayer.setMediaSource(mediaSource)
        exoPlayer.prepare()
        exoPlayer.play()
    }

    // ── Retry logic (AND-022) ─────────────────────────────────────────────────
    // Up to 3 retries with 2s delay, re-fetching active link from server each time.

    private fun scheduleRetry() {
        if (retryCount >= MAX_RETRY_ATTEMPTS) {
            _playerError.value = "Stream failed after $MAX_RETRY_ATTEMPTS attempts"
            Log.e(TAG, "Max retries reached for ${_activeChannel.value?.name}")
            return
        }
        retryJob?.cancel()
        retryJob = viewModelScope.launch {
            retryCount++
            Log.d(TAG, "Retry $retryCount/$MAX_RETRY_ATTEMPTS in ${RETRY_DELAY_MS}ms")
            delay(RETRY_DELAY_MS)
            _activeChannel.value?.let { channel ->
                try {
                    // Re-fetch active link from server before retrying
                    val detail = apiClient.getChannel(channel.tvgId.ifEmpty { channel.id })
                    val activeUrl = detail.activeLink?.url ?: channel.streamUrl
                    val refreshedChannel = channel.copy(streamUrl = activeUrl)
                    loadStream(refreshedChannel)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to re-fetch active link: ${e.message} — using cached URL")
                    loadStream(channel)
                }
            }
        }
    }

    // ── Picture in Picture (AND-020) ──────────────────────────────────────────

    fun buildPipParams(): PictureInPictureParams =
        PictureInPictureParams.Builder()
            .setAspectRatio(Rational(PIP_ASPECT_RATIO_NUM, PIP_ASPECT_RATIO_DEN))
            .build()

    fun onEnteredPip() {
        _isInPip.value = true
    }

    fun onExitedPip() {
        _isInPip.value = false
        _controlsVisible.value = true
    }

    // ── Score overlay helper ──────────────────────────────────────────────────

    fun extractScore(title: String): String? {
        val match = SCORE_REGEX.find(title) ?: return null
        return "${match.groupValues[1]} – ${match.groupValues[2]}"
    }

    override fun onCleared() {
        super.onCleared()
        retryJob?.cancel()
        controlsHideJob?.cancel()
        exoPlayer.release()
    }


    // ── Sidebar channel list (AND-019) ────────────────────────────────────────
    // Populated from PlaylistViewModel when sidebar opens.

    private val _channelList =
        MutableStateFlow<List<com.nativestream.android.domain.model.Channel>>(emptyList())
    val channelList: StateFlow<List<com.nativestream.android.domain.model.Channel>> =
        _channelList.asStateFlow()

    fun setChannelList(channels: List<com.nativestream.android.domain.model.Channel>) {
        _channelList.value = channels
    }

    // ── Sidebar visibility ────────────────────────────────────────────────────

    private val _sidebarVisible = MutableStateFlow(false)
    val sidebarVisible: StateFlow<Boolean> = _sidebarVisible.asStateFlow()

    fun toggleSidebar() {
        _sidebarVisible.value = !_sidebarVisible.value
    }

    private fun mapHeightToQuality(height: Int): String? {
        return when {
            height >= 2160 -> "4K"
            height >= 1080 -> "FHD"
            height >= 720  -> "HD"
            height > 0     -> "SD"
            else           -> null // Unknown or audio-only
        }
    }

}