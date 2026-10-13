package com.adlessarcade.games.gaterush

import com.adlessarcade.core.SaveJson
import kotlinx.serialization.Serializable

@Serializable
data class StatsSnapshot(
    val damage: Float,
    val fireRate: Float,
    val multishot: Int,
    val pierce: Int,
    val hearts: Int,
)

@Serializable
data class EnemySnapshot(val id: Long, val type: EnemyType, val x: Float, val z: Float, val hp: Float, val maxHp: Float)

@Serializable
data class GateSideSnapshot(val type: GateType, val baseValue: Int, val hits: Int)

@Serializable
data class GateSnapshot(val z: Float, val left: GateSideSnapshot?, val right: GateSideSnapshot?)

@Serializable
data class CrateSnapshot(val x: Float, val z: Float, val maxHits: Int, val hitsLeft: Int, val reward: CrateReward)

@Serializable
data class GateRushSnapshot(
    val playerX: Float,
    val time: Float,
    val distance: Float,
    val killScore: Int,
    val nextId: Long,
    val fireCooldown: Float,
    val enemyTimer: Float,
    val gateTimer: Float,
    val crateTimer: Float,
    val bossTimer: Float,
    val stats: StatsSnapshot,
    val enemies: List<EnemySnapshot>,
    val gates: List<GateSnapshot>,
    val crates: List<CrateSnapshot>,
)

/** Ein unterbrochener Lauf inkl. Rekord zu Rundenbeginn (für "Neuer Rekord!" nach dem Fortsetzen). */
@Serializable
data class GateRushSave(val version: Int, val snapshot: GateRushSnapshot, val bestAtStart: Int = 0)

object GateRushSaves {
    const val VERSION = 1

    fun encode(snapshot: GateRushSnapshot, bestAtStart: Int): String =
        SaveJson.encodeToString(GateRushSave.serializer(), GateRushSave(VERSION, snapshot, bestAtStart))

    /** Liefert null bei defekten oder inkompatiblen Spielständen. */
    fun decode(json: String): GateRushSave? {
        val save = runCatching { SaveJson.decodeFromString(GateRushSave.serializer(), json) }.getOrNull() ?: return null
        return if (save.version == VERSION && save.snapshot.stats.hearts > 0) save else null
    }
}
