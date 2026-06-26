/**
 * app/src/main/java/com/nativestream/android/ui/viewmodel/ChannelLoadingViewModel.kt
 *
 * Single responsibility: fetch channels from all configured sources, manage the
 * warm-boot cache strategy, and schedule auto-refresh. Writes results to
 * ChannelRepository so downstream consumers (ChannelFilterViewModel, NowViewModel)
 * react without depending on this class directly.
 *
 * Dependencies:
 *   - ApiClient        — network fetch
 *   - M3uParser        — byte array → List<Channel>
 *   - ChannelCache     — disk cache read/write
 *   - SettingsDataStore — EPG URL write-back after parse
 *   - SourceViewModel  — reads sources (avoids duplicating source state)
 *   - ChannelRepositoryImpl — writes the loaded channel list
 */

package com.nativestream.android.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nativestream.android.data.local.ChannelCache
import com.nativestream.android.data.local.SettingsDataStore
import com.nativestream.android.data.parser.M3uParser
import com.nativestream.android.data.remote.ApiClient
import com.nativestream.android.data.repository.ChannelRepositoryImpl
import com.nativestream.android.domain.model.Channel
import com.nativestream.android.domain.model.PlaylistSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject
import javax.inject.Named

private const val TAG = "ChannelLoadingViewModel"
private const val FALLBACK_REFRESH_INTERVAL_MS = 3_600_000L  // 1 hour
private const val CHANNEL_CACHE_TTL_MS = 6 * 3_600_000L     // 6 hours

@HiltViewModel
class ChannelLoadingViewModel @Inject constructor(
    private val apiClient: ApiClient,
    private val m3uParser: M3uParser,
    private val settingsDataStore: SettingsDataStore,
    private val channelCache: ChannelCache,
    private val channelRepository: ChannelRepositoryImpl,
    private val sourceViewModel: SourceViewModel,
    @Named("io") private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var autoRefreshJob: Job? = null

    init {
        viewModelScope.launch {
            sourceViewModel.sources.collect { sources ->
                if (sources.isNotEmpty()) {
                    loadFromCacheThenRefresh(sources)
                }
            }
        }
    }

    // ── Boot strategy ─────────────────────────────────────────────────────────

    /**
     * Warm boot: emit cached channels immediately so the UI is responsive, then
     * fetch fresh data in the background without a loading spinner.
     * Cold boot (no cache): falls through to loadAll() with the loading spinner.
     */
    private fun loadFromCacheThenRefresh(sources: List<PlaylistSource>) {
        viewModelScope.launch {
            val cachedChannels = withContext(ioDispatcher) {
                sources.flatMap { source ->
                    channelCache.read(
                        sourceId  = source.id,
                        sourceUrl = source.url,
                        ttlMs     = CHANNEL_CACHE_TTL_MS,
                    ) ?: emptyList()
                }
            }

            if (cachedChannels.isNotEmpty()) {
                channelRepository.emit(cachedChannels)
                loadAll(isBackgroundRefresh = true)
            } else {
                loadAll(isBackgroundRefresh = false)
            }
        }
    }

    // ── Load ──────────────────────────────────────────────────────────────────

    fun loadAll(isBackgroundRefresh: Boolean = false) {
        if (_isLoading.value) return
        viewModelScope.launch {
            if (isBackgroundRefresh) {
                _isRefreshing.value = true
            } else {
                _isLoading.value = true
            }
            _error.value = null
            try {
                val allChannels = fetchAllSourcesInParallel(sourceViewModel.sources.value)
                channelRepository.emit(allChannels)
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value    = false
                _isRefreshing.value = false
            }
        }
    }

    private suspend fun fetchAllSourcesInParallel(
        sources: List<PlaylistSource>,
    ): List<Channel> = withContext(ioDispatcher) {
        sources.map { source ->
            async {
                try {
                    val bytes  = fetchSourceBytes(source)
                    val result = m3uParser.parse(bytes)

                    val taggedChannels = result.channels
                        .map {
                            it.copy(
                                id       = "${source.id}_${it.tvgId.ifEmpty { it.streamUrl }}",
                                sourceId = source.id,
                            )
                        }
                        .distinctBy { it.id }

                    result.warnings.forEach { w ->
                        Log.w(TAG, "M3U line ${w.lineNumber}: ${w.reason}")
                    }

                    val updatedSource = source.copy(
                        channelCount = taggedChannels.size,
                        epgUrl       = result.epgUrl ?: source.epgUrl,
                    )
                    settingsDataStore.updateSource(updatedSource)
                    if (result.epgUrl != null) settingsDataStore.setEpgUrl(result.epgUrl)

                    channelCache.write(
                        sourceId  = source.id,
                        sourceUrl = source.url,
                        channels  = taggedChannels,
                    )
                    taggedChannels
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to load source ${source.name}", e)
                    _error.value = "Failed to load ${source.name}: ${e.message}"
                    emptyList()
                }
            }
        }.awaitAll().flatten()
    }

    private suspend fun fetchSourceBytes(source: PlaylistSource): ByteArray =
        if (source.url.startsWith("http")) {
            apiClient.fetchRawUrl(source.url)
        } else {
            apiClient.playlistData()
        }

    // ── Auto-refresh ──────────────────────────────────────────────────────────

    fun scheduleAutoRefresh() {
        autoRefreshJob?.cancel()
        autoRefreshJob = viewModelScope.launch {
            while (true) {
                val intervalMs = sourceViewModel.sources.value
                    .filter { it.refreshIntervalHours > 0 }
                    .minOfOrNull { it.refreshIntervalHours * 3_600_000L }
                    ?: FALLBACK_REFRESH_INTERVAL_MS

                delay(intervalMs)
                loadAll()
            }
        }
    }

    fun stopAutoRefresh() {
        autoRefreshJob?.cancel()
        autoRefreshJob = null
    }

    // ── Diagnostics ───────────────────────────────────────────────────────────

    fun logMatchDiagnostic(epgViewModel: EpgViewModel) {
        epgViewModel.logMatchDiagnostic(channelRepository.channels.value)
    }

    override fun onCleared() {
        super.onCleared()
        stopAutoRefresh()
    }
}