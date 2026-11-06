package com.vesteai.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.vesteai.domain.model.GarmentCategory
import kotlinx.coroutines.flow.Flow

@Dao
interface GarmentDao {
    @Query("SELECT * FROM garment ORDER BY createdAt DESC, id")
    fun observeAll(): Flow<List<GarmentEntity>>

    @Query("SELECT * FROM garment WHERE category = :category ORDER BY createdAt DESC, id")
    fun observeByCategory(category: GarmentCategory): Flow<List<GarmentEntity>>

    @Query("SELECT * FROM garment WHERE id IN (:ids)")
    suspend fun getByIds(ids: Collection<String>): List<GarmentEntity>

    @Upsert
    suspend fun upsert(garment: GarmentEntity)

    @Query("DELETE FROM garment WHERE id = :id")
    suspend fun delete(id: String): Int

    @Query("UPDATE garment SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun setFavorite(id: String, isFavorite: Boolean): Int
}
