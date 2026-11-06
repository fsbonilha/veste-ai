package com.vesteai.data.vton.pixelapi

import okhttp3.OkHttpClient
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

internal data class PixelApiConfig(
    val pollInterval: Duration = 2500.milliseconds,
    val overallTimeout: Duration = 180.seconds,
    val connectTimeout: Duration = 10.seconds,
    val readTimeout: Duration = 30.seconds,
    val writeTimeout: Duration = 30.seconds,
) {
    fun newHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(connectTimeout)
        .readTimeout(readTimeout)
        .writeTimeout(writeTimeout)
        .build()

    companion object {
        const val DEFAULT_BASE_URL = "https://api.pixelapi.dev"

        /** PixelAPI's CDN may block requests without a User-Agent (plan §6.5). */
        const val USER_AGENT = "VesteAI/1.0 (Android)"
    }
}
