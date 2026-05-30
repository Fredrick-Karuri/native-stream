// app/src/main/java/com/nativestream/android/ui/screens/browse/BrowseScreen.kt
//
// All-channels browser with search, group chips, and adaptive grid.


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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nativestream.android.domain.model.Channel
import com.nativestream.android.domain.model.SportCategory
import com.nativestream.android.ui.components.NSGroupHeader
import com.nativestream.android.ui.theme.NSColors
import com.nativestream.android.ui.theme.NSDimens
import com.nativestream.android.ui.theme.NSType
import com.nativestream.android.ui.viewmodel.EpgViewModel
import com.nativestream.android.ui.viewmodel.PlaylistViewModel
import com.nativestream.android.ui.viewmodel.PlayerViewModel

private val MIN_CARD_WIDTH = 160.dp

private data class ChannelSection(val name: String, val channels: List<Channel>)

// Sport chip options — "All" + each SportCategory
private sealed interface ChipOption {
    data object All : ChipOption
    data class Sport(val category: SportCategory) : ChipOption
    data class Group(val name: String) : ChipOption
}

@Composable
fun BrowseScreen(
    playerViewModel: PlayerViewModel,
    modifier: Modifier = Modifier,
    playlistViewModel: PlaylistViewModel = hiltViewModel(),
    epgViewModel: EpgViewModel           = hiltViewModel(),
) {
    val channels  by playlistViewModel.channels.collectAsState()
    val isLoading by playlistViewModel.isLoading.collectAsState()

    var searchText     by remember { mutableStateOf("") }
    var selectedGroup  by remember { mutableStateOf<String?>(null) }
    var selectedSport  by remember { mutableStateOf<SportCategory?>(null) }
    var showAddChannel by remember { mutableStateOf(false) }

    // If a sport is selected, show MatchDayScreen instead
    if (selectedSport != null) {
        MatchDayScreen(
            sport             = selectedSport!!,
            playlistViewModel = playlistViewModel,
            epgViewModel      = epgViewModel,
            onSelectChannel   = { playerViewModel.play(it) },
            modifier          = modifier,
        )
        return
    }

    val filtered = remember(channels, searchText) {
        if (searchText.isEmpty()) channels
        else channels.filter {
            it.name.contains(searchText, ignoreCase = true) ||
            it.groupTitle.contains(searchText, ignoreCase = true)
        }
    }

    val groupedSections = remember(filtered, selectedGroup) {
        val groups = filtered.groupBy { it.groupTitle }
        val sorted = groups.keys.sorted().map { ChannelSection(it, groups[it]!!) }
        if (selectedGroup != null) sorted.filter { it.name == selectedGroup } else sorted
    }

    Column(modifier = modifier.fillMaxSize().background(NSColors.bg)) {
        BrowseTopBar(
            searchText    = searchText,
            channelCount  = filtered.size,
            onSearchChange = {
                searchText    = it
                selectedGroup = null
            },
            onAddChannel  = { showAddChannel = true },
        )
        Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(NSColors.border))

        GroupChipsRow(
            groups         = groupedSections.map { it.name },
            selectedGroup  = selectedGroup,
            selectedSport  = selectedSport,
            onSelectGroup  = { selectedGroup = it },
            onSelectSport  = { selectedSport = it },
            onSelectAll    = { selectedGroup = null; selectedSport = null },
        )
        Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(NSColors.border))

        when {
            isLoading       -> BrowseLoadingView()
            filtered.isEmpty() -> BrowseEmptyView(searchText)
            else            -> BrowseChannelContent(
                sections        = groupedSections,
                onSelectChannel = { playerViewModel.play(it) },
            )
        }
    }

    // TODO AND-015: AddChannelSheet(visible = showAddChannel, onDismiss = { showAddChannel = false })
}

// ── Top bar ───────────────────────────────────────────────────────────────────

@Composable
private fun BrowseTopBar(
    searchText: String,
    channelCount: Int,
    onSearchChange: (String) -> Unit,
    onAddChannel: () -> Unit,
) {
    val dimens = NSDimens.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(NSColors.surface)
            .padding(horizontal = dimens.spacing.xl, vertical = dimens.spacing.md),
    ) {
        Text(text = "All Channels", style = NSType.heading(), color = NSColors.text)
        Spacer(modifier = Modifier.weight(1f))

        // Search field
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(dimens.radius.md))
                .background(NSColors.surface2)
                .border(0.5.dp, NSColors.border2, RoundedCornerShape(dimens.radius.md))
                .padding(horizontal = dimens.spacing.md, vertical = 6.dp)
                .width(200.dp),
        ) {
            Icon(
                imageVector        = Icons.Default.Search,
                contentDescription = "Search",
                tint               = NSColors.text3,
                modifier           = Modifier.height(12.dp).width(12.dp),
            )
            Spacer(modifier = Modifier.width(dimens.spacing.xs))
            BasicTextField(
                value         = searchText,
                onValueChange = onSearchChange,
                textStyle     = NSType.caption().copy(color = NSColors.text),
                cursorBrush   = SolidColor(NSColors.accent),
                decorationBox = { inner ->
                    if (searchText.isEmpty()) {
                        Text(text = "Search channels…", style = NSType.caption(), color = NSColors.text3)
                    }
                    inner()
                },
            )
        }

        Spacer(modifier = Modifier.width(dimens.spacing.md))
        Text(text = "$channelCount channels", style = NSType.caption(), color = NSColors.text3)
        Spacer(modifier = Modifier.width(dimens.spacing.md))

        // Add Channel button
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.spacing.xs),
            modifier = Modifier
                .clip(RoundedCornerShape(dimens.radius.md))
                .background(NSColors.accentGlow)
                .border(0.5.dp, NSColors.accentBorder, RoundedCornerShape(dimens.radius.md))
                .clickable(onClick = onAddChannel)
                .padding(horizontal = dimens.spacing.md, vertical = 6.dp),
        ) {
            Icon(
                imageVector        = Icons.Default.Add,
                contentDescription = "Add Channel",
                tint               = NSColors.accent,
                modifier           = Modifier.height(11.dp).width(11.dp),
            )
            Text(text = "Add Channel", style = NSType.captionMedium(), color = NSColors.accent)
        }
    }
}

// ── Group chips ───────────────────────────────────────────────────────────────

@Composable
private fun GroupChipsRow(
    groups: List<String>,
    selectedGroup: String?,
    selectedSport: SportCategory?,
    onSelectGroup: (String?) -> Unit,
    onSelectSport: (SportCategory) -> Unit,
    onSelectAll: () -> Unit,
) {
    val dimens = NSDimens.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(dimens.spacing.xs),
        modifier = Modifier
            .fillMaxWidth()
            .background(NSColors.surface)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = dimens.spacing.xl, vertical = dimens.spacing.sm),
    ) {
        NSChip(label = "All", isActive = selectedGroup == null && selectedSport == null, onClick = onSelectAll)
        SportCategory.entries.forEach { sport ->
            NSChip(label = sport.label, isActive = selectedSport == sport) { onSelectSport(sport) }
        }
        groups.forEach { group ->
            NSChip(label = group, isActive = selectedGroup == group) { onSelectGroup(group) }
        }
    }
}

@Composable
fun NSChip(label: String, isActive: Boolean, onClick: () -> Unit) {
    val dimens = NSDimens.current
    Text(
        text     = label,
        style    = NSType.caption(),
        color    = if (isActive) NSColors.accent2 else NSColors.text3,
        modifier = Modifier
            .clip(RoundedCornerShape(dimens.radius.pill))
            .background(if (isActive) NSColors.accentGlow else NSColors.surface2)
            .border(
                0.5.dp,
                if (isActive) NSColors.accentBorder else NSColors.border,
                RoundedCornerShape(dimens.radius.pill),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = dimens.spacing.md, vertical = dimens.spacing.xs),
    )
}

// ── Channel grid ──────────────────────────────────────────────────────────────

@Composable
private fun BrowseChannelContent(
    sections: List<ChannelSection>,
    onSelectChannel: (Channel) -> Unit,
) {
    val dimens = NSDimens.current
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(dimens.spacing.xxl),
        modifier = Modifier.fillMaxSize().padding(dimens.spacing.xl),
    ) {
        sections.forEach { section ->
            item {
                Column(verticalArrangement = Arrangement.spacedBy(dimens.spacing.md)) {
                    NSGroupHeader(title = section.name, count = section.channels.size)
                    LazyVerticalGrid(
                        columns             = GridCells.Adaptive(minSize = MIN_CARD_WIDTH),
                        horizontalArrangement = Arrangement.spacedBy(dimens.spacing.sm),
                        verticalArrangement   = Arrangement.spacedBy(dimens.spacing.sm),
                        // Fixed height — LazyVerticalGrid inside LazyColumn needs bounded height
                        modifier = Modifier.height(
                            (((section.channels.size + 1) / 2) * 120).dp
                        ),
                    ) {
                        items(section.channels, key = { it.id }) { channel ->
                            ChannelGridCard(channel = channel, onClick = { onSelectChannel(channel) })
                        }
                    }
                }
            }
        }
        item { Spacer(modifier = Modifier.height(80.dp)) }
    }
}

// ── Channel grid card (placeholder — full impl AND-014) ───────────────────────

@Composable
private fun ChannelGridCard(channel: Channel, onClick: () -> Unit) {
    val dimens = NSDimens.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .clip(RoundedCornerShape(dimens.radius.lg))
            .background(NSColors.surface2)
            .border(0.5.dp, NSColors.border, RoundedCornerShape(dimens.radius.lg))
            .clickable(onClick = onClick),
    ) {
        Text(
            text     = channel.name,
            style    = NSType.captionMedium(),
            color    = NSColors.text2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(dimens.spacing.sm),
        )
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