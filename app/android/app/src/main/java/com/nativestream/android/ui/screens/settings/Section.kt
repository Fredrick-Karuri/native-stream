package com.nativestream.android.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
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

private val SECTION_LABEL_BOTTOM = 6.dp

@Composable
fun SettingsSection(label: String, content: @Composable () -> Unit) {
    val dimens = NSDimens.current
    Column {
        Text(
            text     = label.uppercase(),
            style    = NSType.label(),
            color    = NSColors.text3,
            modifier = Modifier.padding(
                horizontal = dimens.spacing.xs,
                vertical   = SECTION_LABEL_BOTTOM,
            ),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(dimens.radius.xl))
                .background(NSColors.surface2)
                .border(0.5.dp, NSColors.border, RoundedCornerShape(dimens.radius.xl)),
        ) {
            content()
        }
    }
}