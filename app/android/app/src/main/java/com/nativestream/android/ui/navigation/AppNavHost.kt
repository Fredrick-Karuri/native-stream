// app/src/main/java/com/nativestream/android/ui/navigation/AppNavHost.kt
//
// App Navigation Host
// Shows OnboardingScreen on first launch (onboardingComplete = false).
// After completion → main tab shell with player overlay + mini player.

package com.nativestream.android.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import com.nativestream.android.ui.components.MiniPlayer
import com.nativestream.android.ui.screens.browse.BrowseScreen
import com.nativestream.android.ui.screens.now.NowScreen
import com.nativestream.android.ui.screens.onboarding.OnboardingScreen
import com.nativestream.android.ui.screens.player.PlayerScreen
import com.nativestream.android.ui.screens.settings.SettingsScreen
import com.nativestream.android.ui.viewmodel.EpgViewModel
import com.nativestream.android.ui.viewmodel.PlaylistViewModel
import com.nativestream.android.ui.viewmodel.PlayerViewModel
import com.nativestream.android.ui.viewmodel.SettingsViewModel
import com.nativestream.android.ui.viewmodel.CastViewModel

@Composable
fun AppNavHost(modifier: Modifier = Modifier) {
    val navController       = rememberNavController()
    val playerViewModel: PlayerViewModel   = hiltViewModel()
    val epgViewModel: EpgViewModel         = hiltViewModel()
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val playlistViewModel: PlaylistViewModel = hiltViewModel()
    val castViewModel: CastViewModel = hiltViewModel()
    val hasActiveChannel by playerViewModel.hasActiveChannel.collectAsState()

    val isPlayerVisible     by playerViewModel.isPlayerVisible.collectAsState()
    val onboardingComplete  by settingsViewModel.onboardingComplete.collectAsState()

    // Show onboarding until complete
    if (!onboardingComplete) {
        OnboardingScreen(onComplete = { settingsViewModel.setOnboardingComplete(true) })
        return
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            NavHost(
                navController    = navController,
                startDestination = AppDestination.Now.route,
                modifier         = Modifier.weight(1f),
            ) {
                composable(AppDestination.Now.route) {
                    NowScreen(playerViewModel = playerViewModel)
                }
                composable(AppDestination.Browse.route) {
                    BrowseScreen(playerViewModel = playerViewModel)
                }
                composable(AppDestination.Settings.route) {
                    SettingsScreen()
                }
            }

            AnimatedVisibility(
                visible = hasActiveChannel && !isPlayerVisible,
                enter   = slideInVertically { it },
                exit    = slideOutVertically { it },
            ) {
                MiniPlayer(
                    playerViewModel = playerViewModel,
                    epgViewModel    = epgViewModel,
                    onExpand        = { playerViewModel.showPlayer() },
                    onClose         = { playerViewModel.stop() },
                )
            }

            NSBottomNavBar(
                navController = navController,
                destinations  = bottomNavDestinations,
                onDestinationSelected = { destination ->
                    navController.navigate(destination.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState    = true
                    }
                },
            )
        }

        AnimatedVisibility(
            visible  = isPlayerVisible,
            modifier = Modifier.fillMaxSize(),
            enter    = slideInVertically { it } + fadeIn(),
            exit     = slideOutVertically { it } + fadeOut(),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                PlayerScreen(
                    playerViewModel = playerViewModel,
                    castViewModel = castViewModel,
                    epgViewModel = epgViewModel,
                    playlistViewModel = playlistViewModel,
                    onDismiss = { playerViewModel.hidePlayer() },
                )
            }
        }
    }
}