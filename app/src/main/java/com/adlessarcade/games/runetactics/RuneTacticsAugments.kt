package com.adlessarcade.games.runetactics

import kotlin.random.Random

/** Runen-Segen: dauerhafte Boni für den restlichen Lauf (1 aus 3 in bestimmten Runden). */
enum class Augment(
    val title: String,
    val description: String,
    /** Embleme zählen als zusätzliche Einheit dieser Synergie. */
    val emblem: Trait? = null,
    /** Kann mehrfach gewählt werden (Sofort-Belohnungen). */
    val repeatable: Boolean = false,
) {
    TREASURY("Schatzkammer", "Zinsen bis zu 8 Gold statt 5."),
    LUCKY_DICE("Glückswürfel", "Jede Runde einmal kostenlos würfeln."),
    WAR_CHEST("Kriegskasse", "Sofort 15 Gold.", repeatable = true),
    WISDOM("Weisheit", "+2 Erfahrung pro Runde."),
    COMMANDER("Feldherr", "+1 Platz auf dem Brett."),
    ARMORY("Waffenkammer", "Sofort 3 Item-Komponenten.", repeatable = true),
    SPRING("Heilquelle", "Sofort +25 Leben (max. 100).", repeatable = true),
    VITALITY("Lebensrune", "Deine Einheiten haben +15 % Leben."),
    FURY("Kriegsrune", "Deine Einheiten verursachen +12 % Schaden."),
    HASTE("Eilrune", "Deine Einheiten greifen 15 % schneller an."),
    WELLSPRING("Manaquelle", "Deine Einheiten starten mit +20 Mana."),
    WARD("Schutzrune", "Deine Einheiten starten mit einem Schild von 200."),
    EMBLEM_UNDEAD("Banner der Untoten", "Zählt als zusätzliche Untote (ab 1 Untoten auf dem Brett).", Trait.UNDEAD),
    EMBLEM_ELF("Banner der Elfen", "Zählt als zusätzliche Elfe (ab 1 Elfe auf dem Brett).", Trait.ELF),
    EMBLEM_DWARF("Banner der Zwerge", "Zählt als zusätzlicher Zwerg (ab 1 Zwerg auf dem Brett).", Trait.DWARF),
    EMBLEM_WARRIOR("Banner der Krieger", "Zählt als zusätzlicher Krieger (ab 1 Krieger auf dem Brett).", Trait.WARRIOR),
    EMBLEM_GUARDIAN("Banner der Wächter", "Zählt als zusätzlicher Wächter (ab 1 Wächter auf dem Brett).", Trait.GUARDIAN),
    EMBLEM_MAGE("Banner der Magier", "Zählt als zusätzlicher Magier (ab 1 Magier auf dem Brett).", Trait.MAGE);

    companion object {
        const val CHOICES = 3

        /** Segen-Runden: 3, 12, 22, danach alle 15 Runden. */
        fun isAugmentRound(round: Int): Boolean =
            round == 3 || round == 12 || round == 22 || (round > 22 && (round - 22) % 15 == 0)

        fun roll(owned: Collection<Augment>, rng: Random): List<Augment> =
            entries.filter { it.repeatable || it !in owned }.shuffled(rng).take(CHOICES)
    }
}

/** Flüche: ab Runde 15 können normale KI-Runden verflucht sein; sie werden in der Planungsphase angezeigt. */
enum class Curse(val title: String, val description: String) {
    BONE_ARMOR("Knochenpanzer", "Gegner starten mit einem Schild von 15 % ihrer Leben."),
    ROT("Fäulnis", "Deine Heilung ist um 50 % verringert."),
    AMBUSH("Hinterhalt", "Zwei Gegner springen zu Kampfbeginn in deine hintere Reihe."),
    BLOOD_MOON("Blutmond", "Gegner greifen 25 % schneller an."),
    SILENCE("Stille", "Deine Einheiten starten ohne Mana.");

    companion object {
        const val START_ROUND = 15

        /** Wahrscheinlichkeit für einen Fluch, steigt ab Runde 15 von 35 % bis 80 %. */
        fun chance(round: Int): Float = if (round < START_ROUND) 0f else (0.35f + 0.03f * (round - START_ROUND)).coerceAtMost(0.8f)

        fun roll(seed: Long, round: Int): Curse? {
            if (RoundType.of(round) != RoundType.NORMAL) return null
            val rng = Random(seed * 104729 + round)
            return if (rng.nextFloat() < chance(round)) entries[rng.nextInt(entries.size)] else null
        }
    }
}

/** Kampf-Modifikatoren aus Segen und Flüchen, an die Simulation übergeben. */
data class CombatModifiers(
    val playerHpPct: Float = 0f,
    val playerDamage: Float = 0f,
    val playerAttackSpeed: Float = 0f,
    val playerStartMana: Float = 0f,
    val playerShield: Float = 0f,
    val emblems: Map<Trait, Int> = emptyMap(),
    val curse: Curse? = null,
) {
    companion object {
        fun of(augments: Collection<Augment>, curse: Curse?): CombatModifiers = CombatModifiers(
            playerHpPct = if (Augment.VITALITY in augments) 0.15f else 0f,
            playerDamage = if (Augment.FURY in augments) 0.12f else 0f,
            playerAttackSpeed = if (Augment.HASTE in augments) 0.15f else 0f,
            playerStartMana = if (Augment.WELLSPRING in augments) 20f else 0f,
            playerShield = if (Augment.WARD in augments) 200f else 0f,
            emblems = augments.mapNotNull { it.emblem }.groupingBy { it }.eachCount(),
            curse = curse,
        )
    }
}
