package com.adlessarcade.games.kitchen

import android.content.pm.ActivityInfo
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.adlessarcade.ui.components.ArcadeIconButton
import com.adlessarcade.ui.components.ArcadeScaffold
import com.adlessarcade.ui.components.GameOverOverlay
import com.adlessarcade.ui.components.LockOrientation
import com.adlessarcade.ui.components.PrimaryButton
import com.adlessarcade.ui.components.SecondaryButton
import kotlinx.coroutines.isActive

private val Ink = Color(0xFF3B2A20)
private val Paper = Color(0xFFFFFBEB)

/** Für Oberflächen-Tests. */
const val KITCHEN_CANVAS_TAG = "kitchen_canvas"

@Composable
fun KitchenScreen(onExit: () -> Unit, viewModel: KitchenViewModel = viewModel()) {
    LockOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE)
    val haptics = LocalHapticFeedback.current

    LaunchedEffect(viewModel) {
        var last = 0L
        while (isActive) {
            withFrameNanos { now ->
                if (last != 0L) viewModel.step(((now - last) / 1_000_000_000f).coerceAtMost(0.05f))
                last = now
            }
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { viewModel.pause() }
    LaunchedEffect(viewModel.hurtTick) { if (viewModel.hurtTick > 0) haptics.performHapticFeedback(HapticFeedbackType.LongPress) }
    LaunchedEffect(viewModel.rewardTick) { if (viewModel.rewardTick > 0) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) }

    ArcadeScaffold {
        if (!viewModel.loaded) return@ArcadeScaffold
        when (viewModel.phase) {
            KitchenPhase.SELECT -> LevelSelect(viewModel.levels(), onStart = viewModel::startLevel, onBack = onExit)
            else -> GameView(viewModel, onExit)
        }
    }
}

@Composable
private fun GameView(viewModel: KitchenViewModel, onExit: () -> Unit) {
    val tm = rememberTextMeasurer(cacheSize = 128)
    var drag by remember { mutableStateOf<KitchenDrag?>(null) }

    Box(Modifier.fillMaxSize()) {
        Canvas(
            Modifier
                .fillMaxSize()
                .testTag(KITCHEN_CANVAS_TAG)
                .onSizeChanged { viewModel.setLayout(KitchenLayout(it.width.toFloat(), it.height.toFloat())) }
                .pointerInput(viewModel) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val layout = viewModel.layout ?: return@awaitEachGesture
                        val engine = viewModel.engine ?: return@awaitEachGesture
                        if (engine.busy) {
                            // Minispiel: Gesten in Flächen-Koordinaten weiterreichen
                            viewModel.gameDown(layout.toPanel(down.position))
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) {
                                    viewModel.gameUp(layout.toPanel(change.position))
                                    break
                                }
                                viewModel.gameMove(layout.toPanel(change.position))
                                change.consume()
                            }
                            return@awaitEachGesture
                        }
                        val station = layout.stationAt(down.position)
                        val ticket = layout.ticketAt(down.position)
                        var pos = down.position
                        var dragging = false
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            pos = change.position
                            if (!dragging && station != null && (pos - down.position).getDistance() > viewConfiguration.touchSlop) {
                                drag = viewModel.dragFor(station, pos)
                                dragging = drag != null
                                if (dragging) viewModel.closeRecipe()
                            }
                            if (dragging) {
                                drag = drag?.copy(pos = pos)
                                change.consume()
                            }
                        }
                        if (dragging && station != null) {
                            layout.stationAt(pos)?.let { target -> viewModel.drop(station, target) }
                            drag = null
                        } else when {
                            ticket != null && engine.orders.size > ticket -> viewModel.showRecipe(ticket)
                            station != null -> {
                                viewModel.closeRecipe()
                                viewModel.tap(station)
                            }
                            else -> viewModel.closeRecipe()
                        }
                    }
                }
        ) {
            viewModel.frame // Neuzeichnen pro Frame
            val layout = viewModel.layout ?: KitchenLayout(size.width, size.height)
            val engine = viewModel.engine ?: return@Canvas
            drawKitchen(layout, engine, drag, viewModel.effects, viewModel.time, tm, viewModel.flies)
            drawMiniGameOverlay(layout, engine, viewModel.time, tm)
        }

        Hud(viewModel.hud, onPause = viewModel::pause, modifier = Modifier.align(Alignment.TopEnd))
        viewModel.recipe?.let { RecipeCardView(it, onClose = viewModel::closeRecipe, modifier = Modifier.align(Alignment.Center)) }
        Banner(viewModel.banner, Modifier.align(Alignment.Center))
        viewModel.hint?.let { HintToast(it, Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp)) }

        if (viewModel.paused) {
            PauseOverlay(
                onResume = viewModel::resume,
                onRestart = viewModel::retry,
                onMenu = if (viewModel.isCampaign) viewModel::toLevelSelect else onExit,
                menuLabel = if (viewModel.isCampaign) "Levelauswahl" else "Zum Menü",
            )
        }

        val result = viewModel.result
        if (viewModel.phase == KitchenPhase.RESULT && result != null) {
            if (result.levelId != null) {
                LevelResult(
                    result,
                    level = KitchenCatalog.level(result.levelId),
                    hasNext = viewModel.hasNextLevel(),
                    onRetry = viewModel::retry,
                    onNext = viewModel::nextLevel,
                    onSelect = viewModel::toLevelSelect,
                )
            } else {
                GameOverOverlay(
                    visible = true,
                    score = result.score,
                    best = viewModel.best,
                    isNewBest = result.isNewBest,
                    onPlayAgain = viewModel::retry,
                    onMenu = onExit,
                    title = "Küche geschlossen",
                    message = "${result.served} Gerichte serviert, ${result.missed} Bestellungen verpasst",
                    coinsEarned = result.coins,
                    bonusCoins = result.recordBonus,
                    leaderboardRank = viewModel.leaderboardRank,
                )
            }
        }
    }
}

@Composable
private fun Hud(hud: KitchenHud, onPause: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .padding(6.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Paper.copy(alpha = 0.95f))
            .border(2.dp, Ink, RoundedCornerShape(14.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(horizontalAlignment = Alignment.End) {
            Text("${hud.score}", style = MaterialTheme.typography.titleLarge, color = Ink)
            val sub = when {
                hud.endless -> "✗ ".repeat(hud.strikes).ifEmpty { "Keine Fehler" }
                hud.secondsLeft != null -> "%d:%02d".format(hud.secondsLeft / 60, hud.secondsLeft % 60)
                else -> ""
            }
            Text(sub, style = MaterialTheme.typography.labelMedium, color = if (hud.endless && hud.strikes > 0) Bad else Ink)
            if (hud.combo > 1) Text("Kombo ×${hud.combo}", style = MaterialTheme.typography.labelSmall, color = Good)
            if (hud.targets.isNotEmpty()) {
                val stars = hud.targets.count { hud.score >= it }
                Text("★".repeat(stars) + "☆".repeat(3 - stars), style = MaterialTheme.typography.labelMedium, color = Warn)
            }
        }
        Spacer(Modifier.width(8.dp))
        ArcadeIconButton(onClick = onPause) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(2) { Box(Modifier.size(width = 4.dp, height = 14.dp).clip(RoundedCornerShape(2.dp)).background(Color.White)) }
            }
        }
    }
}

@Composable
private fun RecipeCardView(card: RecipeCard, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier
            .padding(24.dp)
            .clip(shape)
            .background(Paper)
            .border(2.dp, Ink, shape)
            .clickable(onClick = onClose)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("Rezept: ${card.dish.name}", style = MaterialTheme.typography.titleMedium, color = Ink)
        card.lines.forEach { (kind, steps) ->
            Text("• ${kind.title}: $steps", style = MaterialTheme.typography.bodyMedium, color = Ink)
        }
        Text(
            "Alles auf einen Teller legen, ${card.dish.plating.joinToString { it.title.lowercase() }} – dann zur Ausgabe ziehen.",
            style = MaterialTheme.typography.bodySmall,
            color = Ink.copy(alpha = 0.75f),
        )
        Text("Antippen zum Schließen", style = MaterialTheme.typography.labelSmall, color = Ink.copy(alpha = 0.5f))
    }
}

@Composable
private fun Banner(banner: KitchenBanner?, modifier: Modifier = Modifier) {
    AnimatedVisibility(visible = banner != null, enter = fadeIn() + scaleIn(initialScale = 0.7f), exit = fadeOut(), modifier = modifier) {
        var shown by remember { mutableStateOf(KitchenBanner("", null, Color.White)) }
        if (banner != null) shown = banner
        val shadow = TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.5f), Offset(0f, 3f), 10f))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(shown.title, style = MaterialTheme.typography.headlineMedium.merge(shadow), color = shown.color)
            shown.subtitle?.let { Text(it, style = MaterialTheme.typography.titleMedium.merge(shadow), color = Color.White) }
        }
    }
}

@Composable
private fun HintToast(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier
            .clip(RoundedCornerShape(50))
            .background(Ink.copy(alpha = 0.85f))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        style = MaterialTheme.typography.labelLarge,
        color = Color.White,
    )
}

@Composable
private fun LevelSelect(levels: List<Triple<LevelDef, Int, Boolean>>, onStart: (String) -> Unit, onBack: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFFFFF4E0))
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ArcadeIconButton(onClick = onBack) { Text("←", color = Color.White) }
            Spacer(Modifier.width(12.dp))
            Text("Kitchen Rush – Kampagne", style = MaterialTheme.typography.headlineSmall, color = Ink)
            Spacer(Modifier.weight(1f))
            val total = levels.sumOf { it.second }
            Text("★ $total / ${levels.size * 3}", style = MaterialTheme.typography.titleMedium, color = Warn)
        }
        Restaurant.entries.forEach { restaurant ->
            Text(restaurant.title + if (!restaurant.available) " – bald verfügbar" else "", style = MaterialTheme.typography.titleMedium, color = Ink)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val own = levels.filter { it.first.restaurant == restaurant }
                if (own.isEmpty()) {
                    repeat(5) { LevelCard(number = it + 1, stars = 0, unlocked = false, dishes = emptyList(), onClick = {}) }
                }
                own.forEach { (level, stars, unlocked) ->
                    LevelCard(level.number, stars, unlocked, level.dishes.map { KitchenCatalog.dish(it).name }, onClick = { onStart(level.id) })
                }
            }
        }
    }
}

@Composable
private fun LevelCard(number: Int, stars: Int, unlocked: Boolean, dishes: List<String>, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier
            .width(130.dp)
            .height(110.dp)
            .alpha(if (unlocked) 1f else 0.45f)
            .clip(shape)
            .background(Paper)
            .border(2.dp, Ink, shape)
            .clickable(enabled = unlocked, onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(if (unlocked) "Level $number" else "🔒 Level $number", style = MaterialTheme.typography.titleMedium, color = Ink)
        Text("★".repeat(stars) + "☆".repeat(3 - stars), style = MaterialTheme.typography.titleMedium, color = Warn)
        Text(dishes.joinToString(", "), style = MaterialTheme.typography.labelSmall, color = Ink.copy(alpha = 0.7f), textAlign = TextAlign.Center, maxLines = 3)
    }
}

@Composable
private fun LevelResult(
    result: KitchenResult,
    level: LevelDef,
    hasNext: Boolean,
    onRetry: () -> Unit,
    onNext: () -> Unit,
    onSelect: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        val shape = RoundedCornerShape(24.dp)
        Column(
            Modifier
                .width(380.dp)
                .clip(shape)
                .background(Paper)
                .border(2.dp, Ink, shape)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("Schicht beendet", style = MaterialTheme.typography.headlineSmall, color = Ink)
            Text(level.title, style = MaterialTheme.typography.titleMedium, color = Ink.copy(alpha = 0.7f))
            Text("★".repeat(result.stars) + "☆".repeat(3 - result.stars), style = MaterialTheme.typography.displaySmall, color = Warn)
            Text("${result.score} Punkte", style = MaterialTheme.typography.titleLarge, color = Ink)
            Text("Ziele: ${level.starTargets.joinToString(" / ")}", style = MaterialTheme.typography.bodySmall, color = Ink.copy(alpha = 0.7f))
            Text("${result.served} serviert · ${result.missed} verpasst · +${result.coins} Coins", style = MaterialTheme.typography.bodySmall, color = Ink)
            if (result.isNewBest) Text("Neue Bestleistung!", style = MaterialTheme.typography.labelLarge, color = Good)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton("Nochmal", onRetry, Modifier.weight(1f))
                if (hasNext && result.stars > 0) PrimaryButton("Weiter", onNext, Modifier.weight(1f))
            }
            SecondaryButton("Levelauswahl", onSelect)
        }
    }
}

@Composable
private fun PauseOverlay(onResume: () -> Unit, onRestart: () -> Unit, onMenu: () -> Unit, menuLabel: String) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.65f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        val shape = RoundedCornerShape(24.dp)
        Column(
            Modifier
                .width(340.dp)
                .clip(shape)
                .background(Paper)
                .border(2.dp, Ink, shape)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Pause", style = MaterialTheme.typography.headlineSmall, color = Ink)
            Text(
                "Zutaten aus dem Vorrat auf die Stationen ziehen. Schüssel antippen zum Rühren, Pfanne antippen zum Wenden. " +
                    "Fertigen Teller zur Ausgabe ziehen.",
                style = MaterialTheme.typography.bodySmall,
                color = Ink.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
            )
            PrimaryButton("Weiter", onResume)
            SecondaryButton("Neu starten", onRestart)
            SecondaryButton(menuLabel, onMenu)
        }
    }
}
