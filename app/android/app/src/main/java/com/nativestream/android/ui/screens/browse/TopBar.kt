package com.nativestream.android.ui.screens.browse

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import com.nativestream.android.domain.model.PlaylistSource
import com.nativestream.android.ui.components.NSIconButton
import com.nativestream.android.ui.components.NSSourcePill
import com.nativestream.android.ui.theme.NSColors
import com.nativestream.android.ui.theme.NSDimens
import com.nativestream.android.ui.theme.NSType

@Composable
fun BrowseTopBar(
    searchActive: Boolean,
    searchText: String,
    onSearchClick: () -> Unit,
    onSearchChange: (String) -> Unit,
    onSearchClose: () -> Unit,
    onPlayUrl: () -> Unit,
    onAddChannel: () -> Unit,
    selectedSource: PlaylistSource?,
    onSourceClick: () -> Unit,
    isRefreshing: Boolean = false,
) {
    val dimens = NSDimens.current
    var menuExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(NSColors.surface)
            .windowInsetsPadding(WindowInsets.displayCutout),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = dimens.spacing.lg, vertical = dimens.spacing.md),
        ) {
            Text(text = "Browse", style = NSType.heading(), color = NSColors.text)
            if (isRefreshing) {
                Spacer(modifier = Modifier.width(dimens.spacing.sm))
                CircularProgressIndicator(
                    color       = NSColors.text3,
                    strokeWidth = 1.5.dp,
                    modifier    = Modifier.size(12.dp),
                )
            }
            Spacer(modifier = Modifier.width(dimens.spacing.md))
            NSSourcePill(
                source  = selectedSource,
                onClick = onSourceClick,
            )
            Spacer(modifier = Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                NSIconButton(
                    icon               = Icons.Default.Search,
                    contentDescription = "Search",
                    onClick            = onSearchClick,
                )
                Spacer(modifier = Modifier.width(dimens.spacing.sm))
                Box {
                    NSIconButton(
                        icon               = Icons.Default.MoreVert,
                        contentDescription = "More",
                        onClick            = { menuExpanded = true },
                    )
                    DropdownMenu(
                        expanded         = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        containerColor   = NSColors.surface2,
                    ) {
                        DropdownMenuItem(
                            text    = { Text("Play URL", style = NSType.caption(), color = NSColors.text) },
                            onClick = { menuExpanded = false; onPlayUrl() },
                        )
                        DropdownMenuItem(
                            text    = { Text("Add Channel", style = NSType.caption(), color = NSColors.text) },
                            onClick = { menuExpanded = false; onAddChannel() },
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = searchActive,
            enter   = fadeIn() + expandVertically(),
            exit    = fadeOut() + shrinkVertically(),
        ) {
            BrowseSearchBar(
                searchText     = searchText,
                onSearchChange = onSearchChange,
                onSearchClose  = onSearchClose,
            )
        }
    }
}