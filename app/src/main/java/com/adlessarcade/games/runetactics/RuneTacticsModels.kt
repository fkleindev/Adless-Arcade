package com.adlessarcade.games.runetactics

import kotlinx.serialization.Serializable

/** Stufen aller Synergien: 2, 4 und 6 verschiedene Einheiten. */
private val TIERS = listOf(2, 4, 6)

enum class TraitKind {
    ORIGIN,
    CLASS,
    /** Kreaturen und Bosse – keine Synergie. */
    MONSTER,
}

/** Synergien: Stufen werden mit unterschiedlichen Einheiten (nicht Kopien) auf dem Brett erreicht. */
enum class Trait(
    val title: String,
    val kind: TraitKind,
    val tiers: List<Int>,
    val description: String,
    /** Effektstärke je Stufe (Index 0 = Stufe 1). */
    val values: List<Float> = emptyList(),
) {
    UNDEAD(
        "Untote", TraitKind.ORIGIN, TIERS, "Untote erheben sich einmal pro Kampf mit 30 % / 60 % / 90 % Leben.",
        listOf(0.3f, 0.6f, 0.9f),
    ),
    ELF("Elfen", TraitKind.ORIGIN, TIERS, "Elfen weichen 20 % / 40 % / 60 % der Angriffe aus.", listOf(0.2f, 0.4f, 0.6f)),
    DWARF(
        "Zwerge", TraitKind.ORIGIN, TIERS, "Zwerge erhalten +30 / +70 / +120 Rüstung und Magieresistenz.",
        listOf(30f, 70f, 120f),
    ),
    DEMON(
        "Dämonen", TraitKind.ORIGIN, TIERS, "Dämonen heilen sich um 15 % / 30 % / 50 % ihres Schadens.",
        listOf(0.15f, 0.3f, 0.5f),
    ),
    ELEMENTAL(
        "Elementare", TraitKind.ORIGIN, TIERS, "Elementare starten mit +20 / +40 / +70 Mana.",
        listOf(20f, 40f, 70f),
    ),
    WARRIOR("Krieger", TraitKind.CLASS, TIERS, "Krieger verursachen +20 % / +45 % / +80 % Schaden.", listOf(0.2f, 0.45f, 0.8f)),
    GUARDIAN(
        "Wächter", TraitKind.CLASS, TIERS,
        "Zum Kampfbeginn erhalten Wächter und angrenzende Verbündete einen Schild von 150 / 350 / 600.",
        listOf(150f, 350f, 600f),
    ),
    MAGE(
        "Magier", TraitKind.CLASS, TIERS, "Fähigkeiten von Magiern verursachen +30 % / +70 % / +120 % Schaden.",
        listOf(0.3f, 0.7f, 1.2f),
    ),
    RANGER(
        "Waldläufer", TraitKind.CLASS, TIERS,
        "Jeder Angriff erhöht das Angriffstempo um 5 % / 10 % / 16 % (bis zu 10-mal).", listOf(0.05f, 0.1f, 0.16f),
    ),
    ASSASSIN(
        "Assassinen", TraitKind.CLASS, TIERS,
        "Springen zu Kampfbeginn hinter die feindlichen Linien; 20 % / 35 % / 50 % Chance auf kritische Treffer (+60 %).",
        listOf(0.2f, 0.35f, 0.5f),
    ),
    BEAST("Kreatur", TraitKind.MONSTER, emptyList(), "Wilde Kreaturen lassen Item-Komponenten fallen."),
    BOSS("Boss", TraitKind.MONSTER, emptyList(), "Bosse kündigen ihre Flächenangriffe an. Ein Sieg bringt eine Belohnung zur Wahl.");

    /** Erreichte Stufe (0 = inaktiv) bei [count] verschiedenen Einheiten. */
    fun level(count: Int): Int = tiers.count { count >= it }

    /** Effektstärke der Stufe [level]; 0 wenn inaktiv. */
    fun value(level: Int): Float = if (level <= 0) 0f else values[level - 1]

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
    /** Angekündigter Flächenangriff (Bosse): Zone wird markiert, schlägt nach kurzer Verzögerung ein. Radius = count. */
    SLAM,
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
    val traits: List<Trait> get() = if (origin == role) listOf(origin) else listOf(origin, role)
    val isBoss: Boolean get() = role == Trait.BOSS
}

@Serializable
data class UnitInstance(
    val uid: Long,
    val defId: String,
    val star: Int = 1,
    /** Item-IDs (max. 3). */
    val items: List<String> = emptyList(),
) {
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
data class PlacedUnit(
    val defId: String,
    val star: Int,
    val col: Int,
    val row: Int,
    val items: List<String> = emptyList(),
    /** Eigene Werte-Skalierung (Kreaturen, Bosse), zusätzlich zum Aufstieg. */
    val scale: Float = 1f,
) {
    val def: UnitDef get() = RuneTacticsUnits.byId(defId)
}

enum class RoundType(val title: String) {
    NORMAL("KI-Armee"),
    CREEPS("Kreaturen"),
    BOSS("Boss");

    companion object {
        /** 5er-Zyklus: x4 Kreaturen, x5 Boss, sonst KI-Armee. */
        fun of(round: Int): RoundType = when (round % 5) {
            4 -> CREEPS
            0 -> BOSS
            else -> NORMAL
        }
    }
}

enum class RewardKind { GOLD, COMPONENTS, ITEM, UNIT }

/** Belohnung nach einem Bosssieg (eine von drei wählen). */
@Serializable
data class Reward(val kind: RewardKind, val amount: Int = 0, val id: String? = null, val star: Int = 1) {
    val title: String
        get() = when (kind) {
            RewardKind.GOLD -> "$amount Gold"
            RewardKind.COMPONENTS -> "$amount Komponenten"
            RewardKind.ITEM -> RuneTacticsItems.byId(id!!).name
            RewardKind.UNIT -> "${"★".repeat(star)} ${RuneTacticsUnits.byId(id!!).name}"
        }

    val description: String
        get() = when (kind) {
            RewardKind.GOLD -> "Sofort aufs Konto."
            RewardKind.COMPONENTS -> "Zufällige Item-Komponenten."
            RewardKind.ITEM -> RuneTacticsItems.byId(id!!).description
            RewardKind.UNIT -> "Kommt auf die Bank (bei voller Bank als Gold)."
        }
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
    val type: RoundType = RoundType.NORMAL,
    /** Erbeutete Items (Kreaturen- und Bossrunden). */
    val loot: List<String> = emptyList(),
    /** Gold aus vollem Inventar oder Bank. */
    val overflowGold: Int = 0,
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
    fun count(defs: Collection<UnitDef>, emblems: Map<Trait, Int> = emptyMap()): Map<Trait, Int> {
        val result = LinkedHashMap<Trait, Int>()
        defs.distinctBy { it.id }.forEach { def -> def.traits.forEach { result[it] = (result[it] ?: 0) + 1 } }
        // Embleme zählen nur, wenn die Synergie schon mit mindestens einer Einheit vertreten ist.
        emblems.forEach { (trait, n) -> result[trait]?.let { result[trait] = it + n } }
        return result
    }
}
