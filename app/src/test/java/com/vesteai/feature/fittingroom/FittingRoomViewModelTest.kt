package com.vesteai.feature.fittingroom

import com.vesteai.core.error.AppError
import com.vesteai.core.media.ImageStore
import com.vesteai.core.result.DataResult
import com.vesteai.domain.model.Garment
import com.vesteai.domain.model.GarmentCategory
import com.vesteai.domain.model.Outfit
import com.vesteai.domain.repository.LookbookRepository
import com.vesteai.domain.repository.ProfileRepository
import com.vesteai.domain.repository.WardrobeRepository
import com.vesteai.domain.vton.AspectRequirement
import com.vesteai.domain.vton.VtonCapabilities
import com.vesteai.domain.vton.VtonEngine
import com.vesteai.domain.vton.VtonError
import com.vesteai.domain.vton.VtonException
import com.vesteai.domain.vton.VtonRequest
import com.vesteai.domain.vton.VtonResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class FittingRoomViewModelTest {

    private class FakeWardrobe : WardrobeRepository {
        val garments = MutableStateFlow<List<Garment>>(emptyList())

        override fun observeAll(): Flow<List<Garment>> = garments

        override fun observeByCategory(category: GarmentCategory): Flow<List<Garment>> =
            garments.map { list -> list.filter { it.category == category } }

        override suspend fun getByIds(ids: Collection<String>): List<Garment> = emptyList()

        override suspend fun add(
            imagePath: String,
            category: GarmentCategory,
            displayName: String?,
        ): Garment = error("not used")

        override suspend fun setFavorite(id: String, isFavorite: Boolean) = Unit

        override suspend fun countLookbooksContaining(garmentId: String): Int = 0

        override suspend fun deleteGarment(garmentId: String): DataResult<Unit> =
            DataResult.Success(Unit)
    }

    private class FakeProfile(private val baseline: Boolean) : ProfileRepository {
        override fun observeOnboardingComplete(): Flow<Boolean> = MutableStateFlow(true)

        override suspend fun completeOnboarding() = Unit

        override fun observeBaselinePresent(): Flow<Boolean> = MutableStateFlow(baseline)

        override fun observeBaselineSavedAtMillis(): Flow<Long?> = MutableStateFlow(0L)

        override suspend fun markBaselineSaved(savedAtMillis: Long) = Unit
    }

    private class FakeLookbook : LookbookRepository {
        var savedPath: String? = null
        var failing = false

        override fun observeAll(): Flow<List<Outfit>> = MutableStateFlow(emptyList())

        override suspend fun getById(id: String): Outfit? = null

        override suspend fun save(
            resultImagePath: String,
            garmentIds: Collection<String>,
        ): DataResult<Outfit> {
            if (failing) return DataResult.Failure(IllegalStateException("db down"))
            savedPath = resultImagePath
            return DataResult.Success(
                Outfit(
                    id = "o1",
                    resultImagePath = resultImagePath,
                    createdAt = 0L,
                    isFavorite = false,
                    garments = emptyList(),
                ),
            )
        }

        override suspend fun setFavorite(id: String, isFavorite: Boolean) = Unit

        override suspend fun delete(id: String) = Unit
    }

    private class FakeEngine(
        override val capabilities: VtonCapabilities = CAPABILITIES,
    ) : VtonEngine {
        override val displayName: String = "fake"

        var outcome: DataResult<VtonResult> = DataResult.Success(VtonResult(RESULT_BYTES))
        var gate: CompletableDeferred<Unit>? = null
        var renderCalls = 0
        var lastRequest: VtonRequest? = null

        override suspend fun render(request: VtonRequest): DataResult<VtonResult> {
            renderCalls++
            lastRequest = request
            gate?.await()
            return outcome
        }

        companion object {
            val RESULT_BYTES = byteArrayOf(1, 2, 3, 4)

            val CAPABILITIES = VtonCapabilities(
                supportedCategories = setOf(
                    GarmentCategory.TOP,
                    GarmentCategory.BOTTOM,
                    GarmentCategory.FULL_BODY,
                ),
                supportsTopBottomOutfit = true,
                maxParallelJobs = 3,
                requiredAspect = AspectRequirement.Unconstrained,
            )
        }
    }

    /** Reads elapsed time from the test scheduler, so timers never touch the wall clock. */
    private class SchedulerClock(private val dispatcher: TestDispatcher) : Clock() {
        override fun getZone(): ZoneOffset = ZoneOffset.UTC

        override fun withZone(zone: ZoneId): Clock = this

        override fun instant(): Instant = Instant.ofEpochMilli(dispatcher.scheduler.currentTime)

        override fun millis(): Long = dispatcher.scheduler.currentTime
    }

    @get:Rule
    val tmp = TemporaryFolder()

    private val dispatcher = StandardTestDispatcher()
    private lateinit var wardrobe: FakeWardrobe
    private lateinit var lookbook: FakeLookbook
    private lateinit var engine: FakeEngine
    private lateinit var imageStore: ImageStore

    private val top = garment("t1", GarmentCategory.TOP)
    private val bottom = garment("b1", GarmentCategory.BOTTOM)
    private val hat = garment("h1", GarmentCategory.HEADWEAR)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        wardrobe = FakeWardrobe()
        lookbook = FakeLookbook()
        engine = FakeEngine()
        imageStore = ImageStore(tmp.root)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun garment(id: String, category: GarmentCategory) = Garment(
        id = id,
        imagePath = "wardrobe/$id.jpg",
        category = category,
        createdAt = 1L,
        isFavorite = false,
    )

    private fun writeMedia(vararg relativePaths: String) {
        relativePaths.forEach { relative ->
            val file = imageStore.resolve(relative)
            file.parentFile?.mkdirs()
            file.writeBytes(FIXTURE_BYTES)
        }
    }

    private fun viewModel(baselinePresent: Boolean = true): FittingRoomViewModel {
        if (baselinePresent) writeMedia(ImageStore.BASELINE_PATH)
        writeMedia("wardrobe/t1.jpg", "wardrobe/b1.jpg", "wardrobe/h1.jpg")
        return FittingRoomViewModel(
            wardrobe = wardrobe,
            lookbook = lookbook,
            profile = FakeProfile(baselinePresent),
            imageStore = imageStore,
            vtonEngine = engine,
            clock = SchedulerClock(dispatcher),
            ioDispatcher = dispatcher,
        )
    }

    /** Loads the listing and returns a view model with [top] selected. */
    private fun kotlinx.coroutines.test.TestScope.selectedViewModel(
        baselinePresent: Boolean = true,
    ): FittingRoomViewModel {
        wardrobe.garments.value = listOf(top)
        val vm = viewModel(baselinePresent)
        advanceUntilIdle()
        vm.toggle(top)
        advanceUntilIdle()
        return vm
    }

    @Test
    fun exposesCapabilityGatingWithoutAHardcodedList() = runTest(dispatcher) {
        wardrobe.garments.value = listOf(top, bottom, hat)
        val vm = viewModel()

        advanceUntilIdle()
        val state = vm.uiState.value

        assertFalse(state.isSupported(GarmentCategory.HEADWEAR))
        assertFalse(state.isSupported(GarmentCategory.FOOTWEAR))
        assertTrue(state.isSupported(GarmentCategory.TOP))
        assertEquals(
            setOf(GarmentCategory.HEADWEAR, GarmentCategory.FOOTWEAR),
            state.unsupportedCategories,
        )
    }

    @Test
    fun supportsEveryCategoryTheEngineAdvertises() = runTest(dispatcher) {
        engine = FakeEngine(
            capabilities = FakeEngine.CAPABILITIES.copy(
                supportedCategories = GarmentCategory.entries.toSet(),
            ),
        )
        val vm = viewModel()

        advanceUntilIdle()

        assertTrue(vm.uiState.value.unsupportedCategories.isEmpty())
    }

    @Test
    fun tryOnIsBlockedUntilSomethingIsSelected() = runTest(dispatcher) {
        wardrobe.garments.value = listOf(top, bottom)
        val vm = viewModel()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.canTryOn)

        vm.toggle(top)
        advanceUntilIdle()

        assertTrue(vm.uiState.value.canTryOn)
    }

    @Test
    fun selectionFollowsTheDomainRulesForFullBody() = runTest(dispatcher) {
        val dress = garment("f1", GarmentCategory.FULL_BODY)
        wardrobe.garments.value = listOf(top, bottom, hat, dress)
        val vm = viewModel()
        advanceUntilIdle()

        vm.toggle(top)
        vm.toggle(bottom)
        vm.toggle(dress)
        advanceUntilIdle()

        assertEquals(setOf(GarmentCategory.FULL_BODY), vm.uiState.value.selection.selected.keys)
    }

    @Test
    fun successfulTryOnReportsQueuedThenProcessingThenCompleted() = runTest(dispatcher) {
        engine.gate = CompletableDeferred()
        val vm = selectedViewModel()

        vm.tryOn()
        assertEquals(TryOnStage.Queued, vm.uiState.value.tryOnStage)

        advanceTimeBy(1)
        assertEquals(TryOnStage.Processing, vm.uiState.value.tryOnStage)

        engine.gate?.complete(Unit)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(TryOnStage.Completed, state.tryOnStage)
        assertEquals(
            FakeEngine.RESULT_BYTES.toList(),
            vm.imageFile(state.result!!.relativePath).readBytes().toList(),
        )
        assertEquals(setOf("t1"), state.result!!.garmentIds)
    }

    @Test
    fun elapsedTimerAdvancesWhileTheJobRuns() = runTest(dispatcher) {
        engine.gate = CompletableDeferred()
        val vm = selectedViewModel()

        vm.tryOn()
        advanceTimeBy(3_001)

        assertEquals(3L, vm.uiState.value.elapsedSeconds)
    }

    @Test
    fun engineFailureMapsToAnAppErrorAndLeavesNoResult() = runTest(dispatcher) {
        engine.outcome = DataResult.Failure(VtonException(VtonError.BodyNotDetected))
        val vm = selectedViewModel()

        vm.tryOn()
        advanceUntilIdle()

        assertEquals(TryOnStage.Failed(AppError.BodyNotDetected), vm.uiState.value.tryOnStage)
        assertEquals(null, vm.uiState.value.result)
    }

    @Test
    fun providerGuidanceSurvivesTheMapping() = runTest(dispatcher) {
        engine.outcome = DataResult.Failure(
            VtonException(VtonError.TooManyConcurrentJobs("Wait for a job to finish")),
        )
        val vm = selectedViewModel()

        vm.tryOn()
        advanceUntilIdle()

        assertEquals(
            TryOnStage.Failed(AppError.TooManyConcurrentJobs("Wait for a job to finish")),
            vm.uiState.value.tryOnStage,
        )
    }

    @Test
    fun cancelReturnsToIdleAndStopsTheEngine() = runTest(dispatcher) {
        engine.gate = CompletableDeferred()
        val vm = selectedViewModel()
        vm.tryOn()
        advanceTimeBy(1)

        vm.cancelTryOn()
        advanceUntilIdle()

        assertEquals(TryOnStage.Idle, vm.uiState.value.tryOnStage)
        assertEquals(0L, vm.uiState.value.elapsedSeconds)
        engine.gate?.complete(Unit)
        advanceUntilIdle()
        assertEquals(TryOnStage.Idle, vm.uiState.value.tryOnStage)
        assertEquals(1, engine.renderCalls)
    }

    @Test
    fun aHungEngineTimesOutAfterThreeMinutes() = runTest(dispatcher) {
        engine.gate = CompletableDeferred()
        val vm = selectedViewModel()

        vm.tryOn()
        advanceTimeBy(179_000)
        assertTrue(vm.uiState.value.tryOnStage.isInFlight)

        advanceTimeBy(2_000)
        advanceUntilIdle()

        assertEquals(TryOnStage.Failed(AppError.Timeout), vm.uiState.value.tryOnStage)
    }

    @Test
    fun tryOnIsDisabledWhileAJobIsInFlight() = runTest(dispatcher) {
        engine.gate = CompletableDeferred()
        val vm = selectedViewModel()

        vm.tryOn()
        advanceTimeBy(1)
        assertTrue(vm.uiState.value.tryOnStage.isInFlight)
        assertFalse(vm.uiState.value.canTryOn)

        vm.tryOn()

        assertEquals(1, engine.renderCalls)
    }

    @Test
    fun missingBaselineBlocksTryOn() = runTest(dispatcher) {
        val vm = selectedViewModel(baselinePresent = false)

        assertFalse(vm.uiState.value.baselinePresent)
        assertFalse(vm.uiState.value.canTryOn)
    }

    @Test
    fun saveToLookbookPersistsTheRenderedFile() = runTest(dispatcher) {
        val vm = selectedViewModel()
        vm.tryOn()
        advanceUntilIdle()
        val relativePath = vm.uiState.value.result!!.relativePath

        vm.saveToLookbook()
        advanceUntilIdle()

        assertEquals(relativePath, lookbook.savedPath)
        assertTrue(vm.uiState.value.result!!.saved)
    }

    @Test
    fun failedSaveKeepsTheSheetOpenAndDeletesTheTempFile() = runTest(dispatcher) {
        lookbook.failing = true
        val vm = selectedViewModel()
        vm.tryOn()
        advanceUntilIdle()
        val resultFile = vm.imageFile(vm.uiState.value.result!!.relativePath)

        vm.saveToLookbook()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.result!!.saveFailed)
        assertFalse(resultFile.exists())
    }

    @Test
    fun discardDeletesTheTempResultFile() = runTest(dispatcher) {
        val vm = selectedViewModel()
        vm.tryOn()
        advanceUntilIdle()
        val resultFile = vm.imageFile(vm.uiState.value.result!!.relativePath)
        assertTrue(resultFile.exists())

        vm.discardResult()
        advanceUntilIdle()

        assertFalse(resultFile.exists())
        assertEquals(null, vm.uiState.value.result)
        assertEquals(TryOnStage.Idle, vm.uiState.value.tryOnStage)
    }

    @Test
    fun aSecondTryOnDropsThePreviousUnsavedResult() = runTest(dispatcher) {
        val vm = selectedViewModel()
        vm.tryOn()
        advanceUntilIdle()
        val firstFile = vm.imageFile(vm.uiState.value.result!!.relativePath)

        engine.gate = CompletableDeferred()
        vm.tryOn()
        advanceTimeBy(1)

        assertFalse(firstFile.exists())
        assertEquals(TryOnStage.Processing, vm.uiState.value.tryOnStage)
    }

    @Test
    fun deletedGarmentsLeaveTheSelection() = runTest(dispatcher) {
        wardrobe.garments.value = listOf(top, bottom)
        val vm = viewModel()
        advanceUntilIdle()
        vm.toggle(top)
        vm.toggle(bottom)
        advanceUntilIdle()

        wardrobe.garments.value = listOf(top)
        advanceUntilIdle()

        assertEquals(setOf(GarmentCategory.TOP), vm.uiState.value.selection.selected.keys)
    }

    @Test
    fun requestCarriesTheBaselineAndGarmentsKeyedByCategory() = runTest(dispatcher) {
        wardrobe.garments.value = listOf(top, bottom)
        val vm = viewModel()
        advanceUntilIdle()
        vm.toggle(top)
        vm.toggle(bottom)
        advanceUntilIdle()

        vm.tryOn()
        advanceUntilIdle()

        val request = engine.lastRequest!!
        assertEquals(setOf(GarmentCategory.TOP, GarmentCategory.BOTTOM), request.garments.keys)
        assertEquals(FIXTURE_BYTES.toList(), request.baselineImage.toList())
    }

    private companion object {
        val FIXTURE_BYTES = byteArrayOf(9, 9, 9)
    }
}
