// app/src/main/java/com/nativestream/android/data/local/StreamQuality.kt

package com.nativestream.android.data.local

enum class StreamQuality(val bitrateBps: Long, val label: String) {
    AUTO(0L,           "Auto"),
    LOW(1_500_000L,    "480p"),
    MEDIUM(4_000_000L, "720p"),
    HIGH(8_000_000L,   "1080p"),
}