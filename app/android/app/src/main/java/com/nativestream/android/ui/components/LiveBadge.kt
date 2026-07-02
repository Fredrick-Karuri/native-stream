package com.nativestream.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nativestream.android.ui.theme.NSColors

private val LIVE_BADGE_HORIZONTAL_PADDING = 5.dp
private val LIVE_BADGE_VERTICAL_PADDING   = 2.dp
private val LIVE_BADGE_RADIUS             = 3.dp
private val LIVE_BADGE_FONT_SIZE          = 7.sp

@Composable
fun LiveBadge(
    isLive: Boolean,
    modifier: Modifier = Modifier,
) {
    if (!isLive) return
    Text(
        text     = "LIVE",
        color    = Color.White,
        fontSize = LIVE_BADGE_FONT_SIZE,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.06.sp,
        modifier = modifier
            .clip(RoundedCornerShape(LIVE_BADGE_RADIUS))
            .background(NSColors.live)
            .padding(horizontal = LIVE_BADGE_HORIZONTAL_PADDING, vertical = LIVE_BADGE_VERTICAL_PADDING),
    )
}