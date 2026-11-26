package com.vesteai.feature.fittingroom

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vesteai.core.di.IoDispatcher
import com.vesteai.core.error.AppError
import com.vesteai.core.error.toAppError
import com.vesteai.core.media.ImageStore
import com.vesteai.core.media.MediaKind
import com.vesteai.core.result.DataResult
import com.vesteai.domain.fitting.FittingRoomSelection
import com.vesteai.domain.model.Garment
import com.vesteai.domain.model.GarmentCategory
import com.vesteai.domain.repository.JobRepository
import com.vesteai.domain.repository.LookbookRepository
import com.vesteai.domain.repository.ProfileRepository
import com.vesteai.domain.repository.WardrobeRepository
import com.vesteai.domain.vton.JobStatus
import com.vesteai.domain.vton.PendingJob
import com.vesteai.domain.vton.VtonEngine
import com.vesteai.domain.vton.VtonProgress
import com.vesteai.domain.vton.VtonRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.IOException
import java.time.Clock
import java.util.UUID
import javax.inject.Inject

/** Try On stages reported by [VtonEngine]; `completed` also shows a transient confirmation. */
sealed interface TryOnStage {
    data object Idle : TryOnStage

    data object Queued : TryOnStage

    data object Processing : TryOnStage

    data object Completed : TryOnStage

    data class Failed(val error: AppError) : TryOnStage
}

val TryOnStage.isInFlight: Boolean
    get() = this == TryOnStage.Queued || this == TryOnStage.Processing

/** Which half of the fitting room is showing. */
enum class FittingRoomTab { TryOn, Ongoing }

data class FittingRoomUiState(
    val loading: Boolean = false,
    val loadFailed: Boolean = false,
    val baselinePresent: Boolean = false,
    val garmentsByCategory: Map<GarmentCategory, List<Garment>> = emptyMap(),
    val unsupportedCategories: Set<GarmentCategory> = emptySet(),
    val selection: FittingRoomSelection = FittingRoomSelection.Empty,
    val tab: FittingRoomTab = FittingRoomTab.TryOn,
    val tryOnStage: TryOnStage = TryOnStage.Idle,
    val elapsedSeconds: Long = 0,
    /** The provider's own queue reporting, when it has any (plan §7). */
    val queue: VtonProgress = VtonProgress(),
    /** In-progress and failed jobs, newest first; shown in the Ongoing tab. */
    val jobs: List<PendingJob> = emptyList(),
    /** A completed result was written to the Lookbook; the UI shows a brief confirmation. */
    val savedConfirmation: Boolean = false,
) {
    val hasGarments: Boolean get() = garmentsByCategory.values.any { it.isNotEmpty() }

    fun isSupported(category: GarmentCategory): Boolean = category !in unsupportedCategories

    val canTryOn: Boolean
        get() = baselinePresent && !tryOnStage.isInFlight && selection.selected.isNotEmpty()

    /**
     * Jobs the Ongoing tab lists. Every row in the repo belongs here: it is either still running,
     * failed, or a completed result whose lookbook write failed and can be retried.
     */
    val ongoingJobs: List<PendingJob>
        get() = jobs

    val activeJobCount: Int
        get() = jobs.count { it.isActive }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class FittingRoomViewModel @Inject constructor(
    private val wardrobe: WardrobeRepository,
    private val lookbook: LookbookRepository,
    private val profile: ProfileRepository,
    private val jobRepo: JobRepository,
    private val imageStore: ImageStore,
    private val vtonEngine: VtonEngine,
    private val clock: Clock,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    /**
     * Categories the active engine cannot render come from its own [VtonEngine.capabilities],
     * never from a list kept here, so swapping the engine regates the drawer for free.
     */
    private val unsupportedCategories: Set<GarmentCategory> =
        GarmentCategory.entries
            .filterNot { it in vtonEngine.capabilities.supportedCategories }
            .toSet()

    private val attempt = MutableStateFlow(0)
    private val _uiState = MutableStateFlow(
        FittingRoomUiState(loading = true, unsupportedCategories = unsupportedCategories),
    )
    val uiState: StateFlow<FittingRoomUiState> = _uiState.asStateFlow()

    private var renderJob: Job? = null

    /** The job the inline progress is watching, if any; used to tell success from a cancel. */
    private var watchedJobId: String? = null

    /** A job the user cancelled; its disappearance from the repo is not a success. */
    private var cancelledJobId: String? = null

    init {
        viewModelScope.launch {
            attempt
                .flatMapLatest {
                    combine(
                        wardrobe.observeAll(),
                        profile.observeBaselinePresent(),
                        ::loadGarments,
                    )
                        .flowOn(ioDispatcher)
                        .map<LoadedGarments, Load> { it }
                        .onStart { emit(Loading) }
                        .catch { emit(LoadFailed) }
                }
                .collect(::applyLoad)
        }
        viewModelScope.launch {
            jobRepo.observeAll().collect { jobs ->
                _uiState.update { it.copy(jobs = jobs) }
                // Drop the inline progress once the watched job is no longer in flight, so a
                // completed-and-saved job does not leave a stuck bar behind.
                val watched = jobs.firstOrNull { it.id == watchedJobId }
                if (watched == null && watchedJobId != null && watchedJobId != cancelledJobId) {
                    // Gone from the repo without a failure means the poller saved it.
                    _uiState.update {
                        it.copy(tryOnStage = TryOnStage.Completed, savedConfirmation = true, elapsedSeconds = 0)
                    }
                    watchedJobId = null
                } else if (watched?.status is JobStatus.Failed) {
                    _uiState.update { it.copy(tryOnStage = TryOnStage.Failed((watched.status as JobStatus.Failed).error.toAppError())) }
                    watchedJobId = null
                } else if (watched?.status is JobStatus.Completed) {
                    // Saved to the lookbook by the poller but the job row lingers only if the
                    // lookbook write failed; the Ongoing tab offers a retry save.
                    _uiState.update { it.copy(tryOnStage = TryOnStage.Idle, elapsedSeconds = 0) }
                    watchedJobId = null
                }
            }
        }
    }

    fun imageFile(relativePath: String): File = imageStore.resolve(relativePath)

    fun baselineFile(): File = imageStore.resolve(ImageStore.BASELINE_PATH)

    fun retry() {
        attempt.update { it + 1 }
    }

    fun selectTab(tab: FittingRoomTab) {
        _uiState.update { it.copy(tab = tab) }
    }

    fun dismissSavedConfirmation() {
        _uiState.update { it.copy(savedConfirmation = false) }
    }

    /** Delegates every selection rule to [FittingRoomSelection.toggle]; no branching here. */
    fun toggle(garment: Garment) {
        _uiState.update { it.copy(selection = it.selection.toggle(garment)) }
    }

    /**
     * Selects every wardrobe garment in [garmentIds], in one step, on the canvas. Used by the
     * lookbook's re-load action. Ids that no longer exist are ignored rather than failing the
     * whole re-load, and each toggle goes through [FittingRoomSelection.toggle] so the plan
     * section 6.3 rules (FULL_BODY vs TOP+BOTTOM) still decide the outcome.
     */
    fun preselect(garmentIds: Collection<String>) {
        if (garmentIds.isEmpty()) return
        val wanted = garmentIds.toSet()
        _uiState.update { state ->
            val known = state.garmentsByCategory.values.flatten().filter { it.id in wanted }
            state.copy(selection = known.fold(state.selection) { acc, garment -> acc.toggle(garment) })
        }
    }

    fun tryOn() {
        val snapshot = _uiState.value
        if (!snapshot.canTryOn || renderJob?.isActive == true) return

        // When the engine doesn't support async submit/poll, fall back to synchronous render.
        if (!vtonEngine.capabilities.isAsync) {
            syncTryOn()
            return
        }

        val garmentIds = snapshot.selection.selected.values.map { it.id }.toList()
        if (garmentIds.isEmpty()) return

        val jobId = UUID.randomUUID().toString()
        val pendingJob = PendingJob(
            id = jobId,
            providerName = vtonEngine.displayName,
            providerJobId = null,
            garmentIds = garmentIds,
            baselineImagePath = ImageStore.BASELINE_PATH,
            submittedAt = clock.millis(),
            status = JobStatus.Queued,
        )

        watchedJobId = jobId
        cancelledJobId = null
        _uiState.update {
            it.copy(
                tryOnStage = TryOnStage.Queued,
                elapsedSeconds = 0,
                queue = VtonProgress(),
                savedConfirmation = false,
            )
        }

        // Save the job (the poller picks it up) and show progress locally.
        renderJob = viewModelScope.launch {
            jobRepo.save(pendingJob)
            val timer = launch { tick(pendingJob.submittedAt) }
            val progressWatcher = launch {
                vtonEngine.progress.collect { progress ->
                    _uiState.update { it.copy(queue = progress) }
                }
            }
            try {
                // The observeAll collector in init flips the stage when the job leaves the repo
                // (success) or shows Failed; here we only keep the queued->processing step.
                jobRepo.observeAll()
                    .first { jobs -> jobs.firstOrNull { it.id == jobId }?.status is JobStatus.Processing }
                _uiState.update { it.copy(tryOnStage = TryOnStage.Processing) }
            } finally {
                timer.cancel()
                progressWatcher.cancel()
            }
        }
    }

    fun cancelTryOn() {
        val id = watchedJobId
        cancelledJobId = id
        watchedJobId = null
        renderJob?.cancel()
        renderJob = null
        _uiState.update {
            it.copy(tryOnStage = TryOnStage.Idle, elapsedSeconds = 0, queue = VtonProgress())
        }
        if (id != null) viewModelScope.launch { jobRepo.delete(id) }
    }

    fun dismissTryOnError() {
        _uiState.update {
            if (it.tryOnStage is TryOnStage.Failed) it.copy(tryOnStage = TryOnStage.Idle) else it
        }
    }

    /** Cancel an active job from the Ongoing list. */
    fun cancelJob(job: PendingJob) {
        if (job.status.isTerminal) return
        if (job.id == watchedJobId) {
            cancelledJobId = job.id
            watchedJobId = null
            _uiState.update {
                it.copy(tryOnStage = TryOnStage.Idle, elapsedSeconds = 0, queue = VtonProgress())
            }
        }
        viewModelScope.launch { jobRepo.delete(job.id) }
    }

    /** Retry a failed job: a fresh row for the poller, then drop the failed one. */
    fun retryJob(job: PendingJob) {
        if (job.status !is JobStatus.Failed) return
        viewModelScope.launch {
            jobRepo.save(
                PendingJob(
                    id = UUID.randomUUID().toString(),
                    providerName = job.providerName,
                    providerJobId = null,
                    garmentIds = job.garmentIds,
                    baselineImagePath = job.baselineImagePath,
                    submittedAt = clock.millis(),
                    status = JobStatus.Queued,
                ),
            )
            jobRepo.delete(job.id)
        }
    }

    /** Retry saving a completed job whose lookbook write failed. */
    fun retrySaveJob(job: PendingJob) {
        val status = job.status
        if (status !is JobStatus.Completed) return
        viewModelScope.launch {
            when (lookbook.save(status.resultImagePath, job.garmentIds)) {
                is DataResult.Success -> jobRepo.delete(job.id)
                is DataResult.Failure -> Unit // leave it for another try
            }
        }
    }

    override fun onCleared() {
        renderJob?.cancel()
    }

    private suspend fun tick(startedAtMillis: Long) {
        while (currentCoroutineContext().isActive) {
            val elapsedMillis = clock.millis() - startedAtMillis
            _uiState.update { it.copy(elapsedSeconds = elapsedMillis / MILLIS_PER_SECOND) }
            // Sleep to the next whole second so the display never drifts behind the timer.
            delay(ELAPSED_TICK_MILLIS - elapsedMillis % ELAPSED_TICK_MILLIS)
        }
    }

    /**
     * Synchronous fallback for engines that only support [render] (e.g. [MockVtonEngine]).
     * Renders, then saves the result straight to the lookbook, matching the async path's
     * auto-save behaviour.
     */
    private fun syncTryOn() {
        val snapshot = _uiState.value
        val startedAtMillis = clock.millis()
        _uiState.update {
            it.copy(tryOnStage = TryOnStage.Queued, elapsedSeconds = 0, queue = VtonProgress(), savedConfirmation = false)
        }
        renderJob = viewModelScope.launch {
            val timer = launch { tick(startedAtMillis) }
            val progressWatcher = launch {
                vtonEngine.progress.collect { progress ->
                    _uiState.update { it.copy(queue = progress) }
                }
            }
            try {
                val request = buildRequest(snapshot)
                if (request == null) {
                    ensureActive()
                    fail(AppError.LocalMedia)
                    return@launch
                }
                _uiState.update { it.copy(tryOnStage = TryOnStage.Processing) }
                val outcome = withTimeoutOrNull(OVERALL_TIMEOUT_MILLIS) { vtonEngine.render(request) }
                when (outcome) {
                    null -> fail(AppError.Timeout)
                    is DataResult.Failure -> fail(outcome.error.toAppError())
                    is DataResult.Success -> saveRenderedResult(outcome.value.image, snapshot)
                }
            } finally {
                timer.cancel()
                progressWatcher.cancel()
            }
        }
    }

    private fun applyLoad(load: Load) {
        _uiState.update { current ->
            when (load) {
                Loading -> current.copy(loading = true, loadFailed = false)
                LoadFailed -> current.copy(loading = false, loadFailed = true)
                is LoadedGarments -> current.copy(
                    loading = false,
                    loadFailed = false,
                    baselinePresent = load.baselinePresent,
                    garmentsByCategory = load.garmentsByCategory,
                    selection = dropVanishedGarments(current.selection, load.garmentsByCategory),
                )
            }
        }
    }

    private fun loadGarments(garments: List<Garment>, baselineFlag: Boolean): LoadedGarments =
        LoadedGarments(
            garmentsByCategory = garments.groupBy { it.category },
            baselinePresent = baselineFlag &&
                imageStore.resolve(ImageStore.BASELINE_PATH).exists(),
        )

    private suspend fun buildRequest(snapshot: FittingRoomUiState): VtonRequest? =
        withContext(ioDispatcher) {
            try {
                VtonRequest(
                    baselineImage = imageStore.resolve(ImageStore.BASELINE_PATH).readBytes(),
                    garments = snapshot.selection.selected.mapValues { (_, garment) ->
                        imageStore.resolve(garment.imagePath).readBytes()
                    },
                )
            } catch (e: IOException) {
                null
            }
        }

    /** Writes a synchronously rendered result to the image store, then to the lookbook. */
    private suspend fun saveRenderedResult(image: ByteArray, snapshot: FittingRoomUiState) {
        val relativePath = imageStore.newRelativePath(MediaKind.LOOK)
        val written = withContext(ioDispatcher) {
            val target = imageStore.resolve(relativePath)
            try {
                target.parentFile?.mkdirs()
                target.outputStream().use { stream -> image.inputStream().copyTo(stream) }
                true
            } catch (e: IOException) {
                target.delete()
                false
            }
        }
        if (!written) {
            fail(AppError.LocalMedia)
            return
        }
        val garmentIds = snapshot.selection.selected.values.map { garment -> garment.id }
        when (lookbook.save(relativePath, garmentIds)) {
            is DataResult.Success ->
                _uiState.update {
                    it.copy(tryOnStage = TryOnStage.Completed, savedConfirmation = true, elapsedSeconds = 0)
                }
            is DataResult.Failure -> {
                withContext(ioDispatcher) { imageStore.delete(relativePath) }
                fail(AppError.LocalMedia)
            }
        }
    }

    private fun fail(error: AppError) {
        _uiState.update { it.copy(tryOnStage = TryOnStage.Failed(error)) }
    }

    private fun dropVanishedGarments(
        selected: FittingRoomSelection,
        garmentsByCategory: Map<GarmentCategory, List<Garment>>,
    ): FittingRoomSelection {
        val liveIds = garmentsByCategory.values.flatten().map { it.id }.toSet()
        // toggle() removes an active garment, so pruning invents no selection rule of its own.
        return selected.selected.values
            .filterNot { garment -> garment.id in liveIds }
            .fold(selected) { acc, garment -> acc.toggle(garment) }
    }

    private companion object {
        const val ELAPSED_TICK_MILLIS = 1_000L
        const val MILLIS_PER_SECOND = 1_000L

        /**
         * A backstop for an engine that ignores cancellation. It must stay at or above the engine's
         * own budget ([PixelApiConfig.overallTimeout], 1200 s), or this shorter guard fires first
         * and the engine's timeout becomes unreachable.
         */
        const val OVERALL_TIMEOUT_MILLIS = 1_200_000L
    }
}

private sealed interface Load

private data class LoadedGarments(
    val garmentsByCategory: Map<GarmentCategory, List<Garment>>,
    val baselinePresent: Boolean,
) : Load

private data object Loading : Load

private data object LoadFailed : Load
