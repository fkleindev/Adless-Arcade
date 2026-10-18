package com.adlessarcade.games.runetactics

import com.adlessarcade.core.SaveJson
import kotlinx.serialization.Serializable

@Serializable
data class BoardEntry(val col: Int, val row: Int, val unit: UnitInstance)

@Serializable
data class RuneTacticsSnapshot(
    val round: Int,
    val hp: Int,
    val gold: Int,
    val level: Int,
    val xp: Int,
    val winStreak: Int,
    val lossStreak: Int,
    val coinsEarned: Int,
    val board: List<BoardEntry>,
    val bench: List<UnitInstance?>,
    val shop: List<String?>,
    val pool: Map<String, Int>,
    val enemyArmy: List<PlacedUnit>,
    val nextUid: Long,
    val rngCounter: Long,
    /** true: Kampf lief bereits – nach dem Laden wird er mit derselben Aufstellung neu gestartet. */
    val inCombat: Boolean = false,
)

@Serializable
data class RuneTacticsSave(
    val version: Int,
    val snapshot: RuneTacticsSnapshot,
    val bestAtStart: Int = 0,
    val seed: Long = 0,
)

object RuneTacticsSaves {
    const val VERSION = 1

    fun encode(snapshot: RuneTacticsSnapshot, bestAtStart: Int, seed: Long): String =
        SaveJson.encodeToString(RuneTacticsSave.serializer(), RuneTacticsSave(VERSION, snapshot, bestAtStart, seed))

    /** Liefert null bei defekten oder inkompatiblen Spielständen. */
    fun decode(json: String): RuneTacticsSave? {
        val save = runCatching { SaveJson.decodeFromString(RuneTacticsSave.serializer(), json) }.getOrNull() ?: return null
        if (save.version != VERSION || save.snapshot.hp <= 0) return null
        // Unbekannte Einheiten (z. B. nach Umbenennung) machen den Stand unbrauchbar.
        val ids = save.snapshot.board.map { it.unit.defId } + save.snapshot.bench.mapNotNull { it?.defId } +
            save.snapshot.shop.filterNotNull() + save.snapshot.enemyArmy.map { it.defId }
        return if (ids.all { RuneTacticsUnits.find(it) != null }) save else null
    }
}
