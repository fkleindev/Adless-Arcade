package com.adlessarcade.games.gaterush

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class GateRushEngineTest {

    private fun engine(autoSpawn: Boolean = false, mode: GateRushMode = GateRushMode.ENDLESS, seed: Long = 7) =
        GateRushEngine(seed, mode, autoSpawn)

    private fun GateRushEngine.run(seconds: Float) {
        var t = 0f
        while (t < seconds) {
            update(0.05f)
            t += 0.05f
        }
    }

    // --- Grundlagen ---

    @Test
    fun bullet_killsEnemyInFront() {
        val e = engine()
        e.stats.damage = 5f
        e.spawnEnemy(EnemyType.RUNNER, x = 0f, z = 8f)
        e.run(1f)
        assertTrue(e.enemies.isEmpty())
        assertEquals(EnemyType.RUNNER.score, e.killScore)
        assertEquals(GateRushEngine.START_HEARTS, e.stats.hearts)
        assertTrue(e.drainEvents().any { it is GateRushEvent.EnemyKilled })
    }

    @Test
    fun enemyReachingLine_costsHeart() {
        val e = engine()
        e.movePlayer(-1f)
        e.spawnEnemy(EnemyType.RUNNER, x = 0.9f, z = 2f)
        e.run(1f)
        assertEquals(GateRushEngine.START_HEARTS - 1, e.stats.hearts)
        assertTrue(e.drainEvents().contains(GateRushEvent.HeartsLost(1)))
        assertFalse(e.isGameOver)
    }

    @Test
    fun losingAllHearts_endsGame() {
        val e = engine()
        e.movePlayer(-1f)
        e.spawnEnemy(EnemyType.BRUTE, x = 0.9f, z = 1f)
        e.spawnEnemy(EnemyType.RUNNER, x = 0.9f, z = 1.5f)
        e.run(1f)
        assertEquals(0, e.stats.hearts)
        assertTrue(e.isGameOver)
    }

    @Test
    fun boss_costsThreeHearts_andIgnoresShields() {
        val e = engine()
        e.stats.hearts = 5
        e.stats.shields = 2
        e.movePlayer(-1f)
        e.spawnEnemy(EnemyType.BOSS, x = 0.9f, z = 1f)
        e.run(1f)
        assertEquals(2, e.stats.hearts)
        assertEquals(2, e.stats.shields)
    }

    // --- Tore ---

    @Test
    fun passingGate_appliesLaneWherePlayerStands() {
        val e = engine()
        e.movePlayer(-1f)
        e.spawnGate(listOf(GateSide(GateType.DAMAGE, 3), GateSide(GateType.MULTISHOT, 1)), z = 1f)
        e.run(0.5f)
        assertEquals(4f, e.stats.damage, 0.001f)
        assertEquals(1, e.stats.multishot)
        assertTrue(e.gates.isEmpty())
    }

    @Test
    fun threeLaneGate_appliesMiddleLaneWhenCentered() {
        val e = engine()
        val lanes = listOf(GateSide(GateType.PIERCE, 1), GateSide(GateType.MULTISHOT, 1), GateSide(GateType.HEART, 1))
        val row = e.spawnGate(lanes, z = 1f)
        assertEquals(1, row.laneAt(0f))
        assertEquals(0, row.laneAt(-0.9f))
        assertEquals(2, row.laneAt(0.9f))
        e.run(0.5f)
        assertEquals(2, e.stats.multishot)
        assertEquals(0, e.stats.pierce)
    }

    @Test
    fun gapLane_hasNoEffect() {
        val e = engine()
        e.spawnGate(listOf(GateSide(GateType.DAMAGE, 5), null, GateSide(GateType.DAMAGE, 5)), z = 1f)
        e.run(0.5f)
        assertEquals(1f, e.stats.damage, 0.001f)
        assertTrue(e.drainEvents().none { it is GateRushEvent.GatePassed })
    }

    @Test
    fun weaponLane_switchesWeapon() {
        val e = engine()
        e.spawnGate(listOf(GateSide(GateType.WEAPON, 1, Weapon.LASER)), z = 1f)
        e.run(0.5f)
        assertEquals(Weapon.LASER, e.stats.weapon)
    }

    @Test
    fun bullets_passThroughGates_andStillHitEnemiesBehind() {
        val e = engine()
        e.stats.damage = 50f
        val row = e.spawnGate(listOf(GateSide(GateType.DAMAGE, 1), GateSide(GateType.DAMAGE, 1)), z = 4f)
        e.spawnEnemy(EnemyType.RUNNER, x = 0f, z = 12f)
        e.run(0.4f)
        assertTrue(row.lanes.sumOf { it!!.hits } > 0)
        assertTrue(e.enemies.isEmpty())
        assertTrue(row.lanes.sumOf { it!!.hits } <= 3)
    }

    @Test
    fun shootingGate_increasesItsValue() {
        val e = engine()
        e.movePlayer(0.5f)
        val gate = e.spawnGate(listOf(null, GateSide(GateType.FIRE_RATE, -10)), z = 12f)
        e.run(1f)
        val side = gate.lanes[1]!!
        assertTrue(side.hits > 0)
        assertTrue(side.value > -10)
    }

    @Test
    fun gateValue_eachStepCostsMoreHits() {
        val side = GateSide(GateType.DAMAGE, -1)
        val per = GateType.DAMAGE.hitsPerStep
        side.hits = per
        assertEquals(0, side.value)
        side.hits = per + 2 * per - 1
        assertEquals(0, side.value)
        side.hits = per + 2 * per
        assertEquals(1, side.value)
        assertEquals("+1", side.valueText)
    }

    @Test
    fun rareUpgrades_canOnlyBeShotUpOneStep() {
        val multi = GateSide(GateType.MULTISHOT, 1)
        multi.hits = 100_000
        assertEquals(2, multi.value)
        val damage = GateSide(GateType.DAMAGE, 1)
        damage.hits = 100_000
        assertEquals(1 + GateSide.MAX_SHOT_STEPS, damage.value)
        val weapon = GateSide(GateType.WEAPON, 1, Weapon.ROCKET)
        weapon.hits = 100_000
        assertEquals(1, weapon.value)
    }

    @Test
    fun negativeGates_respectLowerBounds() {
        val e = engine()
        e.applyGate(GateSide(GateType.DAMAGE, -10))
        e.applyGate(GateSide(GateType.MULTISHOT, -3))
        e.applyGate(GateSide(GateType.FIRE_RATE, -99))
        assertEquals(1f, e.stats.damage, 0.001f)
        assertEquals(1, e.stats.multishot)
        assertEquals(GateRushEngine.MIN_FIRE_RATE, e.stats.fireRate, 0.001f)
    }

    @Test
    fun upgrades_respectNewCaps() {
        val e = engine()
        repeat(20) {
            e.applyGate(GateSide(GateType.MULTISHOT, 1))
            e.applyGate(GateSide(GateType.DRONE, 1))
            e.applyGate(GateSide(GateType.SHIELD, 1))
            e.applyGate(GateSide(GateType.CRIT, 10))
            e.applyGate(GateSide(GateType.SPLASH, 1))
        }
        assertEquals(GateRushEngine.MAX_MULTISHOT, e.stats.multishot)
        assertEquals(GateRushEngine.MAX_DRONES, e.stats.drones)
        assertEquals(GateRushEngine.MAX_SHIELDS, e.stats.shields)
        assertEquals(GateRushEngine.MAX_CRIT, e.stats.critChance, 0.001f)
        assertEquals(GateRushEngine.MAX_SPLASH, e.stats.splash)
        assertEquals(4, e.dronePositions().size)
    }

    @Test
    fun earlyGates_areAllPositive() {
        val e = engine(autoSpawn = true)
        e.stats.hearts = 1_000
        val seen = ArrayList<GateRow>()
        while (e.time < GateRushEngine.FRIENDLY_GATES_TIME - 1f && !e.isGameOver) {
            e.update(0.05f)
            e.gates.filter { it !in seen }.forEach { seen += it }
        }
        assertTrue(seen.isNotEmpty())
        assertTrue(seen.all { row -> row.lanes.all { it == null || it.baseValue > 0 } })
    }

    // --- Hindernisse ---

    @Test
    fun obstacle_blocksBullets() {
        val e = engine()
        e.stats.damage = 50f
        e.spawnObstacle(x = 0f, halfWidth = 0.2f, kind = ObstacleKind.BLOCK, z = 4f)
        val enemy = e.spawnEnemy(EnemyType.BRUTE, x = 0f, z = 14f)
        e.run(0.3f)
        assertEquals(enemy.maxHp, enemy.hp, 0.001f)
    }

    @Test
    fun obstacle_costsHeart_orShield() {
        val e = engine()
        e.spawnObstacle(x = 0f, halfWidth = 0.2f, kind = ObstacleKind.SPIKES, z = 1f)
        e.run(0.5f)
        assertEquals(GateRushEngine.START_HEARTS - 1, e.stats.hearts)
        assertTrue(e.obstacles.isEmpty())
        assertTrue(e.drainEvents().contains(GateRushEvent.ObstacleHit))

        e.stats.shields = 1
        e.spawnObstacle(x = 0f, halfWidth = 0.2f, kind = ObstacleKind.BLOCK, z = 1f)
        e.run(0.5f)
        assertEquals(GateRushEngine.START_HEARTS - 1, e.stats.hearts)
        assertEquals(0, e.stats.shields)
    }

    @Test
    fun obstacle_canBeDodged() {
        val e = engine()
        e.movePlayer(-1f)
        e.spawnObstacle(x = 0.5f, halfWidth = 0.2f, kind = ObstacleKind.BLOCK, z = 1f)
        e.run(0.6f)
        assertEquals(GateRushEngine.START_HEARTS, e.stats.hearts)
        assertTrue(e.obstacles.isEmpty())
    }

    // --- Neue Gegner ---

    @Test
    fun shieldedEnemy_takesReducedDamage_exceptFromArmorPiercing() {
        val blaster = engine()
        blaster.stats.damage = 4f
        val a = blaster.spawnEnemy(EnemyType.SHIELDED, x = 0f, z = 5f)
        blaster.run(0.2f)
        assertTrue(a.hp > a.maxHp - 4f)

        val rocket = engine()
        rocket.stats.damage = 4f
        rocket.stats.weapon = Weapon.ROCKET
        val b = rocket.spawnEnemy(EnemyType.SHIELDED, x = 0f, z = 3f)
        rocket.run(0.25f)
        assertTrue(b.hp <= b.maxHp - 4f * Weapon.ROCKET.damageFactor + 0.01f)
    }

    @Test
    fun splitter_splitsIntoThree() {
        val e = engine()
        e.stats.damage = 100f
        e.spawnEnemy(EnemyType.SPLITTER, x = 0f, z = 8f)
        // Genau ein Schuss trifft – danach sofort auswerten.
        e.stats.fireRate = GateRushEngine.MIN_FIRE_RATE
        var splitlings = 0
        repeat(40) {
            e.update(0.01f)
            splitlings = maxOf(splitlings, e.enemies.count { it.type == EnemyType.SPLITLING })
        }
        assertEquals(3, splitlings)
    }

    @Test
    fun zigzag_movesSideways() {
        val e = engine()
        e.movePlayer(-1f)
        val z = e.spawnEnemy(EnemyType.ZIGZAG, x = 0.3f, z = 30f)
        val xs = ArrayList<Float>()
        repeat(20) {
            e.update(0.1f)
            xs += z.x
        }
        assertTrue(xs.max() - xs.min() > 0.3f)
    }

    // --- Waffen ---

    @Test
    fun weapons_fireAsDefined() {
        fun bulletsAfterFirstShot(weapon: Weapon): List<Bullet> {
            val e = engine()
            e.stats.weapon = weapon
            e.update(1f / 120f)
            return e.bullets.toList()
        }
        assertEquals(1, bulletsAfterFirstShot(Weapon.BLASTER).size)
        assertEquals(1 + Weapon.SHOTGUN.extraPellets, bulletsAfterFirstShot(Weapon.SHOTGUN).size)
        assertEquals(Weapon.LASER.extraPierce, bulletsAfterFirstShot(Weapon.LASER).first().pierceLeft)
        assertEquals(Weapon.ROCKET.damageFactor, bulletsAfterFirstShot(Weapon.ROCKET).first().damage, 0.001f)
    }

    @Test
    fun shotgun_hasShortRange() {
        val e = engine()
        e.stats.weapon = Weapon.SHOTGUN
        e.stats.damage = 100f
        val far = e.spawnEnemy(EnemyType.BRUTE, x = 0f, z = 28f)
        e.run(0.4f)
        assertEquals(far.maxHp, far.hp, 0.001f)
    }

    @Test
    fun rocket_explodesOnImpact() {
        val e = engine()
        e.stats.weapon = Weapon.ROCKET
        e.stats.damage = 5f
        e.spawnEnemy(EnemyType.BRUTE, x = 0f, z = 4f)
        val neighbour = e.spawnEnemy(EnemyType.RUNNER, x = 0.4f, z = 4f)
        e.run(0.3f)
        assertTrue(neighbour.hp < neighbour.maxHp || neighbour !in e.enemies)
    }

    // --- Power-ups ---

    @Test
    fun frenzy_doublesFireRate() {
        fun shotsIn(seconds: Float, frenzy: Boolean): Int {
            val e = engine()
            if (frenzy) e.spawnPickup(x = 0f, kind = PowerUpKind.FRENZY, z = 0.2f)
            var shots = 0
            var t = 0f
            while (t < seconds) {
                val before = e.bullets.size
                e.update(1f / 120f)
                shots += (e.bullets.size - before).coerceAtLeast(0)
                t += 1f / 120f
            }
            return shots
        }
        assertTrue(shotsIn(1f, frenzy = true) >= shotsIn(1f, frenzy = false) * 2 - 2)
    }

    @Test
    fun slowmo_slowsEnemies() {
        val normal = engine()
        val a = normal.spawnEnemy(EnemyType.RUNNER, x = 0.9f, z = 30f)
        normal.movePlayer(-1f)
        normal.run(1f)

        val slow = engine()
        slow.spawnPickup(x = 0f, kind = PowerUpKind.SLOWMO, z = 0.2f)
        val b = slow.spawnEnemy(EnemyType.RUNNER, x = 0.9f, z = 30f)
        slow.run(0.1f)
        slow.movePlayer(-1f)
        slow.run(0.9f)
        assertTrue(30f - b.z < (30f - a.z) * 0.7f)
    }

    @Test
    fun bomb_clearsNonBosses_andHurtsBoss() {
        val e = engine()
        e.movePlayer(-1f)
        repeat(5) { e.spawnEnemy(EnemyType.RUNNER, x = 0.8f, z = 20f + it) }
        val boss = e.spawnEnemy(EnemyType.BOSS, x = 0.5f, z = 25f)
        e.spawnPickup(x = -0.9f, kind = PowerUpKind.BOMB, z = 0.2f)
        e.update(0.05f)
        assertEquals(listOf(boss), e.enemies)
        assertTrue(boss.hp <= boss.maxHp * 0.75f + 0.01f)
        assertTrue(e.drainEvents().contains(GateRushEvent.PowerUpCollected(PowerUpKind.BOMB)))
    }

    @Test
    fun missedPickup_disappears() {
        val e = engine()
        e.movePlayer(-1f)
        e.spawnPickup(x = 0.8f, kind = PowerUpKind.FRENZY, z = 1f)
        e.run(0.5f)
        assertTrue(e.pickups.isEmpty())
        assertEquals(0f, e.stats.frenzy, 0.001f)
    }

    // --- Modi ---

    @Test
    fun bossRush_alternatesArmingAndBossPhases() {
        val e = engine(autoSpawn = true, mode = GateRushMode.BOSS_RUSH)
        e.stats.hearts = 1_000
        e.stats.damage = 10_000f
        e.stats.multishot = GateRushEngine.MAX_MULTISHOT
        assertFalse(e.bossPhase)
        var sawBoss = false
        var t = 0f
        while (t < 60f && e.wave < 2) {
            // Einzelschritte, sonst stirbt der Boss innerhalb eines einzigen Updates.
            e.update(1f / 120f)
            t += 1f / 120f
            if (e.bossPhase) sawBoss = true
        }
        assertTrue(sawBoss)
        assertEquals(2, e.wave)
        assertFalse(e.bossPhase)
        assertTrue(e.drainEvents().any { it is GateRushEvent.WaveStarted })
    }

    @Test
    fun daily_sameSeedGivesSameLayout_evenWithDifferentCombat() {
        fun layout(critChance: Float): List<String> {
            val e = engine(autoSpawn = true, mode = GateRushMode.DAILY, seed = 20261014)
            e.stats.hearts = 1_000
            e.stats.critChance = critChance
            e.stats.damage = 3f
            val seenGates = LinkedHashMap<Long, String>()
            repeat(600) {
                e.update(0.05f)
                e.gates.forEach { row ->
                    seenGates.getOrPut(row.id) { row.lanes.joinToString { l -> "${l?.type}${l?.baseValue}" } }
                }
            }
            return seenGates.values.toList()
        }
        assertEquals(layout(0f), layout(0.5f))
    }

    // --- Schwierigkeit, Coins, Spielstand ---

    @Test
    fun lateGame_isMuchHarderThanStart() {
        val e = engine()
        val early = e.spawnEnemy(EnemyType.RUNNER, x = 0.9f, z = 500f).maxHp
        e.movePlayer(-1f)
        e.stats.hearts = 1_000
        e.run(480f)
        assertTrue(e.hpMultiplier() > 300f)
        assertTrue(e.spawnInterval() < 0.4f)
        assertTrue(e.spawnEnemy(EnemyType.RUNNER, x = 0.9f, z = 500f).maxHp > early * 300f)
    }

    @Test
    fun killingBoss_andOpeningCrate_earnCoins() {
        val e = engine()
        e.stats.damage = 10_000f
        e.spawnEnemy(EnemyType.BOSS, x = 0f, z = 6f)
        e.run(0.5f)
        assertEquals(GateRushEngine.BOSS_COINS, e.coinsEarned)

        e.spawnCrate(x = 0f, hits = 1, reward = CrateReward.HEART, z = 6f)
        e.run(0.5f)
        assertEquals(GateRushEngine.BOSS_COINS + GateRushEngine.CRATE_COINS, e.coinsEarned)
        val coinEvents = e.drainEvents().filterIsInstance<GateRushEvent.CoinsEarned>()
        assertEquals(e.coinsEarned, coinEvents.sumOf { it.amount })
    }

    @Test
    fun crate_opensAfterEnoughHits() {
        val e = engine()
        e.spawnCrate(x = 0f, hits = 3, reward = CrateReward.MULTISHOT, z = 10f)
        e.run(1.5f)
        assertEquals(2, e.stats.multishot)
        assertTrue(e.crates.isEmpty())
        assertTrue(e.drainEvents().any { it is GateRushEvent.CrateOpened })
    }

    @Test
    fun crate_canGiveWeapon() {
        val e = engine()
        e.spawnCrate(x = 0f, hits = 1, reward = CrateReward.ROCKET, z = 4f)
        e.run(0.5f)
        assertEquals(Weapon.ROCKET, e.stats.weapon)
    }

    @Test
    fun save_roundTripRestoresIdenticalSnapshot() {
        val e = engine(autoSpawn = true)
        e.stats.hearts = 99
        e.stats.weapon = Weapon.LASER
        e.stats.frenzy = 3f
        e.spawnObstacle(0.5f, 0.2f, ObstacleKind.SPIKES, z = 20f)
        e.spawnPickup(-0.5f, PowerUpKind.BOMB, z = 25f)
        e.movePlayer(0.3f)
        e.run(20f)
        val snapshot = e.snapshot()
        val decoded = assertNotNullAndGet(GateRushSaves.decode(GateRushSaves.encode(snapshot, bestAtStart = 7, seed = 42)))
        assertEquals(7, decoded.bestAtStart)
        assertEquals(42L, decoded.seed)

        val restored = GateRushEngine(seed = 99)
        restored.restore(decoded.snapshot)
        assertEquals(snapshot, restored.snapshot())
        assertEquals(e.score, restored.score)

        val before = restored.time
        restored.run(2f)
        assertTrue(restored.time > before)
    }

    @Test
    fun save_rejectsBrokenData() {
        assertTrue(GateRushSaves.decode("{}") == null)
        assertTrue(GateRushSaves.decode("kaputt") == null)
    }

    @Test
    fun autoSpawn_producesEnemiesGatesCratesObstaclesAndPickups() {
        val e = engine(autoSpawn = true)
        e.stats.hearts = 1_000
        var sawGate = false
        var sawCrate = false
        var sawObstacle = false
        var sawPickup = false
        repeat(1_200) {
            e.update(0.05f)
            sawGate = sawGate || e.gates.isNotEmpty()
            sawCrate = sawCrate || e.crates.isNotEmpty()
            sawObstacle = sawObstacle || e.obstacles.isNotEmpty()
            sawPickup = sawPickup || e.pickups.isNotEmpty()
        }
        assertTrue(e.killScore > 0 || e.enemies.isNotEmpty())
        assertTrue(sawGate && sawCrate && sawObstacle && sawPickup)
    }

    @Test
    fun autoSpawn_enemiesNeverSpawnBehindObstacles() {
        for (seed in 1L..6L) {
            val e = engine(autoSpawn = true, seed = seed)
            e.stats.hearts = 1_000
            val seen = HashSet<Long>()
            repeat(4_000) {
                e.update(0.05f)
                for (enemy in e.enemies) {
                    if (enemy.type in setOf(EnemyType.BOSS, EnemyType.ZIGZAG, EnemyType.SPLITLING)) continue
                    // Frische Gegner (neu oder noch hinter der Spawnlinie) dürfen nicht hinter einem Hindernis stehen.
                    if (!seen.add(enemy.id) && enemy.z < GateRushEngine.Z_FAR) continue
                    val shadow = e.obstacles.firstOrNull {
                        it.z > 2f && it.z < enemy.z && abs(enemy.x - it.x) < it.halfWidth
                    }
                    assertTrue("Seed $seed: ${enemy.type} bei x=${enemy.x} hinter Hindernis", shadow == null)
                }
            }
        }
    }

    private fun <T> assertNotNullAndGet(value: T?): T {
        assertNotNull(value)
        return value!!
    }
}
