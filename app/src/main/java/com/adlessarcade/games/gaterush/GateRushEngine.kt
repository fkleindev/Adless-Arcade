package com.adlessarcade.games.gaterush

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

class GateRushEngine(
    /** Seed für den Levelaufbau (bei der Täglichen Challenge für alle gleich). */
    val seed: Long,
    val mode: GateRushMode = GateRushMode.ENDLESS,
    /** Für Tests abschaltbar, damit nur manuell platzierte Objekte existieren. */
    private val autoSpawn: Boolean = true,
) {
    companion object {
        const val Z_FAR = 32f
        const val WORLD_SPEED = 6f
        const val MAX_X = 0.9f
        const val START_HEARTS = 3
        const val START_FIRE_RATE = 4.5f
        const val MAX_HEARTS = 6
        const val MAX_MULTISHOT = 8
        const val MAX_PIERCE = 6
        /** Jeder durchschlagene Gegner verringert den Schaden des Projektils um diesen Anteil. */
        const val PIERCE_FALLOFF = 0.3f
        const val MIN_FIRE_RATE = 2f
        const val MAX_FIRE_RATE = 20f
        const val MAX_CRIT = 0.75f
        const val CRIT_MULTIPLIER = 2.5f
        const val MAX_SPLASH = 10
        const val MAX_FROST = 10
        const val MAX_SHIELDS = 5
        const val MAX_DRONES = 4
        const val FIRST_BOSS_TIME = 60f
        const val FIRST_OBSTACLE_TIME = 20f
        /** In dieser Anfangsphase kommen nur einzelne Läufer. */
        const val WARMUP_TIME = 15f
        /** Bis hierhin sind alle Torspuren positiv. */
        const val FRIENDLY_GATES_TIME = 25f
        const val BOSS_COINS = 8
        /** Im Boss Rush kommen Bosse viel öfter – daher weniger Coins pro Boss. */
        const val BOSS_RUSH_COINS = 4
        const val CRATE_COINS = 1
        /** Drohnen-Positionen relativ zum Helden, in Reihenfolge des Freischaltens. */
        val DRONE_OFFSETS = listOf(-0.24f, 0.24f, -0.44f, 0.44f)
        const val HERO_HALF_WIDTH = 0.1f
        const val PICKUP_RADIUS = 0.18f
        private const val STEP = 1f / 120f
        private const val MAX_STEPS_PER_UPDATE = 12
        private const val HIT_DEPTH = 0.45f
        private const val GATE_DEPTH = 0.3f
        private const val BULLET_RADIUS = 0.03f
        private const val CRATE_RADIUS = 0.15f
        private const val SLOW_DURATION = 1.2f
        private const val SHIELDED_DAMAGE = 0.25f
        private const val ZIGZAG_AMPLITUDE = 0.35f
    }

    val stats = PlayerStats()
    val enemies = ArrayList<Enemy>()
    val bullets = ArrayList<Bullet>()
    val gates = ArrayList<GateRow>()
    val crates = ArrayList<Crate>()
    val obstacles = ArrayList<Obstacle>()
    val pickups = ArrayList<Pickup>()

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
    /** In diesem Lauf verdiente Coins. */
    var coinsEarned = 0
        private set

    val score: Int get() = killScore + (distance / 2f).toInt()

    private val combatRandom = Random(seed * 31 + 7)
    private var spawner = GateRushSpawner(this, Random(seed), SpawnerState())

    /** Boss Rush: aktuelle Welle bzw. ob gerade ein Boss kämpft. */
    val wave: Int get() = spawner.state.wave
    val bossPhase: Boolean get() = spawner.state.bossPhase

    private val events = ArrayList<GateRushEvent>()
    private var nextId = 0L
    private var accumulator = 0f
    private var fireCooldown = 0f
    private var droneCooldown = 0f

    // --- Schwierigkeitskurve (Endless) ---

    /**
     * HP-Multiplikator für neue Gegner: am Anfang flach, danach polynomiell und zusätzlich exponentiell –
     * so holt das Spiel auch voll aufgerüstete Helden irgendwann ein.
     */
    fun hpMultiplier(): Float {
        val m = time / 60f
        return (1f + 0.2f * m + 0.5f * m * m) * 1.5f.pow(m)
    }

    /** Gegner werden über die Zeit schneller (bis +60 %). */
    fun speedMultiplier(): Float = 1f + (time / 400f).coerceAtMost(0.6f)

    /** Abstand zwischen Spawns: anfangs großzügig, später sehr dicht. */
    fun spawnInterval(): Float = (2.2f - time / 110f).coerceAtLeast(0.3f)

    /** Fester Schadensbonus der Kiste (wächst mit der Zeit, aber nicht multiplikativ – sonst eskaliert es). */
    fun damageBoostAmount(): Float = 2f + time / 30f

    // --- Spielstand ---

    /** Momentaufnahme für Speichern/Fortsetzen. Projektile und Restzeit des Fixed-Steps sind flüchtig. */
    fun snapshot(): GateRushSnapshot = GateRushSnapshot(
        playerX = playerX,
        time = time,
        distance = distance,
        killScore = killScore,
        coinsEarned = coinsEarned,
        nextId = nextId,
        fireCooldown = fireCooldown,
        droneCooldown = droneCooldown,
        spawner = spawner.state.copy(),
        stats = StatsSnapshot(
            damage = stats.damage,
            fireRate = stats.fireRate,
            multishot = stats.multishot,
            pierce = stats.pierce,
            hearts = stats.hearts,
            critChance = stats.critChance,
            splash = stats.splash,
            frost = stats.frost,
            shields = stats.shields,
            drones = stats.drones,
            weapon = stats.weapon,
            frenzy = stats.frenzy,
            slowmo = stats.slowmo,
        ),
        enemies = enemies.map {
            EnemySnapshot(it.id, it.type, it.x, it.z, it.hp, it.maxHp, it.speedFactor, it.slowTimer, it.baseX, it.phase)
        },
        gates = gates.map { row -> GateSnapshot(row.id, row.z, row.lanes.map { it?.toSnapshot() }) },
        crates = crates.map { CrateSnapshot(it.x, it.z, it.maxHits, it.hitsLeft, it.reward) },
        obstacles = obstacles.map { ObstacleSnapshot(it.id, it.x, it.z, it.halfWidth, it.kind) },
        pickups = pickups.map { PickupSnapshot(it.id, it.x, it.z, it.kind) },
    )

    /** Übernimmt einen gespeicherten Zustand (nur direkt nach dem Erzeugen der Engine sinnvoll). */
    fun restore(s: GateRushSnapshot) {
        playerX = s.playerX.coerceIn(-MAX_X, MAX_X)
        time = s.time
        distance = s.distance
        killScore = s.killScore
        coinsEarned = s.coinsEarned
        nextId = s.nextId
        fireCooldown = s.fireCooldown
        droneCooldown = s.droneCooldown
        // Neuer Zufallsstrom ab der Fortsetzung, aber gleiche Timer/Phasen.
        spawner = GateRushSpawner(this, Random(seed + s.nextId), s.spawner.copy())
        with(s.stats) {
            stats.damage = damage
            stats.fireRate = fireRate
            stats.multishot = multishot
            stats.pierce = pierce
            stats.hearts = hearts
            stats.critChance = critChance
            stats.splash = splash
            stats.frost = frost
            stats.shields = shields
            stats.drones = drones
            stats.weapon = weapon
            stats.frenzy = frenzy
            stats.slowmo = slowmo
        }
        enemies.clear()
        s.enemies.forEach {
            enemies += Enemy(it.id, it.type, it.x, it.z, it.maxHp, it.speedFactor, it.baseX, it.phase).apply {
                hp = it.hp
                slowTimer = it.slowTimer
            }
        }
        gates.clear()
        s.gates.forEach { row -> gates += GateRow(row.id, row.z, row.lanes.map { it?.toSide() }) }
        crates.clear()
        s.crates.forEach { crates += Crate(it.x, it.z, it.maxHits, it.reward).apply { hitsLeft = it.hitsLeft } }
        obstacles.clear()
        s.obstacles.forEach { obstacles += Obstacle(it.id, it.x, it.z, it.halfWidth, it.kind) }
        pickups.clear()
        s.pickups.forEach { pickups += Pickup(it.id, it.x, it.z, it.kind) }
        bullets.clear()
        events.clear()
        accumulator = 0f
        isGameOver = stats.hearts <= 0
    }

    private fun GateSide.toSnapshot() = GateSideSnapshot(type, baseValue, hits, weapon)
    private fun GateSideSnapshot.toSide() = GateSide(type, baseValue, weapon).also { it.hits = hits }

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

    internal fun emit(event: GateRushEvent) {
        events += event
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

    // --- Spawning (öffentlich für Spawner und Tests) ---

    fun spawnEnemy(
        type: EnemyType,
        x: Float,
        z: Float = Z_FAR,
        hpMultiplier: Float = hpMultiplier(),
        phase: Float = 0f,
    ): Enemy {
        val cx = x.coerceIn(-MAX_X, MAX_X)
        val enemy = Enemy(nextId++, type, cx, z, type.baseHp * hpMultiplier, speedMultiplier(), cx, phase)
        enemies += enemy
        return enemy
    }

    fun spawnGate(lanes: List<GateSide?>, z: Float = Z_FAR): GateRow =
        GateRow(nextId++, z, lanes).also { gates += it }

    fun spawnCrate(x: Float, hits: Int, reward: CrateReward, z: Float = Z_FAR): Crate =
        Crate(x, z, hits, reward).also { crates += it }

    fun spawnObstacle(x: Float, halfWidth: Float, kind: ObstacleKind, z: Float = Z_FAR): Obstacle =
        Obstacle(nextId++, x, z, halfWidth, kind).also { obstacles += it }

    fun spawnPickup(x: Float, kind: PowerUpKind, z: Float = Z_FAR): Pickup =
        Pickup(nextId++, x, z, kind).also { pickups += it }

    // --- Simulation ---

    private fun step(dt: Float) {
        time += dt
        distance += WORLD_SPEED * dt
        stats.frenzy = (stats.frenzy - dt).coerceAtLeast(0f)
        stats.slowmo = (stats.slowmo - dt).coerceAtLeast(0f)
        if (autoSpawn) spawner.update(dt)
        fire(dt)
        moveBullets(dt)
        moveEnemies(dt)
        moveGates(dt)
        moveCrates(dt)
        moveObstacles(dt)
        movePickups(dt)
        bullets.removeAll { !it.alive || it.z > it.weapon.range || abs(it.x) > 1.3f }
    }

    /** Aktuelle Positionen der Drohnen. */
    fun dronePositions(): List<Float> = DRONE_OFFSETS.take(stats.drones).map { playerX + it }

    private fun fire(dt: Float) {
        val weapon = stats.weapon
        val frenzy = if (stats.frenzy > 0f) 2f else 1f
        fireCooldown -= dt
        while (fireCooldown <= 0f) {
            val n = stats.multishot + weapon.extraPellets
            for (i in 0 until n) {
                val offset = (i - (n - 1) / 2f) * weapon.spread
                bullets += Bullet(
                    x = playerX + offset,
                    z = 0.3f,
                    vx = offset * weapon.fan,
                    damage = stats.damage * weapon.damageFactor,
                    pierceLeft = stats.pierce + weapon.extraPierce,
                    weapon = weapon,
                )
            }
            fireCooldown += 1f / (stats.fireRate * weapon.rateFactor * frenzy)
        }
        if (stats.drones > 0) {
            droneCooldown -= dt
            while (droneCooldown <= 0f) {
                dronePositions().forEach { x -> bullets += Bullet(x, 0.3f, 0f, stats.damage * 0.6f, stats.pierce) }
                droneCooldown += 1f / (stats.fireRate * 0.6f * frenzy)
            }
        }
    }

    private fun moveBullets(dt: Float) {
        for (b in bullets) {
            if (!b.alive) continue
            b.z += b.weapon.speed * dt
            b.x += b.vx * dt
            passGates(b)
            if (hitObstacle(b) || hitCrate(b)) {
                b.alive = false
                continue
            }
            hitEnemy(b)
        }
    }

    /** Projektile fliegen durch Tore hindurch und erhöhen dabei den Wert der getroffenen Spur. */
    private fun passGates(b: Bullet) {
        if (abs(b.x) > 1f) return
        for (row in gates) {
            if (abs(b.z - row.z) > GATE_DEPTH || row.id in b.gateIds) continue
            b.gateIds += row.id
            val lane = row.lanes[row.laneAt(b.x)] ?: continue
            lane.hits++
            lane.hitFlash = 1f
        }
    }

    /** Hindernisse sind unzerstörbar und schlucken Projektile. */
    private fun hitObstacle(b: Bullet): Boolean =
        obstacles.any { abs(b.z - it.z) <= GATE_DEPTH && abs(b.x - it.x) <= it.halfWidth }

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
                earnCoins(crate.x, crate.z, CRATE_COINS)
                iterator.remove()
            }
            return true
        }
        return false
    }

    private fun hitEnemy(b: Bullet) {
        for (e in enemies) {
            if (e.hp <= 0f || e.id in b.hitIds) continue
            if (abs(b.z - e.z) > HIT_DEPTH || abs(b.x - e.x) > e.type.radius + BULLET_RADIUS) continue
            val crit = stats.critChance > 0f && combatRandom.nextFloat() < stats.critChance
            var damage = b.damage * if (crit) CRIT_MULTIPLIER else 1f
            if (e.type == EnemyType.SHIELDED && !b.weapon.armorPiercing) damage *= SHIELDED_DAMAGE
            damageEnemy(e, damage)
            b.hitIds += e.id
            when {
                b.weapon.explosive -> explode(e, b.damage, radius = 0.35f + 0.03f * stats.splash, fraction = 0.6f)
                stats.splash > 0 -> explode(e, damage, radius = 0.1f + 0.03f * stats.splash, fraction = 0.2f + 0.04f * stats.splash)
            }
            if (b.pierceLeft > 0) {
                b.pierceLeft--
                b.damage *= 1f - PIERCE_FALLOFF
            } else {
                b.alive = false
                break
            }
        }
        removeDeadEnemies()
    }

    private fun damageEnemy(e: Enemy, damage: Float) {
        e.hp -= damage
        e.hitFlash = 1f
        if (stats.frost > 0) e.slowTimer = SLOW_DURATION
    }

    /** Flächenschaden um den getroffenen Gegner herum (trifft auch Schildträger voll). */
    private fun explode(center: Enemy, damage: Float, radius: Float, fraction: Float) {
        for (other in enemies) {
            if (other === center || other.hp <= 0f) continue
            if (abs(other.x - center.x) <= radius + other.type.radius && abs(other.z - center.z) <= radius * 4f) {
                damageEnemy(other, damage * fraction)
            }
        }
        events += GateRushEvent.Explosion(center.x, center.z, radius)
    }

    private fun removeDeadEnemies() {
        var spawned: MutableList<Enemy>? = null
        val iterator = enemies.iterator()
        while (iterator.hasNext()) {
            val e = iterator.next()
            if (e.hp > 0f) continue
            killScore += e.type.score
            events += GateRushEvent.EnemyKilled(e.x, e.z, e.type)
            earnCoins(e.x, e.z, coinDrop(e.type))
            iterator.remove()
            if (e.type == EnemyType.SPLITTER) {
                val list = spawned ?: ArrayList<Enemy>().also { spawned = it }
                // Kleinlinge erben die Stärke des Splitters.
                val hpFactor = e.maxHp / EnemyType.SPLITTER.baseHp
                for (dx in listOf(-0.15f, 0f, 0.15f)) {
                    val x = (e.x + dx).coerceIn(-MAX_X, MAX_X)
                    list += Enemy(nextId++, EnemyType.SPLITLING, x, e.z + 0.3f, EnemyType.SPLITLING.baseHp * hpFactor, e.speedFactor, x)
                }
            }
        }
        spawned?.let { enemies += it }
    }

    private fun moveEnemies(dt: Float) {
        val frostFactor = 1f - 0.045f * stats.frost
        val slowmoFactor = if (stats.slowmo > 0f) 0.5f else 1f
        val iterator = enemies.iterator()
        while (iterator.hasNext()) {
            val e = iterator.next()
            val slowed = if (e.slowTimer > 0f) frostFactor else 1f
            e.z -= e.type.speed * e.speedFactor * slowed * slowmoFactor * dt
            if (e.type == EnemyType.ZIGZAG) {
                e.x = (e.baseX + sin(time * 2.5f + e.phase) * ZIGZAG_AMPLITUDE).coerceIn(-MAX_X, MAX_X)
            }
            e.slowTimer = (e.slowTimer - dt).coerceAtLeast(0f)
            e.hitFlash = (e.hitFlash - dt * 6f).coerceAtLeast(0f)
            if (e.z <= 0f) {
                iterator.remove()
                takeHit(e.type.heartDamage, blockable = e.type != EnemyType.BOSS)
            }
        }
    }

    private fun moveGates(dt: Float) {
        val iterator = gates.iterator()
        while (iterator.hasNext()) {
            val row = iterator.next()
            row.z -= WORLD_SPEED * dt
            row.lanes.forEach { lane -> lane?.let { it.hitFlash = (it.hitFlash - dt * 6f).coerceAtLeast(0f) } }
            if (row.z <= 0f) {
                iterator.remove()
                row.lanes[row.laneAt(playerX)]?.let(::applyGate)
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

    private fun moveObstacles(dt: Float) {
        val iterator = obstacles.iterator()
        while (iterator.hasNext()) {
            val o = iterator.next()
            o.z -= WORLD_SPEED * dt
            if (o.z <= 0.3f && o.z > -0.3f && abs(playerX - o.x) < o.halfWidth + HERO_HALF_WIDTH) {
                iterator.remove()
                events += GateRushEvent.ObstacleHit
                takeHit(1, blockable = true)
            } else if (o.z <= -0.6f) {
                iterator.remove()
            }
        }
    }

    private fun movePickups(dt: Float) {
        val iterator = pickups.iterator()
        while (iterator.hasNext()) {
            val p = iterator.next()
            p.z -= WORLD_SPEED * dt
            if (p.z <= 0.3f && p.z > -0.3f && abs(playerX - p.x) < PICKUP_RADIUS) {
                iterator.remove()
                activate(p.kind)
            } else if (p.z <= -0.6f) {
                iterator.remove()
            }
        }
    }

    private fun activate(kind: PowerUpKind) {
        when (kind) {
            PowerUpKind.FRENZY -> stats.frenzy = kind.duration
            PowerUpKind.SLOWMO -> stats.slowmo = kind.duration
            PowerUpKind.BOMB -> {
                // Alles Sichtbare: Nicht-Bosse vernichtet, Bosse verlieren ein Viertel ihrer HP.
                for (e in enemies) {
                    if (e.z > Z_FAR) continue
                    if (e.type == EnemyType.BOSS) damageEnemy(e, e.maxHp * 0.25f) else e.hp = 0f
                }
                removeDeadEnemies()
            }
        }
        events += GateRushEvent.PowerUpCollected(kind)
    }

    /** Coins, die ein besiegter Gegner fallen lässt (bewusst knapp, damit es zu Block Break passt). */
    private fun coinDrop(type: EnemyType): Int {
        val chance = when (type) {
            EnemyType.RUNNER -> 0.04f
            EnemyType.SWARM -> 0.01f
            EnemyType.SPLITLING -> 0f
            EnemyType.ZIGZAG -> 0.05f
            EnemyType.BRUTE -> 0.25f
            EnemyType.SPLITTER -> 0.15f
            EnemyType.SHIELDED -> 0.2f
            EnemyType.BOSS -> return if (mode == GateRushMode.BOSS_RUSH) BOSS_RUSH_COINS else BOSS_COINS
        }
        return if (combatRandom.nextFloat() < chance) 1 else 0
    }

    private fun earnCoins(x: Float, z: Float, amount: Int) {
        if (amount <= 0) return
        coinsEarned += amount
        events += GateRushEvent.CoinsEarned(x, z, amount)
    }

    /** Ein Durchbruch oder Hindernis-Treffer; Schilde fangen ihn ab, wenn [blockable]. */
    private fun takeHit(hearts: Int, blockable: Boolean) {
        if (blockable && stats.shields > 0) {
            stats.shields--
            events += GateRushEvent.ShieldBlocked
            return
        }
        val lost = minOf(hearts, stats.hearts)
        stats.hearts -= lost
        events += GateRushEvent.HeartsLost(lost)
        if (stats.hearts <= 0) isGameOver = true
    }

    fun applyGate(side: GateSide) {
        val v = side.value
        with(stats) {
            when (side.type) {
                GateType.DAMAGE -> damage = (damage + v).coerceAtLeast(1f)
                GateType.FIRE_RATE -> fireRate = (fireRate * (1f + v / 100f)).coerceIn(MIN_FIRE_RATE, MAX_FIRE_RATE)
                GateType.MULTISHOT -> multishot = (multishot + v).coerceIn(1, MAX_MULTISHOT)
                GateType.PIERCE -> pierce = (pierce + v).coerceIn(0, MAX_PIERCE)
                GateType.HEART -> hearts = (hearts + v).coerceIn(1, MAX_HEARTS)
                GateType.CRIT -> critChance = (critChance + v / 100f).coerceIn(0f, MAX_CRIT)
                GateType.SPLASH -> splash = (splash + v).coerceIn(0, MAX_SPLASH)
                GateType.FROST -> frost = (frost + v).coerceIn(0, MAX_FROST)
                GateType.SHIELD -> shields = (shields + v).coerceIn(0, MAX_SHIELDS)
                GateType.DRONE -> drones = (drones + v).coerceIn(0, MAX_DRONES)
                GateType.WEAPON -> side.weapon?.let { weapon = it }
            }
        }
        val text = if (side.type == GateType.WEAPON) side.valueText else "${side.valueText} ${side.title}"
        events += GateRushEvent.GatePassed(text, v > 0)
    }

    private fun applyReward(reward: CrateReward) {
        with(stats) {
            when (reward) {
                CrateReward.MULTISHOT -> multishot = (multishot + 1).coerceAtMost(MAX_MULTISHOT)
                CrateReward.DAMAGE_BOOST -> damage += damageBoostAmount()
                CrateReward.FIRE_BOOST -> fireRate = (fireRate * 1.15f).coerceAtMost(MAX_FIRE_RATE)
                CrateReward.HEART -> hearts = (hearts + 1).coerceAtMost(MAX_HEARTS)
                CrateReward.PIERCE -> pierce = (pierce + 1).coerceAtMost(MAX_PIERCE)
                CrateReward.CRIT -> critChance = (critChance + 0.05f).coerceAtMost(MAX_CRIT)
                CrateReward.SPLASH -> splash = (splash + 1).coerceAtMost(MAX_SPLASH)
                CrateReward.FROST -> frost = (frost + 1).coerceAtMost(MAX_FROST)
                CrateReward.SHIELD -> shields = (shields + 1).coerceAtMost(MAX_SHIELDS)
                CrateReward.DRONE -> drones = (drones + 1).coerceAtMost(MAX_DRONES)
                CrateReward.SHOTGUN -> weapon = Weapon.SHOTGUN
                CrateReward.LASER -> weapon = Weapon.LASER
                CrateReward.ROCKET -> weapon = Weapon.ROCKET
            }
        }
    }
}
