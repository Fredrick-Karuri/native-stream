package com.nativestream.android.ui.screens.player

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import com.nativestream.android.domain.model.Programme
import com.nativestream.android.ui.foldable.rememberFoldPosture
import com.nativestream.android.ui.viewmodel.CastViewModel
import com.nativestream.android.ui.viewmodel.EpgViewModel
import com.nativestream.android.ui.viewmodel.PlayerViewModel
import com.nativestream.android.ui.viewmodel.PlaylistViewModel


@Composable
fun PlayerStandardLayout(
    playerViewModel: PlayerViewModel,
    castViewModel: CastViewModel,
    epgViewModel: EpgViewModel?,
    playlistViewModel: PlaylistViewModel?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val activeChannel   by playerViewModel.activeChannel.collectAsState()
    val playerError     by playerViewModel.playerError.collectAsState()
    val isInPip         by playerViewModel.isInPip.collectAsState()
    val sidebarVisible  by playerViewModel.sidebarVisible.collectAsState()
    val isCastAvailable by castViewModel.isCastAvailable.collectAsState()
    val resizeMode      by playerViewModel.resizeMode.collectAsState()
    val foldPosture     = rememberFoldPosture()
    val context         = LocalContext.current

    val programme: Programme? = activeChannel?.let { epgViewModel?.currentProgramme(it) }

    Row(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        // ── Video area ────────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .then(
                    if (foldPosture.isBook && foldPosture.hingeBounds != null) {
                        Modifier.windowInsetsPadding(
                            WindowInsets(right = foldPosture.hingeBounds.width.toInt())
                        )
                    } else Modifier
                )
                .clickable(
                    indication        = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = {
                        if (sidebarVisible) {
                            playerViewModel.toggleSidebar()
                        } else {
                            playerViewModel.onPlayerTapped()
                        }
                    },
                ),
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player        = playerViewModel.player
                        useController = false
                    }
                },
                update = { view ->
                    view.player     = playerViewModel.player
                    view.resizeMode = resizeMode
                },
                modifier = Modifier.fillMaxSize(),
            )

            playerError?.let { error ->
                Box(
                    contentAlignment = Alignment.Center,
                    modifier         = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.72f)),
                ) {
                    PlayerErrorOverlay(
                        message = error,
                        onRetry = { playerViewModel.retryManually() },
                    )
                }
            }

            if (!isInPip) {
                PlayerControlsOverlay(
                    playerViewModel   = playerViewModel,
                    channelName       = activeChannel?.name ?: "",
                    programmeTitle    = programme?.title ?: "",
                    onBack            = onDismiss,
                    onToggleSidebar   = { playerViewModel.toggleSidebar() },
                    onPip = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            (context as? Activity)?.enterPictureInPictureMode(
                                playerViewModel.buildPipParams()
                            )
                            playerViewModel.onEnteredPip()
                        }
                    },
                    modifier          = Modifier.fillMaxSize(),
                    onNextChannel     = { playerViewModel.playNextChannel() },
                    onPreviousChannel = { playerViewModel.playPreviousChannel() },
                    isCastAvailable   = isCastAvailable,
                    onCast            = {
                        activeChannel?.let {
                            castViewModel.castStream(it.streamUrl, it.name)
                        }
                    },
                    resizeMode     = resizeMode,
                    onToggleResize = { playerViewModel.toggleResizeMode() },
                    channel        = activeChannel,
                    programme      = programme,
                )
            }
        }

        // ── Collapsible sidebar ───────────────────────────────────────────────
        if (epgViewModel != null) {
            PlayerSidebar(
                isVisible       = sidebarVisible,
                playerViewModel = playerViewModel,
                epgViewModel    = epgViewModel,
            )
        }
    }
}
