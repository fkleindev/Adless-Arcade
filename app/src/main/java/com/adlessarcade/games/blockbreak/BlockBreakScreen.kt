package com.adlessarcade.games.blockbreak

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.adlessarcade.core.GameRegistry
import com.adlessarcade.ui.components.ArcadeIconButton
import com.adlessarcade.ui.components.ArcadeScaffold
import com.adlessarcade.ui.components.GameOverOverlay
import com.adlessarcade.ui.components.CoinGold
import com.adlessarcade.ui.components.CoinIcon
import com.adlessarcade.ui.components.drawBlock
import com.adlessarcade.ui.components.drawSkinBlock
import com.adlessarcade.ui.components.drawEmptyCell
import com.adlessarcade.ui.theme.ArcadeOutline
import com.adlessarcade.ui.theme.ArcadeSurface
import com.adlessarcade.ui.theme.ArcadeText
import com.adlessarcade.ui.theme.ArcadeTextMuted
import com.adlessarcade.ui.theme.BoardBackground
import com.adlessarcade.ui.theme.EmptyCell
import com.adlessarcade.ui.theme.NeonCyan
import com.adlessarcade.ui.theme.NeonPink
import com.adlessarcade.ui.theme.NeonYellow
import com.adlessarcade.ui.theme.LocalCosmetics
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private const val SIZE = BlockBreakEngine.SIZE

/** Hält Drag-Zustand und Geometrie; alle Positionen in Root-Koordinaten. */
@Stable
private class DragController {
    var slot by mutableStateOf<Int?>(null)
    var pointer by mutableStateOf(Offset.Zero)
    var boardOrigin by mutableStateOf(Offset.Zero)
    var cellSize by mutableFloatStateOf(0f)
    var containerOrigin by mutableStateOf(Offset.Zero)
    val slotOrigins = Array(BlockBreakEngine.TRAY_SIZE) { Offset.Zero }

    /** Das gezogene Teil schwebt über dem Finger, damit es sichtbar bleibt. */
    fun pieceTopLeft(shape: Shape, liftPx: Float): Offset =
        pointer - Offset(shape.width * cellSize / 2f, shape.height * cellSize + liftPx)

    fun target(board: List<Int>, shape: Shape, liftPx: Float): Cell? {
        if (cellSize <= 0f) return null
        val rel = pieceTopLeft(shape, liftPx) - boardOrigin
        val row = (rel.y / cellSize).roundToInt()
        val col = (rel.x / cellSize).roundToInt()
        return if (BlockBreakEngine.canPlace(board, shape, row, col)) Cell(row, col) else null
    }
}

@Composable
fun BlockBreakScreen(
    modeId: String,
    onExit: () -> Unit,
    viewModel: BlockBreakViewModel = viewModel(),
) {
    val ui = viewModel.state
    val game = ui.game
    val haptics = LocalHapticFeedback.current
    val skin = LocalCosmetics.current.blockSkin
    val liftPx = with(LocalDensity.current) { 56.dp.toPx() }
    val drag = remember { DragController() }
    var showRestartDialog by remember { mutableStateOf(false) }
    var showGameOver by remember { mutableStateOf(false) }

    LaunchedEffect(game.isGameOver) {
        if (game.isGameOver) {
            delay(700) // Kurz das volle Brett zeigen
            showGameOver = true
        } else {
            showGameOver = false
        }
    }

    val target by remember(viewModel, liftPx) {
        derivedStateOf {
            val slot = drag.slot ?: return@derivedStateOf null
            val piece = viewModel.state.game.tray.getOrNull(slot) ?: return@derivedStateOf null
            drag.target(viewModel.state.game.board, piece.shape, liftPx)
        }
    }
    val draggedPiece = drag.slot?.let { game.tray.getOrNull(it) }
    val previewClear = remember(target, draggedPiece, game.board) {
        val t = target
        if (t != null && draggedPiece != null) {
            BlockBreakEngine.previewClear(game.board, draggedPiece.shape, t.row, t.col)
        } else {
            emptySet()
        }
    }
    val placeable = remember(game.board, game.tray) {
        game.tray.map { it != null && BlockBreakEngine.canPlaceAnywhere(game.board, it.shape) }
    }

    val startDrag by rememberUpdatedState { slot: Int, position: Offset ->
        if (game.isGameOver || game.tray.getOrNull(slot) == null) {
            false
        } else {
            drag.pointer = position
            drag.slot = slot
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            true
        }
    }
    val drop by rememberUpdatedState {
        val slot = drag.slot
        val t = target
        drag.slot = null
        if (slot != null && t != null) {
            val result = viewModel.place(slot, t.row, t.col)
            if (result != null) {
                haptics.performHapticFeedback(
                    if (result.linesCleared > 0) HapticFeedbackType.LongPress
                    else HapticFeedbackType.TextHandleMove
                )
            }
        }
    }

    val modeTitle = GameRegistry.find(BlockBreak.GAME_ID)?.modes?.firstOrNull { it.id == modeId }?.title

    ArcadeScaffold(
        title = "Block Break",
        subtitle = modeTitle,
        onBack = onExit,
        actions = {
            ArcadeIconButton(onClick = {
                if (game.score > 0 && !game.isGameOver) showRestartDialog = true else viewModel.restart()
            }) {
                Icon(Icons.Filled.Refresh, contentDescription = "Neu starten")
            }
        },
    ) {
        if (!viewModel.loaded) return@ArcadeScaffold
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .onGloballyPositioned { drag.containerOrigin = it.positionInRoot() }
        ) {
            val trayHeight = 150.dp
            val boardSize = minOf(maxWidth - 32.dp, maxHeight - trayHeight - 150.dp).coerceAtLeast(160.dp)

            Column(
                Modifier.fillMaxSize().padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ScoreHeader(score = game.score, best = ui.best, streak = game.streak, coins = game.coinsEarned)
                Spacer(Modifier.height(12.dp))
                Box(
                    Modifier
                        .size(boardSize)
                        .clip(RoundedCornerShape(20.dp))
                        .background(BoardBackground)
                        .border(1.dp, ArcadeOutline, RoundedCornerShape(20.dp))
                        .padding(8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Board(
                        board = game.board,
                        draggedPiece = draggedPiece,
                        target = target,
                        previewClear = previewClear,
                        clearEvent = ui.lastClear,
                        modifier = Modifier
                            .fillMaxSize()
                            .onGloballyPositioned {
                                drag.boardOrigin = it.positionInRoot()
                                drag.cellSize = it.size.width / SIZE.toFloat()
                            },
                    )
                    ClearPopup(ui.lastClear)
                }
                Spacer(Modifier.weight(1f))
                Row(Modifier.fillMaxWidth().height(trayHeight)) {
                    repeat(BlockBreakEngine.TRAY_SIZE) { index ->
                        val piece = game.tray.getOrNull(index)
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .onGloballyPositioned { drag.slotOrigins[index] = it.positionInRoot() }
                                .pointerInput(index) {
                                    awaitEachGesture {
                                        val down = awaitFirstDown()
                                        val origin = drag.slotOrigins[index]
                                        if (!startDrag(index, origin + down.position)) return@awaitEachGesture
                                        down.consume()
                                        try {
                                            while (true) {
                                                val event = awaitPointerEvent()
                                                val change = event.changes.firstOrNull { it.id == down.id }
                                                if (change == null) {
                                                    drag.slot = null
                                                    break
                                                }
                                                change.consume()
                                                if (!change.pressed) {
                                                    drop()
                                                    break
                                                }
                                                drag.pointer = origin + change.position
                                            }
                                        } finally {
                                            if (drag.slot == index) drag.slot = null
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (piece != null && drag.slot != index) {
                                TrayPiece(
                                    piece = piece,
                                    boardCellSize = drag.cellSize,
                                    enabled = placeable.getOrElse(index) { false },
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            // Gezogenes Teil in voller Größe über allem anderen
            if (draggedPiece != null) {
                Canvas(Modifier.fillMaxSize()) {
                    val cell = drag.cellSize
                    val topLeft = drag.pieceTopLeft(draggedPiece.shape, liftPx) - drag.containerOrigin
                    val color = skin.color(draggedPiece.color)
                    draggedPiece.shape.cells.forEach {
                        drawBlock(topLeft + Offset(it.col * cell, it.row * cell), cell, color, style = skin.style)
                    }
                }
            }

            GameOverOverlay(
                visible = showGameOver,
                score = game.score,
                best = ui.best,
                isNewBest = ui.isNewBest,
                onPlayAgain = viewModel::restart,
                onMenu = onExit,
                coinsEarned = game.coinsEarned + ui.recordBonus,
                bonusCoins = ui.recordBonus,
            )
        }
    }

    if (showRestartDialog) {
        AlertDialog(
            onDismissRequest = { showRestartDialog = false },
            containerColor = ArcadeSurface,
            title = { Text("Neu starten?") },
            text = { Text("Dein aktueller Fortschritt geht verloren.", color = ArcadeTextMuted) },
            confirmButton = {
                TextButton(onClick = {
                    showRestartDialog = false
                    viewModel.restart()
                }) { Text("Neu starten", color = NeonCyan) }
            },
            dismissButton = {
                TextButton(onClick = { showRestartDialog = false }) { Text("Abbrechen", color = ArcadeTextMuted) }
            },
        )
    }
}

@Composable
private fun ScoreHeader(score: Int, best: Int, streak: Int, coins: Int) {
    val animatedScore by animateIntAsState(score, tween(350), label = "score")
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Star, contentDescription = null, tint = NeonYellow, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("$best", style = MaterialTheme.typography.titleMedium, color = ArcadeTextMuted)
            Spacer(Modifier.width(16.dp))
            CoinIcon(16.dp)
            Spacer(Modifier.width(6.dp))
            Text("$coins", style = MaterialTheme.typography.titleMedium, color = CoinGold)
        }
        Text("$animatedScore", style = MaterialTheme.typography.displayMedium, color = ArcadeText)
        Box(Modifier.height(22.dp), contentAlignment = Alignment.Center) {
            // Voll qualifiziert, da sonst die ColumnScope-Variante der äußeren Column gewählt wird.
            androidx.compose.animation.AnimatedVisibility(visible = streak > 1, enter = fadeIn() + scaleIn(), exit = fadeOut()) {
                Text("COMBO x$streak", style = MaterialTheme.typography.labelLarge, color = NeonPink)
            }
        }
    }
}

@Composable
private fun Board(
    board: List<Int>,
    draggedPiece: Piece?,
    target: Cell?,
    previewClear: Set<Int>,
    clearEvent: ClearEvent?,
    modifier: Modifier = Modifier,
) {
    val skin = LocalCosmetics.current.blockSkin
    val clearProgress = remember { Animatable(0f) }
    LaunchedEffect(clearEvent?.id) {
        if (clearEvent != null) {
            clearProgress.snapTo(1f)
            clearProgress.animateTo(0f, tween(450, easing = FastOutSlowInEasing))
        }
    }

    Canvas(modifier) {
        val cell = size.width / SIZE
        val pieceColor = draggedPiece?.let { skin.color(it.color) }

        for (i in 0 until BlockBreakEngine.CELL_COUNT) {
            val topLeft = Offset((i % SIZE) * cell, (i / SIZE) * cell)
            val value = board[i]
            when {
                pieceColor != null && i in previewClear -> drawBlock(topLeft, cell, pieceColor, style = skin.style)
                value != 0 -> drawSkinBlock(skin, topLeft, cell, value)
                else -> drawEmptyCell(topLeft, cell, EmptyCell)
            }
        }

        if (draggedPiece != null && target != null && pieceColor != null) {
            draggedPiece.shape.cells.forEach {
                val topLeft = Offset((target.col + it.col) * cell, (target.row + it.row) * cell)
                drawBlock(topLeft, cell, pieceColor, alpha = 0.45f, style = skin.style)
            }
        }

        val p = clearProgress.value
        if (p > 0f && clearEvent != null) {
            val scale = 1f + (1f - p) * 0.3f
            val s = cell * scale
            clearEvent.cells.forEach { (i, color) ->
                val center = Offset((i % SIZE + 0.5f) * cell, (i / SIZE + 0.5f) * cell)
                val topLeft = center - Offset(s / 2f, s / 2f)
                drawSkinBlock(skin, topLeft, s, color, alpha = p)
                drawRoundRect(
                    Color.White,
                    topLeft,
                    Size(s, s),
                    CornerRadius(s * 0.2f),
                    alpha = p * 0.45f,
                )
            }
        }
    }
}

@Composable
private fun TrayPiece(piece: Piece, boardCellSize: Float, enabled: Boolean) {
    val skin = LocalCosmetics.current.blockSkin
    val appear = remember(piece.id) { Animatable(0f) }
    LaunchedEffect(piece.id) { appear.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 400f)) }
    Canvas(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = appear.value
                scaleY = appear.value
            }
    ) {
        val maxCell = if (boardCellSize > 0f) boardCellSize * 0.55f else Float.MAX_VALUE
        val cell = minOf(maxCell, size.width / 5.5f, size.height / 5.5f)
        val shape = piece.shape
        val origin = Offset(
            (size.width - shape.width * cell) / 2f,
            (size.height - shape.height * cell) / 2f,
        )
        val color = skin.color(piece.color)
        shape.cells.forEach {
            drawBlock(origin + Offset(it.col * cell, it.row * cell), cell, color, alpha = if (enabled) 1f else 0.3f, style = skin.style)
        }
    }
}

@Composable
private fun ClearPopup(event: ClearEvent?) {
    if (event == null) return
    val progress = remember(event.id) { Animatable(0f) }
    LaunchedEffect(event.id) { progress.animateTo(1f, tween(1100, easing = LinearOutSlowInEasing)) }
    if (progress.value >= 1f) return

    val textStyle = TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.6f), Offset(0f, 4f), 12f))
    Column(
        Modifier.graphicsLayer {
            val p = progress.value
            translationY = -p * 60.dp.toPx()
            alpha = if (p < 0.7f) 1f else (1f - p) / 0.3f
            val pop = 0.7f + 0.3f * minOf(1f, p * 6f)
            scaleX = pop
            scaleY = pop
        },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (event.boardCleared) {
            Text(
                "BRETT LEER!",
                style = MaterialTheme.typography.titleLarge.merge(textStyle),
                color = NeonYellow,
            )
        }
        if (event.streak > 1) {
            Text(
                "COMBO x${event.streak}",
                style = MaterialTheme.typography.titleLarge.merge(textStyle),
                color = NeonPink,
            )
        }
        Text(
            "+${event.points}",
            style = MaterialTheme.typography.displaySmall.merge(textStyle),
            color = ArcadeText,
        )
        if (event.coins > 0) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CoinIcon(20.dp)
                Spacer(Modifier.width(6.dp))
                Text("+${event.coins}", style = MaterialTheme.typography.titleLarge.merge(textStyle), color = CoinGold)
            }
        }
    }
}
