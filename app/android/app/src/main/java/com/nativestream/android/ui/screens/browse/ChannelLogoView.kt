// app/src/main/java/com/nativestream/android/ui/screens/browse/ChannelLogoView.kt
//
// Channel Logo View
// Loads channel logo via Coil. Falls back to initials placeholder on error or
// missing URL.

package com.nativestream.android.ui.screens.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.nativestream.android.domain.model.Channel
import com.nativestream.android.ui.theme.NSColors
import com.nativestream.android.ui.theme.NSDimens
import com.nativestream.android.ui.theme.NSType

@Composable
fun ChannelLogoView(
    channel: Channel,
    borderColor: Color = NSColors.border,
    modifier: Modifier = Modifier,
) {
    val dimens = NSDimens.current

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(dimens.radius.lg))
            .background(NSColors.surface2)
            .border(0.5.dp, borderColor, RoundedCornerShape(dimens.radius.lg)),
    ) {
        if (!channel.logoUrl.isNullOrBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(channel.logoUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = channel.name,
                contentScale       = ContentScale.Fit,
                error              = painterResource(id = android.R.drawable.ic_menu_report_image),
                placeholder        = null,
                onError            = { /* log */ },
            )
        } else {
            ChannelInitialsPlaceholder(channel.name)
        }
    }
}

@Composable
private fun ChannelInitialsPlaceholder(channelName: String) {
    Text(
        text  = channelName.take(3).uppercase(),
        style = NSType.label(),
        color = NSColors.text3,
    )
}