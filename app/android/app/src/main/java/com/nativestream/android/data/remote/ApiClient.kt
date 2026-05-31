// app/src/main/java/com/nativestream/android/data/remote/ApiClient.kt
//
// API Client (Ktor)
// Mirrors APIClient.swift actor exactly — same endpoints, same error mapping,
// same timeout values. Server URL is configurable (not hardcoded) because
// Android connects over LAN, not localhost.
//
// Inject via Hilt (see AppModule.kt). Do not instantiate directly.

package com.nativestream.android.data.remote
import io.ktor.client.engine.android.Android


import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import android.util.Log
import javax.inject.Singleton

private const val TAG = "ApiClient"
private const val REQUEST_TIMEOUT_MS  = 10_000L
private const val RESOURCE_TIMEOUT_MS = 30_000L
private const val UNMATCHED_DEFAULT_LIMIT = 50

@Singleton
class ApiClient  constructor() {

    // ── Base URL — set during onboarding / settings change ───────────────────

    @Volatile private var baseUrl: String = "http://192.168.1.42:8888"

    fun setBaseUrl(url: String) {
        baseUrl = url.trimEnd('/')
    }

    // ── Ktor HTTP client ──────────────────────────────────────────────────────

    private val httpClient = HttpClient(Android) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient         = true
            })
        }
        install(Logging) {
            logger = object : Logger {
                override fun log(message: String) { Log.d(TAG, message) }
            }
            level = LogLevel.INFO
        }
        engine {
            connectTimeout = REQUEST_TIMEOUT_MS.toInt()
            socketTimeout  = RESOURCE_TIMEOUT_MS.toInt()
        }
    }

    // ── Health ────────────────────────────────────────────────────────────────

    suspend fun health(): HealthResponse =
        get("api/health")

    // ── Playlist & EPG (raw bytes — consumed by parsers in AND-005 / AND-006) ─

    suspend fun playlistData(): ByteArray =
        rawGet("playlist.m3u")

    suspend fun epgData(): ByteArray =
        rawGet("epg.xml")

    // ── Channels ──────────────────────────────────────────────────────────────

    suspend fun listChannels(): List<ChannelResponse> {
        val response: ChannelListResponse = get("api/channels")
        return response.channels
    }

    suspend fun getChannel(id: String): ChannelDetailResponse =
        get("api/channels/$id")

    suspend fun createChannel(request: CreateChannelRequest): ChannelDetailResponse =
        post("api/channels", request)

    suspend fun updateChannel(id: String, request: UpdateChannelRequest) {
        put<StatusResponse>("api/channels/$id", request)
    }

    suspend fun deleteChannel(id: String) {
        executeDelete("api/channels/$id")
    }

    // ── Probe ─────────────────────────────────────────────────────────────────

    suspend fun triggerProbe() {
        post<StatusResponse>("api/probe", EmptyBody())
    }

    // ── Discovery ─────────────────────────────────────────────────────────────

    suspend fun discoveryStatus(): DiscoveryStatusResponse =
        get("api/discovery/status")

    suspend fun triggerDiscovery() {
        post<StatusResponse>("api/discovery/run", EmptyBody())
    }

    suspend fun unmatchedLinks(limit: Int = UNMATCHED_DEFAULT_LIMIT): UnmatchedResponse =
        get("api/discovery/unmatched?limit=$limit")

    suspend fun assignUnmatchedLink(channelId: String, streamUrl: String) {
        updateChannel(channelId, UpdateChannelRequest(streamUrl = streamUrl))
    }

    // ── HTTP primitives ───────────────────────────────────────────────────────

    private suspend inline fun <reified T> get(path: String): T =
        executeAndDecode { httpClient.get(resolve(path)) }

    private suspend fun rawGet(path: String): ByteArray =
        wrapNetworkErrors(path) {
            val response = httpClient.get(resolve(path))
            guardSuccess(response)
            response.body()
        }

    private suspend inline fun <reified T> post(path: String, body: Any): T =
        executeAndDecode {
            httpClient.post(resolve(path)) {
                contentType(ContentType.Application.Json)
                setBody(body)
            }
        }

    private suspend inline fun <reified T> put(path: String, body: Any): T =
        executeAndDecode {
            httpClient.put(resolve(path)) {
                contentType(ContentType.Application.Json)
                setBody(body)
            }
        }

    private suspend fun executeDelete(path: String) {
        wrapNetworkErrors(path) {
            val response = httpClient.delete(resolve(path))
            guardSuccess(response)
        }
    }

    private suspend inline fun <reified T> executeAndDecode(
        crossinline block: suspend () -> HttpResponse,
    ): T = wrapNetworkErrors("") {
        val response = block()
        guardSuccess(response)
        try {
            response.body<T>()
        } catch (cause: Exception) {
            throw ApiError.DecodingFailed(cause)
        }
    }

    private suspend fun <T> wrapNetworkErrors(path: String, block: suspend () -> T): T =
        try {
            block()
        } catch (apiError: ApiError) {
            throw apiError
        } catch (cause: Exception) {
            throw ApiError.ServerUnreachable(resolve(path))
        }

    private suspend fun guardSuccess(response: HttpResponse) {
        if (!response.status.isSuccess()) {
            val body = runCatching { response.bodyAsText() }.getOrNull()
            throw ApiError.HttpError(response.status.value, body)
        }
    }

    private fun resolve(path: String): String = "$baseUrl/$path"
}