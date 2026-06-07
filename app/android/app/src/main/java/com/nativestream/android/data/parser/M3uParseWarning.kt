// app/src/main/java/com/nativestream/android/data/parser/M3uParseWarning.kt
//
//  M3U Parse Warning
// Non-fatal warning emitted during M3U parsing.

package com.nativestream.android.data.parser

data class M3uParseWarning(
    val lineNumber: Int,
    val reason: String,
)