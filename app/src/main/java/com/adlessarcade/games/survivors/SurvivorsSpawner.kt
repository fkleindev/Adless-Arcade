package com.adlessarcade.games.survivors

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/** Erzeugt Gegnerwellen, Elites, Schwärme und Bosse; Schwierigkeit wächst mit der Zeit. */
internal class SurvivorsSpawner(private val e: SurvivorsEngine, private val random: Random) {
    companion object {
        /** Gegner erscheinen knapp außerhalb des sichtbaren Bereichs. */
        const val SPAWN_DISTANCE = 12f
        const val MAX_ENEMIES = 320
        const val TIMED_DURATION = 900f
        const val BOSS_INTERVAL = 300f
    }

    private val s get() = e.state.spawner

    /** HP-Faktor für neue Gegner. */
    fun hpMultiplier(time: Float): Float {
        val m = time / 60f
        return 1f + 0.12f * m + 0.06f * m * m + 0.004f * m * m * m + if (time > TIMED_DURATION) (time - TIMED_DURATION) / 60f * 0.6f else 0f
    }

    /** Neue Gegner pro Sekunde. */
    fun spawnRate(time: Float): Float {
        val m = time / 60f
        return 0.9f + 0.9f * m + 0.04f * m.pow(2f)
    }

    fun update(dt: Float) {
        val state = e.state
        val t = state.time

        // Im 15-Minuten-Modus kommt am Ende der Endboss und sonst nichts Neues mehr.
        if (state.mode == SurvivorsMode.TIMED && t >= TIMED_DURATION) {
            if (!s.finalBossSpawned) {
                s.finalBossSpawned = true
                state.enemies.removeAll { !it.isBoss } // Bühne frei für den Endkampf
                // Endboss bewusst nicht voll skaliert, damit ein starker Build ihn in ~1 Minute schafft.
                // Der Dämonenfürst wächst mit der Macht des Helden: +20 % Leben pro Entwicklung.
                spawnAround(EnemyKind.DEMON, hpMultiplier(t) * 0.3f * (1f + 0.2f * e.state.evolved.size))
                e.emit(SurvivorsEvent.BossSpawned(EnemyKind.DEMON))
            }
            return
        }

        if (t >= s.nextBossTime) {
            s.nextBossTime += BOSS_INTERVAL
            spawnAround(EnemyKind.BOSS, hpMultiplier(t))
            e.emit(SurvivorsEvent.BossSpawned(EnemyKind.BOSS))
        }

        s.eliteTimer -= dt
        if (s.eliteTimer <= 0f) {
            s.eliteTimer = 60f
            spawnAround(randomKind(t), hpMultiplier(t) * 8f, elite = true)
        }

        s.swarmTimer -= dt
        if (s.swarmTimer <= 0f) {
            s.swarmTimer = 90f
            spawnBatRing(t)
        }

        s.spawnBudget += spawnRate(t) * dt
        while (s.spawnBudget >= 1f) {
            s.spawnBudget -= 1f
            if (state.enemies.size < MAX_ENEMIES) spawnAround(randomKind(t), hpMultiplier(t))
        }
    }

    /** Welche Gegner es zu welcher Zeit gibt. */
    private fun randomKind(t: Float): EnemyKind {
        val options = buildList {
            add(EnemyKind.SLIME to 4f)
            add(EnemyKind.BAT to 3f)
            if (t > 120f) add(EnemyKind.SKELETON to 4f)
            if (t > 300f) add(EnemyKind.GHOST to 3f)
            if (t > 480f) add(EnemyKind.ORC to 2f)
            if (t > 600f) add(EnemyKind.SKELETON to 2f)
        }
        var pick = random.nextFloat() * options.sumOf { it.second.toDouble() }.toFloat()
        for ((kind, weight) in options) {
            pick -= weight
            if (pick <= 0f) return kind
        }
        return options.last().first
    }

    private fun spawnAround(kind: EnemyKind, hpMultiplier: Float, elite: Boolean = false) {
        val angle = random.nextFloat() * 2f * PI.toFloat()
        val distance = SPAWN_DISTANCE + random.nextFloat() * 2f
        e.spawnEnemy(
            kind,
            e.state.px + cos(angle) * distance,
            e.state.py + sin(angle) * distance,
            hpMultiplier,
            elite,
        )
    }

    /** Ein Ring aus Fledermäusen, der sich um den Helden schließt. */
    private fun spawnBatRing(t: Float) {
        val count = 16 + (t / 60f).toInt() * 2
        repeat(count) { i ->
            val angle = i * 2f * PI.toFloat() / count
            e.spawnEnemy(
                EnemyKind.BAT,
                e.state.px + cos(angle) * SPAWN_DISTANCE,
                e.state.py + sin(angle) * SPAWN_DISTANCE,
                hpMultiplier(t),
            )
        }
    }
}
