package com.vesteai.domain.repository

import com.vesteai.core.result.DataResult
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

    /** Number of lookbooks containing [garmentId]. Advisory only; see [deleteGarment]. */
    suspend fun countLookbooksContaining(garmentId: String): Int

    /**
     * Deletes the garment and every lookbook containing it in one transaction, then removes
     * their image files. The affected lookbooks are re-derived inside the transaction, so the
     * count from [countLookbooksContaining] is never trusted.
     */
    suspend fun deleteGarment(garmentId: String): DataResult<Unit>
}
