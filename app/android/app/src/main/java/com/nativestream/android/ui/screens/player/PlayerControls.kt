// app/src/main/java/com/nativestream/android/ui/screens/player/PlayerControls.kt
//
// Top gradient + back/title bar. Bottom gradient + progress + control buttons.
// Controls auto-hide after 3s.

package com.nativestream.android.ui.screens.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import com.adamglin.phosphoricons.regular.Screencast
import com.adamglin.phosphoricons.regular.PictureInPicture
import com.adamglin.phosphoricons.regular.ArrowsOut
import com.adamglin.phosphoricons.regular.ArrowsIn
import com.adamglin.phosphoricons.regular.SkipBack
import com.adamglin.phosphoricons.regular.SkipForward
import com.adamglin.phosphoricons.regular.MonitorPlay
import com.adamglin.phosphoricons.regular.Play
import com.adamglin.phosphoricons.regular.Pause
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import com.nativestream.android.ui.LocalWindowSizeClass
import com.adamglin.PhosphorIcons
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.remember
import androidx.media3.ui.AspectRatioFrameLayout
import com.adamglin.phosphoricons.Regular
import com.nativestream.android.R
import com.nativestream.android.domain.model.Channel
import com.nativestream.android.domain.model.LiveEligibility
import com.nativestream.android.domain.model.Programme
import com.nativestream.android.ui.components.LiveBadge
import com.nativestream.android.ui.components.ProgressBar
import com.nativestream.android.ui.components.QualityBadge
import com.nativestream.android.ui.theme.NSDimens
import com.nativestream.android.ui.theme.NSGradients
import com.nativestream.android.ui.theme.NSType
import com.nativestream.android.ui.viewmodel.PlayerViewModel

val CTRL_ICON_SIZE      = 18.dp


@Composable
fun PlayerControlsOverlay(
    playerViewModel: PlayerViewModel,
    channelName: String,
    programmeTitle: String,
    onBack: () -> Unit,
    onToggleSidebar: () -> Unit,
    onPip: () -> Unit,
    onNextChannel: () -> Unit,
    onPreviousChannel: () -> Unit,
    modifier: Modifier = Modifier,
    isCastAvailable: Boolean = false,
    onCast: () -> Unit = {},
    onLmcCast: () -> Unit = {},
    resizeMode: Int,
    onToggleResize: () -> Unit,
    channel: Channel?,
    programme: Programme?,
) {
    val windowSizeClass = LocalWindowSizeClass.current
    val isExpanded      = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Expanded

    val primarySize   = if (isExpanded) 52.dp else 44.dp
    val secondarySize = if (isExpanded) 44.dp else 36.dp
    val controlsVisible by playerViewModel.controlsVisible.collectAsState()
    val isInPip          by playerViewModel.isInPip.collectAsState()
    val isPlaying       by playerViewModel.isPlaying.collectAsState()
    val channelList     by playerViewModel.channelList.collectAsState()
    val hasPlaylistContext = remember(channel, channelList) {
        channel != null && channelList.any { it.id == channel.id }
    }
    val isMuted         by playerViewModel.isMuted.collectAsState()
    val videoQuality    by playerViewModel.videoQuality.collectAsState()
    val sessionQuality    by playerViewModel.sessionQuality.collectAsState()

    AnimatedVisibility(
        visible  = controlsVisible && !isInPip,
        enter    = fadeIn(),
        exit     = fadeOut(),
        modifier = modifier,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {

            // ── Top gradient + info bar ───────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(NSGradients.playerTop)
                    .padding(NSDimens.current.spacing.md)
                    .align(Alignment.TopCenter),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(NSDimens.current.spacing.sm),
                ) {
                    ControlButton(
                        icon               = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        onClick            = onBack,
                        size               = secondarySize,
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text     = channelName,
                            style    = NSType.caption(),
                            color    = Color.White.copy(alpha = 0.6f),
                        )
                        Text(
                            text     = programmeTitle,
                            style    = NSType.bodyMedium(),
                            color    = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    LiveBadge(isLive = LiveEligibility.isLive(channel, programme))
                    QualityBadge(
                        label   = sessionQuality?.label ?: (videoQuality ?: "Auto"),
                        onClick = { playerViewModel.cycleSessionQuality() },
                    )
                    if (isCastAvailable) {
                        ControlButton(
                            icon               = PhosphorIcons.Regular.Screencast,
                            contentDescription = "Cast",
                            onClick            = onCast,
                            size               = secondarySize,
                        )
                    }
                    ControlButton(
                        icon               = PhosphorIcons.Regular.MonitorPlay,
                        contentDescription = "Send to device",
                        onClick            = onLmcCast,
                        size               = secondarySize,
                    )
                    ControlButton(
                        icon               = Icons.Default.Close,
                        contentDescription = "Stop",
                        onClick            = { playerViewModel.stop(); onBack() },
                        size               = secondarySize,
                    )
                }
            }

            // ── Bottom gradient + controls ────────────────────────────────────
            Column(
                verticalArrangement = Arrangement.spacedBy(NSDimens.current.spacing.sm),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(NSGradients.playerBottom)
                    .padding(NSDimens.current.spacing.md)
                    .align(Alignment.BottomCenter),
            ) {
                ProgressBar(value = programme?.progress?.toFloat() ?: 1f)

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(NSDimens.current.spacing.sm),
                ) {
                    if (hasPlaylistContext) {
                        ControlButton(
                            icon               = PhosphorIcons.Regular.SkipBack,
                            contentDescription = "Previous channel",
                            onClick            = onPreviousChannel,
                            size               = secondarySize,
                        )
                    }
                    ControlButton(
                        icon               = if (isPlaying) PhosphorIcons.Regular.Pause
                        else PhosphorIcons.Regular.Play,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        onClick            = { playerViewModel.togglePlayback() },
                        size               = primarySize,
                        isPrimary          = true,
                    )
                    if (hasPlaylistContext) {
                        ControlButton(
                            icon               = PhosphorIcons.Regular.SkipForward,
                            contentDescription = "Next channel",
                            onClick            = onNextChannel,
                            size               = secondarySize,
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    ControlButton(
                        icon               = ImageVector.vectorResource(
                            if (isMuted) R.drawable.ic_volume_off else R.drawable.ic_volume_on
                        ),
                        contentDescription = if (isMuted) "Unmute" else "Mute",
                        onClick            = { playerViewModel.toggleMute() },
                        size               = secondarySize,
                    )
                    ControlButton(
                        icon               = PhosphorIcons.Regular.PictureInPicture,
                        contentDescription = "Picture in picture",
                        onClick            = onPip,
                        size               = secondarySize,
                    )
                    ControlButton(
                        icon               = ImageVector.vectorResource(R.drawable.ic_sidebar),
                        contentDescription = "Toggle channel list",
                        onClick            = onToggleSidebar,
                        size               = secondarySize,
                    )
                    ControlButton(
                        icon               = if (resizeMode == AspectRatioFrameLayout.RESIZE_MODE_FILL)
                            PhosphorIcons.Regular.ArrowsIn
                        else PhosphorIcons.Regular.ArrowsOut,
                        contentDescription = "Toggle fill",
                        onClick            = onToggleResize,
                        size               = secondarySize,
                    )
                }
            }
        }
    }
}