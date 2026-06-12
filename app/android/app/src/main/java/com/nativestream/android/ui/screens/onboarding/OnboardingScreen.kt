// app/src/main/java/com/nativestream/android/ui/screens/onboarding/OnboardingScreen.kt
//
// Onboarding Flow
// 3-step flow shown on first launch:
//   Step 1 — Server setup: enter LAN IP, test connection
//   Step 2 — Playlist source: M3U URL field, add inline
//   Step 3 — EPG source: XMLTV URL field
// Completion sets onboardingComplete = true in SettingsDataStore.
// Android emphasises LAN IP, not localhost.

package com.nativestream.android.ui.screens.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.hilt.navigation.compose.hiltViewModel
import com.nativestream.android.domain.model.PlaylistSource
import com.nativestream.android.ui.theme.NSColors
import com.nativestream.android.ui.theme.NSDimens
import com.nativestream.android.ui.viewmodel.PlaylistViewModel
import com.nativestream.android.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch
import java.util.UUID

private const val TOTAL_STEPS           = 3
private const val DEFAULT_REFRESH_HOURS = 6
private val STEP_PILL_ACTIVE_WIDTH      = 24.dp
private val STEP_PILL_INACTIVE_WIDTH    = 8.dp
private val STEP_PILL_HEIGHT            = 6.dp

private enum class OnboardingStep { SERVER_CHECK, CHANNEL_SETUP, EPG_SETUP, COMPLETE }

@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
    settingsViewModel: SettingsViewModel  = hiltViewModel(),
    playlistViewModel: PlaylistViewModel  = hiltViewModel(),
) {
    val scope  = rememberCoroutineScope()
    val dimens = NSDimens.current

    var step             by remember { mutableStateOf(OnboardingStep.SERVER_CHECK) }
    var isChecking       by remember { mutableStateOf(false) }
    var checkError       by remember { mutableStateOf<String?>(null) }
    var playlistUrlInput by remember { mutableStateOf("") }
    var epgUrlInput      by remember { mutableStateOf("") }

    val serverUrlStored  by settingsViewModel.serverUrl.collectAsState()
    val discoveredUrl    by settingsViewModel.discoveredUrl.collectAsState()
    var serverUrlInput   by remember { mutableStateOf("") }

    LaunchedEffect(serverUrlStored) {
        if (serverUrlInput.isBlank()) serverUrlInput = serverUrlStored
    }
    LaunchedEffect(discoveredUrl) {
        discoveredUrl?.let { serverUrlInput = it }
    }
    LaunchedEffect(Unit) { settingsViewModel.startDiscovery() }

    val stepIndex = when (step) {
        OnboardingStep.SERVER_CHECK  -> 0
        OnboardingStep.CHANNEL_SETUP -> 1
        OnboardingStep.EPG_SETUP,
        OnboardingStep.COMPLETE      -> 2
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxSize()
            .background(NSColors.bg)
            .imePadding()
            .padding(dimens.spacing.xxl)
            .verticalScroll(rememberScrollState()),
    ) {
        // ── Step pills ────────────────────────────────────────────────────────
        Row(
            horizontalArrangement = Arrangement.spacedBy(dimens.spacing.xs),
            modifier = Modifier.padding(top = dimens.spacing.xxl),
        ) {
            repeat(TOTAL_STEPS) { i ->
                val isActive = stepIndex >= i
                val width    = if (stepIndex == i) STEP_PILL_ACTIVE_WIDTH else STEP_PILL_INACTIVE_WIDTH
                Spacer(
                    modifier = Modifier
                        .width(width)
                        .height(STEP_PILL_HEIGHT)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(if (isActive) NSColors.accent else NSColors.border2),
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // ── Step content ──────────────────────────────────────────────────────
        AnimatedContent(
            targetState = step,
            transitionSpec = {
                slideInHorizontally { it } togetherWith slideOutHorizontally { -it }
            },
            label = "onboardingStep",
        ) { currentStep ->
            when (currentStep) {
                OnboardingStep.SERVER_CHECK -> ServerCheckStep(
                    serverUrl         = serverUrlInput,
                    isChecking        = isChecking,
                    checkError        = checkError,
                    scanning          = settingsViewModel.scanning.collectAsState().value,
                    discoveredUrl     = discoveredUrl,
                    onServerUrlChange = { serverUrlInput = it },
                    onScan            = { settingsViewModel.startDiscovery() },
                    onConfirmDiscovered = { url ->
                        settingsViewModel.confirmDiscoveredUrl(url)
                        scope.launch {
                            if (playlistViewModel.sources.value.isEmpty()) {
                                playlistViewModel.addSource(
                                    PlaylistSource(
                                        id                   = UUID.randomUUID().toString(),
                                        name                 = "StreamServer",
                                        colorHex             = PlaylistSource.COLOR_BLUE,
                                        url                  = "$url/playlist.m3u",
                                        refreshIntervalHours = DEFAULT_REFRESH_HOURS,
                                    )
                                )
                            }
                            step = OnboardingStep.CHANNEL_SETUP
                        }
                    },
                    onSkip            = { step = OnboardingStep.CHANNEL_SETUP },
                    onCheck           = {
                        scope.launch {
                            isChecking = true
                            checkError = null
                            val reachable = checkServerReachable(serverUrlInput)
                            isChecking = false
                            if (reachable) {
                                settingsViewModel.setServerUrl(serverUrlInput)
                                if (playlistViewModel.sources.value.isEmpty()) {
                                    playlistViewModel.addSource(
                                        PlaylistSource(
                                            id                   = UUID.randomUUID().toString(),
                                            name                 = "StreamServer",
                                            colorHex             = PlaylistSource.COLOR_BLUE,
                                            url                  = "$serverUrlInput/playlist.m3u",
                                            refreshIntervalHours = DEFAULT_REFRESH_HOURS,
                                        )
                                    )
                                }
                                step = OnboardingStep.CHANNEL_SETUP
                            } else {
                                checkError = "Could not reach $serverUrlInput — check the IP and port."
                            }
                        }
                    },
                )

                OnboardingStep.CHANNEL_SETUP -> ChannelSetupStep(
                    playlistUrl        = playlistUrlInput,
                    onPlaylistUrlChange = { playlistUrlInput = it },
                    onSkip             = { step = OnboardingStep.EPG_SETUP },
                    onAddAndContinue   = {
                        if (playlistUrlInput.isNotBlank()) {
                            playlistViewModel.addSource(
                                PlaylistSource(
                                    id                   = UUID.randomUUID().toString(),
                                    name                 = playlistUrlInput.substringAfterLast("/").ifEmpty { "Playlist" },
                                    colorHex = PlaylistSource.COLOR_GREEN,
                                    url                  = playlistUrlInput.trim(),
                                    refreshIntervalHours = DEFAULT_REFRESH_HOURS,
                                )
                            )
                            playlistViewModel.loadAll()
                        }
                        step = OnboardingStep.EPG_SETUP
                    },
                )

                OnboardingStep.EPG_SETUP -> EpgSetupStep(
                    epgUrl        = epgUrlInput,
                    onEpgUrlChange = { epgUrlInput = it },
                    onSkip        = { step = OnboardingStep.COMPLETE },
                    onSaveAndFinish = {
                        if (epgUrlInput.isNotBlank()) settingsViewModel.setEpgUrl(epgUrlInput)
                        step = OnboardingStep.COMPLETE
                    },
                )

                OnboardingStep.COMPLETE -> CompleteStep(
                    onStartWatching = {
                        settingsViewModel.setOnboardingComplete(true)
                        onComplete()
                    },
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))
    }
}
// ── Network helper ────────────────────────────────────────────────────────────

private suspend fun checkServerReachable(serverUrl: String): Boolean {
    return try {
        val url = java.net.URL("$serverUrl/api/health")
        val connection = url.openConnection() as java.net.HttpURLConnection
        connection.connectTimeout = 5_000
        connection.readTimeout    = 5_000
        connection.requestMethod  = "GET"
        val code = connection.responseCode
        connection.disconnect()
        code in 200..299
    } catch (e: Exception) {
        false
    }
}