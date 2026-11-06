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

/** Carries a [VtonError] through [com.vesteai.core.result.DataResult.Failure], which requires a Throwable. */
class VtonException(val error: VtonError) : Exception(error.toString())
