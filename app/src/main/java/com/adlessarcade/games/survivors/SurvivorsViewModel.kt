package com.adlessarcade.games.survivors

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
import com.adlessarcade.core.shop.CoinRewards
import com.adlessarcade.core.shop.ShopCatalog
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Eine Waffe oder ein Talent in der Build-Anzeige des HUD. */
data class BuildSlot(val icon: String, val title: String, val level: Int, val evolved: Boolean)

data class SurvivorsHud(
    val hp: Float = 0f,
    val maxHp: Float = 1f,
    val level: Int = 1,
    val xp: Int = 0,
    val xpNeeded: Int = 1,
    val time: Float = 0f,
    val kills: Int = 0,
    val coins: Int = 0,
    val score: Int = 0,
    val weapons: List<BuildSlot> = emptyList(),
    val passives: List<BuildSlot> = emptyList(),
)

/**
 * Eine Kisten-Belohnung für die Roll-Animation. [rollId] ändert sich mit jeder neuen Belohnung,
 * [animate] = false zeigt das Ergebnis sofort (z. B. nach dem Fortsetzen eines gespeicherten Laufs).
 */
data class ChestRoll(
    val offer: UpgradeOffer,
    val index: Int,
    val total: Int,
    val rollId: Long,
    val animate: Boolean,
    /** Mögliche Belohnungen, die über die Walze laufen. */
    val reel: List<UpgradeOffer>,
)

private const val MAX_FX = 70

class SurvivorsViewModel(app: Application, savedStateHandle: SavedStateHandle) : AndroidViewModel(app) {
    private val highscores = app.arcadeApp.highscores
    private val saves = app.arcadeApp.saves
    private val profile = app.arcadeApp.profile

    val mode = SurvivorsMode.fromId(savedStateHandle.get<String>("modeId"))
    private val scoreKey = mode.id

    /** null = Klassenwahl (noch kein Lauf). */
    var engine by mutableStateOf<SurvivorsEngine?>(null)
        private set
    val effects = ArrayList<SurvivorsFx>()

    var frame by mutableLongStateOf(0L)
        private set
    var hud by mutableStateOf(SurvivorsHud())
        private set
    var offers by mutableStateOf<List<UpgradeOffer>>(emptyList())
        private set
    /** Aktuell ausgerollte Kisten-Belohnung (null = keine Kiste offen). */
    var chest by mutableStateOf<ChestRoll?>(null)
        private set
    var paused by mutableStateOf(false)
        private set
    var finished by mutableStateOf(false)
        private set
    var victory by mutableStateOf(false)
        private set
    var best by mutableIntStateOf(0)
        private set
    var isNewBest by mutableStateOf(false)
        private set
    var recordBonus by mutableIntStateOf(0)
        private set
    var loaded by mutableStateOf(false)
        private set
    var banner by mutableStateOf<String?>(null)
        private set
    private var bannerTimer = 0f

    var hurtTick by mutableIntStateOf(0)
        private set
    var rewardTick by mutableIntStateOf(0)
        private set
    var damageFlash = 0f
        private set

    private var bestAtStart = 0
    private var coinsBooked = 0
    private var coinFlushTimer = 0f

    init {
        viewModelScope.launch {
            val stored = highscores.highscore(Survivors.GAME_ID, scoreKey).first()
            val json = saves.load(Survivors.GAME_ID, scoreKey)
            val saved = json?.let(SurvivorsSaves::decode)
            if (json != null && saved == null) saves.clear(Survivors.GAME_ID, scoreKey)
            if (saved != null) {
                val restored = SurvivorsEngine(saved.state, System.nanoTime())
                coinsBooked = saved.state.coinsEarned
                bestAtStart = minOf(saved.bestAtStart, stored)
                best = maxOf(stored, restored.score)
                engine = restored
                refreshHud()
                syncChest(restored, animate = false)
                paused = true // fortgesetzter Lauf startet pausiert
            } else {
                bestAtStart = stored
                best = stored
            }
            loaded = true
            frame++
        }
    }

    /** Startet einen neuen Lauf mit der gewählten Klasse und merkt sie sich als Standard. */
    fun start(heroClass: HeroClass) {
        ShopCatalog.find(heroClass.shopId)?.let(profile::equip)
        engine = SurvivorsEngine.newGame(heroClass, mode, System.nanoTime())
        effects.clear()
        coinsBooked = 0
        coinFlushTimer = 0f
        recordBonus = 0
        isNewBest = false
        finished = false
        victory = false
        paused = false
        offers = emptyList()
        chest = null
        refreshHud()
        frame++
    }

    fun setInput(x: Float, y: Float) {
        engine?.let {
            it.inputX = x
            it.inputY = y
        }
    }

    fun choose(index: Int) {
        val e = engine ?: return
        e.choose(index)
        offers = e.state.offers
        refreshHud()
        frame++
    }

    fun acceptChest() {
        val e = engine ?: return
        e.acceptChestReward()
        afterChest(e)
    }

    fun declineChest() {
        val e = engine ?: return
        e.declineChestReward()
        afterChest(e)
    }

    private fun afterChest(e: SurvivorsEngine) {
        handleEvents(e) // rollt ggf. die nächste Belohnung aus
        if (!e.awaitingChest) chest = null
        offers = e.state.offers
        refreshHud()
        frame++
    }

    /** Übernimmt die aktuelle Kisten-Belohnung der Engine in den UI-Zustand. */
    private fun syncChest(e: SurvivorsEngine, animate: Boolean) {
        val offer = e.state.chestReward
        if (offer == null) {
            chest = null
            return
        }
        val reel = (Upgrades.pool(e.state) + offer).distinct()
        chest = ChestRoll(offer, e.state.chestIndex, e.state.chestTotal, (chest?.rollId ?: 0L) + 1, animate, reel)
    }

    fun step(dt: Float) {
        val e = engine ?: return
        if (paused || finished) return
        if (banner != null) {
            bannerTimer -= dt
            if (bannerTimer <= 0f) banner = null
        }
        if (e.isWaiting) {
            offers = e.state.offers
            return
        }
        e.update(dt)
        handleEvents(e)
        effects.forEach { it.age += dt }
        effects.removeAll { it.age >= it.lifetime }
        damageFlash = (damageFlash - dt * 2.5f).coerceAtLeast(0f)
        offers = e.state.offers
        refreshHud()
        if (e.score > best) best = e.score
        isNewBest = e.score > bestAtStart

        coinFlushTimer += dt
        if (coinFlushTimer >= 1f) {
            coinFlushTimer = 0f
            flushCoins()
        }

        if (e.isOver) {
            finished = true
            victory = e.state.victory
            submitScore()
            flushCoins()
            if (isNewBest) {
                recordBonus = CoinRewards.NEW_RECORD_BONUS
                profile.addCoins(recordBonus)
            }
            saves.clear(Survivors.GAME_ID, scoreKey)
        }
        frame++
    }

    fun pause() {
        if (engine == null || finished) return
        paused = true
        setInput(0f, 0f)
        submitScore()
        flushCoins()
        saveRun()
    }

    fun resume() {
        paused = false
    }

    /** Zurück zur Klassenwahl für einen neuen Lauf. */
    fun restart() {
        flushCoins()
        saves.clear(Survivors.GAME_ID, scoreKey)
        bestAtStart = best
        engine = null
        effects.clear()
        offers = emptyList()
        chest = null
        finished = false
        victory = false
        paused = false
        banner = null
        frame++
    }

    override fun onCleared() {
        flushCoins()
        saveRun()
    }

    private fun saveRun() {
        val e = engine ?: return
        if (finished || e.isOver) return
        saves.save(Survivors.GAME_ID, scoreKey, SurvivorsSaves.encode(e.state, bestAtStart), e.score)
    }

    private fun flushCoins() {
        val e = engine ?: return
        val delta = e.state.coinsEarned - coinsBooked
        if (delta > 0) {
            profile.addCoins(delta)
            coinsBooked = e.state.coinsEarned
        }
    }

    private fun submitScore() {
        val score = engine?.score ?: return
        if (score > bestAtStart) viewModelScope.launch { highscores.submit(Survivors.GAME_ID, scoreKey, score) }
    }

    private fun refreshHud() {
        val e = engine ?: return
        val s = e.state
        hud = SurvivorsHud(
            hp = s.hp,
            maxHp = e.stats.maxHp,
            level = s.level,
            xp = s.xp,
            xpNeeded = SurvivorsEngine.xpForLevel(s.level),
            time = s.time,
            kills = s.kills,
            coins = s.coinsEarned,
            score = e.score,
            weapons = s.weapons.map { (w, lv) -> BuildSlot(if (w in s.evolved) w.evolutionIcon else w.icon, w.title, lv, w in s.evolved) },
            passives = s.passives.map { (p, lv) -> BuildSlot(p.icon, p.title, lv, false) },
        )
    }

    private fun showBanner(text: String) {
        banner = text
        bannerTimer = 2f
    }

    private fun handleEvents(e: SurvivorsEngine) {
        for (event in e.drainEvents()) {
            when (event) {
                is SurvivorsEvent.Damage -> if (effects.size < MAX_FX) {
                    effects += SurvivorsFx.Number(event.x, event.y, event.amount.toInt().coerceAtLeast(1))
                }
                is SurvivorsEvent.EnemyKilled -> if (event.elite || event.kind == EnemyKind.BOSS || event.kind == EnemyKind.DEMON) {
                    effects += SurvivorsFx.Blast(event.x, event.y, 1.5f, Color(0xFFFACC15))
                }
                is SurvivorsEvent.PlayerHit -> {
                    damageFlash = 1f
                    hurtTick++
                }
                is SurvivorsEvent.LevelUp -> rewardTick++
                is SurvivorsEvent.ChestReward -> syncChest(e, animate = true)
                is SurvivorsEvent.Evolved -> {
                    showBanner("${event.weapon.evolutionTitle.uppercase()}!")
                    effects += SurvivorsFx.Blast(e.state.px, e.state.py, 3f, Color(0xFFFACC15))
                }
                is SurvivorsEvent.BossSpawned -> {
                    showBanner(if (event.kind == EnemyKind.DEMON) "DER ENDBOSS NAHT!" else "${event.kind.title.uppercase()} ERSCHEINT!")
                    hurtTick++
                }
                is SurvivorsEvent.Victory -> showBanner("SIEG!")
                is SurvivorsEvent.CoinsEarned -> effects += SurvivorsFx.CoinPop(event.x, event.y, event.amount)
                is SurvivorsEvent.Healed -> effects += SurvivorsFx.Text(e.state.px, e.state.py, "+${event.amount.toInt()}", Color(0xFF4ADE80))
                is SurvivorsEvent.Swing ->
                    effects += SurvivorsFx.Swing(event.x, event.y, event.angle, event.range, event.fullCircle, event.evolved)
                is SurvivorsEvent.Lightning -> if (effects.size < MAX_FX) effects += SurvivorsFx.Bolt(event.x, event.y, event.radius)
                is SurvivorsEvent.Chain -> if (effects.size < MAX_FX) effects += SurvivorsFx.Arc(event.x1, event.y1, event.x2, event.y2)
                is SurvivorsEvent.Nova -> effects += SurvivorsFx.Nova(event.x, event.y, event.radius, event.freeze)
                is SurvivorsEvent.Explosion -> if (effects.size < MAX_FX) {
                    effects += SurvivorsFx.Blast(event.x, event.y, event.radius, Color(0xFFF97316))
                }
            }
        }
    }
}
