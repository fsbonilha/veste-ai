package com.vesteai.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingJobDao {
    @Query("SELECT * FROM pending_job ORDER BY submittedAt DESC")
    fun observeAll(): Flow<List<PendingJobEntity>>

    @Query("SELECT * FROM pending_job WHERE status NOT IN ('completed', 'failed') ORDER BY submittedAt DESC")
    fun observeActive(): Flow<List<PendingJobEntity>>

    @Query("SELECT * FROM pending_job WHERE id = :id")
    suspend fun getById(id: String): PendingJobEntity?

    @Insert
    suspend fun insert(job: PendingJobEntity)

    @Query("UPDATE pending_job SET providerJobId = :providerJobId WHERE id = :id")
    suspend fun updateProviderJobId(id: String, providerJobId: String): Int

    @Query("UPDATE pending_job SET status = 'processing' WHERE id = :id")
    suspend fun transitionToProcessing(id: String): Int

    @Query("UPDATE pending_job SET status = 'completed', resultImagePath = :resultImagePath WHERE id = :id")
    suspend fun complete(id: String, resultImagePath: String): Int

    @Query("UPDATE pending_job SET status = 'failed', errorCode = :errorCode, guidance = :guidance WHERE id = :id")
    suspend fun fail(id: String, errorCode: String, guidance: String?): Int

    @Query("DELETE FROM pending_job WHERE id = :id")
    suspend fun delete(id: String): Int
}
