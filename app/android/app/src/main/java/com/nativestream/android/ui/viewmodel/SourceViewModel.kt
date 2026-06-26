/**
 * app/src/main/java/com/nativestream/android/ui/viewmodel/SourceViewModel.kt
 *
 * Single responsibility: playlist source CRUD and the currently selected source.
 * Dependencies: SettingsDataStore (persistence) and ChannelCache (clearing stale
 * cache on source removal). No network, no channel loading, no filter logic.
 *
 */

package com.nativestream.android.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nativestream.android.data.local.ChannelCache
import com.nativestream.android.data.local.SettingsDataStore
import com.nativestream.android.domain.model.PlaylistSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SourceViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    private val channelCache: ChannelCache,
) : ViewModel() {

    private val _sources = MutableStateFlow<List<PlaylistSource>>(emptyList())
    val sources: StateFlow<List<PlaylistSource>> = _sources.asStateFlow()

    private val _selectedSource = MutableStateFlow<PlaylistSource?>(null)
    val selectedSource: StateFlow<PlaylistSource?> = _selectedSource.asStateFlow()

    init {
        viewModelScope.launch {
            settingsDataStore.sources.collect { stored ->
                _sources.value = stored
            }
        }
        viewModelScope.launch {
            val savedId = settingsDataStore.selectedSourceId.first()
            if (savedId.isNotEmpty()) {
                // Wait until sources are populated before resolving the saved selection
                _sources.first { it.isNotEmpty() }
                _selectedSource.value = _sources.value.find { it.id == savedId }
            }
        }
    }

    fun selectSource(source: PlaylistSource?) {
        _selectedSource.value = source
        viewModelScope.launch {
            settingsDataStore.setSelectedSourceId(source?.id ?: "")
        }
    }

    fun addSource(source: PlaylistSource) {
        viewModelScope.launch {
            settingsDataStore.addSource(source)
        }
    }

    /** Removes the source and clears its cached channels. */
    fun removeSource(id: String) {
        viewModelScope.launch {
            settingsDataStore.removeSource(id)
            channelCache.clear(id)
        }
    }

    fun updateSource(source: PlaylistSource) {
        viewModelScope.launch {
            settingsDataStore.updateSource(source)
        }
    }
}