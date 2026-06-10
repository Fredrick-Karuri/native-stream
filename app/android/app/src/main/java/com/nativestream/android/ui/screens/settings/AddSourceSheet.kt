package com.nativestream.android.ui.screens.settings

// AddSourceSheet.kt

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.nativestream.android.domain.model.PlaylistSource
import com.nativestream.android.ui.components.NSTextField
import com.nativestream.android.ui.screens.browse.SheetButton
import com.nativestream.android.ui.theme.NSColors
import com.nativestream.android.ui.theme.NSDimens
import com.nativestream.android.ui.theme.NSType
import com.nativestream.android.ui.viewmodel.PlaylistViewModel
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSourceSheet(
    onDone: () -> Unit,
    playlistViewModel: PlaylistViewModel,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name     by remember { mutableStateOf("") }
    var url      by remember { mutableStateOf("") }
    var refresh  by remember { mutableStateOf("6") }
    var epgUrl by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDone,
        sheetState       = sheetState,
        containerColor   = NSColors.bg,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(NSDimens.current.spacing.xl),
            modifier = Modifier.fillMaxWidth().padding(NSDimens.current.spacing.xxl),
        ) {
            Text("Add Playlist Source", style = NSType.heading(), color = NSColors.text)

            Column(verticalArrangement = Arrangement.spacedBy(NSDimens.current.spacing.xs)) {
                Text("Name", style = NSType.caption(), color = NSColors.text3)
                NSTextField(value = name, onValueChange = { name = it }, placeholder = "My Playlist")
            }
            Column(verticalArrangement = Arrangement.spacedBy(NSDimens.current.spacing.xs)) {
                Text("URL", style = NSType.caption(), color = NSColors.text3)
                NSTextField(value = url, onValueChange = { url = it }, placeholder = "http://...")
            }
            Column(verticalArrangement = Arrangement.spacedBy(NSDimens.current.spacing.xs)) {
                Text("EPG URL (optional)", style = NSType.caption(), color = NSColors.text3)
                NSTextField(value = epgUrl, onValueChange = { epgUrl = it }, placeholder = "http://.../epg.xml")
            }
            Column(verticalArrangement = Arrangement.spacedBy(NSDimens.current.spacing.xs)) {
                Text("Refresh interval (hours)", style = NSType.caption(), color = NSColors.text3)
                NSTextField(value = refresh, onValueChange = { refresh = it }, placeholder = "6")
            }

            Row(modifier = Modifier.fillMaxWidth()) {
                SheetButton(label = "Cancel", isPrimary = false, enabled = true, onClick = onDone)
                Spacer(modifier = Modifier.weight(1f))
                SheetButton(
                    label     = "Add",
                    isPrimary = true,
                    enabled   = name.isNotBlank() && url.isNotBlank(),
                    onClick   = {
                        playlistViewModel.addSource(
                            PlaylistSource(
                                id = UUID.randomUUID().toString(),
                                name = name.trim(),
                                colorHex = PlaylistSource.COLOR_BLUE,
                                url = url.trim(),
                                epgUrl               = epgUrl.trim().ifEmpty { null },
                                refreshIntervalHours = refresh.toIntOrNull() ?: 6,
                            )
                        )
                        playlistViewModel.loadAll()
                        onDone()
                    },
                )
            }
        }
    }
}