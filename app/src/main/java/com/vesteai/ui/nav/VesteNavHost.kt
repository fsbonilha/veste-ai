package com.vesteai.ui.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.vesteai.ui.home.WardrobePlaceholderScreen
import com.vesteai.ui.onboarding.onboardingGraph

@Composable
fun VesteNavHost(rootViewModel: RootViewModel = hiltViewModel()) {
    val startsInOnboarding by rootViewModel.startsInOnboarding.collectAsState()
    val onboarding = startsInOnboarding
    if (onboarding == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = if (onboarding) OnboardingGraph else WardrobePlaceholder,
    ) {
        onboardingGraph(navController)
        composable<WardrobePlaceholder> { WardrobePlaceholderScreen() }
    }
}
