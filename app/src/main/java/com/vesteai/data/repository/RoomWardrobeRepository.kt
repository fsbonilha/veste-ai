package com.vesteai.data.repository

import android.database.sqlite.SQLiteException
import androidx.room.withTransaction
import com.vesteai.core.di.IoDispatcher
import com.vesteai.core.media.ImageStore
import com.vesteai.core.result.DataResult
import com.vesteai.data.db.VesteDatabase
import com.vesteai.domain.model.Garment
import com.vesteai.domain.model.GarmentCategory
import com.vesteai.domain.repository.WardrobeRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.Clock
import java.util.UUID
import javax.inject.Inject

class RoomWardrobeRepository @Inject constructor(
    private val database: VesteDatabase,
    private val imageStore: ImageStore,
    private val clock: Clock,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : WardrobeRepository {

    private val garmentDao = database.garmentDao()
    private val outfitDao = database.outfitDao()

    override fun observeAll(): Flow<List<Garment>> =
        garmentDao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override fun observeByCategory(category: GarmentCategory): Flow<List<Garment>> =
        garmentDao.observeByCategory(category).map { rows -> rows.map { it.toDomain() } }

    override suspend fun getByIds(ids: Collection<String>): List<Garment> =
        withContext(ioDispatcher) { garmentDao.getByIds(ids).map { it.toDomain() } }

    override suspend fun add(
        imagePath: String,
        category: GarmentCategory,
        displayName: String?,
    ): Garment = withContext(ioDispatcher) {
        val garment = Garment(
            id = UUID.randomUUID().toString(),
            imagePath = imagePath,
            category = category,
            createdAt = clock.millis(),
            isFavorite = false,
            displayName = displayName,
        )
        garmentDao.upsert(garment.toEntity())
        garment
    }

    override suspend fun setFavorite(id: String, isFavorite: Boolean) {
        withContext(ioDispatcher) { garmentDao.setFavorite(id, isFavorite) }
    }

    override suspend fun countLookbooksContaining(garmentId: String): Int =
        withContext(ioDispatcher) { outfitDao.countContaining(garmentId) }

    override suspend fun deleteGarment(garmentId: String): DataResult<Unit> =
        withContext(ioDispatcher) {
            val removedPaths = try {
                database.withTransaction {
                    val garment = garmentDao.getByIds(listOf(garmentId)).firstOrNull()
                        ?: return@withTransaction emptyList()
                    val lookbookPaths = outfitDao.resultPathsContaining(garmentId)
                    outfitDao.deleteContaining(garmentId)
                    garmentDao.delete(garmentId)
                    lookbookPaths + garment.imagePath
                }
            } catch (e: SQLiteException) {
                return@withContext DataResult.Failure(e)
            }
            removedPaths.forEach { imageStore.delete(it) }
            DataResult.Success(Unit)
        }
}
