package com.vesteai.data.repository

import com.vesteai.data.db.inMemoryDatabase
import com.vesteai.domain.model.Garment
import com.vesteai.domain.model.GarmentCategory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class RoomWardrobeRepositoryTest {
    private val db = inMemoryDatabase()
    private val dispatcher = UnconfinedTestDispatcher()
    private val clock = Clock.fixed(Instant.ofEpochMilli(1_700_000_000_000L), ZoneOffset.UTC)
    private val repository = RoomWardrobeRepository(db.garmentDao(), clock, dispatcher)

    @After
    fun tearDown() = db.close()

    @Test
    fun addStampsCreatedAtFromInjectedClock() = runTest(dispatcher) {
        val added = repository.add("wardrobe/x.jpg", GarmentCategory.BOTTOM, displayName = "Chinos")

        assertEquals(
            Garment(
                id = added.id,
                imagePath = "wardrobe/x.jpg",
                category = GarmentCategory.BOTTOM,
                createdAt = 1_700_000_000_000L,
                isFavorite = false,
                displayName = "Chinos",
            ),
            added,
        )
    }

    @Test
    fun addedGarmentIsObservableAndFetchableById() = runTest(dispatcher) {
        val added = repository.add("wardrobe/x.jpg", GarmentCategory.TOP)

        assertEquals(listOf(added), repository.observeAll().first())
        assertEquals(listOf(added), repository.getByIds(listOf(added.id)))
    }

    @Test
    fun observeByCategoryMapsOnlyMatchingGarments() = runTest(dispatcher) {
        repository.add("wardrobe/top.jpg", GarmentCategory.TOP)
        val shoe = repository.add("wardrobe/shoe.jpg", GarmentCategory.FOOTWEAR)

        assertEquals(listOf(shoe), repository.observeByCategory(GarmentCategory.FOOTWEAR).first())
    }

    @Test
    fun setFavoriteIsReflectedInObservedGarment() = runTest(dispatcher) {
        val added = repository.add("wardrobe/x.jpg", GarmentCategory.TOP)

        repository.setFavorite(added.id, true)

        assertEquals(listOf(added.copy(isFavorite = true)), repository.observeAll().first())
    }
}
