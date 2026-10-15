package com.adlessarcade.games.survivors

import kotlinx.serialization.Serializable

/*
 * Weltkoordinaten: unendliche Ebene, 1 Einheit ≈ ein Zehntel der Bildschirmbreite. y wächst nach unten.
 */

object Survivors {
    const val GAME_ID = "survivors"
}

enum class SurvivorsMode(val id: String) {
    /** 15 Minuten überleben, dann erscheint der Endboss – wer ihn besiegt, gewinnt. */
    TIMED("timed"),
    ENDLESS("endless");

    companion object {
        fun fromId(id: String?): SurvivorsMode = entries.firstOrNull { it.id == id } ?: TIMED
    }
}

enum class HeroClass(
    val shopId: String,
    val title: String,
    val description: String,
    val maxHp: Float,
    val speed: Float,
    val armor: Int,
    val startWeapon: WeaponKind,
    val cooldownBonus: Float = 0f,
    val pickupBonus: Float = 0f,
    val healPerKill: Float = 0f,
) {
    KNIGHT(
        "class_knight", "Krieger", "Viel Leben und Rüstung. Startet mit dem Schwertschwung.",
        maxHp = 150f, speed = 3f, armor = 1, startWeapon = WeaponKind.SWORD,
    ),
    MAGE(
        "class_mage", "Magierin", "Zerbrechlich, aber schnellere Zauber. Startet mit Feuerbällen.",
        maxHp = 80f, speed = 3f, armor = 0, startWeapon = WeaponKind.FIREBALL, cooldownBonus = 0.1f,
    ),
    RANGER(
        "class_ranger", "Waldläuferin", "Flink und präzise. Startet mit Pfeilen.",
        maxHp = 100f, speed = 3.5f, armor = 0, startWeapon = WeaponKind.ARROWS,
    ),
    NECROMANCER(
        "class_necro", "Nekromant", "Zieht Seelen an und heilt sich bei Kills. Startet mit dem Knochenorbit.",
        maxHp = 110f, speed = 3f, armor = 0, startWeapon = WeaponKind.BONE_ORBIT, pickupBonus = 0.3f, healPerKill = 0.8f,
    );

    companion object {
        fun fromShopId(id: String?): HeroClass? = entries.firstOrNull { it.shopId == id }
    }
}

enum class EnemyKind(
    val title: String,
    val hp: Float,
    val speed: Float,
    val radius: Float,
    /** Schaden pro Treffer am Helden (vor Rüstung). */
    val damage: Float,
    val xp: Int,
    val score: Int,
) {
    SLIME("Schleim", hp = 8f, speed = 1.5f, radius = 0.35f, damage = 6f, xp = 1, score = 1),
    BAT("Fledermaus", hp = 5f, speed = 2.7f, radius = 0.28f, damage = 5f, xp = 1, score = 1),
    SKELETON("Skelett", hp = 16f, speed = 1.9f, radius = 0.36f, damage = 9f, xp = 2, score = 2),
    GHOST("Geist", hp = 14f, speed = 2.2f, radius = 0.36f, damage = 8f, xp = 2, score = 2),
    ORC("Ork", hp = 45f, speed = 1.4f, radius = 0.5f, damage = 15f, xp = 5, score = 5),
    BOSS("Höhlentroll", hp = 1200f, speed = 1.35f, radius = 1f, damage = 25f, xp = 40, score = 100),
    DEMON("Dämonenfürst", hp = 6000f, speed = 1.5f, radius = 1.3f, damage = 35f, xp = 0, score = 1000),
}

@Serializable
class Enemy(
    val id: Long,
    val kind: EnemyKind,
    var x: Float,
    var y: Float,
    val maxHp: Float,
    /** Elite: größer, stärker, lässt eine Truhe fallen. */
    val elite: Boolean = false,
    /** Phase der Schlingerbewegung (Fledermäuse). */
    val phase: Float = 0f,
) {
    var hp: Float = maxHp
    var slowTimer: Float = 0f
    /** Eiszeit: eingefroren (bewegt sich nicht, richtet keinen Kontaktschaden an). */
    var freezeTimer: Float = 0f
    var hitFlash: Float = 0f
    /** Abklingzeit, bis der Knochenorbit diesen Gegner erneut treffen darf. */
    var orbitCooldown: Float = 0f

    val radius: Float get() = kind.radius * if (elite) 1.4f else 1f
    val isBoss: Boolean get() = kind == EnemyKind.BOSS || kind == EnemyKind.DEMON
}

@Serializable
class Projectile(
    val id: Long,
    val weapon: WeaponKind,
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val damage: Float,
    val radius: Float,
    var pierce: Int,
    var life: Float,
    /** > 0: explodiert beim ersten Treffer in diesem Radius (Feuerball). */
    val explosionRadius: Float = 0f,
    /** > 0: kehrt bei dieser Restlebensdauer um und fliegt zurück (Klingensturm). */
    val returnAt: Float = 0f,
) {
    val hitIds: MutableSet<Long> = HashSet()
    var alive: Boolean = true
    var returned: Boolean = false
}

/** Brennender Boden (Meteorregen): schadet Gegnern in regelmäßigen Abständen. */
@Serializable
class Hazard(val x: Float, val y: Float, val radius: Float, val damage: Float, var life: Float) {
    var tick: Float = 0f
}

/** Verzögerter Waffeneinsatz: Folgeschläge (Vielfalt), Frost-Echos und Meteor-Einschläge. */
@Serializable
class PendingStrike(
    val weapon: WeaponKind,
    var delay: Float,
    val damageFactor: Float = 1f,
    /** Schwert: der wievielte nächste Gegner angepeilt wird. */
    val targetIndex: Int = 0,
    val x: Float = 0f,
    val y: Float = 0f,
)

@Serializable
class Gem(var x: Float, var y: Float, var value: Int) {
    /** Wird gerade vom Helden angezogen. */
    var magnetized: Boolean = false
}

enum class PickupKind { POTION, CHEST, COIN, MAGNET }

@Serializable
class Pickup(val id: Long, val kind: PickupKind, var x: Float, var y: Float) {
    /** Wird gerade vom Helden angezogen (Coins im Sammelradius, Magnet, Sieg). */
    var magnetized: Boolean = false
}

/** Eine Karte bei der Level-up-Auswahl oder eine Kisten-Belohnung. Genau eines der Felder ist gesetzt. */
@Serializable
data class UpgradeOffer(
    val weapon: WeaponKind? = null,
    val passive: PassiveKind? = null,
    /** Ersatz, wenn alles maximiert ist: Heilung. */
    val heal: Boolean = false,
    /** Waffenentwicklung (nur aus Kisten). */
    val evolution: WeaponKind? = null,
)

/** Alle Timer des Spawners – Teil des Spielstands. */
@Serializable
data class SpawnerState(
    var spawnBudget: Float = 0f,
    var eliteTimer: Float = 60f,
    var swarmTimer: Float = 90f,
    /** Zeitpunkt des nächsten Bosses. */
    var nextBossTime: Float = 300f,
    var finalBossSpawned: Boolean = false,
)

/** Gesamter Zustand eines Laufs – wird 1:1 als Spielstand gespeichert. */
@Serializable
class SurvivorsState(
    val heroClass: HeroClass,
    val mode: SurvivorsMode,
) {
    var time: Float = 0f
    var px: Float = 0f
    var py: Float = 0f
    /** Blickrichtung (normiert), für Schwert und Dolche. */
    var faceX: Float = 1f
    var faceY: Float = 0f
    var hp: Float = heroClass.maxHp
    var level: Int = 1
    var xp: Int = 0
    var kills: Int = 0
    /** Punkte aus Kills (je nach Gegnertyp). */
    var killScore: Int = 0
    var coinsEarned: Int = 0
    /** Unverwundbarkeit nach einem Treffer. */
    var invulnerable: Float = 0f
    var victory: Boolean = false
    /** Siegesrunde nach dem Endboss: alle Drops fliegen zum Helden, danach ist der Lauf gewonnen. */
    var victorySweep: Boolean = false
    var victorySweepTime: Float = 0f
    var dead: Boolean = false
    val weapons: MutableMap<WeaponKind, Int> = LinkedHashMap()
    val passives: MutableMap<PassiveKind, Int> = LinkedHashMap()
    val cooldowns: MutableMap<WeaponKind, Float> = LinkedHashMap()
    val enemies: MutableList<Enemy> = ArrayList()
    val projectiles: MutableList<Projectile> = ArrayList()
    val gems: MutableList<Gem> = ArrayList()
    val pickups: MutableList<Pickup> = ArrayList()
    var pendingLevelUps: Int = 0
    var offers: List<UpgradeOffer> = emptyList()
    /** Entwickelte Waffen (belegen weiter ihren Platz, Stufe bleibt 5). */
    val evolved: MutableSet<WeaponKind> = LinkedHashSet()
    /** Aktuell ausgerollte Kisten-Belohnung – das Spiel steht, bis sie angenommen oder abgelehnt wird. */
    var chestReward: UpgradeOffer? = null
    /** Index der aktuellen Belohnung und Gesamtzahl der offenen Kiste(n). */
    var chestIndex: Int = 0
    var chestTotal: Int = 0
    /** Höchstens eine Entwicklung pro Kiste. */
    var chestEvolutionGiven: Boolean = false
    val pendingStrikes: MutableList<PendingStrike> = ArrayList()
    val hazards: MutableList<Hazard> = ArrayList()
    /** Drehwinkel des Knochenorbits. */
    var orbitAngle: Float = 0f
    /** Lebensraub-Vorrat: begrenzt die Heilung durch Entwicklungen pro Sekunde. */
    var leechPool: Float = 0f
    var nextId: Long = 0
    var auraTimer: Float = 0f
    /** Zählt bis zur nächsten vollen Minute (Zeitbonus-Coins). */
    var minuteTimer: Float = 0f
    val spawner: SpawnerState = SpawnerState()
}

sealed interface SurvivorsEvent {
    data class EnemyKilled(val x: Float, val y: Float, val kind: EnemyKind, val elite: Boolean) : SurvivorsEvent
    data class Damage(val x: Float, val y: Float, val amount: Float) : SurvivorsEvent
    data class PlayerHit(val amount: Float) : SurvivorsEvent
    data object LevelUp : SurvivorsEvent
    /** Eine Kisten-Belohnung wurde ausgerollt ([index] von [total], ab 0). */
    data class ChestReward(val offer: UpgradeOffer, val index: Int, val total: Int) : SurvivorsEvent
    data class Evolved(val weapon: WeaponKind) : SurvivorsEvent
    data class BossSpawned(val kind: EnemyKind) : SurvivorsEvent
    data object Victory : SurvivorsEvent
    data class CoinsEarned(val x: Float, val y: Float, val amount: Int) : SurvivorsEvent
    data class Healed(val amount: Float) : SurvivorsEvent

    // Rein visuelle Waffeneffekte
    data class Swing(val x: Float, val y: Float, val angle: Float, val range: Float, val fullCircle: Boolean, val evolved: Boolean = false) : SurvivorsEvent
    data class Lightning(val x: Float, val y: Float, val radius: Float) : SurvivorsEvent
    data class Nova(val x: Float, val y: Float, val radius: Float, val freeze: Boolean = false) : SurvivorsEvent
    /** Gewittersturm: Blitz springt von (x1, y1) nach (x2, y2). */
    data class Chain(val x1: Float, val y1: Float, val x2: Float, val y2: Float) : SurvivorsEvent
    data class Explosion(val x: Float, val y: Float, val radius: Float) : SurvivorsEvent
}
