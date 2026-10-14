package com.adlessarcade.games.blockbreak

import com.adlessarcade.games.blockbreak.BlockBreakEngine.SIZE
import com.adlessarcade.games.blockbreak.BlockBreakEngine.index
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class BlockBreakEngineTest {

    private val random = Random(42)
    private val dot = PieceCatalog.normalize(PieceCatalog.parse("X"))
    private val square = PieceCatalog.normalize(PieceCatalog.parse("XX/XX"))
    private val lineOf2 = PieceCatalog.normalize(PieceCatalog.parse("XX"))

    private fun piece(shape: Shape, color: Int = 1) = Piece(id = random.nextLong(), shape = shape, color = color)

    private fun boardWith(vararg filled: Pair<Int, Int>): List<Int> {
        val board = BlockBreakEngine.emptyBoard().toMutableList()
        filled.forEach { (r, c) -> board[index(r, c)] = 2 }
        return board
    }

    /** Alle Felder einer Zeile außer den angegebenen Spalten belegt. */
    private fun rowExcept(row: Int, vararg freeCols: Int) =
        (0 until SIZE).filter { it !in freeCols }.map { row to it }

    @Test
    fun canPlace_respectsBoundsAndOccupiedCells() {
        val board = boardWith(0 to 0)
        assertFalse(BlockBreakEngine.canPlace(board, square, 0, 0))
        assertTrue(BlockBreakEngine.canPlace(board, square, 0, 1))
        assertFalse(BlockBreakEngine.canPlace(board, square, 7, 7))
        assertFalse(BlockBreakEngine.canPlace(board, square, -1, 0))
    }

    @Test
    fun placing_scoresOnePointPerCell() {
        val state = GameState(BlockBreakEngine.emptyBoard(), listOf(piece(square), piece(dot), piece(dot)))
        val result = requireNotNull(BlockBreakEngine.place(state, 0, 3, 3, random))
        assertEquals(4, result.points)
        assertEquals(0, result.linesCleared)
        assertNull(result.state.tray[0])
        assertEquals(4, result.state.board.count { it != 0 })
    }

    @Test
    fun completingRow_clearsItAndAddsBonus() {
        val board = boardWith(*rowExcept(0, 6, 7).toTypedArray())
        val state = GameState(board, listOf(piece(lineOf2), piece(dot), piece(dot)))
        val result = requireNotNull(BlockBreakEngine.place(state, 0, 0, 6, random))
        assertEquals(1, result.linesCleared)
        assertEquals(2 + BlockBreakEngine.lineBonus(1) + BlockBreakEngine.BOARD_CLEAR_BONUS, result.points)
        assertEquals(SIZE, result.clearedCells.size)
        assertTrue(result.state.board.all { it == 0 })
        assertTrue(result.boardCleared)
    }

    @Test
    fun rowAndColumn_clearSimultaneously() {
        val filled = rowExcept(0, 0) + (1 until SIZE).map { it to 0 }
        val state = GameState(boardWith(*filled.toTypedArray()), listOf(piece(dot), piece(dot), piece(dot)))
        val result = requireNotNull(BlockBreakEngine.place(state, 0, 0, 0, random))
        assertEquals(2, result.linesCleared)
        assertEquals(SIZE * 2 - 1, result.clearedCells.size)
        assertEquals(1 + BlockBreakEngine.lineBonus(2), result.points - BlockBreakEngine.BOARD_CLEAR_BONUS)
    }

    @Test
    fun consecutiveClears_increaseStreakMultiplier() {
        val filled = rowExcept(0, 7) + rowExcept(1, 7) + listOf(5 to 5)
        val state = GameState(boardWith(*filled.toTypedArray()), listOf(piece(dot), piece(dot), piece(square)))
        val first = requireNotNull(BlockBreakEngine.place(state, 0, 0, 7, random))
        assertEquals(1, first.state.streak)
        val second = requireNotNull(BlockBreakEngine.place(first.state, 1, 1, 7, random))
        assertEquals(2, second.state.streak)
        assertEquals(1 + BlockBreakEngine.lineBonus(1) * 2, second.points)
    }

    @Test
    fun streak_resetsAfterComboWindowWithoutClear() {
        var state = GameState(
            BlockBreakEngine.emptyBoard(),
            listOf(piece(dot), piece(dot), piece(dot)),
            streak = 3,
        )
        repeat(BlockBreakEngine.COMBO_WINDOW) { i ->
            state = requireNotNull(BlockBreakEngine.place(state, i, 4, i * 2, random)).state
        }
        assertEquals(0, state.streak)
    }

    @Test
    fun usingAllPieces_refillsTray() {
        val state = GameState(BlockBreakEngine.emptyBoard(), listOf(piece(dot), null, null))
        val result = requireNotNull(BlockBreakEngine.place(state, 0, 0, 0, random))
        assertEquals(BlockBreakEngine.TRAY_SIZE, result.state.tray.filterNotNull().size)
    }

    @Test
    fun gameOver_whenNoPieceFits() {
        // Schachbrettmuster: kein 2x2 passt mehr, nur Einzelfelder.
        val board = List(BlockBreakEngine.CELL_COUNT) { i -> if ((i / SIZE + i % SIZE) % 2 == 0) 3 else 0 }
        assertTrue(BlockBreakEngine.isGameOver(board, listOf(piece(square), null, piece(lineOf2))))
        assertFalse(BlockBreakEngine.isGameOver(board, listOf(piece(square), piece(dot), null)))
    }

    @Test
    fun invalidMoves_returnNull() {
        val state = GameState(boardWith(0 to 0), listOf(piece(square), null, piece(dot)))
        assertNull(BlockBreakEngine.place(state, 0, 0, 0, random))
        assertNull(BlockBreakEngine.place(state, 1, 4, 4, random))
        assertNull(BlockBreakEngine.place(state.copy(isGameOver = true), 2, 4, 4, random))
    }

    @Test
    fun newGame_traysArePlaceable() {
        repeat(50) {
            val state = BlockBreakEngine.newGame(Random(it))
            assertEquals(BlockBreakEngine.TRAY_SIZE, state.tray.size)
            assertFalse(state.isGameOver)
            state.tray.forEach { p -> assertTrue(BlockBreakEngine.canPlaceAnywhere(state.board, p!!.shape)) }
        }
    }

    @Test
    fun coins_equalLinesTimesStreak_plusBoardClearBonus() {
        // Zwei Linien gleichzeitig im zweiten Combo-Zug: 2 × 2 = 4 Coins, Brett danach nicht leer.
        val filled = rowExcept(0, 0) + (1 until SIZE).map { it to 0 } + listOf(5 to 5)
        val state = GameState(boardWith(*filled.toTypedArray()), listOf(piece(dot), piece(dot), piece(dot)), streak = 1)
        val result = requireNotNull(BlockBreakEngine.place(state, 0, 0, 0, random))
        assertEquals(4, result.coins)
        assertEquals(4, result.state.coinsEarned)

        // Leeres Brett: 1 Linie × Combo 1 + Bonus.
        val clearing = GameState(boardWith(*rowExcept(0, 7).toTypedArray()), listOf(piece(dot), piece(dot), piece(dot)))
        val cleared = requireNotNull(BlockBreakEngine.place(clearing, 0, 0, 7, random))
        assertEquals(1 + BlockBreakEngine.BOARD_CLEAR_COINS, cleared.coins)

        // Ohne Linie keine Coins.
        val none = requireNotNull(BlockBreakEngine.place(cleared.state, 1, 4, 4, random))
        assertEquals(0, none.coins)
        assertEquals(cleared.state.coinsEarned, none.state.coinsEarned)
    }

    @Test
    fun save_roundTripRestoresIdenticalState() {
        var state = BlockBreakEngine.newGame(Random(3))
        // Einige gültige Züge machen, damit Brett, Tray und Score nicht trivial sind.
        repeat(5) {
            val slot = state.tray.indexOfFirst { it != null }
            val piece = state.tray[slot]!!
            val pos = (0 until SIZE * SIZE).first { BlockBreakEngine.canPlace(state.board, piece.shape, it / SIZE, it % SIZE) }
            state = requireNotNull(BlockBreakEngine.place(state, slot, pos / SIZE, pos % SIZE, random)).state
        }
        val decoded = requireNotNull(BlockBreakSaves.decode(BlockBreakSaves.encode(state, bestAtStart = 42)))
        assertEquals(state, decoded.game)
        assertEquals(42, decoded.bestAtStart)
    }

    @Test
    fun save_rejectsBrokenOrFinishedGames() {
        assertNull(BlockBreakSaves.decode("kein json"))
        assertNull(BlockBreakSaves.decode("""{"version":999,"game":{"board":[],"tray":[]}}"""))
        val over = BlockBreakEngine.newGame(Random(1)).copy(isGameOver = true)
        assertNull(BlockBreakSaves.decode(BlockBreakSaves.encode(over, 0)))
    }

    @Test
    fun catalog_containsOnlyValidUniqueShapes() {
        val shapes = PieceCatalog.allShapes
        assertEquals(shapes.size, shapes.toSet().size)
        shapes.forEach {
            assertTrue(it.width in 1..5 && it.height in 1..5)
            assertTrue(it.cells.all { c -> c.row >= 0 && c.col >= 0 })
        }
    }
}
