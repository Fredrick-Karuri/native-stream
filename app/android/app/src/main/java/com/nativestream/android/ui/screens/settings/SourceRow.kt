package com.nativestream.android.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.regular.ArrowsClockwise
import com.adamglin.phosphoricons.regular.Link
import com.adamglin.phosphoricons.regular.Trash
import com.nativestream.android.ui.theme.NSColors
import com.nativestream.android.ui.theme.NSDimens
import com.nativestream.android.ui.theme.NSType

@Composable
fun SourceRow(
    name: String,
    url: String,
    epgUrl: String?,
    refreshHours: Int,
    isHealthy: Boolean,
    onDelete: () -> Unit,
    onRefresh: () -> Unit,
    onEpgEdit: (String?) -> Unit,
) {
    val dimens = NSDimens.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.spacing.md, vertical = dimens.spacing.sm),
    ) {
        NSHealthDot(score = if (isHealthy) 1.0 else 0.3)
        Spacer(modifier = Modifier.width(dimens.spacing.sm))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = name, style = NSType.bodyMedium(), color = NSColors.text)
            Text(text = url, style = NSType.monoSmall(), color = NSColors.text3,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(modifier = Modifier.width(dimens.spacing.sm))
        // ── Action icons ──────────────────────────────────────────────────────
        Row(horizontalArrangement = Arrangement.spacedBy(dimens.spacing.xs)) {
            SourceActionIcon(
                icon    = PhosphorIcons.Regular.Link,
                tint    = if (!epgUrl.isNullOrBlank()) NSColors.accent2 else NSColors.text3,
                onClick = { onEpgEdit(epgUrl) },
            )
            SourceActionIcon(
                icon    = PhosphorIcons.Regular.ArrowsClockwise,
                tint    = NSColors.text3,
                onClick = onRefresh,
            )
            SourceActionIcon(
                icon    = PhosphorIcons.Regular.Trash,
                tint    = NSColors.live,
                onClick = onDelete,
            )
        }
    }
}

@Composable
private fun SourceActionIcon(icon: ImageVector, tint: Color, onClick: () -> Unit) {
    val dimens = NSDimens.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .clip(RoundedCornerShape(dimens.radius.sm))
            .background(tint.copy(alpha = 0.08f))
            .clickable(onClick = onClick)
            .padding(dimens.spacing.xs),
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint,
            modifier = Modifier.size(14.dp))
    }
}