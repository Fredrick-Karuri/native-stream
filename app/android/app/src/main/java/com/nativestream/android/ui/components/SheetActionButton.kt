package com.nativestream.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.nativestream.android.ui.theme.NSColors
import com.nativestream.android.ui.theme.NSDimens
import com.nativestream.android.ui.theme.NSType

@Composable
internal fun SheetActionButton(
    label: String,
    isPrimary: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier) {
    val dimens      = NSDimens.current
    val background  = if (isPrimary && enabled) NSColors.accentGlow else NSColors.surface3
    val borderColor = if (isPrimary && enabled) NSColors.accentBorder else NSColors.border2
    val textColor   = when {
        isPrimary && enabled  -> NSColors.accent
        isPrimary && !enabled -> NSColors.text3
        else                  -> NSColors.text2
    }
    Text(
        text     = label,
        style    = NSType.captionMedium(),
        color    = textColor,
        modifier = modifier
            .clip(RoundedCornerShape(dimens.radius.md))
            .background(background)
            .border(0.5.dp, borderColor, RoundedCornerShape(dimens.radius.md))
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = dimens.spacing.md, vertical = 6.dp),
    )
}