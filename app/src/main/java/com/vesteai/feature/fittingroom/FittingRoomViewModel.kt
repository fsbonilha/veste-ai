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
import com.vesteai.domain.vton.VtonRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.IOException
import java.time.Clock
import java.util.UUID
import javax.inject.Inject

/** Try On stages. `completed` also shows a transient confirmation; the rest gate the action bar. */
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

    /**
     * True while a try-on is being processed. The canvas shows nothing for it; the action bar
     * swaps its Try On button for a way into the Ongoing list instead.
     */
    val isProcessing: Boolean
        get() = tryOnStage.isInFlight || activeJobCount > 0
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

    /** Only the synchronous render path needs a cancellable job here. */
    private var renderJob: Job? = null

    /** The job the UI is watching for a terminal state, so success and failure can be shown. */
    private var watchedJobId: String? = null

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
            // A single bad emission must not kill the collector: if it did, the Ongoing list would
            // freeze on its last value and a finished job would appear to stay forever. Re-subscribe
            // after any failure instead.
            while (true) {
                try {
                    jobRepo.observeAll().collect(::applyJobs)
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    delay(RESUBSCRIBE_DELAY_MILLIS)
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
        if (!snapshot.canTryOn) return

        // When the engine doesn't support async submit/poll, fall back to synchronous render.
        if (!vtonEngine.capabilities.isAsync) {
            syncTryOn()
            return
        }

        val garmentIds = snapshot.selection.selected.values.map { it.id }.toList()
        if (garmentIds.isEmpty()) return

        val jobId = UUID.randomUUID().toString()
        watchedJobId = jobId
        _uiState.update { it.copy(tryOnStage = TryOnStage.Queued, savedConfirmation = false) }

        // Save the job; the poller submits and polls it in the background, and [applyJobs] reacts
        // as its status changes. Nothing about the in-flight job is drawn on the canvas.
        viewModelScope.launch {
            jobRepo.save(
                PendingJob(
                    id = jobId,
                    providerName = vtonEngine.displayName,
                    providerJobId = null,
                    garmentIds = garmentIds,
                    baselineImagePath = ImageStore.BASELINE_PATH,
                    submittedAt = clock.millis(),
                    status = JobStatus.Queued,
                ),
            )
        }
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
            // Stop watching so its disappearance is not mistaken for a completed save.
            watchedJobId = null
            _uiState.update { it.copy(tryOnStage = TryOnStage.Idle) }
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

    /**
     * Mirrors every job into the UI and, for the watched one, reflects its progress: a failed job
     * shows the error banner, and a job that has left the repo means its result was saved to the
     * lookbook.
     */
    private fun applyJobs(jobs: List<PendingJob>) {
        _uiState.update { it.copy(jobs = jobs) }
        val watchedId = watchedJobId ?: return
        val watched = jobs.firstOrNull { it.id == watchedId }
        when {
            // Gone from the repo without failing: the poller saved it to the lookbook.
            watched == null -> {
                watchedJobId = null
                _uiState.update {
                    it.copy(tryOnStage = TryOnStage.Completed, savedConfirmation = true)
                }
            }
            watched.status is JobStatus.Processing -> {
                _uiState.update { it.copy(tryOnStage = TryOnStage.Processing) }
            }
            watched.status is JobStatus.Failed -> {
                watchedJobId = null
                val error = (watched.status as JobStatus.Failed).error
                _uiState.update { it.copy(tryOnStage = TryOnStage.Failed(error.toAppError())) }
            }
            watched.status is JobStatus.Completed -> {
                // Reached only when the lookbook write failed; the Ongoing tab offers a retry.
                watchedJobId = null
                _uiState.update { it.copy(tryOnStage = TryOnStage.Idle) }
            }
        }
    }

    /**
     * Synchronous fallback for engines that only support [render] (e.g. [MockVtonEngine]).
     * Renders, then saves the result straight to the lookbook, matching the async path's
     * auto-save behaviour.
     */
    private fun syncTryOn() {
        val snapshot = _uiState.value
        _uiState.update { it.copy(tryOnStage = TryOnStage.Queued, savedConfirmation = false) }
        renderJob = viewModelScope.launch {
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
                    it.copy(tryOnStage = TryOnStage.Completed, savedConfirmation = true)
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
        /** Backoff before re-subscribing to the jobs flow after an unexpected failure. */
        const val RESUBSCRIBE_DELAY_MILLIS = 1_000L

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
