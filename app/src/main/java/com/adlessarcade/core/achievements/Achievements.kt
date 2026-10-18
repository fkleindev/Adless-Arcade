package com.adlessarcade.core.achievements

/**
 * Ein Erfolg. Wer ihn schafft, bekommt den Titel [titleName] (Shop-Artikel [titleId]), der nicht mit Coins kaufbar ist.
 * [gameId] = null: spielübergreifend.
 */
data class Achievement(
    val id: String,
    val gameId: String?,
    val name: String,
    val description: String,
    val icon: String,
    val titleName: String,
) {
    val titleId: String get() = "title_ach_$id"
}

object Achievements {
    // Die Spiel-IDs stehen hier als Text, damit dieser Kern nicht von den Spielpaketen abhängt.
    private const val BLOCK_BREAK = "blockbreak"
    private const val GATE_RUSH = "gaterush"
    private const val SURVIVORS = "survivors"
    private const val GALAXY_TOWERS = "galaxytowers"
    private const val RUNE_TACTICS = "runetactics"
    private const val KITCHEN_RUSH = "kitchenrush"

    // Block Break
    const val BB_FULL_CLEAR = "bb_full_clear"
    const val BB_STREAK = "bb_streak"
    const val BB_SCORE_2000 = "bb_score_2000"
    const val BB_SCORE_10000 = "bb_score_10000"

    // Gate Rush
    const val GR_BOSS = "gr_boss"
    const val GR_UNTOUCHED = "gr_untouched"
    const val GR_ENDLESS_3MIN = "gr_endless_3min"
    const val GR_BOSS_RUSH_5 = "gr_boss_rush_5"

    // Rune Survivors
    const val SV_EVOLUTION = "sv_evolution"
    const val SV_KILLS = "sv_kills"
    const val SV_VICTORY = "sv_victory"
    const val SV_ENDLESS_20MIN = "sv_endless_20min"

    // Galaxy Towers 2
    const val GT_ROUND_CLEAR = "gt_round_clear"
    const val GT_STREAK = "gt_streak"
    const val GT_ROUND_5 = "gt_round_5"

    // Rune Tactics
    const val RT_ROUND_15 = "rt_round_15"
    const val RT_THREE_STAR = "rt_three_star"
    const val RT_FULL_SYNERGY = "rt_full_synergy"

    // Kitchen Rush
    const val KR_THREE_STARS = "kr_three_stars"
    const val KR_STREAK = "kr_streak"
    const val KR_CHEF = "kr_chef"

    // Spielübergreifend
    const val ALL_ROUNDER = "all_rounder"

    /** Schwellen für Fortschrittsbedingungen. */
    const val BB_STREAK_TARGET = 5
    const val GR_UNTOUCHED_SECONDS = 90f
    const val GR_ENDLESS_SECONDS = 180f
    const val GR_BOSS_RUSH_WAVE = 5
    const val SV_KILLS_TARGET = 3000
    const val SV_ENDLESS_SECONDS = 1200f
    const val GT_STREAK_TARGET = 15
    const val GT_ROUND_TARGET = 5
    const val RT_ROUND_TARGET = 15
    const val KR_STREAK_TARGET = 10
    const val KR_CHEF_SCORE = 3000

    val all: List<Achievement> = listOf(
        Achievement(BB_FULL_CLEAR, BLOCK_BREAK, "Tabula Rasa", "Räume das komplette Brett leer.", "✨", "Tabula Rasa"),
        Achievement(BB_STREAK, BLOCK_BREAK, "Kettenreaktion", "Erreiche eine $BB_STREAK_TARGET-fach-Combo.", "🔗", "Combo-Virtuose"),
        Achievement(BB_SCORE_2000, BLOCK_BREAK, "Architekt", "Erreiche 2.000 Punkte in einem Spiel.", "🧱", "Blockarchitekt"),
        Achievement(BB_SCORE_10000, BLOCK_BREAK, "Block-Legende", "Erreiche 10.000 Punkte in einem Spiel.", "🏛️", "Block-Legende"),

        Achievement(GR_BOSS, GATE_RUSH, "Bossbrecher", "Besiege einen Boss.", "👹", "Bossbrecher"),
        Achievement(GR_UNTOUCHED, GATE_RUSH, "Unantastbar", "Überstehe 90 Sekunden am Stück, ohne ein Herz zu verlieren.", "🛡️", "Unantastbar"),
        Achievement(GR_ENDLESS_3MIN, GATE_RUSH, "Linienhalter", "Halte im Endless-Modus 3 Minuten durch.", "⏱️", "Linienhalter"),
        Achievement(GR_BOSS_RUSH_5, GATE_RUSH, "Boss-Jäger", "Erreiche im Boss Rush Welle $GR_BOSS_RUSH_WAVE.", "🎯", "Boss-Jäger"),

        Achievement(SV_EVOLUTION, SURVIVORS, "Runenschmied", "Entwickle eine Waffe.", "⚒️", "Runenschmied"),
        Achievement(SV_KILLS, SURVIVORS, "Hordenbrecher", "Besiege 3.000 Gegner in einem Lauf.", "💀", "Hordenbrecher"),
        Achievement(SV_VICTORY, SURVIVORS, "Dämonenbezwinger", "Besiege den Dämonenfürsten im 15-Minuten-Modus.", "😈", "Dämonenbezwinger"),
        Achievement(SV_ENDLESS_20MIN, SURVIVORS, "Unsterblich", "Überlebe im Endlos-Modus 20 Minuten.", "♾️", "Unsterblich"),

        Achievement(GT_ROUND_CLEAR, GALAXY_TOWERS, "Sternenstaub", "Räume alle drei Türme einer Runde ab.", "🌠", "Sternenstaub"),
        Achievement(GT_STREAK, GALAXY_TOWERS, "Kometenschweif", "Lege eine Kette aus $GT_STREAK_TARGET Karten.", "☄️", "Kometenschweif"),
        Achievement(GT_ROUND_5, GALAXY_TOWERS, "Galaxie-Meister", "Räume $GT_ROUND_TARGET Runden in einem Spiel ab.", "🌌", "Galaxie-Meister"),

        Achievement(RT_ROUND_15, RUNE_TACTICS, "Feldherr", "Erreiche Runde $RT_ROUND_TARGET.", "⚔️", "Feldherr"),
        Achievement(RT_THREE_STAR, RUNE_TACTICS, "Dreigestirn", "Werte eine Einheit auf ★★★ auf.", "⭐", "Dreigestirn"),
        Achievement(RT_FULL_SYNERGY, RUNE_TACTICS, "Bannerträger", "Aktiviere eine Synergie auf höchster Stufe.", "🚩", "Bannerträger"),

        Achievement(KR_THREE_STARS, KITCHEN_RUSH, "Sternekoch", "Serviere ein Gericht mit drei Sternen.", "👩‍🍳", "Sternekoch"),
        Achievement(KR_STREAK, KITCHEN_RUSH, "Fließband", "Serviere $KR_STREAK_TARGET Gerichte in Folge, ohne eine Bestellung zu verpassen.", "🍳", "Fließbandkoch"),
        Achievement(KR_CHEF, KITCHEN_RUSH, "Küchenchef", "Erreiche im Endlos-Modus $KR_CHEF_SCORE Punkte.", "🥞", "Küchenchef"),

        Achievement(ALL_ROUNDER, null, "Allrounder", "Schaffe in jedem Spiel mindestens einen Erfolg.", "🏆", "Allrounder"),
    )

    private val byId = all.associateBy { it.id }

    fun find(id: String): Achievement? = byId[id]

    /** Spiele, in denen es Erfolge gibt (für „Allrounder“). */
    val gameIds: Set<String> = all.mapNotNull { it.gameId }.toSet()

    /** Erfolge, die durch die bereits freigeschalteten zusätzlich fällig werden (derzeit nur „Allrounder“). */
    fun derived(unlocked: Set<String>): List<Achievement> {
        val allRounder = byId.getValue(ALL_ROUNDER)
        if (ALL_ROUNDER in unlocked) return emptyList()
        val gamesDone = all.filter { it.id in unlocked }.mapNotNull { it.gameId }.toSet()
        return if (gamesDone.containsAll(gameIds)) listOf(allRounder) else emptyList()
    }
}
