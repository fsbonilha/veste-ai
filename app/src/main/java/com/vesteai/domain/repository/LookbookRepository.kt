package com.vesteai.domain.repository

import com.vesteai.core.result.DataResult
import com.vesteai.domain.model.Outfit
import kotlinx.coroutines.flow.Flow

interface LookbookRepository {
    fun observeAll(): Flow<List<Outfit>>

    suspend fun getById(id: String): Outfit?

    /** Fails if [garmentIds] is empty or references a garment that no longer exists. */
    suspend fun save(resultImagePath: String, garmentIds: Collection<String>): DataResult<Outfit>

    suspend fun setFavorite(id: String, isFavorite: Boolean)

    /** Deletes the outfit row, then its result image. */
    suspend fun delete(id: String)
}
