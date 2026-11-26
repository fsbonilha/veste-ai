package com.vesteai.data.vton.pixelapi

import com.vesteai.core.result.DataResult
import com.vesteai.data.vton.VtonCredentialProvider
import com.vesteai.data.vton.VtonCredentials
import com.vesteai.data.vton.pixelapi.dto.JobStatusDto
import com.vesteai.data.vton.pixelapi.dto.PixelApiJobStatus
import com.vesteai.domain.vton.AspectRequirement
import com.vesteai.domain.vton.VtonCapabilities
import com.vesteai.domain.vton.VtonEngine
import com.vesteai.domain.vton.VtonError
import com.vesteai.domain.vton.VtonException
import com.vesteai.domain.vton.VtonPollResult
import com.vesteai.domain.vton.VtonProgress
import com.vesteai.domain.vton.VtonRequest
import com.vesteai.domain.vton.VtonResult
import com.vesteai.domain.vton.isRetryable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.IOException
import java.util.Base64
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

internal class PixelApiVtonEngine(
    private val credentialProvider: VtonCredentialProvider,
    private val client: PixelApiClient,
    private val config: PixelApiConfig = PixelApiConfig(),
    private val pause: suspend (Duration) -> Unit = { delay(it) },
) : VtonEngine {

    override val displayName: String = "PixelAPI"

    private val _progress = MutableSharedFlow<VtonProgress>(replay = 1, extraBufferCapacity = 8)
    override val progress: Flow<VtonProgress> = _progress.asSharedFlow()

    override val capabilities: VtonCapabilities = VtonCapabilities(
        supportedCategories = PixelApiCategoryMap.supportedCategories,
        supportsTopBottomOutfit = true,
        maxParallelJobs = MAX_PARALLEL_JOBS,
        requiredAspect = AspectRequirement.Portrait(minWidthPx = 300, minHeightPx = 400),
    )

    /** Reads credentials fresh each call so the user can change them between renders. */
    private suspend fun credentialsOrNull(): Pair<VtonCredentials, HttpUrl>? {
        val c = credentialProvider.credentials() ?: return null
        val url = c.baseUrl.ifBlank { PixelApiConfig.DEFAULT_BASE_URL }.toHttpUrlOrNull() ?: return null
        return c to url
    }

    override suspend fun submit(request: VtonRequest): DataResult<String> {
        val (credentials, baseUrl) = credentialsOrNull() ?: return failure(VtonError.NotConfigured)
        val submission = PixelApiRequestBuilder.build(request)
            ?: return failure(VtonError.UnsupportedCombination)
        return try {
            val jobId = client.submit(baseUrl, credentials, submission)
            DataResult.Success(jobId)
        } catch (e: VtonException) {
            DataResult.Failure(e)
        } catch (e: IOException) {
            failure(PixelApiErrorMapper.fromTransport(e))
        }
    }

    /**
     * Poll one job. Returns null while the job is still queued or processing.
     * Transient transport and provider errors return null so the caller keeps polling
     * rather than abandoning a running job; permanent errors propagate as failures.
     */
    override suspend fun poll(jobId: String): DataResult<VtonPollResult?> {
        val (credentials, baseUrl) = credentialsOrNull()
            ?: return DataResult.Failure(VtonException(VtonError.NotConfigured))
        return try {
            val job = client.fetchJob(baseUrl, credentials, jobId)
            _progress.tryEmit(job.toProgress())
            val result: VtonPollResult? = when (PixelApiJobStatus.fromWire(job.status)) {
                PixelApiJobStatus.COMPLETED -> VtonPollResult.Completed(decodeResult(job))
                PixelApiJobStatus.FAILED -> VtonPollResult.Failed(VtonException(VtonError.JobFailed(job.guidance)))
                PixelApiJobStatus.QUEUED, PixelApiJobStatus.PROCESSING -> null
                null -> VtonPollResult.Failed(VtonException(VtonError.JobFailed(job.guidance)))
            }
            DataResult.Success(result)
        } catch (e: VtonException) {
            if (e.error.isRetryable) DataResult.Success<VtonPollResult?>(null) else DataResult.Failure(e)
        } catch (e: IOException) {
            DataResult.Success<VtonPollResult?>(null)
        }
    }

    override suspend fun render(request: VtonRequest): DataResult<VtonResult> {
        val (credentials, baseUrl) = credentialsOrNull() ?: return failure(VtonError.NotConfigured)
        val submission = PixelApiRequestBuilder.build(request)
            ?: return failure(VtonError.UnsupportedCombination)

        return try {
            val image = withTimeoutOrNull(config.overallTimeout) {
                // Never retried: the provider has already billed for a submit it accepted, and a
                // timeout does not prove it was rejected. Retrying here could charge twice.
                val jobId = client.submit(baseUrl, credentials, submission)
                awaitResult(baseUrl, credentials, jobId)
            } ?: return failure(VtonError.Timeout)
            DataResult.Success(VtonResult(image))
        } catch (e: VtonException) {
            DataResult.Failure(e)
        } catch (e: IOException) {
            failure(PixelApiErrorMapper.fromTransport(e))
        }
    }

    /**
     * Polling loop used by the synchronous [render] path. Unlike [poll], this retries
     * transient failures internally before giving up.
     */
    private suspend fun awaitResult(
        baseUrl: HttpUrl,
        credentials: VtonCredentials,
        jobId: String,
    ): ByteArray {
        var consecutiveFailures = 0
        while (true) {
            pause(config.pollInterval)
            val job = try {
                client.fetchJob(baseUrl, credentials, jobId)
            } catch (e: IOException) {
                if (++consecutiveFailures > config.maxPollRetries) {
                    throw VtonException(PixelApiErrorMapper.fromTransport(e))
                }
                continue
            } catch (e: VtonException) {
                if (!e.error.isRetryable || ++consecutiveFailures > config.maxPollRetries) throw e
                continue
            }
            consecutiveFailures = 0
            _progress.tryEmit(job.toProgress())
            when (PixelApiJobStatus.fromWire(job.status)) {
                PixelApiJobStatus.COMPLETED -> return decodeResult(job)
                PixelApiJobStatus.FAILED -> throw VtonException(VtonError.JobFailed(job.guidance))
                PixelApiJobStatus.QUEUED, PixelApiJobStatus.PROCESSING -> Unit
                null -> throw VtonException(VtonError.JobFailed(job.guidance))
            }
        }
    }

    private fun JobStatusDto.toProgress(): VtonProgress = VtonProgress(
        estimatedWaitSeconds = waitInfo?.estimatedWaitSeconds ?: etaSeconds,
        queuePosition = waitInfo?.queuePosition ?: queuePosition,
        userMessage = waitInfo?.userMessage ?: friendlyMessage,
    )

    private fun decodeResult(job: JobStatusDto): ByteArray {
        val encoded = job.resultImageB64 ?: throw VtonException(VtonError.JobFailed(job.guidance))
        return try {
            Base64.getDecoder().decode(encoded)
        } catch (e: IllegalArgumentException) {
            throw VtonException(VtonError.JobFailed(job.guidance))
        }
    }

    private fun failure(error: VtonError) = DataResult.Failure(VtonException(error))

    private companion object {
        const val MAX_PARALLEL_JOBS = 3
    }
}