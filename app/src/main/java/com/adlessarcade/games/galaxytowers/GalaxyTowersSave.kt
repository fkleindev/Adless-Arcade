package com.adlessarcade.games.galaxytowers

import com.adlessarcade.core.SaveJson
import kotlinx.serialization.Serializable

/** Ein unterbrochener Lauf inkl. Rekord zu Spielbeginn (für "Neuer Rekord!" nach dem Fortsetzen). */
@Serializable
data class GalaxyTowersSave(val version: Int, val game: GameState, val bestAtStart: Int = 0)

object GalaxyTowersSaves {
    const val VERSION = 2

    fun encode(game: GameState, bestAtStart: Int): String =
        SaveJson.encodeToString(GalaxyTowersSave.serializer(), GalaxyTowersSave(VERSION, game, bestAtStart))

    /** Liefert null bei defekten oder inkompatiblen Spielständen. */
    fun decode(json: String, mode: GalaxyTowersMode? = null): GalaxyTowersSave? {
        val save = runCatching { SaveJson.decodeFromString(GalaxyTowersSave.serializer(), json) }.getOrNull() ?: return null
        val game = save.game
        val valid = save.version == VERSION &&
            game.tower.size == GalaxyTowersEngine.TOWER_SIZE &&
            game.waste.isNotEmpty() &&
            (mode == null || game.mode == mode) &&
            !game.isGameOver
        return if (valid) save else null
    }
}
