package com.nativestream.android.ui.screens.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.nativestream.android.ui.components.NSTextField
import com.nativestream.android.ui.components.SheetActionButton
import com.nativestream.android.ui.theme.NSColors
import com.nativestream.android.ui.theme.NSDimens
import com.nativestream.android.ui.theme.NSType

private const val IPTV_ORG_EPG =
    "https://iptv-org.github.io/epg/guides/en/xmltv.xml"
@Composable
fun EpgScreen(
    onSave: (String) -> Unit,
    onSkip: () -> Unit,
) {
    val dimens = NSDimens.current
    var epgInput by remember { mutableStateOf("") }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxSize()
            .background(NSColors.bg)
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(dimens.spacing.xxl),
    ) {
        Spacer(modifier = Modifier.weight(1f))

        StepContainer {
            StepIcon("📺")
            Text(
                text  = "Add a TV Guide",
                style = NSType.display(),
                color = NSColors.text,
            )
            Text(
                text  = "A TV Guide shows upcoming match times and what's on.\nYour server didn't return one automatically.",
                style = NSType.body(),
                color = NSColors.text3,
            )
            NSTextField(
                value         = epgInput,
                onValueChange = { epgInput = it },
                placeholder   = "http://192.168.1.42:8888/epg.xml",
            )
            SheetActionButton(
                label     = "Use IPTV-org guide",
                isPrimary = false,
                enabled   = true,
                onClick   = { epgInput = IPTV_ORG_EPG },
                modifier  = Modifier.fillMaxWidth(),
            )
            StepButtons(
                skipLabel      = "Skip for now",
                primaryLabel   = "Add Guide",
                primaryEnabled = epgInput.isNotBlank(),
                onSkip         = onSkip,
                onPrimary      = { onSave(epgInput) },
            )
        }

        Spacer(modifier = Modifier.weight(1f))
    }
}
