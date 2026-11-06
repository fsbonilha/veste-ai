package com.vesteai.data.vton.pixelapi

import com.vesteai.data.vton.pixelapi.dto.OutfitTryOnRequestDto
import com.vesteai.data.vton.pixelapi.dto.SingleTryOnRequestDto
import com.vesteai.domain.model.GarmentCategory
import com.vesteai.domain.vton.VtonRequest
import java.util.Base64

internal sealed interface PixelApiSubmission {
    data class Single(val body: SingleTryOnRequestDto) : PixelApiSubmission

    data class Outfit(val body: OutfitTryOnRequestDto) : PixelApiSubmission
}

internal object PixelApiRequestBuilder {

    /** Returns null when the selection cannot be sent as one PixelAPI call. */
    fun build(request: VtonRequest): PixelApiSubmission? {
        val garments = request.garments
        val person = encode(request.baselineImage)

        if (garments.keys == setOf(GarmentCategory.TOP, GarmentCategory.BOTTOM)) {
            return PixelApiSubmission.Outfit(
                OutfitTryOnRequestDto(
                    personImage = person,
                    upperbodyGarment = encode(garments.getValue(GarmentCategory.TOP)),
                    lowerbodyGarment = encode(garments.getValue(GarmentCategory.BOTTOM)),
                ),
            )
        }

        val (category, bytes) = garments.entries.singleOrNull() ?: return null
        val wire = PixelApiCategoryMap.toProvider(category) ?: return null
        return PixelApiSubmission.Single(
            SingleTryOnRequestDto(
                personImage = person,
                garmentImage = encode(bytes),
                category = wire.wire,
            ),
        )
    }

    private fun encode(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)
}
