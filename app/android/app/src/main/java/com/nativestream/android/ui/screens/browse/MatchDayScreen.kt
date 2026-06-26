// app/src/main/java/com/nativestream/android/ui/screens/browse/MatchDayScreen.kt
//
// Sport filter screen — live and upcoming matches for a given SportCategory.

package com.nativestream.android.ui.screens.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nativestream.android.domain.model.Channel
import com.nativestream.android.domain.model.Programme
import com.nativestream.android.domain.model.SportCategory
import com.nativestream.android.domain.repository.ChannelRepository
import com.nativestream.android.ui.components.NSGroupHeader
import com.nativestream.android.ui.components.NSProgressBar
import com.nativestream.android.ui.components.NSPulseDot
import com.nativestream.android.ui.theme.NSColors
import com.nativestream.android.ui.theme.NSDimens
import com.nativestream.android.ui.theme.NSGradients
import com.nativestream.android.ui.theme.NSType
import com.nativestream.android.ui.viewmodel.EpgViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val SCHEDULE_HOURS        = 8
private const val VS_SEPARATOR          = " vs "
private const val COMPETITION_SEPARATOR = " — "
private val SCORE_REGEX                 = Regex("""(\d+)\s*[–\-]\s*(\d+)""")
private val timeFormatter               = SimpleDateFormat("HH:mm", Locale.getDefault())
private val dateFormatter               = SimpleDateFormat("EEEE d MMM", Locale.getDefault())

@Composable
fun MatchDayScreen(
    sport: SportCategory,
    channelRepository: ChannelRepository,
    epgViewModel: EpgViewModel,
    onSelectChannel: (Channel) -> Unit,
    modifier: Modifier = Modifier,
) {
    val channels by channelRepository.channels.collectAsState()

    val allMatches = remember(channels, sport) {
        buildMatchItems(channels, sport, epgViewModel)
    }
    val liveMatches     = remember(allMatches) { allMatches.filter { it.programme.isNow } }
    val upcomingMatches = remember(allMatches) {
        val nowMs = System.currentTimeMillis()
        allMatches.filter { !it.programme.isNow && it.programme.startEpochMs > nowMs }
    }

    Column(modifier = modifier.fillMaxSize().background(NSColors.bg)) {
        MatchDayHero(sport = sport, totalCount = allMatches.size, liveCount = liveMatches.size)
        Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(NSColors.border))

        if (allMatches.isEmpty()) {
            MatchDayEmptyView(sport = sport)
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 280.dp),
                verticalArrangement = Arrangement.spacedBy(NSDimens.current.spacing.xxl),
                horizontalArrangement = Arrangement.spacedBy(NSDimens.current.spacing.md),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(NSDimens.current.spacing.xl),
            ) {
                if (liveMatches.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        MatchSectionHeader(title = "Live now", count = liveMatches.size, isLive = true)
                    }
                    items(liveMatches, key = { it.programme.id }) { match ->
                        val channel = channels.firstOrNull { it.tvgId == match.channelTvgId }
                        MatchCard(
                            match    = match,
                            onClick  = { channel?.let(onSelectChannel) },
                            modifier = Modifier.widthIn(max = 400.dp),
                        )
                    }
                }
                if (upcomingMatches.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        MatchSectionHeader(title = "Up next", count = upcomingMatches.size, isLive = false)
                    }
                    items(upcomingMatches, key = { it.programme.id }) { match ->
                        val channel = channels.firstOrNull { it.tvgId == match.channelTvgId }
                        MatchCard(
                            match    = match,
                            onClick  = { channel?.let(onSelectChannel) },
                            modifier = Modifier.widthIn(max = 400.dp),
                        )
                    }
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Spacer(modifier = Modifier.height(80.dp))
                }
            }
        }
    }
}

// ── Hero header ───────────────────────────────────────────────────────────────

@Composable
fun MatchDayHero(sport: SportCategory, totalCount: Int, liveCount: Int) {
    val dimens = NSDimens.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(NSColors.surface)
            .padding(horizontal = dimens.spacing.xl),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = sport.label, style = NSType.heading(), color = NSColors.text)
            Text(
                text  = "${dateFormatter.format(Date())} · $totalCount matches",
                style = NSType.caption(),
                color = NSColors.text3,
            )
        }
        if (liveCount > 0) {
            Spacer(modifier = Modifier.width(dimens.spacing.lg))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dimens.spacing.xs),
                modifier = Modifier
                    .clip(RoundedCornerShape(dimens.radius.sm))
                    .background(NSColors.live)
                    .padding(horizontal = dimens.spacing.md, vertical = dimens.spacing.xs),
            ) {
                NSPulseDot()
                Text(
                    text       = "$liveCount LIVE",
                    style      = NSType.monoSmall(),
                    color      = Color.White,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                )
            }
        }
        Spacer(modifier = Modifier.weight(1f))
    }
}

// ── Section header (grid span item) ──────────────────────────────────────────

@Composable
private fun MatchSectionHeader(title: String, count: Int, isLive: Boolean) {
    val dimens = NSDimens.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.spacing.sm),
        modifier = Modifier.padding(bottom = dimens.spacing.sm),
    ) {
        if (isLive) NSPulseDot()
        NSGroupHeader(title = title, count = count)
    }
}

// ── Match card ────────────────────────────────────────────────────────────────

@Composable
fun MatchCard(match: MatchItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val dimens     = NSDimens.current
    val isLive     = match.programme.isNow
    val background = matchCardBackground(match.variant)
    val borderColor = matchCardBorderColor(match.variant)

    Column(
        verticalArrangement = Arrangement.spacedBy(dimens.spacing.md),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(dimens.radius.lg))
            .background(background)
            .border(0.5.dp, borderColor, RoundedCornerShape(dimens.radius.lg))
            .clickable(onClick = onClick)
            .padding(dimens.spacing.md),
    ) {
        // Competition row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.spacing.sm),
        ) {
            if (isLive) NSPulseDot()
            Text(
                text     = match.competition.ifEmpty { "MATCH" }.uppercase(),
                style    = NSType.monoSmall(),
                color    = NSColors.text3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // Teams + score
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text     = match.homeTeam,
                style    = NSType.cardTitle(),
                color    = NSColors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            ScoreBox(isLive = isLive, title = match.programme.title)
            Text(
                text      = match.awayTeam,
                style     = NSType.cardTitle(),
                color     = NSColors.text,
                maxLines  = 1,
                overflow  = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
                modifier  = Modifier.weight(1f),
            )
        }

        // Progress (live only)
        if (isLive) {
            NSProgressBar(value = match.programme.progress.toFloat())
        }

        // Footer — time + channel id
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text  = matchTimeLabel(match.programme),
                style = NSType.monoSmall(),
                color = if (isLive) NSColors.accent2 else NSColors.text3,
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text     = match.channelTvgId,
                style    = NSType.monoSmall(),
                color    = NSColors.text3,
                modifier = Modifier
                    .clip(RoundedCornerShape(dimens.radius.sm))
                    .background(NSColors.bg)
                    .border(0.5.dp, NSColors.border, RoundedCornerShape(dimens.radius.sm))
                    .padding(horizontal = dimens.spacing.sm, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun ScoreBox(isLive: Boolean, title: String) {
    val dimens    = NSDimens.current
    val scoreText = if (isLive) SCORE_REGEX.find(title)?.let {
        "${it.groupValues[1]} – ${it.groupValues[2]}"
    } ?: "0 – 0" else "vs"

    Text(
        text      = scoreText,
        style     = if (isLive) NSType.display() else NSType.caption(),
        color     = if (isLive) NSColors.accent2 else NSColors.text3,
        textAlign = TextAlign.Center,
        modifier  = Modifier
            .padding(horizontal = dimens.spacing.sm)
            .clip(RoundedCornerShape(dimens.radius.md))
            .background(NSColors.bg)
            .border(
                0.5.dp,
                if (isLive) NSColors.accentBorder else NSColors.border2,
                RoundedCornerShape(dimens.radius.md),
            )
            .padding(horizontal = dimens.spacing.sm, vertical = dimens.spacing.xs),
    )
}

// ── Empty view ────────────────────────────────────────────────────────────────

@Composable
private fun MatchDayEmptyView(sport: SportCategory) {
    val dimens = NSDimens.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize().padding(top = 80.dp),
    ) {
        Text(text = "📅", fontSize = 40.sp)
        Spacer(modifier = Modifier.height(dimens.spacing.md))
        Text(text = "No ${sport.label} on right now", style = NSType.display(), color = NSColors.text)
        Spacer(modifier = Modifier.height(dimens.spacing.sm))
        Text(text = "Check back closer to kick-off time.", style = NSType.caption(), color = NSColors.text3)
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun matchCardBackground(variant: MatchCardVariant): Brush = when (variant) {
    MatchCardVariant.LIVE     -> NSGradients.liveCard
    MatchCardVariant.FEATURED -> NSGradients.activeCard
    MatchCardVariant.UCL      -> NSGradients.uclCard
    MatchCardVariant.PLAIN    -> SolidColor(NSColors.surface2)
}

private fun matchCardBorderColor(variant: MatchCardVariant): Color = when (variant) {
    MatchCardVariant.LIVE     -> NSColors.live.copy(alpha = 0.19f)
    MatchCardVariant.FEATURED -> NSColors.accentBorder
    MatchCardVariant.UCL      -> Color(0xFF3B82F6).copy(alpha = 0.25f)
    MatchCardVariant.PLAIN    -> NSColors.border
}

private fun matchTimeLabel(programme: Programme): String {
    if (programme.isNow) {
        val elapsedMinutes = ((System.currentTimeMillis() - programme.startEpochMs) / 60_000L).toInt()
        return "$elapsedMinutes' · Live"
    }
    return timeFormatter.format(Date(programme.startEpochMs))
}

private fun buildMatchItems(
    channels: List<Channel>,
    sport: SportCategory,
    epgViewModel: EpgViewModel,
): List<MatchItem> {
    val items = mutableListOf<MatchItem>()
    for (channel in channels) {
        val schedule = epgViewModel.schedule(channel, hours = SCHEDULE_HOURS)
        for (programme in schedule) {
            val titleLower = programme.title.lowercase()
            val groupLower = channel.groupTitle.lowercase()
            val matchesSport = sport.epgKeywords.any {
                titleLower.contains(it) || groupLower.contains(it)
            }
            if (!matchesSport) continue
            val item = parseMatchItem(programme, channel.tvgId) ?: continue
            items.add(item)
        }
    }
    return items.sortedBy { it.programme.startEpochMs }
}

private fun parseMatchItem(programme: Programme, channelTvgId: String): MatchItem? {
    if (!programme.title.contains(VS_SEPARATOR, ignoreCase = true)) return null
    val parts = programme.title.split(VS_SEPARATOR, ignoreCase = true)
    if (parts.size < 2) return null

    val homeTeam        = parts[0].trim()
    val awayAndComp     = parts[1].split(COMPETITION_SEPARATOR)
    val awayTeam        = awayAndComp[0].trim()
    val competition     = if (awayAndComp.size > 1) awayAndComp[1].trim() else ""
    val competitionLower = competition.lowercase()

    val variant = when {
        programme.isNow                                                       -> MatchCardVariant.LIVE
        competitionLower.contains("champions") || competitionLower.contains("ucl")   -> MatchCardVariant.UCL
        competitionLower.contains("premier") || competitionLower.contains("liga") ||
                competitionLower.contains("bundesliga")                           -> MatchCardVariant.FEATURED
        else                                                                  -> MatchCardVariant.PLAIN
    }

    return MatchItem(
        programme    = programme,
        channelTvgId = channelTvgId,
        competition  = competition,
        homeTeam     = homeTeam,
        awayTeam     = awayTeam,
        variant      = variant,
    )
}