// app/src/test/java/com/nativestream/android/domain/ChannelTest.kt
//
// Channel identity
// Tests the id-derivation and equality/hash behaviour of Channel.

package com.nativestream.android.domain

import com.nativestream.android.domain.model.Channel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ChannelTest {

    // ── id derivation ─────────────────────────────────────────────────────────

    @Test
    fun `create with non-empty tvgId sets id to tvgId`() {
        val channel = Channel.create(
            tvgId     = "bbc.one",
            name      = "BBC One",
            streamUrl = "http://example.com/bbc1.m3u8",
        )
        assertEquals("bbc.one", channel.id)
    }

    @Test
    fun `create with empty tvgId falls back to streamUrl`() {
        val url     = "http://example.com/fallback.m3u8"
        val channel = Channel.create(
            tvgId     = "",
            name      = "No ID Channel",
            streamUrl = url,
        )
        assertEquals(url, channel.id)
    }

    // ── equality ──────────────────────────────────────────────────────────────

    @Test
    fun `two channels with same id are equal regardless of other fields`() {
        val a = Channel.create(
            tvgId     = "sky.sports",
            name      = "Sky Sports 1",
            groupTitle = "Sport",
            streamUrl = "http://example.com/sky1.m3u8",
        )
        val b = Channel.create(
            tvgId     = "sky.sports",
            name      = "Completely Different Name",
            groupTitle = "Entertainment",
            streamUrl = "http://other.example.com/different.m3u8",
        )
        assertEquals(a, b)
    }

    @Test
    fun `two channels with different id are not equal`() {
        val a = Channel.create(
            tvgId     = "bbc.one",
            name      = "BBC One",
            streamUrl = "http://example.com/bbc1.m3u8",
        )
        val b = Channel.create(
            tvgId     = "bbc.two",
            name      = "BBC Two",
            streamUrl = "http://example.com/bbc2.m3u8",
        )
        assertNotEquals(a, b)
    }

    // ── hashCode contract ─────────────────────────────────────────────────────

    @Test
    fun `equal channels have the same hashCode`() {
        val a = Channel.create(tvgId = "ch4", name = "Channel 4", streamUrl = "http://example.com/ch4.m3u8")
        val b = Channel.create(tvgId = "ch4", name = "Different", streamUrl = "http://other.com/ch4.m3u8")
        assertEquals(a.hashCode(), b.hashCode())
    }
}