// app/src/test/java/com/nativestream/android/domain/ProgrammeTest.kt
//
// Programme computed properties
// Uses a fixed "now" offset rather than System.currentTimeMillis() directly,
// achieved by constructing start/stop times relative to the real clock at
// test-construction time so the assertions are deterministic.

package com.nativestream.android.domain

import com.nativestream.android.domain.model.Programme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgrammeTest {

    // ── Helpers ───────────────────────────────────────────────────────────────

    private val now = System.currentTimeMillis()

    /** Programme that started in the past and ends in the future. */
    private fun midProgramme(
        elapsedFraction: Double = 0.5,
        durationMs: Long = 60 * 60 * 1_000L,   // 1h default
    ): Programme {
        val start = now - (durationMs * elapsedFraction).toLong()
        return Programme(
            channelId    = "ch1",
            title        = "Test Show",
            startEpochMs = start,
            stopEpochMs  = start + durationMs,
        )
    }

    // ── progress ──────────────────────────────────────────────────────────────

    @Test
    fun `progress returns 0 before programme start`() {
        val programme = Programme(
            channelId    = "ch1",
            title        = "Future Show",
            startEpochMs = now + 60_000,
            stopEpochMs  = now + 120_000,
        )
        assertEquals(0.0, programme.progress, 0.0)
    }

    @Test
    fun `progress returns 1 after programme stop`() {
        val programme = Programme(
            channelId    = "ch1",
            title        = "Past Show",
            startEpochMs = now - 120_000,
            stopEpochMs  = now - 60_000,
        )
        assertEquals(1.0, programme.progress, 0.0)
    }

    @Test
    fun `progress is clamped to 0-1 mid-programme`() {
        val programme = midProgramme(elapsedFraction = 0.5)
        val p = programme.progress
        assertTrue("progress should be in [0,1] but was $p", p in 0.0..1.0)
        assertTrue("progress should be > 0 mid-programme but was $p", p > 0.0)
        assertTrue("progress should be < 1 mid-programme but was $p", p < 1.0)
    }

    // ── isNow ─────────────────────────────────────────────────────────────────

    @Test
    fun `isNow is true when now is within start and stop`() {
        assertTrue(midProgramme().isNow)
    }

    @Test
    fun `isNow is false when now is at or after stop`() {
        val programme = Programme(
            channelId    = "ch1",
            title        = "Finished Show",
            startEpochMs = now - 120_000,
            stopEpochMs  = now - 1,
        )
        assertFalse(programme.isNow)
    }

    // ── timeRemainingString ───────────────────────────────────────────────────

    @Test
    fun `timeRemainingString returns Ending when stop is in the past`() {
        val programme = Programme(
            channelId    = "ch1",
            title        = "Past Show",
            startEpochMs = now - 120_000,
            stopEpochMs  = now - 60_000,
        )
        assertEquals("Ending", programme.timeRemainingString)
    }

    // ── id stability ──────────────────────────────────────────────────────────

    @Test
    fun `id is stable and equal for two instances with same channelId and startEpochMs`() {
        val start = now - 30_000
        val a = Programme(channelId = "ch1", title = "Show A", startEpochMs = start, stopEpochMs = start + 60_000)
        val b = Programme(channelId = "ch1", title = "Different Title", startEpochMs = start, stopEpochMs = start + 90_000)
        assertEquals(a.id, b.id)
    }
}