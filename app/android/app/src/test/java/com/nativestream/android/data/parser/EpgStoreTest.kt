// app/src/test/java/com/nativestream/android/data/parser/EpgStoreTest.kt
//
// AND-T009 — EpgStore: lookup + FX-002 fallback

package com.nativestream.android.data.parser

import com.nativestream.android.domain.model.Programme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class EpgStoreTest {

    private val now = System.currentTimeMillis()

    // ── Fixture ───────────────────────────────────────────────────────────────
    //  "BBC.ONE" (uppercase) is the key as it would come from an XMLTV file,
    //  simulating the FX-002 case-mismatch scenario.

    private fun programme(
        channelId: String,
        title: String,
        startOffsetMs: Long,
        durationMs: Long = 60 * 60 * 1_000L,
    ) = Programme(
        channelId    = channelId,
        title        = title,
        startEpochMs = now + startOffsetMs,
        stopEpochMs  = now + startOffsetMs + durationMs,
    )

    private lateinit var store: EpgStore

    @Before
    fun setUp() {
        val current  = programme("BBC.ONE", "Midday News",   startOffsetMs = -30 * 60_000L) // started 30m ago
        val upcoming = programme("BBC.ONE", "Afternoon Show", startOffsetMs = 30 * 60_000L)  // starts in 30m
        val future   = programme("BBC.ONE", "Evening News",   startOffsetMs = 3 * 60 * 60_000L) // starts in 3h

        store = EpgStore(
            mapOf("BBC.ONE" to listOf(current, upcoming, future))
        )
    }

    // ── Lookup ────────────────────────────────────────────────────────────────

    @Test
    fun `exact tvgId match returns correct programmes`() {
        assertEquals(3, store.programmesFor("BBC.ONE").size)
    }

    @Test
    fun `lowercase fallback - tvgId BBC_ONE matches key bbc_one`() {
        // FX-002: caller passes lowercase, XMLTV key is uppercase
        val programmes = store.programmesFor("bbc.one")
        assertEquals(3, programmes.size)
    }

    @Test
    fun `unknown tvgId returns empty list not null`() {
        val result = store.programmesFor("unknown.channel")
        assertNotNull(result)
        assertTrue(result.isEmpty())
    }

    // ── currentProgramme ─────────────────────────────────────────────────────

    @Test
    fun `currentProgramme returns programme where isNow is true`() {
        val current = store.currentProgramme("BBC.ONE")
        assertNotNull(current)
        assertEquals("Midday News", current!!.title)
    }

    // ── nextProgramme ─────────────────────────────────────────────────────────

    @Test
    fun `nextProgramme returns earliest programme with startEpochMs after now`() {
        val next = store.nextProgramme("BBC.ONE")
        assertNotNull(next)
        assertEquals("Afternoon Show", next!!.title)
    }

    // ── schedule ──────────────────────────────────────────────────────────────

    @Test
    fun `schedule filters correctly by time window`() {
        // Window: now-1h to now+2h — should include Midday News (current) and Afternoon Show
        val from = now - 60 * 60_000L
        val to   = now + 2 * 60 * 60_000L
        val result = store.schedule("BBC.ONE", from, to)
        assertEquals(2, result.size)
        assertTrue(result.any { it.title == "Midday News" })
        assertTrue(result.any { it.title == "Afternoon Show" })
    }

    @Test
    fun `schedule excludes programmes outside window`() {
        val from = now - 60 * 60_000L
        val to   = now + 2 * 60 * 60_000L
        val result = store.schedule("BBC.ONE", from, to)
        assertTrue(result.none { it.title == "Evening News" })
    }
}