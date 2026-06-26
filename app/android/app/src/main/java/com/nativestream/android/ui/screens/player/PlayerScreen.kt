// app/src/main/java/com/nativestream/android/ui/screens/player/PlayerScreen.kt
//
// Full-screen composable with ExoPlayer, sidebar, score overlay, error overlay, PiP.
// Foldable-aware: tabletop posture splits video (top) / controls (bottom).

package com.nativestream.android.ui.screens.player

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.nativestream.android.ui.foldable.rememberFoldPosture
import com.nativestream.android.ui.viewmodel.CastViewModel
import com.nativestream.android.ui.viewmodel.EpgViewModel
import com.nativestream.android.ui.viewmodel.PlayerViewModel

@Composable
fun PlayerScreen(
    playerViewModel: PlayerViewModel,
    castViewModel: CastViewModel,
    onDismiss: () -> Unit,
    epgViewModel: EpgViewModel?           = null,
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


    DisposableEffect(Unit) {
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    if (foldPosture.isTableTop) {
        PlayerTabletopLayout(
            playerViewModel   = playerViewModel,
            castViewModel     = castViewModel,
            epgViewModel      = epgViewModel,
            onDismiss         = onDismiss,
            modifier          = modifier,
        )
    } else {
        PlayerStandardLayout(
            playerViewModel   = playerViewModel,
            castViewModel     = castViewModel,
            epgViewModel      = epgViewModel,
            onDismiss         = onDismiss,
            modifier          = modifier,
        )
    }
}