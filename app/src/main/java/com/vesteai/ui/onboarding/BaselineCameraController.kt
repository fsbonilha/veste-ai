package com.vesteai.ui.onboarding

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** One back-camera session for the baseline screen: live preview plus a still capture. */
class BaselineCameraController(private val cacheDir: File) {

    private val imageCapture = ImageCapture.Builder().build()

    suspend fun bind(context: Context, lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
        val provider = awaitCameraProvider(context)
        val preview = Preview.Builder().build().apply {
            surfaceProvider = previewView.surfaceProvider
        }
        provider.unbindAll()
        provider.bindToLifecycle(
            lifecycleOwner,
            CameraSelector.DEFAULT_BACK_CAMERA,
            preview,
            imageCapture,
        )
    }

    suspend fun capture(context: Context): File = suspendCancellableCoroutine { continuation ->
        val file = File(cacheDir, "baseline-capture-${UUID.randomUUID()}.jpg")
        imageCapture.takePicture(
            ImageCapture.OutputFileOptions.Builder(file).build(),
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    continuation.resume(file)
                }

                override fun onError(exception: ImageCaptureException) {
                    file.delete()
                    continuation.resumeWithException(exception)
                }
            },
        )
    }

    private suspend fun awaitCameraProvider(context: Context): ProcessCameraProvider =
        suspendCancellableCoroutine { continuation ->
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener(
                { continuation.resumeWith(runCatching { future.get() }) },
                ContextCompat.getMainExecutor(context),
            )
        }
}
