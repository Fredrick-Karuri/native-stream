// app/src/main/java/com/nativestream/android/ui/navigation/AppDestination.kt
//
// NS-008: App Navigation Destinations
// Sealed hierarchy mirroring AppDestination from SportNavRail.swift.
// Used by NavHost and bottom navigation bar.

package com.nativestream.android.ui.navigation

sealed class AppDestination(val route: String) {
    data object Now      : AppDestination("now")
    data object Browse   : AppDestination("browse")
    data object Settings : AppDestination("settings")
}

/** Bottom nav items in display order. */
val bottomNavDestinations = listOf(
    AppDestination.Now,
    AppDestination.Browse,
    AppDestination.Settings,
)