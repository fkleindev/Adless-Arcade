package com.adlessarcade.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.adlessarcade.core.GameRegistry
import com.adlessarcade.games.blockbreak.BlockBreak
import com.adlessarcade.games.blockbreak.BlockBreakScreen
import com.adlessarcade.ui.home.HomeScreen
import com.adlessarcade.ui.modes.ModeSelectScreen

private object Routes {
    const val HOME = "home"
    const val GAME = "game/{gameId}"
    const val PLAY = "play/{gameId}/{modeId}"

    fun game(gameId: String) = "game/$gameId"
    fun play(gameId: String, modeId: String) = "play/$gameId/$modeId"
}

@Composable
fun AppNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        enterTransition = { fadeIn(tween(220)) + slideInHorizontally(tween(220)) { it / 10 } },
        exitTransition = { fadeOut(tween(180)) },
        popEnterTransition = { fadeIn(tween(220)) },
        popExitTransition = { fadeOut(tween(180)) + slideOutHorizontally(tween(220)) { it / 10 } },
    ) {
        composable(Routes.HOME) {
            HomeScreen(onGameClick = { navController.navigate(Routes.game(it.id)) })
        }
        composable(Routes.GAME) { entry ->
            val game = GameRegistry.find(entry.arguments?.getString("gameId")) ?: return@composable
            ModeSelectScreen(
                game = game,
                onBack = { navController.popIfCurrent(entry) },
                onModeClick = { navController.navigate(Routes.play(game.id, it.id)) },
            )
        }
        composable(Routes.PLAY) { entry ->
            GameHost(
                gameId = entry.arguments?.getString("gameId").orEmpty(),
                modeId = entry.arguments?.getString("modeId").orEmpty(),
                onExit = { navController.popIfCurrent(entry) },
            )
        }
    }
}

/** Verhindert doppeltes Zurück (z. B. Doppeltipp während einer Transition). */
private fun NavHostController.popIfCurrent(entry: NavBackStackEntry) {
    if (entry.lifecycle.currentState == Lifecycle.State.RESUMED) popBackStack()
}

/** Verknüpft gameId/modeId mit dem jeweiligen Spiel-Screen. */
@Composable
private fun GameHost(gameId: String, modeId: String, onExit: () -> Unit) {
    when (gameId) {
        BlockBreak.GAME_ID -> BlockBreakScreen(modeId = modeId, onExit = onExit)
    }
}
