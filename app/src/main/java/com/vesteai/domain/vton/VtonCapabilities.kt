package com.vesteai.domain.vton

import com.vesteai.domain.model.GarmentCategory

data class VtonCapabilities(
    val supportedCategories: Set<GarmentCategory>,
    val supportsTopBottomOutfit: Boolean,
    val maxParallelJobs: Int,
    val requiredAspect: AspectRequirement,
)

sealed interface AspectRequirement {
    data object Unconstrained : AspectRequirement

    data class Portrait(val minWidthPx: Int, val minHeightPx: Int) : AspectRequirement
}
