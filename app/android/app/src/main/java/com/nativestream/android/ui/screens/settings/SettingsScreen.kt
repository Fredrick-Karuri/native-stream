// app/src/main/java/com/nativestream/android/ui/screens/settings/SettingsScreen.kt
//
// Settings Screen — mobile design (single scrollable column)
// Matches the Android design exactly:
//   - Server health card at top
//   - Section labels (SERVER, PLAYLIST SOURCES, EPG/TV GUIDE, PLAYBACK, PROXY)
//   - Icon rows with chevron or toggle on the right
//   - Source rows with health dot + refresh interval
//   - Buffer preset segmented picker inline

package com.nativestream.android.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.rememberCoroutineScope
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.regular.ArrowsClockwise
import com.adamglin.phosphoricons.regular.Calendar
import com.adamglin.phosphoricons.regular.Cpu
import com.adamglin.phosphoricons.regular.Database
import com.adamglin.phosphoricons.regular.FileLock
import com.adamglin.phosphoricons.regular.GearSix
import com.adamglin.phosphoricons.regular.Link
import com.adamglin.phosphoricons.regular.Play
import com.adamglin.phosphoricons.regular.Trash
import com.nativestream.android.data.local.BufferPreset
import com.nativestream.android.ui.components.NSTextField
import com.nativestream.android.ui.theme.NSColors
import com.nativestream.android.ui.theme.NSDimens
import com.nativestream.android.ui.theme.NSType
import com.nativestream.android.ui.viewmodel.PlaylistViewModel
import com.nativestream.android.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch

private val ROW_ICON_SIZE        = 32.dp
private val ROW_ICON_RADIUS      = 8.dp
private val ROW_ICON_INNER_SIZE  = 16.dp
private val CHEVRON_SIZE         = 16.dp
private val SECTION_LABEL_BOTTOM = 6.dp

// Icon background colours matching design
private val COLOR_BLUE   = Color(0xFF0EA5E9).copy(alpha = 0.12f)
private val COLOR_GREEN  = Color(0xFF10B981).copy(alpha = 0.12f)
private val COLOR_AMBER  = Color(0xFFF59E0B).copy(alpha = 0.12f)
private val COLOR_RED    = Color(0xFFEF4444).copy(alpha = 0.12f)
private val TINT_BLUE    = Color(0xFF38BDF8)
private val TINT_GREEN   = Color(0xFF10B981)
private val TINT_AMBER   = Color(0xFFF59E0B)
private val TINT_RED     = Color(0xFFEF4444)

@Composable
fun SettingsScreen(
    modifier: Modifier            = Modifier,
    settingsViewModel: SettingsViewModel = hiltViewModel(),
    playlistViewModel: PlaylistViewModel = hiltViewModel(),
) {
    val dimens = NSDimens.current
    val serverUrl by settingsViewModel.serverUrl.collectAsState()
    val bufferPreset by settingsViewModel.bufferPreset.collectAsState()
    val sources by playlistViewModel.sources.collectAsState()

    var proxyEnabled by remember { mutableStateOf(false) }
    var hwDecode by remember { mutableStateOf(true) }

    var showServerUrlDialog by remember { mutableStateOf(false) }
    var urlInput by remember(serverUrl) { mutableStateOf(serverUrl) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showAddSource by remember { mutableStateOf(false) }

    var showEpgUrlDialog by remember { mutableStateOf(false) }
    var epgInput by remember { mutableStateOf("") }

    var editingSourceEpg by remember { mutableStateOf<Pair<String, String?>?>(null) } // id to epgUrl

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = NSColors.bg,
    ) { paddingValues ->
        Column(modifier = modifier.fillMaxSize().background(NSColors.bg)) {

            // Top bar
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(NSColors.surface)
                    .padding(horizontal = dimens.spacing.lg, vertical = dimens.spacing.md),
            ) {
                Text(text = "Settings", style = NSType.heading(), color = NSColors.text)
            }
            Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(NSColors.border))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(dimens.spacing.lg),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = dimens.spacing.md, vertical = dimens.spacing.md),
            ) {
                // ── Server health card ────────────────────────────────────────────
                item {
                    ServerHealthCard(serverUrl = serverUrl)
                }

                // ── SERVER section ────────────────────────────────────────────────
                item {
                    SettingsSection(label = "Server") {
                        SettingsIconRow(
                            iconBackground = COLOR_BLUE,
                            iconTint = TINT_BLUE,
                            icon = PhosphorIcons.Regular.Database,
                            title = "Server URL",
                            subtitle = serverUrl.removePrefix("http://"),
                            onClick = { showServerUrlDialog = true }
                        )
                        SettingsDivider()
                        SettingsIconRow(
                            iconBackground = COLOR_GREEN,
                            iconTint = TINT_GREEN,
                            icon = PhosphorIcons.Regular.Cpu,
                            title = "Trigger probe",
                            subtitle = "Re-validate all stream links",
                            onClick = {
                                settingsViewModel.triggerProbe { success ->
                                    scope.launch {
                                        snackbarHostState.showSnackbar(
                                            if (success) "Probe started successfully" else "Probe failed — check server"
                                        )
                                    }
                                }
                            }
                        )
                    }
                }

                // ── PLAYLIST SOURCES section ──────────────────────────────────────
                item {
                    SettingsSection(label = "Playlist Sources") {
                        sources.forEachIndexed { index, source ->
                            if (index > 0) SettingsDivider()
                            SourceRow(
                                name = source.name,
                                url = source.url,
                                epgUrl = source.epgUrl,
                                        refreshHours = source.refreshIntervalHours,
                                isHealthy = true,
                                onEpgEdit   = { epg -> editingSourceEpg = source.id to epg },
                                onRefresh   = { playlistViewModel.loadAll() },
                                onDelete    = { playlistViewModel.removeSource(source.id); playlistViewModel.loadAll() },
                            )
                        }
                        SettingsDivider()
                        AddSourceRow(onClick = { showAddSource = true })
                    }
                }

                // ── PLAYBACK section ──────────────────────────────────────────────
                item {
                    SettingsSection(label = "Playback") {
                        // Buffer preset row
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    horizontal = dimens.spacing.md,
                                    vertical = dimens.spacing.sm
                                ),
                        ) {
                            RowIcon(
                                background = COLOR_BLUE,
                                tint = TINT_BLUE,
                                icon = PhosphorIcons.Regular.GearSix
                            )
                            Spacer(modifier = Modifier.width(dimens.spacing.sm))
                            Text(
                                text = "Buffer preset",
                                style = NSType.bodyMedium(),
                                color = NSColors.text,
                                modifier = Modifier.weight(1f)
                            )
                            BufferSegmentedPicker(
                                selected = bufferPreset,
                                onSelect = { settingsViewModel.setBufferPreset(it) },
                            )
                        }
                        SettingsDivider()
                        // Hardware decode row
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    horizontal = dimens.spacing.md,
                                    vertical = dimens.spacing.sm
                                ),
                        ) {
                            RowIcon(
                                background = COLOR_AMBER,
                                tint = TINT_AMBER,
                                icon = PhosphorIcons.Regular.Play
                            )
                            Spacer(modifier = Modifier.width(dimens.spacing.sm))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Hardware decode",
                                    style = NSType.bodyMedium(),
                                    color = NSColors.text
                                )
                                Text(
                                    text = "Always on (ExoPlayer)",
                                    style = NSType.caption(),
                                    color = NSColors.text3
                                )
                            }
                            NSToggle(checked = hwDecode, onCheckedChange = {}, enabled = false)
                        }
                    }
                }

                // ── PROXY section ─────────────────────────────────────────────────
                item {
                    SettingsSection(label = "Proxy") {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    horizontal = dimens.spacing.md,
                                    vertical = dimens.spacing.sm
                                ),
                        ) {
                            RowIcon(
                                background = COLOR_RED,
                                tint = TINT_RED,
                                icon = PhosphorIcons.Regular.FileLock
                            )
                            Spacer(modifier = Modifier.width(dimens.spacing.sm))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Enable proxy",
                                    style = NSType.bodyMedium(),
                                    color = NSColors.text
                                )
                                Text(
                                    text = "Inject Referer / User-Agent",
                                    style = NSType.caption(),
                                    color = NSColors.text3
                                )
                            }
                            NSToggle(
                                checked = proxyEnabled,
                                onCheckedChange = { proxyEnabled = it })
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
            if (showAddSource) {
                AddSourceSheet(
                    onDone = { showAddSource = false },
                    playlistViewModel = playlistViewModel
                )
            }
            if (showServerUrlDialog) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { showServerUrlDialog = false },
                    containerColor = NSColors.surface2,
                    title = { Text("Server URL", style = NSType.heading(), color = NSColors.text) },
                    text = {
                        NSTextField(
                            value = urlInput,
                            onValueChange = { urlInput = it },
                            placeholder = "http://192.168.1.x:8888",
                        )
                    },
                    confirmButton = {
                        Text(
                            "Save", style = NSType.captionMedium(), color = NSColors.accent,
                            modifier = Modifier.clickable {
                                settingsViewModel.setServerUrl(urlInput)
                                showServerUrlDialog = false
                            }.padding(8.dp)
                        )
                    },
                    dismissButton = {
                        Text(
                            "Cancel", style = NSType.captionMedium(), color = NSColors.text3,
                            modifier = Modifier.clickable { showServerUrlDialog = false }
                                .padding(8.dp)
                        )
                    },
                )
            }
            if (showEpgUrlDialog) {
                val epgUrl by settingsViewModel.epgUrl.collectAsState()
                AlertDialog(
                    onDismissRequest = { showEpgUrlDialog = false },
                    containerColor   = NSColors.surface2,
                    title = { Text("EPG URL", style = NSType.heading(), color = NSColors.text) },
                    text  = {
                        NSTextField(
                            value         = epgInput.ifEmpty { epgUrl ?: "" },
                            onValueChange = { epgInput = it },
                            placeholder   = "http://.../epg.xml",
                        )
                    },
                    confirmButton = {
                        Text("Save", style = NSType.captionMedium(), color = NSColors.accent,
                            modifier = Modifier.clickable {
                                settingsViewModel.setEpgUrl(epgInput)
                                showEpgUrlDialog = false
                            }.padding(8.dp))
                    },
                    dismissButton = {
                        Text("Cancel", style = NSType.captionMedium(), color = NSColors.text3,
                            modifier = Modifier.clickable { showEpgUrlDialog = false }.padding(8.dp))
                    },
                )
            }

            editingSourceEpg?.let { (sourceId, currentEpg) ->
                var epgInput by remember { mutableStateOf(currentEpg ?: "") }
                AlertDialog(
                    onDismissRequest = { editingSourceEpg = null },
                    containerColor   = NSColors.surface2,
                    title = { Text("EPG URL", style = NSType.heading(), color = NSColors.text) },
                    text  = {
                        Column(verticalArrangement = Arrangement.spacedBy(NSDimens.current.spacing.sm)) {
                            Text("Link an EPG source to this playlist.",
                                style = NSType.caption(), color = NSColors.text3)
                            NSTextField(value = epgInput, onValueChange = { epgInput = it },
                                placeholder = "http://.../epg.xml")
                        }
                    },
                    confirmButton = {
                        Text("Save", style = NSType.captionMedium(), color = NSColors.accent,
                            modifier = Modifier.clickable {
                                val updated = sources.find { it.id == sourceId }
                                    ?.copy(epgUrl = epgInput.trim().ifEmpty { null })
                                updated?.let { playlistViewModel.updateSource(it) }
                                editingSourceEpg = null
                            }.padding(8.dp))
                    },
                    dismissButton = {
                        Text("Clear", style = NSType.captionMedium(), color = NSColors.text3,
                            modifier = Modifier.clickable {
                                val updated = sources.find { it.id == sourceId }?.copy(epgUrl = null)
                                updated?.let { playlistViewModel.updateSource(it) }
                                editingSourceEpg = null
                            }.padding(8.dp))
                    },
                )
            }
        }
    }
}

// ── Server health card ────────────────────────────────────────────────────────

@Composable
private fun ServerHealthCard(serverUrl: String) {
    val dimens    = NSDimens.current
    val connected = serverUrl.isNotBlank()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(dimens.radius.xl))
            .background(NSColors.surface2)
            .border(0.5.dp, NSColors.border, RoundedCornerShape(dimens.radius.xl))
            .padding(dimens.spacing.md),
    ) {
        NSHealthDot(score = if (connected) 1.0 else 0.0)
        Spacer(modifier = Modifier.width(dimens.spacing.sm))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text  = if (connected) "Server connected" else "Server unreachable",
                style = NSType.bodyMedium(),
                color = NSColors.text,
            )
            Text(
                text     = serverUrl.removePrefix("http://"),
                style    = NSType.monoSmall(),
                color    = NSColors.text3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ── Settings section card ─────────────────────────────────────────────────────

@Composable
private fun SettingsSection(label: String, content: @Composable () -> Unit) {
    val dimens = NSDimens.current
    Column {
        Text(
            text     = label.uppercase(),
            style    = NSType.label(),
            color    = NSColors.text3,
            modifier = Modifier.padding(
                horizontal = dimens.spacing.xs,
                vertical   = SECTION_LABEL_BOTTOM,
            ),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(dimens.radius.xl))
                .background(NSColors.surface2)
                .border(0.5.dp, NSColors.border, RoundedCornerShape(dimens.radius.xl)),
        ) {
            content()
        }
    }
}

// ── Standard icon row with chevron ────────────────────────────────────────────

@Composable
private fun SettingsIconRow(
    iconBackground: Color,
    iconTint: Color,
    icon: ImageVector,
    title: String,
    subtitle: String = "",
    onClick: () -> Unit,
) {
    val dimens = NSDimens.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = dimens.spacing.md, vertical = dimens.spacing.sm),
    ) {
        RowIcon(background = iconBackground, tint = iconTint, icon = icon)
        Spacer(modifier = Modifier.width(dimens.spacing.sm))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = NSType.bodyMedium(), color = NSColors.text)
            if (subtitle.isNotEmpty()) {
                Text(
                    text     = subtitle,
                    style    = NSType.caption(),
                    color    = NSColors.text3,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Icon(
            imageVector        = Icons.Default.KeyboardArrowRight,
            contentDescription = null,
            tint               = NSColors.text3,
            modifier           = Modifier.size(CHEVRON_SIZE),
        )
    }
}

@Composable
private fun RowIcon(background: Color, tint: Color, icon: ImageVector) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(ROW_ICON_SIZE)
            .clip(RoundedCornerShape(ROW_ICON_RADIUS))
            .background(background),
    ) {
        Icon(
            imageVector        = icon,
            contentDescription = null,
            tint               = tint,
            modifier           = Modifier.size(ROW_ICON_INNER_SIZE),
        )
    }
}

// ── Source row (health dot + refresh interval) ────────────────────────────────
@Composable
private fun SourceRow(
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

@Composable
private fun AddSourceRow(onClick: () -> Unit) {
    val dimens = NSDimens.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(dimens.spacing.sm)
            .clip(RoundedCornerShape(dimens.radius.md))
            .border(
                0.5.dp,
                NSColors.border2,
                RoundedCornerShape(dimens.radius.md),
            )
            .padding(vertical = dimens.spacing.sm),
    ) {
        Text(text = "+ Add source", style = NSType.caption(), color = NSColors.text3)
    }
}

@Composable
private fun SettingsDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(0.5.dp)
            .background(NSColors.border),
    )
}

// ── Buffer segmented picker ───────────────────────────────────────────────────

@Composable
private fun BufferSegmentedPicker(selected: BufferPreset, onSelect: (BufferPreset) -> Unit) {
    val dimens = NSDimens.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(dimens.radius.sm))
            .background(NSColors.bg)
            .border(0.5.dp, NSColors.border, RoundedCornerShape(dimens.radius.sm))
            .padding(2.dp),
    ) {
        BufferPreset.entries.forEach { preset ->
            val isActive = selected == preset
            Text(
                text  = preset.name.lowercase().replaceFirstChar { it.uppercase() },
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
                    .clickable { onSelect(preset) }
                    .padding(horizontal = dimens.spacing.sm, vertical = 4.dp),
            )
        }
    }
}