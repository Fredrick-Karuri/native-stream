// app/src/main/java/com/nativestream/android/ui/screens/onboarding/OnboardingScreen.kt

package com.nativestream.android.ui.screens.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.nativestream.android.domain.model.PlaylistSource
import com.nativestream.android.ui.viewmodel.SettingsViewModel
import com.nativestream.android.ui.viewmodel.SourceViewModel
import java.util.UUID

private const val DEFAULT_REFRESH_HOURS = 6
private enum class OnboardingStep { SPLASH, SERVER, PLAYLIST, EPG }


@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
    settingsViewModel: SettingsViewModel = hiltViewModel(),
    sourceViewModel:   SourceViewModel   = hiltViewModel(),
) {
    var step by remember { mutableStateOf(OnboardingStep.SPLASH) }

    val connectionState by settingsViewModel.connectionState.collectAsState()
    val discoveredUrl   by settingsViewModel.discoveredUrl.collectAsState()
    val scanning        by settingsViewModel.scanning.collectAsState()
    val serverUrl       by settingsViewModel.serverUrl.collectAsState()

    LaunchedEffect(connectionState) {
        if (connectionState is OnboardingConnectionState.Success) {
            step = OnboardingStep.PLAYLIST
        }
    }

    AnimatedContent(
        targetState = step,
        transitionSpec = {
            slideInHorizontally { it } togetherWith slideOutHorizontally { -it }
        },
        label = "onboardingStep",
        modifier = modifier.fillMaxSize(),
    ) { currentStep ->
        when (currentStep) {
            OnboardingStep.SPLASH -> SplashScreen(
                onComplete        = { step = OnboardingStep.SERVER },
                onStartDiscovery  = { settingsViewModel.startDiscovery() },
            )

            OnboardingStep.SERVER -> ServerConnectScreen(
                serverUrl       = serverUrl,
                connectionState = connectionState,
                discoveredUrl   = discoveredUrl,
                scanning        = scanning,
                onServerUrlChange = { settingsViewModel.setServerUrl(it) },
                onScan          = { settingsViewModel.startDiscovery() },
                onConnect       = { url ->
                    settingsViewModel.setServerUrl(url)
                    settingsViewModel.checkConnection(url)
                },
                onConfirmDiscovered = { url ->
                    settingsViewModel.confirmDiscoveredUrl(url)
                    settingsViewModel.checkConnection(url)
                },
                onAddServerSource = { url ->
                    if (sourceViewModel.sources.value.isEmpty()) {
                        sourceViewModel.addSource(
                            PlaylistSource(
                                id                   = UUID.randomUUID().toString(),
                                name                 = "StreamServer",
                                colorHex             = PlaylistSource.COLOR_BLUE,
                                url                  = "$url/playlist.m3u",
                                refreshIntervalHours = DEFAULT_REFRESH_HOURS,
                            )
                        )
                    }
                },
                onSkip = {
                    settingsViewModel.setOnboardingComplete(true)
                    onComplete()
                },
            )
            OnboardingStep.PLAYLIST -> PlaylistScreen(
                connectionState = connectionState,
                onSourceAdded   = { url ->
                    if (url.isNotBlank()) {
                        sourceViewModel.addSource(
                            PlaylistSource(
                                id                   = UUID.randomUUID().toString(),
                                name                 = url.substringAfterLast("/").ifEmpty { "Playlist" },
                                colorHex             = PlaylistSource.COLOR_GREEN,
                                url                  = url.trim(),
                                refreshIntervalHours = DEFAULT_REFRESH_HOURS,
                            )
                        )
                    }
                },
                onAddAndContinue = { _, foundEpgUrl ->
                    val success      = connectionState as? OnboardingConnectionState.Success
                    val serverHasEpg = success?.hasEpg == true || success?.epgFromPlaylist == true
                    if (serverHasEpg || foundEpgUrl != null) {
                        if (foundEpgUrl != null) settingsViewModel.setEpgUrl(foundEpgUrl)
                        settingsViewModel.setOnboardingComplete(true)
                        onComplete()
                    } else {
                        step = OnboardingStep.EPG
                    }
                },
                onSkip          = {
                    val success = connectionState as? OnboardingConnectionState.Success
                    if (success?.hasEpg == true || success?.epgFromPlaylist == true) {
                        settingsViewModel.setOnboardingComplete(true)
                        onComplete()
                    } else {
                        step = OnboardingStep.EPG
                    }
                },
                onProbePlaylist = { url -> settingsViewModel.probePlaylistForEpg(url) },
            )

            OnboardingStep.EPG -> EpgScreen(
                onSave = { epgUrl ->
                    if (epgUrl.isNotBlank()) settingsViewModel.setEpgUrl(epgUrl)
                    settingsViewModel.setOnboardingComplete(true)
                    onComplete()
                },
                onSkip = {
                    settingsViewModel.setOnboardingComplete(true)
                    onComplete()
                },
            )
        }
    }
}