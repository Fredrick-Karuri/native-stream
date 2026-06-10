package com.nativestream.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nativestream.android.domain.model.PlaylistSource
import com.nativestream.android.domain.model.isAll
import com.nativestream.android.ui.theme.NSColors
import com.nativestream.android.ui.theme.NSDimens
import com.nativestream.android.ui.theme.NSType


/**
 * Bottom sheet for selecting a playlist source.
 * "All Sources" is always first. Active source gets a checkmark.
 * Footer row navigates to add-playlist flow.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NSSourcePickerSheet(
    sources: List<PlaylistSource>,
    selectedSource: PlaylistSource?,
    onSelectSource: (PlaylistSource?) -> Unit,
    onAddPlaylist: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest  = onDismiss,
        sheetState        = sheetState,
        containerColor    = NSColors.surface,
        dragHandle        = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            // Sheet handle + title
            SheetHandle()
            Text(
                text     = "Playlist",
                style    = NSType.heading(),
                color    = NSColors.text,
                modifier = Modifier.padding(
                    horizontal = NSDimens.current.spacing.lg,
                    vertical   = NSDimens.current.spacing.md,
                ),
            )
            Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(NSColors.border))

            LazyColumn {
                // All Sources row
                item {
                    SourceRow(
                        label     = "All Sources",
                        meta      = "${sources.sumOf { it.channelCount }} channels",
                        dotColor  = NSColors.text3,
                        isActive  = selectedSource == null || selectedSource.isAll,
                        onClick   = { onSelectSource(null); onDismiss() },
                    )
                }

                items(sources, key = { it.id }) { source ->
                    val parsedColor = runCatching {
                        Color(android.graphics.Color.parseColor(source.colorHex))
                    }.getOrElse { NSColors.text3 }

                    SourceRow(
                        label    = source.name,
                        meta     = "${source.url} · ${source.channelCount} ch",
                        dotColor = parsedColor,
                        isActive = selectedSource?.id == source.id,
                        onClick  = { onSelectSource(source); onDismiss() },
                    )
                }

                // Add playlist footer
                item {
                    Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(NSColors.border))
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(NSDimens.current.spacing.sm),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onAddPlaylist)
                            .padding(
                                horizontal = NSDimens.current.spacing.lg,
                                vertical   = NSDimens.current.spacing.md,
                            ),
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .border(1.dp, NSColors.border2, CircleShape),
                        ) {
                            Icon(
                                imageVector        = Icons.Default.Add,
                                contentDescription = null,
                                tint               = NSColors.text3,
                                modifier           = Modifier.size(12.dp),
                            )
                        }
                        Text(
                            text  = "Add playlist",
                            style = NSType.caption(),
                            color = NSColors.text3,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceRow(
    label: String,
    meta: String,
    dotColor: Color,
    isActive: Boolean,
    onClick: () -> Unit,
) {
    val dimens = NSDimens.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = dimens.spacing.lg, vertical = dimens.spacing.md),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Spacer(modifier = Modifier.width(dimens.spacing.sm))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text  = label,
                style = NSType.captionMedium(),
                color = if (isActive) NSColors.text else NSColors.text2,
            )
            Text(
                text     = meta,
                style    = NSType.mono(),
                color    = NSColors.text3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (isActive) {
            Icon(
                imageVector        = Icons.Default.Check,
                contentDescription = null,
                tint               = NSColors.accent2,
                modifier           = Modifier.size(14.dp),
            )
        }
    }
}

@Composable
private fun SheetHandle() {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
    ) {
        Box(
            modifier = Modifier
                .width(36.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(NSColors.border3)
        )
    }
}