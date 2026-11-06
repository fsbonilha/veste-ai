package com.vesteai.data.repository

import com.vesteai.core.media.ImageStore
import com.vesteai.core.result.DataResult
import com.vesteai.data.db.crossRefCount
import com.vesteai.data.db.crossRefs
import com.vesteai.data.db.garment
import com.vesteai.data.db.inMemoryDatabase
import com.vesteai.data.db.outfit
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class RoomWardrobeDeletionTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val db = inMemoryDatabase()
    private val dispatcher = UnconfinedTestDispatcher()
    private val imageStore by lazy { ImageStore(tmp.root) }
    private val clock = Clock.fixed(Instant.ofEpochMilli(1_700_000_000_000L), ZoneOffset.UTC)
    private val repository by lazy { RoomWardrobeRepository(db, imageStore, clock, dispatcher) }

    @After
    fun tearDown() = db.close()

    private suspend fun seed(vararg ids: String) =
        ids.forEach { db.garmentDao().upsert(garment(it)) }

    private fun touchFile(relative: String): File =
        imageStore.resolve(relative).apply {
            parentFile.mkdirs()
            writeText("bytes")
        }

    private suspend fun saveOutfit(id: String, vararg garmentIds: String) {
        db.outfitDao().insert(outfit(id), crossRefs(id, *garmentIds))
        touchFile("looks/$id.jpg")
    }

    @Test
    fun deleteWithoutLookbooksRemovesOnlyTheGarmentAndItsFile() = runTest(dispatcher) {
        seed("solo", "keep")
        val solo = touchFile("wardrobe/solo.jpg")

        val result = repository.deleteGarment("solo")

        assertEquals(DataResult.Success(Unit), result)
        assertEquals(listOf("keep"), db.garmentDao().observeAll().first().map { it.id })
        assertFalse(solo.exists())
    }

    @Test
    fun deleteRemovesTheSingleContainingOutfitAndItsFile() = runTest(dispatcher) {
        seed("g1", "other")
        saveOutfit("o1", "g1", "other")
        val look = imageStore.resolve("looks/o1.jpg")

        repository.deleteGarment("g1")

        assertTrue(db.outfitDao().observeAll().first().isEmpty())
        assertFalse(look.exists())
        assertEquals(0, db.crossRefCount())
    }

    @Test
    fun deleteRemovesEveryContainingOutfitAndKeepsUnrelatedOnes() = runTest(dispatcher) {
        seed("g1", "g2")
        saveOutfit("a", "g1")
        saveOutfit("b", "g1", "g2")
        saveOutfit("c", "g1")
        saveOutfit("unrelated", "g2")

        repository.deleteGarment("g1")

        assertEquals(listOf("unrelated"), db.outfitDao().observeAll().first().map { it.outfit.id })
        assertFalse(imageStore.resolve("looks/a.jpg").exists())
        assertFalse(imageStore.resolve("looks/b.jpg").exists())
        assertFalse(imageStore.resolve("looks/c.jpg").exists())
        assertTrue(imageStore.resolve("looks/unrelated.jpg").exists())
    }

    @Test
    fun staleCountCannotOrphanALookbookAddedAfterThePlanWasShown() = runTest(dispatcher) {
        seed("g1")
        saveOutfit("early", "g1")
        assertEquals(1, repository.countLookbooksContaining("g1"))
        saveOutfit("late", "g1")

        repository.deleteGarment("g1")

        assertTrue(db.outfitDao().observeAll().first().isEmpty())
        assertEquals(0, db.crossRefCount())
    }

    @Test
    fun failureMidTransactionRollsBackOutfitsGarmentAndFiles() = runTest(dispatcher) {
        seed("g1", "g2")
        saveOutfit("o1", "g1", "g2")
        val garmentFile = touchFile("wardrobe/g1.jpg")
        val look = imageStore.resolve("looks/o1.jpg")
        db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER fail_garment_delete BEFORE DELETE ON garment " +
                "BEGIN SELECT RAISE(ABORT, 'simulated failure'); END",
        )

        val result = repository.deleteGarment("g1")

        assertTrue(result is DataResult.Failure)
        assertEquals(listOf("o1"), db.outfitDao().observeAll().first().map { it.outfit.id })
        assertEquals(2, db.crossRefCount())
        assertEquals(setOf("g1", "g2"), db.garmentDao().observeAll().first().map { it.id }.toSet())
        assertTrue(garmentFile.exists())
        assertTrue(look.exists())
    }

    @Test
    fun deletingAnUnknownGarmentIsANoOp() = runTest(dispatcher) {
        seed("g1")
        saveOutfit("o1", "g1")

        val result = repository.deleteGarment("missing")

        assertEquals(DataResult.Success(Unit), result)
        assertEquals(1, db.outfitDao().observeAll().first().size)
        assertEquals(1, db.crossRefCount())
    }
}
