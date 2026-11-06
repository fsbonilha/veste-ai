package com.vesteai.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class OutfitDao {
    @Transaction
    @Query("SELECT * FROM outfit ORDER BY createdAt DESC, id")
    abstract fun observeAll(): Flow<List<OutfitWithGarments>>

    @Transaction
    @Query("SELECT * FROM outfit WHERE id = :id")
    abstract suspend fun getWithGarments(id: String): OutfitWithGarments?

    @Transaction
    open suspend fun insert(outfit: OutfitEntity, crossRefs: List<OutfitGarmentCrossRef>) {
        insertOutfit(outfit)
        insertCrossRefs(crossRefs)
    }

    @Query("DELETE FROM outfit WHERE id = :id")
    abstract suspend fun delete(id: String): Int

    @Query("UPDATE outfit SET isFavorite = :isFavorite WHERE id = :id")
    abstract suspend fun setFavorite(id: String, isFavorite: Boolean): Int

    @Query("SELECT COUNT(DISTINCT outfitId) FROM outfit_garment WHERE garmentId = :garmentId")
    abstract suspend fun countContaining(garmentId: String): Int

    @Query(
        "DELETE FROM outfit WHERE id IN " +
            "(SELECT outfitId FROM outfit_garment WHERE garmentId = :garmentId)",
    )
    abstract suspend fun deleteContaining(garmentId: String): Int

    @Insert
    protected abstract suspend fun insertOutfit(outfit: OutfitEntity)

    @Insert
    protected abstract suspend fun insertCrossRefs(crossRefs: List<OutfitGarmentCrossRef>)
}
