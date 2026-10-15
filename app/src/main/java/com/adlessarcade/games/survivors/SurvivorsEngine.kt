package com.adlessarcade.games.survivors

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

class SurvivorsEngine(val state: SurvivorsState, seed: Long) {
    companion object {
        const val PLAYER_RADIUS = 0.4f
        const val INVULNERABLE_TIME = 0.5f
        const val GEM_PICKUP_DISTANCE = 0.5f
        const val PICKUP_DISTANCE = 0.7f
        const val MAX_GEMS = 250
        const val VICTORY_BONUS_SCORE = 2000
        const val VICTORY_COINS = 20
        const val BOSS_COINS = 5
        const val ELITE_COINS = 2
        /** Coins für jede überlebte Minute. */
        const val MINUTE_COINS = 3
        /** Runen (Währung der Runen-Schmiede) – ein starker 15-Minuten-Lauf bringt ca. 45–50. */
        const val MINUTE_RUNES = 2
        const val ELITE_RUNES = 1
        const val BOSS_RUNES = 4
        const val VICTORY_RUNES = 10
        /** Heilung, wenn man eine Kisten-Belohnung ablehnt. */
        const val CHEST_DECLINE_HEAL = 25f
        /** Lebensraub der Entwicklungen: höchstens so viel Heilung pro Sekunde. */
        const val LEECH_PER_SECOND = 4f
        const val FROZEN_DAMAGE_BONUS = 1.3f
        const val FREEZE_TIME = 1.5f
        /** Entwicklungen gibt es erst ab dem ersten Boss. */
        const val EVOLUTION_MIN_TIME = 300f
        const val METEOR_DELAY = 0.45f
        /** Siegesrunde: mindestens/höchstens so lange fliegen die Drops zum Helden. */
        const val VICTORY_SWEEP_MIN_TIME = 0.6f
        const val VICTORY_SWEEP_MAX_TIME = 3f
        private const val PULL_SPEED = 9f
        private const val SWEEP_PULL_SPEED = 14f
        private const val STORM_BOW_PIERCE = 4
        private const val MAX_HAZARDS = 12
        /** Sichtbarer Bereich (halbe Breite/Höhe) – Blitze treffen nur sichtbare Gegner. */
        const val VIEW_HALF_WIDTH = 5.5f
        const val VIEW_HALF_HEIGHT = 10f
        private const val STEP = 1f / 60f
        private const val MAX_STEPS_PER_UPDATE = 8
        private const val CELL = 1.5f
        private const val TABLE_SIZE = 4096
        /** Schadenszahlen pro Schritt begrenzen – sonst entstehen bei großen Horden tausende Events. */
        private const val MAX_DAMAGE_EVENTS_PER_STEP = 4
        private const val MAX_ENEMY_RADIUS = 1.9f
        private const val RELOCATE_DISTANCE = 24f

        fun newGame(heroClass: HeroClass, mode: SurvivorsMode, seed: Long): SurvivorsEngine {
            val state = SurvivorsState(heroClass, mode)
            state.weapons[heroClass.startWeapon] = 1
            return SurvivorsEngine(state, seed)
        }

        /** Benötigte Erfahrung für den Aufstieg von [level] auf [level] + 1. */
        fun xpForLevel(level: Int): Int = (5 + level * 4 + level * level * 0.7f).toInt()
    }

    private val random = Random(seed)
    private val spawner = SurvivorsSpawner(this, Random(seed * 31 + 5))
    private val events = ArrayList<SurvivorsEvent>()
    private var accumulator = 0f
    private var damageEventsThisStep = 0

    /** Joystick-Eingabe, Länge 0..1. */
    var inputX = 0f
    var inputY = 0f

    val isOver: Boolean get() = state.dead || state.victory
    /** Eine Kiste wird gerade ausgerollt – hat Vorrang vor Level-up-Karten. */
    val awaitingChest: Boolean get() = state.chestReward != null
    val awaitingChoice: Boolean get() = state.offers.isNotEmpty() && !awaitingChest
    /** Das Spiel steht still, bis der Spieler eine Karte wählt oder eine Kisten-Belohnung annimmt/ablehnt. */
    val isWaiting: Boolean get() = awaitingChest || state.offers.isNotEmpty()
    val stats: HeroStats get() = HeroStats(state)

    val score: Int
        get() = state.killScore + state.time.toInt() + if (state.victory) VICTORY_BONUS_SCORE else 0

    fun hpMultiplier(): Float = spawner.hpMultiplier(state.time)

    fun drainEvents(): List<SurvivorsEvent> {
        if (events.isEmpty()) return emptyList()
        val copy = events.toList()
        events.clear()
        return copy
    }

    internal fun emit(event: SurvivorsEvent) {
        events += event
    }

    fun update(dt: Float) {
        if (isOver || isWaiting) return
        accumulator += dt
        var steps = 0
        while (accumulator >= STEP && steps < MAX_STEPS_PER_UPDATE && !isOver && !isWaiting) {
            step(STEP)
            accumulator -= STEP
            steps++
        }
        if (steps == MAX_STEPS_PER_UPDATE || isWaiting) accumulator = 0f
    }

    /** Wählt eine der angebotenen Level-up-Karten. */
    fun choose(index: Int) {
        if (awaitingChest) return
        val offer = state.offers.getOrNull(index) ?: return
        Upgrades.apply(state, offer)
        state.pendingLevelUps = (state.pendingLevelUps - 1).coerceAtLeast(0)
        state.offers = if (state.pendingLevelUps > 0) Upgrades.offers(state, random) else emptyList()
    }

    /** Nimmt die aktuell ausgerollte Kisten-Belohnung an. */
    fun acceptChestReward() {
        val offer = state.chestReward ?: return
        Upgrades.apply(state, offer)
        if (offer.evolution != null) events += SurvivorsEvent.Evolved(offer.evolution)
        nextChestReward()
    }

    /** Lehnt die Belohnung ab (Build bleibt unverändert) und heilt stattdessen. */
    fun declineChestReward() {
        if (state.chestReward == null) return
        val before = state.hp
        state.hp = (state.hp + CHEST_DECLINE_HEAL).coerceAtMost(stats.maxHp)
        if (state.hp > before) events += SurvivorsEvent.Healed(state.hp - before)
        nextChestReward()
    }

    private fun openChest() {
        val rewards = 1 + (if (random.nextFloat() < 0.4f) 1 else 0) + (if (random.nextFloat() < 0.15f) 1 else 0)
        if (state.chestReward != null) {
            // Zweite Kiste im selben Moment: Belohnungen hinten anhängen.
            state.chestTotal += rewards
            return
        }
        state.chestIndex = 0
        state.chestTotal = rewards
        state.chestEvolutionGiven = false
        rollChestReward()
    }

    private fun nextChestReward() {
        state.chestIndex++
        if (state.chestIndex < state.chestTotal) {
            rollChestReward()
        } else {
            state.chestReward = null
            state.chestIndex = 0
            state.chestTotal = 0
        }
    }

    /**
     * Würfelt die nächste Belohnung auf Basis des aktuellen Builds. Ab dem ersten Boss hat eine bereite
     * Entwicklung Vorrang – höchstens eine pro Kiste.
     */
    private fun rollChestReward() {
        val evolution = if (state.time >= EVOLUTION_MIN_TIME && !state.chestEvolutionGiven) {
            Upgrades.evolutionsReady(state).randomOrNull(random)
        } else null
        val offer = when {
            evolution != null -> {
                state.chestEvolutionGiven = true
                UpgradeOffer(evolution = evolution)
            }
            else -> Upgrades.pool(state).randomOrNull(random) ?: UpgradeOffer(heal = true)
        }
        state.chestReward = offer
        events += SurvivorsEvent.ChestReward(offer, state.chestIndex, state.chestTotal)
    }

    fun spawnEnemy(kind: EnemyKind, x: Float, y: Float, hpMultiplier: Float = 1f, elite: Boolean = false): Enemy {
        val enemy = Enemy(state.nextId++, kind, x, y, kind.hp * hpMultiplier, elite, random.nextFloat() * 6.28f)
        state.enemies += enemy
        return enemy
    }

    fun spawnPickup(kind: PickupKind, x: Float, y: Float): Pickup =
        Pickup(state.nextId++, kind, x, y).also { state.pickups += it }

    /** Gibt Erfahrung, z. B. für Tests. */
    fun addXp(amount: Int) {
        state.xp += amount
        checkLevelUp()
    }

    // --- Simulation ---

    private fun step(dt: Float) {
        val st = stats
        if (state.victorySweep) {
            victorySweepStep(dt, st)
            return
        }
        state.time += dt
        damageEventsThisStep = 0
        movePlayer(dt, st)
        state.invulnerable = (state.invulnerable - dt).coerceAtLeast(0f)
        state.hp = (state.hp + st.regen * dt).coerceAtMost(st.maxHp)
        state.leechPool = (state.leechPool + LEECH_PER_SECOND * dt).coerceAtMost(LEECH_PER_SECOND)

        spawner.update(dt)
        buildGrid()
        moveEnemies(dt, st)
        if (state.dead) return
        fireWeapons(dt, st)
        pendingStrikes(dt, st)
        orbit(dt, st)
        aura(dt, st)
        hazards(dt)
        moveProjectiles(dt)
        removeDeadEnemies(st)
        collect(dt, st)

        state.minuteTimer += dt
        if (state.minuteTimer >= 60f) {
            state.minuteTimer -= 60f
            earnCoins(state.px, state.py, MINUTE_COINS)
            earnRunes(state.px, state.py, MINUTE_RUNES)
        }
    }

    private fun movePlayer(dt: Float, st: HeroStats) {
        val length = hypot(inputX, inputY)
        if (length < 0.05f) return
        val nx = inputX / length
        val ny = inputY / length
        val magnitude = min(1f, length)
        state.px += nx * magnitude * st.speed * dt
        state.py += ny * magnitude * st.speed * dt
        state.faceX = nx
        state.faceY = ny
    }

    // --- Raster für schnelle Nachbarschaftssuche ---

    /*
     * Räumlicher Hash ohne Allokationen pro Schritt: Jede Zelle hat einen Bucket, die Gegner eines Buckets bilden
     * eine verkettete Liste über Indizes. Kollidieren zwei Zellen im selben Bucket, wird über die gespeicherte
     * Zelle gefiltert, damit kein Gegner doppelt getroffen wird.
     */
    private val buckets = IntArray(TABLE_SIZE)
    private var nextInBucket = IntArray(512)
    private var cellXs = IntArray(512)
    private var cellYs = IntArray(512)
    private var gridEnemies = arrayOfNulls<Enemy>(512)
    private var gridCount = 0

    private fun bucket(cx: Int, cy: Int): Int = ((cx * 73856093) xor (cy * 19349663)) and (TABLE_SIZE - 1)

    private fun buildGrid() {
        val n = state.enemies.size
        if (n > gridEnemies.size) {
            val size = n * 2
            nextInBucket = IntArray(size)
            cellXs = IntArray(size)
            cellYs = IntArray(size)
            gridEnemies = arrayOfNulls(size)
        }
        buckets.fill(-1)
        gridCount = n
        for (i in 0 until n) {
            val e = state.enemies[i]
            val cx = floor(e.x / CELL).toInt()
            val cy = floor(e.y / CELL).toInt()
            val b = bucket(cx, cy)
            gridEnemies[i] = e
            cellXs[i] = cx
            cellYs[i] = cy
            nextInBucket[i] = buckets[b]
            buckets[b] = i
        }
    }

    /** Ruft [action] für alle lebenden Gegner auf, deren Rand höchstens [radius] von (x, y) entfernt ist. */
    private inline fun forEnemiesNear(x: Float, y: Float, radius: Float, action: (Enemy) -> Unit) {
        val reach = radius + MAX_ENEMY_RADIUS
        val minX = floor((x - reach) / CELL).toInt()
        val maxX = floor((x + reach) / CELL).toInt()
        val minY = floor((y - reach) / CELL).toInt()
        val maxY = floor((y + reach) / CELL).toInt()
        for (cx in minX..maxX) for (cy in minY..maxY) {
            var i = buckets[bucket(cx, cy)]
            while (i >= 0) {
                val e = gridEnemies[i]!!
                val sameCell = cellXs[i] == cx && cellYs[i] == cy
                i = nextInBucket[i]
                if (!sameCell || e.hp <= 0f) continue
                val dx = e.x - x
                val dy = e.y - y
                val r = radius + e.radius
                if (dx * dx + dy * dy <= r * r) action(e)
            }
        }
    }

    /** Wie [forEnemiesNear], aber ohne Raster – nur für Tests zum Abgleich. */
    fun enemiesNearNaive(x: Float, y: Float, radius: Float): List<Enemy> =
        state.enemies.filter { it.hp > 0f && hypot(it.x - x, it.y - y) <= radius + it.radius }

    fun enemiesNear(x: Float, y: Float, radius: Float): List<Enemy> {
        buildGrid()
        val result = ArrayList<Enemy>()
        forEnemiesNear(x, y, radius) { result += it }
        return result
    }

    // --- Gegner ---

    private fun moveEnemies(dt: Float, st: HeroStats) {
        val hitScale = 1f + state.time / 600f
        for (e in state.enemies) {
            val dx = state.px - e.x
            val dy = state.py - e.y
            val d = hypot(dx, dy)
            if (d > RELOCATE_DISTANCE && !e.isBoss) {
                // Zu weit abgehängt: vor dem Helden neu auftauchen lassen.
                val angle = atan2(state.faceY, state.faceX) + (random.nextFloat() - 0.5f) * 1.6f
                e.x = state.px + cos(angle) * SurvivorsSpawner.SPAWN_DISTANCE
                e.y = state.py + sin(angle) * SurvivorsSpawner.SPAWN_DISTANCE
                continue
            }
            val frozen = e.freezeTimer > 0f
            val speed = e.kind.speed * (if (e.slowTimer > 0f) 0.5f else 1f) * (if (e.elite) 0.9f else 1f)
            if (d > 0.01f && !frozen) {
                e.x += dx / d * speed * dt
                e.y += dy / d * speed * dt
                if (e.kind == EnemyKind.BAT) {
                    val wobble = sin(state.time * 4f + e.phase) * 1.2f * dt
                    e.x += -dy / d * wobble
                    e.y += dx / d * wobble
                }
            }
            e.slowTimer = (e.slowTimer - dt).coerceAtLeast(0f)
            e.freezeTimer = (e.freezeTimer - dt).coerceAtLeast(0f)
            e.hitFlash = (e.hitFlash - dt * 6f).coerceAtLeast(0f)
            e.orbitCooldown = (e.orbitCooldown - dt).coerceAtLeast(0f)

            if (!frozen && d < e.radius + PLAYER_RADIUS && state.invulnerable <= 0f) {
                val raw = e.kind.damage * (if (e.elite) 1.5f else 1f) * hitScale
                val damage = (raw - st.armor).coerceAtLeast(1f)
                state.hp -= damage
                state.invulnerable = INVULNERABLE_TIME
                events += SurvivorsEvent.PlayerHit(damage)
                if (state.hp <= 0f) {
                    state.hp = 0f
                    state.dead = true
                    return
                }
            }
        }
        separate()
    }

    /** Gegner in derselben Rasterzelle schieben sich leicht auseinander (Geister schweben durch). */
    private fun separate() {
        for (i in 0 until gridCount) {
            val a = gridEnemies[i]!!
            if (a.kind == EnemyKind.GHOST) continue
            // Nur Nachfolger im selben Bucket und derselben Zelle prüfen → jedes Paar genau einmal.
            var j = nextInBucket[i]
            while (j >= 0) {
                val b = gridEnemies[j]!!
                val sameCell = cellXs[j] == cellXs[i] && cellYs[j] == cellYs[i]
                j = nextInBucket[j]
                run {
                    if (!sameCell || b.kind == EnemyKind.GHOST) return@run
                    val dx = b.x - a.x
                    val dy = b.y - a.y
                    val d = hypot(dx, dy)
                    val minD = (a.radius + b.radius) * 0.9f
                    if (d < minD && d > 0.0001f) {
                        val push = (minD - d) * 0.5f
                        val ax = dx / d * push
                        val ay = dy / d * push
                        if (!a.isBoss) { a.x -= ax; a.y -= ay }
                        if (!b.isBoss) { b.x += ax; b.y += ay }
                    }
                }
            }
        }
    }

    private fun damage(e: Enemy, raw: Float) {
        if (e.hp <= 0f) return
        // Eiszeit: eingefrorene Gegner zerbrechen leichter.
        val amount = if (e.freezeTimer > 0f) raw * FROZEN_DAMAGE_BONUS else raw
        e.hp -= amount
        e.hitFlash = 1f
        if (damageEventsThisStep++ < MAX_DAMAGE_EVENTS_PER_STEP) events += SurvivorsEvent.Damage(e.x, e.y, amount)
    }

    private fun removeDeadEnemies(st: HeroStats) {
        var demonDefeated = false
        val iterator = state.enemies.iterator()
        while (iterator.hasNext()) {
            val e = iterator.next()
            if (e.hp > 0f) continue
            iterator.remove()
            state.kills++
            state.killScore += e.kind.score * if (e.elite) 5 else 1
            events += SurvivorsEvent.EnemyKilled(e.x, e.y, e.kind, e.elite)
            if (state.heroClass.healPerKill > 0f) {
                state.hp = (state.hp + state.heroClass.healPerKill).coerceAtMost(st.maxHp)
            }
            val xp = e.kind.xp * if (e.elite) 5 else 1
            if (xp > 0) dropGem(e.x, e.y, xp)
            when {
                e.kind == EnemyKind.DEMON -> {
                    demonDefeated = true
                    earnCoins(e.x, e.y, VICTORY_COINS)
                    earnRunes(e.x, e.y, VICTORY_RUNES)
                    events += SurvivorsEvent.Victory
                }
                e.kind == EnemyKind.BOSS -> {
                    spawnPickup(PickupKind.CHEST, e.x, e.y)
                    earnCoins(e.x, e.y, BOSS_COINS)
                    earnRunes(e.x, e.y, BOSS_RUNES)
                    state.bossesKilled++
                }
                e.elite -> {
                    spawnPickup(PickupKind.CHEST, e.x, e.y)
                    earnCoins(e.x, e.y, ELITE_COINS)
                    earnRunes(e.x, e.y, ELITE_RUNES)
                }
                else -> {
                    val roll = random.nextFloat()
                    when {
                        roll < 0.004f -> spawnPickup(PickupKind.POTION, e.x, e.y)
                        roll < 0.0055f -> spawnPickup(PickupKind.MAGNET, e.x, e.y)
                        roll < 0.02f -> spawnPickup(PickupKind.COIN, e.x, e.y)
                    }
                }
            }
        }
        if (demonDefeated) startVictorySweep()
    }

    private fun dropGem(x: Float, y: Float, value: Int) {
        if (state.gems.size >= MAX_GEMS) {
            // Zu viele Kristalle: in einen bestehenden einrechnen, damit nichts verloren geht.
            state.gems[random.nextInt(state.gems.size)].value += value
        } else {
            state.gems += Gem(x, y, value)
        }
    }

    private fun earnCoins(x: Float, y: Float, amount: Int) {
        state.coinsEarned += amount
        events += SurvivorsEvent.CoinsEarned(x, y, amount)
    }

    private fun earnRunes(x: Float, y: Float, amount: Int) {
        state.runesEarned += amount
        events += SurvivorsEvent.RunesEarned(x, y, amount)
    }

    // --- Waffen ---

    fun isEvolved(weapon: WeaponKind): Boolean = weapon in state.evolved

    private fun fireWeapons(dt: Float, st: HeroStats) {
        for ((weapon, level) in state.weapons) {
            if (weapon == WeaponKind.BONE_ORBIT || weapon == WeaponKind.AURA) continue
            val cd = (state.cooldowns[weapon] ?: 0f) - dt
            if (cd > 0f) {
                state.cooldowns[weapon] = cd
                continue
            }
            val fired = fire(weapon, level, st)
            state.cooldowns[weapon] = if (fired) cooldown(weapon, level, st) else 0.2f
        }
    }

    private fun cooldown(weapon: WeaponKind, level: Int, st: HeroStats): Float {
        val evolvedFactor = if (weapon == WeaponKind.ARROWS && isEvolved(weapon)) 0.65f else 1f
        return weapon.cooldown(level) * st.cooldownMultiplier * evolvedFactor
    }

    private fun count(weapon: WeaponKind, level: Int, st: HeroStats): Int =
        weapon.count(level) + if (weapon.usesExtraProjectiles) st.extraProjectiles else 0

    /** Feuert eine Waffe; false, wenn es kein Ziel gab (dann bald erneut versuchen). */
    private fun fire(weapon: WeaponKind, level: Int, st: HeroStats): Boolean {
        val damage = weapon.damage(level) * st.damageMultiplier
        when (weapon) {
            WeaponKind.SWORD -> {
                swordSwing(level, st, damage, targetIndex = 0)
                // Vielfalt: Folgeschläge auf die nächsten Gegner
                repeat(st.extraProjectiles) { i ->
                    state.pendingStrikes += PendingStrike(WeaponKind.SWORD, delay = 0.12f * (i + 1), targetIndex = i + 1)
                }
                return true
            }
            WeaponKind.FIREBALL -> {
                if (isEvolved(weapon)) return meteors(level, st)
                val targets = nearestEnemies(count(weapon, level, st), 11f)
                if (targets.isEmpty()) return false
                targets.forEach { t ->
                    shoot(weapon, t.x, t.y, speed = 6f, damage = damage, radius = 0.3f, pierce = 0, life = 2.5f,
                        explosionRadius = weapon.area(level) * st.areaMultiplier)
                }
                return true
            }
            WeaponKind.ARROWS -> {
                val target = nearestEnemies(1, 11f * st.areaMultiplier).firstOrNull() ?: return false
                val n = count(weapon, level, st)
                val base = atan2(target.y - state.py, target.x - state.px)
                val pierce = weapon.pierce(level) + if (isEvolved(weapon)) STORM_BOW_PIERCE else 0
                repeat(n) { i ->
                    val angle = base + (i - (n - 1) / 2f) * 0.12f
                    shootAngle(weapon, angle, speed = 11f, damage = damage, radius = 0.2f * st.areaMultiplier, pierce = pierce,
                        life = 1.4f * st.areaMultiplier)
                }
                return true
            }
            WeaponKind.LIGHTNING -> {
                val visible = visibleEnemies()
                if (visible.isEmpty()) return false
                val radius = weapon.area(level) * st.areaMultiplier
                val chain = isEvolved(weapon)
                visible.shuffled(random).take(count(weapon, level, st)).forEach { t ->
                    val x = t.x
                    val y = t.y
                    forEnemiesNear(x, y, radius) { damage(it, damage) }
                    events += SurvivorsEvent.Lightning(x, y, radius)
                    if (chain) chainLightning(t, damage * 0.5f)
                }
                return true
            }
            WeaponKind.DAGGERS -> {
                val n = count(weapon, level, st)
                val radius = 0.18f * st.areaMultiplier
                val life = 0.9f * st.areaMultiplier
                if (isEvolved(weapon)) {
                    // Klingensturm: Dolche in alle Richtungen, die umkehren und erneut treffen
                    val total = 4 + 2 * n
                    val base = atan2(state.faceY, state.faceX) + state.time * 1.3f
                    repeat(total) { i ->
                        shootAngle(weapon, base + i * 2f * PI.toFloat() / total, speed = 11f, damage = damage, radius = radius,
                            pierce = weapon.pierce(level) + 2, life = life * 1.2f, returnAt = life * 0.6f)
                    }
                } else {
                    val base = atan2(state.faceY, state.faceX)
                    repeat(n) { i ->
                        val angle = base + (i - (n - 1) / 2f) * 0.15f
                        shootAngle(weapon, angle, speed = 13f, damage = damage, radius = radius, pierce = weapon.pierce(level), life = life)
                    }
                }
                return true
            }
            WeaponKind.FROST_NOVA -> {
                nova(level, st, damage)
                // Vielfalt: Echo-Wellen
                repeat(st.extraProjectiles) { i ->
                    state.pendingStrikes += PendingStrike(WeaponKind.FROST_NOVA, delay = 0.35f * (i + 1), damageFactor = 0.6f)
                }
                return true
            }
            WeaponKind.BONE_ORBIT, WeaponKind.AURA -> return true
        }
    }

    private fun swordSwing(level: Int, st: HeroStats, damage: Float, targetIndex: Int) {
        val weapon = WeaponKind.SWORD
        val evolved = isEvolved(weapon)
        val range = weapon.area(level) * st.areaMultiplier * if (evolved) 1.25f else 1f
        val hit = damage * if (evolved) 1.6f else 1f
        // Schlägt Richtung nächstem Gegner (sonst in Laufrichtung) – wer flieht, trifft trotzdem.
        // Folgeschläge peilen den zweit-, dritt- … nächsten Gegner an.
        val targets = nearestEnemies(targetIndex + 1, range * 1.6f)
        val target = targets.getOrNull(targetIndex) ?: targets.lastOrNull()
        val facing = if (target != null) atan2(target.y - state.py, target.x - state.px)
            else atan2(state.faceY, state.faceX) + targetIndex * 2.4f
        val fullCircle = evolved || weapon.doubleSwing(level)
        forEnemiesNear(state.px, state.py, range) { e ->
            val angle = atan2(e.y - state.py, e.x - state.px)
            if (fullCircle || angleDiff(angle, facing) <= PI.toFloat() / 2f) damage(e, hit)
        }
        events += SurvivorsEvent.Swing(state.px, state.py, facing, range, fullCircle, evolved)
    }

    private fun nova(level: Int, st: HeroStats, damage: Float) {
        val radius = WeaponKind.FROST_NOVA.area(level) * st.areaMultiplier
        val freeze = isEvolved(WeaponKind.FROST_NOVA)
        forEnemiesNear(state.px, state.py, radius) { e ->
            damage(e, damage)
            e.slowTimer = 2f
            if (freeze && !e.isBoss) e.freezeTimer = FREEZE_TIME
        }
        events += SurvivorsEvent.Nova(state.px, state.py, radius, freeze)
    }

    /** Meteorregen: Einschläge auf die dichtesten Gruppen, mit kurzer Vorwarnung. */
    private fun meteors(level: Int, st: HeroStats): Boolean {
        val visible = visibleEnemies()
        if (visible.isEmpty()) return false
        val candidates = visible.shuffled(random).take(16).map { c ->
            var neighbours = 0
            forEnemiesNear(c.x, c.y, 1.5f) { neighbours++ }
            c to neighbours
        }.sortedByDescending { it.second }
        candidates.take(count(WeaponKind.FIREBALL, level, st)).forEachIndexed { i, (t, _) ->
            state.pendingStrikes += PendingStrike(WeaponKind.FIREBALL, delay = METEOR_DELAY + i * 0.08f, damageFactor = 1.2f, x = t.x, y = t.y)
        }
        return true
    }

    fun meteorRadius(): Float {
        val level = state.weapons[WeaponKind.FIREBALL] ?: return 0f
        return WeaponKind.FIREBALL.area(level) * stats.areaMultiplier * 1.8f
    }

    /** Gewittersturm: springt vom getroffenen Gegner auf bis zu 3 Nachbarn über. */
    private fun chainLightning(start: Enemy, damage: Float) {
        var fx = start.x
        var fy = start.y
        val hit = HashSet<Long>()
        hit += start.id
        for (jump in 0 until 3) {
            var next: Enemy? = null
            var best = Float.MAX_VALUE
            forEnemiesNear(fx, fy, 3f) { e ->
                if (e.id !in hit) {
                    val d = (e.x - fx) * (e.x - fx) + (e.y - fy) * (e.y - fy)
                    if (d < best) {
                        best = d
                        next = e
                    }
                }
            }
            val n = next ?: break
            damage(n, damage)
            events += SurvivorsEvent.Chain(fx, fy, n.x, n.y)
            hit += n.id
            fx = n.x
            fy = n.y
        }
    }

    /** Verzögerte Einsätze abarbeiten: Folgeschläge, Frost-Echos, Meteore. */
    private fun pendingStrikes(dt: Float, st: HeroStats) {
        if (state.pendingStrikes.isEmpty()) return
        val due = ArrayList<PendingStrike>()
        val iterator = state.pendingStrikes.iterator()
        while (iterator.hasNext()) {
            val p = iterator.next()
            p.delay -= dt
            if (p.delay <= 0f) {
                iterator.remove()
                due += p
            }
        }
        for (p in due) {
            val level = state.weapons[p.weapon] ?: continue
            val damage = p.weapon.damage(level) * st.damageMultiplier * p.damageFactor
            when (p.weapon) {
                WeaponKind.SWORD -> swordSwing(level, st, damage, p.targetIndex)
                WeaponKind.FROST_NOVA -> nova(level, st, damage)
                WeaponKind.FIREBALL -> {
                    val radius = meteorRadius()
                    forEnemiesNear(p.x, p.y, radius) { damage(it, damage) }
                    events += SurvivorsEvent.Explosion(p.x, p.y, radius)
                    if (state.hazards.size >= MAX_HAZARDS) state.hazards.removeAt(0)
                    state.hazards += Hazard(p.x, p.y, radius * 0.8f, damage * 0.12f, life = 2f)
                }
                else -> Unit
            }
        }
    }

    private fun hazards(dt: Float) {
        if (state.hazards.isEmpty()) return
        val iterator = state.hazards.iterator()
        while (iterator.hasNext()) {
            val h = iterator.next()
            h.life -= dt
            if (h.life <= 0f) {
                iterator.remove()
                continue
            }
            h.tick -= dt
            if (h.tick <= 0f) {
                h.tick = 0.4f
                forEnemiesNear(h.x, h.y, h.radius) { damage(it, h.damage) }
            }
        }
    }

    /** Heilung durch Entwicklungen, begrenzt durch den Lebensraub-Vorrat. */
    private fun leech(amount: Float, st: HeroStats) {
        val heal = min(amount, state.leechPool)
        if (heal <= 0f) return
        state.leechPool -= heal
        state.hp = (state.hp + heal).coerceAtMost(st.maxHp)
    }

    /** Positionen der Orbit-Schädel (auch für die Darstellung). */
    fun orbitPositions(): List<Pair<Float, Float>> {
        val level = state.weapons[WeaponKind.BONE_ORBIT] ?: return emptyList()
        val st = stats
        val n = count(WeaponKind.BONE_ORBIT, level, st) + if (isEvolved(WeaponKind.BONE_ORBIT)) 2 else 0
        val radius = WeaponKind.BONE_ORBIT.area(level) * st.areaMultiplier
        val base = state.orbitAngle
        return List(n) { i ->
            val angle = base + i * 2f * PI.toFloat() / n
            (state.px + cos(angle) * radius) to (state.py + sin(angle) * radius)
        }
    }

    private fun orbit(dt: Float, st: HeroStats) {
        val level = state.weapons[WeaponKind.BONE_ORBIT] ?: return
        // Weisheit: schnellere Drehung und kürzere Treffer-Sperre
        state.orbitAngle = (state.orbitAngle + dt * 3f / st.cooldownMultiplier) % (2f * PI.toFloat())
        val evolved = isEvolved(WeaponKind.BONE_ORBIT)
        val damage = WeaponKind.BONE_ORBIT.damage(level) * st.damageMultiplier * if (evolved) 1.15f else 1f
        val hitCooldown = 0.35f * st.cooldownMultiplier
        for ((x, y) in orbitPositions()) {
            forEnemiesNear(x, y, 0.35f) { e ->
                if (e.orbitCooldown <= 0f) {
                    damage(e, damage)
                    e.orbitCooldown = hitCooldown
                    if (evolved) leech(0.5f, st)
                }
            }
        }
    }

    /** Radius der Heiligen Aura (0 = keine). */
    fun auraRadius(): Float {
        val level = state.weapons[WeaponKind.AURA] ?: return 0f
        return WeaponKind.AURA.area(level) * stats.areaMultiplier * if (isEvolved(WeaponKind.AURA)) 1.25f else 1f
    }

    private fun aura(dt: Float, st: HeroStats) {
        val level = state.weapons[WeaponKind.AURA] ?: return
        state.auraTimer -= dt
        if (state.auraTimer > 0f) return
        // Weisheit: schnellerer Takt · Vielfalt: zusätzliche Lichtstrahlen = mehr Schaden
        state.auraTimer = WeaponKind.AURA.cooldown(level) * st.cooldownMultiplier
        val evolved = isEvolved(WeaponKind.AURA)
        val damage = WeaponKind.AURA.damage(level) * st.damageMultiplier * (1f + 0.12f * st.extraProjectiles) *
            if (evolved) 1.1f else 1f
        var hits = 0
        forEnemiesNear(state.px, state.py, auraRadius()) {
            damage(it, damage)
            hits++
        }
        if (evolved && hits > 0) leech(0.25f * hits, st)
    }

    private fun visibleEnemies(): List<Enemy> = state.enemies.filter {
        it.hp > 0f && abs(it.x - state.px) < VIEW_HALF_WIDTH && abs(it.y - state.py) < VIEW_HALF_HEIGHT
    }

    private fun nearestEnemies(n: Int, maxDistance: Float): List<Enemy> =
        state.enemies.asSequence()
            .filter { it.hp > 0f }
            .map { it to hypot(it.x - state.px, it.y - state.py) }
            .filter { it.second <= maxDistance }
            .sortedBy { it.second }
            .take(n)
            .map { it.first }
            .toList()

    private fun shoot(
        weapon: WeaponKind, tx: Float, ty: Float, speed: Float, damage: Float, radius: Float, pierce: Int, life: Float,
        explosionRadius: Float = 0f,
    ) = shootAngle(weapon, atan2(ty - state.py, tx - state.px), speed, damage, radius, pierce, life, explosionRadius)

    private fun shootAngle(
        weapon: WeaponKind, angle: Float, speed: Float, damage: Float, radius: Float, pierce: Int, life: Float,
        explosionRadius: Float = 0f, returnAt: Float = 0f,
    ) {
        state.projectiles += Projectile(
            id = state.nextId++,
            weapon = weapon,
            x = state.px,
            y = state.py,
            vx = cos(angle) * speed,
            vy = sin(angle) * speed,
            damage = damage,
            radius = radius,
            pierce = pierce,
            life = life,
            explosionRadius = explosionRadius,
            returnAt = returnAt,
        )
    }

    private fun moveProjectiles(dt: Float) {
        for (p in state.projectiles) {
            if (!p.alive) continue
            p.x += p.vx * dt
            p.y += p.vy * dt
            p.life -= dt
            if (p.life <= 0f) {
                p.alive = false
                continue
            }
            if (p.returnAt > 0f && !p.returned && p.life <= p.returnAt) {
                // Klingensturm: umkehren und auf dem Rückweg erneut treffen
                p.vx = -p.vx
                p.vy = -p.vy
                p.returned = true
                p.hitIds.clear()
            }
            var hit: Enemy? = null
            forEnemiesNear(p.x, p.y, p.radius) { e -> if (hit == null && e.id !in p.hitIds) hit = e }
            val target = hit ?: continue
            if (p.explosionRadius > 0f) {
                forEnemiesNear(p.x, p.y, p.explosionRadius) { damage(it, p.damage) }
                events += SurvivorsEvent.Explosion(p.x, p.y, p.explosionRadius)
                p.alive = false
            } else {
                damage(target, p.damage)
                p.hitIds += target.id
                if (p.pierce > 0) p.pierce-- else p.alive = false
            }
        }
        state.projectiles.removeAll { !it.alive }
    }

    // --- Einsammeln & Aufstieg ---

    /** Zieht einen Drop Richtung Held; liefert die Entfernung vor dem Zug. */
    private inline fun pull(x: Float, y: Float, dt: Float, move: (Float, Float) -> Unit): Float {
        val dx = state.px - x
        val dy = state.py - y
        val d = hypot(dx, dy)
        if (d > 0.001f) {
            // In der Siegesrunde fliegen auch weit entfernte Drops zügig heran.
            val speed = if (state.victorySweep) maxOf(SWEEP_PULL_SPEED, d * 4f) else PULL_SPEED
            val step = min(d, speed * dt)
            move(dx / d * step, dy / d * step)
        }
        return d
    }

    private fun collect(dt: Float, st: HeroStats) {
        val gems = state.gems.iterator()
        while (gems.hasNext()) {
            val g = gems.next()
            val d = hypot(state.px - g.x, state.py - g.y)
            if (d < st.pickupRadius || state.victorySweep) g.magnetized = true
            if (g.magnetized) pull(g.x, g.y, dt) { mx, my -> g.x += mx; g.y += my }
            if (d < GEM_PICKUP_DISTANCE) {
                gems.remove()
                state.xp += g.value
            }
        }
        // Nach dem Sieg gibt es keine Level-ups mehr (das Spiel ist vorbei).
        if (!state.victorySweep) checkLevelUp()

        val pickups = state.pickups.iterator()
        while (pickups.hasNext()) {
            val p = pickups.next()
            val d = hypot(state.px - p.x, state.py - p.y)
            // Gold wird wie Kristalle vom Sammelradius angezogen; in der Siegesrunde alles.
            if (state.victorySweep || (p.kind == PickupKind.COIN && d < st.pickupRadius)) p.magnetized = true
            if (p.magnetized) pull(p.x, p.y, dt) { mx, my -> p.x += mx; p.y += my }
            if (d > PICKUP_DISTANCE) continue
            pickups.remove()
            when (p.kind) {
                PickupKind.POTION -> {
                    val before = state.hp
                    state.hp = (state.hp + 30f).coerceAtMost(st.maxHp)
                    if (state.hp > before) events += SurvivorsEvent.Healed(state.hp - before)
                }
                PickupKind.COIN -> earnCoins(p.x, p.y, 1)
                PickupKind.MAGNET -> {
                    state.gems.forEach { it.magnetized = true }
                    state.pickups.forEach { if (it.kind == PickupKind.COIN) it.magnetized = true }
                }
                // Nach dem Sieg wird eine Truhe nicht mehr geöffnet, sondern in Coins umgewandelt.
                PickupKind.CHEST -> if (state.victorySweep) earnCoins(p.x, p.y, ELITE_COINS) else openChest()
            }
        }
    }

    /** Endboss besiegt: Bühne räumen und alle Drops einsammeln lassen. */
    private fun startVictorySweep() {
        state.victorySweep = true
        state.victorySweepTime = 0f
        state.enemies.clear()
        state.projectiles.clear()
        state.pendingStrikes.clear()
        state.hazards.clear()
        state.gems.forEach { it.magnetized = true }
        state.pickups.forEach { it.magnetized = true }
    }

    private fun victorySweepStep(dt: Float, st: HeroStats) {
        state.victorySweepTime += dt
        movePlayer(dt, st)
        collect(dt, st)
        val done = state.gems.isEmpty() && state.pickups.isEmpty() && state.victorySweepTime >= VICTORY_SWEEP_MIN_TIME
        if (done || state.victorySweepTime >= VICTORY_SWEEP_MAX_TIME) {
            // Was bis dahin nicht angekommen ist, wird direkt gutgeschrieben.
            state.gems.forEach { state.xp += it.value }
            state.gems.clear()
            state.pickups.forEach { p ->
                when (p.kind) {
                    PickupKind.COIN -> earnCoins(p.x, p.y, 1)
                    PickupKind.CHEST -> earnCoins(p.x, p.y, ELITE_COINS)
                    else -> Unit
                }
            }
            state.pickups.clear()
            state.victorySweep = false
            state.victory = true
        }
    }

    private fun checkLevelUp() {
        var leveled = false
        while (state.xp >= xpForLevel(state.level)) {
            state.xp -= xpForLevel(state.level)
            state.level++
            state.pendingLevelUps++
            leveled = true
        }
        if (leveled && state.offers.isEmpty()) {
            state.offers = Upgrades.offers(state, random)
            events += SurvivorsEvent.LevelUp
        }
    }

    private fun angleDiff(a: Float, b: Float): Float {
        var d = abs(a - b) % (2f * PI.toFloat())
        if (d > PI.toFloat()) d = 2f * PI.toFloat() - d
        return d
    }
}
