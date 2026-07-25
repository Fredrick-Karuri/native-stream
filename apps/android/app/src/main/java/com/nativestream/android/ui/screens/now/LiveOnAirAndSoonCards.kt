// app/src/main/java/com/nativestream/android/ui/screens/now/LiveOnAirAndSoonCards.kt
//
// Live On Air Row + Starting Soon Card
// LiveOnAirRow: Coil logo square (48dp), programme title, channel, progress bar, LIVE badge.
// StartingSoonCard: 160dp, kick-off time bold accent, team badges inline, title, channel.

package com.nativestream.android.ui.screens.now

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import com.nativestream.android.domain.model.Channel
import com.nativestream.android.domain.model.Programme
import com.nativestream.android.ui.components.LiveBadge
import com.nativestream.android.ui.components.ProgressBar
import com.nativestream.android.ui.theme.NSColors
import com.nativestream.android.ui.theme.NSDimens
import com.nativestream.android.ui.theme.NSType

private val LOGO_SIZE        = 48.dp
private val LOGO_RADIUS      = 8.dp
private val SOON_CARD_WIDTH  = 160.dp
private val TEAM_BADGE_SIZE  = 20.dp

// ── Live On Air Row ───────────────────────────────────────────────────────────

@Composable
fun LiveOnAirRow(
    channel: Channel,
    programme: Programme,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = NSDimens.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(dimens.radius.lg))
            .background(NSColors.surface2)
            .border(0.5.dp, NSColors.border, RoundedCornerShape(dimens.radius.lg))
            .clickable(onClick = onClick),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(dimens.spacing.md),
        ) {
            // Channel logo — Coil with initials fallback
            ChannelLogoSquare(channel = channel, size = LOGO_SIZE, cornerRadius = LOGO_RADIUS)

            Spacer(modifier = Modifier.width(dimens.spacing.md))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text     = programme.title,
                    style    = NSType.bodyMedium(),
                    color    = NSColors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text  = channel.name,
                    style = NSType.caption(),
                    color = NSColors.text3,
                )
            }

            Spacer(modifier = Modifier.width(dimens.spacing.md))
            LiveBadge(isLive = true)
        }

        // Progress bar flush at bottom, full width, no horizontal padding
        ProgressBar(
            value    = programme.progress.toFloat(),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// ── Inline logo square used inside LiveOnAirRow ───────────────────────────────

@Composable
private fun ChannelLogoSquare(channel: Channel, size: androidx.compose.ui.unit.Dp, cornerRadius: androidx.compose.ui.unit.Dp) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(cornerRadius))
            .background(NSColors.surface3)
            .border(0.5.dp, NSColors.border2, RoundedCornerShape(cornerRadius)),
    ) {
        if (!channel.logoUrl.isNullOrBlank()) {
            SubcomposeAsyncImage(
                model              = channel.logoUrl,
                contentDescription = channel.name,
                contentScale       = ContentScale.Fit,
                modifier           = Modifier.padding(4.dp),
                error              = { ChannelInitials(channel.name) },
                loading            = { ChannelInitials(channel.name) },
            )
        } else {
            ChannelInitials(channel.name)
        }
    }
}

@Composable
private fun ChannelInitials(name: String) {
    Text(text = name.take(3).uppercase(), style = NSType.label(), color = NSColors.text3)
}

// ── Starting Soon grid + card ─────────────────────────────────────────────────

@Composable
fun StartingSoonGrid(
    items: List<ChannelWithProgramme>,
    onSelectChannel: (Channel) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(NSDimens.current.spacing.sm),
        modifier = modifier,
    ) {
        items(items, key = { it.channel.id }) { item ->
            StartingSoonCard(
                channel   = item.channel,
                programme = item.programme,
                onClick   = { onSelectChannel(item.channel) },
            )
        }
    }
}

@Composable
fun StartingSoonCard(
    channel: Channel,
    programme: Programme,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = NSDimens.current
    Column(
        verticalArrangement = Arrangement.spacedBy(dimens.spacing.xs),
        modifier = modifier
            .width(SOON_CARD_WIDTH)
            .clip(RoundedCornerShape(dimens.radius.lg))
            .background(NSColors.surface2)
            .border(0.5.dp, NSColors.border, RoundedCornerShape(dimens.radius.lg))
            .clickable(onClick = onClick)
            .padding(dimens.spacing.md),
    ) {
        // Kick-off time in accent colour
        Text(
            text  = programme.startTimeString,
            style = NSType.captionMedium(),
            color = NSColors.accent,
        )

        // Team badges — tight row matching design
        val teams = programme.title.split(" vs ", ignoreCase = true)
        if (teams.size == 2) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dimens.spacing.xs),
            ) {
                TeamBadge(initials = teams[0].trim().take(3).uppercase())
                Text(text = "vs", style = NSType.caption(), color = NSColors.text3)
                TeamBadge(initials = teams[1].trim().take(3).uppercase())
            }
        }

        Text(
            text     = programme.title,
            style    = NSType.captionMedium(),
            color    = NSColors.text,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text  = channel.name,
            style = NSType.caption(),
            color = NSColors.text3,
        )
    }
}

@Composable
private fun TeamBadge(initials: String) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(TEAM_BADGE_SIZE)
            .clip(CircleShape)
            .background(NSColors.surface3)
            .border(0.5.dp, NSColors.border2, CircleShape),
    ) {
        Text(text = initials, style = NSType.caption(), color = NSColors.text2)
    }
}