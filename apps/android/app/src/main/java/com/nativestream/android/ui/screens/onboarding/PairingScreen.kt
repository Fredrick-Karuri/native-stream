// app/src/main/java/com/nativestream/android/ui/screens/onboarding/PairingScreen.kt
//
// Shown during onboarding once the server connection succeeds
// and a pairing session has started. Renders the human-facing code and
// reflects PairingViewModel's state — waiting, approved, expired, denied,
// or failed — with a retry action for anything but the happy path.

package com.nativestream.android.ui.screens.onboarding

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nativestream.android.ui.viewmodel.PairingState

@Composable
fun PairingScreen(
    pairingState: PairingState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Pair this device",
            style = MaterialTheme.typography.headlineMedium,
        )

        Spacer(modifier = Modifier.height(24.dp))

        when (pairingState) {
            is PairingState.Idle, is PairingState.Starting -> {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Preparing device pairing…",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            is PairingState.WaitingForApproval -> {
                Text(
                    text = "Approve this device from your server's admin page",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = pairingState.code,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 40.sp,
                    letterSpacing = 6.sp,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            is PairingState.Approved -> {
                Text(
                    text = "✓ Device paired",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            is PairingState.Expired -> RetryBlock(
                message = "Pairing code expired",
                onRetry = onRetry,
            )

            is PairingState.Denied -> RetryBlock(
                message = "Pairing was denied",
                onRetry = onRetry,
            )

            is PairingState.Failed -> RetryBlock(
                message = pairingState.message,
                onRetry = onRetry,
            )
        }
    }
}

@Composable
private fun RetryBlock(message: String, onRetry: () -> Unit) {
    Text(
        text = "✗ $message",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
        textAlign = TextAlign.Center,
    )
    Spacer(modifier = Modifier.height(16.dp))
    Button(onClick = onRetry) {
        Text("Retry")
    }
}