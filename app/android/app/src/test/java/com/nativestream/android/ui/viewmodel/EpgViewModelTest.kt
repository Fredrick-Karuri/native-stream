// app/src/test/java/com/nativestream/android/ui/viewmodel/EpgViewModelTest.kt
//
// AND-T013 — EpgViewModel: load + queries
// AND-T014 — EpgViewModel: sport helpers

package com.nativestream.android.ui.viewmodel

import com.nativestream.android.data.local.SettingsDataStore
import com.nativestream.android.data.parser.EpgParser
import com.nativestream.android.data.parser.EpgStore
import com.nativestream.android.data.remote.ApiClient
import com.nativestream.android.domain.model.Channel
import com.nativestream.android.domain.model.PlaylistSource
import com.nativestream.android.domain.model.Programme
import com.nativestream.android.domain.model.SportCategory
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EpgViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var apiClient: ApiClient
    private lateinit var epgParser: EpgParser
    private lateinit var settingsDataStore: SettingsDataStore
    private lateinit var viewModel: EpgViewModel

    private val now = System.currentTimeMillis()

    // ── Fixture helpers ───────────────────────────────────────────────────────

    private fun programme(
        channelId: String,
        title: String,
        startOffsetMs: Long,
        durationMs: Long = 3_600_000L,
    ) = Programme(
        channelId    = channelId,
        title        = title,
        startEpochMs = now + startOffsetMs,
        stopEpochMs  = now + startOffsetMs + durationMs,
    )

    private val bbcChannel = Channel.create(
        tvgId     = "bbc.one",
        name      = "BBC One",
        streamUrl = "http://s.example.com/bbc1.m3u8",
    )

    private val skyChannel = Channel.create(
        tvgId     = "sky.sports.1",
        name      = "Sky Sports 1",
        streamUrl = "http://s.example.com/sky1.m3u8",
    )

    private val fakeSource = PlaylistSource(
        id = "src-1", name = "Test", url = "http://epg.example.com/guide.xml",
        refreshIntervalHours = 1, epgUrl = "http://epg.example.com/guide.xml",
    )

    private lateinit var fakeStore: EpgStore

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        apiClient        = mockk()
        epgParser        = mockk()
        settingsDataStore = mockk()

        val currentProgramme = programme("bbc.one", "News at Noon", startOffsetMs = -1_800_000L)
        val nextProgramme    = programme("bbc.one", "Afternoon Show", startOffsetMs = 1_800_000L)
        val footballProg     = programme("sky.sports.1", "Premier League: Arsenal vs Chelsea", startOffsetMs = -1_800_000L)

        fakeStore = EpgStore(mapOf(
            "bbc.one"      to listOf(currentProgramme, nextProgramme),
            "sky.sports.1" to listOf(footballProg),
        ))

        every { settingsDataStore.sources } returns flowOf(listOf(fakeSource))
        coEvery { settingsDataStore.epgUrl() } returns "http://epg.example.com/guide.xml"
        coEvery { apiClient.fetchRawUrl(any()) } returns ByteArray(0)
        coEvery { epgParser.parse(any()) } returns fakeStore

        viewModel = EpgViewModel(apiClient, epgParser, settingsDataStore)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── AND-T013 ──────────────────────────────────────────────────────────────

    @Test
    fun `T013 - load sets isLoading true during fetch then false after`() = runTest {
        viewModel.load()
        // isLoading goes true synchronously before dispatcher runs
        assertTrue(viewModel.isLoading.value)
        advanceUntilIdle()
        assertFalse(viewModel.isLoading.value)
    }

    @Test
    fun `T013 - currentProgramme delegates to store and returns correctly`() = runTest {
        viewModel.load()
        advanceUntilIdle()
        val current = viewModel.currentProgramme(bbcChannel)
        assertNotNull(current)
        assertEquals("News at Noon", current!!.title)
    }

    @Test
    fun `T013 - nextProgramme returns earliest future programme`() = runTest {
        viewModel.load()
        advanceUntilIdle()
        val next = viewModel.nextProgramme(bbcChannel)
        assertNotNull(next)
        assertEquals("Afternoon Show", next!!.title)
    }

    @Test
    fun `T013 - schedule deduplicates across stores by Programme id`() = runTest {
        // Two stores with the same programme (simulates overlapping EPG sources)
        val duplicate = programme("bbc.one", "News at Noon", startOffsetMs = -1_800_000L)
        val storeA = EpgStore(mapOf("bbc.one" to listOf(duplicate)))
        val storeB = EpgStore(mapOf("bbc.one" to listOf(duplicate)))

        // Inject both stores by loading twice with different parse results
        coEvery { epgParser.parse(any()) } returnsMany listOf(storeA, storeB)
        val sources = listOf(
            fakeSource.copy(id = "a"),
            fakeSource.copy(id = "b"),
        )
        every { settingsDataStore.sources } returns flowOf(sources)
        val vm = EpgViewModel(apiClient, epgParser, settingsDataStore)
        vm.load()
        advanceUntilIdle()

        val schedule = vm.schedule(bbcChannel, 6)
        val ids = schedule.map { it.id }
        assertEquals("Duplicate programmes should be deduped", ids.distinct().size, ids.size)
    }

    @Test
    fun `T013 - logMatchDiagnostic with empty list does not crash`() = runTest {
        viewModel.load()
        advanceUntilIdle()
        viewModel.logMatchDiagnostic(emptyList()) // should not throw
    }

    @Test
    fun `T013 - activeSports sorted by live count descending`() = runTest {
        viewModel.load()
        advanceUntilIdle()
        val sports = viewModel.activeSports(listOf(bbcChannel, skyChannel))
        // Football should appear (premier league keyword match on sky channel)
        assertTrue(sports.contains(SportCategory.FOOTBALL))
        // Verify ordering: no sport with higher live count appears after one with lower
        val liveCounts = sports.map { viewModel.liveCount(it, listOf(bbcChannel, skyChannel)) }
        assertEquals(liveCounts, liveCounts.sortedDescending())
    }

    // ── AND-T014 ──────────────────────────────────────────────────────────────

    @Test
    fun `T014 - matchesSport FOOTBALL true when title contains premier league`() = runTest {
        val prog = programme("sky.sports.1", "Premier League: Arsenal vs Chelsea", startOffsetMs = 0)
        assertTrue(viewModel.matchesSport(SportCategory.FOOTBALL, prog))
    }

    @Test
    fun `T014 - hasContent GOLF false when no live or upcoming golf`() = runTest {
        viewModel.load()
        advanceUntilIdle()
        // fakeStore has no golf content
        assertFalse(viewModel.hasContent(SportCategory.GOLF, listOf(bbcChannel, skyChannel)))
    }

    @Test
    fun `T014 - activeSports excludes sports with no content`() = runTest {
        viewModel.load()
        advanceUntilIdle()
        val sports = viewModel.activeSports(listOf(bbcChannel, skyChannel))
        assertFalse(sports.contains(SportCategory.GOLF))
        assertFalse(sports.contains(SportCategory.TENNIS))
    }
}