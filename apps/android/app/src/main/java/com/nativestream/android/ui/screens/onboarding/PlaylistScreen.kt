package com.nativestream.android.ui.screens.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.nativestream.android.ui.components.NSTextField
import com.nativestream.android.ui.components.SheetActionButton
import com.nativestream.android.ui.theme.NSColors
import com.nativestream.android.ui.theme.NSDimens
import com.nativestream.android.ui.theme.NSType
import kotlinx.coroutines.launch

@Composable
fun PlaylistScreen(
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
            AnimatedVisibility(visible = foundEpg != null && !isAdded) {
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