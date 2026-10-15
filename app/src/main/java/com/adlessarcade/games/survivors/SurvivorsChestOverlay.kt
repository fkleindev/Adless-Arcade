package com.adlessarcade.games.survivors

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adlessarcade.ui.components.PrimaryButton
import com.adlessarcade.ui.theme.ArcadeSurface
import com.adlessarcade.ui.theme.ArcadeSurfaceHigh
import com.adlessarcade.ui.theme.ArcadeText
import com.adlessarcade.ui.theme.ArcadeTextMuted
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

private val GOLD = Color(0xFFFACC15)
private val CARD_WIDTH = 92.dp
private val CARD_HEIGHT = 108.dp
private val CARD_GAP = 10.dp
private val REEL_HEIGHT = 150.dp

/** Seltenheitsfarbe einer Belohnung. */
fun rewardColor(state: SurvivorsState, offer: UpgradeOffer): Color = when {
    offer.evolution != null -> GOLD
    offer.item != null -> Color(0xFFFB923C)
    offer.weapon != null && offer.weapon !in state.weapons -> Color(0xFF22D3EE)
    offer.weapon != null -> Color(0xFFF1F5F9)
    offer.passive != null -> Color(0xFFA78BFA)
    else -> Color(0xFF4ADE80)
}

/**
 * Kisten-Overlay: Die Kiste springt auf, drei Walzen rollen wie an einem Spielautomaten und stoppen nacheinander
 * (eine Entwicklung immer zuletzt). Danach wählt der Spieler eine der drei Belohnungen.
 */
@Composable
fun ChestOverlay(state: SurvivorsState, roll: ChestRoll, onChoose: (Int) -> Unit) {
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val offers = roll.offers
    val evolutionSlot = offers.indexOfFirst { it.evolution != null }

    // Reihenfolge des Anhaltens: Entwicklung zuletzt, sonst von links nach rechts.
    val stopOrder = remember(roll.rollId) {
        offers.indices.sortedBy { if (it == evolutionSlot) Int.MAX_VALUE else it }
    }
    // Walzen: zufällige Füllkarten, die Gewinnkarte jeweils an fester Position
    val strips = remember(roll.rollId) {
        offers.mapIndexed { slot, offer ->
            val rnd = Random(roll.rollId * 7919 + slot * 31)
            val length = 18 + 7 * stopOrder.indexOf(slot) + if (slot == evolutionSlot) 8 else 0
            List(length) { roll.reel[rnd.nextInt(roll.reel.size)] } + offer + List(2) { roll.reel[rnd.nextInt(roll.reel.size)] }
        }
    }
    val targets = strips.map { it.size - 3f }

    val chestOpen = remember(roll.rollId) { Animatable(if (!roll.animate) 1f else 0f) }
    val reels = remember(roll.rollId) { targets.map { Animatable(if (roll.animate) 0f else it) } }
    val landings = remember(roll.rollId) { offers.map { Animatable(if (roll.animate) 0f else 1f) } }
    var landed by remember(roll.rollId) { mutableStateOf(List(offers.size) { !roll.animate }) }
    var selected by remember(roll.rollId) { mutableStateOf<Int?>(null) }
    val allLanded = landed.all { it }

    fun land(slot: Int) {
        if (landed[slot]) return
        landed = landed.toMutableList().also { it[slot] = true }
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        scope.launch {
            landings[slot].animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
        }
    }

    LaunchedEffect(roll.rollId) {
        if (!roll.animate) return@LaunchedEffect
        if (chestOpen.value < 1f) chestOpen.animateTo(1f, tween(900, easing = LinearEasing))
        stopOrder.forEachIndexed { order, slot ->
            launch {
                val duration = 1500 + 450 * order + if (slot == evolutionSlot) 900 else 0
                reels[slot].animateTo(targets[slot], tween(duration, easing = CubicBezierEasing(0.12f, 0.75f, 0.2f, 1f)))
                land(slot)
            }
        }
    }
    // Haptischer Tick für jede vorbeiziehende Karte (der zuletzt stoppenden Walze)
    LaunchedEffect(roll.rollId) {
        val ticking = reels[stopOrder.last()]
        snapshotFlow { floor(ticking.value + 0.5f).toInt() }.collect {
            if (ticking.isRunning) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    val skip = {
        if (!allLanded) {
            scope.launch {
                chestOpen.snapTo(1f)
                offers.indices.forEach { slot ->
                    reels[slot].snapTo(targets[slot])
                    land(slot)
                }
            }
        }
    }

    val evolutionShown = evolutionSlot >= 0 && landed.getOrElse(evolutionSlot) { false }
    val glow = selected?.let { rewardColor(state, offers[it]) } ?: if (evolutionShown) GOLD else Color(0xFFFB923C)

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.8f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { skip() },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                if (evolutionShown) "ENTWICKLUNG!" else "TRUHE!",
                style = MaterialTheme.typography.headlineMedium,
                color = if (evolutionShown) GOLD else ArcadeText,
            )
            Text(
                if (roll.remaining > 0) "Wähle eine Belohnung · noch ${roll.remaining} weitere Truhe${if (roll.remaining > 1) "n" else ""}"
                else "Wähle eine Belohnung",
                style = MaterialTheme.typography.bodyMedium,
                color = ArcadeTextMuted,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))

            ChestGraphic(chestOpen.value, glow, evolutionShown, Modifier.size(120.dp))

            Spacer(Modifier.height(6.dp))
            Box(
                Modifier.fillMaxWidth().height(REEL_HEIGHT),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(CARD_GAP)) {
                    offers.indices.forEach { slot ->
                        SlotReel(
                            strip = strips[slot],
                            position = reels[slot].value,
                            state = state,
                            landed = landed[slot],
                            landing = landings[slot].value,
                            selected = selected == slot,
                            dimmed = selected != null && selected != slot,
                            onClick = { if (allLanded) selected = slot else skip() },
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            // Details & Auswahl (fester Platz, damit nichts springt)
            Box(Modifier.fillMaxWidth().height(190.dp), contentAlignment = Alignment.TopCenter) {
                val choice = selected
                when {
                    !allLanded -> Text("Tippen zum Überspringen", style = MaterialTheme.typography.labelLarge, color = ArcadeTextMuted)
                    choice == null -> Text("Tippe auf eine Karte", style = MaterialTheme.typography.titleMedium, color = ArcadeText)
                    else -> RewardDetails(state, offers[choice]) { onChoose(choice) }
                }
            }
        }
    }
}

@Composable
private fun RewardDetails(state: SurvivorsState, offer: UpgradeOffer, onTake: () -> Unit) {
    val color = rewardColor(state, offer)
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "${Upgrades.title(offer)} · ${Upgrades.levelLabel(state, offer)}",
            style = MaterialTheme.typography.titleLarge,
            color = color,
            textAlign = TextAlign.Center,
        )
        Text(
            Upgrades.description(state, offer),
            style = MaterialTheme.typography.bodyMedium,
            color = ArcadeText,
            textAlign = TextAlign.Center,
        )
        Upgrades.synergy(state, offer)?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = ArcadeTextMuted, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(12.dp))
        PrimaryButton("Nehmen", onTake)
    }
}

/** Senkrechte Walze; [position] = Kartenindex in der Mitte (als Kommazahl während des Rollens). */
@Composable
private fun SlotReel(
    strip: List<UpgradeOffer>,
    position: Float,
    state: SurvivorsState,
    landed: Boolean,
    landing: Float,
    selected: Boolean,
    dimmed: Boolean,
    onClick: () -> Unit,
) {
    val density = LocalDensity.current
    val pitch = with(density) { (CARD_HEIGHT + CARD_GAP).toPx() }
    val cardPx = with(density) { CARD_HEIGHT.toPx() }
    val winner = strip.size - 3
    val winnerColor = rewardColor(state, strip[winner])
    val sparkle = rememberInfiniteTransition(label = "sparkle")
    val spin by sparkle.animateFloat(0f, 360f, infiniteRepeatable(tween(6000, easing = LinearEasing)), label = "spin")
    val pulse by sparkle.animateFloat(0.6f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "pulse")
    val shape = RoundedCornerShape(18.dp)

    BoxWithConstraints(
        Modifier
            .size(CARD_WIDTH + 8.dp, REEL_HEIGHT)
            .clip(shape)
            .background(Color.Black.copy(alpha = 0.4f))
            .border(2.dp, if (selected) winnerColor else Color.White.copy(alpha = 0.08f), shape)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .alpha(if (dimmed) 0.45f else 1f),
    ) {
        val height = with(density) { maxHeight.toPx() }
        val centerY = height / 2f

        // Lichtstrahlen und Partikel hinter der Gewinnkarte
        Canvas(Modifier.fillMaxSize()) {
            if (landed) {
                val center = Offset(size.width / 2f, centerY)
                val strength = if (selected) 0.45f else 0.25f
                drawRays(center, size.height, winnerColor, spin, strength * landing.coerceIn(0f, 1f))
                drawBurst(center, size.width, winnerColor, landing)
            }
        }

        val first = floor(position - centerY / pitch - 1).toInt().coerceAtLeast(0)
        val last = (position + centerY / pitch + 1).toInt().coerceAtMost(strip.lastIndex)
        for (i in first..last) {
            val y = centerY + (i - position) * pitch - cardPx / 2f
            val isWinner = i == winner && landed
            val distance = kotlin.math.abs(i - position)
            val baseScale = (1f - 0.15f * distance).coerceIn(0.7f, 1f)
            val scale = if (isWinner) baseScale * (1f + (if (selected) 0.1f else 0.04f) * landing) else baseScale
            RewardCard(
                strip[i],
                state,
                highlight = isWinner,
                glow = if (isWinner) pulse else 0f,
                modifier = Modifier
                    .offset { androidx.compose.ui.unit.IntOffset(0, y.roundToInt()) }
                    .align(Alignment.TopCenter)
                    .scale(scale)
                    .alpha(if (landed && !isWinner) 0.25f else 1f),
            )
        }

        // Unschärfe an den Rändern der Walze
        Canvas(Modifier.fillMaxSize()) {
            val fade = size.height * 0.18f
            val band = Size(size.width, fade)
            drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent), 0f, fade), size = band)
            drawRect(
                Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)), size.height - fade, size.height),
                topLeft = Offset(0f, size.height - fade),
                size = band,
            )
        }
    }
}

@Composable
private fun RewardCard(offer: UpgradeOffer, state: SurvivorsState, highlight: Boolean, glow: Float, modifier: Modifier = Modifier) {
    val color = rewardColor(state, offer)
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier
            .size(CARD_WIDTH, CARD_HEIGHT)
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        if (highlight) color.copy(alpha = 0.35f + 0.15f * glow) else ArcadeSurfaceHigh,
                        ArcadeSurface,
                    )
                )
            )
            .border(if (highlight) 3.dp else 2.dp, color.copy(alpha = if (highlight) 1f else 0.7f), shape)
            .padding(6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(Upgrades.icon(offer), fontSize = 32.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                Upgrades.title(offer),
                style = MaterialTheme.typography.labelSmall,
                color = ArcadeText,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
        }
    }
}

/** Die Kiste: wackelt, springt auf und strahlt. [open] 0..1 (0–0,6 Wackeln, danach Deckel auf). */
@Composable
private fun ChestGraphic(open: Float, glowColor: Color, evolution: Boolean, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "chest")
    val spin by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(8000, easing = LinearEasing)), label = "rays")
    val shimmer by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1600, easing = LinearEasing)), label = "shimmer")
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val shakePhase = (open / 0.6f).coerceIn(0f, 1f)
        val lid = ((open - 0.6f) / 0.4f).coerceIn(0f, 1f)
        val shake = if (shakePhase < 1f) sin(shakePhase * 40f) * w * 0.03f * shakePhase else 0f
        val hop = if (shakePhase < 1f) -kotlin.math.abs(sin(shakePhase * 12f)) * h * 0.04f * shakePhase else 0f

        val center = Offset(w / 2f, h * 0.6f)
        if (lid > 0f) {
            val rayColor = if (evolution) Color.hsv((shimmer * 360f) % 360f, 0.5f, 1f) else glowColor
            drawRays(center + Offset(0f, -h * 0.1f), w * 0.9f, rayColor, spin, 0.35f * lid)
            drawCircle(
                Brush.radialGradient(listOf(glowColor.copy(alpha = 0.7f * lid), Color.Transparent), center, w * 0.45f),
                w * 0.45f,
                center,
            )
        }

        translate(shake, hop) {
            val bodyW = w * 0.62f
            val bodyH = h * 0.32f
            val left = (w - bodyW) / 2f
            val top = h * 0.55f
            // Korpus
            drawRoundRect(Color(0xFF7C2D12), Offset(left, top), Size(bodyW, bodyH), CornerRadius(w * 0.03f))
            drawRoundRect(Color(0xFF9A3412), Offset(left, top), Size(bodyW, bodyH * 0.45f), CornerRadius(w * 0.03f))
            listOf(0.12f, 0.88f).forEach { fx ->
                drawRect(GOLD, Offset(left + bodyW * fx - w * 0.025f, top), Size(w * 0.05f, bodyH))
            }
            drawRoundRect(GOLD, Offset(w / 2f - w * 0.05f, top + bodyH * 0.12f), Size(w * 0.1f, bodyH * 0.35f), CornerRadius(w * 0.02f))
            // Leuchten aus der offenen Kiste
            if (lid > 0f) {
                drawRect(glowColor.copy(alpha = 0.8f * lid), Offset(left + w * 0.02f, top - h * 0.015f), Size(bodyW - w * 0.04f, h * 0.03f))
            }
            // Deckel: klappt nach hinten auf
            val lidH = h * 0.16f
            val lidTop = top - lidH
            rotate(-110f * lid, pivot = Offset(left, top - lidH * 0.1f)) {
                drawRoundRect(Color(0xFF9A3412), Offset(left, lidTop), Size(bodyW, lidH), CornerRadius(w * 0.06f, w * 0.06f))
                listOf(0.12f, 0.88f).forEach { fx ->
                    drawRect(GOLD, Offset(left + bodyW * fx - w * 0.025f, lidTop), Size(w * 0.05f, lidH))
                }
            }
        }
        // Funken steigen auf
        if (lid > 0f) {
            val rnd = Random(3)
            repeat(14) { i ->
                val speed = 0.5f + rnd.nextFloat()
                val t = (shimmer * speed + rnd.nextFloat()) % 1f
                val x = center.x + (rnd.nextFloat() - 0.5f) * w * 0.55f + sin(t * 6f + i) * w * 0.03f
                val y = center.y - t * h * 0.6f
                drawCircle(glowColor.copy(alpha = (1f - t) * lid), w * 0.012f * (1.5f - t), Offset(x, y))
            }
        }
    }
}

private fun DrawScope.drawRays(center: Offset, length: Float, color: Color, angle: Float, alpha: Float) {
    if (alpha <= 0f) return
    rotate(angle, pivot = center) {
        val rays = 12
        for (i in 0 until rays) {
            val a0 = i * 2f * PI.toFloat() / rays
            val a1 = a0 + PI.toFloat() / rays * 0.6f
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(center.x, center.y)
                lineTo(center.x + cos(a0) * length, center.y + sin(a0) * length)
                lineTo(center.x + cos(a1) * length, center.y + sin(a1) * length)
                close()
            }
            drawPath(path, Brush.radialGradient(listOf(color.copy(alpha = alpha), Color.Transparent), center, length))
        }
    }
}

/** Partikelregen bei der Landung; [t] läuft von 0 bis ~1 (Feder schwingt leicht darüber). */
private fun DrawScope.drawBurst(center: Offset, radius: Float, color: Color, t: Float) {
    val p = t.coerceIn(0f, 1f)
    if (p >= 1f) return
    val rnd = Random(11)
    repeat(28) {
        val angle = rnd.nextFloat() * 2f * PI.toFloat()
        val dist = radius * (0.3f + rnd.nextFloat() * 0.9f) * p
        val pos = center + Offset(cos(angle) * dist, sin(angle) * dist + p * p * radius * 0.3f)
        drawCircle(if (it % 3 == 0) Color.White else color, radius * 0.035f * (1f - p * 0.6f), pos, alpha = 1f - p)
    }
    drawCircle(color.copy(alpha = 0.6f * (1f - p)), radius * 0.6f * p, center, style = Stroke(width = radius * 0.04f))
}
