// app/src/main/java/com/nativestream/android/data/parser/M3uParseWarning.kt
//
// NS-021: M3U Parse Warning
// Non-fatal warning emitted during M3U parsing. Mirrors M3UParseWarning from
// M3UParser.swift — line number + human-readable reason.

package com.nativestream.android.data.parser

data class M3uParseWarning(
    val lineNumber: Int,
    val reason: String,
)