package com.vesteai.data.vton.pixelapi

import com.vesteai.domain.model.GarmentCategory

internal enum class PixelApiCategory(val wire: String) {
    UPPERBODY("upperbody"),
    LOWERBODY("lowerbody"),
    DRESS("dress"),
}

internal object PixelApiCategoryMap {
    fun toProvider(category: GarmentCategory): PixelApiCategory? = when (category) {
        GarmentCategory.TOP -> PixelApiCategory.UPPERBODY
        GarmentCategory.BOTTOM -> PixelApiCategory.LOWERBODY
        GarmentCategory.FULL_BODY -> PixelApiCategory.DRESS
        GarmentCategory.HEADWEAR, GarmentCategory.FOOTWEAR -> null
    }

    val supportedCategories: Set<GarmentCategory> =
        GarmentCategory.entries.filterTo(mutableSetOf()) { toProvider(it) != null }
}
