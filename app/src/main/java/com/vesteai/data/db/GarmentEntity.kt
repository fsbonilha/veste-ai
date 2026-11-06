package com.vesteai.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.vesteai.domain.model.GarmentCategory

@Entity(tableName = "garment")
data class GarmentEntity(
    @PrimaryKey val id: String,
    val imagePath: String,
    val category: GarmentCategory,
    val createdAt: Long,
    val isFavorite: Boolean,
    val displayName: String? = null,
)
