// app/src/main/java/com/nativestream/android/data/remote/ApiClient.kt
//
// API Client (Ktor)
// Server URL is configurable (not hardcoded) because
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
import io.ktor.client.engine.HttpClientEngine
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import android.util.Log
import javax.inject.Singleton
import android.app.Application
import com.google.protobuf.util.JsonFormat
import javax.inject.Inject
import io.ktor.client.plugins.cache.HttpCache
import io.ktor.client.plugins.cache.storage.FileStorage
import io.ktor.http.HttpHeaders
import io.ktor.client.request.header
import java.io.File
import com.nativestream.android.data.remote.ServerUrlResolver

private const val TAG = "ApiClient"
private const val REQUEST_TIMEOUT_MS  = 10_000L
private const val RESOURCE_TIMEOUT_MS = 30_000L
private const val UNMATCHED_DEFAULT_LIMIT = 50

@Singleton
class ApiClient  @Inject constructor(
    private val application: Application,
    private val engine: HttpClientEngine = Android.create()
) {

// ── Base URL — set during onboarding / settings change ───────────────────

    @Volatile private var baseUrl: String = ServerUrlResolver.HOSTED_DEFAULT_URL
    @Volatile private var apiToken: String? = null

    fun setBaseUrl(url: String) {
        baseUrl = url.trimEnd('/')
    }

    fun setApiToken(token: String?) {
        apiToken = token?.ifBlank { null }
    }

    // ── Ktor HTTP clients ─────────────────────────────────────────────────────

    private fun buildClient(withAuth: Boolean) = HttpClient(engine) {
        install(HttpCache) {
            val cacheDirectory = File(application.cacheDir, "ktor_network_cache")
            publicStorage(FileStorage(cacheDirectory))
        }

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

        if (withAuth) {
            install(io.ktor.client.plugins.DefaultRequest) {
                apiToken?.let { header(HttpHeaders.Authorization, "Bearer $it") }
            }
        }

        engine {
            if (this is io.ktor.client.engine.android.AndroidEngineConfig) {
                connectTimeout = REQUEST_TIMEOUT_MS.toInt()
                socketTimeout  = RESOURCE_TIMEOUT_MS.toInt()
            }
        }
    }

    // Used by every method that talks to our own server (resolve(path)).
    private val httpClient = buildClient(withAuth = true)

    // Used only by fetchRawUrl() — never carries the API token.
    private val externalHttpClient = buildClient(withAuth = false)

    // ── Pairing (unauthenticated — device has no token yet) ─────────────────

    /**
     * Starts a pairing session. Unauthenticated by construction: a device
     * with no credential yet must be able to reach this endpoint.
     * Uses externalHttpClient, same as fetchRawUrl — never attaches
     * Authorization even if a stale token happens to be set.
     */
    suspend fun startPairing(platform: String): com.stream.v1.PairStartResponse {
        val request = com.stream.v1.PairStartRequest.newBuilder()
            .setPlatform(platform)
            .build()
        val json = JsonFormat.printer().preservingProtoFieldNames().print(request)
        val bytes = wrapNetworkErrors("api/pair/start") {
            val response = externalHttpClient.post(resolve("api/pair/start")) {
                contentType(ContentType.Application.Json)
                setBody(json)
            }
            guardSuccess(response)
            response.body<ByteArray>()
        }
        val builder = com.stream.v1.PairStartResponse.newBuilder()
        return try {
            JsonFormat.parser().ignoringUnknownFields().merge(bytes.decodeToString(), builder)
            builder.build()
        } catch (cause: Exception) {
            throw ApiError.DecodingFailed(cause)
        }
    }

    /**
     * Polls one pairing session's status. Unauthenticated, session-scoped.
     * Callers are expected to poll this on an interval and
     * stop once status is no longer "pending".
     */
    suspend fun pairingStatus(sessionId: String): com.stream.v1.PairStatusResponse {
        val bytes = wrapNetworkErrors("api/pair/status/$sessionId") {
            val response = externalHttpClient.get(resolve("api/pair/status/$sessionId"))
            guardSuccess(response)
            response.body<ByteArray>()
        }
        val builder = com.stream.v1.PairStatusResponse.newBuilder()
        return try {
            JsonFormat.parser().ignoringUnknownFields().merge(bytes.decodeToString(), builder)
            builder.build()
        } catch (cause: Exception) {
            throw ApiError.DecodingFailed(cause)
        }
    }

    // ── Health ────────────────────────────────────────────────────────────────

    suspend fun health(): com.stream.v1.HealthResponse {
        val bytes = rawGet("api/health")
        val builder = com.stream.v1.HealthResponse.newBuilder()
        return try {
            JsonFormat.parser().ignoringUnknownFields().merge(bytes.decodeToString(), builder)
            builder.build()
        } catch (cause: Exception) {
            throw ApiError.DecodingFailed(cause)
        }
    }

    suspend fun playlistData(): ByteArray =
        rawGet("playlist.m3u")

    suspend fun epgData(): ByteArray =
        rawGet("epg.xml")

    // ── Channels ──────────────────────────────────────────────────────────────

    suspend fun listChannels(): List<com.stream.v1.ChannelResponse> {
        val bytes = rawGet("api/channels")
        val builder = com.stream.v1.ChannelListResponse.newBuilder()
        return try {
            JsonFormat.parser().ignoringUnknownFields().merge(bytes.decodeToString(), builder)
            builder.build().channelsList
        } catch (cause: Exception) {
            throw ApiError.DecodingFailed(cause)
        }
    }

    suspend fun getChannel(id: String): com.stream.v1.ChannelDetailResponse {
        val bytes = rawGet("api/channels/$id")
        val builder = com.stream.v1.ChannelDetailResponse.newBuilder()
        return try {
            JsonFormat.parser().ignoringUnknownFields().merge(bytes.decodeToString(), builder)
            builder.build()
        } catch (cause: Exception) {
            throw ApiError.DecodingFailed(cause)
        }
    }

    suspend fun createChannel(request: com.stream.v1.CreateChannelRequest): com.stream.v1.ChannelDetailResponse {
        val json = JsonFormat.printer().preservingProtoFieldNames().print(request)
        val bytes = rawPostJson("api/channels", json)
        val builder = com.stream.v1.ChannelDetailResponse.newBuilder()
        return try {
            JsonFormat.parser().ignoringUnknownFields().merge(bytes.decodeToString(), builder)
            builder.build()
        } catch (cause: Exception) {
            throw ApiError.DecodingFailed(cause)
        }
    }

    suspend fun updateChannel(id: String, request: com.stream.v1.UpdateChannelRequest) {
        val json = JsonFormat.printer().preservingProtoFieldNames().print(request)
        rawPut("api/channels/$id", json)
    }

    suspend fun deleteChannel(id: String) {
        executeDelete("api/channels/$id")
    }

    // ── Probe ─────────────────────────────────────────────────────────────────

    suspend fun triggerProbe() {
        wrapNetworkErrors("api/probe") {
            val response = httpClient.post(resolve("api/probe"))
            guardSuccess(response)
        }
    }

    suspend fun getProxyEnabled(): Boolean {
        val bytes = rawGet("api/proxy/config")
        val builder = com.stream.v1.ProxyConfigResponse.newBuilder()
        return try {
            JsonFormat.parser().ignoringUnknownFields().merge(bytes.decodeToString(), builder)
            builder.build().enabled
        } catch (cause: Exception) {
            throw ApiError.DecodingFailed(cause)
        }
    }

    suspend fun putProxyEnabled(enabled: Boolean) {
        val request = com.stream.v1.UpdateProxyConfigRequest.newBuilder()
            .setEnabled(enabled)
            .build()
        val json = JsonFormat.printer().preservingProtoFieldNames().print(request)
        rawPut("api/proxy/config", json)
    }

    // ── Discovery ─────────────────────────────────────────────────────────────

    suspend fun discoveryStatus(): com.stream.v1.DiscoveryStatusResponse {
        val bytes = rawGet("api/discovery/status")
        val builder = com.stream.v1.DiscoveryStatusResponse.newBuilder()
        return try {
            JsonFormat.parser().ignoringUnknownFields().merge(bytes.decodeToString(), builder)
            builder.build()
        } catch (cause: Exception) {
            throw ApiError.DecodingFailed(cause)
        }
    }

    suspend fun triggerDiscovery() {
        wrapNetworkErrors("api/discovery/run") {
            val response = httpClient.post(resolve("api/discovery/run"))
            guardSuccess(response)
        }
    }

    suspend fun unmatchedLinks(limit: Int = UNMATCHED_DEFAULT_LIMIT): com.stream.v1.UnmatchedResponse {
        val bytes = rawGet("api/discovery/unmatched?limit=$limit")
        val builder = com.stream.v1.UnmatchedResponse.newBuilder()
        return try {
            JsonFormat.parser().ignoringUnknownFields().merge(bytes.decodeToString(), builder)
            builder.build()
        } catch (cause: Exception) {
            throw ApiError.DecodingFailed(cause)
        }
    }

    suspend fun assignUnmatchedLink(channelId: String, streamUrl: String) {
        val request = com.stream.v1.UpdateChannelRequest.newBuilder()
            .setStreamUrl(streamUrl)
            .build()
        updateChannel(channelId, request)
    }

    // ── HTTP primitives ───────────────────────────────────────────────────────

    private suspend inline fun <reified T> get(path: String): T =
        executeAndDecode { httpClient.get(resolve(path)) }

    private suspend fun rawGet(path: String): ByteArray =
        wrapNetworkErrors(path) {
            val response = httpClient.get(resolve(path)) {
                // Forces client-side validation fallback policy: Max-Age 2 hours
                header(HttpHeaders.CacheControl, "max-age=7200, public")
            }
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
        } catch (cause: java.io.IOException) {
            throw ApiError.ServerUnreachable(resolve(path))
        }

    private suspend fun guardSuccess(response: HttpResponse) {
        if (!response.status.isSuccess()) {
            val body = runCatching { response.bodyAsText() }.getOrNull()
            throw ApiError.HttpError(response.status.value, body)
        }
    }
    private suspend fun rawPut(path: String, jsonBody: String) {
        wrapNetworkErrors(path) {
            val response = httpClient.put(resolve(path)) {
                contentType(ContentType.Application.Json)
                setBody(jsonBody)
            }
            guardSuccess(response)
        }
    }

    private suspend fun rawPostJson(path: String, jsonBody: String): ByteArray =
        wrapNetworkErrors(path) {
            val response = httpClient.post(resolve(path)) {
                contentType(ContentType.Application.Json)
                setBody(jsonBody)
            }
            guardSuccess(response)
            response.body()
        }

    private fun resolve(path: String): String = "$baseUrl/$path"

    suspend fun fetchRawUrl(url: String): ByteArray =
        wrapNetworkErrors(url) {
            val response = externalHttpClient.get(url) {
                header(HttpHeaders.CacheControl, "max-age=7200, public")
            }
            guardSuccess(response)
            response.body()
        }
}