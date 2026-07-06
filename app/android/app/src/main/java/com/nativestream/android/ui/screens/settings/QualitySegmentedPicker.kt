// app/src/main/java/com/nativestream/android/ui/screens/settings/QualitySegmentedPicker.kt

package com.nativestream.android.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.nativestream.android.data.local.StreamQuality
import com.nativestream.android.ui.theme.NSColors
import com.nativestream.android.ui.theme.NSDimens
import com.nativestream.android.ui.theme.NSType

@Composable
fun QualitySegmentedPicker(
    selected: StreamQuality,
    onSelect: (StreamQuality) -> Unit,
) {
    val dimens = NSDimens.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(dimens.radius.sm))
            .background(NSColors.bg)
            .border(0.5.dp, NSColors.border, RoundedCornerShape(dimens.radius.sm))
            .padding(2.dp),
    ) {
        StreamQuality.entries.forEach { quality ->
            val isActive = selected == quality
            Text(
                text  = quality.label,
                style = NSType.caption(),
                color = if (isActive) NSColors.accent2 else NSColors.text3,
                modifier = Modifier
                    .clip(RoundedCornerShape(dimens.radius.sm - 2.dp))
                    .background(if (isActive) NSColors.accentGlow else Color.Transparent)
                    .border(
                        0.5.dp,
                        if (isActive) NSColors.accentBorder else Color.Transparent,
                        RoundedCornerShape(dimens.radius.sm - 2.dp),
                    )
                    .clickable { onSelect(quality) }
                    .padding(horizontal = dimens.spacing.sm, vertical = 4.dp),
            )
        }
    }
}