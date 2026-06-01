// app/src/main/java/com/nativestream/android/ui/screens/browse/BrowseScreen.kt
//
// Browse Screen
// - Top bar: "Browse" title + search icon (mobile style)
// - Chips: icon-above-label square pills matching design
// - Grid: real ChannelCard, adaptive 2-column, correct height

package com.nativestream.android.ui.screens.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.FloatingActionButton
import com.adamglin.phosphoricons.RegularGroup
import com.adamglin.phosphoricons.regular.Basketball
import com.adamglin.phosphoricons.regular.Football
import com.adamglin.phosphoricons.regular.Cricket
import com.adamglin.phosphoricons.regular.Golf
import com.adamglin.phosphoricons.regular.SoccerBall
import com.adamglin.phosphoricons.regular.TennisBall
import com.nativestream.android.domain.model.Channel
import com.nativestream.android.domain.model.SportCategory
import com.nativestream.android.ui.components.NSGroupHeader
import com.nativestream.android.ui.components.NSTextField
import com.nativestream.android.ui.theme.NSColors
import com.nativestream.android.ui.theme.NSDimens
import com.nativestream.android.ui.theme.NSType
import com.nativestream.android.ui.viewmodel.EpgViewModel
import com.nativestream.android.ui.viewmodel.FavouritesViewModel
import com.nativestream.android.ui.viewmodel.PlaylistViewModel
import com.nativestream.android.ui.viewmodel.PlayerViewModel

private val CARD_COLUMNS     = 2       // fixed 2-col grid on mobile

private val Regular = RegularGroup

private data class ChannelSection(val name: String, val channels: List<Channel>)

@Composable
fun BrowseScreen(
    playerViewModel: PlayerViewModel,
    modifier: Modifier = Modifier,
    playlistViewModel: PlaylistViewModel  = hiltViewModel(),
    epgViewModel: EpgViewModel            = hiltViewModel(),
    favouritesViewModel: FavouritesViewModel = hiltViewModel(),
) {
    val channels  by playlistViewModel.channels.collectAsState()
    val isLoading by playlistViewModel.isLoading.collectAsState()

    var showAddChannel by remember { mutableStateOf(false) }

    var selectedGroup by remember { mutableStateOf<String?>(null) }
    var selectedSport by remember { mutableStateOf<SportCategory?>(null) }

    var searchActive by remember { mutableStateOf(false) }
    var searchText   by remember { mutableStateOf("") }

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


    // Filtered channels
    val filtered = remember(channels, selectedGroup, selectedSport, searchText) {
        channels
            .filter { if (selectedGroup != null) it.groupTitle == selectedGroup else true }
            .filter { if (selectedSport != null) {
                val prog = epgViewModel.currentProgramme(it) ?: epgViewModel.nextProgramme(it)
                prog != null && epgViewModel.matchesSport(selectedSport!!, prog)
            } else true }
            .filter { if (searchText.isNotEmpty())
                it.name.contains(searchText, ignoreCase = true) else true }
    }

    val groupedSections = remember(filtered, selectedGroup) {
        val groups = filtered.groupBy { it.groupTitle }
        val sorted = groups.keys.sorted().map { ChannelSection(it, groups[it]!!) }
        if (selectedGroup != null) sorted.filter { it.name == selectedGroup } else sorted
    }
    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = modifier.fillMaxSize().background(NSColors.bg)) {

            // ── Top bar — mobile style: title + search icon ───────────────────────
            BrowseTopBar(
                searchActive  = searchActive,
                searchText    = searchText,
                onSearchClick = { searchActive = true },
                onSearchChange = { searchText = it },
                onSearchClose  = { searchActive = false; searchText = "" },
            )
            Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(NSColors.border))

            // ── Sport + group chips ───────────────────────────────────────────────
            BrowseChipsRow(
                groups        = groups,
                selectedGroup = selectedGroup,
                activeSports  = activeSports,
                selectedSport = selectedSport,
                onSelectAll   = { selectedGroup = null; selectedSport = null },
                onSelectGroup = { selectedGroup = it },
                onSelectSport = { selectedSport = it },
            )
            Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(NSColors.border))
            when {
                isLoading          -> BrowseLoadingView()
                filtered.isEmpty() -> BrowseEmptyView(searchText)
                else               -> BrowseGrid(
                    sections            = groupedSections,
                    playerViewModel     = playerViewModel,
                    epgViewModel        = epgViewModel,
                    favouritesViewModel = favouritesViewModel,
                )
            }
        }


        var showPlayUrl by remember { mutableStateOf(false) }

        FloatingActionButton(
            onClick = { showPlayUrl = true },
            containerColor = NSColors.accent,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 80.dp, end = 16.dp), // above bottom nav
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = "Play URL", tint = NSColors.bg)
        }

        if (showPlayUrl) {
            PlayURLSheet(
                playerViewModel = playerViewModel,
                onDismiss = { showPlayUrl = false },
                onPlay    = { playerViewModel.showPlayer() },
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

// ── Top bar ───────────────────────────────────────────────────────────────────

@Composable
private fun BrowseTopBar(
    searchActive: Boolean,
    searchText: String,
    onSearchClick: () -> Unit,
    onSearchChange: (String) -> Unit,
    onSearchClose: () -> Unit,
) {
    val dimens = NSDimens.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(NSColors.surface)
            .padding(horizontal = dimens.spacing.lg, vertical = dimens.spacing.md),
    ) {
        if (searchActive) {
            NSTextField(
                value         = searchText,
                onValueChange = onSearchChange,
                placeholder   = "Search channels…",
                modifier      = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(dimens.spacing.sm))
            Icon(
                imageVector        = Icons.Default.Close,
                contentDescription = "Close search",
                tint               = NSColors.text2,
                modifier           = Modifier.size(20.dp).clickable {
                    onSearchClose()
                },
            )
        } else {
            Text(text = "Browse", style = NSType.heading(), color = NSColors.text)
            Spacer(modifier = Modifier.weight(1f))
            Icon(
                imageVector        = Icons.Default.Search,
                contentDescription = "Search",
                tint               = NSColors.text2,
                modifier           = Modifier.size(20.dp).clickable(onClick = onSearchClick),
            )
        }
    }
}

// ── Chips — icon above label, square rounded, matching design ─────────────────
@Composable
private fun BrowseChipsRow(
    groups: List<String>,
    selectedGroup: String?,
    activeSports: List<SportCategory>,
    selectedSport: SportCategory?,
    onSelectAll: () -> Unit,
    onSelectGroup: (String) -> Unit,
    onSelectSport: (SportCategory?) -> Unit,
) {
    val dimens = NSDimens.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(NSColors.surface),
    ) {
        // Level 1 — groups
        Row(
            horizontalArrangement = Arrangement.spacedBy(dimens.spacing.sm),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = dimens.spacing.lg, vertical = dimens.spacing.sm),
        ) {
            NSChip(label = "All", isActive = selectedGroup == null, onClick = onSelectAll)
            groups.forEach { group ->
                NSChip(
                    label    = group,
                    isActive = selectedGroup == group,
                    onClick  = { onSelectGroup(group); onSelectSport(null) },
                )
            }
        }

        // Level 2 — sport sub-chips, only when relevant
        if (activeSports.isNotEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(NSColors.border))
            Row(
                horizontalArrangement = Arrangement.spacedBy(dimens.spacing.sm),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = dimens.spacing.lg, vertical = dimens.spacing.xs),
            ) {
                NSChip(
                    label   = "All Sports",
                    isActive = selectedSport == null,
                    onClick  = { onSelectSport(null) },
                )
                activeSports.forEach { sport ->
                    NSChip(
                        label    = sport.label,
                        isActive = selectedSport == sport,
                        onClick  = { onSelectSport(sport) },
                    )
                }
            }
        }
    }
}


// ── Channel grid — real ChannelCard, 2-column ─────────────────────────────────

@Composable
private fun BrowseGrid(
    sections: List<ChannelSection>,
    playerViewModel: PlayerViewModel,
    epgViewModel: EpgViewModel,
    favouritesViewModel: FavouritesViewModel,
) {
    val dimens = NSDimens.current
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(dimens.spacing.xxl),
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = dimens.spacing.md, vertical = dimens.spacing.md),
    ) {
        sections.forEach { section ->
            item(key = "header_${section.name}") {
                NSGroupHeader(title = section.name, count = section.channels.size)
            }
            // Emit rows of 2
            val rows = section.channels.chunked(CARD_COLUMNS)
            itemsIndexed(rows, key = { i, _ -> "${section.name}_row_$i" }) { _, row ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(dimens.spacing.sm),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    row.forEach { channel ->
                        ChannelCard(
                            channel             = channel,
                            playerViewModel     = playerViewModel,
                            epgViewModel        = epgViewModel,
                            favouritesViewModel = favouritesViewModel,
                            onClick             = { playerViewModel.play(channel) },
                            modifier            = Modifier.weight(1f),
                        )
                    }
                    // Fill empty slot if odd number
                    if (row.size < CARD_COLUMNS) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
        item { Spacer(modifier = Modifier.height(80.dp)) }
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
fun NSChip(label: String, isActive: Boolean, onClick: () -> Unit) {
    val dimens = NSDimens.current
    Text(
        text  = label,
        style = NSType.caption(),
        color = if (isActive) NSColors.accent2 else NSColors.text3,
        modifier = Modifier
            .clip(RoundedCornerShape(dimens.radius.pill))
            .background(if (isActive) NSColors.accentGlow else NSColors.surface2)
            .border(0.5.dp, if (isActive) NSColors.accentBorder else NSColors.border, RoundedCornerShape(dimens.radius.pill))
            .clickable(onClick = onClick)
            .padding(horizontal = dimens.spacing.lg, vertical = dimens.spacing.sm),
    )
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