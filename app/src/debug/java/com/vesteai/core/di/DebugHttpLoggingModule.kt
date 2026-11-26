package com.vesteai.core.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.logging.HttpLoggingInterceptor
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DebugHttpLoggingModule {

    @Provides
    @Singleton
    fun provideHttpLogging(): HttpLogging = debugHttpLogging(HttpLoggingInterceptor.Logger.DEFAULT)
}

/**
 * `HEADERS` rather than `BODY`: the bodies are base64 images that would flood logcat, while the
 * headers carry everything needed to tell a timeout from a rejected key — method, URL, status,
 * elapsed time and `Content-Length`. `Authorization` is redacted because `HEADERS` prints every
 * header verbatim. Read the output with `adb logcat -s OkHttp`.
 *
 * [logger] is a parameter so a test can capture the lines instead of asserting on logcat.
 */
internal fun debugHttpLogging(logger: HttpLoggingInterceptor.Logger): HttpLogging {
    val interceptor = HttpLoggingInterceptor(logger).apply {
        level = HttpLoggingInterceptor.Level.HEADERS
        redactHeader("Authorization")
    }
    // Body logging is a separate pass so the two can differ: HEADERS gives the status, timing and
    // headers, while only the body interceptor prints payloads (eliding base64). Listed first so it
    // is the outermost interceptor: the headers block prints, then the body follows it.
    return HttpLogging { listOf(ResponseBodyLoggingInterceptor(logger), interceptor) }
}