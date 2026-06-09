// app/src/main/java/com/nativestream/android/ui/screens/settings/SettingsScreen.kt
//
// Settings Screen — adaptive layout:
//   Compact  → single scrollable column (phone)
//   Medium/Expanded → two-pane sidebar layout (tablet)

package com.nativestream.android.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.regular.ArrowsClockwise
import com.adamglin.phosphoricons.regular.Cpu
import com.adamglin.phosphoricons.regular.Database
import com.adamglin.phosphoricons.regular.FileLock
import com.adamglin.phosphoricons.regular.GearSix
import com.adamglin.phosphoricons.regular.Link
import com.adamglin.phosphoricons.regular.Play
import com.adamglin.phosphoricons.regular.Trash
import com.nativestream.android.data.local.BufferPreset
import com.nativestream.android.ui.LocalWindowSizeClass
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
private val SETTINGS_SIDEBAR_WIDTH = 200.dp

// Icon background colours matching design
private val COLOR_BLUE   = Color(0xFF0EA5E9).copy(alpha = 0.12f)
private val COLOR_GREEN  = Color(0xFF10B981).copy(alpha = 0.12f)
private val COLOR_AMBER  = Color(0xFFF59E0B).copy(alpha = 0.12f)
private val COLOR_RED    = Color(0xFFEF4444).copy(alpha = 0.12f)
private val TINT_BLUE    = Color(0xFF38BDF8)
private val TINT_GREEN   = Color(0xFF10B981)
private val TINT_AMBER   = Color(0xFFF59E0B)
private val TINT_RED     = Color(0xFFEF4444)

private enum class SettingsSection {
    SERVER, SOURCES, PLAYBACK, PROXY;
    val label get() = when (this) {
        SERVER   -> "Server"
        SOURCES  -> "Sources"
        PLAYBACK -> "Playback"
        PROXY    -> "Proxy"
    }
}

@Composable
private fun settingsFieldModifier(): Modifier {
    val windowSizeClass = LocalWindowSizeClass.current
    return if (windowSizeClass.widthSizeClass == WindowWidthSizeClass.Expanded) {
        Modifier.widthIn(max = 480.dp)
    } else {
        Modifier.fillMaxWidth()
    }
}

// ── Root screen ───────────────────────────────────────────────────────────────

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    settingsViewModel: SettingsViewModel = hiltViewModel(),
    playlistViewModel: PlaylistViewModel = hiltViewModel(),
) {
    val dimens = NSDimens.current
    val serverUrl    by settingsViewModel.serverUrl.collectAsState()
    val bufferPreset by settingsViewModel.bufferPreset.collectAsState()
    val sources      by playlistViewModel.sources.collectAsState()

    var proxyEnabled by remember { mutableStateOf(false) }
    var hwDecode     by remember { mutableStateOf(true) }

    var showServerUrlDialog by remember { mutableStateOf(false) }
    var urlInput            by remember(serverUrl) { mutableStateOf(serverUrl) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope             = rememberCoroutineScope()
    var showAddSource     by remember { mutableStateOf(false) }

    var showEpgUrlDialog  by remember { mutableStateOf(false) }
    var epgInput          by remember { mutableStateOf("") }

    var editingSourceEpg  by remember { mutableStateOf<Pair<String, String?>?>(null) }

    val windowSizeClass = LocalWindowSizeClass.current
    val useSidebar = windowSizeClass.widthSizeClass != WindowWidthSizeClass.Compact

    Scaffold(
        snackbarHost   = { SnackbarHost(snackbarHostState) },
        containerColor = NSColors.bg,
    ) { paddingValues ->
        Column(modifier = modifier.fillMaxSize().background(NSColors.bg)) {

            // Top bar
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(NSColors.surface)
                    .windowInsetsPadding(WindowInsets.displayCutout)
                    .padding(horizontal = dimens.spacing.lg, vertical = dimens.spacing.lg),
            ) {
                Text(text = "Settings", style = NSType.heading(), color = NSColors.text)
            }
            Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(NSColors.border))

            if (useSidebar) {
                SettingsTwoPane(
                    settingsViewModel   = settingsViewModel,
                    playlistViewModel   = playlistViewModel,
                    showAddSource       = showAddSource,
                    onShowAddSource     = { showAddSource = it },
                    showServerUrlDialog = showServerUrlDialog,
                    onShowServerUrl     = { showServerUrlDialog = it },
                    urlInput            = urlInput,
                    onUrlInput          = { urlInput = it },
                    showEpgUrlDialog    = showEpgUrlDialog,
                    onShowEpgUrl        = { showEpgUrlDialog = it },
                    epgInput            = epgInput,
                    onEpgInput          = { epgInput = it },
                    editingSourceEpg    = editingSourceEpg,
                    onEditingSourceEpg  = { editingSourceEpg = it },
                    snackbarHostState   = snackbarHostState,
                    scope               = scope,
                    proxyEnabled        = proxyEnabled,
                    onProxyEnabled      = { proxyEnabled = it },
                    hwDecode            = hwDecode,
                )
            } else {
                SettingsSingleColumn(
                    settingsViewModel   = settingsViewModel,
                    playlistViewModel   = playlistViewModel,
                    showAddSource       = showAddSource,
                    onShowAddSource     = { showAddSource = it },
                    showServerUrlDialog = showServerUrlDialog,
                    onShowServerUrl     = { showServerUrlDialog = it },
                    urlInput            = urlInput,
                    onUrlInput          = { urlInput = it },
                    showEpgUrlDialog    = showEpgUrlDialog,
                    onShowEpgUrl        = { showEpgUrlDialog = it },
                    epgInput            = epgInput,
                    onEpgInput          = { epgInput = it },
                    editingSourceEpg    = editingSourceEpg,
                    onEditingSourceEpg  = { editingSourceEpg = it },
                    snackbarHostState   = snackbarHostState,
                    scope               = scope,
                    proxyEnabled        = proxyEnabled,
                    onProxyEnabled      = { proxyEnabled = it },
                    hwDecode            = hwDecode,
                )
            }
        }
    }
}

// ── Two-pane (tablet) ─────────────────────────────────────────────────────────

@Composable
private fun SettingsTwoPane(
    settingsViewModel: SettingsViewModel,
    playlistViewModel: PlaylistViewModel,
    showAddSource: Boolean,
    onShowAddSource: (Boolean) -> Unit,
    showServerUrlDialog: Boolean,
    onShowServerUrl: (Boolean) -> Unit,
    urlInput: String,
    onUrlInput: (String) -> Unit,
    showEpgUrlDialog: Boolean,
    onShowEpgUrl: (Boolean) -> Unit,
    epgInput: String,
    onEpgInput: (String) -> Unit,
    editingSourceEpg: Pair<String, String?>?,
    onEditingSourceEpg: (Pair<String, String?>?) -> Unit,
    snackbarHostState: SnackbarHostState,
    scope: kotlinx.coroutines.CoroutineScope,
    proxyEnabled: Boolean,
    onProxyEnabled: (Boolean) -> Unit,
    hwDecode: Boolean,
) {
    val dimens       = NSDimens.current
    val serverUrl    by settingsViewModel.serverUrl.collectAsState()
    val bufferPreset by settingsViewModel.bufferPreset.collectAsState()
    val sources      by playlistViewModel.sources.collectAsState()

    var selectedSection by rememberSaveable { mutableStateOf(SettingsSection.SERVER) }

    Row(modifier = Modifier.fillMaxSize()) {

        // ── Left sidebar ──────────────────────────────────────────────────────
        LazyColumn(
            modifier = Modifier
                .width(SETTINGS_SIDEBAR_WIDTH)
                .fillMaxHeight()
                .background(NSColors.surface),
        ) {
            items(SettingsSection.entries) { section ->
                val isActive = selectedSection == section
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (isActive) NSColors.accentGlow else Color.Transparent)
                        .clickable { selectedSection = section }
                        .padding(
                            horizontal = dimens.spacing.lg,
                            vertical   = dimens.spacing.md,
                        ),
                ) {
                    Text(
                        text  = section.label,
                        style = NSType.bodyMedium(),
                        color = if (isActive) NSColors.accent2 else NSColors.text,
                    )
                }
            }
        }

        // Divider
        Box(
            modifier = Modifier
                .width(0.5.dp)
                .fillMaxHeight()
                .background(NSColors.border),
        )

        // ── Right panel ───────────────────────────────────────────────────────
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(dimens.spacing.lg),
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(horizontal = dimens.spacing.lg, vertical = dimens.spacing.md),
        ) {
            when (selectedSection) {
                SettingsSection.SERVER -> {
                    item { ServerHealthCard(serverUrl = serverUrl) }
                    item {
                        SettingsSection(label = "Server") {
                            SettingsIconRow(
                                iconBackground = COLOR_BLUE,
                                iconTint       = TINT_BLUE,
                                icon           = PhosphorIcons.Regular.Database,
                                title          = "Server URL",
                                subtitle       = serverUrl.removePrefix("http://"),
                                onClick        = { onShowServerUrl(true) },
                            )
                            SettingsDivider()
                            SettingsIconRow(
                                iconBackground = COLOR_GREEN,
                                iconTint       = TINT_GREEN,
                                icon           = PhosphorIcons.Regular.Cpu,
                                title          = "Trigger probe",
                                subtitle       = "Re-validate all stream links",
                                onClick        = {
                                    settingsViewModel.triggerProbe { success ->
                                        scope.launch {
                                            snackbarHostState.showSnackbar(
                                                if (success) "Probe started successfully"
                                                else "Probe failed — check server"
                                            )
                                        }
                                    }
                                },
                            )
                        }
                    }
                }

                SettingsSection.SOURCES -> {
                    item {
                        SettingsSection(label = "Playlist Sources") {
                            sources.forEachIndexed { index, source ->
                                if (index > 0) SettingsDivider()
                                SourceRow(
                                    name         = source.name,
                                    url          = source.url,
                                    epgUrl       = source.epgUrl,
                                    refreshHours = source.refreshIntervalHours,
                                    isHealthy    = true,
                                    onEpgEdit    = { epg -> onEditingSourceEpg(source.id to epg) },
                                    onRefresh    = { playlistViewModel.loadAll() },
                                    onDelete     = {
                                        playlistViewModel.removeSource(source.id)
                                        playlistViewModel.loadAll()
                                    },
                                )
                            }
                            SettingsDivider()
                            AddSourceRow(onClick = { onShowAddSource(true) })
                        }
                    }
                }

                SettingsSection.PLAYBACK -> {
                    item {
                        SettingsSection(label = "Playback") {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        horizontal = dimens.spacing.md,
                                        vertical   = dimens.spacing.sm,
                                    ),
                            ) {
                                RowIcon(background = COLOR_BLUE, tint = TINT_BLUE, icon = PhosphorIcons.Regular.GearSix)
                                Spacer(modifier = Modifier.width(dimens.spacing.sm))
                                Text(
                                    text     = "Buffer preset",
                                    style    = NSType.bodyMedium(),
                                    color    = NSColors.text,
                                    modifier = Modifier.weight(1f),
                                )
                                BufferSegmentedPicker(
                                    selected = bufferPreset,
                                    onSelect = { settingsViewModel.setBufferPreset(it) },
                                )
                            }
                            SettingsDivider()
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        horizontal = dimens.spacing.md,
                                        vertical   = dimens.spacing.sm,
                                    ),
                            ) {
                                RowIcon(background = COLOR_AMBER, tint = TINT_AMBER, icon = PhosphorIcons.Regular.Play)
                                Spacer(modifier = Modifier.width(dimens.spacing.sm))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = "Hardware decode", style = NSType.bodyMedium(), color = NSColors.text)
                                    Text(text = "Always on (ExoPlayer)", style = NSType.caption(), color = NSColors.text3)
                                }
                                NSToggle(checked = hwDecode, onCheckedChange = {}, enabled = false)
                            }
                        }
                    }
                }

                SettingsSection.PROXY -> {
                    item {
                        SettingsSection(label = "Proxy") {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        horizontal = dimens.spacing.md,
                                        vertical   = dimens.spacing.sm,
                                    ),
                            ) {
                                RowIcon(background = COLOR_RED, tint = TINT_RED, icon = PhosphorIcons.Regular.FileLock)
                                Spacer(modifier = Modifier.width(dimens.spacing.sm))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = "Enable proxy", style = NSType.bodyMedium(), color = NSColors.text)
                                    Text(text = "Inject Referer / User-Agent", style = NSType.caption(), color = NSColors.text3)
                                }
                                NSToggle(checked = proxyEnabled, onCheckedChange = { onProxyEnabled(it) })
                            }
                        }
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }

    // ── Dialogs ───────────────────────────────────────────────────────────────
    SettingsDialogs(
        settingsViewModel   = settingsViewModel,
        playlistViewModel   = playlistViewModel,
        showAddSource       = showAddSource,
        onShowAddSource     = onShowAddSource,
        showServerUrlDialog = showServerUrlDialog,
        onShowServerUrl     = onShowServerUrl,
        urlInput            = urlInput,
        onUrlInput          = onUrlInput,
        showEpgUrlDialog    = showEpgUrlDialog,
        onShowEpgUrl        = onShowEpgUrl,
        epgInput            = epgInput,
        onEpgInput          = onEpgInput,
        editingSourceEpg    = editingSourceEpg,
        onEditingSourceEpg  = onEditingSourceEpg,
        sources             = sources,
    )
}

// ── Single column (phone) ─────────────────────────────────────────────────────

@Composable
private fun SettingsSingleColumn(
    settingsViewModel: SettingsViewModel,
    playlistViewModel: PlaylistViewModel,
    showAddSource: Boolean,
    onShowAddSource: (Boolean) -> Unit,
    showServerUrlDialog: Boolean,
    onShowServerUrl: (Boolean) -> Unit,
    urlInput: String,
    onUrlInput: (String) -> Unit,
    showEpgUrlDialog: Boolean,
    onShowEpgUrl: (Boolean) -> Unit,
    epgInput: String,
    onEpgInput: (String) -> Unit,
    editingSourceEpg: Pair<String, String?>?,
    onEditingSourceEpg: (Pair<String, String?>?) -> Unit,
    snackbarHostState: SnackbarHostState,
    scope: kotlinx.coroutines.CoroutineScope,
    proxyEnabled: Boolean,
    onProxyEnabled: (Boolean) -> Unit,
    hwDecode: Boolean,
) {
    val dimens       = NSDimens.current
    val serverUrl    by settingsViewModel.serverUrl.collectAsState()
    val bufferPreset by settingsViewModel.bufferPreset.collectAsState()
    val sources      by playlistViewModel.sources.collectAsState()

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(dimens.spacing.lg),
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = dimens.spacing.md, vertical = dimens.spacing.md),
    ) {
        item { ServerHealthCard(serverUrl = serverUrl) }

        item {
            SettingsSection(label = "Server") {
                SettingsIconRow(
                    iconBackground = COLOR_BLUE,
                    iconTint       = TINT_BLUE,
                    icon           = PhosphorIcons.Regular.Database,
                    title          = "Server URL",
                    subtitle       = serverUrl.removePrefix("http://"),
                    onClick        = { onShowServerUrl(true) },
                )
                SettingsDivider()
                SettingsIconRow(
                    iconBackground = COLOR_GREEN,
                    iconTint       = TINT_GREEN,
                    icon           = PhosphorIcons.Regular.Cpu,
                    title          = "Trigger probe",
                    subtitle       = "Re-validate all stream links",
                    onClick        = {
                        settingsViewModel.triggerProbe { success ->
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    if (success) "Probe started successfully"
                                    else "Probe failed — check server"
                                )
                            }
                        }
                    },
                )
            }
        }

        item {
            SettingsSection(label = "Playlist Sources") {
                sources.forEachIndexed { index, source ->
                    if (index > 0) SettingsDivider()
                    SourceRow(
                        name         = source.name,
                        url          = source.url,
                        epgUrl       = source.epgUrl,
                        refreshHours = source.refreshIntervalHours,
                        isHealthy    = true,
                        onEpgEdit    = { epg -> onEditingSourceEpg(source.id to epg) },
                        onRefresh    = { playlistViewModel.loadAll() },
                        onDelete     = {
                            playlistViewModel.removeSource(source.id)
                            playlistViewModel.loadAll()
                        },
                    )
                }
                SettingsDivider()
                AddSourceRow(onClick = { onShowAddSource(true) })
            }
        }

        item {
            SettingsSection(label = "Playback") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = dimens.spacing.md, vertical = dimens.spacing.sm),
                ) {
                    RowIcon(background = COLOR_BLUE, tint = TINT_BLUE, icon = PhosphorIcons.Regular.GearSix)
                    Spacer(modifier = Modifier.width(dimens.spacing.sm))
                    Text(
                        text     = "Buffer preset",
                        style    = NSType.bodyMedium(),
                        color    = NSColors.text,
                        modifier = Modifier.weight(1f),
                    )
                    BufferSegmentedPicker(
                        selected = bufferPreset,
                        onSelect = { settingsViewModel.setBufferPreset(it) },
                    )
                }
                SettingsDivider()
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = dimens.spacing.md, vertical = dimens.spacing.sm),
                ) {
                    RowIcon(background = COLOR_AMBER, tint = TINT_AMBER, icon = PhosphorIcons.Regular.Play)
                    Spacer(modifier = Modifier.width(dimens.spacing.sm))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Hardware decode", style = NSType.bodyMedium(), color = NSColors.text)
                        Text(text = "Always on (ExoPlayer)", style = NSType.caption(), color = NSColors.text3)
                    }
                    NSToggle(checked = hwDecode, onCheckedChange = {}, enabled = false)
                }
            }
        }

        item {
            SettingsSection(label = "Proxy") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = dimens.spacing.md, vertical = dimens.spacing.sm),
                ) {
                    RowIcon(background = COLOR_RED, tint = TINT_RED, icon = PhosphorIcons.Regular.FileLock)
                    Spacer(modifier = Modifier.width(dimens.spacing.sm))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Enable proxy", style = NSType.bodyMedium(), color = NSColors.text)
                        Text(text = "Inject Referer / User-Agent", style = NSType.caption(), color = NSColors.text3)
                    }
                    NSToggle(checked = proxyEnabled, onCheckedChange = { onProxyEnabled(it) })
                }
            }
        }

        item { Spacer(modifier = Modifier.height(80.dp)) }
    }

    SettingsDialogs(
        settingsViewModel   = settingsViewModel,
        playlistViewModel   = playlistViewModel,
        showAddSource       = showAddSource,
        onShowAddSource     = onShowAddSource,
        showServerUrlDialog = showServerUrlDialog,
        onShowServerUrl     = onShowServerUrl,
        urlInput            = urlInput,
        onUrlInput          = onUrlInput,
        showEpgUrlDialog    = showEpgUrlDialog,
        onShowEpgUrl        = onShowEpgUrl,
        epgInput            = epgInput,
        onEpgInput          = onEpgInput,
        editingSourceEpg    = editingSourceEpg,
        onEditingSourceEpg  = onEditingSourceEpg,
        sources             = sources,
    )
}

// ── Shared dialogs ────────────────────────────────────────────────────────────

@Composable
private fun SettingsDialogs(
    settingsViewModel: SettingsViewModel,
    playlistViewModel: PlaylistViewModel,
    showAddSource: Boolean,
    onShowAddSource: (Boolean) -> Unit,
    showServerUrlDialog: Boolean,
    onShowServerUrl: (Boolean) -> Unit,
    urlInput: String,
    onUrlInput: (String) -> Unit,
    showEpgUrlDialog: Boolean,
    onShowEpgUrl: (Boolean) -> Unit,
    epgInput: String,
    onEpgInput: (String) -> Unit,
    editingSourceEpg: Pair<String, String?>?,
    onEditingSourceEpg: (Pair<String, String?>?) -> Unit,
    sources: List<com.nativestream.android.domain.model.PlaylistSource>,
) {
    val dimens = NSDimens.current

    if (showAddSource) {
        AddSourceSheet(
            onDone            = { onShowAddSource(false) },
            playlistViewModel = playlistViewModel,
        )
    }

    if (showServerUrlDialog) {
        AlertDialog(
            onDismissRequest = { onShowServerUrl(false) },
            containerColor   = NSColors.surface2,
            title = { Text("Server URL", style = NSType.heading(), color = NSColors.text) },
            text  = {
                NSTextField(
                    value         = urlInput,
                    onValueChange = { onUrlInput(it) },
                    placeholder   = "http://192.168.1.x:8888",
                    modifier      = settingsFieldModifier(),
                )
            },
            confirmButton = {
                Text(
                    "Save",
                    style    = NSType.captionMedium(),
                    color    = NSColors.accent,
                    modifier = Modifier
                        .clickable {
                            settingsViewModel.setServerUrl(urlInput)
                            onShowServerUrl(false)
                        }
                        .padding(8.dp),
                )
            },
            dismissButton = {
                Text(
                    "Cancel",
                    style    = NSType.captionMedium(),
                    color    = NSColors.text3,
                    modifier = Modifier.clickable { onShowServerUrl(false) }.padding(8.dp),
                )
            },
        )
    }

    if (showEpgUrlDialog) {
        val epgUrl by settingsViewModel.epgUrl.collectAsState()
        AlertDialog(
            onDismissRequest = { onShowEpgUrl(false) },
            containerColor   = NSColors.surface2,
            title = { Text("EPG URL", style = NSType.heading(), color = NSColors.text) },
            text  = {
                NSTextField(
                    value         = epgInput.ifEmpty { epgUrl ?: "" },
                    onValueChange = { onEpgInput(it) },
                    placeholder   = "http://.../epg.xml",
                    modifier      = settingsFieldModifier(),
                )
            },
            confirmButton = {
                Text(
                    "Save",
                    style    = NSType.captionMedium(),
                    color    = NSColors.accent,
                    modifier = Modifier
                        .clickable {
                            settingsViewModel.setEpgUrl(epgInput)
                            onShowEpgUrl(false)
                        }
                        .padding(8.dp),
                )
            },
            dismissButton = {
                Text(
                    "Cancel",
                    style    = NSType.captionMedium(),
                    color    = NSColors.text3,
                    modifier = Modifier.clickable { onShowEpgUrl(false) }.padding(8.dp),
                )
            },
        )
    }

    editingSourceEpg?.let { (sourceId, currentEpg) ->
        var localEpgInput by remember { mutableStateOf(currentEpg ?: "") }
        AlertDialog(
            onDismissRequest = { onEditingSourceEpg(null) },
            containerColor   = NSColors.surface2,
            title = { Text("EPG URL", style = NSType.heading(), color = NSColors.text) },
            text  = {
                Column(verticalArrangement = Arrangement.spacedBy(dimens.spacing.sm)) {
                    Text(
                        "Link an EPG source to this playlist.",
                        style = NSType.caption(),
                        color = NSColors.text3,
                    )
                    NSTextField(
                        value         = localEpgInput,
                        onValueChange = { localEpgInput = it },
                        placeholder   = "http://.../epg.xml",
                        modifier      = settingsFieldModifier(),
                    )
                }
            },
            confirmButton = {
                Text(
                    "Save",
                    style    = NSType.captionMedium(),
                    color    = NSColors.accent,
                    modifier = Modifier
                        .clickable {
                            val updated = sources.find { it.id == sourceId }
                                ?.copy(epgUrl = localEpgInput.trim().ifEmpty { null })
                            updated?.let { playlistViewModel.updateSource(it) }
                            onEditingSourceEpg(null)
                        }
                        .padding(8.dp),
                )
            },
            dismissButton = {
                Text(
                    "Clear",
                    style    = NSType.captionMedium(),
                    color    = NSColors.text3,
                    modifier = Modifier
                        .clickable {
                            val updated = sources.find { it.id == sourceId }?.copy(epgUrl = null)
                            updated?.let { playlistViewModel.updateSource(it) }
                            onEditingSourceEpg(null)
                        }
                        .padding(8.dp),
                )
            },
        )
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