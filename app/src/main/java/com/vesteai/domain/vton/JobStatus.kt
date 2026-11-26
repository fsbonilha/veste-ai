package com.vesteai.domain.vton

import com.vesteai.core.result.DataResult

/**
 * Persistable status of a try-on job that runs outside the UI session. Terminal states
 * ([Completed], [Failed]) are never updated again; the row is kept until deleted or saved.
 */
sealed interface JobStatus {
    data object Queued : JobStatus
    data object Processing : JobStatus
    data class Completed(val resultImagePath: String) : JobStatus
    data class Failed(val error: VtonError, val guidance: String?) : JobStatus
}

val JobStatus.isTerminal: Boolean
    get() = this is JobStatus.Completed || this is JobStatus.Failed