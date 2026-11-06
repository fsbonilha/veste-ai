package com.vesteai.data.vton.pixelapi.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

internal val pixelApiJson = Json { ignoreUnknownKeys = true }

@Serializable
internal data class SingleTryOnRequestDto(
    @SerialName("person_image") val personImage: String,
    @SerialName("garment_image") val garmentImage: String,
    val category: String,
)

@Serializable
internal data class OutfitTryOnRequestDto(
    @SerialName("person_image") val personImage: String,
    @SerialName("upperbody_garment") val upperbodyGarment: String,
    @SerialName("lowerbody_garment") val lowerbodyGarment: String,
)

@Serializable
internal data class SubmitResponseDto(
    @SerialName("job_id") val jobId: String,
)

@Serializable
internal data class JobStatusDto(
    val status: String,
    @SerialName("result_image_b64") val resultImageB64: String? = null,
    val guidance: String? = null,
)

@Serializable
internal data class ErrorEnvelopeDto(val detail: ErrorDetailDto? = null)

@Serializable
internal data class ErrorDetailDto(
    val error: String? = null,
    val guidance: String? = null,
    @SerialName("received_size") val receivedSize: JsonElement? = null,
)

internal enum class PixelApiJobStatus(val wire: String) {
    QUEUED("queued"),
    PROCESSING("processing"),
    COMPLETED("completed"),
    FAILED("failed");

    companion object {
        fun fromWire(value: String): PixelApiJobStatus? = entries.firstOrNull { it.wire == value }
    }
}
