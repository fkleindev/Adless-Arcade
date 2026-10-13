package com.adlessarcade.games.gaterush

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.adlessarcade.arcadeApp
import com.adlessarcade.ui.theme.NeonCyan
import com.adlessarcade.ui.theme.NeonPink
import com.adlessarcade.ui.theme.NeonYellow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.random.Random

data class GateRushHud(
    val score: Int = 0,
    val hearts: Int = GateRushEngine.START_HEARTS,
    val damage: Float = 1f,
    val fireRate: Float = 4f,
    val multishot: Int = 1,
    val pierce: Int = 0,
)

/** Rein visuelle Effekte in Weltkoordinaten (Explosionen, schwebende Texte). */
sealed class GateRushEffect(val x: Float, val z: Float, val color: Color, val lifetime: Float) {
    var age = 0f
    val progress: Float get() = (age / lifetime).coerceIn(0f, 1f)

    class Burst(x: Float, z: Float, color: Color, val size: Float) : GateRushEffect(x, z, color, 0.4f)
    class FloatingText(x: Float, z: Float, color: Color, val text: String) : GateRushEffect(x, z, color, 1.2f)
}

private val GoodColor = Color(0xFF4ADE80)
private val BadColor = Color(0xFFFF5C6C)

class GateRushViewModel(app: Application) : AndroidViewModel(app) {
    private val highscores = app.arcadeApp.highscores
    private val modeId = GateRush.MODE_ENDLESS
    private var bestAtStart = 0

    var engine = GateRushEngine(Random(System.nanoTime()))
        private set
    val effects = ArrayList<GateRushEffect>()

    /** Wird jeden Frame erhöht und vom Canvas gelesen, um neu zu zeichnen. */
    var frame by mutableLongStateOf(0L)
        private set
    var hud by mutableStateOf(GateRushHud())
        private set
    var started by mutableStateOf(false)
        private set
    var paused by mutableStateOf(false)
        private set
    var gameOver by mutableStateOf(false)
        private set
    var best by mutableIntStateOf(0)
        private set
    var isNewBest by mutableStateOf(false)
        private set

    /** Zähler für Haptik-Ereignisse, die der Screen beobachtet. */
    var hurtTick by mutableIntStateOf(0)
        private set
    var rewardTick by mutableIntStateOf(0)
        private set

    /** Roter Bildschirmblitz nach Herzverlust, 1 → 0. */
    var damageFlash = 0f
        private set

    init {
        viewModelScope.launch {
            bestAtStart = highscores.highscore(GateRush.GAME_ID, modeId).first()
            best = maxOf(best, bestAtStart)
        }
    }

    fun step(dt: Float) {
        if (!started || paused || gameOver) return
        engine.update(dt)
        handleEvents()
        updateEffects(dt)
        damageFlash = (damageFlash - dt * 2.5f).coerceAtLeast(0f)

        val stats = engine.stats
        hud = GateRushHud(
            score = engine.score,
            hearts = stats.hearts,
            damage = stats.damage,
            fireRate = stats.fireRate,
            multishot = stats.multishot,
            pierce = stats.pierce,
        )
        if (engine.score > best) best = engine.score
        isNewBest = engine.score > bestAtStart
        if (engine.isGameOver) {
            gameOver = true
            submitScore()
        }
        frame++
    }

    fun movePlayer(dx: Float) {
        if (paused || gameOver) return
        started = true
        engine.movePlayer(dx)
        frame++
    }

    fun pause() {
        if (!started || gameOver) return
        paused = true
        submitScore()
    }

    fun resume() {
        paused = false
    }

    fun restart() {
        bestAtStart = best
        engine = GateRushEngine(Random(System.nanoTime()))
        effects.clear()
        hud = GateRushHud()
        damageFlash = 0f
        started = false
        paused = false
        gameOver = false
        isNewBest = false
        frame++
    }

    private fun submitScore() {
        val score = engine.score
        if (score > bestAtStart) {
            viewModelScope.launch { highscores.submit(GateRush.GAME_ID, modeId, score) }
        }
    }

    private fun handleEvents() {
        for (event in engine.drainEvents()) {
            when (event) {
                is GateRushEvent.EnemyKilled -> {
                    val size = if (event.type == EnemyType.BOSS) 0.9f else event.type.radius * 2.5f
                    effects += GateRushEffect.Burst(event.x, event.z, NeonPink, size)
                    if (event.type == EnemyType.BOSS || event.type == EnemyType.BRUTE) {
                        effects += GateRushEffect.FloatingText(event.x, event.z, NeonYellow, "+${event.type.score}")
                    }
                }
                is GateRushEvent.HeartsLost -> {
                    damageFlash = 1f
                    hurtTick++
                }
                is GateRushEvent.GatePassed -> {
                    effects += GateRushEffect.FloatingText(
                        engine.playerX, 0.5f, if (event.positive) GoodColor else BadColor, event.text,
                    )
                    if (event.positive) rewardTick++ else hurtTick++
                }
                is GateRushEvent.CrateOpened -> {
                    effects += GateRushEffect.Burst(event.x, event.z, NeonYellow, 0.5f)
                    effects += GateRushEffect.FloatingText(event.x, event.z, NeonCyan, event.text)
                    rewardTick++
                }
            }
        }
    }

    private fun updateEffects(dt: Float) {
        effects.forEach { it.age += dt }
        effects.removeAll { it.age >= it.lifetime }
    }
}
