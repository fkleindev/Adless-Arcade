package com.adlessarcade.games.survivors

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/**
 * Eigene Angriffe der Gegner (Friedhof): Knochenschützen zielen und schießen, Grabhexen legen Fluchkreise und die
 * Bosse greifen in Mustern mit Warn-Indikatoren an. Alles, was den Helden trifft, ist vorher sichtbar angekündigt.
 */
internal class SurvivorsEnemyAttacks(private val e: SurvivorsEngine, private val random: Random) {
    companion object {
        /** Wunschabstand der Fernkämpfer zum Helden. */
        const val KEEP_DISTANCE = 5f
        const val ARCHER_RANGE = 7.5f
        const val ARCHER_AIM_TIME = 0.7f
        const val ARROW_SPEED = 7.5f
        const val WITCH_RANGE = 8f
        const val WITCH_CAST_TIME = 1.1f
        const val GUARDIAN_DASH_TIME = 0.45f
        const val GUARDIAN_DASH_LENGTH = 8f
        private const val MAX_SHOTS = 500
        private const val MAX_ATTACKS = 200
        /** Zielgenauigkeit: Der Held gilt als getroffen, wenn er mit halbem Radius in der Fläche steht. */
        private const val HIT_MARGIN = SurvivorsEngine.PLAYER_RADIUS * 0.5f
    }

    private val state get() = e.state
    /** Beschworene Skelette – erst nach der Schleife über die Gegner hinzufügen. */
    private val summons = ArrayList<Pair<Float, Float>>()

    fun update(dt: Float, st: HeroStats) {
        val scale = e.enemyHitScale()
        summons.clear()
        for (enemy in state.enemies) {
            if (enemy.hp <= 0f) continue
            if (enemy.freezeTimer > 0f || enemy.stunTimer > 0f) continue
            when (enemy.kind) {
                EnemyKind.ARCHER -> archer(enemy, dt, scale)
                EnemyKind.WITCH -> witch(enemy, dt, scale)
                EnemyKind.GRAVE_GUARDIAN -> guardian(enemy, dt, scale)
                EnemyKind.LICH -> lich(enemy, dt, scale)
                else -> Unit
            }
        }
        if (summons.isNotEmpty()) {
            val hp = e.hpMultiplier()
            summons.forEach { (x, y) -> e.spawnEnemy(EnemyKind.SKELETON, x, y, hp) }
        }
        resolveAttacks(dt, st)
        moveShots(dt, st)
    }

    private fun distanceToHero(enemy: Enemy) = hypot(state.px - enemy.x, state.py - enemy.y)

    private fun addAttack(attack: EnemyAttack) {
        if (state.enemyAttacks.size < MAX_ATTACKS) state.enemyAttacks += attack
    }

    // --- Gegner ---

    /** Knochenschütze: zielt kurz (Ziellinie), dann fliegt ein Pfeil in diese Richtung. */
    private fun archer(enemy: Enemy, dt: Float, scale: Float) {
        if (enemy.windup > 0f) {
            enemy.windup -= dt
            if (enemy.windup <= 0f) {
                enemy.windup = 0f
                shoot(enemy.x, enemy.y, enemy.dashVx, enemy.dashVy, ARROW_SPEED, 0.22f, 5f * scale, soul = false)
            }
            return
        }
        enemy.attackTimer -= dt
        val d = distanceToHero(enemy)
        if (enemy.attackTimer <= 0f && d < ARCHER_RANGE) {
            enemy.dashVx = (state.px - enemy.x) / d
            enemy.dashVy = (state.py - enemy.y) / d
            enemy.windup = ARCHER_AIM_TIME
            enemy.attackTimer = 3.5f + random.nextFloat()
        }
    }

    /** Grabhexe: Fluchkreis unter dem Helden, schlägt nach kurzer Vorwarnung ein. */
    private fun witch(enemy: Enemy, dt: Float, scale: Float) {
        if (enemy.windup > 0f) enemy.windup = (enemy.windup - dt).coerceAtLeast(0f)
        enemy.attackTimer -= dt
        if (enemy.attackTimer <= 0f && distanceToHero(enemy) < WITCH_RANGE) {
            addAttack(EnemyAttack(AttackShape.CIRCLE, state.px, state.py, 1.2f, WITCH_CAST_TIME, 8f * scale))
            enemy.windup = 0.5f
            enemy.attackTimer = 5f + random.nextFloat() * 2f
        }
    }

    // --- Bosse ---

    /** Grabwächter: abwechselnd Sturmangriff (Linie) und Erdstoß (Kreis um ihn herum). */
    private fun guardian(enemy: Enemy, dt: Float, scale: Float) {
        if (enemy.windup > 0f) {
            enemy.windup -= dt
            if (enemy.windup <= 0f) {
                enemy.windup = 0f
                // Sturmangriff vorbereitet → jetzt losrennen
                if (enemy.dashTime < 0f) enemy.dashTime = GUARDIAN_DASH_TIME
            }
            return
        }
        if (enemy.dashTime > 0f) return
        enemy.attackTimer -= dt
        if (enemy.attackTimer > 0f) return
        val d = distanceToHero(enemy).coerceAtLeast(0.01f)
        if (enemy.attackStep % 2 == 0) {
            val dx = (state.px - enemy.x) / d
            val dy = (state.py - enemy.y) / d
            val speed = GUARDIAN_DASH_LENGTH / GUARDIAN_DASH_TIME
            enemy.dashVx = dx * speed
            enemy.dashVy = dy * speed
            enemy.dashTime = -1f // wartet auf das Ende des Ausholens
            enemy.windup = 0.9f
            addAttack(
                EnemyAttack(
                    AttackShape.LINE, enemy.x, enemy.y, enemy.radius * 0.9f, 0.9f + GUARDIAN_DASH_TIME * 0.5f, 18f * scale,
                    x2 = enemy.x + dx * GUARDIAN_DASH_LENGTH, y2 = enemy.y + dy * GUARDIAN_DASH_LENGTH,
                )
            )
        } else {
            enemy.windup = 1f
            addAttack(EnemyAttack(AttackShape.CIRCLE, enemy.x, enemy.y, 2.6f, 1f, 16f * scale))
        }
        enemy.attackStep++
        enemy.attackTimer = 4.5f
    }

    /** Lichkönig: Knochenfeld, Seelenring, Todesstrahl und Beschwörung im Wechsel; unter 50 % Leben schneller. */
    private fun lich(enemy: Enemy, dt: Float, scale: Float) {
        if (enemy.windup > 0f) {
            enemy.windup = (enemy.windup - dt).coerceAtLeast(0f)
            return
        }
        enemy.attackTimer -= dt
        if (enemy.attackTimer > 0f) return
        val enraged = enemy.hp < enemy.maxHp * 0.5f
        when (enemy.attackStep % 4) {
            0 -> {
                // Knochenfeld: ein Kreis direkt unter dem Helden, weitere drumherum
                val n = if (enraged) 6 else 4
                addAttack(EnemyAttack(AttackShape.CIRCLE, state.px, state.py, 1.3f, 1.2f, 15f * scale))
                repeat(n - 1) { i ->
                    val a = i * 2f * PI.toFloat() / (n - 1) + random.nextFloat() * 0.6f
                    val r = 1.8f + random.nextFloat() * 1.2f
                    addAttack(EnemyAttack(AttackShape.CIRCLE, state.px + cos(a) * r, state.py + sin(a) * r, 1.3f, 1.2f, 15f * scale))
                }
                enemy.windup = 0.6f
            }
            1 -> {
                // Seelenring: Kugeln in alle Richtungen mit einer Lücke
                val count = 16
                val gap = random.nextInt(count)
                val toHero = atan2(state.py - enemy.y, state.px - enemy.x)
                repeat(count) { i ->
                    if ((i - gap + count) % count < 3) return@repeat
                    val a = toHero + i * 2f * PI.toFloat() / count
                    shoot(enemy.x, enemy.y, cos(a), sin(a), 5f, 0.3f, 12f * scale, soul = true)
                }
                enemy.windup = 0.6f
            }
            2 -> {
                // Todesstrahl: breite Linie Richtung Held
                val d = distanceToHero(enemy).coerceAtLeast(0.01f)
                val dx = (state.px - enemy.x) / d
                val dy = (state.py - enemy.y) / d
                addAttack(
                    EnemyAttack(
                        AttackShape.LINE, enemy.x, enemy.y, 0.8f, 1.1f, 22f * scale,
                        x2 = enemy.x + dx * 14f, y2 = enemy.y + dy * 14f,
                    )
                )
                enemy.windup = 1.1f
            }
            else -> {
                // Beschwörung: Skelette erheben sich rund um den Lichkönig
                repeat(6) { i ->
                    val a = i * PI.toFloat() / 3f
                    summons += enemy.x + cos(a) * 2f to enemy.y + sin(a) * 2f
                }
                e.emit(SurvivorsEvent.Nova(enemy.x, enemy.y, 2.5f))
                enemy.windup = 0.5f
            }
        }
        enemy.attackStep++
        enemy.attackTimer = if (enraged) 2.4f else 3.2f
    }

    // --- Einschläge & Geschosse ---

    private fun shoot(x: Float, y: Float, dirX: Float, dirY: Float, speed: Float, radius: Float, damage: Float, soul: Boolean) {
        if (state.enemyShots.size >= MAX_SHOTS) return
        state.enemyShots += EnemyShot(x, y, dirX * speed, dirY * speed, radius, damage, life = 3.5f, soul = soul)
    }

    private fun resolveAttacks(dt: Float, st: HeroStats) {
        if (state.enemyAttacks.isEmpty()) return
        val iterator = state.enemyAttacks.iterator()
        while (iterator.hasNext()) {
            val a = iterator.next()
            a.delay -= dt
            if (a.delay > 0f) continue
            iterator.remove()
            e.emit(SurvivorsEvent.AttackImpact(a))
            if (hits(a, state.px, state.py)) e.hurtPlayer(a.damage, st)
            if (state.dead) return
        }
    }

    /** Steht (x, y) in der Fläche des Angriffs? */
    fun hits(a: EnemyAttack, x: Float, y: Float): Boolean = when (a.shape) {
        AttackShape.CIRCLE -> hypot(x - a.x, y - a.y) < a.radius + HIT_MARGIN
        AttackShape.LINE -> distanceToSegment(x, y, a.x, a.y, a.x2, a.y2) < a.radius + HIT_MARGIN
    }

    private fun moveShots(dt: Float, st: HeroStats) {
        if (state.enemyShots.isEmpty()) return
        val obstacles = e.obstacles
        val iterator = state.enemyShots.iterator()
        while (iterator.hasNext()) {
            val s = iterator.next()
            s.x += s.vx * dt
            s.y += s.vy * dt
            s.life -= dt
            when {
                s.life <= 0f -> iterator.remove()
                // Grüfte und Grabsteine halten Geschosse auf – Deckung!
                obstacles.isNotEmpty() && SurvivorsObstacles.blocked(s.x, s.y, 0f, obstacles) -> iterator.remove()
                hypot(s.x - state.px, s.y - state.py) < s.radius + SurvivorsEngine.PLAYER_RADIUS -> {
                    iterator.remove()
                    e.hurtPlayer(s.damage, st)
                    if (state.dead) return
                }
            }
        }
    }
}

/** Abstand des Punkts (px, py) zur Strecke (ax, ay)–(bx, by). */
internal fun distanceToSegment(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
    val vx = bx - ax
    val vy = by - ay
    val len2 = vx * vx + vy * vy
    val t = if (len2 <= 0f) 0f else (((px - ax) * vx + (py - ay) * vy) / len2).coerceIn(0f, 1f)
    return hypot(px - (ax + t * vx), py - (ay + t * vy))
}
