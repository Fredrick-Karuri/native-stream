// app/src/main/java/com/nativestream/android/domain/model/SportCategory.kt
//
//Sport Category
// Drives EPG keyword matching, Browse chip filtering, and sport chip
// visibility logic.

package com.nativestream.android.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class SportCategory(val rawValue: String) {

    FOOTBALL("football"),
    RUGBY("rugby"),
    TENNIS("tennis"),
    BASKETBALL("basketball"),
    CRICKET("cricket"),
    GOLF("golf");

    val label: String get() = rawValue.replaceFirstChar { it.uppercase() }

    /**
     * EPG title keywords used to detect whether a programme belongs to
     * this sport. Matching is case-insensitive (callers lowercase before check).
     */
    val epgKeywords: List<String> get() = when (this) {
        FOOTBALL   -> listOf(
            "football", "soccer", "premier league", "bundesliga",
            "ligue 1", "champions league", "europa league", "nwsl", "mls",
        )
        RUGBY      -> listOf("rugby", "six nations", "pro14")
        TENNIS     -> listOf("tennis", "atp tour", "wta tour", "wimbledon")
        BASKETBALL -> listOf("nba", "wnba", "euroleague")
        CRICKET    -> listOf("cricket", "ipl cricket", "test match", "odi")
        GOLF       -> listOf(
            "golf", "pga tour live", "lpga", "ryder cup", "open championship",
        )
    }

    companion object {
        /** All keywords across every sport — used for broad isSportMatch checks. */
        val allKeywords: List<String> by lazy {
            entries.flatMap { it.epgKeywords }
        }
    }
}