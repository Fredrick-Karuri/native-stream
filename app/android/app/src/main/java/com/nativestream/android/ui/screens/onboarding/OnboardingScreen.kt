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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nativestream.android.domain.model.PlaylistSource
import com.nativestream.android.ui.components.NSTextField
import com.nativestream.android.ui.screens.settings.SheetActionButton
import com.nativestream.android.ui.theme.NSColors
import com.nativestream.android.ui.theme.NSDimens
import com.nativestream.android.ui.theme.NSType
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

    val serverUrl by settingsViewModel.serverUrl.collectAsState()

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
            .padding(dimens.spacing.xxl),
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
                    serverUrl    = serverUrl,
                    isChecking   = isChecking,
                    checkError   = checkError,
                    onServerUrlChange = { settingsViewModel.setServerUrl(it) },
                    onSkip       = { step = OnboardingStep.CHANNEL_SETUP },
                    onCheck      = {
                        scope.launch {
                            isChecking  = true
                            checkError  = null
                            val reachable = checkServerReachable(serverUrl)
                            isChecking  = false
                            if (reachable) {
                                if (playlistViewModel.sources.value.isEmpty()) {
                                    playlistViewModel.addSource(
                                        PlaylistSource(
                                            id                   = UUID.randomUUID().toString(),
                                            name                 = "StreamServer",
                                            colorHex = PlaylistSource.COLOR_BLUE,
                                            url                  = "$serverUrl/playlist.m3u",
                                            refreshIntervalHours = DEFAULT_REFRESH_HOURS,
                                        )
                                    )
                                }
                                step = OnboardingStep.CHANNEL_SETUP
                            } else {
                                checkError = "Could not reach $serverUrl — check the IP and port."
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

// ── Step 1: Server check ──────────────────────────────────────────────────────

@Composable
private fun ServerCheckStep(
    serverUrl: String,
    isChecking: Boolean,
    checkError: String?,
    onServerUrlChange: (String) -> Unit,
    onSkip: () -> Unit,
    onCheck: () -> Unit,
) {
    val dimens = NSDimens.current
    StepContainer {
        StepIcon("🖥")
        Text(text = "Welcome to NativeStream", style = NSType.display(), color = NSColors.text)
        Text(
            text  = "Enter the LAN IP address of your NativeStream server.\nMake sure StreamServer is running.",
            style = NSType.body(),
            color = NSColors.text3,
        )
        NSTextField(
            value         = serverUrl,
            onValueChange = onServerUrlChange,
            placeholder   = "http://192.168.1.42:8888",
        )
        checkError?.let {
            Text(text = it, style = NSType.monoSmall(), color = NSColors.live)
        }
        StepButtons(
            skipLabel    = "Skip",
            primaryLabel = if (isChecking) "Checking…" else "Check Connection",
            primaryEnabled = !isChecking,
            onSkip       = onSkip,
            onPrimary    = onCheck,
        )
    }
}

// ── Step 2: Channel setup ─────────────────────────────────────────────────────

@Composable
private fun ChannelSetupStep(
    playlistUrl: String,
    onPlaylistUrlChange: (String) -> Unit,
    onSkip: () -> Unit,
    onAddAndContinue: () -> Unit,
) {
    StepContainer {
        StepIcon("📋")
        Text(text = "Add a Playlist Source", style = NSType.display(), color = NSColors.text)
        Text(
            text  = "Paste your M3U playlist URL below.\nThis is usually your StreamServer's playlist endpoint.",
            style = NSType.body(),
            color = NSColors.text3,
        )
        NSTextField(
            value         = playlistUrl,
            onValueChange = onPlaylistUrlChange,
            placeholder   = "http://192.168.1.42:8888/playlist.m3u",
        )
        StepButtons(
            skipLabel      = "Skip",
            primaryLabel   = "Add & Continue",
            primaryEnabled = playlistUrl.isNotBlank(),
            onSkip         = onSkip,
            onPrimary      = onAddAndContinue,
        )
    }
}

// ── Step 3: EPG setup ─────────────────────────────────────────────────────────

@Composable
private fun EpgSetupStep(
    epgUrl: String,
    onEpgUrlChange: (String) -> Unit,
    onSkip: () -> Unit,
    onSaveAndFinish: () -> Unit,
) {
    StepContainer {
        StepIcon("📺")
        Text(text = "Set Up TV Guide", style = NSType.display(), color = NSColors.text)
        Text(
            text  = "Enter your EPG URL so NativeStream can show\nwhat's on and upcoming match times.",
            style = NSType.body(),
            color = NSColors.text3,
        )
        NSTextField(
            value         = epgUrl,
            onValueChange = onEpgUrlChange,
            placeholder   = "http://192.168.1.42:8888/epg.xml",
        )
        Text(
            text  = "Or use a public source like https://iptv-org.github.io/epg/",
            style = NSType.monoSmall(),
            color = NSColors.text3,
        )
        StepButtons(
            skipLabel    = "Skip",
            primaryLabel = "Save & Finish",
            primaryEnabled = true,
            onSkip       = onSkip,
            onPrimary    = onSaveAndFinish,
        )
    }
}

// ── Step 4: Complete ──────────────────────────────────────────────────────────

@Composable
private fun CompleteStep(onStartWatching: () -> Unit) {
    val dimens = NSDimens.current
    StepContainer {
        Text(text = "✅", fontSize = 56.sp)
        Text(text = "You're all set!", style = NSType.display(), color = NSColors.text)
        Text(
            text  = "NativeStream is ready. Your channels are loading now.\nSelect any channel to start watching.",
            style = NSType.body(),
            color = NSColors.text3,
        )
        Spacer(modifier = Modifier.height(dimens.spacing.sm))
        SheetActionButton(label = "Start Watching", isPrimary = true, enabled = true, onClick = onStartWatching)
    }
}

// ── Shared composables ────────────────────────────────────────────────────────

@Composable
private fun StepContainer(content: @Composable () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(NSDimens.current.spacing.xl),
        modifier = Modifier.padding(NSDimens.current.spacing.xxl),
    ) { content() }
}

@Composable
private fun StepIcon(emoji: String) {
    Text(text = emoji, fontSize = 48.sp)
}

@Composable
private fun StepButtons(
    skipLabel: String,
    primaryLabel: String,
    primaryEnabled: Boolean,
    onSkip: () -> Unit,
    onPrimary: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(NSDimens.current.spacing.md)) {
        SheetActionButton(label = skipLabel,    isPrimary = false, enabled = true,           onClick = onSkip)
        SheetActionButton(label = primaryLabel, isPrimary = true,  enabled = primaryEnabled, onClick = onPrimary)
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