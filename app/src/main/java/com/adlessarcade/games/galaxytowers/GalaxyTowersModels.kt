package com.adlessarcade.games.galaxytowers

import kotlinx.serialization.Serializable

/** Eine Spielkarte. [rank] 1 = Ass … 13 = König, 0 = Joker. [suit] 0 = Pik, 1 = Herz, 2 = Karo, 3 = Kreuz. */
@Serializable
data class Card(val rank: Int, val suit: Int) {
    val isJoker: Boolean get() = rank == JOKER_RANK
    val isRed: Boolean get() = suit == 1 || suit == 2

    val label: String
        get() = when (rank) {
            JOKER_RANK -> "★"
            1 -> "A"
            11 -> "J"
            12 -> "Q"
            13 -> "K"
            else -> rank.toString()
        }

    companion object {
        const val JOKER_RANK = 0
        val JOKER = Card(JOKER_RANK, 0)
    }
}

@Serializable
enum class PowerUp(val title: String, val symbol: String, val price: Int, val description: String) {
    JOKER("Joker", "★", 40, "Wildkarte auf die Ablage – jede Karte passt, die Kette läuft weiter."),
    UNDO("Zeitsprung", "↶", 20, "Nimmt den letzten Zug zurück."),
    SCAN("Scanner", "◎", 30, "Zeigt alle verdeckten Karten bis zum nächsten Zug."),
    WARP("Warp", "+3", 50, "Legt 3 neue Karten unter den Nachziehstapel."),
}

/** Warum eine Runde endete. */
@Serializable
enum class RoundEnd { CLEARED, TIME_UP, STUCK }

/** Zustand vor einem Zug – Grundlage für den Zeitsprung. */
@Serializable
data class Snapshot(
    val tower: List<Card?>,
    val waste: List<Card>,
    val stock: List<Card>,
    val score: Int,
    val streak: Int,
    val waste2: List<Card>? = null,
)

/**
 * Ein Lauf. [tower] hat [GalaxyTowersEngine.TOWER_SIZE] Plätze (null = abgeräumt), [waste] endet mit der obersten
 * Ablagekarte, [stock] beginnt mit der nächsten Karte. Alle Zufallswerte leiten sich aus [seed] ab.
 */
@Serializable
data class GameState(
    val seed: Long,
    val mode: GalaxyTowersMode = GalaxyTowersMode.CLASSIC,
    val round: Int = 1,
    val tower: List<Card?>,
    val waste: List<Card>,
    val stock: List<Card>,
    /** Zweite Ablage: öffnet sich ab einer 3er-Kette, verschwindet beim Nachziehen (null = gesperrt). */
    val waste2: List<Card>? = null,
    val score: Int = 0,
    val streak: Int = 0,
    val bestStreak: Int = 0,
    val charges: Map<PowerUp, Int> = PowerUp.entries.associateWith { 1 },
    val scanActive: Boolean = false,
    val history: List<Snapshot> = emptyList(),
    val coinsEarned: Int = 0,
    /** Restzeit der Runde in Sekunden (nur bei Modi mit Zeitlimit). */
    val timeLeft: Float = 0f,
    /** Gesetzt, sobald die Runde vorbei ist; die nächste wird erst auf Tipp ausgeteilt. */
    val roundOver: RoundEnd? = null,
    val roundsCleared: Int = 0,
    /** Anzahl genutzter Warps in dieser Runde (für deterministische Warp-Karten). */
    val warps: Int = 0,
    val isGameOver: Boolean = false,
) {
    val top: Card? get() = waste.lastOrNull()
    val top2: Card? get() = waste2?.lastOrNull()
    val isLastRound: Boolean get() = round >= mode.rounds
    /** Runde läuft: Züge, Power-ups und Zeit sind aktiv. */
    val inPlay: Boolean get() = !isGameOver && roundOver == null
    fun charges(p: PowerUp): Int = charges[p] ?: 0
}

/** Ergebnis eines gespielten Zugs für Punkte-Einblendungen. */
data class PlayResult(
    val state: GameState,
    /** 0 = Hauptablage, 1 = zweite Ablage. */
    val pile: Int,
    val points: Int,
    val peakCleared: Boolean,
    val roundCleared: Boolean,
    val clearBonus: Int,
    val timeBonus: Int,
    val coins: Int,
    val reward: PowerUp?,
    /** Die zweite Ablage hat sich mit diesem Zug geöffnet. */
    val secondPileOpened: Boolean,
    /** Gold (Coins), das die Karte dank der Kette abgeworfen hat; ist in [coins] enthalten. */
    val gold: Int = 0,
)
