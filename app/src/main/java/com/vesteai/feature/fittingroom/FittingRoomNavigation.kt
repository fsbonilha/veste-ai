package com.vesteai.feature.fittingroom

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.vesteai.R
import kotlinx.serialization.Serializable
import java.io.File

@Serializable
data object FittingRoomDestination

fun NavGraphBuilder.fittingRoomGraph(navController: NavController) {
    composable<FittingRoomDestination> {
        FittingRoomRoute()
    }
}

@Composable
fun FittingRoomRoute(viewModel: FittingRoomViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val share = remember(context) {
        LookSharing(context, onUnavailable = viewModel::onShareUnavailable)
    }

    FittingRoomScreen(
        state = state,
        baselineFile = viewModel.baselineFile().takeIf { state.baselinePresent },
        imageFile = viewModel::imageFile,
        onToggleGarment = viewModel::toggle,
        onTryOn = viewModel::tryOn,
        onCancelTryOn = viewModel::cancelTryOn,
        onDismissTryOnError = viewModel::dismissTryOnError,
        onRetry = viewModel::retry,
        onSaveToLookbook = viewModel::saveToLookbook,
        onDismissSaveFailure = viewModel::dismissSaveFailure,
        onDismissShareFailure = viewModel::dismissShareFailure,
        onShareResult = { relativePath -> share(viewModel.imageFile(relativePath)) },
        onDiscardResult = viewModel::discardResult,
    )
}

/** `ACTION_SEND` over a FileProvider uri, so nothing outside app storage is ever shared. */
internal class LookSharing(
    private val context: Context,
    private val onUnavailable: () -> Unit = {},
) {
    operator fun invoke(file: File) {
        val uri = FileProvider.getUriForFile(context, context.fileProviderAuthority(), file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = MIME_JPEG
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.share_look_subject))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, context.getString(R.string.share_look_chooser_title))
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(chooser)
        } catch (e: ActivityNotFoundException) {
            onUnavailable()
        }
    }

    private companion object {
        const val MIME_JPEG = "image/jpeg"
    }
}

internal fun Context.fileProviderAuthority(): String = packageName + ".fileprovider"
