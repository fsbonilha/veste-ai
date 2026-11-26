package com.vesteai.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for a try-on job that runs outside the UI session. Each row represents
 * one try-on submitted to a provider; the background poller picks up jobs with
 * status "queued" or "processing" and advances them toward a terminal state.
 *
 * [garmentIdsJson] stores a JSON array like ["id1","id2"], parsed by [RoomPendingJobRepository].
 * [errorCode] is a simple kebab-case string (e.g. "job_failed", "timeout") that the repository
 * maps back to a [VtonError] variant on read; [guidance] holds the provider's message.
 */
@Entity(tableName = "pending_job")
data class PendingJobEntity(
    @PrimaryKey val id: String,
    val providerName: String,
    val providerJobId: String? = null,
    val garmentIdsJson: String,
    val baselineImagePath: String,
    val submittedAt: Long,
    val status: String = "queued",
    val resultImagePath: String? = null,
    val errorCode: String? = null,
    val guidance: String? = null,
    val seen: Boolean = false,
)