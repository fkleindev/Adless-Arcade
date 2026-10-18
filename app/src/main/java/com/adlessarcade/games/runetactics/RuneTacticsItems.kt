package com.adlessarcade.games.runetactics

/** Sondereffekte fertiger Items, ausgewertet in der Kampf-Simulation. */
enum class ItemEffect {
    DAMAGE, RAGE, OMNIVAMP, SUNDER, LIFESTEAL, TITAN,
    DOUBLE_SHOT, SPARK, RANGE, DODGE, BURN,
    AP, SHIELD_START, MANA_START, CAST_HEAL,
    THORNS, DMG_REDUCE, AURA_BURN,
    MAGIC_REDUCE, STUN_IMMUNE,
    REGEN,
}

data class ItemDef(
    val id: String,
    val name: String,
    /** Leer bei Komponenten, sonst die zwei Komponenten-IDs. */
    val recipe: List<String>,
    val hp: Float = 0f,
    val ad: Float = 0f,
    /** Angriffstempo in Prozent (0,15 = +15 %). */
    val attackSpeed: Float = 0f,
    val armor: Float = 0f,
    val mr: Float = 0f,
    /** Fähigkeitsschaden in Prozent. */
    val ability: Float = 0f,
    val effect: ItemEffect? = null,
    val description: String,
) {
    val isComponent: Boolean get() = recipe.isEmpty()

    /** Werte als kurze Zeile, z. B. „+15 Angriff · +150 Leben“. */
    val statLine: String
        get() = buildList {
            if (ad > 0f) add("+${ad.toInt()} Angriff")
            if (attackSpeed > 0f) add("+${(attackSpeed * 100).toInt()} % Tempo")
            if (ability > 0f) add("+${(ability * 100).toInt()} % Fähigkeit")
            if (armor > 0f) add("+${armor.toInt()} Rüstung")
            if (mr > 0f) add("+${mr.toInt()} Magieres.")
            if (hp > 0f) add("+${hp.toInt()} Leben")
        }.joinToString(" · ")
}

object RuneTacticsItems {
    const val MAX_PER_UNIT = 3
    const val INVENTORY_SIZE = 10

    const val SWORD = "sword"
    const val BOW = "bow"
    const val ROD = "rod"
    const val VEST = "vest"
    const val CLOAK = "cloak"
    const val BELT = "belt"

    val components: List<ItemDef> = listOf(
        ItemDef(SWORD, "Schwert", emptyList(), ad = 15f, description = "+15 Angriff"),
        ItemDef(BOW, "Bogen", emptyList(), attackSpeed = 0.15f, description = "+15 % Angriffstempo"),
        ItemDef(ROD, "Stab", emptyList(), ability = 0.2f, description = "+20 % Fähigkeitsschaden"),
        ItemDef(VEST, "Weste", emptyList(), armor = 25f, description = "+25 Rüstung"),
        ItemDef(CLOAK, "Umhang", emptyList(), mr = 25f, description = "+25 Magieresistenz"),
        ItemDef(BELT, "Gürtel", emptyList(), hp = 150f, description = "+150 Leben"),
    )

    private val componentById = components.associateBy { it.id }

    private fun combined(a: String, b: String, name: String, effect: ItemEffect, description: String, extra: ItemDef.() -> ItemDef = { this }): ItemDef {
        val ca = componentById.getValue(a)
        val cb = componentById.getValue(b)
        return ItemDef(
            id = "${a}_$b",
            name = name,
            recipe = listOf(a, b),
            hp = ca.hp + cb.hp,
            ad = ca.ad + cb.ad,
            attackSpeed = ca.attackSpeed + cb.attackSpeed,
            armor = ca.armor + cb.armor,
            mr = ca.mr + cb.mr,
            ability = ca.ability + cb.ability,
            effect = effect,
            description = description,
        ).extra()
    }

    val combinedItems: List<ItemDef> = listOf(
        combined(SWORD, SWORD, "Riesenklinge", ItemEffect.DAMAGE, "Verursacht 30 % mehr Schaden."),
        combined(SWORD, BOW, "Raserklinge", ItemEffect.RAGE, "Jeder Angriff erhöht das Angriffstempo um 6 % (bis 60 %)."),
        combined(SWORD, ROD, "Runenklinge", ItemEffect.OMNIVAMP, "Heilt um 20 % des verursachten Schadens."),
        combined(SWORD, VEST, "Rüstungsbrecher", ItemEffect.SUNDER, "Angriffe senken die Rüstung des Ziels um 40 %."),
        combined(SWORD, CLOAK, "Blutdurst", ItemEffect.LIFESTEAL, "Angriffe heilen um 30 % des Schadens."),
        combined(SWORD, BELT, "Titanenzorn", ItemEffect.TITAN, "Erlittene Treffer erhöhen den Schaden um 1,5 % (bis 25×)."),
        combined(BOW, BOW, "Zwillingsbogen", ItemEffect.DOUBLE_SHOT, "Jeder dritte Angriff trifft doppelt."),
        combined(BOW, ROD, "Funkenbogen", ItemEffect.SPARK, "Jeder dritte Angriff entlädt 90 Blitzschaden auf bis zu 3 Gegner."),
        combined(BOW, VEST, "Langbogen", ItemEffect.RANGE, "+1 Reichweite."),
        combined(BOW, CLOAK, "Windmantel", ItemEffect.DODGE, "+20 % Ausweichen."),
        combined(BOW, BELT, "Feuerpfeile", ItemEffect.BURN, "Angriffe verursachen zusätzlich 3 % der maximalen Leben des Ziels."),
        combined(ROD, ROD, "Erzmagierstab", ItemEffect.AP, "Weitere +50 % Fähigkeitsschaden."),
        combined(ROD, VEST, "Runenschild", ItemEffect.SHIELD_START, "Startet den Kampf mit einem Schild von 300."),
        combined(ROD, CLOAK, "Arkaner Fokus", ItemEffect.MANA_START, "Startet den Kampf mit 40 zusätzlichem Mana."),
        combined(ROD, BELT, "Lebensfunke", ItemEffect.CAST_HEAL, "Heilt sich beim Wirken um 15 % der maximalen Leben."),
        combined(VEST, VEST, "Dornenpanzer", ItemEffect.THORNS, "Wirft 30 % des erlittenen Angriffsschadens zurück."),
        combined(VEST, CLOAK, "Steinhaut", ItemEffect.DMG_REDUCE, "Erleidet 15 % weniger Schaden."),
        combined(VEST, BELT, "Sonnenfeuer", ItemEffect.AURA_BURN, "Verbrennt angrenzende Gegner mit 40 Schaden pro Sekunde."),
        combined(CLOAK, CLOAK, "Drachenschuppe", ItemEffect.MAGIC_REDUCE, "Erleidet 25 % weniger magischen Schaden."),
        combined(CLOAK, BELT, "Bannrobe", ItemEffect.STUN_IMMUNE, "Immun gegen Betäubung."),
        combined(BELT, BELT, "Riesengürtel", ItemEffect.REGEN, "+300 Leben, regeneriert 3 % Leben pro Sekunde.") {
            copy(hp = hp + 300f)
        },
    )

    val all: List<ItemDef> = components + combinedItems

    private val byId = all.associateBy { it.id }
    private val byRecipe = combinedItems.associateBy { it.recipe.sorted() }

    fun byId(id: String): ItemDef = byId.getValue(id)

    fun find(id: String): ItemDef? = byId[id]

    /** Fertiges Item aus zwei Komponenten (Reihenfolge egal). */
    fun combine(a: String, b: String): ItemDef? = byRecipe[listOf(a, b).sorted()]
}
