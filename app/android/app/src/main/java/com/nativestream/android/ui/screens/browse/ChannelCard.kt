// app/src/main/java/com/nativestream/android/ui/screens/browse/ChannelCard.kt
//
// Image-first card used in the Browse grid:
//   - 16:9 logo area via ChannelLogoView (Coil + initials fallback)
//   - LIVE badge top-left, star / ▶NOW badge top-right
//   - Progress bar bottom edge of artwork
//   - Playing state: accentBorder stroke + ▶NOW badge
//   - Ripple on press

package com.nativestream.android.ui.screens.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
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
import com.nativestream.android.domain.model.Channel
import com.nativestream.android.domain.model.LiveEligibility
import com.nativestream.android.ui.components.NSLiveBadge
import com.nativestream.android.ui.components.NSProgressBar
import com.nativestream.android.ui.theme.NSColors
import com.nativestream.android.ui.theme.NSDimens
import com.nativestream.android.ui.theme.NSType
import com.nativestream.android.ui.viewmodel.EpgViewModel
import com.nativestream.android.ui.viewmodel.FavouritesViewModel
import com.nativestream.android.ui.viewmodel.PlayerViewModel

private val BADGE_H_PADDING = 6.dp
private val BADGE_V_PADDING = 3.dp
private val STAR_ICON_SIZE  = 12.dp
private val NOW_ICON_SIZE   = 7.dp

@Composable
fun ChannelCard(
    channel: Channel,
    playerViewModel: PlayerViewModel,
    epgViewModel: EpgViewModel,
    favouritesViewModel: FavouritesViewModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens        = NSDimens.current
    val activeChannel by playerViewModel.activeChannel.collectAsState()
    val favourites    by favouritesViewModel.favouriteIds.collectAsState()

    val programme  = epgViewModel.currentProgramme(channel)
    val nextProg   = epgViewModel.nextProgramme(channel)
    val isPlaying  = activeChannel?.id == channel.id
    val isLive = LiveEligibility.isLive(channel, programme)
    val isFavourite = favourites.contains(channel.id)

    val borderColor = when {
        isPlaying -> NSColors.accentBorder
        isLive    -> NSColors.live.copy(alpha = 0.157f)
        else      -> NSColors.border
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(dimens.radius.lg))
            .clickable(onClick = onClick),
    ) {
        // ── Artwork area ──────────────────────────────────────────────────────
        Box(modifier = Modifier.fillMaxWidth()) {
            ChannelLogoView(channel = channel, borderColor = borderColor)

            // Progress bar at bottom edge
            programme?.let { prog ->
                NSProgressBar(
                    value    = prog.progress.toFloat(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter),
                )
            }

            // Top overlay — LIVE badge + NOW/star
            Row(
                verticalAlignment = Alignment.Top,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
                    .align(Alignment.TopStart),
            ) {
                NSLiveBadge(isLive = isLive)
                Spacer(modifier = Modifier.weight(1f))
                if (isPlaying) {
                    NowBadge()
                } else {
                    StarButton(
                        isFavourite = isFavourite,
                        onClick     = { favouritesViewModel.toggle(channel) },
                    )
                }
            }
        }

        // ── Channel name ──────────────────────────────────────────────────────
        Text(
            text     = channel.name,
            style    = NSType.captionMedium(),
            color    = NSColors.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        // ── EPG line ──────────────────────────────────────────────────────────
        when {
            programme != null ->
                Text(
                    text     = programme.title,
                    style    = NSType.caption(),
                    color    = NSColors.accent2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            nextProg != null ->
                Text(
                    text     = nextProg.title,
                    style    = NSType.caption(),
                    color    = NSColors.text3,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
        }
    }
}

// ── ▶NOW badge ────────────────────────────────────────────────────────────────

@Composable
private fun NowBadge() {
    val dimens = NSDimens.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(dimens.radius.sm))
            .background(NSColors.accentGlow)
            .padding(horizontal = BADGE_H_PADDING, vertical = BADGE_V_PADDING),
    ) {
        Icon(
            imageVector        = ImageVector.vectorResource(R.drawable.ic_play),
            contentDescription = null,
            tint               = Color.White,
            modifier           = Modifier
                .height(NOW_ICON_SIZE)
                .width(NOW_ICON_SIZE),
        )
        Text(
            text   = "NOW",
            style  = NSType.monoSmall(),
            color  = Color.White,
        )
    }
}

// ── Star button ───────────────────────────────────────────────────────────────

@Composable
private fun StarButton(isFavourite: Boolean, onClick: () -> Unit) {
    Icon(
        imageVector        = if (isFavourite) Icons.Filled.Star else Icons.Outlined.Star,
        contentDescription = if (isFavourite) "Remove from favourites" else "Add to favourites",
        tint               = if (isFavourite) NSColors.amber else Color.White,
        modifier           = Modifier
            .height(STAR_ICON_SIZE)
            .width(STAR_ICON_SIZE)
            .clickable(onClick = onClick),
    )
}