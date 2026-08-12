// app/src/main/java/com/nativestream/android/ui/screens/now/NowBuckets.kt
//
// Now Screen Bucketing Logic
//   - liveMatches  — live + sport keyword + " vs " in title
//   - liveOnAir    — live + NOT a sport match
//   - startingSoon — nothing live but next programme within 2 hours

package com.nativestream.android.ui.screens.now

import com.nativestream.android.domain.model.Channel
import com.nativestream.android.domain.model.LiveEligibility
import com.nativestream.android.domain.model.Programme

private const val STARTING_SOON_WINDOW_MS = 2 * 60 * 60 * 1000L // 2 hours
private const val VS_SEPARATOR = " vs "

data class ChannelWithProgramme(
    val channel: Channel,
    val programme: Programme,
)

object NowBuckets {

    /**
     * Channels with a live sport match — programme is live, matches sport
     * keywords, and title contains " vs ". Mirrors liveMatches in NowScreen.swift.
     */
    fun liveMatches(
        channels: List<Channel>,
        currentProgrammeFor: (Channel) -> Programme?,
    ): List<ChannelWithProgramme> =
        channels.mapNotNull { channel ->
            val programme = currentProgrammeFor(channel) ?: return@mapNotNull null
            if (LiveEligibility.isLive(channel, programme) && programme.title.contains(VS_SEPARATOR, ignoreCase = true)) {
                ChannelWithProgramme(channel, programme)
            } else null
        }

    /**
     * Channels live but NOT a sport match — studio shows, golf coverage, snooker etc.
     * Mirrors liveOnAir in NowScreen.swift.
     */
    fun liveOnAir(
        channels: List<Channel>,
        currentProgrammeFor: (Channel) -> Programme?,
    ): List<ChannelWithProgramme> =
        channels.mapNotNull { channel ->
            val programme = currentProgrammeFor(channel) ?: return@mapNotNull null
            if (LiveEligibility.isLive(channel, programme) &&
                !programme.title.contains(VS_SEPARATOR, ignoreCase = true)) {
                ChannelWithProgramme(channel, programme)
            } else null
        }

    /**
     * Channels with nothing live but a next programme starting within 2 hours.
     * Mirrors startingSoon in NowScreen.swift.
     */
    fun startingSoon(
        channels: List<Channel>,
        currentProgrammeFor: (Channel) -> Programme?,
        nextProgrammeFor: (Channel) -> Programme?,
    ): List<ChannelWithProgramme> {
        val cutoffMs = System.currentTimeMillis() + STARTING_SOON_WINDOW_MS
        return channels.mapNotNull { channel ->
            if (currentProgrammeFor(channel) != null) return@mapNotNull null
            val next = nextProgrammeFor(channel) ?: return@mapNotNull null
            if (next.startEpochMs <= cutoffMs) ChannelWithProgramme(channel, next) else null
        }
    }
}