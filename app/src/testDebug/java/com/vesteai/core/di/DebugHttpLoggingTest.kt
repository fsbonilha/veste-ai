package com.vesteai.core.di

import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Debug-only wiring (plan §6.4): the log must identify a request and its cause without leaking the key. */
class DebugHttpLoggingTest {

    private lateinit var server: MockWebServer
    private val lines = mutableListOf<String>()
    private val logger = HttpLoggingInterceptor.Logger { lines += it }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun client(): OkHttpClient = OkHttpClient.Builder()
        .apply { debugHttpLogging(logger).interceptors().forEach(::addInterceptor) }
        .build()

    private fun call(
        apiKey: String = "pk_live_secret_value",
        status: Int = 401,
        body: String = """{"detail":"Invalid API key"}""",
    ): String {
        server.enqueue(MockResponse.Builder().code(status).body(body).build())
        val request = Request.Builder()
            .url(server.url("/v1/virtual-tryon"))
            .header("Authorization", "Bearer $apiKey")
            .post("""{"person_image":"BASE64PAYLOAD"}""".toRequestBody())
            .build()
        return client().newCall(request).execute().use { it.body.string() }
    }

    private fun output(): String = lines.joinToString("\n")

    @Test
    fun logsTheUrlAndMethodSoSubmitCanBeToldFromPoll() {
        call()

        assertTrue(output(), output().contains("POST"))
        assertTrue(output(), output().contains("/v1/virtual-tryon"))
    }

    @Test
    fun logsTheResponseStatusSo401IsDistinguishableFromATimeout() {
        call()

        assertTrue(output(), lines.any { it.contains("401") })
    }

    @Test
    fun theAuthorizationHeaderIsRedacted() {
        call(apiKey = "pk_live_secret_value")

        assertTrue(output(), output().contains("Authorization"))
        assertTrue(output(), !output().contains("pk_live_secret_value"))
    }

    @Test
    fun imageRequestBodiesAreNotLogged() {
        call()

        assertTrue(output(), lines.none { it.contains("BASE64PAYLOAD") })
    }

    @Test
    fun theResponseBodyIsLoggedSoA402IsNotJustAStatusCode() {
        call(status = 402, body = """{"detail":{"error":"insufficient_credits","guidance":"Top up at pixelapi.dev/app"}}""")

        assertTrue(output(), output().contains("insufficient_credits"))
        assertTrue(output(), output().contains("Top up at pixelapi.dev/app"))
    }

    @Test
    fun theBodyIsLoggedAfterTheStatusLineSoThePairReadsInOrder() {
        call(status = 402, body = """{"detail":{"error":"insufficient_credits"}}""")

        val statusIndex = lines.indexOfFirst { it.contains("402") }
        val bodyIndex = lines.indexOfFirst { it.contains("insufficient_credits") }
        assertTrue("status line missing", statusIndex >= 0)
        assertTrue("body line missing", bodyIndex >= 0)
        assertTrue("body should follow the status line", bodyIndex > statusIndex)
    }

    @Test
    fun loggingTheBodyDoesNotConsumeItForTheCaller() {
        val body = call(status = 402, body = """{"detail":{"error":"insufficient_credits"}}""")

        assertEquals("""{"detail":{"error":"insufficient_credits"}}""", body)
    }

    @Test
    fun longBase64InTheBodyIsElidedSoPngResultsDoNotFloodTheLog() {
        val png = "A".repeat(3_000)
        call(status = 200, body = """{"status":"completed","result_image_b64":"$png"}""")

        assertTrue(output(), output().contains("result_image_b64"))
        assertTrue(output(), output().contains("<base64 3000 chars>"))
        assertTrue(output(), !lines.any { it.contains("A".repeat(500)) })
    }

    @Test
    fun aBodyLongerThanThePeekIsMarkedTruncatedSoAnElidedRunIsNotMistakenForTheWholePayload() {
        call(status = 200, body = """{"result_image_b64":"${"A".repeat(50_000)}"}""")

        assertTrue(output(), output().contains("truncated at 4096 bytes"))
    }
}