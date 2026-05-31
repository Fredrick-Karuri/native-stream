// app/src/main/java/com/nativestream/android/ui/screens/browse/AddChannelSheet.kt
//
// Bottom sheet triggered from Browse FAB:
//   - Fields: Stream URL (required), Name (required), Group, TVG ID
//   - Validation prevents empty submit
//   - Inline error display on failure
//   - Success → calls onDone() so caller reloads playlist

package com.nativestream.android.ui.screens.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nativestream.android.ui.components.NSTextField
import com.nativestream.android.ui.theme.NSColors
import com.nativestream.android.ui.theme.NSDimens
import com.nativestream.android.ui.theme.NSType
import com.nativestream.android.ui.viewmodel.ChannelManagerViewModel
import com.nativestream.android.ui.viewmodel.PlaylistViewModel

private const val DEFAULT_GROUP = "General"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddChannelSheet(
    onDone: () -> Unit,
    playlistViewModel: PlaylistViewModel,
    modifier: Modifier = Modifier,
    channelManagerViewModel: ChannelManagerViewModel = hiltViewModel(),
) {
    val sheetState  = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isLoading   by channelManagerViewModel.isLoading.collectAsState()
    val serverError by channelManagerViewModel.error.collectAsState()

    var name       by remember { mutableStateOf("") }
    var streamUrl  by remember { mutableStateOf("") }
    var groupTitle by remember { mutableStateOf(DEFAULT_GROUP) }
    var tvgId      by remember { mutableStateOf("") }

    val canSubmit = name.isNotBlank() && streamUrl.isNotBlank() && !isLoading

    ModalBottomSheet(
        onDismissRequest  = onDone,
        sheetState        = sheetState,
        containerColor    = NSColors.surface,
        modifier          = modifier,
    ) {
        AddChannelContent(
            name           = name,
            streamUrl      = streamUrl,
            groupTitle     = groupTitle,
            tvgId          = tvgId,
            isLoading      = isLoading,
            error          = serverError,
            canSubmit      = canSubmit,
            onNameChange       = { name = it },
            onStreamUrlChange  = { streamUrl = it },
            onGroupTitleChange = { groupTitle = it },
            onTvgIdChange      = { tvgId = it },
            onCancel           = onDone,
            onSubmit           = {
                val keywords = listOf(name.lowercase().replace(" ", ""))
                channelManagerViewModel.addChannel(
                    name       = name,
                    groupTitle = groupTitle.ifEmpty { DEFAULT_GROUP },
                    tvgId      = tvgId,
                    logoUrl    = "",
                    streamUrl  = streamUrl,
                    keywords   = keywords,
                    onSuccess  = {
                        playlistViewModel.loadAll()
                        onDone()
                    },
                )
            },
        )
    }
}

// ── Sheet content ─────────────────────────────────────────────────────────────

@Composable
private fun AddChannelContent(
    name: String,
    streamUrl: String,
    groupTitle: String,
    tvgId: String,
    isLoading: Boolean,
    error: String?,
    canSubmit: Boolean,
    onNameChange: (String) -> Unit,
    onStreamUrlChange: (String) -> Unit,
    onGroupTitleChange: (String) -> Unit,
    onTvgIdChange: (String) -> Unit,
    onCancel: () -> Unit,
    onSubmit: () -> Unit,
) {
    val dimens = NSDimens.current

    Column(
        verticalArrangement = Arrangement.spacedBy(dimens.spacing.xl),
        modifier = Modifier
            .fillMaxWidth()
            .padding(dimens.spacing.xl),
    ) {
        Text(text = "Add Channel", style = NSType.heading(), color = NSColors.text)

        // ── Fields ────────────────────────────────────────────────────────────
        Column(verticalArrangement = Arrangement.spacedBy(dimens.spacing.md)) {
            LabelledField(label = "Stream URL *", placeholder = "https://…", value = streamUrl, onChange = onStreamUrlChange)
            LabelledField(label = "Name *",       placeholder = "e.g. NBC",   value = name,      onChange = onNameChange)
            LabelledField(label = "Group",        placeholder = "e.g. Sports", value = groupTitle, onChange = onGroupTitleChange)
            LabelledField(label = "TVG ID",       placeholder = "optional",    value = tvgId,     onChange = onTvgIdChange)
        }

        // ── Inline error ──────────────────────────────────────────────────────
        error?.let {
            Text(
                text     = it,
                style    = NSType.monoSmall(),
                color    = NSColors.live,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(dimens.radius.md))
                    .background(NSColors.live.copy(alpha = 0.08f))
                    .border(0.5.dp, NSColors.live.copy(alpha = 0.2f), RoundedCornerShape(dimens.radius.md))
                    .padding(dimens.spacing.sm),
            )
        }

        // ── Action buttons ────────────────────────────────────────────────────
        Row(
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            SheetButton(
                label    = "Cancel",
                enabled  = true,
                isPrimary = false,
                onClick  = onCancel,
            )
            Spacer(modifier = Modifier.width(dimens.spacing.sm))
            SheetButton(
                label     = if (isLoading) "Adding…" else "Add Channel",
                enabled   = canSubmit,
                isPrimary = true,
                onClick   = onSubmit,
            )
        }
    }
}

@Composable
private fun LabelledField(
    label: String,
    placeholder: String,
    value: String,
    onChange: (String) -> Unit,
) {
    val dimens = NSDimens.current
    Column(verticalArrangement = Arrangement.spacedBy(dimens.spacing.xs)) {
        Text(text = label, style = NSType.monoSmall(), color = NSColors.text3)
        NSTextField(value = value, onValueChange = onChange, placeholder = placeholder)
    }
}