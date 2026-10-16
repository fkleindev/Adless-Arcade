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
        const val MAX_ENEMIES = 600
        const val TIMED_DURATION = 900f
        const val BOSS_INTERVAL = 300f
        /** Höchstens so viele Fernkämpfer (Knochenschützen, Grabhexen) gleichzeitig. */
        const val MAX_RANGED = 5
        /** Endlos-Modus: ab hier werden die Gegner rasend (HP, Schaden und Tempo steigen stärker). */
        const val ENDLESS_RAMP_START = 1200f
        const val MAX_ENRAGED_SPEED = 1.8f

        /** Minuten seit Beginn der Raserei (nur Endlos-Modus, sonst 0). */
        fun overtime(mode: SurvivorsMode, time: Float): Float =
            if (mode == SurvivorsMode.ENDLESS) ((time - ENDLESS_RAMP_START) / 60f).coerceAtLeast(0f) else 0f

        /**
         * Zusätzliche Faktoren der Raserei; vor Minute 20 bzw. im 15-Minuten-Modus alle 1.
         * Leben wächst zusätzlich exponentiell, damit Endlos-Läufe irgendwann sicher enden.
         */
        fun enemyScaling(mode: SurvivorsMode, time: Float): EnemyScaling {
            val o = overtime(mode, time)
            return EnemyScaling(
                hp = (1f + 0.25f * o + 0.04f * o * o) * 1.06f.pow(o),
                damage = 1f + 0.1f * o + 0.008f * o * o,
                speed = (1f + 0.035f * o).coerceAtMost(MAX_ENRAGED_SPEED),
                spawn = 1f + 0.05f * o,
            )
        }
    }

    private val s get() = e.state.spawner

    /** HP-Faktor für neue Gegner. */
    fun hpMultiplier(time: Float): Float {
        val m = time / 60f
        val base = 1f + 0.12f * m + 0.06f * m * m + 0.004f * m * m * m + if (time > TIMED_DURATION) (time - TIMED_DURATION) / 60f * 0.6f else 0f
        return base * enemyScaling(e.state.mode, time).hp * e.state.stage.hpFactor
    }

    /** Neue Gegner pro Sekunde. */
    fun spawnRate(time: Float): Float {
        val m = time / 60f
        return (0.9f + 0.9f * m + 0.04f * m.pow(2f)) * e.state.stage.spawnFactor * enemyScaling(e.state.mode, time).spawn
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
                val finalBoss = state.stage.finalBoss
                spawnAround(finalBoss, hpMultiplier(t) * 0.3f * (1f + 0.2f * e.state.evolved.size))
                e.emit(SurvivorsEvent.BossSpawned(finalBoss))
            }
            return
        }

        if (!s.enraged && overtime(state.mode, t) > 0f) {
            s.enraged = true
            e.emit(SurvivorsEvent.Enraged)
        }

        if (t >= s.nextBossTime) {
            s.nextBossTime += BOSS_INTERVAL
            spawnAround(state.stage.bossKind, hpMultiplier(t))
            state.bossSpawnTime = t
            e.emit(SurvivorsEvent.BossSpawned(state.stage.bossKind))
        }

        s.eliteTimer -= dt
        if (s.eliteTimer <= 0f) {
            s.eliteTimer = 60f
            spawnAround(randomKind(t, elite = true), hpMultiplier(t) * 8f, elite = true)
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
    private fun randomKind(t: Float, elite: Boolean = false): EnemyKind {
        val graveyard = e.state.stage == SurvivorsStage.GRAVEYARD
        val options = buildList {
            add((if (graveyard) EnemyKind.ZOMBIE else EnemyKind.SLIME) to 4f)
            add(EnemyKind.BAT to 3f)
            if (t > 120f) add(EnemyKind.SKELETON to 4f)
            if (t > 300f) add(EnemyKind.GHOST to 3f)
            if (t > 480f) add(EnemyKind.ORC to 2f)
            if (t > 600f) add(EnemyKind.SKELETON to 2f)
            // Friedhof: vereinzelt Gegner mit Fernangriffen (nicht als Elite). Sie halten Abstand und leben daher
            // länger – deshalb nur wenige gleichzeitig.
            if (graveyard && !elite && e.state.enemies.count { it.kind.keepsDistance } < MAX_RANGED) {
                if (t > 180f) add(EnemyKind.ARCHER to 0.35f)
                if (t > 240f) add(EnemyKind.WITCH to 0.25f)
            }
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
