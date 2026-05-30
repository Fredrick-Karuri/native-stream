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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.PictureInPicture
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
import com.nativestream.android.R
import com.nativestream.android.ui.components.NSLiveBadge
import com.nativestream.android.ui.components.NSProgressBar
import com.nativestream.android.ui.theme.NSColors
import com.nativestream.android.ui.theme.NSDimens
import com.nativestream.android.ui.theme.NSGradients
import com.nativestream.android.ui.theme.NSType
import com.nativestream.android.ui.viewmodel.PlayerViewModel

private val CTRL_PRIMARY_SIZE   = 44.dp
private val CTRL_SECONDARY_SIZE = 36.dp
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
    modifier: Modifier = Modifier,
) {
    val controlsVisible by playerViewModel.controlsVisible.collectAsState()
    val isPlaying       by playerViewModel.isPlaying.collectAsState()
    val isMuted         by playerViewModel.isMuted.collectAsState()

    AnimatedVisibility(
        visible  = controlsVisible,
        enter    = fadeIn(),
        exit     = fadeOut(),
        modifier = modifier,
    ) {
        Box(modifier = Modifier.matchParentSize()) {

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
                        size               = CTRL_SECONDARY_SIZE,
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
                    NSLiveBadge(isLive = true)
                    QualityBadge(label = "HD")
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
                NSProgressBar(value = 0f) // live stream — always at end

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(NSDimens.current.spacing.sm),
                ) {
                    ControlButton(
                        icon               = ImageVector.vectorResource(R.drawable.ic_skip_back),
                        contentDescription = "Skip back",
                        onClick            = { /* live stream — no-op */ },
                        size               = CTRL_SECONDARY_SIZE,
                    )
                    ControlButton(
                        icon               = ImageVector.vectorResource(
                            if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
                        ),
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        onClick            = { playerViewModel.togglePlayback() },
                        size               = CTRL_PRIMARY_SIZE,
                        isPrimary          = true,
                    )
                    ControlButton(
                        icon               = ImageVector.vectorResource(R.drawable.ic_skip_forward),
                        contentDescription = "Skip forward",
                        onClick            = { /* live stream — no-op */ },
                        size               = CTRL_SECONDARY_SIZE,
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    ControlButton(
                        icon               = ImageVector.vectorResource(
                            if (isMuted) R.drawable.ic_volume_off else R.drawable.ic_volume_on
                        ),
                        contentDescription = if (isMuted) "Unmute" else "Mute",
                        onClick            = { playerViewModel.toggleMute() },
                        size               = CTRL_SECONDARY_SIZE,
                    )
                    ControlButton(
                        icon               = Icons.Default.Cast,
                        contentDescription = "Cast",
                        onClick            = { /* AND-021 */ },
                        size               = CTRL_SECONDARY_SIZE,
                    )
                    ControlButton(
                        icon               = Icons.Default.PictureInPicture,
                        contentDescription = "Picture in picture",
                        onClick            = onPip,
                        size               = CTRL_SECONDARY_SIZE,
                    )
                    ControlButton(
                        icon               = ImageVector.vectorResource(R.drawable.ic_sidebar),
                        contentDescription = "Toggle channel list",
                        onClick            = onToggleSidebar,
                        size               = CTRL_SECONDARY_SIZE,
                    )
                }
            }
        }
    }
}

// ── Control button ────────────────────────────────────────────────────────────

@Composable
private fun ControlButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    size: androidx.compose.ui.unit.Dp,
    isPrimary: Boolean = false,
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
            modifier           = Modifier.size(CTRL_ICON_SIZE),
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