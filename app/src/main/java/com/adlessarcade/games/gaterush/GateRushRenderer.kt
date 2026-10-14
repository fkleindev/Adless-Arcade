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
import com.adlessarcade.ui.components.CoinGold
import com.adlessarcade.ui.components.drawCoin
import com.adlessarcade.ui.theme.HeroSkin
import com.adlessarcade.ui.theme.ArcadeBackground
import com.adlessarcade.ui.theme.ArcadeBackgroundTop
import com.adlessarcade.ui.theme.NeonCyan
import com.adlessarcade.ui.theme.NeonPink
import com.adlessarcade.ui.theme.NeonViolet
import com.adlessarcade.ui.theme.NeonYellow
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

private const val CAMERA_DISTANCE = 7f
private val RoadColor = Color(0xFF171926)
private val GroundColor = Color(0xFF0F1019)
private val GoodColor = Color(0xFF4ADE80)
private val BadColor = Color(0xFFFF5C6C)
private val NeutralColor = Color(0xFF9097B0)
private val FrostColor = Color(0xFF7DD3FC)
private val ShieldColor = Color(0xFF38BDF8)
private val WeaponGateColor = Color(0xFFC084FC)
private val WarningYellow = Color(0xFFFACC15)

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
    heroSkin: HeroSkin,
) {
    val p = RoadProjection(size.width, size.height)
    drawEnvironment(p, engine.distance, engine.time)

    // Tiefensortiert von hinten nach vorne zeichnen.
    data class Drawable(val z: Float, val draw: () -> Unit)
    val drawables = ArrayList<Drawable>(engine.enemies.size + engine.gates.size + engine.crates.size)
    engine.gates.forEach { g -> drawables += Drawable(g.z) { drawGate(p, g, textMeasurer) } }
    engine.crates.forEach { c -> drawables += Drawable(c.z) { drawCrate(p, c, textMeasurer) } }
    engine.obstacles.forEach { o -> drawables += Drawable(o.z) { drawObstacle(p, o) } }
    engine.pickups.forEach { pu -> drawables += Drawable(pu.z) { drawPickup(p, pu, engine.time, textMeasurer) } }
    engine.enemies.forEach { e -> drawables += Drawable(e.z) { drawEnemy(p, e, engine.time) } }
    drawables.sortByDescending { it.z }
    drawables.forEach { it.draw() }

    engine.bullets.forEachIndexed { i, b -> drawBullet(p, b, heroSkin, engine.time, i) }
    engine.dronePositions().forEach { drawDrone(p, it, engine.time, heroSkin) }
    if (engine.stats.frenzy > 0f) drawFrenzyAura(p, engine.playerX, engine.time)
    drawHero(p, engine.playerX, engine.time, heroSkin)
    if (engine.stats.shields > 0) drawShield(p, engine.playerX, engine.stats.shields, engine.time)
    effects.forEach { drawEffect(p, it, textMeasurer) }

    // Zeitlupe: kühler Farbschleier über allem.
    if (engine.stats.slowmo > 0f) {
        drawRect(FrostColor.copy(alpha = 0.10f * (engine.stats.slowmo / PowerUpKind.SLOWMO.duration).coerceIn(0.3f, 1f)))
    }

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

private fun DrawScope.drawGate(p: RoadProjection, gate: GateRow, textMeasurer: TextMeasurer) {
    if (gate.z > Z_FAR + 1f) return
    val z = gate.z
    val bottom = p.y(z)
    val height = p.size(0.8f, z)
    gate.lanes.forEachIndexed { lane, side ->
        val left = p.x(gate.laneLeft(lane) + 0.015f, z)
        val right = p.x(gate.laneRight(lane) - 0.015f, z)
        if (side == null) {
            // Lücke: nur zwei schmale Pfosten, keine Wirkung.
            val post = max(2f, height * 0.05f)
            listOf(left, right - post).forEach { x ->
                drawRoundRect(NeutralColor.copy(alpha = 0.5f), Offset(x, bottom - height), Size(post, height), CornerRadius(post / 2f))
            }
            return@forEachIndexed
        }
        val base = when {
            side.type == GateType.WEAPON -> WeaponGateColor
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
            // Schriftgröße auch an die Spurbreite anpassen, damit Texte bei 4 Spuren nicht überlaufen.
            val width = right - left
            val valueSize = minOf(height * 0.36f, width / (side.valueText.length * 0.62f))
            val titleSize = minOf(height * 0.13f, width * 0.92f / (side.title.length * 0.62f))
            val valueLayout = textMeasurer.measure(
                side.valueText,
                TextStyle(color = Color.White, fontSize = valueSize.toSp(), fontWeight = FontWeight.Black),
            )
            val titleLayout = textMeasurer.measure(
                side.title,
                TextStyle(color = Color.White.copy(alpha = 0.85f), fontSize = titleSize.toSp(), fontWeight = FontWeight.Bold),
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
        EnemyType.ZIGZAG -> Color(0xFF2DD4BF)
        EnemyType.SPLITTER -> Color(0xFFF97316)
        EnemyType.SPLITLING -> Color(0xFFFDBA74)
        EnemyType.SHIELDED -> Color(0xFF64748B)
    }
    val tinted = if (e.slowTimer > 0f) lerp(baseColor, FrostColor, 0.55f) else baseColor
    val color = lerp(tinted, Color.White, e.hitFlash * 0.7f)
    val topLeft = Offset(cx - w / 2f, bottom - h + bob)

    drawOval(Color.Black.copy(alpha = 0.35f), Offset(cx - w * 0.55f, bottom - w * 0.1f), Size(w * 1.1f, w * 0.2f))
    if (e.type == EnemyType.ZIGZAG) {
        // Bewegungsstreifen hinter dem Pendler.
        val dir = if (cos(time * 2.5f + e.phase) >= 0f) -1f else 1f
        repeat(3) { i ->
            val off = (i + 1) * w * 0.35f * dir
            drawRoundRect(
                baseColor.copy(alpha = 0.25f - i * 0.07f),
                Offset(topLeft.x + off, topLeft.y + h * 0.2f), Size(w, h * 0.6f), CornerRadius(w * 0.4f),
            )
        }
    }
    drawRoundRect(color, topLeft, Size(w, h), CornerRadius(w * 0.4f))
    drawRoundRect(
        Color.Black.copy(alpha = 0.2f),
        Offset(topLeft.x, topLeft.y + h * 0.7f), Size(w, h * 0.3f), CornerRadius(w * 0.4f),
    )
    if (e.type == EnemyType.SPLITTER) {
        // Risse deuten an, dass er zerfällt.
        val crack = Path().apply {
            moveTo(cx - w * 0.1f, topLeft.y + h * 0.05f)
            lineTo(cx + w * 0.05f, topLeft.y + h * 0.3f)
            lineTo(cx - w * 0.08f, topLeft.y + h * 0.5f)
            lineTo(cx + w * 0.1f, topLeft.y + h * 0.8f)
        }
        drawPath(crack, Color(0xFF431407), style = Stroke(width = max(1.5f, w * 0.06f)))
    }
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
    if (e.type == EnemyType.SHIELDED) {
        // Frontaler Schild vor dem Körper.
        val shieldTop = topLeft.y + h * 0.15f
        drawRoundRect(
            ShieldColor.copy(alpha = 0.35f),
            Offset(cx - w * 0.65f, shieldTop), Size(w * 1.3f, h * 0.75f), CornerRadius(w * 0.2f),
        )
        drawRoundRect(
            ShieldColor,
            Offset(cx - w * 0.65f, shieldTop), Size(w * 1.3f, h * 0.75f), CornerRadius(w * 0.2f),
            style = Stroke(width = max(1.5f, w * 0.07f)),
        )
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

/** Projektilfarbe; beim Regenbogen-Skin wandert der Farbton mit der Flugstrecke. */
fun HeroSkin.bulletColor(z: Float, time: Float): Color =
    if (rainbow) Color.hsv(((z * 25f + time * 240f) % 360f + 360f) % 360f, 0.75f, 1f) else bullet

private fun DrawScope.drawBullet(p: RoadProjection, b: Bullet, skin: HeroSkin, time: Float, index: Int) {
    val color = skin.bulletColor(b.z + index * 0.7f, time)
    fun at(z: Float) = Offset(p.x(b.x - b.vx * (b.z - z) / b.weapon.speed, z), p.y(z) - p.size(0.12f, z))
    val center = at(b.z)
    when (b.weapon) {
        Weapon.BLASTER -> {
            val r = max(2.5f, p.size(0.025f, b.z))
            drawLine(color.copy(alpha = 0.35f), at(b.z - 0.8f), center, strokeWidth = r * 1.4f)
            drawCircle(color.copy(alpha = 0.3f), r * 2.2f, center)
            drawCircle(Color.White, r, center)
        }
        Weapon.SHOTGUN -> {
            val r = max(2f, p.size(0.018f, b.z))
            drawCircle(color.copy(alpha = 0.35f), r * 2f, center)
            drawCircle(lerp(color, Color.White, 0.5f), r, center)
        }
        Weapon.LASER -> {
            val r = max(1.5f, p.size(0.016f, b.z))
            drawLine(color.copy(alpha = 0.3f), at(b.z - 2.2f), center, strokeWidth = r * 4f)
            drawLine(Color.White, at(b.z - 2.2f), center, strokeWidth = r * 1.4f)
        }
        Weapon.ROCKET -> {
            val r = max(3f, p.size(0.035f, b.z))
            // Rauchspur
            repeat(4) { i ->
                val z = b.z - 0.4f * (i + 1)
                drawCircle(Color.White.copy(alpha = 0.18f - i * 0.04f), r * (1f + i * 0.35f), at(z))
            }
            drawCircle(Color(0xFFFF9F43).copy(alpha = 0.5f), r * 1.8f, at(b.z - 0.25f))
            drawRoundRect(color, center - Offset(r * 0.6f, r * 1.4f), Size(r * 1.2f, r * 2.4f), CornerRadius(r * 0.6f))
        }
    }
}

/** Unzerstörbares Hindernis: Betonblock mit Warnstreifen oder Stachelbarriere. */
private fun DrawScope.drawObstacle(p: RoadProjection, o: Obstacle) {
    if (o.z > Z_FAR + 1f) return
    val left = p.x(o.x - o.halfWidth, o.z)
    val right = p.x(o.x + o.halfWidth, o.z)
    val bottom = p.y(o.z)
    val width = right - left
    when (o.kind) {
        ObstacleKind.BLOCK -> {
            val h = p.size(0.32f, o.z)
            val top = bottom - h
            drawRect(Color.Black.copy(alpha = 0.35f), Offset(left, bottom - h * 0.08f), Size(width, h * 0.16f))
            drawRect(Color(0xFF3F4458), Offset(left, top), Size(width, h))
            drawRect(Color(0xFF575D75), Offset(left, top), Size(width, h * 0.18f))
            // Gelb-schwarze Warnstreifen
            val stripeW = max(4f, h * 0.35f)
            var x = left
            var k = 0
            while (x < right) {
                if (k % 2 == 0) {
                    drawRect(WarningYellow, Offset(x, top + h * 0.45f), Size(minOf(stripeW, right - x), h * 0.22f))
                }
                x += stripeW
                k++
            }
        }
        ObstacleKind.SPIKES -> {
            val h = p.size(0.26f, o.z)
            drawRect(Color(0xFF2A2E40), Offset(left, bottom - h * 0.25f), Size(width, h * 0.25f))
            val count = (width / max(6f, h * 0.45f)).toInt().coerceAtLeast(3)
            val step = width / count
            repeat(count) { i ->
                val spike = Path().apply {
                    moveTo(left + i * step, bottom - h * 0.25f)
                    lineTo(left + (i + 0.5f) * step, bottom - h)
                    lineTo(left + (i + 1) * step, bottom - h * 0.25f)
                    close()
                }
                drawPath(spike, Color(0xFFCBD5E1))
                drawPath(spike, BadColor.copy(alpha = 0.6f), style = Stroke(width = max(1f, h * 0.03f)))
            }
        }
    }
}

/** Leuchtendes, schwebendes Power-up-Symbol. */
private fun DrawScope.drawPickup(p: RoadProjection, pu: Pickup, time: Float, textMeasurer: TextMeasurer) {
    if (pu.z > Z_FAR + 1f) return
    val r = p.size(0.11f, pu.z)
    val hover = sin(time * 4f + pu.id) * r * 0.25f
    val center = Offset(p.x(pu.x, pu.z), p.y(pu.z) - r * 1.6f + hover)
    val color = when (pu.kind) {
        PowerUpKind.FRENZY -> Color(0xFFFF3B5C)
        PowerUpKind.SLOWMO -> FrostColor
        PowerUpKind.BOMB -> NeonYellow
    }
    drawOval(Color.Black.copy(alpha = 0.3f), Offset(center.x - r, p.y(pu.z) - r * 0.15f), Size(r * 2f, r * 0.3f))
    drawCircle(color.copy(alpha = 0.25f + 0.1f * sin(time * 6f)), r * 1.7f, center)
    val diamond = Path().apply {
        moveTo(center.x, center.y - r)
        lineTo(center.x + r, center.y)
        lineTo(center.x, center.y + r)
        lineTo(center.x - r, center.y)
        close()
    }
    drawPath(diamond, color)
    drawPath(diamond, Color.White.copy(alpha = 0.8f), style = Stroke(width = max(1.5f, r * 0.1f)))
    if (r > 8f) {
        val symbol = when (pu.kind) {
            PowerUpKind.FRENZY -> "»"
            PowerUpKind.SLOWMO -> "⏱"
            PowerUpKind.BOMB -> "✸"
        }
        val layout = textMeasurer.measure(
            symbol,
            TextStyle(color = Color(0xFF111111), fontSize = (r * 0.9f).toSp(), fontWeight = FontWeight.Black),
        )
        drawText(layout, topLeft = center - Offset(layout.size.width / 2f, layout.size.height / 2f))
    }
}

/** Rote, pulsierende Aura während der Raserei. */
private fun DrawScope.drawFrenzyAura(p: RoadProjection, x: Float, time: Float) {
    val w = p.size(0.2f, 0f)
    val center = Offset(p.x(x, 0f), p.y(0f) - w * 0.45f)
    val pulse = 0.7f + 0.3f * sin(time * 18f)
    drawCircle(Color(0xFFFF3B5C).copy(alpha = 0.22f * pulse), w * 1.4f, center)
}

/** Kleine schwebende Begleit-Drohne neben dem Helden. */
private fun DrawScope.drawDrone(p: RoadProjection, x: Float, time: Float, skin: HeroSkin) {
    val r = p.size(0.05f, 0f)
    val hover = sin(time * 6f + x * 10f) * r * 0.3f
    val center = Offset(p.x(x, 0f), p.y(0f) - p.size(0.32f, 0f) + hover)
    drawOval(Color.Black.copy(alpha = 0.3f), Offset(center.x - r, p.y(0f) - r * 0.2f), Size(r * 2f, r * 0.4f))
    drawCircle(skin.body.copy(alpha = 0.25f), r * 1.8f, center)
    drawCircle(Color(0xFF2A2E40), r, center)
    drawCircle(skin.bullet, r * 0.45f, center)
    // Rotoren
    drawLine(Color.White.copy(alpha = 0.7f), center - Offset(r * 1.6f, r * 0.9f), center + Offset(-r * 0.4f, -r * 0.9f), strokeWidth = r * 0.25f)
    drawLine(Color.White.copy(alpha = 0.7f), center + Offset(r * 0.4f, -r * 0.9f), center + Offset(r * 1.6f, -r * 0.9f), strokeWidth = r * 0.25f)
}

/** Schild-Blase um den Helden; dicker je mehr Schilde. */
private fun DrawScope.drawShield(p: RoadProjection, x: Float, shields: Int, time: Float) {
    val w = p.size(0.2f, 0f)
    val center = Offset(p.x(x, 0f), p.y(0f) - w * 0.45f)
    val pulse = 0.85f + 0.15f * sin(time * 4f)
    drawCircle(ShieldColor.copy(alpha = 0.10f * pulse), w * 1.05f, center)
    drawCircle(ShieldColor.copy(alpha = 0.6f * pulse), w * 1.05f, center, style = Stroke(width = w * 0.04f * shields))
}

private fun DrawScope.drawHero(p: RoadProjection, x: Float, time: Float, skin: HeroSkin) {
    val w = p.size(0.2f, 0f)
    val bob = sin(time * 14f) * w * 1.3f * 0.03f
    drawHeroFigure(Offset(p.x(x, 0f), p.y(0f) + w * 1.3f * 0.35f), w, skin, bob)
}

/** Der Held; [bottomCenter] = Fußpunkt, [w] = Körperbreite. Auch für die Shop-Vorschau. */
fun DrawScope.drawHeroFigure(bottomCenter: Offset, w: Float, skin: HeroSkin, bob: Float = 0f) {
    val h = w * 1.3f
    val cx = bottomCenter.x
    val bottom = bottomCenter.y
    val top = bottom - h + bob

    drawOval(Color.Black.copy(alpha = 0.4f), Offset(cx - w * 0.6f, bottom - w * 0.12f), Size(w * 1.2f, w * 0.24f))
    drawCircle(skin.body.copy(alpha = 0.18f), w * 0.9f, Offset(cx, top + h * 0.5f))
    // Körper
    drawRoundRect(skin.body, Offset(cx - w / 2f, top + h * 0.3f), Size(w, h * 0.7f), CornerRadius(w * 0.3f))
    drawRoundRect(
        Color.Black.copy(alpha = 0.2f),
        Offset(cx - w / 2f, top + h * 0.75f), Size(w, h * 0.25f), CornerRadius(w * 0.3f),
    )
    // Kopf
    drawCircle(skin.head, w * 0.26f, Offset(cx, top + h * 0.2f))
    drawRoundRect(skin.visor, Offset(cx - w * 0.2f, top + h * 0.12f), Size(w * 0.4f, w * 0.12f), CornerRadius(w * 0.06f))
    // Waffe
    drawRoundRect(Color(0xFF2A2E40), Offset(cx + w * 0.18f, top + h * 0.05f), Size(w * 0.16f, h * 0.55f), CornerRadius(w * 0.05f))
    drawCircle(skin.bullet, w * 0.06f, Offset(cx + w * 0.26f, top + h * 0.05f))
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
        is GateRushEffect.CoinPop -> {
            // Münze steigt vom Gegner auf und verblasst.
            val radius = size.width * 0.028f
            val base = Offset(p.x(effect.x, effect.z), p.y(effect.z) - p.size(0.3f, effect.z) - t * size.height * 0.1f)
            drawCoin(base, radius, alpha)
            val layout = textMeasurer.measure(
                "+${effect.amount}",
                TextStyle(color = CoinGold.copy(alpha = alpha), fontSize = (radius * 1.6f).toSp(), fontWeight = FontWeight.Black),
            )
            drawText(layout, topLeft = Offset(base.x + radius * 1.3f, base.y - layout.size.height / 2f))
        }
    }
}
