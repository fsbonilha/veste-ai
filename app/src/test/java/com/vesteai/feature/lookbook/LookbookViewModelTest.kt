package com.vesteai.feature.lookbook

import com.vesteai.core.media.ImageStore
import com.vesteai.core.result.DataResult
import com.vesteai.domain.model.Garment
import com.vesteai.domain.model.GarmentCategory
import com.vesteai.domain.model.Outfit
import com.vesteai.domain.repository.LookbookRepository
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class LookbookViewModelTest {

    private class FakeLookbook : LookbookRepository {
        val outfits = MutableStateFlow<List<Outfit>>(emptyList())
        val favoriteCalls = mutableListOf<Pair<String, Boolean>>()
        val deleteCalls = mutableListOf<String>()
        var deleteFailure: Exception? = null
        var failObserve = false

        override fun observeAll(): Flow<List<Outfit>> =
            // A real Room query fails during collection, not at call time, so the fake
            // reproduces that: a throwing operator inside the flow.
            if (failObserve) flow { throw IllegalStateException("db is gone") } else outfits

        override suspend fun getById(id: String): Outfit? = outfits.value.firstOrNull { it.id == id }

        override suspend fun save(
            resultImagePath: String,
            garmentIds: Collection<String>,
        ): DataResult<Outfit> = error("not used")

        override suspend fun setFavorite(id: String, isFavorite: Boolean) {
            favoriteCalls += id to isFavorite
        }

        override suspend fun delete(id: String) {
            deleteFailure?.let { throw it }
            deleteCalls += id
        }
    }

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var lookbook: FakeLookbook

    private val top = Garment(
        id = "top",
        imagePath = "wardrobe/top.jpg",
        category = GarmentCategory.TOP,
        createdAt = 1L,
        isFavorite = false,
    )
    private val jeans = Garment(
        id = "jeans",
        imagePath = "wardrobe/jeans.jpg",
        category = GarmentCategory.BOTTOM,
        createdAt = 2L,
        isFavorite = false,
    )
    private val look = Outfit(
        id = "look-1",
        resultImagePath = "looks/look-1.jpg",
        createdAt = DAY_MILLIS,
        isFavorite = false,
        garments = listOf(top, jeans),
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        lookbook = FakeLookbook().apply { outfits.value = listOf(look) }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = LookbookViewModel(
        lookbook = lookbook,
        imageStore = ImageStore(tmp.root),
        dateFormatter = LookbookDateFormatter(Locale.US, ZoneId.of("UTC")),
    )

    @Test
    fun loadsSavedLooksWithTheirFormattedDate() {
        val vm = viewModel()

        val state = vm.uiState.value as LookbookUiState.Content
        assertEquals(listOf(LookCard(look, "Mar 9, 2024")), state.looks)
    }

    @Test
    fun emptyLookbookIsContentWithNoLooks() {
        lookbook.outfits.value = emptyList()

        val vm = viewModel()

        assertEquals(emptyList<LookCard>(), (vm.uiState.value as LookbookUiState.Content).looks)
    }

    @Test
    fun observeFailureSurfacesTheErrorState() {
        lookbook.failObserve = true

        val vm = viewModel()

        assertEquals(LookbookUiState.Error, vm.uiState.value)
    }

    @Test
    fun retryReloadsAfterAnError() {
        val vm = viewModel()
        lookbook.failObserve = false
        lookbook.outfits.value = listOf(look)

        vm.retry()

        assertEquals(1, (vm.uiState.value as LookbookUiState.Content).looks.size)
    }

    @Test
    fun toggleFavoriteFlipsTheCurrentFlagOnTheRepository() = runTest {
        val vm = viewModel()

        vm.toggleFavorite(LookCard(look, "Mar 9, 2024"))
        advanceUntilIdle()

        assertEquals(listOf("look-1" to true), lookbook.favoriteCalls)
    }

    @Test
    fun toggleFavoriteOnAFavoriteLookUnsetsIt() = runTest {
        val vm = viewModel()
        val favorite = LookCard(look.copy(isFavorite = true), "Mar 9, 2024")

        vm.toggleFavorite(favorite)
        advanceUntilIdle()

        assertEquals(listOf("look-1" to false), lookbook.favoriteCalls)
    }

    @Test
    fun requestDeleteAsksForConfirmationWithoutTouchingTheRepository() = runTest {
        val vm = viewModel()

        vm.requestDelete((vm.uiState.value as LookbookUiState.Content).looks.single())
        advanceUntilIdle()

        val state = vm.uiState.value as LookbookUiState.Content
        assertEquals(look.id, state.pendingDeletion?.outfit?.id)
        assertEquals(emptyList<String>(), lookbook.deleteCalls)
    }

    @Test
    fun cancellingDeleteKeepsTheLookAndDeletesNothing() = runTest {
        val vm = viewModel()
        vm.requestDelete((vm.uiState.value as LookbookUiState.Content).looks.single())

        vm.cancelDelete()
        advanceUntilIdle()

        val state = vm.uiState.value as LookbookUiState.Content
        assertNull(state.pendingDeletion)
        assertEquals(emptyList<String>(), lookbook.deleteCalls)
    }

    @Test
    fun confirmDeleteDeletesOnlyTheConfirmedLook() = runTest {
        val vm = viewModel()
        vm.requestDelete((vm.uiState.value as LookbookUiState.Content).looks.single())

        vm.confirmDelete()
        advanceUntilIdle()

        assertEquals(listOf("look-1"), lookbook.deleteCalls)
        assertNull((vm.uiState.value as LookbookUiState.Content).pendingDeletion)
    }

    @Test
    fun confirmDeleteWithoutAPendingRequestIsANoOp() = runTest {
        val vm = viewModel()

        vm.confirmDelete()
        advanceUntilIdle()

        assertEquals(emptyList<String>(), lookbook.deleteCalls)
    }

    @Test
    fun deleteFailureIsReportedAndDismissible() = runTest {
        lookbook.deleteFailure = IllegalStateException("disk full")
        val vm = viewModel()
        vm.requestDelete((vm.uiState.value as LookbookUiState.Content).looks.single())

        vm.confirmDelete()
        advanceUntilIdle()
        assertTrue((vm.uiState.value as LookbookUiState.Content).deleteFailed)

        vm.dismissDeleteError()

        assertTrue(!(vm.uiState.value as LookbookUiState.Content).deleteFailed)
    }

    @Test
    fun reloadExposesTheConstituentGarmentIds() {
        val vm = viewModel()

        val ids = vm.reloadGarmentIds(LookCard(look, "Mar 9, 2024"))

        assertEquals(listOf("top", "jeans"), ids)
    }

    @Test
    fun imageFileResolvesAgainstTheMediaRoot() {
        val vm = viewModel()

        assertEquals(tmp.root.resolve("looks/look-1.jpg"), vm.imageFile("looks/look-1.jpg"))
    }

    private companion object {
        const val DAY_MILLIS = 1_710_000_367_000L
    }
}
