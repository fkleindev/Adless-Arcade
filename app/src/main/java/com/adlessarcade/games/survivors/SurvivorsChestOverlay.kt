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
import com.adlessarcade.ui.components.SecondaryButton
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
private val CARD_GAP = 10.dp

/** Seltenheitsfarbe einer Belohnung. */
fun rewardColor(state: SurvivorsState, offer: UpgradeOffer): Color = when {
    offer.evolution != null -> GOLD
    offer.weapon != null && offer.weapon !in state.weapons -> Color(0xFF22D3EE)
    offer.weapon != null -> Color(0xFFF1F5F9)
    offer.passive != null -> Color(0xFFA78BFA)
    else -> Color(0xFF4ADE80)
}

/**
 * Kisten-Overlay: Kiste springt auf, eine Walze mit möglichen Belohnungen rollt aus und bremst auf dem Gewinn ab.
 * Danach kann der Spieler die Belohnung annehmen oder (für etwas Heilung) ablehnen.
 */
@Composable
fun ChestOverlay(state: SurvivorsState, roll: ChestRoll, onAccept: () -> Unit, onDecline: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val isEvolution = roll.offer.evolution != null
    val color = rewardColor(state, roll.offer)

    // Walze: zufällige Füllkarten, die Gewinnkarte an fester Position
    val strip = remember(roll.rollId) {
        val rnd = Random(roll.rollId * 7919 + roll.index)
        val length = if (isEvolution) 44 else 30
        List(length) { roll.reel[rnd.nextInt(roll.reel.size)] } + roll.offer + List(3) { roll.reel[rnd.nextInt(roll.reel.size)] }
    }
    val target = strip.size - 4f

    val chestOpen = remember(roll.rollId) { Animatable(if (roll.index > 0 || !roll.animate) 1f else 0f) }
    val reel = remember(roll.rollId) { Animatable(if (roll.animate) 0f else target) }
    val landing = remember(roll.rollId) { Animatable(if (roll.animate) 0f else 1f) }
    var landed by remember(roll.rollId) { mutableStateOf(!roll.animate) }

    fun land() {
        if (landed) return
        landed = true
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        scope.launch { landing.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)) }
    }

    LaunchedEffect(roll.rollId) {
        if (!roll.animate) return@LaunchedEffect
        if (chestOpen.value < 1f) chestOpen.animateTo(1f, tween(900, easing = LinearEasing))
        reel.animateTo(
            target,
            tween(if (isEvolution) 3000 else 2000, easing = CubicBezierEasing(0.12f, 0.75f, 0.2f, 1f)),
        )
        land()
    }
    // Haptischer Tick für jede vorbeiziehende Karte
    LaunchedEffect(roll.rollId) {
        snapshotFlow { floor(reel.value + 0.5f).toInt() }.collect {
            if (!landed && reel.isRunning) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    val skip = {
        if (!landed) {
            scope.launch {
                chestOpen.snapTo(1f)
                reel.snapTo(target)
                land()
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.78f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { skip() },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                if (isEvolution && landed) "ENTWICKLUNG!" else "TRUHE!",
                style = MaterialTheme.typography.headlineMedium,
                color = if (isEvolution && landed) GOLD else ArcadeText,
            )
            Text(
                if (roll.total > 1) "Belohnung ${roll.index + 1} von ${roll.total}" else "Belohnung",
                style = MaterialTheme.typography.bodyMedium,
                color = ArcadeTextMuted,
            )
            Spacer(Modifier.height(8.dp))

            ChestGraphic(chestOpen.value, if (landed) color else GOLD, isEvolution && landed, Modifier.size(150.dp))

            Spacer(Modifier.height(8.dp))
            Reel(strip, reel.value, state, landed, landing.value, Modifier.fillMaxWidth().height(126.dp))
            Spacer(Modifier.height(14.dp))

            // Details & Auswahl (fester Platz, damit nichts springt)
            Box(Modifier.fillMaxWidth().height(210.dp), contentAlignment = Alignment.TopCenter) {
                if (landed) {
                    RewardDetails(state, roll, color, onAccept, onDecline, Modifier.alpha(landing.value.coerceIn(0f, 1f)))
                } else {
                    Text("Tippen zum Überspringen", style = MaterialTheme.typography.labelLarge, color = ArcadeTextMuted)
                }
            }
        }
    }
}

@Composable
private fun RewardDetails(
    state: SurvivorsState,
    roll: ChestRoll,
    color: Color,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val offer = roll.offer
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
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
        Spacer(Modifier.height(14.dp))
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton("Nehmen", onAccept)
            if (!offer.heal) {
                SecondaryButton("Ablehnen (+${SurvivorsEngine.CHEST_DECLINE_HEAL.toInt()} ❤)", onDecline)
            }
        }
    }
}

/** Horizontale Walze; [position] = Kartenindex in der Mitte (als Kommazahl während des Rollens). */
@Composable
private fun Reel(
    strip: List<UpgradeOffer>,
    position: Float,
    state: SurvivorsState,
    landed: Boolean,
    landing: Float,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val pitch = with(density) { (CARD_WIDTH + CARD_GAP).toPx() }
    val cardPx = with(density) { CARD_WIDTH.toPx() }
    val winner = strip.size - 4
    val winnerColor = rewardColor(state, strip[winner])
    val sparkle = rememberInfiniteTransition(label = "sparkle")
    val spin by sparkle.animateFloat(0f, 360f, infiniteRepeatable(tween(6000, easing = LinearEasing)), label = "spin")
    val pulse by sparkle.animateFloat(0.6f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "pulse")

    BoxWithConstraints(modifier.clip(RoundedCornerShape(20.dp)).background(Color.Black.copy(alpha = 0.35f))) {
        val width = with(density) { maxWidth.toPx() }
        val centerX = width / 2f

        // Lichtstrahlen und Partikel hinter der Gewinnkarte
        Canvas(Modifier.fillMaxSize()) {
            if (landed) {
                drawRays(Offset(centerX, size.height / 2f), size.height * 1.4f, winnerColor, spin, 0.25f * landing.coerceIn(0f, 1f))
                drawBurst(Offset(centerX, size.height / 2f), size.height, winnerColor, landing)
            }
        }

        val first = floor(position - centerX / pitch - 1).toInt().coerceAtLeast(0)
        val last = (position + centerX / pitch + 1).toInt().coerceAtMost(strip.lastIndex)
        for (i in first..last) {
            val x = centerX + (i - position) * pitch - cardPx / 2f
            val isWinner = i == winner && landed
            val distance = kotlin.math.abs(i - position)
            val baseScale = (1f - 0.12f * distance).coerceIn(0.75f, 1f)
            val scale = if (isWinner) baseScale * (1f + 0.18f * landing) else baseScale
            RewardCard(
                strip[i],
                state,
                highlight = isWinner,
                glow = if (isWinner) pulse else 0f,
                modifier = Modifier
                    .offset { androidx.compose.ui.unit.IntOffset(x.roundToInt(), 0) }
                    .align(Alignment.CenterStart)
                    .scale(scale)
                    .alpha(if (landed && !isWinner) 0.35f else 1f),
            )
        }

        // Markierung in der Mitte
        Canvas(Modifier.fillMaxSize()) {
            val tri = 10.dp.toPx()
            listOf(0f to 1f, size.height to -1f).forEach { (y, dir) ->
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(centerX - tri, y)
                    lineTo(centerX + tri, y)
                    lineTo(centerX, y + dir * tri * 1.2f)
                    close()
                }
                drawPath(path, GOLD)
            }
        }
    }
}

@Composable
private fun RewardCard(offer: UpgradeOffer, state: SurvivorsState, highlight: Boolean, glow: Float, modifier: Modifier = Modifier) {
    val color = rewardColor(state, offer)
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier
            .size(CARD_WIDTH, 108.dp)
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
