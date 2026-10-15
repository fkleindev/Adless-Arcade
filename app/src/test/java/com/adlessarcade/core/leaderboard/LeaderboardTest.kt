package com.adlessarcade.core.leaderboard

import com.adlessarcade.core.SaveJson
import kotlinx.serialization.builtins.ListSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LeaderboardTest {

    private fun entry(score: Int, time: Long = 0L) = LeaderboardEntry(score, time)

    @Test
    fun insert_sortsDescending_andReturnsRank() {
        var list = emptyList<LeaderboardEntry>()
        listOf(300, 100, 500, 200).forEach { list = Leaderboards.insert(list, entry(it)).first }
        assertEquals(listOf(500, 300, 200, 100), list.map { it.score })
        val (updated, rank) = Leaderboards.insert(list, entry(250))
        assertEquals(3, rank)
        assertEquals(listOf(500, 300, 250, 200, 100), updated.map { it.score })
    }

    @Test
    fun insert_tie_keepsOlderEntryFirst() {
        val list = listOf(entry(500, time = 1))
        val (updated, rank) = Leaderboards.insert(list, entry(500, time = 2))
        assertEquals(2, rank)
        assertEquals(listOf(1L, 2L), updated.map { it.timestamp })
    }

    @Test
    fun insert_keepsOnlyTopTen() {
        var list = emptyList<LeaderboardEntry>()
        (1..Leaderboards.SIZE).forEach { list = Leaderboards.insert(list, entry(it * 100)).first }
        assertEquals(Leaderboards.SIZE, list.size)

        // Zu schwach für die Liste
        val (same, noRank) = Leaderboards.insert(list, entry(50))
        assertNull(noRank)
        assertEquals(list, same)

        // Neuer Spitzenplatz verdrängt den letzten Eintrag
        val (top, rank) = Leaderboards.insert(list, entry(5000))
        assertEquals(1, rank)
        assertEquals(Leaderboards.SIZE, top.size)
        assertEquals(5000, top.first().score)
        assertEquals(200, top.last().score)
    }

    @Test
    fun insert_ignoresZeroScores() {
        assertNull(Leaderboards.insert(emptyList(), entry(0)).second)
    }

    @Test
    fun keys_separateModesAndVariants() {
        val keys = listOf(
            LeaderboardKey("survivors", "timed", "class_knight"),
            LeaderboardKey("survivors", "timed", "class_mage"),
            LeaderboardKey("survivors", "endless", "class_knight"),
            LeaderboardKey("gaterush", "endless"),
        ).map { it.storageKey }
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun appVersion_isStored_andOldEntriesStillLoad() {
        val serializer = ListSerializer(LeaderboardEntry.serializer())
        val list = listOf(LeaderboardEntry(100, 1L, appVersion = "1.15"))
        assertEquals(list, SaveJson.decodeFromString(serializer, SaveJson.encodeToString(serializer, list)))
        val old = SaveJson.decodeFromString(serializer, """[{"score":50,"timestamp":2,"details":"x"}]""")
        assertNull(old.single().appVersion)
    }
}
