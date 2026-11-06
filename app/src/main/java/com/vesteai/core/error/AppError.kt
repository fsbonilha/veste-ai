package com.vesteai.core.error

import com.vesteai.domain.vton.VtonError
import com.vesteai.domain.vton.VtonException

/**
 * User-facing failure categories. Unlike [VtonError] nothing here may carry a provider
 * exception, a stack trace or a status code as its only meaning: `feature` code renders an
 * [AppError] through [com.vesteai.core.error.userMessage] and never shows a raw throwable.
 */
sealed interface AppError {
    data object NotConfigured : AppError
    data object AuthenticationFailed : AppError
    data object UnsupportedCombination : AppError
    data object NoNetwork : AppError
    data object Timeout : AppError
    data class TooManyConcurrentJobs(val guidance: String) : AppError
    data class ProcessingBusy(val retryable: Boolean) : AppError
    data class InputTooSmall(val receivedSize: String?) : AppError
    data object WrongAspectRatio : AppError
    data object BodyNotDetected : AppError
    data object InvalidImage : AppError
    data class JobFailed(val guidance: String?) : AppError
    data class ServerError(val status: Int) : AppError

    /** A local photo or result file could not be read from, or written to, this device. */
    data object LocalMedia : AppError

    /** Anything that did not originate from a [VtonError]. */
    data object Unknown : AppError
}

fun VtonError.toAppError(): AppError = when (this) {
    VtonError.NotConfigured -> AppError.NotConfigured
    VtonError.AuthenticationFailed -> AppError.AuthenticationFailed
    VtonError.UnsupportedCombination -> AppError.UnsupportedCombination
    VtonError.NoNetwork -> AppError.NoNetwork
    VtonError.Timeout -> AppError.Timeout
    is VtonError.TooManyConcurrentJobs -> AppError.TooManyConcurrentJobs(guidance)
    is VtonError.ProcessingBusy -> AppError.ProcessingBusy(retryable)
    is VtonError.InputTooSmall -> AppError.InputTooSmall(receivedSize)
    VtonError.WrongAspectRatio -> AppError.WrongAspectRatio
    VtonError.BodyNotDetected -> AppError.BodyNotDetected
    VtonError.InvalidImage -> AppError.InvalidImage
    is VtonError.JobFailed -> AppError.JobFailed(guidance)
    is VtonError.ServerError -> AppError.ServerError(status)
}

fun Throwable.toAppError(): AppError = when (this) {
    is VtonException -> error.toAppError()
    else -> AppError.Unknown
}
