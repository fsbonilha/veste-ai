package com.vesteai.domain.repository

import com.vesteai.domain.model.Garment
import com.vesteai.domain.model.GarmentCategory
import kotlinx.coroutines.flow.Flow

interface WardrobeRepository {
    fun observeAll(): Flow<List<Garment>>

    fun observeByCategory(category: GarmentCategory): Flow<List<Garment>>

    suspend fun getByIds(ids: Collection<String>): List<Garment>

    /** [imagePath] is relative to the app's media root (see ImageStore). */
    suspend fun add(
        imagePath: String,
        category: GarmentCategory,
        displayName: String? = null,
    ): Garment

    suspend fun setFavorite(id: String, isFavorite: Boolean)
}
