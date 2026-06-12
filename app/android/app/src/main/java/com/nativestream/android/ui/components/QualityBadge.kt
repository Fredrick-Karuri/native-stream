package com.nativestream.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.nativestream.android.ui.theme.NSType

private val LIVE_BADGE_RADIUS   = 4.dp
private val QUALITY_BADGE_H_PAD = 5.dp
private val QUALITY_BADGE_V_PAD = 2.dp

@Composable
fun QualityBadge(label: String) {
    Text(
        text  = label,
        style = NSType.monoSmall(),
        color = Color.White.copy(alpha = 0.7f),
        modifier = Modifier
            .clip(RoundedCornerShape(LIVE_BADGE_RADIUS))
            .background(Color.White.copy(alpha = 0.15f))
            .padding(horizontal = QUALITY_BADGE_H_PAD, vertical = QUALITY_BADGE_V_PAD),
    )
}