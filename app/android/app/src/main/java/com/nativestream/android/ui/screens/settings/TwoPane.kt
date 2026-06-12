package com.nativestream.android.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.regular.Cpu
import com.adamglin.phosphoricons.regular.Database
import com.adamglin.phosphoricons.regular.FileLock
import com.adamglin.phosphoricons.regular.GearSix
import com.adamglin.phosphoricons.regular.Play
import com.nativestream.android.ui.theme.NSColors
import com.nativestream.android.ui.theme.NSDimens
import com.nativestream.android.ui.theme.NSType
import com.nativestream.android.ui.viewmodel.PlaylistViewModel
import com.nativestream.android.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch

private val SETTINGS_SIDEBAR_WIDTH = 200.dp

@Composable
fun SettingsTwoPane(
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
    val serverReachable by settingsViewModel.serverReachable.collectAsState()

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
                    item {
                        ServerHealthCard(
                            serverUrl       = serverUrl,
                            serverReachable = serverReachable,
                            onScan          = { settingsViewModel.startDiscovery() },
                        )
                    }
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