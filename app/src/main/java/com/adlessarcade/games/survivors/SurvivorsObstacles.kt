package com.adlessarcade.games.survivors

import kotlin.math.floor
import kotlin.math.hypot

enum class ObstacleKind(val radius: Float) {
    CRYPT(1.1f),
    GRAVES(0.5f),
    STATUE(0.6f),
    DEAD_TREE(0.55f),
}

/** Ein festes Hindernis (Kreis), durch das weder Held noch Gegner laufen können. */
data class Obstacle(val x: Float, val y: Float, val kind: ObstacleKind) {
    val radius: Float get() = kind.radius
}

/**
 * Hindernisse des Friedhofs. Die Welt ist unendlich, daher werden sie pro Weltzelle deterministisch aus einem
 * Hash berechnet – sie müssen weder gespeichert noch erzeugt werden und liegen nach dem Laden wieder genauso da.
 */
object SurvivorsObstacles {
    const val CELL = 6f
    /** Rund um den Startpunkt bleibt alles frei. */
    const val SAFE_RADIUS = 4f
    private const val CHANCE = 0.28f

    private fun hash(cx: Int, cy: Int): Int {
        var h = cx * 374761393 + cy * 668265263 + 0x5bd1e995
        h = (h xor (h ushr 13)) * 1274126177
        return h xor (h ushr 16)
    }

    private fun unit(h: Int, shift: Int): Float = ((h ushr shift) and 0xFF) / 255f

    /** Hindernis der Zelle (cx, cy) oder null. */
    fun inCell(cx: Int, cy: Int): Obstacle? {
        val h = hash(cx, cy)
        if (unit(h, 0) >= CHANCE) return null
        val kind = when ((h ushr 8) and 0xF) {
            in 0..3 -> ObstacleKind.CRYPT
            in 4..8 -> ObstacleKind.GRAVES
            in 9..11 -> ObstacleKind.STATUE
            else -> ObstacleKind.DEAD_TREE
        }
        // Mit Abstand zum Zellrand, damit zwischen zwei Hindernissen immer ein Durchgang bleibt.
        val margin = 1.5f
        val x = cx * CELL + margin + unit(h, 16) * (CELL - 2 * margin)
        val y = cy * CELL + margin + unit(h, 24) * (CELL - 2 * margin)
        if (hypot(x, y) < SAFE_RADIUS + kind.radius) return null
        return Obstacle(x, y, kind)
    }

    /** Alle Hindernisse, deren Mittelpunkt höchstens [radius] (+ eine Zelle) von (x, y) entfernt ist. */
    fun near(x: Float, y: Float, radius: Float, into: MutableList<Obstacle> = ArrayList()): MutableList<Obstacle> {
        into.clear()
        val minX = floor((x - radius) / CELL).toInt()
        val maxX = floor((x + radius) / CELL).toInt()
        val minY = floor((y - radius) / CELL).toInt()
        val maxY = floor((y + radius) / CELL).toInt()
        for (cx in minX..maxX) for (cy in minY..maxY) inCell(cx, cy)?.let { into += it }
        return into
    }

    /**
     * Schiebt einen Kreis (x, y, [r]) aus allen [obstacles] heraus; liefert die neue Position.
     * Das Herausschieben entlang der Normalen lässt Figuren an der Kante entlanggleiten.
     */
    inline fun pushOut(x: Float, y: Float, r: Float, obstacles: List<Obstacle>, set: (Float, Float) -> Unit) {
        var px = x
        var py = y
        for (o in obstacles) {
            val dx = px - o.x
            val dy = py - o.y
            val min = o.radius + r
            if (dx * dx + dy * dy >= min * min) continue
            val d = hypot(dx, dy)
            if (d < 0.0001f) {
                px = o.x + min
            } else {
                px = o.x + dx / d * min
                py = o.y + dy / d * min
            }
        }
        if (px != x || py != y) set(px, py)
    }

    /** Liegt der Punkt (bzw. Kreis mit [r]) in einem Hindernis? */
    fun blocked(x: Float, y: Float, r: Float, obstacles: List<Obstacle>): Boolean =
        obstacles.any { o -> hypot(x - o.x, y - o.y) < o.radius + r }
}
