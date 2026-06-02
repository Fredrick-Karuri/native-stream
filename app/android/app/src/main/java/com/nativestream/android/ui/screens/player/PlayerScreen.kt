// app/src/main/java/com/nativestream/android/ui/screens/player/PlayerScreen.kt
//
// Full-screen composable with ExoPlayer, sidebar, score overlay, error overlay, PiP.

package com.nativestream.android.ui.screens.player

import android.app.Activity
import android.content.pm.ActivityInfo
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

import com.nativestream.android.domain.model.Programme
import com.nativestream.android.ui.viewmodel.EpgViewModel
import com.nativestream.android.ui.viewmodel.PlaylistViewModel
import com.nativestream.android.ui.viewmodel.PlayerViewModel
import com.nativestream.android.ui.viewmodel.CastViewModel

@Composable
fun PlayerScreen(
    playerViewModel: PlayerViewModel,
    castViewModel: CastViewModel,
    onDismiss: () -> Unit,
    epgViewModel: EpgViewModel?       = null,
    playlistViewModel: PlaylistViewModel? = null,
    modifier: Modifier = Modifier,

) {
    val context       = LocalContext.current
    val activity      = context as? Activity
    val activeChannel by playerViewModel.activeChannel.collectAsState()
    val playerError   by playerViewModel.playerError.collectAsState()
    val isInPip       by playerViewModel.isInPip.collectAsState()
    val sidebarVisible by playerViewModel.sidebarVisible.collectAsState()
    val isCastAvailable by castViewModel.isCastAvailable.collectAsState()
    val resizeMode by playerViewModel.resizeMode.collectAsState()

    val programme: Programme? = activeChannel?.let { epgViewModel?.currentProgramme(it) }
    val hasScoreOverlay = programme?.title?.contains(" vs ", ignoreCase = true) == true

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && isInPip) {
                playerViewModel.onExitedPip()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Populate sidebar channel list when playlist is available
    LaunchedEffect(playlistViewModel) {
        playlistViewModel?.channels?.collect { channels ->
            playerViewModel.setChannelList(channels)
        }
    }

    // Force landscape on entry, restore portrait on exit
    DisposableEffect(Unit) {
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

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
                .clickable(
                    indication        = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = {
                        if (sidebarVisible) {
                            playerViewModel.toggleSidebar()
                        } else {
                            playerViewModel.onPlayerTapped()
                        }
                    }
                ),
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player        = playerViewModel.exoPlayer
                        useController = false
                    }
                },
                update   = { view ->
                    view.player = playerViewModel.exoPlayer
                    view.resizeMode = resizeMode
               },
                modifier = Modifier.fillMaxSize(),
            )

            if (hasScoreOverlay && programme != null && !isInPip) {
                ScoreOverlay(
                    programme = programme,
                    modifier  = Modifier.align(Alignment.Center),
                )
            }

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
                    playerViewModel = playerViewModel,
                    channelName     = activeChannel?.name ?: "",
                    programmeTitle  = programme?.title ?: "",
                    onBack          = onDismiss,
                    onToggleSidebar = { playerViewModel.toggleSidebar() },
                    onPip           = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            activity?.enterPictureInPictureMode(playerViewModel.buildPipParams())
                            playerViewModel.onEnteredPip()
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    onNextChannel     = { playerViewModel.playNextChannel() },
                    onPreviousChannel = { playerViewModel.playPreviousChannel() },
                    isCastAvailable = isCastAvailable,
                    onCast = {
                        activeChannel?.let {
                            castViewModel.castStream(it.streamUrl, it.name)
                        }
                    },
                    resizeMode      = resizeMode,
                    onToggleResize  = { playerViewModel.toggleResizeMode() },
                    channel   = activeChannel,
                    programme = programme,
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