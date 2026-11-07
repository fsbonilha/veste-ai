package com.vesteai.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.vesteai.ui.onboarding.BaselineCaptureScreen
import kotlinx.serialization.Serializable

@Serializable
data object SettingsDestination

/**
 * Re-take baseline. This re-uses [BaselineCaptureScreen] on its own destination rather than
 * navigating into the onboarding graph: the onboarding route's `onSaved` pops the whole onboarding
 * graph and lands on Wardrobe, which is wrong when the user came from Settings. Re-using the screen
 * keeps the capture, import, and framing behaviour identical with no edits to onboarding.
 */
@Serializable
data object BaselineRetakeDestination

fun NavGraphBuilder.settingsGraph(navController: NavController) {
    composable<SettingsDestination> {
        SettingsRoute(onRetakeBaseline = { navController.navigate(BaselineRetakeDestination) })
    }
    composable<BaselineRetakeDestination> {
        BaselineCaptureScreen(onSaved = { navController.popBackStack() })
    }
}

@Composable
internal fun SettingsRoute(
    onRetakeBaseline: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    SettingsScreen(
        state = state,
        onApiKeyChanged = viewModel::onApiKeyChanged,
        onSaveKey = viewModel::save,
        onRemoveKey = viewModel::removeKey,
        onBaseUrlChanged = viewModel::onBaseUrlChanged,
        onRetakeBaseline = onRetakeBaseline,
        onRequestClearData = viewModel::requestClearData,
        onConfirmClearData = viewModel::confirmClearData,
        onCancelClearData = viewModel::cancelClearData,
        onDismissClearDataResult = viewModel::dismissClearDataResult,
        onDismissKeyMessage = viewModel::dismissKeyMessage,
        onRetry = viewModel::retry,
    )
}