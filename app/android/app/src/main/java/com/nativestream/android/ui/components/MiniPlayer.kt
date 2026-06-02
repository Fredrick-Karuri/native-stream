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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.vectorResource
import com.adamglin.phosphoricons.regular.SkipBack
import com.adamglin.phosphoricons.regular.SkipForward
import com.adamglin.PhosphorIcons
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.adamglin.phosphoricons.Regular
import com.nativestream.android.R
import com.nativestream.android.domain.model.Programme
import com.nativestream.android.ui.theme.NSColors
import com.nativestream.android.ui.theme.NSDimens
import com.nativestream.android.ui.theme.NSType
import com.nativestream.android.ui.viewmodel.EpgViewModel
import com.nativestream.android.ui.viewmodel.PlayerViewModel

private val MINI_VIDEO_HEIGHT     = 140.dp
private val MINI_PLAYER_WIDTH     = 256.dp
private val MINI_CTRL_SIZE        = 26.dp
private val MINI_CTRL_RADIUS      = 7.dp
private val MINI_CTRL_ICON_SIZE   = 10.dp
private val MINI_INFO_H_PADDING   = 12.dp
private val MINI_INFO_V_PADDING   = 10.dp
private val MINI_CTRL_SPACING     = 6.dp
private val SWIPE_UP_THRESHOLD    = -80f  // px upward drag to trigger expand

// Score pattern — mirrors extractScore in MiniPlayerWidget.swift
private val SCORE_REGEX = Regex("""(\d+)\s*[–\-]\s*(\d+)""")

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
    val isMuted       by playerViewModel.isMuted.collectAsState()

    val currentProgramme = activeChannel?.let { epgViewModel.currentProgramme(it) }
    val dimens = NSDimens.current

    Box(
        modifier = modifier
            .width(MINI_PLAYER_WIDTH)
            .clip(RoundedCornerShape(dimens.radius.xl))
            .border(
                width = 0.5.dp,
                color = NSColors.border2,
                shape = RoundedCornerShape(dimens.radius.xl),
            )
            .pointerInput(Unit) {
                detectVerticalDragGestures { _, dragAmount ->
                    if (dragAmount < SWIPE_UP_THRESHOLD) onExpand()
                }
            },
    ) {
        Column {
            // ── Mini video area ───────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(MINI_VIDEO_HEIGHT)
                    .background(NSColors.bg),
            ) {
                // Score or programme title overlay
                currentProgramme?.let { prog ->
                    val overlayText = extractScore(prog.title) ?: when {
                        prog.isSportMatch -> "● Live"
                        else              -> prog.title
                    }
                    val isScoreOrLive = extractScore(prog.title) != null || prog.isSportMatch
                    Text(
                        text     = overlayText,
                        style    = if (isScoreOrLive) NSType.scoreXL() else NSType.captionMedium(),
                        color    = Color.White,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }

                // Top control strip — live badge + expand + close
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                        .align(Alignment.TopStart),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    NSLiveBadge(isLive = currentProgramme?.isSportMatch == true)
                    Spacer(modifier = Modifier.weight(1f))
                    NSIconButton(
                        icon               = Icons.Default.KeyboardArrowUp,
                        contentDescription = "Expand player",
                        onClick            = onExpand,
                        size               = 22.dp,
                        isDark             = true,
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    NSIconButton(
                        icon               = Icons.Default.Close,
                        contentDescription = "Close player",
                        onClick            = onClose,
                        size               = 22.dp,
                        isDark             = true,
                    )
                }
            }

            // ── Info + controls strip ─────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(NSColors.surface)
                    .padding(
                        horizontal = MINI_INFO_H_PADDING,
                        vertical   = MINI_INFO_V_PADDING,
                    ),
                verticalArrangement = Arrangement.spacedBy(dimens.spacing.sm),
            ) {
                activeChannel?.let { channel ->
                    Text(
                        text     = channel.name,
                        style    = NSType.captionMedium(),
                        color    = NSColors.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    currentProgramme?.let { prog ->
                        Text(
                            text     = "${prog.title} · ${elapsedMinutes(prog)}",
                            style    = NSType.caption(),
                            color    = NSColors.text3,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Row(
                    verticalAlignment   = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MINI_CTRL_SPACING),
                ) {
                    MiniControl(
                        icon               = PhosphorIcons.Regular.SkipBack,
                        contentDescription = "Previous channel",
                        onClick            = { playerViewModel.playPreviousChannel() },
                    )
                    MiniControl(
                        icon               = ImageVector.vectorResource(
                            if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
                        ),
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        isPrimary          = true,
                        onClick            = { playerViewModel.togglePlayback() },
                    )
                    MiniControl(
                        icon               = PhosphorIcons.Regular.SkipForward,
                        contentDescription = "Next channel",
                        onClick            = { playerViewModel.playNextChannel() },
                    )
                    NSProgressBar(
                        value    = currentProgramme?.progress?.toFloat() ?: 0f,
                        modifier = Modifier.weight(1f),
                    )
                    MiniControl(
                        icon               = ImageVector.vectorResource(
                            if (isMuted) R.drawable.ic_volume_off else R.drawable.ic_volume_on
                        ),
                        contentDescription = if (isMuted) "Unmute" else "Mute",
                        onClick            = { playerViewModel.toggleMute() },
                    )
                }
            }
        }
    }
}

// ── Mini control button ─────────

@Composable
private fun MiniControl(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    isPrimary: Boolean = false,
) {
    val background  = if (isPrimary) NSColors.accent    else NSColors.surface2
    val iconTint    = if (isPrimary) NSColors.bg         else NSColors.text2
    val borderColor = if (isPrimary) NSColors.accent     else NSColors.border

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(MINI_CTRL_SIZE)
            .clip(RoundedCornerShape(MINI_CTRL_RADIUS))
            .background(background)
            .border(0.5.dp, borderColor, RoundedCornerShape(MINI_CTRL_RADIUS))
            .clickable(onClick = onClick),
    ) {
        Icon(
            imageVector        = icon,
            contentDescription = contentDescription,
            tint               = iconTint,
            modifier           = Modifier.size(MINI_CTRL_ICON_SIZE),
        )
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

/** Extracts "X – Y" score string from a programme title, or null if none found. */
private fun extractScore(title: String): String? {
    val match = SCORE_REGEX.find(title) ?: return null
    return "${match.groupValues[1]} – ${match.groupValues[2]}"
}

/** Elapsed minutes since programme start — mirrors minuteStr() in Swift. */
private fun elapsedMinutes(programme: Programme): String {
    val elapsedMs = System.currentTimeMillis() - programme.startEpochMs
    val minutes   = (elapsedMs / 60_000L).coerceAtLeast(0L)
    return "$minutes'"
}