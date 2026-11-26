package com.vesteai.feature.fittingroom

import com.vesteai.core.error.AppError
import com.vesteai.core.media.ImageStore
import com.vesteai.core.result.DataResult
import com.vesteai.domain.model.Garment
import com.vesteai.domain.model.GarmentCategory
import com.vesteai.domain.model.Outfit
import com.vesteai.domain.repository.JobRepository
import com.vesteai.domain.repository.LookbookRepository
import com.vesteai.domain.repository.ProfileRepository
import com.vesteai.domain.repository.WardrobeRepository
import com.vesteai.domain.vton.JobStatus
import com.vesteai.domain.vton.PendingJob
import com.vesteai.domain.vton.AspectRequirement
import com.vesteai.domain.vton.VtonCapabilities
import com.vesteai.domain.vton.VtonEngine
import com.vesteai.domain.vton.VtonError
import com.vesteai.domain.vton.VtonException
import com.vesteai.domain.vton.VtonPollResult
import com.vesteai.domain.vton.VtonProgress
import com.vesteai.domain.vton.VtonRequest
import com.vesteai.domain.vton.VtonResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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

        override suspend fun reset() = Unit
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

    private class FakeJobRepository : JobRepository {
        val jobsFlow = MutableStateFlow<List<PendingJob>>(emptyList())

        override fun observeActive(): Flow<List<PendingJob>> =
            jobsFlow.map { jobs -> jobs.filter { it.isActive } }

        override fun observeAll(): Flow<List<PendingJob>> = jobsFlow

        override suspend fun save(job: PendingJob): DataResult<PendingJob> {
            jobsFlow.value = jobsFlow.value + job
            return DataResult.Success(job)
        }

        override suspend fun getById(id: String): PendingJob? = jobsFlow.value.firstOrNull { it.id == id }

        override suspend fun updateProviderJobId(id: String, providerJobId: String) = Unit

        override suspend fun transitionToProcessing(id: String) = Unit

        override suspend fun complete(id: String, resultImagePath: String) = Unit

        override suspend fun fail(id: String, error: VtonError, guidance: String?) = Unit

        override suspend fun delete(id: String) {
            jobsFlow.value = jobsFlow.value.filterNot { it.id == id }
        }
    }

    private class FakeEngine(
        override val capabilities: VtonCapabilities = CAPABILITIES,
    ) : VtonEngine {
        override val displayName: String = "fake"

        val progressFlow = MutableSharedFlow<VtonProgress>(replay = 1, extraBufferCapacity = 8)
        override val progress: Flow<VtonProgress> = progressFlow

        var outcome: DataResult<VtonResult> = DataResult.Success(VtonResult(RESULT_BYTES))
        var gate: CompletableDeferred<Unit>? = null
        var renderCalls = 0
        var lastRequest: VtonRequest? = null

        override suspend fun submit(request: VtonRequest): DataResult<String> =
            DataResult.Failure(VtonException(VtonError.UnsupportedCombination))

        override suspend fun poll(jobId: String): DataResult<VtonPollResult?> =
            DataResult.Failure(VtonException(VtonError.UnsupportedCombination))

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
    private val jobRepo = FakeJobRepository()

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
            jobRepo = jobRepo,
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

        assertEquals(TryOnStage.Completed, vm.uiState.value.tryOnStage)
        // A synchronous render auto-saves, exactly like an async job the poller completes.
        assertNotNull(lookbook.savedPath)
        assertTrue(vm.uiState.value.savedConfirmation)
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
        assertNull(lookbook.savedPath)
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
    fun aHungEngineStillReportedAsInFlightWellPastTheOldThreeMinuteLimit() = runTest(dispatcher) {
        engine.gate = CompletableDeferred()
        val vm = selectedViewModel()

        vm.tryOn()
        advanceTimeBy(300_000)

        assertTrue(vm.uiState.value.tryOnStage.isInFlight)
    }

    @Test
    fun aHungEngineTimesOutAfterTenMinutes() = runTest(dispatcher) {
        engine.gate = CompletableDeferred()
        val vm = selectedViewModel()

        vm.tryOn()
        advanceTimeBy(599_000)
        assertTrue(vm.uiState.value.tryOnStage.isInFlight)

        advanceTimeBy(2_000)
        advanceUntilIdle()

        assertEquals(TryOnStage.Failed(AppError.Timeout), vm.uiState.value.tryOnStage)
    }

    @Test
    fun theProvidersQueueMessageReachesTheUiWhileTheJobRuns() = runTest(dispatcher) {
        engine.gate = CompletableDeferred()
        val vm = selectedViewModel()

        vm.tryOn()
        engine.progressFlow.emit(
            VtonProgress(
                estimatedWaitSeconds = 340,
                queuePosition = 2,
                userMessage = "Preparing your result",
            ),
        )
        advanceUntilIdle()

        val queue = vm.uiState.value.queue
        assertEquals("Preparing your result", queue.userMessage)
        assertEquals(340L, queue.estimatedWaitSeconds)
        assertEquals(2, queue.queuePosition)
    }

    @Test
    fun startingOrCancellingAJobClearsAStaleQueueMessage() = runTest(dispatcher) {
        engine.gate = CompletableDeferred()
        val vm = selectedViewModel()
        vm.tryOn()
        engine.progressFlow.emit(VtonProgress(userMessage = "Preparing your result"))
        advanceUntilIdle()

        vm.cancelTryOn()

        assertTrue(vm.uiState.value.queue.isEmpty)
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
    fun aSynchronousRenderAutoSavesToTheLookbook() = runTest(dispatcher) {
        val vm = selectedViewModel()

        vm.tryOn()
        advanceUntilIdle()

        assertNotNull(lookbook.savedPath)
        assertTrue(vm.uiState.value.savedConfirmation)
    }

    @Test
    fun aFailedLookbookWriteSurfacesAsAFailure() = runTest(dispatcher) {
        lookbook.failing = true
        val vm = selectedViewModel()

        vm.tryOn()
        advanceUntilIdle()

        assertEquals(TryOnStage.Failed(AppError.LocalMedia), vm.uiState.value.tryOnStage)
        assertNull(lookbook.savedPath)
    }

    @Test
    fun dismissingTheSavedConfirmationClearsIt() = runTest(dispatcher) {
        val vm = selectedViewModel()
        vm.tryOn()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.savedConfirmation)

        vm.dismissSavedConfirmation()

        assertFalse(vm.uiState.value.savedConfirmation)
    }

    @Test
    fun selectTabSwitchesTheVisiblePane() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(FittingRoomTab.TryOn, vm.uiState.value.tab)
        vm.selectTab(FittingRoomTab.Ongoing)
        assertEquals(FittingRoomTab.Ongoing, vm.uiState.value.tab)
    }

    @Test
    fun ongoingJobsComeFromTheJobRepository() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        jobRepo.jobsFlow.value = listOf(
            PendingJob(
                id = "j1",
                providerName = "PixelAPI",
                garmentIds = listOf("t1"),
                baselineImagePath = ImageStore.BASELINE_PATH,
                submittedAt = 0L,
                status = JobStatus.Processing,
            ),
        )
        advanceUntilIdle()

        assertEquals(listOf("j1"), vm.uiState.value.ongoingJobs.map { it.id })
        assertEquals(1, vm.uiState.value.activeJobCount)
    }

    @Test
    fun cancelJobRemovesItFromTheRepository() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        val job = PendingJob(
            id = "j1",
            providerName = "PixelAPI",
            garmentIds = listOf("t1"),
            baselineImagePath = ImageStore.BASELINE_PATH,
            submittedAt = 0L,
            status = JobStatus.Processing,
        )
        jobRepo.jobsFlow.value = listOf(job)
        advanceUntilIdle()

        vm.cancelJob(job)
        advanceUntilIdle()

        // The fake records the delete by dropping the row; a real repo would too.
        assertTrue(vm.uiState.value.ongoingJobs.isEmpty())
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


    @Test
    fun preselectSelectsTheMatchingGarments() = runTest(dispatcher) {
        wardrobe.garments.value = listOf(top, bottom)
        val vm = viewModel()
        advanceUntilIdle()

        vm.preselect(listOf("t1", "b1"))
        advanceUntilIdle()

        val selected = vm.uiState.value.selection.selected.values.map { it.id }.toSet()
        assertEquals(setOf("t1", "b1"), selected)
    }

    @Test
    fun preselectIgnoresIdsThatNoLongerExist() = runTest(dispatcher) {
        wardrobe.garments.value = listOf(top)
        val vm = viewModel()
        advanceUntilIdle()

        vm.preselect(listOf("t1", "deleted-garment"))
        advanceUntilIdle()

        val selected = vm.uiState.value.selection.selected.values.map { it.id }.toSet()
        assertEquals(setOf("t1"), selected)
    }

    @Test
    fun preselectOfAFullBodyLookClearsTopAndBottom() = runTest(dispatcher) {
        val dress = garment("f1", GarmentCategory.FULL_BODY)
        wardrobe.garments.value = listOf(top, bottom, dress)
        val vm = viewModel()
        advanceUntilIdle()
        vm.toggle(top)
        vm.toggle(bottom)

        vm.preselect(listOf("f1"))
        advanceUntilIdle()

        val selected = vm.uiState.value.selection.selected.values.map { it.id }.toSet()
        assertEquals(setOf("f1"), selected)
    }

    @Test
    fun preselectWithNoIdsLeavesTheSelectionAlone() = runTest(dispatcher) {
        wardrobe.garments.value = listOf(top)
        val vm = viewModel()
        advanceUntilIdle()
        vm.toggle(top)
        advanceUntilIdle()

        vm.preselect(emptyList())
        advanceUntilIdle()

        val selected = vm.uiState.value.selection.selected.values.map { it.id }.toSet()
        assertEquals(setOf("t1"), selected)
    }

    private companion object {
        val FIXTURE_BYTES = byteArrayOf(9, 9, 9)
    }
}
