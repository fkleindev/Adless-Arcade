package com.adlessarcade.games.kitchen

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
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

enum class KitchenPhase { SELECT, PLAYING, RESULT }

/** Unveränderliche Werte für die Compose-Leisten (keine veränderliche Engine als Parameter). */
data class KitchenHud(
    val score: Int = 0,
    val strikes: Int = 0,
    val secondsLeft: Int? = null,
    val combo: Int = 0,
    val served: Int = 0,
    val title: String = "",
    val endless: Boolean = false,
    val targets: List<Int> = emptyList(),
)

data class KitchenResult(
    val score: Int,
    val stars: Int,
    val served: Int,
    val missed: Int,
    val levelId: String?,
    val isNewBest: Boolean,
    val coins: Int,
    val recordBonus: Int,
)

data class KitchenBanner(val title: String, val subtitle: String?, val color: Color)

/** Rezeptkarte eines angetippten Bons. */
data class RecipeCard(val dish: Dish, val lines: List<Pair<ItemKind, String>>)

class KitchenViewModel(app: Application, savedStateHandle: SavedStateHandle) : AndroidViewModel(app) {
    private val highscores = app.arcadeApp.highscores
    private val saves = app.arcadeApp.saves
    private val leaderboards = app.arcadeApp.leaderboards
    private val profile = app.arcadeApp.profile
    private val progress = app.arcadeApp.kitchenProgress
    private val achievements = AchievementTracker(profile)

    val modeId: String = savedStateHandle.get<String>("modeId") ?: KitchenRush.MODE_ENDLESS
    val isCampaign: Boolean = modeId == KitchenRush.MODE_CAMPAIGN

    var engine: KitchenEngine? = null
        private set
    var layout: KitchenLayout? = null
        private set
    val effects = ArrayList<KitchenEffect>()
    val flies = ArrayList<KitchenFly>()
    var time = 0f
        private set

    var frame by mutableLongStateOf(0L)
        private set
    var phase by mutableStateOf(if (isCampaign) KitchenPhase.SELECT else KitchenPhase.PLAYING)
        private set
    var hud by mutableStateOf(KitchenHud())
        private set
    var levelStars by mutableStateOf<Map<String, Int>>(emptyMap())
        private set
    var paused by mutableStateOf(false)
        private set
    var loaded by mutableStateOf(false)
        private set
    var result by mutableStateOf<KitchenResult?>(null)
        private set
    var best by mutableIntStateOf(0)
        private set
    var banner by mutableStateOf<KitchenBanner?>(null)
        private set
    var hint by mutableStateOf<String?>(null)
        private set
    var recipe by mutableStateOf<RecipeCard?>(null)
        private set
    var leaderboardRank by mutableStateOf<Int?>(null)
        private set
    var hurtTick by mutableIntStateOf(0)
        private set
    var rewardTick by mutableIntStateOf(0)
        private set

    private var bannerTimer = 0f
    private var hintTimer = 0f
    private var bestAtStart = 0
    private var coinsBooked = 0
    private var coinFlushTimer = 0f

    private val scoreKey = modeId

    init {
        viewModelScope.launch {
            val stored = highscores.highscore(KitchenRush.GAME_ID, scoreKey).first()
            best = stored
            bestAtStart = stored
            levelStars = progress.stars.first()
            val json = saves.load(KitchenRush.GAME_ID, scoreKey)
            val saved = json?.let(KitchenSaves::decode)
            if (json != null && saved == null) saves.clear(KitchenRush.GAME_ID, scoreKey)
            if (saved != null) {
                val level = saved.snapshot.levelId?.let(KitchenCatalog::findLevel)
                engine = KitchenEngine(saved.seed, level).also { it.restore(saved.snapshot) }
                coinsBooked = engine?.coinsEarned ?: 0
                phase = KitchenPhase.PLAYING
                paused = true
            } else if (!isCampaign) {
                startEndless()
            }
            loaded = true
            frame++
        }
        viewModelScope.launch { progress.stars.collect { levelStars = it } }
    }

    override fun onCleared() {
        flushCoins()
        saveRun()
    }

    // --- Spielstart ---

    fun levels(): List<Triple<LevelDef, Int, Boolean>> =
        KitchenCatalog.levels.map { Triple(it, levelStars[it.id] ?: 0, KitchenProgress.isUnlocked(it, levelStars)) }

    fun startLevel(id: String) {
        val level = KitchenCatalog.findLevel(id) ?: return
        if (!KitchenProgress.isUnlocked(level, levelStars)) return
        begin(KitchenEngine(System.nanoTime(), level))
        if (level.number == 1 && level.restaurant == Restaurant.CAFE) {
            showBanner(KitchenBanner("Willkommen!", "Tippe einen Bon an, um das Rezept zu sehen", Color(0xFF0EA5E9)), 3.5f)
        } else {
            showBanner(KitchenBanner(level.title, "Ziele: ${level.starTargets.joinToString(" / ")} Punkte", Color(0xFF0EA5E9)), 2.5f)
        }
    }

    fun startEndless() {
        begin(KitchenEngine(System.nanoTime(), null))
        showBanner(KitchenBanner("Endlos", "Nach 3 verpassten Bestellungen ist Schluss", Color(0xFF0EA5E9)), 2.5f)
    }

    private fun begin(e: KitchenEngine) {
        saves.clear(KitchenRush.GAME_ID, scoreKey)
        layout?.let { e.aspect = it.panelAspect }
        engine = e
        effects.clear()
        result = null
        recipe = null
        leaderboardRank = null
        coinsBooked = 0
        bestAtStart = best
        paused = false
        phase = KitchenPhase.PLAYING
        refreshHud()
        frame++
    }

    fun retry() {
        val level = engine?.level
        if (level != null) startLevel(level.id) else startEndless()
    }

    fun nextLevel() {
        val current = engine?.level ?: return
        val next = KitchenCatalog.levels.getOrNull(KitchenCatalog.levels.indexOf(current) + 1) ?: return
        startLevel(next.id)
    }

    fun hasNextLevel(): Boolean {
        val current = engine?.level ?: return false
        val next = KitchenCatalog.levels.getOrNull(KitchenCatalog.levels.indexOf(current) + 1) ?: return false
        return KitchenProgress.isUnlocked(next, levelStars + (current.id to maxOf(result?.stars ?: 0, levelStars[current.id] ?: 0)))
    }

    fun toLevelSelect() {
        flushCoins()
        saves.clear(KitchenRush.GAME_ID, scoreKey)
        engine = null
        result = null
        paused = false
        phase = KitchenPhase.SELECT
    }

    fun setLayout(l: KitchenLayout) {
        if (layout?.width == l.width && layout?.height == l.height) return
        layout = l
        engine?.aspect = l.panelAspect
    }

    // --- Game-Loop ---

    fun step(dt: Float) {
        if (!loaded) return
        time += dt
        effects.forEach { it.age += dt }
        effects.removeAll { it.age >= it.lifetime }
        flies.forEach { it.age += dt }
        flies.removeAll { it.age >= it.lifetime }
        if (banner != null) {
            bannerTimer -= dt
            if (bannerTimer <= 0f) banner = null
        }
        if (hint != null) {
            hintTimer -= dt
            if (hintTimer <= 0f) hint = null
        }
        val e = engine
        if (e != null && phase == KitchenPhase.PLAYING && !paused) {
            e.update(dt)
            handleEvents(e)
            refreshHud()
            coinFlushTimer += dt
            if (coinFlushTimer >= 2f) {
                coinFlushTimer = 0f
                flushCoins()
            }
            if (e.isFinished) finish(e)
        }
        frame++
    }

    private fun refreshHud() {
        val e = engine ?: return
        hud = KitchenHud(
            score = e.score,
            strikes = e.strikes,
            secondsLeft = e.remainingTime?.let { kotlin.math.ceil(it).toInt() },
            combo = e.combo,
            served = e.served,
            title = e.level?.title ?: "Endlos",
            endless = e.isEndless,
            targets = e.level?.starTargets ?: emptyList(),
        )
    }

    private fun finish(e: KitchenEngine) {
        flushCoins()
        saves.clear(KitchenRush.GAME_ID, scoreKey)
        val level = e.level
        var bonus = 0
        val stars = level?.stars(e.score) ?: 0
        val isNewBest: Boolean
        if (level != null) {
            val previous = levelStars[level.id] ?: 0
            isNewBest = stars > previous
            progress.record(level.id, stars)
            val updated = levelStars + (level.id to maxOf(stars, previous))
            val total = KitchenProgress.totalStars(updated)
            if (total > best) {
                best = total
                viewModelScope.launch { highscores.submit(KitchenRush.GAME_ID, scoreKey, total) }
            }
            if (isNewBest) bonus = stars * 5
        } else {
            isNewBest = e.score > bestAtStart
            if (e.score > best) best = e.score
            if (isNewBest) {
                bonus = CoinRewards.NEW_RECORD_BONUS
                viewModelScope.launch { highscores.submit(KitchenRush.GAME_ID, scoreKey, e.score) }
            }
            submitToLeaderboard(e)
            if (e.score >= Achievements.KR_CHEF_SCORE) achievements.unlock(Achievements.KR_CHEF)
        }
        if (bonus > 0) profile.addCoins(bonus)
        result = KitchenResult(e.score, stars, e.served, e.missed, level?.id, isNewBest, e.coinsEarned + bonus, bonus)
        phase = KitchenPhase.RESULT
        recipe = null
    }

    private fun submitToLeaderboard(e: KitchenEngine) {
        viewModelScope.launch {
            val rank = leaderboards.submit(
                LeaderboardKey(KitchenRush.GAME_ID, modeId),
                LeaderboardEntry(e.score, System.currentTimeMillis(), "${e.served} Gerichte serviert"),
            )
            if (engine === e) leaderboardRank = rank
        }
    }

    private fun flushCoins() {
        val e = engine ?: return
        val delta = e.coinsEarned - coinsBooked
        if (delta > 0) {
            profile.addCoins(delta)
            coinsBooked = e.coinsEarned
        }
    }

    private fun saveRun() {
        val e = engine ?: return
        if (phase != KitchenPhase.PLAYING || e.isFinished) return
        saves.save(KitchenRush.GAME_ID, scoreKey, KitchenSaves.encode(e.snapshot(), bestAtStart, e.seed), e.score)
    }

    private fun showBanner(b: KitchenBanner, seconds: Float) {
        banner = b
        bannerTimer = seconds
    }

    private fun showHint(text: String) {
        hint = text
        hintTimer = 2f
    }

    private fun handleEvents(e: KitchenEngine) {
        val l = layout
        for (event in e.drainEvents()) {
            when (event) {
                is KitchenEvent.Served -> {
                    val r = event.result
                    val stars = "★".repeat(r.stars) + "☆".repeat(3 - r.stars)
                    showBanner(KitchenBanner("${r.dish.name} serviert!", "$stars  +${r.points}" + if (r.combo > 1) "  Kombo ×${r.combo}" else "", Good), 1.6f)
                    l?.let { effects += KitchenEffect("+${r.points}", it.serve.center, Good) }
                    if (r.stars == 3) achievements.unlock(Achievements.KR_THREE_STARS)
                    if (e.bestStreak >= Achievements.KR_STREAK_TARGET) achievements.unlock(Achievements.KR_STREAK)
                    rewardTick++
                }
                is KitchenEvent.Missed -> {
                    showBanner(KitchenBanner("Bestellung verpasst!", event.dish.name, Bad), 1.6f)
                    hurtTick++
                }
                is KitchenEvent.NewOrder -> rewardTick++
                is KitchenEvent.StepDone -> l?.let {
                    val (text, color) = when {
                        event.accuracy >= 0.92f -> "Perfekt!" to Good
                        event.accuracy >= 0.75f -> "Gut!" to Color(0xFF0EA5E9)
                        else -> "Okay" to Warn
                    }
                    effects += KitchenEffect(text, it.panel.center, color)
                }
                KitchenEvent.MamaHelps -> hurtTick++
                is KitchenEvent.Burnt -> {
                    l?.let { effects += KitchenEffect("Verbrannt!", it.burner(event.stove).center, Bad) }
                    hurtTick++
                }
                is KitchenEvent.Rejected -> showHint(event.reason)
            }
        }
    }

    // --- Eingaben ---

    private val canAct: Boolean get() = loaded && !paused && phase == KitchenPhase.PLAYING

    fun canPick(station: Station): Boolean = canAct && engine?.canPick(station) == true

    /** Was beim Ziehen gezeigt wird: Gegenstand oder Teller. */
    fun dragFor(station: Station, pos: Offset): KitchenDrag? {
        val e = engine ?: return null
        if (!canPick(station)) return null
        return if (station is Station.PlateSpot) {
            KitchenDrag(station, null, e.plates[station.index], pos)
        } else {
            KitchenDrag(station, e.carried(station), null, pos)
        }
    }

    fun drop(from: Station, to: Station) {
        val e = engine ?: return
        if (!canAct) return
        val plate = (from as? Station.PlateSpot)?.let { e.plates[it.index] }
        if (e.move(from, to)) {
            recipe = null
            layout?.let { l ->
                if (to == Station.Serve && plate != null) {
                    flies += KitchenFly(l.rectOf(from).center, l.serve.center, plate.components.map { it.kind })
                } else {
                    effects += KitchenEffect("", l.rectOf(to).center, Color.White, lifetime = 0.35f, ring = true)
                }
            }
        }
        handleEvents(e)
        refreshHud()
        frame++
    }

    fun tap(station: Station) {
        val e = engine ?: return
        if (!canAct) return
        e.tap(station)
        handleEvents(e)
        frame++
    }

    fun showRecipe(ticket: Int) {
        val order = engine?.orders?.getOrNull(ticket) ?: return
        val dish = order.dish
        recipe = if (recipe?.dish == dish) null else RecipeCard(dish, dish.components.map { it to KitchenRecipesHelp.steps(it) })
    }

    fun closeRecipe() {
        recipe = null
    }

    fun gameDown(p: Offset) { if (canAct) engine?.gameDown(p.x, p.y) }
    fun gameMove(p: Offset) { if (canAct) engine?.gameMove(p.x, p.y) }
    fun gameUp(p: Offset) { if (canAct) engine?.gameUp(p.x, p.y) }

    fun pause() {
        if (!loaded || phase != KitchenPhase.PLAYING) return
        paused = true
        flushCoins()
        saveRun()
    }

    fun resume() {
        paused = false
    }
}
