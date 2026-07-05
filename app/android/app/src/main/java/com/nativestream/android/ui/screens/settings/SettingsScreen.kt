/**
 * app/src/main/java/com/nativestream/android/ui/screens/settings/SettingsScreen.kt
 *
 * Settings Screen — adaptive layout:
 *   Compact       → single scrollable column (phone)
 *   Expanded      → two-pane sidebar layout (tablet)
 *
 */

package com.nativestream.android.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowHeightSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nativestream.android.ui.LocalWindowSizeClass
import com.nativestream.android.ui.theme.NSColors
import com.nativestream.android.ui.theme.NSDimens
import com.nativestream.android.ui.theme.NSType
import com.nativestream.android.ui.viewmodel.ChannelLoadingViewModel
import com.nativestream.android.ui.viewmodel.SettingsViewModel
import com.nativestream.android.ui.viewmodel.SourceViewModel

val COLOR_BLUE   = Color(0xFF0EA5E9).copy(alpha = 0.12f)
val COLOR_GREEN  = Color(0xFF10B981).copy(alpha = 0.12f)
val COLOR_AMBER  = Color(0xFFF59E0B).copy(alpha = 0.12f)
val COLOR_RED    = Color(0xFFEF4444).copy(alpha = 0.12f)
val TINT_BLUE    = Color(0xFF38BDF8)
val TINT_GREEN   = Color(0xFF10B981)
val TINT_AMBER   = Color(0xFFF59E0B)
val TINT_RED     = Color(0xFFEF4444)

enum class SettingsSection {
    SERVER, SOURCES, PLAYBACK, PROXY, SYSTEM;
    val label get() = when (this) {
        SERVER   -> "Server"
        SOURCES  -> "Sources"
        PLAYBACK -> "Playback"
        PROXY    -> "Proxy"
        SYSTEM   -> "System"
    }
}


@Composable
fun settingsFieldModifier(): Modifier {
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
    settingsViewModel: SettingsViewModel        = hiltViewModel(),
    sourceViewModel:   SourceViewModel          = hiltViewModel(),
    loadingViewModel:  ChannelLoadingViewModel  = hiltViewModel(),
) {
    val dimens    = NSDimens.current
    val serverUrl by settingsViewModel.serverUrl.collectAsState()
    LaunchedEffect(Unit) { settingsViewModel.checkHealth() }

    val proxyEnabled by settingsViewModel.proxyEnabled.collectAsState()
    var hwDecode     by remember { mutableStateOf(true) }

    var showServerUrlDialog by remember { mutableStateOf(false) }
    var urlInput            by remember(serverUrl) { mutableStateOf(serverUrl) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope             = rememberCoroutineScope()
    var showAddSource     by remember { mutableStateOf(false) }

    var showEpgUrlDialog by remember { mutableStateOf(false) }
    var epgInput         by remember { mutableStateOf("") }

    var editingSourceEpg by remember { mutableStateOf<Pair<String, String?>?>(null) }

    val windowSizeClass = LocalWindowSizeClass.current
    val isTablet = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Expanded
            && windowSizeClass.heightSizeClass != WindowHeightSizeClass.Compact

    val discoveredUrl by settingsViewModel.discoveredUrl.collectAsState()
    LaunchedEffect(discoveredUrl) {
        discoveredUrl?.let { settingsViewModel.confirmDiscoveredUrl(it) }
    }

    Scaffold(
        snackbarHost   = { SnackbarHost(snackbarHostState) },
        containerColor = NSColors.bg,
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(NSColors.bg)
        ) {

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

            if (isTablet) {
                SettingsTwoPane(
                    settingsViewModel   = settingsViewModel,
                    sourceViewModel     = sourceViewModel,
                    loadingViewModel    = loadingViewModel,
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
                    onProxyEnabled      = { settingsViewModel.setProxyEnabled(it) },
                    hwDecode            = hwDecode,
                )
            } else {
                SettingsSingleColumn(
                    settingsViewModel   = settingsViewModel,
                    sourceViewModel     = sourceViewModel,
                    loadingViewModel    = loadingViewModel,
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
                    onProxyEnabled      = { settingsViewModel.setProxyEnabled(it) },
                    hwDecode            = hwDecode,
                )
            }
        }
    }
}