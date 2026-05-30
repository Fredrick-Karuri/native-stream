// app/src/main/java/com/nativestream/android/ui/components/NSGroupHeader.kt
//
// NS Design System — Group section header with count badge (AND-010)
// Mirrors NSGroupHeader + NSPulseDot from SwiftUI.

package com.nativestream.android.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
//import androidx.compose.ui.text.style.TextTransform
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nativestream.android.ui.theme.NSColors
import com.nativestream.android.ui.theme.NSDimens
import com.nativestream.android.ui.theme.NSType

private val COUNT_BADGE_RADIUS    = 8.dp
private val COUNT_BADGE_H_PADDING = 6.dp
private val COUNT_BADGE_V_PADDING = 1.dp
private val PULSE_DOT_SIZE        = 6.dp
private val LABEL_LETTER_SPACING  = 0.05.sp

/** Uppercase section label + count badge. */
@Composable
fun NSGroupHeader(
    title: String,
    count: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        Text(
            text          = title.uppercase(),
            style         = NSType.label(),
            color         = NSColors.text2,
            letterSpacing = LABEL_LETTER_SPACING,
        )
        Spacer(modifier = Modifier.width(NSDimens.current.spacing.sm))
        Text(
            text  = count.toString(),
            style = NSType.caption(),
            color = NSColors.text3,
            modifier = Modifier
                .clip(RoundedCornerShape(COUNT_BADGE_RADIUS))
                .background(NSColors.surface2)
                .padding(horizontal = COUNT_BADGE_H_PADDING, vertical = COUNT_BADGE_V_PADDING),
        )
    }
}

/** Pulsing red dot used beside the live matches section header. */
@Composable
fun NSPulseDot(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue  = 0.3f,
        animationSpec = infiniteRepeatable(
            animation  = tween(durationMillis = 800),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseAlpha",
    )
    Box(
        modifier = modifier
            .size(PULSE_DOT_SIZE)
            .clip(CircleShape)
            .background(NSColors.live)
            .graphicsLayer { this.alpha = alpha },
    )
}