// app/src/main/java/com/nativestream/android/ui/components/ServerReconnectBanner.kt
//
// Slim banner shown when ServerHealthMonitor discovers a new server URL.
// User taps "Connect" to confirm or dismisses it. Same visual weight as OfflineBanner.

package com.nativestream.android.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.regular.WifiHigh
import com.nativestream.android.ui.theme.NSColors
import com.nativestream.android.ui.theme.NSDimens
import com.nativestream.android.ui.theme.NSType

@Composable
fun ServerReconnectBanner(
    discoveredUrl: String?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible  = discoveredUrl != null,
        enter    = slideInVertically { -it },
        exit     = slideOutVertically { -it },
        modifier = modifier,
    ) {
        val dimens = NSDimens.current
        val host   = discoveredUrl
            ?.removePrefix("http://")
            ?.removePrefix("https://")
            ?: return@AnimatedVisibility

        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.spacing.sm),
            modifier = Modifier
                .fillMaxWidth()
                .background(NSColors.accentGlow)
                .padding(horizontal = dimens.spacing.md, vertical = dimens.spacing.sm),
        ) {
            Icon(
                imageVector        = PhosphorIcons.Regular.WifiHigh,
                contentDescription = null,
                tint               = NSColors.accent,
                modifier           = Modifier.size(12.dp),
            )
            Text(
                text     = "Server found at $host",
                style    = NSType.caption(),
                color    = NSColors.accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(dimens.spacing.xs))
            SheetActionButton(
                label     = "Connect",
                isPrimary = true,
                enabled   = true,
                onClick   = onConfirm,
            )
            SheetActionButton(
                label     = "Dismiss",
                isPrimary = false,
                enabled   = true,
                onClick   = onDismiss,
            )
        }
    }
}