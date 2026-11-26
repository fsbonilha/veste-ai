package com.vesteai.feature.lookbook

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import com.vesteai.ui.common.LookSharing

@Composable
fun LookbookRoute(
    onReloadInFittingRoom: (List<String>) -> Unit,
    viewModel: LookbookViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val share = remember(context) { LookSharing(context) }

    LookbookScreen(
        state = state,
        imageFile = viewModel::imageFile,
        onRetry = viewModel::retry,
        onOpenLook = viewModel::openLook,
        onCloseLook = viewModel::closeLook,
        onShareLook = { look -> share(viewModel.imageFile(look.outfit.resultImagePath)) },
        onToggleFavorite = viewModel::toggleFavorite,
        onReload = { look -> onReloadInFittingRoom(viewModel.reloadGarmentIds(look)) },
        onRequestDelete = viewModel::requestDelete,
        onConfirmDelete = viewModel::confirmDelete,
        onCancelDelete = viewModel::cancelDelete,
        onDismissDeleteError = viewModel::dismissDeleteError,
    )
}
