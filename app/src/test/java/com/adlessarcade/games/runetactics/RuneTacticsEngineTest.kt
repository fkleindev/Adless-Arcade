package com.adlessarcade.games.runetactics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class RuneTacticsEngineTest {

    /** Setzt einen festen Shop, damit Käufe vorhersagbar sind. */
    private fun RuneTacticsEngine.forceShop(vararg ids: String) {
        for (i in shop.indices) shop[i] = ids.getOrNull(i)
    }

    private fun RuneTacticsEngine.giveGold(amount: Int) {
        // Gold nur über Verkäufe erhöhbar – deshalb per Reflection für Tests.
        val field = RuneTacticsEngine::class.java.getDeclaredField("gold")
        field.isAccessible = true
        field.setInt(this, amount)
    }

    // --- Shop & Sterne ---

    @Test
    fun startState() {
        val e = RuneTacticsEngine(1)
        assertEquals(Economy.START_GOLD, e.gold)
        assertEquals(Economy.START_LEVEL, e.level)
        assertEquals(1, e.round)
        assertTrue(e.shop.all { it != null })
        // Level 2: nur 1-Gold-Einheiten im Shop
        assertTrue(e.shop.all { RuneTacticsUnits.byId(it!!).cost == 1 })
        assertEquals(2, e.enemyArmy.size)
    }

    @Test
    fun threeCopies_combineToTwoStar() {
        val e = RuneTacticsEngine(2)
        e.giveGold(20)
        e.forceShop("skeleton", "skeleton", "skeleton")
        assertEquals(1, e.buy(0))
        assertEquals(1, e.buy(1))
        assertTrue(e.wouldCombine("skeleton"))
        assertEquals(2, e.buy(2))
        val units = e.bench.filterNotNull()
        assertEquals(1, units.size)
        assertEquals(2, units[0].star)
        assertEquals(17, e.gold)
    }

    @Test
    fun combine_keepsBoardPosition() {
        val e = RuneTacticsEngine(3)
        e.giveGold(20)
        e.forceShop("wisp", "wisp", "wisp")
        e.buy(0)
        val cell = Cell(3, 7)
        assertTrue(e.move(Slot.Bench(0), Slot.Board(cell)))
        e.buy(1)
        e.buy(2)
        assertEquals(2, e.board[cell]?.star)
        assertTrue(e.bench.all { it == null })
    }

    @Test
    fun nineCopies_combineToThreeStar() {
        val e = RuneTacticsEngine(4)
        e.giveGold(50)
        repeat(3) {
            e.forceShop("wisp", "wisp", "wisp")
            repeat(3) { i -> e.buy(i) }
        }
        val units = e.bench.filterNotNull() + e.board.values
        assertEquals(1, units.size)
        assertEquals(3, units[0].star)
    }

    @Test
    fun fullBench_onlyBuysWhenCombining() {
        val e = RuneTacticsEngine(5)
        e.giveGold(100)
        // 6 verschiedene Einheiten + 2 Grablichter füllen die Bank
        listOf("skeleton", "bladedancer", "dwarfguard", "apprentice", "axeman", "runesmith").forEach {
            e.forceShop(it)
            assertNotNull(e.buy(0))
        }
        e.forceShop("wisp", "wisp")
        e.buy(0)
        e.buy(1)
        assertTrue(e.bench.all { it != null })
        e.forceShop("lich", "wisp")
        assertNull(e.buy(0)) // kein Platz, keine Verschmelzung
        assertEquals(2, e.buy(1)) // dritte Kopie verschmilzt trotz voller Bank
        assertEquals(1, e.bench.count { it == null })
    }

    @Test
    fun sell_refundsGoldAndPool() {
        val e = RuneTacticsEngine(6)
        e.giveGold(10)
        e.forceShop("skeleton")
        val before = e.poolCount("skeleton")
        e.buy(0)
        assertEquals(9, e.gold)
        assertEquals(1, e.sell(Slot.Bench(0)))
        assertEquals(10, e.gold)
        assertEquals(before + 1, e.poolCount("skeleton"))
    }

    @Test
    fun reroll_returnsUnboughtToPool() {
        val e = RuneTacticsEngine(7)
        val total = { RuneTacticsUnits.all.sumOf { e.poolCount(it.id) } + e.shop.count { it != null } }
        val before = total()
        assertTrue(e.reroll())
        assertEquals(before, total())
        assertEquals(Economy.START_GOLD - Economy.REROLL_COST, e.gold)
    }

    @Test
    fun boardCap_limitsPlacement() {
        val e = RuneTacticsEngine(8)
        e.giveGold(20)
        e.forceShop("skeleton", "wisp", "apprentice")
        repeat(3) { e.buy(it) }
        assertTrue(e.move(Slot.Bench(0), Slot.Board(Cell(0, 4))))
        assertTrue(e.move(Slot.Bench(1), Slot.Board(Cell(1, 4))))
        assertFalse(e.move(Slot.Bench(2), Slot.Board(Cell(2, 4)))) // Level 2 = 2 Plätze
        // Tauschen geht trotzdem
        assertTrue(e.move(Slot.Bench(2), Slot.Board(Cell(0, 4))))
        // Gegnerische Hälfte ist tabu
        assertFalse(e.move(Slot.Board(Cell(1, 4)), Slot.Board(Cell(1, 2))))
    }

    @Test
    fun buyXp_levelsUp() {
        val e = RuneTacticsEngine(9)
        e.giveGold(20)
        assertTrue(e.buyXp()) // 4 XP: Level 2 → 3 (2 XP), Rest 2
        assertEquals(3, e.level)
        assertEquals(2, e.xp)
    }

    // --- Wirtschaft ---

    @Test
    fun economy_rules() {
        assertEquals(0, Economy.interest(9))
        assertEquals(3, Economy.interest(34))
        assertEquals(5, Economy.interest(120))
        assertEquals(0, Economy.streakBonus(1))
        assertEquals(1, Economy.streakBonus(2))
        assertEquals(2, Economy.streakBonus(4))
        assertEquals(3, Economy.streakBonus(7))
    }

    @Test
    fun shopOdds_respectLevel() {
        val rng = Random(1)
        repeat(500) {
            val cost = Economy.rollCost(3, rng) { true }!!
            assertTrue(cost <= 2)
        }
        val high = (0 until 2000).map { Economy.rollCost(9, rng) { true }!! }
        assertTrue(high.count { it == 5 } > 150)
    }

    @Test
    fun roundFlow_incomeAndDamage() {
        val e = RuneTacticsEngine(10)
        // Ohne Einheiten verliert man sofort
        assertTrue(e.startCombat())
        val result = e.finishCombat()!!
        assertEquals(Outcome.LOSS, result.outcome)
        assertEquals(Economy.damage(1, 2), result.hpLost)
        assertEquals(Economy.START_HP - result.hpLost, e.hp)
        assertEquals(2, e.round)
        assertEquals(Phase.PLANNING, e.phase)
        assertEquals(Economy.START_GOLD + Economy.BASE_INCOME, e.gold)
    }

    @Test
    fun startCombat_autoFillsFromBench() {
        val e = RuneTacticsEngine(11)
        e.forceShop("skeleton", "wisp")
        e.buy(0)
        e.buy(1)
        e.startCombat()
        assertEquals(2, e.board.size)
        assertTrue(e.board.keys.all { Board.isPlayerCell(it) })
    }

    @Test
    fun gameOver_whenHpZero() {
        val e = RuneTacticsEngine(12)
        var guard = 0
        while (!e.isGameOver && guard++ < 200) {
            e.startCombat()
            e.finishCombat()
        }
        assertTrue(e.isGameOver)
        assertEquals(0, e.hp)
    }

    // --- Kampf ---

    private fun army(vararg ids: Pair<String, Int>): List<Pair<UnitInstance, Cell>> =
        ids.mapIndexed { i, (id, star) -> UnitInstance(i.toLong(), id, star) to Cell(i % Board.COLS, 4 + i / Board.COLS) }

    private fun enemies(vararg ids: Pair<String, Int>): List<PlacedUnit> =
        ids.mapIndexed { i, (id, star) -> PlacedUnit(id, star, i % Board.COLS, 3 - i / Board.COLS) }

    @Test
    fun combat_isDeterministic() {
        fun run(): Pair<Outcome?, Float> {
            val c = RuneTacticsCombat(
                army("skeleton" to 1, "apprentice" to 1, "warden" to 1),
                enemies("bladedancer" to 1, "wisp" to 1, "dwarfguard" to 1),
                1f, seed = 42,
            )
            c.runToEnd()
            return c.outcome to c.time
        }
        assertEquals(run(), run())
    }

    @Test
    fun combat_strongerArmyWins() {
        repeat(5) { seed ->
            val c = RuneTacticsCombat(
                army("deathknight" to 2, "lich" to 2, "thane" to 2),
                enemies("skeleton" to 1, "wisp" to 1),
                1f, seed = seed.toLong(),
            )
            c.runToEnd()
            assertEquals(Outcome.WIN, c.outcome)
        }
    }

    @Test
    fun combat_alwaysEnds() {
        val c = RuneTacticsCombat(
            army("dwarfguard" to 3, "warden" to 3),
            enemies("dwarfguard" to 3, "warden" to 3),
            1f, seed = 3,
        )
        c.runToEnd()
        assertNotNull(c.outcome)
        assertTrue(c.time <= RuneTacticsCombat.TIME_LIMIT + RuneTacticsCombat.TICK)
    }

    @Test
    fun undead_revive() {
        val c = RuneTacticsCombat(army("skeleton" to 1, "wisp" to 1), enemies("queen" to 3), 1f, seed = 1)
        var revived = 0
        while (!c.isOver) {
            c.step()
            revived += c.drainEvents().count { it is CombatEvent.Revive }
        }
        assertEquals(2, revived)
    }

    @Test
    fun allAbilities_castWithoutCrash() {
        RuneTacticsUnits.all.forEach { def ->
            val c = RuneTacticsCombat(
                army(def.id to 2, "skeleton" to 1),
                enemies("dwarfguard" to 2, "wisp" to 1, "apprentice" to 1),
                1f, seed = 5,
            )
            var casts = 0
            while (!c.isOver) {
                c.step()
                casts += c.drainEvents().count { it is CombatEvent.Cast && c.find(it.caster)?.def == def }
            }
            assertTrue("${def.name} hat nie gezaubert", casts > 0)
        }
    }

    // --- KI ---

    @Test
    fun enemyBudget_growsAndArmyFits() {
        var last = 0f
        for (round in 1..60) {
            val budget = RuneTacticsEnemyAI.budget(round)
            assertTrue(budget > last)
            last = budget
            val army = RuneTacticsEnemyAI.army(99, round)
            assertEquals(RuneTacticsEnemyAI.unitCount(round), army.size)
            assertTrue(army.all { it.row in 0 until Board.PLAYER_FIRST_ROW && it.col in 0 until Board.COLS })
            assertEquals(army.size, army.map { Cell(it.col, it.row) }.toSet().size)
            assertTrue(army.all { it.def.cost <= RuneTacticsEnemyAI.maxCost(round) })
            assertTrue(army.all { it.star <= RuneTacticsEnemyAI.maxStar(round) })
        }
        assertEquals(1f, RuneTacticsEnemyAI.scale(25))
        assertTrue(RuneTacticsEnemyAI.scale(45) > RuneTacticsEnemyAI.scale(40))
    }

    @Test
    fun enemyArmy_isStablePerSeedAndRound() {
        assertEquals(RuneTacticsEnemyAI.army(5, 12), RuneTacticsEnemyAI.army(5, 12))
    }

    // --- Spielstand ---

    @Test
    fun saveRoundTrip() {
        val e = RuneTacticsEngine(13)
        e.forceShop("skeleton", "wisp", "apprentice")
        e.buy(0)
        e.move(Slot.Bench(0), Slot.Board(Cell(3, 4)))
        val json = RuneTacticsSaves.encode(e.snapshot(), bestAtStart = 7, seed = e.seed)
        val save = RuneTacticsSaves.decode(json)!!
        val copy = RuneTacticsEngine(save.seed).also { it.restore(save.snapshot) }
        assertEquals(e.snapshot(), copy.snapshot())
        assertEquals(7, save.bestAtStart)
        // Gleicher Zufall nach dem Laden
        e.reroll()
        copy.reroll()
        assertEquals(e.shop.toList(), copy.shop.toList())
    }

    @Test
    fun saveDuringCombat_replaysSameFight() {
        val e = RuneTacticsEngine(14)
        e.forceShop("skeleton", "wisp")
        e.buy(0)
        e.buy(1)
        e.startCombat()
        repeat(40) { e.combat!!.step() }
        val snap = e.snapshot()
        assertTrue(snap.inCombat)
        val copy = RuneTacticsEngine(e.seed).also { it.restore(snap) }
        assertEquals(Phase.COMBAT, copy.phase)
        val a = e.finishCombat()!!
        val b = copy.finishCombat()!!
        assertEquals(a, b)
    }
}
