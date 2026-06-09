// app/src/main/java/com/nativestream/android/ui/screens/player/PlayerScreen.kt
//
// Full-screen composable with ExoPlayer, sidebar, score overlay, error overlay, PiP.
// Foldable-aware: tabletop posture splits video (top) / controls (bottom).

package com.nativestream.android.ui.screens.player

import android.app.Activity
import android.content.pm.ActivityInfo
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.material3.Text
import androidx.media3.ui.PlayerView
import com.nativestream.android.domain.model.Programme
import com.nativestream.android.ui.components.NSProgressBar
import com.nativestream.android.ui.foldable.rememberFoldPosture
import com.nativestream.android.ui.theme.NSColors
import com.nativestream.android.ui.theme.NSDimens
import com.nativestream.android.ui.theme.NSType
import com.nativestream.android.ui.viewmodel.CastViewModel
import com.nativestream.android.ui.viewmodel.EpgViewModel
import com.nativestream.android.ui.viewmodel.PlaylistViewModel
import com.nativestream.android.ui.viewmodel.PlayerViewModel

@Composable
fun PlayerScreen(
    playerViewModel: PlayerViewModel,
    castViewModel: CastViewModel,
    onDismiss: () -> Unit,
    epgViewModel: EpgViewModel?           = null,
    playlistViewModel: PlaylistViewModel? = null,
    modifier: Modifier = Modifier,
) {
    val context       = LocalContext.current
    val activity      = context as? Activity
    val isInPip       by playerViewModel.isInPip.collectAsState()
    val foldPosture   = rememberFoldPosture()

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

    LaunchedEffect(playlistViewModel) {
        playlistViewModel?.channels?.collect { channels ->
            playerViewModel.setChannelList(channels)
        }
    }

    DisposableEffect(Unit) {
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    if (foldPosture.isTableTop) {
        PlayerTabletopLayout(
            playerViewModel   = playerViewModel,
            castViewModel     = castViewModel,
            epgViewModel      = epgViewModel,
            playlistViewModel = playlistViewModel,
            onDismiss         = onDismiss,
            modifier          = modifier,
        )
    } else {
        PlayerStandardLayout(
            playerViewModel   = playerViewModel,
            castViewModel     = castViewModel,
            epgViewModel      = epgViewModel,
            playlistViewModel = playlistViewModel,
            onDismiss         = onDismiss,
            modifier          = modifier,
        )
    }
}

// ── Standard layout (existing Row body) ──────────────────────────────────────

@Composable
private fun PlayerStandardLayout(
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
                    onPip             = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            (LocalContext as? Activity)?.enterPictureInPictureMode(playerViewModel.buildPipParams())
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

// ── Tabletop layout ───────────────────────────────────────────────────────────

@Composable
private fun PlayerTabletopLayout(
    playerViewModel: PlayerViewModel,
    castViewModel: CastViewModel,
    epgViewModel: EpgViewModel?,
    playlistViewModel: PlaylistViewModel?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val activeChannel   by playerViewModel.activeChannel.collectAsState()
    val playerError     by playerViewModel.playerError.collectAsState()
    val resizeMode      by playerViewModel.resizeMode.collectAsState()
    val isCastAvailable by castViewModel.isCastAvailable.collectAsState()

    val programme = activeChannel?.let { epgViewModel?.currentProgramme(it) }
    val schedule  = remember(activeChannel) {
        activeChannel?.let { epgViewModel?.schedule(it) } ?: emptyList()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        // ── Top half — video only ─────────────────────────────────────────────
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
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
        }

        // Hinge divider
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color.White.copy(alpha = 0.08f)),
        )

        // ── Bottom half — controls + programme info + mini EPG ────────────────
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color(0xFF0A0A0A))
                .padding(NSDimens.current.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(NSDimens.current.spacing.md),
        ) {
            // Channel + programme + back button
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(NSDimens.current.spacing.sm),
                modifier              = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text  = activeChannel?.name ?: "",
                        style = NSType.caption(),
                        color = Color.White.copy(alpha = 0.55f),
                    )
                    Text(
                        text     = programme?.title ?: "",
                        style    = NSType.bodyMedium(),
                        color    = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                ControlButton(
                    icon               = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    onClick            = onDismiss,
                    size               = 36.dp,
                )
            }

            programme?.let {
                NSProgressBar(
                    value    = it.progress.toFloat(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // Playback controls
            PlayerControlsOverlay(
                playerViewModel   = playerViewModel,
                channelName       = activeChannel?.name ?: "",
                programmeTitle    = programme?.title ?: "",
                onBack            = onDismiss,
                onToggleSidebar   = { playerViewModel.toggleSidebar() },
                onPip             = {},
                onNextChannel     = { playerViewModel.playNextChannel() },
                onPreviousChannel = { playerViewModel.playPreviousChannel() },
                isCastAvailable   = isCastAvailable,
                onCast            = {
                    activeChannel?.let { castViewModel.castStream(it.streamUrl, it.name) }
                },
                resizeMode     = resizeMode,
                onToggleResize = { playerViewModel.toggleResizeMode() },
                channel        = activeChannel,
                programme      = programme,
                modifier       = Modifier.fillMaxWidth(),
            )

            // Mini EPG
            if (schedule.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(0.5.dp)
                        .background(Color.White.copy(alpha = 0.07f)),
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(NSDimens.current.spacing.sm),
                    modifier              = Modifier.fillMaxWidth(),
                ) {
                    items(schedule.take(6), key = { it.id }) { prog ->
                        TabletopEpgChip(programme = prog)
                    }
                }
            }
        }
    }
}

// ── Mini EPG chip ─────────────────────────────────────────────────────────────

@Composable
private fun TabletopEpgChip(programme: Programme) {
    val dimens     = NSDimens.current
    val timeFormat = remember { java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()) }
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(dimens.radius.md))
            .background(
                if (programme.isNow) NSColors.accentGlow else Color.White.copy(alpha = 0.05f)
            )
            .border(
                0.5.dp,
                if (programme.isNow) NSColors.accentBorder else Color.White.copy(alpha = 0.08f),
                RoundedCornerShape(dimens.radius.md),
            )
            .padding(horizontal = dimens.spacing.sm, vertical = dimens.spacing.xs)
            .widthIn(min = 80.dp, max = 140.dp),
    ) {
        Text(
            text  = timeFormat.format(java.util.Date(programme.startEpochMs)),
            style = NSType.monoSmall(),
            color = if (programme.isNow) NSColors.accent else Color.White.copy(alpha = 0.35f),
        )
        Text(
            text     = programme.title,
            style    = NSType.caption(),
            color    = Color.White.copy(alpha = if (programme.isNow) 0.85f else 0.5f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}