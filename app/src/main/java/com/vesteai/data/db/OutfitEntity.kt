package com.vesteai.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "outfit")
data class OutfitEntity(
    @PrimaryKey val id: String,
    val resultImagePath: String,
    val createdAt: Long,
    val isFavorite: Boolean,
)
