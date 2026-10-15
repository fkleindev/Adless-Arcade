package com.adlessarcade.games.survivors

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

enum class DecorStyle { MEADOW, FROST, VOLCANO, GRAVEYARD, NEON }

/** Optik einer Stage: Boden, Deko und Einfärbung der Gegner. */
@Immutable
data class SurvivorTheme(
    val ground: Color,
    val groundAlt: Color,
    val detail: Color,
    val accent: Color,
    val decor: DecorStyle,
    val enemyTint: Color = Color.Transparent,
    val tintAmount: Float = 0f,
    val vignette: Color = Color.Black,
)

/**
 * Eine spielbare Stage: eigene Optik, Gegnermischung, Bosse und Schwierigkeit.
 * [unlockId] = Eintrag der Runen-Schmiede (null = von Beginn an spielbar).
 */
enum class SurvivorsStage(
    val id: String,
    val title: String,
    val number: Int,
    val bossKind: EnemyKind,
    val finalBoss: EnemyKind,
    val unlockId: String?,
    val hpFactor: Float = 1f,
    val spawnFactor: Float = 1f,
    val damageFactor: Float = 1f,
    val runeFactor: Float = 1f,
    /** Hindernisse, durch die Held und Gegner nicht laufen können. */
    val hasObstacles: Boolean = false,
) {
    MEADOW("meadow", "Fantasy-Wiese", 1, EnemyKind.BOSS, EnemyKind.DEMON, unlockId = null),
    GRAVEYARD(
        "graveyard", "Vergessener Friedhof", 2, EnemyKind.GRAVE_GUARDIAN, EnemyKind.LICH, unlockId = "stage_graveyard",
        hpFactor = 1.1f, spawnFactor = 1.05f, damageFactor = 1.1f, runeFactor = 1.25f, hasObstacles = true,
    );

    val theme: SurvivorTheme
        get() = when (this) {
            MEADOW -> SurvivorsStages.MEADOW
            GRAVEYARD -> SurvivorsStages.GRAVEYARD
        }

    /** Schlüssel für Rekorde/Bestenlisten: die Wiese behält die bisherigen Schlüssel. */
    fun scoreKey(mode: SurvivorsMode): String = if (this == MEADOW) mode.id else "${mode.id}_$id"
    fun leaderboardVariant(heroClass: HeroClass): String = if (this == MEADOW) heroClass.shopId else "${id}_${heroClass.shopId}"

    companion object {
        fun fromId(id: String?): SurvivorsStage = entries.firstOrNull { it.id == id } ?: MEADOW
    }
}

/** Optiken der Stages; FROST, VOLCANO und NEON sind Vorlagen für spätere Stages. */
object SurvivorsStages {
    val MEADOW = SurvivorTheme(
        ground = Color(0xFF1F3B24), groundAlt = Color(0xFF244530), detail = Color(0xFF4ADE80),
        accent = Color(0xFFFDE047), decor = DecorStyle.MEADOW,
    )

    // --- Vorlagen für spätere Stages ---

    val FROST = SurvivorTheme(
        ground = Color(0xFF1E3A52), groundAlt = Color(0xFF23445F), detail = Color(0xFFBAE6FD),
        accent = Color(0xFFE0F2FE), decor = DecorStyle.FROST,
        enemyTint = Color(0xFF93C5FD), tintAmount = 0.25f, vignette = Color(0xFF0C1A2B),
    )
    val VOLCANO = SurvivorTheme(
        ground = Color(0xFF2A1410), groundAlt = Color(0xFF331A13), detail = Color(0xFFF97316),
        accent = Color(0xFFFACC15), decor = DecorStyle.VOLCANO,
        enemyTint = Color(0xFFFF5C3A), tintAmount = 0.2f, vignette = Color(0xFF2A0500),
    )
    val GRAVEYARD = SurvivorTheme(
        ground = Color(0xFF1C1B26), groundAlt = Color(0xFF232131), detail = Color(0xFF6B7280),
        accent = Color(0xFFA78BFA), decor = DecorStyle.GRAVEYARD,
        enemyTint = Color(0xFF9CA3AF), tintAmount = 0.3f, vignette = Color(0xFF0A0A12),
    )
    val NEON = SurvivorTheme(
        ground = Color(0xFF0B0B1A), groundAlt = Color(0xFF111129), detail = Color(0xFF22D3EE),
        accent = Color(0xFFF472B6), decor = DecorStyle.NEON,
        enemyTint = Color(0xFFE879F9), tintAmount = 0.3f, vignette = Color(0xFF05010F),
    )
}
