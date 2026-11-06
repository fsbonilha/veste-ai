package com.vesteai.data.repository

import android.database.sqlite.SQLiteException
import com.vesteai.core.di.IoDispatcher
import com.vesteai.core.media.ImageStore
import com.vesteai.core.result.DataResult
import com.vesteai.data.db.OutfitDao
import com.vesteai.data.db.OutfitEntity
import com.vesteai.data.db.OutfitGarmentCrossRef
import com.vesteai.domain.model.Outfit
import com.vesteai.domain.repository.LookbookRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.Clock
import java.util.UUID
import javax.inject.Inject

class RoomLookbookRepository @Inject constructor(
    private val outfitDao: OutfitDao,
    private val imageStore: ImageStore,
    private val clock: Clock,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : LookbookRepository {

    override fun observeAll(): Flow<List<Outfit>> =
        outfitDao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override suspend fun getById(id: String): Outfit? =
        withContext(ioDispatcher) { outfitDao.getWithGarments(id)?.toDomain() }

    override suspend fun save(
        resultImagePath: String,
        garmentIds: Collection<String>,
    ): DataResult<Outfit> = withContext(ioDispatcher) {
        if (garmentIds.isEmpty()) {
            return@withContext DataResult.Failure(
                IllegalArgumentException("An outfit needs at least one garment"),
            )
        }
        val outfit = OutfitEntity(
            id = UUID.randomUUID().toString(),
            resultImagePath = resultImagePath,
            createdAt = clock.millis(),
            isFavorite = false,
        )
        val crossRefs = garmentIds.distinct().map { OutfitGarmentCrossRef(outfit.id, it) }
        try {
            outfitDao.insert(outfit, crossRefs)
        } catch (e: SQLiteException) {
            return@withContext DataResult.Failure(e)
        }
        val saved = outfitDao.getWithGarments(outfit.id)
            ?: return@withContext DataResult.Failure(
                IllegalStateException("Outfit ${outfit.id} missing after insert"),
            )
        DataResult.Success(saved.toDomain())
    }

    override suspend fun setFavorite(id: String, isFavorite: Boolean) {
        withContext(ioDispatcher) { outfitDao.setFavorite(id, isFavorite) }
    }

    override suspend fun delete(id: String) {
        withContext(ioDispatcher) {
            val outfit = outfitDao.getWithGarments(id)?.outfit ?: return@withContext
            if (outfitDao.delete(id) > 0) imageStore.delete(outfit.resultImagePath)
        }
    }
}
