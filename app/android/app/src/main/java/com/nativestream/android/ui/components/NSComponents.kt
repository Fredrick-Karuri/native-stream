// app/src/main/java/com/nativestream/android/ui/components/NSComponents.kt
//
// NS Design System — Shared UI components (AND-009)
// NSLiveBadge, NSProgressBar, NSIconButton — reused across mini player,
// channel cards, and programme rows. Mirrors Swift counterparts.

package com.nativestream.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nativestream.android.ui.theme.NSColors
import com.nativestream.android.ui.theme.NSDimens

private val LIVE_BADGE_HORIZONTAL_PADDING = 5.dp
private val LIVE_BADGE_VERTICAL_PADDING   = 2.dp
private val LIVE_BADGE_RADIUS             = 3.dp
private val LIVE_BADGE_FONT_SIZE          = 7.sp

private val ICON_BUTTON_RADIUS            = 50 // percent — circular

/**
 * Red "LIVE" badge. Hidden (no space reserved) when [isLive] is false.
 * Mirrors NSLiveBadge from SwiftUI.
 */
@Composable
fun NSLiveBadge(
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

/**
 * Thin horizontal progress bar.
 * [value] is clamped 0–1. Height defaults to the design-system channel progress height.
 */
@Composable
fun NSProgressBar(
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

/**
 * Small circular icon button.
 * [isDark] true → dark semi-transparent background (player overlay use).
 * Mirrors NSIconButton from SwiftUI.
 */
@Composable
fun NSIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    size: Dp          = NSDimens.current.iconButton.medium,
    isDark: Boolean   = false,
    modifier: Modifier = Modifier,
) {
    val background = if (isDark) Color.Black.copy(alpha = 0.40f) else NSColors.surface2
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(percent = ICON_BUTTON_RADIUS))
            .background(background)
            .clickable(onClick = onClick),
    ) {
        Icon(
            imageVector        = icon,
            contentDescription = contentDescription,
            tint               = NSColors.text2,
            modifier           = Modifier.size(size * 0.55f),
        )
    }
}