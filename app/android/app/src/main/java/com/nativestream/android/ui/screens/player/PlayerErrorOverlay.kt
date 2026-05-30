// app/src/main/java/com/nativestream/android/ui/screens/player/PlayerErrorOverlay.kt
//
// Shown after MAX_RETRY_ATTEMPTS failures. Manual Retry button re-attempts.

package com.nativestream.android.ui.screens.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.nativestream.android.ui.components.NSTextField
import com.nativestream.android.ui.theme.NSColors
import com.nativestream.android.ui.theme.NSDimens
import com.nativestream.android.ui.theme.NSType
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip

@Composable
fun PlayerErrorOverlay(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = NSDimens.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier.padding(dimens.player.errorPadding),
    ) {
        Icon(
            imageVector        = Icons.Default.Warning,
            contentDescription = "Playback error",
            tint               = NSColors.live,
            modifier           = Modifier.size(dimens.player.errorIconSize),
        )
        Spacer(modifier = Modifier.height(dimens.spacing.md))
        Text(text = message, style = NSType.body(), color = Color.White)
        Spacer(modifier = Modifier.height(dimens.spacing.lg))
        Text(
            text     = "Retry",
            style    = NSType.captionMedium(),
            color    = NSColors.accent,
            modifier = Modifier
                .clip(RoundedCornerShape(dimens.radius.md))
                .background(NSColors.accentGlow)
                .border(0.5.dp, NSColors.accentBorder, RoundedCornerShape(dimens.radius.md))
                .clickable(onClick = onRetry)
                .padding(horizontal = dimens.spacing.lg, vertical = dimens.spacing.sm),
        )
    }
}