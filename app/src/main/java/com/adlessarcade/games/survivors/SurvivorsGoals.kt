package com.adlessarcade.games.survivors

import kotlin.random.Random

/** Lauf-Ziele: drei zufällige pro Lauf, jedes bringt einmalig Runen. */
enum class RunGoal(val title: String, val runes: Int, val target: Int) {
    WEAPON_KILLS("750 Kills mit einer Waffe", 3, 750),
    EARLY_EVOLUTION("Erste Entwicklung vor 9:00", 4, 1),
    NO_HIT("60 s ohne Treffer (ab 3:00)", 3, 60),
    ELITES("6 Elites besiegen", 3, 6),
    LEVEL("Stufe 25 erreichen", 3, 25),
    FULL_ARSENAL("$MAX_WEAPONS Waffen gleichzeitig", 3, MAX_WEAPONS),
    RUNE_STONES("3 Runensteine aktivieren", 4, 3),
    FAST_BOSS("Zwischenboss in 45 s besiegen", 4, 1);

    /** Aktueller Fortschritt (0..[target]). */
    fun progress(s: SurvivorsState): Int = when (this) {
        WEAPON_KILLS -> s.weaponKills.values.maxOrNull() ?: 0
        EARLY_EVOLUTION -> if (s.firstEvolutionTime in 0f..EARLY_EVOLUTION_TIME) 1 else 0
        NO_HIT -> (s.time - maxOf(s.lastHurtTime, NO_HIT_FROM)).toInt().coerceAtLeast(0)
        ELITES -> s.elitesKilled
        LEVEL -> s.level
        FULL_ARSENAL -> s.weapons.size
        RUNE_STONES -> s.runeStonesActivated
        FAST_BOSS -> if (s.fastBossKill) 1 else 0
    }.coerceAtMost(target)

    fun isReached(s: SurvivorsState): Boolean = progress(s) >= target

    /** Ziele mit einem einzigen Schritt zeigen keinen Zähler. */
    val hasCounter: Boolean get() = target > 1

    companion object {
        const val PER_RUN = 3
        const val EARLY_EVOLUTION_TIME = 540f
        const val NO_HIT_FROM = 180f
        const val FAST_BOSS_TIME = 45f

        fun roll(random: Random): List<RunGoal> = entries.shuffled(random).take(PER_RUN)
    }
}
