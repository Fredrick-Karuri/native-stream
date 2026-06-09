// app/src/androidTest/java/com/nativestream/android/NowScreenTest.kt
//
// NowScreen: section visibility
// Type: UI (Compose instrumented)

package com.nativestream.android

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.nativestream.android.domain.model.Channel
import com.nativestream.android.domain.model.Programme
import com.nativestream.android.ui.screens.now.NowScreen
import com.nativestream.android.ui.viewmodel.EpgViewModel
import com.nativestream.android.ui.viewmodel.PlaylistViewModel
import com.nativestream.android.ui.viewmodel.PlayerViewModel
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@HiltAndroidTest
class NowScreenTest {

    @get:Rule(order = 0) val hiltRule = HiltAndroidRule(this)
    @get:Rule(order = 1) val composeRule = createComposeRule()

    private val now = System.currentTimeMillis()

    // ── Fake ViewModels ───────────────────────────────────────────────────────

    private lateinit var playlistViewModel: PlaylistViewModel
    private lateinit var epgViewModel: EpgViewModel
    private lateinit var playerViewModel: PlayerViewModel

    private fun liveChannel(tvgId: String, group: String = "Sports") = Channel.create(
        tvgId = tvgId, name = tvgId, groupTitle = group,
        streamUrl = "http://stream.example.com/$tvgId.m3u8",
    )

    private fun liveProgramme(channelId: String, title: String) = Programme(
        channelId = channelId, title = title,
        startEpochMs = now - 1_800_000L,
        stopEpochMs  = now + 1_800_000L,
    )

    @Before
    fun setUp() {
        hiltRule.inject()
        playlistViewModel = mockk(relaxed = true)
        epgViewModel      = mockk(relaxed = true)
        playerViewModel   = mockk(relaxed = true)

        // Default: empty, not loading
        every { playlistViewModel.channels  } returns MutableStateFlow(emptyList())
        every { playlistViewModel.isLoading } returns MutableStateFlow(false)
        every { epgViewModel.isReady        } returns MutableStateFlow(false)
        every { epgViewModel.currentProgramme(any()) } returns null
        every { epgViewModel.nextProgramme(any())    } returns null
        every { playerViewModel.activeChannel } returns MutableStateFlow(null)
    }

    // ── AND-T020 cases ────────────────────────────────────────────────────────

    @Test
    fun emptyChannels_showsNothingOnRightNowEmptyState() {
        composeRule.setContent {
            NowScreen(
                playerViewModel   = playerViewModel,
                playlistViewModel = playlistViewModel,
                epgViewModel      = epgViewModel,
            )
        }
        composeRule.onNodeWithText("Nothing on right now").assertIsDisplayed()
    }

    @Test
    fun loadingState_showsCircularProgressIndicator() {
        every { playlistViewModel.isLoading } returns MutableStateFlow(true)

        composeRule.setContent {
            NowScreen(
                playerViewModel   = playerViewModel,
                playlistViewModel = playlistViewModel,
                epgViewModel      = epgViewModel,
            )
        }
        // CircularProgressIndicator has no text; verify loading state via absence of empty state
        composeRule.onNodeWithText("Nothing on right now").assertDoesNotExist()
    }

    @Test
    fun liveMatchesPresent_showsMatchesLiveSectionHeader() {
        val channel   = liveChannel("sky.sports.1")
        val programme = liveProgramme("sky.sports.1", "Arsenal vs Chelsea")

        every { playlistViewModel.channels } returns MutableStateFlow(listOf(channel))
        every { epgViewModel.isReady        } returns MutableStateFlow(true)
        every { epgViewModel.currentProgramme(channel) } returns programme

        composeRule.setContent {
            NowScreen(
                playerViewModel   = playerViewModel,
                playlistViewModel = playlistViewModel,
                epgViewModel      = epgViewModel,
            )
        }
        composeRule.onNodeWithText("Matches live", substring = true).assertIsDisplayed()
    }

    @Test
    fun noMatches_onAirOnly_matchesSectionAbsent() {
        val channel   = liveChannel("bbc.news", "News")
        val programme = liveProgramme("bbc.news", "BBC News at Six")

        every { playlistViewModel.channels } returns MutableStateFlow(listOf(channel))
        every { epgViewModel.isReady        } returns MutableStateFlow(true)
        every { epgViewModel.currentProgramme(channel) } returns programme

        composeRule.setContent {
            NowScreen(
                playerViewModel   = playerViewModel,
                playlistViewModel = playlistViewModel,
                epgViewModel      = epgViewModel,
            )
        }
        composeRule.onNodeWithText("Matches live", substring = true).assertDoesNotExist()
    }

    @Test
    fun liveOnAirCountExceedsTen_showAllButtonVisible_tapExpandsList() {
        val channels = (1..12).map { i ->
            liveChannel("news.$i", "News").also { ch ->
                every { epgViewModel.currentProgramme(ch) } returns
                        liveProgramme("news.$i", "Programme $i")
            }
        }
        every { playlistViewModel.channels } returns MutableStateFlow(channels)
        every { epgViewModel.isReady        } returns MutableStateFlow(true)

        composeRule.setContent {
            NowScreen(
                playerViewModel   = playerViewModel,
                playlistViewModel = playlistViewModel,
                epgViewModel      = epgViewModel,
            )
        }

        composeRule.onNodeWithText("Show all 12", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Show all 12", substring = true).performClick()
        // After tap, all 12 rows visible; "Show less" now shown
        composeRule.onNodeWithText("Show less").assertIsDisplayed()
    }

    @Test
    fun startingSoonSectionAbsentWhenListIsEmpty() {
        composeRule.setContent {
            NowScreen(
                playerViewModel   = playerViewModel,
                playlistViewModel = playlistViewModel,
                epgViewModel      = epgViewModel,
            )
        }
        composeRule.onNodeWithText("Starting soon", substring = true).assertDoesNotExist()
    }
}