// app/src/main/java/com/nativestream/android/data/parser/EpgParser.kt
//
// Kotlin XMLTV parser using XmlPullParser (SAX-equivalent).
//   - Produces Map<String, List<Programme>> keyed by channel tvg-id
//   - Supports files > 100MB without OOM — no full-file buffering
//   - FX-002 case-insensitive fallback matching handled in EpgStore
//   - Match rate logged on load (AND-026)
//   - XMLTV date format: "yyyyMMddHHmmss Z"

package com.nativestream.android.data.parser

import android.util.Log
import android.util.Xml
import com.nativestream.android.domain.model.Programme
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "EpgParser"

private const val ELEMENT_PROGRAMME = "programme"
private const val ELEMENT_TITLE     = "title"

private const val ATTR_CHANNEL = "channel"
private const val ATTR_START   = "start"
private const val ATTR_STOP    = "stop"

// XMLTV timestamp format — matches EPGParser.swift "yyyyMMddHHmmss Z"
private const val XMLTV_DATE_FORMAT = "yyyyMMddHHmmss Z"

@Singleton
class EpgParser @Inject constructor() {

    private val dateFormatter = SimpleDateFormat(XMLTV_DATE_FORMAT, Locale.US).apply {
        isLenient = false
    }

    /**
     * Parses an XMLTV [InputStream] into an [EpgStore].
     * Reads the stream element-by-element — safe for files > 100MB.
     */
    fun parse(stream: InputStream): EpgStore {
        val programmes = mutableMapOf<String, MutableList<Programme>>()

        val parser = Xml.newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            setInput(stream, null) // let the parser detect encoding from the XML declaration
        }

        // State machine — mirrors EPGParser.swift XMLParserDelegate fields
        var insideProgramme = false
        var insideTitle     = false
        var currentChannelId: String? = null
        var currentStart: Long?       = null
        var currentStop: Long?        = null
        var titleBuffer               = StringBuilder()

        try {
            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> when (parser.name) {
                        ELEMENT_PROGRAMME -> {
                            insideProgramme  = true
                            currentChannelId = parser.getAttributeValue(null, ATTR_CHANNEL)
                            currentStart     = parseXmltvDate(parser.getAttributeValue(null, ATTR_START))
                            currentStop      = parseXmltvDate(parser.getAttributeValue(null, ATTR_STOP))
                            titleBuffer.clear()
                        }
                        ELEMENT_TITLE -> if (insideProgramme) {
                            insideTitle = true
                            titleBuffer.clear()
                        }
                    }

                    XmlPullParser.TEXT -> {
                        if (insideTitle) titleBuffer.append(parser.text)
                    }

                    XmlPullParser.END_TAG -> when (parser.name) {
                        ELEMENT_TITLE -> if (insideTitle) {
                            insideTitle = false
                        }
                        ELEMENT_PROGRAMME -> {
                            val channelId = currentChannelId
                            val title     = titleBuffer.toString().trim()
                            val start     = currentStart
                            val stop      = currentStop

                            if (channelId != null && title.isNotEmpty() &&
                                start != null && stop != null) {
                                programmes
                                    .getOrPut(channelId) { mutableListOf() }
                                    .add(Programme(
                                        channelId    = channelId,
                                        title        = title,
                                        startEpochMs = start,
                                        stopEpochMs  = stop,
                                    ))
                            }

                            insideProgramme  = false
                            insideTitle      = false
                            currentChannelId = null
                            currentStart     = null
                            currentStop      = null
                            titleBuffer.clear()
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: XmlPullParserException) {
            // Log and return whatever was successfully parsed — partial EPG
            // is better than crashing. Callers check EpgStore.programmeCount.
            Log.e(TAG, "EPG parse error — returning partial results: ${e.message}")
        }

        val totalProgrammes = programmes.values.sumOf { it.size }
        Log.d(TAG, "EPG parsed: ${programmes.size} channels, $totalProgrammes programmes")

        return EpgStore(programmes)
    }

    /**
     * Parses an XMLTV timestamp string to epoch milliseconds.
     * Returns null for malformed or missing values — mirrors Swift's flatMap(parseDate).
     */
    private fun parseXmltvDate(value: String?): Long? {
        if (value.isNullOrBlank()) return null
        return try {
            dateFormatter.parse(value)?.time
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse XMLTV date: '$value'")
            null
        }
    }
}