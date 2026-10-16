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
    /** Einzigartige Klassen-Passive. */
    val passiveTitle: String,
    val passiveIcon: String,
    val passiveDescription: String,
) {
    KNIGHT(
        "class_knight", "Krieger", "Viel Leben und Rüstung. Startet mit dem Schwertschwung.",
        maxHp = 150f, speed = 3f, armor = 1, startWeapon = WeaponKind.SWORD,
        passiveTitle = "Eiserner Wille", passiveIcon = "🛡",
        passiveDescription = "+6 % Schaden je Rüstungspunkt. Unter 50 % Leben +25 % Schaden.",
    ),
    MAGE(
        "class_mage", "Magierin", "Zerbrechlich, aber schnellere Zauber. Startet mit Feuerbällen.",
        maxHp = 80f, speed = 3f, armor = 0, startWeapon = WeaponKind.FIREBALL, cooldownBonus = 0.1f,
        passiveTitle = "Elementarkunde", passiveIcon = "🔮",
        passiveDescription = "Gift wirkt 50 % länger, Funken- und Frost-Chancen ×1,5. " +
            "Vergiftete, gefrorene oder gefesselte Gegner nehmen +20 % Schaden.",
    ),
    RANGER(
        "class_ranger", "Waldläuferin", "Flink und präzise. Startet mit Pfeilen.",
        maxHp = 100f, speed = 3.5f, armor = 0, startWeapon = WeaponKind.ARROWS,
        passiveTitle = "Jagdinstinkt", passiveIcon = "🏹",
        passiveDescription = "+12 % Krit-Chance. In Bewegung +25 % Krit-Schaden.",
    ),
    NECROMANCER(
        "class_necro", "Nekromant", "Zieht Seelen an und heilt sich bei Kills. Startet mit dem Knochenorbit.",
        maxHp = 110f, speed = 3f, armor = 0, startWeapon = WeaponKind.BONE_ORBIT, pickupBonus = 0.3f, healPerKill = 0.8f,
        passiveTitle = "Seelenernte", passiveIcon = "💀",
        passiveDescription = "Jeder Kill bringt eine Seele (max. 150, je +0,4 % Schaden). " +
            "Vergiftete Gegner verbreiten ihr Gift beim Tod.",
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

    // --- Vergessener Friedhof (Stage 2) ---
    ZOMBIE("Zombie", hp = 10f, speed = 1.3f, radius = 0.38f, damage = 8f, xp = 1, score = 1),
    /** Bleibt auf Abstand und schießt Knochenpfeile (mit Ziellinie). */
    ARCHER("Knochenschütze", hp = 18f, speed = 1.7f, radius = 0.36f, damage = 7f, xp = 3, score = 3),
    /** Legt Fluchkreise unter den Helden. */
    WITCH("Grabhexe", hp = 30f, speed = 1.5f, radius = 0.38f, damage = 8f, xp = 4, score = 4),
    GRAVE_GUARDIAN("Grabwächter", hp = 1200f, speed = 1.3f, radius = 1.05f, damage = 28f, xp = 40, score = 120),
    LICH("Lichkönig", hp = 6000f, speed = 1.4f, radius = 1.2f, damage = 38f, xp = 0, score = 1500);

    val isFinalBoss: Boolean get() = this == DEMON || this == LICH
    val isMidBoss: Boolean get() = this == BOSS || this == GRAVE_GUARDIAN
    /** Fernkämpfer: nähern sich nur bis auf Schussweite. */
    val keepsDistance: Boolean get() = this == ARCHER || this == WITCH
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
    /** Betäubt/gefesselt (Erdbrecher, Urwald): bewegt sich nicht, richtet keinen Kontaktschaden an. */
    var stunTimer: Float = 0f
    var hitFlash: Float = 0f
    /** Waffe des letzten Treffers (für Kills pro Waffe). */
    var lastHit: WeaponKind? = null
    /** Abklingzeit, bis der Knochenorbit diesen Gegner erneut treffen darf. */
    var orbitCooldown: Float = 0f
    /** Abklingzeit für die kreisenden Schilde (Bollwerk). */
    var shieldCooldown: Float = 0f
    /** Gift (Giftphiole): Schaden pro Sekunde, solange [poisonTimer] läuft. */
    var poisonDps: Float = 0f
    var poisonTimer: Float = 0f
    /** Waffe, die das Gift verursacht hat (für Kills pro Waffe). */
    var poisonSource: WeaponKind? = null
    /** Gesammelter Giftschaden für die nächste Schadenszahl und Zeit bis dahin. */
    var poisonShown: Float = 0f
    var poisonNumberTimer: Float = 0f
    /** Abklingzeit bis zum nächsten eigenen Angriff (Schützen, Hexen, Friedhof-Bosse). */
    var attackTimer: Float = 2f
    /** Holt gerade aus bzw. stürmt (steht still bzw. bewegt sich nicht selbst). */
    var windup: Float = 0f
    /** Muster-Index der Boss-Attacken. */
    var attackStep: Int = 0
    /** Sturmangriff des Grabwächters: Geschwindigkeit, solange [dashTime] läuft. */
    var dashVx: Float = 0f
    var dashVy: Float = 0f
    var dashTime: Float = 0f

    val radius: Float get() = kind.radius * if (elite) 1.4f else 1f
    val isBoss: Boolean get() = kind.isFinalBoss || kind.isMidBoss
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
    /** Lenkt auf den nächsten Gegner (Arkane Geschosse). */
    val homing: Boolean = false,
    /** Springt nach einem Treffer zum nächsten Gegner (Kristallsplitter, Arkansturm). */
    val bounce: Boolean = false,
    /** Kann sich bei einem Treffer teilen (Splittersturm); Abspaltungen nicht mehr. */
    val splits: Boolean = false,
    /** Stößt getroffene Gegner zurück (Schildwurf). */
    val knockback: Float = 0f,
    /** Trifft denselben Gegner in diesem Takt erneut (Orkan); 0 = nur einmal. */
    val rehit: Float = 0f,
    /** Zieht Gegner in diesem Radius an (Orkan); 0 = nicht. */
    val pullRadius: Float = 0f,
    /** Explodiert am Ende der Flugzeit in diesem Radius (Götterspeer); 0 = nicht. */
    val endBlast: Float = 0f,
) {
    val hitIds: MutableSet<Long> = HashSet()
    var alive: Boolean = true
    var returned: Boolean = false
    var tick: Float = 0f
}

enum class HazardKind { FIRE, VINES, GRAVITY }

/**
 * Wirkfläche am Boden: brennender Boden (Meteorregen), Ranken (Rankensaat) oder Gravitationsbrunnen.
 * Schadet Gegnern in regelmäßigen Abständen.
 */
@Serializable
class Hazard(
    val x: Float,
    val y: Float,
    val radius: Float,
    val damage: Float,
    var life: Float,
    val kind: HazardKind = HazardKind.FIRE,
    /** Entwickelte Variante (Urwald, Singularität). */
    val evolved: Boolean = false,
) {
    var tick: Float = 0f
    /** Gesamtdauer – für die Darstellung (Ein-/Ausblenden). */
    val maxLife: Float = life
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

enum class AttackShape { CIRCLE, LINE }

/**
 * Angekündigter Gegnerangriff mit Warn-Indikator: schlägt nach [delay] Sekunden ein und trifft den Helden nur,
 * wenn er dann noch im Kreis (Mittelpunkt x/y, [radius]) bzw. Streifen (x/y → x2/y2, Breite 2·[radius]) steht.
 */
@Serializable
class EnemyAttack(
    val shape: AttackShape,
    val x: Float,
    val y: Float,
    val radius: Float,
    var delay: Float,
    val damage: Float,
    val x2: Float = x,
    val y2: Float = y,
) {
    val maxDelay: Float = delay
}

/** Gegnerisches Geschoss (Knochenpfeil, Seelenkugel) – wird von Hindernissen aufgehalten. */
@Serializable
class EnemyShot(
    var x: Float,
    var y: Float,
    val vx: Float,
    val vy: Float,
    val radius: Float,
    val damage: Float,
    var life: Float,
    /** Seelenkugel (Lichkönig) statt Pfeil – nur für die Darstellung. */
    val soul: Boolean = false,
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
    /** Stapelbares Item (nur aus Kisten). */
    val item: ItemKind? = null,
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
    /** Endlos-Modus: Raserei ab Minute 20 bereits angekündigt. */
    var enraged: Boolean = false,
)

/** Zusätzliche Gegner-Skalierung (Raserei im Endlos-Modus). */
/** Art einer Schadenszahl: normaler Treffer, Gift oder Hinrichtung (Henkersmal). */
enum class DamageKind { HIT, POISON, EXECUTE }

data class EnemyScaling(val hp: Float, val damage: Float, val speed: Float)

/** Gesamter Zustand eines Laufs – wird 1:1 als Spielstand gespeichert. */
@Serializable
class SurvivorsState(
    val heroClass: HeroClass,
    val mode: SurvivorsMode,
    val stage: SurvivorsStage = SurvivorsStage.MEADOW,
) {
    /** Angekündigte Gegnerangriffe (Indikatoren) und gegnerische Geschosse. */
    val enemyAttacks: MutableList<EnemyAttack> = ArrayList()
    val enemyShots: MutableList<EnemyShot> = ArrayList()
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
    /** Runen dieses Laufs – Währung der Runen-Schmiede. */
    var runesEarned: Int = 0
    /** Besiegte Höhlentrolle (für spätere Freischalt-Kriterien). */
    var bossesKilled: Int = 0
    /** Unverwundbarkeit nach einem Treffer. */
    var invulnerable: Float = 0f
    var victory: Boolean = false
    /** Siegesrunde nach dem Endboss: alle Drops fliegen zum Helden, danach ist der Lauf gewonnen. */
    var victorySweep: Boolean = false
    var victorySweepTime: Float = 0f
    var dead: Boolean = false
    val weapons: MutableMap<WeaponKind, Int> = LinkedHashMap()
    /** Waffen, die in diesem Lauf angeboten werden dürfen (Grundwaffen + in der Runen-Schmiede freigeschaltete). */
    val availableWeapons: MutableSet<WeaponKind> = LinkedHashSet(WeaponKind.BASE)
    /** Kills pro Waffe in diesem Lauf (letzter Treffer zählt) – für Freischalt-Kriterien. */
    val weaponKills: MutableMap<WeaponKind, Int> = LinkedHashMap()
    /** Verursachter Schaden pro Waffe in diesem Lauf (ohne Overkill) – für die Statistik im Pause-Menü. */
    val weaponDamage: MutableMap<WeaponKind, Float> = LinkedHashMap()
    /** Spielzeit, zu der die Waffe dazukam (für Schaden pro Sekunde). */
    val weaponSince: MutableMap<WeaponKind, Float> = LinkedHashMap()
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
    /** Items aus Kisten (Stapelanzahl). */
    val items: MutableMap<ItemKind, Int> = LinkedHashMap()
    /** Drei Belohnungen der geöffneten Kiste – das Spiel steht, bis eine gewählt wird. */
    var chestOffers: List<UpgradeOffer> = emptyList()
    /** Weitere Kisten, die im selben Moment eingesammelt wurden und danach geöffnet werden. */
    var pendingChests: Int = 0
    /** Seelen des Nekromanten (Seelenernte). */
    var souls: Int = 0
    /** Bruchteile von Coins und Erfahrung (Goldene Gans). */
    var coinRemainder: Float = 0f
    var xpRemainder: Float = 0f
    var runeRemainder: Float = 0f
    val pendingStrikes: MutableList<PendingStrike> = ArrayList()
    val hazards: MutableList<Hazard> = ArrayList()
    /** Drehwinkel des Knochenorbits. */
    var orbitAngle: Float = 0f
    /** Drehwinkel der Bollwerk-Schilde. */
    var shieldAngle: Float = 0f
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
    /** [crit]: 0 normal, 1 Krit, 2 Superkrit. */
    data class Damage(val x: Float, val y: Float, val amount: Float, val crit: Int = 0, val kind: DamageKind = DamageKind.HIT) : SurvivorsEvent
    data class PlayerHit(val amount: Float) : SurvivorsEvent
    data object LevelUp : SurvivorsEvent
    /** Eine Kiste wurde geöffnet; [remaining] weitere warten noch. */
    data class ChestOpened(val offers: List<UpgradeOffer>, val remaining: Int) : SurvivorsEvent
    data class Evolved(val weapon: WeaponKind) : SurvivorsEvent
    data class Dodged(val x: Float, val y: Float) : SurvivorsEvent
    data class BossSpawned(val kind: EnemyKind) : SurvivorsEvent
    data object Victory : SurvivorsEvent
    /** Endlos-Modus: ab Minute 20 werden die Gegner rasend. */
    data object Enraged : SurvivorsEvent
    data class CoinsEarned(val x: Float, val y: Float, val amount: Int) : SurvivorsEvent
    data class RunesEarned(val x: Float, val y: Float, val amount: Int) : SurvivorsEvent
    data class Healed(val amount: Float) : SurvivorsEvent

    // Rein visuelle Waffeneffekte
    data class Swing(val x: Float, val y: Float, val angle: Float, val range: Float, val fullCircle: Boolean, val evolved: Boolean = false) : SurvivorsEvent
    data class Lightning(val x: Float, val y: Float, val radius: Float) : SurvivorsEvent
    data class Nova(val x: Float, val y: Float, val radius: Float, val freeze: Boolean = false) : SurvivorsEvent
    /** Gewittersturm: Blitz springt von (x1, y1) nach (x2, y2). */
    data class Chain(val x1: Float, val y1: Float, val x2: Float, val y2: Float) : SurvivorsEvent
    /** Kriegshammer-Hieb (Kegel) bzw. Erdbrecher-Schockwelle ([ring]). */
    data class Smash(val x: Float, val y: Float, val angle: Float, val range: Float, val ring: Boolean) : SurvivorsEvent
    data class Thorns(val x: Float, val y: Float, val radius: Float, val evolved: Boolean) : SurvivorsEvent
    data class Explosion(val x: Float, val y: Float, val radius: Float) : SurvivorsEvent
    /** Ein angekündigter Gegnerangriff schlägt ein (Effekt). */
    data class AttackImpact(val attack: EnemyAttack) : SurvivorsEvent
}
