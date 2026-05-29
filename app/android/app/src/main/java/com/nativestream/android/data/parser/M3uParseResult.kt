// app/src/main/java/com/nativestream/android/data/parser/M3uParseResult.kt
//
// NS-021: M3U Parse Result
// Container returned by M3uParser.parse(). Mirrors the tuple returned by
// M3UParser.parse() in M3UParser.swift.

package com.nativestream.android.data.parser

import com.nativestream.android.domain.model.Channel

data class M3uParseResult(
    val channels: List<Channel>,
    val epgUrl: String?,
    val warnings: List<M3uParseWarning>,
)