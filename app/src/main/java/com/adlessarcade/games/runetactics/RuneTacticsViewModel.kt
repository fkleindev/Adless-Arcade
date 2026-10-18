package com.adlessarcade.games.runetactics

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
import com.adlessarcade.core.achievements.AchievementTracker
import com.adlessarcade.core.achievements.Achievements
import com.adlessarcade.core.leaderboard.LeaderboardEntry
import com.adlessarcade.core.leaderboard.LeaderboardKey
import com.adlessarcade.core.shop.CoinRewards
import com.adlessarcade.ui.theme.ArcadeText
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Rein visuelle Effekte in Brett-Koordinaten (Spalte/Reihe als Float). */
sealed class TacticsEffect(val lifetime: Float) {
    var age = 0f
    val progress: Float get() = (age / lifetime).coerceIn(0f, 1f)

    class Projectile(
        val fromCol: Float, val fromRow: Float, val toCol: Float, val toRow: Float, val color: Color,
    ) : TacticsEffect(0.22f)

    class Number(val col: Float, val row: Float, val text: String, val color: Color) : TacticsEffect(0.8f)
    class Ring(val col: Float, val row: Float, val radius: Float, val color: Color) : TacticsEffect(0.45f)
    class Slash(val col: Float, val row: Float, val color: Color) : TacticsEffect(0.18f)

    /** Markierte Zone eines Bossangriffs bis zum Einschlag. */
    class Telegraph(val col: Int, val row: Int, val radius: Int, delay: Float) : TacticsEffect(delay)
}

/** Großer Hinweis in der Bildmitte mit optionaler zweiter Zeile. */
data class TacticsBanner(val title: String, val subtitle: String?, val color: Color)

/** Angetippte Einheit für die Info-Karte. */
data class Inspected(
    val def: UnitDef,
    val star: Int,
    val enemy: Boolean,
    val items: List<String> = emptyList(),
    /** Werte-Skalierung gegnerischer Einheiten (Aufstieg, Kreaturen, Bosse). */
    val scale: Float = 1f,
)

private const val MAX_EFFECTS = 90
private const val RESULT_DELAY = 1.6f

class RuneTacticsViewModel(app: Application, savedStateHandle: SavedStateHandle) : AndroidViewModel(app) {
    private val highscores = app.arcadeApp.highscores
    private val saves = app.arcadeApp.saves
    private val leaderboards = app.arcadeApp.leaderboards
    private val profile = app.arcadeApp.profile
    private val achievements = AchievementTracker(profile)

    val modeId: String = savedStateHandle.get<String>("modeId") ?: RuneTactics.MODE_ENDLESS
    val isDaily: Boolean = modeId == RuneTactics.MODE_DAILY

    /** Schlüssel für Highscore und Spielstand (bei der Täglichen Challenge pro Tag). */
    private val scoreKey = if (isDaily) DailyChallenge.scoreKey(modeId) else modeId

    private fun newSeed(): Long = if (isDaily) DailyChallenge.todaySeed() else System.nanoTime()

    var engine = RuneTacticsEngine(newSeed())
        private set
    val effects = ArrayList<TacticsEffect>()

    /** Wird jeden Frame erhöht und vom Canvas gelesen, um neu zu zeichnen. */
    var frame by mutableLongStateOf(0L)
        private set
    /** Wird bei jeder Änderung des Spielzustands erhöht (Gold, Shop, Bank …), damit die Leisten neu zeichnen. */
    var revision by mutableIntStateOf(0)
        private set
    var paused by mutableStateOf(false)
        private set
    var gameOver by mutableStateOf(false)
        private set
    var loaded by mutableStateOf(false)
        private set
    var best by mutableIntStateOf(0)
        private set
    var isNewBest by mutableStateOf(false)
        private set
    var speed by mutableIntStateOf(1)
        private set
    var banner by mutableStateOf<TacticsBanner?>(null)
        private set
    var inspected by mutableStateOf<Inspected?>(null)
        private set
    /** Ausgewähltes Item im Inventar; ein Tipp auf eine eigene Einheit rüstet sie damit aus. */
    var selectedItem by mutableStateOf<Int?>(null)
        private set
    var leaderboardRank by mutableStateOf<Int?>(null)
        private set
    var recordBonus by mutableIntStateOf(0)
        private set
    var hurtTick by mutableIntStateOf(0)
        private set
    var rewardTick by mutableIntStateOf(0)
        private set

    private var bestAtStart = 0
    private var coinsBooked = 0
    private var bannerTimer = 0f
    private var accumulator = 0f
    /** Wartezeit nach Kampfende, bevor die nächste Runde beginnt; < 0 = Kampf läuft noch. */
    private var resultTimer = -1f

    init {
        viewModelScope.launch {
            val stored = highscores.highscore(RuneTactics.GAME_ID, scoreKey).first()
            val json = saves.load(RuneTactics.GAME_ID, scoreKey)
            val saved = json?.let(RuneTacticsSaves::decode)
                // Tägliche Challenge: nur Läufe mit dem heutigen Seed fortsetzen.
                ?.takeIf { !isDaily || it.seed == DailyChallenge.todaySeed() }
            if (json != null && saved == null) saves.clear(RuneTactics.GAME_ID, scoreKey)
            if (saved != null) {
                engine = RuneTacticsEngine(saved.seed).also { it.restore(saved.snapshot) }
                coinsBooked = engine.coinsEarned // wurden beim Unterbrechen bereits gutgeschrieben
                bestAtStart = minOf(saved.bestAtStart, stored)
                // Ein unterbrochener Kampf startet pausiert, damit man ihn nicht verpasst.
                if (engine.phase == Phase.COMBAT) paused = true
            } else {
                bestAtStart = stored
            }
            best = maxOf(stored, engine.round)
            isNewBest = engine.round > bestAtStart
            loaded = true
            changed()
        }
    }

    override fun onCleared() {
        flushCoins()
        saveRun()
    }

    private fun changed() {
        revision++
        frame++
        checkAchievements()
    }

    private fun checkAchievements() {
        if (!loaded) return
        val run = engine
        if (run.round >= Achievements.RT_ROUND_TARGET) achievements.unlock(Achievements.RT_ROUND_15)
        if ((run.board.values + run.bench.filterNotNull()).any { it.star >= Stars.MAX }) {
            achievements.unlock(Achievements.RT_THREE_STAR)
        }
        if (run.activeTraits().any { (trait, count) -> trait.level(count) == trait.tiers.size }) {
            achievements.unlock(Achievements.RT_FULL_SYNERGY)
        }
    }

    private fun flushCoins() {
        val delta = engine.coinsEarned - coinsBooked
        if (delta > 0) {
            profile.addCoins(delta)
            coinsBooked = engine.coinsEarned
        }
    }

    private fun saveRun() {
        if (!loaded || gameOver || engine.isGameOver) return
        val json = RuneTacticsSaves.encode(engine.snapshot(), bestAtStart, engine.seed)
        saves.save(RuneTactics.GAME_ID, scoreKey, json, engine.round)
    }

    // --- Spieleraktionen (Planungsphase) ---

    private val canPlan: Boolean get() = loaded && !paused && !gameOver && engine.phase == Phase.PLANNING

    fun buy(index: Int) {
        if (!canPlan) return
        val star = engine.buy(index) ?: return
        if (star > 1) {
            showBanner(TacticsBanner("${"★".repeat(star)} AUFGEWERTET", null, StarGold), 1.1f)
            rewardTick++
        }
        changed()
    }

    fun sell(slot: Slot) {
        if (!canPlan) return
        val gold = engine.sell(slot) ?: return
        effects += TacticsEffect.Number(3f, 7.5f, "+$gold Gold", StarGold)
        inspected = null
        selectedItem = null
        changed()
    }

    fun selectItem(index: Int?) {
        selectedItem = if (index == null || index == selectedItem || index !in engine.items.indices) null else index
        if (selectedItem != null) inspected = null
    }

    /** Rüstet die Einheit mit dem ausgewählten Item aus; liefert false, wenn kein Item gewählt ist. */
    fun equipSelected(slot: Slot): Boolean {
        val index = selectedItem ?: return false
        if (!canPlan) return true
        val unit = engine.unitAt(slot)
        if (unit == null) {
            selectedItem = null
            return true
        }
        val item = engine.equip(index, slot)
        if (item == null) {
            showBanner(TacticsBanner("Kein Platz", "Höchstens ${RuneTacticsItems.MAX_PER_UNIT} Items pro Einheit", LossRed), 1.2f)
            hurtTick++
        } else {
            selectedItem = null
            if (!item.isComponent) {
                showBanner(TacticsBanner(item.name, unit.def.name, StarGold), 1.2f)
                rewardTick++
            }
            changed()
        }
        return true
    }

    fun chooseAugment(index: Int) {
        if (!loaded || paused || gameOver) return
        val augment = engine.chooseAugment(index) ?: return
        showBanner(TacticsBanner(augment.title, augment.description, MagicViolet), 1.6f)
        rewardTick++
        saveRun()
        changed()
    }

    fun chooseReward(index: Int) {
        if (!loaded || paused || gameOver) return
        val reward = engine.chooseReward(index) ?: return
        showBanner(TacticsBanner(reward.title, null, StarGold), 1.2f)
        rewardTick++
        saveRun()
        changed()
    }

    fun move(from: Slot, to: Slot) {
        if (!canPlan) return
        if (engine.move(from, to)) changed()
    }

    fun reroll() {
        if (canPlan && engine.reroll()) changed()
    }

    fun buyXp() {
        if (!canPlan) return
        val before = engine.level
        if (engine.buyXp()) {
            if (engine.level > before) {
                showBanner(TacticsBanner("LEVEL ${engine.level}", "${engine.boardCap} Plätze auf dem Brett", ArcadeCyan), 1.2f)
                rewardTick++
            }
            changed()
        }
    }

    fun fight() {
        if (!canPlan) return
        if (engine.board.isEmpty() && engine.bench.all { it == null }) {
            showBanner(TacticsBanner("Keine Einheiten", "Kaufe zuerst Einheiten im Shop", LossRed), 1.4f)
            return
        }
        inspected = null
        selectedItem = null
        if (engine.startCombat()) {
            accumulator = 0f
            resultTimer = -1f
            effects.clear()
            saveRun()
            changed()
        }
    }

    fun toggleSpeed() {
        speed = when (speed) {
            1 -> 2
            2 -> 4
            else -> 1
        }
    }

    fun inspect(unit: Inspected?) {
        inspected = unit
    }

    fun pause() {
        if (!loaded || gameOver) return
        paused = true
        flushCoins()
        saveRun()
    }

    fun resume() {
        paused = false
    }

    fun restart() {
        flushCoins()
        saves.clear(RuneTactics.GAME_ID, scoreKey)
        bestAtStart = best
        engine = RuneTacticsEngine(newSeed())
        coinsBooked = 0
        recordBonus = 0
        leaderboardRank = null
        effects.clear()
        banner = null
        inspected = null
        selectedItem = null
        resultTimer = -1f
        accumulator = 0f
        paused = false
        gameOver = false
        isNewBest = false
        changed()
    }

    // --- Game-Loop ---

    fun step(dt: Float) {
        if (!loaded || paused || gameOver) return
        if (banner != null) {
            bannerTimer -= dt
            if (bannerTimer <= 0f) banner = null
        }
        val fight = engine.combat
        val simDt = if (engine.phase == Phase.COMBAT) dt * speed else dt
        effects.forEach { it.age += simDt }
        effects.removeAll { it.age >= it.lifetime }

        if (engine.phase == Phase.COMBAT && fight != null) {
            if (!fight.isOver) {
                accumulator += simDt
                while (accumulator >= RuneTacticsCombat.TICK && !fight.isOver) {
                    fight.step()
                    accumulator -= RuneTacticsCombat.TICK
                }
                handleEvents(fight)
                if (fight.isOver) {
                    resultTimer = RESULT_DELAY
                    showOutcome(fight)
                }
            } else {
                resultTimer -= dt
                if (resultTimer <= 0f) finishRound()
            }
        }
        frame++
    }

    private fun showOutcome(fight: RuneTacticsCombat) {
        val outcome = fight.outcome ?: return
        val full = Economy.damage(engine.round, fight.survivingEnemyStars())
        when (outcome) {
            Outcome.WIN -> {
                showBanner(TacticsBanner("SIEG!", null, WinGreen), RESULT_DELAY)
                rewardTick++
            }
            Outcome.LOSS -> {
                showBanner(TacticsBanner("NIEDERLAGE", "−$full Leben", LossRed), RESULT_DELAY)
                hurtTick++
            }
            Outcome.DRAW -> {
                showBanner(TacticsBanner("UNENTSCHIEDEN", "−${(full + 1) / 2} Leben", StarGold), RESULT_DELAY)
                hurtTick++
            }
        }
    }

    private fun finishRound() {
        val result = engine.finishCombat() ?: return
        resultTimer = -1f
        effects.clear()
        flushCoins()
        if (engine.isGameOver) {
            gameOver = true
            if (engine.round > best) best = engine.round
            isNewBest = engine.round > bestAtStart
            submitScore()
            if (isNewBest) {
                recordBonus = CoinRewards.NEW_RECORD_BONUS
                profile.addCoins(recordBonus)
            }
            saves.clear(RuneTactics.GAME_ID, scoreKey)
            submitToLeaderboard()
        } else {
            if (engine.round > best) best = engine.round
            isNewBest = engine.round > bestAtStart
            val parts = buildList {
                add("+${result.income} Gold")
                if (result.interest > 0) add("${result.interest} Zinsen")
                if (result.streakBonus > 0) add("${result.streakBonus} Serie")
                if (result.loot.isNotEmpty()) add("+${result.loot.size} Komponente" + if (result.loot.size > 1) "n" else "")
            }
            val title = when (engine.roundType) {
                RoundType.NORMAL -> "RUNDE ${engine.round}"
                RoundType.CREEPS -> "RUNDE ${engine.round} · KREATUREN"
                RoundType.BOSS -> "RUNDE ${engine.round} · BOSS"
            }
            val curse = engine.curse
            when {
                engine.round == RuneTacticsEnemyAI.ASCENSION_START + 1 ->
                    showBanner(TacticsBanner("AUFSTIEG", "Gegner werden ab jetzt jede Runde stärker", LossRed), 2.2f)
                curse != null ->
                    showBanner(TacticsBanner("FLUCH: ${curse.title.uppercase()}", curse.description, MagicViolet), 2.2f)
                else -> showBanner(
                    TacticsBanner(title, parts.joinToString(" · "), if (engine.roundType == RoundType.BOSS) LossRed else ArcadeText),
                    1.6f,
                )
            }
            submitScore()
            saveRun()
        }
        changed()
    }

    private fun submitScore() {
        val score = engine.round
        if (score > bestAtStart) viewModelScope.launch { highscores.submit(RuneTactics.GAME_ID, scoreKey, score) }
    }

    private fun submitToLeaderboard() {
        val run = engine
        val details = if (isDaily) {
            "Challenge vom ${LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))} · Level ${run.level}"
        } else {
            "Level ${run.level} · ${run.board.size} Einheiten"
        }
        viewModelScope.launch {
            val rank = leaderboards.submit(
                LeaderboardKey(RuneTactics.GAME_ID, modeId),
                LeaderboardEntry(run.round, System.currentTimeMillis(), details),
            )
            if (engine === run) leaderboardRank = rank
        }
    }

    private fun showBanner(value: TacticsBanner, seconds: Float) {
        banner = value
        bannerTimer = seconds
    }

    private fun handleEvents(fight: RuneTacticsCombat) {
        for (event in fight.drainEvents()) {
            when (event) {
                is CombatEvent.Attack -> {
                    val from = fight.find(event.from) ?: continue
                    val to = fight.find(event.to) ?: continue
                    if (effects.size >= MAX_EFFECTS) continue
                    effects += if (event.ranged) {
                        TacticsEffect.Projectile(
                            from.visualCol, from.visualRow, to.visualCol, to.visualRow,
                            if (from.side == 0) ArcadeCyan else LossRed,
                        )
                    } else {
                        TacticsEffect.Slash(to.visualCol, to.visualRow, Color.White)
                    }
                }
                is CombatEvent.Damage -> {
                    val target = fight.find(event.target) ?: continue
                    if (effects.size >= MAX_EFFECTS || event.amount <= 0) continue
                    effects += TacticsEffect.Number(
                        target.visualCol, target.visualRow, if (event.crit) "${event.amount}!" else "${event.amount}",
                        when {
                            event.crit -> StarGold
                            event.magic -> MagicViolet
                            else -> Color.White
                        },
                    )
                }
                is CombatEvent.Dodge -> {
                    val target = fight.find(event.target) ?: continue
                    effects += TacticsEffect.Number(target.visualCol, target.visualRow, "Ausgewichen", WinGreen)
                }
                is CombatEvent.Heal -> {
                    val target = fight.find(event.target) ?: continue
                    effects += TacticsEffect.Number(target.visualCol, target.visualRow, "+${event.amount}", WinGreen)
                }
                is CombatEvent.Cast -> {
                    val caster = fight.find(event.caster) ?: continue
                    effects += TacticsEffect.Number(caster.visualCol, caster.visualRow - 0.4f, event.ability.name, StarGold)
                }
                is CombatEvent.Telegraph -> {
                    effects += TacticsEffect.Telegraph(event.col, event.row, event.radius, event.delay)
                    hurtTick++
                }
                is CombatEvent.Area -> {
                    effects += TacticsEffect.Ring(event.col, event.row, event.radius + 0.5f, areaColor(event.kind))
                }
                is CombatEvent.Death -> {
                    val unit = fight.find(event.unit) ?: continue
                    effects += TacticsEffect.Ring(unit.visualCol, unit.visualRow, 0.6f, Color(0xFF6B7280))
                }
                is CombatEvent.Revive -> {
                    val unit = fight.find(event.unit) ?: continue
                    effects += TacticsEffect.Ring(unit.visualCol, unit.visualRow, 0.7f, MagicViolet)
                    effects += TacticsEffect.Number(unit.visualCol, unit.visualRow - 0.4f, "Erhebt sich!", MagicViolet)
                }
            }
        }
    }

    private fun areaColor(kind: AbilityKind): Color = when (kind) {
        AbilityKind.FROST -> Color(0xFF7DD3FC)
        AbilityKind.QUAKE, AbilityKind.LEAP -> Color(0xFFF59E0B)
        AbilityKind.MOON -> Color(0xFFE0E7FF)
        AbilityKind.SPLASH -> Color(0xFFFF7849)
        else -> MagicViolet
    }
}
