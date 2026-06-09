// app/src/main/java/com/nativestream/android/ui/screens/player/PlayerSidebar.kt
//
// Player Sidebar
// 240dp collapsible sidebar with two tabs: On Now and Schedule.
// Channel switch via ExoPlayer.setMediaItem() through PlayerViewModel.play().

package com.nativestream.android.ui.screens.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.nativestream.android.domain.model.Channel
import com.nativestream.android.domain.model.Programme
import com.nativestream.android.ui.components.NSProgressBar
import com.nativestream.android.ui.theme.NSColors
import com.nativestream.android.ui.theme.NSDimens
import com.nativestream.android.ui.theme.NSType
import com.nativestream.android.ui.viewmodel.EpgViewModel
import com.nativestream.android.ui.viewmodel.PlayerViewModel

private val TAB_INDICATOR_H    = 1.5.dp
private val SCHEDULE_HOURS     = 12

enum class SidebarTab { ON_NOW, SCHEDULE }

@Composable
fun PlayerSidebarContent(
    playerViewModel: PlayerViewModel,
    epgViewModel: EpgViewModel,
    modifier: Modifier = Modifier,
) {
    val activeChannel by playerViewModel.activeChannel.collectAsState()
    var selectedTab   by remember { mutableStateOf(SidebarTab.ON_NOW) }

    Box(
        modifier = modifier
            .width(NSDimens.current.player.sidebarWidth)  // 230dp token
            .fillMaxHeight()
            .background(Color(0xFF0E0E0E))
            .border(
                width = 0.5.dp,
                color = Color.White.copy(alpha = 0.07f),
                shape = RoundedCornerShape(0.dp),
            ),
    ) {
        Column {
            SidebarTabBar(selectedTab = selectedTab, onSelectTab = { selectedTab = it })
            when (selectedTab) {
                SidebarTab.ON_NOW   -> OnNowTab(
                    currentChannel  = activeChannel,
                    playerViewModel = playerViewModel,
                    epgViewModel    = epgViewModel,
                )
                SidebarTab.SCHEDULE -> ScheduleTab(
                    channel      = activeChannel,
                    epgViewModel = epgViewModel,
                )
            }
        }
    }
}

@Composable
fun PlayerSidebar(
    isVisible: Boolean,
    playerViewModel: PlayerViewModel,
    epgViewModel: EpgViewModel,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible  = isVisible,
        enter    = slideInHorizontally { it },
        exit     = slideOutHorizontally { it },
        modifier = modifier,
    ) {
        PlayerSidebarContent(
            playerViewModel = playerViewModel,
            epgViewModel    = epgViewModel,
        )
    }
}

// ── Tab bar ───────────────────────────────────────────────────────────────────

@Composable
private fun SidebarTabBar(selectedTab: SidebarTab, onSelectTab: (SidebarTab) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.6f)),
    ) {
        Row {
            SidebarTab.entries.forEach { tab ->
                val isActive = selectedTab == tab
                val label    = if (tab == SidebarTab.ON_NOW) "On now" else "Schedule"
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onSelectTab(tab) }
                        .padding(vertical = NSDimens.current.spacing.sm),
                ) {
                    Text(
                        text  = label,
                        style = NSType.captionMedium(),
                        color = Color.White.copy(alpha = if (isActive) 0.85f else 0.35f),
                    )
                    if (isActive) {
                        Spacer(modifier = Modifier.height(NSDimens.current.spacing.xs))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(TAB_INDICATOR_H)
                                .background(NSColors.accent),
                        )
                    }
                }
            }
        }
        // Bottom border
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(Color.White.copy(alpha = 0.07f))
                .align(Alignment.BottomCenter),
        )
    }
}

// ── On Now tab ────────────────────────────────────────────────────────────────

@Composable
private fun OnNowTab(
    currentChannel: Channel?,
    playerViewModel: PlayerViewModel,
    epgViewModel: EpgViewModel,
) {
    val allChannels by playerViewModel.channelList.collectAsState()

    val filteredChannels = remember(allChannels) {
        val withEpg = allChannels.filter {
            epgViewModel.currentProgramme(it) != null || epgViewModel.nextProgramme(it) != null
        }
        withEpg.ifEmpty { allChannels }
    }

    val sortedChannels = remember(filteredChannels) {
        filteredChannels.sortedWith(compareBy(
            { epgViewModel.currentProgramme(it) == null },
            { epgViewModel.nextProgramme(it) == null },
        ))
    }
    val listState = rememberLazyListState()
    val activeIndex = remember(sortedChannels, currentChannel) {
        sortedChannels.indexOfFirst { it.id == currentChannel?.id }
    }

    LaunchedEffect(activeIndex) {
        if (activeIndex >= 0) {
            val visibleItems = listState.layoutInfo.visibleItemsInfo
            val isVisible = visibleItems.any { it.index == activeIndex }
            if (!isVisible) listState.scrollToItem(activeIndex)
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxHeight()
            .padding(NSDimens.current.spacing.sm)
    ) {
        items(sortedChannels, key = { it.id }) { channel ->
            PlayerSidebarRow(
                channel         = channel,
                playerViewModel = playerViewModel,
                epgViewModel    = epgViewModel,
            )
        }
    }
}

// ── Schedule tab ──────────────────────────────────────────────────────────────

@Composable
private fun ScheduleTab(channel: Channel?, epgViewModel: EpgViewModel) {
    val dimens = NSDimens.current

    val programmes: List<Programme> = remember(channel) {
        channel?.let {
            epgViewModel.schedule(it, hours = SCHEDULE_HOURS)
                .sortedBy { prog -> prog.startEpochMs }
        } ?: emptyList()
    }

    LazyColumn(modifier = Modifier.fillMaxHeight().padding(dimens.spacing.sm)) {
        items(programmes, key = { it.id }) { prog ->
            ScheduleRow(programme = prog)
        }
    }
}

@Composable
private fun ScheduleRow(programme: Programme) {
    val dimens  = NSDimens.current
    val isPast  = programme.stopEpochMs < System.currentTimeMillis()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(dimens.radius.md))
            .background(if (programme.isNow) NSColors.accentGlow else Color.Transparent)
            .border(
                0.5.dp,
                if (programme.isNow) NSColors.accentBorder else Color.Transparent,
                RoundedCornerShape(dimens.radius.md),
            )
            .padding(dimens.spacing.sm)
            .then(if (isPast) Modifier.then(Modifier.padding(0.dp)) else Modifier),
        ) {
        Text(
            text  = programme.startTimeString,
            style = NSType.monoSmall(),
            color = if (programme.isNow) NSColors.accent else Color.White.copy(alpha = 0.3f),
        )
        if (programme.isNow) {
            Text(
                text  = "Now playing",
                style = NSType.monoSmall(),
                color = NSColors.accent,
            )
        }
        Text(
            text     = programme.title,
            style    = NSType.captionMedium(),
            color    = Color.White.copy(alpha = if (programme.isNow) 0.85f else 0.4f),
            maxLines = 2,
        )
        if (programme.isNow) {
            NSProgressBar(
                value    = programme.progress.toFloat(),
                modifier = Modifier.padding(top = dimens.spacing.xs),
            )
        }
    }
}