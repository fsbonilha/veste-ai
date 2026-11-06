package com.vesteai.domain.model

data class Garment(
    val id: String,
    val imagePath: String,
    val category: GarmentCategory,
    val createdAt: Long,
    val isFavorite: Boolean,
    val displayName: String? = null,
)
