package com.adlessarcade.core

import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Tägliche Challenge: gleicher Seed für alle an einem Tag, eigener Rekord pro Tag. */
object DailyChallenge {
    fun todayKey(date: LocalDate = LocalDate.now()): String = date.format(DateTimeFormatter.BASIC_ISO_DATE)

    fun todaySeed(date: LocalDate = LocalDate.now()): Long = todayKey(date).toLong()

    /** Schlüssel für Highscore und Spielstand eines Modus am heutigen Tag. */
    fun scoreKey(modeId: String, date: LocalDate = LocalDate.now()): String = "${modeId}_${todayKey(date)}"
}
