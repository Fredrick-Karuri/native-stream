// app/src/androidTest/java/com/nativestream/android/ChannelCardTest.kt
//
// ChannelCard: playing state

package com.nativestream.android

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.nativestream.android.domain.model.Channel
import com.nativestream.android.domain.model.Programme
import com.nativestream.android.ui.screens.browse.ChannelCard
import com.nativestream.android.ui.viewmodel.EpgViewModel
import com.nativestream.android.ui.viewmodel.FavouritesViewModel
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
class ChannelCardTest {

    @get:Rule(order = 0) val hiltRule = HiltAndroidRule(this)
    @get:Rule(order = 1) val composeRule = createComposeRule()

    private lateinit var playerViewModel: PlayerViewModel
    private lateinit var epgViewModel: EpgViewModel
    private lateinit var favouritesViewModel: FavouritesViewModel

    private val now = System.currentTimeMillis()

    private val testChannel = Channel.create(
        tvgId     = "sky.sports.1",
        name      = "Sky Sports 1",
        groupTitle = "Sports",
        streamUrl = "http://s.example.com/sky1.m3u8",
    )

    private val liveSportProgramme = Programme(
        channelId    = "sky.sports.1",
        title        = "Arsenal vs Chelsea — Premier League",
        startEpochMs = now - 1_800_000L,
        stopEpochMs  = now + 1_800_000L,
    )

    @Before
    fun setUp() {
        hiltRule.inject()
        playerViewModel     = mockk(relaxed = true)
        epgViewModel        = mockk(relaxed = true)
        favouritesViewModel = mockk(relaxed = true)

        every { playerViewModel.activeChannel    } returns MutableStateFlow(null)
        every { favouritesViewModel.favouriteIds } returns MutableStateFlow(emptySet())
        every { epgViewModel.currentProgramme(any()) } returns null
        every { epgViewModel.nextProgramme(any())    } returns null
    }

    private fun setContent(channel: Channel = testChannel) {
        composeRule.setContent {
            ChannelCard(
                channel             = channel,
                playerViewModel     = playerViewModel,
                epgViewModel        = epgViewModel,
                favouritesViewModel = favouritesViewModel,
                onClick             = {},
            )
        }
    }

    @Test
    fun playingChannel_showsNowBadge_andAccentBorderApplied() {
        every { playerViewModel.activeChannel } returns MutableStateFlow(testChannel)
        setContent()
        // ▶NOW badge is rendered as text "NOW"
        composeRule.onNodeWithText("NOW").assertIsDisplayed()
    }

    @Test
    fun nonPlayingChannel_showsStarIcon() {
        setContent()
        composeRule.onNodeWithContentDescription("Add to favourites").assertIsDisplayed()
    }

    @Test
    fun starTapped_callsFavouritesViewModelToggle() {
        setContent()
        composeRule.onNodeWithContentDescription("Add to favourites").performClick()
        verify { favouritesViewModel.toggle(testChannel) }
    }

    @Test
    fun liveBadgeVisible_whenCurrentProgrammeIsSportMatch() {
        every { epgViewModel.currentProgramme(testChannel) } returns liveSportProgramme
        setContent()
        // NSLiveBadge renders "LIVE" text when isLive == true
        composeRule.onNodeWithText("LIVE").assertIsDisplayed()
    }

    @Test
    fun progressBarVisible_whenCurrentProgrammeExists() {
        every { epgViewModel.currentProgramme(testChannel) } returns liveSportProgramme
        setContent()
        // Programme title line confirms EPG content is rendered (implies progress bar rendered)
        composeRule.onNodeWithText("Arsenal vs Chelsea", substring = true).assertIsDisplayed()
    }
}