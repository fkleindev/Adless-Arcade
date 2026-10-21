package com.adlessarcade.games.runetactics

import kotlin.random.Random

/** Regeln für Gold, Erfahrung und Shop – ohne Zustand, damit sie einzeln testbar sind. */
object Economy {
    const val START_GOLD = 6
    const val START_LEVEL = 2
    const val START_HP = 100
    const val MAX_LEVEL = 9
    const val REROLL_COST = 2
    const val XP_COST = 4
    const val XP_PER_BUY = 4
    const val PASSIVE_XP = 2
    const val BASE_INCOME = 5
    const val WIN_BONUS = 1
    const val MAX_INTEREST = 5

    /** Benötigte Erfahrung von Level n auf n+1 (Index = Level). */
    private val XP_TO_NEXT = intArrayOf(0, 2, 2, 6, 10, 20, 36, 56, 80)

    fun xpToNext(level: Int): Int = if (level >= MAX_LEVEL) 0 else XP_TO_NEXT[level]

    /** Shop-Wahrscheinlichkeiten in Prozent für Kosten 1–5, Index = Level. */
    private val ODDS = arrayOf(
        intArrayOf(100, 0, 0, 0, 0),
        intArrayOf(100, 0, 0, 0, 0),
        intArrayOf(100, 0, 0, 0, 0),
        intArrayOf(75, 25, 0, 0, 0),
        intArrayOf(55, 30, 15, 0, 0),
        intArrayOf(45, 33, 20, 2, 0),
        intArrayOf(30, 40, 25, 5, 0),
        intArrayOf(19, 30, 35, 15, 1),
        intArrayOf(15, 20, 35, 25, 5),
        intArrayOf(10, 15, 30, 30, 15),
    )

    fun odds(level: Int): IntArray = ODDS[level.coerceIn(1, MAX_LEVEL)]

    /** Chance in Promille, dass ein Shop-Platz eine ★2-Einheit anbietet, Index = Level. */
    private val STAR2_ODDS = intArrayOf(0, 0, 0, 0, 0, 20, 30, 50, 65, 80)

    fun star2Odds(level: Int): Int = STAR2_ODDS[level.coerceIn(1, MAX_LEVEL)]

    /** Preis eines Shop-Angebots: eine ★2 kostet zwei statt drei Kopien. */
    fun shopPrice(cost: Int, star: Int): Int = if (star >= 2) cost * 2 else cost

    fun interest(gold: Int, cap: Int = MAX_INTEREST): Int = (gold / 10).coerceAtMost(cap)

    fun streakBonus(streak: Int): Int = when {
        streak >= 5 -> 3
        streak >= 3 -> 2
        streak >= 2 -> 1
        else -> 0
    }

    /** Verlorene Leben bei einer Niederlage. */
    fun damage(round: Int, survivingEnemyStars: Int): Int = 2 + round / 3 + survivingEnemyStars

    fun sellValue(unit: UnitInstance): Int = unit.def.cost * Stars.copies(unit.star) - unit.discount

    /** Würfelt eine Kostenstufe nach den Odds des Levels; nur Stufen mit Kopien im Pool. */
    fun rollCost(level: Int, rng: Random, available: (Int) -> Boolean): Int? {
        val odds = odds(level)
        val weights = (1..5).map { cost -> if (available(cost)) odds[cost - 1] else 0 }
        val total = weights.sum()
        if (total <= 0) return (1..5).firstOrNull(available)
        var pick = rng.nextInt(total)
        for (cost in 1..5) {
            pick -= weights[cost - 1]
            if (pick < 0) return cost
        }
        return null
    }
}
