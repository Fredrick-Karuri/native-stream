// app/src/main/java/com/nativestream/android/data/parser/M3uParseResult.kt
//
// M3U Parse Result
// Container returned by M3uParser.parse().

package com.nativestream.android.data.parser

import com.nativestream.android.domain.model.Channel

data class M3uParseResult(
    val channels: List<Channel>,
    val epgUrl: String?,
    val warnings: List<M3uParseWarning>,
)