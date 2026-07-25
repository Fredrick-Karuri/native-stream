package com.nativestream.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.nativestream.android.ui.theme.NSColors
import com.nativestream.android.ui.theme.NSDimens
import com.nativestream.android.ui.theme.NSType


@Composable
fun NSChip(
    label: String,
    isActive: Boolean,
    onClick: () -> Unit,
    icon: ImageVector? = null,
) {
    val dimens = NSDimens.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.spacing.xs),
        modifier = Modifier
            .clip(RoundedCornerShape(dimens.radius.pill))
            .background(if (isActive) NSColors.accentGlow else NSColors.surface2)
            .border(0.5.dp, if (isActive) NSColors.accentBorder else NSColors.border, RoundedCornerShape(dimens.radius.pill))
            .clickable(onClick = onClick)
            .padding(horizontal = dimens.spacing.lg, vertical = dimens.spacing.sm),
    ) {
        if (icon != null) {
            Icon(
                imageVector        = icon,
                contentDescription = null,
                tint               = if (isActive) NSColors.accent2 else NSColors.text3,
                modifier           = Modifier.size(12.dp),
            )
        }
        Text(
            text  = label,
            style = NSType.caption(),
            color = if (isActive) NSColors.accent2 else NSColors.text3,
        )
    }
}