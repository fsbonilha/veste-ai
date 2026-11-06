package com.vesteai.domain.vton

import com.vesteai.core.result.DataResult

interface VtonEngine {
    val capabilities: VtonCapabilities
    val displayName: String

    /** Failures are [DataResult.Failure] wrapping a [VtonException]. Must honour caller cancellation. */
    suspend fun render(request: VtonRequest): DataResult<VtonResult>
}
