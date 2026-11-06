package com.vesteai.feature.wardrobe

import androidx.annotation.StringRes
import com.vesteai.R
import com.vesteai.domain.model.GarmentCategory

@StringRes
internal fun GarmentCategory.labelRes(): Int = when (this) {
    GarmentCategory.TOP -> R.string.category_top
    GarmentCategory.BOTTOM -> R.string.category_bottom
    GarmentCategory.FULL_BODY -> R.string.category_full_body
    GarmentCategory.FOOTWEAR -> R.string.category_footwear
    GarmentCategory.HEADWEAR -> R.string.category_headwear
}
