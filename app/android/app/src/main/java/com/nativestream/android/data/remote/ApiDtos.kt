// app/src/main/java/com/nativestream/android/data/remote/ApiDtos.kt
//
// API Data Transfer Objects
// Snake_case JSON keys mapped via @SerialName to camelCase Kotlin fields.
// Dates arrive as ISO-8601 strings and are stored as such — callers parse
// to Instant/Date at the domain boundary.

package com.nativestream.android.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ── Health ────────────────────────────────────────────────────────────────────

@Serializable
data class HealthResponse(
    val status: String,
    val uptime: String,
    val channels: Int,
    val healthy: Int,
    @SerialName("last_probe") val lastProbe: String? = null,
)

// ── Channel list ──────────────────────────────────────────────────────────────

@Serializable
data class ChannelResponse(
    val id: String,
    val name: String,
    @SerialName("group_title")     val groupTitle: String,
    @SerialName("tvg_id")          val tvgId: String,
    @SerialName("logo_url")        val logoUrl: String,
    val healthy: Boolean,
    @SerialName("active_score")    val activeScore: Double,
    @SerialName("candidate_count") val candidateCount: Int,
)

@Serializable
data class ChannelListResponse(
    val channels: List<ChannelResponse>,
)

// ── Channel detail ────────────────────────────────────────────────────────────

@Serializable
data class ChannelDetailResponse(
    val id: String,
    val name: String,
    @SerialName("group_title") val groupTitle: String,
    @SerialName("tvg_id")      val tvgId: String,
    @SerialName("logo_url")    val logoUrl: String,
    val keywords: List<String>,
    @SerialName("active_link") val activeLink: LinkScoreResponse? = null,
    val candidates: List<LinkScoreResponse>,
)

@Serializable
data class LinkScoreResponse(
    val url: String,
    val score: Double,
    @SerialName("latency_ms")     val latencyMs: Int,
    val state: String,
    @SerialName("fail_count")     val failCount: Int,
    @SerialName("failure_reason") val failureReason: String? = null,
    val headers: Map<String, String>? = null,
)

// ── Mutations ─────────────────────────────────────────────────────────────────

@Serializable
data class CreateChannelRequest(
    val name: String,
    @SerialName("group_title") val groupTitle: String,
    @SerialName("tvg_id")      val tvgId: String,
    @SerialName("logo_url")    val logoUrl: String,
    @SerialName("stream_url")  val streamUrl: String,
    val keywords: List<String>,
)

@Serializable
data class UpdateChannelRequest(
    val name: String?             = null,
    @SerialName("group_title") val groupTitle: String? = null,
    @SerialName("stream_url")  val streamUrl: String?  = null,
    val keywords: List<String>?   = null,
    @SerialName("stream_headers") val streamHeaders: Map<String, String>? = null,
)

// ── Discovery ─────────────────────────────────────────────────────────────────

@Serializable
data class DiscoveryStatusResponse(
    @SerialName("last_run")        val lastRun: String? = null,
    @SerialName("found_today")     val foundToday: Int,
    @SerialName("promoted_today")  val promotedToday: Int,
    @SerialName("unmatched_count") val unmatchedCount: Int,
)

@Serializable
data class UnmatchedLink(
    val url: String,
    @SerialName("source_url") val sourceUrl: String,
    val context: String,
)

@Serializable
data class UnmatchedResponse(
    val unmatched: List<UnmatchedLink>,
    val total: Int,
)

// ── Generic ───────────────────────────────────────────────────────────────────

@Serializable
data class StatusResponse(val status: String)

/** Used for POST bodies that carry no payload. */
@Serializable
internal class EmptyBody