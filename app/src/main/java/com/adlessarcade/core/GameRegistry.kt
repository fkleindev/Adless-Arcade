package com.adlessarcade.core

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.adlessarcade.games.blockbreak.BlockBreak
import com.adlessarcade.games.blockbreak.BlockBreakPreview
import com.adlessarcade.games.gaterush.GateRush
import com.adlessarcade.games.gaterush.GateRushMode
import com.adlessarcade.games.gaterush.GateRushPreview
import com.adlessarcade.core.leaderboard.LeaderboardVariant
import com.adlessarcade.games.survivors.HeroClass
import com.adlessarcade.games.survivors.Survivors
import com.adlessarcade.games.survivors.SurvivorsMode
import com.adlessarcade.games.survivors.SurvivorsPreview
import com.adlessarcade.games.survivors.SurvivorsStage
import com.adlessarcade.ui.theme.NeonCyan
import com.adlessarcade.ui.theme.NeonPink
import com.adlessarcade.ui.theme.NeonViolet
import com.adlessarcade.ui.theme.NeonYellow

/** Zusätzlicher Knopf im Modus-Menü eines Spiels (z. B. eigener Shop). */
data class GameAction(val title: String, val route: String)

data class ModeInfo(
    val id: String,
    val title: String,
    val description: String,
    val available: Boolean = true,
    /** Schlüssel für Highscore und Spielstand; z. B. pro Tag bei der Täglichen Challenge. */
    val scoreKey: () -> String = { id },
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
    /** Weitere Unterteilung der Bestenlisten innerhalb eines Modus (z. B. Klassen); leer = keine. */
    val leaderboardVariants: List<LeaderboardVariant> = emptyList(),
    /**
     * Oberste Unterteilung der Bestenlisten (z. B. Stages). Die erste Gruppe hat die ID "" und nutzt die Varianten-ID
     * unverändert; weitere Gruppen setzen ihre ID davor ("graveyard_class_mage").
     */
    val leaderboardGroups: List<LeaderboardVariant> = emptyList(),
    val extraAction: GameAction? = null,
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
        GameInfo(
            id = GateRush.GAME_ID,
            title = "Gate Rush",
            tagline = "Ballern, Tore wählen, Linie halten",
            description = "Bewege deinen Helden nach links und rechts, er schießt automatisch. " +
                "Lauf durch die richtigen Tore, schieß Kisten auf, sammle Power-ups und weiche Hindernissen aus – " +
                "kein Gegner darf deine Linie erreichen. Tipp: Schüsse fliegen durch Tore und erhöhen ihren Wert.",
            accent = NeonPink,
            accentSecondary = NeonYellow,
            modes = listOf(
                ModeInfo(
                    id = GateRushMode.ENDLESS.id,
                    title = "Endless",
                    description = "Immer mehr und stärkere Gegner. Wie weit kommst du?",
                ),
                ModeInfo(
                    id = GateRushMode.BOSS_RUSH.id,
                    title = "Boss Rush",
                    description = "Rüsten, Boss besiegen, nächste Welle. Jeder Boss ist stärker als der letzte.",
                ),
                ModeInfo(
                    id = GateRushMode.DAILY.id,
                    title = "Tägliche Challenge",
                    description = "Jeden Tag ein neues, festes Level – gleicher Aufbau für alle. Eigener Tagesrekord.",
                    scoreKey = { DailyChallenge.scoreKey(GateRushMode.DAILY.id) },
                ),
            ),
            preview = { modifier -> GateRushPreview(modifier) },
        ),
        GameInfo(
            id = Survivors.GAME_ID,
            title = "Rune Survivors",
            tagline = "Überlebe die Horden",
            description = "Lauf mit dem Joystick durch die Welt – deine Waffen kämpfen von selbst. Sammle Seelenkristalle, " +
                "steige auf und kombiniere Waffen und Talente. Mit Runen schaltest du in der Runen-Schmiede neue Klassen frei.",
            accent = Color(0xFF16A34A),
            accentSecondary = Color(0xFFFACC15),
            modes = listOf(
                ModeInfo(
                    id = SurvivorsMode.TIMED.id,
                    title = "15 Minuten",
                    description = "Überlebe 15 Minuten und besiege den Endboss der Stage.",
                ),
                ModeInfo(
                    id = SurvivorsMode.ENDLESS.id,
                    title = "Endlos",
                    description = "Kein Ende, nur immer stärkere Horden. Wie lange hältst du durch?",
                ),
            ),
            preview = { modifier -> SurvivorsPreview(modifier) },
            leaderboardVariants = HeroClass.entries.map { LeaderboardVariant(it.shopId, it.title) },
            leaderboardGroups = SurvivorsStage.entries.map {
                LeaderboardVariant(if (it == SurvivorsStage.MEADOW) "" else it.id, it.title)
            },
            extraAction = GameAction("ᚱ  Runen-Schmiede", "survivors_forge"),
        ),
    )

    fun find(id: String?): GameInfo? = games.firstOrNull { it.id == id }
}
