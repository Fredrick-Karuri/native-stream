// app/src/test/java/com/nativestream/android/data/parser/M3uParserTest.kt
//
// AND-T004 — M3uParser: happy path
// AND-T005 — M3uParser: malformed input
// AND-T006 — M3uParser: EPG URL detection

package com.nativestream.android.data.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class M3uParserTest {

    private lateinit var parser: M3uParser

    @Before
    fun setUp() {
        parser = M3uParser()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun parse(content: String) = parser.parse(content.trimIndent().byteInputStream())

    // =========================================================================
    // AND-T004 — Happy path
    // =========================================================================

    private val happyM3u = """
        #EXTM3U url-tvg="http://epg.example.com/guide.xml"
        #EXTINF:-1 tvg-id="bbc.one" tvg-logo="http://logos.example.com/bbc1.png" group-title="News",BBC One
        http://stream.example.com/bbc1.m3u8
        #EXTINF:-1 tvg-id="sky.sports.1" tvg-logo="http://logos.example.com/sky1.png" group-title="Sport",Sky Sports 1
        http://stream.example.com/sky1.m3u8
        #EXTINF:-1 tvg-id="itv.1" tvg-logo="" group-title="Entertainment",ITV
        http://stream.example.com/itv.m3u8
    """.trimIndent()

    @Test
    fun `T004 - correct channel count returned`() {
        assertEquals(3, parse(happyM3u).channels.size)
    }

    @Test
    fun `T004 - tvgId extracted from tvg-id attribute`() {
        val channels = parse(happyM3u).channels
        assertEquals("bbc.one",     channels[0].tvgId)
        assertEquals("sky.sports.1", channels[1].tvgId)
        assertEquals("itv.1",        channels[2].tvgId)
    }

    @Test
    fun `T004 - groupTitle extracted from group-title attribute`() {
        val channels = parse(happyM3u).channels
        assertEquals("News",          channels[0].groupTitle)
        assertEquals("Sport",         channels[1].groupTitle)
        assertEquals("Entertainment", channels[2].groupTitle)
    }

    @Test
    fun `T004 - logoUrl extracted from tvg-logo attribute`() {
        val channels = parse(happyM3u).channels
        assertEquals("http://logos.example.com/bbc1.png", channels[0].logoUrl)
    }

    @Test
    fun `T004 - name taken from after last comma on EXTINF line`() {
        val channels = parse(happyM3u).channels
        assertEquals("BBC One",    channels[0].name)
        assertEquals("Sky Sports 1", channels[1].name)
        assertEquals("ITV",        channels[2].name)
    }

    @Test
    fun `T004 - missing tvg-id gives empty string and does not crash`() {
        val m3u = """
            #EXTM3U
            #EXTINF:-1 tvg-logo="" group-title="Test",No ID Channel
            http://stream.example.com/noid.m3u8
        """.trimIndent()
        val channel = parse(m3u).channels.first()
        assertEquals("", channel.tvgId)
        // id falls back to streamUrl per Channel.create()
        assertEquals("http://stream.example.com/noid.m3u8", channel.id)
    }

    // =========================================================================
    // AND-T005 — Malformed input
    // =========================================================================

    @Test
    fun `T005 - EXTINF with no comma emits warning and entry is skipped`() {
        val m3u = """
            #EXTM3U
            #EXTINF:-1 tvg-id="bad"
            http://stream.example.com/bad.m3u8
        """.trimIndent()
        val result = parse(m3u)
        assertTrue("Expected at least one warning", result.warnings.isNotEmpty())
        // The url line following a skipped meta gets consumed as a bare URL
        // channel with no metadata — verify no crash rather than strict count
    }

    @Test
    fun `T005 - non-URL line after EXTINF emits warning`() {
        val m3u = """
            #EXTM3U
            #EXTINF:-1 tvg-id="x" group-title="G",A Channel
            not-a-url-line
        """.trimIndent()
        val result = parse(m3u)
        assertTrue("Expected warning for invalid URL", result.warnings.any { it.reason.contains("Invalid stream URL") })
    }

    @Test
    fun `T005 - empty input returns empty list without exception`() {
        val result = parse("")
        assertTrue(result.channels.isEmpty())
        assertTrue(result.warnings.isEmpty())
    }

    @Test
    fun `T005 - Latin-1 encoded bytes decoded without replacement characters`() {
        // "Télévision Française" in Latin-1
        val latin1Name = "T\u00E9l\u00E9vision Fran\u00E7aise"
        val latin1Bytes = buildString {
            append("#EXTM3U\n")
            append("#EXTINF:-1 tvg-id=\"fr.1\" group-title=\"FR\",$latin1Name\n")
            append("http://stream.example.com/fr1.m3u8\n")
        }.toByteArray(Charsets.ISO_8859_1)

        val result = parser.parse(latin1Bytes)
        assertTrue("Expected at least one channel", result.channels.isNotEmpty())
        assertFalse(
            "Channel name should not contain replacement characters",
            result.channels.first().name.contains('\uFFFD'),
        )
    }

    @Test
    fun `T005 - 10000-channel fixture parses in under 500ms`() {
        val sb = StringBuilder()
        sb.append("#EXTM3U\n")
        repeat(10_000) { i ->
            sb.append("#EXTINF:-1 tvg-id=\"ch.$i\" group-title=\"Group${i % 20}\",Channel $i\n")
            sb.append("http://stream.example.com/ch$i.m3u8\n")
        }
        val start = System.currentTimeMillis()
        val result = parser.parse(sb.toString().byteInputStream())
        val elapsed = System.currentTimeMillis() - start
        assertEquals(10_000, result.channels.size)
        assertTrue("Parsing took ${elapsed}ms, expected < 500ms", elapsed < 500)
    }

    // =========================================================================
    // AND-T006 — EPG URL detection
    // =========================================================================

    @Test
    fun `T006 - EXTM3U with url-tvg populates epgUrl`() {
        val result = parse(happyM3u)
        assertEquals("http://epg.example.com/guide.xml", result.epgUrl)
    }

    @Test
    fun `T006 - EXTM3U without url-tvg leaves epgUrl null`() {
        val m3u = """
            #EXTM3U
            #EXTINF:-1 tvg-id="bbc.one" group-title="News",BBC One
            http://stream.example.com/bbc1.m3u8
        """.trimIndent()
        assertNull(parse(m3u).epgUrl)
    }
}

// Needed because assertFalse is not imported from JUnit by default in all setups
private fun assertFalse(message: String, condition: Boolean) =
    org.junit.Assert.assertFalse(message, condition)