package com.vesteai.domain.vton

/**
 * What the provider says about a job that is still running. PixelAPI reports this on every poll
 * (`eta_seconds`, `queue_position`, and a `wait_info.user_message` written for end users), which is
 * far better copy than anything this app can invent. Engines with no queue reporting leave the
 * fields null and the UI falls back to its elapsed timer.
 */
data class VtonProgress(
    val estimatedWaitSeconds: Long? = null,
    val queuePosition: Int? = null,
    val userMessage: String? = null,
) {
    val isEmpty: Boolean
        get() = estimatedWaitSeconds == null && queuePosition == null && userMessage.isNullOrBlank()
}