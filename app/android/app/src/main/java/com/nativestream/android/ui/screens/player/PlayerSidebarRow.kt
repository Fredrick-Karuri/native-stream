// app/src/main/java/com/nativestream/android/ui/screens/player/PlayerSidebarRow.kt
//
// Single row in the On Now tab. Shows logo, channel name, programme title,
// and right indicator (▶ playing / time remaining / next start time).

package com.nativestream.android.ui.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nativestream.android.domain.model.Channel
import com.nativestream.android.ui.theme.NSColors
import com.nativestream.android.ui.theme.NSDimens
import com.nativestream.android.ui.theme.NSType
import com.nativestream.android.ui.viewmodel.EpgViewModel
import com.nativestream.android.ui.viewmodel.PlayerViewModel

private val PLAY_ICON_SIZE = 9.dp

@Composable
fun PlayerSidebarRow(
    channel: Channel,
    playerViewModel: PlayerViewModel,
    epgViewModel: EpgViewModel,
    modifier: Modifier = Modifier,
) {
    val dimens        = NSDimens.current
    val activeChannel by playerViewModel.activeChannel.collectAsState()

    val isPlaying = activeChannel?.id == channel.id
    val current   = epgViewModel.currentProgramme(channel)
    val next      = epgViewModel.nextProgramme(channel)
    val programme = current ?: next

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.spacing.sm),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(dimens.radius.md))
            .background(if (isPlaying) NSColors.accentGlow else Color.Transparent)
            .clickable { playerViewModel.play(channel) }
            .padding(horizontal = dimens.spacing.sm, vertical = dimens.spacing.xs),
    ) {
        ChannelLogoSquare(
            channel      = channel,
            size         = dimens.channel.logoSquareSmall,
            cornerRadius = dimens.radius.sm,
        )

        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text     = channel.name,
                style    = NSType.captionMedium(),
                color    = Color.White.copy(alpha = if (isPlaying) 0.9f else 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            programme?.let {
                Text(
                    text     = it.title,
                    style    = NSType.monoSmall(),
                    color    = Color.White.copy(alpha = 0.3f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        // Right indicator
        when {
            isPlaying -> Icon(
                imageVector        = Icons.Default.PlayArrow,
                contentDescription = "Now playing",
                tint               = NSColors.accent,
                modifier           = Modifier.size(PLAY_ICON_SIZE),
            )
            current != null -> Text(
                text  = current.timeRemainingString,
                style = NSType.monoSmall(),
                color = Color.White.copy(alpha = 0.3f),
            )
            next != null -> Text(
                text  = next.startTimeString,
                style = NSType.monoSmall(),
                color = NSColors.accent.copy(alpha = 0.7f),
            )
        }
    }
}