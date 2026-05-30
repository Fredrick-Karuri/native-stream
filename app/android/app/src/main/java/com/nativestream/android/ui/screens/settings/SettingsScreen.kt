// app/src/main/java/com/nativestream/android/ui/screens/settings/SettingsScreen.kt
//
// Single LazyColumn with section tabs: Sources · Playback · Server · Proxy · Discovery.
// Health dot + server URL shown at bottom of nav.

package com.nativestream.android.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nativestream.android.ui.theme.NSColors
import com.nativestream.android.ui.theme.NSDimens
import com.nativestream.android.ui.theme.NSType
import com.nativestream.android.ui.viewmodel.PlaylistViewModel
import com.nativestream.android.ui.viewmodel.SettingsViewModel

private val SIDEBAR_WIDTH = 200.dp

private enum class SettingsSection(val label: String) {
    SOURCES("Sources"),
    PLAYBACK("Playback"),
    SERVER("Server"),
    PROXY("Proxy"),
    DISCOVERY("Discovery"),
}

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    settingsViewModel: SettingsViewModel  = hiltViewModel(),
    playlistViewModel: PlaylistViewModel  = hiltViewModel(),
) {
    val serverUrl by settingsViewModel.serverUrl.collectAsState()
    var selected  by remember { mutableStateOf(SettingsSection.SOURCES) }

    Column(modifier = modifier.fillMaxSize().background(NSColors.bg)) {

        // Top bar
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(NSColors.surface)
                .padding(horizontal = NSDimens.current.spacing.xl, vertical = NSDimens.current.spacing.md),
        ) {
            Text(text = "Settings", style = NSType.heading(), color = NSColors.text)
        }
        Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(NSColors.border))

        Row(modifier = Modifier.fillMaxSize()) {

            // ── Sidebar ───────────────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .width(SIDEBAR_WIDTH)
                    .fillMaxHeight()
                    .background(NSColors.surface)
                    .padding(NSDimens.current.spacing.sm),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                SettingsSection.entries.forEach { section ->
                    SettingsNavItem(
                        label    = section.label,
                        isActive = selected == section,
                        onClick  = { selected = section },
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                ServerHealthCard(serverUrl = serverUrl)
            }

            Box(modifier = Modifier.width(0.5.dp).fillMaxHeight().background(NSColors.border))

            // ── Panel ─────────────────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(NSColors.bg)
                    .verticalScroll(rememberScrollState())
                    .padding(NSDimens.current.spacing.xxl),
                verticalArrangement = Arrangement.spacedBy(NSDimens.current.spacing.xl),
            ) {
                when (selected) {
                    SettingsSection.SOURCES   -> SourcesSection(playlistViewModel, settingsViewModel)
                    SettingsSection.PLAYBACK  -> PlaybackSection(settingsViewModel)
                    SettingsSection.SERVER    -> ServerSection(settingsViewModel)
                    SettingsSection.PROXY     -> ProxySection()
                    SettingsSection.DISCOVERY -> DiscoverySection()
                }
            }
        }
    }
}

// ── Nav item ──────────────────────────────────────────────────────────────────

@Composable
private fun SettingsNavItem(label: String, isActive: Boolean, onClick: () -> Unit) {
    val dimens = NSDimens.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.spacing.sm),
        modifier = Modifier
            .fillMaxWidth()
            .height(dimens.settings.navItemHeight)
            .clip(RoundedCornerShape(dimens.radius.md))
            .background(if (isActive) NSColors.accentGlow else NSColors.bg.copy(alpha = 0f))
            .border(
                0.5.dp,
                if (isActive) NSColors.accentBorder else NSColors.bg.copy(alpha = 0f),
                RoundedCornerShape(dimens.radius.md),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = dimens.spacing.sm),
    ) {
        Text(
            text  = label,
            style = NSType.captionMedium(),
            color = if (isActive) NSColors.accent2 else NSColors.text2,
        )
    }
}

// ── Server health card ────────────────────────────────────────────────────────

@Composable
private fun ServerHealthCard(serverUrl: String) {
    val dimens = NSDimens.current
    Column(
        verticalArrangement = Arrangement.spacedBy(dimens.spacing.sm),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(dimens.radius.md))
            .background(NSColors.surface2)
            .border(0.5.dp, NSColors.border2, RoundedCornerShape(dimens.radius.md))
            .padding(dimens.spacing.md),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.spacing.sm),
        ) {
            NSHealthDot(score = if (serverUrl.isNotBlank()) 1.0 else 0.0)
            Text(
                text  = if (serverUrl.isNotBlank()) "Server configured" else "Not configured",
                style = NSType.caption(),
                color = NSColors.text2,
            )
        }
        Text(
            text     = serverUrl.ifEmpty { "No server URL set" },
            style    = NSType.monoSmall(),
            color    = NSColors.text3,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}