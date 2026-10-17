package com.adlessarcade.games.survivors

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import com.adlessarcade.ui.components.CoinGold
import com.adlessarcade.ui.components.drawCoin
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Sichtbare Breite in Welteinheiten. */
private const val VIEW_WIDTH = 10f
private const val DECOR_CELL = 2f

/** Farbe der Runen (Währung der Runen-Schmiede). */
val RuneViolet = Color(0xFFA78BFA)
const val RUNE_SYMBOL = "ᚱ"

/** Kamera, die dem Helden folgt: rechnet Welt- in Bildschirmkoordinaten um. */
class SurvivorsCamera(val width: Float, val height: Float, val cx: Float, val cy: Float) {
    val scale = width / VIEW_WIDTH
    fun x(wx: Float): Float = width / 2f + (wx - cx) * scale
    fun y(wy: Float): Float = height / 2f + (wy - cy) * scale
    fun at(wx: Float, wy: Float) = Offset(x(wx), y(wy))
    fun len(w: Float): Float = w * scale
    val halfWorldW get() = width / 2f / scale
    val halfWorldH get() = height / 2f / scale
}

/** Rein visuelle Effekte in Weltkoordinaten. */
sealed class SurvivorsFx(val x: Float, val y: Float, val lifetime: Float) {
    var age = 0f
    val progress: Float get() = (age / lifetime).coerceIn(0f, 1f)

    class Swing(x: Float, y: Float, val angle: Float, val range: Float, val fullCircle: Boolean, val evolved: Boolean) : SurvivorsFx(x, y, if (evolved) 0.3f else 0.22f)
    class Bolt(x: Float, y: Float, val radius: Float) : SurvivorsFx(x, y, 0.3f)
    class Nova(x: Float, y: Float, val radius: Float, val freeze: Boolean) : SurvivorsFx(x, y, 0.45f)
    class Arc(x: Float, y: Float, val x2: Float, val y2: Float) : SurvivorsFx(x, y, 0.25f)
    class Smash(x: Float, y: Float, val angle: Float, val range: Float, val ring: Boolean) : SurvivorsFx(x, y, if (ring) 0.4f else 0.25f)
    class ThornPulse(x: Float, y: Float, val radius: Float, val evolved: Boolean) : SurvivorsFx(x, y, 0.35f)
    class Blast(x: Float, y: Float, val radius: Float, val color: Color) : SurvivorsFx(x, y, 0.35f)
    /** Kleine Staubwolke, wenn ein normaler Gegner stirbt. */
    class Puff(x: Float, y: Float, val radius: Float) : SurvivorsFx(x, y, 0.3f)
    /** Einschlag eines Linienangriffs (Todesstrahl, Sturmangriff). */
    class Beam(x: Float, y: Float, val x2: Float, val y2: Float, val halfWidth: Float) : SurvivorsFx(x, y, 0.35f)
    /** [crit]: 0 normal, 1 Krit, 2 Superkrit. */
    class Number(x: Float, y: Float, val amount: Int, val crit: Int = 0, val kind: DamageKind = DamageKind.HIT) :
        SurvivorsFx(x, y, if (crit > 0 || kind == DamageKind.EXECUTE) 0.8f else 0.6f)
    class Text(x: Float, y: Float, val text: String, val color: Color) : SurvivorsFx(x, y, 1.4f)
    class CoinPop(x: Float, y: Float, val amount: Int) : SurvivorsFx(x, y, 1f)
    class RunePop(x: Float, y: Float, val amount: Int) : SurvivorsFx(x, y, 1.2f)

    /** Zahlen und Texte werden über allem gezeichnet, Waffeneffekte in der Verdecker-Ebene. */
    val isLabel: Boolean get() = this is Number || this is Text || this is CoinPop || this is RunePop
}

/** Gegner-Rand, nur sichtbar, wo Effekte oder Projektile über dem Gegner liegen. */
private val ENEMY_OUTLINE = Color(0xFFFF6B6B).copy(alpha = 0.55f)
/** Breite der Gegner-Outline in Welteinheiten – unabhängig von der Gegnergröße. */
private const val ENEMY_OUTLINE_WIDTH = 0.045f
private val HERO_OUTLINE = Color(0xFF67E8F9)

/** Zeichnet [block] in eine eigene Ebene, die beim Zurückschreiben mit [paint] verrechnet wird. */
private inline fun DrawScope.layer(paint: Paint = Paint(), block: DrawScope.() -> Unit) {
    drawIntoCanvas { it.saveLayer(Rect(Offset.Zero, size), paint) }
    block()
    drawIntoCanvas { it.restore() }
}

/** Zeichnet [block] als einfarbige Silhouette in [color] (eine Ebene für alle Formen darin). */
private inline fun DrawScope.silhouette(color: Color, block: DrawScope.() -> Unit) =
    layer(Paint().apply { colorFilter = ColorFilter.tint(color, BlendMode.SrcIn) }, block)

fun DrawScope.drawSurvivors(
    engine: SurvivorsEngine,
    effects: List<SurvivorsFx>,
    theme: SurvivorTheme,
    textMeasurer: TextMeasurer,
    damageFlash: Float,
) {
    val s = engine.state
    val cam = SurvivorsCamera(size.width, size.height, s.px, s.py)
    drawGround(cam, theme, s.time)

    // Heilige Aura unter allem anderen
    val aura = engine.auraRadius()
    if (aura > 0f) {
        val pulse = 0.85f + 0.15f * sin(s.time * 3f)
        val holy = engine.isEvolved(WeaponKind.AURA)
        val center = cam.at(s.px, s.py)
        val r = cam.len(aura)
        drawCircle(Color(0xFFFDE68A).copy(alpha = (if (holy) 0.2f else 0.12f) * pulse), r, center)
        drawCircle(Color(0xFFFDE68A).copy(alpha = 0.45f * pulse), r, center, style = Stroke(width = if (holy) 5f else 3f))
        // Lichtstrahlen: einer pro Vielfalt-Stufe (bei Heiligem Licht immer mindestens 6)
        val rays = (s.passives[PassiveKind.MULTI] ?: 0) + if (holy) 6 else 0
        for (i in 0 until rays) {
            val ang = s.time * 0.8f + i * 2f * PI.toFloat() / rays
            val dir = Offset(cos(ang), sin(ang))
            drawLine(Color(0xFFFEF3C7).copy(alpha = 0.35f * pulse), center + dir * (r * 0.25f), center + dir * r, strokeWidth = cam.len(0.12f))
        }
    }

    val margin = 2f
    fun visible(x: Float, y: Float) = abs(x - s.px) < cam.halfWorldW + margin && abs(y - s.py) < cam.halfWorldH + margin

    // Bodenflächen (brennender Boden, Ranken, Gravitationsbrunnen) und Vorwarnung kommender Meteore
    s.hazards.forEach { h ->
        if (visible(h.x, h.y)) drawHazard(cam, h, s.time)
    }
    val meteorRadius = engine.meteorRadius()
    s.pendingStrikes.forEach { p ->
        if (p.weapon != WeaponKind.FIREBALL || !visible(p.x, p.y)) return@forEach
        val k = 1f - (p.delay / SurvivorsEngine.METEOR_DELAY).coerceIn(0f, 1f)
        val at = cam.at(p.x, p.y)
        drawCircle(Color(0xFFF97316).copy(alpha = 0.15f + 0.25f * k), cam.len(meteorRadius), at)
        drawCircle(Color(0xFFFDBA74).copy(alpha = 0.8f), cam.len(meteorRadius), at, style = Stroke(width = 3f))
        // Meteor stürzt von oben rechts herab
        val rock = at + Offset(cam.len(3f), -cam.len(6f)) * (1f - k)
        drawLine(Color(0xFFFDE68A).copy(alpha = 0.6f), rock, rock + Offset(cam.len(0.9f), -cam.len(1.8f)), strokeWidth = cam.len(0.25f))
        drawCircle(Color(0xFFF97316), cam.len(0.3f), rock)
        drawCircle(Color(0xFFFDE68A), cam.len(0.15f), rock)
    }

    // Hindernisse (Friedhof) und Runenstein
    if (s.stage.hasObstacles) {
        SurvivorsObstacles.near(s.px, s.py, cam.halfWorldH + margin).forEach { if (visible(it.x, it.y)) drawObstacle(cam, it, theme) }
    }
    s.runeStone?.let { if (visible(it.x, it.y)) drawRuneStone(cam, it, s.time) }

    // Gegner nach y sortiert (weiter unten = weiter vorne)
    val enemies = s.enemies.filter { visible(it.x, it.y) }.sortedBy { it.y }
    enemies.forEach { drawEnemy(cam, it, theme, s.time) }

    // Verdecker (Projektile, Waffeneffekte) in eigener Ebene. Darin wird der Gegner-Rand nur dort eingeblendet,
    // wo ein Verdecker über dem Gegner liegt (SrcAtop) – frei stehende Gegner bleiben ohne Outline.
    layer {
        s.projectiles.forEach { if (visible(it.x, it.y)) drawProjectile(cam, it, s.time, engine.isEvolved(it.weapon)) }
        val soulVortex = engine.isEvolved(WeaponKind.BONE_ORBIT)
        engine.orbitPositions().forEach { (x, y) -> drawSkull(cam, x, y, s.time, soulVortex) }
        engine.shieldPositions().forEach { (x, y) -> drawShield(cam.at(x, y), cam.len(0.42f), s.time * 6f, glow = true) }
        effects.forEach { if (!it.isLabel) drawFx(cam, it, textMeasurer) }
        if (enemies.isNotEmpty()) layer(Paint().apply { blendMode = BlendMode.SrcAtop }) {
            silhouette(ENEMY_OUTLINE) {
                enemies.forEach { e ->
                    scale(1f + ENEMY_OUTLINE_WIDTH / e.radius, pivot = cam.at(e.x, e.y)) { drawEnemy(cam, e, theme, s.time, bodyOnly = true) }
                }
            }
            // Inneres ausstanzen: übrig bleibt nur der Rand
            layer(Paint().apply { blendMode = BlendMode.DstOut }) {
                enemies.forEach { drawEnemy(cam, it, theme, s.time, bodyOnly = true) }
            }
        }
    }

    s.gems.forEach { if (visible(it.x, it.y)) drawGem(cam, it, s.time) }
    s.pickups.forEach { if (visible(it.x, it.y)) drawPickup(cam, it, s.time) }

    // Warn-Indikatoren der Gegnerangriffe (füllen sich bis zum Einschlag) und gegnerische Geschosse
    s.enemyAttacks.forEach { drawAttackIndicator(cam, it, s.time) }
    s.enemyShots.forEach { if (visible(it.x, it.y)) drawEnemyShot(cam, it, s.time) }

    // Schadenszahlen und Texte, darüber der Held – er bleibt immer sichtbar.
    effects.forEach { if (it.isLabel) drawFx(cam, it, textMeasurer) }
    val blink = s.invulnerable > 0f && ((s.invulnerable * 20f).toInt() % 2 == 0)
    val hero = cam.at(s.px, s.py)
    val faceRight = s.faceX >= 0f
    drawOval(HERO_OUTLINE.copy(alpha = 0.7f), hero + Offset(-cam.len(0.5f), cam.len(0.28f)), Size(cam.len(1f), cam.len(0.3f)), style = Stroke(width = cam.len(0.05f)))
    if (!blink) {
        silhouette(HERO_OUTLINE.copy(alpha = 0.35f)) {
            scale(1.35f, pivot = hero) { drawHeroClass(s.heroClass, hero, cam.len(0.9f), s.time, faceRight, bodyOnly = true) }
        }
        silhouette(HERO_OUTLINE) {
            scale(1.16f, pivot = hero) { drawHeroClass(s.heroClass, hero, cam.len(0.9f), s.time, faceRight, bodyOnly = true) }
        }
        drawHeroClass(s.heroClass, hero, cam.len(0.9f), s.time, faceRight)
    }
    drawPlayerHp(cam, s, engine.stats.maxHp)
    drawEdgeIndicators(cam, s)

    // Vignette und Treffer-Blitz
    drawRect(
        Brush.radialGradient(
            listOf(Color.Transparent, theme.vignette.copy(alpha = 0.55f)),
            center = Offset(size.width / 2f, size.height / 2f),
            radius = max(size.width, size.height) * 0.7f,
        )
    )
    if (damageFlash > 0f) {
        drawRect(
            Brush.radialGradient(
                listOf(Color.Transparent, Color(0xFFFF3B3B).copy(alpha = 0.5f * damageFlash)),
                center = Offset(size.width / 2f, size.height / 2f),
                radius = max(size.width, size.height) * 0.7f,
            )
        )
    }
}

// --- Boden & Deko ---

private fun hash(cx: Int, cy: Int, salt: Int = 0): Int {
    var h = cx * 73856093 xor cy * 19349663 xor salt * 83492791
    h = h xor (h ushr 13)
    h *= 0x5bd1e995
    return h xor (h ushr 15)
}

private fun unit(h: Int, shift: Int): Float = ((h ushr shift) and 0xFF) / 255f

private fun DrawScope.drawGround(cam: SurvivorsCamera, theme: SurvivorTheme, time: Float) {
    drawRect(theme.ground)
    val minX = floor((cam.cx - cam.halfWorldW - DECOR_CELL) / DECOR_CELL).toInt()
    val maxX = floor((cam.cx + cam.halfWorldW + DECOR_CELL) / DECOR_CELL).toInt()
    val minY = floor((cam.cy - cam.halfWorldH - DECOR_CELL) / DECOR_CELL).toInt()
    val maxY = floor((cam.cy + cam.halfWorldH + DECOR_CELL) / DECOR_CELL).toInt()
    val cell = cam.len(DECOR_CELL)
    for (cx in minX..maxX) for (cy in minY..maxY) {
        val topLeft = cam.at(cx * DECOR_CELL, cy * DECOR_CELL)
        if ((cx + cy) % 2 == 0) drawRect(theme.groundAlt, topLeft, Size(cell, cell))
        val h = hash(cx, cy)
        val px = topLeft.x + unit(h, 0) * cell
        val py = topLeft.y + unit(h, 8) * cell
        val variant = (h ushr 16) and 0xF
        drawDecor(theme, Offset(px, py), cam.scale, variant, time, h)
    }
}

private fun DrawScope.drawDecor(theme: SurvivorTheme, at: Offset, unit: Float, variant: Int, time: Float, h: Int) {
    when (theme.decor) {
        DecorStyle.MEADOW -> when (variant) {
            in 0..5 -> grassTuft(at, unit, theme.detail.copy(alpha = 0.55f))
            in 6..8 -> repeat(3) { i ->
                drawCircle(if (i == 1) Color.White else theme.accent, unit * 0.06f, at + Offset(i * unit * 0.15f, (i % 2) * unit * 0.1f))
            }
            9 -> tree(at, unit, Color(0xFF14532D), Color(0xFF166534))
            10 -> drawOval(Color(0xFF6B7280), at, Size(unit * 0.45f, unit * 0.3f))
            else -> Unit
        }
        DecorStyle.FROST -> when (variant) {
            in 0..4 -> drawOval(Color.White.copy(alpha = 0.18f), at, Size(unit * 0.9f, unit * 0.35f))
            in 5..7 -> crystal(at, unit * 0.35f, theme.detail)
            8 -> tree(at, unit, Color(0xFF1E3A5F), Color(0xFFE0F2FE))
            else -> Unit
        }
        DecorStyle.VOLCANO -> when (variant) {
            in 0..3 -> {
                val glow = 0.5f + 0.3f * sin(time * 2f + h)
                val crack = Path().apply {
                    moveTo(at.x, at.y)
                    lineTo(at.x + unit * 0.4f, at.y + unit * 0.15f)
                    lineTo(at.x + unit * 0.55f, at.y + unit * 0.5f)
                    lineTo(at.x + unit * 0.9f, at.y + unit * 0.6f)
                }
                drawPath(crack, theme.detail.copy(alpha = glow), style = Stroke(width = unit * 0.07f))
            }
            in 4..6 -> drawOval(Color(0xFF1C0F0B), at, Size(unit * 0.55f, unit * 0.35f))
            7 -> drawCircle(theme.accent.copy(alpha = 0.6f + 0.4f * sin(time * 5f + h)), unit * 0.05f, at)
            else -> Unit
        }
        DecorStyle.GRAVEYARD -> when (variant) {
            // Flach und blass, damit die Deko nicht mit den festen Hindernissen verwechselt wird
            in 0..2 -> gravestone(at, unit * 0.75f, theme.detail.copy(alpha = 0.35f))
            3 -> deadTree(at, unit * 0.8f, Color(0xFF3F2E25).copy(alpha = 0.45f))
            in 4..6 -> drawCircle(theme.accent.copy(alpha = 0.08f), unit * 0.6f, at)
            else -> Unit
        }
        DecorStyle.NEON -> {
            drawLine(theme.detail.copy(alpha = 0.12f), at - Offset(unit, 0f), at + Offset(unit, 0f), strokeWidth = 1.5f)
            if (variant < 2) {
                val pulse = 0.6f + 0.4f * sin(time * 3f + h)
                drawRect(theme.accent.copy(alpha = 0.25f * pulse), at, Size(unit * 0.4f, unit * 0.4f))
                drawRect(theme.accent.copy(alpha = pulse), at, Size(unit * 0.4f, unit * 0.4f), style = Stroke(width = 2f))
            }
        }
    }
}

private fun DrawScope.grassTuft(at: Offset, unit: Float, color: Color) {
    for (i in -1..1) {
        drawLine(color, at, at + Offset(i * unit * 0.08f, -unit * 0.18f), strokeWidth = unit * 0.03f)
    }
}

private fun DrawScope.tree(at: Offset, unit: Float, dark: Color, light: Color) {
    drawOval(Color.Black.copy(alpha = 0.25f), at + Offset(-unit * 0.35f, unit * 0.25f), Size(unit * 0.7f, unit * 0.2f))
    drawRect(Color(0xFF5B3A1E), at + Offset(-unit * 0.05f, 0f), Size(unit * 0.1f, unit * 0.3f))
    drawCircle(dark, unit * 0.38f, at + Offset(0f, -unit * 0.15f))
    drawCircle(light.copy(alpha = 0.6f), unit * 0.18f, at + Offset(-unit * 0.1f, -unit * 0.28f))
}

private fun DrawScope.crystal(at: Offset, size: Float, color: Color) {
    val path = Path().apply {
        moveTo(at.x, at.y - size)
        lineTo(at.x + size * 0.4f, at.y)
        lineTo(at.x, at.y + size * 0.4f)
        lineTo(at.x - size * 0.4f, at.y)
        close()
    }
    drawPath(path, color.copy(alpha = 0.7f))
    drawPath(path, Color.White.copy(alpha = 0.6f), style = Stroke(width = 1.5f))
}

private fun DrawScope.gravestone(at: Offset, unit: Float, color: Color) {
    val w = unit * 0.32f
    val h = unit * 0.42f
    val cross = Color.Black.copy(alpha = 0.4f * color.alpha)
    drawRoundRect(color, at - Offset(w / 2f, h), Size(w, h), CornerRadius(w / 2f, w / 2f))
    drawLine(cross, at + Offset(0f, -h * 0.8f), at + Offset(0f, -h * 0.35f), strokeWidth = unit * 0.03f)
    drawLine(cross, at + Offset(-w * 0.25f, -h * 0.65f), at + Offset(w * 0.25f, -h * 0.65f), strokeWidth = unit * 0.03f)
}

private fun DrawScope.deadTree(at: Offset, unit: Float, brown: Color) {
    drawLine(brown, at, at + Offset(0f, -unit * 0.6f), strokeWidth = unit * 0.07f)
    drawLine(brown, at + Offset(0f, -unit * 0.35f), at + Offset(unit * 0.25f, -unit * 0.55f), strokeWidth = unit * 0.05f)
    drawLine(brown, at + Offset(0f, -unit * 0.45f), at + Offset(-unit * 0.2f, -unit * 0.62f), strokeWidth = unit * 0.05f)
}

// --- Objekte ---

private fun DrawScope.drawGem(cam: SurvivorsCamera, g: Gem, time: Float) {
    val color = when {
        g.value >= 20 -> Color(0xFFF43F5E)
        g.value >= 5 -> Color(0xFF4ADE80)
        else -> Color(0xFF60A5FA)
    }
    val r = cam.len(0.13f + min(0.1f, g.value * 0.004f))
    val c = cam.at(g.x, g.y) + Offset(0f, sin(time * 4f + g.x) * r * 0.2f)
    // Schwacher Leuchthof, damit Kristalle auf dunklem Boden nicht untergehen.
    drawCircle(color.copy(alpha = 0.12f), r * 1.3f, c + Offset(0f, -r * 0.3f))
    val h = r * 1.4f
    val path = Path().apply {
        moveTo(c.x, c.y - h)
        lineTo(c.x + h * 0.42f, c.y)
        lineTo(c.x, c.y + h * 0.42f)
        lineTo(c.x - h * 0.42f, c.y)
        close()
    }
    drawPath(path, color.copy(alpha = 0.85f))
    drawPath(path, Color.White.copy(alpha = 0.6f), style = Stroke(width = 1.5f))
}

private fun DrawScope.drawPickup(cam: SurvivorsCamera, p: Pickup, time: Float) {
    val c = cam.at(p.x, p.y)
    val u = cam.scale
    val bob = sin(time * 3f + p.id) * u * 0.05f
    val pulse = 0.5f + 0.5f * sin(time * 4f + p.id)
    drawCircle(Color.White.copy(alpha = 0.18f), u * 0.45f, c)
    drawCircle(Color.White.copy(alpha = 0.5f + 0.4f * pulse), u * (0.45f + 0.05f * pulse), c, style = Stroke(width = u * 0.04f))
    when (p.kind) {
        PickupKind.POTION -> {
            drawCircle(Color(0xFFEF4444), u * 0.2f, c + Offset(0f, bob))
            drawRect(Color(0xFFE5E7EB), c + Offset(-u * 0.06f, -u * 0.32f + bob), Size(u * 0.12f, u * 0.14f))
            drawCircle(Color.White.copy(alpha = 0.5f), u * 0.06f, c + Offset(-u * 0.07f, -u * 0.06f + bob))
        }
        PickupKind.CHEST -> {
            val w = u * 0.6f
            val h = u * 0.42f
            val tl = c + Offset(-w / 2f, -h / 2f + bob)
            drawCircle(CoinGold.copy(alpha = 0.25f + 0.15f * sin(time * 4f)), u * 0.55f, c)
            drawRoundRect(Color(0xFF92400E), tl, Size(w, h), CornerRadius(u * 0.06f))
            drawRect(CoinGold, tl + Offset(0f, h * 0.35f), Size(w, h * 0.12f))
            drawRect(CoinGold, tl + Offset(w * 0.42f, h * 0.3f), Size(w * 0.16f, h * 0.3f))
        }
        PickupKind.COIN -> drawCoin(c + Offset(0f, bob), u * 0.18f)
        PickupKind.RUNE -> {
            // Violetter Runen-Kristall mit Runenzeichen; größere Beute leuchtet stärker.
            val at = c + Offset(0f, bob)
            val h = u * (0.26f + 0.03f * p.amount.coerceAtMost(4))
            drawCircle(RuneViolet.copy(alpha = 0.2f + 0.15f * pulse), h * 1.2f, at)
            val gem = Path().apply {
                moveTo(at.x, at.y - h)
                lineTo(at.x + h * 0.6f, at.y)
                lineTo(at.x, at.y + h * 0.75f)
                lineTo(at.x - h * 0.6f, at.y)
                close()
            }
            drawPath(gem, RuneViolet)
            drawPath(gem, Color.White.copy(alpha = 0.7f), style = Stroke(width = 1.5f))
            drawLine(Color.White, at + Offset(0f, -h * 0.45f), at + Offset(0f, h * 0.4f), strokeWidth = u * 0.035f)
            drawLine(Color.White, at + Offset(0f, -h * 0.45f), at + Offset(h * 0.25f, -h * 0.2f), strokeWidth = u * 0.035f)
            drawLine(Color.White, at + Offset(h * 0.25f, -h * 0.2f), at + Offset(0f, 0f), strokeWidth = u * 0.035f)
        }
        PickupKind.MAGNET -> {
            drawArc(Color(0xFFEF4444), 180f, 180f, false, c + Offset(-u * 0.22f, -u * 0.22f + bob), Size(u * 0.44f, u * 0.44f), style = Stroke(width = u * 0.1f))
            drawRect(Color(0xFFE5E7EB), c + Offset(-u * 0.27f, bob), Size(u * 0.1f, u * 0.12f))
            drawRect(Color(0xFFE5E7EB), c + Offset(u * 0.17f, bob), Size(u * 0.1f, u * 0.12f))
        }
    }
}

private fun enemyColor(kind: EnemyKind): Color = when (kind) {
    EnemyKind.SLIME -> Color(0xFF4ADE80)
    EnemyKind.BAT -> Color(0xFF8B5CF6)
    EnemyKind.SKELETON -> Color(0xFFE5E7EB)
    EnemyKind.GHOST -> Color(0xFFCBD5E1)
    EnemyKind.ORC -> Color(0xFF65A30D)
    EnemyKind.BOSS -> Color(0xFF78716C)
    EnemyKind.DEMON -> Color(0xFFDC2626)
    EnemyKind.ZOMBIE -> Color(0xFF84A98C)
    EnemyKind.ARCHER -> Color(0xFFF5F5F4)
    EnemyKind.WITCH -> Color(0xFF7C3AED)
    EnemyKind.GRAVE_GUARDIAN -> Color(0xFF64748B)
    EnemyKind.LICH -> Color(0xFF312E81)
}

// --- Friedhof: Hindernisse, Indikatoren, Geschosse ---

private val DangerRed = Color(0xFFF43F5E)
private val ObstacleOutline = Color(0xFF0B0B12)

private fun DrawScope.drawObstacle(cam: SurvivorsCamera, o: Obstacle, theme: SurvivorTheme) {
    val c = cam.at(o.x, o.y)
    val r = cam.len(o.radius)
    val stone = Color(0xFF64748B)
    val light = Color(0xFF94A3B8)
    val outline = Stroke(width = cam.len(0.05f))
    drawOval(Color.Black.copy(alpha = 0.45f), c + Offset(-r * 1.05f, r * 0.35f), Size(r * 2.1f, r * 0.8f))
    // Fester Sockel in Größe der Kollisionsfläche: zeigt, wo man nicht durchkommt
    drawCircle(Color(0xFF0F0E16).copy(alpha = 0.55f), r, c)
    drawCircle(Color(0xFF9CA3AF).copy(alpha = 0.35f), r, c, style = outline)
    when (o.kind) {
        ObstacleKind.CRYPT -> {
            // Gruft: Steinhaus mit Giebel und dunkler Tür
            val bodyTl = c + Offset(-r, -r * 0.7f)
            val bodySize = Size(r * 2f, r * 1.4f)
            drawRoundRect(stone, bodyTl, bodySize, CornerRadius(r * 0.1f))
            drawRoundRect(ObstacleOutline, bodyTl, bodySize, CornerRadius(r * 0.1f), style = outline)
            val roof = Path().apply {
                moveTo(c.x - r * 1.12f, c.y - r * 0.65f)
                lineTo(c.x, c.y - r * 1.35f)
                lineTo(c.x + r * 1.12f, c.y - r * 0.65f)
                close()
            }
            drawPath(roof, light)
            drawPath(roof, ObstacleOutline, style = outline)
            drawRoundRect(Color(0xFF111827), c + Offset(-r * 0.3f, -r * 0.2f), Size(r * 0.6f, r * 0.9f), CornerRadius(r * 0.3f, r * 0.3f))
            drawCircle(theme.accent.copy(alpha = 0.5f), r * 0.08f, c + Offset(0f, -r * 0.95f))
        }
        ObstacleKind.GRAVES -> {
            // Grabsteingruppe
            listOf(Offset(-0.45f, 0.1f), Offset(0.45f, 0.15f), Offset(0f, -0.25f)).forEach { off ->
                val p = c + Offset(off.x * r, off.y * r)
                val w = r * 0.6f
                val h = r * 0.85f
                drawRoundRect(light, p - Offset(w / 2f, h * 0.7f), Size(w, h), CornerRadius(w / 2f, w / 2f))
                drawRoundRect(ObstacleOutline, p - Offset(w / 2f, h * 0.7f), Size(w, h), CornerRadius(w / 2f, w / 2f), style = outline)
                drawLine(Color.Black.copy(alpha = 0.4f), p + Offset(0f, -h * 0.5f), p + Offset(0f, -h * 0.05f), strokeWidth = r * 0.06f)
            }
        }
        ObstacleKind.STATUE -> {
            // Engelsstatue auf Sockel
            drawRect(stone, c + Offset(-r * 0.7f, -r * 0.1f), Size(r * 1.4f, r * 0.7f))
            drawRect(ObstacleOutline, c + Offset(-r * 0.7f, -r * 0.1f), Size(r * 1.4f, r * 0.7f), style = outline)
            listOf(-1f, 1f).forEach { side ->
                val wing = Path().apply {
                    moveTo(c.x + side * r * 0.2f, c.y - r * 0.9f)
                    lineTo(c.x + side * r * 0.95f, c.y - r * 1.3f)
                    lineTo(c.x + side * r * 0.6f, c.y - r * 0.45f)
                    close()
                }
                drawPath(wing, Color(0xFFCBD5E1))
                drawPath(wing, ObstacleOutline, style = outline)
            }
            drawRoundRect(light, c + Offset(-r * 0.3f, -r * 1.1f), Size(r * 0.6f, r * 1.05f), CornerRadius(r * 0.25f))
            drawRoundRect(ObstacleOutline, c + Offset(-r * 0.3f, -r * 1.1f), Size(r * 0.6f, r * 1.05f), CornerRadius(r * 0.25f), style = outline)
            drawCircle(light, r * 0.25f, c + Offset(0f, -r * 1.25f))
            drawCircle(ObstacleOutline, r * 0.25f, c + Offset(0f, -r * 1.25f), style = outline)
        }
        ObstacleKind.DEAD_TREE -> {
            val brown = Color(0xFF57412F)
            val rim = cam.len(0.1f)
            val trunk = listOf(
                Triple(c, c + Offset(0f, -r * 1.8f), r * 0.3f),
                Triple(c + Offset(0f, -r * 1.1f), c + Offset(r * 0.8f, -r * 1.7f), r * 0.18f),
                Triple(c + Offset(0f, -r * 1.35f), c + Offset(-r * 0.7f, -r * 1.95f), r * 0.16f),
            )
            drawCircle(Color(0xFF292524), r * 0.55f, c)
            // Dunkle, breitere Linie darunter ergibt die Kontur
            trunk.forEach { (a, b, w) -> drawLine(ObstacleOutline, a, b, strokeWidth = w + rim) }
            trunk.forEach { (a, b, w) -> drawLine(brown, a, b, strokeWidth = w) }
        }
    }
}

private fun DrawScope.drawAttackIndicator(cam: SurvivorsCamera, a: EnemyAttack, time: Float) {
    val k = 1f - (a.delay / a.maxDelay).coerceIn(0f, 1f)
    val blink = 0.75f + 0.25f * sin(time * 18f)
    when (a.shape) {
        AttackShape.CIRCLE -> {
            val c = cam.at(a.x, a.y)
            val r = cam.len(a.radius)
            drawCircle(DangerRed.copy(alpha = 0.14f), r, c)
            drawCircle(DangerRed.copy(alpha = 0.35f), r * k, c)
            drawCircle(DangerRed.copy(alpha = 0.9f * blink), r, c, style = Stroke(width = cam.len(0.06f)))
        }
        AttackShape.LINE -> {
            val from = cam.at(a.x, a.y)
            val to = cam.at(a.x2, a.y2)
            val width = cam.len(a.radius * 2f)
            drawLine(DangerRed.copy(alpha = 0.16f), from, to, strokeWidth = width)
            drawLine(DangerRed.copy(alpha = 0.4f), from, from + (to - from) * k, strokeWidth = width)
            drawLine(DangerRed.copy(alpha = 0.9f * blink), from, to, strokeWidth = cam.len(0.05f))
        }
    }
}

private fun DrawScope.drawEnemyShot(cam: SurvivorsCamera, s: EnemyShot, time: Float) {
    val c = cam.at(s.x, s.y)
    if (s.soul) {
        val r = cam.len(s.radius)
        drawCircle(Color(0xFF22D3EE).copy(alpha = 0.3f), r * 1.6f, c)
        drawCircle(Color(0xFFA5F3FC), r, c)
        drawCircle(Color.White, r * 0.45f, c + Offset(sin(time * 9f) * r * 0.15f, 0f))
    } else {
        // Knochenpfeil
        val angle = atan2(s.vy, s.vx)
        val dir = Offset(cos(angle), sin(angle))
        val len = cam.len(0.55f)
        drawLine(Color(0xFFF5F5F4), c - dir * len, c, strokeWidth = cam.len(0.07f))
        drawCircle(DangerRed, cam.len(0.1f), c)
    }
}

/** [bodyOnly]: nur die Figur ohne Schatten, Leuchten und Anzeigen – für die Outline-Silhouette. */
private fun DrawScope.drawEnemy(cam: SurvivorsCamera, e: Enemy, theme: SurvivorTheme, time: Float, bodyOnly: Boolean = false) {
    val c = cam.at(e.x, e.y)
    val r = cam.len(e.radius)
    var color = lerp(enemyColor(e.kind), theme.enemyTint, theme.tintAmount)
    if (e.slowTimer > 0f) color = lerp(color, Color(0xFF7DD3FC), 0.5f)
    if (e.freezeTimer > 0f) color = lerp(color, Color(0xFFE0F2FE), 0.65f)
    else if (e.poisonTimer > 0f) color = lerp(color, Color(0xFF4ADE80), 0.45f)
    color = lerp(color, Color.White, e.hitFlash * 0.7f)

    if (!bodyOnly) drawOval(Color.Black.copy(alpha = 0.3f), c + Offset(-r, r * 0.6f), Size(r * 2f, r * 0.5f))
    if ((e.elite || e.isBoss) && !bodyOnly) {
        val glow = when (e.kind) {
            EnemyKind.DEMON -> Color(0xFFF97316)
            EnemyKind.LICH -> Color(0xFF22D3EE)
            else -> CoinGold
        }
        drawCircle(glow.copy(alpha = 0.25f + 0.1f * sin(time * 5f)), r * 1.35f, c)
    }
    when (e.kind) {
        EnemyKind.SLIME -> {
            val squish = 1f + 0.08f * sin(time * 6f + e.phase)
            drawOval(color, c + Offset(-r * squish, -r * 0.8f / squish), Size(r * 2f * squish, r * 1.6f / squish))
            eyes(c + Offset(0f, -r * 0.2f), r)
        }
        EnemyKind.BAT -> {
            val flap = sin(time * 14f + e.phase) * r * 0.5f
            listOf(-1f, 1f).forEach { side ->
                val wing = Path().apply {
                    moveTo(c.x, c.y)
                    lineTo(c.x + side * r * 1.6f, c.y - r * 0.4f - flap)
                    lineTo(c.x + side * r * 1.1f, c.y + r * 0.3f)
                    close()
                }
                drawPath(wing, lerp(color, Color.Black, 0.3f))
            }
            drawCircle(color, r * 0.7f, c)
            eyes(c, r * 0.7f, Color(0xFFFDE047))
        }
        EnemyKind.SKELETON -> {
            drawRoundRect(color, c + Offset(-r * 0.4f, -r * 0.1f), Size(r * 0.8f, r), CornerRadius(r * 0.2f))
            for (i in 0..2) {
                drawLine(Color.Black.copy(alpha = 0.35f), c + Offset(-r * 0.35f, r * (0.1f + i * 0.25f)), c + Offset(r * 0.35f, r * (0.1f + i * 0.25f)), strokeWidth = r * 0.06f)
            }
            drawCircle(color, r * 0.55f, c + Offset(0f, -r * 0.55f))
            eyes(c + Offset(0f, -r * 0.55f), r * 0.55f, Color(0xFF111111))
        }
        EnemyKind.GHOST -> {
            val g = color.copy(alpha = 0.75f)
            val wave = sin(time * 5f + e.phase) * r * 0.15f
            val body = Path().apply {
                moveTo(c.x - r, c.y + r * 0.6f)
                lineTo(c.x - r, c.y - r * 0.2f)
                quadraticTo(c.x - r, c.y - r * 1.1f, c.x, c.y - r * 1.1f)
                quadraticTo(c.x + r, c.y - r * 1.1f, c.x + r, c.y - r * 0.2f)
                lineTo(c.x + r, c.y + r * 0.6f)
                lineTo(c.x + r * 0.5f, c.y + r * 0.3f + wave)
                lineTo(c.x, c.y + r * 0.6f - wave)
                lineTo(c.x - r * 0.5f, c.y + r * 0.3f + wave)
                close()
            }
            drawPath(body, g)
            eyes(c + Offset(0f, -r * 0.4f), r, Color(0xFF1E1B4B))
        }
        EnemyKind.ORC -> {
            drawRoundRect(color, c + Offset(-r, -r), Size(r * 2f, r * 1.9f), CornerRadius(r * 0.6f))
            eyes(c + Offset(0f, -r * 0.35f), r, Color(0xFFDC2626))
            drawLine(Color.White, c + Offset(-r * 0.35f, r * 0.15f), c + Offset(-r * 0.3f, -r * 0.05f), strokeWidth = r * 0.12f)
            drawLine(Color.White, c + Offset(r * 0.35f, r * 0.15f), c + Offset(r * 0.3f, -r * 0.05f), strokeWidth = r * 0.12f)
        }
        EnemyKind.BOSS -> {
            drawRoundRect(color, c + Offset(-r, -r), Size(r * 2f, r * 2f), CornerRadius(r * 0.7f))
            drawRoundRect(lerp(color, Color.Black, 0.25f), c + Offset(-r, r * 0.3f), Size(r * 2f, r * 0.7f), CornerRadius(r * 0.5f))
            eyes(c + Offset(0f, -r * 0.35f), r, Color(0xFFF59E0B))
            // Keule
            drawLine(Color(0xFF5B3A1E), c + Offset(r * 0.8f, r * 0.2f), c + Offset(r * 1.5f, -r * 0.9f), strokeWidth = r * 0.25f)
            drawCircle(Color(0xFF44403C), r * 0.35f, c + Offset(r * 1.5f, -r * 0.9f))
        }
        EnemyKind.DEMON -> {
            drawCircle(Color(0xFFF97316).copy(alpha = 0.2f), r * 1.6f, c)
            drawRoundRect(color, c + Offset(-r, -r), Size(r * 2f, r * 2f), CornerRadius(r * 0.8f))
            listOf(-1f, 1f).forEach { side ->
                val horn = Path().apply {
                    moveTo(c.x + side * r * 0.4f, c.y - r * 0.8f)
                    lineTo(c.x + side * r * 1.1f, c.y - r * 1.7f)
                    lineTo(c.x + side * r * 0.75f, c.y - r * 0.6f)
                    close()
                }
                drawPath(horn, Color(0xFF1C1917))
            }
            eyes(c + Offset(0f, -r * 0.3f), r, Color(0xFFFDE047))
        }
        EnemyKind.ZOMBIE -> {
            // Wankender Zombie mit ausgestreckten Armen
            val sway = sin(time * 3f + e.phase) * r * 0.12f
            drawRoundRect(lerp(color, Color(0xFF3F3F46), 0.35f), c + Offset(-r * 0.55f + sway, -r * 0.2f), Size(r * 1.1f, r * 1.1f), CornerRadius(r * 0.25f))
            drawLine(color, c + Offset(-r * 0.4f + sway, 0f), c + Offset(-r * 1.05f + sway, -r * 0.15f), strokeWidth = r * 0.22f)
            drawLine(color, c + Offset(r * 0.4f + sway, 0f), c + Offset(r * 1.05f + sway, -r * 0.1f), strokeWidth = r * 0.22f)
            drawCircle(color, r * 0.55f, c + Offset(sway, -r * 0.6f))
            eyes(c + Offset(sway, -r * 0.6f), r * 0.55f, Color(0xFFDC2626))
        }
        EnemyKind.ARCHER -> {
            drawRoundRect(color, c + Offset(-r * 0.4f, -r * 0.1f), Size(r * 0.8f, r), CornerRadius(r * 0.2f))
            drawCircle(color, r * 0.55f, c + Offset(0f, -r * 0.55f))
            eyes(c + Offset(0f, -r * 0.55f), r * 0.55f, DangerRed)
            // Bogen in Zielrichtung
            val aim = if (e.windup > 0f) atan2(e.dashVy, e.dashVx) else 0f
            val bowCenter = c + Offset(cos(aim) * r * 0.7f, sin(aim) * r * 0.7f)
            drawArc(
                Color(0xFF92400E), aim * 180f / PI.toFloat() - 70f, 140f, false,
                bowCenter - Offset(r * 0.6f, r * 0.6f), Size(r * 1.2f, r * 1.2f), style = Stroke(width = r * 0.14f),
            )
        }
        EnemyKind.WITCH -> {
            // Hexe mit Spitzhut; leuchtet beim Zaubern
            if (e.windup > 0f) drawCircle(Color(0xFFC084FC).copy(alpha = 0.4f), r * 1.4f, c)
            val robe = Path().apply {
                moveTo(c.x - r * 0.75f, c.y + r * 0.7f)
                lineTo(c.x, c.y - r * 0.5f)
                lineTo(c.x + r * 0.75f, c.y + r * 0.7f)
                close()
            }
            drawPath(robe, color)
            drawCircle(Color(0xFFA3E635), r * 0.38f, c + Offset(0f, -r * 0.55f))
            val hat = Path().apply {
                moveTo(c.x - r * 0.6f, c.y - r * 0.7f)
                lineTo(c.x + r * 0.15f, c.y - r * 1.7f)
                lineTo(c.x + r * 0.6f, c.y - r * 0.7f)
                close()
            }
            drawPath(hat, Color(0xFF1E1B4B))
            eyes(c + Offset(0f, -r * 0.55f), r * 0.38f, Color(0xFFFDE047))
        }
        EnemyKind.GRAVE_GUARDIAN -> {
            // Steinerner Wächter mit Schild-Schultern; glüht beim Ausholen
            if (e.windup > 0f || e.dashTime > 0f) drawCircle(DangerRed.copy(alpha = 0.3f), r * 1.5f, c)
            drawRoundRect(color, c + Offset(-r, -r * 0.9f), Size(r * 2f, r * 1.9f), CornerRadius(r * 0.35f))
            drawRoundRect(lerp(color, Color.Black, 0.3f), c + Offset(-r * 1.25f, -r * 0.95f), Size(r * 0.55f, r * 0.8f), CornerRadius(r * 0.15f))
            drawRoundRect(lerp(color, Color.Black, 0.3f), c + Offset(r * 0.7f, -r * 0.95f), Size(r * 0.55f, r * 0.8f), CornerRadius(r * 0.15f))
            for (i in 0..2) drawLine(Color(0xFF334155), c + Offset(-r * 0.7f, r * (0.1f + i * 0.25f)), c + Offset(r * 0.7f, r * (0.1f + i * 0.25f)), strokeWidth = r * 0.06f)
            eyes(c + Offset(0f, -r * 0.4f), r, Color(0xFF22D3EE))
        }
        EnemyKind.LICH -> {
            // Lichkönig: dunkle Robe, Krone, schwebende Seelen
            for (i in 0 until 4) {
                val a = time * 1.5f + i * PI.toFloat() / 2f
                drawCircle(Color(0xFF67E8F9).copy(alpha = 0.6f), r * 0.15f, c + Offset(cos(a) * r * 1.4f, sin(a) * r * 1.4f * 0.6f))
            }
            if (e.windup > 0f) drawCircle(Color(0xFF22D3EE).copy(alpha = 0.3f), r * 1.5f, c)
            val robe = Path().apply {
                moveTo(c.x - r, c.y + r * 0.9f)
                lineTo(c.x - r * 0.55f, c.y - r * 0.6f)
                lineTo(c.x + r * 0.55f, c.y - r * 0.6f)
                lineTo(c.x + r, c.y + r * 0.9f)
                close()
            }
            drawPath(robe, color)
            drawCircle(Color(0xFFE7E5E4), r * 0.45f, c + Offset(0f, -r * 0.75f))
            eyes(c + Offset(0f, -r * 0.75f), r * 0.45f, Color(0xFF22D3EE))
            val crown = Path().apply {
                moveTo(c.x - r * 0.45f, c.y - r * 1.1f)
                lineTo(c.x - r * 0.45f, c.y - r * 1.45f)
                lineTo(c.x - r * 0.2f, c.y - r * 1.25f)
                lineTo(c.x, c.y - r * 1.55f)
                lineTo(c.x + r * 0.2f, c.y - r * 1.25f)
                lineTo(c.x + r * 0.45f, c.y - r * 1.45f)
                lineTo(c.x + r * 0.45f, c.y - r * 1.1f)
                close()
            }
            drawPath(crown, CoinGold)
        }
    }
    if (bodyOnly) return

    if (e.kind == EnemyKind.ARCHER && e.windup > 0f) {
        // Ziellinie des Knochenschützen
        val dir = Offset(e.dashVx, e.dashVy)
        val k = 1f - (e.windup / SurvivorsEnemyAttacks.ARCHER_AIM_TIME).coerceIn(0f, 1f)
        drawLine(DangerRed.copy(alpha = 0.25f + 0.5f * k), c, c + dir * cam.len(9f), strokeWidth = cam.len(0.05f + 0.05f * k))
    }

    if (e.freezeTimer > 0f) {
        // Eisblock um eingefrorene Gegner
        drawRoundRect(Color(0xFFBAE6FD).copy(alpha = 0.35f), c + Offset(-r * 1.15f, -r * 1.2f), Size(r * 2.3f, r * 2.2f), CornerRadius(r * 0.3f))
        drawRoundRect(Color.White.copy(alpha = 0.7f), c + Offset(-r * 1.15f, -r * 1.2f), Size(r * 2.3f, r * 2.2f), CornerRadius(r * 0.3f), style = Stroke(width = max(2f, r * 0.08f)))
    }
    if (e.stunTimer > 0f) {
        // Betäubt/gefesselt: kreisende Sterne über dem Kopf
        for (i in 0 until 3) {
            val a = time * 6f + i * 2.09f
            drawCircle(Color(0xFFFDE047), max(2f, r * 0.12f), c + Offset(cos(a) * r * 0.6f, -r * 1.1f + sin(a) * r * 0.2f))
        }
    }
    if (e.elite || e.isBoss) {
        val barW = r * 2.4f
        val barH = max(4f, r * 0.15f)
        val tl = c + Offset(-barW / 2f, -r * 1.55f - barH)
        drawRoundRect(Color.Black.copy(alpha = 0.6f), tl, Size(barW, barH), CornerRadius(barH / 2f))
        drawRoundRect(if (e.isBoss) Color(0xFFF43F5E) else CoinGold, tl, Size(barW * (e.hp / e.maxHp).coerceIn(0f, 1f), barH), CornerRadius(barH / 2f))
    }
}

private fun DrawScope.eyes(center: Offset, r: Float, pupil: Color = Color(0xFF111111)) {
    listOf(-0.3f, 0.3f).forEach { dx ->
        drawCircle(Color.White, r * 0.22f, center + Offset(dx * r, 0f))
        drawCircle(pupil, r * 0.11f, center + Offset(dx * r, r * 0.04f))
    }
}

private fun DrawScope.drawProjectile(cam: SurvivorsCamera, p: Projectile, time: Float, evolved: Boolean) {
    val c = cam.at(p.x, p.y)
    val angle = atan2(p.vy, p.vx)
    val u = cam.len(p.radius / defaultRadius(p.weapon))
    if (evolved) {
        // Entwickelte Geschosse leuchten
        val glow = if (p.weapon == WeaponKind.ARROWS) Color(0xFF5EEAD4) else Color(0xFFE2E8F0)
        drawCircle(glow.copy(alpha = 0.3f), u * 0.3f, c)
    }
    when (p.weapon) {
        WeaponKind.FIREBALL -> {
            drawCircle(Color(0xFFF97316).copy(alpha = 0.35f), u * 0.4f, c)
            drawCircle(Color(0xFFFB923C), u * 0.24f, c)
            drawCircle(Color(0xFFFDE68A), u * 0.12f, c)
        }
        WeaponKind.ARROWS -> {
            val tail = c - Offset(cos(angle), sin(angle)) * (u * 0.5f)
            drawLine(Color(0xFF92400E), tail, c, strokeWidth = u * 0.05f)
            drawCircle(Color(0xFFE5E7EB), u * 0.06f, c)
            drawLine(Color(0xFF4ADE80), tail, tail - Offset(cos(angle + 0.5f), sin(angle + 0.5f)) * (u * 0.12f), strokeWidth = u * 0.04f)
        }
        WeaponKind.DAGGERS -> {
            rotate(angle * 180f / PI.toFloat() + 90f + time * 900f, c) {
                drawRect(Color(0xFFCBD5E1), c - Offset(u * 0.04f, u * 0.18f), Size(u * 0.08f, u * 0.28f))
                drawRect(Color(0xFF78350F), c + Offset(-u * 0.05f, u * 0.1f), Size(u * 0.1f, u * 0.08f))
            }
        }
        WeaponKind.ARCANE -> {
            val pulse = 0.85f + 0.15f * sin(time * 20f + p.id)
            drawCircle(Color(0xFFA855F7).copy(alpha = 0.35f), u * 0.4f * pulse, c)
            drawCircle(Color(0xFFC084FC), u * 0.22f, c)
            drawCircle(Color.White, u * 0.1f, c)
        }
        WeaponKind.WIND -> {
            val r = cam.len(p.radius)
            val fade = (p.life / 0.4f).coerceAtMost(1f)
            if (p.pullRadius > 0f) {
                // Orkan: wirbelnder Sturm
                for (i in 0 until 4) {
                    val rr = r * (0.35f + i * 0.2f)
                    drawArc(
                        Color(0xFFE0F2FE).copy(alpha = (0.55f - i * 0.1f) * fade), time * 900f + i * 70f, 220f, false,
                        c - Offset(rr, rr * 0.7f), Size(rr * 2f, rr * 1.4f), style = Stroke(width = cam.len(0.06f)),
                    )
                }
            } else {
                // Windklinge: rotierende Sichel
                rotate(time * 720f + p.id * 37f, c) {
                    drawArc(Color(0xFFBAE6FD).copy(alpha = 0.85f * fade), 0f, 140f, false, c - Offset(r, r), Size(r * 2f, r * 2f),
                        style = Stroke(width = cam.len(0.07f)))
                    drawArc(Color.White.copy(alpha = 0.7f * fade), 180f, 140f, false, c - Offset(r * 0.7f, r * 0.7f), Size(r * 1.4f, r * 1.4f),
                        style = Stroke(width = cam.len(0.05f)))
                }
            }
        }
        WeaponKind.SPEAR -> {
            val dir = Offset(cos(angle), sin(angle))
            val tail = c - dir * (u * 0.9f)
            drawLine(Color(0xFF92400E), tail, c, strokeWidth = u * 0.07f)
            val tip = Path().apply {
                moveTo(c.x + dir.x * u * 0.25f, c.y + dir.y * u * 0.25f)
                lineTo(c.x - dir.y * u * 0.1f, c.y + dir.x * u * 0.1f)
                lineTo(c.x + dir.y * u * 0.1f, c.y - dir.x * u * 0.1f)
                close()
            }
            drawPath(tip, if (evolved) Color(0xFFFDE047) else Color(0xFFE5E7EB))
            drawCircle(Color(0xFF60A5FA).copy(alpha = 0.6f), u * 0.05f, c - dir * (u * 0.45f))
        }
        WeaponKind.SHARDS -> {
            rotate(time * 500f + p.id * 20f, c) {
                val s = u * 0.16f
                val diamond = Path().apply {
                    moveTo(c.x, c.y - s)
                    lineTo(c.x + s * 0.6f, c.y)
                    lineTo(c.x, c.y + s)
                    lineTo(c.x - s * 0.6f, c.y)
                    close()
                }
                drawPath(diamond, Color(0xFF67E8F9))
                drawPath(diamond, Color.White.copy(alpha = 0.8f), style = Stroke(width = max(1f, u * 0.02f)))
            }
        }
        WeaponKind.SHIELD -> drawShield(c, u * 0.35f, time * 12f, glow = false)
        else -> drawCircle(Color.White, u * 0.1f, c)
    }
}

/** Runder Schild (Schildwurf, Bollwerk). */
private fun DrawScope.drawShield(c: Offset, r: Float, spin: Float, glow: Boolean) {
    if (glow) drawCircle(Color(0xFF93C5FD).copy(alpha = 0.3f), r * 1.5f, c)
    rotate(spin * 57.3f, c) {
        drawCircle(Color(0xFF1D4ED8), r, c)
        drawCircle(Color(0xFFFACC15), r, c, style = Stroke(width = r * 0.2f))
        drawLine(Color(0xFFFACC15), c - Offset(r * 0.6f, 0f), c + Offset(r * 0.6f, 0f), strokeWidth = r * 0.18f)
        drawLine(Color(0xFFFACC15), c - Offset(0f, r * 0.6f), c + Offset(0f, r * 0.6f), strokeWidth = r * 0.18f)
    }
}

/** Bodenfläche: brennender Boden, Ranken oder Gravitationsbrunnen. */
private fun DrawScope.drawHazard(cam: SurvivorsCamera, h: Hazard, time: Float) {
    val center = cam.at(h.x, h.y)
    val r = cam.len(h.radius)
    val fade = (h.life / 0.5f).coerceAtMost(1f) * ((h.maxLife - h.life) / 0.25f).coerceIn(0f, 1f)
    when (h.kind) {
        HazardKind.FIRE -> {
            val flicker = 0.8f + 0.2f * sin(time * 18f + h.x * 3f)
            drawCircle(
                Brush.radialGradient(
                    listOf(Color(0xFFFDE047).copy(alpha = 0.5f * fade * flicker), Color(0xFFEA580C).copy(alpha = 0.35f * fade), Color.Transparent),
                    center = center,
                    radius = r,
                ),
                r,
                center,
            )
        }
        HazardKind.VINES -> {
            val green = if (h.evolved) Color(0xFF22C55E) else Color(0xFF4D7C0F)
            drawCircle(green.copy(alpha = 0.25f * fade), r, center)
            // Rankenarme mit Blättern
            for (i in 0 until 7) {
                val a = i * 0.9f + h.x
                val sway = sin(time * 3f + i) * 0.15f
                val end = center + Offset(cos(a + sway), sin(a + sway)) * (r * 0.95f)
                val mid = center + Offset(cos(a + 0.5f), sin(a + 0.5f)) * (r * 0.5f)
                val vine = Path().apply {
                    moveTo(center.x, center.y)
                    quadraticTo(mid.x, mid.y, end.x, end.y)
                }
                drawPath(vine, Color(0xFF65A30D).copy(alpha = 0.85f * fade), style = Stroke(width = cam.len(0.06f)))
                drawCircle(Color(0xFF86EFAC).copy(alpha = 0.9f * fade), cam.len(0.07f), mid)
            }
            if (h.evolved) drawCircle(Color(0xFFF472B6).copy(alpha = 0.8f * fade), cam.len(0.09f), center)
        }
        HazardKind.GRAVITY -> {
            drawCircle(
                Brush.radialGradient(
                    listOf(Color.Black.copy(alpha = 0.85f * fade), Color(0xFF6D28D9).copy(alpha = 0.4f * fade), Color.Transparent),
                    center = center,
                    radius = r,
                ),
                r,
                center,
            )
            // Einströmende Spiralarme
            for (i in 0 until 3) {
                rotate(-time * 240f + i * 120f, center) {
                    drawArc(
                        Color(0xFFC4B5FD).copy(alpha = 0.6f * fade), 0f, 160f, false,
                        center - Offset(r * 0.75f, r * 0.75f), Size(r * 1.5f, r * 1.5f), style = Stroke(width = cam.len(0.05f)),
                    )
                }
            }
            drawCircle(if (h.evolved) Color.Black else Color(0xFF1E1B4B), cam.len(if (h.evolved) 0.45f else 0.25f), center)
            if (h.evolved) drawCircle(Color(0xFFE879F9).copy(alpha = 0.8f * fade), cam.len(0.45f), center, style = Stroke(width = cam.len(0.05f)))
        }
    }
}

/** Projektilradius ohne „Reichweite“ – damit größere Projektile auch größer gezeichnet werden. */
private fun defaultRadius(weapon: WeaponKind): Float = when (weapon) {
    WeaponKind.FIREBALL -> 0.3f
    WeaponKind.ARROWS -> 0.2f
    WeaponKind.DAGGERS -> 0.18f
    WeaponKind.ARCANE -> 0.22f
    WeaponKind.SPEAR -> 0.25f
    WeaponKind.SHARDS -> 0.18f
    WeaponKind.SHIELD -> 0.35f
    else -> 0.2f
}

private fun DrawScope.drawSkull(cam: SurvivorsCamera, x: Float, y: Float, time: Float, soulVortex: Boolean) {
    val c = cam.at(x, y)
    val r = cam.len(0.22f)
    if (soulVortex) {
        // Seelenwirbel: geisterhafte Schweife
        drawCircle(Color(0xFF5EEAD4).copy(alpha = 0.18f), r * 2.4f, c)
        drawCircle(Color(0xFF5EEAD4).copy(alpha = 0.35f + 0.15f * sin(time * 10f + x)), r * 1.7f, c)
    }
    drawCircle(Color(0xFFA78BFA).copy(alpha = 0.3f), r * 1.6f, c)
    drawCircle(Color(0xFFF5F5F4), r, c)
    drawRect(Color(0xFFF5F5F4), c + Offset(-r * 0.55f, r * 0.4f), Size(r * 1.1f, r * 0.5f))
    drawCircle(Color(0xFF1C1917), r * 0.25f, c + Offset(-r * 0.35f, -r * 0.05f))
    drawCircle(Color(0xFF1C1917), r * 0.25f, c + Offset(r * 0.35f, -r * 0.05f))
}

private fun DrawScope.drawPlayerHp(cam: SurvivorsCamera, s: SurvivorsState, maxHp: Float) {
    val w = cam.len(1.1f)
    val h = max(6f, cam.len(0.12f))
    val tl = cam.at(s.px, s.py) + Offset(-w / 2f, cam.len(0.65f))
    val border = max(2f, cam.len(0.025f))
    drawRoundRect(Color.Black.copy(alpha = 0.8f), tl, Size(w, h), CornerRadius(h / 2f))
    drawRoundRect(Color(0xFFEF4444), tl, Size(w * (s.hp / maxHp).coerceIn(0f, 1f), h), CornerRadius(h / 2f))
    drawRoundRect(Color.White.copy(alpha = 0.9f), tl, Size(w, h), CornerRadius(h / 2f), style = Stroke(width = border))
}

/**
 * Die Heldenfigur einer Klasse – auch für Klassenwahl und Shop-Vorschau.
 * [bodyOnly]: ohne Schatten und Leuchten – für die Outline-Silhouette.
 */
fun DrawScope.drawHeroClass(heroClass: HeroClass, center: Offset, size: Float, time: Float, faceRight: Boolean = true, bodyOnly: Boolean = false) {
    val r = size / 2f
    val bob = sin(time * 8f) * r * 0.05f
    val c = center + Offset(0f, bob)
    val dir = if (faceRight) 1f else -1f
    if (!bodyOnly) drawOval(Color.Black.copy(alpha = 0.3f), center + Offset(-r * 0.8f, r * 0.75f), Size(r * 1.6f, r * 0.4f))
    when (heroClass) {
        HeroClass.KNIGHT -> {
            drawRoundRect(Color(0xFF1D4ED8), c + Offset(-r * 0.75f, -r * 0.2f), Size(r * 1.5f, r * 0.95f), CornerRadius(r * 0.3f)) // Umhang
            drawRoundRect(Color(0xFF9CA3AF), c + Offset(-r * 0.55f, -r * 0.3f), Size(r * 1.1f, r * 1f), CornerRadius(r * 0.35f))
            drawCircle(Color(0xFFD1D5DB), r * 0.45f, c + Offset(0f, -r * 0.6f))
            drawRect(Color(0xFF1F2937), c + Offset(-r * 0.3f, -r * 0.65f), Size(r * 0.6f, r * 0.12f)) // Visier
            drawRoundRect(Color(0xFFB45309), c + Offset(-dir * r * 0.95f - r * 0.3f, -r * 0.2f), Size(r * 0.6f, r * 0.75f), CornerRadius(r * 0.2f)) // Schild
            drawLine(Color(0xFFE5E7EB), c + Offset(dir * r * 0.6f, r * 0.2f), c + Offset(dir * r * 1.2f, -r * 0.7f), strokeWidth = r * 0.12f) // Schwert
        }
        HeroClass.MAGE -> {
            val robe = Path().apply {
                moveTo(c.x, c.y - r * 0.4f)
                lineTo(c.x + r * 0.7f, c.y + r * 0.6f)
                lineTo(c.x - r * 0.7f, c.y + r * 0.6f)
                close()
            }
            drawPath(robe, Color(0xFF7C3AED))
            drawCircle(Color(0xFFFDE68A), r * 0.35f, c + Offset(0f, -r * 0.5f))
            val hat = Path().apply {
                moveTo(c.x - r * 0.5f, c.y - r * 0.65f)
                lineTo(c.x + r * 0.5f, c.y - r * 0.65f)
                lineTo(c.x + dir * r * 0.15f, c.y - r * 1.5f)
                close()
            }
            drawPath(hat, Color(0xFF5B21B6))
            drawLine(Color(0xFF92400E), c + Offset(dir * r * 0.75f, r * 0.6f), c + Offset(dir * r * 0.75f, -r * 0.9f), strokeWidth = r * 0.1f)
            drawCircle(Color(0xFFF472B6).copy(alpha = 0.5f + 0.3f * sin(time * 6f)), r * 0.25f, c + Offset(dir * r * 0.75f, -r * 0.95f))
        }
        HeroClass.RANGER -> {
            drawRoundRect(Color(0xFF166534), c + Offset(-r * 0.55f, -r * 0.3f), Size(r * 1.1f, r * 0.95f), CornerRadius(r * 0.35f))
            drawCircle(Color(0xFF15803D), r * 0.48f, c + Offset(0f, -r * 0.6f)) // Kapuze
            drawCircle(Color(0xFFFDE68A), r * 0.3f, c + Offset(dir * r * 0.05f, -r * 0.52f))
            drawArc(Color(0xFF92400E), if (faceRight) -70f else 110f, 140f, false,
                c + Offset(dir * r * 0.4f - r * 0.45f, -r * 0.6f), Size(r * 0.9f, r * 1.2f), style = Stroke(width = r * 0.1f))
        }
        HeroClass.NECROMANCER -> {
            if (!bodyOnly) drawCircle(Color(0xFFA78BFA).copy(alpha = 0.2f + 0.1f * sin(time * 3f)), r * 1.1f, c)
            val robe = Path().apply {
                moveTo(c.x, c.y - r * 0.6f)
                lineTo(c.x + r * 0.75f, c.y + r * 0.65f)
                lineTo(c.x - r * 0.75f, c.y + r * 0.65f)
                close()
            }
            drawPath(robe, Color(0xFF1F1B2E))
            drawCircle(Color(0xFF2E1065), r * 0.48f, c + Offset(0f, -r * 0.55f))
            drawCircle(Color(0xFFF5F5F4), r * 0.3f, c + Offset(0f, -r * 0.5f))
            drawCircle(Color(0xFF7C3AED), r * 0.08f, c + Offset(-r * 0.11f, -r * 0.52f))
            drawCircle(Color(0xFF7C3AED), r * 0.08f, c + Offset(r * 0.11f, -r * 0.52f))
        }
    }
}

// --- Effekte ---

private fun DrawScope.drawFx(cam: SurvivorsCamera, fx: SurvivorsFx, textMeasurer: TextMeasurer) {
    val t = fx.progress
    val a = 1f - t
    val c = cam.at(fx.x, fx.y)
    when (fx) {
        is SurvivorsFx.Swing -> {
            val range = cam.len(fx.range)
            val sweep = 180f
            val start = fx.angle * 180f / PI.toFloat() - sweep / 2f
            val arcs = if (fx.fullCircle) listOf(start, start + 180f) else listOf(start)
            val fill = if (fx.evolved) Color(0xFF60A5FA) else Color.White
            val edge = if (fx.evolved) Color(0xFFBFDBFE) else Color.White
            arcs.forEach { s ->
                drawArc(fill.copy(alpha = 0.45f * a), s, sweep, true, c - Offset(range, range), Size(range * 2f, range * 2f))
                drawArc(edge.copy(alpha = 0.9f * a), s, sweep, false, c - Offset(range, range), Size(range * 2f, range * 2f), style = Stroke(width = cam.len(0.08f)))
            }
            if (fx.evolved) {
                // Runenzeichen auf dem Rand der Runenklinge
                for (i in 0 until 8) {
                    val ang = fx.angle + i * PI.toFloat() / 4f + t * 2f
                    val p = c + Offset(cos(ang), sin(ang)) * (range * 0.85f)
                    drawLine(Color.White.copy(alpha = a), p - Offset(0f, cam.len(0.1f)), p + Offset(0f, cam.len(0.1f)), strokeWidth = cam.len(0.04f))
                    drawLine(Color.White.copy(alpha = a), p, p + Offset(cam.len(0.07f), -cam.len(0.05f)), strokeWidth = cam.len(0.04f))
                }
            }
        }
        is SurvivorsFx.Smash -> {
            val range = cam.len(fx.range)
            if (fx.ring) {
                // Erdbrecher: Schockwellen-Ring mit Steinsplittern
                val rr = range * (0.3f + 0.7f * t)
                drawCircle(Color(0xFFD97706).copy(alpha = 0.3f * a), rr, c)
                drawCircle(Color(0xFFFDE68A).copy(alpha = 0.8f * a), rr, c, style = Stroke(width = cam.len(0.14f)))
                for (i in 0 until 10) {
                    val ang = i * 0.628f + fx.angle
                    drawCircle(Color(0xFF78716C).copy(alpha = a), cam.len(0.07f), c + Offset(cos(ang), sin(ang)) * rr)
                }
            } else {
                // Hammerschlag: Kegel nach vorn
                val sweep = 86f
                val start = fx.angle * 180f / PI.toFloat() - sweep / 2f
                drawArc(Color(0xFFFBBF24).copy(alpha = 0.45f * a), start, sweep, true, c - Offset(range, range), Size(range * 2f, range * 2f))
                drawArc(Color.White.copy(alpha = 0.9f * a), start, sweep, false, c - Offset(range, range), Size(range * 2f, range * 2f),
                    style = Stroke(width = cam.len(0.1f)))
            }
        }
        is SurvivorsFx.ThornPulse -> {
            val rr = cam.len(fx.radius) * (0.5f + 0.5f * t)
            val color = if (fx.evolved) Color(0xFFDC2626) else Color(0xFFBE185D)
            drawCircle(color.copy(alpha = 0.25f * a), rr, c)
            for (i in 0 until 14) {
                val ang = i * 0.449f
                val dir = Offset(cos(ang), sin(ang))
                drawLine(color.copy(alpha = a), c + dir * (rr * 0.75f), c + dir * (rr * 1.05f), strokeWidth = cam.len(0.06f))
            }
        }
        is SurvivorsFx.Arc -> {
            val to = cam.at(fx.x2, fx.y2)
            val mid = Offset((c.x + to.x) / 2f, (c.y + to.y) / 2f) + Offset((to.y - c.y), (c.x - to.x)) * 0.15f
            val arc = Path().apply {
                moveTo(c.x, c.y)
                lineTo(mid.x, mid.y)
                lineTo(to.x, to.y)
            }
            drawPath(arc, Color(0xFFC4B5FD).copy(alpha = a), style = Stroke(width = cam.len(0.1f)))
            drawPath(arc, Color.White.copy(alpha = a), style = Stroke(width = cam.len(0.035f)))
        }
        is SurvivorsFx.Bolt -> {
            val top = Offset(c.x, c.y - size.height)
            val bolt = Path().apply {
                moveTo(top.x, top.y)
                var y = top.y
                var x = top.x
                var i = 0
                while (y < c.y) {
                    y = min(c.y, y + cam.len(0.8f))
                    x = c.x + (if (i % 2 == 0) 1 else -1) * cam.len(0.25f) * (1f - (y - top.y) / (c.y - top.y + 1f))
                    lineTo(x, y)
                    i++
                }
            }
            drawPath(bolt, Color(0xFFBAE6FD).copy(alpha = a), style = Stroke(width = cam.len(0.12f)))
            drawPath(bolt, Color.White.copy(alpha = a), style = Stroke(width = cam.len(0.04f)))
            drawCircle(Color(0xFFBAE6FD).copy(alpha = 0.4f * a), cam.len(fx.radius), c)
        }
        is SurvivorsFx.Nova -> {
            val r = cam.len(fx.radius) * (0.3f + 0.7f * t)
            drawCircle(Color(0xFF7DD3FC).copy(alpha = (if (fx.freeze) 0.4f else 0.25f) * a), r, c)
            drawCircle(Color.White.copy(alpha = 0.8f * a), r, c, style = Stroke(width = cam.len(if (fx.freeze) 0.16f else 0.1f)))
            if (fx.freeze) {
                // Eiszacken am Wellenrand
                for (i in 0 until 12) {
                    val ang = i * PI.toFloat() / 6f
                    val dir = Offset(cos(ang), sin(ang))
                    drawLine(Color(0xFFE0F2FE).copy(alpha = a), c + dir * (r * 0.85f), c + dir * (r * 1.08f), strokeWidth = cam.len(0.07f))
                }
            }
        }
        is SurvivorsFx.Puff -> {
            val r = cam.len(fx.radius) * (0.6f + 0.6f * t)
            drawCircle(Color(0xFFE2E8F0).copy(alpha = 0.35f * a), r, c)
            drawCircle(Color.White.copy(alpha = 0.6f * a), r, c, style = Stroke(width = cam.len(0.05f)))
        }
        is SurvivorsFx.Blast -> {
            val r = cam.len(fx.radius) * (0.4f + 0.6f * t)
            drawCircle(fx.color.copy(alpha = 0.4f * a), r, c)
            drawCircle(Color(0xFFFDE68A).copy(alpha = 0.7f * a), r * 0.5f, c)
        }
        is SurvivorsFx.Beam -> {
            val to = cam.at(fx.x2, fx.y2)
            drawLine(Color(0xFFA855F7).copy(alpha = 0.5f * a), c, to, strokeWidth = cam.len(fx.halfWidth * 2f) * (1f - 0.4f * t))
            drawLine(Color.White.copy(alpha = 0.8f * a), c, to, strokeWidth = cam.len(0.12f))
        }
        is SurvivorsFx.Number -> {
            val (label, color, size) = when {
                fx.kind == DamageKind.POISON -> Triple("${fx.amount}", Color(0xFF4ADE80), 0.26f)
                fx.kind == DamageKind.EXECUTE -> Triple("💀${fx.amount}", Color(0xFFF87171), 0.34f)
                fx.crit == 2 -> Triple("${fx.amount}!!", Color(0xFFF0ABFC), 0.5f)
                fx.crit == 1 -> Triple("${fx.amount}!", Color(0xFFFDE047), 0.36f)
                else -> Triple("${fx.amount}", Color.White, 0.28f)
            }
            val layout = textMeasurer.measure(
                label,
                TextStyle(color = color.copy(alpha = a), fontSize = (cam.scale * size).toSp(), fontWeight = FontWeight.Black),
            )
            drawText(layout, topLeft = c + Offset(-layout.size.width / 2f, -cam.len(0.6f) - t * cam.len(0.6f)))
        }
        is SurvivorsFx.Text -> {
            val layout = textMeasurer.measure(
                fx.text,
                TextStyle(color = fx.color.copy(alpha = a), fontSize = (cam.scale * 0.38f).toSp(), fontWeight = FontWeight.Black),
            )
            drawText(
                layout,
                topLeft = Offset(
                    (c.x - layout.size.width / 2f).coerceAtMost(size.width - layout.size.width).coerceAtLeast(0f),
                    c.y - cam.len(1.2f) - t * cam.len(1f),
                ),
            )
        }
        is SurvivorsFx.RunePop -> {
            // Leuchtende Rune, die nach oben schwebt (leicht versetzt zu einem gleichzeitigen Coin-Pop)
            val p = c + Offset(cam.len(0.5f), -cam.len(0.9f) - t * cam.len(1.1f))
            drawCircle(RuneViolet.copy(alpha = 0.35f * a), cam.len(0.3f), p)
            val layout = textMeasurer.measure(
                "ᚱ +${fx.amount}",
                TextStyle(color = RuneViolet.copy(alpha = a), fontSize = (cam.scale * 0.32f).toSp(), fontWeight = FontWeight.Black),
            )
            drawText(layout, topLeft = p + Offset(-layout.size.width / 2f, -layout.size.height / 2f))
        }
        is SurvivorsFx.CoinPop -> {
            val p = c - Offset(0f, cam.len(0.5f) + t * cam.len(1f))
            drawCoin(p, cam.len(0.18f), a)
            val layout = textMeasurer.measure(
                "+${fx.amount}",
                TextStyle(color = CoinGold.copy(alpha = a), fontSize = (cam.scale * 0.3f).toSp(), fontWeight = FontWeight.Black),
            )
            drawText(layout, topLeft = p + Offset(cam.len(0.25f), -layout.size.height / 2f))
        }
    }
}

/** Pfeile am Bildrand zu Bossen und zum Runenstein, wenn sie außerhalb des Bildes sind. */
private fun DrawScope.drawEdgeIndicators(cam: SurvivorsCamera, s: SurvivorsState) {
    s.enemies.filter { it.isBoss }.forEach { drawEdgeArrow(cam, s, it.x, it.y, DangerRed, EdgeIcon.BOSS) }
    s.runeStone?.let { drawEdgeArrow(cam, s, it.x, it.y, RuneViolet, EdgeIcon.RUNE) }
}

private enum class EdgeIcon { BOSS, RUNE }

/** Pulsierende Plakette mit Symbol, an der außen eine Pfeilspitze in Richtung Ziel sitzt. */
private fun DrawScope.drawEdgeArrow(cam: SurvivorsCamera, s: SurvivorsState, x: Float, y: Float, color: Color, icon: EdgeIcon) {
    val dx = x - s.px
    val dy = y - s.py
    if (abs(dx) < cam.halfWorldW && abs(dy) < cam.halfWorldH) return
    val angle = atan2(dy, dx)
    val d = density
    val margin = 48f * d // Platz für die nach außen zeigende Spitze
    val edge = Offset(
        (size.width / 2f + cos(angle) * size.width).coerceIn(margin, size.width - margin),
        (size.height / 2f + sin(angle) * size.height).coerceIn(margin, size.height - margin),
    )
    val wave = sin(s.time * 5f)
    val k = d * (1f + 0.12f * wave)
    val badge = 16f * k
    fun polar(a: Float, len: Float) = edge + Offset(cos(a), sin(a)) * len

    drawCircle(color.copy(alpha = 0.35f + 0.1f * wave), badge * 1.6f, edge)
    val arrow = Path().apply {
        val tip = polar(angle, badge + 20f * k)
        moveTo(tip.x, tip.y)
        polar(angle + 0.62f, badge + 2f * k).let { lineTo(it.x, it.y) }
        polar(angle - 0.62f, badge + 2f * k).let { lineTo(it.x, it.y) }
        close()
    }
    drawPath(arrow, color)
    drawPath(arrow, Color.White, style = Stroke(width = 2f * d))
    drawCircle(Color.Black.copy(alpha = 0.6f), badge, edge)
    drawCircle(color, badge, edge, style = Stroke(width = 2.5f * d))

    val line = 2.5f * d
    when (icon) {
        EdgeIcon.RUNE -> {
            // Runenzeichen wie beim Runen-Drop
            val h = badge * 0.55f
            drawLine(Color.White, edge + Offset(0f, -h), edge + Offset(0f, h), strokeWidth = line)
            drawLine(Color.White, edge + Offset(0f, -h), edge + Offset(h * 0.55f, -h * 0.45f), strokeWidth = line)
            drawLine(Color.White, edge + Offset(h * 0.55f, -h * 0.45f), edge + Offset(0f, 0f), strokeWidth = line)
        }
        EdgeIcon.BOSS -> {
            val h = badge * 0.55f
            drawLine(Color.White, edge + Offset(0f, -h), edge + Offset(0f, h * 0.25f), strokeWidth = line * 1.2f)
            drawCircle(Color.White, line * 0.7f, edge + Offset(0f, h * 0.7f))
        }
    }
}

/** Runenstein: Obelisk mit Lade-Ring (füllt sich) und Restzeit-Bogen. */
private fun DrawScope.drawRuneStone(cam: SurvivorsCamera, stone: RuneStone, time: Float) {
    val c = cam.at(stone.x, stone.y)
    val u = cam.scale
    val r = cam.len(SurvivorsEngine.RUNE_STONE_RADIUS)
    val pulse = 0.5f + 0.5f * sin(time * 3f)
    drawCircle(RuneViolet.copy(alpha = 0.08f + 0.06f * pulse + 0.15f * stone.charge), r, c)
    drawCircle(RuneViolet.copy(alpha = 0.5f), r, c, style = Stroke(width = u * 0.03f))
    // Ladung als Bogen am Rand, Restzeit als dünner Bogen innen
    val ring = Size(r * 2f, r * 2f)
    drawArc(RuneViolet, -90f, 360f * stone.charge, false, c - Offset(r, r), ring, style = Stroke(width = u * 0.08f))
    val inner = r * 0.82f
    drawArc(Color.White.copy(alpha = 0.35f), -90f, 360f * (stone.life / SurvivorsEngine.RUNE_STONE_LIFE).coerceIn(0f, 1f), false,
        c - Offset(inner, inner), Size(inner * 2f, inner * 2f), style = Stroke(width = u * 0.025f))
    // Obelisk
    val w = u * 0.32f
    val h = u * 0.9f
    drawOval(Color.Black.copy(alpha = 0.3f), c + Offset(-w * 0.8f, -u * 0.06f), Size(w * 1.6f, u * 0.2f))
    val body = Path().apply {
        moveTo(c.x - w / 2f, c.y)
        lineTo(c.x - w * 0.35f, c.y - h * 0.82f)
        lineTo(c.x, c.y - h)
        lineTo(c.x + w * 0.35f, c.y - h * 0.82f)
        lineTo(c.x + w / 2f, c.y)
        close()
    }
    drawPath(body, Color(0xFF3B2F5C))
    drawPath(body, RuneViolet.copy(alpha = 0.8f), style = Stroke(width = 1.5f))
    // Leuchtende Rune, heller mit wachsender Ladung
    val glyph = RuneViolet.copy(alpha = 0.55f + 0.45f * maxOf(stone.charge, pulse * 0.5f))
    val gx = c.x
    val top = c.y - h * 0.7f
    drawLine(glyph, Offset(gx, top), Offset(gx, top + h * 0.5f), strokeWidth = u * 0.045f)
    drawLine(glyph, Offset(gx, top), Offset(gx + w * 0.28f, top + h * 0.12f), strokeWidth = u * 0.045f)
    drawLine(glyph, Offset(gx + w * 0.28f, top + h * 0.12f), Offset(gx, top + h * 0.24f), strokeWidth = u * 0.045f)
}

/** Ausschnitt einer Welt für die Shop-Vorschau. */
fun DrawScope.drawSurvivorThemePreview(theme: SurvivorTheme, time: Float = 0f) {
    drawRect(theme.ground)
    val cell = size.width / 3f
    for (cx in 0..3) for (cy in 0..3) {
        val tl = Offset(cx * cell, cy * cell)
        if ((cx + cy) % 2 == 0) drawRect(theme.groundAlt, tl, Size(cell, cell))
        val h = hash(cx + 11, cy + 7)
        drawDecor(theme, tl + Offset(unit(h, 0) * cell, unit(h, 8) * cell), cell / DECOR_CELL, (h ushr 16) and 0xF, time, h)
    }
    val slimeColor = lerp(enemyColor(EnemyKind.SLIME), theme.enemyTint, theme.tintAmount)
    drawOval(slimeColor, Offset(size.width * 0.62f, size.height * 0.62f), Size(size.width * 0.18f, size.width * 0.14f))
}
