package com.vesteai.domain.vton

import com.vesteai.domain.model.GarmentCategory

class VtonRequest(
    val baselineImage: ByteArray,
    val garments: Map<GarmentCategory, ByteArray>,
)
