// app/src/main/java/com/nativestream/android/ui/screens/now/NowScreen.kt
//
// Now Screen
// Section headers match design: pulse dot for matches, TV icon for on air, clock for soon.
// "Show all / Show less" toggle for Live on Air.
// AND-TABLET-004: two-column layout on Expanded width.

package com.nativestream.android.ui.screens.now

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.regular.Television
import com.adamglin.phosphoricons.regular.Clock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.adamglin.phosphoricons.Regular
import com.nativestream.android.ui.LocalWindowSizeClass
import com.nativestream.android.ui.components.NSGroupHeader
import com.nativestream.android.ui.components.NSPulseDot
import com.nativestream.android.ui.foldable.rememberFoldPosture
import com.nativestream.android.ui.theme.NSColors
import com.nativestream.android.ui.theme.NSDimens
import com.nativestream.android.ui.theme.NSType
import com.nativestream.android.ui.viewmodel.EpgViewModel
import com.nativestream.android.ui.viewmodel.PlaylistViewModel
import com.nativestream.android.ui.viewmodel.PlayerViewModel

private const val LIVE_ON_AIR_INITIAL_VISIBLE = 10
private val SECTION_ICON_SIZE = 13.dp

@Composable
fun NowScreen(
    playerViewModel: PlayerViewModel,
    modifier: Modifier = Modifier,
    playlistViewModel: PlaylistViewModel = hiltViewModel(),
    epgViewModel: EpgViewModel           = hiltViewModel(),
) {
    // Staggered initialization: Trigger EPG loading exactly one frame after structural bar rendering
    LaunchedEffect(Unit) {
        epgViewModel.load()
    }
    val channels by playlistViewModel.filteredChannels.collectAsState()
    val isLoading by playlistViewModel.isLoading.collectAsState()
    val epgReady by epgViewModel.isReady.collectAsState()

    val liveMatches  = remember(channels,epgReady) {
        NowBuckets.liveMatches(channels) { epgViewModel.currentProgramme(it) }
    }
    val liveOnAir = remember(channels, epgReady) {
        NowBuckets.liveOnAir(channels) { epgViewModel.currentProgramme(it) }
    }
    val startingSoon = remember(channels,epgReady) {
        NowBuckets.startingSoon(
            channels            = channels,
            currentProgrammeFor = { epgViewModel.currentProgramme(it) },
            nextProgrammeFor    = { epgViewModel.nextProgramme(it) },
        )
    }

    val liveCount = liveMatches.size + liveOnAir.size
    val soonCount = startingSoon.size

    Column(modifier = modifier.fillMaxSize().background(NSColors.bg)) {
        NowTopBar(liveCount = liveCount, soonCount = soonCount)
        Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(NSColors.border))

        when {
            isLoading                        -> LoadingView()
            liveCount == 0 && soonCount == 0 -> EmptyView()
            else                             -> NowContent(
                liveMatches  = liveMatches,
                liveOnAir    = liveOnAir,
                startingSoon = startingSoon,
                onSelect     = { playerViewModel.play(it) },
            )
        }
    }
}

// ── Top bar ───────────────────────────────────────────────────────────────────

@Composable
private fun NowTopBar(liveCount: Int, soonCount: Int) {
    val dimens = NSDimens.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(NSColors.surface)
            .windowInsetsPadding(WindowInsets.displayCutout)
            .padding(horizontal = dimens.spacing.lg, vertical = dimens.spacing.md),
    ) {
        Text(text = "What's on", style = NSType.heading(), color = NSColors.text)
        Spacer(modifier = Modifier.weight(1f))
        // Pill chip — "11 live · 5 soon"
        Text(
            text  = "$liveCount live · $soonCount soon",
            style = NSType.caption(),
            color = NSColors.accent2,
            modifier = Modifier
                .background(NSColors.accentGlow, androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                .padding(horizontal = 10.dp, vertical = 3.dp),
        )
    }
}

// ── Scrollable content ────────────────────────────────────────────────────────

@Composable
private fun NowContent(
    liveMatches: List<ChannelWithProgramme>,
    liveOnAir: List<ChannelWithProgramme>,
    startingSoon: List<ChannelWithProgramme>,
    onSelect: (com.nativestream.android.domain.model.Channel) -> Unit,
) {
    val windowSizeClass = LocalWindowSizeClass.current
    val useColumns = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Expanded

    if (useColumns) {
        NowContentTwoColumn(liveMatches, liveOnAir, startingSoon, onSelect)
    } else {
        NowContentSingleColumn(liveMatches, liveOnAir, startingSoon, onSelect)
    }
}

@Composable
private fun NowContentSingleColumn(
    liveMatches: List<ChannelWithProgramme>,
    liveOnAir: List<ChannelWithProgramme>,
    startingSoon: List<ChannelWithProgramme>,
    onSelect: (com.nativestream.android.domain.model.Channel) -> Unit,
) {
    val dimens = NSDimens.current

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(dimens.spacing.xxl),
        modifier = Modifier
            .fillMaxSize()
            .padding(dimens.spacing.lg),
    ) {
        // ── Matches live ──────────────────────────────────────────────────────
        if (liveMatches.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(dimens.spacing.md)) {
                    // Pulse dot + header — matches design
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(dimens.spacing.sm),
                    ) {
                        NSPulseDot()
                        NSGroupHeader(title = "Matches live", count = liveMatches.size)
                    }
                    MatchHeroCard(
                        channel   = liveMatches.first().channel,
                        programme = liveMatches.first().programme,
                        onClick   = { onSelect(liveMatches.first().channel) },
                    )
                    if (liveMatches.size > 1) {
                        MatchSmallGrid(items = liveMatches.drop(1), onSelectChannel = onSelect)
                    }
                }
            }
        }

        // ── Live on air ───────────────────────────────────────────────────────
        if (liveOnAir.isNotEmpty()) {
            item { LiveOnAirSection(liveOnAir = liveOnAir, onSelect = onSelect) }
        }

        // ── Starting soon ─────────────────────────────────────────────────────
        if (startingSoon.isNotEmpty()) {
            item { StartingSoonSection(startingSoon = startingSoon, onSelect = onSelect) }
        }

        item { Spacer(modifier = Modifier.height(80.dp)) }
    }
}

@Composable
private fun NowContentTwoColumn(
    liveMatches: List<ChannelWithProgramme>,
    liveOnAir: List<ChannelWithProgramme>,
    startingSoon: List<ChannelWithProgramme>,
    onSelect: (com.nativestream.android.domain.model.Channel) -> Unit,
) {
    val dimens = NSDimens.current
    val foldPosture = rememberFoldPosture()
    val columnSpacing = if (foldPosture.isBook && foldPosture.hingeBounds != null) {
        with(LocalDensity.current) { foldPosture.hingeBounds.width.toDp() }
    } else {
        dimens.spacing.lg
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(dimens.spacing.lg),
        horizontalArrangement = Arrangement.spacedBy(columnSpacing),
    ) {
        // Left — Matches live (only shown if content exists)
        if (liveMatches.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(dimens.spacing.md),
            ) {
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(dimens.spacing.sm),
                    ) {
                        NSPulseDot()
                        NSGroupHeader(title = "Matches live", count = liveMatches.size)
                    }
                }
                item {
                    MatchHeroCard(
                        channel   = liveMatches.first().channel,
                        programme = liveMatches.first().programme,
                        onClick   = { onSelect(liveMatches.first().channel) },
                    )
                }
                if (liveMatches.size > 1) {
                    item { MatchSmallGrid(items = liveMatches.drop(1), onSelectChannel = onSelect) }
                }
                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }

        // Right — Live on air + Starting soon (full width if no matches)
        LazyColumn(
            modifier = if (liveMatches.isEmpty()) Modifier.fillMaxSize()
            else Modifier.weight(1f).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(dimens.spacing.xxl),
        ) {
            if (liveOnAir.isNotEmpty()) {
                item { LiveOnAirSection(liveOnAir = liveOnAir, onSelect = onSelect) }
            }
            if (startingSoon.isNotEmpty()) {
                item { StartingSoonSection(startingSoon = startingSoon, onSelect = onSelect) }
            }
            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }
}

// ── Extracted section composables ─────────────────────────────────────────────

@Composable
private fun LiveOnAirSection(
    liveOnAir: List<ChannelWithProgramme>,
    onSelect: (com.nativestream.android.domain.model.Channel) -> Unit,
) {
    val dimens = NSDimens.current
    var showAllOnAir by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(dimens.spacing.md)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.spacing.sm),
        ) {
            Icon(
                imageVector        = PhosphorIcons.Regular.Television,
                contentDescription = null,
                tint               = NSColors.text3,
                modifier           = Modifier.size(SECTION_ICON_SIZE),
            )
            NSGroupHeader(title = "Live on air", count = liveOnAir.size)
        }

        val visible = if (showAllOnAir) liveOnAir
        else liveOnAir.take(LIVE_ON_AIR_INITIAL_VISIBLE)

        Column(verticalArrangement = Arrangement.spacedBy(dimens.spacing.sm)) {
            visible.forEach { item ->
                LiveOnAirRow(
                    channel   = item.channel,
                    programme = item.programme,
                    onClick   = { onSelect(item.channel) },
                )
            }
        }

        if (liveOnAir.size > LIVE_ON_AIR_INITIAL_VISIBLE) {
            val label = if (showAllOnAir) "Show less"
            else "Show all ${liveOnAir.size}"
            Text(
                text     = label,
                style    = NSType.captionMedium(),
                color    = NSColors.accent2,
                modifier = Modifier
                    .clickable { showAllOnAir = !showAllOnAir }
                    .padding(vertical = dimens.spacing.xs),
            )
        }
    }
}

@Composable
private fun StartingSoonSection(
    startingSoon: List<ChannelWithProgramme>,
    onSelect: (com.nativestream.android.domain.model.Channel) -> Unit,
) {
    val dimens = NSDimens.current

    Column(verticalArrangement = Arrangement.spacedBy(dimens.spacing.md)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.spacing.sm),
        ) {
            Icon(
                imageVector        = PhosphorIcons.Regular.Clock,
                contentDescription = null,
                tint               = NSColors.text3,
                modifier           = Modifier.size(SECTION_ICON_SIZE),
            )
            NSGroupHeader(title = "Starting soon", count = startingSoon.size)
        }
        StartingSoonGrid(items = startingSoon, onSelectChannel = onSelect)
    }
}

// ── States ────────────────────────────────────────────────────────────────────

@Composable
private fun LoadingView() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize(),
    ) {
        CircularProgressIndicator(color = NSColors.accent, strokeWidth = 2.dp)
        Spacer(modifier = Modifier.height(NSDimens.current.spacing.md))
        Text(text = "Loading…", style = NSType.caption(), color = NSColors.text3)
    }
}

@Composable
private fun EmptyView() {
    val dimens = NSDimens.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize().padding(dimens.spacing.xl),
    ) {
        Text(text = "📺", fontSize = 36.sp)
        Spacer(modifier = Modifier.height(dimens.spacing.md))
        Text(text = "Nothing on right now", style = NSType.display(), color = NSColors.text)
        Spacer(modifier = Modifier.height(dimens.spacing.sm))
        Text(
            text  = "Add a playlist source in Settings or check back later.",
            style = NSType.caption(),
            color = NSColors.text3,
        )
    }
}