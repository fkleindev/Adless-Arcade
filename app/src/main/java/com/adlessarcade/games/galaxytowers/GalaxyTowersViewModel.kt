package com.adlessarcade.games.galaxytowers

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.adlessarcade.arcadeApp
import com.adlessarcade.core.DailyChallenge
import com.adlessarcade.core.achievements.AchievementTracker
import com.adlessarcade.core.achievements.Achievements
import com.adlessarcade.core.leaderboard.LeaderboardEntry
import com.adlessarcade.core.leaderboard.LeaderboardKey
import com.adlessarcade.core.shop.CoinRewards
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Eine abgelegte Karte, die die UI als Flug zur Ablage und mit Punkten animiert. */
data class PlayEvent(
    val id: Long,
    val index: Int,
    val card: Card,
    val pile: Int,
    val points: Int,
    val streak: Int,
    val peakCleared: Boolean,
    val reward: PowerUp?,
    val secondPileOpened: Boolean,
    val gold: Int,
    val timeDrop: Int,
)

/** Eine falsch angetippte Karte, die wackelt und Strafpunkte zeigt. */
data class MisplayEvent(val id: Long, val index: Int, val penalty: Int)

/** Punkte der zuletzt abgeräumten Runde (für die Runden-Einblendung). */
data class ClearBonus(val clear: Int, val time: Int, val coins: Int)

class GalaxyTowersViewModel(app: Application, savedStateHandle: SavedStateHandle) : AndroidViewModel(app) {
    private val highscores = app.arcadeApp.highscores
    private val saves = app.arcadeApp.saves
    private val leaderboards = app.arcadeApp.leaderboards
    private val profile = app.arcadeApp.profile
    private val achievements = AchievementTracker(profile)
    private var eventCounter = 0L
    /** Sekunden seit dem letzten Speichern während die Zeit läuft. */
    private var sinceSave = 0f

    val mode = GalaxyTowersMode.fromId(savedStateHandle.get<String>("modeId"))

    /** Schlüssel für Highscore und Spielstand (bei der Täglichen Challenge pro Tag). */
    private val scoreKey = if (mode == GalaxyTowersMode.DAILY) DailyChallenge.scoreKey(mode.id) else mode.id

    private fun newSeed(): Long = if (mode == GalaxyTowersMode.DAILY) DailyChallenge.todaySeed() else System.nanoTime()

    /** Rekord zu Spielbeginn – Grundlage für "Neuer Rekord!". */
    private var bestAtStart = 0

    var game by mutableStateOf(GalaxyTowersEngine.newGame(newSeed(), mode))
        private set
    var best by mutableIntStateOf(0)
        private set
    var isNewBest by mutableStateOf(false)
        private set
    var recordBonus by mutableIntStateOf(0)
        private set
    var leaderboardRank by mutableStateOf<Int?>(null)
        private set
    var lastPlay by mutableStateOf<PlayEvent?>(null)
        private set
    var lastClear by mutableStateOf(ClearBonus(0, 0, 0))
        private set
    var lastMisplay by mutableStateOf<MisplayEvent?>(null)
        private set
    /** Summe der Punkte der laufenden (bzw. zuletzt beendeten) Kette – nur für den Ketten-Banner. */
    var chainPoints by mutableIntStateOf(0)
        private set
    var walletCoins by mutableIntStateOf(0)
        private set
    var paused by mutableStateOf(false)
        private set
    var loaded by mutableStateOf(false)
        private set

    init {
        viewModelScope.launch {
            best = highscores.highscore(GalaxyTowers.GAME_ID, scoreKey).first()
            val json = saves.load(GalaxyTowers.GAME_ID, scoreKey)
            val saved = json?.let { GalaxyTowersSaves.decode(it, mode) }
                // Tägliche Challenge: nur Läufe mit dem heutigen Seed fortsetzen.
                ?.takeIf { mode != GalaxyTowersMode.DAILY || it.game.seed == DailyChallenge.todaySeed() }
            if (json != null && saved == null) saves.clear(GalaxyTowers.GAME_ID, scoreKey) // defekt/inkompatibel/veraltet
            if (saved != null) {
                bestAtStart = minOf(saved.bestAtStart, best)
                game = saved.game
                best = maxOf(best, saved.game.score)
                isNewBest = saved.game.score > bestAtStart
                // Nach dem Fortsetzen erst pausiert, damit die Zeit nicht sofort weiterläuft.
                if (game.inPlay && mode.timed) paused = true
            } else {
                bestAtStart = best
            }
            loaded = true
        }
        viewModelScope.launch { profile.state.collect { walletCoins = it.coins } }
    }

    fun play(index: Int): PlayResult? {
        if (paused) return null
        val result = GalaxyTowersEngine.play(game, index) ?: return null
        lastPlay = PlayEvent(
            id = ++eventCounter,
            index = index,
            card = game.tower[index]!!,
            pile = result.pile,
            points = result.points,
            streak = result.state.streak,
            peakCleared = result.peakCleared,
            reward = result.reward,
            secondPileOpened = result.secondPileOpened,
            gold = result.gold,
            timeDrop = result.timeDrop,
        )
        chainPoints = if (game.streak == 0) result.points else chainPoints + result.points
        if (result.roundCleared) lastClear = ClearBonus(result.clearBonus, result.timeBonus, result.coins - result.gold)
        profile.addCoins(result.coins)
        update(result.state)
        if (result.state.streak >= Achievements.GT_STREAK_TARGET) achievements.unlock(Achievements.GT_STREAK)
        if (result.roundCleared) achievements.unlock(Achievements.GT_ROUND_CLEAR)
        if (result.state.roundsCleared >= Achievements.GT_ROUND_TARGET) achievements.unlock(Achievements.GT_ROUND_5)
        return result
    }

    fun draw(): Boolean {
        if (paused) return false
        val next = GalaxyTowersEngine.draw(game) ?: return false
        update(next)
        return true
    }

    /** Offene, aber unpassende Karte angetippt: Strafpunkte. */
    fun misplay(index: Int): Boolean {
        if (paused) return false
        val next = GalaxyTowersEngine.misplay(game, index) ?: return false
        lastMisplay = MisplayEvent(++eventCounter, index, GalaxyTowersEngine.WRONG_CARD_PENALTY)
        update(next)
        return true
    }

    /** Rundenzeit, vom Frame-Loop des Screens aufgerufen. */
    fun tick(dt: Float) {
        if (paused || !loaded || !game.inPlay || !mode.timed) return
        val next = GalaxyTowersEngine.tick(game, dt)
        game = next
        sinceSave += dt
        if (!next.inPlay || sinceSave >= 1f) save(next)
    }

    /** Runde beenden, wenn keine Züge mehr möglich sind. */
    fun endRound() {
        if (GalaxyTowersEngine.isStuck(game)) update(GalaxyTowersEngine.endRound(game, RoundEnd.STUCK))
    }

    fun nextRound() {
        if (game.roundOver == null || game.isGameOver) return
        val next = GalaxyTowersEngine.nextRound(game)
        if (next.isGameOver) finish(next) else update(next)
    }

    fun usePowerUp(p: PowerUp): Boolean {
        if (paused) return false
        val next = GalaxyTowersEngine.use(game, p) ?: return false
        update(next)
        return true
    }

    private fun update(next: GameState) {
        // Ohne rettende Ladung endet eine festgefahrene Runde sofort.
        val state = if (GalaxyTowersEngine.isHopeless(next)) GalaxyTowersEngine.endRound(next, RoundEnd.STUCK) else next
        game = state
        if (state.score > best) best = state.score
        isNewBest = state.score > bestAtStart
        if (isNewBest) viewModelScope.launch { highscores.submit(GalaxyTowers.GAME_ID, scoreKey, state.score) }
        save(state)
    }

    private fun save(state: GameState) {
        sinceSave = 0f
        saves.save(GalaxyTowers.GAME_ID, scoreKey, GalaxyTowersSaves.encode(state, bestAtStart), state.score)
    }

    private fun finish(ended: GameState) {
        recordBonus = if (isNewBest) CoinRewards.NEW_RECORD_BONUS else 0
        game = ended
        profile.addCoins(recordBonus)
        saves.clear(GalaxyTowers.GAME_ID, scoreKey)
        val score = ended.score
        viewModelScope.launch {
            val rank = leaderboards.submit(
                LeaderboardKey(GalaxyTowers.GAME_ID, mode.id),
                LeaderboardEntry(score, System.currentTimeMillis(), details = "${ended.roundsCleared}/${mode.rounds} Runden abgeräumt"),
            )
            // Nur übernehmen, wenn inzwischen kein neues Spiel gestartet wurde.
            if (game.isGameOver && game.score == score) leaderboardRank = rank
        }
    }

    fun pause() {
        if (!game.isGameOver) paused = true
    }

    fun resume() {
        paused = false
    }

    fun restart() {
        saves.clear(GalaxyTowers.GAME_ID, scoreKey)
        bestAtStart = best
        game = GalaxyTowersEngine.newGame(newSeed(), mode)
        isNewBest = false
        recordBonus = 0
        leaderboardRank = null
        lastPlay = null
        lastClear = ClearBonus(0, 0, 0)
        lastMisplay = null
        chainPoints = 0
        paused = false
    }
}
