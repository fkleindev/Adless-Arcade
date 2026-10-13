package com.adlessarcade.games.blockbreak

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.adlessarcade.arcadeApp
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
)

data class BlockBreakUiState(
    val game: GameState,
    val best: Int = 0,
    val isNewBest: Boolean = false,
    val lastClear: ClearEvent? = null,
)

class BlockBreakViewModel(app: Application) : AndroidViewModel(app) {
    private val highscores = app.arcadeApp.highscores
    private val modeId = BlockBreak.MODE_ENDLESS
    private val random = Random(System.nanoTime())
    private var eventCounter = 0L

    /** Rekord zu Beginn der aktuellen Runde – Grundlage für "Neuer Rekord!". */
    private var bestAtStart = 0

    var state by mutableStateOf(BlockBreakUiState(game = BlockBreakEngine.newGame(random)))
        private set

    init {
        viewModelScope.launch {
            bestAtStart = highscores.highscore(BlockBreak.GAME_ID, modeId).first()
            state = state.copy(
                best = maxOf(state.best, bestAtStart),
                isNewBest = state.game.score > bestAtStart,
            )
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
            )
        } else {
            state.lastClear
        }
        state = state.copy(
            game = result.state,
            best = maxOf(state.best, score),
            isNewBest = score > bestAtStart,
            lastClear = clear,
        )
        if (score > bestAtStart) {
            viewModelScope.launch { highscores.submit(BlockBreak.GAME_ID, modeId, score) }
        }
        return result
    }

    fun restart() {
        bestAtStart = state.best
        state = BlockBreakUiState(game = BlockBreakEngine.newGame(random), best = state.best)
    }
}
