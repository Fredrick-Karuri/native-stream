package com.nativestream.android.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import com.nativestream.android.ui.theme.NSColors
import com.nativestream.android.ui.theme.NSDimens
import com.nativestream.android.ui.theme.NSType

@Composable
fun ProxyHint(proxyEnabled: Boolean) {
    val dimens = NSDimens.current
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(dimens.spacing.xs),
    ) {
        Text(
            text  = if (proxyEnabled) "✓" else "ℹ",
            style = NSType.caption(),
            color = if (proxyEnabled) NSColors.accent else NSColors.text3,
        )
        Text(
            text = if (proxyEnabled)
                "Proxy active — streams are routing through your server with custom headers."
            else
                "Most streams work without this. Enable it only if you're seeing blank screens or playback failures on specific channels.",
            style = NSType.caption(),
            color = if (proxyEnabled) NSColors.accent else NSColors.text3,
        )
    }
}