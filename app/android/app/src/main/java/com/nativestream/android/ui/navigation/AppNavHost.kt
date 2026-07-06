// app/src/main/java/com/nativestream/android/ui/navigation/AppNavHost.kt
//
// App Navigation Host
// Shows OnboardingScreen on first launch (onboardingComplete = false).
// After completion → main tab shell with player overlay + mini player.
// Player AnimatedVisibility sits outside the safeDrawing-padded box so it
// renders at true window bounds (covers notch + system nav).

package com.nativestream.android.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.windowsizeclass.WindowHeightSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.nativestream.android.ui.LocalWindowSizeClass
import com.nativestream.android.ui.components.MiniPlayer
import com.nativestream.android.ui.foldable.rememberFoldPosture
import com.nativestream.android.ui.screens.browse.BrowseScreen
import com.nativestream.android.ui.screens.now.NowScreen
import com.nativestream.android.ui.screens.onboarding.OnboardingScreen
import com.nativestream.android.ui.screens.player.PlayerScreen
import com.nativestream.android.ui.screens.settings.SettingsScreen
import com.nativestream.android.ui.viewmodel.CastViewModel
import com.nativestream.android.ui.viewmodel.ChannelLoadingViewModel
import com.nativestream.android.ui.viewmodel.EpgViewModel
import com.nativestream.android.ui.viewmodel.PlayerViewModel
import com.nativestream.android.ui.viewmodel.SettingsViewModel
import com.nativestream.android.ui.components.ConnectBar
import com.nativestream.android.ui.viewmodel.ControlViewModel
import com.nativestream.android.ui.screens.remote.RemoteScreen
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.background
import com.nativestream.android.ui.theme.NSColors
import com.nativestream.android.ui.viewmodel.NetworkViewModel
import com.nativestream.android.ui.components.OfflineBanner
import com.nativestream.android.ui.components.ServerReconnectBanner
import com.nativestream.android.ui.viewmodel.ServerHealthViewModel
import android.os.Build

@Composable
fun AppNavHost(
    modifier: Modifier = Modifier,
) {
    val navController        = rememberNavController()
    val playerViewModel: PlayerViewModel     = hiltViewModel()
    val epgViewModel: EpgViewModel           = hiltViewModel()
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val castViewModel: CastViewModel         = hiltViewModel()
    val loadingViewModel: ChannelLoadingViewModel = hiltViewModel() // Added to ensure Now screen loads channels
    val controlViewModel: ControlViewModel        = hiltViewModel()
    val networkViewModel: NetworkViewModel        = hiltViewModel()
    val serverHealthViewModel: ServerHealthViewModel = hiltViewModel()


    val hasActiveChannel   by playerViewModel.hasActiveChannel.collectAsState()
    val isOnline           by networkViewModel.isOnline.collectAsState()
    val pendingUrl         by serverHealthViewModel.pendingUrl.collectAsState()
    var showRemoteScreen   by remember { mutableStateOf(false) }
    val isPlayerVisible    by playerViewModel.isPlayerVisible.collectAsState()
    val isLoading          by settingsViewModel.isLoading.collectAsState()
    val onboardingComplete by settingsViewModel.onboardingComplete.collectAsState()

    val windowSizeClass = LocalWindowSizeClass.current
    val isTablet = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Expanded
            && windowSizeClass.heightSizeClass != WindowHeightSizeClass.Compact
    val useRail = isTablet

    val foldPosture     = rememberFoldPosture()

    val onDestinationSelected: (AppDestination) -> Unit = { destination ->
        navController.navigate(destination.route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState    = true
        }
    }
    var wasOnline by remember { mutableStateOf(isOnline) }

    if (isLoading) return

    if (!onboardingComplete) {
        LaunchedEffect(Unit) { settingsViewModel.resetConnectionState() }
        OnboardingScreen(onComplete = { })
        return
    }

    LaunchedEffect(Unit) {
        playerViewModel.connectToService()
    }

    LaunchedEffect(isOnline) {
        if (isOnline && !wasOnline) {
            loadingViewModel.loadAll(isBackgroundRefresh = true)
            epgViewModel.load(isBackgroundRefresh = true)
            controlViewModel.retryConnection()
        }
        wasOnline = isOnline
    }

    // Outer Box — true window bounds, no inset padding.
    // The player overlay is a direct child here so it fills the full screen.
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(NSColors.bg),
    ) {

        // Inner Box — safeDrawing + book-posture hinge padding for the nav shell only.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .then(
                    if (foldPosture.isBook && foldPosture.hingeBounds != null) {
                        Modifier.windowInsetsPadding(
                            WindowInsets(
                                left  = foldPosture.hingeBounds.width.toInt(),
                                right = 0,
                            )
                        )
                    } else Modifier
                ),
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                    if (useRail && !isPlayerVisible) {
                        NSNavRail(
                            navController = navController,
                            destinations = bottomNavDestinations,
                            onDestinationSelected = onDestinationSelected,
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        OfflineBanner(isOffline = !isOnline)
                        ServerReconnectBanner(
                            discoveredUrl = pendingUrl,
                            onConfirm = {
                                pendingUrl?.let { url ->
                                    serverHealthViewModel.confirmDiscoveredUrl(url)
                                    controlViewModel.retryConnection()
                                    loadingViewModel.loadAll(isBackgroundRefresh = true)
                                    epgViewModel.load(isBackgroundRefresh = true)
                                }
                            },
                            onDismiss = { serverHealthViewModel.dismissDiscoveredUrl() },
                        )
                        NavHost(
                            navController = navController,
                            startDestination = AppDestination.Now.route,
                            modifier = Modifier.weight(1f),
                        ) {
                            composable(AppDestination.Now.route) {
                                NowScreen(
                                    playerViewModel = playerViewModel,
                                    epgViewModel = epgViewModel,
                                )
                            }
                            composable(AppDestination.Browse.route) {
                                BrowseScreen(
                                    playerViewModel = playerViewModel,
                                )
                            }
                            composable(AppDestination.Settings.route) {
                                SettingsScreen()
                            }
                        }

                        AnimatedVisibility(
                            visible = hasActiveChannel && !isPlayerVisible,
                            enter = slideInVertically { it },
                            exit = slideOutVertically { it },
                        ) {
                            MiniPlayer(
                                playerViewModel = playerViewModel,
                                epgViewModel = epgViewModel,
                                onExpand = { playerViewModel.showPlayer() },
                                onClose = { playerViewModel.stop() },
                            )
                        }

                        if (!isPlayerVisible) {
                            ConnectBar(
                                controlViewModel = controlViewModel,
                                onTap            = { showRemoteScreen = true },
                            )
                        }

                        if (!useRail && !isPlayerVisible) {
                            NSBottomNavBar(
                                navController = navController,
                                destinations = bottomNavDestinations,
                                onDestinationSelected = onDestinationSelected,
                            )
                        }
                    }
            }
        }

        // Player overlay — child of the outer Box, outside safeDrawing padding.
        // Renders at true window bounds; PlayerControls.kt applies its own
        // statusBarsPadding / navigationBarsPadding internally.
        AnimatedVisibility(
            visible  = isPlayerVisible,
            modifier = Modifier.fillMaxSize(),
            enter    = slideInVertically { it } + fadeIn(),
            exit     = slideOutVertically { it } + fadeOut(),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                PlayerScreen(
                    playerViewModel   = playerViewModel,
                    castViewModel     = castViewModel,
                    epgViewModel      = epgViewModel,
                    onDismiss         = { playerViewModel.hidePlayer() },
                )
            }
        }
        if (showRemoteScreen) {
            RemoteScreen(
                controlViewModel = controlViewModel,
                onDismiss        = { showRemoteScreen = false },
                onPullBackReady  = { channelId, channelName, streamUrl ->
                    playerViewModel.playFromRemote(channelId, channelName, streamUrl)
                    showRemoteScreen = false
                },
            )
        }
    }
}

