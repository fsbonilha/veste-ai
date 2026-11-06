package com.vesteai.feature.wardrobe

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import java.io.File

fun NavGraphBuilder.wardrobeGraph(
    navController: NavController,
    cameraCapture: CameraCaptureSlot? = null,
) {
    composable<WardrobeDestination> {
        WardrobeRoute(
            onGarmentImported = { imagePath ->
                navController.navigate(GarmentDraftDestination(imagePath))
            },
            cameraCapture = cameraCapture,
        )
    }
    composable<GarmentDraftDestination> {
        GarmentDraftRoute(onFinished = { navController.popBackStack() })
    }
}

/**
 * Entry point for in-app camera capture, owned by the camera feature. Call [onCaptured]
 * with the file the camera wrote; call [onCancel] to return without importing.
 */
typealias CameraCaptureSlot = @Composable (onCaptured: (File) -> Unit, onCancel: () -> Unit) -> Unit
