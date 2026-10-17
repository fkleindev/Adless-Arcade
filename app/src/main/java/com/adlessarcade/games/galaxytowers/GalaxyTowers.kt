package com.adlessarcade.games.galaxytowers

import kotlinx.serialization.Serializable

object GalaxyTowers {
    const val GAME_ID = "galaxytowers"
}

/**
 * Spielmodi wie im ersten Teil: [rounds] feste Runden, bei [timed] mit Rundenzeit.
 * Die Tägliche Challenge nutzt die Classic-Regeln mit festem Seed und ohne gekaufte Power-ups.
 */
@Serializable
enum class GalaxyTowersMode(val id: String, val rounds: Int, val timed: Boolean) {
    CLASSIC("classic", 10, true),
    TIMELESS("timeless", 10, false),
    SINGLE("single", 1, true),
    DAILY("daily", 10, true);

    val canBuy: Boolean get() = this != DAILY

    /** Sekunden für Runde [round] (1-basiert); 0 ohne Zeitlimit. */
    fun roundTime(round: Int): Float = when {
        !timed -> 0f
        this == SINGLE -> 80f
        else -> 90f - 5f * (round - 1)
    }

    companion object {
        fun fromId(id: String?): GalaxyTowersMode = entries.firstOrNull { it.id == id } ?: CLASSIC
    }
}
