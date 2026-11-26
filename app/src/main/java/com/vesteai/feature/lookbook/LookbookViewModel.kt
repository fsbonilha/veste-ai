package com.vesteai.feature.lookbook

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vesteai.core.media.ImageStore
import com.vesteai.domain.model.Outfit
import com.vesteai.domain.repository.JobRepository
import com.vesteai.domain.repository.LookbookRepository
import com.vesteai.domain.repository.WardrobeRepository
import com.vesteai.domain.vton.JobStatus
import com.vesteai.domain.vton.PendingJob
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/** One grid card: the saved outfit plus its already-formatted creation date. */
data class LookCard(val outfit: Outfit, val createdLabel: String)

sealed interface LookbookUiState {
    data object Loading : LookbookUiState

    data object Error : LookbookUiState

    data class Content(
        val looks: List<LookCard>,
        val pendingJobs: List<PendingJob> = emptyList(),
        val pendingDeletion: LookCard? = null,
        val deleteFailed: Boolean = false,
    ) : LookbookUiState
}

@HiltViewModel
class LookbookViewModel @Inject constructor(
    private val lookbook: LookbookRepository,
    private val jobRepo: JobRepository,
    private val wardrobe: WardrobeRepository,
    private val imageStore: ImageStore,
    private val dateFormatter: LookbookDateFormatter,
) : ViewModel() {

    private val attempt = MutableStateFlow(0)
    private val pendingDeletionId = MutableStateFlow<String?>(null)
    private val deleteFailed = MutableStateFlow(false)

    private val listing: Flow<LookbookUiState> = attempt.flatMapLatest {
        lookbook.observeAll()
            .map<List<Outfit>, LookbookUiState> { outfits ->
                LookbookUiState.Content(
                    looks = outfits.map { LookCard(it, dateFormatter.format(it.createdAt)) },
                )
            }
            .onStart { emit(LookbookUiState.Loading) }
            .catch { emit(LookbookUiState.Error) }
    }

    val uiState: StateFlow<LookbookUiState> =
        combine(listing, pendingDeletionId, deleteFailed, jobRepo.observeAll()) { base, pendingId, failed, jobs ->
            if (base is LookbookUiState.Content) {
                base.copy(
                    pendingJobs = jobs,
                    pendingDeletion = base.looks.firstOrNull { it.outfit.id == pendingId },
                    deleteFailed = failed,
                )
            } else {
                base
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, LookbookUiState.Loading)

    /** Number of unseen terminal jobs, for the Lookbook tab badge. */
    val unseenCount: StateFlow<Int> =
        jobRepo.observeUnseen()
            .map { jobs -> jobs.size }
            .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    fun imageFile(imagePath: String): File = imageStore.resolve(imagePath)

    /** Garment ids handed to the fitting room when a card is re-loaded. */
    fun reloadGarmentIds(look: LookCard): List<String> = look.outfit.garments.map { it.id }

    /** Garment ids from a pending job, for re-loading into the fitting room. */
    fun pendingJobGarmentIds(job: PendingJob): List<String> = job.garmentIds

    fun retry() {
        attempt.value += 1
    }

    fun toggleFavorite(look: LookCard) {
        viewModelScope.launch { lookbook.setFavorite(look.outfit.id, !look.outfit.isFavorite) }
    }

    fun requestDelete(look: LookCard) {
        pendingDeletionId.value = look.outfit.id
    }

    fun cancelDelete() {
        pendingDeletionId.value = null
    }

    fun confirmDelete() {
        val outfitId = pendingDeletionId.value ?: return
        pendingDeletionId.value = null
        viewModelScope.launch {
            deleteFailed.value = try {
                lookbook.delete(outfitId)
                false
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                true
            }
        }
    }

    fun dismissDeleteError() {
        deleteFailed.value = false
    }

    /** Save a completed job's result to the lookbook, then delete the job row. */
    fun saveJobToLookbook(job: PendingJob) {
        if (job.status !is JobStatus.Completed) return
        val resultPath = (job.status as JobStatus.Completed).resultImagePath
        viewModelScope.launch {
            lookbook.save(resultPath, job.garmentIds)
            jobRepo.delete(job.id)
        }
    }

    /** Discard a completed job's result file and delete the job row. */
    fun discardJob(job: PendingJob) {
        viewModelScope.launch {
            if (job.status is JobStatus.Completed) {
                imageStore.delete((job.status as JobStatus.Completed).resultImagePath)
            }
            jobRepo.delete(job.id)
        }
    }

    /** Cancel an active (queued/processing) job — removes it from the poller's view. */
    fun cancelJob(job: PendingJob) {
        if (job.status.isTerminal) return
        viewModelScope.launch { jobRepo.delete(job.id) }
    }

    /** Retry a failed job: re-insert with queued status for the poller to pick up. */
    fun retryJob(job: PendingJob) {
        if (job.status !is JobStatus.Failed) return
        viewModelScope.launch {
            val retry = PendingJob(
                id = job.id,
                providerName = job.providerName,
                providerJobId = null,
                garmentIds = job.garmentIds,
                baselineImagePath = job.baselineImagePath,
                submittedAt = job.submittedAt,
                status = JobStatus.Queued,
                seen = false,
            )
            jobRepo.save(retry)
        }
    }

    /** Mark all unseen jobs as seen (called when the Lookbook tab is navigated to). */
    fun markAllSeen() {
        viewModelScope.launch {
            val unseen = jobRepo.observeUnseen().first() ?: return@launch
            unseen.forEach { jobRepo.markSeen(it.id) }
        }
    }
}