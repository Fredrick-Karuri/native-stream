/**
 * app/src/main/java/com/nativestream/android/ui/viewmodel/BrowseViewModel.kt
 *
 * Single responsibility: UI state for BrowseScreen.
 * Owns sheet visibility, search bar state, and the selected channel ID.
 * No business logic — delegates filter actions to ChannelFilterViewModel and
 * source actions to SourceViewModel.
 *
 * Also owns the selectedSource → deselect-channel side-effect that previously
 * lived as a LaunchedEffect in BrowseScreen.
 */

package com.nativestream.android.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nativestream.android.domain.model.isAll
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BrowseViewModel @Inject constructor(
    private val sourceViewModel: SourceViewModel,
) : ViewModel() {

    // ── Sheet visibility ──────────────────────────────────────────────────────

    private val _showAddChannel   = MutableStateFlow(false)
    private val _showPlayUrl      = MutableStateFlow(false)
    private val _showSourcePicker = MutableStateFlow(false)
    private val _showAddSource    = MutableStateFlow(false)

    val showAddChannel:   StateFlow<Boolean> = _showAddChannel.asStateFlow()
    val showPlayUrl:      StateFlow<Boolean> = _showPlayUrl.asStateFlow()
    val showSourcePicker: StateFlow<Boolean> = _showSourcePicker.asStateFlow()
    val showAddSource:    StateFlow<Boolean> = _showAddSource.asStateFlow()

    fun openAddChannel()    { _showAddChannel.value   = true  }
    fun closeAddChannel()   { _showAddChannel.value   = false }
    fun openPlayUrl()       { _showPlayUrl.value      = true  }
    fun closePlayUrl()      { _showPlayUrl.value      = false }
    fun openSourcePicker()  { _showSourcePicker.value = true  }
    fun closeSourcePicker() { _showSourcePicker.value = false }

    /** Closing the source picker and opening add-source are always paired. */
    fun navigateToAddSource() {
        _showSourcePicker.value = false
        _showAddSource.value    = true
    }
    fun closeAddSource() { _showAddSource.value = false }

    // ── Search bar ────────────────────────────────────────────────────────────

    private val _searchActive = MutableStateFlow(false)
    private val _searchText   = MutableStateFlow("")

    val searchActive: StateFlow<Boolean> = _searchActive.asStateFlow()
    val searchText:   StateFlow<String>  = _searchText.asStateFlow()

    fun activateSearch() { _searchActive.value = true }

    fun closeSearch(onClearQuery: () -> Unit) {
        _searchActive.value = false
        _searchText.value   = ""
        onClearQuery()
    }

    fun setSearchText(text: String, onQueryChange: (String) -> Unit) {
        _searchText.value = text
        onQueryChange(text)
    }

    // ── Selected channel ──────────────────────────────────────────────────────

    private val _selectedChannelId = MutableStateFlow<String?>(null)
    val selectedChannelId: StateFlow<String?> = _selectedChannelId.asStateFlow()

    fun selectChannel(id: String?) { _selectedChannelId.value = id }

    // ── Side-effects ──────────────────────────────────────────────────────────

    init {
        // Deselect channel when the user switches to a source it doesn't belong to.
        // Mirrors the LaunchedEffect(selectedSource) that previously lived in BrowseScreen.
        viewModelScope.launch {
            sourceViewModel.selectedSource.collect { source ->
                val currentId = _selectedChannelId.value ?: return@collect
                if (source != null && !source.isAll) {
                    // Channel ID format is "{sourceId}_{tvgId|streamUrl}" — prefix check is safe
                    if (!currentId.startsWith(source.id)) {
                        _selectedChannelId.value = null
                    }
                }
            }
        }
    }
}