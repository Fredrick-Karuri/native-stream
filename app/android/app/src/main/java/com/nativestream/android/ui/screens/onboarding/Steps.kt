package com.nativestream.android.ui.screens.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import com.nativestream.android.ui.components.NSTextField
import com.nativestream.android.ui.components.SheetActionButton
import com.nativestream.android.ui.theme.NSColors
import com.nativestream.android.ui.theme.NSDimens
import com.nativestream.android.ui.theme.NSType


// ── Step 1: Server check ──────────────────────────────────────────────────────

@Composable
 fun ServerCheckStep(
    serverUrl: String,
    isChecking: Boolean,
    checkError: String?,
    scanning: Boolean,
    discoveredUrl: String?,
    onServerUrlChange: (String) -> Unit,
    onScan: () -> Unit,
    onConfirmDiscovered: (String) -> Unit,
    onSkip: () -> Unit,
    onCheck: () -> Unit,
) {
    StepContainer {
        StepIcon("🖥")
        Text(text = "Welcome to NativeStream", style = NSType.display(), color = NSColors.text)

        Text(
            text  = if (scanning) "Scanning your network…\nOr enter the LAN IP manually."
            else "Enter your server's LAN IP below.",
            style = NSType.body(),
            color = NSColors.text3,
        )
        discoveredUrl?.let { found ->
            Text(
                text  = "Found: $found",
                style = NSType.monoSmall(),
                color = NSColors.accent,
            )
            SheetActionButton(
                label     = "Use $found",
                isPrimary = false,
                enabled   = true,
                onClick   = { onConfirmDiscovered(found) },
            )
        }
        NSTextField(
            value         = serverUrl,
            onValueChange = onServerUrlChange,
            placeholder   = "http://192.168.1.42:8888",
        )
        checkError?.let {
            Text(text = it, style = NSType.monoSmall(), color = NSColors.live)
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(NSDimens.current.spacing.md),
            modifier = Modifier.fillMaxWidth(),
        ) {
            SheetActionButton(
                label     = if (scanning) "Scanning…" else "Scan network",
                isPrimary = false,
                enabled   = !scanning,
                onClick   = onScan,
                modifier  = Modifier.weight(1f),
            )
            SheetActionButton(
                label     = if (isChecking) "Checking…" else "Check Connection",
                isPrimary = true,
                enabled   = !isChecking,
                onClick   = onCheck,
                modifier  = Modifier.weight(1f),
            )
        }
    }
}

// ── Step 2: Channel setup ─────────────────────────────────────────────────────

@Composable
fun ChannelSetupStep(
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
fun EpgSetupStep(
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
fun CompleteStep(onStartWatching: () -> Unit) {
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