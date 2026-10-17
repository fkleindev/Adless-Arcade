package com.adlessarcade.games.galaxytowers

import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Regeln von Galaxy Towers (TriPeaks): drei Türme aus 28 Karten in vier Reihen (3/6/9/10), die untere Reihe offen.
 * Freie Karten, die einen Rang über oder unter einer Ablage liegen (K–A rundum), dürfen abgelegt werden.
 * Ab einer 3er-Kette öffnet sich eine zweite Ablage mit der nächsten Stapelkarte; Nachziehen schließt sie wieder.
 * Feste Rundenzahl je Modus, die nächste Runde ist auch ohne Abräumen spielbar.
 * Reine Funktionen; jeder Zufall wird aus dem Seed abgeleitet, damit die Tägliche Challenge für alle gleich ist.
 */
object GalaxyTowersEngine {
    const val TOWER_SIZE = 28
    const val PEAKS = 3
    /** Die Ablage ist zum Rundenstart leer – alle übrigen Karten liegen im Stapel. */
    const val STOCK_SIZE = 52 - TOWER_SIZE
    const val WRONG_CARD_PENALTY = 100
    const val CARD_POINTS = 100
    const val PEAK_BONUS = 1000
    const val CLEAR_BONUS = 3000
    const val STOCK_CARD_BONUS = 200
    const val TIME_BONUS_PER_SECOND = 20
    const val SECOND_PILE_STREAK = 3
    const val GOLD_MIN_STREAK = 3
    const val WARP_CARDS = 3
    private const val HISTORY_LIMIT = 20

    /** Erster Index jeder Reihe; Reihe 0 sind die Turmspitzen. */
    private val rowStart = intArrayOf(0, 3, 9, 18, TOWER_SIZE)

    fun rowOf(index: Int): Int = (0 until 4).first { index < rowStart[it + 1] }

    /** Karten, die auf [index] liegen (eine Reihe tiefer). */
    val coveredBy: List<List<Int>> = List(TOWER_SIZE) { i ->
        when (rowOf(i)) {
            0 -> listOf(3 + 2 * i, 4 + 2 * i)
            1 -> {
                val j = i - 3
                val first = 9 + 3 * (j / 2) + j % 2
                listOf(first, first + 1)
            }
            2 -> listOf(18 + (i - 9), 19 + (i - 9))
            else -> emptyList()
        }
    }

    /** Mittelpunkt einer Turmkarte in Kartenbreiten (x, 0..9) und Reihen (y, 0..3). */
    fun slotPosition(index: Int): Pair<Float, Float> {
        val row = rowOf(index)
        val j = index - rowStart[row]
        val x = when (row) {
            0 -> 1.5f + 3 * j
            1 -> 1f + 3 * (j / 2) + j % 2
            2 -> 0.5f + 3 * (j / 3) + j % 3
            else -> j.toFloat()
        }
        return x to row.toFloat()
    }

    fun multiplier(round: Int): Float = 1f + 0.25f * (round - 1)

    private fun scaled(points: Int, round: Int): Int = (points * multiplier(round)).roundToInt()

    /** Coins für eine abgeräumte Runde. */
    fun roundCoins(round: Int): Int = minOf(40, 10 + 5 * (round - 1))

    private fun roundRandom(seed: Long, round: Int, salt: Int = 0): Random =
        Random(seed * 31 + round * 7919L + salt * 104729L)

    fun newGame(seed: Long, mode: GalaxyTowersMode = GalaxyTowersMode.CLASSIC): GameState =
        deal(GameState(seed = seed, mode = mode, tower = emptyList(), waste = emptyList(), stock = emptyList()), 1)

    private fun deal(base: GameState, round: Int): GameState {
        val deck = (0 until 4).flatMap { suit -> (1..13).map { Card(it, suit) } }.shuffled(roundRandom(base.seed, round))
        return base.copy(
            round = round,
            tower = deck.take(TOWER_SIZE),
            waste = emptyList(),
            stock = deck.drop(TOWER_SIZE),
            waste2 = null,
            streak = 0,
            scanActive = false,
            history = emptyList(),
            timeLeft = base.mode.roundTime(round),
            roundOver = null,
            warps = 0,
        )
    }

    /** Teilt nach dem Rundenende die nächste Runde aus; nach der letzten Runde ist das Spiel vorbei. */
    fun nextRound(state: GameState): GameState = when {
        state.roundOver == null || state.isGameOver -> state
        state.isLastRound -> endGame(state)
        else -> deal(state, state.round + 1)
    }

    fun isFree(tower: List<Card?>, index: Int): Boolean =
        tower.getOrNull(index) != null && coveredBy[index].all { tower[it] == null }

    fun fits(card: Card, top: Card?): Boolean {
        if (top == null) return false
        if (top.isJoker) return true
        val diff = (card.rank - top.rank).mod(13)
        return diff == 1 || diff == 12
    }

    /** Ablage, auf die [card] passt: 0 = Hauptablage (Vorrang, leer nimmt sie jede Karte), 1 = zweite Ablage, null = keine. */
    fun targetPile(state: GameState, card: Card): Int? = when {
        state.waste.isEmpty() || fits(card, state.top) -> 0
        fits(card, state.top2) -> 1
        else -> null
    }

    fun canPlay(state: GameState, index: Int): Boolean =
        state.inPlay && isFree(state.tower, index) && targetPile(state, state.tower[index]!!) != null

    fun playable(state: GameState): List<Int> = (0 until TOWER_SIZE).filter { canPlay(state, it) }

    fun hasMove(state: GameState): Boolean = playable(state).isNotEmpty()

    /** Keine Karte passt und der Nachziehstapel ist leer. */
    fun isStuck(state: GameState): Boolean = state.inPlay && state.stock.isEmpty() && !hasMove(state)

    /** Feststecken ohne eine Ladung, die noch helfen könnte. */
    fun isHopeless(state: GameState): Boolean =
        isStuck(state) && state.charges(PowerUp.JOKER) == 0 && state.charges(PowerUp.WARP) == 0 &&
            (state.charges(PowerUp.UNDO) == 0 || state.history.isEmpty())

    private fun GameState.snapshot() = Snapshot(tower, waste, stock, score, streak, waste2, penaltyTotal)

    /**
     * Strafe für das Antippen einer offenen Karte, die auf keine Ablage passt. Kostet nur Punkte (nicht unter 0),
     * die Kette bleibt bestehen. null, wenn die Karte verdeckt ist, passt oder die Runde nicht läuft.
     */
    fun misplay(state: GameState, index: Int): GameState? {
        if (!state.inPlay || !isFree(state.tower, index) || canPlay(state, index)) return null
        return state.copy(
            score = (state.score - WRONG_CARD_PENALTY).coerceAtLeast(0),
            penaltyTotal = state.penaltyTotal + WRONG_CARD_PENALTY,
        )
    }

    private fun GameState.pushHistory() = (history + snapshot()).takeLast(HISTORY_LIMIT)

    fun play(state: GameState, index: Int): PlayResult? {
        if (!canPlay(state, index)) return null
        val card = state.tower[index]!!
        val pile = targetPile(state, card)!!
        val tower = state.tower.toMutableList().also { it[index] = null }
        val streak = state.streak + 1
        var points = scaled(CARD_POINTS * streak, state.round)
        val peakCleared = rowOf(index) == 0
        if (peakCleared) points += scaled(PEAK_BONUS, state.round)
        val cleared = tower.all { it == null }

        var waste = state.waste
        var waste2 = state.waste2
        var stock = state.stock
        if (pile == 0) waste = waste + card else waste2 = waste2!! + card
        // Zweite Ablage öffnet sich bei einer 3er-Kette und zieht dafür die nächste Stapelkarte.
        val opens = !cleared && waste2 == null && streak >= SECOND_PILE_STREAK && stock.isNotEmpty()
        if (opens) {
            waste2 = listOf(stock.first())
            stock = stock.drop(1)
        }

        val clearBonus = if (cleared) scaled(CLEAR_BONUS + STOCK_CARD_BONUS * stock.size, state.round) else 0
        val timeBonus = if (cleared && state.mode.timed) scaled(TIME_BONUS_PER_SECOND * state.timeLeft.toInt(), state.round) else 0
        val gold = goldDrop(state, streak)
        val coins = (if (cleared) roundCoins(state.round) else 0) + gold
        val reward = powerUpDrop(state, streak)
        val charges = if (reward != null) state.charges + (reward to state.charges(reward) + 1) else state.charges
        val timeDrop = if (cleared) 0 else timeDrop(state, streak)
        val next = state.copy(
            tower = tower,
            waste = waste,
            waste2 = waste2,
            stock = stock,
            score = state.score + points + clearBonus + timeBonus,
            streak = streak,
            bestStreak = maxOf(state.bestStreak, streak),
            charges = charges,
            scanActive = false,
            history = if (cleared) emptyList() else state.pushHistory(),
            coinsEarned = state.coinsEarned + coins,
            roundOver = if (cleared) RoundEnd.CLEARED else null,
            roundsCleared = state.roundsCleared + if (cleared) 1 else 0,
            timeLeft = state.timeLeft + timeDrop,
            moves = state.moves + 1,
        )
        return PlayResult(next, pile, points, peakCleared, cleared, clearBonus, timeBonus, coins, reward, opens, gold, timeDrop)
    }

    /**
     * Zufall für Drops einer gelegten Karte. [GameState.moves] zählt jeden Zug und wird vom Zeitsprung nicht
     * zurückgesetzt – ein zurückgenommener und erneut gespielter Zug würfelt also neu (kein Endlos-Farmen).
     */
    private fun dropRandom(state: GameState, salt: Int): Random = roundRandom(state.seed, state.round, salt + state.moves * 31)

    /** Chance auf Gold für eine Karte mit Kettenlänge [streak]: ab 3 steigend, höchstens 60 %. */
    fun goldChance(streak: Int): Float = if (streak < GOLD_MIN_STREAK) 0f else minOf(0.6f, 0.08f * (streak - 2))

    /** Gold (Coins), das die Karte mit Kettenlänge [streak] abwirft; deterministisch aus dem Spielstand. */
    private fun goldDrop(state: GameState, streak: Int): Int {
        if (streak < GOLD_MIN_STREAK) return 0
        val rng = dropRandom(state, 5000)
        if (rng.nextFloat() >= goldChance(streak)) return 0
        return rng.nextInt(1, if (streak >= 8) 6 else 4)
    }

    /** Chance, dass eine gelegte Karte ein Power-up abwirft: 3 % + 0,5 % pro Kettenglied. */
    fun powerUpChance(streak: Int): Float = 0.03f + 0.005f * streak

    private fun powerUpDrop(state: GameState, streak: Int): PowerUp? {
        val rng = dropRandom(state, 6000)
        if (rng.nextFloat() >= powerUpChance(streak)) return null
        return PowerUp.entries[rng.nextInt(PowerUp.entries.size)]
    }

    /** Chance auf Bonuszeit (nur mit Zeitlimit): 4 % + 0,5 % pro Kettenglied, höchstens 12 %. */
    fun timeDropChance(streak: Int): Float = minOf(0.12f, 0.04f + 0.005f * streak)

    /** Bonuszeit in Sekunden, die eine gelegte Karte abwirft (5 s, ab Kette 6 8 s). */
    private fun timeDrop(state: GameState, streak: Int): Int {
        if (!state.mode.timed) return 0
        val rng = dropRandom(state, 7000)
        if (rng.nextFloat() >= timeDropChance(streak)) return 0
        return if (streak >= 6) 8 else 5
    }

    /** Nächste Karte auf die Hauptablage; beendet die Kette und schließt die zweite Ablage. */
    fun draw(state: GameState): GameState? {
        if (!state.inPlay || state.stock.isEmpty()) return null
        return state.copy(
            waste = state.waste + state.stock.first(),
            stock = state.stock.drop(1),
            waste2 = null,
            streak = 0,
            scanActive = false,
            history = state.pushHistory(),
            moves = state.moves + 1,
        )
    }

    /** Lässt die Rundenzeit laufen; bei 0 endet die Runde. */
    fun tick(state: GameState, dt: Float): GameState {
        if (!state.mode.timed || !state.inPlay || dt <= 0f) return state
        val left = (state.timeLeft - dt).coerceAtLeast(0f)
        return if (left <= 0f) endRound(state.copy(timeLeft = 0f), RoundEnd.TIME_UP) else state.copy(timeLeft = left)
    }

    fun endRound(state: GameState, reason: RoundEnd): GameState =
        if (state.inPlay) state.copy(roundOver = reason, scanActive = false, history = emptyList()) else state

    fun canUse(state: GameState, p: PowerUp): Boolean {
        if (!state.inPlay || state.charges(p) <= 0) return false
        return when (p) {
            PowerUp.JOKER -> state.top?.isJoker != true
            PowerUp.UNDO -> state.history.isNotEmpty()
            PowerUp.SCAN -> !state.scanActive && state.tower.indices.any { state.tower[it] != null && !isFree(state.tower, it) }
            PowerUp.WARP -> true
        }
    }

    /** Setzt ein Power-up ein und verbraucht eine Ladung; null, wenn es gerade nicht möglich ist. */
    fun use(state: GameState, p: PowerUp): GameState? {
        if (!canUse(state, p)) return null
        val spent = state.copy(charges = state.charges + (p to state.charges(p) - 1))
        return when (p) {
            PowerUp.JOKER -> spent.copy(waste = spent.waste + Card.JOKER)
            PowerUp.UNDO -> {
                val last = spent.history.last()
                spent.copy(
                    tower = last.tower,
                    waste = last.waste,
                    waste2 = last.waste2,
                    stock = last.stock,
                    // Strafen seit dem zurückgenommenen Zug bleiben bestehen.
                    score = (last.score - (spent.penaltyTotal - last.penaltyTotal)).coerceAtLeast(0),
                    streak = last.streak,
                    history = spent.history.dropLast(1),
                    scanActive = false,
                )
            }
            PowerUp.SCAN -> spent.copy(scanActive = true)
            PowerUp.WARP -> {
                val rng = roundRandom(state.seed, state.round, 1000 + state.warps)
                val cards = List(WARP_CARDS) { Card(rng.nextInt(1, 14), rng.nextInt(4)) }
                spent.copy(stock = spent.stock + cards, warps = state.warps + 1)
            }
        }
    }

    fun addCharge(state: GameState, p: PowerUp): GameState = state.copy(charges = state.charges + (p to state.charges(p) + 1))

    fun endGame(state: GameState): GameState = state.copy(isGameOver = true, scanActive = false)
}
