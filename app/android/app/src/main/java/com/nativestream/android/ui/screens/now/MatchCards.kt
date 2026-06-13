// app/src/main/java/com/nativestream/android/ui/screens/now/MatchCards.kt
//
// Match Cards
// MatchHeroCard — full width, matches hero art height from design tokens.
// MatchSmallCard — 180dp wide for horizontal LazyRow scroll.
// Both handle missing score data gracefully.

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nativestream.android.domain.model.Channel
import com.nativestream.android.domain.model.Programme
import com.nativestream.android.ui.LocalWindowSizeClass
import com.nativestream.android.ui.components.NSLiveBadge
import com.nativestream.android.ui.components.NSProgressBar
import com.nativestream.android.ui.theme.NSColors
import com.nativestream.android.ui.theme.NSDimens
import com.nativestream.android.ui.theme.NSType

private val SMALL_CARD_WIDTH  = 180.dp
private val HERO_ART_HEIGHT_LANDSCAPE = 180.dp
private val TEAM_BADGE_RADIUS = 50  // percent — circular

// Score + minute regex — "X – Y  67'" style
private val SCORE_REGEX  = Regex("""(\d+)\s*[–\-]\s*(\d+)""")
private val MINUTE_REGEX = Regex("""(\d+)'""")

// ── Hero card ─────────────────────────────────────────────────────────────────

@Composable
fun MatchHeroCard(
    channel: Channel,
    programme: Programme,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = NSDimens.current
    val windowSizeClass = LocalWindowSizeClass.current
    val artHeight = if (windowSizeClass.widthSizeClass == WindowWidthSizeClass.Expanded) {
        HERO_ART_HEIGHT_LANDSCAPE
    } else {
        dimens.match.heroArtHeight
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(dimens.radius.xl))
            .border(0.5.dp, NSColors.live.copy(alpha = 0.20f), RoundedCornerShape(dimens.radius.xl))
            .background(NSColors.surface2)
            .clickable(onClick = onClick),
    ) {
        // Art area
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .height(artHeight)
                .background(NSColors.surface3),
        ) {
            MatchScoreOverlay(
                programme  = programme,
                badgeSize  = dimens.match.heroBadgeSize,
                isHero     = true,
            )
            NSLiveBadge(
                isLive   = true,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(dimens.spacing.sm),
            )
            // Progress bar flush at bottom of art area
            NSProgressBar(
                value    = programme.progress.toFloat(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
            )
        }

        // Footer
        Column(
            modifier = Modifier.padding(
                horizontal = dimens.spacing.md,
                vertical   = dimens.spacing.sm,
            )
        ) {
            NSLiveBadge(isLive = true)
            Spacer(modifier = Modifier.height(dimens.spacing.xs))
            Text(
                text     = programme.title,
                style    = NSType.cardTitle(),
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
    }
}

// ── Small card grid (horizontal scroll) ──────────────────────────────────────

@Composable
fun MatchSmallGrid(
    items: List<ChannelWithProgramme>,
    onSelectChannel: (Channel) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(NSDimens.current.spacing.sm),
        modifier = modifier,
    ) {
        items(items, key = { it.channel.id }) { item ->
            MatchSmallCard(
                channel   = item.channel,
                programme = item.programme,
                onClick   = { onSelectChannel(item.channel) },
            )
        }
    }
}

@Composable
fun MatchSmallCard(
    channel: Channel,
    programme: Programme,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = NSDimens.current
    Column(
        modifier = modifier
            .width(SMALL_CARD_WIDTH)
            .clip(RoundedCornerShape(dimens.radius.lg))
            .border(0.5.dp, NSColors.live.copy(alpha = 0.15f), RoundedCornerShape(dimens.radius.lg))
            .background(NSColors.surface2)
            .clickable(onClick = onClick),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .height(dimens.match.smallArtHeight)
                .background(NSColors.surface3),
        ) {
            MatchScoreOverlay(
                programme = programme,
                badgeSize = dimens.match.smallBadgeSize,
                isHero    = false,
            )
        }

        Column(modifier = Modifier.padding(dimens.spacing.sm)) {
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
            Spacer(modifier = Modifier.height(dimens.spacing.xs))
            NSProgressBar(value = programme.progress.toFloat())
        }
    }
}

// ── Shared score overlay ──────────────────────────────────────────────────────

@Composable
private fun MatchScoreOverlay(
    programme: Programme,
    badgeSize: androidx.compose.ui.unit.Dp,
    isHero: Boolean,
) {
    val dimens       = NSDimens.current
    val teams        = programme.title.split(" vs ", ignoreCase = true)
    val homeInitials = teams.getOrNull(0)?.take(3)?.uppercase() ?: "???"
    val awayInitials = teams.getOrNull(1)?.take(3)?.uppercase() ?: "???"
    val scoreMatch   = SCORE_REGEX.find(programme.title)
    val minuteMatch  = MINUTE_REGEX.find(programme.title)

    Row(
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        TeamBadge(initials = homeInitials, size = badgeSize)
        Spacer(modifier = Modifier.width(dimens.spacing.md))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (scoreMatch != null) {
                Text(
                    text      = "${scoreMatch.groupValues[1]} – ${scoreMatch.groupValues[2]}",
                    style     = if (isHero) NSType.scoreXL() else NSType.display(),
                    color     = Color.White,
                    textAlign = TextAlign.Center,
                )
            } else {
                Text(
                    text  = "vs",
                    style = if (isHero) NSType.display() else NSType.body(),
                    color = NSColors.text3,
                )
            }
            minuteMatch?.let {
                Text(
                    text  = it.value,
                    style = NSType.caption(),
                    color = NSColors.live,
                )
            }
        }
        Spacer(modifier = Modifier.width(dimens.spacing.md))
        TeamBadge(initials = awayInitials, size = badgeSize)
    }
}

@Composable
private fun TeamBadge(initials: String, size: androidx.compose.ui.unit.Dp) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .width(size)
            .height(size)
            .clip(RoundedCornerShape(percent = TEAM_BADGE_RADIUS))
            .background(NSColors.surface3)
            .border(0.5.dp, NSColors.border2, RoundedCornerShape(percent = TEAM_BADGE_RADIUS)),
    ) {
        Text(
            text      = initials,
            style     = NSType.label(),
            color     = NSColors.text2,
            textAlign = TextAlign.Center,
        )
    }
}