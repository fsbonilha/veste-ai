package com.vesteai.feature.wardrobe

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun WardrobeRoute(
    onGarmentImported: (String) -> Unit,
    cameraCapture: CameraCaptureSlot? = null,
    listViewModel: WardrobeViewModel = hiltViewModel(),
    captureViewModel: GarmentCaptureViewModel = hiltViewModel(),
) {
    val state by listViewModel.uiState.collectAsState()
    val captureError by captureViewModel.error.collectAsState()

    LaunchedEffect(captureViewModel) {
        captureViewModel.importedImages.collect(onGarmentImported)
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> if (uri != null) captureViewModel.importPicked(uri) }
    val launchPicker = {
        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    var capturing by remember { mutableStateOf(false) }
    if (capturing && cameraCapture != null) {
        cameraCapture(
            { file ->
                capturing = false
                captureViewModel.importCaptured(file)
            },
            { capturing = false },
        )
    }

    WardrobeScreen(
        state = state,
        captureError = captureError,
        imageFile = listViewModel::imageFile,
        onFilterSelected = listViewModel::selectFilter,
        onRetry = listViewModel::retry,
        onToggleFavorite = listViewModel::toggleFavorite,
        onRequestDelete = listViewModel::requestDelete,
        onConfirmDelete = listViewModel::confirmDelete,
        onCancelDelete = listViewModel::cancelDelete,
        onDismissDeleteError = listViewModel::dismissDeleteError,
        onDismissCaptureError = captureViewModel::dismissError,
        onAddFromGallery = launchPicker,
        onAddFromCamera = if (cameraCapture != null) {
            { capturing = true }
        } else {
            launchPicker
        },
    )
}
