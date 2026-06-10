// app/src/main/java/com/nativestream/android/ui/screens/browse/BrowseScreen.kt
//
// Browse Screen
// - Top bar: "Browse" title + search icon (mobile style)
// - Chips: icon-above-label square pills matching design
// - Grid: real ChannelCard, adaptive 2-column, correct height

package com.nativestream.android.ui.screens.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.adamglin.phosphoricons.RegularGroup
import com.adamglin.phosphoricons.regular.Basketball
import com.adamglin.phosphoricons.regular.Football
import com.adamglin.phosphoricons.regular.Cricket
import com.adamglin.phosphoricons.regular.Golf
import com.adamglin.phosphoricons.regular.SoccerBall
import com.adamglin.phosphoricons.regular.Star
import com.adamglin.phosphoricons.regular.TennisBall
import com.nativestream.android.domain.model.Channel
import com.nativestream.android.domain.model.SportCategory
import com.nativestream.android.ui.components.NSGroupHeader
import com.nativestream.android.ui.components.NSIconButton
import com.nativestream.android.ui.components.NSTextField
import com.nativestream.android.ui.theme.NSColors
import com.nativestream.android.ui.theme.NSDimens
import com.nativestream.android.ui.theme.NSType
import com.nativestream.android.ui.viewmodel.EpgViewModel
import com.nativestream.android.ui.viewmodel.FavouritesViewModel
import com.nativestream.android.ui.viewmodel.PlaylistViewModel
import com.nativestream.android.ui.viewmodel.PlayerViewModel
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import com.nativestream.android.ui.LocalWindowSizeClass
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.LaunchedEffect
import com.nativestream.android.ui.components.NSSourcePickerSheet
import com.nativestream.android.domain.model.PlaylistSource
import com.nativestream.android.domain.model.isAll
import com.nativestream.android.ui.components.AddSourceSheet
import com.nativestream.android.ui.components.NSChip

private val Regular = RegularGroup

private val MASTER_PANE_WIDTH = 320.dp
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

    var selectedGroup by remember { mutableStateOf<String?>(null) }
    var selectedSport by remember { mutableStateOf<SportCategory?>(null) }

    var searchActive by remember { mutableStateOf(false) }
    var searchText   by remember { mutableStateOf("") }

    var showFavouritesOnly by remember { mutableStateOf(false) }
    val favouriteIds by favouritesViewModel.favouriteIds.collectAsState()

    var showPlayUrl by remember { mutableStateOf(false) }

    var selectedChannelId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedChannel = channels.find { it.id == selectedChannelId }

    var showSourcePicker by remember { mutableStateOf(false) }
    val sources          by playlistViewModel.sources.collectAsState()
    val selectedSource   by playlistViewModel.selectedSource.collectAsState()

    var selectedSubGroup by remember { mutableStateOf<String?>(null) }
    val subGroups        by playlistViewModel.subGroups.collectAsState()

    var showAddSource by remember { mutableStateOf(false) }

    // Deselect channel if it no longer belongs to the newly selected source
    LaunchedEffect(selectedSource) {
        val source = selectedSource
        val current = channels.find { it.id == selectedChannelId }
        if (current != null && source != null && !source.isAll) {
            if (current.sourceId != source.id) selectedChannelId = null
        }
    }

    // Groups from playlist
    val groups = remember(channels) {
        channels.map { it.groupTitle }.distinct().sorted()
    }

    // Sport sub-chips only when Sports group selected
    val activeSports = remember(channels, selectedGroup) {
        if (selectedGroup?.lowercase()?.contains("sport") == true)
            epgViewModel.activeSports(channels)
        else emptyList()
    }

    val filtered = remember(channels, selectedGroup, selectedSubGroup, selectedSport, searchText, showFavouritesOnly, favouriteIds) {
        channels
            .filter { if (selectedGroup != null) it.groupTitle == selectedGroup else true }
            .filter { if (selectedSubGroup != null) it.subGroupTitle == selectedSubGroup else true }
            .filter { if (selectedSport != null) {
                val prog = epgViewModel.currentProgramme(it) ?: epgViewModel.nextProgramme(it)
                prog != null && epgViewModel.matchesSport(selectedSport!!, prog)
            } else true }
            .filter { if (searchText.isNotEmpty()) it.name.contains(searchText, ignoreCase = true) else true }
            .filter { if (showFavouritesOnly) favouriteIds.contains(it.id) else true }
    }

    val groupedSections = remember(filtered, selectedGroup) {
        val groups = filtered.groupBy { it.groupTitle }
        val sorted = groups.keys.sorted().map { ChannelSection(it, groups[it]!!) }
        if (selectedGroup != null) sorted.filter { it.name == selectedGroup } else sorted
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
                onSearchChange = { searchText = it },
                onSearchClose  = { searchActive = false; searchText = "" },
                onPlayUrl      = { showPlayUrl = true },
                onAddChannel   = { showAddChannel = true },
                selectedSource = selectedSource,
                onSourceClick  = { showSourcePicker = true },
            )
            Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(NSColors.border))

            // ── Sport + group chips — hidden on Expanded (lives in list pane instead) ──
            val windowSizeClass = LocalWindowSizeClass.current
            val useDetail = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Expanded
            if (!useDetail) BrowseFilterRow(
                sources        = sources,
                selectedSource = selectedSource,
                groups         = groups,
                selectedGroup  = selectedGroup,
                activeSports   = activeSports,
                selectedSport  = selectedSport,
                onPillClick    = { showSourcePicker = true },
                onSelectGroup  = { selectedGroup = it; selectedSport = null; selectedSubGroup = null; showFavouritesOnly = false },
                onSelectAll    = { selectedGroup = null; selectedSport = null; selectedSubGroup = null; showFavouritesOnly = false },
                onSelectSport  = { selectedSport = it },
                showFavouritesOnly  = showFavouritesOnly,
                onToggleFavourites = { if (!showFavouritesOnly) { showFavouritesOnly = true; selectedGroup = null; selectedSport = null } },
                subGroups        = subGroups,
                selectedSubGroup = selectedSubGroup,
                onSelectSubGroup = { selectedSubGroup = it },
            )
            Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(NSColors.border))
            when {
                isLoading          -> BrowseLoadingView()
                filtered.isEmpty() -> BrowseEmptyView(searchText)
                else -> {
                    if (useDetail) {
                        BrowseMasterDetail(
                            sections            = groupedSections,
                            selectedChannel     = selectedChannel,
                            onSelectChannel     = { selectedChannel = it },
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
                            onSelectAll         = { selectedGroup = null; selectedSport = null; selectedSubGroup = null; showFavouritesOnly = false },
                            onSelectGroup       = { selectedGroup = it; selectedSport = null; selectedSubGroup = null; showFavouritesOnly = false },
                            onSelectSubGroup    = { selectedSubGroup = it },
                            onSelectSport       = { selectedSport = it },
                            showFavouritesOnly  = showFavouritesOnly,
                            onToggleFavourites  = { if (!showFavouritesOnly) { showFavouritesOnly = true; selectedGroup = null; selectedSport = null } },
                        )
                    } else {
                        BrowseGrid(
                            sections            = groupedSections,
                            playerViewModel     = playerViewModel,
                            epgViewModel        = epgViewModel,
                            favouritesViewModel = favouritesViewModel,
                        )
                    }
                }
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

// ── Chips — icon above label, square rounded, matching design ─────────────────
@Composable
private fun BrowseFilterRow(
    sources: List<PlaylistSource>,
    selectedSource: PlaylistSource?,
    groups: List<String>,
    selectedGroup: String?,
    activeSports: List<SportCategory>,
    selectedSport: SportCategory?,
    onPillClick: () -> Unit,
    onSelectAll: () -> Unit,
    onSelectGroup: (String) -> Unit,
    onSelectSport: (SportCategory?) -> Unit,
    showFavouritesOnly: Boolean,
    onToggleFavourites: () -> Unit,
    subGroups: List<String>,
    selectedSubGroup: String?,
    onSelectSubGroup: (String?) -> Unit,
) {
    val dimens = NSDimens.current
    val showSubGroups = selectedSource != null && !selectedSource.isAll
            && selectedGroup != null && subGroups.isNotEmpty()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(NSColors.surface),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(dimens.spacing.sm),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = dimens.spacing.lg, vertical = dimens.spacing.sm),
        ) {
                NSChip(
                    label    = "All",
                    isActive = selectedGroup == null && !showFavouritesOnly,
                    onClick  = onSelectAll,
                )
                NSChip(
                    label    = "Favourites",
                    isActive = showFavouritesOnly,
                    icon     = Regular.Star,
                    onClick  = onToggleFavourites,
                )
                groups.forEach { group ->
                    NSChip(
                        label    = group,
                        isActive = selectedGroup == group,
                        onClick  = { onSelectGroup(group); onSelectSport(null) },
                    )
                }
            }
        }
        AnimatedVisibility(
            visible = showSubGroups,
            enter   = fadeIn() + expandVertically(),
            exit    = fadeOut() + shrinkVertically(),
        ) {
            Column {
                Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(NSColors.border))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(dimens.spacing.sm),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = dimens.spacing.lg, vertical = dimens.spacing.xs),
                ) {
                    NSChip(
                        label    = "All",
                        isActive = selectedSubGroup == null,
                        onClick  = { onSelectSubGroup(null) },
                    )
                    subGroups.forEach { sub ->
                        NSChip(
                            label    = sub,
                            isActive = selectedSubGroup == sub,
                            onClick  = { onSelectSubGroup(sub) },
                        )
                    }
                }
            }
        }

        // Level 2 — sport sub-chips
        if (activeSports.isNotEmpty() && !showSubGroups) {
            Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(NSColors.border))
            Row(
                horizontalArrangement = Arrangement.spacedBy(dimens.spacing.sm),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = dimens.spacing.lg, vertical = dimens.spacing.xs),
            ) {
                NSChip(label = "All Sports", isActive = selectedSport == null, onClick = { onSelectSport(null) })
                activeSports.forEach { sport ->
                    NSChip(label = sport.label, isActive = selectedSport == sport, onClick = { onSelectSport(sport) })
                }
            }
        }
    }

// ── Loading / empty ───────────────────────────────────────────────────────────

@Composable
private fun BrowseLoadingView() {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
        Text(text = "Loading channels…", style = NSType.caption(), color = NSColors.text3)
    }
}

@Composable
private fun BrowseEmptyView(searchText: String) {
    val dimens = NSDimens.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize().padding(dimens.spacing.xl),
    ) {
        Text(text = "📺", fontSize = 40.sp)
        Spacer(modifier = Modifier.height(dimens.spacing.md))
        Text(text = "No channels found", style = NSType.display(), color = NSColors.text)
        Spacer(modifier = Modifier.height(dimens.spacing.sm))
        Text(
            text  = if (searchText.isEmpty()) "Add a playlist source in Settings."
            else "Try a different search term.",
            style = NSType.caption(),
            color = NSColors.text3,
        )
    }
}


@Composable
fun BrowseSearchBar(
    searchText: String,
    onSearchChange: (String) -> Unit,
    onSearchClose: () -> Unit,
) {
    val dimens = NSDimens.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(NSColors.surface)
            .padding(horizontal = dimens.spacing.lg, vertical = dimens.spacing.sm),
    ) {
        NSTextField(
            value         = searchText,
            onValueChange = onSearchChange,
            placeholder   = "Search channels…",
            modifier      = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(dimens.spacing.sm))
        NSIconButton(
            icon               = Icons.Default.Close,
            contentDescription = "Close search",
            onClick            = onSearchClose,
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


@Composable
private fun BrowseMasterDetail(
    sections: List<ChannelSection>,
    selectedChannel: Channel?,
    onSelectChannel: (Channel) -> Unit,
    playerViewModel: PlayerViewModel,
    epgViewModel: EpgViewModel,
    favouritesViewModel: FavouritesViewModel,
    sources: List<PlaylistSource>,
    selectedSource: PlaylistSource?,
    groups: List<String>,
    selectedGroup: String?,
    subGroups: List<String>,
    selectedSubGroup: String?,
    activeSports: List<SportCategory>,
    selectedSport: SportCategory?,
    onPillClick: () -> Unit,
    onSelectAll: () -> Unit,
    onSelectGroup: (String) -> Unit,
    onSelectSubGroup: (String?) -> Unit,
    onSelectSport: (SportCategory?) -> Unit,
    showFavouritesOnly: Boolean,
    onToggleFavourites: () -> Unit,
) {
    val dimens = NSDimens.current

    Row(modifier = Modifier.fillMaxSize()) {
        // Left pane — single-column channel list
        LazyColumn(
            modifier = Modifier
                .width(MASTER_PANE_WIDTH)
                .fillMaxHeight()
                .background(NSColors.surface),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            sections.forEach { section ->
                item(key = "header_${section.name}") {
                    NSGroupHeader(
                        title    = section.name,
                        count    = section.channels.size,
                        modifier = Modifier.padding(
                            horizontal = dimens.spacing.md,
                            vertical   = dimens.spacing.sm,
                        ),
                    )
                }
                items(section.channels, key = { it.id }) { channel ->
                    MasterPaneRow(
                        channel    = channel,
                        isSelected = selectedChannel?.id == channel.id,
                        onClick    = { onSelectChannel(channel) },
                    )
                }
            }
            item { Spacer(modifier = Modifier.height(80.dp)) }
        }

        // Divider
        Box(
            modifier = Modifier
                .width(0.5.dp)
                .fillMaxHeight()
                .background(NSColors.border)
        )

        // Right pane — detail or empty state
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        ) {
            if (selectedChannel != null) {
                BrowseDetailPane(
                    channel         = selectedChannel,
                    epgViewModel    = epgViewModel,
                    playerViewModel = playerViewModel,
                )
            } else {
                DetailEmptyState()
            }
        }
    }
}

@Composable
private fun MasterPaneRow(
    channel: Channel,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val dimens = NSDimens.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isSelected) NSColors.accentGlow else NSColors.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = dimens.spacing.md, vertical = dimens.spacing.sm),
    ) {
        Text(
            text     = channel.name,
            style    = NSType.caption(),
            color    = if (isSelected) NSColors.accent2 else NSColors.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun DetailEmptyState() {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
        Text(
            text  = "Select a channel",
            style = NSType.caption(),
            color = NSColors.text3,
        )
    }
}