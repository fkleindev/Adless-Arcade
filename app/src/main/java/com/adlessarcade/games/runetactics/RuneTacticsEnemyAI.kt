package com.adlessarcade.games.runetactics

import kotlin.math.abs
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

/**
 * KI-Kommandant: stellt pro Runde eine Armee aus einem wachsenden Budget zusammen. Für jeden Abschnitt von
 * 5 Runden wählt er einen Archetyp (Herkunft + Klasse) und bevorzugt passende Einheiten, damit Synergien aktiv sind.
 */
object RuneTacticsEnemyAI {
    const val ASCENSION_START = 25
    private const val STEEP_START = 40

    /** Gold-Wert der gegnerischen Armee (Kosten × ★1-Kopien). */
    fun budget(round: Int): Float = 2f + 2.2f * round + 0.1f * round * round

    fun unitCount(round: Int): Int = if (round >= ASCENSION_START) 10 else min(2 + (round - 1) / 3, 9)

    fun maxCost(round: Int): Int = when {
        round < 3 -> 1
        round < 6 -> 2
        round < 10 -> 3
        round < 15 -> 4
        else -> 5
    }

    fun maxStar(round: Int): Int = when {
        round < 5 -> 1
        round < 14 -> 2
        else -> 3
    }

    /** Werte-Multiplikator ab Runde 25 (Aufstieg): +4 % pro Runde, ab Runde 40 +6 %. */
    fun scale(round: Int): Float {
        var m = 1f
        if (round > ASCENSION_START) m *= 1.04f.pow(min(round, STEEP_START) - ASCENSION_START)
        if (round > STEEP_START) m *= 1.06f.pow(round - STEEP_START)
        return m
    }

    fun archetype(seed: Long, round: Int): Pair<Trait, Trait> {
        val rng = Random(seed * 31 + (round - 1) / 5)
        val origins = Trait.entries.filter { it.kind == TraitKind.ORIGIN }
        val classes = Trait.entries.filter { it.kind == TraitKind.CLASS }
        return origins[rng.nextInt(origins.size)] to classes[rng.nextInt(classes.size)]
    }

    fun army(seed: Long, round: Int): List<PlacedUnit> {
        val rng = Random(seed * 7919 + round)
        val (origin, role) = archetype(seed, round)
        val count = unitCount(round)
        val maxCost = maxCost(round)
        val maxStar = maxStar(round)
        var budget = budget(round)
        val chosen = ArrayList<Pair<UnitDef, Int>>()
        val options = RuneTacticsUnits.all.filter { it.cost <= maxCost }
            .flatMap { def -> (1..maxStar).map { star -> def to star } }

        for (slot in 0 until count) {
            val target = budget / (count - slot)
            val pick = options
                .filter { (def, star) -> value(def, star) <= maxOf(target * 1.25f, 1f) }
                .minByOrNull { (def, star) ->
                    val v = value(def, star)
                    var score = abs(v - target) / maxOf(target, 1f)
                    if (origin in def.traits) score -= 0.3f
                    if (role in def.traits) score -= 0.3f
                    if (chosen.any { it.first.id == def.id }) score += 0.25f
                    score + rng.nextFloat() * 0.25f
                } ?: (options.first { it.second == 1 })
            chosen += pick
            budget -= value(pick.first, pick.second)
        }
        return place(chosen, rng)
    }

    private fun value(def: UnitDef, star: Int): Float = (def.cost * Stars.copies(star)).toFloat()

    /** Nahkämpfer vorne (Reihe 3), Fernkämpfer hinten (Reihe 0). */
    private fun place(units: List<Pair<UnitDef, Int>>, rng: Random): List<PlacedUnit> {
        val taken = HashSet<Cell>()
        val sorted = units.sortedByDescending { it.first.cost * 10 + it.second }
        return sorted.map { (def, star) ->
            val rows = when {
                def.range <= 1 -> listOf(3, 2, 1, 0)
                def.range == 2 -> listOf(2, 1, 3, 0)
                else -> listOf(0, 1, 2, 3)
            }
            val cell = rows.firstNotNullOf { row ->
                centerOut(rng).map { Cell(it, row) }.firstOrNull { it !in taken }
            }
            taken += cell
            PlacedUnit(def.id, star, cell.col, cell.row)
        }
    }

    /** Spalten von der Mitte nach außen, leicht gemischt. */
    private fun centerOut(rng: Random): List<Int> =
        (0 until Board.COLS).sortedBy { abs(it - Board.COLS / 2) + rng.nextFloat() * 1.5f }
}
