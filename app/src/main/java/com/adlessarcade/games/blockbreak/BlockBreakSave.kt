package com.adlessarcade.games.blockbreak

import com.adlessarcade.core.SaveJson
import kotlinx.serialization.Serializable

/** Ein unterbrochener Lauf inkl. Rekord zu Rundenbeginn (für "Neuer Rekord!" nach dem Fortsetzen). */
@Serializable
data class BlockBreakSave(val version: Int, val game: GameState, val bestAtStart: Int = 0)

object BlockBreakSaves {
    const val VERSION = 1

    fun encode(game: GameState, bestAtStart: Int): String =
        SaveJson.encodeToString(BlockBreakSave.serializer(), BlockBreakSave(VERSION, game, bestAtStart))

    /** Liefert null bei defekten oder inkompatiblen Spielständen. */
    fun decode(json: String): BlockBreakSave? {
        val save = runCatching { SaveJson.decodeFromString(BlockBreakSave.serializer(), json) }.getOrNull() ?: return null
        val game = save.game
        val valid = save.version == VERSION &&
            game.board.size == BlockBreakEngine.CELL_COUNT &&
            game.tray.size == BlockBreakEngine.TRAY_SIZE &&
            !game.isGameOver
        return if (valid) save else null
    }
}
