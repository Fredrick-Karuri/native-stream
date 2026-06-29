// app/src/main/java/com/nativestream/android/data/parser/M3uParser.kt
//
// M3U Parser
// Pure Kotlin M3U/M3U8 playlist parser. No third-party dependencies.
//   - Parses InputStream line-by-line — no full-file buffering
//   - Extracts tvg-id, tvg-logo, group-title from #EXTINF
//   - Skips malformed entries with a logged warning (no crash)
//   - Channels with missing tvg-id get empty string
//   - Detects url-tvg on the #EXTM3U header line
//
// Target: 10,000-channel M3U in < 500ms (verified by M3uParserTest)

package com.nativestream.android.data.parser

import android.util.Log
import com.nativestream.android.domain.model.Channel
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "M3uParser"

private const val HEADER_PREFIX    = "#EXTM3U"
private const val EXTINF_PREFIX    = "#EXTINF:"
private const val COMMENT_PREFIX   = "#"
private const val FALLBACK_GROUP   = "Uncategorised"

// Attribute keys used in #EXTINF lines
private const val ATTR_TVG_ID      = "tvg-id"
private const val ATTR_TVG_LOGO    = "tvg-logo"
private const val ATTR_GROUP_TITLE = "group-title"
private const val ATTR_URL_TVG     = "url-tvg"

// Regex patterns for key="value" and key='value' attribute extraction
private val DOUBLE_QUOTE_PATTERN = Regex("""(\S+)="([^"]*)"""")
private val SINGLE_QUOTE_PATTERN = Regex("""(\S+)='([^']*)'""")

private const val INITIAL_CHANNEL_CAPACITY  = 512
private const val INITIAL_WARNING_CAPACITY  = 16

@Singleton
class M3uParser @Inject constructor() {

    companion object {
        /** Extract only the EPG url-tvg from the M3U header — cheap, no full parse. */
        fun extractEpgUrl(bytes: ByteArray): String? {
            val text = bytes.toString(Charsets.UTF_8).let {
                if (it.contains('\uFFFD')) bytes.toString(Charsets.ISO_8859_1) else it
            }
            val firstLine = text.lineSequence().firstOrNull { it.isNotBlank() } ?: return null
            if (!firstLine.trimStart().startsWith(HEADER_PREFIX)) return null
            return DOUBLE_QUOTE_PATTERN.findAll(firstLine)
                .find { it.groupValues[1] == ATTR_URL_TVG }?.groupValues?.get(2)
                ?: SINGLE_QUOTE_PATTERN.findAll(firstLine)
                    .find { it.groupValues[1] == ATTR_URL_TVG }?.groupValues?.get(2)
        }
    }

    /**
     * Parse M3U content from a raw [ByteArray].
     * UTF-8 is attempted first; ISO-8859-1 (Latin-1) used as fallback — mirrors Swift behaviour.
     */
    fun parse(bytes: ByteArray): M3uParseResult {
        val text = bytes.toString(Charsets.UTF_8).let {
            // If the decoded text contains replacement characters, retry with Latin-1
            if (it.contains('\uFFFD')) bytes.toString(Charsets.ISO_8859_1) else it
        }
        return parse(text.byteInputStream())
    }

    /**
     * Parse M3U content from an [InputStream].
     * Reads line-by-line — no full-file buffering — safe for very large playlists.
     */
    fun parse(stream: InputStream): M3uParseResult {
        val channels  = ArrayList<Channel>(INITIAL_CHANNEL_CAPACITY)
        val warnings  = ArrayList<M3uParseWarning>(INITIAL_WARNING_CAPACITY)
        var epgUrl: String? = null
        var pendingMeta: ChannelMetadata? = null
        var lineNumber = 0

        BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
            reader.forEachLine { rawLine ->
                lineNumber++
                val line = rawLine.trim()
                if (line.isEmpty()) return@forEachLine

                when {
                    lineNumber == 1 && line.startsWith(HEADER_PREFIX) -> {
                        epgUrl = extractAttributeValue(ATTR_URL_TVG, from = line)
                    }

                    line.startsWith(EXTINF_PREFIX) -> {
                        pendingMeta = parseExtInf(line, lineNumber, warnings)
                    }

                    line.startsWith(COMMENT_PREFIX) -> {
                        // Other directive or comment — skip
                    }

                    else -> {
                        // Should be a stream URL
                        if (!looksLikeUrl(line)) {
                            warnings.add(M3uParseWarning(lineNumber, "Invalid stream URL: $line"))
                            Log.w(TAG, "Line $lineNumber — invalid stream URL, skipping")
                            pendingMeta = null
                            return@forEachLine
                        }

                        val meta = pendingMeta ?: ChannelMetadata(
                            name = "Channel ${channels.size + 1}",
                        )
                        channels.add(
                            Channel.create(
                                tvgId      = meta.tvgId,
                                name       = meta.name,
                                groupTitle = meta.groupTitle,
                                logoUrl    = meta.logoUrl,
                                streamUrl  = line,
                            )
                        )
                        pendingMeta = null
                    }
                }
            }
        }

        Log.d(TAG, "Parsed ${channels.size} channels, ${warnings.size} warnings")
        return M3uParseResult(channels = channels, epgUrl = epgUrl, warnings = warnings)
    }

    // ── EXTINF line parsing ───────────────────────────────────────────────────

    /**
     * Parses a single #EXTINF line into a [ChannelMetadata].
     * Format: #EXTINF:-1 tvg-id="…" tvg-logo="…" group-title="…",Display Name
     * Display name is everything after the last comma.
     */
    private fun parseExtInf(
        line: String,
        lineNumber: Int,
        warnings: MutableList<M3uParseWarning>,
    ): ChannelMetadata? {
        val commaIndex = line.lastIndexOf(',')
        if (commaIndex < 0) {
            warnings.add(M3uParseWarning(lineNumber, "EXTINF missing comma separator"))
            return null
        }

        val displayName  = line.substring(commaIndex + 1).trim()
        val attributeSection = line.substring(0, commaIndex)

        val name       = displayName.ifEmpty { "Unknown Channel" }
        val tvgId      = extractAttributeValue(ATTR_TVG_ID,      from = attributeSection) ?: ""
        val groupTitle = extractAttributeValue(ATTR_GROUP_TITLE,  from = attributeSection)
            ?.takeUnless { it.isEmpty() } ?: FALLBACK_GROUP
        val logoUrl    = extractAttributeValue(ATTR_TVG_LOGO,     from = attributeSection)

        return ChannelMetadata(
            tvgId      = tvgId,
            name       = name,
            groupTitle = groupTitle,
            logoUrl    = logoUrl,
        )
    }

    // ── Attribute extraction ──────────────────────────────────────────────────

    /**
     * Extracts a quoted attribute value from an EXTINF attribute string.
     * Handles both double-quoted and single-quoted values — mirrors Swift impl.
     */
    private fun extractAttributeValue(key: String, from: String): String? {
        // Try key="value" first, then key='value'
        DOUBLE_QUOTE_PATTERN.findAll(from).forEach { match ->
            if (match.groupValues[1] == key) return match.groupValues[2]
        }
        SINGLE_QUOTE_PATTERN.findAll(from).forEach { match ->
            if (match.groupValues[1] == key) return match.groupValues[2]
        }
        return null
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Lightweight URL sanity check — avoids constructing a full URI object for
     * every line. Mirrors Swift's `URL(string:)?.scheme != nil` guard.
     */
    private fun looksLikeUrl(line: String): Boolean =
        line.startsWith("http://")  ||
                line.startsWith("https://") ||
                line.startsWith("rtmp://")  ||
                line.startsWith("rtsp://")

    // ── Internal metadata accumulator ────────────────────────────────────────

    private data class ChannelMetadata(
        val tvgId:      String  = "",
        val name:       String,
        val groupTitle: String  = FALLBACK_GROUP,
        val logoUrl:    String? = null,
    )
}