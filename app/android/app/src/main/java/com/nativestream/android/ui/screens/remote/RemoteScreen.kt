// app/src/main/java/com/nativestream/android/ui/screens/remote/RemoteScreen.kt
//
// Remote Screen — full control surface for an active remote session.
// Entered by tapping the ConnectBar. Owns: volume, stop, pull-back.
// Presented as a bottom sheet so it's easy to dismiss.

package com.nativestream.android.ui.screens.remote

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.regular.MonitorPlay
import com.adamglin.phosphoricons.regular.SpeakerHigh
import com.adamglin.phosphoricons.regular.SpeakerNone
import com.nativestream.android.ui.components.SheetActionButton
import com.nativestream.android.ui.theme.NSColors
import com.nativestream.android.ui.theme.NSDimens
import com.nativestream.android.ui.theme.NSType
import com.nativestream.android.ui.viewmodel.ControlViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteScreen(
    controlViewModel: ControlViewModel,
    onDismiss: () -> Unit,
    onPullBackReady: (channelId: String, channelName: String, streamUrl: String) -> Unit,
) {
    val dimens      = NSDimens.current
    val sheetState  = rememberModalBottomSheetState()
    val sessions    by controlViewModel.sessions.collectAsState()

    val isPullingBack by controlViewModel.isPullingBack.collectAsState()
    val session       = sessions.firstOrNull { it.playing } ?: return

    var volume by remember(session.deviceId) { mutableFloatStateOf(session.volume) }

    LaunchedEffect(session.volume) { volume = session.volume }

    LaunchedEffect(Unit) {
        controlViewModel.pullBackReady.collect { ready ->
            onPullBackReady(ready.channelId, ready.channelName, ready.streamUrl)
            onDismiss()
        }
    }


    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = sheetState,
        containerColor   = NSColors.surface,
        shape            = RoundedCornerShape(
            topStart = dimens.radius.xl,
            topEnd   = dimens.radius.xl,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(dimens.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(dimens.spacing.lg),
        ) {
            // ── Header ────────────────────────────────────────────────────────
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dimens.spacing.sm),
            ) {
                Icon(
                    imageVector        = PhosphorIcons.Regular.MonitorPlay,
                    contentDescription = null,
                    tint               = NSColors.accent,
                    modifier           = Modifier.size(18.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text  = session.name,
                        style = NSType.heading(),
                        color = NSColors.text,
                    )
                    Text(
                        text     = session.channelName.ifEmpty { session.channelId },
                        style    = NSType.caption(),
                        color    = NSColors.text3,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // ── Volume ────────────────────────────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(dimens.spacing.xs)) {
                Text(
                    text  = "Volume",
                    style = NSType.captionMedium(),
                    color = NSColors.text3,
                )
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(dimens.spacing.sm),
                    modifier              = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        imageVector        = if (volume == 0f) PhosphorIcons.Regular.SpeakerNone
                        else PhosphorIcons.Regular.SpeakerHigh,
                        contentDescription = "Volume",
                        tint               = NSColors.text3,
                        modifier           = Modifier.size(16.dp),
                    )
                    Slider(
                        value             = volume,
                        onValueChange     = { volume = it },
                        onValueChangeFinished = {
                            controlViewModel.setVolume(session.deviceId, volume)
                        },
                        modifier = Modifier.weight(1f),
                        colors   = SliderDefaults.colors(
                            thumbColor         = NSColors.accent,
                            activeTrackColor   = NSColors.accent,
                            inactiveTrackColor = NSColors.surface3,
                        ),
                    )
                }
            }

            // ── Actions ───────────────────────────────────────────────────────
            Row(
                horizontalArrangement = Arrangement.spacedBy(dimens.spacing.sm),
                modifier              = Modifier.fillMaxWidth(),
            ) {
                SheetActionButton(
                    label     = if (isPullingBack) "Pulling…" else "Pull Back",
                    isPrimary = true,
                    enabled   = !isPullingBack,
                    onClick   = { controlViewModel.pullBack(session.deviceId) },
                    modifier  = Modifier.weight(1f),
                )
                SheetActionButton(
                    label     = "Stop",
                    isPrimary = false,
                    enabled   = true,
                    onClick   = {
                        controlViewModel.stop(session.deviceId)
                        onDismiss()
                    },
                    modifier  = Modifier.weight(1f),
                )
            }

            Spacer(modifier = Modifier.height(dimens.spacing.md))
        }
    }
}