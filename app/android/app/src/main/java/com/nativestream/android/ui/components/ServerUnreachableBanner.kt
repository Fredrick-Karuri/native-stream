package com.nativestream.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.nativestream.android.ui.theme.NSColors
import com.nativestream.android.ui.theme.NSDimens
import com.nativestream.android.ui.theme.NSType


@Composable
fun ServerUnreachableBanner(onScan: () -> Unit) {
    val dimens = NSDimens.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .background(NSColors.amber.copy(alpha = 0.15f))
            .padding(horizontal = dimens.spacing.md, vertical = dimens.spacing.sm),
    ) {
        Text(
            text  = "Can't reach server",
            style = NSType.bodyMedium(),
            color = NSColors.amber,
        )
        Text(
            text  = "Scan again",
            style = NSType.bodyMedium(),
            color = NSColors.accent,
            modifier = Modifier.clickable { onScan() },
        )
    }
}