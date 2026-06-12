package com.nativestream.android.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nativestream.android.ui.theme.NSColors
import com.nativestream.android.ui.theme.NSDimens
import com.nativestream.android.ui.theme.NSType

@Composable
fun ServerHealthCard(
    serverUrl: String,
    serverReachable: Boolean,
    onScan: () -> Unit = {},
) {
    val dimens = NSDimens.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(dimens.radius.xl))
            .background(NSColors.surface2)
            .border(0.5.dp, NSColors.border, RoundedCornerShape(dimens.radius.xl))
            .padding(dimens.spacing.md),
    ) {
        NSHealthDot(score = if (serverReachable) 1.0 else 0.0)
        Spacer(modifier = Modifier.width(dimens.spacing.sm))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text  = if (serverReachable) "Server connected" else "Server unreachable",
                style = NSType.bodyMedium(),
                color = NSColors.text,
            )
            Text(
                text     = serverUrl.removePrefix("http://"),
                style    = NSType.monoSmall(),
                color    = NSColors.text3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (!serverReachable) {
            Text(
                text     = "Scan again",
                style    = NSType.bodyMedium(),
                color    = NSColors.accent,
                modifier = Modifier.clickable { onScan() },
            )
        }
    }
}
