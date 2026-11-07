package com.vesteai.ui.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.vesteai.R
import com.vesteai.feature.fittingroom.FittingRoomDestination
import com.vesteai.feature.fittingroom.fittingRoomGraph
import com.vesteai.feature.lookbook.LookbookDestination
import com.vesteai.feature.lookbook.lookbookGraph
import com.vesteai.feature.settings.SettingsDestination
import com.vesteai.feature.settings.settingsGraph
import com.vesteai.feature.wardrobe.WardrobeDestination
import com.vesteai.feature.wardrobe.wardrobeGraph
import com.vesteai.ui.onboarding.onboardingGraph

/** Tabs in bottom-bar order. Four entries: Material3 supports 3-5 before labels truncate. */
internal enum class HomeTab(val route: Any, val labelRes: Int, val iconRes: Int) {
    FittingRoom(FittingRoomDestination, R.string.tab_fitting_room, R.drawable.ic_tab_fitting_room),
    Wardrobe(WardrobeDestination, R.string.tab_wardrobe, R.drawable.ic_tab_wardrobe),
    Lookbook(LookbookDestination, R.string.tab_lookbook, R.drawable.ic_tab_lookbook),
    Settings(SettingsDestination, R.string.tab_settings, R.drawable.ic_tab_settings),
}

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

    if (onboarding) {
        LaunchFlow(onComplete = rootViewModel::onOnboardingFinished)
    } else {
        HomeShell()
    }
}

@Composable
private fun LaunchFlow(onComplete: () -> Unit) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = OnboardingGraph) {
        onboardingGraph(navController, onComplete)
    }
}

@Composable
private fun HomeShell() {
    val navController = rememberNavController()
    val entry by navController.currentBackStackEntryAsState()
    val current = entry?.destination

    Scaffold(
        bottomBar = {
            NavigationBar {
                HomeTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = current.isOn(tab),
                        onClick = { navController.switchTab(tab) },
                        icon = {
                            Icon(
                                painter = painterResource(tab.iconRes),
                                contentDescription = null,
                            )
                        },
                        label = { Text(stringResource(tab.labelRes)) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = HomeTab.Wardrobe.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            fittingRoomGraph(navController)
            wardrobeGraph(navController)
            lookbookGraph(
                navController = navController,
                onReloadOutfit = { garmentIds ->
                    navController.reloadInFittingRoom(garmentIds)
                },
            )
            settingsGraph(navController)
        }
    }
}

private fun NavDestination?.isOn(tab: HomeTab): Boolean = when (tab) {
    HomeTab.FittingRoom -> this?.hasRoute<FittingRoomDestination>() == true
    HomeTab.Wardrobe -> this?.hasRoute<WardrobeDestination>() == true
    HomeTab.Lookbook -> this?.hasRoute<LookbookDestination>() == true
    HomeTab.Settings -> this?.hasRoute<SettingsDestination>() == true
}

/**
 * Re-loads a saved look: switches to the fitting room tab with the constituent garments
 * preselected. Uses the same tab-switch options so the fitting room keeps its back stack.
 */
private fun NavController.reloadInFittingRoom(garmentIds: List<String>) {
    navigate(FittingRoomDestination(preselectGarmentIds = garmentIds)) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** Switches tabs with each tab keeping its own back stack. */
private fun NavController.switchTab(tab: HomeTab) {
    navigate(tab.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
