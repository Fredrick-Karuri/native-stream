// app/src/main/java/com/nativestream/android/MainActivity.kt
//
// Entry point. Enables edge-to-edge, provides NSTheme, mounts AppNavHost.
// Back button on Now tab exits the app — handled by NavHost default behaviour.

package com.nativestream.android


import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.ui.Modifier
import com.nativestream.android.ui.navigation.AppNavHost
import com.nativestream.android.ui.theme.NSColors
import com.nativestream.android.ui.theme.NSTheme
import androidx.activity.viewModels
import dagger.hilt.android.AndroidEntryPoint
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.CompositionLocalProvider
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.nativestream.android.ui.LocalWindowSizeClass
import com.nativestream.android.ui.viewmodel.SettingsViewModel
import com.nativestream.android.ui.viewmodel.PlayerViewModel

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val settingsViewModel: SettingsViewModel by viewModels()
    private val playerViewModel: PlayerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        setContent {
            val windowSizeClass = calculateWindowSizeClass(this)
            CompositionLocalProvider(LocalWindowSizeClass provides windowSizeClass) {
                NSTheme {
                    Surface(modifier = Modifier.fillMaxSize(), color = NSColors.bg) {
                        AppNavHost()
                    }
                }
            }
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        if (isInPictureInPictureMode) {
            playerViewModel.onEnteredPip()
        } else {
            playerViewModel.onExitedPip()
        }
    }

    override fun onResume() {
        super.onResume()
        settingsViewModel.checkHealth()
    }

}