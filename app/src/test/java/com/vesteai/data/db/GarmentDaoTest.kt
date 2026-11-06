package com.vesteai.data.db

import com.vesteai.domain.model.GarmentCategory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GarmentDaoTest {
    private val db = inMemoryDatabase()
    private val dao = db.garmentDao()

    @After
    fun tearDown() = db.close()

    @Test
    fun upsertThenObserveAllReturnsAllFieldsIncludingCategory() = runTest {
        val shoe = garment("g1", GarmentCategory.FOOTWEAR, displayName = "Boots")

        dao.upsert(shoe)

        assertEquals(listOf(shoe), dao.observeAll().first())
    }

    @Test
    fun upsertWithExistingIdReplacesRow() = runTest {
        dao.upsert(garment("g1", GarmentCategory.TOP))

        dao.upsert(garment("g1", GarmentCategory.BOTTOM, displayName = "Jeans"))

        assertEquals(
            listOf(garment("g1", GarmentCategory.BOTTOM, displayName = "Jeans")),
            dao.observeAll().first(),
        )
    }

    @Test
    fun observeAllIsNewestFirst() = runTest {
        dao.upsert(garment("old", createdAt = 1L))
        dao.upsert(garment("new", createdAt = 3L))
        dao.upsert(garment("mid", createdAt = 2L))

        assertEquals(listOf("new", "mid", "old"), dao.observeAll().first().map { it.id })
    }

    @Test
    fun observeByCategoryFiltersToThatCategory() = runTest {
        dao.upsert(garment("top", GarmentCategory.TOP))
        dao.upsert(garment("dress", GarmentCategory.FULL_BODY))

        assertEquals(
            listOf("dress"),
            dao.observeByCategory(GarmentCategory.FULL_BODY).first().map { it.id },
        )
    }

    @Test
    fun getByIdsReturnsOnlyRequestedExistingRows() = runTest {
        dao.upsert(garment("a"))
        dao.upsert(garment("b"))
        dao.upsert(garment("c"))

        assertEquals(
            setOf("a", "c"),
            dao.getByIds(listOf("a", "c", "missing")).map { it.id }.toSet(),
        )
    }

    @Test
    fun setFavoriteUpdatesFlagAndReportsOneRow() = runTest {
        dao.upsert(garment("g1", isFavorite = false))

        val updated = dao.setFavorite("g1", true)

        assertEquals(1, updated)
        assertEquals(true, dao.getByIds(listOf("g1")).single().isFavorite)
    }

    @Test
    fun setFavoriteOnUnknownIdReportsZeroRows() = runTest {
        assertEquals(0, dao.setFavorite("missing", true))
    }

    @Test
    fun deleteRemovesOnlyThatGarment() = runTest {
        dao.upsert(garment("keep"))
        dao.upsert(garment("drop"))

        val deleted = dao.delete("drop")

        assertEquals(1, deleted)
        assertEquals(listOf("keep"), dao.observeAll().first().map { it.id })
    }
}
