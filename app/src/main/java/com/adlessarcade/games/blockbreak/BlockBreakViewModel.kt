package com.adlessarcade.games.blockbreak

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.adlessarcade.arcadeApp
import com.adlessarcade.core.shop.CoinRewards
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.random.Random

/** Ein Linien-Clear, den die UI animiert. */
data class ClearEvent(
    val id: Long,
    val cells: Map<Int, Int>,
    val points: Int,
    val lines: Int,
    val streak: Int,
    val boardCleared: Boolean,
    val coins: Int,
)

data class BlockBreakUiState(
    val game: GameState,
    val best: Int = 0,
    val isNewBest: Boolean = false,
    val lastClear: ClearEvent? = null,
    /** Coin-Bonus für einen neuen Rekord, gutgeschrieben bei Game Over. */
    val recordBonus: Int = 0,
)

class BlockBreakViewModel(app: Application) : AndroidViewModel(app) {
    private val highscores = app.arcadeApp.highscores
    private val saves = app.arcadeApp.saves
    private val profile = app.arcadeApp.profile
    private val modeId = BlockBreak.MODE_ENDLESS
    private val random = Random(System.nanoTime())
    private var eventCounter = 0L

    /** Rekord zu Beginn der aktuellen Runde – Grundlage für "Neuer Rekord!". */
    private var bestAtStart = 0

    var state by mutableStateOf(BlockBreakUiState(game = BlockBreakEngine.newGame(random)))
        private set

    /** false, bis Highscore und ggf. gespeicherter Lauf geladen sind (verhindert ein kurz falsches Brett). */
    var loaded by mutableStateOf(false)
        private set

    init {
        viewModelScope.launch {
            val best = highscores.highscore(BlockBreak.GAME_ID, modeId).first()
            val json = saves.load(BlockBreak.GAME_ID, modeId)
            val saved = json?.let(BlockBreakSaves::decode)
            if (json != null && saved == null) saves.clear(BlockBreak.GAME_ID, modeId) // defekt/inkompatibel
            if (saved != null) {
                bestAtStart = minOf(saved.bestAtStart, best)
                state = BlockBreakUiState(
                    game = saved.game,
                    best = maxOf(best, saved.game.score),
                    isNewBest = saved.game.score > bestAtStart,
                )
            } else {
                bestAtStart = best
                state = state.copy(best = best)
            }
            loaded = true
        }
    }

    fun place(slot: Int, row: Int, col: Int): PlaceResult? {
        val result = BlockBreakEngine.place(state.game, slot, row, col, random) ?: return null
        val score = result.state.score
        val clear = if (result.linesCleared > 0) {
            ClearEvent(
                id = ++eventCounter,
                cells = result.clearedCells,
                points = result.points,
                lines = result.linesCleared,
                streak = result.state.streak,
                boardCleared = result.boardCleared,
                coins = result.coins,
            )
        } else {
            state.lastClear
        }
        val isNewBest = score > bestAtStart
        val recordBonus = if (result.state.isGameOver && isNewBest) CoinRewards.NEW_RECORD_BONUS else 0
        state = state.copy(
            game = result.state,
            best = maxOf(state.best, score),
            isNewBest = isNewBest,
            lastClear = clear,
            recordBonus = recordBonus,
        )
        profile.addCoins(result.coins + recordBonus)
        if (isNewBest) {
            viewModelScope.launch { highscores.submit(BlockBreak.GAME_ID, modeId, score) }
        }
        if (result.state.isGameOver) {
            saves.clear(BlockBreak.GAME_ID, modeId)
        } else {
            saves.save(BlockBreak.GAME_ID, modeId, BlockBreakSaves.encode(result.state, bestAtStart), score)
        }
        return result
    }

    fun restart() {
        bestAtStart = state.best
        state = BlockBreakUiState(game = BlockBreakEngine.newGame(random), best = state.best)
        saves.clear(BlockBreak.GAME_ID, modeId)
    }
}
