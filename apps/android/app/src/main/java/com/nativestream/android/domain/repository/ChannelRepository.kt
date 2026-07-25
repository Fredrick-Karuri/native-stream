package com.nativestream.android.domain.repository

import com.nativestream.android.domain.model.Channel
import kotlinx.coroutines.flow.StateFlow

/**
 * app/src/main/java/com/nativestream/android/domain/repository/ChannelRepository.kt
 *
 * Shared state bus for the loaded channel list.
 * ChannelLoadingViewModel writes to it via ChannelRepositoryImpl.emit();
 * ChannelFilterViewModel and NowViewModel read from it via this interface.
 * Scoped as a Hilt singleton so all consumers observe the same StateFlow instance.
 */
interface ChannelRepository {
    val channels: StateFlow<List<Channel>>
}