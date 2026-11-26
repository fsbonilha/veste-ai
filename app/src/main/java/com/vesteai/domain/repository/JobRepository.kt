package com.vesteai.domain.repository

import com.vesteai.core.result.DataResult
import com.vesteai.domain.vton.JobStatus
import com.vesteai.domain.vton.PendingJob
import com.vesteai.domain.vton.VtonError
import kotlinx.coroutines.flow.Flow

interface JobRepository {
    /** Non-terminal jobs, ordered by submittedAt desc. */
    fun observeActive(): Flow<List<PendingJob>>

    /** Terminal jobs not yet marked seen, for the Lookbook tab badge. */
    fun observeUnseen(): Flow<List<PendingJob>>

    /** All jobs, ordered by submittedAt desc. */
    fun observeAll(): Flow<List<PendingJob>>

    suspend fun save(job: PendingJob): DataResult<PendingJob>

    suspend fun updateProviderJobId(id: String, providerJobId: String)

    suspend fun transitionToProcessing(id: String)

    suspend fun complete(id: String, resultImagePath: String)

    suspend fun fail(id: String, error: VtonError, guidance: String?)

    suspend fun markSeen(id: String)

    suspend fun delete(id: String)

    suspend fun getById(id: String): PendingJob?
}