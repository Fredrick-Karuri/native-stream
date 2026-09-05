// app/src/main/java/com/nativestream/android/ui/screens/onboarding/PairingScreen.kt
//
// Shown during onboarding once the server connection succeeds
// and a pairing session has started. Renders the human-facing code and
// reflects PairingViewModel's state — waiting, approved, expired, denied,
// or failed — with a retry action for anything but the happy path.

package com.nativestream.android.ui.screens.onboarding


import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.nativestream.android.ui.viewmodel.PairingState
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.regular.ArrowSquareOut
import com.adamglin.phosphoricons.regular.Copy

@Composable
fun PairingScreen(
    pairingState: PairingState,
    serverUrl: String,
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
                Spacer(modifier = Modifier.height(16.dp))
                AdminUrlHint(serverUrl = serverUrl)
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

@Composable
private fun AdminUrlHint(serverUrl: String) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val adminUrl = serverUrl.trimEnd('/') + "/admin"

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "Approve at:",
            style = MaterialTheme.typography.labelSmall,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = adminUrl,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
            IconButton(onClick = {
                clipboardManager.setText(AnnotatedString(adminUrl))
            }) {
                Icon(
                    imageVector = PhosphorIcons.Regular.Copy,
                    contentDescription = "Copy admin URL",
                    modifier = Modifier.height(16.dp),
                )
            }
            IconButton(onClick = {
                val intent = Intent(Intent.ACTION_VIEW, adminUrl.toUri())
                context.startActivity(intent)
            }) {
                Icon(
                    imageVector = PhosphorIcons.Regular.ArrowSquareOut,
                    contentDescription = "Open in browser",
                    modifier = Modifier.height(16.dp),
                )
            }
        }
    }
}