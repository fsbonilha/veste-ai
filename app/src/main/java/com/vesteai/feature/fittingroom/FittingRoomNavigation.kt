package com.vesteai.feature.fittingroom

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import kotlinx.serialization.Serializable

@Serializable
data class FittingRoomDestination(val preselectGarmentIds: List<String> = emptyList())

fun NavGraphBuilder.fittingRoomGraph(navController: NavController) {
    composable<FittingRoomDestination> { entry ->
        val route = entry.toRoute<FittingRoomDestination>()
        FittingRoomRoute(preselectGarmentIds = route.preselectGarmentIds)
    }
}

@Composable
fun FittingRoomRoute(
    preselectGarmentIds: List<String> = emptyList(),
    viewModel: FittingRoomViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(preselectGarmentIds, state.loading) {
        if (!state.loading) viewModel.preselect(preselectGarmentIds)
    }

    FittingRoomScreen(
        state = state,
        baselineFile = viewModel.baselineFile().takeIf { state.baselinePresent },
        imageFile = viewModel::imageFile,
        onToggleGarment = viewModel::toggle,
        onSelectTab = viewModel::selectTab,
        onTryOn = viewModel::tryOn,
        onCancelTryOn = viewModel::cancelTryOn,
        onDismissTryOnError = viewModel::dismissTryOnError,
        onDismissSavedConfirmation = viewModel::dismissSavedConfirmation,
        onRetry = viewModel::retry,
        onCancelJob = viewModel::cancelJob,
        onRetryJob = viewModel::retryJob,
        onRetrySaveJob = viewModel::retrySaveJob,
    )
}
