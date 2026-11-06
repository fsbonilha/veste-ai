package com.vesteai.ui.onboarding

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vesteai.core.media.ImageImportException
import com.vesteai.core.media.ImageImporter
import com.vesteai.core.media.ImportedImage
import com.vesteai.core.media.MediaKind
import com.vesteai.core.result.DataResult
import com.vesteai.domain.repository.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.time.Clock
import javax.inject.Inject

enum class BaselineSaveError { CAPTURE_FAILED, TOO_SMALL, UNREADABLE, STORAGE_FAILED }

sealed interface BaselineCaptureState {
    data object Ready : BaselineCaptureState
    data object Saving : BaselineCaptureState
    data object Saved : BaselineCaptureState
    data class Failed(val error: BaselineSaveError) : BaselineCaptureState
}

@HiltViewModel
class BaselineCaptureViewModel @Inject constructor(
    private val imageImporter: ImageImporter,
    private val profileRepository: ProfileRepository,
    private val clock: Clock,
) : ViewModel() {

    private val _state = MutableStateFlow<BaselineCaptureState>(BaselineCaptureState.Ready)
    val state: StateFlow<BaselineCaptureState> = _state.asStateFlow()

    fun onCaptured(file: File) = save {
        try {
            imageImporter.import(file, MediaKind.BASELINE)
        } finally {
            file.delete()
        }
    }

    fun onPicked(uri: Uri) = save { imageImporter.import(uri, MediaKind.BASELINE) }

    fun onCaptureFailed() {
        _state.value = BaselineCaptureState.Failed(BaselineSaveError.CAPTURE_FAILED)
    }

    fun onRetry() {
        if (_state.value is BaselineCaptureState.Failed) _state.value = BaselineCaptureState.Ready
    }

    private fun save(import: suspend () -> DataResult<ImportedImage>) {
        if (_state.value == BaselineCaptureState.Saving) return
        _state.value = BaselineCaptureState.Saving
        viewModelScope.launch {
            _state.value = when (val result = import()) {
                is DataResult.Success -> persistFlags()
                is DataResult.Failure -> BaselineCaptureState.Failed(result.error.toSaveError())
            }
        }
    }

    private suspend fun persistFlags(): BaselineCaptureState = try {
        profileRepository.markBaselineSaved(clock.millis())
        profileRepository.completeOnboarding()
        BaselineCaptureState.Saved
    } catch (e: IOException) {
        BaselineCaptureState.Failed(BaselineSaveError.STORAGE_FAILED)
    }

    private fun Throwable.toSaveError(): BaselineSaveError = when (this) {
        is ImageImportException.TooSmall -> BaselineSaveError.TOO_SMALL
        else -> BaselineSaveError.UNREADABLE
    }
}
