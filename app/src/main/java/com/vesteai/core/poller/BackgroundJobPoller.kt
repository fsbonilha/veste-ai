package com.vesteai.core.poller

import com.vesteai.core.di.IoDispatcher
import com.vesteai.core.media.ImageStore
import com.vesteai.core.media.MediaKind
import com.vesteai.core.result.DataResult
import com.vesteai.domain.repository.JobRepository
import com.vesteai.domain.repository.WardrobeRepository
import com.vesteai.domain.vton.JobStatus
import com.vesteai.domain.vton.PendingJob
import com.vesteai.domain.vton.VtonEngine
import com.vesteai.domain.vton.VtonError
import com.vesteai.domain.vton.VtonException
import com.vesteai.domain.vton.VtonPollResult
import com.vesteai.domain.vton.VtonRequest
import com.vesteai.domain.vton.isRetryable
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import java.io.IOException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * App-scoped service that watches for new pending try-on jobs, submits them to the provider,
 * polls them to completion, and persists the result or failure. Started once in [VesteApplication]
 * and lives for the process lifetime.
 */
class BackgroundJobPoller @Inject constructor(
    private val engine: VtonEngine,
    private val jobRepo: JobRepository,
    private val wardrobe: WardrobeRepository,
    private val imageStore: ImageStore,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    private val pollInterval: Duration = 30.seconds
    private val activeJobIds = mutableSetOf<String>()

    /** Start watching for new pending jobs. Call once from an app-scoped scope. */
    fun start(scope: CoroutineScope) {
        scope.launch {
            jobRepo.observeActive().collect { activeJobs ->
                activeJobs.forEach { job ->
                    if (job.id !in activeJobIds && !job.status.isTerminal) {
                        activeJobIds.add(job.id)
                        scope.launch { processJob(job) }
                    }
                }
            }
        }
    }

    private suspend fun processJob(job: PendingJob) {
        try {
            if (job.providerJobId == null) {
                if (!submitJob(job)) {
                    activeJobIds.remove(job.id)
                    return
                }
            }
            val updated = jobRepo.getById(job.id) ?: return
            if (updated.providerJobId == null || updated.status.isTerminal) {
                activeJobIds.remove(job.id)
                return
            }
            pollUntilTerminal(updated)
        } finally {
            activeJobIds.remove(job.id)
        }
    }

    /** Returns true if the submit succeeded, false if it failed terminally. */
    private suspend fun submitJob(job: PendingJob): Boolean {
        val request = buildRequest(job) ?: return false
        val result = engine.submit(request)
        return when (result) {
            is DataResult.Success -> {
                jobRepo.updateProviderJobId(job.id, result.value)
                jobRepo.transitionToProcessing(job.id)
                true
            }
            is DataResult.Failure -> {
                val error = (result.error as? VtonException)?.error ?: VtonError.JobFailed(null)
                jobRepo.fail(job.id, error, null)
                false
            }
        }
    }

    private suspend fun pollUntilTerminal(job: PendingJob) {
        val jobId = job.providerJobId ?: return
        var consecutiveFailures = 0

        while (true) {
            currentCoroutineContext().ensureActive()
            delay(pollInterval)

            val pollResult = engine.poll(jobId)
            when (pollResult) {
                is DataResult.Failure -> {
                    val error = (pollResult.error as? VtonException)?.error ?: VtonError.NoNetwork
                    if (error.isRetryable && ++consecutiveFailures <= MAX_CONSECUTIVE_FAILURES) continue
                    jobRepo.fail(job.id, error, null)
                    return
                }
                is DataResult.Success -> {
                    consecutiveFailures = 0
                    val outcome = pollResult.value
                    when (outcome) {
                        null -> { /* still running */ }
                        is VtonPollResult.Completed -> {
                            val path = persistResult(job.id, outcome.image)
                            if (path != null) jobRepo.complete(job.id, path)
                            else jobRepo.fail(job.id, VtonError.JobFailed(null), "Could not save result image")
                            return
                        }
                        is VtonPollResult.Failed -> {
                            jobRepo.fail(job.id, outcome.error.error, null)
                            return
                        }
                    }
                }
            }
        }
    }

    private suspend fun buildRequest(job: PendingJob): VtonRequest? {
        val baselineBytes = try {
            imageStore.resolve(job.baselineImagePath).readBytes()
        } catch (e: IOException) { return null }

        val garmentMeta = wardrobe.getByIds(job.garmentIds)
        if (garmentMeta.size != job.garmentIds.size) return null

        val garments = mutableMapOf<com.vesteai.domain.model.GarmentCategory, ByteArray>()
        for (garment in garmentMeta) {
            val bytes = try { imageStore.resolve(garment.imagePath).readBytes() }
                catch (e: IOException) { return null }
            garments[garment.category] = bytes
        }

        return VtonRequest(baselineImage = baselineBytes, garments = garments)
    }

    private suspend fun persistResult(jobId: String, image: ByteArray): String? {
        val relativePath = imageStore.newRelativePath(MediaKind.LOOK)
        val target = imageStore.resolve(relativePath)
        return try {
            target.parentFile?.mkdirs()
            target.outputStream().use { image.inputStream().copyTo(it) }
            relativePath
        } catch (e: IOException) { null }
    }

    private companion object {
        const val MAX_CONSECUTIVE_FAILURES = 6
    }
}