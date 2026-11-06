package com.vesteai.domain.usecase

import com.vesteai.core.media.ImageStore
import com.vesteai.core.result.DataResult
import com.vesteai.data.db.VesteDatabase
import com.vesteai.data.db.crossRefs
import com.vesteai.data.db.garment
import com.vesteai.data.db.inMemoryDatabase
import com.vesteai.data.db.outfit
import com.vesteai.data.repository.RoomLookbookRepository
import com.vesteai.data.repository.RoomWardrobeRepository
import com.vesteai.domain.model.Garment
import com.vesteai.domain.model.GarmentCategory
import com.vesteai.domain.model.Outfit
import com.vesteai.domain.repository.LookbookRepository
import com.vesteai.domain.repository.WardrobeRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
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
class OrphanFileSweepUseCaseTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val db: VesteDatabase = inMemoryDatabase()
    private val dispatcher = UnconfinedTestDispatcher()
    private val imageStore by lazy { ImageStore(tmp.root) }
    private val clock = Clock.fixed(Instant.ofEpochMilli(NOW), ZoneOffset.UTC)
    private val wardrobe by lazy {
        RoomWardrobeRepository(db, imageStore, clock, dispatcher)
    }
    private val lookbook by lazy {
        RoomLookbookRepository(db.outfitDao(), imageStore, clock, dispatcher)
    }

    @After
    fun tearDown() = db.close()

    private fun sweep(): OrphanFileSweepUseCase =
        OrphanFileSweepUseCase(wardrobe, lookbook, imageStore, clock)

    private fun touch(relative: String, ageMillis: Long = 0L): File =
        imageStore.resolve(relative).apply {
            parentFile?.mkdirs()
            writeText("bytes")
            setLastModified(NOW - ageMillis)
        }

    private suspend fun seedGarment(id: String) = db.garmentDao().upsert(garment(id))

    private suspend fun seedOutfit(id: String, vararg garmentIds: String) =
        db.outfitDao().insert(outfit(id), crossRefs(id, *garmentIds))

    @Test
    fun deletesWardrobeFileThatNoGarmentReferences() = runTest(dispatcher) {
        seedGarment("g1")
        val referenced = touch("wardrobe/g1.jpg")
        val orphan = touch("wardrobe/orphan.jpg")

        val report = sweep().sweep()

        assertTrue(referenced.exists())
        assertFalse(orphan.exists())
        assertEquals(1, report.deletedFiles)
    }

    @Test
    fun keepsEveryWardrobeFileThatAGarmentReferences() = runTest(dispatcher) {
        seedGarment("g1")
        seedGarment("g2")
        touch("wardrobe/g1.jpg")
        touch("wardrobe/g2.jpg")

        val report = sweep().sweep()

        assertTrue(imageStore.resolve("wardrobe/g1.jpg").exists())
        assertTrue(imageStore.resolve("wardrobe/g2.jpg").exists())
        assertEquals(0, report.deletedFiles)
    }

    @Test
    fun deletesLookFileThatNoOutfitReferences() = runTest(dispatcher) {
        seedGarment("g1")
        seedOutfit("o1", "g1")
        val orphan = touch("looks/orphan.jpg")

        val report = sweep().sweep()

        assertFalse(orphan.exists())
        assertEquals(1, report.deletedFiles)
    }

    @Test
    fun neverDeletesAFileReferencedByAnOutfit() = runTest(dispatcher) {
        seedGarment("g1")
        seedGarment("g2")
        seedOutfit("o1", "g1", "g2")
        touch("looks/o1.jpg")
        touch("wardrobe/g1.jpg")
        touch("wardrobe/g2.jpg")

        val report = sweep().sweep()

        assertTrue(imageStore.resolve("looks/o1.jpg").exists())
        assertEquals(0, report.deletedFiles)
    }

    @Test
    fun deletesTempFileOlderThanAnHour() = runTest(dispatcher) {
        val stale = touch("wardrobe/stale.jpg.tmp", ageMillis = 2 * HOUR)

        val report = sweep().sweep()

        assertFalse(stale.exists())
        assertEquals(1, report.deletedFiles)
    }

    @Test
    fun keepsTempFileFromAWriteThatMayStillBeRunning() = runTest(dispatcher) {
        val fresh = touch("wardrobe/fresh.jpg.tmp", ageMillis = 10 * MINUTE)

        val report = sweep().sweep()

        assertTrue(fresh.exists())
        assertEquals(0, report.deletedFiles)
    }

    @Test
    fun keepsTempFileOfAReferencedImage() = runTest(dispatcher) {
        seedGarment("g1")
        touch("wardrobe/g1.jpg")
        val fresh = touch("wardrobe/g1.jpg.tmp", ageMillis = 10 * MINUTE)

        sweep().sweep()

        assertTrue(fresh.exists())
    }

    @Test
    fun deletesStaleTempFileUnderTheLooksTree() = runTest(dispatcher) {
        val stale = touch("looks/o1.jpg.tmp", ageMillis = 2 * HOUR)

        val report = sweep().sweep()

        assertFalse(stale.exists())
        assertEquals(1, report.deletedFiles)
    }

    @Test
    fun neverDeletesTheBaselinePhoto() = runTest(dispatcher) {
        val baseline = touch(ImageStore.BASELINE_PATH)

        sweep().sweep()

        assertTrue(baseline.exists())
    }

    @Test
    fun emptyStoreIsANoOp() = runTest(dispatcher) {
        val report = sweep().sweep()

        assertEquals(0, report.deletedFiles)
        assertTrue(tmp.root.listFiles().isNullOrEmpty())
    }

    @Test
    fun missingMediaDirectoriesAreANoOp() = runTest(dispatcher) {
        val missingRoot = File(tmp.root, "not-created")
        val store = ImageStore(missingRoot)

        val report = OrphanFileSweepUseCase(
            wardrobe,
            lookbook,
            store,
            clock,
        ).sweep()

        assertEquals(0, report.deletedFiles)
        assertFalse(missingRoot.exists())
    }

    @Test
    fun leavesARowWhoseImageIsMissing() = runTest(dispatcher) {
        seedGarment("g1")

        val report = sweep().sweep()

        assertEquals(listOf("g1"), db.garmentDao().observeAll().first().map { it.id })
        assertEquals(0, report.deletedFiles)
    }

    @Test
    fun deletesNothingWhenTheRowsCannotBeRead() = runTest(dispatcher) {
        val file = touch("wardrobe/orphan.jpg")
        val sweep = OrphanFileSweepUseCase(
            UnreadableWardrobe,
            UnreadableLookbook,
            imageStore,
            clock,
        )

        val report = sweep.sweep()

        assertTrue(file.exists())
        assertEquals(0, report.deletedFiles)
    }

    @Test
    fun cancellationStopsTheSweepWithoutDeletingAnything() = runTest(dispatcher) {
        val file = touch("wardrobe/orphan.jpg")
        val sweep = OrphanFileSweepUseCase(HangingWardrobe, UnreadableLookbook, imageStore, clock)

        val job = launch { sweep.sweep() }
        advanceUntilIdle()
        job.cancelAndJoin()

        assertTrue(job.isCancelled)
        assertTrue(file.exists())
    }

    private companion object {
        const val NOW = 1_700_000_000_000L
        const val MINUTE = 60_000L
        const val HOUR = 60 * MINUTE
    }

    /** A store whose read fails the way Room does when the database is closed under it. */
    private object UnreadableWardrobe : WardrobeRepository {
        override fun observeAll(): Flow<List<Garment>> =
            flow { throw IllegalStateException("database closed") }

        override fun observeByCategory(category: GarmentCategory): Flow<List<Garment>> = error("unused")

        override suspend fun getByIds(ids: Collection<String>): List<Garment> = error("unused")

        override suspend fun add(
            imagePath: String,
            category: GarmentCategory,
            displayName: String?,
        ): Garment = error("unused")

        override suspend fun setFavorite(id: String, isFavorite: Boolean) = error("unused")

        override suspend fun countLookbooksContaining(garmentId: String): Int = error("unused")

        override suspend fun deleteGarment(garmentId: String): DataResult<Unit> = error("unused")
    }

    private object HangingWardrobe : WardrobeRepository by UnreadableWardrobe {
        override fun observeAll(): Flow<List<Garment>> = flow { awaitCancellation() }
    }

    private object UnreadableLookbook : LookbookRepository {
        override fun observeAll(): Flow<List<Outfit>> =
            flow { throw IllegalStateException("database closed") }

        override suspend fun getById(id: String): Outfit? = error("unused")

        override suspend fun save(
            resultImagePath: String,
            garmentIds: Collection<String>,
        ): DataResult<Outfit> = error("unused")

        override suspend fun setFavorite(id: String, isFavorite: Boolean) = error("unused")

        override suspend fun delete(id: String) = error("unused")
    }
}
