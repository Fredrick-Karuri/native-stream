// app/src/main/java/com/nativestream/android/ui/screens/now/LiveOnAirAndSoonCards.kt
//
// NS-012: Live On Air Row + Starting Soon Card (AND-012)
// LiveOnAirRow  — 48dp logo, programme, channel, progress, LIVE badge.
// StartingSoonCard — 160dp wide horizontal scroll card, kick-off time, teams, channel.

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nativestream.android.domain.model.Channel
import com.nativestream.android.domain.model.Programme
import com.nativestream.android.ui.components.NSLiveBadge
import com.nativestream.android.ui.components.NSProgressBar
import com.nativestream.android.ui.theme.NSColors
import com.nativestream.android.ui.theme.NSDimens
import com.nativestream.android.ui.theme.NSType

private val LOGO_SIZE          = 48.dp
private val LOGO_RADIUS        = 8.dp
private val SOON_CARD_WIDTH    = 160.dp
private val TEAM_BADGE_SIZE    = 18.dp
private const val TEAM_BADGE_RADIUS = 50 // percent

// ── Live On Air Row ───────────────────────────────────────────────────────────

@Composable
fun LiveOnAirRow(
    channel: Channel,
    programme: Programme,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = NSDimens.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(dimens.radius.lg))
            .background(NSColors.surface2)
            .border(0.5.dp, NSColors.border, RoundedCornerShape(dimens.radius.lg))
            .clickable(onClick = onClick)
            .padding(dimens.spacing.md),
    ) {
        // Channel logo placeholder
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(LOGO_SIZE)
                .clip(RoundedCornerShape(LOGO_RADIUS))
                .background(NSColors.surface3)
                .border(0.5.dp, NSColors.border2, RoundedCornerShape(LOGO_RADIUS)),
        ) {
            Text(
                text  = channel.name.take(3).uppercase(),
                style = NSType.label(),
                color = NSColors.text3,
            )
        }

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
            Spacer(modifier = Modifier.height(dimens.spacing.xs))
            NSProgressBar(value = programme.progress.toFloat())
        }

        Spacer(modifier = Modifier.width(dimens.spacing.md))
        NSLiveBadge(isLive = true)
    }
}

// ── Starting Soon Card ────────────────────────────────────────────────────────

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
        modifier = modifier
            .width(SOON_CARD_WIDTH)
            .clip(RoundedCornerShape(dimens.radius.lg))
            .background(NSColors.surface2)
            .border(0.5.dp, NSColors.border, RoundedCornerShape(dimens.radius.lg))
            .clickable(onClick = onClick)
            .padding(dimens.spacing.md),
    ) {
        Text(
            text  = programme.startTimeString,
            style = NSType.captionMedium(),
            color = NSColors.accent,
        )
        Spacer(modifier = Modifier.height(dimens.spacing.xs))

        // Team badges for matches, plain icon otherwise
        val teams = programme.title.split(" vs ", ignoreCase = true)
        if (teams.size == 2) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dimens.spacing.xs),
            ) {
                SoonTeamBadge(teams[0].take(3).uppercase())
                Text(text = "vs", style = NSType.caption(), color = NSColors.text3)
                SoonTeamBadge(teams[1].take(3).uppercase())
            }
            Spacer(modifier = Modifier.height(dimens.spacing.xs))
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
private fun SoonTeamBadge(initials: String) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(TEAM_BADGE_SIZE)
            .clip(RoundedCornerShape(percent = TEAM_BADGE_RADIUS))
            .background(NSColors.surface3)
            .border(0.5.dp, NSColors.border2, RoundedCornerShape(percent = TEAM_BADGE_RADIUS)),
    ) {
        Text(
            text  = initials,
            style = NSType.caption(),
            color = NSColors.text2,
        )
    }
}