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

    override suspend fun render(request: VtonRequest): DataResult<VtonResult> {
        val credentials = credentialProvider.credentials()
            ?: return failure(VtonError.NotConfigured)
        val baseUrl = credentials.baseUrl.ifBlank { PixelApiConfig.DEFAULT_BASE_URL }.toHttpUrlOrNull()
            ?: return failure(VtonError.NotConfigured)
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
     * Polling is idempotent (a GET per job id), so a transport failure here is retried rather than
     * thrown away: a single socket timeout used to lose a job the provider was still rendering. The
     * retries stay inside [PixelApiConfig.overallTimeout] because this whole loop is bounded by it.
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
                // A transport failure carries no HTTP status, so it is always worth another try.
                if (++consecutiveFailures > config.maxPollRetries) {
                    throw VtonException(PixelApiErrorMapper.fromTransport(e))
                }
                continue
            } catch (e: VtonException) {
                // Provider-side transients (429 rate limit, 503 out of memory) say "come back later",
                // not "this job failed"; discarding the job here loses a render that was in progress.
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