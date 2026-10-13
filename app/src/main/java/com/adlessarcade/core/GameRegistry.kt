package com.adlessarcade.core

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.adlessarcade.games.blockbreak.BlockBreak
import com.adlessarcade.games.blockbreak.BlockBreakPreview
import com.adlessarcade.ui.theme.NeonCyan
import com.adlessarcade.ui.theme.NeonViolet

data class ModeInfo(
    val id: String,
    val title: String,
    val description: String,
    val available: Boolean = true,
)

data class GameInfo(
    val id: String,
    val title: String,
    val tagline: String,
    val description: String,
    val accent: Color,
    val accentSecondary: Color,
    val modes: List<ModeInfo>,
    val preview: @Composable (Modifier) -> Unit,
)

/** Zentrale Liste aller Spiele. Neues Spiel: hier eintragen und in GameHost (AppNavHost) verknüpfen. */
object GameRegistry {
    val games: List<GameInfo> = listOf(
        GameInfo(
            id = BlockBreak.GAME_ID,
            title = "Block Break",
            tagline = "Blöcke setzen, Reihen sprengen",
            description = "Ziehe die Blöcke aufs 8×8-Feld. Volle Reihen und Spalten verschwinden – " +
                "räume in Folge ab, um Combos auszulösen.",
            accent = NeonViolet,
            accentSecondary = NeonCyan,
            modes = listOf(
                ModeInfo(
                    id = BlockBreak.MODE_ENDLESS,
                    title = "Endless",
                    description = "Spiele, bis nichts mehr passt. Jage deinen Highscore.",
                ),
                ModeInfo(
                    id = "coming_soon",
                    title = "Weitere Modi",
                    description = "Kommen bald.",
                    available = false,
                ),
            ),
            preview = { modifier -> BlockBreakPreview(modifier) },
        ),
    )

    fun find(id: String?): GameInfo? = games.firstOrNull { it.id == id }
}
