// app/src/main/java/com/nativestream/android/ui/components/ConnectBar.kt
//
// Connect Bar — lightweight ambient strip shown above the bottom nav when a
// remote device is playing. Single job: "is something playing elsewhere?"
// Tap to open RemoteScreen for full controls.

package com.nativestream.android.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.regular.MonitorPlay
import com.nativestream.android.ui.theme.NSColors
import com.nativestream.android.ui.theme.NSDimens
import com.nativestream.android.ui.theme.NSType
import com.nativestream.android.ui.viewmodel.ControlViewModel

private val CONNECT_BAR_HEIGHT = 44.dp

@Composable
fun ConnectBar(
    controlViewModel: ControlViewModel,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sessions by controlViewModel.sessions.collectAsState()
    val playingSession = sessions.firstOrNull { it.playing }

    AnimatedVisibility(
        visible  = playingSession != null,
        enter    = slideInVertically { it },
        exit     = slideOutVertically { it },
        modifier = modifier,
    ) {
        val session = playingSession ?: return@AnimatedVisibility
        val dimens  = NSDimens.current

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(CONNECT_BAR_HEIGHT)
                .clip(RoundedCornerShape(topStart = dimens.radius.xl, topEnd = dimens.radius.xl))
                .background(NSColors.surface2)
                .border(
                    width = 0.5.dp,
                    color = NSColors.accentBorder,
                    shape = RoundedCornerShape(topStart = dimens.radius.xl, topEnd = dimens.radius.xl),
                )
                .clickable { onTap() },
        ) {
            // Accent top edge
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(NSColors.accent.copy(alpha = 0.4f))
                    .align(Alignment.TopCenter),
            )

            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dimens.spacing.sm),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = dimens.spacing.md)
                    .align(Alignment.Center),
            ) {
                Icon(
                    imageVector        = PhosphorIcons.Regular.MonitorPlay,
                    contentDescription = null,
                    tint               = NSColors.accent,
                    modifier           = Modifier.size(14.dp),
                )
                Text(
                    text     = session.name,
                    style    = NSType.monoSmall(),
                    color    = NSColors.accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text  = "·",
                    style = NSType.monoSmall(),
                    color = NSColors.text3,
                )
                Text(
                    text     = session.channelName.ifEmpty { session.channelId },
                    style    = NSType.captionMedium(),
                    color    = NSColors.text2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}