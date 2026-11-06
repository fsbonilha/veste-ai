package com.vesteai.feature.wardrobe

import android.database.sqlite.SQLiteException
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vesteai.core.media.ImageStore
import com.vesteai.domain.model.GarmentCategory
import com.vesteai.domain.repository.WardrobeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

enum class DraftStep { CROP_HINT, TAGGING }

data class DraftUiState(
    val imagePath: String,
    val step: DraftStep = DraftStep.CROP_HINT,
    val category: GarmentCategory? = null,
    val saving: Boolean = false,
    val saveFailed: Boolean = false,
)

@HiltViewModel
class GarmentDraftViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val wardrobe: WardrobeRepository,
    private val imageStore: ImageStore,
) : ViewModel() {

    private val imagePath = savedStateHandle.toRoute<GarmentDraftDestination>().imagePath

    private val state = MutableStateFlow(DraftUiState(imagePath))
    val uiState: StateFlow<DraftUiState> = state.asStateFlow()

    private val finished = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val done: Flow<Unit> = finished.asSharedFlow()

    fun imageFile(): File = imageStore.resolve(imagePath)

    fun continueToTagging() {
        state.value = state.value.copy(step = DraftStep.TAGGING)
    }

    fun backToCropHint() {
        state.value = state.value.copy(step = DraftStep.CROP_HINT)
    }

    fun selectCategory(category: GarmentCategory) {
        state.value = state.value.copy(category = category, saveFailed = false)
    }

    fun save() {
        val category = state.value.category ?: return
        if (state.value.saving) return
        state.value = state.value.copy(saving = true, saveFailed = false)
        viewModelScope.launch {
            try {
                wardrobe.add(imagePath, category)
                finished.emit(Unit)
            } catch (e: SQLiteException) {
                state.value = state.value.copy(saving = false, saveFailed = true)
            }
        }
    }

    /** Called when the user backs out before saving, so the imported file does not orphan. */
    fun discard() {
        if (state.value.saving) return
        imageStore.delete(imagePath)
        viewModelScope.launch { finished.emit(Unit) }
    }
}
