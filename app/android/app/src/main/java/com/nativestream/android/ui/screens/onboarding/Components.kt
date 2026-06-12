package com.nativestream.android.ui.screens.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import com.nativestream.android.ui.components.SheetActionButton
import com.nativestream.android.ui.theme.NSDimens


// ── Shared composables ────────────────────────────────────────────────────────

@Composable
fun StepContainer(content: @Composable () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(NSDimens.current.spacing.xl),
        modifier = Modifier.padding(NSDimens.current.spacing.xxl),
    ) { content() }
}

@Composable
fun StepIcon(emoji: String) {
    Text(text = emoji, fontSize = 48.sp)
}

@Composable
fun StepButtons(
    skipLabel: String,
    primaryLabel: String,
    primaryEnabled: Boolean,
    onSkip: () -> Unit,
    onPrimary: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(NSDimens.current.spacing.md)) {
        SheetActionButton(label = skipLabel,    isPrimary = false, enabled = true,           onClick = onSkip)
        SheetActionButton(label = primaryLabel, isPrimary = true,  enabled = primaryEnabled, onClick = onPrimary)
    }
}
