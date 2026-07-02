// app/src/main/java/com/nativestream/android/ui/components/ConnectBar.kt
//
// Connect Bar — persistent strip shown above the bottom nav when a remote
// device is playing. Mirrors Spotify Connect bar UX: device name, channel,
// volume slider, stop and pull-back actions.

package com.nativestream.android.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.regular.MonitorPlay
import com.adamglin.phosphoricons.regular.SpeakerHigh
import com.nativestream.android.domain.model.control.SessionInfo
import com.nativestream.android.ui.theme.NSColors
import com.nativestream.android.ui.theme.NSDimens
import com.nativestream.android.ui.theme.NSType
import com.nativestream.android.ui.viewmodel.ControlViewModel

private val CONNECT_BAR_HEIGHT = 80.dp

@Composable
fun ConnectBar(
    controlViewModel: ControlViewModel,
    modifier: Modifier = Modifier,
) {
    val sessions by controlViewModel.sessions.collectAsState()
    val playingSession = sessions.firstOrNull { it.playing }

    AnimatedVisibility(
        visible = playingSession != null,
        enter   = slideInVertically { it },
        exit    = slideOutVertically { it },
        modifier = modifier,
    ) {
        playingSession?.let { session ->
            ConnectBarContent(
                session         = session,
                onStop          = { controlViewModel.stop(session.deviceId) },
                onPullBack      = { controlViewModel.pullBack(session.deviceId) },
                onVolumeChange  = { controlViewModel.setVolume(session.deviceId, it) },
            )
        }
    }
}

@Composable
private fun ConnectBarContent(
    session: SessionInfo,
    onStop: () -> Unit,
    onPullBack: () -> Unit,
    onVolumeChange: (Float) -> Unit,
) {
    val dimens = NSDimens.current
    var volume by remember { mutableFloatStateOf(1f) }

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
            ),
    ) {
        // Accent top edge to distinguish from MiniPlayer
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(NSColors.accent.copy(alpha = 0.4f))
                .align(Alignment.TopCenter),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = dimens.spacing.md)
                .padding(top = dimens.spacing.sm, bottom = dimens.spacing.xs),
            verticalArrangement = Arrangement.spacedBy(dimens.spacing.xs),
        ) {
            // ── Top row: device info + actions ────────────────────────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dimens.spacing.sm),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector        = PhosphorIcons.Regular.MonitorPlay,
                    contentDescription = null,
                    tint               = NSColors.accent,
                    modifier           = Modifier.size(12.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text     = session.name,
                        style    = NSType.monoSmall(),
                        color    = NSColors.accent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text     = session.channelName.ifEmpty { session.channelId },
                        style    = NSType.captionMedium(),
                        color    = NSColors.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                SheetActionButton(
                    label     = "Pull Back",
                    isPrimary = false,
                    enabled   = true,
                    onClick   = onPullBack,
                )
                SheetActionButton(
                    label     = "Stop",
                    isPrimary = false,
                    enabled   = true,
                    onClick   = onStop,
                )
            }

            // ── Volume row ────────────────────────────────────────────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dimens.spacing.sm),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector        = PhosphorIcons.Regular.SpeakerHigh,
                    contentDescription = "Volume",
                    tint               = NSColors.text3,
                    modifier           = Modifier.size(12.dp),
                )
                Slider(
                    value         = volume,
                    onValueChange = { volume = it },
                    onValueChangeFinished = { onVolumeChange(volume) },
                    modifier      = Modifier.weight(1f).height(20.dp),
                    colors        = SliderDefaults.colors(
                        thumbColor            = NSColors.accent,
                        activeTrackColor      = NSColors.accent,
                        inactiveTrackColor    = NSColors.surface3,
                    ),
                )
            }
        }
    }
}