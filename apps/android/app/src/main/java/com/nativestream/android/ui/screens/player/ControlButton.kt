package com.nativestream.android.ui.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import com.nativestream.android.ui.theme.NSColors

private val CTRL_RADIUS         = 50   // percent — circular
@Composable
fun ControlButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    size: Dp,
    isPrimary: Boolean = false,
    iconSize: Dp = CTRL_ICON_SIZE,
) {
    val background = if (isPrimary) NSColors.accent else Color.White.copy(alpha = 0.12f)
    val tint       = if (isPrimary) NSColors.bg     else Color.White.copy(alpha = 0.85f)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(percent = CTRL_RADIUS))
            .background(background)
            .clickable(onClick = onClick),
    ) {
        Icon(
            imageVector        = icon,
            contentDescription = contentDescription,
            tint               = tint,
            modifier           = Modifier.size(iconSize),
        )
    }
}