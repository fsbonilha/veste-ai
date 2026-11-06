package com.vesteai.ui.onboarding

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.annotation.StringRes
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.PreviewView
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.vesteai.R
import kotlinx.coroutines.launch

@Composable
fun BaselineCaptureScreen(
    onSaved: () -> Unit,
    viewModel: BaselineCaptureViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val controller = remember { BaselineCameraController(context.cacheDir) }
    val state by viewModel.state.collectAsState()
    val cameraGranted by rememberCameraGranted()
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var cameraReady by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf(false) }

    LaunchedEffect(previewView, cameraGranted) {
        val view = previewView ?: return@LaunchedEffect
        if (!cameraGranted) return@LaunchedEffect
        try {
            controller.bind(context, lifecycleOwner, view)
            cameraReady = true
        } catch (e: IllegalArgumentException) {
            cameraError = true
        } catch (e: IllegalStateException) {
            cameraError = true
        }
    }
    LaunchedEffect(state) {
        if (state == BaselineCaptureState.Saved) onSaved()
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> if (uri != null) viewModel.onPicked(uri) }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.baseline_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = stringResource(R.string.baseline_instructions),
            style = MaterialTheme.typography.bodyMedium,
        )

        Box(
            modifier = Modifier.fillMaxWidth().weight(1f).background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            when {
                cameraError -> Text(
                    text = stringResource(R.string.baseline_camera_unavailable),
                    color = Color.White,
                )
                !cameraGranted -> Text(
                    text = stringResource(R.string.baseline_camera_not_allowed),
                    color = Color.White,
                )
                else -> {
                    AndroidView(
                        factory = { viewContext -> PreviewView(viewContext) },
                        modifier = Modifier.fillMaxSize(),
                        update = { view -> previewView = view },
                    )
                    if (!cameraReady) CircularProgressIndicator(color = Color.White)
                    if (cameraReady) FramingOverlay(modifier = Modifier.fillMaxSize())
                }
            }
        }

        when (val current = state) {
            BaselineCaptureState.Ready -> Unit
            BaselineCaptureState.Saving -> Text(stringResource(R.string.baseline_saving))
            BaselineCaptureState.Saved -> Unit
            is BaselineCaptureState.Failed -> {
                Text(
                    text = stringResource(current.error.messageRes()),
                    color = MaterialTheme.colorScheme.error,
                )
                OutlinedButton(onClick = viewModel::onRetry) {
                    Text(stringResource(R.string.baseline_retry))
                }
            }
        }

        Button(
            onClick = {
                scope.launch {
                    try {
                        viewModel.onCaptured(controller.capture(context))
                    } catch (e: ImageCaptureException) {
                        viewModel.onCaptureFailed()
                    }
                }
            },
            enabled = cameraReady && state != BaselineCaptureState.Saving,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.baseline_capture))
        }
        OutlinedButton(
            onClick = {
                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            enabled = state != BaselineCaptureState.Saving,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.baseline_pick))
        }
    }
}

@Composable
private fun FramingOverlay(modifier: Modifier) {
    val description = stringResource(R.string.baseline_frame_description)
    Canvas(modifier = modifier.semantics { contentDescription = description }) {
        val height = size.height * FRAME_HEIGHT_FRACTION
        val width = height * FRAME_ASPECT
        val left = (size.width - width) / 2f
        val top = (size.height - height) / 2f
        drawRect(
            color = Color.White,
            topLeft = Offset(left, top),
            size = Size(width, height),
            style = Stroke(width = 3.dp.toPx()),
        )
    }
}

private const val FRAME_HEIGHT_FRACTION = 0.9f
private const val FRAME_ASPECT = 0.4f

@StringRes
private fun BaselineSaveError.messageRes(): Int = when (this) {
    BaselineSaveError.CAPTURE_FAILED -> R.string.baseline_error_capture_failed
    BaselineSaveError.TOO_SMALL -> R.string.baseline_error_too_small
    BaselineSaveError.UNREADABLE -> R.string.baseline_error_unreadable
    BaselineSaveError.STORAGE_FAILED -> R.string.baseline_error_storage_failed
}
