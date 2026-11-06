package com.vesteai.data.db

import android.database.sqlite.SQLiteConstraintException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OutfitDaoTest {
    private val db = inMemoryDatabase()
    private val garmentDao = db.garmentDao()
    private val dao = db.outfitDao()

    @After
    fun tearDown() = db.close()

    private suspend fun seedGarments(vararg ids: String) = ids.forEach { garmentDao.upsert(garment(it)) }

    @Test
    fun getWithGarmentsJoinsThroughCrossRefs() = runTest {
        seedGarments("top", "bottom", "unrelated")

        dao.insert(outfit("o1"), crossRefs("o1", "top", "bottom"))

        val loaded = dao.getWithGarments("o1")!!
        assertEquals(outfit("o1"), loaded.outfit)
        assertEquals(setOf(garment("top"), garment("bottom")), loaded.garments.toSet())
    }

    @Test
    fun getWithGarmentsOnUnknownIdReturnsNull() = runTest {
        assertNull(dao.getWithGarments("missing"))
    }

    @Test
    fun observeAllEmitsOutfitsNewestFirstWithTheirGarments() = runTest {
        seedGarments("a", "b")
        dao.insert(outfit("older", createdAt = 1L), crossRefs("older", "a"))
        dao.insert(outfit("newer", createdAt = 2L), crossRefs("newer", "a", "b"))

        val all = dao.observeAll().first()

        assertEquals(listOf("newer", "older"), all.map { it.outfit.id })
        assertEquals(listOf(setOf("a", "b"), setOf("a")), all.map { o -> o.garments.map { it.id }.toSet() })
    }

    @Test
    fun insertReferencingMissingGarmentFailsAndRollsBackOutfit() = runTest {
        seedGarments("a")

        assertFailsWith<SQLiteConstraintException> {
            dao.insert(outfit("o1"), crossRefs("o1", "a", "ghost"))
        }

        assertNull(dao.getWithGarments("o1"))
        assertEquals(0, db.crossRefCount())
    }

    @Test
    fun deletingOutfitCascadesItsCrossRefsButKeepsGarments() = runTest {
        seedGarments("a", "b")
        dao.insert(outfit("o1"), crossRefs("o1", "a", "b"))

        val deleted = dao.delete("o1")

        assertEquals(1, deleted)
        assertEquals(0, db.crossRefCount())
        assertEquals(2, garmentDao.getByIds(listOf("a", "b")).size)
    }

    @Test
    fun deletingGarmentStillReferencedByOutfitIsRejected() = runTest {
        seedGarments("a")
        dao.insert(outfit("o1"), crossRefs("o1", "a"))

        assertFailsWith<SQLiteConstraintException> { garmentDao.delete("a") }

        assertEquals(1, garmentDao.getByIds(listOf("a")).size)
        assertEquals(1, db.crossRefCount())
    }

    @Test
    fun countContainingCountsEachOutfitOnce() = runTest {
        seedGarments("shared", "other")
        dao.insert(outfit("o1"), crossRefs("o1", "shared", "other"))
        dao.insert(outfit("o2"), crossRefs("o2", "shared"))
        dao.insert(outfit("o3"), crossRefs("o3", "other"))

        assertEquals(2, dao.countContaining("shared"))
    }

    @Test
    fun countContainingUnreferencedGarmentIsZero() = runTest {
        seedGarments("lonely")

        assertEquals(0, dao.countContaining("lonely"))
    }

    @Test
    fun deleteContainingRemovesExactlyTheContainingOutfits() = runTest {
        seedGarments("shared", "other")
        dao.insert(outfit("o1"), crossRefs("o1", "shared", "other"))
        dao.insert(outfit("o2"), crossRefs("o2", "shared"))
        dao.insert(outfit("o3"), crossRefs("o3", "other"))

        val deleted = dao.deleteContaining("shared")

        assertEquals(2, deleted)
        assertEquals(listOf("o3"), dao.observeAll().first().map { it.outfit.id })
        assertEquals(1, db.crossRefCount())
    }

    @Test
    fun garmentIsDeletableAfterDeleteContaining() = runTest {
        seedGarments("a")
        dao.insert(outfit("o1"), crossRefs("o1", "a"))
        dao.deleteContaining("a")

        assertEquals(1, garmentDao.delete("a"))
    }

    @Test
    fun setFavoriteUpdatesOutfit() = runTest {
        seedGarments("a")
        dao.insert(outfit("o1"), crossRefs("o1", "a"))

        dao.setFavorite("o1", true)

        assertEquals(true, dao.getWithGarments("o1")!!.outfit.isFavorite)
    }
}
