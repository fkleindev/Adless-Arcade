package com.adlessarcade.games.galaxytowers

import android.content.pm.ActivityInfo
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.delay
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.adlessarcade.core.GameRegistry
import com.adlessarcade.ui.components.ArcadeIconButton
import com.adlessarcade.ui.components.ArcadeScaffold
import com.adlessarcade.ui.components.CoinGold
import com.adlessarcade.ui.components.CoinIcon
import com.adlessarcade.ui.components.GameOverOverlay
import com.adlessarcade.ui.components.LockOrientation
import com.adlessarcade.ui.components.drawCoin
import com.adlessarcade.ui.components.PrimaryButton
import com.adlessarcade.ui.components.SecondaryButton
import com.adlessarcade.ui.theme.CardBack
import com.adlessarcade.ui.theme.ArcadeOutline
import com.adlessarcade.ui.theme.ArcadeSurface
import com.adlessarcade.ui.theme.ArcadeText
import com.adlessarcade.ui.theme.ArcadeTextMuted
import com.adlessarcade.ui.theme.LocalCosmetics
import com.adlessarcade.ui.theme.NeonCyan
import com.adlessarcade.ui.theme.NeonPink
import com.adlessarcade.ui.theme.NeonYellow
import com.adlessarcade.ui.theme.SpaceBackdrop
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.sin

/** Abstand von Kartenmitte zu Kartenmitte in Kartenbreiten (kleine Lücke zwischen den Turmkarten). */
private const val PITCH = 1.12f
/** Zusätzlicher Abstand zwischen den Türmen in Kartenbreiten. */
private const val TOWER_GAP = 0.5f
/** Vertikaler Versatz der Turmreihen in Kartenhöhen. */
private const val ROW_STEP = 0.55f
/** Größe der Stapel- und Ablagekarten relativ zu den Turmkarten. */
private const val PILE_SCALE = 1.1f

/**
 * Geometrie des Spieltischs im Querformat wie im ersten Teil: Türme oben, darunter Nachziehstapel, Ablage,
 * Sanduhr und zweite Ablage. Die Kartengröße richtet sich nach Breite und Höhe des Bereichs.
 */
private class TableLayout(width: Float, height: Float, gap: Float) {
    val cardW = minOf(
        width / (9f * PITCH + 1f + 2f * TOWER_GAP),
        (height - gap * 2f) / (3f * ROW_STEP * CARD_RATIO + CARD_RATIO + PILE_SCALE * CARD_RATIO),
    )
    val cardSize = Size(cardW, cardW * CARD_RATIO)
    private val pitch = cardW * PITCH
    private val towerGap = cardW * TOWER_GAP
    private val margin = (width - (9f * pitch + cardW + 2f * towerGap)) / 2f
    private val rowStep = cardSize.height * ROW_STEP
    val pileSize = Size(cardW * PILE_SCALE, cardW * PILE_SCALE * CARD_RATIO)
    private val used = rowStep * 3f + cardSize.height + gap * 1.2f + pileSize.height
    private val towerTop = ((height - used) / 2f).coerceAtLeast(gap * 0.4f)
    val pileY = towerTop + rowStep * 3f + cardSize.height + gap * 1.2f
    private val center = width / 2f
    val stockTopLeft = Offset(margin, pileY)
    val wasteTopLeft = Offset(center - pileSize.width * 1.5f, pileY)
    /** Zweite Ablage rechts neben der Sanduhr wie im ersten Teil. */
    val waste2TopLeft = Offset(center + pileSize.width * 0.5f, pileY)
    /** Mitte der Sanduhr zwischen den beiden Ablagen. */
    val hourglassCenter = Offset(center, pileY + pileSize.height / 2f)
    /** Freier Bereich rechts neben der zweiten Ablage für Ketten-Banner und Punkte (reicht etwas in die Höhe). */
    val infoArea: Rect = run {
        val left = waste2TopLeft.x + pileSize.width + gap * 0.5f
        val right = maxOf(left + cardW * 2f, margin + 9f * pitch + cardW + 2f * towerGap)
        val top = (pileY - cardW * 0.6f).coerceAtLeast(0f)
        Rect(left, top, right, (pileY + pileSize.height).coerceAtMost(height))
    }

    fun pileTopLeft(pile: Int) = if (pile == 0) wasteTopLeft else waste2TopLeft

    fun slotTopLeft(index: Int): Offset {
        val (x, row) = GalaxyTowersEngine.slotPosition(index)
        return Offset(margin + x * pitch + gapOffset(index), towerTop + row * rowStep)
    }

    /**
     * Zusätzlicher Abstand zwischen den drei Türmen. Die durchgehende untere Reihe teilt sich die Karten 3 und 6
     * mit zwei Türmen – sie liegen mittig in der Lücke.
     */
    private fun gapOffset(index: Int): Float {
        val peak = when (GalaxyTowersEngine.rowOf(index)) {
            0 -> index.toFloat()
            1 -> ((index - 3) / 2).toFloat()
            2 -> ((index - 9) / 3).toFloat()
            else -> when (val j = index - 18) {
                3 -> 0.5f
                6 -> 1.5f
                else -> (j / 3).toFloat().coerceAtMost(2f)
            }
        }
        return peak * towerGap
    }

    fun slotRect(index: Int) = Rect(slotTopLeft(index), cardSize)
    val stockRect get() = Rect(stockTopLeft, pileSize)
}

@Composable
fun GalaxyTowersScreen(
    modeId: String,
    onExit: () -> Unit,
    viewModel: GalaxyTowersViewModel = viewModel(),
) {
    val game = viewModel.game
    val haptics = LocalHapticFeedback.current
    val cosmetics = LocalCosmetics.current
    val textMeasurer = rememberTextMeasurer(cacheSize = 64)
    var infoDialog by remember { mutableStateOf<PowerUp?>(null) }
    var showRestartDialog by remember { mutableStateOf(false) }

    // Flug der zuletzt abgelegten Karte von ihrem Ursprung (Turm oder Stapel) zur Ablage
    var flightFrom by remember { mutableStateOf<Flight?>(null) }
    var flight by remember { mutableFloatStateOf(1f) }
    val scope = rememberCoroutineScope()
    fun startFlight(from: Flight) {
        flightFrom = from
        flight = 0f
        scope.launch {
            animate(0f, 1f, animationSpec = tween(230, easing = FastOutSlowInEasing)) { v, _ -> flight = v }
            if (flightFrom === from) flightFrom = null
        }
    }

    // Falsch angetippte Karte: kurzes Wackeln und rote Strafpunkte
    val misplay = viewModel.lastMisplay
    val misplayProgress = remember { Animatable(1f) }
    LaunchedEffect(misplay?.id) {
        if (misplay != null) {
            misplayProgress.snapTo(0f)
            misplayProgress.animateTo(1f, tween(1100, easing = LinearOutSlowInEasing))
        }
    }

    // Rundenzeit: läuft pro Frame, das ViewModel ignoriert Ticks in Pause und nach Rundenende.
    LaunchedEffect(viewModel) {
        var last = 0L
        while (isActive) {
            withFrameNanos { now ->
                if (last != 0L) viewModel.tick(((now - last) / 1_000_000_000f).coerceAtMost(0.1f))
                last = now
            }
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { viewModel.pause() }

    // Wie im ersten Teil im Querformat; beim Verlassen dreht die App zurück ins Hochformat.
    LockOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE)

    val modeTitle = GameRegistry.find(GalaxyTowers.GAME_ID)?.modes?.firstOrNull { it.id == modeId }?.title

    // Ohne Titelleiste: im Querformat zählt jede Zeile Höhe, Zurück und Pause sitzen in der Seitenleiste.
    ArcadeScaffold {
        if (!viewModel.loaded) return@ArcadeScaffold
        Box(Modifier.fillMaxSize()) {
            Backdrop(cosmetics.backdrop, Modifier.fillMaxSize())
            Row(Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 6.dp)) {
                BoxWithConstraints(Modifier.weight(1f).fillMaxHeight()) {
                    val density = LocalDensity.current
                    val widthPx = with(density) { maxWidth.toPx() }
                    val heightPx = with(density) { maxHeight.toPx() }
                    val layout = remember(widthPx, heightPx) { TableLayout(widthPx, heightPx, with(density) { 14.dp.toPx() }) }
                    Canvas(
                        Modifier
                            .fillMaxSize()
                            .pointerInput(layout) {
                                detectTapGestures { pos ->
                                    val g = viewModel.game
                                    if (viewModel.paused || !g.inPlay) return@detectTapGestures
                                    // Oberste Karte zuerst: höhere Indizes liegen weiter vorne.
                                    for (i in GalaxyTowersEngine.TOWER_SIZE - 1 downTo 0) {
                                        if (g.tower[i] == null || !layout.slotRect(i).contains(pos)) continue
                                        if (GalaxyTowersEngine.canPlay(g, i)) {
                                            val result = viewModel.play(i)
                                            if (result != null) startFlight(Flight(layout.slotTopLeft(i), layout.cardSize, result.pile))
                                            haptics.performHapticFeedback(
                                                if (result?.peakCleared == true || result?.roundCleared == true) HapticFeedbackType.LongPress
                                                else HapticFeedbackType.TextHandleMove
                                            )
                                        } else if (viewModel.misplay(i)) {
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        }
                                        return@detectTapGestures
                                    }
                                    if (layout.stockRect.contains(pos) && g.stock.isNotEmpty()) {
                                        if (viewModel.draw()) {
                                            startFlight(Flight(layout.stockTopLeft, layout.pileSize, pile = 0))
                                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        }
                                    }
                                }
                            },
                    ) {
                        drawTable(game, layout, textMeasurer, cosmetics.cardBack, flightFrom, flight, misplay, misplayProgress.value)
                    }
                    GoldBurst(viewModel.lastPlay, layout)
                    TimeGlow(viewModel.lastPlay, layout)
                    // Kette und Punkte im freien Bereich rechts neben der zweiten Ablage – verdecken keine Karten.
                    val area = layout.infoArea
                    Column(
                        Modifier
                            .padding(
                                start = with(density) { area.left.toDp() },
                                top = with(density) { area.top.toDp() },
                            )
                            .size(with(density) { area.width.toDp() }, with(density) { area.height.toDp() }),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        ChainBanner(game.streak, viewModel.chainPoints)
                        PlayPopup(viewModel.lastPlay)
                    }
                }
                Spacer(Modifier.width(8.dp))
                Column(
                    Modifier
                        .width(164.dp)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ArcadeIconButton(onClick = onExit) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                        }
                        Spacer(Modifier.weight(1f))
                        ArcadeIconButton(onClick = { if (viewModel.paused) viewModel.resume() else viewModel.pause() }) {
                            PauseGlyph()
                        }
                    }
                    InfoPanel(game = game, modeTitle = modeTitle, best = viewModel.best, wallet = viewModel.walletCoins)
                    if (GalaxyTowersEngine.isStuck(game)) StuckPanel(onEndRound = viewModel::endRound)
                    PowerUpGrid(
                        game = game,
                        foundTick = viewModel.lastPlay?.takeIf { it.reward != null },
                        onUse = { p ->
                            if (game.charges(p) > 0) {
                                if (viewModel.usePowerUp(p)) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            } else {
                                infoDialog = p
                            }
                        },
                    )
                }
            }

            val roundOver = game.roundOver
            if (roundOver != null && !game.isGameOver) {
                RoundOverOverlay(
                    game = game,
                    reason = roundOver,
                    clear = viewModel.lastClear,
                    onNext = viewModel::nextRound,
                )
            }

            if (viewModel.paused) {
                PauseOverlay(
                    onResume = viewModel::resume,
                    onRestart = { showRestartDialog = true },
                    onMenu = onExit,
                )
            }

            GameOverOverlay(
                visible = game.isGameOver,
                score = game.score,
                best = viewModel.best,
                isNewBest = viewModel.isNewBest,
                onPlayAgain = viewModel::restart,
                onMenu = onExit,
                title = "Ergebnis",
                message = "${game.roundsCleared} von ${game.mode.rounds} " + (if (game.mode.rounds == 1) "Runde" else "Runden") + " abgeräumt",
                coinsEarned = game.coinsEarned + viewModel.recordBonus,
                bonusCoins = viewModel.recordBonus,
                leaderboardRank = viewModel.leaderboardRank,
            )
        }
    }

    infoDialog?.let { p ->
        AlertDialog(
            onDismissRequest = { infoDialog = null },
            containerColor = ArcadeSurface,
            title = { Text("${p.symbol}  ${p.title}") },
            text = {
                Column {
                    Text(p.description, color = ArcadeTextMuted)
                    Spacer(Modifier.height(10.dp))
                    Text("Noch nicht gefunden – Power-ups fallen zufällig beim Ablegen von Karten, mit langen Ketten öfter.", color = ArcadeText)
                }
            },
            confirmButton = {
                TextButton(onClick = { infoDialog = null }) { Text("OK", color = NeonCyan) }
            },
        )
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

private fun DrawScope.drawTable(
    game: GameState,
    layout: TableLayout,
    textMeasurer: TextMeasurer,
    back: CardBack,
    flightFrom: Flight?,
    flight: Float,
    misplay: MisplayEvent?,
    misplayProgress: Float,
) {
    // Türme: von hinten (Spitzen) nach vorne (untere Reihe); spielbare Karten werden bewusst nicht markiert.
    val shaking = misplay?.index?.takeIf { misplayProgress < 0.35f }
    for (i in 0 until GalaxyTowersEngine.TOWER_SIZE) {
        val card = game.tower[i] ?: continue
        var topLeft = layout.slotTopLeft(i)
        if (i == shaking) {
            val p = misplayProgress / 0.35f
            topLeft += Offset(sin(p * PI.toFloat() * 6f) * (1f - p) * layout.cardW * 0.15f, 0f)
        }
        val open = GalaxyTowersEngine.isFree(game.tower, i)
        when {
            open -> drawCardFace(topLeft, layout.cardSize, card, textMeasurer)
            game.scanActive -> {
                drawCardFace(topLeft, layout.cardSize, card, textMeasurer)
                drawRoundRect(NeonCyan.copy(alpha = 0.28f), topLeft, layout.cardSize, CornerRadius(layout.cardW * 0.1f))
            }
            else -> drawCardBack(topLeft, layout.cardSize, back)
        }
        if (i == shaking) {
            drawRoundRect(NeonPink.copy(alpha = 0.35f * (1f - misplayProgress / 0.35f)), topLeft, layout.cardSize, CornerRadius(layout.cardW * 0.1f))
        }
    }
    // Rote Strafpunkte steigen über der falschen Karte auf
    if (misplay != null && misplayProgress < 1f) {
        val tl = layout.slotTopLeft(misplay.index)
        val text = textMeasurer.measure(
            "−${misplay.penalty}",
            TextStyle(color = NeonPink.copy(alpha = if (misplayProgress < 0.7f) 1f else (1f - misplayProgress) / 0.3f), fontSize = (layout.cardW * 0.42f).toSp(), fontWeight = FontWeight.Black, shadow = Shadow(Color.Black, Offset(0f, 2f), 8f)),
        )
        drawText(text, topLeft = tl + Offset((layout.cardW - text.size.width) / 2f, -text.size.height * 0.6f - misplayProgress * layout.cardW * 0.6f))
    }

    // Nachziehstapel, die Kartenzahl steht wie im ersten Teil auf dem Stapel
    val stockTl = layout.stockTopLeft
    if (game.stock.isNotEmpty()) {
        if (game.stock.size > 1) drawCardBack(stockTl + Offset(layout.cardW * 0.08f, -layout.cardW * 0.08f), layout.pileSize, back, alpha = 0.6f)
        drawCardBack(stockTl, layout.pileSize, back)
    } else {
        drawCardSlot(stockTl, layout.pileSize)
    }
    val countLayout = textMeasurer.measure(
        "${game.stock.size}",
        TextStyle(color = Color.White, fontSize = (layout.cardW * 0.5f).toSp(), fontWeight = FontWeight.Black, shadow = Shadow(Color.Black, Offset(0f, 2f), 8f)),
    )
    drawText(countLayout, topLeft = stockTl + Offset((layout.pileSize.width - countLayout.size.width) / 2f, (layout.pileSize.height - countLayout.size.height) / 2f))

    if (game.mode.timed) drawHourglass(game, layout, textMeasurer)

    // Beide Ablagen im Rahmen; die gesperrte zweite Ablage zeigt, wie man sie freischaltet.
    drawPile(game.waste, 0, layout, textMeasurer, flightFrom, flight)
    val waste2 = game.waste2
    if (waste2 != null) {
        drawPile(waste2, 1, layout, textMeasurer, flightFrom, flight)
    } else {
        val tl = layout.waste2TopLeft
        drawCardSlot(tl, layout.pileSize)
        val hint = textMeasurer.measure(
            "Kette\n${GalaxyTowersEngine.SECOND_PILE_STREAK}",
            TextStyle(color = Color.White.copy(alpha = 0.45f), fontSize = (layout.cardW * 0.3f).toSp(), fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
        )
        drawText(hint, topLeft = tl + Offset((layout.pileSize.width - hint.size.width) / 2f, (layout.pileSize.height - hint.size.height) / 2f))
    }
}

/** Sanduhr mit Restsekunden zwischen den Ablagen; der Sand rinnt mit der Zeit, die letzten 10 s pulsiert sie rot. */
private fun DrawScope.drawHourglass(game: GameState, layout: TableLayout, textMeasurer: TextMeasurer) {
    val c = layout.hourglassCenter
    val w = layout.cardW * 0.5f
    val h = layout.cardW * 0.75f
    val urgent = game.timeLeft <= 10f && game.inPlay
    val color = if (urgent) NeonPink.copy(alpha = 0.6f + 0.4f * sin(game.timeLeft * 12f)) else NeonYellow
    val glass = c + Offset(0f, h * 0.15f)
    val frac = (game.timeLeft / game.mode.roundTime(game.round).coerceAtLeast(1f)).coerceIn(0f, 1f)
    val outline = Path().apply {
        moveTo(glass.x - w / 2f, glass.y - h / 2f)
        lineTo(glass.x + w / 2f, glass.y - h / 2f)
        lineTo(glass.x, glass.y)
        lineTo(glass.x + w / 2f, glass.y + h / 2f)
        lineTo(glass.x - w / 2f, glass.y + h / 2f)
        lineTo(glass.x, glass.y)
        close()
    }
    // Sand oben (Rest) und unten (verronnen) als Dreiecke
    val top = h / 2f * frac
    drawPath(Path().apply {
        moveTo(glass.x - w / 2f * frac, glass.y - top)
        lineTo(glass.x + w / 2f * frac, glass.y - top)
        lineTo(glass.x, glass.y)
        close()
    }, color.copy(alpha = 0.8f))
    val bottom = h / 2f * (1f - frac)
    drawPath(Path().apply {
        moveTo(glass.x, glass.y + h / 2f - bottom)
        lineTo(glass.x + w / 2f * (1f - frac), glass.y + h / 2f)
        lineTo(glass.x - w / 2f * (1f - frac), glass.y + h / 2f)
        close()
    }, color.copy(alpha = 0.8f))
    drawPath(outline, color, style = Stroke(width = layout.cardW * 0.05f))
    val text = textMeasurer.measure(
        "${ceil(game.timeLeft).toInt()}",
        TextStyle(color = color, fontSize = (layout.cardW * 0.4f).toSp(), fontWeight = FontWeight.Black, shadow = Shadow(Color.Black, Offset(0f, 2f), 6f)),
    )
    drawText(text, topLeft = Offset(c.x - text.size.width / 2f, glass.y - h / 2f - text.size.height - layout.cardW * 0.05f))
}

/** Eine Ablage; fliegt ihre oberste Karte gerade heran, liegt darunter die vorige. */
private fun DrawScope.drawPile(
    cards: List<Card>,
    pile: Int,
    layout: TableLayout,
    textMeasurer: TextMeasurer,
    flightFrom: Flight?,
    flight: Float,
) {
    val tl = layout.pileTopLeft(pile)
    drawCardSlot(tl, layout.pileSize)
    val top = cards.lastOrNull()
    if (top == null) {
        // Leere Ablage zum Rundenstart: jede offene Karte passt.
        val hint = textMeasurer.measure(
            "frei",
            TextStyle(color = Color.White.copy(alpha = 0.45f), fontSize = (layout.cardW * 0.3f).toSp(), fontWeight = FontWeight.Bold),
        )
        drawText(hint, topLeft = tl + Offset((layout.pileSize.width - hint.size.width) / 2f, (layout.pileSize.height - hint.size.height) / 2f))
        return
    }
    if (flightFrom == null || flightFrom.pile != pile) {
        drawCardFace(tl, layout.pileSize, top, textMeasurer)
        return
    }
    cards.getOrNull(cards.size - 2)?.let { drawCardFace(tl, layout.pileSize, it, textMeasurer) }
    val t = flight.coerceIn(0f, 1f)
    val size = Size(
        flightFrom.size.width + (layout.pileSize.width - flightFrom.size.width) * t,
        flightFrom.size.height + (layout.pileSize.height - flightFrom.size.height) * t,
    )
    drawCardFace(lerp(flightFrom.topLeft, tl, t), size, top, textMeasurer)
}

/** Flug einer Karte von [topLeft] in Größe [size] zur Ablage [pile]. */
private class Flight(val topLeft: Offset, val size: Size, val pile: Int)

@Composable
private fun Backdrop(backdrop: SpaceBackdrop, modifier: Modifier = Modifier) {
    val time by produceState(0f) {
        val start = withFrameNanos { it }
        while (true) withFrameNanos { value = (it - start) / 1_000_000_000f }
    }
    Canvas(modifier) { drawSpaceBackdrop(backdrop, time) }
}

/** Info-Box wie im ersten Teil: Runde, Punkte, Kette – dazu Rekord und Coins. */
@Composable
private fun InfoPanel(game: GameState, modeTitle: String?, best: Int, wallet: Int) {
    val animatedScore by animateIntAsState(game.score, tween(350), label = "score")
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color(0xFF3F3F46).copy(alpha = 0.85f))
            .border(2.dp, Color(0xFF52525B), shape)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (modeTitle != null) {
            Text(modeTitle.uppercase(), style = MaterialTheme.typography.labelSmall, color = ArcadeTextMuted, maxLines = 1)
        }
        Text("Runde ${game.round} / ${game.mode.rounds}", style = MaterialTheme.typography.labelLarge, color = ArcadeText)
        Text("$animatedScore", style = MaterialTheme.typography.titleLarge, color = ArcadeText, maxLines = 1)
        val mult = GalaxyTowersEngine.multiplier(game.round)
        Text(
            if (game.streak > 1) "Kette x${game.streak}" else if (mult > 1f) "Punkte ×${"%.2f".format(mult).trimEnd('0').trimEnd('.')}" else " ",
            style = MaterialTheme.typography.labelLarge,
            color = if (game.streak > 1) NeonPink else ArcadeTextMuted,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Star, contentDescription = null, tint = NeonYellow, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(3.dp))
            Text("$best", style = MaterialTheme.typography.labelMedium, color = ArcadeTextMuted, maxLines = 1)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("$wallet", style = MaterialTheme.typography.labelMedium, color = CoinGold, maxLines = 1)
            Spacer(Modifier.width(3.dp))
            CoinIcon(13.dp)
        }
    }
}

/** Power-ups als 2×2-Raster für die Seitenleiste; ein frisch gefundenes Power-up leuchtet kurz auf. */
@Composable
private fun PowerUpGrid(game: GameState, foundTick: PlayEvent?, onUse: (PowerUp) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        PowerUp.entries.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { p ->
                    PowerUpButton(game, p, foundTick?.takeIf { it.reward == p }?.id, onUse, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun PowerUpButton(game: GameState, p: PowerUp, foundId: Long?, onUse: (PowerUp) -> Unit, modifier: Modifier) {
    val charges = game.charges(p)
    val usable = GalaxyTowersEngine.canUse(game, p)
    val glow = remember { Animatable(0f) }
    LaunchedEffect(foundId) {
        if (foundId != null) {
            glow.snapTo(1f)
            glow.animateTo(0f, tween(1400))
        }
    }
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier
            .height(48.dp)
            .clip(shape)
            .background(ArcadeSurface.copy(alpha = 0.9f))
            .background(NeonYellow.copy(alpha = 0.35f * glow.value))
            .border(1.dp, if (usable) NeonCyan.copy(alpha = 0.7f) else ArcadeOutline, shape)
            // Leere Power-ups bleiben antippbar und erklären, wie man sie findet.
            .clickable(enabled = usable || charges == 0) { onUse(p) }
            .alpha(if (usable) 1f else 0.4f),
        contentAlignment = Alignment.Center,
    ) {
        Text(p.symbol, style = MaterialTheme.typography.titleLarge, color = NeonYellow)
        if (charges > 0) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(3.dp)
                    .clip(RoundedCornerShape(50))
                    .background(NeonPink)
                    .padding(horizontal = 5.dp),
            ) {
                Text("$charges", style = MaterialTheme.typography.labelSmall, color = Color.White)
            }
        }
    }
}

@Composable
private fun StuckPanel(onEndRound: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(ArcadeSurface.copy(alpha = 0.95f))
            .border(1.dp, NeonPink.copy(alpha = 0.6f), shape)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Keine Züge mehr", style = MaterialTheme.typography.titleSmall, color = ArcadeText)
        Text("Power-up einsetzen oder", style = MaterialTheme.typography.bodySmall, color = ArcadeTextMuted)
        TextButton(onClick = onEndRound) { Text("Runde beenden", color = NeonPink) }
    }
}

/** Die Sanduhr glüht kurz auf, wenn eine Karte Bonuszeit abgeworfen hat. */
@Composable
private fun TimeGlow(event: PlayEvent?, layout: TableLayout) {
    if (event == null || event.timeDrop <= 0) return
    val progress = remember(event.id) { Animatable(0f) }
    LaunchedEffect(event.id) { progress.animateTo(1f, tween(1200, easing = LinearOutSlowInEasing)) }
    if (progress.value >= 1f) return
    Canvas(Modifier.fillMaxSize()) {
        val p = progress.value
        val c = layout.hourglassCenter
        val r = layout.cardW * (0.6f + 0.5f * p)
        drawCircle(
            Brush.radialGradient(listOf(NeonYellow.copy(alpha = 0.55f * (1f - p)), Color.Transparent), c, r),
            r, c,
        )
    }
}

/** Gold-Münze, die bei einem Gold-Drop von der gelegten Karte aufsteigt. */
@Composable
private fun GoldBurst(event: PlayEvent?, layout: TableLayout) {
    if (event == null || event.gold <= 0) return
    val progress = remember(event.id) { Animatable(0f) }
    LaunchedEffect(event.id) { progress.animateTo(1f, tween(1100, easing = LinearOutSlowInEasing)) }
    if (progress.value >= 1f) return
    Canvas(Modifier.fillMaxSize()) {
        val p = progress.value
        val start = layout.slotTopLeft(event.index) + Offset(layout.cardW / 2f, layout.cardSize.height / 2f)
        val r = layout.cardW * 0.22f
        val alpha = if (p < 0.7f) 1f else (1f - p) / 0.3f
        val coins = minOf(event.gold, 3)
        repeat(coins) { k ->
            val spread = (k - (coins - 1) / 2f) * r * 1.6f
            val pos = start + Offset(spread * p, -layout.cardW * 1.6f * p - k * r * 0.4f)
            drawCircle(Color(0xFFFACC15).copy(alpha = 0.3f * alpha), r * 1.6f, pos)
            drawCoin(pos, r)
        }
    }
}

/**
 * Dauerhafte Kettenanzeige: Länge und Punktesumme der laufenden Kette. Sie wächst bei jeder Karte kurz an
 * und bleibt nach dem Ende der Kette noch einen Moment mit dem Endergebnis stehen.
 */
@Composable
private fun ChainBanner(streak: Int, points: Int) {
    var shownStreak by remember { mutableIntStateOf(0) }
    var shownPoints by remember { mutableIntStateOf(0) }
    val currentPoints by rememberUpdatedState(points)
    val alpha = remember { Animatable(0f) }
    val pop = remember { Animatable(1f) }
    LaunchedEffect(streak) {
        if (streak >= 2) {
            shownStreak = streak
            shownPoints = currentPoints
            alpha.snapTo(1f)
            pop.snapTo(1.25f)
            pop.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 500f))
        } else if (shownStreak >= 2) {
            delay(2500)
            alpha.animateTo(0f, tween(600))
            shownStreak = 0
        }
    }
    if (shownStreak < 2 || alpha.value <= 0f) return
    val textStyle = TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.8f), Offset(0f, 3f), 10f), textAlign = TextAlign.Center)
    Column(
        Modifier.graphicsLayer {
            this.alpha = alpha.value
            scaleX = pop.value
            scaleY = pop.value
        },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Kette x$shownStreak", style = MaterialTheme.typography.headlineSmall.merge(textStyle), color = NeonPink)
        Text("+$shownPoints", style = MaterialTheme.typography.titleLarge.merge(textStyle), color = ArcadeText)
    }
}

/** Einzelpunkte und Extras der zuletzt abgelegten Karte; bleiben gut lesbar stehen und verblassen dann. */
@Composable
private fun PlayPopup(event: PlayEvent?, modifier: Modifier = Modifier) {
    if (event == null) return
    val progress = remember(event.id) { Animatable(0f) }
    LaunchedEffect(event.id) { progress.animateTo(1f, tween(3000, easing = LinearOutSlowInEasing)) }
    if (progress.value >= 1f) return
    val textStyle = TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.8f), Offset(0f, 3f), 10f), textAlign = TextAlign.Center)
    Column(
        modifier.graphicsLayer {
            val p = progress.value
            translationY = -p * 12.dp.toPx()
            alpha = if (p < 0.85f) 1f else (1f - p) / 0.15f
            val pop = 0.75f + 0.25f * minOf(1f, p * 6f)
            scaleX = pop
            scaleY = pop
        },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (event.peakCleared) {
            Text("TURM ABGERÄUMT!", style = MaterialTheme.typography.titleLarge.merge(textStyle), color = NeonYellow)
        }
        if (event.secondPileOpened) {
            Text("2. ABLAGE FREI!", style = MaterialTheme.typography.titleMedium.merge(textStyle), color = NeonCyan)
        }
        if (event.reward != null) {
            Text("+1 ${event.reward.title}", style = MaterialTheme.typography.titleMedium.merge(textStyle), color = NeonCyan)
        }
        Text("+${event.points}", style = MaterialTheme.typography.titleLarge.merge(textStyle), color = NeonYellow)
        if (event.timeDrop > 0) {
            Text("⌛ +${event.timeDrop} s", style = MaterialTheme.typography.titleLarge.merge(textStyle), color = NeonYellow)
        }
        if (event.gold > 0) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CoinIcon(20.dp)
                Spacer(Modifier.width(6.dp))
                Text("+${event.gold}", style = MaterialTheme.typography.titleLarge.merge(textStyle), color = CoinGold)
            }
        }
    }
}

@Composable
private fun RoundOverOverlay(game: GameState, reason: RoundEnd, clear: ClearBonus, onNext: () -> Unit) {
    val round = game.round
    val cleared = reason == RoundEnd.CLEARED
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        val shape = RoundedCornerShape(28.dp)
        Column(
            Modifier
                .padding(28.dp)
                .clip(shape)
                .background(ArcadeSurface)
                .border(2.dp, (if (cleared) NeonYellow else NeonPink).copy(alpha = 0.8f), shape)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val title = when (reason) {
                RoundEnd.CLEARED -> "Runde $round geschafft!"
                RoundEnd.TIME_UP -> "Zeit abgelaufen"
                RoundEnd.STUCK -> "Keine Züge mehr"
            }
            Text(title, style = MaterialTheme.typography.headlineSmall, color = ArcadeText)
            Text("Runde $round von ${game.mode.rounds}", style = MaterialTheme.typography.bodyMedium, color = ArcadeTextMuted)
            if (cleared) {
                Text("+${clear.clear + clear.time}", style = MaterialTheme.typography.displaySmall, color = NeonYellow)
                if (clear.time > 0) {
                    Text("davon Zeitbonus +${clear.time}", style = MaterialTheme.typography.bodyMedium, color = ArcadeTextMuted)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CoinIcon(20.dp)
                    Spacer(Modifier.width(6.dp))
                    Text("+${clear.coins}", style = MaterialTheme.typography.titleLarge, color = CoinGold)
                }
            } else {
                Text("${game.score} Punkte", style = MaterialTheme.typography.titleLarge, color = ArcadeText)
            }
            if (!game.isLastRound) {
                val next = round + 1
                val time = game.mode.roundTime(next).toInt()
                Text(
                    "Nächste Runde: Punkte ×${"%.2f".format(GalaxyTowersEngine.multiplier(next)).trimEnd('0').trimEnd('.')}" +
                        if (game.mode.timed) " · $time s" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ArcadeTextMuted,
                )
            }
            Spacer(Modifier.height(6.dp))
            PrimaryButton(if (game.isLastRound) "Ergebnis" else "Runde ${round + 1}", onNext)
        }
    }
}

@Composable
private fun PauseOverlay(onResume: () -> Unit, onRestart: () -> Unit, onMenu: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        val shape = RoundedCornerShape(28.dp)
        Column(
            Modifier
                .padding(28.dp)
                .clip(shape)
                .background(ArcadeSurface)
                .border(1.dp, ArcadeOutline, shape)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Pausiert", style = MaterialTheme.typography.headlineMedium, color = ArcadeText)
            Spacer(Modifier.height(8.dp))
            PrimaryButton("Weiter", onResume)
            SecondaryButton("Neu starten", onRestart)
            SecondaryButton("Zum Menü", onMenu)
        }
    }
}

@Composable
private fun PauseGlyph() {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(2) {
            Box(
                Modifier
                    .size(width = 4.dp, height = 14.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(ArcadeText)
            )
        }
    }
}
