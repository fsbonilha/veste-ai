package com.vesteai.feature.lookbook

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun LookbookRoute(
    onReloadInFittingRoom: (List<String>) -> Unit,
    viewModel: LookbookViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    viewModel.markAllSeen()

    LookbookScreen(
        state = state,
        imageFile = viewModel::imageFile,
        onRetry = viewModel::retry,
        onToggleFavorite = viewModel::toggleFavorite,
        onReload = { look -> onReloadInFittingRoom(viewModel.reloadGarmentIds(look)) },
        onRequestDelete = viewModel::requestDelete,
        onConfirmDelete = viewModel::confirmDelete,
        onCancelDelete = viewModel::cancelDelete,
        onDismissDeleteError = viewModel::dismissDeleteError,
        onCancelJob = viewModel::cancelJob,
        onRetryJob = viewModel::retryJob,
        onSaveJobToLookbook = viewModel::saveJobToLookbook,
        onDiscardJob = viewModel::discardJob,
        onReloadJob = { job -> onReloadInFittingRoom(viewModel.pendingJobGarmentIds(job)) },
    )
}