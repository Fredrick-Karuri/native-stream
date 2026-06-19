// app/src/main/java/com/nativestream/android/ui/screens/browse/BrowseScreen.kt
//
// Browse Screen
// - Top bar: "Browse" title + search icon (mobile style)
// - Chips: icon-above-label square pills matching design
// - Grid: real ChannelCard, adaptive 2-column, correct height

package com.nativestream.android.ui.screens.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.windowsizeclass.WindowHeightSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.runtime.derivedStateOf
import com.adamglin.phosphoricons.RegularGroup
import com.adamglin.phosphoricons.regular.Basketball
import com.adamglin.phosphoricons.regular.Football
import com.adamglin.phosphoricons.regular.Cricket
import com.adamglin.phosphoricons.regular.Golf
import com.adamglin.phosphoricons.regular.SoccerBall
import com.adamglin.phosphoricons.regular.TennisBall
import com.nativestream.android.domain.model.Channel
import com.nativestream.android.domain.model.SportCategory
import com.nativestream.android.ui.theme.NSColors
import com.nativestream.android.ui.viewmodel.EpgViewModel
import com.nativestream.android.ui.viewmodel.FavouritesViewModel
import com.nativestream.android.ui.viewmodel.PlaylistViewModel
import com.nativestream.android.ui.viewmodel.PlayerViewModel
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import com.nativestream.android.ui.LocalWindowSizeClass
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.LaunchedEffect
import com.nativestream.android.ui.components.NSSourcePickerSheet
import com.nativestream.android.domain.model.isAll
import com.nativestream.android.ui.components.AddSourceSheet

val Regular = RegularGroup
data class ChannelSection(val name: String, val channels: List<Channel>)

@Composable
fun BrowseScreen(
    playerViewModel: PlayerViewModel,
    modifier: Modifier = Modifier,
    playlistViewModel: PlaylistViewModel  = hiltViewModel(),
    epgViewModel: EpgViewModel            = hiltViewModel(),
    favouritesViewModel: FavouritesViewModel = hiltViewModel(),
) {
    val channels by playlistViewModel.filteredChannels.collectAsState()
    val isLoading by playlistViewModel.isLoading.collectAsState()

    var showAddChannel by remember { mutableStateOf(false) }

    var searchActive by remember { mutableStateOf(false) }

    var showPlayUrl by remember { mutableStateOf(false) }

    var selectedChannelId by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedChannel = channels.find { it.id == selectedChannelId }

    var showSourcePicker by remember { mutableStateOf(false) }
    val sources          by playlistViewModel.sources.collectAsState()
    val selectedSource   by playlistViewModel.selectedSource.collectAsState()

    val subGroups        by playlistViewModel.subGroups.collectAsState()

    var showAddSource by remember { mutableStateOf(false) }

    val filteredSections  by playlistViewModel.filteredSections.collectAsState()
    val selectedGroup     by playlistViewModel.selectedGroup.collectAsState()
    val selectedSubGroup  by playlistViewModel.selectedSubGroup.collectAsState()
    val selectedSport     by playlistViewModel.selectedSport.collectAsState()
    val showFavouritesOnly by playlistViewModel.showFavouritesOnly.collectAsState()
    var searchText        by remember { mutableStateOf("") }  // local only for text field
    val favouriteIds      by favouritesViewModel.favouriteIds.collectAsState()

    val groups by remember {
        derivedStateOf {
            channels.map { it.groupTitle }.distinct().sorted()
        }
    }
    val activeSports by remember {
        derivedStateOf {
            if (selectedGroup?.lowercase()?.contains("sport") == true)
                epgViewModel.activeSports(channels)
            else emptyList()
        }
    }
    val isRefreshing by playlistViewModel.isRefreshing.collectAsState()

    // Deselect channel if it no longer belongs to the newly selected source
    LaunchedEffect(selectedSource) {
        val source = selectedSource
        val current = channels.find { it.id == selectedChannelId }
        if (current != null && source != null && !source.isAll) {
            if (current.sourceId != source.id) selectedChannelId = null
        }
    }
    LaunchedEffect(favouriteIds) {
        playlistViewModel.updateFavouriteIds(favouriteIds)
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = modifier
            .fillMaxSize()
            .background(NSColors.bg)
        ) {

            // ── Top bar — mobile style: title + search icon ───────────────────────
            BrowseTopBar(
                searchActive   = searchActive,
                searchText     = searchText,
                onSearchClick  = { searchActive = true },
                onSearchChange = { searchText = it; playlistViewModel.setSearchQuery(it) },
                onSearchClose  = { searchActive = false; searchText = ""; playlistViewModel.setSearchQuery("") },
                onPlayUrl      = { showPlayUrl = true },
                onAddChannel   = { showAddChannel = true },
                selectedSource = selectedSource,
                onSourceClick  = { showSourcePicker = true },
                isRefreshing   = isRefreshing,
            )
            Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(NSColors.border))

            // ── Sport + group chips — hidden on Expanded (lives in list pane instead) ──
            val windowSizeClass = LocalWindowSizeClass.current
            val isTablet = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Expanded
                    && windowSizeClass.heightSizeClass != WindowHeightSizeClass.Compact
            val useDetail = isTablet

            if (!useDetail) BrowseFilterRow(
                sources        = sources,
                selectedSource = selectedSource,
                groups         = groups,
                selectedGroup  = selectedGroup,
                activeSports   = activeSports,
                selectedSport  = selectedSport,
                onPillClick    = { showSourcePicker = true },
                onSelectAll        = { playlistViewModel.clearFilters() },
                onSelectGroup      = { playlistViewModel.setSelectedGroup(it) },
                onSelectSubGroup   = { playlistViewModel.setSelectedSubGroup(it) },
                onSelectSport      = { playlistViewModel.setSelectedSport(it) },
                onToggleFavourites = { playlistViewModel.toggleFavourites() },
                showFavouritesOnly  = showFavouritesOnly,
                subGroups        = subGroups,
                selectedSubGroup = selectedSubGroup,
            )
            Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(NSColors.border))
            when {
                isLoading -> BrowseLoadingView()
                useDetail -> BrowseMasterDetail(
                    sections = filteredSections,
                    isEmptyState    = filteredSections.isEmpty(),
                    emptySearchText     = searchText,
                    selectedChannel     = selectedChannel,
                    onSelectChannel     = { selectedChannelId = it.id },
                    playerViewModel     = playerViewModel,
                    epgViewModel        = epgViewModel,
                    favouritesViewModel = favouritesViewModel,
                    sources             = sources,
                    selectedSource      = selectedSource,
                    groups              = groups,
                    selectedGroup       = selectedGroup,
                    subGroups           = subGroups,
                    selectedSubGroup    = selectedSubGroup,
                    activeSports        = activeSports,
                    selectedSport       = selectedSport,
                    onPillClick         = { showSourcePicker = true },
                    onSelectAll        = { playlistViewModel.clearFilters() },
                    onSelectGroup      = { playlistViewModel.setSelectedGroup(it) },
                    onSelectSubGroup   = { playlistViewModel.setSelectedSubGroup(it) },
                    onSelectSport      = { playlistViewModel.setSelectedSport(it) },
                    onToggleFavourites = { playlistViewModel.toggleFavourites() },
                    showFavouritesOnly  = showFavouritesOnly,
                )
                filteredSections.isEmpty() -> BrowseEmptyView(searchText)
                else -> BrowseGrid(
                    sections            = filteredSections,
                    playerViewModel     = playerViewModel,
                    epgViewModel        = epgViewModel,
                    favouritesViewModel = favouritesViewModel,
                )
            }
        }

        if (showPlayUrl) {
            PlayURLSheet(
                playerViewModel = playerViewModel,
                onDismiss = { showPlayUrl = false },
                onPlay    = { playerViewModel.showPlayer() },
            )
        }
        if (showAddSource) {
            AddSourceSheet(
                onDone            = { showAddSource = false },
                playlistViewModel = playlistViewModel,
            )
        }
        if (showSourcePicker) {
            NSSourcePickerSheet(
                sources        = sources,
                selectedSource = selectedSource,
                onSelectSource = { playlistViewModel.selectSource(it) },
                onAddPlaylist  = { showSourcePicker = false; showAddSource = true },
                onDismiss      = { showSourcePicker = false },
            )
        }
    }

    if (showAddChannel) {
        AddChannelSheet(
            onDone            = { showAddChannel = false },
            playlistViewModel = playlistViewModel,
        )
    }
}

// ── Sport icon mapping ────────────────────────────────────────────────────────

private fun SportCategory.chipIcon() = when (this) {
    SportCategory.FOOTBALL   -> Regular.SoccerBall
    SportCategory.RUGBY      -> Regular.Football
    SportCategory.TENNIS     -> Regular.TennisBall
    SportCategory.BASKETBALL -> Regular.Basketball
    SportCategory.CRICKET    -> Regular.Cricket
    SportCategory.GOLF       -> Regular.Golf
}