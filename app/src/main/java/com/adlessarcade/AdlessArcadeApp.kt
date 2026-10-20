package com.adlessarcade

import android.app.Application
import android.content.Context
import com.adlessarcade.core.DailyResultRepository
import com.adlessarcade.core.GameRegistry
import com.adlessarcade.core.HighscoreRepository
import com.adlessarcade.core.SaveGameRepository
import com.adlessarcade.core.leaderboard.LeaderboardKey
import com.adlessarcade.core.leaderboard.LeaderboardRepository
import com.adlessarcade.core.shop.ProfileRepository
import com.adlessarcade.games.kitchen.KitchenProgressRepository
import com.adlessarcade.games.survivors.progress.SurvivorsLegacy
import com.adlessarcade.games.survivors.progress.SurvivorsProgressRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AdlessArcadeApp : Application() {
    val highscores: HighscoreRepository by lazy { HighscoreRepository(this) }
    val saves: SaveGameRepository by lazy { SaveGameRepository(this) }
    val profile: ProfileRepository by lazy { ProfileRepository(this) }
    val leaderboards: LeaderboardRepository by lazy { LeaderboardRepository(this) }
    val dailyResults: DailyResultRepository by lazy { DailyResultRepository(this) }
    val survivorsProgress: SurvivorsProgressRepository by lazy { SurvivorsProgressRepository(this) }
    val kitchenProgress: KitchenProgressRepository by lazy { KitchenProgressRepository(this) }

    override fun onCreate() {
        super.onCreate()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            migrateRecordsToLeaderboards()
            migrateSurvivorsShopItems()
        }
    }

    /**
     * Rune-Survivors-Klassen und -Welten lagen früher im allgemeinen Gold-Shop. Gekaufte Klassen wandern in die
     * Runen-Schmiede, gekaufte Welten werden in Coins erstattet. Beide Schritte sind wiederholbar, falls die App
     * dazwischen beendet wird.
     */
    private suspend fun migrateSurvivorsShopItems() {
        val (owned, equipped) = profile.rawOwnedAndEquipped()
        if (owned.none { it in SurvivorsLegacy.LEGACY_IDS } && equipped.keys.none { it.startsWith("SURVIVOR_") }) return
        val migration = SurvivorsLegacy.migrate(owned, equipped["SURVIVOR_CLASS"])
        survivorsProgress.migrateLegacy(migration)
        profile.removeItems(
            ids = SurvivorsLegacy.LEGACY_IDS,
            equippedCategories = setOf("SURVIVOR_CLASS", "SURVIVOR_THEME"),
            refundFor = SurvivorsLegacy.WORLD_PRICES,
        )
    }

    /**
     * Alte Rekorde als ersten Bestenlisten-Eintrag übernehmen. Ausgenommen sind Modi mit Tages-Rekorden und
     * Spiele mit Varianten (bei Rune Survivors ist die Klasse des alten Rekords unbekannt).
     */
    private suspend fun migrateRecordsToLeaderboards() {
        val records = GameRegistry.games
            .filter { it.leaderboardVariants.isEmpty() }
            .flatMap { game ->
                game.modes.filter { it.available && it.scoreKey() == it.id }.map { mode ->
                    LeaderboardKey(game.id, mode.id) to highscores.highscore(game.id, mode.id).first()
                }
            }
            .toMap()
        leaderboards.migrateLegacy(records)
    }
}

val Context.arcadeApp: AdlessArcadeApp get() = applicationContext as AdlessArcadeApp
