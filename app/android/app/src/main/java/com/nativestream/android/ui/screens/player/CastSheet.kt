// app/src/main/java/com/nativestream/android/ui/screens/player/CastSheet.kt
//
// Local Media Connect cast sheet — shows connected target devices,
// allows sending play/stop commands and initiating pull-back.
// Triggered from player controls or Now screen top bar.

package com.nativestream.android.ui.screens.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.regular.MonitorPlay
import com.adamglin.phosphoricons.regular.CircleNotch
import com.nativestream.android.domain.model.Channel
import com.nativestream.android.domain.model.control.SessionInfo
import com.nativestream.android.ui.components.SheetActionButton
import com.nativestream.android.ui.screens.settings.NSHealthDot
import com.nativestream.android.ui.theme.NSColors
import com.nativestream.android.ui.theme.NSDimens
import com.nativestream.android.ui.theme.NSType
import com.nativestream.android.ui.viewmodel.ControlViewModel
import com.nativestream.android.ui.viewmodel.PullBackState

private val DEVICE_ROW_HEIGHT    = 72.dp
private val DEVICE_ICON_SIZE     = 20.dp
private val SHEET_CORNER_RADIUS  = 16.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CastSheet(
    controlViewModel: ControlViewModel,
    currentChannel: Channel?,
    onDismiss: () -> Unit,
    onPullBackReady: (channelId: String, channelName: String, streamUrl: String) -> Unit,
    onStopLocalPlayback: () -> Unit,
) {
    val dimens        = NSDimens.current
    val sheetState    = rememberModalBottomSheetState()
    val sessions      by controlViewModel.sessions.collectAsState()
    val connected     by controlViewModel.connected.collectAsState()
    val pullBackState by controlViewModel.pullBackState.collectAsState()
    val scanning      by controlViewModel.discoveryScanning.collectAsState()

    // Pull-back ready — notify caller and dismiss
    LaunchedEffect(pullBackState) {
        if (pullBackState is PullBackState.Ready) {
            val ready = pullBackState as PullBackState.Ready
            onPullBackReady(ready.channelId, ready.channelName, ready.streamUrl)
            controlViewModel.resetPullBackState()
            onDismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest  = onDismiss,
        sheetState        = sheetState,
        containerColor    = NSColors.surface,
        shape             = RoundedCornerShape(
            topStart = SHEET_CORNER_RADIUS,
            topEnd   = SHEET_CORNER_RADIUS,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(dimens.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(dimens.spacing.md),
        ) {
            // ── Header ────────────────────────────────────────────────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dimens.spacing.sm),
            ) {
                Icon(
                    imageVector        = PhosphorIcons.Regular.MonitorPlay,
                    contentDescription = null,
                    tint               = NSColors.accent,
                    modifier           = Modifier.size(DEVICE_ICON_SIZE),
                )
                Text(
                    text  = "Cast to a device",
                    style = NSType.heading(),
                    color = NSColors.text,
                )
            }

            Spacer(modifier = Modifier.height(dimens.spacing.xs))

            // ── Connection status ─────────────────────────────────────────────
            if (!connected) {
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(dimens.spacing.sm),
                ) {
                    Icon(
                        imageVector        = PhosphorIcons.Regular.CircleNotch,
                        contentDescription = null,
                        tint               = NSColors.text3,
                        modifier           = Modifier.size(14.dp),
                    )
                    Text(
                        text  = "Connecting to server…",
                        style = NSType.body(),
                        color = NSColors.text3,
                    )
                }
                return@Column
            }

            // ── No targets ────────────────────────────────────────────────────
            if (sessions.isEmpty()) {
                Text(
                    text  = if (scanning) "Scanning your network…"
                    else "No devices found on your network.",
                    style = NSType.body(),
                    color = NSColors.text3,
                )
                if (!scanning) {
                    SheetActionButton(
                        label     = "Scan again",
                        isPrimary = false,
                        enabled   = true,
                        onClick   = { controlViewModel.startDiscovery() },
                        modifier  = Modifier.fillMaxWidth(),
                    )
                }
                return@Column
            }

            // ── Device list ───────────────────────────────────────────────────
            sessions.forEach { session ->
                DeviceRow(
                    session        = session,
                    currentChannel = currentChannel,
                    pullBackState  = pullBackState,
                    onPlay         = {
                        val channel = currentChannel ?: return@DeviceRow
                        controlViewModel.play(session.deviceId, channel.id, channel.name, channel.streamUrl)
                        onStopLocalPlayback()
                        onDismiss()
                    },
                    onStop         = {
                        controlViewModel.stop(session.deviceId)
                        onDismiss()
                    },
                    onPullBack     = {
                        controlViewModel.pullBack(session.deviceId)
                    },
                )
            }

            Spacer(modifier = Modifier.height(dimens.spacing.md))
        }
    }
}

@Composable
private fun DeviceRow(
    session: SessionInfo,
    currentChannel: Channel?,
    pullBackState: PullBackState,
    onPlay: () -> Unit,
    onStop: () -> Unit,
    onPullBack: () -> Unit,
) {
    val dimens         = NSDimens.current
    val isPlaying      = session.playing
    val isPullingBack  = pullBackState is PullBackState.Requesting

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(DEVICE_ROW_HEIGHT)
            .background(NSColors.surface2, RoundedCornerShape(dimens.radius.lg))
            .padding(horizontal = dimens.spacing.md),
    ) {
        NSHealthDot(score = if (isPlaying) 1.0 else 0.3)
        Spacer(modifier = Modifier.width(dimens.spacing.sm))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text  = session.name,
                style = NSType.bodyMedium(),
                color = NSColors.text,
            )
            Text(
                text  = if (isPlaying) "Playing: ${session.channelId}"
                else "Idle",
                style = NSType.monoSmall(),
                color = if (isPlaying) NSColors.accent else NSColors.text3,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(dimens.spacing.sm)) {
            if (isPlaying) {
                SheetActionButton(
                    label     = if (isPullingBack) "Pulling…" else "Pull Back",
                    isPrimary = false,
                    enabled   = !isPullingBack,
                    onClick   = onPullBack,
                )
                SheetActionButton(
                    label     = "Stop",
                    isPrimary = false,
                    enabled   = true,
                    onClick   = onStop,
                )
            } else {
                SheetActionButton(
                    label     = if (currentChannel != null) "Play here" else "—",
                    isPrimary = true,
                    enabled   = currentChannel != null,
                    onClick   = onPlay,
                )
            }
        }
    }
}