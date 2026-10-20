package com.adlessarcade.core

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Tägliche Challenge: gleicher Seed für alle an einem Tag, eigener Rekord pro Tag, nur ein Lauf pro Tag. */
object DailyChallenge {
    const val APP_LINK = "https://github.com/fkleindev/Adless-Arcade"

    fun todayKey(date: LocalDate = LocalDate.now()): String = date.format(DateTimeFormatter.BASIC_ISO_DATE)

    fun todaySeed(date: LocalDate = LocalDate.now()): Long = todayKey(date).toLong()

    /** Schlüssel für Highscore und Spielstand eines Modus am heutigen Tag. */
    fun scoreKey(modeId: String, date: LocalDate = LocalDate.now()): String = "${modeId}_${todayKey(date)}"

    /** Tagesschlüssel (20261020) als lesbares Datum (20.10.2026). */
    fun formatDay(dayKey: String): String =
        runCatching {
            LocalDate.parse(dayKey, DateTimeFormatter.BASIC_ISO_DATE).format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
        }.getOrDefault(dayKey)

    /** Restzeit bis zur nächsten Challenge, z. B. „5 h 12 min“. */
    fun timeUntilNext(now: LocalDateTime = LocalDateTime.now()): String {
        val minutes = Duration.between(now, now.toLocalDate().plusDays(1).atStartOfDay()).toMinutes().coerceAtLeast(1)
        return if (minutes >= 60) "${minutes / 60} h ${minutes % 60} min" else "$minutes min"
    }

    /** Punktzahl mit Tausenderpunkt (12.340). */
    fun formatScore(score: Int): String = "%,d".format(Locale.GERMANY, score)

    /** Text für den Teilen-Dialog. */
    fun shareText(gameTitle: String, result: DailyResult): String = listOf(
        "🎮 Adless Arcade – $gameTitle Daily",
        "📅 ${formatDay(result.day)}",
        result.summary,
        "Schaffst du mehr?",
        APP_LINK,
    ).joinToString("\n")
}
