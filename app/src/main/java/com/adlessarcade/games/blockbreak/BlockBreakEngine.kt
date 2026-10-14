package com.adlessarcade.games.blockbreak

import kotlinx.serialization.Serializable
import kotlin.random.Random

@Serializable
data class Cell(val row: Int, val col: Int)

/** Normalisierte Form: alle Zellen relativ zu (0,0) oben links. */
@Serializable
data class Shape(val cells: List<Cell>) {
    val width: Int get() = cells.maxOf { it.col } + 1
    val height: Int get() = cells.maxOf { it.row } + 1
}

@Serializable
data class Piece(val id: Long, val shape: Shape, val color: Int)

@Serializable
data class GameState(
    /** SIZE*SIZE Felder, 0 = leer, sonst Farbindex 1..COLOR_COUNT. */
    val board: List<Int>,
    /** Immer TRAY_SIZE Slots, null = bereits gesetzt. */
    val tray: List<Piece?>,
    val score: Int = 0,
    /** Anzahl aufeinanderfolgender Züge mit Linien-Clear (Combo-Multiplikator). */
    val streak: Int = 0,
    val movesWithoutClear: Int = 0,
    val isGameOver: Boolean = false,
    /** In diesem Lauf verdiente Coins. */
    val coinsEarned: Int = 0,
)

data class PlaceResult(
    val state: GameState,
    /** Gelöschte Felder mit ihrer Farbe vor dem Löschen (für Animationen). */
    val clearedCells: Map<Int, Int>,
    val linesCleared: Int,
    val points: Int,
    val boardCleared: Boolean,
    /** Durch diesen Zug verdiente Coins. */
    val coins: Int = 0,
)

data class FullLines(val rows: List<Int>, val cols: List<Int>) {
    val count: Int get() = rows.size + cols.size

    fun cellIndices(): Set<Int> = buildSet {
        rows.forEach { r -> repeat(BlockBreakEngine.SIZE) { c -> add(BlockBreakEngine.index(r, c)) } }
        cols.forEach { c -> repeat(BlockBreakEngine.SIZE) { r -> add(BlockBreakEngine.index(r, c)) } }
    }
}

object PieceCatalog {
    private class Family(val orientations: List<Shape>, val weight: Double) {
        val cellCount = orientations.first().cells.size

        /** Mit steigender Schwierigkeit werden große Teile häufiger und kleine seltener. */
        fun weightAt(difficulty: Float): Double =
            weight * (1.0 + difficulty * (cellCount - 3) * 0.3).coerceAtLeast(0.25)
    }

    // "X" = Block, "." = leer, "/" = neue Zeile. Alle Drehungen/Spiegelungen werden erzeugt;
    // der Spieler kann Teile (wie bei Block Blast) selbst nicht drehen.
    private val families: List<Family> = listOf(
        "X" to 1.5,
        "XX" to 3.0,
        "XXX" to 3.0,
        "XXXX" to 2.0,
        "XXXXX" to 1.5,
        "XX/XX" to 3.0,
        "XXX/XXX" to 1.5,
        "XXX/XXX/XXX" to 1.0,
        "X./XX" to 3.0,
        "X./X./XX" to 2.5,
        "X../X../XXX" to 1.5,
        "XXX/.X." to 2.0,
        "XX./.XX" to 2.0,
    ).map { (pattern, weight) -> Family(orientations(parse(pattern)), weight) }

    val allShapes: List<Shape> get() = families.flatMap { it.orientations }

    fun parse(pattern: String): List<Cell> =
        pattern.split('/').flatMapIndexed { r, line ->
            line.mapIndexedNotNull { c, ch -> if (ch == 'X') Cell(r, c) else null }
        }

    fun normalize(cells: List<Cell>): Shape {
        val minR = cells.minOf { it.row }
        val minC = cells.minOf { it.col }
        return Shape(
            cells.map { Cell(it.row - minR, it.col - minC) }
                .sortedWith(compareBy({ it.row }, { it.col }))
        )
    }

    private fun orientations(cells: List<Cell>): List<Shape> {
        val result = LinkedHashSet<Shape>()
        var current = cells
        repeat(4) {
            result += normalize(current)
            result += normalize(current.map { Cell(it.row, -it.col) })
            current = current.map { Cell(it.col, -it.row) }
        }
        return result.toList()
    }

    /** Zufällige Form nach Familiengewicht; [difficulty] ∈ [0, 1] verschiebt Richtung große Teile. */
    fun randomShape(random: Random, difficulty: Float = 0f): Shape {
        val weights = families.map { it.weightAt(difficulty) }
        var pick = random.nextDouble() * weights.sum()
        for ((i, family) in families.withIndex()) {
            pick -= weights[i]
            if (pick <= 0) return family.orientations.random(random)
        }
        return families.last().orientations.random(random)
    }
}

object BlockBreakEngine {
    const val SIZE = 8
    const val CELL_COUNT = SIZE * SIZE
    const val COLOR_COUNT = 7
    const val TRAY_SIZE = 3
    /** Nach so vielen Zügen ohne Clear endet die Combo-Serie. */
    const val COMBO_WINDOW = 3
    const val BOARD_CLEAR_BONUS = 300
    const val BOARD_CLEAR_COINS = 10
    /** Ab dieser Punktzahl gibt es keine Starthilfe mehr. */
    const val ASSIST_FADE_SCORE = 2500
    /** Über so viele Punkte nach dem Ende der Starthilfe steigt die Schwierigkeit bis zum Maximum. */
    const val DIFFICULTY_RAMP_SCORE = 6000
    private const val GENERATION_ATTEMPTS = 24
    private const val ASSISTED_ATTEMPTS = 16
    /** Bei maximaler Schwierigkeit: Chance, dass ein Set ohne Lösbarkeitsgarantie kommt. */
    private const val MAX_UNSOLVABLE_CHANCE = 0.15f
    private const val LINE_VALUE = 12
    private const val BOARD_CLEAR_VALUE = 60
    private const val EMPTINESS_WEIGHT = 2

    private val NEIGHBORS = listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)

    private val ORDERS = listOf(
        listOf(0, 1, 2), listOf(0, 2, 1), listOf(1, 0, 2),
        listOf(1, 2, 0), listOf(2, 0, 1), listOf(2, 1, 0),
    )

    fun index(row: Int, col: Int): Int = row * SIZE + col

    fun emptyBoard(): List<Int> = List(CELL_COUNT) { 0 }

    fun newGame(random: Random): GameState {
        val board = emptyBoard()
        return GameState(board = board, tray = generateTray(board, random))
    }

    fun canPlace(board: List<Int>, shape: Shape, row: Int, col: Int): Boolean =
        shape.cells.all {
            val r = row + it.row
            val c = col + it.col
            r in 0 until SIZE && c in 0 until SIZE && board[index(r, c)] == 0
        }

    fun canPlaceAnywhere(board: List<Int>, shape: Shape): Boolean {
        for (r in 0..SIZE - shape.height) {
            for (c in 0..SIZE - shape.width) {
                if (canPlace(board, shape, r, c)) return true
            }
        }
        return false
    }

    fun fullLines(board: List<Int>): FullLines = FullLines(
        rows = (0 until SIZE).filter { r -> (0 until SIZE).all { c -> board[index(r, c)] != 0 } },
        cols = (0 until SIZE).filter { c -> (0 until SIZE).all { r -> board[index(r, c)] != 0 } },
    )

    /** Felder, die durch das Setzen von [shape] bei (row, col) gelöscht würden (für die Vorschau). */
    fun previewClear(board: List<Int>, shape: Shape, row: Int, col: Int): Set<Int> {
        if (!canPlace(board, shape, row, col)) return emptySet()
        return fullLines(applyPlacement(board, shape, row, col, 1)).cellIndices()
    }

    fun lineBonus(lines: Int): Int = 10 * lines * (lines + 1) / 2

    fun isGameOver(board: List<Int>, tray: List<Piece?>): Boolean =
        tray.filterNotNull().none { canPlaceAnywhere(board, it.shape) }

    fun place(state: GameState, slot: Int, row: Int, col: Int, random: Random): PlaceResult? {
        if (state.isGameOver) return null
        val piece = state.tray.getOrNull(slot) ?: return null
        if (!canPlace(state.board, piece.shape, row, col)) return null

        val placed = applyPlacement(state.board, piece.shape, row, col, piece.color)
        val lines = fullLines(placed)
        val cleared = lines.cellIndices()
        val board = if (cleared.isEmpty()) placed else placed.mapIndexed { i, v -> if (i in cleared) 0 else v }

        val streak: Int
        val movesWithoutClear: Int
        var points = piece.shape.cells.size
        var coins = 0
        val boardCleared = lines.count > 0 && board.all { it == 0 }
        if (lines.count > 0) {
            streak = state.streak + 1
            movesWithoutClear = 0
            points += lineBonus(lines.count) * streak
            coins = lines.count * streak
            if (boardCleared) {
                points += BOARD_CLEAR_BONUS
                coins += BOARD_CLEAR_COINS
            }
        } else {
            movesWithoutClear = state.movesWithoutClear + 1
            streak = if (movesWithoutClear >= COMBO_WINDOW) 0 else state.streak
        }

        var tray = state.tray.toMutableList().also { it[slot] = null }.toList()
        if (tray.all { it == null }) tray = generateTray(board, random, state.score + points)

        val newState = GameState(
            board = board,
            tray = tray,
            score = state.score + points,
            coinsEarned = state.coinsEarned + coins,
            streak = streak,
            movesWithoutClear = movesWithoutClear,
            isGameOver = isGameOver(board, tray),
        )
        return PlaceResult(
            state = newState,
            clearedCells = cleared.associateWith { placed[it] },
            linesCleared = lines.count,
            points = points,
            boardCleared = boardCleared,
            coins = coins,
        )
    }

    // --- Teile-Erzeugung: Starthilfe & Schwierigkeit ---

    /**
     * Starthilfe ∈ [0, 1]: 1 bei Spielbeginn, 0 ab [ASSIST_FADE_SCORE]. Solange sie wirkt, werden bevorzugt Teile
     * gegeben, die zu den Lücken auf dem Brett passen und Linien bzw. einen Full Clear ermöglichen.
     */
    fun assistLevel(score: Int): Float = (1f - score.toFloat() / ASSIST_FADE_SCORE).coerceIn(0f, 1f)

    /** Schwierigkeit ∈ [0, 1]: steigt nach dem Ende der Starthilfe über [DIFFICULTY_RAMP_SCORE] Punkte an. */
    fun difficultyLevel(score: Int): Float =
        ((score - ASSIST_FADE_SCORE).toFloat() / DIFFICULTY_RAMP_SCORE).coerceIn(0f, 1f)

    /**
     * Erzeugt ein neues 3er-Set für den Punktestand [score].
     * - Starthilfe: Formen werden nach ihrem Nutzen auf dem aktuellen Brett gewichtet, und aus mehreren
     *   Kandidaten wird das Set mit dem besten Ergebnis (Linien, Full Clear, Passform) gewählt.
     * - Danach: zufällige Sets, die sich in irgendeiner Reihenfolge komplett setzen lassen.
     * - Hohe Schwierigkeit: größere Teile, und gelegentlich entfällt die Lösbarkeitsgarantie.
     */
    fun generateTray(board: List<Int>, random: Random, score: Int = 0): List<Piece?> {
        val assist = assistLevel(score)
        val difficulty = difficultyLevel(score)
        val grid = board.toIntArray()
        val helpful = if (assist > 0f) shapeHelpfulness(grid) else emptyList()
        val optimize = random.nextFloat() < assist
        val strict = random.nextFloat() >= difficulty * MAX_UNSOLVABLE_CHANCE

        fun randomSet() = List(TRAY_SIZE) {
            if (helpful.isNotEmpty() && random.nextFloat() < assist) pickWeighted(helpful, random)
            else PieceCatalog.randomShape(random, difficulty)
        }

        var best: List<Shape>? = null
        var bestValue = Int.MIN_VALUE
        var fallback: List<Shape>? = null
        repeat(if (optimize) ASSISTED_ATTEMPTS else GENERATION_ATTEMPTS) {
            val shapes = randomSet()
            if (!strict && shapes.any { canPlaceAnywhere(board, it) }) return shapes.toPieces(random)
            val value = evaluateSet(grid, shapes)
            if (value != null) {
                if (!optimize) return shapes.toPieces(random)
                if (value > bestValue) {
                    best = shapes
                    bestValue = value
                }
            } else if (fallback == null && shapes.any { canPlaceAnywhere(board, it) }) {
                fallback = shapes
            }
        }
        val shapes = best ?: fallback ?: randomSet()
        return shapes.toPieces(random)
    }

    private fun List<Shape>.toPieces(random: Random): List<Piece?> = map { shape ->
        Piece(id = random.nextLong(), shape = shape, color = random.nextInt(1, COLOR_COUNT + 1))
    }

    /** Alle setzbaren Formen mit Gewicht nach ihrem besten Einzelzug (Linien zählen stark, dann Passform). */
    private fun shapeHelpfulness(grid: IntArray): List<Pair<Shape, Double>> =
        PieceCatalog.allShapes.mapNotNull { shape ->
            bestStep(grid, shape)?.let { step ->
                val v = 1.0 + step.value + if (step.boardCleared) BOARD_CLEAR_VALUE else 0
                shape to v * v
            }
        }

    private fun pickWeighted(options: List<Pair<Shape, Double>>, random: Random): Shape {
        var pick = random.nextDouble() * options.sumOf { it.second }
        for ((shape, weight) in options) {
            pick -= weight
            if (pick <= 0) return shape
        }
        return options.last().first
    }

    /** Bestes Ergebnis über alle Reihenfolgen, oder null, wenn sich das Set in keiner Reihenfolge ganz setzen lässt. */
    private fun evaluateSet(grid: IntArray, shapes: List<Shape>): Int? {
        var best: Int? = null
        for (order in ORDERS) {
            var current = grid
            var total = 0
            var complete = true
            for (i in order) {
                val step = bestStep(current, shapes[i])
                if (step == null) {
                    complete = false
                    break
                }
                total += step.value + if (step.boardCleared) BOARD_CLEAR_VALUE else 0
                current = step.board
            }
            // Je leerer das Brett danach, desto näher ist der nächste Full Clear.
            total -= current.count { it != 0 } * EMPTINESS_WEIGHT
            if (complete && (best == null || total > best)) best = total
        }
        return best
    }

    private class Step(val board: IntArray, val value: Int, val boardCleared: Boolean)

    /**
     * Greedy-Zug für die Simulation: Position mit den meisten Linien, bei Gleichstand die mit der besten Passform
     * (Kontakt zu Blöcken und Rand). Liefert das Brett nach dem Löschen.
     */
    private fun bestStep(grid: IntArray, shape: Shape): Step? {
        val rowFill = IntArray(SIZE)
        val colFill = IntArray(SIZE)
        for (i in 0 until CELL_COUNT) {
            if (grid[i] != 0) {
                rowFill[i / SIZE]++
                colFill[i % SIZE]++
            }
        }
        var bestValue = -1
        var bestRow = -1
        var bestCol = -1
        for (r in 0..SIZE - shape.height) {
            for (c in 0..SIZE - shape.width) {
                if (!fits(grid, shape, r, c)) continue
                val value = completedLines(shape, r, c, rowFill, colFill) * LINE_VALUE + contact(grid, shape, r, c)
                if (value > bestValue) {
                    bestValue = value
                    bestRow = r
                    bestCol = c
                }
            }
        }
        if (bestRow < 0) return null

        val next = grid.copyOf()
        shape.cells.forEach { next[index(bestRow + it.row, bestCol + it.col)] = 1 }
        val fullRows = (0 until SIZE).filter { row -> (0 until SIZE).all { next[index(row, it)] != 0 } }
        val fullCols = (0 until SIZE).filter { col -> (0 until SIZE).all { next[index(it, col)] != 0 } }
        fullRows.forEach { row -> for (col in 0 until SIZE) next[index(row, col)] = 0 }
        fullCols.forEach { col -> for (row in 0 until SIZE) next[index(row, col)] = 0 }
        val cleared = (fullRows.isNotEmpty() || fullCols.isNotEmpty()) && next.all { it == 0 }
        return Step(next, bestValue, cleared)
    }

    private fun fits(grid: IntArray, shape: Shape, row: Int, col: Int): Boolean =
        shape.cells.all { grid[index(row + it.row, col + it.col)] == 0 }

    /** Anzahl der Reihen und Spalten, die durch das Setzen voll würden (ohne das Brett zu kopieren). */
    private fun completedLines(shape: Shape, row: Int, col: Int, rowFill: IntArray, colFill: IntArray): Int {
        var lines = 0
        for (dr in 0 until shape.height) {
            if (rowFill[row + dr] + shape.cells.count { it.row == dr } == SIZE) lines++
        }
        for (dc in 0 until shape.width) {
            if (colFill[col + dc] + shape.cells.count { it.col == dc } == SIZE) lines++
        }
        return lines
    }

    /** Wie gut das Teil anliegt: Kanten, die an belegte Felder oder den Rand grenzen. */
    private fun contact(grid: IntArray, shape: Shape, row: Int, col: Int): Int {
        var touching = 0
        for (cell in shape.cells) {
            val r = row + cell.row
            val c = col + cell.col
            for ((dr, dc) in NEIGHBORS) {
                val nr = r + dr
                val nc = c + dc
                if (nr !in 0 until SIZE || nc !in 0 until SIZE || grid[index(nr, nc)] != 0) touching++
            }
        }
        return touching
    }

    private fun applyPlacement(board: List<Int>, shape: Shape, row: Int, col: Int, color: Int): List<Int> {
        val result = board.toMutableList()
        shape.cells.forEach { result[index(row + it.row, col + it.col)] = color }
        return result
    }
}
