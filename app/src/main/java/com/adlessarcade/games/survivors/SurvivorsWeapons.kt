package com.adlessarcade.games.survivors

import kotlin.random.Random

const val MAX_LEVEL = 5
const val MAX_WEAPONS = 6
/** Talente pro Lauf – bewusst knapp; jedes Entwicklungs-Talent deckt dafür mehrere Waffen ab. */
const val MAX_PASSIVES = 4

/** Werte einer Waffe auf einer Stufe (Index 0 = Stufe 1). */
private fun <T> List<T>.at(level: Int): T = this[(level - 1).coerceIn(0, size - 1)]

enum class WeaponKind(val title: String, val icon: String) {
    // Grundwaffen (immer verfügbar)
    SWORD("Schwertschwung", "⚔"),
    FIREBALL("Feuerball", "🔥"),
    ARROWS("Pfeile", "➶"),
    BONE_ORBIT("Knochenorbit", "☠"),
    LIGHTNING("Blitz", "⚡"),
    AURA("Heilige Aura", "✺"),
    DAGGERS("Wurfdolche", "🗡"),
    FROST_NOVA("Frostnova", "❄"),

    // In der Runen-Schmiede freischaltbar – je eine zweite Waffe pro Entwicklungs-Talent
    HAMMER("Kriegshammer", "🔨"),
    ARCANE("Arkane Geschosse", "🔮"),
    WIND("Windklingen", "🌪"),
    THORNS("Dornenpanzer", "🌹"),
    SPEAR("Runenspeer", "🔱"),
    VINES("Rankensaat", "🌿"),
    SHARDS("Kristallsplitter", "💠"),
    SHIELD("Schildwurf", "🛡"),
    GRAVITY("Gravitationsbrunnen", "🌀");

    /** Von Anfang an verfügbar (sonst in der Runen-Schmiede freizuschalten). */
    val isBase: Boolean get() = ordinal <= FROST_NOVA.ordinal

    /** ID in der Runen-Schmiede. */
    val unlockId: String get() = "weapon_${name.lowercase()}"

    fun cooldown(level: Int): Float = when (this) {
        SWORD -> listOf(1.0f, 0.92f, 0.85f, 0.77f, 0.7f).at(level)
        FIREBALL -> listOf(1.4f, 1.3f, 1.15f, 1.0f, 0.9f).at(level)
        ARROWS -> listOf(0.9f, 0.8f, 0.7f, 0.6f, 0.5f).at(level)
        BONE_ORBIT -> 0f
        LIGHTNING -> listOf(2.0f, 1.7f, 1.5f, 1.2f, 1.0f).at(level)
        AURA -> 0.5f
        DAGGERS -> listOf(0.6f, 0.55f, 0.5f, 0.42f, 0.35f).at(level)
        FROST_NOVA -> listOf(4f, 3.6f, 3.2f, 2.8f, 2.5f).at(level)
        HAMMER -> listOf(1.6f, 1.5f, 1.4f, 1.25f, 1.1f).at(level)
        ARCANE -> listOf(0.8f, 0.72f, 0.64f, 0.55f, 0.45f).at(level)
        WIND -> listOf(0.5f, 0.45f, 0.4f, 0.35f, 0.3f).at(level)
        THORNS -> listOf(2.0f, 1.8f, 1.6f, 1.4f, 1.2f).at(level)
        SPEAR -> listOf(1.5f, 1.4f, 1.25f, 1.1f, 1.0f).at(level)
        VINES -> listOf(3.0f, 2.8f, 2.5f, 2.2f, 2.0f).at(level)
        SHARDS -> listOf(0.9f, 0.82f, 0.75f, 0.67f, 0.6f).at(level)
        SHIELD -> listOf(1.6f, 1.5f, 1.35f, 1.2f, 1.1f).at(level)
        GRAVITY -> listOf(4.0f, 3.8f, 3.5f, 3.2f, 3.0f).at(level)
    }

    fun damage(level: Int): Float = when (this) {
        SWORD -> listOf(15f, 20f, 26f, 32f, 40f).at(level)
        FIREBALL -> listOf(15f, 20f, 25f, 30f, 35f).at(level)
        ARROWS -> listOf(8f, 10f, 12f, 15f, 18f).at(level)
        BONE_ORBIT -> listOf(10f, 13f, 16f, 20f, 24f).at(level)
        LIGHTNING -> listOf(20f, 26f, 32f, 38f, 45f).at(level)
        AURA -> listOf(4f, 5f, 6.5f, 8f, 10f).at(level)
        DAGGERS -> listOf(7f, 8.5f, 10f, 12f, 14f).at(level)
        FROST_NOVA -> listOf(10f, 14f, 18f, 21f, 25f).at(level)
        HAMMER -> listOf(30f, 38f, 47f, 57f, 70f).at(level)
        ARCANE -> listOf(9f, 11f, 13.5f, 16.5f, 20f).at(level)
        WIND -> listOf(8f, 10f, 12.5f, 15f, 18f).at(level)
        THORNS -> listOf(6f, 8f, 10f, 12f, 14f).at(level)
        SPEAR -> listOf(25f, 31f, 38f, 46f, 55f).at(level)
        VINES -> listOf(3f, 4f, 5f, 6.5f, 8f).at(level)
        SHARDS -> listOf(8f, 9.5f, 11f, 13.5f, 16f).at(level)
        SHIELD -> listOf(18f, 23f, 28f, 34f, 40f).at(level)
        GRAVITY -> listOf(4f, 5.5f, 7f, 8.5f, 10f).at(level)
    }

    /** Anzahl Projektile/Einschläge/Schädel (ohne Bonus durch „Vielfalt“). */
    fun count(level: Int): Int = when (this) {
        FIREBALL -> listOf(1, 1, 2, 2, 3).at(level)
        ARROWS -> listOf(1, 2, 2, 3, 4).at(level)
        BONE_ORBIT -> listOf(3, 3, 4, 5, 6).at(level)
        LIGHTNING -> listOf(1, 2, 2, 3, 4).at(level)
        DAGGERS -> listOf(1, 1, 2, 2, 3).at(level)
        ARCANE -> listOf(1, 2, 2, 3, 3).at(level)
        SPEAR -> listOf(1, 1, 1, 2, 2).at(level)
        VINES -> listOf(1, 1, 2, 2, 2).at(level)
        SHARDS -> listOf(1, 2, 2, 3, 3).at(level)
        SHIELD -> listOf(1, 1, 2, 2, 2).at(level)
        WIND -> 1
        else -> 1
    }

    /** Reichweite bzw. Radius (vor dem Bonus durch „Reichweite“). */
    fun area(level: Int): Float = when (this) {
        SWORD -> listOf(1.9f, 2.05f, 2.2f, 2.4f, 2.6f).at(level)
        FIREBALL -> listOf(0.8f, 0.9f, 1f, 1.15f, 1.3f).at(level)
        BONE_ORBIT -> listOf(1.8f, 2f, 2.2f, 2.4f, 2.6f).at(level)
        LIGHTNING -> 0.6f
        AURA -> listOf(1.3f, 1.5f, 1.7f, 1.95f, 2.2f).at(level)
        FROST_NOVA -> listOf(2f, 2.3f, 2.6f, 2.9f, 3.2f).at(level)
        HAMMER -> listOf(1.6f, 1.75f, 1.9f, 2.1f, 2.4f).at(level)
        WIND -> listOf(0.45f, 0.5f, 0.55f, 0.6f, 0.7f).at(level)
        THORNS -> listOf(1.4f, 1.55f, 1.7f, 1.85f, 2.0f).at(level)
        VINES -> listOf(1.0f, 1.15f, 1.3f, 1.45f, 1.6f).at(level)
        SHIELD -> listOf(1.5f, 1.6f, 1.7f, 1.8f, 2.0f).at(level)
        GRAVITY -> listOf(2.0f, 2.25f, 2.5f, 2.75f, 3.0f).at(level)
        else -> 0f
    }

    fun pierce(level: Int): Int = when (this) {
        ARROWS -> listOf(1, 1, 2, 2, 3).at(level)
        DAGGERS -> listOf(0, 0, 1, 1, 1).at(level)
        SHARDS -> listOf(2, 2, 3, 3, 4).at(level) // Abpraller
        else -> 0
    }

    /** Ab Stufe 4 schlägt das Schwert auch nach hinten. */
    fun doubleSwing(level: Int): Boolean = this == SWORD && level >= 4

    /** Waffen, bei denen „Vielfalt“ zusätzliche Projektile/Einschläge/Schädel bringt. */
    val usesExtraProjectiles: Boolean
        get() = this in setOf(FIREBALL, ARROWS, BONE_ORBIT, LIGHTNING, DAGGERS, ARCANE, SPEAR, VINES, SHARDS, SHIELD, WIND, GRAVITY)

    /** Was „Vielfalt“ bei dieser Waffe bewirkt. */
    val multiEffect: String
        get() = when (this) {
            SWORD, HAMMER -> "+1 Folgeschlag"
            AURA -> "+12 % Schaden"
            FROST_NOVA -> "+1 Echo-Welle"
            THORNS -> "+1 Echo-Puls"
            GRAVITY -> "+1 Brunnen"
            BONE_ORBIT -> "+1 Schädel"
            LIGHTNING -> "+1 Einschlag"
            VINES -> "+1 Ranke"
            WIND -> "+1 Klinge"
            else -> "+1 Projektil"
        }

    /** Talent, das zusammen mit Stufe 5 die Entwicklung freischaltet. */
    val evolutionPassive: PassiveKind
        get() = when (this) {
            SWORD, HAMMER -> PassiveKind.MIGHT
            FIREBALL, ARCANE -> PassiveKind.WISDOM
            ARROWS, WIND -> PassiveKind.HASTE
            BONE_ORBIT, THORNS -> PassiveKind.VITALITY
            LIGHTNING, SPEAR -> PassiveKind.REACH
            AURA, VINES -> PassiveKind.REGEN
            DAGGERS, SHARDS -> PassiveKind.MULTI
            FROST_NOVA, SHIELD -> PassiveKind.ARMOR
            GRAVITY -> PassiveKind.MAGNET
        }

    /** Grundwaffe mit demselben Entwicklungs-Talent (Voraussetzung zum Freischalten); null bei Grundwaffen und Magnet. */
    val partner: WeaponKind?
        get() = if (isBase) null else entries.firstOrNull { it.isBase && it.evolutionPassive == evolutionPassive }

    val evolutionTitle: String
        get() = when (this) {
            SWORD -> "Runenklinge"
            FIREBALL -> "Meteorregen"
            ARROWS -> "Sturmbogen"
            BONE_ORBIT -> "Seelenwirbel"
            LIGHTNING -> "Gewittersturm"
            AURA -> "Heiliges Licht"
            DAGGERS -> "Klingensturm"
            FROST_NOVA -> "Eiszeit"
            HAMMER -> "Erdbrecher"
            ARCANE -> "Arkansturm"
            WIND -> "Orkan"
            THORNS -> "Blutdornen"
            SPEAR -> "Götterspeer"
            VINES -> "Urwald"
            SHARDS -> "Splittersturm"
            SHIELD -> "Bollwerk"
            GRAVITY -> "Singularität"
        }

    val evolutionIcon: String
        get() = when (this) {
            SWORD -> "🗡️"
            FIREBALL -> "☄️"
            ARROWS -> "🏹"
            BONE_ORBIT -> "👻"
            LIGHTNING -> "🌩️"
            AURA -> "☀️"
            DAGGERS -> "🌀"
            FROST_NOVA -> "🧊"
            HAMMER -> "🌋"
            ARCANE -> "✨"
            WIND -> "🌪️"
            THORNS -> "🩸"
            SPEAR -> "⚜️"
            VINES -> "🌳"
            SHARDS -> "💎"
            SHIELD -> "🏰"
            GRAVITY -> "🕳️"
        }

    val evolutionDescription: String
        get() = when (this) {
            SWORD -> "Gewaltiger Runen-Rundumschlag: +60 % Schaden, +25 % Reichweite."
            FIREBALL -> "Meteore stürzen auf die größten Gruppen und hinterlassen brennenden Boden."
            ARROWS -> "+50 % Feuerrate, Pfeile durchschlagen 4 Gegner mehr."
            BONE_ORBIT -> "+2 Schädel, +15 % Schaden, Treffer heilen dich."
            LIGHTNING -> "Blitze springen auf 3 weitere Gegner über."
            AURA -> "Größere Aura, die dich mit jedem Treffer heilt."
            DAGGERS -> "Dolche fliegen in alle Richtungen und kehren zurück."
            FROST_NOVA -> "Friert Gegner ein – eingefrorene Gegner erleiden mehr Schaden."
            HAMMER -> "Zusätzlich eine Schockwelle rundherum, die Gegner kurz betäubt."
            ARCANE -> "Dauerfeuer – Kugeln springen nach jedem Treffer zum nächsten Gegner."
            WIND -> "Wirbelstürme statt Klingen: ziehen Gegner an und treffen sie immer wieder."
            THORNS -> "Größerer, stärkerer Dornen-Puls, der dich mit jedem Treffer heilt."
            SPEAR -> "Speere bleiben stecken und explodieren."
            VINES -> "Ranken fesseln Gegner und heilen dich, solange du darin stehst."
            SHARDS -> "Splitter teilen sich bei jedem Treffer."
            SHIELD -> "Zwei Schilde umkreisen dich dauerhaft und schleudern Gegner weg."
            GRAVITY -> "Schwarzes Loch: stärkerer Sog, im Zentrum dreifacher Schaden."
        }

    fun description(level: Int): String = when (this) {
        SWORD -> if (level <= 1) "Schlägt vor dir einen Bogen." else if (level == 4) "Schlägt jetzt auch nach hinten." else "Mehr Schaden und Reichweite."
        FIREBALL -> if (level <= 1) "Zielt auf den nächsten Gegner und explodiert." else "Mehr Feuerbälle und größere Explosion."
        ARROWS -> if (level <= 1) "Schnelle Pfeile, die durchschlagen." else "Mehr Pfeile, mehr Durchschlag."
        BONE_ORBIT -> if (level <= 1) "Schädel kreisen um dich." else "Mehr Schädel, größerer Kreis."
        LIGHTNING -> if (level <= 1) "Blitze schlagen in zufällige Gegner ein." else "Mehr Einschläge, mehr Schaden."
        AURA -> if (level <= 1) "Schadet Gegnern in deiner Nähe dauerhaft." else "Größere und stärkere Aura."
        DAGGERS -> if (level <= 1) "Wirft Dolche in Laufrichtung." else "Mehr Dolche, schnellere Würfe."
        FROST_NOVA -> if (level <= 1) "Eiswelle um dich, verlangsamt Gegner." else "Größere, häufigere Nova."
        HAMMER -> if (level <= 1) "Wuchtiger Hieb nach vorn, stößt Gegner zurück." else "Mehr Schaden, größere Schockwelle."
        ARCANE -> if (level <= 1) "Zielsuchende Magiekugeln." else "Mehr Kugeln, schnellere Folge."
        WIND -> if (level <= 1) "Beim Laufen bleiben schneidende Klingen zurück – je schneller, desto mehr." else "Größere, häufigere Klingen."
        THORNS -> if (level <= 1) "Dornen-Puls um dich, stärker mit mehr maximalem Leben. Angreifer erleiden Rückschaden." else "Stärkerer, häufigerer Puls."
        SPEAR -> if (level <= 1) "Weiter Wurf, durchbohrt alles in einer Linie." else "Mehr Schaden, mehr Speere."
        VINES -> if (level <= 1) "Pflanzt Ranken, die Gegner verlangsamen und schädigen." else "Mehr und größere Ranken."
        SHARDS -> if (level <= 1) "Splitter prallen von Gegner zu Gegner." else "Mehr Splitter, mehr Abpraller."
        SHIELD -> if (level <= 1) "Schild fliegt zum Gegner und kehrt zurück, stößt zurück." else "Mehr Schaden, mehr Schilde."
        GRAVITY -> if (level <= 1) "Zieht Gegner an einem Punkt zusammen und schädigt sie." else "Größerer, stärkerer Sog."
    }

    companion object {
        val BASE: Set<WeaponKind> = entries.filter { it.isBase }.toSet()
    }
}

enum class PassiveKind(val title: String, val icon: String, val description: String) {
    VITALITY("Vitalität", "❤", "+20 % maximales Leben"),
    MIGHT("Stärke", "💪", "+10 % Schaden"),
    HASTE("Eile", "👟", "+10 % Lauftempo"),
    WISDOM("Weisheit", "📖", "−8 % Abklingzeit, schnellerer Orbit und Aura-Takt"),
    REACH("Reichweite", "◎", "+10 % Fläche, Radius, Projektilgröße und Flugweite"),
    MAGNET("Magnet", "🧲", "+30 % Sammelradius, stärkerer Sog des Gravitationsbrunnens"),
    ARMOR("Rüstung", "🛡", "−1 Schaden pro Treffer"),
    REGEN("Regeneration", "✚", "+0,3 Leben pro Sekunde"),
    MULTI("Vielfalt", "✳", "+1 Projektil, Folgeschlag oder Echo"),
}

/** Abgeleitete Werte aus Klasse und Passiven. */
class HeroStats(private val state: SurvivorsState) {
    private fun lv(p: PassiveKind) = state.passives[p] ?: 0
    private val c = state.heroClass

    val maxHp: Float = c.maxHp * (1f + 0.2f * lv(PassiveKind.VITALITY))
    val damageMultiplier: Float = 1f + 0.1f * lv(PassiveKind.MIGHT)
    val speed: Float = c.speed * (1f + 0.1f * lv(PassiveKind.HASTE))
    val cooldownMultiplier: Float = (1f - c.cooldownBonus) * (1f - 0.08f * lv(PassiveKind.WISDOM))
    val areaMultiplier: Float = 1f + 0.1f * lv(PassiveKind.REACH)
    val pickupRadius: Float = 1.5f * (1f + 0.3f * lv(PassiveKind.MAGNET) + c.pickupBonus)
    val armor: Int = c.armor + lv(PassiveKind.ARMOR)
    val regen: Float = 0.3f * lv(PassiveKind.REGEN)
    val extraProjectiles: Int = lv(PassiveKind.MULTI)
}

object Upgrades {
    /** Alle möglichen Karten für den aktuellen Zustand. */
    fun pool(state: SurvivorsState): List<UpgradeOffer> = buildList {
        for ((weapon, level) in state.weapons) if (level < MAX_LEVEL) add(UpgradeOffer(weapon = weapon))
        if (state.weapons.size < MAX_WEAPONS) {
            WeaponKind.entries.filter { it !in state.weapons && it in state.availableWeapons }.forEach { add(UpgradeOffer(weapon = it)) }
        }
        for ((passive, level) in state.passives) if (level < MAX_LEVEL) add(UpgradeOffer(passive = passive))
        if (state.passives.size < MAX_PASSIVES) {
            PassiveKind.entries.filter { it !in state.passives }.forEach { add(UpgradeOffer(passive = it)) }
        }
    }

    /** Drei verschiedene Karten (oder Heilung, wenn nichts mehr zu verbessern ist). */
    fun offers(state: SurvivorsState, random: Random, count: Int = 3): List<UpgradeOffer> {
        val pool = pool(state).shuffled(random)
        if (pool.isEmpty()) return listOf(UpgradeOffer(heal = true))
        // Vorhandene Waffen leicht bevorzugen, damit Builds sich entwickeln.
        val owned = pool.filter { (it.weapon != null && it.weapon in state.weapons) || (it.passive != null && it.passive in state.passives) }
        val fresh = pool - owned.toSet()
        val result = ArrayList<UpgradeOffer>()
        val ownedFirst = (owned.take(2) + fresh).distinct()
        result += ownedFirst.take(count)
        if (result.size < count) result += pool.filter { it !in result }.take(count - result.size)
        return result.shuffled(random)
    }

    /** Waffen, die jetzt entwickelt werden können: Stufe 5 + passendes Talent, noch nicht entwickelt. */
    fun evolutionsReady(state: SurvivorsState): List<WeaponKind> =
        state.weapons.filter { (weapon, level) ->
            level >= MAX_LEVEL && weapon !in state.evolved && (state.passives[weapon.evolutionPassive] ?: 0) > 0
        }.keys.toList()

    fun apply(state: SurvivorsState, offer: UpgradeOffer) {
        when {
            offer.evolution != null -> state.evolved += offer.evolution
            offer.weapon != null -> {
                state.weapons[offer.weapon] = ((state.weapons[offer.weapon] ?: 0) + 1).coerceAtMost(MAX_LEVEL)
            }
            offer.passive != null -> {
                val before = HeroStats(state).maxHp
                state.passives[offer.passive] = ((state.passives[offer.passive] ?: 0) + 1).coerceAtMost(MAX_LEVEL)
                // Mehr maximales Leben füllt auch das aktuelle Leben auf.
                state.hp += (HeroStats(state).maxHp - before).coerceAtLeast(0f)
            }
            offer.heal -> state.hp = (state.hp + 30f).coerceAtMost(HeroStats(state).maxHp)
        }
    }

    fun title(offer: UpgradeOffer): String = when {
        offer.evolution != null -> offer.evolution.evolutionTitle
        offer.weapon != null -> offer.weapon.title
        offer.passive != null -> offer.passive.title
        else -> "Heiltrank"
    }

    fun icon(offer: UpgradeOffer): String = when {
        offer.evolution != null -> offer.evolution.evolutionIcon
        offer.weapon != null -> offer.weapon.icon
        offer.passive != null -> offer.passive.icon
        else -> "🧪"
    }

    /** „NEU“ oder „Stufe 2“ usw. – bezogen auf den Zustand vor der Wahl. */
    fun levelLabel(state: SurvivorsState, offer: UpgradeOffer): String {
        val current = when {
            offer.evolution != null -> return "ENTWICKLUNG"
            offer.weapon != null -> state.weapons[offer.weapon] ?: 0
            offer.passive != null -> state.passives[offer.passive] ?: 0
            else -> return "+30 Leben"
        }
        return if (current == 0) "NEU" else "Stufe ${current + 1}"
    }

    fun description(state: SurvivorsState, offer: UpgradeOffer): String = when {
        offer.evolution != null -> offer.evolution.evolutionDescription
        offer.weapon != null -> offer.weapon.description((state.weapons[offer.weapon] ?: 0) + 1)
        offer.passive != null -> offer.passive.description
        else -> "Stellt 30 Leben wieder her."
    }

    /** Hinweis auf Entwicklungen: welches Talent eine Waffe braucht bzw. welche Waffe ein Talent entwickelt. */
    fun synergy(state: SurvivorsState, offer: UpgradeOffer): String? = when {
        offer.evolution != null -> null
        offer.weapon != null -> if (offer.weapon in state.evolved) null else
            "Entwicklung (ab Min. 5 aus Kisten): Stufe 5 + ${offer.weapon.evolutionPassive.icon} ${offer.weapon.evolutionPassive.title} → ${offer.weapon.evolutionTitle}"
        offer.passive != null -> WeaponKind.entries
            .filter { it.evolutionPassive == offer.passive && it in state.weapons && it !in state.evolved }
            .takeIf { it.isNotEmpty() }
            ?.joinToString(prefix = "Schlüssel für: ") { it.evolutionTitle }
        else -> null
    }
}
