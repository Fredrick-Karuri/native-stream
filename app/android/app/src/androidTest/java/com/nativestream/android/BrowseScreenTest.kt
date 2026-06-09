// app/src/androidTest/java/com/nativestream/android/BrowseScreenTest.kt
//
// BrowseScreen: chip filtering

package com.nativestream.android

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.nativestream.android.domain.model.Channel
import com.nativestream.android.domain.model.SportCategory
import com.nativestream.android.ui.screens.browse.BrowseScreen
import com.nativestream.android.ui.viewmodel.EpgViewModel
import com.nativestream.android.ui.viewmodel.FavouritesViewModel
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
class BrowseScreenTest {

    @get:Rule(order = 0) val hiltRule = HiltAndroidRule(this)
    @get:Rule(order = 1) val composeRule = createComposeRule()

    private lateinit var playlistViewModel: PlaylistViewModel
    private lateinit var epgViewModel: EpgViewModel
    private lateinit var favouritesViewModel: FavouritesViewModel
    private lateinit var playerViewModel: PlayerViewModel

    private val sportsChannels = listOf(
        Channel.create(tvgId = "sky.sports.1", name = "Sky Sports 1", groupTitle = "Sports",
            streamUrl = "http://s.example.com/sky1.m3u8"),
        Channel.create(tvgId = "sky.sports.2", name = "Sky Sports 2", groupTitle = "Sports",
            streamUrl = "http://s.example.com/sky2.m3u8"),
        Channel.create(tvgId = "bbc.one",      name = "BBC One",       groupTitle = "Entertainment",
            streamUrl = "http://s.example.com/bbc1.m3u8"),
    )

    @Before
    fun setUp() {
        hiltRule.inject()
        playlistViewModel   = mockk(relaxed = true)
        epgViewModel        = mockk(relaxed = true)
        favouritesViewModel = mockk(relaxed = true)
        playerViewModel     = mockk(relaxed = true)

        every { playlistViewModel.channels  } returns MutableStateFlow(sportsChannels)
        every { playlistViewModel.isLoading } returns MutableStateFlow(false)
        every { favouritesViewModel.favouriteIds } returns MutableStateFlow(emptySet())
        every { playerViewModel.activeChannel    } returns MutableStateFlow(null)
        every { epgViewModel.activeSports(any()) } returns emptyList()
        every { epgViewModel.currentProgramme(any()) } returns null
        every { epgViewModel.nextProgramme(any())    } returns null
        every { epgViewModel.matchesSport(any(), any()) } returns false
    }

    @Test
    fun allChipSelectedByDefault() {
        composeRule.setContent {
            BrowseScreen(playerViewModel = playerViewModel,
                playlistViewModel = playlistViewModel,
                epgViewModel = epgViewModel,
                favouritesViewModel = favouritesViewModel)
        }
        // "All" chip should be present and active — all channels visible
        composeRule.onNodeWithText("All").assertIsDisplayed()
        composeRule.onNodeWithText("Sky Sports 1").assertIsDisplayed()
        composeRule.onNodeWithText("BBC One").assertIsDisplayed()
    }

    @Test
    fun tappingGroupChip_filtersGridToThatGroupOnly() {
        composeRule.setContent {
            BrowseScreen(playerViewModel = playerViewModel,
                playlistViewModel = playlistViewModel,
                epgViewModel = epgViewModel,
                favouritesViewModel = favouritesViewModel)
        }
        composeRule.onNodeWithText("Entertainment").performClick()

        composeRule.onNodeWithText("BBC One").assertIsDisplayed()
        composeRule.onNodeWithText("Sky Sports 1").assertDoesNotExist()
    }

    @Test
    fun tappingGroupChip_showsCorrectChannelCountLabel() {
        composeRule.setContent {
            BrowseScreen(playerViewModel = playerViewModel,
                playlistViewModel = playlistViewModel,
                epgViewModel = epgViewModel,
                favouritesViewModel = favouritesViewModel)
        }
        composeRule.onNodeWithText("Sports").performClick()
        // NSGroupHeader renders "Sports (2)" or similar — verify the count label
        composeRule.onNodeWithText("2", substring = true).assertIsDisplayed()
    }

    @Test
    fun tappingSportChip_showsMatchDayScreen() {
        // Simulate a sport group selected and activeSports returning FOOTBALL
        every { epgViewModel.activeSports(any()) } returns listOf(SportCategory.FOOTBALL)

        composeRule.setContent {
            BrowseScreen(playerViewModel = playerViewModel,
                playlistViewModel = playlistViewModel,
                epgViewModel = epgViewModel,
                favouritesViewModel = favouritesViewModel)
        }
        // Select Sports group to reveal sport sub-chips
        composeRule.onNodeWithText("Sports").performClick()
        // Tap the Football sub-chip — MatchDayScreen renders sport label as heading
        composeRule.onNodeWithText("Football").performClick()
        composeRule.onNodeWithText("Football", substring = true).assertIsDisplayed()
    }
}