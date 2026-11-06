package com.vesteai.domain.model

data class Outfit(
    val id: String,
    val resultImagePath: String,
    val createdAt: Long,
    val isFavorite: Boolean,
    val garments: List<Garment>,
)
