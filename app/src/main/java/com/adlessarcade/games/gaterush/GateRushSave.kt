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
    val critChance: Float = 0f,
    val splash: Int = 0,
    val frost: Int = 0,
    val shields: Int = 0,
    val drones: Int = 0,
    val weapon: Weapon = Weapon.BLASTER,
    val frenzy: Float = 0f,
    val slowmo: Float = 0f,
)

@Serializable
data class EnemySnapshot(
    val id: Long,
    val type: EnemyType,
    val x: Float,
    val z: Float,
    val hp: Float,
    val maxHp: Float,
    val speedFactor: Float = 1f,
    val slowTimer: Float = 0f,
    val baseX: Float = x,
    val phase: Float = 0f,
)

@Serializable
data class GateSideSnapshot(val type: GateType, val baseValue: Int, val hits: Int, val weapon: Weapon? = null)

@Serializable
data class GateSnapshot(val id: Long, val z: Float, val lanes: List<GateSideSnapshot?>)

@Serializable
data class CrateSnapshot(val x: Float, val z: Float, val maxHits: Int, val hitsLeft: Int, val reward: CrateReward)

@Serializable
data class ObstacleSnapshot(val id: Long, val x: Float, val z: Float, val halfWidth: Float, val kind: ObstacleKind)

@Serializable
data class PickupSnapshot(val id: Long, val x: Float, val z: Float, val kind: PowerUpKind)

@Serializable
data class GateRushSnapshot(
    val playerX: Float,
    val time: Float,
    val distance: Float,
    val killScore: Int,
    val coinsEarned: Int = 0,
    val nextId: Long,
    val fireCooldown: Float,
    val droneCooldown: Float = 0f,
    val spawner: SpawnerState,
    val stats: StatsSnapshot,
    val enemies: List<EnemySnapshot>,
    val gates: List<GateSnapshot>,
    val crates: List<CrateSnapshot>,
    val obstacles: List<ObstacleSnapshot> = emptyList(),
    val pickups: List<PickupSnapshot> = emptyList(),
)

/** Ein unterbrochener Lauf inkl. Rekord zu Rundenbeginn und Seed (für die Tägliche Challenge). */
@Serializable
data class GateRushSave(
    val version: Int,
    val snapshot: GateRushSnapshot,
    val bestAtStart: Int = 0,
    val seed: Long = 0,
)

object GateRushSaves {
    /** Version 3: Hindernisse, Power-ups, Waffen, Modi. Ältere Spielstände werden verworfen. */
    const val VERSION = 3

    fun encode(snapshot: GateRushSnapshot, bestAtStart: Int, seed: Long): String =
        SaveJson.encodeToString(GateRushSave.serializer(), GateRushSave(VERSION, snapshot, bestAtStart, seed))

    /** Liefert null bei defekten oder inkompatiblen Spielständen. */
    fun decode(json: String): GateRushSave? {
        val save = runCatching { SaveJson.decodeFromString(GateRushSave.serializer(), json) }.getOrNull() ?: return null
        return if (save.version == VERSION && save.snapshot.stats.hearts > 0) save else null
    }
}
