package com.vesteai.feature.lookbook

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
data object LookbookDestination

/**
 * Hook for re-loading a saved look into the fitting room. The fitting room is owned by
 * another worktree, so this graph takes the navigation as a parameter instead of naming a
 * route it does not own. [onReloadOutfit] receives the constituent garment ids in
 * cross-ref order; the default pops back to the Lookbook tab root.
 */
fun NavGraphBuilder.lookbookGraph(
    navController: NavController,
    onReloadOutfit: (List<String>) -> Unit = {},
) {
    composable<LookbookDestination> {
        LookbookRoute(onReloadInFittingRoom = onReloadOutfit)
    }
}
