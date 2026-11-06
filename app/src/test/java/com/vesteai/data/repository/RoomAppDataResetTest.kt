package com.vesteai.data.repository

import androidx.room.Room
import com.vesteai.core.media.ImageStore
import com.vesteai.core.media.ImageStore.Companion.BASELINE_PATH
import com.vesteai.core.media.MediaKind
import com.vesteai.core.result.DataResult
import com.vesteai.data.db.VesteDatabase
import com.vesteai.data.db.crossRefs
import com.vesteai.data.db.garment
import com.vesteai.data.db.outfit
import com.vesteai.data.prefs.DataStoreProfileRepository
import com.vesteai.data.vton.CredentialStoreHarness
import com.vesteai.domain.model.GarmentCategory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class RoomAppDataResetTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val dispatcher = UnconfinedTestDispatcher()
    private val scope = CoroutineScope(dispatcher + SupervisorJob())
    private lateinit var database: VesteDatabase
    private lateinit var harness: CredentialStoreHarness
    private lateinit var imageStore: ImageStore
    private lateinit var profileRepository: DataStoreProfileRepository
    private lateinit var reset: RoomAppDataReset

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            VesteDatabase::class.java,
        ).allowMainThreadQueries().build()
        harness = CredentialStoreHarness(tmp.root)
        imageStore = ImageStore(tmp.newFolder("media"))
        profileRepository = DataStoreProfileRepository(
            androidx.datastore.preferences.core.PreferenceDataStoreFactory.create(
                scope = scope,
                produceFile = { File(tmp.root, "profile.preferences_pb") },
            ),
        )
        reset = RoomAppDataReset(
            database = database,
            imageStore = imageStore,
            profileRepository = profileRepository,
            credentialStore = harness.store,
            ioDispatcher = dispatcher,
        )
    }

    @After
    fun tearDown() {
        database.close()
        harness.close()
        scope.cancel()
    }

    private suspend fun seed() {
        database.garmentDao().upsert(garment(id = "g1", category = GarmentCategory.TOP))
        database.outfitDao().insert(outfit(id = "o1"), crossRefs("o1", "g1"))
        listOf("wardrobe/g1.jpg", "looks/o1.jpg", BASELINE_PATH).forEach { path ->
            imageStore.resolve(path).also { it.parentFile?.mkdirs() }.writeText("bytes")
        }
        harness.store.save("pk_live_test12345678", "https://api.pixelapi.dev")
        profileRepository.completeOnboarding()
        profileRepository.markBaselineSaved(1_000L)
    }

    @Test
    fun clearAllRemovesRowsImagesFlagsAndTheKey() = runTest(dispatcher) {
        seed()

        assertEquals(DataResult.Success(Unit), reset.clearAll())

        assertTrue(database.garmentDao().getByIds(listOf("g1")).isEmpty())
        assertNull(database.outfitDao().getWithGarments("o1"))
        assertFalse(imageStore.resolve("wardrobe/g1.jpg").exists())
        assertFalse(imageStore.resolve(BASELINE_PATH).exists())
        assertFalse(profileRepository.observeOnboardingComplete().first())
        assertFalse(profileRepository.observeBaselinePresent().first())
        assertNull(harness.store.read())
    }

    @Test
    fun clearAllIsSafeOnAnEmptyInstall() = runTest(dispatcher) {
        assertEquals(DataResult.Success(Unit), reset.clearAll())
    }

    @Test
    fun clearAllLeavesFilesItDoesNotOwn() = runTest(dispatcher) {
        val foreign = File(tmp.root, "unrelated.txt").apply { writeText("keep me") }

        reset.clearAll()

        assertTrue(foreign.exists())
    }

}