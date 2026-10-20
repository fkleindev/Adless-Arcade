package com.adlessarcade.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class DailyChallengeTest {

    @Test
    fun shareText_containsGameDateResultAndLink() {
        val result = DailyResult("20261020", 12340, "🏆 ${DailyChallenge.formatScore(12340)} Punkte · ⏱ 3:42")
        assertEquals(
            listOf(
                "🎮 Adless Arcade – Gate Rush Daily",
                "📅 20.10.2026",
                "🏆 12.340 Punkte · ⏱ 3:42",
                "Schaffst du mehr?",
                "https://github.com/fkleindev/Adless-Arcade",
            ).joinToString("\n"),
            DailyChallenge.shareText("Gate Rush", result),
        )
    }

    @Test
    fun timeUntilNext_countsToMidnight() {
        assertEquals("5 h 12 min", DailyChallenge.timeUntilNext(LocalDateTime.of(2026, 10, 20, 18, 48)))
        assertEquals("1 min", DailyChallenge.timeUntilNext(LocalDateTime.of(2026, 10, 20, 23, 59, 30)))
    }

    @Test
    fun dayKey_roundTrips() {
        val key = DailyChallenge.todayKey(LocalDate.of(2026, 1, 5))
        assertEquals("20260105", key)
        assertEquals("05.01.2026", DailyChallenge.formatDay(key))
    }

    @Test
    fun result_serializes() {
        val result = DailyResult("20261020", 14, "🏆 Runde 14 · ⭐ Level 7")
        val json = SaveJson.encodeToString(DailyResult.serializer(), result)
        assertEquals(result, SaveJson.decodeFromString(DailyResult.serializer(), json))
    }
}
