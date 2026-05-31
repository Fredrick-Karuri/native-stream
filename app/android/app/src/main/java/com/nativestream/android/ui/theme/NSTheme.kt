// app/src/main/java/com/nativestream/android/ui/theme/NSTheme.kt
//
// NS Design System — Theme composition root
// Provides all design tokens (colours, typography, spacing) to the
// composition tree via CompositionLocals.
//
// Scale is read from DataStore via [scaleState]; defaults to NS_SCALE_DEFAULT.
// Wrap the entire app with NSTheme at the setContent call-site in MainActivity.
//
// Usage:
//   NSTheme { /* all screens */ }
//   NSColors.accent          — colours
//   NSType.heading()         — text styles
//   NSDimens.current.spacing.lg — spacing tokens

package com.nativestream.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

// ── Material3 colour scheme — backed by NS palette ───────────────────────────

private val nsDarkColorScheme = darkColorScheme(
    primary          = NSColors.accent,
    onPrimary        = NSColors.bg,
    primaryContainer = NSColors.accentGlow,
    secondary        = NSColors.accent2,
    onSecondary      = NSColors.bg,
    background       = NSColors.bg,
    onBackground     = NSColors.text,
    surface          = NSColors.surface,
    onSurface        = NSColors.text,
    surfaceVariant   = NSColors.surface2,
    onSurfaceVariant = NSColors.text2,
    outline          = NSColors.border2,
    error            = NSColors.live,
    onError          = NSColors.text,
)

// ── Root theme composable ─────────────────────────────────────────────────────

/**
 * Root composable that injects all NS design tokens into the composition.
 *
 * @param scale  UI scale multiplier (0.8–1.5). Provide via DataStore-backed
 *               state from [SettingsDataStore] once AND-024 is implemented.
 *               Defaults to [NS_SCALE_DEFAULT] until then.
 * @param content The app content tree.
 */
@Composable
fun NSTheme(
    scale: Float = NS_SCALE_DEFAULT,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalNSScale  provides scale,
        LocalNSDimens provides NSDimens.forScale(scale),
    ) {
        MaterialTheme(
            colorScheme = nsDarkColorScheme,
            content     = content,
        )
    }
}