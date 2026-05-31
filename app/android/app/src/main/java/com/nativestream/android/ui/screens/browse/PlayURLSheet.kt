// app/src/main/java/com/nativestream/android/ui/screens/browse/PlayURLSheet.kt
//
// Direct URL playback — paste any HLS/IPTV URL + optional headers, plays immediately.
// Creates a temporary channel (not persisted).

package com.nativestream.android.ui.screens.browse

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.nativestream.android.ui.components.NSTextField
import com.nativestream.android.ui.theme.NSColors
import com.nativestream.android.ui.theme.NSDimens
import com.nativestream.android.ui.theme.NSType
import com.nativestream.android.ui.viewmodel.PlayerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayURLSheet(
    playerViewModel: PlayerViewModel,
    onDismiss: () -> Unit,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var urlText     by remember { mutableStateOf("") }
    var referer     by remember { mutableStateOf("") }
    var userAgent   by remember { mutableStateOf("") }
    var showHeaders by remember { mutableStateOf(false) }

    val urlIsValid = urlText.trim().isNotEmpty()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = sheetState,
        containerColor   = NSColors.bg,
        modifier         = modifier,
    ) {
        PlayURLContent(
            urlText       = urlText,
            referer       = referer,
            userAgent     = userAgent,
            showHeaders   = showHeaders,
            urlIsValid    = urlIsValid,
            onUrlChange       = { urlText = it },
            onRefererChange   = { referer = it },
            onUserAgentChange = { userAgent = it },
            onToggleHeaders   = { showHeaders = !showHeaders },
            onCancel          = onDismiss,
            onPlay            = {
                val headers = buildMap {
                    if (referer.isNotEmpty())   put("Referer",    referer)
                    if (userAgent.isNotEmpty()) put("User-Agent", userAgent)
                }
                playerViewModel.playUrl(url = urlText.trim(), headers = headers)
                onDismiss()
                onPlay()
            },
        )
    }
}

// ── Sheet content ─────────────────────────────────────────────────────────────

@Composable
private fun PlayURLContent(
    urlText: String,
    referer: String,
    userAgent: String,
    showHeaders: Boolean,
    urlIsValid: Boolean,
    onUrlChange: (String) -> Unit,
    onRefererChange: (String) -> Unit,
    onUserAgentChange: (String) -> Unit,
    onToggleHeaders: () -> Unit,
    onCancel: () -> Unit,
    onPlay: () -> Unit,
) {
    val dimens = NSDimens.current

    Column(
        verticalArrangement = Arrangement.spacedBy(dimens.spacing.xl),
        modifier = Modifier
            .fillMaxWidth()
            .padding(dimens.spacing.xxl),
    ) {
        Text(text = "Play URL", style = NSType.heading(), color = NSColors.text)

        // ── URL field ─────────────────────────────────────────────────────────
        Column(verticalArrangement = Arrangement.spacedBy(dimens.spacing.xs)) {
            Text(text = "Stream URL", style = NSType.caption(), color = NSColors.text3)
            NSTextField(
                value         = urlText,
                onValueChange = onUrlChange,
                placeholder   = "https://example.com/stream.m3u8",
            )
        }

        // ── Headers toggle ────────────────────────────────────────────────────
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.spacing.xs),
            modifier = Modifier.clickable(onClick = onToggleHeaders),
        ) {
            Icon(
                imageVector        = if (showHeaders) Icons.Default.KeyboardArrowDown
                                     else Icons.Default.KeyboardArrowRight,
                contentDescription = "Toggle headers",
                tint               = NSColors.text3,
                modifier           = Modifier.padding(0.dp),
            )
            Text(
                text  = "HTTP Headers (optional)",
                style = NSType.caption(),
                color = NSColors.text3,
            )
        }

        // ── Collapsible header fields ─────────────────────────────────────────
        AnimatedVisibility(
            visible = showHeaders,
            enter   = expandVertically() + fadeIn(),
            exit    = shrinkVertically() + fadeOut(),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(dimens.spacing.sm)) {
                HeaderField(
                    label         = "Referer",
                    placeholder   = "https://example.com",
                    value         = referer,
                    onValueChange = onRefererChange,
                    useMono       = false,
                )
                HeaderField(
                    label         = "User-Agent",
                    placeholder   = "Mozilla/5.0 …",
                    value         = userAgent,
                    onValueChange = onUserAgentChange,
                    useMono       = true,
                )
            }
        }

        // ── Divider ───────────────────────────────────────────────────────────
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .border(0.5.dp, NSColors.border),
        )

        // ── Action buttons ────────────────────────────────────────────────────
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            modifier              = Modifier.fillMaxWidth(),
        ) {
            SheetButton(label = "Cancel", isPrimary = false, enabled = true, onClick = onCancel)
            Spacer(modifier = Modifier.weight(1f))
            SheetButton(label = "Play", isPrimary = true, enabled = urlIsValid, onClick = onPlay)
        }
    }
}

@Composable
private fun HeaderField(
    label: String,
    placeholder: String,
    value: String,
    onValueChange: (String) -> Unit,
    useMono: Boolean,
) {
    val dimens = NSDimens.current
    Column(verticalArrangement = Arrangement.spacedBy(dimens.spacing.xs)) {
        Text(text = label, style = NSType.caption(), color = NSColors.text3)
        NSTextField(
            value         = value,
            onValueChange = onValueChange,
            placeholder   = placeholder,
        )
    }
}

// Reuse the same SheetButton shape from AddChannelSheet
@Composable
fun SheetButton(label: String, isPrimary: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val dimens      = NSDimens.current
    val background  = if (isPrimary && enabled) NSColors.accentGlow else NSColors.surface3
    val borderColor = if (isPrimary && enabled) NSColors.accentBorder else NSColors.border2
    val textColor   = when {
        isPrimary && enabled  -> NSColors.accent
        isPrimary && !enabled -> NSColors.text3
        else                  -> NSColors.text2
    }
    Text(
        text     = label,
        style    = NSType.captionMedium(),
        color    = textColor,
        modifier = Modifier
            .clip(RoundedCornerShape(dimens.radius.md))
            .background(background)
            .border(0.5.dp, borderColor, RoundedCornerShape(dimens.radius.md))
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = dimens.spacing.md, vertical = 6.dp),
    )
}