package com.adlessarcade.games.kitchen

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/**
 * Ein Kochschritt als Gesten-Minispiel. Koordinaten sind auf die Minispiel-Fläche normiert (x, y in 0..1);
 * [aspect] = Breite / Höhe der Fläche, damit Kreise rund bleiben. [difficulty] 0..1 macht Zonen schmaler und Abläufe schneller.
 */
sealed class MiniGame(val kind: MiniGameKind, val difficulty: Float, protected val aspect: Float) {
    var time = 0f
        protected set
    var done = false
        protected set
    var errors = 0
        protected set
    /** Grober Fehler (übergelaufen, Schale im Ei, verbrannt) – dann hilft Mama. */
    var critical = false
        protected set

    /** Höchstdauer; danach endet das Minispiel mit dem bisherigen Ergebnis. */
    protected open val timeLimit: Float = 8f

    abstract val accuracy: Float

    /** Verstrichener Anteil der Höchstdauer (für den Zeitbalken). */
    val timeShare: Float get() = (time / timeLimit).coerceIn(0f, 1f)

    /** Misslungen → Mama springt ein (Schritt zählt mit 0). */
    val failed: Boolean get() = critical || accuracy < FAIL_BELOW

    open fun update(dt: Float) {
        if (done) return
        time += dt
        if (time >= timeLimit) finish()
    }

    open fun down(x: Float, y: Float) {}
    open fun move(x: Float, y: Float) {}
    open fun up(x: Float, y: Float) {}

    protected fun finish() {
        done = true
    }

    /** Lineare Bewertung: 1 bei 0 Abstand, 0 ab [tolerance]. */
    protected fun closeness(distance: Float, tolerance: Float): Float = (1f - abs(distance) / tolerance).coerceIn(0f, 1f)

    companion object {
        const val FAIL_BELOW = 0.3f

        /** [kinds]: Komponenten des Gerichts (fürs Anrichten). */
        fun create(kind: MiniGameKind, difficulty: Float, aspect: Float, seed: Long, kinds: List<ItemKind> = emptyList()): MiniGame {
            val d = difficulty.coerceIn(0f, 1f)
            val rng = Random(seed)
            return when (kind) {
                MiniGameKind.CHOP -> Chop(d, aspect)
                MiniGameKind.PEEL -> Peel(d, aspect)
                MiniGameKind.GRATE -> Grate(d, aspect)
                MiniGameKind.CRACK -> Crack(d, aspect, rng)
                MiniGameKind.POUR -> Pour(d, aspect, rng)
                MiniGameKind.STIR -> Stir(d, aspect)
                MiniGameKind.FLIP -> Flip(d, aspect)
                MiniGameKind.HEAT -> Heat(d, aspect, rng)
                MiniGameKind.PLATE -> PlateUp(d, aspect, kinds.ifEmpty { listOf(ItemKind.TOAST) })
                MiniGameKind.SEASON -> Season(d, aspect, rng)
            }
        }
    }
}

/** Messer fährt von links nach rechts; an jeder Linie (Mitte eines Stücks) tippen. */
class Chop(difficulty: Float, aspect: Float) : MiniGame(MiniGameKind.CHOP, difficulty, aspect) {
    val lines: List<Float>
    /** Genauigkeit je Linie, null = noch nicht geschnitten. */
    val cuts: Array<Float?>
    /** Zeitpunkt jedes Schnitts (für die Animation), −1 = ungeschnitten. */
    val cutTimes: FloatArray
    /** Letzter Tipp (Messer fährt kurz herunter). */
    var lastTap = -1f
        private set
    var knifeX = START
        private set
    val speed = 0.16f + 0.14f * difficulty
    val tolerance = 0.06f - 0.025f * difficulty

    init {
        val n = 3 + (difficulty * 3).toInt()
        // Gleichmäßig verteilt: jede Linie liegt genau in der Mitte eines Stücks
        val spacing = (END - START - 0.12f) / n
        lines = (0 until n).map { START + 0.08f + spacing * (it + 0.5f) }
        cuts = arrayOfNulls(n)
        cutTimes = FloatArray(n) { -1f }
    }

    override val timeLimit = 12f

    override fun update(dt: Float) {
        if (done) return
        super.update(dt)
        knifeX += speed * dt
        if (knifeX >= END) finish()
    }

    override fun down(x: Float, y: Float) {
        if (done) return
        lastTap = time
        val index = lines.indices.filter { cuts[it] == null }.minByOrNull { abs(lines[it] - knifeX) }
        if (index == null || abs(lines[index] - knifeX) > tolerance) {
            errors++
            return
        }
        cuts[index] = closeness(lines[index] - knifeX, tolerance) * 0.5f + 0.5f
        cutTimes[index] = time
        if (cuts.all { it != null }) finish()
    }

    override val accuracy: Float
        get() = (cuts.sumOf { (it ?: 0f).toDouble() }.toFloat() / lines.size - errors * 0.1f).coerceIn(0f, 1f)

    companion object {
        const val START = 0.1f
        const val END = 0.9f
    }
}

/** Waagrechte Schalenstreifen: über jeden Streifen wischen. */
class Peel(difficulty: Float, aspect: Float) : MiniGame(MiniGameKind.PEEL, difficulty, aspect) {
    val bands = 3 + (difficulty * 2).toInt()
    /** Geschälter Anteil je Streifen, in 20 Abschnitten. */
    val peeled: Array<BooleanArray> = Array(bands) { BooleanArray(SEGMENTS) }
    /** Zeitpunkt, an dem ein Abschnitt geschält wurde (für fallende Schalenstreifen). */
    val peelTimes: Array<FloatArray> = Array(bands) { FloatArray(SEGMENTS) { -1f } }
    private var lastX = -1f

    override val timeLimit = 7f - 2f * difficulty

    fun bandCenter(i: Int): Float = TOP + (BOTTOM - TOP) * (i + 0.5f) / bands

    override fun down(x: Float, y: Float) {
        lastX = x
        mark(x, x, y)
    }

    override fun move(x: Float, y: Float) {
        if (done || lastX < 0f) return
        mark(lastX, x, y)
        lastX = x
        if (coverage >= 0.97f) finish()
    }

    override fun up(x: Float, y: Float) {
        lastX = -1f
    }

    private fun mark(from: Float, to: Float, y: Float) {
        val band = ((y - TOP) / (BOTTOM - TOP) * bands).toInt()
        if (band !in 0 until bands) return
        val a = minOf(from, to)
        val b = maxOf(from, to)
        for (s in 0 until SEGMENTS) {
            val sx = LEFT + (RIGHT - LEFT) * (s + 0.5f) / SEGMENTS
            if ((sx in a..b || abs(sx - from) < 0.02f) && !peeled[band][s]) {
                peeled[band][s] = true
                peelTimes[band][s] = time
            }
        }
    }

    val coverage: Float get() = peeled.sumOf { row -> row.count { it } } / (bands * SEGMENTS).toFloat()

    override val accuracy: Float get() = (coverage * 1.05f).coerceAtMost(1f)

    companion object {
        const val SEGMENTS = 20
        const val LEFT = 0.25f
        const val RIGHT = 0.75f
        const val TOP = 0.25f
        const val BOTTOM = 0.75f
    }
}

/** Schnell auf und ab wischen, bis der Käse gerieben ist. Je schneller, desto besser. */
class Grate(difficulty: Float, aspect: Float) : MiniGame(MiniGameKind.GRATE, difficulty, aspect) {
    var progress = 0f
        private set
    private var lastY = -1f
    /** Letzte Wischbewegung (Späne fallen nur, solange gerieben wird). */
    var lastMove = -1f
        private set
    private val needed = 3.5f + 2.5f * difficulty

    override val timeLimit = 6f

    override fun down(x: Float, y: Float) {
        lastY = y
    }

    override fun move(x: Float, y: Float) {
        if (done || lastY < 0f) return
        progress = (progress + abs(y - lastY) / needed).coerceAtMost(1f)
        if (abs(y - lastY) > 0.005f) lastMove = time
        lastY = y
        if (progress >= 1f) finish()
    }

    override fun up(x: Float, y: Float) {
        lastY = -1f
    }

    override val accuracy: Float
        get() = if (progress >= 1f) (1.3f - time / timeLimit).coerceIn(0f, 1f) else progress * 0.4f
}

/** Zeiger pendelt hin und her; tippen, wenn er in der grünen Zone steht. */
class Crack(difficulty: Float, aspect: Float, rng: Random) : MiniGame(MiniGameKind.CRACK, difficulty, aspect) {
    val zoneCenter = 0.3f + rng.nextFloat() * 0.4f
    val zoneHalf = 0.1f - 0.05f * difficulty
    private val speed = 0.7f + 0.8f * difficulty
    var pointer = 0f
        private set
    private var dir = 1f
    private var result = 0f
    /** Zeitpunkt des Aufschlagens (Animation). */
    var tapTime = -1f
        private set

    override val timeLimit = 6f

    override fun update(dt: Float) {
        if (done) return
        super.update(dt)
        pointer += dir * speed * dt
        if (pointer > 1f) { pointer = 2f - pointer; dir = -1f }
        if (pointer < 0f) { pointer = -pointer; dir = 1f }
        if (done && result == 0f) critical = true // nicht getippt
    }

    override fun down(x: Float, y: Float) {
        if (done) return
        tapTime = time
        val dist = pointer - zoneCenter
        if (abs(dist) > zoneHalf) {
            errors++
            critical = true // Schale im Ei
        } else {
            result = closeness(dist, zoneHalf) * 0.4f + 0.6f
        }
        finish()
    }

    override val accuracy: Float get() = if (critical) 0f else result
}

/** Gedrückt halten füllt; bei der Linie loslassen. Über den Rand = übergelaufen. */
class Pour(difficulty: Float, aspect: Float, rng: Random) : MiniGame(MiniGameKind.POUR, difficulty, aspect) {
    val target = 0.55f + rng.nextFloat() * 0.25f
    val tolerance = 0.14f - 0.06f * difficulty
    private val rate = 0.3f + 0.25f * difficulty
    var level = 0f
        private set
    var pouring = false
        private set

    override val timeLimit = 8f

    override fun update(dt: Float) {
        if (done) return
        super.update(dt)
        if (pouring) level += rate * dt
        if (level >= 1f) {
            level = 1f
            critical = true
            errors++
            finish()
        }
    }

    override fun down(x: Float, y: Float) {
        if (!done) pouring = true
    }

    override fun up(x: Float, y: Float) {
        if (done || !pouring) return
        pouring = false
        if (level > 0.05f) finish()
    }

    override val accuracy: Float get() = if (critical) 0f else closeness(level - target, tolerance)
}

/** Kreise im Topf ziehen; die Winkelgeschwindigkeit soll im grünen Bereich bleiben. */
class Stir(difficulty: Float, aspect: Float) : MiniGame(MiniGameKind.STIR, difficulty, aspect) {
    val minSpeed = 1.6f * PI.toFloat()
    val maxSpeed = (4.2f - 1.2f * difficulty) * PI.toFloat()
    private val turnsNeeded = 3f + 2f * difficulty
    var turned = 0f
        private set
    /** Geglättete Drehgeschwindigkeit in rad/s. */
    var speed = 0f
        private set
    private var lastAngle: Float? = null
    private var inZone = 0f
    private var stirring = 0f
    private var lastMoveTime = 0f
    private var lastSpill = -1f

    override val timeLimit = 10f

    val progress: Float get() = (turned / (turnsNeeded * 2f * PI.toFloat())).coerceAtMost(1f)

    override fun update(dt: Float) {
        if (done) return
        super.update(dt)
        if (time - lastMoveTime > 0.15f) speed *= 0.85f
        if (lastAngle != null) {
            stirring += dt
            if (speed in minSpeed..maxSpeed) inZone += dt
            // Viel zu schnell: Spritzer, höchstens einer pro halbe Sekunde
            if (speed > maxSpeed * 1.4f && time - lastSpill > 0.5f) {
                errors++
                lastSpill = time
            }
        }
    }

    override fun down(x: Float, y: Float) {
        lastAngle = angle(x, y)
        lastMoveTime = time
    }

    override fun move(x: Float, y: Float) {
        if (done) return
        val a = angle(x, y)
        val last = lastAngle ?: run { lastAngle = a; return }
        var delta = a - last
        if (delta > PI) delta -= 2f * PI.toFloat()
        if (delta < -PI) delta += 2f * PI.toFloat()
        val dt = (time - lastMoveTime).coerceAtLeast(1f / 120f)
        speed = speed * 0.7f + abs(delta) / dt * 0.3f
        turned += abs(delta)
        lastAngle = a
        lastMoveTime = time
        if (progress >= 1f) finish()
    }

    override fun up(x: Float, y: Float) {
        lastAngle = null
    }

    private fun angle(x: Float, y: Float): Float = atan2(y - 0.5f, (x - 0.5f) * aspect)

    override val accuracy: Float
        get() {
            if (progress < 1f) return progress * 0.4f
            val share = if (stirring > 0f) inZone / stirring else 0f
            return (0.35f + 0.65f * share - errors * 0.05f).coerceIn(0f, 1f)
        }
}

/** Bräunung steigt; bei goldbraun nach oben wischen. */
class Flip(difficulty: Float, aspect: Float) : MiniGame(MiniGameKind.FLIP, difficulty, aspect) {
    var browning = 0f
        private set
    val windowStart = 0.55f
    val windowEnd = 0.8f - 0.08f * difficulty
    private val rate = 0.28f + 0.17f * difficulty
    private var downY = -1f
    private var flippedAt = -1f
    /** Zeitpunkt des Wendens (Animation), −1 = noch nicht gewendet. */
    var flipTime = -1f
        private set

    override val timeLimit = 10f

    override fun update(dt: Float) {
        if (done) return
        super.update(dt)
        browning += rate * dt
        if (browning >= 1f) {
            critical = true
            finish()
        }
    }

    override fun down(x: Float, y: Float) {
        downY = y
    }

    override fun move(x: Float, y: Float) {
        if (done || downY < 0f) return
        if (downY - y > 0.15f) {
            flippedAt = browning
            flipTime = time
            finish()
        }
    }

    override fun up(x: Float, y: Float) {
        downY = -1f
    }

    override val accuracy: Float
        get() = when {
            critical || flippedAt < 0f -> 0f
            flippedAt in windowStart..windowEnd -> 1f
            flippedAt < windowStart -> closeness(windowStart - flippedAt, 0.35f)
            else -> closeness(flippedAt - windowEnd, 0.2f)
        }
}

/** Halten heizt, loslassen kühlt; die Nadel soll in der Zone bleiben. */
class Heat(difficulty: Float, aspect: Float, rng: Random) : MiniGame(MiniGameKind.HEAT, difficulty, aspect) {
    val zoneCenter = 0.5f + (rng.nextFloat() - 0.5f) * 0.2f
    val zoneHalf = 0.12f - 0.04f * difficulty
    var temperature = 0.2f
        private set
    var heating = false
        private set
    private var inZone = 0f
    private val duration = 4f + difficulty

    override val timeLimit get() = duration

    override fun update(dt: Float) {
        if (done) return
        super.update(dt)
        temperature += (if (heating) 0.45f else -0.3f) * dt
        temperature = temperature.coerceIn(0f, 1f)
        if (abs(temperature - zoneCenter) <= zoneHalf) inZone += dt
        if (temperature >= 1f) critical = true
    }

    override fun down(x: Float, y: Float) {
        heating = true
    }

    override fun up(x: Float, y: Float) {
        heating = false
    }

    val remaining: Float get() = (duration - time).coerceAtLeast(0f)

    override val accuracy: Float get() = if (critical) 0f else (inZone / (duration * 0.75f)).coerceIn(0f, 1f)
}

/**
 * Zutaten aus der Ablage auf den Teller legen. Jeder Platz zeigt, welche Zutat dorthin gehört; ein Stück passt auf
 * jeden freien Platz seiner Sorte (z. B. jede Toastscheibe auf jeden Toast-Platz).
 */
class PlateUp(difficulty: Float, aspect: Float, kinds: List<ItemKind>) : MiniGame(MiniGameKind.PLATE, difficulty, aspect) {
    class Spot(val kind: ItemKind, val x: Float, val y: Float) {
        var filled = false
    }

    class Piece(val kind: ItemKind, val homeX: Float, val homeY: Float) {
        var x = homeX
        var y = homeY
        var placed: Float? = null
        /** Zeitpunkt des Ablegens (kleiner Hüpfer). */
        var placedAt = -1f
    }

    val spots: List<Spot>
    val pieces: List<Piece>
    private var dragging: Piece? = null
    val tolerance = 0.1f - 0.04f * difficulty

    init {
        val extra = (difficulty * 2).toInt()
        // Hauptzutat in die Mitte, Beilagen im Kreis darum; bei nur einer Zutat mehrere Stücke davon
        val main = kinds.first()
        val around = when (kinds.size) {
            1 -> List(2 + extra) { main }
            else -> List(2 + extra) { kinds[1 + it % (kinds.size - 1)] }
        }
        val centerKind = if (kinds.size == 1) null else main
        val spotList = ArrayList<Spot>()
        if (centerKind != null) spotList += Spot(centerKind, CENTER_X, CENTER_Y)
        val ring = if (kinds.size == 1) around + main else around
        val r = if (centerKind != null) 0.24f else 0.17f
        ring.forEachIndexed { i, kind ->
            val a = i * 2f * PI.toFloat() / ring.size - PI.toFloat() / 2f
            spotList += Spot(kind, CENTER_X + cos(a) * r / aspect * 1.25f, CENTER_Y + sin(a) * r)
        }
        spots = spotList
        // Ablage links: gleiche Sorten nebeneinander
        val sorted = spots.map { it.kind }.sortedBy { kinds.indexOf(it) }
        pieces = sorted.mapIndexed { i, kind ->
            val col = i % 2
            val row = i / 2
            Piece(kind, 0.1f + col * 0.1f, 0.28f + row * 0.2f)
        }
    }

    override val timeLimit = 10f

    override fun down(x: Float, y: Float) {
        dragging = pieces.filter { it.placed == null }.minByOrNull { dist(it.x, it.y, x, y) }
            ?.takeIf { dist(it.x, it.y, x, y) < 0.14f }
    }

    override fun move(x: Float, y: Float) {
        dragging?.let { it.x = x; it.y = y }
    }

    override fun up(x: Float, y: Float) {
        val p = dragging ?: return
        dragging = null
        val spot = spots.filter { !it.filled && it.kind == p.kind }.minByOrNull { dist(p.x, p.y, it.x, it.y) }
        val d = spot?.let { dist(p.x, p.y, it.x, it.y) } ?: Float.MAX_VALUE
        if (spot != null && d <= SNAP * (1f - 0.3f * difficulty)) {
            spot.filled = true
            p.placed = closeness(d, SNAP) * 0.6f + 0.4f
            p.placedAt = time
            p.x = spot.x
            p.y = spot.y
        } else {
            p.x = p.homeX
            p.y = p.homeY
        }
        if (pieces.all { it.placed != null }) finish()
    }

    /** Abstand in Einheiten der Flächenhöhe. */
    private fun dist(x1: Float, y1: Float, x2: Float, y2: Float) = hypot((x1 - x2) * aspect, y1 - y2)

    override val accuracy: Float get() = pieces.sumOf { (it.placed ?: 0f).toDouble() }.toFloat() / pieces.size

    companion object {
        const val CENTER_X = 0.6f
        const val CENTER_Y = 0.52f
        /** Fangradius eines Platzes (in Flächenhöhen); kleiner als der Abstand zwischen zwei Plätzen. */
        const val SNAP = 0.16f
    }
}

/** Auf den Streuer tippen, bis die Anzeige im Zielbereich ist; eine Sekunde Pause beendet. */
class Season(difficulty: Float, aspect: Float, rng: Random) : MiniGame(MiniGameKind.SEASON, difficulty, aspect) {
    val target = 0.5f + (rng.nextFloat() - 0.5f) * 0.2f
    val tolerance = 0.12f - 0.05f * difficulty
    private val perShake = 0.07f + 0.03f * difficulty
    var amount = 0f
        private set
    private var lastShake = -1f
    /** Zeitpunkte der letzten Schüttler (fallende Körnchen). */
    val shakes = ArrayList<Float>()

    override val timeLimit = 8f

    override fun update(dt: Float) {
        if (done) return
        super.update(dt)
        if (lastShake >= 0f && time - lastShake > 1f) finish()
    }

    override fun down(x: Float, y: Float) {
        if (done) return
        amount = (amount + perShake).coerceAtMost(1f)
        lastShake = time
        shakes += time
        if (shakes.size > 8) shakes.removeAt(0)
        if (amount > target + tolerance * 2.5f) {
            critical = true // versalzen
            errors++
            finish()
        }
    }

    override val accuracy: Float
        get() {
            if (critical) return 0f
            val d = abs(amount - target)
            return if (d <= tolerance) 1f - 0.2f * d / tolerance else (0.8f * (1f - (d - tolerance) / (tolerance * 1.5f))).coerceAtLeast(0f)
        }
}
