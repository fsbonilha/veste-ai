package com.vesteai.data.repository

import android.database.sqlite.SQLiteConstraintException
import com.vesteai.core.media.ImageStore
import com.vesteai.core.result.DataResult
import com.vesteai.data.db.garment
import com.vesteai.data.db.inMemoryDatabase
import com.vesteai.domain.model.Outfit
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
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class RoomLookbookRepositoryTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val db = inMemoryDatabase()
    private val dispatcher = UnconfinedTestDispatcher()
    private val clock = Clock.fixed(Instant.ofEpochMilli(1_700_000_000_000L), ZoneOffset.UTC)
    private val imageStore by lazy { ImageStore(tmp.root) }
    private val repository by lazy {
        RoomLookbookRepository(db.outfitDao(), imageStore, clock, dispatcher)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun saveReturnsOutfitWithGarmentsAndPinnedTimestamp() = runTest(dispatcher) {
        db.garmentDao().upsert(garment("top"))

        val saved = repository.save("looks/l.jpg", listOf("top")) as DataResult.Success

        assertEquals(
            Outfit(
                id = saved.value.id,
                resultImagePath = "looks/l.jpg",
                createdAt = 1_700_000_000_000L,
                isFavorite = false,
                garments = listOf(garment("top").toDomain()),
            ),
            saved.value,
        )
    }

    @Test
    fun saveWithDeletedGarmentFailsAndPersistsNothing() = runTest(dispatcher) {
        val result = repository.save("looks/l.jpg", listOf("ghost")) as DataResult.Failure

        assertTrue(result.error is SQLiteConstraintException)
        assertEquals(emptyList<Outfit>(), repository.observeAll().first())
    }

    @Test
    fun saveWithNoGarmentsFails() = runTest(dispatcher) {
        val result = repository.save("looks/l.jpg", emptyList()) as DataResult.Failure

        assertTrue(result.error is IllegalArgumentException)
    }

    @Test
    fun deleteRemovesRowAndResultImage() = runTest(dispatcher) {
        db.garmentDao().upsert(garment("top"))
        val image = imageStore.resolve("looks/l.jpg").apply { parentFile!!.mkdirs(); writeText("jpg") }
        val saved = repository.save("looks/l.jpg", listOf("top")) as DataResult.Success

        repository.delete(saved.value.id)

        assertEquals(emptyList<Outfit>(), repository.observeAll().first())
        assertFalse(image.exists())
    }
}
