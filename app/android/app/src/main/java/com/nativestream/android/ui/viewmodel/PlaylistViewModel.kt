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
import com.nativestream.android.domain.model.isAll
import com.nativestream.android.ui.screens.browse.ChannelSection
import com.nativestream.android.domain.model.SportCategory
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.FlowPreview
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Named
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first

private const val TAG = "PlaylistViewModel"
private const val FALLBACK_REFRESH_INTERVAL_MS = 3_600_000L // 1 hour
private const val SEARCH_DEBOUNCE_MS = 150L

@HiltViewModel
class PlaylistViewModel @Inject constructor(
    private val apiClient: ApiClient,
    private val m3uParser: M3uParser,
    private val settingsDataStore: SettingsDataStore,
    @Named("io") private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
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

    private val _selectedSource = MutableStateFlow<PlaylistSource?>(null)
    val selectedSource: StateFlow<PlaylistSource?> = _selectedSource.asStateFlow()

    // ── Filter state ──────────────────────────────────────────────────────────────
    private val _searchQuery       = MutableStateFlow("")
    private val _selectedGroup     = MutableStateFlow<String?>(null)
    private val _selectedSubGroup  = MutableStateFlow<String?>(null)
    private val _selectedSport     = MutableStateFlow<SportCategory?>(null)
    private val _showFavouritesOnly = MutableStateFlow(false)
    private val _favouriteIds      = MutableStateFlow<Set<String>>(emptySet())

    val searchQuery:        StateFlow<String>         = _searchQuery.asStateFlow()
    val selectedGroup:      StateFlow<String?>        = _selectedGroup.asStateFlow()
    val selectedSubGroup:   StateFlow<String?>        = _selectedSubGroup.asStateFlow()
    val selectedSport:      StateFlow<SportCategory?> = _selectedSport.asStateFlow()
    val showFavouritesOnly: StateFlow<Boolean>        = _showFavouritesOnly.asStateFlow()

    val filteredChannels: StateFlow<List<Channel>> = combine(
        _channels,
        _selectedSource,
    ) { channels, source ->
        if (source == null) channels
        else channels.filter { it.sourceId == source.id }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    // ── Computed ──────────────────────────────────────────────────────────────

    val groups: Map<String, List<Channel>>
        get() = _channels.value.groupBy { it.groupTitle }

    val sortedGroupNames: List<String>
        get() = groups.keys.sorted()

    val subGroups: StateFlow<List<String>> = combine(
        _channels,
        _selectedSource,
    ) { channels, source ->
        val sourceChannels = if (source == null || source.isAll) channels
        else channels.filter { it.sourceId == source.id }
        sourceChannels.map { it.subGroupTitle }.filter { it.isNotEmpty() }.distinct().sorted()
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    @OptIn(FlowPreview::class)
    val filteredSections: StateFlow<List<ChannelSection>> = combine(
        filteredChannels,
        _searchQuery.debounce(SEARCH_DEBOUNCE_MS).distinctUntilChanged(),
        _selectedGroup,
        _selectedSubGroup,
        _selectedSport,
        _showFavouritesOnly,
        _favouriteIds,
    ) { args ->
        @Suppress("UNCHECKED_CAST")
        val channels        = args[0] as List<Channel>
        val query           = args[1] as String
        val group           = args[2] as String?
        val subGroup        = args[3] as String?
        val sport           = args[4] as SportCategory?
        val favsOnly        = args[5] as Boolean
        val favIds          = args[6] as Set<String>

        val filtered = channels
            .filter { if (group != null) it.groupTitle == group else true }
            .filter { if (subGroup != null) it.subGroupTitle == subGroup else true }
            .filter { if (sport != null) it.groupTitle.contains(sport.label, ignoreCase = true) else true }
            .filter { if (query.isNotEmpty()) it.name.contains(query, ignoreCase = true) else true }
            .filter { if (favsOnly) favIds.contains(it.id) else true }

        val grouped = filtered.groupBy { it.groupTitle }
        val sorted  = grouped.keys.sorted().map { ChannelSection(it, grouped[it]!!) }
        if (group != null) sorted.filter { it.name == group } else sorted
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    // ── Auto-refresh ──────────────────────────────────────────────────────────

    private var autoRefreshJob: Job? = null

    init {
        viewModelScope.launch {
            settingsDataStore.sources.collect { stored ->
                _sources.value = stored
                if (stored.isNotEmpty()) loadAll()
            }
        }
        viewModelScope.launch {
            val savedId = settingsDataStore.selectedSourceId.first()
            if (savedId.isNotEmpty()) {
                _sources.first { it.isNotEmpty() } // wait for sources to populate
                _selectedSource.value = _sources.value.find { it.id == savedId }
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
            } finally {
                _isLoading.value = false
            }
        }
    }

    /** Fetch channels from all configured sources in parallel on background thread pools. */
    private suspend fun fetchAllSourcesInParallel(): List<Channel> = withContext(ioDispatcher) {
        _sources.value.map { source ->
            async {
                try {
                    val bytes = fetchSourceBytes(source)
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
                    taggedChannels
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to load source ${source.name}", e)
                    // Safe UI string update from background context
                    _error.value = "Failed to load ${source.name}: ${e.message}"
                    emptyList()
                }
            }
        }.awaitAll().flatten()
    }

    private suspend fun fetchSourceBytes(source: PlaylistSource): ByteArray {
        return if (source.url.startsWith("http")) {
            apiClient.fetchRawUrl(source.url)
        } else {
            apiClient.playlistData()
        }
    }

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
    fun setSearchQuery(query: String)      { _searchQuery.value = query }
    fun setSelectedGroup(group: String?)   { _selectedGroup.value = group; _selectedSubGroup.value = null; _selectedSport.value = null; _showFavouritesOnly.value = false }
    fun setSelectedSubGroup(sub: String?)  { _selectedSubGroup.value = sub }
    fun setSelectedSport(sport: SportCategory?) { _selectedSport.value = sport }
    fun toggleFavourites()                 { if (!_showFavouritesOnly.value) { _showFavouritesOnly.value = true; _selectedGroup.value = null; _selectedSport.value = null } else { _showFavouritesOnly.value = false } }
    fun clearFilters()                     { _selectedGroup.value = null; _selectedSubGroup.value = null; _selectedSport.value = null; _showFavouritesOnly.value = false; _searchQuery.value = "" }
    fun updateFavouriteIds(ids: Set<String>) { _favouriteIds.value = ids }

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

    fun selectSource(source: PlaylistSource?) {
        _selectedSource.value = source
        viewModelScope.launch {
            settingsDataStore.setSelectedSourceId(source?.id ?: "")
        }
    }


    /**
     * Logs match rate + unmatched channels to Logcat on every cold start.
     * Called by the caller after both playlist + EPG have loaded.
     */
    fun logMatchDiagnostic(epgViewModel: EpgViewModel) {
        epgViewModel.logMatchDiagnostic(_channels.value)
    }


}