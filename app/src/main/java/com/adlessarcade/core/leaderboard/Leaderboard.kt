package com.adlessarcade.core.leaderboard

import kotlinx.serialization.Serializable

/** Ein Eintrag einer lokalen Bestenliste. */
@Serializable
data class LeaderboardEntry(
    val score: Int,
    /** Zeitpunkt des Laufs (Epoch-Millisekunden); 0 = unbekannt (übernommener alter Rekord). */
    val timestamp: Long,
    /** Kurze Zusatzinfo, z. B. „Welle 7 · 3:12“. */
    val details: String? = null,
    /** Lauf gewonnen (Rune Survivors, 15-Minuten-Modus). */
    val victory: Boolean = false,
)

/**
 * Eine Bestenliste, z. B. „Rune Survivors · 15 Minuten · Magierin“.
 * [variant] unterteilt einen Modus weiter (bei Rune Survivors die Klasse), sonst null.
 */
data class LeaderboardKey(val gameId: String, val modeId: String, val variant: String? = null) {
    val storageKey: String get() = listOfNotNull("lb", gameId, modeId, variant).joinToString("_")
}

/** Unterteilung eines Modus (z. B. Klasse) für Bestenliste und Auswahl-Chips. */
data class LeaderboardVariant(val id: String, val title: String)

object Leaderboards {
    const val SIZE = 10

    /**
     * Fügt [entry] ein und liefert die neue Liste samt Platz (1-basiert) oder null, wenn der Eintrag nicht in die
     * Top [SIZE] kommt. Bei Gleichstand steht der ältere Eintrag vorne.
     */
    fun insert(entries: List<LeaderboardEntry>, entry: LeaderboardEntry): Pair<List<LeaderboardEntry>, Int?> {
        if (entry.score <= 0) return entries to null
        val index = entries.indexOfFirst { it.score < entry.score }.let { if (it < 0) entries.size else it }
        if (index >= SIZE) return entries to null
        val updated = (entries.take(index) + entry + entries.drop(index)).take(SIZE)
        return updated to index + 1
    }
}
