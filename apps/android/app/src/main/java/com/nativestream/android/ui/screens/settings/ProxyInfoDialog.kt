package com.nativestream.android.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.nativestream.android.ui.theme.NSColors
import com.nativestream.android.ui.theme.NSDimens
import com.nativestream.android.ui.theme.NSType

/**
 * Explains what "Fix Protected Streams" (proxy) does, shown from an info
 * icon next to the toggle rather than as permanent inline text below it —
 * most users don't need the full explanation every time they open
 * Settings, only the ones deciding whether to turn it on.
 */
@Composable
fun ProxyInfoDialog(proxyEnabled: Boolean, onDismiss: () -> Unit) {
    val dimens = NSDimens.current

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NSColors.surface2,
        title = { Text("Fix Protected Streams", style = NSType.heading(), color = NSColors.text) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(dimens.spacing.sm)) {
                Text(
                    "Some streams block playback unless specific access headers are sent. " +
                            "Enable this if channels show a blank screen or fail to load.",
                    style = NSType.caption(),
                    color = NSColors.text3,
                )
                Text(
                    if (proxyEnabled)
                        "Currently active — streams are routing through your server with custom headers."
                    else
                        "Most streams work without this; only enable it if you're seeing playback failures.",
                    style = NSType.caption(),
                    color = if (proxyEnabled) NSColors.accent else NSColors.text3,
                )
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text("Got it", color = NSColors.accent)
            }
        },
    )
}