/**
 * app/src/main/java/com/nativestream/android/ui/viewmodel/ChannelFilterViewModel.kt
 *
 * Single responsibility: filter state and derived section computation.
 * Reads channels from ChannelRepository (no network, no disk).
 * Reads selectedSource from SourceViewModel (no CRUD).
 * Zero network or cache dependencies — fully unit-testable in isolation.
 *
 * Output: filteredSections, filteredChannels, subGroups.
 * Input:  setSearchQuery / setSelectedGroup / setSelectedSubGroup /
 *         setSelectedSport / toggleFavourites / clearFilters / updateFavouriteIds
 */

package com.nativestream.android.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nativestream.android.domain.model.Channel
import com.nativestream.android.domain.model.SportCategory
import com.nativestream.android.domain.model.isAll
import com.nativestream.android.domain.repository.ChannelRepository
import com.nativestream.android.ui.screens.browse.ChannelSection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

private const val SEARCH_DEBOUNCE_MS = 150L

@HiltViewModel
class ChannelFilterViewModel @Inject constructor(
    private val channelRepository: ChannelRepository,
    private val sourceViewModel: SourceViewModel,
) : ViewModel() {

    // ── Filter inputs ─────────────────────────────────────────────────────────

    private val _searchQuery        = MutableStateFlow("")
    private val _selectedGroup      = MutableStateFlow<String?>(null)
    private val _selectedSubGroup   = MutableStateFlow<String?>(null)
    private val _selectedSport      = MutableStateFlow<SportCategory?>(null)
    private val _showFavouritesOnly = MutableStateFlow(false)
    private val _favouriteIds       = MutableStateFlow<Set<String>>(emptySet())

    val searchQuery:         StateFlow<String>          = _searchQuery.asStateFlow()
    val selectedGroup:       StateFlow<String?>         = _selectedGroup.asStateFlow()
    val selectedSubGroup:    StateFlow<String?>         = _selectedSubGroup.asStateFlow()
    val selectedSport:       StateFlow<SportCategory?>  = _selectedSport.asStateFlow()
    val showFavouritesOnly:  StateFlow<Boolean>         = _showFavouritesOnly.asStateFlow()

    // ── Source-filtered channel list ──────────────────────────────────────────

    val filteredChannels: StateFlow<List<Channel>> = combine(
        channelRepository.channels,
        sourceViewModel.selectedSource,
    ) { channels, source ->
        if (source == null || source.isAll) channels
        else channels.filter { it.sourceId == source.id }
    }.stateIn(
        scope          = viewModelScope,
        started        = SharingStarted.WhileSubscribed(5_000),
        initialValue   = emptyList(),
    )

    // ── Sub-groups derived from source-filtered channels ──────────────────────

    val subGroups: StateFlow<List<String>> = filteredChannels
        .combine(filteredChannels) { channels, _ ->
            channels.map { it.subGroupTitle }
                .filter { it.isNotEmpty() }
                .distinct()
                .sorted()
        }
        .stateIn(
            scope        = viewModelScope,
            started      = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    // ── Fully filtered sections ───────────────────────────────────────────────

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
        val channels  = args[0] as List<Channel>
        val query     = args[1] as String
        val group     = args[2] as String?
        val subGroup  = args[3] as String?
        val sport     = args[4] as SportCategory?
        val favsOnly  = args[5] as Boolean
        val favIds    = args[6] as Set<String>

        val filtered = channels
            .filter { if (group    != null) it.groupTitle.equals(group, ignoreCase = true) else true }
            .filter { if (subGroup != null) it.subGroupTitle == subGroup else true }
            .filter { if (sport    != null) it.groupTitle.contains(sport.label, ignoreCase = true) else true }
            .filter { if (query.isNotEmpty()) it.name.contains(query, ignoreCase = true) else true }
            .filter { if (favsOnly) favIds.contains(it.id) else true }

        val grouped = filtered.groupBy { it.groupTitle }
        val sorted  = grouped.keys.sorted().map { ChannelSection(it, grouped[it]!!) }

        if (group != null) sorted.filter { it.name.equals(group, ignoreCase = true) } else sorted
    }.stateIn(
        scope        = viewModelScope,
        started      = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    // ── Filter actions ────────────────────────────────────────────────────────

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSelectedGroup(group: String?) {
        _selectedGroup.value      = group
        _selectedSubGroup.value   = null
        _selectedSport.value      = null
        _showFavouritesOnly.value = false
    }

    fun setSelectedSubGroup(subGroup: String?) {
        _selectedSubGroup.value = subGroup
    }

    fun setSelectedSport(sport: SportCategory?) {
        _selectedSport.value = sport
    }

    fun toggleFavourites() {
        if (!_showFavouritesOnly.value) {
            _showFavouritesOnly.value = true
            _selectedGroup.value      = null
            _selectedSport.value      = null
        } else {
            _showFavouritesOnly.value = false
        }
    }

    fun clearFilters() {
        _selectedGroup.value      = null
        _selectedSubGroup.value   = null
        _selectedSport.value      = null
        _showFavouritesOnly.value = false
        _searchQuery.value        = ""
    }

    fun updateFavouriteIds(ids: Set<String>) {
        _favouriteIds.value = ids
    }
}