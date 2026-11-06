package com.vesteai.core.error

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.vesteai.R

@StringRes
fun AppError.messageRes(): Int = when (this) {
    AppError.NotConfigured -> R.string.error_vton_not_configured
    AppError.AuthenticationFailed -> R.string.error_vton_authentication
    AppError.UnsupportedCombination -> R.string.error_vton_unsupported
    AppError.NoNetwork -> R.string.error_vton_no_network
    AppError.Timeout -> R.string.error_vton_timeout
    is AppError.TooManyConcurrentJobs -> R.string.error_vton_too_many_jobs
    is AppError.ProcessingBusy -> R.string.error_vton_busy
    is AppError.InputTooSmall -> R.string.error_vton_input_too_small
    AppError.WrongAspectRatio -> R.string.error_vton_wrong_aspect
    AppError.BodyNotDetected -> R.string.error_vton_body_not_detected
    AppError.InvalidImage -> R.string.error_vton_invalid_image
    is AppError.JobFailed -> R.string.error_vton_job_failed
    is AppError.ServerError -> R.string.error_vton_server
    AppError.LocalMedia -> R.string.error_local_media
    AppError.Unknown -> R.string.error_unknown
}

/**
 * The provider's own `guidance` is shown verbatim when it exists (plan §7): it is more
 * specific than any copy this module could invent.
 */
@Composable
fun userMessage(error: AppError): String {
    val generic = stringResource(error.messageRes())
    val guidance = when (error) {
        is AppError.TooManyConcurrentJobs -> error.guidance
        is AppError.JobFailed -> error.guidance
        else -> null
    }
    return if (guidance.isNullOrBlank()) generic else guidance
}
