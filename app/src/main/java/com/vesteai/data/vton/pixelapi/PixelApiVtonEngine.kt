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
import com.vesteai.domain.vton.VtonRequest
import com.vesteai.domain.vton.VtonResult
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.delay
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

    private suspend fun awaitResult(
        baseUrl: HttpUrl,
        credentials: VtonCredentials,
        jobId: String,
    ): ByteArray {
        while (true) {
            pause(config.pollInterval)
            val job = client.fetchJob(baseUrl, credentials, jobId)
            when (PixelApiJobStatus.fromWire(job.status)) {
                PixelApiJobStatus.COMPLETED -> return decodeResult(job)
                PixelApiJobStatus.FAILED -> throw VtonException(VtonError.JobFailed(job.guidance))
                PixelApiJobStatus.QUEUED, PixelApiJobStatus.PROCESSING -> Unit
                null -> throw VtonException(VtonError.JobFailed(job.guidance))
            }
        }
    }

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
