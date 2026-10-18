package com.adlessarcade.games.runetactics

import org.junit.Assert.assertTrue
import org.junit.Test

/** Ein einfacher Bot spielt ganze Läufe – grober Check, dass die Endlos-Kurve fordernd, aber erreichbar ist. */
class RuneTacticsBalanceTest {

    private fun value(u: UnitInstance) = u.def.cost * Stars.copies(u.star)

    private fun RuneTacticsEngine.playRound() {
        // Leveln, solange das Brett im Verhältnis zur Runde klein ist
        while (level < minOf(9, 2 + round / 3) && gold >= Economy.XP_COST + 2) buyXp()
        var rerolls = 0
        while (true) {
            // Kopien bevorzugen, dann teure Einheiten
            val owned = (board.values + bench.filterNotNull()).map { it.defId }.toSet()
            val index = shop.indices
                .filter { i -> shop[i]?.let { RuneTacticsUnits.byId(it).cost <= gold } == true }
                .maxByOrNull { i ->
                    val def = RuneTacticsUnits.byId(shop[i]!!)
                    (if (def.id in owned) 10 else 0) + def.cost
                }
            if (index != null && buy(index) != null) continue
            if (bench.all { it != null }) {
                val worst = bench.indices.minBy { value(bench[it]!!) }
                sell(Slot.Bench(worst))
                continue
            }
            if (gold >= 12 + Economy.REROLL_COST && rerolls < 3) {
                reroll()
                rerolls++
                continue
            }
            break
        }
        // Stärkste Einheiten aufs Brett
        repeat(Board.BENCH_SIZE) {
            val bestBench = bench.indices.filter { bench[it] != null }.maxByOrNull { value(bench[it]!!) } ?: return@repeat
            if (board.size < boardCap) {
                startFill(bestBench)
            } else {
                val weakest = board.entries.minByOrNull { value(it.value) } ?: return@repeat
                if (value(weakest.value) < value(bench[bestBench]!!)) move(Slot.Bench(bestBench), Slot.Board(weakest.key))
            }
        }
        // Items reihum auf die wertvollsten Einheiten
        val carriers = board.entries.sortedByDescending { value(it.value) }.map { Slot.Board(it.key) }
        var attempts = 0
        while (items.isNotEmpty() && carriers.isNotEmpty() && attempts++ < 20) {
            if (carriers.none { equip(0, it) != null }) break
        }
        startCombat()
        finishCombat()
        if (pendingRewards.isNotEmpty()) chooseReward(pendingRewards.indexOfFirst { it.kind == RewardKind.ITEM }.coerceAtLeast(0))
    }

    private fun RuneTacticsEngine.startFill(benchIndex: Int) {
        val unit = bench[benchIndex] ?: return
        val rows = if (unit.def.range <= 1) 4..7 else 7 downTo 4
        val cell = rows.flatMap { r -> (0 until Board.COLS).map { Cell(it, r) } }.first { it !in board }
        move(Slot.Bench(benchIndex), Slot.Board(cell))
    }

    @Test
    fun botReachesMidGame() {
        val rounds = (1L..12L).map { seed ->
            val e = RuneTacticsEngine(seed)
            while (!e.isGameOver && e.round < 200) e.playRound()
            e.round
        }
        println("Bot erreicht Runden: $rounds, Schnitt ${rounds.average()}")
        assertTrue("Zu schwer: $rounds", rounds.average() >= 12)
        assertTrue("Kein Ende in Sicht: $rounds", rounds.all { it < 200 })
    }
}
