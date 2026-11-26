package com.vesteai.domain.vton

sealed interface VtonError {
    data object NotConfigured : VtonError
    data object AuthenticationFailed : VtonError
    data object UnsupportedCombination : VtonError
    data object NoNetwork : VtonError
    data object Timeout : VtonError
    data class TooManyConcurrentJobs(val guidance: String) : VtonError
    data class ProcessingBusy(val retryable: Boolean) : VtonError
    data class InputTooSmall(val receivedSize: String?) : VtonError
    data object WrongAspectRatio : VtonError
    data object BodyNotDetected : VtonError
    data object InvalidImage : VtonError
    data class JobFailed(val guidance: String?) : VtonError
    data class ServerError(val status: Int) : VtonError
}

/**
 * Whether the same request is worth issuing again. Only transient conditions qualify: retrying a
 * rejected key, an unreadable image or a bad combination just repeats the failure, and retrying a
 * *submit* is never safe regardless of this flag because the provider may already have billed for
 * it (see `PixelApiVtonEngine.render`).
 */
val VtonError.isRetryable: Boolean
    get() = when (this) {
        VtonError.NoNetwork, VtonError.Timeout -> true
        is VtonError.TooManyConcurrentJobs -> true
        is VtonError.ProcessingBusy -> retryable
        is VtonError.ServerError -> status >= 500
        else -> false
    }

/** Carries a [VtonError] through [com.vesteai.core.result.DataResult.Failure], which requires a Throwable. */
class VtonException(val error: VtonError) : Exception(error.toString())
