package com.adlessarcade.games.gaterush

import com.adlessarcade.games.gaterush.GateRushEngine.Companion.FIRST_BOSS_TIME
import com.adlessarcade.games.gaterush.GateRushEngine.Companion.FIRST_OBSTACLE_TIME
import com.adlessarcade.games.gaterush.GateRushEngine.Companion.FRIENDLY_GATES_TIME
import com.adlessarcade.games.gaterush.GateRushEngine.Companion.MAX_X
import com.adlessarcade.games.gaterush.GateRushEngine.Companion.WARMUP_TIME
import com.adlessarcade.games.gaterush.GateRushEngine.Companion.Z_FAR
import kotlinx.serialization.Serializable
import kotlin.math.pow
import kotlin.random.Random

/** Alle Timer und Phasen des Spawners – wird im Spielstand gespeichert. */
@Serializable
data class SpawnerState(
    var enemyTimer: Float = 2.5f,
    var gateTimer: Float = 3f,
    var crateTimer: Float = 7f,
    var bossTimer: Float = FIRST_BOSS_TIME,
    var obstacleTimer: Float = FIRST_OBSTACLE_TIME,
    var pickupTimer: Float = 30f,
    /** Boss Rush: aktuelle Welle und ob gerade ein Boss kämpft (sonst Rüstphase). */
    var wave: Int = 1,
    var bossPhase: Boolean = false,
    var phaseTimer: Float = GateRushSpawner.ARM_TIME_FIRST,
)

/**
 * Baut das Level auf. Nutzt einen eigenen Zufallsgenerator, damit der Aufbau bei gleichem Seed (Tägliche Challenge)
 * unabhängig von Kampf-Zufällen wie Krit-Würfen ist.
 */
internal class GateRushSpawner(
    private val e: GateRushEngine,
    private val random: Random,
    val state: SpawnerState,
) {
    companion object {
        const val ARM_TIME_FIRST = 18f
        const val ARM_TIME = 12f
        private const val SPACING = 8f
    }

    /**
     * Tägliche Challenge: Der Aufbau darf nicht vom Spielverlauf abhängen (Upgrades, Bosskampf), sonst sähe
     * nicht jeder das gleiche Level. Dann wird mit Startwerten statt den echten Werten des Spielers gewürfelt.
     */
    private val fixedLayout = e.mode == GateRushMode.DAILY
    private val baseStats = PlayerStats()
    private val layoutStats: PlayerStats get() = if (fixedLayout) baseStats else e.stats

    fun update(dt: Float) {
        if (e.mode == GateRushMode.BOSS_RUSH) bossRush(dt) else endless(dt)
    }

    // --- Endless & Tägliche Challenge ---

    private fun endless(dt: Float) {
        val s = state
        val t = e.time
        val bossAlive = e.enemies.any { it.type == EnemyType.BOSS }

        s.bossTimer -= dt
        if (s.bossTimer <= 0f) {
            s.bossTimer = if (t > 240f) 45f else 60f
            // Der erste Boss ist schwächer, damit der Einstieg fair bleibt.
            val factor = if (t < FIRST_BOSS_TIME + 1f) 1f else 1.5f
            e.spawnEnemy(EnemyType.BOSS, 0f, hpMultiplier = e.hpMultiplier() * factor)
        }

        s.enemyTimer -= dt
        if (s.enemyTimer <= 0f) {
            val interval = e.spawnInterval()
            s.enemyTimer = if (bossAlive && !fixedLayout) interval * 1.6f else interval
            spawnWaveUnit(e.hpMultiplier(), t)
        }

        s.gateTimer -= dt
        if (s.gateTimer <= 0f) {
            if (obstacleNearSpawn()) {
                s.gateTimer = 1f
            } else {
                s.gateTimer = if (t < 60f) 4.5f else 6f
                spawnGateRow(friendly = t < FRIENDLY_GATES_TIME, positiveChance = 0.4f, laneTime = t)
            }
        }

        s.crateTimer -= dt
        if (s.crateTimer <= 0f) {
            s.crateTimer = 10f
            spawnCrate(hits = 8 + (t / 2f).toInt())
        }

        s.obstacleTimer -= dt
        if (s.obstacleTimer <= 0f) {
            if (gateNearSpawn()) {
                s.obstacleTimer = 1f
            } else {
                s.obstacleTimer = (10f - t / 40f).coerceAtLeast(4f) * (0.8f + 0.4f * random.nextFloat())
                spawnObstaclePattern(wallAllowed = t > 60f)
            }
        }

        s.pickupTimer -= dt
        if (s.pickupTimer <= 0f) {
            s.pickupTimer = 18f + random.nextFloat() * 7f
            spawnPickup()
        }
    }

    // --- Boss Rush: Rüstphase → Bosswelle → Rüstphase … ---

    /** HP-Faktor der Begleiter in Welle [wave]. */
    fun waveMultiplier(wave: Int): Float = 1.55f.pow(wave - 1) * (1f + 0.3f * (wave - 1))

    /** HP-Faktor des Bosses in Welle [wave]. */
    fun bossMultiplier(wave: Int): Float = 0.3f * 1.8f.pow(wave - 1) * (1f + 0.5f * (wave - 1))

    private fun bossRush(dt: Float) {
        val s = state
        // Gegnertypen schalten sich mit den Wellen frei, wie im Endless über die Zeit.
        val effectiveTime = 30f + s.wave * 30f
        if (!s.bossPhase) {
            s.phaseTimer -= dt
            s.gateTimer -= dt
            if (s.gateTimer <= 0f && !obstacleNearSpawn()) {
                s.gateTimer = 3f
                spawnGateRow(friendly = s.wave == 1, positiveChance = 0.6f, laneTime = effectiveTime)
            }
            s.crateTimer -= dt
            if (s.crateTimer <= 0f) {
                s.crateTimer = 5f
                spawnCrate(hits = 6 + s.wave * 4)
            }
            s.enemyTimer -= dt
            if (s.enemyTimer <= 0f) {
                s.enemyTimer = 1.6f
                e.spawnEnemy(EnemyType.RUNNER, randomX(), Z_FAR, hpMultiplier = waveMultiplier(s.wave))
            }
            s.obstacleTimer -= dt
            if (s.wave >= 2 && s.obstacleTimer <= 0f && !gateNearSpawn()) {
                s.obstacleTimer = 6f
                spawnObstaclePattern(wallAllowed = s.wave >= 3)
            }
            s.pickupTimer -= dt
            if (s.pickupTimer <= 0f) {
                s.pickupTimer = 15f
                spawnPickup()
            }
            if (s.phaseTimer <= 0f) {
                s.bossPhase = true
                s.enemyTimer = 2.5f
                e.spawnEnemy(EnemyType.BOSS, 0f, hpMultiplier = bossMultiplier(s.wave))
                e.emit(GateRushEvent.WaveStarted(s.wave))
            }
        } else {
            s.enemyTimer -= dt
            if (s.enemyTimer <= 0f) {
                s.enemyTimer = (3f - s.wave * 0.2f).coerceAtLeast(1.2f)
                spawnWaveUnit(waveMultiplier(s.wave), effectiveTime)
            }
            if (e.enemies.none { it.type == EnemyType.BOSS }) {
                s.bossPhase = false
                s.wave++
                s.phaseTimer = ARM_TIME
                s.gateTimer = 1f
                s.crateTimer = 2f
                s.enemyTimer = 2f
            }
        }
    }

    // --- Bausteine ---

    private fun randomX(): Float = random.nextFloat() * 2f * MAX_X - MAX_X

    // Genug Abstand zwischen Toren und Hindernissen, damit man nach der Torwahl noch ausweichen kann.
    private fun gateNearSpawn() = e.gates.any { it.z > Z_FAR - SPACING }
    private fun obstacleNearSpawn() = e.obstacles.any { it.z > Z_FAR - SPACING }

    /** Ein Gegner bzw. eine Gegnergruppe; [t] bestimmt, welche Typen schon vorkommen. */
    private fun spawnWaveUnit(hpMultiplier: Float, t: Float) {
        val x = randomX()
        val z = Z_FAR + random.nextFloat() * 2f
        if (t < WARMUP_TIME) {
            e.spawnEnemy(EnemyType.RUNNER, x, z, hpMultiplier)
            return
        }
        val roll = random.nextFloat()
        var threshold = (0.05f + (t - WARMUP_TIME) / 400f).coerceAtMost(0.35f)
        if (roll < threshold) {
            e.spawnEnemy(EnemyType.BRUTE, x, z, hpMultiplier)
            return
        }
        threshold += 0.18f
        if (roll < threshold) {
            repeat(4 + (t / 90f).toInt().coerceAtMost(4)) {
                e.spawnEnemy(
                    EnemyType.SWARM,
                    x + random.nextFloat() * 0.4f - 0.2f,
                    z + random.nextFloat() * 2f,
                    hpMultiplier,
                )
            }
            return
        }
        if (t > 40f) {
            threshold += 0.15f
            if (roll < threshold) {
                e.spawnEnemy(EnemyType.ZIGZAG, x.coerceIn(-0.55f, 0.55f), z, hpMultiplier, phase = random.nextFloat() * 6.28f)
                return
            }
        }
        if (t > 90f) {
            threshold += 0.1f
            if (roll < threshold) {
                e.spawnEnemy(EnemyType.SPLITTER, x, z, hpMultiplier)
                return
            }
        }
        if (t > 120f) {
            threshold += 0.12f
            if (roll < threshold) {
                e.spawnEnemy(EnemyType.SHIELDED, x, z, hpMultiplier)
                return
            }
        }
        e.spawnEnemy(EnemyType.RUNNER, x, z, hpMultiplier)
    }

    private fun spawnGateRow(friendly: Boolean, positiveChance: Float, laneTime: Float) {
        val laneCount = when {
            laneTime > 90f && random.nextFloat() < 0.35f -> 4
            random.nextFloat() < 0.15f -> 2
            else -> 3
        }
        val types = ArrayList<GateType>()
        repeat(laneCount) {
            var type = randomGateType()
            repeat(4) { if (type in types) type = randomGateType() }
            types += type
        }
        val goodLane = random.nextInt(laneCount)
        // Manche Reihen haben eine Lücke ohne Wirkung.
        val gapLane = if (!friendly && laneCount >= 3 && random.nextFloat() < 0.25f) {
            (goodLane + 1 + random.nextInt(laneCount - 1)) % laneCount
        } else {
            -1
        }
        // Gelegentlich eine Waffen-Spur.
        val weaponLane = if (laneTime > 30f && random.nextFloat() < 0.12f) {
            (0 until laneCount).filter { it != gapLane }.random(random)
        } else {
            -1
        }
        val lanes = types.mapIndexed { i, type ->
            when (i) {
                gapLane -> null
                weaponLane -> GateSide(GateType.WEAPON, 1, randomWeapon())
                else -> GateSide(
                    type,
                    randomGateValue(type, positive = friendly || i == goodLane || random.nextFloat() < positiveChance),
                )
            }
        }
        e.spawnGate(lanes)
    }

    private fun randomWeapon(): Weapon =
        Weapon.entries.filter { it != Weapon.BLASTER && it != layoutStats.weapon }.random(random)

    /**
     * Seltene Upgrades werden mit jeder erreichten Stufe seltener angeboten ([level] = aktuelle Stufe),
     * damit das Maximum nicht schon nach wenigen Minuten erreicht ist.
     */
    private fun offered(level: Int, max: Int, base: Float = 1f): Boolean =
        level < max && random.nextFloat() < base / (1f + level)

    private fun randomGateType(): GateType {
        val st = layoutStats
        val options = buildList {
            repeat(4) { add(GateType.DAMAGE) }
            repeat(3) { add(GateType.FIRE_RATE) }
            if (offered(st.multishot - 1, GateRushEngine.MAX_MULTISHOT - 1)) add(GateType.MULTISHOT)
            if (offered(st.pierce, GateRushEngine.MAX_PIERCE)) add(GateType.PIERCE)
            if (st.hearts < GateRushEngine.MAX_HEARTS) add(GateType.HEART)
            if (st.critChance < GateRushEngine.MAX_CRIT) repeat(2) { add(GateType.CRIT) }
            if (offered(st.splash / 2, GateRushEngine.MAX_SPLASH / 2)) add(GateType.SPLASH)
            if (offered(st.frost / 2, GateRushEngine.MAX_FROST / 2)) add(GateType.FROST)
            if (offered(st.shields, GateRushEngine.MAX_SHIELDS)) add(GateType.SHIELD)
            if (offered(st.drones, GateRushEngine.MAX_DRONES, base = 0.7f)) add(GateType.DRONE)
        }
        return options.random(random)
    }

    private fun randomGateValue(type: GateType, positive: Boolean): Int {
        val sign = if (positive) 1 else -1
        return when (type) {
            GateType.DAMAGE -> sign * random.nextInt(1, 3 + (e.time / 45f).toInt())
            GateType.FIRE_RATE -> sign * random.nextInt(5, 16)
            GateType.CRIT -> sign * random.nextInt(2, 6)
            GateType.HEART -> 1
            else -> sign
        }
    }

    private fun spawnCrate(hits: Int) {
        val st = layoutStats
        val options = CrateReward.entries.filter { reward ->
            when (reward) {
                CrateReward.MULTISHOT -> offered(st.multishot - 1, GateRushEngine.MAX_MULTISHOT - 1, base = 0.6f)
                CrateReward.FIRE_BOOST -> st.fireRate < GateRushEngine.MAX_FIRE_RATE
                CrateReward.HEART -> st.hearts < GateRushEngine.MAX_HEARTS
                CrateReward.PIERCE -> offered(st.pierce, GateRushEngine.MAX_PIERCE)
                CrateReward.CRIT -> st.critChance < GateRushEngine.MAX_CRIT
                CrateReward.SPLASH -> offered(st.splash / 2, GateRushEngine.MAX_SPLASH / 2)
                CrateReward.FROST -> offered(st.frost / 2, GateRushEngine.MAX_FROST / 2)
                CrateReward.SHIELD -> offered(st.shields, GateRushEngine.MAX_SHIELDS)
                CrateReward.DRONE -> offered(st.drones, GateRushEngine.MAX_DRONES, base = 0.7f)
                CrateReward.SHOTGUN -> st.weapon != Weapon.SHOTGUN
                CrateReward.LASER -> st.weapon != Weapon.LASER
                CrateReward.ROCKET -> st.weapon != Weapon.ROCKET
                CrateReward.DAMAGE_BOOST -> true
            }
        }
        // Waffen in Kisten seltener als normale Upgrades.
        val weighted = options.flatMap { r ->
            val weight = if (r == CrateReward.SHOTGUN || r == CrateReward.LASER || r == CrateReward.ROCKET) 1 else 3
            List(weight) { r }
        }
        e.spawnCrate(random.nextFloat() * 1.4f - 0.7f, hits, weighted.random(random))
    }

    private fun spawnObstaclePattern(wallAllowed: Boolean) {
        if (wallAllowed && random.nextFloat() < 0.3f) {
            // Mauer mit Durchgang: zwei Blöcke, dazwischen eine Lücke, durch die man muss.
            val gapCenter = random.nextFloat() * 1.2f - 0.6f
            val gapHalf = 0.22f
            val leftEnd = gapCenter - gapHalf
            val rightStart = gapCenter + gapHalf
            e.spawnObstacle((-1f + leftEnd) / 2f, (leftEnd + 1f) / 2f, ObstacleKind.BLOCK)
            e.spawnObstacle((rightStart + 1f) / 2f, (1f - rightStart) / 2f, ObstacleKind.BLOCK)
        } else {
            val halfWidth = 0.12f + random.nextFloat() * 0.08f
            val kind = if (random.nextBoolean()) ObstacleKind.BLOCK else ObstacleKind.SPIKES
            e.spawnObstacle(random.nextFloat() * 1.6f - 0.8f, halfWidth, kind)
        }
    }

    private fun spawnPickup() {
        val roll = random.nextFloat()
        val kind = when {
            roll < 0.4f -> PowerUpKind.FRENZY
            roll < 0.75f -> PowerUpKind.SLOWMO
            else -> PowerUpKind.BOMB
        }
        e.spawnPickup(random.nextFloat() * 1.4f - 0.7f, kind)
    }
}
