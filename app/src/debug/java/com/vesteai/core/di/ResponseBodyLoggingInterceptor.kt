package com.vesteai.core.di

import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor

/**
 * Logs the response body that [HttpLoggingInterceptor] at `HEADERS` leaves out, without consuming
 * the response the caller needs and without drowning logcat in base64.
 *
 * The provider's error bodies are small (`{"detail":{"error","message","guidance"}}`) and are the
 * only place the real cause appears — a status code alone cannot separate "no plan" from "bad
 * image". Successful poll bodies are mostly a base64 PNG, so long base64 runs are elided and the
 * peek is capped; `peekBody` returns a copy, leaving the original body readable.
 */
internal class ResponseBodyLoggingInterceptor(
    private val logger: HttpLoggingInterceptor.Logger,
    private val maxBytes: Long = MAX_PEEK_BYTES,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        val body = runCatching { response.peekBody(maxBytes).string() }.getOrNull()
        if (!body.isNullOrEmpty()) {
            logger.log("<-- BODY ${elideBase64(body)}${truncationNote(body)}")
        }
        return response
    }

    private fun elideBase64(body: String): String =
        BASE64_RUN.replace(body) { match -> "<base64 ${match.value.length} chars>" }

    /**
     * `peekBody` stops at [maxBytes], so a long body arrives cut off mid-value. Saying so keeps a
     * reader from assuming an elided run is the whole payload or that the JSON is malformed.
     */
    private fun truncationNote(body: String): String =
        if (body.toByteArray().size.toLong() >= maxBytes) " (truncated at $maxBytes bytes)" else ""

    private companion object {
        const val MAX_PEEK_BYTES = 4096L

        /** A base64 payload long enough that it is certainly image data, not a value to read. */
        val BASE64_RUN = Regex("[A-Za-z0-9+/]{200,}={0,2}")
    }
}