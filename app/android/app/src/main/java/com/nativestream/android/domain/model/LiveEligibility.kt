package com.nativestream.android.domain.model

object LiveEligibility {

    private val NEWS_GROUPS = setOf("news", "information")
    private val SPORT_GROUPS = setOf("sport", "sports")

    fun isLive(channel: Channel?, programme: Programme?): Boolean {
        if (channel == null || programme == null) return false
        if (!programme.isNow) return false

        val group = channel.groupTitle.lowercase()
        return when {
            NEWS_GROUPS.any  { group.contains(it) } -> true          // news = always live
            SPORT_GROUPS.any { group.contains(it) } -> programme.isSportMatch || programme.title.contains(" vs ", ignoreCase = true)
            else -> false                                             // entertainment = never
        }
    }
}