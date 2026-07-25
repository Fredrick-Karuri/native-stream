// app/src/main/java/com/nativestream/android/ui/screens/settings/ChannelHeaderRow.kt
//
// Expandable per-channel header editor — same expand/collapse pattern as
// SourceRow's EPG editor. Lists only server-managed channels (hasActiveLink).

package com.nativestream.android.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.regular.Trash
import com.nativestream.android.data.remote.ChannelResponse
import com.nativestream.android.ui.components.NSTextField
import com.nativestream.android.ui.theme.NSColors
import com.nativestream.android.ui.theme.NSDimens
import com.nativestream.android.ui.theme.NSType
import com.nativestream.android.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch

@Composable
fun ChannelHeaderRow(
    channel: ChannelResponse,
    settingsViewModel: SettingsViewModel,
) {
    val dimens = NSDimens.current
    val scope = rememberCoroutineScopeCompat()

    var expanded by remember { mutableStateOf(false) }
    var isLoadingHeaders by remember { mutableStateOf(false) }
    var headers by remember { mutableStateOf(listOf<Pair<String, String>>()) }
    var newKey by remember { mutableStateOf("") }
    var newValue by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }
    var loadedOnce by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(dimens.radius.lg))
            .background(NSColors.surface2),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = dimens.spacing.md, vertical = dimens.spacing.sm),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = channel.name, style = NSType.bodyMedium(), color = NSColors.text)
                Text(text = channel.groupTitle, style = NSType.monoSmall(), color = NSColors.text3)
            }
            TextButton(onClick = {
                expanded = !expanded
                if (expanded && !loadedOnce) {
                    loadedOnce = true
                    isLoadingHeaders = true
                    scope.launch {
                        headers = settingsViewModel.getChannelHeaders(channel.id).toList()
                        isLoadingHeaders = false
                    }
                }
            }) {
                Text(if (expanded) "Close" else "Edit Headers", style = NSType.caption())
            }
        }

        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(dimens.spacing.md),
                verticalArrangement = Arrangement.spacedBy(dimens.spacing.sm),
            ) {
                if (isLoadingHeaders) {
                    CircularProgressIndicator(modifier = Modifier.width(20.dp))
                } else if (headers.isEmpty()) {
                    Text("No custom headers", style = NSType.caption(), color = NSColors.text3)
                } else {
                    headers.forEachIndexed { index, (key, value) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(dimens.spacing.sm),
                        ) {
                            Text(
                                text = key,
                                style = NSType.monoSmall(),
                                color = NSColors.text2,
                                modifier = Modifier.width(110.dp),
                            )
                            NSTextField(
                                value = value,
                                onValueChange = { newVal ->
                                    headers = headers.toMutableList().also { it[index] = key to newVal }
                                },
                                placeholder = "value",
                            )
                            Icon(
                                imageVector = PhosphorIcons.Regular.Trash,
                                contentDescription = "Remove header",
                                tint = NSColors.text3,
                                modifier = Modifier
                                    .clickable { headers = headers.toMutableList().also { it.removeAt(index) } },
                            )
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(dimens.spacing.sm)) {
                    NSTextField(value = newKey, onValueChange = { newKey = it }, placeholder = "key")
                    NSTextField(value = newValue, onValueChange = { newValue = it }, placeholder = "value")
                    TextButton(onClick = {
                        if (newKey.isNotBlank()) {
                            headers = headers + (newKey to newValue)
                            newKey = ""; newValue = ""
                        }
                    }) { Text("+ Add") }
                }

                saveError?.let {
                    Text(it, style = NSType.caption(), color = NSColors.live)
                }

                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(
                        enabled = !isSaving,
                        onClick = {
                            isSaving = true
                            saveError = null
                            scope.launch {
                                val ok = settingsViewModel.saveChannelHeaders(channel.id, headers.toMap())
                                isSaving = false
                                if (!ok) saveError = "Save failed — check connection"
                            }
                        },
                    ) { Text(if (isSaving) "Saving…" else "Save") }
                }
            }
        }
    }
}

@Composable
private fun rememberCoroutineScopeCompat() = androidx.compose.runtime.rememberCoroutineScope()