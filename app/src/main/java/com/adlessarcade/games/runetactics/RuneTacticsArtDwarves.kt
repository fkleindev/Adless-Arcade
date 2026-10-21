package com.adlessarcade.games.runetactics

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.sin

internal val dwarfArt: Map<String, DrawScope.(ArtBody) -> Unit> = mapOf(
    "dwarfguard" to DrawScope::drawDwarfGuard,
    "axeman" to DrawScope::drawAxeman,
    "runesmith" to DrawScope::drawRunesmith,
    "thane" to DrawScope::drawThane,
    "warlord" to DrawScope::drawWarlord,
    "crossbow" to DrawScope::drawCrossbowman,
)

private val DwarfSkin = Color(0xFFF1C27D)

/** Kurze, kräftige Beine mit schweren Stiefeln. */
private fun DrawScope.dwarfLegs(b: ArtBody, pants: Color, boots: Color = ArtBoot, knee: Color? = null) =
    artLegs(b, pants, boots, width = 1.25f, knee = knee, bootHeight = 0.26f, legWidth = 0.32f)

/** Dicker Arm von der breiten Schulter zur Hand. */
private fun DrawScope.dwarfArm(b: ArtBody, hand: Offset, color: Color) = artArm(b, hand, color, 0.27f, 0.5f)

/** Fassförmiger Rumpf; liefert die Form für Schattierung und Details. */
private fun DrawScope.dwarfTorso(b: ArtBody, color: Color, width: Float = 1.12f): Path {
    val u = b.u
    val c = b.c
    val top = b.shoulderY - u * 0.1f
    val bottom = b.hipY + u * 0.06f
    val w = u * width / 2f
    val torso = Path().apply {
        moveTo(c.x - w * 0.88f, top)
        lineTo(c.x + w * 0.88f, top)
        quadraticTo(c.x + w * 1.12f, (top + bottom) / 2f, c.x + w, bottom)
        lineTo(c.x - w, bottom)
        quadraticTo(c.x - w * 1.12f, (top + bottom) / 2f, c.x - w * 0.88f, top)
        close()
    }
    drawPath(torso, color)
    clipPath(torso) {
        drawRect(color.shade(0.22f), Offset(if (b.dir > 0) c.x + w * 0.35f else c.x - w * 1.3f, top), Size(w, bottom - top))
        drawRect(color.bright(0.18f), Offset(if (b.dir > 0) c.x - w * 0.8f else c.x + w * 0.55f, top + u * 0.06f), Size(w * 0.25f, (bottom - top) * 0.5f))
    }
    return torso
}

/** Zwergengesicht: buschige Brauen, Knollennase, rote Wangen. Bart folgt separat. */
private fun DrawScope.dwarfFace(b: ArtBody, brows: Color, skin: Color = DwarfSkin) {
    val h = b.head
    val hr = b.headR
    drawCircle(skin, hr, h)
    drawArc(skin.shade(0.1f), if (b.dir > 0) 100f else -80f, 160f, false, h - Offset(hr * 0.92f, hr * 0.92f), Size(hr * 1.84f, hr * 1.84f), style = Stroke(hr * 0.12f))
    // Ohren
    for (side in listOf(-1f, 1f)) drawCircle(skin.shade(0.06f), hr * 0.18f, Offset(h.x + side * hr * 0.95f, h.y + hr * 0.05f))
    for (side in listOf(-1f, 1f)) {
        val e = Offset(h.x + side * hr * 0.34f + b.dir * hr * 0.06f, h.y - hr * 0.02f)
        drawCircle(Color.White, hr * 0.13f, e)
        drawCircle(ArtInk, hr * 0.08f, e + Offset(b.dir * hr * 0.04f, hr * 0.01f))
        drawCircle(Color.White.copy(alpha = 0.9f), hr * 0.03f, e + Offset(b.dir * hr * 0.02f - hr * 0.03f, -hr * 0.03f))
        // Buschige Braue
        drawLine(brows, e + Offset(-hr * 0.2f, -hr * (0.2f - side * 0.06f)), e + Offset(hr * 0.2f, -hr * (0.2f + side * 0.06f)), hr * 0.13f, StrokeCap.Round)
        drawCircle(Color(0xFFF87171).copy(alpha = 0.35f), hr * 0.16f, Offset(h.x + side * hr * 0.55f, h.y + hr * 0.3f))
    }
    // Knollennase
    val nose = Offset(h.x + b.dir * hr * 0.1f, h.y + hr * 0.25f)
    drawCircle(skin.shade(0.12f), hr * 0.2f, nose + Offset(0f, hr * 0.02f))
    drawCircle(skin.bright(0.05f), hr * 0.18f, nose)
    drawCircle(Color.White.copy(alpha = 0.5f), hr * 0.05f, nose + Offset(-hr * 0.06f, -hr * 0.06f))
}

/**
 * Bart: ausladend von den Wangen bis [bottomY], mit Schnurrbart und optional zwei Zöpfen samt Ringen.
 */
private fun DrawScope.dwarfBeard(b: ArtBody, color: Color, bottomY: Float, braids: Color? = null, forked: Boolean = false, width: Float = 1.05f) {
    val h = b.head
    val hr = b.headR
    val beard = Path().apply {
        moveTo(h.x - hr * width, h.y)
        quadraticTo(h.x - hr * (width + 0.15f), (h.y + bottomY) / 2f, h.x - hr * 0.45f, bottomY - hr * 0.1f)
        if (forked) {
            lineTo(h.x - hr * 0.2f, bottomY + hr * 0.1f)
            lineTo(h.x, bottomY - hr * 0.3f)
            lineTo(h.x + hr * 0.2f, bottomY + hr * 0.1f)
        } else {
            quadraticTo(h.x, bottomY + hr * 0.25f, h.x + hr * 0.45f, bottomY - hr * 0.1f)
        }
        lineTo(h.x + hr * 0.45f, bottomY - hr * 0.1f)
        quadraticTo(h.x + hr * (width + 0.15f), (h.y + bottomY) / 2f, h.x + hr * width, h.y)
        quadraticTo(h.x + hr * 0.6f, h.y + hr * 0.5f, h.x, h.y + hr * 0.45f)
        quadraticTo(h.x - hr * 0.6f, h.y + hr * 0.5f, h.x - hr * width, h.y)
        close()
    }
    drawPath(beard, color)
    clipPath(beard) {
        drawRect(color.shade(0.18f), Offset(if (b.dir > 0) h.x + hr * 0.4f else h.x - hr * 1.4f, h.y), Size(hr, bottomY - h.y + hr))
    }
    // Strähnen
    for (i in -1..1) {
        drawLine(color.shade(0.25f), Offset(h.x + i * hr * 0.4f, h.y + hr * 0.75f), Offset(h.x + i * hr * 0.32f, bottomY - hr * 0.25f), hr * 0.05f, StrokeCap.Round)
    }
    // Schnurrbart
    for (side in listOf(-1f, 1f)) {
        drawPath(Path().apply {
            moveTo(h.x + b.dir * hr * 0.08f, h.y + hr * 0.38f)
            quadraticTo(h.x + side * hr * 0.4f, h.y + hr * 0.3f, h.x + side * hr * 0.65f, h.y + hr * 0.55f)
            quadraticTo(h.x + side * hr * 0.35f, h.y + hr * 0.55f, h.x + b.dir * hr * 0.08f, h.y + hr * 0.52f)
            close()
        }, color.bright(0.12f))
    }
    braids?.let { ring ->
        for (side in listOf(-1f, 1f)) {
            var p = Offset(h.x + side * hr * 0.3f, bottomY - hr * 0.1f)
            for (k in 0..2) {
                drawOval(color.shade(0.08f * (k % 2)), p - Offset(hr * 0.13f, hr * 0.1f), Size(hr * 0.26f, hr * 0.22f))
                p += Offset(0f, hr * 0.17f)
            }
            drawRoundRect(ring, p - Offset(hr * 0.12f, hr * 0.12f), Size(hr * 0.24f, hr * 0.13f), CornerRadius(hr * 0.04f))
            drawOval(color, p - Offset(hr * 0.08f, 0f), Size(hr * 0.16f, hr * 0.16f))
        }
    }
}

/** Runder Helm mit Krempe und Nasenschutz; optional mit Hörnern oder Flügeln. */
private fun DrawScope.dwarfHelm(b: ArtBody, metal: Color, trim: Color, horns: Color? = null, wings: Color? = null, nasal: Boolean = true) {
    val h = b.head
    val hr = b.headR
    horns?.let { horn ->
        for (side in listOf(-1f, 1f)) {
            val p = Path().apply {
                moveTo(h.x + side * hr * 0.8f, h.y - hr * 0.4f)
                quadraticTo(h.x + side * hr * 1.75f, h.y - hr * 0.45f, h.x + side * hr * 1.6f, h.y - hr * 1.45f)
                quadraticTo(h.x + side * hr * 1.4f, h.y - hr * 0.85f, h.x + side * hr * 0.7f, h.y - hr * 0.8f)
                close()
            }
            drawPath(p, horn)
            drawPath(p, horn.shade(0.25f), style = Stroke(hr * 0.05f))
            for (k in 1..2) {
                val q = Offset(h.x + side * hr * (1.0f + k * 0.2f), h.y - hr * (0.55f + k * 0.15f))
                drawLine(horn.shade(0.2f), q - Offset(0f, hr * 0.1f), q + Offset(side * hr * 0.08f, hr * 0.06f), hr * 0.04f)
            }
        }
    }
    wings?.let { w ->
        for (side in listOf(-1f, 1f)) {
            for (k in 0..2) {
                val base = Offset(h.x + side * hr * 0.85f, h.y - hr * 0.55f)
                val tip = base + Offset(side * hr * (0.55f - k * 0.08f), -hr * (0.95f - k * 0.25f))
                drawPath(Path().apply {
                    moveTo(base.x, base.y + k * hr * 0.1f)
                    quadraticTo(base.x + side * hr * 0.1f, tip.y, tip.x, tip.y)
                    quadraticTo(base.x + side * hr * 0.45f, base.y - hr * 0.2f, base.x, base.y + hr * 0.15f + k * hr * 0.1f)
                    close()
                }, w.shade(0.08f * k))
            }
        }
    }
    val dome = Path().apply {
        moveTo(h.x - hr * 1.05f, h.y - hr * 0.2f)
        cubicTo(h.x - hr * 1.1f, h.y - hr * 1.45f, h.x + hr * 1.1f, h.y - hr * 1.45f, h.x + hr * 1.05f, h.y - hr * 0.2f)
        close()
    }
    drawPath(dome, metal)
    drawArc(metal.bright(0.45f), 200f, 50f, false, h + Offset(-hr * 0.78f, -hr * 1.0f), Size(hr * 1.56f, hr * 1.2f), style = Stroke(hr * 0.1f, cap = StrokeCap.Round))
    drawLine(trim, Offset(h.x, h.y - hr * 1.08f), Offset(h.x, h.y - hr * 0.25f), hr * 0.14f)
    drawLine(trim, Offset(h.x - hr * 1.12f, h.y - hr * 0.22f), Offset(h.x + hr * 1.12f, h.y - hr * 0.22f), hr * 0.2f, StrokeCap.Round)
    for (i in -2..2) drawCircle(trim.bright(0.35f), hr * 0.05f, Offset(h.x + i * hr * 0.45f, h.y - hr * 0.22f))
    if (nasal) {
        drawRoundRect(trim, Offset(h.x + b.dir * hr * 0.1f - hr * 0.08f, h.y - hr * 0.2f), Size(hr * 0.16f, hr * 0.42f), CornerRadius(hr * 0.06f))
    }
}

/** Kriegshammer mit Kopf, Runenband und Griffwicklung. */
private fun DrawScope.warhammer(b: ArtBody, hand: Offset, head: Color, rune: Color, length: Float = 1.0f, big: Float = 1f) {
    val u = b.u
    val top = hand + Offset(b.dir * u * 0.08f, -u * length)
    drawLine(ArtWood, hand + Offset(0f, u * 0.25f), top, u * 0.1f, StrokeCap.Round)
    for (k in 0..2) drawLine(ArtLeather.bright(0.15f), hand + Offset(-u * 0.05f, u * (0.12f - k * 0.08f)), hand + Offset(u * 0.05f, u * (0.08f - k * 0.08f)), u * 0.03f)
    val hw = u * 0.34f * big
    val hh = u * 0.24f * big
    drawRoundRect(head, top - Offset(hw, hh), Size(hw * 2f, hh * 2f), CornerRadius(u * 0.05f))
    drawRoundRect(head.shade(0.3f), top - Offset(hw, hh) + Offset(hw * 1.35f, 0f), Size(hw * 0.65f, hh * 2f), CornerRadius(u * 0.05f))
    drawRoundRect(head.bright(0.35f), top - Offset(hw * 0.9f, hh * 0.85f), Size(hw * 0.25f, hh * 1.7f), CornerRadius(u * 0.03f))
    for (side in listOf(-1f, 1f)) drawLine(head.shade(0.4f), top + Offset(side * hw * 0.55f, -hh), top + Offset(side * hw * 0.55f, hh), u * 0.025f)
    drawLine(rune, top + Offset(-hw * 0.3f, 0f), top + Offset(hw * 0.3f, 0f), u * 0.05f, StrokeCap.Round)
    drawLine(rune, top + Offset(0f, -hh * 0.55f), top + Offset(0f, hh * 0.55f), u * 0.05f, StrokeCap.Round)
}

/** Breitblattaxt; [double] mit zwei Blättern. */
private fun DrawScope.battleAxe(
    b: ArtBody,
    hand: Offset,
    steel: Color,
    haft: Color,
    length: Float,
    bladeSize: Float,
    double: Boolean,
    head: (DrawScope.(Offset) -> Unit)? = null,
) {
    val u = b.u
    val dir = b.dir
    // Schräg nach außen, damit das hintere Blatt nicht vor dem Kopf liegt; der Kopf folgt dem Stiel.
    val bottom = hand + Offset(-dir * u * 0.04f, u * 0.3f)
    val tilted = hand + Offset(dir * u * (0.1f + (if (double) 0.22f * bladeSize else 0f)), -u * length)
    val axis = tilted - bottom
    val angle = Math.toDegrees(kotlin.math.atan2(axis.x.toDouble(), (-axis.y).toDouble())).toFloat()
    drawLine(haft, bottom, tilted + axis * (0.1f / length), u * 0.1f, StrokeCap.Round)
    for (k in 0..2) drawLine(ArtLeather.bright(0.15f), hand + Offset(-u * 0.05f, u * (0.14f - k * 0.08f)), hand + Offset(u * 0.05f, u * (0.1f - k * 0.08f)), u * 0.03f)
    rotate(angle, tilted) { if (head != null) head(tilted) else axeHead(b, tilted, steel, bladeSize, double) }
}

/** Prunkaxt des Kriegsherrn: Halbmondblatt mit Goldschneide und Runen, gezackter Dorn, Speerspitze, roter Stein. */
private fun DrawScope.warlordAxeHead(b: ArtBody, top: Offset, gold: Color) {
    val u = b.u
    val dir = b.dir
    val steel = Color(0xFF4B5563)
    val glow = Color(0xFFFB923C)
    val p = b.pulse(3f)
    // Speerspitze oben
    drawPath(Path().apply {
        moveTo(top.x - u * 0.08f, top.y - u * 0.3f)
        lineTo(top.x, top.y - u * 0.72f)
        lineTo(top.x + u * 0.08f, top.y - u * 0.3f)
        close()
    }, Color(0xFFD1D5DB))
    // Gezackter Dorn auf der Rückseite
    drawPath(Path().apply {
        moveTo(top.x - dir * u * 0.06f, top.y - u * 0.16f)
        lineTo(top.x - dir * u * 0.42f, top.y - u * 0.08f)
        lineTo(top.x - dir * u * 0.28f, top.y)
        lineTo(top.x - dir * u * 0.42f, top.y + u * 0.08f)
        lineTo(top.x - dir * u * 0.06f, top.y + u * 0.16f)
        close()
    }, steel.bright(0.15f))
    // Großes Halbmondblatt
    val blade = Path().apply {
        moveTo(top.x + dir * u * 0.05f, top.y - u * 0.22f)
        quadraticTo(top.x + dir * u * 0.35f, top.y - u * 0.3f, top.x + dir * u * 0.55f, top.y - u * 0.7f)
        quadraticTo(top.x + dir * u * 0.95f, top.y, top.x + dir * u * 0.55f, top.y + u * 0.7f)
        quadraticTo(top.x + dir * u * 0.35f, top.y + u * 0.3f, top.x + dir * u * 0.05f, top.y + u * 0.22f)
        close()
    }
    drawPath(blade, steel)
    // Goldene Schneide
    drawPath(Path().apply {
        moveTo(top.x + dir * u * 0.55f, top.y - u * 0.7f)
        quadraticTo(top.x + dir * u * 0.95f, top.y, top.x + dir * u * 0.55f, top.y + u * 0.7f)
    }, gold, style = Stroke(u * 0.09f, cap = StrokeCap.Round))
    drawPath(Path().apply {
        moveTo(top.x + dir * u * 0.6f, top.y - u * 0.55f)
        quadraticTo(top.x + dir * u * 0.9f, top.y, top.x + dir * u * 0.6f, top.y + u * 0.55f)
    }, Color.White.copy(alpha = 0.55f), style = Stroke(u * 0.025f, cap = StrokeCap.Round))
    // Glühende Runen im Blatt
    for (k in -1..1) {
        val r = top + Offset(dir * u * 0.38f, k * u * 0.24f)
        drawLine(glow.copy(alpha = 0.55f + 0.45f * p), r + Offset(0f, -u * 0.07f), r + Offset(0f, u * 0.07f), u * 0.035f, StrokeCap.Round)
        drawLine(glow.copy(alpha = 0.55f + 0.45f * p), r + Offset(0f, -u * 0.02f), r + Offset(dir * u * 0.07f, -u * 0.08f), u * 0.035f, StrokeCap.Round)
    }
    // Goldmanschette mit rotem Stein
    drawRoundRect(gold, top - Offset(u * 0.1f, u * 0.24f), Size(u * 0.2f, u * 0.48f), CornerRadius(u * 0.05f))
    drawRoundRect(gold.shade(0.3f), top - Offset(u * 0.1f, u * 0.24f), Size(u * 0.2f, u * 0.48f), CornerRadius(u * 0.05f), style = Stroke(u * 0.025f))
    drawCircle(Color(0xFFDC2626), u * 0.07f, top)
    drawCircle(Color.White.copy(alpha = 0.7f), u * 0.025f, top + Offset(-u * 0.02f, -u * 0.025f))
}

/** Axtkopf für einen senkrechten Stiel mit Mitte bei [top]. */
private fun DrawScope.axeHead(b: ArtBody, top: Offset, steel: Color, bladeSize: Float, double: Boolean) {
    val u = b.u
    val dir = b.dir
    val sides = if (double) listOf(-1f, 1f) else listOf(dir)
    for (s in sides) {
        val blade = Path().apply {
            moveTo(top.x + s * u * 0.04f, top.y - u * 0.16f * bladeSize)
            quadraticTo(top.x + s * u * 0.3f * bladeSize, top.y - u * 0.2f * bladeSize, top.x + s * u * 0.5f * bladeSize, top.y - u * 0.4f * bladeSize)
            quadraticTo(top.x + s * u * 0.62f * bladeSize, top.y, top.x + s * u * 0.5f * bladeSize, top.y + u * 0.4f * bladeSize)
            quadraticTo(top.x + s * u * 0.3f * bladeSize, top.y + u * 0.2f * bladeSize, top.x + s * u * 0.04f, top.y + u * 0.16f * bladeSize)
            close()
        }
        drawPath(blade, steel)
        clipPath(blade) { drawRect(steel.shade(0.25f), Offset(top.x + (if (s > 0) 0f else -u), top.y), Size(u, u)) }
        drawPath(Path().apply {
            moveTo(top.x + s * u * 0.5f * bladeSize, top.y - u * 0.38f * bladeSize)
            quadraticTo(top.x + s * u * 0.6f * bladeSize, top.y, top.x + s * u * 0.5f * bladeSize, top.y + u * 0.38f * bladeSize)
        }, Color.White.copy(alpha = 0.7f), style = Stroke(u * 0.03f, cap = StrokeCap.Round))
    }
    drawRoundRect(steel.shade(0.35f), top - Offset(u * 0.08f, u * 0.18f * bladeSize), Size(u * 0.16f, u * 0.36f * bladeSize), CornerRadius(u * 0.04f))
}

// --- Zwergenwache ---

private fun DrawScope.drawDwarfGuard(b: ArtBody) {
    val u = b.u
    val c = b.c
    val dir = b.dir
    val iron = Color(0xFF9CA3AF)
    val amber = Color(0xFFB45309)
    val beard = Color(0xFF92400E)

    dwarfLegs(b, Color(0xFF57534E), knee = iron)
    dwarfArm(b, b.hand, Color(0xFF6B7280))
    dwarfArm(b, b.offHand, Color(0xFF6B7280))
    // Kettenhemd mit Wappenrock
    val torso = dwarfTorso(b, Color(0xFF78716C))
    clipPath(torso) {
        for (row in 0..6) for (i in -5..5) {
            drawArc(Color(0xFF57534E), 0f, 180f, false, Offset(c.x + i * u * 0.11f - u * 0.05f + (row % 2) * u * 0.055f, b.shoulderY - u * 0.08f + row * u * 0.1f), Size(u * 0.1f, u * 0.08f), style = Stroke(u * 0.02f))
        }
    }
    val tabard = Path().apply {
        moveTo(c.x - u * 0.24f, b.shoulderY)
        lineTo(c.x + u * 0.24f, b.shoulderY)
        lineTo(c.x + u * 0.26f, b.hipY + u * 0.3f)
        lineTo(c.x, b.hipY + u * 0.22f)
        lineTo(c.x - u * 0.26f, b.hipY + u * 0.3f)
        close()
    }
    drawPath(tabard, amber)
    drawPath(tabard, ArtGold.shade(0.2f), style = Stroke(u * 0.03f))
    artBelt(b, b.hipY - u * 0.02f, 0.56f, ArtLeather.shade(0.2f), iron)
    for (side in listOf(-1f, 1f)) artPauldron(b, side * 1.12f, iron, Color(0xFF57534E), 1.05f)

    // Kopf mit Bart, Helm mit Nasenschutz
    dwarfFace(b, beard.shade(0.2f))
    dwarfBeard(b, beard, b.hipY - u * 0.15f, braids = iron)
    dwarfHelm(b, iron, Color(0xFF6B7280))

    // Rundschild aus Holz mit Eisenbeschlag
    val sc = b.offHand + Offset(-dir * u * 0.12f, -u * 0.12f)
    val sr = u * 0.48f
    drawCircle(amber, sr, sc)
    for (i in -2..2) drawLine(amber.shade(0.25f), sc + Offset(i * sr * 0.38f, -sr * 0.92f), sc + Offset(i * sr * 0.38f, sr * 0.92f), u * 0.025f)
    drawCircle(Color(0xFF6B7280), sr, sc, style = Stroke(u * 0.08f))
    for (k in 0 until 8) {
        val a = k * Math.PI.toFloat() / 4f
        drawCircle(iron.bright(0.3f), u * 0.03f, sc + Offset(kotlin.math.cos(a) * sr * 0.88f, sin(a) * sr * 0.88f))
    }
    drawCircle(iron, sr * 0.3f, sc)
    drawCircle(iron.bright(0.5f), sr * 0.1f, sc + Offset(-sr * 0.08f, -sr * 0.08f))

    warhammer(b, b.hand, iron, ArtGold.copy(alpha = 0.8f), 0.95f)
    artHand(b, b.hand, DwarfSkin.shade(0.1f), 0.13f)
}

// --- Zwergen-Axtkämpfer ---

private fun DrawScope.drawAxeman(b: ArtBody) {
    val u = b.u
    val c = b.c
    val leather = Color(0xFF9A3412)
    val beard = Color(0xFFC2410C)
    val steel = Color(0xFFD6D3D1)

    dwarfLegs(b, Color(0xFF44403C))
    // Nackte, kräftige Arme mit Lederschienen
    for (hd in listOf(b.hand, b.offHand)) {
        dwarfArm(b, hd, DwarfSkin.shade(0.05f))
        val s = if (hd.x > c.x) 1f else -1f
        val shoulder = Offset(c.x + s * u * 0.5f, b.shoulderY + u * 0.1f)
        drawLine(ArtLeather, hd - (hd - shoulder) * 0.1f, hd - (hd - shoulder) * 0.45f, u * 0.3f, StrokeCap.Round)
        drawLine(steel.shade(0.2f), hd - (hd - shoulder) * 0.25f + Offset(-u * 0.1f, 0f), hd - (hd - shoulder) * 0.25f + Offset(u * 0.1f, 0f), u * 0.04f)
    }
    // Lederharnisch mit Kreuzgurten
    dwarfTorso(b, leather)
    for (side in listOf(-1f, 1f)) {
        drawLine(ArtLeather.shade(0.3f), Offset(c.x + side * u * 0.44f, b.shoulderY - u * 0.08f), Offset(c.x - side * u * 0.4f, b.hipY), u * 0.08f)
    }
    drawCircle(steel, u * 0.08f, Offset(c.x, (b.shoulderY + b.hipY) / 2f + u * 0.02f))
    artBelt(b, b.hipY, 0.58f, ArtLeather.shade(0.3f), ArtGold)
    // Fellbesatz an den Schultern
    for (side in listOf(-1f, 1f)) {
        for (k in 0..2) drawCircle(Color(0xFF78716C).bright(0.1f * k), u * (0.14f - k * 0.02f), Offset(c.x + side * u * (0.36f + k * 0.08f), b.shoulderY - u * 0.06f + k * u * 0.02f))
    }

    dwarfFace(b, beard.shade(0.25f))
    dwarfBeard(b, beard, b.hipY - u * 0.1f, braids = ArtGold, forked = true)
    dwarfHelm(b, Color(0xFF78716C), Color(0xFF57534E), horns = Color(0xFFF5F5F4))

    battleAxe(b, b.hand, steel, ArtWood, 1.15f, 1.15f, double = true)
    artHand(b, b.hand, DwarfSkin.shade(0.1f), 0.13f)
    artHand(b, b.offHand, DwarfSkin.shade(0.1f), 0.13f)
}

// --- Runenschmied ---

private fun DrawScope.drawRunesmith(b: ArtBody) {
    val u = b.u
    val c = b.c
    val dir = b.dir
    val tunic = Color(0xFF7C2D12)
    val ember = Color(0xFFF59E0B)
    val grey = Color(0xFFB8BCC4)
    val p = b.pulse(4f)

    dwarfLegs(b, Color(0xFF3F3A36))
    // Hochgekrempelte Ärmel, bloße Unterarme
    for (hd in listOf(b.hand, b.offHand)) {
        val s = if (hd.x > c.x) 1f else -1f
        val shoulder = Offset(c.x + s * u * 0.5f, b.shoulderY + u * 0.1f)
        drawLine(DwarfSkin.shade(0.05f), shoulder, hd, u * 0.24f, StrokeCap.Round)
        drawLine(tunic.bright(0.1f), shoulder, shoulder + (hd - shoulder) * 0.45f, u * 0.3f, StrokeCap.Round)
    }
    dwarfTorso(b, tunic)
    // Lederschürze mit glühenden Runen
    val apron = Path().apply {
        moveTo(c.x - u * 0.3f, b.shoulderY + u * 0.06f)
        lineTo(c.x + u * 0.3f, b.shoulderY + u * 0.06f)
        lineTo(c.x + u * 0.42f, b.hipY + u * 0.36f)
        quadraticTo(c.x, b.hipY + u * 0.44f, c.x - u * 0.42f, b.hipY + u * 0.36f)
        close()
    }
    drawPath(apron, Color(0xFF5B3A1E))
    clipPath(apron) { drawRect(Color(0xFF3F2814), Offset(c.x + dir * u * 0.1f - (if (dir < 0) u * 0.4f else 0f), b.shoulderY), Size(u * 0.4f, u)) }
    drawPath(apron, Color(0xFF3F2814), style = Stroke(u * 0.03f))
    // Runen
    val runeC = Offset(c.x, b.hipY + u * 0.12f)
    drawCircle(ember.copy(alpha = 0.3f * p), u * 0.18f, runeC)
    drawPath(Path().apply {
        moveTo(runeC.x - u * 0.08f, runeC.y + u * 0.12f)
        lineTo(runeC.x - u * 0.08f, runeC.y - u * 0.12f)
        lineTo(runeC.x + u * 0.08f, runeC.y)
        lineTo(runeC.x - u * 0.08f, runeC.y + u * 0.02f)
        moveTo(runeC.x + u * 0.04f, runeC.y + u * 0.02f)
        lineTo(runeC.x + u * 0.1f, runeC.y + u * 0.12f)
    }, ember, style = Stroke(u * 0.035f, cap = StrokeCap.Round))
    // Werkzeugtasche
    drawRoundRect(ArtLeather, Offset(c.x - dir * u * 0.5f - u * 0.1f, b.hipY - u * 0.05f), Size(u * 0.2f, u * 0.2f), CornerRadius(u * 0.04f))
    drawLine(Color(0xFF9CA3AF), Offset(c.x - dir * u * 0.5f, b.hipY - u * 0.15f), Offset(c.x - dir * u * 0.5f, b.hipY), u * 0.04f)

    // Kopf: Glatze mit grauem Haarkranz, Schutzbrille auf der Stirn, langer Bart
    val h = b.head
    val hr = b.headR
    dwarfFace(b, grey.shade(0.3f))
    drawArc(DwarfSkin.bright(0.35f), 200f, 60f, false, h + Offset(-hr * 0.7f, -hr * 0.9f), Size(hr * 1.4f, hr * 1.2f), style = Stroke(hr * 0.1f, cap = StrokeCap.Round))
    // Haarkranz an den Seiten (unter der Brille, nicht über dem Scheitel)
    for (side in listOf(-1f, 1f)) {
        drawPath(Path().apply {
            moveTo(h.x + side * hr * 0.75f, h.y - hr * 0.55f)
            quadraticTo(h.x + side * hr * 1.1f, h.y - hr * 0.3f, h.x + side * hr * 1.0f, h.y + hr * 0.15f)
            lineTo(h.x + side * hr * 0.85f, h.y + hr * 0.05f)
            close()
        }, grey)
    }
    drawLine(Color(0xFF44403C), Offset(h.x - hr * 0.98f, h.y - hr * 0.48f), Offset(h.x + hr * 0.98f, h.y - hr * 0.48f), hr * 0.1f)
    for (side in listOf(-1f, 1f)) {
        val g = Offset(h.x + side * hr * 0.32f, h.y - hr * 0.52f)
        drawCircle(Color(0xFF78350F), hr * 0.2f, g)
        drawCircle(ember.copy(alpha = 0.75f), hr * 0.13f, g)
        drawCircle(Color.White.copy(alpha = 0.8f), hr * 0.04f, g + Offset(-hr * 0.04f, -hr * 0.04f))
    }
    dwarfBeard(b, grey, b.hipY + u * 0.05f, braids = Color(0xFFB45309))

    // Schmiedehammer mit glühendem Runenkopf
    val hand = b.hand
    val top = hand + Offset(dir * u * 0.08f, -u * 0.95f)
    drawCircle(ember.copy(alpha = 0.3f * p), u * 0.4f, top)
    warhammer(b, hand, Color(0xFF57534E), ember, 0.95f, 1.05f)
    // Glühender Runenstein in der zweiten Hand
    val stone = b.offHand + Offset(0f, -u * 0.2f)
    drawCircle(ember.copy(alpha = 0.35f * p), u * 0.26f, stone)
    drawRoundRect(Color(0xFF6B7280), stone - Offset(u * 0.13f, u * 0.11f), Size(u * 0.26f, u * 0.22f), CornerRadius(u * 0.06f))
    drawLine(ember, stone + Offset(-u * 0.06f, -u * 0.06f), stone + Offset(u * 0.06f, u * 0.06f), u * 0.03f, StrokeCap.Round)
    drawLine(ember, stone + Offset(u * 0.06f, -u * 0.06f), stone + Offset(-u * 0.02f, u * 0.02f), u * 0.03f, StrokeCap.Round)
    artHand(b, hand, DwarfSkin.shade(0.1f), 0.13f)
    artHand(b, b.offHand, DwarfSkin.shade(0.1f), 0.13f)
}

// --- Zwergen-Than ---

private fun DrawScope.drawThane(b: ArtBody) {
    val u = b.u
    val c = b.c
    val dir = b.dir
    val time = b.time
    val plate = Color(0xFFA1A1AA)
    val gold = Color(0xFFCA8A04)
    val cloak = Color(0xFF7F1D1D)
    val white = Color(0xFFF5F5F4)

    // Schwerer Umhang
    val sway = sin(time * 2f) * u * 0.04f
    val cape = Path().apply {
        moveTo(c.x - u * 0.5f, b.shoulderY - u * 0.05f)
        lineTo(c.x + u * 0.5f, b.shoulderY - u * 0.05f)
        lineTo(c.x + u * 0.85f - dir * sway, b.footY - u * 0.05f)
        quadraticTo(c.x, b.footY + u * 0.05f, c.x - u * 0.85f - dir * sway, b.footY - u * 0.05f)
        close()
    }
    drawPath(cape, cloak)
    drawPath(cape, gold, style = Stroke(u * 0.04f))

    dwarfLegs(b, Color(0xFF52525B), plate, knee = plate)
    dwarfArm(b, b.hand, plate.shade(0.15f))
    dwarfArm(b, b.offHand, plate.shade(0.15f))
    // Prunkharnisch mit Goldkante und Wappen
    val torso = dwarfTorso(b, plate, 1.18f)
    drawPath(torso, gold, style = Stroke(u * 0.04f))
    val crest = Offset(c.x, b.shoulderY + u * 0.2f)
    drawPath(Path().apply {
        moveTo(crest.x, crest.y - u * 0.14f)
        lineTo(crest.x + u * 0.12f, crest.y)
        lineTo(crest.x, crest.y + u * 0.14f)
        lineTo(crest.x - u * 0.12f, crest.y)
        close()
    }, gold)
    // Plattenschurz
    for (i in -1..1) drawRoundRect(plate.shade(0.1f * kotlin.math.abs(i)), Offset(c.x + i * u * 0.3f - u * 0.15f, b.hipY), Size(u * 0.3f, u * 0.24f), CornerRadius(u * 0.05f))
    artBelt(b, b.hipY - u * 0.02f, 0.6f, ArtLeather.shade(0.3f), gold)
    for (side in listOf(-1f, 1f)) artPauldron(b, side * 1.15f, plate.bright(0.1f), gold, 1.2f)

    // Weißer Bart mit Goldringen, Flügelhelm mit Goldbesatz
    dwarfFace(b, white.shade(0.15f))
    dwarfBeard(b, white, b.hipY + u * 0.1f, braids = gold, width = 1.1f)
    dwarfHelm(b, plate.bright(0.15f), gold, wings = Color(0xFFE5E7EB))

    // Großer Goldschild mit Runenkreis
    val sc = b.offHand + Offset(-dir * u * 0.15f, -u * 0.15f)
    val shield = Path().apply {
        moveTo(sc.x - u * 0.45f, sc.y - u * 0.45f)
        lineTo(sc.x + u * 0.45f, sc.y - u * 0.45f)
        lineTo(sc.x + u * 0.42f, sc.y + u * 0.15f)
        quadraticTo(sc.x + u * 0.3f, sc.y + u * 0.5f, sc.x, sc.y + u * 0.65f)
        quadraticTo(sc.x - u * 0.3f, sc.y + u * 0.5f, sc.x - u * 0.42f, sc.y + u * 0.15f)
        close()
    }
    drawPath(shield, gold)
    clipPath(shield) { drawRect(gold.shade(0.2f), Offset(sc.x + u * 0.1f, sc.y - u * 0.5f), Size(u * 0.5f, u * 1.2f)) }
    drawPath(shield, Color(0xFF713F12), style = Stroke(u * 0.06f))
    drawCircle(cloak, u * 0.2f, sc)
    drawCircle(gold.bright(0.3f), u * 0.2f, sc, style = Stroke(u * 0.03f))
    drawLine(gold.bright(0.4f), sc + Offset(0f, -u * 0.12f), sc + Offset(0f, u * 0.12f), u * 0.035f, StrokeCap.Round)
    drawLine(gold.bright(0.4f), sc + Offset(-u * 0.1f, -u * 0.05f), sc + Offset(u * 0.1f, u * 0.05f), u * 0.035f, StrokeCap.Round)

    warhammer(b, b.hand, gold.bright(0.15f), Color(0xFF7DD3FC), 1.0f, 1.1f)
    artHand(b, b.hand, plate.shade(0.1f), 0.13f)
}

// --- Zwergen-Kriegsherr ---

private fun DrawScope.drawWarlord(b: ArtBody) {
    val u = b.u
    val c = b.c
    val dir = b.dir
    val time = b.time
    val red = Color(0xFF7F1D1D)
    val gold = Color(0xFFFBBF24)
    val beard = Color(0xFFEA580C)
    val steel = Color(0xFF9CA3AF)
    val p = b.pulse(2.5f)

    drawCircle(gold.copy(alpha = 0.1f + 0.05f * p), u * 1.35f, c + Offset(0f, -u * 0.1f))
    // Zerfetzter Kriegsumhang
    val sway = sin(time * 2.2f) * u * 0.05f
    val cape = Path().apply {
        moveTo(c.x - u * 0.55f, b.shoulderY - u * 0.05f)
        lineTo(c.x + u * 0.55f, b.shoulderY - u * 0.05f)
        lineTo(c.x + u * 0.9f - dir * sway, b.footY - u * 0.05f)
        lineTo(c.x + u * 0.55f - dir * sway, b.footY - u * 0.15f)
        lineTo(c.x + u * 0.2f - dir * sway, b.footY)
        lineTo(c.x - u * 0.2f - dir * sway, b.footY - u * 0.14f)
        lineTo(c.x - u * 0.55f - dir * sway, b.footY)
        lineTo(c.x - u * 0.9f - dir * sway, b.footY - u * 0.08f)
        close()
    }
    drawPath(cape, red.shade(0.25f))

    dwarfLegs(b, Color(0xFF3F3F46), red.shade(0.2f), knee = gold.shade(0.2f))
    dwarfArm(b, b.hand, red.shade(0.15f))
    dwarfArm(b, b.offHand, red.shade(0.15f))
    // Roter Plattenpanzer mit Goldornamenten
    val torso = dwarfTorso(b, red, 1.22f)
    drawPath(torso, gold, style = Stroke(u * 0.045f))
    drawArc(gold, 200f, 140f, false, Offset(c.x - u * 0.35f, b.shoulderY + u * 0.08f), Size(u * 0.7f, u * 0.3f), style = Stroke(u * 0.04f))
    drawCircle(gold, u * 0.1f, Offset(c.x, b.shoulderY + u * 0.32f))
    drawCircle(Color(0xFFDC2626), u * 0.05f, Offset(c.x, b.shoulderY + u * 0.32f))
    artBelt(b, b.hipY, 0.62f, Color(0xFF292524), gold)
    // Fellmantel über den Schultern mit Goldschulterplatten
    for (side in listOf(-1f, 1f)) {
        for (k in 0..3) drawCircle(Color(0xFF57534E).bright(0.08f * k), u * (0.16f - k * 0.015f), Offset(c.x + side * u * (0.2f + k * 0.13f), b.shoulderY - u * 0.1f + k * u * 0.02f))
        artPauldron(b, side * 1.22f, red.bright(0.1f), gold, 1.3f)
        for (k in 0..1) drawPath(triangle3dw(Offset(c.x + side * u * (0.5f + k * 0.14f), b.shoulderY - u * 0.12f), u * 0.05f, u * 0.2f), gold.shade(0.1f))
    }

    dwarfFace(b, beard.shade(0.3f))
    dwarfBeard(b, beard, b.hipY + u * 0.08f, braids = gold, forked = true, width = 1.12f)
    dwarfHelm(b, steel.shade(0.15f), gold, horns = Color(0xFF57534E))

    battleAxe(b, b.hand, Color(0xFFE5E7EB), Color(0xFF292524), 1.2f, 1.2f, double = true) { top -> warlordAxeHead(b, top, gold) }
    artHand(b, b.hand, red.shade(0.1f), 0.14f)
    artHand(b, b.offHand, red.shade(0.1f), 0.14f)
}

private fun triangle3dw(base: Offset, halfW: Float, h: Float) = Path().apply {
    moveTo(base.x - halfW, base.y)
    lineTo(base.x, base.y - h)
    lineTo(base.x + halfW, base.y)
    close()
}

// --- Armbrustschütze ---

private fun DrawScope.drawCrossbowman(b: ArtBody) {
    val u = b.u
    val c = b.c
    val dir = b.dir
    val green = Color(0xFF3F6212)
    val beard = Color(0xFFDC2626)
    val iron = Color(0xFF9CA3AF)

    dwarfLegs(b, Color(0xFF57534E))
    // Bolzenköcher an der Hüfte
    val qx = c.x - dir * u * 0.55f
    drawRoundRect(ArtLeather, Offset(qx - u * 0.1f, b.hipY - u * 0.25f), Size(u * 0.2f, u * 0.45f), CornerRadius(u * 0.05f))
    for (i in 0..2) drawLine(Color(0xFFE7E5E4), Offset(qx - u * 0.06f + i * u * 0.06f, b.hipY - u * 0.25f), Offset(qx - u * 0.06f + i * u * 0.06f, b.hipY - u * 0.38f), u * 0.035f)
    // Arme nach vorn zur Armbrust
    val hand = b.hand + Offset(0f, -u * 0.15f)
    val offHand = Offset(c.x + dir * u * 0.25f, hand.y + u * 0.05f)
    dwarfArm(b, hand, green.shade(0.15f))
    dwarfArm(b, offHand, green.shade(0.15f))
    // Lederwams mit Bolzengurt
    dwarfTorso(b, green)
    drawLine(ArtLeather, Offset(c.x - dir * u * 0.48f, b.shoulderY - u * 0.05f), Offset(c.x + dir * u * 0.45f, b.hipY), u * 0.12f)
    for (k in 0..4) {
        val pt = Offset(c.x - dir * u * 0.4f, b.shoulderY) + (Offset(c.x + dir * u * 0.4f, b.hipY - u * 0.05f) - Offset(c.x - dir * u * 0.4f, b.shoulderY)) * (k / 4.5f)
        drawLine(iron.bright(0.2f), pt - Offset(0f, u * 0.07f), pt + Offset(0f, u * 0.07f), u * 0.035f)
    }
    artBelt(b, b.hipY, 0.56f, ArtLeather.shade(0.3f), iron)

    dwarfFace(b, beard.shade(0.3f))
    dwarfBeard(b, beard, b.hipY - u * 0.18f, braids = iron, width = 1.0f)
    // Kappenhelm ohne Nasenschutz, Zielbrille auf der Stirn
    dwarfHelm(b, Color(0xFF78716C), Color(0xFF57534E), nasal = false)
    val h = b.head
    val hr = b.headR
    drawCircle(Color(0xFF44403C), hr * 0.17f, Offset(h.x + dir * hr * 0.55f, h.y - hr * 0.45f))
    drawCircle(Color(0xFF7DD3FC), hr * 0.11f, Offset(h.x + dir * hr * 0.55f, h.y - hr * 0.45f))

    // Schwere Armbrust, waagrecht gehalten
    val stock0 = Offset(c.x - dir * u * 0.05f, hand.y)
    val front = Offset(c.x + dir * u * 1.0f, hand.y - u * 0.04f)
    drawLine(Color(0xFF78350F), stock0, front, u * 0.13f, StrokeCap.Round)
    drawLine(Color(0xFFA16207), stock0 + Offset(0f, -u * 0.04f), front + Offset(0f, -u * 0.04f), u * 0.03f)
    val prod = front + Offset(-dir * u * 0.08f, 0f)
    drawArc(Color(0xFF4B5563), if (dir > 0) 270f else 90f, 180f, false, Offset(prod.x - u * 0.14f, prod.y - u * 0.42f), Size(u * 0.28f, u * 0.84f), style = Stroke(u * 0.08f, cap = StrokeCap.Round))
    drawLine(Color(0xFFE7E5E4), Offset(prod.x, prod.y - u * 0.42f), Offset(c.x + dir * u * 0.3f, hand.y), u * 0.02f)
    drawLine(Color(0xFFE7E5E4), Offset(prod.x, prod.y + u * 0.42f), Offset(c.x + dir * u * 0.3f, hand.y), u * 0.02f)
    drawLine(iron, Offset(c.x + dir * u * 0.3f, hand.y - u * 0.06f), front + Offset(dir * u * 0.12f, -u * 0.06f), u * 0.04f)
    drawPath(Path().apply {
        moveTo(front.x + dir * u * 0.1f, front.y - u * 0.11f)
        lineTo(front.x + dir * u * 0.22f, front.y - u * 0.06f)
        lineTo(front.x + dir * u * 0.1f, front.y - u * 0.01f)
        close()
    }, iron.bright(0.3f))
    artHand(b, hand, DwarfSkin.shade(0.1f), 0.13f)
    artHand(b, offHand + Offset(dir * u * 0.3f, 0f), DwarfSkin.shade(0.1f), 0.13f)
}
