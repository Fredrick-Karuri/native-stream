package com.nativestream.android.ui.screens.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nativestream.android.domain.model.Channel
import com.nativestream.android.domain.model.PlaylistSource
import com.nativestream.android.domain.model.SportCategory
import com.nativestream.android.ui.components.NSGroupHeader
import com.nativestream.android.ui.theme.NSColors
import com.nativestream.android.ui.theme.NSDimens
import com.nativestream.android.ui.theme.NSType
import com.nativestream.android.ui.viewmodel.EpgViewModel
import com.nativestream.android.ui.viewmodel.FavouritesViewModel
import com.nativestream.android.ui.viewmodel.PlayerViewModel

private val MASTER_PANE_WIDTH = 320.dp

@Composable
fun BrowseMasterDetail(
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
    isEmptyState: Boolean = false,
    emptySearchText: String = "",
) {
    val dimens = NSDimens.current

    // replace
    Column(modifier = Modifier.fillMaxSize()) {
        BrowseFilterRow(
            sources = sources,
            selectedSource = selectedSource,
            groups = groups,
            selectedGroup = selectedGroup,
            subGroups = subGroups,
            selectedSubGroup = selectedSubGroup,
            activeSports = activeSports,
            selectedSport = selectedSport,
            onPillClick = onPillClick,
            onSelectAll = onSelectAll,
            onSelectGroup = onSelectGroup,
            onSelectSubGroup = onSelectSubGroup,
            onSelectSport = onSelectSport,
            showFavouritesOnly = showFavouritesOnly,
            onToggleFavourites = onToggleFavourites,
        )
        Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(NSColors.border))

        Row(modifier = Modifier.fillMaxSize()) {
            // Left pane
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
                            title = section.name,
                            count = section.channels.size,
                            modifier = Modifier.padding(
                                horizontal = dimens.spacing.md,
                                vertical = dimens.spacing.sm,
                            ),
                        )
                    }
                    items(section.channels, key = { it.id }) { channel ->
                        val favourites by favouritesViewModel.favouriteIds.collectAsState()
                        MasterPaneRow(
                            channel = channel,
                            isSelected = selectedChannel?.id == channel.id,
                            isFavourite = favourites.contains(channel.id),
                            onFavouriteClick = { favouritesViewModel.toggle(channel) },
                            onClick = { onSelectChannel(channel) },
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

            // Right pane
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            ) {
                when {
                    isEmptyState -> BrowseEmptyView(emptySearchText)
                    selectedChannel != null -> BrowseDetailPane(
                        channel = selectedChannel,
                        epgViewModel = epgViewModel,
                        playerViewModel = playerViewModel,
                        sources = sources,
                        selectedSource = selectedSource,
                    )

                    else -> DetailEmptyState()
                }
            }
        }
    }
}


@Composable
private fun MasterPaneRow(
    channel: Channel,
    isSelected: Boolean,
    isFavourite: Boolean,
    onFavouriteClick: () -> Unit,
    onClick: () -> Unit,
) {
    val dimens = NSDimens.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isSelected) NSColors.accentGlow else NSColors.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = dimens.spacing.md, vertical = dimens.spacing.md),
    ) {
        Text(
            text     = channel.name,
            style    = NSType.caption(),
            color    = if (isSelected) NSColors.accent2 else NSColors.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector        = if (isFavourite) Icons.Filled.Star else Icons.Outlined.Star,
            contentDescription = if (isFavourite) "Remove from favourites" else "Add to favourites",
            tint               = if (isFavourite) NSColors.amber else NSColors.text3,
            modifier           = Modifier
                .size(14.dp)
                .clickable(onClick = onFavouriteClick),
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