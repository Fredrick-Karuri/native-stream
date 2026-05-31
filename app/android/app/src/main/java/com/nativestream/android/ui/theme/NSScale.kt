// app/src/main/java/com/nativestream/android/ui/theme/NSScale.kt
//
// NS Design System — UI scale multiplier (AND-002)
// Mirrors Mac @AppStorage("uiScale") — default 1.0, range 0.8–1.5.
// Backed by DataStore and exposed as a CompositionLocal so every
// sizing token reads it without threading the value through parameters.
//
// Access:  NSScale.current  (inside composition)
// Update:  NSScaleProvider wraps the root composable

package com.nativestream.android.ui.theme

import androidx.compose.runtime.compositionLocalOf

const val NS_SCALE_DEFAULT = 1.4f
const val NS_SCALE_MIN     = 0.8f
const val NS_SCALE_MAX     = 1.5f

/** CompositionLocal carrying the active UI scale multiplier. */
val LocalNSScale = compositionLocalOf { NS_SCALE_DEFAULT }

/** Convenience accessor — mirrors NS.scale on the Mac side. */
object NSScale {
    val current: Float
        @androidx.compose.runtime.Composable
        get() = LocalNSScale.current
}