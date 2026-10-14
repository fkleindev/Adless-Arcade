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
    private class Family(val orientations: List<Shape>, val weight: Double)

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

    private val totalWeight = families.sumOf { it.weight }

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

    fun randomShape(random: Random): Shape {
        var pick = random.nextDouble() * totalWeight
        for (family in families) {
            pick -= family.weight
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
    private const val GENERATION_ATTEMPTS = 24

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
        if (tray.all { it == null }) tray = generateTray(board, random)

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

    /**
     * Erzeugt ein neues 3er-Set. Bevorzugt Sets, die sich (in irgendeiner Reihenfolge) komplett
     * setzen lassen, damit das Spiel fair bleibt – schlechte Züge werden trotzdem bestraft.
     */
    fun generateTray(board: List<Int>, random: Random): List<Piece?> {
        var fallback: List<Shape>? = null
        repeat(GENERATION_ATTEMPTS) {
            val shapes = List(TRAY_SIZE) { PieceCatalog.randomShape(random) }
            if (fitsInSomeOrder(board, shapes)) return shapes.toPieces(random)
            if (fallback == null && shapes.any { canPlaceAnywhere(board, it) }) fallback = shapes
        }
        val shapes = fallback ?: List(TRAY_SIZE) { PieceCatalog.randomShape(random) }
        return shapes.toPieces(random)
    }

    private fun List<Shape>.toPieces(random: Random): List<Piece?> = map { shape ->
        Piece(id = random.nextLong(), shape = shape, color = random.nextInt(1, COLOR_COUNT + 1))
    }

    private fun fitsInSomeOrder(board: List<Int>, shapes: List<Shape>): Boolean =
        ORDERS.any { order ->
            var current = board
            order.all { i ->
                val next = bestPlacement(current, shapes[i])
                if (next != null) current = next
                next != null
            }
        }

    /** Greedy: Position mit den meisten gelöschten Linien, Ergebnis-Board nach dem Clear. */
    private fun bestPlacement(board: List<Int>, shape: Shape): List<Int>? {
        var best: List<Int>? = null
        var bestLines = -1
        for (r in 0..SIZE - shape.height) {
            for (c in 0..SIZE - shape.width) {
                if (!canPlace(board, shape, r, c)) continue
                val placed = applyPlacement(board, shape, r, c, 1)
                val lines = fullLines(placed)
                if (lines.count > bestLines) {
                    bestLines = lines.count
                    val cleared = lines.cellIndices()
                    best = placed.mapIndexed { i, v -> if (i in cleared) 0 else v }
                }
            }
        }
        return best
    }

    private fun applyPlacement(board: List<Int>, shape: Shape, row: Int, col: Int, color: Int): List<Int> {
        val result = board.toMutableList()
        shape.cells.forEach { result[index(row + it.row, col + it.col)] = color }
        return result
    }
}
