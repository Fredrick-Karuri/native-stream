package com.nativestream.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Stub theme for AND-001 bootstrap — compiles and launches with correct dark background.
 * Full NS design token implementation lives in AND-002 (NSTheme.kt full version).
 */

// ── Palette stubs (real values defined in AND-002) ──────────────────────────
internal val NS_Background = Color(0xFF060810)
internal val NS_Surface    = Color(0xFF131826)
internal val NS_Accent     = Color(0xFF0EA5E9)
internal val NS_OnSurface  = Color(0xFFE8EAF0)

private val NSDarkColorScheme = darkColorScheme(
    primary        = NS_Accent,
    background     = NS_Background,
    surface        = NS_Surface,
    onBackground   = NS_OnSurface,
    onSurface      = NS_OnSurface,
)

@Composable
fun NSTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = NSDarkColorScheme,
        content     = content,
    )
}