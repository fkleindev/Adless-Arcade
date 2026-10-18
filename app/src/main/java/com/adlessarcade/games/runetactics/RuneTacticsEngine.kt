package com.adlessarcade.games.runetactics

import kotlin.random.Random

/**
 * Zustandsautomat eines Laufs: Planungsphase (Shop, Bank, Brett) → Kampf → Auswertung → nächste Runde.
 * Reine Logik ohne Android-Abhängigkeiten.
 */
class RuneTacticsEngine(val seed: Long) {
    var phase = Phase.PLANNING
        private set
    var round = 1
        private set
    var hp = Economy.START_HP
        private set
    var gold = Economy.START_GOLD
        private set
    var level = Economy.START_LEVEL
        private set
    var xp = 0
        private set
    var winStreak = 0
        private set
    var lossStreak = 0
        private set
    var coinsEarned = 0
        private set

    val board = LinkedHashMap<Cell, UnitInstance>()
    val bench = arrayOfNulls<UnitInstance>(Board.BENCH_SIZE)
    val shop = arrayOfNulls<String>(Board.SHOP_SIZE)
    private val pool = HashMap<String, Int>()

    var enemyArmy: List<PlacedUnit> = emptyList()
        private set
    var combat: RuneTacticsCombat? = null
        private set

    private var nextUid = 1L
    private var rngCounter = 0L
    private var preCombat: RuneTacticsSnapshot? = null

    init {
        RuneTacticsUnits.all.forEach { pool[it.id] = RuneTacticsUnits.poolSize(it.cost) }
        rollShop()
        enemyArmy = RuneTacticsEnemyAI.army(seed, round)
    }

    val isGameOver: Boolean get() = phase == Phase.GAME_OVER
    val boardCap: Int get() = level
    val enemyScale: Float get() = RuneTacticsEnemyAI.scale(round)

    fun poolCount(defId: String): Int = pool[defId] ?: 0

    fun unitAt(slot: Slot): UnitInstance? = when (slot) {
        is Slot.Board -> board[slot.cell]
        is Slot.Bench -> bench.getOrNull(slot.index)
    }

    fun activeTraits(): Map<Trait, Int> = Traits.count(board.values.map { it.def })

    /** Würde der Kauf dieser Einheit eine ★2 ergeben? (für die Shop-Hervorhebung) */
    fun wouldCombine(defId: String): Boolean = copies(defId, 1).size >= 2

    private fun nextRandom(): Random = Random(seed * 6364136223846793005L + (rngCounter++) * 1442695040888963407L)

    // --- Shop ---

    private fun rollShop() {
        returnShop()
        val rng = nextRandom()
        for (i in shop.indices) {
            val cost = Economy.rollCost(level, rng) { c -> RuneTacticsUnits.ofCost(c).any { poolCount(it.id) > 0 } }
                ?: continue
            val candidates = RuneTacticsUnits.ofCost(cost).filter { poolCount(it.id) > 0 }
            var pick = rng.nextInt(candidates.sumOf { poolCount(it.id) })
            val def = candidates.first { pick -= poolCount(it.id); pick < 0 }
            pool[def.id] = poolCount(def.id) - 1
            shop[i] = def.id
        }
    }

    /** Nicht gekaufte Shop-Einheiten wandern zurück in den Pool. */
    private fun returnShop() {
        for (i in shop.indices) {
            val id = shop[i] ?: continue
            pool[id] = poolCount(id) + 1
            shop[i] = null
        }
    }

    fun reroll(): Boolean {
        if (phase != Phase.PLANNING || gold < Economy.REROLL_COST) return false
        gold -= Economy.REROLL_COST
        rollShop()
        return true
    }

    fun buyXp(): Boolean {
        if (phase != Phase.PLANNING || level >= Economy.MAX_LEVEL || gold < Economy.XP_COST) return false
        gold -= Economy.XP_COST
        addXp(Economy.XP_PER_BUY)
        return true
    }

    private fun addXp(amount: Int) {
        if (level >= Economy.MAX_LEVEL) return
        xp += amount
        while (level < Economy.MAX_LEVEL && xp >= Economy.xpToNext(level)) {
            xp -= Economy.xpToNext(level)
            level++
        }
        if (level >= Economy.MAX_LEVEL) xp = 0
    }

    /** Kauft die Einheit im Shop-Platz [index]; null wenn nicht möglich, sonst der neue Stern (1–3). */
    fun buy(index: Int): Int? {
        if (phase != Phase.PLANNING) return null
        val defId = shop.getOrNull(index) ?: return null
        val def = RuneTacticsUnits.byId(defId)
        if (gold < def.cost) return null
        val freeBench = bench.indexOfFirst { it == null }
        val unit = UnitInstance(nextUid++, defId, 1)
        if (freeBench >= 0) {
            bench[freeBench] = unit
        } else {
            // Bank voll: nur kaufen, wenn die Einheit sofort zu ★2 verschmilzt.
            val existing = copies(defId, 1)
            if (existing.size < 2) {
                nextUid--
                return null
            }
            val target = existing[0]
            remove(existing[0])
            remove(existing[1])
            put(target, UnitInstance(nextUid++, defId, 2))
        }
        gold -= def.cost
        shop[index] = null
        return combineFrom(defId, if (freeBench >= 0) 1 else 2)
    }

    /** Verschmilzt 3 gleiche Einheiten zur nächsten Sternstufe (Brett zuerst). Liefert den höchsten Stern. */
    private fun combineFrom(defId: String, star: Int): Int {
        var current = star
        while (current < Stars.MAX) {
            val found = copies(defId, current)
            if (found.size < 3) break
            val target = found[0]
            found.take(3).forEach(::remove)
            put(target, UnitInstance(nextUid++, defId, current + 1))
            current++
        }
        return current
    }

    /** Fundorte gleicher Einheiten, Brett vor Bank. */
    private fun copies(defId: String, star: Int): List<Slot> {
        val onBoard = board.entries.filter { it.value.defId == defId && it.value.star == star }.map { Slot.Board(it.key) }
        val onBench = bench.indices.filter { bench[it]?.let { u -> u.defId == defId && u.star == star } == true }
            .map { Slot.Bench(it) }
        return onBoard + onBench
    }

    private fun remove(slot: Slot) {
        when (slot) {
            is Slot.Board -> board.remove(slot.cell)
            is Slot.Bench -> bench[slot.index] = null
        }
    }

    private fun put(slot: Slot, unit: UnitInstance) {
        when (slot) {
            is Slot.Board -> board[slot.cell] = unit
            is Slot.Bench -> bench[slot.index] = unit
        }
    }

    fun sell(slot: Slot): Int? {
        if (phase != Phase.PLANNING) return null
        val unit = unitAt(slot) ?: return null
        remove(slot)
        val value = Economy.sellValue(unit)
        gold += value
        pool[unit.defId] = poolCount(unit.defId) + Stars.copies(unit.star)
        return value
    }

    /** Verschiebt oder tauscht Einheiten zwischen Brett und Bank. */
    fun move(from: Slot, to: Slot): Boolean {
        if (phase != Phase.PLANNING || from == to) return false
        val unit = unitAt(from) ?: return false
        if (to is Slot.Board && !Board.isPlayerCell(to.cell)) return false
        if (to is Slot.Bench && to.index !in bench.indices) return false
        val other = unitAt(to)
        if (other == null && from is Slot.Bench && to is Slot.Board && board.size >= boardCap) return false
        remove(from)
        if (other != null) {
            remove(to)
            put(from, other)
        }
        put(to, unit)
        return true
    }

    // --- Kampf ---

    /** Füllt freie Brettplätze mit Einheiten von der Bank auf (wie in TFT). */
    private fun autoFill() {
        for (i in bench.indices) {
            if (board.size >= boardCap) return
            val unit = bench[i] ?: continue
            val rows = if (unit.def.range <= 1) (Board.PLAYER_FIRST_ROW until Board.ROWS)
            else (Board.ROWS - 1 downTo Board.PLAYER_FIRST_ROW)
            val cell = rows.firstNotNullOfOrNull { row ->
                (0 until Board.COLS).sortedBy { kotlin.math.abs(it - Board.COLS / 2) }
                    .map { Cell(it, row) }.firstOrNull { it !in board }
            } ?: return
            bench[i] = null
            board[cell] = unit
        }
    }

    fun startCombat(): Boolean {
        if (phase != Phase.PLANNING) return false
        autoFill()
        preCombat = snapshot()
        phase = Phase.COMBAT
        combat = RuneTacticsCombat(
            players = board.map { (cell, unit) -> unit to cell },
            enemies = enemyArmy,
            enemyScale = enemyScale,
            seed = seed * 92821 + round,
        )
        return true
    }

    /** Wertet den beendeten Kampf aus und bereitet die nächste Runde vor. */
    fun finishCombat(): RoundResult? {
        val fight = combat ?: return null
        if (phase != Phase.COMBAT) return null
        fight.runToEnd()
        val outcome = fight.outcome ?: Outcome.DRAW
        var hpLost = 0
        if (outcome == Outcome.WIN) {
            winStreak++
            lossStreak = 0
        } else {
            lossStreak++
            winStreak = 0
            val full = Economy.damage(round, fight.survivingEnemyStars())
            hpLost = if (outcome == Outcome.DRAW) (full + 1) / 2 else full
            hp -= hpLost
        }
        combat = null
        preCombat = null
        coinsEarned += if (outcome == Outcome.WIN) 2 else 1
        if (hp <= 0) {
            hp = 0
            phase = Phase.GAME_OVER
            return RoundResult(round, outcome, hpLost, 0, 0, 0)
        }
        val interest = Economy.interest(gold)
        val streak = Economy.streakBonus(maxOf(winStreak, lossStreak))
        val income = Economy.BASE_INCOME + interest + streak + if (outcome == Outcome.WIN) Economy.WIN_BONUS else 0
        val result = RoundResult(round, outcome, hpLost, income, interest, streak)
        round++
        gold += income
        addXp(Economy.PASSIVE_XP)
        rollShop()
        enemyArmy = RuneTacticsEnemyAI.army(seed, round)
        phase = Phase.PLANNING
        return result
    }

    // --- Spielstand ---

    /** Im Kampf wird der Stand vom Kampfbeginn geliefert, damit die Aufstellung nicht nachträglich geändert wird. */
    fun snapshot(): RuneTacticsSnapshot {
        preCombat?.let { if (phase == Phase.COMBAT) return it.copy(inCombat = true) }
        return RuneTacticsSnapshot(
            round = round,
            hp = hp,
            gold = gold,
            level = level,
            xp = xp,
            winStreak = winStreak,
            lossStreak = lossStreak,
            coinsEarned = coinsEarned,
            board = board.map { (cell, unit) -> BoardEntry(cell.col, cell.row, unit) },
            bench = bench.toList(),
            shop = shop.toList(),
            pool = HashMap(pool),
            enemyArmy = enemyArmy,
            nextUid = nextUid,
            rngCounter = rngCounter,
        )
    }

    fun restore(s: RuneTacticsSnapshot) {
        round = s.round
        hp = s.hp
        gold = s.gold
        level = s.level
        xp = s.xp
        winStreak = s.winStreak
        lossStreak = s.lossStreak
        coinsEarned = s.coinsEarned
        board.clear()
        s.board.forEach { board[Cell(it.col, it.row)] = it.unit }
        bench.fill(null)
        s.bench.take(bench.size).forEachIndexed { i, u -> bench[i] = u }
        shop.fill(null)
        s.shop.take(shop.size).forEachIndexed { i, id -> shop[i] = id }
        pool.clear()
        RuneTacticsUnits.all.forEach { pool[it.id] = s.pool[it.id] ?: 0 }
        enemyArmy = s.enemyArmy
        nextUid = s.nextUid
        rngCounter = s.rngCounter
        combat = null
        preCombat = null
        phase = Phase.PLANNING
        if (s.inCombat) startCombat()
    }
}
