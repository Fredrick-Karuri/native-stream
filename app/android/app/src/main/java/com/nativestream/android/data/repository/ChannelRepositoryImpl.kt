package com.nativestream.android.data.repository

import com.nativestream.android.domain.model.Channel
import com.nativestream.android.domain.repository.ChannelRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * app/src/main/java/com/nativestream/android/data/repository/ChannelRepositoryImpl.kt
 *
 * Singleton implementation of ChannelRepository. Owns the MutableStateFlow that
 * backs the channel list. Only ChannelLoadingViewModel should call emit() —
 * all other consumers depend on the read-only ChannelRepository interface.
 */
@Singleton
class ChannelRepositoryImpl @Inject constructor() : ChannelRepository {

    private val _channels = MutableStateFlow<List<Channel>>(emptyList())
    override val channels: StateFlow<List<Channel>> = _channels.asStateFlow()

    /** Called exclusively by ChannelLoadingViewModel after a successful fetch or cache read. */
    fun emit(channels: List<Channel>) {
        _channels.value = channels
    }
}