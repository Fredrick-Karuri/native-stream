// app/src/main/java/com/nativestream/android/ui/screens/player/ChannelLogoSquare.kt
//
// Small square channel logo with Coil load + initials fallback.

package com.nativestream.android.ui.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import coil.compose.SubcomposeAsyncImage
import com.nativestream.android.domain.model.Channel
import com.nativestream.android.ui.theme.NSColors
import com.nativestream.android.ui.theme.NSDimens
import com.nativestream.android.ui.theme.NSType

@Composable
fun ChannelLogoSquare(
    channel: Channel,
    size: Dp         = NSDimens.current.channel.logoSquareSmall,
    cornerRadius: Dp = NSDimens.current.radius.sm,
    modifier: Modifier = Modifier,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(cornerRadius))
            .background(NSColors.surface3)
            .border(0.5.dp, NSColors.border2, RoundedCornerShape(cornerRadius)),
    ) {
        if (!channel.logoUrl.isNullOrBlank()) {
            SubcomposeAsyncImage(
                model              = channel.logoUrl,
                contentDescription = channel.name,
                contentScale       = ContentScale.Fit,
                error              = { InitialsText(channel.name) },
                loading            = { InitialsText(channel.name) },
            )
        } else {
            InitialsText(channel.name)
        }
    }
}

@Composable
private fun InitialsText(name: String) {
    Text(
        text  = name.take(3).uppercase(),
        style = NSType.label(),
        color = NSColors.text3,
    )
}

// Needed for .border extension in Dp context
private val androidx.compose.ui.unit.Dp.dp get() = this