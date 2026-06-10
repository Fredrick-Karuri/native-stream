package com.nativestream.android.ui.screens.browse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nativestream.android.ui.components.NSGroupHeader
import com.nativestream.android.ui.theme.NSDimens
import com.nativestream.android.ui.viewmodel.EpgViewModel
import com.nativestream.android.ui.viewmodel.FavouritesViewModel
import com.nativestream.android.ui.viewmodel.PlayerViewModel

@Composable
fun BrowseGrid(
    sections: List<ChannelSection>,
    playerViewModel: PlayerViewModel,
    epgViewModel: EpgViewModel,
    favouritesViewModel: FavouritesViewModel,
) {
    val dimens = NSDimens.current

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 160.dp),
        verticalArrangement = Arrangement.spacedBy(dimens.spacing.md),
        horizontalArrangement = Arrangement.spacedBy(dimens.spacing.sm),
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = dimens.spacing.md, vertical = dimens.spacing.md),
    ) {
        sections.forEach { section ->
            item(
                key = "header_${section.name}",
                span = { GridItemSpan(maxLineSpan) },
            ) {
                NSGroupHeader(title = section.name, count = section.channels.size)
            }
            items(
                items = section.channels,
                key   = { it.id },
            ) { channel ->
                ChannelCard(
                    channel             = channel,
                    playerViewModel     = playerViewModel,
                    epgViewModel        = epgViewModel,
                    favouritesViewModel = favouritesViewModel,
                    onClick             = { playerViewModel.play(channel) },
                )
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}