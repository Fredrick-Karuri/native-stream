// app/src/main/java/com/nativestream/android/ui/screens/browse/BrowseDetailPane.kt

package com.nativestream.android.ui.screens.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nativestream.android.domain.model.Channel
import com.nativestream.android.ui.components.NSLiveBadge
import com.nativestream.android.ui.components.NSProgressBar
import com.nativestream.android.ui.theme.NSColors
import com.nativestream.android.ui.theme.NSDimens
import com.nativestream.android.ui.theme.NSType
import com.nativestream.android.ui.viewmodel.EpgViewModel
import com.nativestream.android.ui.viewmodel.PlayerViewModel
import java.text.SimpleDateFormat
import com.nativestream.android.domain.model.PlaylistSource
import com.nativestream.android.domain.model.isAll
import com.nativestream.android.ui.components.NSSourceBadge
import java.util.*

@Composable
fun BrowseDetailPane(
    channel: Channel,
    epgViewModel: EpgViewModel,
    playerViewModel: PlayerViewModel,
    sources: List<PlaylistSource> = emptyList(),
    selectedSource: PlaylistSource? = null,
    modifier: Modifier = Modifier,
) {
    val dimens = NSDimens.current
    val programme   = epgViewModel.currentProgramme(channel)
    val schedule    = epgViewModel.schedule(channel)
    val badgeSource = remember(channel.sourceId, selectedSource, sources) {
        if (selectedSource == null || selectedSource.isAll)
            sources.find { it.id == channel.sourceId }
        else null
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(NSColors.bg)
            .padding(dimens.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(dimens.spacing.lg),
    ) {
        // Channel name + current programme
        item {
            Column(verticalArrangement = Arrangement.spacedBy(dimens.spacing.sm)) {
                Text(
                    text  = channel.name,
                    style = NSType.heading(),
                    color = NSColors.text,
                )
                badgeSource?.let {
                    NSSourceBadge(source = it)
                }
                if (programme != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(dimens.spacing.sm),
                    ) {
                        NSLiveBadge(isLive = true)
                        Text(
                            text     = programme.title,
                            style    = NSType.body(),
                            color    = NSColors.text2,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    NSProgressBar(
                        value    = programme.progress.toFloat(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Text(
                        text  = "No programme info",
                        style = NSType.caption(),
                        color = NSColors.text3,
                    )
                }
            }
        }

        // Watch now button
        item {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(dimens.radius.lg))
                    .background(NSColors.accent)
                    .clickable {
                        playerViewModel.play(channel)
                        playerViewModel.showPlayer()
                    }
                    .padding(vertical = dimens.spacing.md),
            ) {
                Text(
                    text  = "Watch now",
                    style = NSType.bodyMedium(),
                    color = NSColors.bg,
                )
            }
        }

        // Schedule header
        if (schedule.isNotEmpty()) {
            item {
                Text(
                    text  = "Next 6 hours",
                    style = NSType.captionMedium(),
                    color = NSColors.text3,
                )
            }
            items(schedule, key = { it.id }) { prog ->
                ScheduleRow(prog)
            }
        }

        item { Spacer(modifier = Modifier.height(80.dp)) }
    }
}

@Composable
private fun ScheduleRow(programme: com.nativestream.android.domain.model.Programme) {
    val dimens = NSDimens.current
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val startTime  = remember(programme.startEpochMs) {
        timeFormat.format(Date(programme.startEpochMs))
    }

    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(dimens.spacing.md),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = dimens.spacing.xs),
    ) {
        Text(
            text  = startTime,
            style = NSType.monoSmall(),
            color = NSColors.text3,
            modifier = Modifier.width(36.dp),
        )
        Text(
            text     = programme.title,
            style    = NSType.caption(),
            color    = NSColors.text2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}