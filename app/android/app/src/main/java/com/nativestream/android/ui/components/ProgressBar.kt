package com.nativestream.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import com.nativestream.android.ui.theme.NSColors
import com.nativestream.android.ui.theme.NSDimens


/**
 * Thin horizontal progress bar.
 * [value] is clamped 0–1. Height defaults to the design-system channel progress height.
 */

@Composable
fun ProgressBar(
    value: Float,
    modifier: Modifier = Modifier,
    height: Dp = NSDimens.current.channel.progressHeight,
    trackColor: Color  = Color.White.copy(alpha = 0.15f),
    fillColor: Color   = NSColors.accent,
) {
    val clamped = value.coerceIn(0f, 1f)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(percent = 50))
            .background(trackColor),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(clamped)
                .height(height)
                .clip(RoundedCornerShape(percent = 50))
                .background(fillColor),
        )
    }
}
