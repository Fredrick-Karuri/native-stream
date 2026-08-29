package com.nativestream.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import com.nativestream.android.ui.screens.onboarding.PairingScreen
import com.nativestream.android.ui.theme.NSColors
import com.nativestream.android.ui.viewmodel.PairingState
import com.nativestream.android.ui.viewmodel.PairingViewModel
import com.nativestream.android.ui.viewmodel.SettingsViewModel

/**
 * Re-pairing entry point shown from Settings (Server section) — a compact
 * dialog card, not a full-screen takeover.
 *
 * Owns its own Hilt-injected PairingViewModel — a fresh pairing session
 * each time this dialog opens, independent of onboarding's.
 */
@Composable
fun RepairDeviceDialog(onDismiss: () -> Unit) {
    val pairingViewModel: PairingViewModel = hiltViewModel()
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val pairingState by pairingViewModel.state.collectAsState()
    val serverUrl by settingsViewModel.serverUrl.collectAsState()

    LaunchedEffect(Unit) { pairingViewModel.start() }

    LaunchedEffect(pairingState) {
        if (pairingState is PairingState.Approved) {
            kotlinx.coroutines.delay(1000)
            onDismiss()
        }
    }

    DisposableEffect(Unit) {
        onDispose { pairingViewModel.stop() }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .heightIn(min = 280.dp, max = 420.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(NSColors.surface)
                .padding(4.dp),
        ) {
            PairingScreen(
                pairingState = pairingState,
                serverUrl = serverUrl,
                onRetry = { pairingViewModel.start() },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}