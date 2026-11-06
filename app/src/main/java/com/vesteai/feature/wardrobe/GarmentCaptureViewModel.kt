package com.vesteai.feature.wardrobe

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vesteai.core.media.ImageImportException
import com.vesteai.core.media.ImageImporter
import com.vesteai.core.media.ImportedImage
import com.vesteai.core.media.MediaKind
import com.vesteai.core.result.DataResult
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

sealed interface ImportError {
    data object TooSmall : ImportError

    data object Unreadable : ImportError
}

@HiltViewModel
class GarmentCaptureViewModel @Inject constructor(
    private val imageImporter: ImageImporter,
) : ViewModel() {

    private val imported = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val importedImages: Flow<String> = imported.asSharedFlow()

    private val importError = MutableStateFlow<ImportError?>(null)
    val error: StateFlow<ImportError?> = importError.asStateFlow()

    fun importPicked(uri: Uri) {
        viewModelScope.launch { handle(imageImporter.import(uri, MediaKind.WARDROBE)) }
    }

    fun importCaptured(file: File) {
        viewModelScope.launch { handle(imageImporter.import(file, MediaKind.WARDROBE)) }
    }

    fun dismissError() {
        importError.value = null
    }

    private suspend fun handle(result: DataResult<ImportedImage>) {
        when (result) {
            is DataResult.Success -> {
                importError.value = null
                imported.emit(result.value.relativePath)
            }
            is DataResult.Failure -> importError.value = when (result.error) {
                is ImageImportException.TooSmall -> ImportError.TooSmall
                else -> ImportError.Unreadable
            }
        }
    }
}
