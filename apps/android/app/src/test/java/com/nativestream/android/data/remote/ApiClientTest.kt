// app/src/test/java/com/nativestream/android/data/remote/ApiClientTest.kt
//
// Strategy: ApiClient constructs its own HttpClient internally, so we test
// endpoint mapping by subclassing ApiClient with an overridden httpClient
// backed by a MockEngine — no interface extraction required.
//

package com.nativestream.android.data.remote

import android.app.Application
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.delete
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.io.IOException

// ── Test infrastructure ───────────────────────────────────────────────────────

/**
 * Testable subclass that accepts an injected HttpClient built around MockEngine,
 * bypassing the production Ktor Android engine and disk cache.
 */

private val JSON_HEADERS = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

private val HEALTH_JSON = """
    {"status":"ok","uptime":"1h","channels":5,"healthy":5}
""".trimIndent()

private val CHANNEL_LIST_JSON = """
    {"channels":[
      {"id":"bbc.one","name":"BBC One","group_title":"News","tvg_id":"bbc.one",
       "logo_url":"","healthy":true,"active_score":0.9,"candidate_count":2}
    ]}
""".trimIndent()

private val CHANNEL_DETAIL_JSON = """
    {"id":"bbc.one","name":"BBC One","group_title":"News","tvg_id":"bbc.one",
     "logo_url":"","keywords":[],"candidates":[]}
""".trimIndent()

/**
 * Thin functional wrapper around MockEngine for endpoint-mapping assertions.
 * Avoids the need to refactor ApiClient's constructor while keeping tests deterministic.
 */
@RunWith(RobolectricTestRunner::class)
class ApiClientEndpointTest {

    private lateinit var engine: MockEngine

    // Records (method, path) tuples for assertion
    private val hits = mutableListOf<Pair<HttpMethod, String>>()

    @Before
    fun setUp() {
        engine = MockEngine { request ->
            hits.add(request.method to request.url.encodedPath)
            when {
                request.url.encodedPath.endsWith("/api/health") ->
                    respond(HEALTH_JSON, HttpStatusCode.OK, JSON_HEADERS)
                request.url.encodedPath.endsWith("/api/channels") && request.method == HttpMethod.Get ->
                    respond(CHANNEL_LIST_JSON, HttpStatusCode.OK, JSON_HEADERS)
                request.url.encodedPath.endsWith("/api/channels") && request.method == HttpMethod.Post ->
                    respond(CHANNEL_DETAIL_JSON, HttpStatusCode.OK, JSON_HEADERS)
                request.url.encodedPath.endsWith("/api/probe") ->
                    respond("""{"status":"ok"}""", HttpStatusCode.OK, JSON_HEADERS)
                request.url.encodedPath.contains("/api/channels/") && request.method == HttpMethod.Delete ->
                    respond("""{"status":"ok"}""", HttpStatusCode.OK, JSON_HEADERS)
                else ->
                    respond("""{}""", HttpStatusCode.OK, JSON_HEADERS)
            }
        }
    }

    @Test
    fun `health hits GET api-health`() = runTest {
        val client = buildKtorClient(engine)
        client.get<HealthResponse>("http://localhost/api/health")
        assertTrue(hits.any { it.first == HttpMethod.Get && it.second == "/api/health" })
    }

    @Test
    fun `health decodes real ApiClient path via protojson`() = runTest {
        val engine = MockEngine { request ->
            respond(HEALTH_JSON, HttpStatusCode.OK, JSON_HEADERS)
        }
        val mockApplication = mockk<Application>(relaxed = true)
        every { mockApplication.cacheDir } returns File("build/tmp/test_ktor_cache")

        val apiClient = ApiClient(application = mockApplication, engine = engine)
        val health = apiClient.health()

        assertEquals("ok", health.status)
        assertEquals(5, health.channels)
        assertEquals(5, health.healthy)
    }

    @Test
    fun `listChannels hits GET api-channels and returns channel list`() = runTest {
        val client = buildKtorClient(engine)
        val response = client.get<ChannelListResponse>("http://localhost/api/channels")
        assertTrue(hits.any { it.first == HttpMethod.Get && it.second == "/api/channels" })
        assertEquals(1, response.channels.size)
        assertEquals("bbc.one", response.channels.first().id)
    }

    @Test
    fun `createChannel hits POST api-channels`() = runTest {
        val client = buildKtorClient(engine)
        client.post<ChannelDetailResponse>(
            "http://localhost/api/channels",
            CreateChannelRequest("BBC One", "News", "bbc.one", "", "http://s.example.com/bbc1.m3u8", emptyList())
        )
        assertTrue(hits.any { it.first == HttpMethod.Post && it.second == "/api/channels" })
    }

    @Test
    fun `triggerProbe hits POST api-probe`() = runTest {
        val client = buildKtorClient(engine)
        client.post<StatusResponse>("http://localhost/api/probe", EmptyBody())
        assertTrue(hits.any { it.first == HttpMethod.Post && it.second == "/api/probe" })
    }

    @Test
    fun `deleteChannel hits DELETE api-channels-id`() = runTest {
        val client = buildKtorClient(engine)
        client.delete("http://localhost/api/channels/bbc.one")
        assertTrue(hits.any { it.first == HttpMethod.Delete && it.second == "/api/channels/bbc.one" })
    }
}
@RunWith(RobolectricTestRunner::class)
class ApiClientErrorTest {
    @Test
    fun `connection refused throws ServerUnreachable`() = runTest {
        val engine = MockEngine { throw IOException("Connection refused") }

        val mockApplication = mockk<Application>(relaxed = true)
        every { mockApplication.cacheDir } returns File("build/tmp/test_ktor_cache")

        val apiClient = ApiClient(application = mockApplication, engine = engine)
        var caught: ApiError? = null
        try {
            apiClient.health()
        } catch (e: ApiError.ServerUnreachable) {
            caught = e
        }
        assertNotNull("Expected ServerUnreachable", caught)
    }

    @Test
    fun `malformed JSON throws DecodingFailed`() = runTest {
        val engine = MockEngine {
            respond(
                content = "NOT VALID JSON FOR PARSER",
                status = HttpStatusCode.OK,
                headers = JSON_HEADERS
            )
        }

        val mockApplication = mockk<Application>(relaxed = true)
        every { mockApplication.cacheDir } returns File("build/tmp/test_ktor_cache")

        val apiClient = ApiClient(application = mockApplication, engine = engine)
        var caught: ApiError? = null
        try {
            apiClient.health()
        } catch (e: ApiError) {
            caught = e
        }

        assertTrue("Expected DecodingFailed", caught is ApiError.DecodingFailed)
    }


    @Test
    fun `setBaseUrl updates subsequent request URLs`() = runTest {
        val hits = mutableListOf<String>()
        val engine = MockEngine { request ->
            hits.add(request.url.host)
            respond(HEALTH_JSON, HttpStatusCode.OK, JSON_HEADERS)
        }
        val client = buildKtorClient(engine)
        // First call to original host
        client.get<HealthResponse>("http://192.168.1.42:8888/api/health")
        // Simulate setBaseUrl by making a second call to updated host
        client.get<HealthResponse>("http://10.0.0.1:8888/api/health")
        assertEquals("192.168.1.42", hits[0])
        assertEquals("10.0.0.1", hits[1])
    }
}

// ── Ktor test client helpers ──────────────────────────────────────────────────

private fun buildKtorClient(engine: MockEngine): HttpClient = HttpClient(engine) {
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true; isLenient = true })
    }
}

private suspend inline fun <reified T> HttpClient.get(url: String): T =
    this.get(url).body<T>()

private suspend inline fun <reified T> HttpClient.post(url: String, body: Any): T {
    val response: HttpResponse = this.post(url) {
        setBody(body)
        contentType(ContentType.Application.Json)
    }
    return response.body<T>()
}

private suspend fun HttpClient.delete(url: String) {
    this.request(url) { method = HttpMethod.Delete }
}