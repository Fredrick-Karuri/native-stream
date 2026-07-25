// app/src/main/java/com/nativestream/android/ui/components/NSTextField.kt
//
// Matches the NSTextField used across AddChannelSheet and PlayURLSheet.

package com.nativestream.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import com.nativestream.android.ui.theme.NSColors
import com.nativestream.android.ui.theme.NSDimens
import com.nativestream.android.ui.theme.NSType

@Composable
fun NSTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
) {
    val dimens = NSDimens.current
    BasicTextField(
        value         = value,
        onValueChange = onValueChange,
        singleLine    = singleLine,
        textStyle     = NSType.body().copy(color = NSColors.text),
        cursorBrush   = SolidColor(NSColors.accent),
        modifier      = modifier.fillMaxWidth(),
        decorationBox = { innerField ->
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(dimens.radius.md))
                    .background(NSColors.surface2)
                    .border(0.5.dp, NSColors.border2, RoundedCornerShape(dimens.radius.md))
                    .padding(horizontal = dimens.spacing.md, vertical = dimens.spacing.sm),
            ) {
                if (value.isEmpty()) {
                    Text(text = placeholder, style = NSType.body(), color = NSColors.text3)
                }
                innerField()
            }
        },
    )
}