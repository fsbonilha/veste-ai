package com.vesteai.data.repository

import com.vesteai.core.di.IoDispatcher
import com.vesteai.core.result.DataResult
import com.vesteai.data.db.PendingJobDao
import com.vesteai.data.db.PendingJobEntity
import com.vesteai.domain.vton.JobStatus
import com.vesteai.domain.vton.PendingJob
import com.vesteai.domain.vton.VtonError
import com.vesteai.domain.repository.JobRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject

class RoomPendingJobRepository @Inject constructor(
    private val pendingJobDao: PendingJobDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : JobRepository {

    override fun observeActive(): Flow<List<PendingJob>> =
        pendingJobDao.observeActive().map { rows -> rows.map { it.toDomain() } }

    override fun observeUnseen(): Flow<List<PendingJob>> =
        pendingJobDao.observeUnseen().map { rows -> rows.map { it.toDomain() } }

    override fun observeAll(): Flow<List<PendingJob>> =
        pendingJobDao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override suspend fun save(job: PendingJob): DataResult<PendingJob> = withContext(ioDispatcher) {
        val entity = job.toEntity()
        pendingJobDao.insert(entity)
        val saved = pendingJobDao.getById(job.id)
            ?: return@withContext DataResult.Failure(IllegalStateException("Job ${job.id} missing after insert"))
        DataResult.Success(saved.toDomain())
    }

    override suspend fun getById(id: String): PendingJob? =
        withContext(ioDispatcher) { pendingJobDao.getById(id)?.toDomain() }

    override suspend fun updateProviderJobId(id: String, providerJobId: String) {
        withContext(ioDispatcher) { pendingJobDao.updateProviderJobId(id, providerJobId) }
    }

    override suspend fun transitionToProcessing(id: String) {
        withContext(ioDispatcher) { pendingJobDao.transitionToProcessing(id) }
    }

    override suspend fun complete(id: String, resultImagePath: String) {
        withContext(ioDispatcher) { pendingJobDao.complete(id, resultImagePath) }
    }

    override suspend fun fail(id: String, error: VtonError, guidance: String?) {
        withContext(ioDispatcher) { pendingJobDao.fail(id, errorCodeFor(error), guidance) }
    }

    override suspend fun markSeen(id: String) {
        withContext(ioDispatcher) { pendingJobDao.markSeen(id) }
    }

    override suspend fun delete(id: String) {
        withContext(ioDispatcher) { pendingJobDao.delete(id) }
    }
}

// ── Entity ↔ Domain mappers ──────────────────────────────────────────────────

internal fun PendingJobEntity.toDomain(): PendingJob = PendingJob(
    id = id,
    providerName = providerName,
    providerJobId = providerJobId,
    garmentIds = parseGarmentIds(garmentIdsJson),
    baselineImagePath = baselineImagePath,
    submittedAt = submittedAt,
    status = parseStatus(status, resultImagePath, errorCode, guidance),
    seen = seen,
)

internal fun PendingJob.toEntity(): PendingJobEntity = PendingJobEntity(
    id = id,
    providerName = providerName,
    providerJobId = providerJobId,
    garmentIdsJson = formatGarmentIds(garmentIds),
    baselineImagePath = baselineImagePath,
    submittedAt = submittedAt,
    status = formatStatus(status),
    resultImagePath = if (status is JobStatus.Completed) (status as JobStatus.Completed).resultImagePath else null,
    errorCode = if (status is JobStatus.Failed) errorCodeFor((status as JobStatus.Failed).error) else null,
    guidance = if (status is JobStatus.Failed) (status as JobStatus.Failed).guidance else null,
    seen = seen,
)

// ── Status string ↔ JobStatus ────────────────────────────────────────────────

private fun parseStatus(
    s: String,
    resultImagePath: String?,
    errorCode: String?,
    guidance: String?,
): JobStatus = when (s) {
    "queued" -> JobStatus.Queued
    "processing" -> JobStatus.Processing
    "completed" -> JobStatus.Completed(resultImagePath ?: "")
    "failed" -> JobStatus.Failed(errorFromCode(errorCode), guidance)
    else -> JobStatus.Queued
}

private fun formatStatus(status: JobStatus): String = when (status) {
    is JobStatus.Queued -> "queued"
    is JobStatus.Processing -> "processing"
    is JobStatus.Completed -> "completed"
    is JobStatus.Failed -> "failed"
}

// ── Error code ↔ VtonError ──────────────────────────────────────────────────

private val ERROR_CODE_MAP: Map<String, VtonError> = mapOf(
    "not_configured" to VtonError.NotConfigured,
    "authentication_failed" to VtonError.AuthenticationFailed,
    "unsupported_combination" to VtonError.UnsupportedCombination,
    "no_network" to VtonError.NoNetwork,
    "timeout" to VtonError.Timeout,
    "too_many_concurrent_jobs" to VtonError.TooManyConcurrentJobs(""),
    "processing_busy" to VtonError.ProcessingBusy(retryable = false),
    "input_too_small" to VtonError.InputTooSmall(null),
    "wrong_aspect_ratio" to VtonError.WrongAspectRatio,
    "body_not_detected" to VtonError.BodyNotDetected,
    "invalid_image" to VtonError.InvalidImage,
    "job_failed" to VtonError.JobFailed(null),
    "server_error" to VtonError.ServerError(500),
)

private fun errorCodeFor(error: VtonError): String = when (error) {
    VtonError.NotConfigured -> "not_configured"
    VtonError.AuthenticationFailed -> "authentication_failed"
    VtonError.UnsupportedCombination -> "unsupported_combination"
    VtonError.NoNetwork -> "no_network"
    VtonError.Timeout -> "timeout"
    is VtonError.TooManyConcurrentJobs -> "too_many_concurrent_jobs"
    is VtonError.ProcessingBusy -> "processing_busy"
    is VtonError.InputTooSmall -> "input_too_small"
    VtonError.WrongAspectRatio -> "wrong_aspect_ratio"
    VtonError.BodyNotDetected -> "body_not_detected"
    VtonError.InvalidImage -> "invalid_image"
    is VtonError.JobFailed -> "job_failed"
    is VtonError.ServerError -> "server_error"
}

private fun errorFromCode(code: String?): VtonError =
    ERROR_CODE_MAP[code ?: "job_failed"] ?: VtonError.JobFailed(null)

// ── JSON garment id list (simple, no external dependency) ────────────────────

private fun parseGarmentIds(json: String): List<String> {
    if (json.isBlank() || json == "[]") return emptyList()
    return json.removePrefix("[").removeSuffix("]")
        .split(",")
        .map { it.trim().removeSurrounding("\"") }
        .filter { it.isNotBlank() }
}

private fun formatGarmentIds(ids: List<String>): String =
    ids.map { """"$it"""" }.joinToString(",", "[", "]")