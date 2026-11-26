package com.vesteai.domain.vton

import com.vesteai.core.result.DataResult
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
     * Failures are [DataResult.Failure] wrapping a [VtonException]. Must honour caller cancellation.
     * Long-running by design (plan §7): the caller bounds it, and the provider may legitimately take
     * minutes in the queue.
     */
    suspend fun render(request: VtonRequest): DataResult<VtonResult>
}