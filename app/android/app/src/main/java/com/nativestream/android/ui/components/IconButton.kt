package com.nativestream.android.ui.components

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
import com.nativestream.android.ui.theme.NSDimens

private val ICON_BUTTON_RADIUS            = 50 // percent — circular

/**
 * Small circular icon button.
 */

@Composable
fun IconButton(
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