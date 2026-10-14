package com.adlessarcade.games.gaterush

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.adlessarcade.arcadeApp
import com.adlessarcade.core.DailyChallenge
import com.adlessarcade.core.shop.CoinRewards
import com.adlessarcade.ui.theme.NeonCyan
import com.adlessarcade.ui.theme.NeonPink
import com.adlessarcade.ui.theme.NeonYellow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class GateRushHud(
    val score: Int = 0,
    val hearts: Int = GateRushEngine.START_HEARTS,
    val damage: Float = 1f,
    val fireRate: Float = GateRushEngine.START_FIRE_RATE,
    val multishot: Int = 1,
    val pierce: Int = 0,
    val coins: Int = 0,
    val critChance: Float = 0f,
    val splash: Int = 0,
    val frost: Int = 0,
    val shields: Int = 0,
    val drones: Int = 0,
    val weapon: Weapon = Weapon.BLASTER,
    val frenzy: Float = 0f,
    val slowmo: Float = 0f,
    val wave: Int = 1,
    val bossPhase: Boolean = false,
)

/** Rein visuelle Effekte in Weltkoordinaten (Explosionen, schwebende Texte). */
sealed class GateRushEffect(val x: Float, val z: Float, val color: Color, val lifetime: Float) {
    var age = 0f
    val progress: Float get() = (age / lifetime).coerceIn(0f, 1f)

    class Burst(x: Float, z: Float, color: Color, val size: Float) : GateRushEffect(x, z, color, 0.4f)
    class FloatingText(x: Float, z: Float, color: Color, val text: String) : GateRushEffect(x, z, color, 1.2f)
    class CoinPop(x: Float, z: Float, val amount: Int) : GateRushEffect(x, z, Color.Unspecified, 1.0f)
}

private val GoodColor = Color(0xFF4ADE80)
private val BadColor = Color(0xFFFF5C6C)
private val ExplosionColor = Color(0xFFFF9F43)
private const val MAX_EFFECTS = 60

class GateRushViewModel(app: Application, savedStateHandle: SavedStateHandle) : AndroidViewModel(app) {
    private val highscores = app.arcadeApp.highscores
    private val saves = app.arcadeApp.saves
    private val profile = app.arcadeApp.profile

    val mode = GateRushMode.fromId(savedStateHandle.get<String>("modeId"))

    /** Schlüssel für Highscore und Spielstand (bei der Täglichen Challenge pro Tag). */
    private val scoreKey = if (mode == GateRushMode.DAILY) DailyChallenge.scoreKey(mode.id) else mode.id

    private fun newSeed(): Long = if (mode == GateRushMode.DAILY) DailyChallenge.todaySeed() else System.nanoTime()

    private var bestAtStart = 0

    var engine = GateRushEngine(newSeed(), mode)
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

    /** Großer Hinweis in der Bildmitte (z. B. „WELLE 3“, „RASEREI!“), verschwindet nach kurzer Zeit. */
    var banner by mutableStateOf<String?>(null)
        private set
    private var bannerTimer = 0f

    /** Zähler für Haptik-Ereignisse, die der Screen beobachtet. */
    var hurtTick by mutableIntStateOf(0)
        private set
    var rewardTick by mutableIntStateOf(0)
        private set

    /** Roter Bildschirmblitz nach Herzverlust, 1 → 0. */
    var damageFlash = 0f
        private set

    /** false, bis Highscore und ggf. gespeicherter Lauf geladen sind. */
    var loaded by mutableStateOf(false)
        private set

    /** Coin-Bonus für einen neuen Rekord, gutgeschrieben bei Game Over. */
    var recordBonus by mutableIntStateOf(0)
        private set

    /** Bereits ins Profil gebuchte Coins dieses Laufs; Gutschriften werden gebündelt. */
    private var coinsBooked = 0
    private var coinFlushTimer = 0f

    init {
        viewModelScope.launch {
            val stored = highscores.highscore(GateRush.GAME_ID, scoreKey).first()
            val json = saves.load(GateRush.GAME_ID, scoreKey)
            val saved = json?.let(GateRushSaves::decode)
                // Tägliche Challenge: nur Läufe mit dem heutigen Seed fortsetzen.
                ?.takeIf { mode != GateRushMode.DAILY || it.seed == DailyChallenge.todaySeed() }
            if (json != null && saved == null) saves.clear(GateRush.GAME_ID, scoreKey) // defekt/inkompatibel/veraltet
            if (saved != null) {
                engine = GateRushEngine(saved.seed, mode).also { it.restore(saved.snapshot) }
                coinsBooked = engine.coinsEarned // wurden beim Unterbrechen bereits gutgeschrieben
                bestAtStart = minOf(saved.bestAtStart, stored)
                best = maxOf(stored, engine.score)
                isNewBest = engine.score > bestAtStart
                refreshHud()
                // Fortgesetzter Lauf startet pausiert, damit man in Ruhe weitermachen kann.
                started = true
                paused = true
            } else {
                bestAtStart = stored
                best = maxOf(best, stored)
            }
            loaded = true
            frame++
        }
    }

    override fun onCleared() {
        // Absicherung, falls der Screen ohne ON_PAUSE verlassen wird.
        flushCoins()
        saveRun()
    }

    private fun flushCoins() {
        val delta = engine.coinsEarned - coinsBooked
        if (delta > 0) {
            profile.addCoins(delta)
            coinsBooked = engine.coinsEarned
        }
    }

    private fun saveRun() {
        if (!started || gameOver || engine.isGameOver) return
        val json = GateRushSaves.encode(engine.snapshot(), bestAtStart, engine.seed)
        saves.save(GateRush.GAME_ID, scoreKey, json, engine.score)
    }

    private fun refreshHud() {
        val stats = engine.stats
        hud = GateRushHud(
            score = engine.score,
            hearts = stats.hearts,
            damage = stats.damage,
            fireRate = stats.fireRate,
            multishot = stats.multishot,
            pierce = stats.pierce,
            coins = engine.coinsEarned,
            critChance = stats.critChance,
            splash = stats.splash,
            frost = stats.frost,
            shields = stats.shields,
            drones = stats.drones,
            weapon = stats.weapon,
            frenzy = stats.frenzy,
            slowmo = stats.slowmo,
            wave = engine.wave,
            bossPhase = engine.bossPhase,
        )
    }

    fun step(dt: Float) {
        if (!started || paused || gameOver) return
        engine.update(dt)
        handleEvents()
        updateEffects(dt)
        damageFlash = (damageFlash - dt * 2.5f).coerceAtLeast(0f)
        if (banner != null) {
            bannerTimer -= dt
            if (bannerTimer <= 0f) banner = null
        }

        refreshHud()
        if (engine.score > best) best = engine.score
        isNewBest = engine.score > bestAtStart

        coinFlushTimer += dt
        if (coinFlushTimer >= 1f) {
            coinFlushTimer = 0f
            flushCoins()
        }

        if (engine.isGameOver) {
            gameOver = true
            submitScore()
            flushCoins()
            if (isNewBest) {
                recordBonus = CoinRewards.NEW_RECORD_BONUS
                profile.addCoins(recordBonus)
            }
            saves.clear(GateRush.GAME_ID, scoreKey)
        }
        frame++
    }

    fun movePlayer(dx: Float) {
        if (!loaded || paused || gameOver) return
        started = true
        engine.movePlayer(dx)
        frame++
    }

    fun pause() {
        if (!started || gameOver) return
        paused = true
        submitScore()
        flushCoins()
        saveRun()
    }

    fun resume() {
        paused = false
    }

    fun restart() {
        flushCoins()
        saves.clear(GateRush.GAME_ID, scoreKey)
        bestAtStart = best
        engine = GateRushEngine(newSeed(), mode)
        coinsBooked = 0
        coinFlushTimer = 0f
        recordBonus = 0
        effects.clear()
        hud = GateRushHud()
        damageFlash = 0f
        banner = null
        started = false
        paused = false
        gameOver = false
        isNewBest = false
        frame++
    }

    private fun submitScore() {
        val score = engine.score
        if (score > bestAtStart) {
            viewModelScope.launch { highscores.submit(GateRush.GAME_ID, scoreKey, score) }
        }
    }

    private fun showBanner(text: String) {
        banner = text
        bannerTimer = 1.6f
    }

    private fun handleEvents() {
        for (event in engine.drainEvents()) {
            when (event) {
                is GateRushEvent.EnemyKilled -> {
                    val size = if (event.type == EnemyType.BOSS) 0.9f else event.type.radius * 2.5f
                    if (effects.size < MAX_EFFECTS || event.type == EnemyType.BOSS) {
                        effects += GateRushEffect.Burst(event.x, event.z, NeonPink, size)
                    }
                    if (event.type == EnemyType.BOSS || event.type == EnemyType.BRUTE) {
                        effects += GateRushEffect.FloatingText(event.x, event.z, NeonYellow, "+${event.type.score}")
                    }
                    if (event.type == EnemyType.BOSS && mode == GateRushMode.BOSS_RUSH) showBanner("BOSS BESIEGT!")
                }
                is GateRushEvent.HeartsLost -> {
                    damageFlash = 1f
                    hurtTick++
                }
                is GateRushEvent.ObstacleHit -> {
                    effects += GateRushEffect.FloatingText(engine.playerX, 0.5f, BadColor, "AUTSCH!")
                }
                is GateRushEvent.GatePassed -> {
                    effects += GateRushEffect.FloatingText(
                        engine.playerX, 0.5f, if (event.positive) GoodColor else BadColor, event.text,
                    )
                    if (event.positive) rewardTick++ else hurtTick++
                }
                is GateRushEvent.CoinsEarned -> {
                    effects += GateRushEffect.CoinPop(event.x, event.z, event.amount)
                }
                is GateRushEvent.ShieldBlocked -> {
                    effects += GateRushEffect.FloatingText(engine.playerX, 0.5f, NeonCyan, "SCHILD!")
                    hurtTick++
                }
                is GateRushEvent.Explosion -> {
                    // Bei Dauerfeuer entstehen sehr viele Explosionen – Effekte begrenzen.
                    if (effects.size < MAX_EFFECTS) {
                        effects += GateRushEffect.Burst(event.x, event.z, ExplosionColor, event.radius * 2f)
                    }
                }
                is GateRushEvent.CrateOpened -> {
                    effects += GateRushEffect.Burst(event.x, event.z, NeonYellow, 0.5f)
                    effects += GateRushEffect.FloatingText(event.x, event.z, NeonCyan, event.text)
                    rewardTick++
                }
                is GateRushEvent.PowerUpCollected -> {
                    showBanner("${event.kind.title}!")
                    if (event.kind == PowerUpKind.BOMB) {
                        effects += GateRushEffect.Burst(engine.playerX, 10f, NeonYellow, 3f)
                    }
                    rewardTick++
                }
                is GateRushEvent.WaveStarted -> {
                    showBanner("WELLE ${event.wave}")
                    hurtTick++
                }
            }
        }
    }

    private fun updateEffects(dt: Float) {
        effects.forEach { it.age += dt }
        effects.removeAll { it.age >= it.lifetime }
    }
}
