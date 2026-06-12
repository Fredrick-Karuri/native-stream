// app/src/test/java/com/nativestream/android/ui/viewmodel/PlayerViewModelTest.kt
//
// AND-T015 — PlayerViewModel: playback state
// AND-T016 — PlayerViewModel: retry logic
//

package com.nativestream.android.ui.viewmodel

import android.app.Application
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import com.nativestream.android.data.remote.ApiClient
import com.nativestream.android.data.remote.ChannelDetailResponse
import com.nativestream.android.data.remote.LinkScoreResponse
import com.nativestream.android.domain.model.Channel
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class PlayerViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var apiClient: ApiClient
    private lateinit var application: Application
    private lateinit var fakePlayer: FakePlayer
    private lateinit var viewModel: PlayerViewModel

    private val testChannel = Channel.create(
        tvgId     = "bbc.one",
        name      = "BBC One",
        streamUrl = "http://stream.example.com/bbc1.m3u8",
    )


    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        application = mockk(relaxed = true)
        every { application.packageName } returns "com.nativestream.android"
        apiClient   = mockk()
        fakePlayer  = FakePlayer()
        viewModel = PlayerViewModel(application, apiClient)
        viewModel.setPlayerForTest(fakePlayer)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun getLastLoadedUrl(): String? = fakePlayer.lastLoadedUrl

    private fun getLoadStreamCallCount(): Int {
        return fakePlayer.setMediaItemCallCount
    }

    // ── AND-T015: Playback state ───────────────────────────────────────────────

    @Test
    fun `T015 - play emits activeChannel and sets isPlayerVisible true`() = runTest {
        viewModel.play(testChannel)
        advanceUntilIdle()
        assertEquals(testChannel, viewModel.activeChannel.value)
        assertTrue(viewModel.isPlayerVisible.value)
    }

    @Test
    fun `T015 - togglePlayback flips isPlaying`() = runTest {
        viewModel.play(testChannel)
        advanceUntilIdle()
        fakePlayer.simulatePlaying(true)
        val before = viewModel.isPlaying.value
        viewModel.togglePlayback()
        assertFalse(viewModel.isPlaying.value == before)
    }

    @Test
    fun `T015 - toggleMute flips isMuted`() = runTest {
        assertFalse(viewModel.isMuted.value)
        viewModel.toggleMute()
        assertTrue(viewModel.isMuted.value)
        viewModel.toggleMute()
        assertFalse(viewModel.isMuted.value)
    }

    @Test
    fun `T015 - stop sets activeChannel null, isPlayerVisible false, isPlaying false`() = runTest {
        viewModel.play(testChannel)
        advanceUntilIdle()
        viewModel.stop()
        advanceUntilIdle()
        assertNull(viewModel.activeChannel.value)
        assertFalse(viewModel.isPlayerVisible.value)
        assertFalse(viewModel.isPlaying.value)
    }

    @Test
    fun `T015 - playUrl creates temporary channel with correct streamUrl and headers`() = runTest {
        val url     = "http://direct.example.com/stream.m3u8"
        val headers = mapOf("X-Token" to "abc123")
        viewModel.playUrl(url, headers)
        advanceUntilIdle()
        val channel = viewModel.activeChannel.value
        assertNotNull(channel)
        assertEquals(url, channel!!.streamUrl)
        assertEquals(headers, channel.streamHeaders)
    }

    @Test
    fun `T015 - controls auto-hide after 3 seconds`() = runTest {
        viewModel.play(testChannel)
        assertTrue(viewModel.controlsVisible.value)
        advanceTimeBy(3_001L)
        assertFalse(viewModel.controlsVisible.value)
    }

    // ── AND-T014: Channel ID uniqueness ──────────────────────────────────────

    @Test
    fun `T014 - channels from different sources with same streamUrl have unique ids`() {
        val url = "http://stream.example.com/bbc1.m3u8"
        val c1 = Channel.create(tvgId = "", name = "BBC One", streamUrl = url, sourceId = "source-1")
        val c2 = Channel.create(tvgId = "", name = "BBC One", streamUrl = url, sourceId = "source-2")
        assertTrue("IDs must be unique across sources", c1.id != c2.id)
    }

    @Test
    fun `T014 - channels from same source with same streamUrl are deduplicated`() {
        val url = "http://stream.example.com/bbc1.m3u8"
        val channels = listOf(
            Channel.create(tvgId = "", name = "BBC One", streamUrl = url, sourceId = "source-1"),
            Channel.create(tvgId = "", name = "BBC One Duplicate", streamUrl = url, sourceId = "source-1"),
        )
        val deduped = channels.distinctBy { it.id }
        assertEquals(1, deduped.size)
    }

    // ── AND-T016: Retry logic ─────────────────────────────────────────────────

    @Test
    fun `T016 - first failure retries after 2s delay`() = runTest {
        viewModel.play(testChannel)
        advanceUntilIdle()
        fakePlayer.triggerPlaybackError()
        advanceTimeBy(2_001L)
        // After 2s the retry should have fired — loadStream called again
        assertEquals(2, getLoadStreamCallCount())
    }

    @Test
    fun `T016 - three consecutive failures emit playerError and no further retries`() = runTest {
        coEvery { apiClient.getChannel(any()) } throws RuntimeException("unreachable")
        viewModel.play(testChannel)
        advanceUntilIdle()

        repeat(4) {
            fakePlayer.triggerPlaybackError()
            advanceTimeBy(2_001L)
        }

        assertNotNull(viewModel.playerError.value)
        // loadStream should have been called exactly 4 times: 1 initial + 3 retries
        assertEquals(4, getLoadStreamCallCount())
    }

    @Test
    fun `T016 - retry re-fetches active link from ApiClient getChannel`() = runTest {
        val freshUrl = "http://fresh.example.com/bbc1.m3u8"
        coEvery { apiClient.getChannel(any()) } returns ChannelDetailResponse(
            id         = "bbc.one",
            name       = "BBC One",
            groupTitle = "News",
            tvgId      = "bbc.one",
            logoUrl    = "",
            keywords   = emptyList(),
            activeLink = LinkScoreResponse(freshUrl, 0.9, 50, "healthy", 0),
            candidates = emptyList(),
        )

        viewModel.play(testChannel)
        advanceUntilIdle()
        fakePlayer.triggerPlaybackError()
        advanceTimeBy(2_001L)

        assertEquals(freshUrl, getLastLoadedUrl())
    }

    @Test
    fun `T016 - server unreachable during retry falls back to cached streamUrl`() = runTest {
        coEvery { apiClient.getChannel(any()) } throws RuntimeException("unreachable")
        viewModel.play(testChannel)
        advanceUntilIdle()
        fakePlayer.triggerPlaybackError()
        advanceTimeBy(2_001L)

        assertEquals(testChannel.streamUrl, getLastLoadedUrl())
    }

    @Test
    fun `T016 - retryManually resets retryCount and re-attempts`() = runTest {
        coEvery { apiClient.getChannel(any()) } throws RuntimeException("unreachable")
        viewModel.play(testChannel)
        advanceUntilIdle()

        // Exhaust retries
        repeat(4) {
            fakePlayer.triggerPlaybackError()
            advanceTimeBy(2_001L)
        }
        assertNotNull(viewModel.playerError.value)

        // retryManually should reset and play again
        viewModel.retryManually()
        advanceUntilIdle()
        assertNull(viewModel.playerError.value)
        assertEquals(testChannel, viewModel.activeChannel.value)
    }
}

// ── Test doubles ──────────────────────────────────────────────────────────────

/**
 * Testable subclass of PlayerViewModel that replaces MediaController/ExoPlayer
 * with a [FakePlayer] and exposes test hooks.
 */


/**
 * Minimal [Player] stub — only the methods exercised by PlayerViewModel tests.
 */
// 👇 REPLACE your existing FakePlayer definition with this target implementation:
private class FakePlayer : Player by mockk(relaxed = true) {
    private var playing = false
    private val listeners = mutableListOf<Player.Listener>()
    var setMediaItemCallCount = 0
    var lastLoadedUrl: String? = null

    fun simulatePlaying(value: Boolean) {
        playing = value
        listeners.forEach { it.onIsPlayingChanged(value) }
    }

    fun triggerPlaybackError() {
        val mockException = mockk<PlaybackException>(relaxed = true)
        listeners.forEach { it.onPlayerError(mockException) }
    }

    override fun addListener(listener: Player.Listener) {
        listeners.add(listener)
    }

    override fun isPlaying(): Boolean = playing

    override fun setMediaItem(mediaItem: MediaItem) {
        setMediaItemCallCount++
        lastLoadedUrl = mediaItem.localConfiguration?.uri?.toString()
    }

    override fun play() { simulatePlaying(true) }
    override fun pause() { simulatePlaying(false) }
    override fun stop() { simulatePlaying(false) }
}
