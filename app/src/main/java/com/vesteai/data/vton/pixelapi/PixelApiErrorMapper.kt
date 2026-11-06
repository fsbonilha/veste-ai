package com.vesteai.data.vton.pixelapi

import com.vesteai.data.vton.pixelapi.dto.ErrorEnvelopeDto
import com.vesteai.data.vton.pixelapi.dto.pixelApiJson
import com.vesteai.domain.vton.VtonError
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonPrimitive
import java.io.IOException
import java.io.InterruptedIOException

internal object PixelApiErrorMapper {

    /** Maps a non-2xx PixelAPI response (plan §6.5) to a [VtonError]. */
    fun fromResponse(status: Int, body: String): VtonError {
        val detail = decodeEnvelope(body)?.detail
        val code = detail?.error
        return when {
            status == 422 && code == "image_too_small" ->
                VtonError.InputTooSmall(detail?.receivedSize?.let { (it as? JsonPrimitive)?.content ?: it.toString() })
            status == 422 && code == "wrong_aspect_ratio" -> VtonError.WrongAspectRatio
            status == 422 && code == "body_segmentation_failed" -> VtonError.BodyNotDetected
            status == 422 && code == "invalid_category" -> VtonError.UnsupportedCombination
            status == 400 && code == "invalid_image" -> VtonError.InvalidImage
            status == 401 -> VtonError.AuthenticationFailed
            status == 429 -> VtonError.TooManyConcurrentJobs(detail?.guidance ?: DEFAULT_CONCURRENCY_GUIDANCE)
            status == 503 && code == "processing_out_of_memory" -> VtonError.ProcessingBusy(retryable = true)
            else -> VtonError.ServerError(status)
        }
    }

    /** Maps a transport failure that never produced an HTTP response. */
    fun fromTransport(error: IOException): VtonError = when (error) {
        is InterruptedIOException -> VtonError.Timeout
        else -> VtonError.NoNetwork
    }

    private fun decodeEnvelope(body: String): ErrorEnvelopeDto? =
        try {
            pixelApiJson.decodeFromString<ErrorEnvelopeDto>(body)
        } catch (e: SerializationException) {
            null
        }

    private const val DEFAULT_CONCURRENCY_GUIDANCE =
        "Too many jobs are running on this PixelAPI key. Wait for one to finish and try again."
}
