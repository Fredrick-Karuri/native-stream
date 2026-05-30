// app/src/main/java/com/nativestream/android/ui/screens/settings/SettingsSections.kt
//
// Settings Section Panels
// Sources, Playback, Server, Proxy, Discovery.

package com.nativestream.android.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nativestream.android.data.local.BufferPreset
import com.nativestream.android.domain.model.PlaylistSource
import com.nativestream.android.ui.components.NSTextField
import com.nativestream.android.ui.theme.NSColors
import com.nativestream.android.ui.theme.NSDimens
import com.nativestream.android.ui.theme.NSType
import com.nativestream.android.ui.viewmodel.PlaylistViewModel
import com.nativestream.android.ui.viewmodel.SettingsViewModel
import java.util.UUID

// ── Sources section ───────────────────────────────────────────────────────────

@Composable
fun SourcesSection(
    playlistViewModel: PlaylistViewModel,
    settingsViewModel: SettingsViewModel,
) {
    val dimens  = NSDimens.current
    val sources by playlistViewModel.sources.collectAsState()

    var showAddForm  by remember { mutableStateOf(false) }
    var newLabel     by remember { mutableStateOf("") }
    var newUrl       by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(dimens.spacing.xl)) {
        SectionTitle("Playlist Sources")

        sources.forEach { source ->
            SourceRow(
                source        = source,
                onDelete      = { playlistViewModel.removeSource(source.id) },
                onUpdateEpg   = { epgUrl ->
                    playlistViewModel.updateSource(source.copy(url = epgUrl))
                },
            )
        }

        if (showAddForm) {
            AddSourceForm(
                label    = newLabel,
                url      = newUrl,
                onLabelChange = { newLabel = it },
                onUrlChange   = { newUrl = it },
                onCancel = { showAddForm = false; newLabel = ""; newUrl = "" },
                onAdd    = {
                    if (newUrl.isNotBlank()) {
                        playlistViewModel.addSource(
                            PlaylistSource(
                                id                   = UUID.randomUUID().toString(),
                                name                 = newLabel.ifEmpty { newUrl.substringAfterLast("/") },
                                url                  = newUrl,
                                refreshIntervalHours = 6,
                            )
                        )
                        playlistViewModel.loadAll()
                        showAddForm = false; newLabel = ""; newUrl = ""
                    }
                },
            )
        } else {
            AddButton(label = "+ Add Source") { showAddForm = true }
        }
    }
}

@Composable
private fun SourceRow(
    source: PlaylistSource,
    onDelete: () -> Unit,
    onUpdateEpg: (String) -> Unit,
) {
    val dimens      = NSDimens.current
    val clipboard   = LocalClipboardManager.current
    var showEpg     by remember { mutableStateOf(false) }
    var epgInput    by remember { mutableStateOf(source.url) }
    var copied      by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(dimens.radius.lg))
            .background(NSColors.surface2)
            .border(0.5.dp, NSColors.border, RoundedCornerShape(dimens.radius.lg)),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.spacing.md),
            modifier = Modifier.padding(dimens.spacing.md),
        ) {
            NSHealthDot(score = 1.0)
            Column(modifier = Modifier.weight(1f)) {
                Text(text = source.name, style = NSType.captionMedium(), color = NSColors.text)
                Text(
                    text     = source.url,
                    style    = NSType.monoSmall(),
                    color    = NSColors.text3,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text  = "↻ ${source.refreshIntervalHours}h",
                style = NSType.monoSmall(),
                color = NSColors.text3,
            )
            Text(
                text  = if (copied) "✓" else "⧉",
                style = NSType.monoSmall(),
                color = if (copied) NSColors.amber else NSColors.text3,
                modifier = Modifier.clickable {
                    clipboard.setText(AnnotatedString(source.url))
                    copied = true
                },
            )
            Text(
                text  = "🗑",
                style = NSType.monoSmall(),
                color = NSColors.text3,
                modifier = Modifier.clickable(onClick = onDelete),
            )
        }

        if (showEpg) {
            Spacer(modifier = Modifier.height(0.5.dp).fillMaxWidth().background(NSColors.border))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dimens.spacing.sm),
                modifier = Modifier.padding(dimens.spacing.md),
            ) {
                Text(text = "TV Guide", style = NSType.monoSmall(), color = NSColors.accent)
                NSTextField(value = epgInput, onValueChange = { epgInput = it }, placeholder = "EPG URL (.xml or .xml.gz)", modifier = Modifier.weight(1f))
                Text(
                    text  = "Save",
                    style = NSType.captionMedium(),
                    color = if (epgInput != source.url) NSColors.accent else NSColors.text3,
                    modifier = Modifier.clickable { onUpdateEpg(epgInput); showEpg = false },
                )
            }
        }
    }
}

@Composable
private fun AddSourceForm(
    label: String,
    url: String,
    onLabelChange: (String) -> Unit,
    onUrlChange: (String) -> Unit,
    onCancel: () -> Unit,
    onAdd: () -> Unit,
) {
    val dimens = NSDimens.current
    Column(
        verticalArrangement = Arrangement.spacedBy(dimens.spacing.md),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(dimens.radius.lg))
            .background(NSColors.surface2)
            .border(0.5.dp, NSColors.border2, RoundedCornerShape(dimens.radius.lg))
            .padding(dimens.spacing.md),
    ) {
        NSTextField(value = label, onValueChange = onLabelChange, placeholder = "Label (e.g. Sports Pack)")
        NSTextField(value = url,   onValueChange = onUrlChange,   placeholder = "URL (https:// or file://)")
        Row(modifier = Modifier.fillMaxWidth()) {
            Spacer(modifier = Modifier.weight(1f))
            SheetActionButton(label = "Cancel", isPrimary = false, enabled = true, onClick = onCancel)
            Spacer(modifier = Modifier.width(dimens.spacing.sm))
            SheetActionButton(label = "Add",    isPrimary = true,  enabled = url.isNotBlank(), onClick = onAdd)
        }
    }
}

// ── Playback section ──────────────────────────────────────────────────────────

@Composable
fun PlaybackSection(settingsViewModel: SettingsViewModel) {
    val dimens       = NSDimens.current
    val bufferPreset by settingsViewModel.bufferPreset.collectAsState()

    Column(verticalArrangement = Arrangement.spacedBy(dimens.spacing.xl)) {
        SectionTitle("Playback")
        SettingsRow(
            title    = "Buffer Preset",
            subtitle = "Tradeoff between latency and stability",
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(dimens.spacing.xs)) {
                BufferPreset.entries.forEach { preset ->
                    val isActive = bufferPreset == preset
                    Text(
                        text  = preset.name.lowercase().replaceFirstChar { it.uppercase() },
                        style = NSType.captionMedium(),
                        color = if (isActive) NSColors.accent2 else NSColors.text3,
                        modifier = Modifier
                            .clip(RoundedCornerShape(dimens.radius.sm))
                            .background(if (isActive) NSColors.accentGlow else NSColors.bg)
                            .border(
                                0.5.dp,
                                if (isActive) NSColors.accentBorder else NSColors.border,
                                RoundedCornerShape(dimens.radius.sm),
                            )
                            .clickable { settingsViewModel.setBufferPreset(preset) }
                            .padding(horizontal = dimens.spacing.sm, vertical = 4.dp),
                    )
                }
            }
        }
        SettingsRow(
            title    = "Hardware Decode",
            subtitle = "ExoPlayer — always on",
        ) {
            NSToggle(checked = true, onCheckedChange = {}, enabled = false)
        }
    }
}

// ── Server section ────────────────────────────────────────────────────────────

@Composable
fun ServerSection(settingsViewModel: SettingsViewModel) {
    val dimens    = NSDimens.current
    val serverUrl by settingsViewModel.serverUrl.collectAsState()

    Column(verticalArrangement = Arrangement.spacedBy(dimens.spacing.xl)) {
        SectionTitle("StreamServer")
        Column(verticalArrangement = Arrangement.spacedBy(dimens.spacing.sm)) {
            Text(text = "Server URL", style = NSType.caption(), color = NSColors.text3)
            NSTextField(
                value         = serverUrl,
                onValueChange = { settingsViewModel.setServerUrl(it) },
                placeholder   = "http://192.168.1.42:8888",
            )
            Text(
                text  = "Enter the LAN IP of the NativeStream server, e.g. http://192.168.1.42:8888",
                style = NSType.monoSmall(),
                color = NSColors.text3,
            )
        }
    }
}

// ── Proxy section ─────────────────────────────────────────────────────────────

@Composable
fun ProxySection() {
    val dimens         = NSDimens.current
    var proxyEnabled   by remember { mutableStateOf(false) }
    var referer        by remember { mutableStateOf("") }
    var userAgent      by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(dimens.spacing.xl)) {
        SectionTitle("HLS Proxy")
        SettingsRow(
            title    = "Enable Proxy",
            subtitle = "Injects Referer/User-Agent headers. Enable only if streams require it.",
        ) {
            NSToggle(checked = proxyEnabled, onCheckedChange = { proxyEnabled = it })
        }
        if (proxyEnabled) {
            Column(verticalArrangement = Arrangement.spacedBy(dimens.spacing.sm)) {
                Text(text = "Referer", style = NSType.caption(), color = NSColors.text3)
                NSTextField(value = referer, onValueChange = { referer = it }, placeholder = "https://example.com")
                Spacer(modifier = Modifier.height(dimens.spacing.xs))
                Text(text = "User-Agent", style = NSType.caption(), color = NSColors.text3)
                NSTextField(value = userAgent, onValueChange = { userAgent = it }, placeholder = "Mozilla/5.0 …")
            }
        }
    }
}

// ── Discovery section ─────────────────────────────────────────────────────────

@Composable
fun DiscoverySection() {
    val dimens  = NSDimens.current
    var enabled by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(dimens.spacing.xl)) {
        SectionTitle("Auto-Discovery")
        SettingsRow(
            title    = "Enable Discovery",
            subtitle = "Server crawls configured sources for stream links automatically.",
        ) {
            NSToggle(checked = enabled, onCheckedChange = { enabled = it })
        }
        if (enabled) {
            Text(
                text  = "Configure in your server config.yaml:\n\ndiscovery_enabled: true",
                style = NSType.monoSmall(),
                color = NSColors.text3,
            )
        } else {
            Text(
                text  = "When enabled, StreamServer automatically discovers and validates stream links. Dead links are replaced without manual intervention.",
                style = NSType.caption(),
                color = NSColors.text3,
            )
        }
    }
}

// ── Shared button ─────────────────────────────────────────────────────────────

@Composable
internal fun SheetActionButton(label: String, isPrimary: Boolean, enabled: Boolean, onClick: () -> Unit) {
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