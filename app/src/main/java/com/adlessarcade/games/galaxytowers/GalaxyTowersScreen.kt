package com.adlessarcade.games.galaxytowers

import android.content.pm.ActivityInfo
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
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
import kotlin.math.ceil
import kotlin.math.sin

/**
 * Geometrie des Spieltischs im Querformat wie im ersten Teil: Türme oben, darunter Nachziehstapel, Ablage,
 * Sanduhr und zweite Ablage. Die Kartengröße richtet sich nach Breite und Höhe des Bereichs.
 */
private class TableLayout(width: Float, height: Float, gap: Float) {
    val cardW = minOf(width / 10.4f, (height - gap * 2.1f) / 5.62f)
    val cardSize = Size(cardW, cardW * CARD_RATIO)
    private val margin = (width - cardW * 10f) / 2f
    private val rowStep = cardSize.height * 0.5f
    val pileSize = Size(cardW * 1.45f, cardW * 1.45f * CARD_RATIO)
    private val used = rowStep * 3f + cardSize.height + gap * 1.2f + pileSize.height
    private val towerTop = ((height - used) / 2f).coerceAtLeast(gap * 0.4f)
    val pileY = towerTop + rowStep * 3f + cardSize.height + gap * 1.2f
    private val center = width / 2f
    val stockTopLeft = Offset(margin + cardW * 0.2f, pileY)
    val wasteTopLeft = Offset(center - pileSize.width * 1.35f, pileY)
    /** Zweite Ablage rechts neben der Sanduhr wie im ersten Teil. */
    val waste2TopLeft = Offset(center + pileSize.width * 0.35f, pileY)
    /** Mitte der Sanduhr zwischen den beiden Ablagen. */
    val hourglassCenter = Offset(center, pileY + pileSize.height / 2f)

    fun pileTopLeft(pile: Int) = if (pile == 0) wasteTopLeft else waste2TopLeft

    fun slotTopLeft(index: Int): Offset {
        val (x, row) = GalaxyTowersEngine.slotPosition(index)
        return Offset(margin + x * cardW, towerTop + row * rowStep)
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
    var buyDialog by remember { mutableStateOf<PowerUp?>(null) }
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
                        drawTable(game, layout, textMeasurer, cosmetics.cardBack, flightFrom, flight)
                    }
                    GoldBurst(viewModel.lastPlay, layout)
                    // Einblendung über der Ablage-Reihe, damit sie die Türme möglichst wenig verdeckt
                    PlayPopup(
                        viewModel.lastPlay,
                        Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = with(density) { (layout.pileY.toDp() - 96.dp).coerceAtLeast(0.dp) }),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Column(
                    Modifier
                        .width(212.dp)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ArcadeIconButton(onClick = onExit) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                        }
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Galaxy Towers 2", style = MaterialTheme.typography.titleSmall, color = ArcadeText, maxLines = 1)
                            if (modeTitle != null) {
                                Text(modeTitle.uppercase(), style = MaterialTheme.typography.labelSmall, color = ArcadeTextMuted, maxLines = 1)
                            }
                        }
                        ArcadeIconButton(onClick = { if (viewModel.paused) viewModel.resume() else viewModel.pause() }) {
                            PauseGlyph()
                        }
                    }
                    InfoPanel(game = game, best = viewModel.best, wallet = viewModel.walletCoins)
                    if (GalaxyTowersEngine.isStuck(game)) StuckPanel(onEndRound = viewModel::endRound)
                    PowerUpGrid(
                        game = game,
                        canBuy = viewModel.canBuy,
                        onUse = { p ->
                            if (game.charges(p) > 0) {
                                if (viewModel.usePowerUp(p)) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            } else if (viewModel.canBuy) {
                                buyDialog = p
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

    buyDialog?.let { p ->
        val affordable = viewModel.walletCoins >= p.price
        AlertDialog(
            onDismissRequest = { buyDialog = null },
            containerColor = ArcadeSurface,
            title = { Text("${p.title} kaufen?") },
            text = {
                Column {
                    Text(p.description, color = ArcadeTextMuted)
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CoinIcon(16.dp)
                        Spacer(Modifier.width(6.dp))
                        Text("${p.price}  (du hast ${viewModel.walletCoins})", color = CoinGold)
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = affordable, onClick = {
                    buyDialog = null
                    viewModel.buyPowerUp(p)
                }) { Text(if (affordable) "Kaufen" else "Zu wenig Coins", color = if (affordable) NeonCyan else ArcadeTextMuted) }
            },
            dismissButton = {
                TextButton(onClick = { buyDialog = null }) { Text("Abbrechen", color = ArcadeTextMuted) }
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
) {
    // Türme: von hinten (Spitzen) nach vorne (untere Reihe)
    for (i in 0 until GalaxyTowersEngine.TOWER_SIZE) {
        val card = game.tower[i] ?: continue
        val topLeft = layout.slotTopLeft(i)
        val open = GalaxyTowersEngine.isFree(game.tower, i)
        when {
            open -> drawCardFace(topLeft, layout.cardSize, card, textMeasurer, highlight = GalaxyTowersEngine.canPlay(game, i))
            game.scanActive -> {
                drawCardFace(topLeft, layout.cardSize, card, textMeasurer)
                drawRoundRect(NeonCyan.copy(alpha = 0.28f), topLeft, layout.cardSize, CornerRadius(layout.cardW * 0.1f))
            }
            else -> drawCardBack(topLeft, layout.cardSize, back)
        }
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
    val top = cards.lastOrNull() ?: return
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
private fun InfoPanel(game: GameState, best: Int, wallet: Int) {
    val animatedScore by animateIntAsState(game.score, tween(350), label = "score")
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color(0xFF3F3F46).copy(alpha = 0.85f))
            .border(2.dp, Color(0xFF52525B), shape)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Runde ${game.round} / ${game.mode.rounds}", style = MaterialTheme.typography.titleSmall, color = ArcadeText)
        Text("$animatedScore", style = MaterialTheme.typography.headlineSmall, color = ArcadeText)
        val mult = GalaxyTowersEngine.multiplier(game.round)
        Text(
            if (game.streak > 1) "Kette x${game.streak}" else if (mult > 1f) "Punkte ×${"%.2f".format(mult).trimEnd('0').trimEnd('.')}" else " ",
            style = MaterialTheme.typography.labelLarge,
            color = if (game.streak > 1) NeonPink else ArcadeTextMuted,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Star, contentDescription = null, tint = NeonYellow, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text("$best", style = MaterialTheme.typography.labelLarge, color = ArcadeTextMuted)
            Spacer(Modifier.width(14.dp))
            Text("$wallet", style = MaterialTheme.typography.labelLarge, color = CoinGold)
            Spacer(Modifier.width(4.dp))
            CoinIcon(14.dp)
        }
    }
}

/** Power-ups als 2×2-Raster für die Seitenleiste. */
@Composable
private fun PowerUpGrid(game: GameState, canBuy: Boolean, onUse: (PowerUp) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PowerUp.entries.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { p -> PowerUpButton(game, p, canBuy, onUse, Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun PowerUpButton(game: GameState, p: PowerUp, canBuy: Boolean, onUse: (PowerUp) -> Unit, modifier: Modifier) {
    val charges = game.charges(p)
    val usable = GalaxyTowersEngine.canUse(game, p)
    val buyable = charges == 0 && canBuy && game.inPlay
    val enabled = usable || buyable
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier
            .height(56.dp)
            .clip(shape)
            .background(ArcadeSurface.copy(alpha = 0.9f))
            .border(1.dp, if (usable) NeonCyan.copy(alpha = 0.7f) else ArcadeOutline, shape)
            .clickable(enabled = enabled) { onUse(p) }
            .alpha(if (enabled) 1f else 0.4f),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(p.symbol, style = MaterialTheme.typography.titleLarge, color = NeonYellow)
            Text(p.title, style = MaterialTheme.typography.labelSmall, color = ArcadeText, maxLines = 1)
        }
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .clip(RoundedCornerShape(50))
                .background(if (charges > 0) NeonPink else Color.Transparent)
                .padding(horizontal = 6.dp, vertical = 1.dp),
        ) {
            if (charges > 0) {
                Text("$charges", style = MaterialTheme.typography.labelSmall, color = Color.White)
            } else if (canBuy) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CoinIcon(10.dp)
                    Spacer(Modifier.width(2.dp))
                    Text("${p.price}", style = MaterialTheme.typography.labelSmall, color = CoinGold)
                }
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

/** Punkte, Kette und Bonus der zuletzt abgelegten Karte; bleiben gut lesbar stehen und verblassen dann. */
@Composable
private fun PlayPopup(event: PlayEvent?, modifier: Modifier = Modifier) {
    if (event == null) return
    val progress = remember(event.id) { Animatable(0f) }
    LaunchedEffect(event.id) { progress.animateTo(1f, tween(1800, easing = LinearOutSlowInEasing)) }
    if (progress.value >= 1f) return
    val textStyle = TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.8f), Offset(0f, 3f), 10f), textAlign = TextAlign.Center)
    Column(
        modifier.graphicsLayer {
            val p = progress.value
            translationY = -p * 30.dp.toPx()
            alpha = if (p < 0.8f) 1f else (1f - p) / 0.2f
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
        if (event.streak > 1) {
            Text("Kette x${event.streak}", style = MaterialTheme.typography.titleMedium.merge(textStyle), color = NeonPink)
        }
        Text("+${event.points}", style = MaterialTheme.typography.headlineMedium.merge(textStyle), color = ArcadeText)
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
