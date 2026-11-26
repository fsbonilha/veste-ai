package com.vesteai.domain.vton

/**
 * A try-on job that may outlive the UI session that created it. Stored in the database so
 * the background poller can pick it up after a crash, and shown in the Lookbook as an
 * in-progress card.
 *
 * [garmentIds] and [baselineImagePath] are references to wardrobe/baseline images, not
 * inline bytes, so the job description is light and survives restarts. The actual image
 * data is read from [ImageStore] when the background poller submits.
 */
data class PendingJob(
    val id: String,
    val providerName: String,
    val providerJobId: String? = null,
    val garmentIds: List<String>,
    val baselineImagePath: String,
    val submittedAt: Long,
    val status: JobStatus = JobStatus.Queued,
    val seen: Boolean = false,
) {
    val isActive: Boolean get() = !status.isTerminal
}