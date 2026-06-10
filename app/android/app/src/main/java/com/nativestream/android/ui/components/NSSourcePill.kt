package com.nativestream.android.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.nativestream.android.domain.model.PlaylistSource
import com.nativestream.android.domain.model.isAll
import com.nativestream.android.ui.theme.NSDimens
import com.nativestream.android.ui.theme.NSType
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.nativestream.android.ui.theme.NSColors
import androidx.compose.ui.graphics.Color



/**
 * Source selector pill. Shows a colored dot, source name, and chevron.
 * [source] null or AllSources → neutral/text3 dot, no tint.
 * Specific source → 8% bg tint, 25% border tint from colorHex.
 */
@Composable
fun NSSourcePill(
    source: PlaylistSource?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = NSDimens.current
    val isAll = source == null || source.isAll

    val parsedColor = runCatching {
        source?.colorHex?.let { Color(android.graphics.Color.parseColor(it)) }
    }.getOrNull()

    val bgColor     = if (isAll || parsedColor == null) Color.Transparent
    else parsedColor.copy(alpha = 0.08f)
    val borderColor = if (isAll || parsedColor == null) NSColors.border
    else parsedColor.copy(alpha = 0.25f)
    val dotColor    = if (isAll || parsedColor == null) NSColors.text3
    else parsedColor

    Row(
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.spacing.xs),
        modifier = modifier
            .clip(RoundedCornerShape(dimens.radius.pill))
            .background(bgColor)
            .border(0.5.dp, borderColor, RoundedCornerShape(dimens.radius.pill))
            .clickable(onClick = onClick)
            .padding(horizontal = dimens.spacing.md, vertical = dimens.spacing.xs),
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Text(
            text     = source?.takeIf { !it.isAll }?.name ?: "All Sources",
            style    = NSType.caption(),
            color    = if (isAll) NSColors.text3 else NSColors.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Icon(
            imageVector        = Icons.Default.KeyboardArrowDown,
            contentDescription = null,
            tint               = NSColors.text3,
            modifier           = Modifier.size(12.dp),
        )
    }
}