// app/src/main/java/com/nativestream/android/ui/screens/settings/SettingsComponents.kt
//
// SectionTitle, SettingsRow, AddButton, NSHealthDot, NSToggle.

package com.nativestream.android.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nativestream.android.ui.components.NSTextField
import com.nativestream.android.ui.theme.NSColors
import com.nativestream.android.ui.theme.NSDimens
import com.nativestream.android.ui.theme.NSType

private val HEALTH_DOT_SIZE = 6.dp

@Composable
fun SectionTitle(title: String) {
    Text(
        text          = title.uppercase(),
        style         = NSType.label(),
        color         = NSColors.text3,
        letterSpacing = 1.0.sp,
    )
}

@Composable
fun SettingsRow(
    title: String,
    subtitle: String = "",
    modifier: Modifier = Modifier,
    control: @Composable () -> Unit,
) {
    val dimens = NSDimens.current
    Row(
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.spacing.lg),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(dimens.radius.lg))
            .background(NSColors.surface2)
            .border(0.5.dp, NSColors.border, RoundedCornerShape(dimens.radius.lg))
            .padding(dimens.spacing.md),
    ) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(dimens.spacing.xxs),
        ) {
            Text(text = title, style = NSType.captionMedium(), color = NSColors.text)
            if (subtitle.isNotEmpty()) {
                Text(
                    text     = subtitle,
                    style    = NSType.monoSmall(),
                    color    = NSColors.text3,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        control()
    }
}

@Composable
fun AddButton(label: String, onClick: () -> Unit) {
    val dimens = NSDimens.current
    Box(
        contentAlignment = Alignment.CenterStart,
        modifier = Modifier
            .fillMaxWidth()
            .height(dimens.helpers.addButtonHeight)
            .clip(RoundedCornerShape(dimens.radius.lg))
            .border(
                width = 0.5.dp,
                color = NSColors.border2,
                shape = RoundedCornerShape(dimens.radius.lg),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = dimens.spacing.md),
    ) {
        Text(text = label, style = NSType.caption(), color = NSColors.text3)
    }
}

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