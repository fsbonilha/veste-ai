package com.vesteai.feature.wardrobe

import com.vesteai.core.media.ImageStore
import com.vesteai.core.result.DataResult
import com.vesteai.domain.model.Garment
import com.vesteai.domain.model.GarmentCategory
import com.vesteai.domain.repository.WardrobeRepository
import com.vesteai.domain.usecase.DeletionPlan
import com.vesteai.domain.usecase.GarmentDeletionUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class WardrobeViewModelTest {

    private class FakeWardrobe : WardrobeRepository {
        val garments = MutableStateFlow<List<Garment>>(emptyList())
        val favoriteCalls = mutableListOf<Pair<String, Boolean>>()

        override fun observeAll(): Flow<List<Garment>> = garments

        override fun observeByCategory(category: GarmentCategory): Flow<List<Garment>> =
            garments.map { list -> list.filter { it.category == category } }

        override suspend fun getByIds(ids: Collection<String>): List<Garment> = emptyList()

        override suspend fun add(imagePath: String, category: GarmentCategory, displayName: String?): Garment =
            error("not used")

        override suspend fun setFavorite(id: String, isFavorite: Boolean) {
            favoriteCalls += id to isFavorite
        }

        override suspend fun countLookbooksContaining(garmentId: String): Int = 0

        override suspend fun deleteGarment(garmentId: String): DataResult<Unit> = DataResult.Success(Unit)
    }

    @get:Rule
    val tmp = TemporaryFolder()

    private val shirt = garment("shirt", GarmentCategory.TOP)
    private val boots = garment("boots", GarmentCategory.FOOTWEAR)
    private lateinit var wardrobe: FakeWardrobe

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        wardrobe = FakeWardrobe().apply { garments.value = listOf(shirt, boots) }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() =
        WardrobeViewModel(wardrobe, GarmentDeletionUseCase(wardrobe), ImageStore(tmp.root))

    private fun garment(id: String, category: GarmentCategory, isFavorite: Boolean = false) = Garment(
        id = id,
        imagePath = "wardrobe/$id.jpg",
        category = category,
        createdAt = 1L,
        isFavorite = isFavorite,
    )

    @Test
    fun startsUnfilteredAndShowsEveryGarment() {
        val vm = viewModel()

        val state = vm.uiState.value as WardrobeUiState.Content
        assertEquals(null, state.filter)
        assertEquals(listOf(shirt, boots), state.garments)
    }

    @Test
    fun selectingCategoryShowsOnlyThatCategory() {
        val vm = viewModel()

        vm.selectFilter(GarmentCategory.FOOTWEAR)

        val state = vm.uiState.value as WardrobeUiState.Content
        assertEquals(GarmentCategory.FOOTWEAR, state.filter)
        assertEquals(listOf(boots), state.garments)
    }

    @Test
    fun clearingFilterRestoresAllGarments() {
        val vm = viewModel()
        vm.selectFilter(GarmentCategory.TOP)

        vm.selectFilter(null)

        assertEquals(listOf(shirt, boots), (vm.uiState.value as WardrobeUiState.Content).garments)
    }

    @Test
    fun filterStillAppliesWhenGarmentsChange() {
        val vm = viewModel()
        vm.selectFilter(GarmentCategory.TOP)
        val newShirt = garment("shirt2", GarmentCategory.TOP)

        wardrobe.garments.value = listOf(shirt, boots, newShirt)

        assertEquals(
            listOf(shirt, newShirt),
            (vm.uiState.value as WardrobeUiState.Content).garments,
        )
    }

    @Test
    fun toggleFavoriteFlipsTheCurrentFlagOnTheRepository() = runTest {
        val vm = viewModel()

        vm.toggleFavorite(shirt)
        advanceUntilIdle()

        assertEquals(listOf("shirt" to true), wardrobe.favoriteCalls)
    }

    @Test
    fun toggleFavoriteOnAFavoriteGarmentUnsetsIt() = runTest {
        val vm = viewModel()

        vm.toggleFavorite(shirt.copy(isFavorite = true))
        advanceUntilIdle()

        assertEquals(listOf("shirt" to false), wardrobe.favoriteCalls)
    }

    @Test
    fun deleteRequestWithNoLookbooksPlansStandaloneDeletion() = runTest {
        val vm = viewModel()

        vm.requestDelete(shirt)
        advanceUntilIdle()

        val pending = (vm.uiState.value as WardrobeUiState.Content).pendingDeletion
        assertEquals(DeletionPlan.Standalone, pending?.plan)
    }

    @Test
    fun cancellingDeleteClearsPendingDeletion() = runTest {
        val vm = viewModel()
        vm.requestDelete(shirt)
        advanceUntilIdle()

        vm.cancelDelete()

        assertTrue((vm.uiState.value as WardrobeUiState.Content).pendingDeletion == null)
    }
}
