// app/src/main/java/com/nativestream/android/ui/screens/player/PlayerControls.kt
//
// Top gradient + back/title bar. Bottom gradient + progress + control buttons.
// Controls auto-hide after 3s.

package com.nativestream.android.ui.screens.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import com.adamglin.phosphoricons.regular.Screencast
import com.adamglin.phosphoricons.regular.PictureInPicture
import com.adamglin.phosphoricons.regular.ArrowsOut
import com.adamglin.phosphoricons.regular.ArrowsIn
import com.adamglin.phosphoricons.regular.SkipBack
import com.adamglin.phosphoricons.regular.SkipForward
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import com.nativestream.android.ui.LocalWindowSizeClass
import androidx.compose.ui.unit.Dp
import com.adamglin.PhosphorIcons
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.filled.Close
import androidx.media3.ui.AspectRatioFrameLayout
import com.adamglin.phosphoricons.Regular
import com.nativestream.android.R
import com.nativestream.android.domain.model.Channel
import com.nativestream.android.domain.model.LiveEligibility
import com.nativestream.android.domain.model.Programme
import com.nativestream.android.ui.components.NSLiveBadge
import com.nativestream.android.ui.components.NSProgressBar
import com.nativestream.android.ui.theme.NSColors
import com.nativestream.android.ui.theme.NSDimens
import com.nativestream.android.ui.theme.NSGradients
import com.nativestream.android.ui.theme.NSType
import com.nativestream.android.ui.viewmodel.PlayerViewModel

private val CTRL_ICON_SIZE      = 18.dp
private val CTRL_RADIUS         = 50   // percent — circular
private val LIVE_BADGE_RADIUS   = 4.dp
private val QUALITY_BADGE_H_PAD = 5.dp
private val QUALITY_BADGE_V_PAD = 2.dp

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
    resizeMode: Int,
    onToggleResize: () -> Unit,
    channel: Channel?,
    programme: Programme?,
) {
    val windowSizeClass = LocalWindowSizeClass.current
    val isExpanded      = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Expanded
    val iconSize = if (isExpanded) 22.dp else CTRL_ICON_SIZE

    val primarySize   = if (isExpanded) 52.dp else 44.dp
    val secondarySize = if (isExpanded) 44.dp else 36.dp
    val controlsVisible by playerViewModel.controlsVisible.collectAsState()
    val isPlaying       by playerViewModel.isPlaying.collectAsState()
    val isMuted         by playerViewModel.isMuted.collectAsState()
    val videoQuality    by playerViewModel.videoQuality.collectAsState()

    AnimatedVisibility(
        visible  = controlsVisible,
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
                    NSLiveBadge(isLive = LiveEligibility.isLive(channel, programme))
                    videoQuality?.let { quality ->
                        QualityBadge(label = quality)
                    }
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
                NSProgressBar(value = programme?.progress?.toFloat() ?: 1f)

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(NSDimens.current.spacing.sm),
                ) {
                    ControlButton(
                        icon               = PhosphorIcons.Regular.SkipBack,
                        contentDescription = "Previous channel",
                        onClick            = onPreviousChannel,
                        size               = secondarySize,
                    )
                    ControlButton(
                        icon               = ImageVector.vectorResource(
                            if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
                        ),
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        onClick            = { playerViewModel.togglePlayback() },
                        size               = primarySize,
                        isPrimary          = true,
                    )
                    ControlButton(
                        icon               = PhosphorIcons.Regular.SkipForward,
                        contentDescription = "Next channel",
                        onClick            = onNextChannel,
                        size               = secondarySize,
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    ControlButton(
                        icon               = ImageVector.vectorResource(
                            if (isMuted) R.drawable.ic_volume_off else R.drawable.ic_volume_on
                        ),
                        contentDescription = if (isMuted) "Unmute" else "Mute",
                        onClick            = { playerViewModel.toggleMute() },
                        size               = secondarySize,
                    )
                    if (isCastAvailable){
                        ControlButton(
                            icon               = PhosphorIcons.Regular.Screencast,
                            contentDescription = "Cast",
                            onClick            = onCast,
                            size               = secondarySize,
                        )
                    }
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

// ── Control button ────────────────────────────────────────────────────────────

@Composable
fun ControlButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    size: Dp,
    isPrimary: Boolean = false,
    iconSize: Dp = CTRL_ICON_SIZE,
) {
    val background = if (isPrimary) NSColors.accent else Color.White.copy(alpha = 0.12f)
    val tint       = if (isPrimary) NSColors.bg     else Color.White.copy(alpha = 0.85f)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(percent = CTRL_RADIUS))
            .background(background)
            .clickable(onClick = onClick),
    ) {
        Icon(
            imageVector        = icon,
            contentDescription = contentDescription,
            tint               = tint,
            modifier           = Modifier.size(iconSize),
        )
    }
}

@Composable
private fun QualityBadge(label: String) {
    Text(
        text  = label,
        style = NSType.monoSmall(),
        color = Color.White.copy(alpha = 0.7f),
        modifier = Modifier
            .clip(RoundedCornerShape(LIVE_BADGE_RADIUS))
            .background(Color.White.copy(alpha = 0.15f))
            .padding(horizontal = QUALITY_BADGE_H_PAD, vertical = QUALITY_BADGE_V_PAD),
    )
}