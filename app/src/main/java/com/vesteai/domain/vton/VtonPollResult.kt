package com.vesteai.domain.vton

import com.vesteai.core.result.DataResult

/**
 * Result of a single poll call. An engine that has not finished yet returns null;
 * a terminal state returns [Completed] or [Failed].
 */
sealed interface VtonPollResult {
    data class Completed(val image: ByteArray) : VtonPollResult
    data class Failed(val error: VtonException) : VtonPollResult
}