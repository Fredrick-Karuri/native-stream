/**
 * app/src/main/java/com/nativestream/android/ui/screens/browse/BrowseScreen.kt
 *
 * Browse screen — wired to the post-SRP ViewModel split.
 * UI state       → BrowseViewModel
 * Filter state   → ChannelFilterViewModel
 * Source CRUD    → SourceViewModel
 * Loading state  → ChannelLoadingViewModel
 *
 */

package com.nativestream.android.ui.screens.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.windowsizeclass.WindowHeightSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
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
import com.nativestream.android.ui.viewmodel.BrowseViewModel
import com.nativestream.android.ui.viewmodel.ChannelFilterViewModel
import com.nativestream.android.ui.viewmodel.ChannelLoadingViewModel
import com.nativestream.android.ui.viewmodel.EpgViewModel
import com.nativestream.android.ui.viewmodel.FavouritesViewModel
import com.nativestream.android.ui.viewmodel.PlayerViewModel
import com.nativestream.android.ui.viewmodel.SourceViewModel
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nativestream.android.domain.model.isAll
import com.nativestream.android.ui.LocalWindowSizeClass
import com.nativestream.android.ui.components.NSSourcePickerSheet
import com.nativestream.android.ui.components.AddSourceSheet
import kotlinx.coroutines.delay

val Regular = RegularGroup
data class ChannelSection(val name: String, val channels: List<Channel>)

@Composable
fun BrowseScreen(
    playerViewModel: PlayerViewModel,
    modifier: Modifier = Modifier,
    browseViewModel:   BrowseViewModel          = hiltViewModel(),
    filterViewModel:   ChannelFilterViewModel   = hiltViewModel(),
    sourceViewModel:   SourceViewModel          = hiltViewModel(),
    loadingViewModel:  ChannelLoadingViewModel  = hiltViewModel(),
    epgViewModel:      EpgViewModel             = hiltViewModel(),
    favouritesViewModel: FavouritesViewModel    = hiltViewModel(),
) {
    // ── Loading / refresh state ───────────────────────────────────────────────
    val isLoading    by loadingViewModel.isLoading.collectAsState()
    val isRefreshing by loadingViewModel.isRefreshing.collectAsState()

    // ── Source state ──────────────────────────────────────────────────────────
    val sources        by sourceViewModel.sources.collectAsState()
    val selectedSource by sourceViewModel.selectedSource.collectAsState()

    // ── Filter state ──────────────────────────────────────────────────────────
    val filteredSections  by filterViewModel.filteredSections.collectAsState()
    val selectedGroup     by filterViewModel.selectedGroup.collectAsState()
    val selectedSubGroup  by filterViewModel.selectedSubGroup.collectAsState()
    val selectedSport     by filterViewModel.selectedSport.collectAsState()
    val showFavouritesOnly by filterViewModel.showFavouritesOnly.collectAsState()
    val subGroups         by filterViewModel.subGroups.collectAsState()
    val filteredChannels  by filterViewModel.filteredChannels.collectAsState()

    // ── UI state ──────────────────────────────────────────────────────────────
    val showAddChannel   by browseViewModel.showAddChannel.collectAsState()
    val showPlayUrl      by browseViewModel.showPlayUrl.collectAsState()
    val showSourcePicker by browseViewModel.showSourcePicker.collectAsState()
    val showAddSource    by browseViewModel.showAddSource.collectAsState()
    val searchActive     by browseViewModel.searchActive.collectAsState()
    val searchText       by browseViewModel.searchText.collectAsState()
    val selectedChannelId by browseViewModel.selectedChannelId.collectAsState()

    val selectedChannel = remember(selectedChannelId, filteredChannels) {
        filteredChannels.find { it.id == selectedChannelId }
    }

    // ── Derived ───────────────────────────────────────────────────────────────
    val groups by remember {
        derivedStateOf {
            filteredChannels.map { it.groupTitle }.distinct().sorted()
        }
    }
    val activeSports by remember {
        derivedStateOf {
            if (selectedGroup?.lowercase()?.contains("sport") == true)
                epgViewModel.activeSports(filteredChannels)
            else emptyList()
        }
    }

    // ── Sync favourites into filter VM ────────────────────────────────────────
    val favouriteIds by favouritesViewModel.favouriteIds.collectAsState()
    LaunchedEffect(favouriteIds) {
        filterViewModel.updateFavouriteIds(favouriteIds)
    }

    LaunchedEffect(selectedSource) {
        val source = selectedSource ?: return@LaunchedEffect
        val currentId = browseViewModel.selectedChannelId.value ?: return@LaunchedEffect
        if (!source.isAll && !currentId.startsWith(source.id)) {
            browseViewModel.selectChannel(null)
        }
    }

    // ── Layout ────────────────────────────────────────────────────────────────
    val windowSizeClass = LocalWindowSizeClass.current
    val isTablet = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Expanded
            && windowSizeClass.heightSizeClass != WindowHeightSizeClass.Compact

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(NSColors.bg),
        ) {
            BrowseTopBar(
                searchActive   = searchActive,
                searchText     = searchText,
                onSearchClick  = { browseViewModel.activateSearch() },
                onSearchChange = { browseViewModel.setSearchText(it) { text -> filterViewModel.setSearchQuery(text) } },
                onSearchClose  = { browseViewModel.closeSearch { filterViewModel.setSearchQuery("") } },
                onPlayUrl      = { browseViewModel.openPlayUrl() },
                onAddChannel   = { browseViewModel.openAddChannel() },
                selectedSource = selectedSource,
                onSourceClick  = { browseViewModel.openSourcePicker() },
                isRefreshing   = isRefreshing,
            )
            Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(NSColors.border))

            if (!isTablet) BrowseFilterRow(
                sources            = sources,
                selectedSource     = selectedSource,
                groups             = groups,
                selectedGroup      = selectedGroup,
                activeSports       = activeSports,
                selectedSport      = selectedSport,
                onPillClick        = { browseViewModel.openSourcePicker() },
                onSelectAll        = { filterViewModel.clearFilters() },
                onSelectGroup      = { filterViewModel.setSelectedGroup(it) },
                onSelectSubGroup   = { filterViewModel.setSelectedSubGroup(it) },
                onSelectSport      = { filterViewModel.setSelectedSport(it) },
                onToggleFavourites = { filterViewModel.toggleFavourites() },
                showFavouritesOnly = showFavouritesOnly,
                subGroups          = subGroups,
                selectedSubGroup   = selectedSubGroup,
            )
            Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(NSColors.border))

            var showEmpty by remember { mutableStateOf(false) }
            LaunchedEffect(filteredSections) {
                if (filteredSections.isEmpty()) {
                    delay(800)
                    showEmpty = true
                } else {
                    showEmpty = false
                }
            }
            when {
                isLoading -> BrowseLoadingView()
                isTablet  -> BrowseMasterDetail(
                    sections            = filteredSections,
                    isEmptyState        = filteredSections.isEmpty(),
                    emptySearchText     = searchText,
                    selectedChannel     = selectedChannel,
                    onSelectChannel     = { browseViewModel.selectChannel(it.id) },
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
                    onPillClick         = { browseViewModel.openSourcePicker() },
                    onSelectAll         = { filterViewModel.clearFilters() },
                    onSelectGroup       = { filterViewModel.setSelectedGroup(it) },
                    onSelectSubGroup    = { filterViewModel.setSelectedSubGroup(it) },
                    onSelectSport       = { filterViewModel.setSelectedSport(it) },
                    onToggleFavourites  = { filterViewModel.toggleFavourites() },
                    showFavouritesOnly  = showFavouritesOnly,
                )
                showEmpty -> BrowseEmptyView(searchText)
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
                onDismiss       = { browseViewModel.closePlayUrl() },
                onPlay          = { playerViewModel.showPlayer() },
            )
        }
        if (showAddSource) {
            AddSourceSheet(
                onDone           = { browseViewModel.closeAddSource() },
                sourceViewModel  = sourceViewModel,
                loadingViewModel = loadingViewModel,
            )
        }
        if (showSourcePicker) {
            NSSourcePickerSheet(
                sources        = sources,
                selectedSource = selectedSource,
                onSelectSource = { sourceViewModel.selectSource(it) },
                onAddPlaylist  = { browseViewModel.navigateToAddSource() },
                onDismiss      = { browseViewModel.closeSourcePicker() },
            )
        }
    }

    if (showAddChannel) {
        AddChannelSheet(
            onDone           = { browseViewModel.closeAddChannel() },
            loadingViewModel = loadingViewModel,
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