// app/src/androidTest/java/com/nativestream/android/PlayerScreenTest.kt
//
// PlayerScreen: controls auto-hide

package com.nativestream.android

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.nativestream.android.domain.model.Channel
import com.nativestream.android.domain.model.Programme
import com.nativestream.android.ui.screens.player.PlayerScreen
import com.nativestream.android.ui.viewmodel.CastViewModel
import com.nativestream.android.ui.viewmodel.EpgViewModel
import com.nativestream.android.ui.viewmodel.PlayerViewModel
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
class PlayerScreenTest {

    @get:Rule(order = 0) val hiltRule    = HiltAndroidRule(this)
    @get:Rule(order = 1) val composeRule = createComposeRule()

    private lateinit var playerViewModel: PlayerViewModel
    private lateinit var castViewModel: CastViewModel
    private lateinit var epgViewModel: EpgViewModel

    private val now = System.currentTimeMillis()

    private val testChannel = Channel.create(
        tvgId     = "sky.sports.1",
        name      = "Sky Sports 1",
        groupTitle = "Sports",
        streamUrl = "http://stream.example.com/sky1.m3u8",
    )

    private val matchProgramme = Programme(
        channelId    = "sky.sports.1",
        title        = "Arsenal vs Chelsea — Premier League",
        startEpochMs = now - 1_800_000L,
        stopEpochMs  = now + 1_800_000L,
    )

    private val controlsVisibleFlow = MutableStateFlow(false)
    private val playerErrorFlow     = MutableStateFlow<String?>(null)

    @Before
    fun setUp() {
        hiltRule.inject()
        playerViewModel = mockk(relaxed = true)
        castViewModel   = mockk(relaxed = true)
        epgViewModel    = mockk(relaxed = true)

        every { playerViewModel.activeChannel   } returns MutableStateFlow(testChannel)
        every { playerViewModel.controlsVisible } returns controlsVisibleFlow
        every { playerViewModel.playerError     } returns playerErrorFlow
        every { playerViewModel.isPlaying       } returns MutableStateFlow(true)
        every { playerViewModel.isMuted         } returns MutableStateFlow(false)
        every { playerViewModel.isInPip         } returns MutableStateFlow(false)
        every { playerViewModel.sidebarVisible  } returns MutableStateFlow(false)
        every { playerViewModel.resizeMode      } returns MutableStateFlow(0)
        every { playerViewModel.videoQuality    } returns MutableStateFlow(null)
        every { castViewModel.isCastAvailable   } returns MutableStateFlow(false)
        every { castViewModel.isConnected       } returns MutableStateFlow(false)
        every { epgViewModel.currentProgramme(testChannel) } returns matchProgramme
    }

    private fun setContent() {
        composeRule.setContent {
            PlayerScreen(
                playerViewModel = playerViewModel,
                castViewModel   = castViewModel,
                epgViewModel    = epgViewModel,
                onDismiss       = {},
            )
        }
    }

    @Test
    fun tapOnPlayerArea_makesControlsVisible() {
        setContent()
        // Tap the player area — triggers onPlayerTapped()
        composeRule.onNodeWithContentDescription("Back", useUnmergedTree = true)
            .assertDoesNotExist() // controls hidden initially
        composeRule.onNodeWithText("Sky Sports 1", substring = true, useUnmergedTree = true)
            .performClick()
        verify { playerViewModel.onPlayerTapped() }
    }

    @Test
    fun controlsVisible_areShownWhenStateIsTrue() {
        controlsVisibleFlow.value = true
        setContent()
        composeRule.onNodeWithContentDescription("Back").assertIsDisplayed()
    }

    @Test
    fun errorOverlay_visibleWhenPlayerErrorIsNonNull() {
        playerErrorFlow.value = "Stream failed after 3 attempts"
        setContent()
        composeRule.onNodeWithText("Stream failed after 3 attempts").assertIsDisplayed()
        composeRule.onNodeWithText("Retry").assertIsDisplayed()
    }

    @Test
    fun retryButton_triggersRetryManually() {
        playerErrorFlow.value = "Stream failed after 3 attempts"
        setContent()
        composeRule.onNodeWithText("Retry").performClick()
        verify { playerViewModel.retryManually() }
    }

    @Test
    fun scoreOverlay_visibleWhenProgrammeTitleContainsVs() {
        controlsVisibleFlow.value = false // controls hidden so overlay is visible
        setContent()
        // ScoreOverlay renders home and away team names
        composeRule.onNodeWithText("Arsenal", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Chelsea", substring = true).assertIsDisplayed()
    }
}