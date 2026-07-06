// app/src/main/java/com/nativestream/android/ui/viewmodel/PlayerViewModel.kt
//
// Player ViewModel
// ExoPlayer-backed. Manages playback state, HLS stream loading, retry logic ,
// header injection, PiP state, and player visibility.

package com.nativestream.android.ui.viewmodel

import android.app.Application
import android.content.Context
import android.os.PowerManager
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
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import android.content.ComponentName
import androidx.annotation.VisibleForTesting
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.nativestream.android.data.local.StreamQuality
import com.nativestream.android.data.player.NativeStreamPlaybackService
import com.nativestream.android.data.remote.ApiClient
import com.nativestream.android.domain.model.Channel
import com.nativestream.android.domain.repository.ChannelRepository
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
import org.json.JSONObject
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
    private val channelRepository: ChannelRepository,
) : AndroidViewModel(application) {

    // ── Player (MediaController → service) ───────────────────────────────────
    private var _player: Player? = null
    val player: Player get() = _player!!

    fun connectToService() {
        if (_player != null) return
        val app = getApplication<Application>()
        val sessionToken = SessionToken(
            app,
            ComponentName(app, NativeStreamPlaybackService::class.java)
        )
        controllerFuture = MediaController.Builder(app, sessionToken)
            .buildAsync()
            .also { future ->
                future.addListener({
                    _player = future.get()
                    _player!!.addListener(playerListener)
                }, MoreExecutors.directExecutor())
            }
    }

    @VisibleForTesting
    internal fun setPlayerForTest(p: Player) {
        _player = p
        _player!!.addListener(playerListener)
    }
    private var controllerFuture: ListenableFuture<MediaController>? = null

    private val playerListener = object : Player.Listener {
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

    val currentChannel: StateFlow<Channel?> = _activeChannel

    private val _resizeMode = MutableStateFlow(AspectRatioFrameLayout.RESIZE_MODE_FIT)

    val resizeMode: StateFlow<Int> = _resizeMode.asStateFlow()

    private val _videoQuality = MutableStateFlow<String?>(null)
    val videoQuality: StateFlow<String?> = _videoQuality.asStateFlow()
    private val _sessionQuality = MutableStateFlow<StreamQuality?>(null)
    val sessionQuality: StateFlow<StreamQuality?> = _sessionQuality.asStateFlow()

    // ── Retry state ───────────────────────────────────────────────────────────

    private var retryCount = 0
    private var retryJob: Job? = null
    private var controlsHideJob: Job? = null
    private val wakeLock: PowerManager.WakeLock? by lazy {
        (getApplication<Application>().getSystemService(Context.POWER_SERVICE) as? PowerManager)
            ?.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK, "NativeStream::Playback")
            ?.apply { setReferenceCounted(false) }
    }

    // ── Playback controls ─────────────────────────────────────────────────────

    fun play(channel: Channel, quality: StreamQuality = StreamQuality.AUTO) {
        _activeChannel.value = channel
        _isPlayerVisible.value = true
        _playerError.value = null
        retryCount = 0
        _sessionQuality.value = null
        loadStream(channel)
        applyQuality(quality)
        scheduleControlsHide()
        if (wakeLock?.isHeld == false) wakeLock?.acquire()
    }

    fun playUrl(url: String, headers: Map<String, String> = emptyMap(), displayName: String? = null) {
        val temporaryChannel = Channel.create(
            tvgId = "",
            name = displayName?.ifEmpty { null } ?: url.substringAfterLast("/").ifEmpty { url },
            streamUrl = url,
            streamHeaders = headers,
        )
        play(temporaryChannel)
    }

    fun playFromRemote(channelId: String, channelName: String, streamUrl: String) {
        viewModelScope.launch {
            val channel = channelRepository.channels.value.find { it.id == channelId }
            if (channel != null) {
                play(channel)
            } else {
                playUrl(streamUrl, displayName = channelName)
            }
        }
    }

    fun applyQuality(quality: StreamQuality) {
        val p = _player ?: return
        p.trackSelectionParameters = p.trackSelectionParameters
            .buildUpon()
            .setMaxVideoBitrate(
                if (quality == StreamQuality.AUTO) Int.MAX_VALUE
                else quality.bitrateBps.toInt()
            )
            .build()
    }

    fun cycleSessionQuality() {
        val entries = StreamQuality.entries
        val current = _sessionQuality.value ?: StreamQuality.AUTO
        val next    = entries[(entries.indexOf(current) + 1) % entries.size]
        _sessionQuality.value = next
        applyQuality(next)
    }

    fun showPlayer() {
        _isPlayerVisible.value = true
    }

    fun hidePlayer() {
        _isPlayerVisible.value = false
    }

    fun togglePlayback() {
        if (player.isPlaying) player.pause() else player.play()
        _isPlaying.value = player.isPlaying
    }

    fun toggleMute() {
        val muted = !_isMuted.value
        player.volume = if (muted) 0f else 1f
        _isMuted.value = muted
    }

    fun toggleResizeMode() {
        _resizeMode.value = if (_resizeMode.value == AspectRatioFrameLayout.RESIZE_MODE_FIT)
            AspectRatioFrameLayout.RESIZE_MODE_FILL
        else
            AspectRatioFrameLayout.RESIZE_MODE_FIT
    }

    fun stop() {
        player.stop()
        _isPlayerVisible.value = false
        _isPlaying.value = false
        _activeChannel.value = null
        _playerError.value = null
        retryJob?.cancel()
        if (wakeLock?.isHeld == true) wakeLock?.release()
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

    // ── Stream loading with header injection ────────────────────────

    @OptIn(UnstableApi::class)
    private fun loadStream(channel: Channel) {
        val p = _player ?: return
        p.stop()
        p.clearMediaItems()
        val headersJson = if (channel.streamHeaders.isNotEmpty())
            JSONObject(channel.streamHeaders).toString()
        else ""

        val mediaItem = MediaItem.Builder()
            .setUri(channel.streamUrl)
            .setMediaId(headersJson)
            .setMediaMetadata(
                androidx.media3.common.MediaMetadata.Builder()
                    .setTitle(channel.name)
                    .setArtist(channel.groupTitle.ifEmpty { null })
                    .setArtworkUri(
                        channel.logoUrl?.let { android.net.Uri.parse(it) }
                    )
                    .setIsPlayable(true)
                    .build()
            )
            .build()
        p.setMediaItem(mediaItem)
        p.prepare()
        p.play()
    }

    // ── Retry logic ─────────────────────────────────────────────────
    // Up to 3 retries with 2s delay, re-fetching active link from server each time.

    private fun scheduleRetry() {
        retryCount++
        if (retryCount > MAX_RETRY_ATTEMPTS) {
            _playerError.value = "Stream failed after $MAX_RETRY_ATTEMPTS attempts"
            return
        }
        retryJob?.cancel()
        retryJob = viewModelScope.launch {
            delay(RETRY_DELAY_MS)
            _activeChannel.value?.let { channel ->
                try {
                    val detail = apiClient.getChannel(channel.tvgId.ifEmpty { channel.id })
                    val activeUrl = detail.activeLink?.url ?: channel.streamUrl
                    loadStream(channel.copy(streamUrl = activeUrl))
                } catch (e: Exception) {
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
        controlsHideJob?.cancel()
        _controlsVisible.value = false
    }

    fun onExitedPip() {
        _isInPip.value = false
        _controlsVisible.value = true
        scheduleControlsHide()
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
        if (wakeLock?.isHeld == true) wakeLock?.release()
        MediaController.releaseFuture(controllerFuture!!)
    }


    // ── Sidebar channel list ──────────────────────────────────────────
    // Sourced directly from ChannelRepository — no external setter needed.

    private val _channelList = MutableStateFlow<List<Channel>>(emptyList())
    val channelList: StateFlow<List<Channel>> = _channelList.asStateFlow()

    init {
        viewModelScope.launch {
            channelRepository.channels.collect { _channelList.value = it }
        }
    }

    // ── Sidebar visibility ────────────────────────────────────────────────────

    private val _sidebarVisible = MutableStateFlow(false)
    val sidebarVisible: StateFlow<Boolean> = _sidebarVisible.asStateFlow()

    fun toggleSidebar() {
        _sidebarVisible.value = !_sidebarVisible.value
    }

    fun playNextChannel() {
        val list = _channelList.value
        if (list.isEmpty()) return

        val currentChannel = _activeChannel.value ?: return
        val currentIndex = list.indexOf(currentChannel)

        if (currentIndex != -1) {
            val nextIndex = (currentIndex + 1) % list.size
            play(list[nextIndex])
        }
    }

    fun playPreviousChannel() {
        val list = _channelList.value
        if (list.isEmpty()) return

        val currentChannel = _activeChannel.value ?: return
        val currentIndex = list.indexOf(currentChannel)

        if (currentIndex != -1) {
            val prevIndex = if (currentIndex - 1 < 0) list.lastIndex else currentIndex - 1
            play(list[prevIndex])
        }
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