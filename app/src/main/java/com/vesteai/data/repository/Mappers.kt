package com.vesteai.data.repository

import com.vesteai.data.db.GarmentEntity
import com.vesteai.data.db.OutfitWithGarments
import com.vesteai.domain.model.Garment
import com.vesteai.domain.model.Outfit

internal fun GarmentEntity.toDomain(): Garment = Garment(
    id = id,
    imagePath = imagePath,
    category = category,
    createdAt = createdAt,
    isFavorite = isFavorite,
    displayName = displayName,
)

internal fun Garment.toEntity(): GarmentEntity = GarmentEntity(
    id = id,
    imagePath = imagePath,
    category = category,
    createdAt = createdAt,
    isFavorite = isFavorite,
    displayName = displayName,
)

internal fun OutfitWithGarments.toDomain(): Outfit = Outfit(
    id = outfit.id,
    resultImagePath = outfit.resultImagePath,
    createdAt = outfit.createdAt,
    isFavorite = outfit.isFavorite,
    garments = garments.map { it.toDomain() },
)
