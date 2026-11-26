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

/** Try On stages reported by [VtonEngine.render]; `completed` also opens the result sheet. */
sealed interface TryOnStage {
    data object Idle : TryOnStage

    data object Queued : TryOnStage

    data object Processing : TryOnStage

    data object Completed : TryOnStage

    data class Failed(val error: AppError) : TryOnStage
}

val TryOnStage.isInFlight: Boolean
    get() = this == TryOnStage.Queued || this == TryOnStage.Processing

/**
 * The rendered look held in app storage while the sheet is open. Deleting the file is
 * [FittingRoomViewModel.discardResult]'s job; saving hands ownership to the lookbook.
 */
data class TryOnResultUiState(
    val relativePath: String,
    val garmentIds: Set<String>,
    val saving: Boolean = false,
    val saved: Boolean = false,
    val saveFailed: Boolean = false,
)

data class FittingRoomUiState(
    val loading: Boolean = false,
    val loadFailed: Boolean = false,
    val baselinePresent: Boolean = false,
    val garmentsByCategory: Map<GarmentCategory, List<Garment>> = emptyMap(),
    val unsupportedCategories: Set<GarmentCategory> = emptySet(),
    val selection: FittingRoomSelection = FittingRoomSelection.Empty,
    val tryOnStage: TryOnStage = TryOnStage.Idle,
    val elapsedSeconds: Long = 0,
    /** The provider's own queue reporting, when it has any (plan §7). */
    val queue: VtonProgress = VtonProgress(),
    val result: TryOnResultUiState? = null,
    val shareFailed: Boolean = false,
) {
    val hasGarments: Boolean get() = garmentsByCategory.values.any { it.isNotEmpty() }

    fun isSupported(category: GarmentCategory): Boolean = category !in unsupportedCategories

    val canTryOn: Boolean
        get() = baselinePresent && !tryOnStage.isInFlight && selection.selected.isNotEmpty()
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
    }

    fun imageFile(relativePath: String): File = imageStore.resolve(relativePath)

    fun baselineFile(): File = imageStore.resolve(ImageStore.BASELINE_PATH)

    fun retry() {
        attempt.update { it + 1 }
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

        // Build the pending job from image paths (not bytes) so it survives restarts.
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
            seen = false,
        )

        _uiState.update {
            it.copy(tryOnStage = TryOnStage.Queued, elapsedSeconds = 0, queue = VtonProgress())
        }

        // Save the job (the poller picks it up) and show progress locally.
        renderJob = viewModelScope.launch {
            jobRepo.save(pendingJob)
            // Keep a local progress watcher so the fitting room shows live updates.
            val timer = launch { tick(pendingJob.submittedAt) }
            val progressWatcher = launch {
                vtonEngine.progress.collect { progress ->
                    _uiState.update { it.copy(queue = progress) }
                }
            }
            try {
                // Watch the job repo for completion of this job.
                val completed = jobRepo.observeAll()
                    .first { jobs -> jobs.any { it.id == jobId && it.status.isTerminal } }
                    ?.firstOrNull { it.id == jobId }
                // If the job completed while we were watching, show the result.
                if (completed != null) {
                    when (completed.status) {
                        is JobStatus.Completed -> {
                            val resultPath = (completed.status as JobStatus.Completed).resultImagePath
                            _uiState.update {
                                it.copy(
                                    tryOnStage = TryOnStage.Completed,
                                    result = TryOnResultUiState(
                                        relativePath = resultPath,
                                        garmentIds = garmentIds.toSet(),
                                    ),
                                )
                            }
                        }
                        is JobStatus.Failed -> {
                            val failed = completed.status as JobStatus.Failed
                            fail(failed.error.toAppError())
                        }
                        else -> { /* should not happen */ }
                    }
                }
            } finally {
                timer.cancel()
                progressWatcher.cancel()
            }
        }
    }

    fun cancelTryOn() {
        renderJob?.cancel()
        renderJob = null
        // The job continues running in the background; we just stop watching locally.
        _uiState.update {
            it.copy(tryOnStage = TryOnStage.Idle, elapsedSeconds = 0, queue = VtonProgress())
        }
    }

    fun dismissTryOnError() {
        _uiState.update {
            if (it.tryOnStage is TryOnStage.Failed) it.copy(tryOnStage = TryOnStage.Idle) else it
        }
    }

    fun dismissResult() {
        _uiState.update { it.copy(result = null, tryOnStage = TryOnStage.Idle, elapsedSeconds = 0) }
    }

    fun saveToLookbook() {
        val current = _uiState.value.result ?: return
        if (current.saving || current.saved) return
        updateResult { it.copy(saving = true, saveFailed = false) }
        viewModelScope.launch {
            when (lookbook.save(current.relativePath, current.garmentIds)) {
                is DataResult.Success -> updateResult { it.copy(saving = false, saved = true) }
                is DataResult.Failure -> {
                    withContext(ioDispatcher) { imageStore.delete(current.relativePath) }
                    updateResult { it.copy(saving = false, saveFailed = true) }
                }
            }
        }
    }

    fun dismissSaveFailure() {
        updateResult { it.copy(saveFailed = false) }
    }

    /** Recorded when no installed app can handle the share intent. */
    fun onShareUnavailable() {
        _uiState.update { it.copy(shareFailed = true) }
    }

    fun dismissShareFailure() {
        _uiState.update { it.copy(shareFailed = false) }
    }

    /** Deletes the unsaved result file, then closes the sheet. */
    fun discardResult() {
        _uiState.value.result?.let { discard(it) }
        dismissResult()
    }

    override fun onCleared() {
        renderJob?.cancel()
        _uiState.value.result?.let { discard(it) }
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
     * This is the original blocking path restored for non-async engines.
     */
    private fun syncTryOn() {
        val snapshot = _uiState.value
        val startedAtMillis = clock.millis()
        renderJob = viewModelScope.launch {
            _uiState.update {
                it.copy(tryOnStage = TryOnStage.Queued, elapsedSeconds = 0, queue = VtonProgress())
            }
            val timer = launch { tick(startedAtMillis) }
            val progressWatcher = launch {
                vtonEngine.progress.collect { progress ->
                    _uiState.update { it.copy(queue = progress) }
                }
            }
            try {
                discardStaleResult()
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
                    is DataResult.Success -> persistResult(outcome.value.image, snapshot)
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


    private suspend fun persistResult(image: ByteArray, snapshot: FittingRoomUiState) {
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
        _uiState.update {
            it.copy(
                tryOnStage = TryOnStage.Completed,
                result = TryOnResultUiState(
                    relativePath = relativePath,
                    garmentIds = snapshot.selection.selected.values.map { garment -> garment.id }.toSet(),
                ),
            )
        }
    }

    private suspend fun discardStaleResult() {
        val stale = _uiState.value.result ?: return
        _uiState.update { it.copy(result = null) }
        discard(stale)
    }

    private fun discard(state: TryOnResultUiState) {
        if (state.saved) return
        imageStore.delete(state.relativePath)
    }

    private fun fail(error: AppError) {
        _uiState.update { it.copy(tryOnStage = TryOnStage.Failed(error)) }
    }

    private fun updateResult(transform: (TryOnResultUiState) -> TryOnResultUiState) {
        _uiState.update { current ->
            val result = current.result
            if (result == null) current else current.copy(result = transform(result))
        }
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
