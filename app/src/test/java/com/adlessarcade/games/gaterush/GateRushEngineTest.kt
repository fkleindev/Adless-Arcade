package com.adlessarcade.games.gaterush

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class GateRushEngineTest {

    private fun engine(autoSpawn: Boolean = false) = GateRushEngine(Random(7), autoSpawn)

    private fun GateRushEngine.run(seconds: Float) {
        var t = 0f
        while (t < seconds) {
            update(0.05f)
            t += 0.05f
        }
    }

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
    fun boss_takesAllHearts() {
        val e = engine()
        e.movePlayer(-1f)
        e.spawnEnemy(EnemyType.BOSS, x = 0.9f, z = 1f)
        e.run(1f)
        assertTrue(e.isGameOver)
    }

    @Test
    fun passingGate_appliesSideWherePlayerStands() {
        val e = engine()
        e.movePlayer(-1f)
        e.spawnGate(GateSide(GateType.DAMAGE, 3), GateSide(GateType.MULTISHOT, 1), z = 1f)
        e.run(0.5f)
        assertEquals(4f, e.stats.damage, 0.001f)
        assertEquals(1, e.stats.multishot)
        assertTrue(e.gates.isEmpty())
    }

    @Test
    fun shootingGate_increasesItsValue() {
        val e = engine()
        e.movePlayer(0.5f)
        val gate = e.spawnGate(null, GateSide(GateType.FIRE_RATE, -10), z = 12f)
        e.run(1f)
        val side = gate.right!!
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
    fun gateValue_isCappedByMaxShotSteps() {
        val side = GateSide(GateType.FIRE_RATE, 10)
        side.hits = 100_000
        assertEquals(10 + GateSide.MAX_SHOT_STEPS, side.value)
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
    fun difficulty_increasesOverTime() {
        val e = engine()
        val early = e.spawnEnemy(EnemyType.RUNNER, x = 0.9f, z = 100f).maxHp
        e.movePlayer(-1f)
        e.run(60f)
        val late = e.spawnEnemy(EnemyType.RUNNER, x = 0.9f, z = 100f).maxHp
        assertTrue(late > early * 2f)
    }

    @Test
    fun save_roundTripRestoresIdenticalSnapshot() {
        val e = engine(autoSpawn = true)
        e.stats.hearts = 99 // Lauf soll sicher nicht vorher enden
        e.movePlayer(0.3f)
        e.run(20f)
        val snapshot = e.snapshot()
        val decoded = requireNotNull(GateRushSaves.decode(GateRushSaves.encode(snapshot, bestAtStart = 7)))
        assertEquals(7, decoded.bestAtStart)

        val restored = GateRushEngine(Random(99))
        restored.restore(decoded.snapshot)
        assertEquals(snapshot, restored.snapshot())
        assertEquals(e.score, restored.score)

        // Der wiederhergestellte Lauf spielt normal weiter.
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
    fun autoSpawn_producesEnemiesGatesAndCrates() {
        val e = engine(autoSpawn = true)
        var sawGate = false
        var sawCrate = false
        repeat(300) {
            e.update(0.05f)
            sawGate = sawGate || e.gates.isNotEmpty()
            sawCrate = sawCrate || e.crates.isNotEmpty()
        }
        assertTrue(e.killScore > 0 || e.enemies.isNotEmpty())
        assertTrue(sawGate)
        assertTrue(sawCrate)
    }
}
