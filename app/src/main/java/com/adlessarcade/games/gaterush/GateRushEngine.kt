package com.adlessarcade.games.gaterush

import kotlin.math.abs
import kotlin.random.Random

/*
 * Weltkoordinaten: x ∈ [-1, 1] ist die Straßenbreite, z = Distanz vor dem Helden.
 * Gegner, Tore und Kisten starten bei Z_FAR und bewegen sich auf z = 0 (Verteidigungslinie) zu.
 */

enum class EnemyType(
    val speed: Float,
    val radius: Float,
    val baseHp: Float,
    val score: Int,
    val heartDamage: Int,
) {
    RUNNER(speed = 6.5f, radius = 0.11f, baseHp = 3f, score = 10, heartDamage = 1),
    SWARM(speed = 5.5f, radius = 0.07f, baseHp = 1.5f, score = 4, heartDamage = 1),
    BRUTE(speed = 3.8f, radius = 0.2f, baseHp = 14f, score = 40, heartDamage = 2),
    BOSS(speed = 2.4f, radius = 0.42f, baseHp = 220f, score = 500, heartDamage = 99),
}

class Enemy(val id: Long, val type: EnemyType, var x: Float, var z: Float, val maxHp: Float) {
    var hp: Float = maxHp
    /** 1 → 0 nach einem Treffer, für den weißen Aufblitz-Effekt. */
    var hitFlash: Float = 0f
}

class Bullet(var x: Float, var z: Float, val vx: Float, val damage: Float, var pierceLeft: Int) {
    val hitIds = HashSet<Long>(2)
    var alive = true
}

enum class GateType(val title: String, val hitsPerStep: Int) {
    DAMAGE("SCHADEN", 4),
    FIRE_RATE("FEUERRATE", 1),
    MULTISHOT("MULTISCHUSS", 30),
    PIERCE("DURCHSCHLAG", 30),
    HEART("LEBEN", 40),
}

/**
 * Eine Torhälfte. Jeder Treffer bringt sie näher an den nächsthöheren Wert; jede weitere Stufe kostet
 * mehr Treffer (hitsPerStep · 1, 2, 3 …) und es gibt maximal [MAX_SHOT_STEPS] Stufen, damit es nicht eskaliert.
 */
class GateSide(val type: GateType, val baseValue: Int) {
    companion object {
        const val MAX_SHOT_STEPS = 8
    }

    var hits: Int = 0
    var hitFlash: Float = 0f

    val shotSteps: Int
        get() {
            var steps = 0
            var cost = type.hitsPerStep
            var remaining = hits
            while (steps < MAX_SHOT_STEPS && remaining >= cost) {
                remaining -= cost
                steps++
                cost += type.hitsPerStep
            }
            return steps
        }

    val value: Int get() = baseValue + shotSteps

    val valueText: String
        get() {
            val sign = if (value > 0) "+" else ""
            return if (type == GateType.FIRE_RATE) "$sign$value%" else "$sign$value"
        }
}

class GatePair(var z: Float, val left: GateSide?, val right: GateSide?)

enum class CrateReward(val label: String) {
    MULTISHOT("+1 MULTISCHUSS"),
    DAMAGE_BOOST("SCHADEN x1.5"),
    FIRE_BOOST("FEUERRATE x1.3"),
    HEART("+1 LEBEN"),
    PIERCE("+1 DURCHSCHLAG"),
}

class Crate(var x: Float, var z: Float, val maxHits: Int, val reward: CrateReward) {
    var hitsLeft: Int = maxHits
    var hitFlash: Float = 0f
}

class PlayerStats {
    var damage: Float = 1f
    var fireRate: Float = 4f
    var multishot: Int = 1
    var pierce: Int = 0
    var hearts: Int = GateRushEngine.START_HEARTS
}

sealed interface GateRushEvent {
    data class EnemyKilled(val x: Float, val z: Float, val type: EnemyType) : GateRushEvent
    data class HeartsLost(val amount: Int) : GateRushEvent
    data class GatePassed(val text: String, val positive: Boolean) : GateRushEvent
    data class CrateOpened(val x: Float, val z: Float, val text: String) : GateRushEvent
}

class GateRushEngine(
    private val random: Random,
    /** Für Tests abschaltbar, damit nur manuell platzierte Objekte existieren. */
    private val autoSpawn: Boolean = true,
) {
    companion object {
        const val Z_FAR = 32f
        const val WORLD_SPEED = 6f
        const val BULLET_SPEED = 30f
        const val MAX_X = 0.9f
        const val START_HEARTS = 3
        const val MAX_HEARTS = 5
        const val MAX_MULTISHOT = 7
        const val MAX_PIERCE = 5
        const val MIN_FIRE_RATE = 1.5f
        const val MAX_FIRE_RATE = 18f
        const val BOSS_INTERVAL = 60f
        private const val STEP = 1f / 120f
        private const val MAX_STEPS_PER_UPDATE = 12
        private const val HIT_DEPTH = 0.45f
        private const val BULLET_RADIUS = 0.03f
        private const val CRATE_RADIUS = 0.15f
    }

    val stats = PlayerStats()
    val enemies = ArrayList<Enemy>()
    val bullets = ArrayList<Bullet>()
    val gates = ArrayList<GatePair>()
    val crates = ArrayList<Crate>()

    var playerX = 0f
        private set
    var time = 0f
        private set
    var distance = 0f
        private set
    var killScore = 0
        private set
    var isGameOver = false
        private set

    val score: Int get() = killScore + (distance / 2f).toInt()

    private val events = ArrayList<GateRushEvent>()
    private var nextId = 0L
    private var accumulator = 0f
    private var fireCooldown = 0f
    private var enemyTimer = 2.5f
    private var gateTimer = 3f
    private var crateTimer = 7f
    private var bossTimer = BOSS_INTERVAL

    /** HP-Multiplikator für neu erscheinende Gegner: sanfter Start, später deutlich steiler. */
    fun hpMultiplier(): Float {
        val minutes = time / 60f
        return 1f + minutes * 1.2f + minutes * minutes * 1.5f
    }

    // --- Spielstand ---

    /** Momentaufnahme für Speichern/Fortsetzen. Projektile und Restzeit des Fixed-Steps sind flüchtig. */
    fun snapshot(): GateRushSnapshot = GateRushSnapshot(
        playerX = playerX,
        time = time,
        distance = distance,
        killScore = killScore,
        nextId = nextId,
        fireCooldown = fireCooldown,
        enemyTimer = enemyTimer,
        gateTimer = gateTimer,
        crateTimer = crateTimer,
        bossTimer = bossTimer,
        stats = StatsSnapshot(stats.damage, stats.fireRate, stats.multishot, stats.pierce, stats.hearts),
        enemies = enemies.map { EnemySnapshot(it.id, it.type, it.x, it.z, it.hp, it.maxHp) },
        gates = gates.map { GateSnapshot(it.z, it.left?.toSnapshot(), it.right?.toSnapshot()) },
        crates = crates.map { CrateSnapshot(it.x, it.z, it.maxHits, it.hitsLeft, it.reward) },
    )

    /** Übernimmt einen gespeicherten Zustand (nur direkt nach dem Erzeugen der Engine sinnvoll). */
    fun restore(s: GateRushSnapshot) {
        playerX = s.playerX.coerceIn(-MAX_X, MAX_X)
        time = s.time
        distance = s.distance
        killScore = s.killScore
        nextId = s.nextId
        fireCooldown = s.fireCooldown
        enemyTimer = s.enemyTimer
        gateTimer = s.gateTimer
        crateTimer = s.crateTimer
        bossTimer = s.bossTimer
        stats.damage = s.stats.damage
        stats.fireRate = s.stats.fireRate
        stats.multishot = s.stats.multishot
        stats.pierce = s.stats.pierce
        stats.hearts = s.stats.hearts
        enemies.clear()
        s.enemies.forEach { enemies += Enemy(it.id, it.type, it.x, it.z, it.maxHp).apply { hp = it.hp } }
        gates.clear()
        s.gates.forEach { gates += GatePair(it.z, it.left?.toSide(), it.right?.toSide()) }
        crates.clear()
        s.crates.forEach { crates += Crate(it.x, it.z, it.maxHits, it.reward).apply { hitsLeft = it.hitsLeft } }
        bullets.clear()
        events.clear()
        accumulator = 0f
        isGameOver = stats.hearts <= 0
    }

    private fun GateSide.toSnapshot() = GateSideSnapshot(type, baseValue, hits)
    private fun GateSideSnapshot.toSide() = GateSide(type, baseValue).also { it.hits = hits }

    fun movePlayer(dx: Float) {
        if (isGameOver) return
        playerX = (playerX + dx).coerceIn(-MAX_X, MAX_X)
    }

    fun drainEvents(): List<GateRushEvent> {
        if (events.isEmpty()) return emptyList()
        val copy = events.toList()
        events.clear()
        return copy
    }

    fun update(dt: Float) {
        if (isGameOver) return
        accumulator += dt
        var steps = 0
        while (accumulator >= STEP && steps < MAX_STEPS_PER_UPDATE && !isGameOver) {
            step(STEP)
            accumulator -= STEP
            steps++
        }
        if (steps == MAX_STEPS_PER_UPDATE) accumulator = 0f
    }

    // --- Spawning (öffentlich für Tests) ---

    fun spawnEnemy(type: EnemyType, x: Float, z: Float = Z_FAR): Enemy {
        val enemy = Enemy(nextId++, type, x.coerceIn(-MAX_X, MAX_X), z, type.baseHp * hpMultiplier())
        enemies += enemy
        return enemy
    }

    fun spawnGate(left: GateSide?, right: GateSide?, z: Float = Z_FAR): GatePair =
        GatePair(z, left, right).also { gates += it }

    fun spawnCrate(x: Float, hits: Int, reward: CrateReward, z: Float = Z_FAR): Crate =
        Crate(x, z, hits, reward).also { crates += it }

    // --- Simulation ---

    private fun step(dt: Float) {
        time += dt
        distance += WORLD_SPEED * dt
        if (autoSpawn) runSpawner(dt)
        fire(dt)
        moveBullets(dt)
        moveEnemies(dt)
        moveGates(dt)
        moveCrates(dt)
        bullets.removeAll { !it.alive || it.z > Z_FAR + 2f || abs(it.x) > 1.3f }
    }

    private fun fire(dt: Float) {
        fireCooldown -= dt
        while (fireCooldown <= 0f) {
            val n = stats.multishot
            for (i in 0 until n) {
                val offset = (i - (n - 1) / 2f) * 0.07f
                bullets += Bullet(
                    x = playerX + offset,
                    z = 0.3f,
                    vx = offset * 0.5f,
                    damage = stats.damage,
                    pierceLeft = stats.pierce,
                )
            }
            fireCooldown += 1f / stats.fireRate
        }
    }

    private fun moveBullets(dt: Float) {
        for (b in bullets) {
            if (!b.alive) continue
            b.z += BULLET_SPEED * dt
            b.x += b.vx * dt
            if (hitGate(b) || hitCrate(b)) {
                b.alive = false
                continue
            }
            hitEnemy(b)
        }
    }

    private fun hitGate(b: Bullet): Boolean {
        if (abs(b.x) > 1f) return false
        for (gate in gates) {
            if (abs(b.z - gate.z) > 0.3f) continue
            val side = (if (b.x < 0f) gate.left else gate.right) ?: continue
            side.hits++
            side.hitFlash = 1f
            return true
        }
        return false
    }

    private fun hitCrate(b: Bullet): Boolean {
        val iterator = crates.iterator()
        while (iterator.hasNext()) {
            val crate = iterator.next()
            if (abs(b.z - crate.z) > HIT_DEPTH || abs(b.x - crate.x) > CRATE_RADIUS + BULLET_RADIUS) continue
            crate.hitsLeft--
            crate.hitFlash = 1f
            if (crate.hitsLeft <= 0) {
                applyReward(crate.reward)
                events += GateRushEvent.CrateOpened(crate.x, crate.z, crate.reward.label)
                iterator.remove()
            }
            return true
        }
        return false
    }

    private fun hitEnemy(b: Bullet) {
        val iterator = enemies.iterator()
        while (iterator.hasNext()) {
            val e = iterator.next()
            if (e.id in b.hitIds) continue
            if (abs(b.z - e.z) > HIT_DEPTH || abs(b.x - e.x) > e.type.radius + BULLET_RADIUS) continue
            e.hp -= b.damage
            e.hitFlash = 1f
            b.hitIds += e.id
            if (e.hp <= 0f) {
                killScore += e.type.score
                events += GateRushEvent.EnemyKilled(e.x, e.z, e.type)
                iterator.remove()
            }
            if (b.pierceLeft > 0) {
                b.pierceLeft--
            } else {
                b.alive = false
                return
            }
        }
    }

    private fun moveEnemies(dt: Float) {
        val iterator = enemies.iterator()
        while (iterator.hasNext()) {
            val e = iterator.next()
            e.z -= e.type.speed * dt
            e.hitFlash = (e.hitFlash - dt * 6f).coerceAtLeast(0f)
            if (e.z <= 0f) {
                iterator.remove()
                loseHearts(e.type.heartDamage)
            }
        }
    }

    private fun moveGates(dt: Float) {
        val iterator = gates.iterator()
        while (iterator.hasNext()) {
            val gate = iterator.next()
            gate.z -= WORLD_SPEED * dt
            gate.left?.let { it.hitFlash = (it.hitFlash - dt * 6f).coerceAtLeast(0f) }
            gate.right?.let { it.hitFlash = (it.hitFlash - dt * 6f).coerceAtLeast(0f) }
            if (gate.z <= 0f) {
                iterator.remove()
                val side = if (playerX < 0f) gate.left else gate.right
                if (side != null) applyGate(side)
            }
        }
    }

    private fun moveCrates(dt: Float) {
        val iterator = crates.iterator()
        while (iterator.hasNext()) {
            val crate = iterator.next()
            crate.z -= WORLD_SPEED * dt
            crate.hitFlash = (crate.hitFlash - dt * 6f).coerceAtLeast(0f)
            if (crate.z <= 0f) iterator.remove()
        }
    }

    private fun loseHearts(amount: Int) {
        val lost = minOf(amount, stats.hearts)
        stats.hearts -= lost
        events += GateRushEvent.HeartsLost(lost)
        if (stats.hearts <= 0) isGameOver = true
    }

    fun applyGate(side: GateSide) {
        val v = side.value
        when (side.type) {
            GateType.DAMAGE -> stats.damage = (stats.damage + v).coerceAtLeast(1f)
            GateType.FIRE_RATE ->
                stats.fireRate = (stats.fireRate * (1f + v / 100f)).coerceIn(MIN_FIRE_RATE, MAX_FIRE_RATE)
            GateType.MULTISHOT -> stats.multishot = (stats.multishot + v).coerceIn(1, MAX_MULTISHOT)
            GateType.PIERCE -> stats.pierce = (stats.pierce + v).coerceIn(0, MAX_PIERCE)
            GateType.HEART -> stats.hearts = (stats.hearts + v).coerceIn(1, MAX_HEARTS)
        }
        events += GateRushEvent.GatePassed("${side.valueText} ${side.type.title}", v > 0)
    }

    private fun applyReward(reward: CrateReward) {
        when (reward) {
            CrateReward.MULTISHOT -> stats.multishot = (stats.multishot + 1).coerceAtMost(MAX_MULTISHOT)
            CrateReward.DAMAGE_BOOST -> stats.damage *= 1.5f
            CrateReward.FIRE_BOOST -> stats.fireRate = (stats.fireRate * 1.3f).coerceAtMost(MAX_FIRE_RATE)
            CrateReward.HEART -> stats.hearts = (stats.hearts + 1).coerceAtMost(MAX_HEARTS)
            CrateReward.PIERCE -> stats.pierce = (stats.pierce + 1).coerceAtMost(MAX_PIERCE)
        }
    }

    // --- Spawner / Schwierigkeitskurve ---

    private fun runSpawner(dt: Float) {
        val bossAlive = enemies.any { it.type == EnemyType.BOSS }

        bossTimer -= dt
        if (bossTimer <= 0f) {
            bossTimer = BOSS_INTERVAL
            spawnEnemy(EnemyType.BOSS, 0f)
        }

        enemyTimer -= dt
        if (enemyTimer <= 0f) {
            val interval = (2.0f - time / 70f).coerceAtLeast(0.3f)
            enemyTimer = if (bossAlive) interval * 2f else interval
            spawnWaveUnit()
        }

        gateTimer -= dt
        if (gateTimer <= 0f) {
            gateTimer = 6f
            spawnRandomGate()
        }

        crateTimer -= dt
        if (crateTimer <= 0f) {
            crateTimer = 11f
            val hits = 10 + (time / 2f).toInt()
            spawnCrate(random.nextFloat() * 1.4f - 0.7f, hits, CrateReward.entries.random(random))
        }
    }

    private fun spawnWaveUnit() {
        val x = random.nextFloat() * 2f * MAX_X - MAX_X
        val z = Z_FAR + random.nextFloat() * 2f
        val roll = random.nextFloat()
        val bruteChance = (time / 300f).coerceAtMost(0.25f)
        when {
            roll < bruteChance -> spawnEnemy(EnemyType.BRUTE, x, z)
            roll < bruteChance + 0.2f -> repeat(5) {
                spawnEnemy(
                    EnemyType.SWARM,
                    x + random.nextFloat() * 0.3f - 0.15f,
                    z + random.nextFloat() * 1.5f,
                )
            }
            else -> spawnEnemy(EnemyType.RUNNER, x, z)
        }
    }

    private fun spawnRandomGate() {
        val first = randomGateType()
        var second = randomGateType()
        if (second == first && first != GateType.DAMAGE) second = GateType.DAMAGE
        // Eine Seite meist positiv, die andere oft negativ – man muss sich entscheiden (oder sie freischießen).
        val a = GateSide(first, randomGateValue(first, positive = true))
        val b = GateSide(second, randomGateValue(second, positive = random.nextFloat() < 0.35f))
        if (random.nextBoolean()) spawnGate(a, b) else spawnGate(b, a)
    }

    private fun randomGateType(): GateType {
        val options = buildList {
            repeat(4) { add(GateType.DAMAGE) }
            repeat(4) { add(GateType.FIRE_RATE) }
            if (stats.multishot < MAX_MULTISHOT) repeat(2) { add(GateType.MULTISHOT) }
            if (stats.pierce < MAX_PIERCE) add(GateType.PIERCE)
            if (stats.hearts < MAX_HEARTS) add(GateType.HEART)
        }
        return options.random(random)
    }

    private fun randomGateValue(type: GateType, positive: Boolean): Int {
        val sign = if (positive) 1 else -1
        return when (type) {
            GateType.DAMAGE -> sign * random.nextInt(1, 2 + (time / 40f).toInt() + 1)
            GateType.FIRE_RATE -> sign * random.nextInt(10, 26)
            GateType.MULTISHOT, GateType.PIERCE -> sign
            GateType.HEART -> 1
        }
    }
}
