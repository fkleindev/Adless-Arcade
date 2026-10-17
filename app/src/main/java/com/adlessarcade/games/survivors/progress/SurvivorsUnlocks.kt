package com.adlessarcade.games.survivors.progress

import com.adlessarcade.games.survivors.HeroClass
import com.adlessarcade.games.survivors.SurvivorsStage
import com.adlessarcade.games.survivors.WeaponKind

/** Dauerhafter Fortschritt in Rune Survivors (unabhängig von Coins und dem allgemeinen Shop). */
data class SurvivorsProgress(
    val runes: Int = 0,
    val unlocked: Set<String> = emptySet(),
    /** Zuletzt gewählte Klasse ([HeroClass.shopId]). */
    val selectedClass: String? = null,
    /** Zuletzt gewählte Stage ([SurvivorsStage.id]). */
    val selectedStage: String? = null,
    /** Zähler für Freischalt-Kriterien, z. B. [SurvivorsStats.DEMON_DEFEATED]. */
    val stats: Map<String, Int> = emptyMap(),
) {
    fun stat(key: String): Int = stats[key] ?: 0
}

/** Schlüssel der Fortschritts-Zähler. */
object SurvivorsStats {
    /** Anzahl Siege über den Dämonenfürsten. */
    const val DEMON_DEFEATED = "demon_defeated"
    /** Anzahl Siege über den Lichkönig (Stage 2). */
    const val LICH_DEFEATED = "lich_defeated"
    /** Insgesamt besiegte Höhlentrolle. */
    const val BOSSES_KILLED = "bosses_killed"
    /** Längste Überlebenszeit in Sekunden (beide Modi). */
    const val BEST_TIME = "best_time"
    /** Kills mit einer Waffe über alle Läufe, z. B. „kills_SWORD“. */
    fun weaponKills(weapon: WeaponKind): String = "kills_${weapon.name}"

    /** Gespielte Läufe. */
    const val RUNS = "runs"
}

enum class UnlockKind(val title: String) {
    CLASS("Klassen"),
    WEAPON("Waffen"),
    STAGE("Stages"),
}

/** Zusätzliche Bedingung neben dem Runenpreis. */
sealed interface Requirement {
    /** Anzeige, z. B. „Dämonenfürst einmal besiegt“. */
    val text: String
    fun isMet(progress: SurvivorsProgress): Boolean
}

/** Ein Zähler muss mindestens [value] erreichen. */
data class StatAtLeast(val stat: String, val value: Int, override val text: String) : Requirement {
    override fun isMet(progress: SurvivorsProgress): Boolean = progress.stat(stat) >= value
}

/** Etwas, das in der Runen-Schmiede freigeschaltet wird. */
data class SurvivorsUnlock(
    val id: String,
    val kind: UnlockKind,
    val name: String,
    val runePrice: Int,
    val requirements: List<Requirement> = emptyList(),
) {
    /** Von Anfang an verfügbar. */
    val isFree: Boolean get() = runePrice == 0 && requirements.isEmpty()
}

object SurvivorsUnlocks {
    /** Klassen-IDs entsprechen [HeroClass.shopId]. Waffen und Stages folgen später. */
    val all: List<SurvivorsUnlock> = listOf(
        SurvivorsUnlock(HeroClass.KNIGHT.shopId, UnlockKind.CLASS, HeroClass.KNIGHT.title, runePrice = 0),
        SurvivorsUnlock(HeroClass.MAGE.shopId, UnlockKind.CLASS, HeroClass.MAGE.title, runePrice = 80),
        SurvivorsUnlock(HeroClass.RANGER.shopId, UnlockKind.CLASS, HeroClass.RANGER.title, runePrice = 120),
        SurvivorsUnlock(HeroClass.CRYOMANCER.shopId, UnlockKind.CLASS, HeroClass.CRYOMANCER.title, runePrice = 120),
        SurvivorsUnlock(HeroClass.NECROMANCER.shopId, UnlockKind.CLASS, HeroClass.NECROMANCER.title, runePrice = 150),
    ) + weaponUnlocks() + listOf(
        SurvivorsUnlock(
            SurvivorsStage.GRAVEYARD.unlockId!!, UnlockKind.STAGE, SurvivorsStage.GRAVEYARD.title, runePrice = 100,
            requirements = listOf(StatAtLeast(SurvivorsStats.DEMON_DEFEATED, 1, "Dämonenfürst (Stage 1) besiegt")),
        ),
    )

    /** Runenpreis der freischaltbaren Waffen (Getter, damit die Reihenfolge der Initialisierung egal ist). */
    private val WEAPON_PRICES: Map<WeaponKind, Int> get() = mapOf(
        WeaponKind.HAMMER to 60,
        WeaponKind.ARCANE to 70,
        WeaponKind.SHARDS to 80,
        WeaponKind.SPEAR to 90,
        WeaponKind.THORNS to 100,
        WeaponKind.VINES to 100,
        WeaponKind.WIND to 110,
        WeaponKind.SHIELD to 120,
        WeaponKind.GRAVITY to 150,
    )

    /** Kills mit der Grundwaffe desselben Talents, die eine neue Waffe voraussetzt (über alle Läufe). */
    const val PARTNER_KILLS = 1_000

    /**
     * Neue Waffen: Runen + [PARTNER_KILLS] Kills mit der Grundwaffe desselben Entwicklungs-Talents.
     * Der Gravitationsbrunnen (Magnet) hat keine Partnerwaffe und kostet nur Runen.
     */
    private fun weaponUnlocks(): List<SurvivorsUnlock> = WeaponKind.entries.filter { !it.isBase }.map { weapon ->
        val partner = weapon.partner
        SurvivorsUnlock(
            id = weapon.unlockId,
            kind = UnlockKind.WEAPON,
            name = weapon.title,
            runePrice = WEAPON_PRICES.getValue(weapon),
            requirements = if (partner == null) emptyList() else listOf(
                StatAtLeast(
                    SurvivorsStats.weaponKills(partner),
                    PARTNER_KILLS,
                    "%,d Kills mit %s".format(java.util.Locale.GERMANY, PARTNER_KILLS, partner.title),
                ),
            ),
        )
    }

    private val byId = all.associateBy { it.id }

    fun find(id: String?): SurvivorsUnlock? = id?.let(byId::get)

    fun ofKind(kind: UnlockKind): List<SurvivorsUnlock> = all.filter { it.kind == kind }

    fun forClass(heroClass: HeroClass): SurvivorsUnlock = byId.getValue(heroClass.shopId)

    /** Freischaltung einer Stage (null = von Beginn an spielbar). */
    fun forStage(stage: SurvivorsStage): SurvivorsUnlock? = find(stage.unlockId)
}

object UnlockLogic {
    fun isUnlocked(progress: SurvivorsProgress, unlock: SurvivorsUnlock): Boolean =
        unlock.isFree || unlock.id in progress.unlocked

    fun isUnlocked(progress: SurvivorsProgress, heroClass: HeroClass): Boolean =
        isUnlocked(progress, SurvivorsUnlocks.forClass(heroClass))

    fun requirementsMet(progress: SurvivorsProgress, unlock: SurvivorsUnlock): Boolean =
        unlock.requirements.all { it.isMet(progress) }

    fun canBuy(progress: SurvivorsProgress, unlock: SurvivorsUnlock): Boolean =
        !isUnlocked(progress, unlock) && progress.runes >= unlock.runePrice && requirementsMet(progress, unlock)

    /** Neuer Fortschritt nach dem Kauf, oder null, wenn nicht möglich. */
    fun buy(progress: SurvivorsProgress, unlock: SurvivorsUnlock): SurvivorsProgress? {
        if (!canBuy(progress, unlock)) return null
        return progress.copy(runes = progress.runes - unlock.runePrice, unlocked = progress.unlocked + unlock.id)
    }

    /** Waffen, die im Lauf angeboten werden: Grundwaffen + freigeschaltete. */
    fun availableWeapons(progress: SurvivorsProgress): Set<WeaponKind> =
        WeaponKind.entries.filter { it.isBase || it.unlockId in progress.unlocked }.toSet()

    /** Gewählte Klasse; fällt auf den Krieger zurück, wenn sie (noch) nicht freigeschaltet ist. */
    fun selectedClass(progress: SurvivorsProgress): HeroClass =
        HeroClass.fromShopId(progress.selectedClass)?.takeIf { isUnlocked(progress, it) } ?: HeroClass.KNIGHT

    fun isUnlocked(progress: SurvivorsProgress, stage: SurvivorsStage): Boolean =
        SurvivorsUnlocks.forStage(stage)?.let { isUnlocked(progress, it) } ?: true

    /** Gewählte Stage; fällt auf die Wiese zurück, wenn sie (noch) nicht freigeschaltet ist. */
    fun selectedStage(progress: SurvivorsProgress): SurvivorsStage =
        SurvivorsStage.fromId(progress.selectedStage).takeIf { isUnlocked(progress, it) } ?: SurvivorsStage.MEADOW

    /** Zähler nach einem beendeten Lauf; ein Sieg zählt für den Endboss der jeweiligen Stage. */
    fun afterRun(
        stats: Map<String, Int>,
        victory: Boolean,
        bossesKilled: Int,
        seconds: Int,
        stage: SurvivorsStage = SurvivorsStage.MEADOW,
    ): Map<String, Int> = stats.toMutableMap().apply {
        merge(SurvivorsStats.RUNS, 1, Int::plus)
        if (victory) {
            val key = if (stage == SurvivorsStage.GRAVEYARD) SurvivorsStats.LICH_DEFEATED else SurvivorsStats.DEMON_DEFEATED
            merge(key, 1, Int::plus)
        }
        if (bossesKilled > 0) merge(SurvivorsStats.BOSSES_KILLED, bossesKilled, Int::plus)
        merge(SurvivorsStats.BEST_TIME, seconds, ::maxOf)
    }
}

/** Ergebnis der einmaligen Übernahme aus dem alten Gold-Shop. */
data class LegacyMigration(val unlockedClasses: Set<String>, val selectedClass: String?, val refundCoins: Int)

object SurvivorsLegacy {
    /** Früher im Gold-Shop verkaufte Welten und ihr Preis (werden erstattet). */
    val WORLD_PRICES = mapOf(
        "world_frost" to 300,
        "world_volcano" to 300,
        "world_graveyard" to 400,
        "world_neon" to 500,
    )
    val LEGACY_IDS: Set<String> = WORLD_PRICES.keys + "world_meadow" + HeroClass.entries.map { it.shopId }

    /** Bereits gekaufte Klassen bleiben freigeschaltet, gekaufte Welten werden in Coins erstattet. */
    fun migrate(owned: Set<String>, equippedClass: String?): LegacyMigration {
        val classes = HeroClass.entries.map { it.shopId }.filter { it in owned }.toSet()
        val selected = equippedClass?.takeIf { id -> id in classes || id == HeroClass.KNIGHT.shopId }
        val refund = WORLD_PRICES.filterKeys { it in owned }.values.sum()
        return LegacyMigration(classes, selected, refund)
    }
}
