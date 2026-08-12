// app/src/main/java/com/nativestream/android/ui/screens/onboarding/OnboardingConnectionState.kt

package com.nativestream.android.ui.screens.onboarding

sealed class OnboardingConnectionState {
    object Idle     : OnboardingConnectionState()
    object Checking : OnboardingConnectionState()
    data class Success(
        val channels:        Int,
        val healthy:         Int,
        val hasEpg:          Boolean,
        val epgFromPlaylist: Boolean,
    ) : OnboardingConnectionState()
    data class Failure(val reason: FailureReason) : OnboardingConnectionState()
}

enum class FailureReason {
    UNREACHABLE,
    NO_PLAYLIST,
    UNKNOWN,
}