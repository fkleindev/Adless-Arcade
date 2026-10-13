package com.adlessarcade.games.gaterush

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import com.adlessarcade.games.gaterush.GateRushEngine.Companion.Z_FAR
import com.adlessarcade.ui.theme.ArcadeBackground
import com.adlessarcade.ui.theme.ArcadeBackgroundTop
import com.adlessarcade.ui.theme.NeonCyan
import com.adlessarcade.ui.theme.NeonPink
import com.adlessarcade.ui.theme.NeonViolet
import com.adlessarcade.ui.theme.NeonYellow
import kotlin.math.max
import kotlin.math.sin

private const val CAMERA_DISTANCE = 7f
private val RoadColor = Color(0xFF171926)
private val GroundColor = Color(0xFF0F1019)
private val GoodColor = Color(0xFF4ADE80)
private val BadColor = Color(0xFFFF5C6C)
private val NeutralColor = Color(0xFF9097B0)

/** Perspektivische Projektion von Weltkoordinaten (x, z) auf den Bildschirm. */
class RoadProjection(val width: Float, val height: Float) {
    val horizon = height * 0.16f
    val baseY = height * 0.82f
    val centerX = width / 2f
    val halfRoad = width * 0.44f

    fun scale(z: Float): Float = CAMERA_DISTANCE / (CAMERA_DISTANCE + z)
    fun y(z: Float): Float = horizon + (baseY - horizon) * scale(z)
    fun x(x: Float, z: Float): Float = centerX + x * halfRoad * scale(z)
    fun size(world: Float, z: Float): Float = world * halfRoad * scale(z)
}

fun DrawScope.drawGateRush(
    engine: GateRushEngine,
    effects: List<GateRushEffect>,
    damageFlash: Float,
    textMeasurer: TextMeasurer,
) {
    val p = RoadProjection(size.width, size.height)
    drawEnvironment(p, engine.distance, engine.time)

    // Tiefensortiert von hinten nach vorne zeichnen.
    data class Drawable(val z: Float, val draw: () -> Unit)
    val drawables = ArrayList<Drawable>(engine.enemies.size + engine.gates.size + engine.crates.size)
    engine.gates.forEach { g -> drawables += Drawable(g.z) { drawGate(p, g, textMeasurer) } }
    engine.crates.forEach { c -> drawables += Drawable(c.z) { drawCrate(p, c, textMeasurer) } }
    engine.enemies.forEach { e -> drawables += Drawable(e.z) { drawEnemy(p, e, engine.time) } }
    drawables.sortByDescending { it.z }
    drawables.forEach { it.draw() }

    engine.bullets.forEach { drawBullet(p, it) }
    drawHero(p, engine.playerX, engine.time)
    effects.forEach { drawEffect(p, it, textMeasurer) }

    if (damageFlash > 0f) {
        drawRect(
            Brush.radialGradient(
                listOf(Color.Transparent, BadColor.copy(alpha = 0.55f * damageFlash)),
                center = Offset(size.width / 2f, size.height / 2f),
                radius = max(size.width, size.height) * 0.75f,
            )
        )
    }
}

private fun DrawScope.drawEnvironment(p: RoadProjection, distance: Float, time: Float) {
    drawRect(Brush.verticalGradient(listOf(ArcadeBackgroundTop, ArcadeBackground), endY = p.horizon))
    drawRect(GroundColor, topLeft = Offset(0f, p.horizon), size = Size(size.width, size.height - p.horizon))

    // Querlinien auf dem Boden erzeugen das Gefühl von Vorwärtsbewegung.
    val spacing = 3f
    val offset = distance % spacing
    var z = -offset - spacing
    while (z < Z_FAR + 6f) {
        val y = p.y(z)
        drawLine(NeonViolet.copy(alpha = 0.08f * p.scale(z) * 3f), Offset(0f, y), Offset(size.width, y), strokeWidth = 1.5f)
        z += spacing
    }

    val nearZ = -3f
    val farZ = Z_FAR + 8f
    val road = Path().apply {
        moveTo(p.x(-1f, farZ), p.y(farZ))
        lineTo(p.x(1f, farZ), p.y(farZ))
        lineTo(p.x(1f, nearZ), p.y(nearZ))
        lineTo(p.x(-1f, nearZ), p.y(nearZ))
        close()
    }
    drawPath(road, RoadColor)

    // Mittelstreifen
    var dz = -(distance % 4f) - 4f
    while (dz < Z_FAR + 6f) {
        val z0 = max(dz, nearZ)
        val z1 = dz + 1.6f
        val dash = Path().apply {
            moveTo(p.x(-0.012f, z0), p.y(z0))
            lineTo(p.x(0.012f, z0), p.y(z0))
            lineTo(p.x(0.012f, z1), p.y(z1))
            lineTo(p.x(-0.012f, z1), p.y(z1))
            close()
        }
        drawPath(dash, Color.White.copy(alpha = 0.18f))
        dz += 4f
    }

    // Neon-Ränder
    listOf(-1f, 1f).forEach { side ->
        val start = Offset(p.x(side, farZ), p.y(farZ))
        val end = Offset(p.x(side, nearZ), p.y(nearZ))
        drawLine(NeonViolet.copy(alpha = 0.25f), start, end, strokeWidth = 14f)
        drawLine(NeonViolet, start, end, strokeWidth = 4f)
    }

    // Verteidigungslinie
    val pulse = 0.55f + 0.25f * sin(time * 5f)
    val lineY = p.y(0f)
    drawLine(
        NeonPink.copy(alpha = pulse * 0.35f),
        Offset(p.x(-1f, 0f), lineY), Offset(p.x(1f, 0f), lineY), strokeWidth = 16f,
    )
    drawLine(NeonPink.copy(alpha = pulse), Offset(p.x(-1f, 0f), lineY), Offset(p.x(1f, 0f), lineY), strokeWidth = 4f)
}

private fun DrawScope.drawGate(p: RoadProjection, gate: GatePair, textMeasurer: TextMeasurer) {
    if (gate.z > Z_FAR + 1f) return
    val z = gate.z
    val bottom = p.y(z)
    val height = p.size(0.8f, z)
    listOf(Triple(gate.left, -0.99f, -0.02f), Triple(gate.right, 0.02f, 0.99f)).forEach { (side, xa, xb) ->
        if (side == null) return@forEach
        val left = p.x(xa, z)
        val right = p.x(xb, z)
        val base = when {
            side.value > 0 -> GoodColor
            side.value < 0 -> BadColor
            else -> NeutralColor
        }
        val color = lerp(base, Color.White, side.hitFlash * 0.5f)
        val topLeft = Offset(left, bottom - height)
        val panelSize = Size(right - left, height)
        val radius = CornerRadius(height * 0.08f)
        drawRoundRect(
            Brush.verticalGradient(
                listOf(color.copy(alpha = 0.15f), color.copy(alpha = 0.45f)),
                startY = topLeft.y, endY = bottom,
            ),
            topLeft, panelSize, radius,
        )
        drawRoundRect(color, topLeft, panelSize, radius, style = Stroke(width = max(2f, height * 0.03f)))

        if (height > 18f) {
            val valueLayout = textMeasurer.measure(
                side.valueText,
                TextStyle(color = Color.White, fontSize = (height * 0.36f).toSp(), fontWeight = FontWeight.Black),
            )
            val titleLayout = textMeasurer.measure(
                side.type.title,
                TextStyle(color = Color.White.copy(alpha = 0.85f), fontSize = (height * 0.13f).toSp(), fontWeight = FontWeight.Bold),
            )
            val cx = (left + right) / 2f
            val totalH = valueLayout.size.height + titleLayout.size.height
            val startY = bottom - height / 2f - totalH / 2f
            drawText(valueLayout, topLeft = Offset(cx - valueLayout.size.width / 2f, startY))
            drawText(
                titleLayout,
                topLeft = Offset(cx - titleLayout.size.width / 2f, startY + valueLayout.size.height),
            )
        }
    }
}

private fun DrawScope.drawCrate(p: RoadProjection, crate: Crate, textMeasurer: TextMeasurer) {
    if (crate.z > Z_FAR + 1f) return
    val s = p.size(0.3f, crate.z)
    val cx = p.x(crate.x, crate.z)
    val bottom = p.y(crate.z)
    val topLeft = Offset(cx - s / 2f, bottom - s)
    val color = lerp(Color(0xFFB7791F), Color.White, crate.hitFlash * 0.5f)
    drawOval(Color.Black.copy(alpha = 0.35f), Offset(cx - s * 0.6f, bottom - s * 0.1f), Size(s * 1.2f, s * 0.2f))
    drawRoundRect(color, topLeft, Size(s, s), CornerRadius(s * 0.1f))
    drawRoundRect(NeonYellow, topLeft, Size(s, s), CornerRadius(s * 0.1f), style = Stroke(width = max(2f, s * 0.06f)))
    drawLine(NeonYellow.copy(alpha = 0.6f), topLeft, topLeft + Offset(s, s), strokeWidth = max(1.5f, s * 0.04f))
    drawLine(NeonYellow.copy(alpha = 0.6f), topLeft + Offset(s, 0f), topLeft + Offset(0f, s), strokeWidth = max(1.5f, s * 0.04f))

    if (s > 14f) {
        val countLayout = textMeasurer.measure(
            "${crate.hitsLeft}",
            TextStyle(color = Color.White, fontSize = (s * 0.42f).toSp(), fontWeight = FontWeight.Black),
        )
        drawText(countLayout, topLeft = Offset(cx - countLayout.size.width / 2f, bottom - s / 2f - countLayout.size.height / 2f))
        val rewardLayout = textMeasurer.measure(
            crate.reward.label,
            TextStyle(color = NeonYellow, fontSize = (s * 0.17f).toSp(), fontWeight = FontWeight.Bold),
        )
        drawText(rewardLayout, topLeft = Offset(cx - rewardLayout.size.width / 2f, topLeft.y - rewardLayout.size.height))
    }
}

private fun DrawScope.drawEnemy(p: RoadProjection, e: Enemy, time: Float) {
    if (e.z > Z_FAR + 3f) return
    val w = p.size(e.type.radius * 2f, e.z)
    val h = w * if (e.type == EnemyType.BOSS) 1.1f else 1.35f
    val bob = sin(time * 10f + e.id) * h * 0.04f
    val cx = p.x(e.x, e.z)
    val bottom = p.y(e.z)
    val baseColor = when (e.type) {
        EnemyType.RUNNER -> Color(0xFFFF5C6C)
        EnemyType.SWARM -> Color(0xFFFF9F43)
        EnemyType.BRUTE -> Color(0xFFA78BFA)
        EnemyType.BOSS -> Color(0xFFE11D48)
    }
    val color = lerp(baseColor, Color.White, e.hitFlash * 0.7f)
    val topLeft = Offset(cx - w / 2f, bottom - h + bob)

    drawOval(Color.Black.copy(alpha = 0.35f), Offset(cx - w * 0.55f, bottom - w * 0.1f), Size(w * 1.1f, w * 0.2f))
    drawRoundRect(color, topLeft, Size(w, h), CornerRadius(w * 0.4f))
    drawRoundRect(
        Color.Black.copy(alpha = 0.2f),
        Offset(topLeft.x, topLeft.y + h * 0.7f), Size(w, h * 0.3f), CornerRadius(w * 0.4f),
    )
    // Augen
    val eyeY = topLeft.y + h * 0.32f
    val eyeR = w * 0.12f
    listOf(-0.2f, 0.2f).forEach { dx ->
        drawCircle(Color.White, eyeR, Offset(cx + dx * w, eyeY))
        drawCircle(Color(0xFF111111), eyeR * 0.5f, Offset(cx + dx * w, eyeY + eyeR * 0.2f))
    }
    if (e.type == EnemyType.BOSS) {
        val crown = Path().apply {
            val y = topLeft.y
            moveTo(cx - w * 0.3f, y)
            lineTo(cx - w * 0.3f, y - h * 0.18f)
            lineTo(cx - w * 0.15f, y - h * 0.08f)
            lineTo(cx, y - h * 0.22f)
            lineTo(cx + w * 0.15f, y - h * 0.08f)
            lineTo(cx + w * 0.3f, y - h * 0.18f)
            lineTo(cx + w * 0.3f, y)
            close()
        }
        drawPath(crown, NeonYellow)
    }

    // HP-Balken
    if (e.hp < e.maxHp || e.type == EnemyType.BOSS) {
        val barW = max(w, 16f)
        val barH = max(3f, w * 0.08f)
        val barTop = topLeft.y - barH * 2.5f - if (e.type == EnemyType.BOSS) h * 0.22f else 0f
        val barLeft = cx - barW / 2f
        drawRoundRect(Color.Black.copy(alpha = 0.6f), Offset(barLeft, barTop), Size(barW, barH), CornerRadius(barH / 2f))
        drawRoundRect(
            if (e.type == EnemyType.BOSS) NeonYellow else GoodColor,
            Offset(barLeft, barTop), Size(barW * (e.hp / e.maxHp).coerceIn(0f, 1f), barH), CornerRadius(barH / 2f),
        )
    }
}

private fun DrawScope.drawBullet(p: RoadProjection, b: Bullet) {
    val r = max(2.5f, p.size(0.025f, b.z))
    val center = Offset(p.x(b.x, b.z), p.y(b.z) - p.size(0.12f, b.z))
    val tail = Offset(p.x(b.x - b.vx * 0.03f, b.z - 0.8f), p.y(b.z - 0.8f) - p.size(0.12f, b.z - 0.8f))
    drawLine(NeonCyan.copy(alpha = 0.35f), tail, center, strokeWidth = r * 1.4f)
    drawCircle(NeonCyan.copy(alpha = 0.3f), r * 2.2f, center)
    drawCircle(Color.White, r, center)
}

private fun DrawScope.drawHero(p: RoadProjection, x: Float, time: Float) {
    val z = 0f
    val w = p.size(0.2f, z)
    val h = w * 1.3f
    val cx = p.x(x, z)
    val bottom = p.y(z) + h * 0.35f
    val bob = sin(time * 14f) * h * 0.03f
    val top = bottom - h + bob

    drawOval(Color.Black.copy(alpha = 0.4f), Offset(cx - w * 0.6f, bottom - w * 0.12f), Size(w * 1.2f, w * 0.24f))
    drawCircle(NeonCyan.copy(alpha = 0.18f), w * 0.9f, Offset(cx, top + h * 0.5f))
    // Körper
    drawRoundRect(NeonCyan, Offset(cx - w / 2f, top + h * 0.3f), Size(w, h * 0.7f), CornerRadius(w * 0.3f))
    drawRoundRect(
        Color.Black.copy(alpha = 0.2f),
        Offset(cx - w / 2f, top + h * 0.75f), Size(w, h * 0.25f), CornerRadius(w * 0.3f),
    )
    // Kopf
    drawCircle(Color.White, w * 0.26f, Offset(cx, top + h * 0.2f))
    drawRoundRect(NeonViolet, Offset(cx - w * 0.2f, top + h * 0.12f), Size(w * 0.4f, w * 0.12f), CornerRadius(w * 0.06f))
    // Waffe
    drawRoundRect(Color(0xFF2A2E40), Offset(cx + w * 0.18f, top + h * 0.05f), Size(w * 0.16f, h * 0.55f), CornerRadius(w * 0.05f))
    drawCircle(NeonCyan, w * 0.06f, Offset(cx + w * 0.26f, top + h * 0.05f))
}

private fun DrawScope.drawEffect(p: RoadProjection, effect: GateRushEffect, textMeasurer: TextMeasurer) {
    val t = effect.progress
    val alpha = 1f - t
    when (effect) {
        is GateRushEffect.Burst -> {
            val center = Offset(p.x(effect.x, effect.z), p.y(effect.z) - p.size(effect.size * 0.5f, effect.z))
            val radius = p.size(effect.size, effect.z) * (0.4f + t)
            drawCircle(effect.color.copy(alpha = alpha * 0.35f), radius, center)
            drawCircle(effect.color.copy(alpha = alpha), radius, center, style = Stroke(width = max(2f, radius * 0.15f)))
        }
        is GateRushEffect.FloatingText -> {
            val layout = textMeasurer.measure(
                effect.text,
                TextStyle(
                    color = effect.color.copy(alpha = alpha),
                    fontSize = (size.width * 0.055f).toSp(),
                    fontWeight = FontWeight.Black,
                ),
            )
            val base = Offset(p.x(effect.x, effect.z), p.y(effect.z) - p.size(0.4f, effect.z))
            drawText(
                layout,
                topLeft = Offset(
                    (base.x - layout.size.width / 2f).coerceAtMost(size.width - layout.size.width).coerceAtLeast(0f),
                    base.y - layout.size.height - t * size.height * 0.08f,
                ),
            )
        }
    }
}
