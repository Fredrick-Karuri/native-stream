// app/src/main/java/com/nativestream/android/ui/viewmodel/PlaylistViewModel.kt
//
// Playlist ViewModel
// Owns the channel list, loading state, and auto-refresh scheduling.
// Has parallel source loading,
// auto-refresh via coroutine, source CRUD backed by SettingsDataStore.

package com.nativestream.android.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nativestream.android.data.local.SettingsDataStore
import com.nativestream.android.data.parser.M3uParser
import com.nativestream.android.data.remote.ApiClient
import com.nativestream.android.domain.model.Channel
import com.nativestream.android.domain.model.PlaylistSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "PlaylistViewModel"
private const val FALLBACK_REFRESH_INTERVAL_MS = 3_600_000L // 1 hour

@HiltViewModel
class PlaylistViewModel @Inject constructor(
    private val apiClient: ApiClient,
    private val m3uParser: M3uParser,
    private val settingsDataStore: SettingsDataStore,
) : ViewModel() {

    // ── Public state ──────────────────────────────────────────────────────────

    private val _channels = MutableStateFlow<List<Channel>>(emptyList())
    val channels: StateFlow<List<Channel>> = _channels.asStateFlow()

    private val _sources = MutableStateFlow<List<PlaylistSource>>(emptyList())
    val sources: StateFlow<List<PlaylistSource>> = _sources.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // ── Computed ──────────────────────────────────────────────────────────────

    val groups: Map<String, List<Channel>>
        get() = _channels.value.groupBy { it.groupTitle }

    val sortedGroupNames: List<String>
        get() = groups.keys.sorted()

    // ── Auto-refresh ──────────────────────────────────────────────────────────

    private var autoRefreshJob: Job? = null

    init {
        viewModelScope.launch {
            settingsDataStore.sources.collect { stored ->
                _sources.value = stored
            }
        }
    }

    // ── Load ──────────────────────────────────────────────────────────────────

    /** Fetch channels from all configured sources in parallel. */
    fun loadAll() {
        if (_isLoading.value) return
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            try {
                val allChannels = fetchAllSourcesInParallel()
                _channels.value = allChannels
            } catch (e: Exception) {
                _error.value = e.message
                Log.e(TAG, "loadAll failed", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun fetchAllSourcesInParallel(): List<Channel> = coroutineScope {
        _sources.value.map { source ->
            async {
                try {
                    val bytes = fetchSourceBytes(source)
                    val result = m3uParser.parse(bytes)
                    result.warnings.forEach { w ->
                        Log.w(TAG, "M3U line ${w.lineNumber}: ${w.reason}")
                    }
                    // If the playlist advertised an EPG URL and the source has none, save it
                    if (result.epgUrl != null && source.url.isEmpty()) {
                        updateSource(source.copy(url = result.epgUrl))
                    }
                    result.channels
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to load source ${source.name}", e)
                    _error.value = "Failed to load ${source.name}: ${e.message}"
                    emptyList()
                }
            }
        }.awaitAll().flatten()
    }

    private suspend fun fetchSourceBytes(source: PlaylistSource): ByteArray =
        apiClient.playlistData()

    // ── Auto-refresh scheduling (NS-032) ──────────────────────────────────────

    fun scheduleAutoRefresh() {
        autoRefreshJob?.cancel()
        autoRefreshJob = viewModelScope.launch {
            while (true) {
                val intervalMs = _sources.value
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

    // ── Source CRUD ───────────────────────────────────────────────────────────

    fun addSource(source: PlaylistSource) {
        viewModelScope.launch {
            settingsDataStore.addSource(source)
        }
    }

    fun removeSource(id: String) {
        viewModelScope.launch {
            settingsDataStore.removeSource(id)
        }
    }

    fun updateSource(source: PlaylistSource) {
        viewModelScope.launch {
            settingsDataStore.updateSource(source)
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopAutoRefresh()
    }


    /**
     * AND-026: EPG TVG-ID match diagnostic.
     * Logs match rate + unmatched channels to Logcat on every cold start.
     * Called by the caller after both playlist + EPG have loaded.
     */
    fun logMatchDiagnostic(epgViewModel: EpgViewModel) {
        epgViewModel.logMatchDiagnostic(_channels.value)
    }
}