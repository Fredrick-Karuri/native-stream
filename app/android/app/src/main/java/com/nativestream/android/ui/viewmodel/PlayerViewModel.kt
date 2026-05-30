// app/src/main/java/com/nativestream/android/ui/viewmodel/PlayerViewModel.kt
//
// Exposes player visibility, active channel, isPlaying, isMuted for the shell
// (AND-008) and mini player (AND-009). Full ExoPlayer implementation in AND-017.

package com.nativestream.android.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.nativestream.android.domain.model.Channel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor() : ViewModel() {

    private val _isPlayerVisible = MutableStateFlow(false)
    val isPlayerVisible: StateFlow<Boolean> = _isPlayerVisible.asStateFlow()

    private val _activeChannel = MutableStateFlow<Channel?>(null)
    val activeChannel: StateFlow<Channel?> = _activeChannel.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    val hasActiveChannel: Boolean get() = _activeChannel.value != null

    fun play(channel: Channel) {
        _activeChannel.value   = channel
        _isPlaying.value       = true
        _isPlayerVisible.value = true
        // TODO AND-017: initialise ExoPlayer here
    }

    fun showPlayer() { _isPlayerVisible.value = true }
    fun hidePlayer() { _isPlayerVisible.value = false }

    fun togglePlayback() {
        _isPlaying.value = !_isPlaying.value
        // TODO AND-017: forward to ExoPlayer
    }

    fun toggleMute() {
        _isMuted.value = !_isMuted.value
        // TODO AND-017: forward to ExoPlayer
    }

    fun stop() {
        _isPlayerVisible.value = false
        _isPlaying.value       = false
        _activeChannel.value   = null
        // TODO AND-017: release ExoPlayer here
    }


    /**
     * Plays an arbitrary URL as a temporary channel (not persisted).
     * Mirrors playerVM.playURL(_:headers:) from PlayURLSheet.swift.
     * Headers injected via ExoPlayer in AND-018.
     */
    fun playUrl(url: String, headers: Map<String, String> = emptyMap()) {
        val temporaryChannel = com.nativestream.android.domain.model.Channel.create(
            tvgId         = "",
            name          = url.substringAfterLast("/").ifEmpty { url },
            streamUrl     = url,
            streamHeaders = headers,
        )
        play(temporaryChannel)
        // TODO AND-018: pass headers to ExoPlayer DefaultHttpDataSource.Factory
    }
}