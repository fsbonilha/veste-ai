package com.vesteai.ui.onboarding

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import com.vesteai.ui.nav.BaselineCapture
import com.vesteai.ui.nav.CameraPermission
import com.vesteai.ui.nav.OnboardingGraph
import com.vesteai.ui.nav.WardrobePlaceholder
import com.vesteai.ui.nav.Welcome

fun NavGraphBuilder.onboardingGraph(navController: NavController) {
    navigation<OnboardingGraph>(startDestination = Welcome) {
        composable<Welcome> {
            WelcomeCarouselScreen(
                onFinished = { navController.navigate(CameraPermission) },
            )
        }
        composable<CameraPermission> {
            CameraPermissionScreen(
                onGranted = { navController.navigate(BaselineCapture) },
                onPickInstead = { navController.navigate(BaselineCapture) },
            )
        }
        composable<BaselineCapture> {
            BaselineCaptureScreen(
                onSaved = {
                    navController.navigate(WardrobePlaceholder) {
                        popUpTo<OnboardingGraph> { inclusive = true }
                    }
                },
            )
        }
    }
}
