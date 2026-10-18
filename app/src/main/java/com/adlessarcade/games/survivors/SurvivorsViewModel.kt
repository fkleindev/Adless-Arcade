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
import com.adlessarcade.core.achievements.AchievementTracker
import com.adlessarcade.core.achievements.Achievements
import com.adlessarcade.core.leaderboard.LeaderboardEntry
import com.adlessarcade.core.leaderboard.LeaderboardKey
import com.adlessarcade.core.shop.CoinRewards
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Eine Waffe oder ein Talent in der Build-Anzeige des HUD. */
data class BuildSlot(val icon: String, val title: String, val level: Int, val evolved: Boolean)

data class SurvivorsHud(
    val hp: Float = 0f,
    val maxHp: Float = 1f,
    /** Gottesschild des Paladins. */
    val shield: Float = 0f,
    val level: Int = 1,
    val xp: Int = 0,
    val xpNeeded: Int = 1,
    val time: Float = 0f,
    val kills: Int = 0,
    val coins: Int = 0,
    val runes: Int = 0,
    val score: Int = 0,
    val weapons: List<BuildSlot> = emptyList(),
    val passives: List<BuildSlot> = emptyList(),
    /** Items mit Stapelanzahl (als [BuildSlot.level]). */
    val items: List<BuildSlot> = emptyList(),
    /** Seelen des Nekromanten (null bei anderen Klassen). */
    val souls: Int? = null,
    /** Regeneration in Leben pro Sekunde. */
    val regen: Float = 0f,
)

/**
 * Eine geöffnete Kiste für die Roll-Animation. [rollId] ändert sich mit jeder neuen Kiste,
 * [animate] = false zeigt das Ergebnis sofort (z. B. nach dem Fortsetzen eines gespeicherten Laufs).
 */
data class ChestRoll(
    /** Die drei Belohnungen, aus denen gewählt wird. */
    val offers: List<UpgradeOffer>,
    /** Weitere Kisten, die danach geöffnet werden. */
    val remaining: Int,
    val rollId: Long,
    val animate: Boolean,
    /** Mögliche Belohnungen, die über die Walzen laufen. */
    val reel: List<UpgradeOffer>,
)

private const val MAX_FX = 160

class SurvivorsViewModel(app: Application, savedStateHandle: SavedStateHandle) : AndroidViewModel(app) {
    private val highscores = app.arcadeApp.highscores
    private val saves = app.arcadeApp.saves
    private val leaderboards = app.arcadeApp.leaderboards
    private val profile = app.arcadeApp.profile
    private val progress = app.arcadeApp.survivorsProgress
    private val achievements = AchievementTracker(profile)

    val mode = SurvivorsMode.fromId(savedStateHandle.get<String>("modeId"))
    /** Spielstand-Slot pro Modus; Rekorde pro Modus und Stage. */
    private val saveKey = mode.id
    private var scoreKey = mode.id

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
    /** Platz in der Bestenliste der Klasse nach Spielende (null = nicht in den Top 10 bzw. noch offen). */
    var leaderboardRank by mutableStateOf<Int?>(null)
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
    private var runesBooked = 0
    /** Bereits verbuchte Kills pro Waffe (für die Freischalt-Voraussetzungen der Runen-Schmiede). */
    private val killsBooked = HashMap<WeaponKind, Int>()
    private var coinFlushTimer = 0f

    init {
        viewModelScope.launch {
            val json = saves.load(Survivors.GAME_ID, saveKey)
            val saved = json?.let(SurvivorsSaves::decode)
            if (json != null && saved == null) saves.clear(Survivors.GAME_ID, saveKey)
            // Rekord der Stage des fortgesetzten Laufs (sonst der Wiese bis zum Start)
            scoreKey = (saved?.state?.stage ?: SurvivorsStage.MEADOW).scoreKey(mode)
            val stored = highscores.highscore(Survivors.GAME_ID, scoreKey).first()
            if (saved != null) {
                val restored = SurvivorsEngine(saved.state, System.nanoTime())
                coinsBooked = saved.state.coinsEarned
                runesBooked = saved.state.runesEarned
                killsBooked += saved.state.weaponKills
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
    fun start(
        heroClass: HeroClass,
        availableWeapons: Set<WeaponKind> = WeaponKind.BASE,
        stage: SurvivorsStage = SurvivorsStage.MEADOW,
    ) {
        progress.select(heroClass.shopId)
        progress.selectStage(stage)
        val key = stage.scoreKey(mode)
        if (key != scoreKey) {
            // Rekord der gewählten Stage laden; bis dahin zählt kein Lauf als neuer Rekord.
            scoreKey = key
            bestAtStart = Int.MAX_VALUE
            best = 0
            viewModelScope.launch {
                val stored = highscores.highscore(Survivors.GAME_ID, key).first()
                if (scoreKey == key) {
                    bestAtStart = stored
                    best = maxOf(best, stored)
                }
            }
        }
        engine = SurvivorsEngine.newGame(heroClass, mode, System.nanoTime(), availableWeapons, stage)
        effects.clear()
        coinsBooked = 0
        runesBooked = 0
        killsBooked.clear()
        coinFlushTimer = 0f
        recordBonus = 0
        leaderboardRank = null
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

    fun chooseChest(index: Int) {
        val e = engine ?: return
        e.chooseChestReward(index)
        handleEvents(e) // öffnet ggf. die nächste Kiste
        if (!e.awaitingChest) chest = null
        offers = e.state.offers
        refreshHud()
        frame++
    }

    /** Übernimmt die geöffnete Kiste der Engine in den UI-Zustand. */
    private fun syncChest(e: SurvivorsEngine, animate: Boolean) {
        val offers = e.state.chestOffers
        if (offers.isEmpty()) {
            chest = null
            return
        }
        val reel = (Upgrades.pool(e.state) + ItemKind.entries.map { UpgradeOffer(item = it) } + offers).distinct()
        chest = ChestRoll(offers, e.state.pendingChests, (chest?.rollId ?: 0L) + 1, animate, reel)
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
        checkAchievements(e)
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
            saves.clear(Survivors.GAME_ID, saveKey)
            submitToLeaderboard(e)
            progress.recordRun(e.state.victory, e.state.bossesKilled, e.state.time.toInt(), e.state.stage)
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
        saves.clear(Survivors.GAME_ID, saveKey)
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
        saves.save(Survivors.GAME_ID, saveKey, SurvivorsSaves.encode(e.state, bestAtStart), e.score)
    }

    /** Bucht neu verdiente Coins (Profil) und Runen (Runen-Schmiede) gebündelt. */
    private fun flushCoins() {
        val e = engine ?: return
        val delta = e.state.coinsEarned - coinsBooked
        if (delta > 0) {
            profile.addCoins(delta)
            coinsBooked = e.state.coinsEarned
        }
        val kills = e.state.weaponKills.mapValues { (w, n) -> n - (killsBooked[w] ?: 0) }
        if (kills.values.any { it > 0 }) {
            progress.addWeaponKills(kills)
            killsBooked.clear()
            killsBooked += e.state.weaponKills
        }
        val runes = e.state.runesEarned - runesBooked
        if (runes > 0) {
            progress.addRunes(runes)
            runesBooked = e.state.runesEarned
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
            shield = s.shield,
            level = s.level,
            xp = s.xp,
            xpNeeded = SurvivorsEngine.xpForLevel(s.level),
            time = s.time,
            kills = s.kills,
            coins = s.coinsEarned,
            runes = s.runesEarned,
            score = e.score,
            weapons = s.weapons.map { (w, lv) -> BuildSlot(if (w in s.evolved) w.evolutionIcon else w.icon, w.title, lv, w in s.evolved) },
            passives = s.passives.map { (p, lv) -> BuildSlot(p.icon, p.title, lv, false) },
            items = s.items.map { (i, n) -> BuildSlot(i.icon, i.title, n, false) },
            souls = if (s.heroClass == HeroClass.NECROMANCER) s.souls else null,
            regen = e.stats.regen,
        )
    }

    private fun submitToLeaderboard(e: SurvivorsEngine) {
        val s = e.state
        val seconds = s.time.toInt()
        val details = "%d:%02d · %d Kills · Stufe %d".format(seconds / 60, seconds % 60, s.kills, s.level)
        viewModelScope.launch {
            val rank = leaderboards.submit(
                LeaderboardKey(Survivors.GAME_ID, mode.id, s.stage.leaderboardVariant(s.heroClass)),
                LeaderboardEntry(e.score, System.currentTimeMillis(), details, victory = s.victory),
            )
            if (engine === e) leaderboardRank = rank
        }
    }

    private fun checkAchievements(e: SurvivorsEngine) {
        val s = e.state
        if (s.evolved.isNotEmpty()) achievements.unlock(Achievements.SV_EVOLUTION)
        if (s.kills >= Achievements.SV_KILLS_TARGET) achievements.unlock(Achievements.SV_KILLS)
        if (s.mode == SurvivorsMode.TIMED && (s.victory || s.victorySweep)) achievements.unlock(Achievements.SV_VICTORY)
        if (s.mode == SurvivorsMode.ENDLESS && !s.dead && s.time >= Achievements.SV_ENDLESS_SECONDS) {
            achievements.unlock(Achievements.SV_ENDLESS_20MIN)
        }
    }

    private fun showBanner(text: String) {
        banner = text
        bannerTimer = 2f
    }

    private fun handleEvents(e: SurvivorsEngine) {
        for (event in e.drainEvents()) {
            when (event) {
                is SurvivorsEvent.Damage -> if (effects.size < MAX_FX || event.crit == 2) {
                    effects += SurvivorsFx.Number(event.x, event.y, event.amount.toInt().coerceAtLeast(1), event.crit, event.kind)
                }
                is SurvivorsEvent.Dodged -> if (effects.size < MAX_FX) {
                    effects += SurvivorsFx.Text(event.x, event.y, "Ausgewichen", Color(0xFFCBD5E1))
                }
                is SurvivorsEvent.EnemyKilled -> if (event.elite || event.kind.isFinalBoss || event.kind.isMidBoss) {
                    effects += SurvivorsFx.Blast(event.x, event.y, 1.5f, Color(0xFFFACC15))
                } else if (effects.size < MAX_FX) {
                    effects += SurvivorsFx.Puff(event.x, event.y, event.kind.radius)
                }
                is SurvivorsEvent.PlayerHit -> {
                    damageFlash = 1f
                    hurtTick++
                }
                is SurvivorsEvent.LevelUp -> rewardTick++
                is SurvivorsEvent.ChestOpened -> syncChest(e, animate = true)
                is SurvivorsEvent.Evolved -> {
                    showBanner("${event.weapon.evolutionTitle.uppercase()}!")
                    effects += SurvivorsFx.Blast(e.state.px, e.state.py, 3f, Color(0xFFFACC15))
                }
                is SurvivorsEvent.BossSpawned -> {
                    showBanner(if (event.kind.isFinalBoss) "DER ENDBOSS NAHT!" else "${event.kind.title.uppercase()} ERSCHEINT!")
                    hurtTick++
                }
                is SurvivorsEvent.Victory -> showBanner("SIEG!")
                is SurvivorsEvent.Enraged -> {
                    showBanner("DIE HORDE WIRD RASEND!")
                    hurtTick++
                }
                is SurvivorsEvent.CoinsEarned -> effects += SurvivorsFx.CoinPop(event.x, event.y, event.amount)
                is SurvivorsEvent.RunesEarned -> effects += SurvivorsFx.RunePop(event.x, event.y, event.amount)
                is SurvivorsEvent.Healed -> effects += SurvivorsFx.Text(e.state.px, e.state.py, "+${event.amount.toInt()}", Color(0xFF4ADE80))
                is SurvivorsEvent.RuneStoneSpawned -> showBanner("EIN RUNENSTEIN ERSCHEINT!")
                is SurvivorsEvent.RuneStoneActivated -> {
                    showBanner("RUNENSTEIN AKTIVIERT!")
                    effects += SurvivorsFx.Blast(event.x, event.y, 2.2f, RuneViolet)
                    rewardTick++
                }
                is SurvivorsEvent.MapEventSpawned -> showBanner("EIN EVENT ERSCHEINT!\nBetritt die markierte Zone")
                is SurvivorsEvent.MapEventStarted ->
                    showBanner("SÄUBERUNG!\nBesiege ${event.target} Gegner in der Zone (${event.timeLimit.toInt()} s)")
                is SurvivorsEvent.MapEventCompleted -> {
                    val stars = "★".repeat(event.stars) + "☆".repeat(3 - event.stars)
                    showBanner("EVENT GESCHAFFT! $stars\n+${event.xp} Erfahrung")
                    effects += SurvivorsFx.Blast(event.x, event.y, SurvivorsEngine.MAP_EVENT_RADIUS, EventAmber)
                    rewardTick++
                }
                is SurvivorsEvent.MapEventFailed -> showBanner("EVENT GESCHEITERT")
                is SurvivorsEvent.GoalCompleted -> {
                    showBanner("ZIEL ERREICHT: ${event.goal.title}\n$RUNE_SYMBOL +${event.runes}")
                    rewardTick++
                }
                is SurvivorsEvent.Swing ->
                    effects += SurvivorsFx.Swing(event.x, event.y, event.angle, event.range, event.fullCircle, event.evolved)
                is SurvivorsEvent.Lightning -> if (effects.size < MAX_FX) effects += SurvivorsFx.Bolt(event.x, event.y, event.radius)
                is SurvivorsEvent.Chain -> if (effects.size < MAX_FX) effects += SurvivorsFx.Arc(event.x1, event.y1, event.x2, event.y2)
                is SurvivorsEvent.Anchor -> if (effects.size < MAX_FX) effects += SurvivorsFx.Anchor(event.x1, event.y1, event.x2, event.y2, event.evolved)
                is SurvivorsEvent.Thunder -> effects += SurvivorsFx.Thunder(event.x, event.y, event.radius)
                is SurvivorsEvent.Smite -> if (effects.size < MAX_FX) effects += SurvivorsFx.Smite(event.x, event.y)
                is SurvivorsEvent.IceShard -> if (effects.size < MAX_FX) effects += SurvivorsFx.Shard(event.x1, event.y1, event.x2, event.y2)
                is SurvivorsEvent.Nova -> effects += SurvivorsFx.Nova(event.x, event.y, event.radius, event.freeze)
                is SurvivorsEvent.Smash -> effects += SurvivorsFx.Smash(event.x, event.y, event.angle, event.range, event.ring)
                is SurvivorsEvent.Thorns -> if (effects.size < MAX_FX) effects += SurvivorsFx.ThornPulse(event.x, event.y, event.radius, event.evolved)
                is SurvivorsEvent.AttackImpact -> {
                    val a = event.attack
                    effects += when (a.shape) {
                        AttackShape.CIRCLE -> SurvivorsFx.Blast(a.x, a.y, a.radius, Color(0xFFA855F7))
                        AttackShape.LINE -> SurvivorsFx.Beam(a.x, a.y, a.x2, a.y2, a.radius)
                    }
                }
                is SurvivorsEvent.Explosion -> if (effects.size < MAX_FX) {
                    effects += SurvivorsFx.Blast(event.x, event.y, event.radius, Color(0xFFF97316))
                }
            }
        }
    }
}
