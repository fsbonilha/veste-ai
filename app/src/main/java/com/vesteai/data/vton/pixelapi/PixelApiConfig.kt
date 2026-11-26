package com.vesteai.data.vton.pixelapi

import okhttp3.OkHttpClient
import okhttp3.Interceptor
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

internal data class PixelApiConfig(
    /**
     * Their free tier allows 20 requests/minute and the submit counts as one, so 2.5 s polling
     * (24/min) rate-limited a job the provider was still processing. 10 s is 6/min, comfortably
     * inside the cap while still reacting quickly to a `queued` -> `processing` -> `completed`
     * transition on a job that takes minutes.
     */
    val pollInterval: Duration = 10.seconds,
    /**
     * The whole submit-and-poll cycle. Deliberately loose (plan §10): PixelAPI reports
     * `estimated_wait_s: 340` and `current_load: "overloaded"` on a real outfit job, which is well
     * past the ~90 s it advertises. Tighten only against measured durations, not guesses.
     */
    val overallTimeout: Duration = 1200.seconds,
    val connectTimeout: Duration = 10.seconds,
    /**
     * Applies to the submit as well as the polls, and PixelAPI's origin takes ~30 s to answer a
     * submit under load (`server-timing: cfOrigin;dur=29426`), so 30 s failed every submit right at
     * the deadline. Loosened alongside [writeTimeout] while real durations are unknown.
     */
    val readTimeout: Duration = 300.seconds,
    /**
     * The only write is the submit: a base64 body of a 1024 px JPEG, which is ~1.3x the image on
     * the wire. Loosened alongside [readTimeout], which governs the response that follows it.
     */
    val writeTimeout: Duration = 300.seconds,
    /**
     * Consecutive failed *polls* tolerated before the render gives up. Polling is idempotent, so a
     * transient socket timeout costs nothing to retry; the submit is deliberately not covered.
     */
    val maxPollRetries: Int = 6,
) {
    /** [interceptors] are variant-specific; release passes none (plan §6.4). */
    fun newHttpClient(interceptors: List<Interceptor> = emptyList()): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(connectTimeout)
            .readTimeout(readTimeout)
            .writeTimeout(writeTimeout)
            .apply { interceptors.forEach(::addInterceptor) }
            .build()

    companion object {
        const val DEFAULT_BASE_URL = "https://api.pixelapi.dev"

        /** PixelAPI's CDN may block requests without a User-Agent (plan §6.5). */
        const val USER_AGENT = "VesteAI/1.0 (Android)"
    }
}
