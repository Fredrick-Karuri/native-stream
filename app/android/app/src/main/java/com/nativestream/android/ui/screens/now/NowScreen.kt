/**
 * app/src/main/java/com/nativestream/android/ui/screens/now/NowScreen.kt
 *
 * Now Screen — wired to the post-SRP ViewModel split.
 * Channel bridge  → NowViewModel (observes ChannelRepository → EpgViewModel)
 * EPG bucket data → EpgViewModel (liveMatches, liveOnAir, startingSoon, isRefreshing)
 *
 */

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
import com.adamglin.phosphoricons.regular.MonitorPlay
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowHeightSizeClass
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
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.regular.Clock
import com.adamglin.phosphoricons.regular.Television
import com.nativestream.android.ui.LocalWindowSizeClass
import com.nativestream.android.ui.components.IconButton
import com.nativestream.android.ui.components.NSGroupHeader
import com.nativestream.android.ui.components.NSPulseDot
import com.nativestream.android.ui.foldable.rememberFoldPosture
import com.nativestream.android.ui.screens.player.CastSheet
import com.nativestream.android.ui.theme.NSColors
import com.nativestream.android.ui.theme.NSDimens
import com.nativestream.android.ui.theme.NSType
import com.nativestream.android.ui.viewmodel.ControlViewModel
import com.nativestream.android.ui.viewmodel.EpgViewModel
import com.nativestream.android.ui.viewmodel.PlayerViewModel
import kotlinx.coroutines.delay

private const val LIVE_ON_AIR_INITIAL_VISIBLE = 10
private val SECTION_ICON_SIZE = 13.dp

@Composable
fun NowScreen(
    playerViewModel: PlayerViewModel,
    epgViewModel: EpgViewModel,
    controlViewModel: ControlViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {

    val liveMatches  by epgViewModel.liveMatches.collectAsState()
    val liveOnAir    by epgViewModel.liveOnAir.collectAsState()
    val startingSoon by epgViewModel.startingSoon.collectAsState()
    val isRefreshing by epgViewModel.isRefreshing.collectAsState()

    val liveCount       = liveMatches.size + liveOnAir.size
    val soonCount       = startingSoon.size
    val currentChannel  by playerViewModel.currentChannel.collectAsState()
    var showCastSheet   by remember { mutableStateOf(false) }
    val sessions by controlViewModel.sessions.collectAsState()

    Column(modifier = modifier.fillMaxSize().background(NSColors.bg)) {
        NowTopBar(
            liveCount    = liveCount,
            soonCount    = soonCount,
            isRefreshing = isRefreshing,
            hasConnectedDevice = sessions.isNotEmpty(),
            onCast       = { showCastSheet = true },
        )

        if (showCastSheet) {
            CastSheet(
                controlViewModel = controlViewModel,
                currentChannel   = currentChannel,
                onDismiss = {
                    showCastSheet = false
                },
                onPullBackReady  = { channelId, channelName, streamUrl ->
                    playerViewModel.playFromRemote(channelId, channelName, streamUrl)
                    showCastSheet = false
                },
                onStopLocalPlayback = { playerViewModel.stop() },
            )
        }
        Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(NSColors.border))

        var showEmpty by remember { mutableStateOf(false) }
        LaunchedEffect(liveCount, soonCount) {
            if (liveCount == 0 && soonCount == 0) {
                delay(800)
                showEmpty = true
            } else {
                showEmpty = false
            }
        }

        when {
            showEmpty -> EmptyView()
            liveCount > 0 || soonCount > 0 -> NowContent(
                liveMatches  = liveMatches,
                liveOnAir    = liveOnAir,
                startingSoon = startingSoon,
                onSelect     = { playerViewModel.play(it) },
            )
            else -> Unit
        }
    }
}

// ── Top bar ───────────────────────────────────────────────────────────────────

@Composable
private fun NowTopBar(
    liveCount: Int,
    soonCount: Int,
    isRefreshing: Boolean = false,
    hasConnectedDevice: Boolean = false,
    onCast: () -> Unit = {},
) {
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
        if (isRefreshing) {
            Spacer(modifier = Modifier.width(dimens.spacing.sm))
            CircularProgressIndicator(
                color       = NSColors.text3,
                strokeWidth = 1.5.dp,
                modifier    = Modifier.size(12.dp),
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        IconButton(
            icon               = PhosphorIcons.Regular.MonitorPlay,
            contentDescription = "Cast to device",
            onClick            = onCast,
            tint               = if (hasConnectedDevice) NSColors.accent else NSColors.text3,
            background         = if (hasConnectedDevice) NSColors.accentGlow else NSColors.surface2,
        )
        Spacer(modifier = Modifier.width(dimens.spacing.sm))
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
    val isTablet = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Expanded
            && windowSizeClass.heightSizeClass != WindowHeightSizeClass.Compact

    if (isTablet) {
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
        if (liveMatches.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(dimens.spacing.md)) {
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
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

        if (liveOnAir.isNotEmpty()) {
            item { LiveOnAirSection(liveOnAir = liveOnAir, onSelect = onSelect) }
        }

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
        if (liveMatches.isNotEmpty()) {
            LazyColumn(
                modifier            = Modifier.weight(1f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(dimens.spacing.md),
            ) {
                item {
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
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
            verticalAlignment     = Alignment.CenterVertically,
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

        val visible = if (showAllOnAir) liveOnAir else liveOnAir.take(LIVE_ON_AIR_INITIAL_VISIBLE)

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
            val label = if (showAllOnAir) "Show less" else "Show all ${liveOnAir.size}"
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
            verticalAlignment     = Alignment.CenterVertically,
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
private fun EmptyView() {
    val dimens = NSDimens.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier            = Modifier.fillMaxSize().padding(dimens.spacing.xl),
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