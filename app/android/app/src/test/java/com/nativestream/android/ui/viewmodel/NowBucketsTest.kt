// app/src/test/java/com/nativestream/android/ui/viewmodel/NowBucketsTest.kt
//
// AND-T017 — NowBuckets: bucketing logic

package com.nativestream.android.ui.viewmodel

import com.nativestream.android.domain.model.Channel
import com.nativestream.android.domain.model.Programme
import com.nativestream.android.ui.screens.now.NowBuckets
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NowBucketsTest {

    private val now = System.currentTimeMillis()

    // ── Fixture helpers ───────────────────────────────────────────────────────

    private fun channel(tvgId: String, group: String) = Channel.create(
        tvgId     = tvgId,
        name      = tvgId,
        groupTitle = group,
        streamUrl = "http://stream.example.com/$tvgId.m3u8",
    )

    private fun liveProgramme(channelId: String, title: String) = Programme(
        channelId    = channelId,
        title        = title,
        startEpochMs = now - 1_800_000L,  // started 30m ago
        stopEpochMs  = now + 1_800_000L,  // ends in 30m
    )

    private fun futureProgramme(channelId: String, title: String, startOffsetMs: Long) = Programme(
        channelId    = channelId,
        title        = title,
        startEpochMs = now + startOffsetMs,
        stopEpochMs  = now + startOffsetMs + 3_600_000L,
    )

    // Sport channel with a " vs " match title → goes in liveMatches
    private val sportChannel  = channel("sky.sports.1", "Sports")
    private val matchProgramme = liveProgramme("sky.sports.1", "Arsenal vs Chelsea")

    // Sport channel with non-match title (golf coverage) → goes in liveOnAir
    private val golfChannel   = channel("sky.sports.golf", "Sports")
    private val golfProgramme  = liveProgramme("sky.sports.golf", "PGA Tour Live")

    // News channel — always live
    private val newsChannel   = channel("bbc.news", "News")
    private val newsProgramme  = liveProgramme("bbc.news", "BBC News at Six")

    // Channel with nothing live but next within 2h
    private val soonChannel   = channel("itv.1", "Entertainment")
    private val soonProgramme  = futureProgramme("itv.1", "Upcoming Show", startOffsetMs = 3_600_000L) // 1h away

    // Channel with nothing live and next > 2h away
    private val farChannel    = channel("ch4", "Entertainment")
    private val farProgramme   = futureProgramme("ch4", "Late Show", startOffsetMs = 8 * 3_600_000L) // 8h away

    private val allChannels = listOf(sportChannel, golfChannel, newsChannel, soonChannel, farChannel)

    private fun currentFor(channel: Channel): Programme? = when (channel.id) {
        sportChannel.id -> matchProgramme
        golfChannel.id  -> golfProgramme
        newsChannel.id  -> newsProgramme
        else            -> null
    }

    private fun nextFor(channel: Channel): Programme? = when (channel.id) {
        soonChannel.id -> soonProgramme
        farChannel.id  -> farProgramme
        else           -> null
    }

    // ── liveMatches ───────────────────────────────────────────────────────────

    @Test
    fun `live sport programme with vs appears in liveMatches not liveOnAir`() {
        val matches = NowBuckets.liveMatches(allChannels, ::currentFor)
        val onAir   = NowBuckets.liveOnAir(allChannels, ::currentFor)

        assertTrue(matches.any { it.channel.id == sportChannel.id })
        assertFalse(onAir.any { it.channel.id == sportChannel.id })
    }

    // ── liveOnAir ─────────────────────────────────────────────────────────────

    @Test
    fun `live non-match sport programme appears in liveOnAir not liveMatches`() {
        val matches = NowBuckets.liveMatches(allChannels, ::currentFor)
        val onAir   = NowBuckets.liveOnAir(allChannels, ::currentFor)

        // Golf coverage: isSportMatch is true (keyword hit) but no " vs " → not a match
        // liveOnAir includes all live-eligible channels regardless of " vs "
        assertFalse(matches.any { it.channel.id == golfChannel.id })
        assertTrue(onAir.any { it.channel.id == golfChannel.id })
    }

    // ── startingSoon ──────────────────────────────────────────────────────────

    @Test
    fun `no current programme with next within 2h appears in startingSoon`() {
        val soon = NowBuckets.startingSoon(allChannels, ::currentFor, ::nextFor)
        assertTrue(soon.any { it.channel.id == soonChannel.id })
    }

    @Test
    fun `no current programme with next beyond 2h excluded from all buckets`() {
        val matches = NowBuckets.liveMatches(allChannels, ::currentFor)
        val onAir   = NowBuckets.liveOnAir(allChannels, ::currentFor)
        val soon    = NowBuckets.startingSoon(allChannels, ::currentFor, ::nextFor)

        assertFalse(matches.any { it.channel.id == farChannel.id })
        assertFalse(onAir.any   { it.channel.id == farChannel.id })
        assertFalse(soon.any    { it.channel.id == farChannel.id })
    }

    @Test
    fun `channel with current programme excluded from startingSoon`() {
        val soon = NowBuckets.startingSoon(allChannels, ::currentFor, ::nextFor)
        assertFalse(soon.any { it.channel.id == newsChannel.id })
        assertFalse(soon.any { it.channel.id == sportChannel.id })
    }
}