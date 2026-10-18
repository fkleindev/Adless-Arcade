package com.adlessarcade.games.runetactics

import kotlinx.serialization.Serializable

enum class TraitKind { ORIGIN, CLASS }

/** Synergien: Stufen werden mit unterschiedlichen Einheiten (nicht Kopien) auf dem Brett erreicht. */
enum class Trait(
    val title: String,
    val kind: TraitKind,
    val tiers: List<Int>,
    val description: String,
) {
    UNDEAD("Untote", TraitKind.ORIGIN, listOf(2, 4), "Untote erheben sich einmal pro Kampf mit 30 % / 60 % Leben."),
    ELF("Elfen", TraitKind.ORIGIN, listOf(2, 4), "Elfen weichen 20 % / 40 % der Angriffe aus."),
    DWARF("Zwerge", TraitKind.ORIGIN, listOf(2, 4), "Zwerge erhalten +30 / +70 Rüstung und Magieresistenz."),
    WARRIOR("Krieger", TraitKind.CLASS, listOf(2, 4), "Krieger verursachen +20 % / +45 % Schaden."),
    GUARDIAN(
        "Wächter", TraitKind.CLASS, listOf(2, 4),
        "Zum Kampfbeginn erhalten Wächter und angrenzende Verbündete einen Schild von 150 / 350.",
    ),
    MAGE("Magier", TraitKind.CLASS, listOf(2, 4), "Fähigkeiten von Magiern verursachen +30 % / +70 % Schaden.");

    /** Erreichte Stufe (0 = inaktiv) bei [count] verschiedenen Einheiten. */
    fun level(count: Int): Int = tiers.count { count >= it }

    /** Benötigte Anzahl für die nächste Stufe, oder null wenn die höchste erreicht ist. */
    fun nextTier(count: Int): Int? = tiers.firstOrNull { count < it }
}

enum class AbilityKind {
    /** Körperlicher Schlag mit value × Angriffsschaden auf das Ziel. */
    STRIKE,
    /** Magischer Schaden auf das Ziel. */
    BOLT,
    /** Magischer Schaden auf das Ziel und angrenzende Gegner. */
    SPLASH,
    /** Magischer Schaden an allen angrenzenden Gegnern. */
    WHIRL,
    /** Schild für sich selbst. */
    SHIELD_SELF,
    /** Heilt den am stärksten verletzten Verbündeten. */
    HEAL_ALLY,
    /** Magischer Schaden auf das Ziel, heilt um die Hälfte. */
    DRAIN,
    /** Magischer Schaden und Betäubung an angrenzenden Gegnern. */
    QUAKE,
    /** Magischer Schaden und Betäubung am Ziel und angrenzenden Gegnern. */
    FROST,
    /** Blitz springt auf mehrere Gegner. */
    CHAIN,
    /** Sprung zum entferntesten Gegner, Flächenschaden und Betäubung bei der Landung. */
    LEAP,
    /** Schild für alle Verbündeten und Schaden an Gegnern im Umkreis von 2 Feldern. */
    MOON,
}

data class Ability(
    val name: String,
    val kind: AbilityKind,
    val value: Float,
    val description: String,
    val stun: Float = 0f,
    val count: Int = 0,
) {
    /** Braucht ein Ziel in Reichweite (sonst wird gewartet). */
    val needsTarget: Boolean
        get() = kind in setOf(
            AbilityKind.STRIKE, AbilityKind.BOLT, AbilityKind.SPLASH,
            AbilityKind.DRAIN, AbilityKind.FROST, AbilityKind.CHAIN,
        )
}

data class UnitDef(
    val id: String,
    val name: String,
    val cost: Int,
    val origin: Trait,
    val role: Trait,
    val hp: Float,
    val ad: Float,
    val attackSpeed: Float,
    val range: Int,
    val armor: Float,
    val mr: Float,
    val maxMana: Int,
    val startMana: Int,
    val ability: Ability,
) {
    val traits: List<Trait> get() = listOf(origin, role)
}

@Serializable
data class UnitInstance(val uid: Long, val defId: String, val star: Int = 1) {
    val def: UnitDef get() = RuneTacticsUnits.byId(defId)
}

@Serializable
data class Cell(val col: Int, val row: Int)

/** Ort einer eigenen Einheit in der Planungsphase. */
sealed interface Slot {
    data class Board(val cell: Cell) : Slot
    data class Bench(val index: Int) : Slot
}

/** Gegnerische Einheit mit fester Position (obere Brett-Hälfte). */
@Serializable
data class PlacedUnit(val defId: String, val star: Int, val col: Int, val row: Int) {
    val def: UnitDef get() = RuneTacticsUnits.byId(defId)
}

enum class Phase { PLANNING, COMBAT, GAME_OVER }

enum class Outcome { WIN, LOSS, DRAW }

/** Ergebnis einer Runde inkl. Einkommen für die nächste. */
data class RoundResult(
    val round: Int,
    val outcome: Outcome,
    val hpLost: Int,
    val income: Int,
    val interest: Int,
    val streakBonus: Int,
)

object Stars {
    const val MAX = 3
    private val STAT = floatArrayOf(1f, 1.8f, 3.24f)
    private val ABILITY = floatArrayOf(1f, 1.6f, 2.6f)

    fun stat(star: Int): Float = STAT[star - 1]
    fun ability(star: Int): Float = ABILITY[star - 1]

    /** Anzahl ★1-Kopien in einer Einheit dieses Sterns. */
    fun copies(star: Int): Int = when (star) {
        1 -> 1
        2 -> 3
        else -> 9
    }
}

object Board {
    const val COLS = 7
    const val ROWS = 8
    /** Eigene Reihen 4..7 (unten), Gegner 0..3 (oben). */
    const val PLAYER_FIRST_ROW = 4
    const val BENCH_SIZE = 8
    const val SHOP_SIZE = 5

    fun isPlayerCell(cell: Cell): Boolean =
        cell.col in 0 until COLS && cell.row in PLAYER_FIRST_ROW until ROWS

    fun inside(col: Int, row: Int): Boolean = col in 0 until COLS && row in 0 until ROWS
}

object Traits {
    /** Anzahl verschiedener Einheiten pro Synergie. */
    fun count(defs: Collection<UnitDef>): Map<Trait, Int> {
        val result = LinkedHashMap<Trait, Int>()
        defs.distinctBy { it.id }.forEach { def -> def.traits.forEach { result[it] = (result[it] ?: 0) + 1 } }
        return result
    }
}
