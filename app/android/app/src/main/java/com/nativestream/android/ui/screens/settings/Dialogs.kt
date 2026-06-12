package com.nativestream.android.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nativestream.android.ui.components.AddSourceSheet
import com.nativestream.android.ui.components.NSTextField
import com.nativestream.android.ui.theme.NSColors
import com.nativestream.android.ui.theme.NSDimens
import com.nativestream.android.ui.theme.NSType
import com.nativestream.android.ui.viewmodel.PlaylistViewModel
import com.nativestream.android.ui.viewmodel.SettingsViewModel


@Composable
fun SettingsDialogs(
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