// app/src/main/java/com/nativestream/android/MainActivity.kt
//
// NS-008: Main Activity
// Entry point. Enables edge-to-edge, provides NSTheme, mounts AppNavHost.
// Back button on Now tab exits the app — handled by NavHost default behaviour.

package com.nativestream.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.nativestream.android.ui.navigation.AppNavHost
import com.nativestream.android.ui.theme.NSTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NSTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavHost()
                }
            }
        }
    }
}