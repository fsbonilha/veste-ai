package com.vesteai.feature.wardrobe

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vesteai.core.media.ImageStore
import com.vesteai.core.result.DataResult
import com.vesteai.domain.model.Garment
import com.vesteai.domain.model.GarmentCategory
import com.vesteai.domain.repository.WardrobeRepository
import com.vesteai.domain.usecase.DeletionPlan
import com.vesteai.domain.usecase.GarmentDeletionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
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

data class PendingDeletion(val garment: Garment, val plan: DeletionPlan)

sealed interface WardrobeUiState {
    data object Loading : WardrobeUiState

    data object Error : WardrobeUiState

    data class Content(
        val filter: GarmentCategory?,
        val garments: List<Garment>,
        val pendingDeletion: PendingDeletion? = null,
        val deleteFailed: Boolean = false,
    ) : WardrobeUiState
}

@HiltViewModel
class WardrobeViewModel @Inject constructor(
    private val wardrobe: WardrobeRepository,
    private val deletion: GarmentDeletionUseCase,
    private val imageStore: ImageStore,
) : ViewModel() {

    private val filter = MutableStateFlow<GarmentCategory?>(null)
    private val attempt = MutableStateFlow(0)
    private val pendingDeletion = MutableStateFlow<PendingDeletion?>(null)
    private val deleteFailed = MutableStateFlow(false)

    private val listing: Flow<WardrobeUiState> = combine(filter, attempt) { category, _ -> category }
        .flatMapLatest { category ->
            val source = category?.let { wardrobe.observeByCategory(it) } ?: wardrobe.observeAll()
            source
                .map<List<Garment>, WardrobeUiState> { WardrobeUiState.Content(category, it) }
                .onStart { emit(WardrobeUiState.Loading) }
                .catch { emit(WardrobeUiState.Error) }
        }

    val uiState: StateFlow<WardrobeUiState> =
        combine(listing, pendingDeletion, deleteFailed) { base, pending, failed ->
            if (base is WardrobeUiState.Content) {
                base.copy(pendingDeletion = pending, deleteFailed = failed)
            } else {
                base
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, WardrobeUiState.Loading)

    fun imageFile(imagePath: String): File = imageStore.resolve(imagePath)

    fun selectFilter(category: GarmentCategory?) {
        filter.value = category
    }

    fun retry() {
        attempt.value += 1
    }

    fun toggleFavorite(garment: Garment) {
        viewModelScope.launch { wardrobe.setFavorite(garment.id, !garment.isFavorite) }
    }

    fun requestDelete(garment: Garment) {
        viewModelScope.launch {
            pendingDeletion.value = PendingDeletion(garment, deletion.plan(garment.id))
        }
    }

    fun cancelDelete() {
        pendingDeletion.value = null
    }

    fun confirmDelete() {
        val pending = pendingDeletion.value ?: return
        pendingDeletion.value = null
        viewModelScope.launch {
            deleteFailed.value = when (deletion.delete(pending.garment.id)) {
                is DataResult.Success -> false
                is DataResult.Failure -> true
            }
        }
    }

    fun dismissDeleteError() {
        deleteFailed.value = false
    }
}
