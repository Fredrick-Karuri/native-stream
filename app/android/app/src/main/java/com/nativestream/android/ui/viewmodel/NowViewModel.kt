/**
 * app/src/main/java/com/nativestream/android/ui/viewmodel/NowViewModel.kt
 *
 * Single responsibility: keep EpgViewModel fed with the current channel list.
 * Observes ChannelRepository and forwards updates to EpgViewModel.updateChannels()
 * whenever the channel list changes.
 *
 * NowScreen reads the EPG bucket flows (liveMatches, liveOnAir, startingSoon,
 * isRefreshing) directly from EpgViewModel — this avoids the Hilt constraint
 * that prevents one @HiltViewModel from being constructor-injected into another.
 *
 * Replaces the LaunchedEffect(channels) { epgViewModel.updateChannels(channels) }
 * that previously lived in NowScreen, and removes NowScreen's dependency on
 * PlaylistViewModel entirely.
 */

package com.nativestream.android.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nativestream.android.domain.repository.ChannelRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NowViewModel @Inject constructor(
    private val channelRepository: ChannelRepository,
) : ViewModel() {

    /**
     * Called once from NowScreen with the EpgViewModel instance obtained via
     * hiltViewModel(). Starts collecting the channel list and forwarding updates.
     * Safe to call multiple times — only the first call takes effect.
     */
    private var bridgeStarted = false

    fun bridgeChannelsToEpg(epgViewModel: EpgViewModel) {
        if (bridgeStarted) return
        bridgeStarted = true
        viewModelScope.launch {
            channelRepository.channels.collect { channels ->
                epgViewModel.updateChannels(channels)
            }
        }
    }
}