package com.nativestream.android.ui.screens.browse

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.adamglin.phosphoricons.regular.Star
import com.nativestream.android.domain.model.PlaylistSource
import com.nativestream.android.domain.model.SportCategory
import com.nativestream.android.domain.model.isAll
import com.nativestream.android.ui.components.NSChip
import com.nativestream.android.ui.theme.NSColors
import com.nativestream.android.ui.theme.NSDimens
import kotlin.collections.forEach

@Composable
fun BrowseFilterRow(
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
                label = "All",
                isActive = selectedGroup == null && !showFavouritesOnly,
                onClick = onSelectAll,
            )
            NSChip(
                label = "Favourites",
                isActive = showFavouritesOnly,
                icon = Regular.Star,
                onClick = onToggleFavourites,
            )
            groups.forEach { group ->
                NSChip(
                    label = group,
                    isActive = selectedGroup == group,
                    onClick = { onSelectGroup(group); onSelectSport(null) },
                )
            }
        }

        AnimatedVisibility(
            visible = showSubGroups,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
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
                        label = "All",
                        isActive = selectedSubGroup == null,
                        onClick = { onSelectSubGroup(null) })
                    subGroups.forEach { sub ->
                        NSChip(
                            label = sub,
                            isActive = selectedSubGroup == sub,
                            onClick = { onSelectSubGroup(sub) })
                    }
                }
            }
        }

        if (activeSports.isNotEmpty() && !showSubGroups) {
            Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(NSColors.border))
            Row(
                horizontalArrangement = Arrangement.spacedBy(dimens.spacing.sm),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = dimens.spacing.lg, vertical = dimens.spacing.xs),
            ) {
                NSChip(
                    label = "All Sports",
                    isActive = selectedSport == null,
                    onClick = { onSelectSport(null) })
                activeSports.forEach { sport ->
                    NSChip(
                        label = sport.label,
                        isActive = selectedSport == sport,
                        onClick = { onSelectSport(sport) })
                }
            }
        }
    }
}