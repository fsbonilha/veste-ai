package com.vesteai.feature.lookbook

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vesteai.core.media.ImageStore
import com.vesteai.domain.model.Outfit
import com.vesteai.domain.repository.LookbookRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
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
        val viewing: LookCard? = null,
        val pendingDeletion: LookCard? = null,
        val deleteFailed: Boolean = false,
    ) : LookbookUiState
}

@HiltViewModel
class LookbookViewModel @Inject constructor(
    private val lookbook: LookbookRepository,
    private val imageStore: ImageStore,
    private val dateFormatter: LookbookDateFormatter,
) : ViewModel() {

    private val attempt = MutableStateFlow(0)
    private val viewingId = MutableStateFlow<String?>(null)
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
        combine(listing, viewingId, pendingDeletionId, deleteFailed) { base, viewId, pendingId, failed ->
            if (base is LookbookUiState.Content) {
                base.copy(
                    viewing = base.looks.firstOrNull { it.outfit.id == viewId },
                    pendingDeletion = base.looks.firstOrNull { it.outfit.id == pendingId },
                    deleteFailed = failed,
                )
            } else {
                base
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, LookbookUiState.Loading)

    fun imageFile(imagePath: String): File = imageStore.resolve(imagePath)

    /** Garment ids handed to the fitting room when a card is re-loaded. */
    fun reloadGarmentIds(look: LookCard): List<String> = look.outfit.garments.map { it.id }

    fun retry() {
        attempt.value += 1
    }

    /** Opens a saved look full-screen, like a photo in a gallery. */
    fun openLook(look: LookCard) {
        viewingId.value = look.outfit.id
    }

    fun closeLook() {
        viewingId.value = null
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
        // Deleting the look being viewed closes the viewer with it.
        if (viewingId.value == outfitId) viewingId.value = null
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
}
