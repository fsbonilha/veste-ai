package com.vesteai.data.repository

import com.vesteai.core.di.IoDispatcher
import com.vesteai.data.db.GarmentDao
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
    private val garmentDao: GarmentDao,
    private val clock: Clock,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : WardrobeRepository {

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
}
