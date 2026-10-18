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
import com.adlessarcade.games.galaxytowers.GalaxyTowers
import com.adlessarcade.games.galaxytowers.GalaxyTowersScreen
import com.adlessarcade.games.gaterush.GateRush
import com.adlessarcade.games.gaterush.GateRushScreen
import com.adlessarcade.games.runetactics.RuneTactics
import com.adlessarcade.games.runetactics.RuneTacticsScreen
import com.adlessarcade.games.survivors.Survivors
import com.adlessarcade.games.survivors.SurvivorsForgeScreen
import com.adlessarcade.games.survivors.SurvivorsScreen
import com.adlessarcade.ui.achievements.AchievementsScreen
import com.adlessarcade.ui.home.HomeScreen
import com.adlessarcade.ui.leaderboard.LeaderboardScreen
import com.adlessarcade.ui.shop.ShopScreen
import com.adlessarcade.ui.modes.ModeSelectScreen

private object Routes {
    const val HOME = "home"
    const val SHOP = "shop"
    const val ACHIEVEMENTS = "achievements"
    const val GAME = "game/{gameId}"
    const val PLAY = "play/{gameId}/{modeId}"
    const val LEADERBOARD = "leaderboard/{gameId}"
    const val SURVIVORS_FORGE = "survivors_forge"

    fun game(gameId: String) = "game/$gameId"
    fun play(gameId: String, modeId: String) = "play/$gameId/$modeId"
    fun leaderboard(gameId: String) = "leaderboard/$gameId"
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
            HomeScreen(
                onGameClick = { navController.navigate(Routes.game(it.id)) },
                onShopClick = { navController.navigate(Routes.SHOP) { launchSingleTop = true } },
                onAchievementsClick = { navController.navigate(Routes.ACHIEVEMENTS) { launchSingleTop = true } },
            )
        }
        composable(Routes.SHOP) { entry ->
            ShopScreen(onBack = { navController.popIfCurrent(entry) })
        }
        composable(Routes.ACHIEVEMENTS) { entry ->
            AchievementsScreen(onBack = { navController.popIfCurrent(entry) })
        }
        composable(Routes.GAME) { entry ->
            val game = GameRegistry.find(entry.arguments?.getString("gameId")) ?: return@composable
            ModeSelectScreen(
                game = game,
                onBack = { navController.popIfCurrent(entry) },
                onModeClick = { navController.navigate(Routes.play(game.id, it.id)) },
                onLeaderboardClick = { navController.navigate(Routes.leaderboard(game.id)) { launchSingleTop = true } },
                onExtraActionClick = { route -> navController.navigate(route) { launchSingleTop = true } },
            )
        }
        composable(Routes.SURVIVORS_FORGE) { entry ->
            SurvivorsForgeScreen(onBack = { navController.popIfCurrent(entry) })
        }
        composable(Routes.LEADERBOARD) { entry ->
            val game = GameRegistry.find(entry.arguments?.getString("gameId")) ?: return@composable
            LeaderboardScreen(game = game, onBack = { navController.popIfCurrent(entry) })
        }
        composable(Routes.PLAY) { entry ->
            GameHost(
                gameId = entry.arguments?.getString("gameId").orEmpty(),
                modeId = entry.arguments?.getString("modeId").orEmpty(),
                onExit = { navController.popIfCurrent(entry) },
                onOpenForge = { navController.navigate(Routes.SURVIVORS_FORGE) { launchSingleTop = true } },
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
private fun GameHost(gameId: String, modeId: String, onExit: () -> Unit, onOpenForge: () -> Unit) {
    when (gameId) {
        BlockBreak.GAME_ID -> BlockBreakScreen(modeId = modeId, onExit = onExit)
        GateRush.GAME_ID -> GateRushScreen(modeId = modeId, onExit = onExit)
        Survivors.GAME_ID -> SurvivorsScreen(onExit = onExit, onOpenForge = onOpenForge)
        GalaxyTowers.GAME_ID -> GalaxyTowersScreen(modeId = modeId, onExit = onExit)
        RuneTactics.GAME_ID -> RuneTacticsScreen(onExit = onExit)
    }
}
