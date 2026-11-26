package com.vesteai.domain.vton

import com.vesteai.core.result.DataResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

interface VtonEngine {
    val capabilities: VtonCapabilities
    val displayName: String

    /**
     * Optional queue reporting while the job runs. Empty by default, so an engine that cannot see
     * the queue owes nothing; [render] pushes a value here as polls come back.
     */
    val progress: Flow<VtonProgress> get() = emptyFlow()

    /**
     * Submit the request and return a provider job id. Never retried on failure — the provider
     * may have billed for a submit it accepted, and a timeout does not prove it was rejected.
     */
    suspend fun submit(request: VtonRequest): DataResult<String>

    /**
     * Poll one job. Returns null when the job is still in progress (queued or processing).
     * Terminal states return [VtonPollResult.Completed] or [VtonPollResult.Failed].
     */
    suspend fun poll(jobId: String): DataResult<VtonPollResult?>

    /**
     * Failures are [DataResult.Failure] wrapping a [VtonException]. Must honour caller cancellation.
     * Long-running by design (plan §7): the caller bounds it, and the provider may legitimately take
     * minutes in the queue.
     *
     * Default implementation: calls [submit] then polls at a 1-second interval until terminal.
     * Engines that cannot split submit/poll (e.g. [MockVtonEngine]) override only this.
     */
    suspend fun render(request: VtonRequest): DataResult<VtonResult> {
        val jobIdResult = submit(request)
        val jobId = when (jobIdResult) {
            is DataResult.Success -> jobIdResult.value
            is DataResult.Failure -> return DataResult.Failure(jobIdResult.error)
        }
        while (true) {
            val pollResult = poll(jobId)
            val outcome = when (pollResult) {
                is DataResult.Failure -> return DataResult.Failure(pollResult.error)
                is DataResult.Success -> pollResult.value
            }
            when (outcome) {
                null -> delay(1000L)
                is VtonPollResult.Completed -> return DataResult.Success(VtonResult(outcome.image))
                is VtonPollResult.Failed -> return DataResult.Failure(outcome.error)
            }
        }
    }
}