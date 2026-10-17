package com.adlessarcade.games.galaxytowers

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
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

/** Geometrie des Spieltischs in Pixeln: Turmkarten oben, Nachziehstapel und Ablage darunter. */
private class TableLayout(width: Float, gap: Float) {
    val cardW = width / 10.4f
    val cardSize = Size(cardW, cardW * CARD_RATIO)
    private val margin = (width - cardW * 10f) / 2f
    private val rowStep = cardSize.height * 0.5f
    private val towerTop = gap * 0.5f
    val pileSize = Size(cardW * 1.45f, cardW * 1.45f * CARD_RATIO)
    private val pileY = towerTop + rowStep * 3f + cardSize.height + gap * 2.2f
    val stockTopLeft = Offset(margin + cardW * 0.6f, pileY)
    val wasteTopLeft = Offset(width / 2f - pileSize.width / 2f, pileY)
    /** Zweite Ablage rechts neben der Hauptablage wie im ersten Teil. */
    val waste2TopLeft = wasteTopLeft + Offset(pileSize.width * 1.5f, 0f)
    val height = pileY + pileSize.height + gap * 1.5f

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

    val modeTitle = GameRegistry.find(GalaxyTowers.GAME_ID)?.modes?.firstOrNull { it.id == modeId }?.title

    ArcadeScaffold(
        title = "Galaxy Towers 2",
        subtitle = modeTitle,
        onBack = onExit,
        actions = {
            ArcadeIconButton(onClick = { if (viewModel.paused) viewModel.resume() else viewModel.pause() }) {
                PauseGlyph()
            }
        },
    ) {
        if (!viewModel.loaded) return@ArcadeScaffold
        Box(Modifier.fillMaxSize()) {
            Backdrop(cosmetics.backdrop, Modifier.fillMaxSize())
            Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
                InfoPanel(game = game, best = viewModel.best, wallet = viewModel.walletCoins)
                if (game.mode.timed) {
                    Spacer(Modifier.height(6.dp))
                    TimeBar(game)
                }
                Spacer(Modifier.height(10.dp))
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val density = LocalDensity.current
                    val widthPx = with(density) { maxWidth.toPx() }
                    val layout = remember(widthPx) { TableLayout(widthPx, with(density) { 16.dp.toPx() }) }
                    Canvas(
                        Modifier
                            .fillMaxWidth()
                            .height(with(density) { layout.height.toDp() })
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
                    PlayPopup(viewModel.lastPlay, Modifier.align(Alignment.Center))
                }
                Spacer(Modifier.weight(1f))
                if (GalaxyTowersEngine.isStuck(game)) {
                    StuckPanel(onEndRound = viewModel::endRound)
                    Spacer(Modifier.height(10.dp))
                }
                PowerUpBar(
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
                Spacer(Modifier.height(12.dp))
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

    // Nachziehstapel mit Kartenzahl
    val stockTl = layout.stockTopLeft
    if (game.stock.isNotEmpty()) {
        if (game.stock.size > 1) drawCardBack(stockTl + Offset(layout.cardW * 0.08f, -layout.cardW * 0.08f), layout.pileSize, back, alpha = 0.6f)
        drawCardBack(stockTl, layout.pileSize, back)
    } else {
        drawCardSlot(stockTl, layout.pileSize)
    }
    val countLayout = textMeasurer.measure(
        "${game.stock.size}",
        TextStyle(color = Color.White, fontSize = (layout.cardW * 0.42f).toSp(), fontWeight = FontWeight.Black, shadow = Shadow(Color.Black, Offset(0f, 2f), 6f)),
    )
    drawText(countLayout, topLeft = stockTl + Offset((layout.pileSize.width - countLayout.size.width) / 2f, layout.pileSize.height + layout.cardW * 0.1f))

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
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(shape)
            .background(Color(0xFF3F3F46).copy(alpha = 0.85f))
            .border(2.dp, Color(0xFF52525B), shape)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Runde ${game.round} / ${game.mode.rounds}", style = MaterialTheme.typography.titleMedium, color = ArcadeText)
            val mult = GalaxyTowersEngine.multiplier(game.round)
            Text(
                if (mult > 1f) "Punkte ×${"%.2f".format(mult).trimEnd('0').trimEnd('.')}" else "Kette: ${game.streak}",
                style = MaterialTheme.typography.labelMedium,
                color = ArcadeTextMuted,
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$animatedScore", style = MaterialTheme.typography.headlineMedium, color = ArcadeText)
            Text(
                if (game.streak > 1) "Kette x${game.streak}" else " ",
                style = MaterialTheme.typography.labelLarge,
                color = NeonPink,
            )
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("$wallet", style = MaterialTheme.typography.titleMedium, color = CoinGold)
                Spacer(Modifier.width(4.dp))
                CoinIcon(16.dp)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Star, contentDescription = null, tint = NeonYellow, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("$best", style = MaterialTheme.typography.labelLarge, color = ArcadeTextMuted)
            }
        }
    }
}

/** Sanduhr mit Restzeit der Runde; die letzten 10 Sekunden pulsiert sie rot. */
@Composable
private fun TimeBar(game: GameState) {
    val total = game.mode.roundTime(game.round).coerceAtLeast(1f)
    val fraction = (game.timeLeft / total).coerceIn(0f, 1f)
    val urgent = game.timeLeft <= 10f && game.inPlay
    val pulse = if (urgent) 0.6f + 0.4f * kotlin.math.sin(game.timeLeft * 12f) else 1f
    val color = if (urgent) NeonPink else NeonYellow
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("⌛", style = MaterialTheme.typography.titleMedium, color = color)
        Spacer(Modifier.width(6.dp))
        Box(
            Modifier
                .weight(1f)
                .height(8.dp)
                .clip(RoundedCornerShape(50))
                .background(Color(0xFF3F3F46)),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .height(8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(color.copy(alpha = pulse)),
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            "${kotlin.math.ceil(game.timeLeft).toInt()}",
            style = MaterialTheme.typography.titleMedium,
            color = color,
            modifier = Modifier.width(32.dp),
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun PowerUpBar(game: GameState, canBuy: Boolean, onUse: (PowerUp) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PowerUp.entries.forEach { p ->
            val charges = game.charges(p)
            val usable = GalaxyTowersEngine.canUse(game, p)
            val buyable = charges == 0 && canBuy && game.inPlay
            val enabled = usable || buyable
            val shape = RoundedCornerShape(14.dp)
            Box(
                Modifier
                    .weight(1f)
                    .height(64.dp)
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
    }
}

@Composable
private fun StuckPanel(onEndRound: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(ArcadeSurface.copy(alpha = 0.95f))
            .border(1.dp, NeonPink.copy(alpha = 0.6f), shape)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Keine Züge mehr", style = MaterialTheme.typography.titleMedium, color = ArcadeText)
            Text("Setze ein Power-up ein oder beende die Runde.", style = MaterialTheme.typography.bodySmall, color = ArcadeTextMuted)
        }
        TextButton(onClick = onEndRound) { Text("Runde beenden", color = NeonPink) }
    }
}

/** Punkte, Kette und Bonus der zuletzt abgelegten Karte, steigen kurz auf und verblassen. */
@Composable
private fun PlayPopup(event: PlayEvent?, modifier: Modifier = Modifier) {
    if (event == null) return
    val progress = remember(event.id) { Animatable(0f) }
    LaunchedEffect(event.id) { progress.animateTo(1f, tween(950, easing = LinearOutSlowInEasing)) }
    if (progress.value >= 1f) return
    val textStyle = TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.8f), Offset(0f, 3f), 10f), textAlign = TextAlign.Center)
    Column(
        modifier.graphicsLayer {
            val p = progress.value
            translationY = -p * 50.dp.toPx()
            alpha = if (p < 0.65f) 1f else (1f - p) / 0.35f
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
