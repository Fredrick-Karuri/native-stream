package com.nativestream.android.ui.components


import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.nativestream.android.ui.theme.NSColors
import com.nativestream.android.ui.theme.NSDimens
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import com.nativestream.android.domain.model.PlaylistSource
import com.nativestream.android.domain.model.isAll
import com.nativestream.android.ui.theme.NSType

/**
 * Source badge shown in tablet detail pane.
 * Only rendered when [source] is non-null and not AllSources.
 */
@Composable
fun NSSourceBadge(
    source: PlaylistSource,
    modifier: Modifier = Modifier,
) {
    if (source.isAll) return
    val dimens = NSDimens.current

    val parsedColor = runCatching {
        Color(android.graphics.Color.parseColor(source.colorHex))
    }.getOrElse { NSColors.text3 }

    Row(
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.spacing.xs),
        modifier = modifier
            .clip(RoundedCornerShape(dimens.radius.pill))
            .background(parsedColor.copy(alpha = 0.08f))
            .border(0.5.dp, parsedColor.copy(alpha = 0.25f), RoundedCornerShape(dimens.radius.pill))
            .padding(horizontal = dimens.spacing.md, vertical = dimens.spacing.xs),
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(parsedColor)
        )
        Text(
            text  = source.name,
            style = NSType.mono(),
            color = NSColors.text2,
        )
        Text(
            text     = source.url,
            style    = NSType.monoSmall(),
            color    = NSColors.text3,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}