package com.nativestream.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.nativestream.android.ui.theme.NSTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NSTheme {
                // Navigation shell wired up in AND-008
                Surface {
                    AppShellPlaceholder()
                }
            }
        }
    }
}

/**
 * Placeholder composable — replaced by the real NavHost in AND-008.
 * Exists solely so AND-001 compiles and launches.
 */
@Composable
private fun AppShellPlaceholder() {
    // TODO AND-008: replace with AppNavHost()
}

@Preview(showBackground = true)
@Composable
private fun DefaultPreview() {
    NSTheme {
        AppShellPlaceholder()
    }
}