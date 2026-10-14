package com.adlessarcade.games.gaterush

/*
 * Weltkoordinaten: x ∈ [-1, 1] ist die Straßenbreite, z = Distanz vor dem Helden.
 * Gegner, Tore, Kisten, Hindernisse und Pickups starten bei Z_FAR und bewegen sich auf z = 0 zu.
 */

enum class GateRushMode(val id: String) {
    ENDLESS("endless"),
    BOSS_RUSH("bossrush"),
    DAILY("daily");

    companion object {
        fun fromId(id: String?): GateRushMode = entries.firstOrNull { it.id == id } ?: ENDLESS
    }
}

enum class EnemyType(
    val speed: Float,
    val radius: Float,
    val baseHp: Float,
    val score: Int,
    val heartDamage: Int,
) {
    RUNNER(speed = 6.5f, radius = 0.11f, baseHp = 3f, score = 10, heartDamage = 1),
    SWARM(speed = 5.5f, radius = 0.07f, baseHp = 1.5f, score = 4, heartDamage = 1),
    BRUTE(speed = 3.8f, radius = 0.2f, baseHp = 14f, score = 40, heartDamage = 2),
    BOSS(speed = 2.4f, radius = 0.42f, baseHp = 160f, score = 500, heartDamage = 3),
    /** Pendelt seitlich hin und her. */
    ZIGZAG(speed = 5.5f, radius = 0.1f, baseHp = 4f, score = 15, heartDamage = 1),
    /** Zerfällt beim Tod in drei [SPLITLING]. */
    SPLITTER(speed = 4.2f, radius = 0.16f, baseHp = 9f, score = 25, heartDamage = 2),
    SPLITLING(speed = 6f, radius = 0.07f, baseHp = 2f, score = 3, heartDamage = 1),
    /** Frontaler Schild: normale Schüsse machen nur einen Bruchteil Schaden. */
    SHIELDED(speed = 3.6f, radius = 0.16f, baseHp = 10f, score = 35, heartDamage = 2),
}

class Enemy(
    val id: Long,
    val type: EnemyType,
    var x: Float,
    var z: Float,
    val maxHp: Float,
    /** Tempo-Faktor bei Erscheinen (Gegner werden über die Zeit schneller). */
    val speedFactor: Float = 1f,
    /** Mittelpunkt und Phase der Pendelbewegung (nur Zickzack). */
    val baseX: Float = x,
    val phase: Float = 0f,
) {
    var hp: Float = maxHp
    /** 1 → 0 nach einem Treffer, für den weißen Aufblitz-Effekt. */
    var hitFlash: Float = 0f
    /** Restdauer der Verlangsamung durch Frost. */
    var slowTimer: Float = 0f
}

enum class Weapon(
    val title: String,
    val damageFactor: Float,
    val rateFactor: Float,
    val speed: Float,
    val extraPierce: Int,
    val extraPellets: Int,
    /** Abstand der Projektile beim Mehrfachschuss. */
    val spread: Float,
    /** Wie stark die Projektile seitlich auffächern. */
    val fan: Float,
    val range: Float,
    /** Explodiert bei jedem Treffer (unabhängig vom Explosions-Upgrade). */
    val explosive: Boolean,
    /** Durchdringt den Schild von Schildträgern. */
    val armorPiercing: Boolean,
) {
    BLASTER("BLASTER", 1f, 1f, 30f, 0, 0, 0.07f, 0.5f, 34f, explosive = false, armorPiercing = false),
    SHOTGUN("SCHROT", 0.7f, 0.85f, 26f, 0, 2, 0.12f, 2.2f, 16f, explosive = false, armorPiercing = false),
    LASER("LASER", 0.6f, 1.3f, 60f, 3, 0, 0.05f, 0.2f, 34f, explosive = false, armorPiercing = true),
    ROCKET("RAKETEN", 2.2f, 0.5f, 18f, 0, 0, 0.1f, 0.4f, 34f, explosive = true, armorPiercing = true),
}

class Bullet(
    var x: Float,
    var z: Float,
    val vx: Float,
    var damage: Float,
    var pierceLeft: Int,
    val weapon: Weapon = Weapon.BLASTER,
) {
    val hitIds = HashSet<Long>(2)
    /** Tore, durch die dieses Projektil bereits geflogen ist (zählt pro Tor nur einmal). */
    val gateIds = HashSet<Long>(2)
    var alive = true
}

enum class GateType(
    val title: String,
    val hitsPerStep: Int,
    val percent: Boolean = false,
    /** Wie viele Stufen sich höchstens freischießen lassen (seltene Upgrades nur eine). */
    val maxShotSteps: Int = 1,
) {
    DAMAGE("SCHADEN", 4, maxShotSteps = 8),
    FIRE_RATE("FEUERRATE", 2, percent = true, maxShotSteps = 8),
    MULTISHOT("MULTISCHUSS", 45),
    PIERCE("DURCHSCHLAG", 40),
    HEART("LEBEN", 60),
    CRIT("KRIT", 3, percent = true, maxShotSteps = 6),
    SPLASH("EXPLOSION", 35),
    FROST("FROST", 35),
    SHIELD("SCHILD", 60),
    DRONE("DROHNE", 90),
    /** Waffenwechsel – kann nicht freigeschossen werden. */
    WEAPON("WAFFE", Int.MAX_VALUE / 16),
}

/**
 * Eine Torspur. Jeder Treffer bringt sie näher an den nächsthöheren Wert; jede weitere Stufe kostet
 * mehr Treffer (hitsPerStep · 1, 2, 3 …) und es gibt maximal [MAX_SHOT_STEPS] Stufen, damit es nicht eskaliert.
 */
class GateSide(val type: GateType, val baseValue: Int, val weapon: Weapon? = null) {
    companion object {
        const val MAX_SHOT_STEPS = 8
    }

    var hits: Int = 0
    var hitFlash: Float = 0f

    val shotSteps: Int
        get() {
            if (type == GateType.WEAPON) return 0
            var steps = 0
            var cost = type.hitsPerStep
            var remaining = hits
            while (steps < minOf(MAX_SHOT_STEPS, type.maxShotSteps) && remaining >= cost) {
                remaining -= cost
                steps++
                cost += type.hitsPerStep
            }
            return steps
        }

    val value: Int get() = baseValue + shotSteps

    val valueText: String
        get() {
            if (type == GateType.WEAPON) return weapon?.title ?: "?"
            val sign = if (value > 0) "+" else ""
            return if (type.percent) "$sign$value%" else "$sign$value"
        }

    val title: String get() = if (type == GateType.WEAPON) "WAFFE" else type.title
}

/** Eine Torreihe über die ganze Straße, aufgeteilt in gleich breite Spuren (null = Lücke ohne Wirkung). */
class GateRow(val id: Long, var z: Float, val lanes: List<GateSide?>) {
    val laneCount: Int get() = lanes.size

    fun laneAt(x: Float): Int = ((x + 1f) / 2f * laneCount).toInt().coerceIn(0, laneCount - 1)

    fun laneLeft(lane: Int): Float = -1f + 2f * lane / laneCount
    fun laneRight(lane: Int): Float = -1f + 2f * (lane + 1) / laneCount
}

enum class CrateReward(val label: String) {
    MULTISHOT("+1 MULTISCHUSS"),
    DAMAGE_BOOST("SCHADEN BOOST"),
    FIRE_BOOST("FEUERRATE +15%"),
    HEART("+1 LEBEN"),
    PIERCE("+1 DURCHSCHLAG"),
    CRIT("+5% KRIT"),
    SPLASH("+1 EXPLOSION"),
    FROST("+1 FROST"),
    SHIELD("+1 SCHILD"),
    DRONE("+1 DROHNE"),
    SHOTGUN("SCHROTFLINTE"),
    LASER("LASER"),
    ROCKET("RAKETEN"),
}

class Crate(var x: Float, var z: Float, val maxHits: Int, val reward: CrateReward) {
    var hitsLeft: Int = maxHits
    var hitFlash: Float = 0f
}

enum class ObstacleKind { BLOCK, SPIKES }

/** Unzerstörbares Hindernis: blockiert Schüsse und kostet ein Herz, wenn man hineinläuft. */
class Obstacle(val id: Long, val x: Float, var z: Float, val halfWidth: Float, val kind: ObstacleKind)

enum class PowerUpKind(val title: String, val duration: Float) {
    FRENZY("RASEREI", 6f),
    SLOWMO("ZEITLUPE", 5f),
    BOMB("BOMBE", 0f),
}

/** Temporäres Power-up auf der Straße, wird durch Darüberlaufen eingesammelt. */
class Pickup(val id: Long, val x: Float, var z: Float, val kind: PowerUpKind)

class PlayerStats {
    var damage: Float = 1f
    var fireRate: Float = GateRushEngine.START_FIRE_RATE
    var multishot: Int = 1
    var pierce: Int = 0
    var hearts: Int = GateRushEngine.START_HEARTS
    /** Chance auf kritischen Treffer (×[GateRushEngine.CRIT_MULTIPLIER]). */
    var critChance: Float = 0f
    /** Stufe der Flächenexplosion bei Treffern. */
    var splash: Int = 0
    /** Stufe der Verlangsamung bei Treffern. */
    var frost: Int = 0
    /** Schilde fangen je einen Durchbruch oder Hindernis-Treffer ab (außer Bosse). */
    var shields: Int = 0
    /** Begleit-Drohnen, die automatisch mitschießen. */
    var drones: Int = 0
    var weapon: Weapon = Weapon.BLASTER
    /** Restdauer der temporären Power-ups. */
    var frenzy: Float = 0f
    var slowmo: Float = 0f
}

sealed interface GateRushEvent {
    data class EnemyKilled(val x: Float, val z: Float, val type: EnemyType) : GateRushEvent
    data class HeartsLost(val amount: Int) : GateRushEvent
    data object ShieldBlocked : GateRushEvent
    data object ObstacleHit : GateRushEvent
    data class GatePassed(val text: String, val positive: Boolean) : GateRushEvent
    data class CrateOpened(val x: Float, val z: Float, val text: String) : GateRushEvent
    data class CoinsEarned(val x: Float, val z: Float, val amount: Int) : GateRushEvent
    data class Explosion(val x: Float, val z: Float, val radius: Float) : GateRushEvent
    data class PowerUpCollected(val kind: PowerUpKind) : GateRushEvent
    data class WaveStarted(val wave: Int) : GateRushEvent
}
