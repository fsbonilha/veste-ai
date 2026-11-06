package com.vesteai.domain.usecase

import com.vesteai.core.result.DataResult
import com.vesteai.domain.model.Garment
import com.vesteai.domain.model.GarmentCategory
import com.vesteai.domain.repository.WardrobeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class GarmentDeletionUseCaseTest {

    private class FakeWardrobe(var lookbooksContaining: Int = 0) : WardrobeRepository {
        val deleted = mutableListOf<String>()
        var deleteResult: DataResult<Unit> = DataResult.Success(Unit)

        override fun observeAll(): Flow<List<Garment>> = MutableStateFlow(emptyList())

        override fun observeByCategory(category: GarmentCategory): Flow<List<Garment>> =
            observeAll().map { it }

        override suspend fun getByIds(ids: Collection<String>): List<Garment> = emptyList()

        override suspend fun add(imagePath: String, category: GarmentCategory, displayName: String?): Garment =
            error("not used")

        override suspend fun setFavorite(id: String, isFavorite: Boolean) = Unit

        override suspend fun countLookbooksContaining(garmentId: String): Int = lookbooksContaining

        override suspend fun deleteGarment(garmentId: String): DataResult<Unit> {
            deleted += garmentId
            return deleteResult
        }
    }

    @Test
    fun planIsStandaloneWhenNoLookbookContainsTheGarment() = runTest {
        val useCase = GarmentDeletionUseCase(FakeWardrobe(lookbooksContaining = 0))

        assertEquals(DeletionPlan.Standalone, useCase.plan("g1"))
    }

    @Test
    fun planCarriesLookbookCountWhenGarmentIsInLookbooks() = runTest {
        val useCase = GarmentDeletionUseCase(FakeWardrobe(lookbooksContaining = 3))

        assertEquals(DeletionPlan.CascadesLookbooks(3), useCase.plan("g1"))
    }

    @Test
    fun deleteDelegatesToRepositoryAndReturnsItsResult() = runTest {
        val wardrobe = FakeWardrobe()
        val useCase = GarmentDeletionUseCase(wardrobe)

        val result = useCase.delete("g1")

        assertEquals(listOf("g1"), wardrobe.deleted)
        assertEquals(DataResult.Success(Unit), result)
    }
}
