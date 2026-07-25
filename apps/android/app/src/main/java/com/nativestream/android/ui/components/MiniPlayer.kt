// app/src/main/java/com/nativestream/android/ui/components/MiniPlayer.kt
//
// Mini Player
// 64dp persistent strip above the bottom nav. Shown when a channel is playing,
// hidden inside the full player:
//   - Mini video area with score overlay for sport matches
//   - Channel name + programme title + elapsed minutes
//   - Skip back / play-pause (primary) / skip forward / progress / mute controls
//   - Swipe up → expand to full player
//   - Close button → stops playback

package com.nativestream.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nativestream.android.R
import com.nativestream.android.ui.screens.player.ChannelLogoSquare
import com.nativestream.android.ui.theme.NSColors
import com.nativestream.android.ui.theme.NSDimens
import com.nativestream.android.ui.theme.NSType
import com.nativestream.android.ui.viewmodel.EpgViewModel
import com.nativestream.android.ui.viewmodel.PlayerViewModel

private val SWIPE_UP_THRESHOLD    = -80f  // px upward drag to trigger expand

private val MINI_PLAYER_HEIGHT = 68.dp
private val MINI_LOGO_SIZE     = 48.dp

@Composable
fun MiniPlayer(
    playerViewModel: PlayerViewModel,
    epgViewModel: EpgViewModel,
    onExpand: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val activeChannel by playerViewModel.activeChannel.collectAsState()
    val isPlaying     by playerViewModel.isPlaying.collectAsState()
    val dimens = NSDimens.current

    val currentProgramme = activeChannel?.let { epgViewModel.currentProgramme(it) }

    Box(
        modifier = modifier
            .fillMaxWidth() // Expands across the entire width of the screen shell
            .height(MINI_PLAYER_HEIGHT)
            .clip(RoundedCornerShape(topStart = dimens.radius.xl, topEnd = dimens.radius.xl))
            .background(NSColors.surface)
            .border(
                width = 0.5.dp,
                color = NSColors.border2,
                shape = RoundedCornerShape(topStart = dimens.radius.xl, topEnd = dimens.radius.xl),
            )
            .clickable { onExpand() } // Entire bar expands player on tap
            .pointerInput(Unit) {
                detectVerticalDragGestures { _, dragAmount ->
                    if (dragAmount < SWIPE_UP_THRESHOLD) onExpand()
                }
            },
    ) {
        // Main Horizontal Content Wrapper
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = dimens.spacing.md, vertical = dimens.spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.spacing.md)
        ) {
            // ── Left: Channel Logo ───────────────────────────────────────────
            activeChannel?.let { channel ->
                ChannelLogoSquare(
                    channel      = channel,
                    size         = MINI_LOGO_SIZE,
                    cornerRadius = dimens.radius.md,
                )
            }

            // ── Middle: Info Metadata Column ──────────────────────────────────
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                activeChannel?.let { channel ->
                    Text(
                        text     = channel.name,
                        style    = NSType.captionMedium(),
                        color    = NSColors.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                currentProgramme?.let { prog ->
                    Text(
                        text     = prog.title,
                        style    = NSType.caption(),
                        color    = NSColors.text3,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // ── Right: Consolidated Actions ───────────────────────────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dimens.spacing.sm)
            ) {
                // Play / Pause Button
                IconButton(
                    icon = ImageVector.vectorResource(
                        if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
                    ),
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    onClick = {
                        // Stop propagation so it doesn't fire the outer onExpand click
                        playerViewModel.togglePlayback()
                    },
                    size   = 24.dp,
                    isDark = false,
                )

                // Close Button
                IconButton(
                    icon               = Icons.Default.Close,
                    contentDescription = "Close player",
                    onClick            = onClose,
                    size               = 24.dp,
                    isDark             = false,
                )
            }
        }

        // ── Bottom Edge: Timeline Progress Tracker ────────────────────────────
        if (currentProgramme != null) {
            ProgressBar(
                value    = currentProgramme.progress.toFloat(),
                height   = 2.dp, // Ultra thin edge tracking line
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
            )
        }
    }
}