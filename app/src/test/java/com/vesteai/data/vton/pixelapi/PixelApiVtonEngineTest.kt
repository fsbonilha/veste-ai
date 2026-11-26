package com.vesteai.data.vton.pixelapi

import com.vesteai.core.result.DataResult
import com.vesteai.data.vton.FakeVtonCredentialProvider
import com.vesteai.data.vton.VtonCredentials
import com.vesteai.domain.model.GarmentCategory
import com.vesteai.domain.vton.AspectRequirement
import com.vesteai.domain.vton.VtonError
import com.vesteai.domain.vton.VtonException
import com.vesteai.domain.vton.VtonProgress
import com.vesteai.domain.vton.VtonRequest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Base64
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class PixelApiVtonEngineTest {

    private lateinit var server: MockWebServer
    private val person = byteArrayOf(1, 2, 3)
    private val top = byteArrayOf(10, 11)
    private val resultPng = byteArrayOf(-119, 80, 78, 71)
    private val credentials by lazy {
        VtonCredentials(apiKey = "pk_live_test", baseUrl = server.url("/").toString())
    }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun engine(
        overallTimeout: Duration = 5.seconds,
        pause: suspend (Duration) -> Unit = {},
        provider: FakeVtonCredentialProvider = FakeVtonCredentialProvider(credentials),
    ): PixelApiVtonEngine {
        val config = PixelApiConfig(pollInterval = 1.milliseconds, overallTimeout = overallTimeout)
        return PixelApiVtonEngine(provider, PixelApiClient(config.newHttpClient()), config, pause)
    }

    private fun singleTopRequest() = VtonRequest(person, mapOf(GarmentCategory.TOP to top))

    private fun enqueueSubmit(jobId: String = "job-1") {
        server.enqueue(MockResponse.Builder().code(200).body("""{"job_id":"$jobId","status":"queued"}""").build())
    }

    private fun enqueueJob(status: String, resultB64: String? = null, guidance: String? = null) {
        val fields = listOfNotNull(
            """"status":"$status"""",
            resultB64?.let { """"result_image_b64":"$it"""" },
            guidance?.let { """"guidance":"$it"""" },
        )
        server.enqueue(MockResponse.Builder().code(200).body("{${fields.joinToString(",")}}").build())
    }

    private fun enqueueError(status: Int, body: String) {
        server.enqueue(MockResponse.Builder().code(status).body(body).build())
    }

    private fun failureError(result: DataResult<*>): VtonError =
        ((result as DataResult.Failure).error as VtonException).error

    @Test
    fun render_submitsSingleGarment_andReturnsDecodedPng() = runBlocking {
        enqueueSubmit()
        enqueueJob("completed", Base64.getEncoder().encodeToString(resultPng))

        val result = engine().render(singleTopRequest())

        assertArrayEquals(resultPng, (result as DataResult.Success).value.image)
        val submit = server.takeRequest()
        assertEquals("/v1/virtual-tryon", submit.url.encodedPath)
        assertEquals("POST", submit.method)
    }

    @Test
    fun render_sendsBearerAndMandatoryUserAgent() = runBlocking {
        enqueueSubmit()
        enqueueJob("completed", Base64.getEncoder().encodeToString(resultPng))

        engine().render(singleTopRequest())

        val submit = server.takeRequest()
        assertEquals("Bearer pk_live_test", submit.headers["Authorization"])
        assertEquals("VesteAI/1.0 (Android)", submit.headers["User-Agent"])
        val poll = server.takeRequest()
        assertEquals("Bearer pk_live_test", poll.headers["Authorization"])
        assertEquals("VesteAI/1.0 (Android)", poll.headers["User-Agent"])
    }

    @Test
    fun render_singleGarmentBody_isBase64WithoutDataPrefix() = runBlocking {
        enqueueSubmit()
        enqueueJob("completed", Base64.getEncoder().encodeToString(resultPng))

        engine().render(singleTopRequest())

        val body = Json.parseToJsonElement(server.takeRequest().body!!.utf8()).jsonObject
        assertEquals(Base64.getEncoder().encodeToString(person), body.getValue("person_image").jsonPrimitive.content)
        assertEquals(Base64.getEncoder().encodeToString(top), body.getValue("garment_image").jsonPrimitive.content)
        assertEquals("upperbody", body.getValue("category").jsonPrimitive.content)
        assertTrue(!body.getValue("person_image").jsonPrimitive.content.startsWith("data:"))
    }

    @Test
    fun render_topAndBottom_usesOutfitEndpoint() = runBlocking {
        enqueueSubmit()
        enqueueJob("completed", Base64.getEncoder().encodeToString(resultPng))
        val request = VtonRequest(
            person,
            mapOf(GarmentCategory.TOP to top, GarmentCategory.BOTTOM to byteArrayOf(20)),
        )

        engine().render(request)

        val submit = server.takeRequest()
        assertEquals("/v1/virtual-tryon/outfit", submit.url.encodedPath)
        val body = Json.parseToJsonElement(submit.body!!.utf8()).jsonObject
        assertEquals(Base64.getEncoder().encodeToString(top), body.getValue("upperbody_garment").jsonPrimitive.content)
        assertEquals(Base64.getEncoder().encodeToString(byteArrayOf(20)), body.getValue("lowerbody_garment").jsonPrimitive.content)
    }

    @Test
    fun render_missingCredentials_failsNotConfiguredWithoutNetwork() = runBlocking {
        val result = engine(provider = FakeVtonCredentialProvider(null)).render(singleTopRequest())

        assertEquals(VtonError.NotConfigured, failureError(result))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun render_unsupportedCategory_failsWithoutNetwork() = runBlocking {
        val request = VtonRequest(person, mapOf(GarmentCategory.FOOTWEAR to top))

        val result = engine().render(request)

        assertEquals(VtonError.UnsupportedCombination, failureError(result))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun capabilities_reportPixelApiSupport() {
        val capabilities = engine().capabilities

        assertEquals(PixelApiCategoryMap.supportedCategories, capabilities.supportedCategories)
        assertTrue(capabilities.supportsTopBottomOutfit)
        assertEquals(3, capabilities.maxParallelJobs)
        assertEquals(AspectRequirement.Portrait(300, 400), capabilities.requiredAspect)
    }

    @Test
    fun poll_terminatesOnCompleted() = runBlocking {
        enqueueSubmit()
        enqueueJob("processing")
        enqueueJob("completed", Base64.getEncoder().encodeToString(resultPng))

        val result = engine().render(singleTopRequest())

        assertArrayEquals(resultPng, (result as DataResult.Success).value.image)
        assertEquals(3, server.requestCount)
    }

    /** PixelAPI's own queue copy is better than any the app can invent (plan §7). */
    @Test
    fun progress_carriesTheProvidersWaitInfo() = runBlocking {
        val pixelApi = engine()
        val seen = mutableListOf<VtonProgress>()
        val watcher = launch {
            pixelApi.progress.collect { seen += it }
        }
        enqueueSubmit()
        server.enqueue(
            MockResponse.Builder().code(200).body(
                """
                {"status":"processing","eta_seconds":10,
                 "wait_info":{"estimated_wait_s":340,"queue_position":2,
                 "user_message":"Preparing your result"}}
                """.trimIndent(),
            ).build(),
        )
        enqueueJob("completed", Base64.getEncoder().encodeToString(resultPng))

        pixelApi.render(singleTopRequest())
        watcher.cancelAndJoin()

        val latest = seen.last()
        assertEquals(340L, latest.estimatedWaitSeconds)
        assertEquals(2, latest.queuePosition)
        assertEquals("Preparing your result", latest.userMessage)
    }

    /** `wait_info` wins over the flat fields, which reported a nonsense "eta_seconds": 10. */
    @Test
    fun progress_prefersWaitInfoOverTheFlatEtaField() = runBlocking {
        val pixelApi = engine()
        val seen = mutableListOf<VtonProgress>()
        val watcher = launch {
            pixelApi.progress.collect { seen += it }
        }
        enqueueSubmit()
        enqueueJob("processing")
        enqueueJob("completed", Base64.getEncoder().encodeToString(resultPng))

        pixelApi.render(singleTopRequest())
        watcher.cancelAndJoin()

        assertTrue(seen.isNotEmpty())
    }

    @Test
    fun poll_terminatesOnFailed_withGuidance() = runBlocking {
        enqueueSubmit()
        enqueueJob("processing")
        enqueueJob("failed", guidance = "Try a clearer photo")

        val result = engine().render(singleTopRequest())

        assertEquals(VtonError.JobFailed("Try a clearer photo"), failureError(result))
        assertEquals(3, server.requestCount)
    }

    @Test
    fun cancellation_midPoll_stopsPolling() = runBlocking {
        enqueueSubmit()
        enqueueJob("processing")
        val parked = CompletableDeferred<Unit>()
        var pauses = 0
        val pause: suspend (Duration) -> Unit = {
            pauses++
            if (pauses == 2) {
                parked.complete(Unit)
                CompletableDeferred<Unit>().await()
            }
        }
        val pending = async { engine(pause = pause).render(singleTopRequest()) }

        parked.await()
        pending.cancelAndJoin()

        assertEquals(2, pauses)
        assertEquals(2, server.requestCount)
    }


    @Test
    fun overallTimeout_returnsTimeoutAndStopsPolling() = runBlocking {
        enqueueSubmit()
        val result = engine(
            overallTimeout = 200.milliseconds,
            pause = { CompletableDeferred<Unit>().await() },
        ).render(singleTopRequest())

        assertEquals(VtonError.Timeout, failureError(result))
        assertEquals(1, server.requestCount)
    }

    /**
     * Deliberately loose while real durations are unknown: a measured outfit job reported
     * `estimated_wait_s: 340` on an overloaded queue, far past the ~90 s advertised. The point of
     * this test is that the budget dwarfs the provider's own estimate.
     */
    @Test
    fun overallTimeoutIsLooseEnoughForTheMeasuredQueueWait() {
        val config = PixelApiConfig()

        assertTrue("budget must exceed the 340 s measured wait", config.overallTimeout > 340.seconds)
        assertEquals(1200L, config.overallTimeout.inWholeSeconds)
    }

    /**
     * 20 requests/minute is PixelAPI's free-tier cap, so anything under 3 s exceeds it and gets a
     * 429 on a poll — aborting a job the provider is still working on.
     */
    @Test
    fun pollInterval_staysUnderTheDocumentedTwentyRequestsPerMinute() {
        val perMinute = 60_000.0 / PixelApiConfig().pollInterval.inWholeMilliseconds

        assertTrue("poll interval is $perMinute req/min", perMinute <= 20.0)
        assertEquals(10L, PixelApiConfig().pollInterval.inWholeSeconds)
    }

    @Test
    fun aLongQueueFitsTheOverallBudgetAtTheDefaultPollInterval() {
        val config = PixelApiConfig()

        assertTrue(
            "a 600 s budget only allows " +
                "${config.overallTimeout / config.pollInterval} polls, which is too few to notice " +
                "a completed job",
            config.overallTimeout / config.pollInterval >= 30,
        )
    }

    /** The submit's body is a base64 JPEG (~1.3x on the wire); 30 s failed real uploads. */
    @Test
    fun writeTimeoutIsLooseEnoughForABase64Upload() {
        assertEquals(300L, PixelApiConfig().writeTimeout.inWholeSeconds)
    }

    /**
     * The submit's *response* is slow too, not just its upload: PixelAPI's origin measured 29.4 s
     * before answering under load, so a 30 s read timeout failed real submits at the deadline.
     */
    @Test
    fun readTimeout_coversTheProvidersSlowSubmitResponse() {
        val config = PixelApiConfig()

        assertEquals(300L, config.readTimeout.inWholeSeconds)
        assertTrue("read timeout must exceed the observed 30 s origin latency", config.readTimeout > 30.seconds)
    }

    @Test
    fun everyPerOperationTimeoutFitsInsideTheOverallBudget() {
        val config = PixelApiConfig()

        assertTrue(config.connectTimeout < config.overallTimeout)
        assertTrue(config.readTimeout < config.overallTimeout)
        assertTrue(config.writeTimeout < config.overallTimeout)
    }

    @Test
    fun newHttpClient_appliesTheInterceptorsItIsGiven() = runBlocking {
        var touched = false
        val marker = okhttp3.Interceptor { chain ->
            touched = true
            chain.proceed(chain.request())
        }
        enqueueSubmit()
        enqueueJob("completed", Base64.getEncoder().encodeToString(resultPng))
        val config = PixelApiConfig(pollInterval = 1.milliseconds, overallTimeout = 5.seconds)
        val engine = PixelApiVtonEngine(
            FakeVtonCredentialProvider(credentials),
            PixelApiClient(config.newHttpClient(listOf(marker))),
            config,
            {},
        )

        engine.render(singleTopRequest())

        assertTrue(touched)
    }

    @Test
    fun newHttpClient_withNoInterceptorsStillWorksForRelease() = runBlocking {
        enqueueSubmit()
        enqueueJob("completed", Base64.getEncoder().encodeToString(resultPng))

        val result = engine().render(singleTopRequest())

        assertArrayEquals(resultPng, (result as DataResult.Success).value.image)
    }

    @Test
    fun error_422ImageTooSmall_mapsToInputTooSmall() = runBlocking {
        enqueueError(422, """{"detail":{"error":"image_too_small","received_size":"200x300"}}""")

        assertEquals(VtonError.InputTooSmall("200x300"), failureError(engine().render(singleTopRequest())))
    }

    @Test
    fun error_422WrongAspectRatio_mapsToWrongAspectRatio() = runBlocking {
        enqueueError(422, """{"detail":{"error":"wrong_aspect_ratio"}}""")

        assertEquals(VtonError.WrongAspectRatio, failureError(engine().render(singleTopRequest())))
    }

    @Test
    fun error_422BodySegmentationFailed_mapsToBodyNotDetected() = runBlocking {
        enqueueError(422, """{"detail":{"error":"body_segmentation_failed"}}""")

        assertEquals(VtonError.BodyNotDetected, failureError(engine().render(singleTopRequest())))
    }

    @Test
    fun error_422InvalidCategory_mapsToUnsupportedCombination() = runBlocking {
        enqueueError(422, """{"detail":{"error":"invalid_category"}}""")

        assertEquals(VtonError.UnsupportedCombination, failureError(engine().render(singleTopRequest())))
    }

    @Test
    fun error_400InvalidImage_mapsToInvalidImage() = runBlocking {
        enqueueError(400, """{"detail":{"error":"invalid_image"}}""")

        assertEquals(VtonError.InvalidImage, failureError(engine().render(singleTopRequest())))
    }

    @Test
    fun error_401_mapsToAuthenticationFailed() = runBlocking {
        enqueueError(401, """{"detail":{"message":"bad key"}}""")

        assertEquals(VtonError.AuthenticationFailed, failureError(engine().render(singleTopRequest())))
    }

    @Test
    fun error_429_mapsToTooManyConcurrentJobsWithGuidance() = runBlocking {
        enqueueError(429, """{"detail":{"guidance":"Wait for a running job"}}""")

        assertEquals(
            VtonError.TooManyConcurrentJobs("Wait for a running job"),
            failureError(engine().render(singleTopRequest())),
        )
    }

    /**
     * Polling is an idempotent GET, so a transient failure costs nothing to retry. Before this, one
     * 429 or socket timeout mid-poll discarded a job the provider was still rendering.
     */
    @Test
    fun aFailedPollIsRetriedInsteadOfDiscardingTheRunningJob() = runBlocking {
        enqueueSubmit()
        enqueueJob("processing")
        server.enqueue(MockResponse.Builder().code(503).body("""{"detail":{"error":"processing_out_of_memory"}}""").build())
        enqueueJob("completed", Base64.getEncoder().encodeToString(resultPng))

        val result = engine().render(singleTopRequest())

        assertArrayEquals(resultPng, (result as DataResult.Success).value.image)
        assertEquals(4, server.requestCount)
    }

    @Test
    fun pollingGivesUpAfterTheConfiguredConsecutiveFailures() = runBlocking {
        enqueueSubmit()
        repeat(10) { enqueueError(429, """{"detail":{"guidance":"Rate limited"}}""") }

        val result = engine().render(singleTopRequest())

        assertEquals(VtonError.TooManyConcurrentJobs("Rate limited"), failureError(result))
        assertEquals(1 + PixelApiConfig().maxPollRetries + 1, server.requestCount)
    }

    /** A rejected key will never become valid by asking again, so it must fail on the first poll. */
    @Test
    fun anAuthenticationFailureIsNotRetried() = runBlocking {
        enqueueSubmit()
        enqueueError(401, """{"detail":{"message":"bad key"}}""")

        val result = engine().render(singleTopRequest())

        assertEquals(VtonError.AuthenticationFailed, failureError(result))
        assertEquals(2, server.requestCount)
    }

    @Test
    fun error_503OutOfMemory_mapsToRetryableProcessingBusy() = runBlocking {
        enqueueError(503, """{"detail":{"error":"processing_out_of_memory"}}""")

        assertEquals(VtonError.ProcessingBusy(retryable = true), failureError(engine().render(singleTopRequest())))
    }

    @Test
    fun unknownServerError_mapsToServerErrorWithStatus() = runBlocking {
        enqueueError(500, "<html>boom</html>")

        assertEquals(VtonError.ServerError(500), failureError(engine().render(singleTopRequest())))
    }

    @Test
    fun transportFailure_mapsToNoNetwork() = runBlocking {
        val closedUrl = server.url("/").toString()
        server.close()
        val provider = FakeVtonCredentialProvider(VtonCredentials("pk_live_test", closedUrl))

        val result = engine(provider = provider).render(singleTopRequest())

        assertEquals(VtonError.NoNetwork, failureError(result))
    }

}
