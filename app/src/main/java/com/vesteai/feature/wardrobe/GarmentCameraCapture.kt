package com.vesteai.feature.wardrobe

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.vesteai.R
import com.vesteai.ui.onboarding.BaselineCameraController
import kotlinx.coroutines.launch
import java.io.File

/**
 * In-app camera capture for a garment, plugged into `WardrobeRoute`'s `cameraCapture` slot so the
 * wardrobe's "Take photo" button opens a real camera instead of the gallery picker.
 */
@Composable
fun GarmentCameraCapture(
    onCaptured: (File) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val controller = remember { BaselineCameraController(context.cacheDir) }

    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var ready by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val previewView = remember { mutableStateOf<PreviewView?>(null) }

    val permissionRequest = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { result -> granted = result }

    LaunchedEffect(granted) {
        if (!granted) permissionRequest.launch(Manifest.permission.CAMERA)
    }

    LaunchedEffect(granted, previewView.value) {
        val view = previewView.value ?: return@LaunchedEffect
        if (!granted) return@LaunchedEffect
        runCatching { controller.bind(context, lifecycleOwner, view) }
            .onSuccess { ready = true }
            .onFailure { failed = true }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.garment_camera_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = stringResource(R.string.crop_hint_body),
            style = MaterialTheme.typography.bodyMedium,
        )

        Box(
            modifier = Modifier.fillMaxWidth().weight(1f).background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            val description = stringResource(R.string.garment_camera_preview_description)
            when {
                failed -> Text(
                    text = stringResource(R.string.baseline_camera_unavailable),
                    color = Color.White,
                )
                !granted -> Text(
                    text = stringResource(R.string.baseline_camera_not_allowed),
                    color = Color.White,
                )
                else -> {
                    AndroidView(
                        factory = { viewContext ->
                            PreviewView(viewContext).also { previewView.value = it }
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .semantics { contentDescription = description },
                    )
                    if (!ready) CircularProgressIndicator(color = Color.White)
                }
            }
        }

        Button(
            onClick = {
                scope.launch {
                    runCatching { controller.capture(context) }
                        .onSuccess(onCaptured)
                        .onFailure { failed = true }
                }
            },
            enabled = ready,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) {
            Text(stringResource(R.string.garment_camera_shutter))
        }
        TextButton(
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) {
            Text(stringResource(R.string.action_cancel))
        }
    }
}
