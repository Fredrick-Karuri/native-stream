// app/src/main/java/com/nativestream/android/ui/screens/settings/SettingsComponents.kt
//
// SectionTitle, SettingsRow, AddButton, NSHealthDot, NSToggle.

package com.nativestream.android.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.nativestream.android.ui.theme.NSColors
import com.nativestream.android.ui.theme.NSDimens
import com.nativestream.android.ui.theme.NSType

private val HEALTH_DOT_SIZE = 6.dp
private val ROW_ICON_SIZE        = 32.dp
private val ROW_ICON_RADIUS      = 8.dp
private val ROW_ICON_INNER_SIZE  = 16.dp

@Composable
fun NSHealthDot(score: Double, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(HEALTH_DOT_SIZE)
            .clip(CircleShape)
            .background(NSColors.healthColor(score)),
    )
}

@Composable
fun NSToggle(checked: Boolean, onCheckedChange: (Boolean) -> Unit, enabled: Boolean = true) {
    Switch(
        checked         = checked,
        onCheckedChange = onCheckedChange,
        enabled         = enabled,
        colors          = SwitchDefaults.colors(
            checkedThumbColor       = Color.White,
            checkedTrackColor       = NSColors.accent,
            uncheckedThumbColor     = Color.White,
            uncheckedTrackColor     = NSColors.surface3,
            uncheckedBorderColor    = NSColors.border2,
        ),
    )
}


@Composable
fun AddSourceRow(onClick: () -> Unit) {
    val dimens = NSDimens.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(dimens.spacing.sm)
            .clip(RoundedCornerShape(dimens.radius.md))
            .border(
                0.5.dp,
                NSColors.border2,
                RoundedCornerShape(dimens.radius.md),
            )
            .padding(vertical = dimens.spacing.sm),
    ) {
        Text(text = "+ Add source", style = NSType.caption(), color = NSColors.text3)
    }
}

@Composable
fun SettingsDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(0.5.dp)
            .background(NSColors.border),
    )
}

@Composable
fun RowIcon(background: Color, tint: Color, icon: ImageVector) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(ROW_ICON_SIZE)
            .clip(RoundedCornerShape(ROW_ICON_RADIUS))
            .background(background),
    ) {
        Icon(
            imageVector        = icon,
            contentDescription = null,
            tint               = tint,
            modifier           = Modifier.size(ROW_ICON_INNER_SIZE),
        )
    }
}