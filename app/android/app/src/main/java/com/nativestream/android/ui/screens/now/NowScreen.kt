// app/src/main/java/com/nativestream/android/ui/screens/now/NowScreen.kt
//
// NS-010: Now Screen (UX-009)
// EPG-first home screen. Three sections — Matches live, Live on air, Starting soon.
// Sections hidden when empty. Bucketing mirrors NowScreen.swift exactly.

package com.nativestream.android.ui.screens.now

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.clickable
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nativestream.android.ui.components.NSGroupHeader
import com.nativestream.android.ui.components.NSPulseDot
import com.nativestream.android.ui.theme.NSColors
import com.nativestream.android.ui.theme.NSDimens
import com.nativestream.android.ui.theme.NSType
import com.nativestream.android.ui.viewmodel.EpgViewModel
import com.nativestream.android.ui.viewmodel.PlaylistViewModel
import com.nativestream.android.ui.viewmodel.PlayerViewModel

private const val LIVE_ON_AIR_INITIAL_VISIBLE = 10

@Composable
fun NowScreen(
    playerViewModel: PlayerViewModel,
    modifier: Modifier = Modifier,
    playlistViewModel: PlaylistViewModel = hiltViewModel(),
    epgViewModel: EpgViewModel           = hiltViewModel(),
) {
    val channels  by playlistViewModel.channels.collectAsState()
    val isLoading by playlistViewModel.isLoading.collectAsState()

    val liveMatches  = remember(channels) {
        NowBuckets.liveMatches(channels) { epgViewModel.currentProgramme(it) }
    }
    val liveOnAir    = remember(channels) {
        NowBuckets.liveOnAir(channels) { epgViewModel.currentProgramme(it) }
    }
    val startingSoon = remember(channels) {
        NowBuckets.startingSoon(
            channels           = channels,
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
            isLoading                              -> LoadingView()
            liveCount == 0 && soonCount == 0       -> EmptyView()
            else                                   -> NowScrollContent(
                liveMatches    = liveMatches,
                liveOnAir      = liveOnAir,
                startingSoon   = startingSoon,
                onSelectChannel = { playerViewModel.play(it) },
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
            .padding(horizontal = dimens.spacing.xl, vertical = dimens.spacing.md),
    ) {
        Text(text = "What's on", style = NSType.heading(), color = NSColors.text)
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text  = "$liveCount live · $soonCount soon",
            style = NSType.caption(),
            color = NSColors.text3,
        )
    }
}

// ── Scroll content ────────────────────────────────────────────────────────────

@Composable
private fun NowScrollContent(
    liveMatches: List<ChannelWithProgramme>,
    liveOnAir: List<ChannelWithProgramme>,
    startingSoon: List<ChannelWithProgramme>,
    onSelectChannel: (com.nativestream.android.domain.model.Channel) -> Unit,
) {
    val dimens = NSDimens.current
    var showAllOnAir by remember { mutableStateOf(false) }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(dimens.spacing.xxl),
        modifier = Modifier
            .fillMaxSize()
            .padding(dimens.spacing.xl),
    ) {
        // ── Matches live ──────────────────────────────────────────────────────
        if (liveMatches.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(dimens.spacing.md)) {
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
                        onClick   = { onSelectChannel(liveMatches.first().channel) },
                    )
                    if (liveMatches.size > 1) {
                        MatchSmallGrid(
                            items           = liveMatches.drop(1),
                            onSelectChannel = onSelectChannel,
                        )
                    }
                }
            }
        }

        // ── Live on air ───────────────────────────────────────────────────────
        if (liveOnAir.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(dimens.spacing.md)) {
                    NSGroupHeader(title = "Live on air", count = liveOnAir.size)

                    val visibleItems = if (showAllOnAir) liveOnAir
                                       else liveOnAir.take(LIVE_ON_AIR_INITIAL_VISIBLE)

                    Column(verticalArrangement = Arrangement.spacedBy(dimens.spacing.sm)) {
                        visibleItems.forEach { item ->
                            LiveOnAirRow(
                                channel   = item.channel,
                                programme = item.programme,
                                onClick   = { onSelectChannel(item.channel) },
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
                                .padding(top = dimens.spacing.xs)
                                .clickable { showAllOnAir = !showAllOnAir }

                        )
                    }
                }
            }
        }

        // ── Starting soon ─────────────────────────────────────────────────────
        if (startingSoon.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(dimens.spacing.md)) {
                    NSGroupHeader(title = "Starting soon", count = startingSoon.size)
                    StartingSoonGrid(
                        items           = startingSoon,
                        onSelectChannel = onSelectChannel,
                    )
                }
            }
        }

        // Bottom padding for mini player
        item { Spacer(modifier = Modifier.height(80.dp)) }
    }
}

// ── Loading / empty states ────────────────────────────────────────────────────

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