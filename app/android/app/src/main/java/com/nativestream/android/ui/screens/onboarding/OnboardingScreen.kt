// app/src/main/java/com/nativestream/android/ui/screens/onboarding/OnboardingScreen.kt

package com.nativestream.android.ui.screens.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nativestream.android.domain.model.PlaylistSource
import com.nativestream.android.ui.components.NSTextField
import com.nativestream.android.ui.components.SheetActionButton
import com.nativestream.android.ui.theme.NSColors
import com.nativestream.android.ui.theme.NSDimens
import com.nativestream.android.ui.theme.NSType
import com.nativestream.android.ui.viewmodel.SettingsViewModel
import com.nativestream.android.ui.viewmodel.SourceViewModel
import kotlinx.coroutines.launch
import java.util.UUID

private const val DEFAULT_REFRESH_HOURS = 6
private const val SCAN_TIMEOUT_MS       = 10_000L
private const val NARRATIVE_DELAY_MS    = 300L

private const val IPTV_ORG_EPG =
    "https://iptv-org.github.io/epg/guides/en/xmltv.xml"

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

// ── Screen 1: Server connect ──────────────────────────────────────────────────

@Composable
private fun ServerConnectScreen(
    serverUrl: String,
    connectionState: OnboardingConnectionState,
    discoveredUrl: String?,
    scanning: Boolean,
    onServerUrlChange: (String) -> Unit,
    onScan: () -> Unit,
    onConnect: (String) -> Unit,
    onConfirmDiscovered: (String) -> Unit,
    onAddServerSource: (String) -> Unit,
    onSkip: () -> Unit,
) {
    val dimens = NSDimens.current
    var urlInput by remember(serverUrl) { mutableStateOf(serverUrl) }

    // Auto-fill on discovery
    LaunchedEffect(discoveredUrl) {
        discoveredUrl?.let { urlInput = it }
    }

    // Auto-fill URL on discovery but wait for user to confirm
    LaunchedEffect(discoveredUrl) {
        discoveredUrl?.let { urlInput = it }
    }

    // Add server playlist source on success
    LaunchedEffect(connectionState) {
        if (connectionState is OnboardingConnectionState.Success) {
            onAddServerSource(urlInput)
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxSize()
            .background(NSColors.bg)
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(dimens.spacing.xxl),
    ) {
        Spacer(modifier = Modifier.weight(1f))

        StepContainer {
            StepIcon("🖥")
            Text(
                text  = "Connect to your server",
                style = NSType.display(),
                color = NSColors.text,
            )

            when (connectionState) {
                is OnboardingConnectionState.Checking -> {
                    // Narrative progress
                    NarrativeProgress(connectionState = connectionState)
                }

                is OnboardingConnectionState.Success -> {
                    NarrativeProgress(connectionState = connectionState)
                }

                is OnboardingConnectionState.Failure -> {
                    FailureState(
                        reason    = connectionState.reason,
                        serverUrl = urlInput,
                        onRetry   = { onConnect(urlInput) },
                    )
                }

                else -> {
                    // Idle — show input
                    IdleServerInput(
                        urlInput      = urlInput,
                        discoveredUrl = discoveredUrl,
                        scanning      = scanning,
                        onUrlChange   = { urlInput = it },
                        onScan        = onScan,
                        onConnect     = { onConnect(urlInput) },
                        onSkip        = onSkip,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun IdleServerInput(
    urlInput: String,
    discoveredUrl: String?,
    scanning: Boolean,
    onUrlChange: (String) -> Unit,
    onScan: () -> Unit,
    onConnect: () -> Unit,
    onSkip: () -> Unit,
) {
    val dimens    = NSDimens.current
    val isTyping  = urlInput.isNotBlank() && urlInput != discoveredUrl

    Text(
        text  = when {
            discoveredUrl != null -> "Server found on your network!"
            scanning              -> "Scanning your network…"
            else                  -> "Enter your NativeStream server address."
        },
        style = NSType.body(),
        color = if (discoveredUrl != null) NSColors.accent else NSColors.text3,
    )

    if (scanning && discoveredUrl == null) {
        CircularProgressIndicator(
            color       = NSColors.accent,
            strokeWidth = 2.dp,
        )
    }

    NSTextField(
        value         = urlInput,
        onValueChange = onUrlChange,
        placeholder   = "http://192.168.1.42:8888",
    )

    Row(
        horizontalArrangement = Arrangement.spacedBy(dimens.spacing.md),
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (!isTyping) {
            SheetActionButton(
                label     = if (scanning) "Scanning…" else "Scan Network",
                isPrimary = false,
                enabled   = !scanning,
                onClick   = onScan,
                modifier  = Modifier.weight(1f),
            )
        }
        SheetActionButton(
            label     = "Connect",
            isPrimary = true,
            enabled   = urlInput.isNotBlank(),
            onClick   = onConnect,
            modifier  = Modifier.weight(1f),
        )
    }

    Text(
        text     = "Skip for now",
        style    = NSType.monoSmall(),
        color    = NSColors.text3.copy(alpha = 0.5f),
        modifier = Modifier.clickable(onClick = onSkip),
    )
}

@Composable
private fun NarrativeProgress(connectionState: OnboardingConnectionState) {
    val dimens  = NSDimens.current
    val success = connectionState as? OnboardingConnectionState.Success

    var showServer   by remember { mutableStateOf(false) }
    var showPlaylist by remember { mutableStateOf(false) }
    var showEpg      by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        showServer = true
        kotlinx.coroutines.delay(NARRATIVE_DELAY_MS)
        showPlaylist = true
        kotlinx.coroutines.delay(NARRATIVE_DELAY_MS)
        if (success?.hasEpg == true) showEpg = true
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(dimens.spacing.sm),
        horizontalAlignment = Alignment.Start,
        modifier = Modifier.fillMaxWidth(),
    ) {
        AnimatedVisibility(visible = showServer, enter = fadeIn(tween(300))) {
            Text(
                text  = "✓ Server reached",
                style = NSType.bodyMedium(),
                color = NSColors.accent,
            )
        }
        AnimatedVisibility(visible = showPlaylist, enter = fadeIn(tween(300))) {
            Text(
                text  = success?.let { "✓ Playlist found — ${it.channels} channels" }
                    ?: "✓ Playlist found",
                style = NSType.bodyMedium(),
                color = NSColors.accent,
            )
        }
        AnimatedVisibility(visible = showEpg, enter = fadeIn(tween(300))) {
            Text(
                text  = "✓ TV Guide found",
                style = NSType.bodyMedium(),
                color = NSColors.accent,
            )
        }
        if (connectionState is OnboardingConnectionState.Checking) {
            CircularProgressIndicator(
                color       = NSColors.accent,
                strokeWidth = 2.dp,
            )
        }
    }
}

@Composable
private fun FailureState(
    reason: FailureReason,
    serverUrl: String,
    onRetry: () -> Unit,
) {
    val dimens = NSDimens.current
    Column(
        verticalArrangement = Arrangement.spacedBy(dimens.spacing.sm),
        horizontalAlignment = Alignment.Start,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text  = "✗ Couldn't reach $serverUrl",
            style = NSType.bodyMedium(),
            color = NSColors.live,
        )
        val suggestions = when (reason) {
            FailureReason.UNREACHABLE -> listOf(
                "Is the server running? Try: make run-server",
                "Are you on the same WiFi network?",
                "Check the IP in your server's terminal output",
            )
            FailureReason.NO_PLAYLIST -> listOf(
                "Server reached but no playlist found",
                "Check StreamServer is running: make run-server",
            )
            FailureReason.UNKNOWN -> listOf(
                "Something went wrong — check the server logs",
            )
        }
        suggestions.forEach { suggestion ->
            Text(
                text  = "→ $suggestion",
                style = NSType.body(),
                color = NSColors.text3,
            )
        }
        SheetActionButton(
            label     = "Try again",
            isPrimary = true,
            enabled   = true,
            onClick   = onRetry,
        )
    }
}

// ── Screen 2: EPG (conditional) ───────────────────────────────────────────────

@Composable
private fun EpgScreen(
    onSave: (String) -> Unit,
    onSkip: () -> Unit,
) {
    val dimens = NSDimens.current
    var epgInput by remember { mutableStateOf("") }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxSize()
            .background(NSColors.bg)
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(dimens.spacing.xxl),
    ) {
        Spacer(modifier = Modifier.weight(1f))

        StepContainer {
            StepIcon("📺")
            Text(
                text  = "Add a TV Guide",
                style = NSType.display(),
                color = NSColors.text,
            )
            Text(
                text  = "A TV Guide shows upcoming match times and what's on.\nYour server didn't return one automatically.",
                style = NSType.body(),
                color = NSColors.text3,
            )
            NSTextField(
                value         = epgInput,
                onValueChange = { epgInput = it },
                placeholder   = "http://192.168.1.42:8888/epg.xml",
            )
            SheetActionButton(
                label     = "Use IPTV-org guide",
                isPrimary = false,
                enabled   = true,
                onClick   = { epgInput = IPTV_ORG_EPG },
                modifier  = Modifier.fillMaxWidth(),
            )
            StepButtons(
                skipLabel      = "Skip for now",
                primaryLabel   = "Add Guide",
                primaryEnabled = epgInput.isNotBlank(),
                onSkip         = onSkip,
                onPrimary      = { onSave(epgInput) },
            )
        }

        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun PlaylistScreen(
    connectionState: OnboardingConnectionState,
    onSourceAdded: (url: String) -> Unit,
    onAddAndContinue: (url: String, foundEpgUrl: String?) -> Unit,
    onSkip: () -> Unit,
    onProbePlaylist: suspend (String) -> String?,
) {
    val dimens        = NSDimens.current
    var playlistInput by remember { mutableStateOf("") }
    var isProbing     by remember { mutableStateOf(false) }
    var foundEpg      by remember { mutableStateOf<String?>(null) }
    val scope         = rememberCoroutineScope()
    var isAdded by remember { mutableStateOf(false) }

    // Reset EPG hint when URL changes
    LaunchedEffect(playlistInput) { foundEpg = null }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxSize()
            .background(NSColors.bg)
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(dimens.spacing.xxl),
    ) {
        Spacer(modifier = Modifier.weight(1f))

        StepContainer {
            StepIcon("📋")
            Text(
                text  = "Add a Playlist Source",
                style = NSType.display(),
                color = NSColors.text,
            )
            Text(
                text  = "Your server playlist was added automatically.\nWant to add another M3U source?",
                style = NSType.body(),
                color = NSColors.text3,
            )
            NSTextField(
                value         = playlistInput,
                onValueChange = { playlistInput = it },
                placeholder   = "http://192.168.1.42:8888/playlist.m3u",
            )

            // EPG discovery hint
            AnimatedVisibility(visible = foundEpg != null) {
                Text(
                    text  = "✓ TV Guide found in this playlist — will be added automatically",
                    style = NSType.monoSmall(),
                    color = NSColors.accent,
                )
            }
            AnimatedVisibility(visible = isProbing) {
                Text(
                    text  = "Checking for TV Guide…",
                    style = NSType.monoSmall(),
                    color = NSColors.text3,
                )
            }

            // Phase 1 — add playlist
            AnimatedVisibility(visible = foundEpg == null && !isAdded) {
                StepButtons(
                    skipLabel      = "Skip for now",
                    primaryLabel   = if (isProbing) "Checking…" else "Add Source",
                    primaryEnabled = playlistInput.isNotBlank() && !isProbing,
                    onSkip         = onSkip,
                    onPrimary      = {
                        scope.launch {
                            isProbing = true
                            val epgUrl = onProbePlaylist(playlistInput.trim())
                            foundEpg  = epgUrl
                            isProbing = false
                            isAdded   = true
                            onSourceAdded(playlistInput.trim())
                        }
                    },
                )
            }

            // Phase 2 — show result + let user continue
            AnimatedVisibility(visible = isAdded, enter = fadeIn(tween(300))) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(dimens.spacing.md),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text  = "✓ Playlist added",
                        style = NSType.bodyMedium(),
                        color = NSColors.accent,
                    )
                    AnimatedVisibility(visible = foundEpg != null) {
                        Text(
                            text  = "✓ TV Guide found — will be added automatically",
                            style = NSType.bodyMedium(),
                            color = NSColors.accent,
                        )
                    }
                    AnimatedVisibility(visible = foundEpg == null) {
                        Text(
                            text  = "No TV Guide found in this playlist",
                            style = NSType.monoSmall(),
                            color = NSColors.text3,
                        )
                    }
                    SheetActionButton(
                        label     = "Continue",
                        isPrimary = true,
                        enabled   = true,
                        onClick   = { onAddAndContinue(playlistInput.trim(), foundEpg) },
                        modifier  = Modifier.fillMaxWidth(),
                    )
                }
            }

        }

        Spacer(modifier = Modifier.weight(1f))
    }
}