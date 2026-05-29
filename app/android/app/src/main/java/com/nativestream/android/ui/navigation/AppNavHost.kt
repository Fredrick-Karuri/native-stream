// app/src/main/java/com/nativestream/android/ui/navigation/AppNavHost.kt
//
// NS-008: App Navigation Host (updated AND-009 — MiniPlayer wired in)
// NavHost wiring Now · Browse · Settings tabs.
// Player launches as a full-screen composable overlay — not a separate Activity.
// MiniPlayer sits above the bottom nav bar, shown when playing outside full player.

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.nativestream.android.ui.components.MiniPlayer
import com.nativestream.android.ui.screens.browse.BrowseScreen
import com.nativestream.android.ui.screens.now.NowScreen
import com.nativestream.android.ui.screens.player.PlayerScreen
import com.nativestream.android.ui.screens.settings.SettingsScreen
import com.nativestream.android.ui.viewmodel.EpgViewModel
import com.nativestream.android.ui.viewmodel.PlayerViewModel

@Composable
fun AppNavHost(modifier: Modifier = Modifier) {
    val navController   = rememberNavController()
    val playerViewModel: PlayerViewModel = hiltViewModel()
    val epgViewModel: EpgViewModel       = hiltViewModel()
    val isPlayerVisible by playerViewModel.isPlayerVisible.collectAsState()

    Box(modifier = modifier.fillMaxSize()) {

        // ── Tab scaffold ──────────────────────────────────────────────────────
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

            // Mini player — visible when playing, hidden inside full player
            AnimatedVisibility(
                visible = playerViewModel.hasActiveChannel && !isPlayerVisible,
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

        // ── Full-screen player overlay ─────────────────────────────────────
        AnimatedVisibility(
            visible  = isPlayerVisible,
            modifier = Modifier.fillMaxSize(),
            enter    = slideInVertically { it } + fadeIn(),
            exit     = slideOutVertically { it } + fadeOut(),
        ) {
            PlayerScreen(
                playerViewModel = playerViewModel,
                onDismiss       = { playerViewModel.hidePlayer() },
            )
        }
    }
}