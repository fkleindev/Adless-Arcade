package com.adlessarcade.games.runetactics

import com.adlessarcade.games.runetactics.AbilityKind.BOLT
import com.adlessarcade.games.runetactics.AbilityKind.CHAIN
import com.adlessarcade.games.runetactics.AbilityKind.DRAIN
import com.adlessarcade.games.runetactics.AbilityKind.FROST
import com.adlessarcade.games.runetactics.AbilityKind.HEAL_ALLY
import com.adlessarcade.games.runetactics.AbilityKind.LEAP
import com.adlessarcade.games.runetactics.AbilityKind.MOON
import com.adlessarcade.games.runetactics.AbilityKind.QUAKE
import com.adlessarcade.games.runetactics.AbilityKind.SHIELD_SELF
import com.adlessarcade.games.runetactics.AbilityKind.SPLASH
import com.adlessarcade.games.runetactics.AbilityKind.STRIKE
import com.adlessarcade.games.runetactics.AbilityKind.WHIRL
import com.adlessarcade.games.runetactics.Trait.ASSASSIN
import com.adlessarcade.games.runetactics.Trait.DEMON
import com.adlessarcade.games.runetactics.Trait.DWARF
import com.adlessarcade.games.runetactics.Trait.ELEMENTAL
import com.adlessarcade.games.runetactics.Trait.ELF
import com.adlessarcade.games.runetactics.Trait.GUARDIAN
import com.adlessarcade.games.runetactics.Trait.MAGE
import com.adlessarcade.games.runetactics.Trait.RANGER
import com.adlessarcade.games.runetactics.Trait.UNDEAD
import com.adlessarcade.games.runetactics.Trait.WARRIOR

/** Katalog aller Shop-Einheiten: 30 Stück, je Synergie 6 Einheiten über alle Kostenstufen verteilt. */
object RuneTacticsUnits {
    val all: List<UnitDef> = listOf(
        // --- 1 Gold ---
        UnitDef(
            "skeleton", "Skelettkrieger", 1, UNDEAD, WARRIOR,
            hp = 600f, ad = 55f, attackSpeed = 0.7f, range = 1, armor = 30f, mr = 20f, maxMana = 70, startMana = 0,
            ability = Ability("Knochenhieb", STRIKE, 2.2f, "Schlägt mit 220 % Angriffsschaden zu."),
        ),
        UnitDef(
            "bladedancer", "Klingentänzerin", 1, ELF, WARRIOR,
            hp = 550f, ad = 50f, attackSpeed = 0.8f, range = 1, armor = 25f, mr = 20f, maxMana = 60, startMana = 0,
            ability = Ability("Klingenwirbel", WHIRL, 160f, "Wirbelt herum: 160 Schaden an allen angrenzenden Gegnern."),
        ),
        UnitDef(
            "dwarfguard", "Zwergenwache", 1, DWARF, GUARDIAN,
            hp = 700f, ad = 45f, attackSpeed = 0.6f, range = 1, armor = 40f, mr = 30f, maxMana = 80, startMana = 20,
            ability = Ability("Schildwall", SHIELD_SELF, 350f, "Erhält einen Schild von 350."),
        ),
        UnitDef(
            "wisp", "Grablicht", 1, UNDEAD, MAGE,
            hp = 450f, ad = 40f, attackSpeed = 0.65f, range = 3, armor = 15f, mr = 20f, maxMana = 60, startMana = 10,
            ability = Ability("Seelenpfeil", BOLT, 220f, "Feuert einen Seelenpfeil: 220 magischer Schaden."),
        ),
        UnitDef(
            "apprentice", "Elfen-Lehrling", 1, ELF, MAGE,
            hp = 450f, ad = 40f, attackSpeed = 0.7f, range = 3, armor = 15f, mr = 20f, maxMana = 70, startMana = 10,
            ability = Ability("Funkenregen", SPLASH, 170f, "170 magischer Schaden am Ziel und angrenzenden Gegnern."),
        ),
        // --- 2 Gold ---
        UnitDef(
            "axeman", "Zwergen-Axtkämpfer", 2, DWARF, WARRIOR,
            hp = 750f, ad = 65f, attackSpeed = 0.7f, range = 1, armor = 35f, mr = 25f, maxMana = 80, startMana = 0,
            ability = Ability("Spalter", STRIKE, 2.8f, "Spaltet das Ziel mit 280 % Angriffsschaden."),
        ),
        UnitDef(
            "bonewarden", "Knochenwächter", 2, UNDEAD, GUARDIAN,
            hp = 800f, ad = 50f, attackSpeed = 0.6f, range = 1, armor = 45f, mr = 30f, maxMana = 90, startMana = 30,
            ability = Ability(
                "Grabesgriff", QUAKE, 120f, "120 Schaden an angrenzenden Gegnern, betäubt sie 1 s.", stun = 1f,
            ),
        ),
        UnitDef(
            "warden", "Elfen-Hüterin", 2, ELF, GUARDIAN,
            hp = 700f, ad = 45f, attackSpeed = 0.65f, range = 2, armor = 35f, mr = 35f, maxMana = 80, startMana = 20,
            ability = Ability("Waldsegen", HEAL_ALLY, 300f, "Heilt den am stärksten verletzten Verbündeten um 300."),
        ),
        UnitDef(
            "runesmith", "Runenschmied", 2, DWARF, MAGE,
            hp = 550f, ad = 45f, attackSpeed = 0.65f, range = 3, armor = 20f, mr = 25f, maxMana = 80, startMana = 20,
            ability = Ability("Feuerschlag", SPLASH, 260f, "260 magischer Schaden am Ziel und angrenzenden Gegnern."),
        ),
        // --- 3 Gold ---
        UnitDef(
            "deathknight", "Todesritter", 3, UNDEAD, WARRIOR,
            hp = 900f, ad = 75f, attackSpeed = 0.75f, range = 1, armor = 40f, mr = 30f, maxMana = 80, startMana = 0,
            ability = Ability("Seelenernte", DRAIN, 300f, "300 magischer Schaden, heilt sich um die Hälfte."),
        ),
        UnitDef(
            "thane", "Zwergen-Than", 3, DWARF, GUARDIAN,
            hp = 1000f, ad = 60f, attackSpeed = 0.6f, range = 1, armor = 55f, mr = 40f, maxMana = 100, startMana = 40,
            ability = Ability(
                "Erdbeben", QUAKE, 220f, "220 Schaden an angrenzenden Gegnern, betäubt sie 1,5 s.", stun = 1.5f,
            ),
        ),
        UnitDef(
            "lich", "Lich", 3, UNDEAD, MAGE,
            hp = 650f, ad = 50f, attackSpeed = 0.7f, range = 3, armor = 25f, mr = 30f, maxMana = 90, startMana = 30,
            ability = Ability(
                "Frostnova", FROST, 300f, "300 Schaden am Ziel und angrenzenden Gegnern, betäubt 1,2 s.", stun = 1.2f,
            ),
        ),
        // --- 4 Gold ---
        UnitDef(
            "stormmage", "Sturmmagierin", 4, ELF, MAGE,
            hp = 750f, ad = 55f, attackSpeed = 0.75f, range = 3, armor = 25f, mr = 35f, maxMana = 90, startMana = 30,
            ability = Ability("Kettenblitz", CHAIN, 350f, "Blitz mit 350 Schaden, springt auf bis zu 4 Gegner.", count = 4),
        ),
        UnitDef(
            "warlord", "Zwergen-Kriegsherr", 4, DWARF, WARRIOR,
            hp = 1100f, ad = 90f, attackSpeed = 0.75f, range = 1, armor = 50f, mr = 35f, maxMana = 90, startMana = 20,
            ability = Ability(
                "Bergsturz", LEAP, 300f,
                "Springt zum entferntesten Gegner: 300 Schaden in der Nähe, betäubt 0,8 s.", stun = 0.8f,
            ),
        ),
        // --- 5 Gold ---
        UnitDef(
            "queen", "Elfenkönigin", 5, ELF, GUARDIAN,
            hp = 1300f, ad = 80f, attackSpeed = 0.8f, range = 2, armor = 50f, mr = 50f, maxMana = 100, startMana = 40,
            ability = Ability(
                "Mondsegen", MOON, 300f,
                "Schild von 300 für alle Verbündeten, 300 Schaden an Gegnern im Umkreis von 2 Feldern.",
            ),
        ),
        // --- Erweiterung: Dämonen, Elementare, Waldläufer, Assassinen ---
        UnitDef(
            "imp", "Teufelchen", 1, DEMON, ASSASSIN,
            hp = 500f, ad = 55f, attackSpeed = 0.8f, range = 1, armor = 20f, mr = 20f, maxMana = 60, startMana = 0,
            ability = Ability("Höllenstich", STRIKE, 2.0f, "Sticht mit 200 % Angriffsschaden zu."),
        ),
        UnitDef(
            "sparkling", "Funkengeist", 1, ELEMENTAL, RANGER,
            hp = 450f, ad = 45f, attackSpeed = 0.75f, range = 3, armor = 15f, mr = 25f, maxMana = 60, startMana = 10,
            ability = Ability("Funke", BOLT, 200f, "Schleudert einen Funken: 200 magischer Schaden."),
        ),
        UnitDef(
            "huntress", "Elfenschützin", 1, ELF, RANGER,
            hp = 450f, ad = 50f, attackSpeed = 0.75f, range = 4, armor = 15f, mr = 15f, maxMana = 70, startMana = 0,
            ability = Ability("Gezielter Schuss", STRIKE, 2.4f, "Ein Schuss mit 240 % Angriffsschaden."),
        ),
        UnitDef(
            "emberling", "Glutwicht", 1, ELEMENTAL, WARRIOR,
            hp = 600f, ad = 50f, attackSpeed = 0.7f, range = 1, armor = 30f, mr = 25f, maxMana = 70, startMana = 10,
            ability = Ability("Glutstoß", WHIRL, 150f, "150 Schaden an allen angrenzenden Gegnern."),
        ),
        UnitDef(
            "shadowblade", "Schattenklinge", 2, UNDEAD, ASSASSIN,
            hp = 600f, ad = 65f, attackSpeed = 0.8f, range = 1, armor = 25f, mr = 25f, maxMana = 70, startMana = 0,
            ability = Ability("Meucheln", STRIKE, 2.6f, "Trifft mit 260 % Angriffsschaden."),
        ),
        UnitDef(
            "crossbow", "Armbrustschütze", 2, DWARF, RANGER,
            hp = 600f, ad = 55f, attackSpeed = 0.7f, range = 3, armor = 25f, mr = 20f, maxMana = 80, startMana = 10,
            ability = Ability("Bolzenhagel", SPLASH, 200f, "200 Schaden am Ziel und angrenzenden Gegnern."),
        ),
        UnitDef(
            "succubus", "Sukkubus", 2, DEMON, MAGE,
            hp = 550f, ad = 45f, attackSpeed = 0.7f, range = 3, armor = 20f, mr = 30f, maxMana = 80, startMana = 20,
            ability = Ability("Seelenkuss", DRAIN, 260f, "260 magischer Schaden, heilt sich um die Hälfte."),
        ),
        UnitDef(
            "rockwarden", "Felswächter", 2, ELEMENTAL, GUARDIAN,
            hp = 850f, ad = 45f, attackSpeed = 0.55f, range = 1, armor = 50f, mr = 30f, maxMana = 90, startMana = 30,
            ability = Ability("Felshaut", SHIELD_SELF, 450f, "Erhält einen Schild von 450."),
        ),
        UnitDef(
            "demonhunter", "Dämonenjäger", 3, DEMON, RANGER,
            hp = 750f, ad = 70f, attackSpeed = 0.8f, range = 3, armor = 25f, mr = 25f, maxMana = 80, startMana = 0,
            ability = Ability("Prellschuss", CHAIN, 240f, "Ein Schuss mit 240 Schaden prallt auf bis zu 3 Gegner.", count = 3),
        ),
        UnitDef(
            "stormspirit", "Sturmelementar", 3, ELEMENTAL, ASSASSIN,
            hp = 750f, ad = 70f, attackSpeed = 0.8f, range = 1, armor = 30f, mr = 35f, maxMana = 80, startMana = 20,
            ability = Ability(
                "Blitzsprung", LEAP, 260f, "Springt zum entferntesten Gegner: 260 Schaden in der Nähe, betäubt 0,5 s.",
                stun = 0.5f,
            ),
        ),
        UnitDef(
            "nightstalker", "Nachtpirscherin", 3, DEMON, ASSASSIN,
            hp = 750f, ad = 80f, attackSpeed = 0.8f, range = 1, armor = 30f, mr = 25f, maxMana = 70, startMana = 0,
            ability = Ability("Schattenhieb", STRIKE, 3.2f, "Trifft mit 320 % Angriffsschaden."),
        ),
        UnitDef(
            "fireelemental", "Feuerelementar", 4, ELEMENTAL, RANGER,
            hp = 850f, ad = 70f, attackSpeed = 0.8f, range = 3, armor = 30f, mr = 40f, maxMana = 90, startMana = 30,
            ability = Ability("Feuerball", SPLASH, 380f, "380 Schaden am Ziel und angrenzenden Gegnern."),
        ),
        UnitDef(
            "bladedemon", "Klingendämon", 4, DEMON, ASSASSIN,
            hp = 950f, ad = 95f, attackSpeed = 0.85f, range = 1, armor = 40f, mr = 30f, maxMana = 80, startMana = 0,
            ability = Ability("Klingensturm", WHIRL, 340f, "340 Schaden an allen angrenzenden Gegnern."),
        ),
        UnitDef(
            "primal", "Urelementar", 5, ELEMENTAL, RANGER,
            hp = 1150f, ad = 85f, attackSpeed = 0.85f, range = 3, armor = 40f, mr = 50f, maxMana = 100, startMana = 40,
            ability = Ability(
                "Elementarsturm", FROST, 420f, "420 Schaden am Ziel und angrenzenden Gegnern, betäubt 1 s.", stun = 1f,
            ),
        ),
        UnitDef(
            "archdemon", "Erzdämon", 5, DEMON, ASSASSIN,
            hp = 1250f, ad = 110f, attackSpeed = 0.85f, range = 1, armor = 45f, mr = 40f, maxMana = 90, startMana = 20,
            ability = Ability(
                "Höllensturz", LEAP, 450f, "Springt zum entferntesten Gegner: 450 Schaden in der Nähe, betäubt 1 s.",
                stun = 1f,
            ),
        ),
    )

    /** Kreaturen ohne Fähigkeit (maxMana 0), nicht im Shop. */
    private val noAbility = Ability("–", AbilityKind.STRIKE, 1f, "Keine Fähigkeit.")
    val creatures: List<UnitDef> = listOf(
        UnitDef(
            "wolf", "Grauwolf", 1, Trait.BEAST, Trait.BEAST,
            hp = 500f, ad = 45f, attackSpeed = 0.85f, range = 1, armor = 20f, mr = 15f, maxMana = 0, startMana = 0,
            ability = noAbility,
        ),
        UnitDef(
            "golem", "Steingolem", 2, Trait.BEAST, Trait.BEAST,
            hp = 900f, ad = 40f, attackSpeed = 0.5f, range = 1, armor = 50f, mr = 30f, maxMana = 0, startMana = 0,
            ability = noAbility,
        ),
        UnitDef(
            "spider", "Riesenspinne", 1, Trait.BEAST, Trait.BEAST,
            hp = 600f, ad = 50f, attackSpeed = 0.7f, range = 1, armor = 25f, mr = 25f, maxMana = 0, startMana = 0,
            ability = noAbility,
        ),
        UnitDef(
            "harpy", "Harpyie", 1, Trait.BEAST, Trait.BEAST,
            hp = 400f, ad = 40f, attackSpeed = 0.75f, range = 3, armor = 15f, mr = 20f, maxMana = 0, startMana = 0,
            ability = noAbility,
        ),
    )

    /** Bosse rotieren alle 5 Runden; Werte werden mit der Runde skaliert. */
    val bosses: List<UnitDef> = listOf(
        UnitDef(
            "bonedragon", "Knochendrache", 5, Trait.BOSS, Trait.BOSS,
            hp = 2600f, ad = 85f, attackSpeed = 0.6f, range = 2, armor = 40f, mr = 40f, maxMana = 70, startMana = 30,
            ability = Ability(
                "Seelenfeuer", AbilityKind.SLAM, 280f,
                "Markiert ein Gebiet (Radius 1) und verbrennt es kurz darauf mit 280 Schaden.", count = 1,
            ),
        ),
        UnitDef(
            "stonetitan", "Steintitan", 5, Trait.BOSS, Trait.BOSS,
            hp = 3400f, ad = 75f, attackSpeed = 0.5f, range = 1, armor = 70f, mr = 30f, maxMana = 90, startMana = 30,
            ability = Ability(
                "Bergbeben", AbilityKind.SLAM, 200f,
                "Markiert ein großes Gebiet (Radius 2): 200 Schaden und 1,5 s Betäubung.", stun = 1.5f, count = 2,
            ),
        ),
        UnitDef(
            "stormwitch", "Sturmhexe", 5, Trait.BOSS, Trait.BOSS,
            hp = 2200f, ad = 70f, attackSpeed = 0.75f, range = 3, armor = 30f, mr = 60f, maxMana = 60, startMana = 20,
            ability = Ability(
                "Blitzschlag", AbilityKind.SLAM, 330f,
                "Markiert ein Gebiet (Radius 1): 330 Schaden und 0,8 s Betäubung.", stun = 0.8f, count = 1,
            ),
        ),
    )

    private val byId = (all + creatures + bosses).associateBy { it.id }

    fun byId(id: String): UnitDef = byId.getValue(id)

    fun find(id: String): UnitDef? = byId[id]

    fun ofCost(cost: Int): List<UnitDef> = all.filter { it.cost == cost }

    /** Kopien pro Einheit im gemeinsamen Pool. */
    fun poolSize(cost: Int): Int = when (cost) {
        1 -> 22
        2 -> 18
        3 -> 15
        4 -> 12
        else -> 10
    }
}
