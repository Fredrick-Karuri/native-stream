// app/src/test/java/com/nativestream/android/data/parser/EpgParserTest.kt
//
// AND-T007 — EpgParser: happy path
// AND-T008 — EpgParser: malformed input
//
// NOTE: EpgParser uses android.util.Xml and android.util.Log internally.
// Run with Robolectric (@RunWith(RobolectricTestRunner::class)) or replace
// Log calls with a test-safe wrapper — annotated here for the test author.

package com.nativestream.android.data.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EpgParserTest {

    private lateinit var parser: EpgParser

    @Before
    fun setUp() {
        parser = EpgParser()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun parse(xml: String) = parser.parse(xml.trimIndent().byteInputStream())

    // =========================================================================
    // AND-T007 — Happy path
    // =========================================================================

    private val happyXml = """
        <?xml version="1.0" encoding="utf-8"?>
        <tv>
          <channel id="bbc.one"><display-name>BBC One</display-name></channel>
          <channel id="sky.sports.1"><display-name>Sky Sports 1</display-name></channel>
          <programme start="20250101120000 +0000" stop="20250101130000 +0000" channel="bbc.one">
            <title>News at Noon</title>
          </programme>
          <programme start="20250101130000 +0000" stop="20250101140000 +0000" channel="bbc.one">
            <title>Afternoon Drama</title>
          </programme>
          <programme start="20250101120000 +0000" stop="20250101140000 +0000" channel="sky.sports.1">
            <title>Premier League Goals</title>
          </programme>
          <programme start="20250101140000 +0000" stop="20250101160000 +0000" channel="sky.sports.1">
            <title>Champions League Highlights</title>
          </programme>
        </tv>
    """.trimIndent()

    @Test
    fun `T007 - correct channel count in returned EpgStore`() {
        val store = parse(happyXml)
        assertEquals(2, store.channelCount)
    }

    @Test
    fun `T007 - programme title parsed correctly`() {
        val store = parse(happyXml)
        val programmes = store.programmesFor("bbc.one")
        assertTrue(programmes.any { it.title == "News at Noon" })
    }

    @Test
    fun `T007 - programme start epoch parsed correctly`() {
        val store = parse(happyXml)
        val programme = store.programmesFor("bbc.one").first { it.title == "News at Noon" }
        // "20250101120000 +0000" = 2025-01-01T12:00:00Z
        assertEquals(1_735_732_800_000L, programme.startEpochMs)
    }

    @Test
    fun `T007 - programme stop epoch parsed correctly`() {
        val store = parse(happyXml)
        val programme = store.programmesFor("bbc.one").first { it.title == "News at Noon" }
        assertEquals(1_735_736_400_000L, programme.stopEpochMs)
    }

    @Test
    fun `T007 - programmes keyed by channel tvg-id`() {
        val store = parse(happyXml)
        assertEquals(2, store.programmesFor("bbc.one").size)
        assertEquals(2, store.programmesFor("sky.sports.1").size)
    }

    @Test
    fun `T007 - XMLTV date 20250101120000 +0000 parses to correct epoch`() {
        val store = parse(happyXml)
        val p = store.programmesFor("bbc.one").first { it.title == "News at Noon" }
        assertNotNull(p)
        assertEquals(1_735_732_800_000L, p.startEpochMs)
    }

    // =========================================================================
    // AND-T008 — Malformed input
    // =========================================================================

    @Test
    fun `T008 - missing stop attribute skips entry without crash`() {
        val xml = """
            <?xml version="1.0" encoding="utf-8"?>
            <tv>
              <programme start="20250101120000 +0000" channel="bbc.one">
                <title>No Stop</title>
              </programme>
              <programme start="20250101130000 +0000" stop="20250101140000 +0000" channel="bbc.one">
                <title>Valid Programme</title>
              </programme>
            </tv>
        """.trimIndent()
        val store = parse(xml)
        val programmes = store.programmesFor("bbc.one")
        assertEquals(1, programmes.size)
        assertEquals("Valid Programme", programmes.first().title)
    }

    @Test
    fun `T008 - malformed date string skips entry without crash`() {
        val xml = """
            <?xml version="1.0" encoding="utf-8"?>
            <tv>
              <programme start="NOT-A-DATE" stop="ALSO-BAD" channel="bbc.one">
                <title>Bad Dates</title>
              </programme>
              <programme start="20250101130000 +0000" stop="20250101140000 +0000" channel="bbc.one">
                <title>Good Programme</title>
              </programme>
            </tv>
        """.trimIndent()
        val store = parse(xml)
        val programmes = store.programmesFor("bbc.one")
        assertEquals(1, programmes.size)
        assertEquals("Good Programme", programmes.first().title)
    }

    @Test
    fun `T008 - partial XML returns whatever was successfully parsed`() {
        // Truncated mid-programme — parser should return partial results, not throw
        val xml = """
            <?xml version="1.0" encoding="utf-8"?>
            <tv>
              <programme start="20250101120000 +0000" stop="20250101130000 +0000" channel="bbc.one">
                <title>Complete Programme</title>
              </programme>
              <programme start="20250101130000 +0000" stop="20250101140000 +0000" channel="bbc.one">
                <title>Truncated
        """.trimIndent() // intentionally truncated

        val store = parse(xml)
        // At least the complete programme before truncation should be present
        assertTrue(store.programmeCount >= 1)
        assertTrue(store.programmesFor("bbc.one").any { it.title == "Complete Programme" })
    }
}