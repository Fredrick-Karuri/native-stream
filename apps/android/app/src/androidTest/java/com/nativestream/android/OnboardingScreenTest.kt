// app/src/androidTest/java/com/nativestream/android/OnboardingScreenTest.kt
//
// AND-T025 — OnboardingScreen: step progression

package com.nativestream.android

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.nativestream.android.ui.screens.onboarding.OnboardingScreen
import com.nativestream.android.ui.viewmodel.SettingsViewModel
import com.nativestream.android.ui.viewmodel.SourceViewModel
import com.nativestream.android.ui.viewmodel.ChannelLoadingViewModel
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@HiltAndroidTest
class OnboardingScreenTest {

    @get:Rule(order = 0) val hiltRule    = HiltAndroidRule(this)
    @get:Rule(order = 1) val composeRule = createComposeRule()

    private lateinit var settingsViewModel: SettingsViewModel
    private lateinit var sourceViewModel: SourceViewModel
    private lateinit var loadingViewModel: ChannelLoadingViewModel
    private var completed = false

    private val serverUrlFlow = MutableStateFlow("http://192.168.1.42:8888")

    @Before
    fun setUp() {
        hiltRule.inject()
        settingsViewModel = mockk(relaxed = true)
        completed         = false
        sourceViewModel  = mockk(relaxed = true)
        loadingViewModel = mockk(relaxed = true)
        every { sourceViewModel.sources      } returns MutableStateFlow(emptyList())
        every { loadingViewModel.isLoading   } returns MutableStateFlow(false)

        every { settingsViewModel.serverUrl } returns serverUrlFlow
    }

    private fun setContent() {
        composeRule.setContent {
            OnboardingScreen(
                onComplete        = { completed = true },
                settingsViewModel = settingsViewModel,
                sourceViewModel   = sourceViewModel,
                loadingViewModel  = loadingViewModel,
            )
        }
    }

    // ── AND-T025 cases ────────────────────────────────────────────────────────

    @Test
    fun step1_visibleOnFirstComposition() {
        setContent()
        composeRule.onNodeWithText("Welcome to NativeStream", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Check Connection").assertIsDisplayed()
    }

    @Test
    fun skip_advancesToStep2() {
        setContent()
        composeRule.onNodeWithText("Skip").performClick()
        composeRule.onNodeWithText("Add a Playlist Source", substring = true).assertIsDisplayed()
    }

    @Test
    fun checkConnection_withUnreachableUrl_showsErrorText() {
        // checkServerReachable() uses a real HTTP connection; mock the URL so it fails immediately
        serverUrlFlow.value = "http://0.0.0.0:9999" // guaranteed unreachable
        setContent()
        composeRule.onNodeWithText("Check Connection").performClick()
        // Wait for coroutine + error state
        composeRule.waitUntil(timeoutMillis = 8_000) {
            composeRule.onAllNodes(
                androidx.compose.ui.test.hasText("Could not reach", substring = true)
            ).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Could not reach", substring = true).assertIsDisplayed()
    }

    @Test
    fun step2_addAndContinueButton_disabledWhenUrlFieldEmpty() {
        setContent()
        composeRule.onNodeWithText("Skip").performClick() // advance to step 2
        // URL field is empty by default
        composeRule.onNodeWithText("Add & Continue").assertIsNotEnabled()
    }

    @Test
    fun step4_startWatching_callsSetOnboardingCompleteTrue() {
        setContent()
        // Navigate through all steps via Skip
        composeRule.onNodeWithText("Skip").performClick() // step 1 → 2
        composeRule.onNodeWithText("Skip").performClick() // step 2 → 3
        composeRule.onNodeWithText("Skip").performClick() // step 3 → 4 (complete)
        composeRule.onNodeWithText("Start Watching").assertIsDisplayed()
        composeRule.onNodeWithText("Start Watching").performClick()
        verify { settingsViewModel.setOnboardingComplete(true) }
        assert(completed) { "onComplete callback should have been called" }
    }
}