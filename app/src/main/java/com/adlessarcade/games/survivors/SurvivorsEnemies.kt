package com.adlessarcade.games.survivors

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.lerp
import kotlin.math.abs
import kotlin.math.sin

/** Färbt Zubehör wie den Körper: Themen-Tönung und Treffer-Blitz. */
internal class EnemyInk(private val theme: SurvivorTheme, private val flash: Float) {
    operator fun invoke(c: Color): Color = lerp(lerp(c, theme.enemyTint, theme.tintAmount), Color.White, flash * 0.7f)
}

private fun Color.darker(f: Float = 0.35f) = lerp(this, Color.Black, f)
private fun Color.lighter(f: Float = 0.3f) = lerp(this, Color.White, f)

/** Klinge von [from] nach [to] mit Spitze und Mittelgrat. */
private fun DrawScope.enemyBlade(from: Offset, to: Offset, halfWidth: Float, steel: Color) {
    val d = to - from
    val len = kotlin.math.sqrt(d.x * d.x + d.y * d.y)
    val n = Offset(-d.y / len, d.x / len) * halfWidth
    val shoulder = from + d * 0.82f
    drawPath(Path().apply {
        moveTo(from.x + n.x, from.y + n.y)
        lineTo(shoulder.x + n.x, shoulder.y + n.y)
        lineTo(to.x, to.y)
        lineTo(shoulder.x - n.x, shoulder.y - n.y)
        lineTo(from.x - n.x, from.y - n.y)
        close()
    }, steel)
    drawLine(steel.darker(0.25f), from, from + d * 0.8f, halfWidth * 0.35f)
}

// --- Schleim ---

/** Glibberiger Tropfen mit Pfütze, Glanz, Luftblasen und frechem Grinsen. */
internal fun DrawScope.drawSlimeFigure(c: Offset, r: Float, color: Color, ink: EnemyInk, time: Float, phase: Float) {
    val squish = 1f + 0.08f * sin(time * 6f + phase)
    val w = r * squish
    val top = c.y - r * 0.95f / squish
    val bottom = c.y + r * 0.6f
    // Pfütze am Boden
    drawOval(color.darker(0.25f), Offset(c.x - w * 1.1f, bottom - r * 0.2f), Size(w * 2.2f, r * 0.38f))
    // Weicher, runder Körper mit bauchigen Seiten
    val body = Path().apply {
        moveTo(c.x - w * 0.92f, bottom)
        cubicTo(c.x - w * 1.22f, c.y, c.x - w * 0.8f, top, c.x, top)
        cubicTo(c.x + w * 0.8f, top, c.x + w * 1.22f, c.y, c.x + w * 0.92f, bottom)
        quadraticTo(c.x, bottom + r * 0.12f, c.x - w * 0.92f, bottom)
        close()
    }
    drawPath(body, color)
    clipPath(body) {
        // Schatten rechts unten, heller Kern
        drawOval(color.darker(0.1f), Offset(c.x + w * 0.2f, c.y - r * 0.3f), Size(w * 1.3f, r * 1.3f))
        drawOval(color.lighter(0.18f), Offset(c.x - w * 0.6f, c.y - r * 0.55f), Size(w * 0.9f, r * 0.8f))
        // Luftblasen im Inneren
        for (i in 0..2) {
            val y = c.y + r * 0.35f - ((time * 0.4f + i * 0.33f + phase * 0.1f) % 1f) * r * 1.1f
            drawCircle(color.lighter(0.45f).copy(alpha = 0.7f), r * (0.06f + i * 0.02f), Offset(c.x + (i - 1) * w * 0.4f, y))
        }
    }
    // Glanzlichter
    drawOval(Color.White.copy(alpha = 0.75f), Offset(c.x - w * 0.62f, c.y - r * 0.55f), Size(w * 0.3f, r * 0.2f))
    drawCircle(Color.White.copy(alpha = 0.75f), r * 0.06f, Offset(c.x - w * 0.6f, c.y - r * 0.2f))
    // Gesicht: schräge Augen mit Lichtpunkt und Grinsen mit Zahn
    val eyeY = c.y - r * 0.12f
    for (side in listOf(-1f, 1f)) {
        val e = Offset(c.x + side * w * 0.32f, eyeY)
        drawOval(Color.White, e - Offset(r * 0.17f, r * 0.2f), Size(r * 0.34f, r * 0.4f))
        drawCircle(ink(Color(0xFF14532D)), r * 0.11f, e + Offset(0f, r * 0.04f))
        drawCircle(Color.White, r * 0.04f, e + Offset(-r * 0.04f, -r * 0.02f))
        drawLine(color.darker(0.5f), e + Offset(-side * r * 0.14f, -r * 0.2f), e + Offset(side * r * 0.2f, -r * 0.32f), r * 0.07f, StrokeCap.Round)
    }
    val mouth = Offset(c.x, c.y + r * 0.22f)
    drawArc(color.darker(0.55f), 10f, 160f, true, mouth - Offset(r * 0.3f, r * 0.14f), Size(r * 0.6f, r * 0.3f))
    drawPath(Path().apply {
        moveTo(mouth.x + r * 0.06f, mouth.y + r * 0.01f)
        lineTo(mouth.x + r * 0.16f, mouth.y + r * 0.01f)
        lineTo(mouth.x + r * 0.11f, mouth.y + r * 0.11f)
        close()
    }, Color.White)
    // Tropfen an der Seite
    val drip = ((time * 0.7f + phase) % 1f)
    drawCircle(color, r * 0.08f, Offset(c.x + w * 0.85f, bottom - r * 0.3f + drip * r * 0.25f))
}

// --- Fledermaus ---

/** Pelzige Fledermaus mit Hautflügeln (Fingerknochen, gezackter Saum), großen Ohren und Fangzähnen. */
internal fun DrawScope.drawBatFigure(c: Offset, r: Float, color: Color, ink: EnemyInk, time: Float, phase: Float) {
    val flap = sin(time * 14f + phase)
    val lift = flap * r * 0.5f
    val membrane = color.darker(0.35f)
    val bone = color.darker(0.55f)
    for (side in listOf(-1f, 1f)) {
        val shoulder = Offset(c.x + side * r * 0.45f, c.y - r * 0.1f)
        val wrist = Offset(c.x + side * r * 1.25f, c.y - r * 0.55f - lift)
        val tip = Offset(c.x + side * r * 1.85f, c.y - r * 0.1f - lift * 0.7f)
        val f2 = Offset(c.x + side * r * 1.55f, c.y + r * 0.35f - lift * 0.4f)
        val f3 = Offset(c.x + side * r * 1.0f, c.y + r * 0.5f - lift * 0.2f)
        val wing = Path().apply {
            moveTo(shoulder.x, shoulder.y)
            lineTo(wrist.x, wrist.y)
            lineTo(tip.x, tip.y)
            quadraticTo(c.x + side * r * 1.55f, c.y + r * 0.05f - lift * 0.55f, f2.x, f2.y)
            quadraticTo(c.x + side * r * 1.2f, c.y + r * 0.3f - lift * 0.3f, f3.x, f3.y)
            quadraticTo(c.x + side * r * 0.75f, c.y + r * 0.3f, c.x + side * r * 0.4f, c.y + r * 0.35f)
            close()
        }
        drawPath(wing, membrane)
        // Fingerknochen
        drawLine(bone, shoulder, wrist, r * 0.09f, StrokeCap.Round)
        for (f in listOf(tip, f2, f3)) drawLine(bone, wrist, f, r * 0.05f, StrokeCap.Round)
        drawPath(Path().apply {
            moveTo(wrist.x, wrist.y)
            lineTo(wrist.x + side * r * 0.08f, wrist.y - r * 0.2f)
            lineTo(wrist.x + side * r * 0.12f, wrist.y - r * 0.02f)
            close()
        }, bone)
    }
    // Ohren mit rosa Innenseite
    for (side in listOf(-1f, 1f)) {
        val base = Offset(c.x + side * r * 0.3f, c.y - r * 0.5f)
        drawPath(Path().apply {
            moveTo(base.x - side * r * 0.2f, base.y + r * 0.1f)
            lineTo(base.x + side * r * 0.12f, base.y - r * 0.6f)
            lineTo(base.x + side * r * 0.3f, base.y + r * 0.12f)
            close()
        }, color)
        drawPath(Path().apply {
            moveTo(base.x - side * r * 0.08f, base.y + r * 0.04f)
            lineTo(base.x + side * r * 0.12f, base.y - r * 0.42f)
            lineTo(base.x + side * r * 0.2f, base.y + r * 0.06f)
            close()
        }, ink(Color(0xFFF9A8D4)))
    }
    // Pelziger Körper mit hellem Bauch
    drawCircle(color, r * 0.68f, c)
    for (i in -2..2) drawCircle(color, r * 0.16f, Offset(c.x + i * r * 0.2f, c.y - r * 0.6f + kotlin.math.abs(i) * r * 0.08f))
    drawOval(color.lighter(0.25f), Offset(c.x - r * 0.38f, c.y - r * 0.05f), Size(r * 0.76f, r * 0.7f))
    for (i in -1..1) drawLine(color.lighter(0.1f), Offset(c.x + i * r * 0.15f, c.y + r * 0.1f), Offset(c.x + i * r * 0.18f, c.y + r * 0.25f), r * 0.04f, StrokeCap.Round)
    // Füßchen
    for (side in listOf(-1f, 1f)) drawLine(bone, Offset(c.x + side * r * 0.18f, c.y + r * 0.6f), Offset(c.x + side * r * 0.24f, c.y + r * 0.8f), r * 0.07f, StrokeCap.Round)
    // Leuchtende Augen, Nase und Fangzähne
    for (side in listOf(-1f, 1f)) {
        val e = Offset(c.x + side * r * 0.25f, c.y - r * 0.2f)
        drawCircle(ink(Color(0xFFFDE047)), r * 0.15f, e)
        drawCircle(ink(Color(0xFFDC2626)), r * 0.08f, e + Offset(0f, r * 0.02f))
        drawCircle(Color.White, r * 0.035f, e + Offset(-r * 0.04f, -r * 0.05f))
    }
    drawCircle(color.darker(0.5f), r * 0.05f, Offset(c.x, c.y - r * 0.03f))
    drawArc(color.darker(0.5f), 20f, 140f, false, Offset(c.x - r * 0.2f, c.y - r * 0.05f), Size(r * 0.4f, r * 0.22f), style = Stroke(r * 0.05f, cap = StrokeCap.Round))
    for (side in listOf(-1f, 1f)) {
        drawPath(Path().apply {
            moveTo(c.x + side * r * 0.06f, c.y + r * 0.13f)
            lineTo(c.x + side * r * 0.15f, c.y + r * 0.13f)
            lineTo(c.x + side * r * 0.1f, c.y + r * 0.27f)
            close()
        }, Color.White)
    }
}

// --- Skelett ---

/** Knochen von [a] nach [b] mit verdickten Gelenkenden. */
private fun DrawScope.bone(a: Offset, b: Offset, width: Float, color: Color) {
    drawLine(color, a, b, width, StrokeCap.Round)
    drawCircle(color, width * 0.75f, a)
    drawCircle(color, width * 0.75f, b)
}

/**
 * Gemeinsamer Skelettkörper (Skelett, Knochenschütze): Schädel mit glühenden Augen, Kiefer,
 * Wirbelsäule, Rippen, Becken und Gliedmaßen. Liefert die Handpositionen für Waffen.
 */
private fun DrawScope.skeletonBody(c: Offset, r: Float, boneColor: Color, eyeGlow: Color, ink: EnemyInk, time: Float, phase: Float): Pair<Offset, Offset> {
    val shade = boneColor.darker(0.3f)
    val step = sin(time * 8f + phase) * r * 0.1f
    val shoulderY = c.y - r * 0.12f
    val hipY = c.y + r * 0.38f
    // Beine mit Knien und Füßen
    for (side in listOf(-1f, 1f)) {
        val lift = if (side > 0) step else -step
        val hip = Offset(c.x + side * r * 0.16f, hipY)
        val knee = Offset(c.x + side * r * 0.2f, c.y + r * 0.68f - lift.coerceAtLeast(0f) * 0.5f)
        val foot = Offset(c.x + side * r * 0.2f, c.y + r * 0.98f - lift.coerceAtLeast(0f))
        bone(hip, knee, r * 0.09f, boneColor)
        bone(knee, foot, r * 0.08f, boneColor)
        drawOval(boneColor, Offset(foot.x - r * 0.1f, foot.y - r * 0.03f), Size(r * 0.22f, r * 0.09f))
    }
    // Becken
    drawPath(Path().apply {
        moveTo(c.x - r * 0.26f, hipY - r * 0.1f)
        lineTo(c.x + r * 0.26f, hipY - r * 0.1f)
        lineTo(c.x + r * 0.16f, hipY + r * 0.1f)
        lineTo(c.x - r * 0.16f, hipY + r * 0.1f)
        close()
    }, boneColor)
    drawCircle(shade, r * 0.06f, Offset(c.x - r * 0.1f, hipY))
    drawCircle(shade, r * 0.06f, Offset(c.x + r * 0.1f, hipY))
    // Wirbelsäule
    drawLine(boneColor, Offset(c.x, shoulderY), Offset(c.x, hipY - r * 0.08f), r * 0.08f)
    for (i in 0..3) drawLine(shade, Offset(c.x - r * 0.05f, hipY - r * (0.12f + i * 0.1f)), Offset(c.x + r * 0.05f, hipY - r * (0.12f + i * 0.1f)), r * 0.02f)
    // Rippen und Schlüsselbein
    for (i in 0..2) {
        val y = shoulderY + r * (0.06f + i * 0.1f)
        val w = r * (0.3f - i * 0.04f)
        drawArc(boneColor, 200f, 140f, false, Offset(c.x - w, y - r * 0.02f), Size(w * 2f, r * 0.24f), style = Stroke(r * 0.055f, cap = StrokeCap.Round))
    }
    drawLine(boneColor, Offset(c.x - r * 0.32f, shoulderY - r * 0.02f), Offset(c.x + r * 0.32f, shoulderY - r * 0.02f), r * 0.07f, StrokeCap.Round)
    // Arme
    val swing = sin(time * 8f + phase) * r * 0.08f
    val hands = listOf(-1f, 1f).map { side ->
        val shoulder = Offset(c.x + side * r * 0.33f, shoulderY)
        val elbow = Offset(c.x + side * r * 0.45f, c.y + r * 0.12f + side * swing)
        val hand = Offset(c.x + side * r * 0.52f, c.y + r * 0.36f + side * swing)
        bone(shoulder, elbow, r * 0.07f, boneColor)
        bone(elbow, hand, r * 0.06f, boneColor)
        drawCircle(boneColor, r * 0.07f, hand)
        hand
    }
    // Schädel mit Augenhöhlen, Nasenloch, Kiefer und Zähnen
    val head = Offset(c.x, c.y - r * 0.56f)
    val hr = r * 0.4f
    drawCircle(boneColor, hr, head)
    drawRoundRect(boneColor, Offset(head.x - hr * 0.62f, head.y + hr * 0.35f), Size(hr * 1.24f, hr * 0.62f), androidx.compose.ui.geometry.CornerRadius(hr * 0.2f))
    drawArc(shade.copy(alpha = 0.5f), 20f, 140f, false, Offset(head.x - hr * 0.9f, head.y - hr * 0.9f), Size(hr * 1.8f, hr * 1.8f), style = Stroke(hr * 0.12f))
    drawArc(Color.White.copy(alpha = 0.5f), 200f, 60f, false, Offset(head.x - hr * 0.75f, head.y - hr * 0.8f), Size(hr * 1.5f, hr * 1.5f), style = Stroke(hr * 0.1f, cap = StrokeCap.Round))
    val glow = 0.7f + 0.3f * sin(time * 5f + phase)
    for (side in listOf(-1f, 1f)) {
        val e = Offset(head.x + side * hr * 0.38f, head.y + hr * 0.05f)
        drawOval(ink(Color(0xFF1C1917)), e - Offset(hr * 0.27f, hr * 0.3f), Size(hr * 0.54f, hr * 0.56f))
        drawCircle(eyeGlow.copy(alpha = glow), hr * 0.12f, e + Offset(0f, hr * 0.04f))
    }
    drawPath(Path().apply {
        moveTo(head.x, head.y + hr * 0.3f)
        lineTo(head.x - hr * 0.1f, head.y + hr * 0.5f)
        lineTo(head.x + hr * 0.1f, head.y + hr * 0.5f)
        close()
    }, ink(Color(0xFF1C1917)))
    drawLine(shade, Offset(head.x - hr * 0.5f, head.y + hr * 0.68f), Offset(head.x + hr * 0.5f, head.y + hr * 0.68f), hr * 0.05f)
    for (i in -2..2) drawLine(shade, Offset(head.x + i * hr * 0.2f, head.y + hr * 0.56f), Offset(head.x + i * hr * 0.2f, head.y + hr * 0.82f), hr * 0.05f)
    // Riss im Schädel
    drawLine(shade, Offset(head.x + hr * 0.2f, head.y - hr * 0.95f), Offset(head.x + hr * 0.32f, head.y - hr * 0.6f), hr * 0.05f, StrokeCap.Round)
    drawLine(shade, Offset(head.x + hr * 0.32f, head.y - hr * 0.6f), Offset(head.x + hr * 0.22f, head.y - hr * 0.42f), hr * 0.05f, StrokeCap.Round)
    return hands[0] to hands[1]
}

/** Skelettkrieger mit Lendentuch, rostigem Schwert und zerbrochenem Holzschild. */
internal fun DrawScope.drawSkeletonFigure(c: Offset, r: Float, color: Color, ink: EnemyInk, time: Float, phase: Float) {
    // Zerfetztes Lendentuch hinter dem Becken
    val hipY = c.y + r * 0.38f
    drawPath(Path().apply {
        moveTo(c.x - r * 0.28f, hipY - r * 0.12f)
        lineTo(c.x + r * 0.28f, hipY - r * 0.12f)
        lineTo(c.x + r * 0.22f, hipY + r * 0.3f)
        lineTo(c.x + r * 0.08f, hipY + r * 0.18f)
        lineTo(c.x - r * 0.04f, hipY + r * 0.34f)
        lineTo(c.x - r * 0.14f, hipY + r * 0.2f)
        lineTo(c.x - r * 0.24f, hipY + r * 0.3f)
        close()
    }, ink(Color(0xFF7F1D1D)))
    val (left, right) = skeletonBody(c, r, color, ink(Color(0xFFEF4444)), ink, time, phase)
    // Rostiges Schwert in der rechten Hand
    val tip = right + Offset(r * 0.28f, -r * 0.85f)
    enemyBlade(right + Offset(r * 0.03f, -r * 0.1f), tip, r * 0.06f, ink(Color(0xFFA8A29E)))
    drawCircle(ink(Color(0xFFB45309)), r * 0.04f, right + Offset(r * 0.12f, -r * 0.4f))
    drawCircle(ink(Color(0xFFB45309)), r * 0.03f, right + Offset(r * 0.2f, -r * 0.6f))
    drawLine(ink(Color(0xFF57534E)), right + Offset(-r * 0.14f, -r * 0.06f), right + Offset(r * 0.18f, -r * 0.14f), r * 0.07f, StrokeCap.Round)
    drawCircle(color, r * 0.07f, right)
    // Zerbrochener Rundschild am linken Arm
    val sc = left + Offset(-r * 0.05f, -r * 0.08f)
    val shield = Path().apply {
        addOval(androidx.compose.ui.geometry.Rect(sc - Offset(r * 0.3f, r * 0.3f), Size(r * 0.6f, r * 0.6f)))
    }
    clipPath(Path().apply {
        moveTo(sc.x - r * 0.4f, sc.y - r * 0.4f)
        lineTo(sc.x + r * 0.25f, sc.y - r * 0.4f)
        lineTo(sc.x + r * 0.05f, sc.y - r * 0.05f)
        lineTo(sc.x + r * 0.3f, sc.y + r * 0.1f)
        lineTo(sc.x + r * 0.2f, sc.y + r * 0.4f)
        lineTo(sc.x - r * 0.4f, sc.y + r * 0.4f)
        close()
    }) {
        drawPath(shield, ink(Color(0xFF78350F)))
        for (i in -1..1) drawLine(ink(Color(0xFF451A03)), Offset(sc.x + i * r * 0.15f, sc.y - r * 0.3f), Offset(sc.x + i * r * 0.15f, sc.y + r * 0.3f), r * 0.025f)
        drawPath(shield, ink(Color(0xFF57534E)), style = Stroke(r * 0.05f))
    }
    drawCircle(ink(Color(0xFF78716C)), r * 0.06f, sc)
}

// --- Geist ---

/** Durchscheinender Geist mit wehendem Schweif, erhobenen Ärmchen, heulendem Mund und Kettenrest. */
internal fun DrawScope.drawGhostFigure(c: Offset, r: Float, color: Color, ink: EnemyInk, time: Float, phase: Float) {
    val wave = sin(time * 5f + phase)
    val hover = sin(time * 2.5f + phase) * r * 0.08f
    val cc = c + Offset(0f, hover)
    val body = color.copy(alpha = 0.78f)
    // Schweif, der in eine Spitze ausläuft
    val outline = Path().apply {
        moveTo(cc.x - r * 0.85f, cc.y - r * 0.1f)
        quadraticTo(cc.x - r * 0.9f, cc.y - r * 1.15f, cc.x, cc.y - r * 1.15f)
        quadraticTo(cc.x + r * 0.9f, cc.y - r * 1.15f, cc.x + r * 0.85f, cc.y - r * 0.1f)
        quadraticTo(cc.x + r * 0.8f, cc.y + r * 0.45f, cc.x + r * 0.45f + wave * r * 0.1f, cc.y + r * 0.6f)
        quadraticTo(cc.x + r * 0.2f, cc.y + r * 0.5f, cc.x + wave * r * 0.25f, cc.y + r * 0.95f)
        quadraticTo(cc.x - r * 0.15f, cc.y + r * 0.55f, cc.x - r * 0.45f - wave * r * 0.1f, cc.y + r * 0.62f)
        quadraticTo(cc.x - r * 0.8f, cc.y + r * 0.45f, cc.x - r * 0.85f, cc.y - r * 0.1f)
        close()
    }
    // Erhobene Ärmchen, mit dem Körper vereinigt, damit sich die Transparenz nicht überlagert
    var tail = outline
    for (side in listOf(-1f, 1f)) {
        val tip = Offset(cc.x + side * r * 1.15f, cc.y - r * 0.55f + side * wave * r * 0.08f)
        val arm = Path().apply {
            moveTo(cc.x + side * r * 0.7f, cc.y - r * 0.45f)
            quadraticTo(cc.x + side * r * 1.0f, cc.y - r * 0.4f, tip.x, tip.y)
            quadraticTo(cc.x + side * r * 1.05f, cc.y - r * 0.15f, cc.x + side * r * 0.75f, cc.y - r * 0.05f)
            close()
        }
        tail = Path().apply { op(tail, arm, PathOperation.Union) }
    }
    drawPath(tail, body)
    clipPath(tail) {
        drawOval(color.lighter(0.4f).copy(alpha = 0.5f), Offset(cc.x - r * 0.55f, cc.y - r * 1.0f), Size(r * 0.9f, r * 1.1f))
        drawOval(color.darker(0.2f).copy(alpha = 0.4f), Offset(cc.x + r * 0.1f, cc.y - r * 0.2f), Size(r, r))
    }
    // Kettenrest am rechten Arm
    val cuff = Offset(cc.x + r * 1.0f, cc.y - r * 0.38f)
    drawRoundRect(ink(Color(0xFF6B7280)), Offset(cuff.x - r * 0.1f, cuff.y - r * 0.08f), Size(r * 0.2f, r * 0.16f), androidx.compose.ui.geometry.CornerRadius(r * 0.04f))
    for (k in 0..2) {
        val link = cuff + Offset(r * 0.05f + wave * r * 0.03f * k, r * (0.16f + k * 0.13f))
        drawOval(ink(Color(0xFF9CA3AF)), link - Offset(r * 0.05f, r * 0.07f), Size(r * 0.1f, r * 0.14f), style = Stroke(r * 0.035f))
    }
    // Hohle Augen mit blauem Glimmen, heulender Mund
    val eyeY = cc.y - r * 0.55f
    for (side in listOf(-1f, 1f)) {
        val e = Offset(cc.x + side * r * 0.3f, eyeY)
        drawOval(ink(Color(0xFF1E1B4B)), e - Offset(r * 0.15f, r * 0.2f), Size(r * 0.3f, r * 0.38f))
        drawCircle(ink(Color(0xFF67E8F9)).copy(alpha = 0.6f + 0.3f * sin(time * 4f + phase)), r * 0.07f, e + Offset(0f, r * 0.03f))
        drawLine(ink(Color(0xFF1E1B4B)).copy(alpha = 0.7f), e + Offset(-side * r * 0.1f, -r * 0.2f), e + Offset(side * r * 0.18f, -r * 0.31f), r * 0.06f, StrokeCap.Round)
    }
    val mouthH = r * (0.26f + 0.06f * wave)
    drawOval(ink(Color(0xFF1E1B4B)), Offset(cc.x - r * 0.14f, cc.y - r * 0.18f), Size(r * 0.28f, mouthH))
    drawOval(ink(Color(0xFF3730A3)).copy(alpha = 0.7f), Offset(cc.x - r * 0.08f, cc.y - r * 0.18f + mouthH * 0.45f), Size(r * 0.16f, mouthH * 0.45f))
}

// --- Ork ---

/** Massiger Ork mit Hauern, Haarknoten, Fellschulter, Gürtel und Hackbeil. */
internal fun DrawScope.drawOrcFigure(c: Offset, r: Float, color: Color, ink: EnemyInk, time: Float, phase: Float) {
    val leather = ink(Color(0xFF5B3A1E))
    val fur = ink(Color(0xFF78716C))
    val step = sin(time * 6f + phase) * r * 0.06f
    val shoulderY = c.y - r * 0.32f
    val hipY = c.y + r * 0.38f
    // Stämmige Beine mit Fußwickeln
    for (side in listOf(-1f, 1f)) {
        val lift = (if (side > 0) step else -step).coerceAtLeast(0f)
        val x = c.x + side * r * 0.32f
        drawRoundRect(color.darker(0.15f), Offset(x - r * 0.17f, hipY - r * 0.05f), Size(r * 0.34f, r * 0.55f - lift), androidx.compose.ui.geometry.CornerRadius(r * 0.12f))
        drawRoundRect(leather, Offset(x - r * 0.2f, c.y + r * 0.78f - lift), Size(r * 0.4f, r * 0.2f), androidx.compose.ui.geometry.CornerRadius(r * 0.07f))
        drawLine(leather.darker(0.3f), Offset(x - r * 0.18f, c.y + r * 0.84f - lift), Offset(x + r * 0.18f, c.y + r * 0.88f - lift), r * 0.03f)
    }
    // Lendenschurz
    drawPath(Path().apply {
        moveTo(c.x - r * 0.42f, hipY - r * 0.05f)
        lineTo(c.x + r * 0.42f, hipY - r * 0.05f)
        lineTo(c.x + r * 0.3f, hipY + r * 0.35f)
        lineTo(c.x, hipY + r * 0.25f)
        lineTo(c.x - r * 0.3f, hipY + r * 0.35f)
        close()
    }, leather)
    // Arme (dick, hinter dem Rumpf ansetzend)
    val swing = sin(time * 6f + phase) * r * 0.06f
    val leftHand = Offset(c.x - r * 0.95f, c.y + r * 0.25f - swing)
    val rightHand = Offset(c.x + r * 0.95f, c.y + r * 0.2f + swing)
    for (hand in listOf(leftHand, rightHand)) {
        val s = if (hand.x > c.x) 1f else -1f
        drawLine(color.darker(0.08f), Offset(c.x + s * r * 0.6f, shoulderY + r * 0.1f), hand, r * 0.32f, StrokeCap.Round)
        drawLine(leather, hand - Offset(s * r * 0.08f, r * 0.18f), hand - Offset(s * r * 0.02f, r * 0.04f), r * 0.34f)
    }
    // Rumpf: breite Schultern, Bauch
    drawPath(Path().apply {
        moveTo(c.x - r * 0.72f, shoulderY)
        quadraticTo(c.x, shoulderY - r * 0.22f, c.x + r * 0.72f, shoulderY)
        quadraticTo(c.x + r * 0.62f, c.y + r * 0.15f, c.x + r * 0.46f, hipY)
        lineTo(c.x - r * 0.46f, hipY)
        quadraticTo(c.x - r * 0.62f, c.y + r * 0.15f, c.x - r * 0.72f, shoulderY)
        close()
    }, color)
    // Brust- und Bauchkontur
    drawArc(color.darker(0.2f), 20f, 140f, false, Offset(c.x - r * 0.4f, shoulderY + r * 0.02f), Size(r * 0.4f, r * 0.3f), style = Stroke(r * 0.03f))
    drawArc(color.darker(0.2f), 20f, 140f, false, Offset(c.x, shoulderY + r * 0.02f), Size(r * 0.4f, r * 0.3f), style = Stroke(r * 0.03f))
    drawOval(color.lighter(0.12f), Offset(c.x - r * 0.28f, c.y + r * 0.0f), Size(r * 0.56f, r * 0.36f))
    // Narben
    drawLine(color.darker(0.35f), Offset(c.x + r * 0.15f, shoulderY + r * 0.35f), Offset(c.x + r * 0.38f, shoulderY + r * 0.15f), r * 0.03f, StrokeCap.Round)
    for (i in 0..2) drawLine(color.darker(0.35f), Offset(c.x + r * (0.2f + i * 0.07f), shoulderY + r * (0.2f + i * 0.04f)), Offset(c.x + r * (0.24f + i * 0.07f), shoulderY + r * (0.3f + i * 0.04f)), r * 0.02f)
    // Gürtel mit Schädelschnalle
    drawLine(leather.darker(0.2f), Offset(c.x - r * 0.48f, hipY - r * 0.05f), Offset(c.x + r * 0.48f, hipY - r * 0.05f), r * 0.12f)
    drawCircle(ink(Color(0xFFE7E5E4)), r * 0.1f, Offset(c.x, hipY - r * 0.06f))
    for (side in listOf(-1f, 1f)) drawCircle(ink(Color(0xFF1C1917)), r * 0.03f, Offset(c.x + side * r * 0.04f, hipY - r * 0.07f))
    // Fellschulter links mit Riemen
    drawLine(leather, Offset(c.x - r * 0.55f, shoulderY), Offset(c.x + r * 0.42f, hipY - r * 0.1f), r * 0.08f)
    for (i in 0..4) drawCircle(fur.lighter(if (i % 2 == 0) 0.1f else 0f), r * 0.16f, Offset(c.x - r * (0.8f - i * 0.12f), shoulderY - r * 0.02f + kotlin.math.abs(i - 2) * r * 0.05f))
    // Kopf: tief zwischen den Schultern, Unterbiss mit Hauern
    val head = Offset(c.x, c.y - r * 0.55f)
    val hr = r * 0.36f
    drawRoundRect(color, Offset(head.x - hr, head.y - hr * 0.9f), Size(hr * 2f, hr * 1.9f), androidx.compose.ui.geometry.CornerRadius(hr * 0.7f))
    // Ohren
    for (side in listOf(-1f, 1f)) drawPath(Path().apply {
        moveTo(head.x + side * hr * 0.9f, head.y - hr * 0.3f)
        lineTo(head.x + side * hr * 1.55f, head.y - hr * 0.55f)
        lineTo(head.x + side * hr * 0.95f, head.y + hr * 0.15f)
        close()
    }, color.darker(0.1f))
    // Haarknoten
    drawCircle(ink(Color(0xFF1C1917)), hr * 0.3f, head + Offset(0f, -hr * 1.05f))
    drawRoundRect(ink(Color(0xFF1C1917)), Offset(head.x - hr * 0.15f, head.y - hr * 1.3f), Size(hr * 0.3f, hr * 0.5f), androidx.compose.ui.geometry.CornerRadius(hr * 0.1f))
    drawLine(ink(Color(0xFFB45309)), Offset(head.x - hr * 0.2f, head.y - hr * 0.88f), Offset(head.x + hr * 0.2f, head.y - hr * 0.88f), hr * 0.08f)
    // Wulstige Stirn, rote Augen
    drawLine(color.darker(0.25f), Offset(head.x - hr * 0.7f, head.y - hr * 0.3f), Offset(head.x + hr * 0.7f, head.y - hr * 0.3f), hr * 0.22f, StrokeCap.Round)
    for (side in listOf(-1f, 1f)) {
        val e = Offset(head.x + side * hr * 0.38f, head.y - hr * 0.08f)
        drawOval(ink(Color(0xFFFEF3C7)), e - Offset(hr * 0.18f, hr * 0.12f), Size(hr * 0.36f, hr * 0.24f))
        drawCircle(ink(Color(0xFFDC2626)), hr * 0.09f, e)
        drawLine(color.darker(0.45f), e + Offset(-side * hr * 0.1f, -hr * 0.12f), e + Offset(side * hr * 0.24f, -hr * 0.24f), hr * 0.09f, StrokeCap.Round)
    }
    // Breite Nase mit Ring
    drawOval(color.darker(0.15f), Offset(head.x - hr * 0.22f, head.y + hr * 0.1f), Size(hr * 0.44f, hr * 0.26f))
    drawCircle(ink(Color(0xFFFACC15)), hr * 0.1f, Offset(head.x, head.y + hr * 0.38f), style = Stroke(hr * 0.05f))
    // Breites Maul mit Hauern
    drawRoundRect(color.darker(0.5f), Offset(head.x - hr * 0.5f, head.y + hr * 0.52f), Size(hr * 1.0f, hr * 0.16f), androidx.compose.ui.geometry.CornerRadius(hr * 0.08f))
    for (side in listOf(-1f, 1f)) drawPath(Path().apply {
        moveTo(head.x + side * hr * 0.32f, head.y + hr * 0.66f)
        lineTo(head.x + side * hr * 0.5f, head.y + hr * 0.66f)
        lineTo(head.x + side * hr * 0.44f, head.y + hr * 0.18f)
        close()
    }, ink(Color(0xFFF5F5F4)))
    // Hackbeil in der rechten Hand
    val haft = rightHand + Offset(r * 0.08f, -r * 0.8f)
    drawLine(leather.darker(0.2f), rightHand + Offset(-r * 0.02f, r * 0.15f), haft, r * 0.1f, StrokeCap.Round)
    drawPath(Path().apply {
        moveTo(haft.x - r * 0.04f, haft.y + r * 0.05f)
        lineTo(haft.x + r * 0.45f, haft.y - r * 0.05f)
        quadraticTo(haft.x + r * 0.55f, haft.y + r * 0.2f, haft.x + r * 0.42f, haft.y + r * 0.42f)
        lineTo(haft.x - r * 0.02f, haft.y + r * 0.38f)
        close()
    }, ink(Color(0xFF9CA3AF)))
    drawLine(ink(Color(0xFFE5E7EB)), Offset(haft.x + r * 0.46f, haft.y), Offset(haft.x + r * 0.44f, haft.y + r * 0.38f), r * 0.04f, StrokeCap.Round)
    drawCircle(ink(Color(0xFF7F1D1D)), r * 0.04f, Offset(haft.x + r * 0.25f, haft.y + r * 0.25f))
    drawCircle(color.darker(0.05f), r * 0.14f, rightHand)
    drawCircle(color.darker(0.05f), r * 0.14f, leftHand)
}

// --- Höhlentroll ---

/** Buckliger Höhlentroll: Steinhaut mit Moos und Pilzen, lange Arme, Knollennase, Hauer und Nagelkeule. */
internal fun DrawScope.drawTrollFigure(c: Offset, r: Float, color: Color, ink: EnemyInk, time: Float, phase: Float) {
    val moss = ink(Color(0xFF4D7C0F))
    val wood = ink(Color(0xFF5B3A1E))
    val rag = ink(Color(0xFF57534E))
    val breathe = sin(time * 2f + phase) * r * 0.02f
    val swing = sin(time * 3f + phase) * r * 0.05f
    // Kurze, krumme Beine mit breiten Füßen
    for (side in listOf(-1f, 1f)) {
        val x = c.x + side * r * 0.38f
        drawRoundRect(color.darker(0.18f), Offset(x - r * 0.18f, c.y + r * 0.25f), Size(r * 0.36f, r * 0.45f), androidx.compose.ui.geometry.CornerRadius(r * 0.15f))
        drawOval(color.darker(0.25f), Offset(x - r * 0.26f + side * r * 0.04f, c.y + r * 0.6f), Size(r * 0.52f, r * 0.2f))
        for (t in -1..1) drawCircle(color.darker(0.4f), r * 0.035f, Offset(x + side * r * 0.04f + t * r * 0.12f, c.y + r * 0.66f))
    }
    // Buckel mit Moos und Pilzen (hinter dem Körper)
    drawCircle(color.darker(0.1f), r * 0.62f, Offset(c.x, c.y - r * 0.55f))
    drawArc(moss, 190f, 160f, true, Offset(c.x - r * 0.6f, c.y - r * 1.15f), Size(r * 1.2f, r * 0.7f))
    for ((dx, s) in listOf(-0.4f to 0.15f, 0.4f to 0.13f, 0.18f to 0.1f)) {
        val p = Offset(c.x + dx * r, c.y - r * 1.05f + (0.16f - s) * r)
        drawLine(ink(Color(0xFFF5F5F4)), p, p + Offset(0f, r * s), r * s * 0.4f)
        drawArc(ink(Color(0xFFDC2626)), 180f, 180f, true, p - Offset(r * s, r * s * 0.6f), Size(r * s * 2f, r * s * 1.2f))
        drawCircle(ink(Color(0xFFFEF3C7)), r * s * 0.18f, p + Offset(-r * s * 0.35f, -r * s * 0.3f))
    }
    // Lange Arme: links hängend, rechts mit Keule
    val leftHand = Offset(c.x - r * 1.15f, c.y + r * 0.4f + swing)
    val rightHand = Offset(c.x + r * 1.1f, c.y + r * 0.15f - swing)
    for (hand in listOf(leftHand, rightHand)) {
        val s = if (hand.x > c.x) 1f else -1f
        val shoulder = Offset(c.x + s * r * 0.62f, c.y - r * 0.35f)
        val elbow = Offset(c.x + s * r * 1.0f, c.y - r * 0.05f)
        drawLine(color.darker(0.05f), shoulder, elbow, r * 0.34f, StrokeCap.Round)
        drawLine(color.darker(0.05f), elbow, hand, r * 0.3f, StrokeCap.Round)
        drawCircle(moss, r * 0.1f, shoulder + Offset(s * r * 0.05f, -r * 0.08f))
    }
    // Riesige Nagelkeule
    val clubTip = rightHand + Offset(r * 0.35f, -r * 1.0f)
    drawLine(wood, rightHand + Offset(-r * 0.05f, r * 0.2f), clubTip, r * 0.16f, StrokeCap.Round)
    drawLine(wood, clubTip + Offset(-r * 0.1f, r * 0.28f), clubTip, r * 0.34f, StrokeCap.Round)
    drawLine(wood.lighter(0.15f), clubTip + Offset(-r * 0.14f, r * 0.3f), clubTip + Offset(-r * 0.04f, r * 0.02f), r * 0.06f, StrokeCap.Round)
    for (k in 0..4) {
        val p = clubTip + Offset(-r * 0.1f * (k / 4f), r * 0.28f * (k / 4f))
        val s = if (k % 2 == 0) 1f else -1f
        drawLine(ink(Color(0xFF9CA3AF)), p + Offset(s * r * 0.12f, 0f), p + Offset(s * r * 0.26f, -r * 0.04f), r * 0.035f, StrokeCap.Round)
    }
    // Massiger Rumpf mit Bauch
    val body = Path().apply {
        moveTo(c.x - r * 0.7f, c.y - r * 0.5f)
        quadraticTo(c.x, c.y - r * 0.85f, c.x + r * 0.7f, c.y - r * 0.5f)
        quadraticTo(c.x + r * 0.85f + breathe, c.y + r * 0.1f, c.x + r * 0.55f, c.y + r * 0.42f)
        lineTo(c.x - r * 0.55f, c.y + r * 0.42f)
        quadraticTo(c.x - r * 0.85f - breathe, c.y + r * 0.1f, c.x - r * 0.7f, c.y - r * 0.5f)
        close()
    }
    drawPath(body, color)
    clipPath(body) {
        drawOval(color.lighter(0.15f), Offset(c.x - r * 0.45f, c.y - r * 0.25f), Size(r * 0.9f + breathe, r * 0.65f))
        drawOval(Color.Black.copy(alpha = 0.14f), Offset(c.x + r * 0.3f, c.y - r * 0.6f), Size(r * 0.9f, r * 1.3f))
        // Steinflecken und Warzen
        for ((dx, dy) in listOf(-0.5f to -0.35f, 0.42f to -0.2f, -0.3f to 0.2f)) drawCircle(color.darker(0.2f), r * 0.07f, Offset(c.x + dx * r, c.y + dy * r))
    }
    // Lumpen um die Hüfte mit Strick
    drawPath(Path().apply {
        moveTo(c.x - r * 0.6f, c.y + r * 0.22f)
        lineTo(c.x + r * 0.6f, c.y + r * 0.22f)
        lineTo(c.x + r * 0.5f, c.y + r * 0.6f)
        lineTo(c.x + r * 0.25f, c.y + r * 0.48f)
        lineTo(c.x + r * 0.05f, c.y + r * 0.64f)
        lineTo(c.x - r * 0.2f, c.y + r * 0.5f)
        lineTo(c.x - r * 0.48f, c.y + r * 0.62f)
        close()
    }, rag)
    drawLine(wood, Offset(c.x - r * 0.62f, c.y + r * 0.24f), Offset(c.x + r * 0.62f, c.y + r * 0.24f), r * 0.06f)
    // Kleiner Kopf vorn auf dem Buckel
    val head = Offset(c.x, c.y - r * 0.62f)
    val hr = r * 0.32f
    for (side in listOf(-1f, 1f)) drawPath(Path().apply {
        moveTo(head.x + side * hr * 0.85f, head.y - hr * 0.2f)
        lineTo(head.x + side * hr * 1.5f, head.y - hr * 0.1f)
        lineTo(head.x + side * hr * 0.9f, head.y + hr * 0.3f)
        close()
    }, color.darker(0.1f))
    drawCircle(color, hr, head)
    // Hornstummel
    for (side in listOf(-1f, 1f)) drawPath(Path().apply {
        moveTo(head.x + side * hr * 0.3f, head.y - hr * 0.8f)
        lineTo(head.x + side * hr * 0.5f, head.y - hr * 1.3f)
        lineTo(head.x + side * hr * 0.65f, head.y - hr * 0.65f)
        close()
    }, ink(Color(0xFFD6D3D1)))
    drawLine(color.darker(0.3f), Offset(head.x - hr * 0.65f, head.y - hr * 0.3f), Offset(head.x + hr * 0.65f, head.y - hr * 0.3f), hr * 0.2f, StrokeCap.Round)
    for (side in listOf(-1f, 1f)) {
        val e = Offset(head.x + side * hr * 0.35f, head.y - hr * 0.08f)
        drawCircle(ink(Color(0xFFF59E0B)), hr * 0.16f, e)
        drawCircle(ink(Color(0xFF1C1917)), hr * 0.07f, e)
    }
    // Knollennase
    drawOval(color.darker(0.12f), Offset(head.x - hr * 0.25f, head.y), Size(hr * 0.5f, hr * 0.45f))
    drawCircle(color.lighter(0.2f), hr * 0.07f, Offset(head.x - hr * 0.08f, head.y + hr * 0.1f))
    // Unterbiss mit Hauern
    drawArc(color.darker(0.5f), 0f, 180f, true, Offset(head.x - hr * 0.5f, head.y + hr * 0.38f), Size(hr * 1.0f, hr * 0.42f))
    for (side in listOf(-1f, 1f)) drawPath(Path().apply {
        moveTo(head.x + side * hr * 0.28f, head.y + hr * 0.62f)
        lineTo(head.x + side * hr * 0.44f, head.y + hr * 0.6f)
        lineTo(head.x + side * hr * 0.38f, head.y + hr * 0.25f)
        close()
    }, ink(Color(0xFFFEF3C7)))
    // Pranken
    drawCircle(color.darker(0.05f), r * 0.2f, rightHand)
    drawCircle(color.darker(0.05f), r * 0.2f, leftHand)
    for (t in -1..1) drawCircle(color.darker(0.35f), r * 0.035f, leftHand + Offset(t * r * 0.1f, r * 0.15f))
}

// --- Dämonenfürst ---

/** Dämonenfürst: Fledermausflügel, Widderhörner, Flammenmähne, Obsidianrüstung, Schwanz, Hufe und Flammendreizack. */
internal fun DrawScope.drawDemonFigure(c: Offset, r: Float, color: Color, ink: EnemyInk, time: Float, phase: Float, bodyOnly: Boolean) {
    val obsidian = ink(Color(0xFF1C1917))
    val horn = ink(Color(0xFF292524))
    val fire = ink(Color(0xFFF97316))
    val flame = ink(Color(0xFFFDE047))
    val flap = sin(time * 2.5f + phase) * r * 0.08f
    if (!bodyOnly) drawCircle(fire.copy(alpha = 0.18f + 0.06f * sin(time * 4f)), r * 1.5f, c)
    // Große Fledermausflügel
    for (side in listOf(-1f, 1f)) {
        val root = Offset(c.x + side * r * 0.35f, c.y - r * 0.45f)
        val wrist = Offset(c.x + side * r * 1.2f, c.y - r * 1.15f - flap)
        val tip = Offset(c.x + side * r * 1.65f, c.y - r * 0.55f - flap * 0.6f)
        val f2 = Offset(c.x + side * r * 1.45f, c.y + r * 0.0f - flap * 0.3f)
        val f3 = Offset(c.x + side * r * 1.0f, c.y + r * 0.15f)
        drawPath(Path().apply {
            moveTo(root.x, root.y)
            lineTo(wrist.x, wrist.y)
            lineTo(tip.x, tip.y)
            quadraticTo(c.x + side * r * 1.4f, c.y - r * 0.35f, f2.x, f2.y)
            quadraticTo(c.x + side * r * 1.15f, c.y - r * 0.1f, f3.x, f3.y)
            quadraticTo(c.x + side * r * 0.7f, c.y - r * 0.15f, c.x + side * r * 0.4f, c.y - r * 0.05f)
            close()
        }, color.darker(0.45f))
        drawLine(color.darker(0.65f), root, wrist, r * 0.07f, StrokeCap.Round)
        for (f in listOf(tip, f2, f3)) drawLine(color.darker(0.65f), wrist, f, r * 0.035f, StrokeCap.Round)
        drawPath(Path().apply {
            moveTo(wrist.x - side * r * 0.04f, wrist.y)
            lineTo(wrist.x + side * r * 0.04f, wrist.y - r * 0.2f)
            lineTo(wrist.x + side * r * 0.1f, wrist.y + r * 0.02f)
            close()
        }, horn)
    }
    // Schwanz mit Pfeilspitze
    val tailSway = sin(time * 3f + phase) * r * 0.12f
    val tailTip = Offset(c.x + r * 1.15f, c.y + r * 0.35f + tailSway)
    drawPath(Path().apply {
        moveTo(c.x + r * 0.2f, c.y + r * 0.45f)
        cubicTo(c.x + r * 0.7f, c.y + r * 0.85f, c.x + r * 1.1f, c.y + r * 0.7f, tailTip.x, tailTip.y)
    }, color.darker(0.15f), style = Stroke(r * 0.08f, cap = StrokeCap.Round))
    drawPath(Path().apply {
        moveTo(tailTip.x - r * 0.1f, tailTip.y + r * 0.04f)
        lineTo(tailTip.x + r * 0.05f, tailTip.y - r * 0.2f)
        lineTo(tailTip.x + r * 0.14f, tailTip.y + r * 0.06f)
        lineTo(tailTip.x + r * 0.02f, tailTip.y)
        close()
    }, color.darker(0.15f))
    // Bocksbeine: kräftiger Oberschenkel, nach hinten geknicktes Sprunggelenk, Fell und gespaltene Hufe
    val furColor = ink(Color(0xFF3B0A0A))
    for (side in listOf(-1f, 1f)) {
        val hip = Offset(c.x + side * r * 0.2f, c.y + r * 0.3f)
        val knee = Offset(c.x + side * r * 0.36f, c.y + r * 0.55f)
        val hock = Offset(c.x + side * r * 0.24f, c.y + r * 0.72f)
        val hoof = Offset(c.x + side * r * 0.3f, c.y + r * 0.86f)
        // Oberschenkel als Muskelform
        drawPath(Path().apply {
            moveTo(hip.x - side * r * 0.14f, hip.y - r * 0.04f)
            quadraticTo(hip.x + side * r * 0.28f, hip.y - r * 0.02f, knee.x + side * r * 0.08f, knee.y)
            lineTo(knee.x - side * r * 0.1f, knee.y + r * 0.04f)
            quadraticTo(hip.x - side * r * 0.05f, hip.y + r * 0.2f, hip.x - side * r * 0.14f, hip.y - r * 0.04f)
            close()
        }, color.darker(0.05f))
        drawArc(color.lighter(0.12f), 200f, 70f, false, Offset(hip.x - r * 0.08f, hip.y - r * 0.02f), Size(r * 0.3f, r * 0.24f), style = Stroke(r * 0.025f, cap = StrokeCap.Round))
        // Zottiges Fell am Unterschenkel
        drawLine(furColor, knee, hock, r * 0.16f, StrokeCap.Round)
        drawLine(furColor, hock, hoof, r * 0.12f, StrokeCap.Round)
        for (k in 0..2) {
            val p = knee + (hock - knee) * (k / 2f)
            drawPath(Path().apply {
                moveTo(p.x - side * r * 0.08f, p.y - r * 0.03f)
                lineTo(p.x + side * r * 0.12f, p.y + r * 0.05f)
                lineTo(p.x + side * r * 0.02f, p.y + r * 0.06f)
                close()
            }, furColor)
        }
        // Gespaltener Huf
        drawPath(Path().apply {
            moveTo(hoof.x - r * 0.1f, hoof.y - r * 0.02f)
            lineTo(hoof.x + r * 0.1f, hoof.y - r * 0.02f)
            lineTo(hoof.x + r * 0.12f, hoof.y + r * 0.08f)
            lineTo(hoof.x - r * 0.12f, hoof.y + r * 0.08f)
            close()
        }, obsidian)
        drawLine(color.darker(0.4f), Offset(hoof.x, hoof.y), Offset(hoof.x, hoof.y + r * 0.08f), r * 0.025f)
    }
    // Arme
    val leftHand = Offset(c.x - r * 0.72f, c.y + r * 0.12f)
    val rightHand = Offset(c.x + r * 0.72f, c.y + r * 0.05f)
    for (hand in listOf(leftHand, rightHand)) {
        val s = if (hand.x > c.x) 1f else -1f
        drawLine(color.darker(0.05f), Offset(c.x + s * r * 0.42f, c.y - r * 0.4f), hand, r * 0.18f, StrokeCap.Round)
        drawLine(obsidian, hand - Offset(s * r * 0.08f, r * 0.2f), hand - Offset(s * r * 0.02f, r * 0.04f), r * 0.2f)
    }
    // Muskulöser Rumpf mit Obsidian-Gürtel
    val torso = Path().apply {
        moveTo(c.x - r * 0.48f, c.y - r * 0.5f)
        lineTo(c.x + r * 0.48f, c.y - r * 0.5f)
        quadraticTo(c.x + r * 0.42f, c.y, c.x + r * 0.28f, c.y + r * 0.38f)
        lineTo(c.x - r * 0.28f, c.y + r * 0.38f)
        quadraticTo(c.x - r * 0.42f, c.y, c.x - r * 0.48f, c.y - r * 0.5f)
        close()
    }
    drawPath(torso, color)
    for (side in listOf(-1f, 1f)) {
        drawArc(color.darker(0.25f), 20f, 140f, false, Offset(c.x + (if (side < 0) -0.36f else 0.02f) * r, c.y - r * 0.45f), Size(r * 0.34f, r * 0.24f), style = Stroke(r * 0.025f))
    }
    for (row in 0..1) for (side in listOf(-1f, 1f)) {
        drawRoundRect(color.lighter(0.08f), Offset(c.x + (if (side < 0) -0.15f else 0.02f) * r, c.y - r * 0.12f + row * r * 0.15f), Size(r * 0.13f, r * 0.12f), CornerRadius(r * 0.04f))
    }
    // Leuchtende Narbe auf der Brust
    drawLine(flame.copy(alpha = if (bodyOnly) 1f else 0.6f + 0.3f * sin(time * 5f)), Offset(c.x, c.y - r * 0.42f), Offset(c.x, c.y - r * 0.2f), r * 0.03f, StrokeCap.Round)
    drawLine(obsidian, Offset(c.x - r * 0.32f, c.y + r * 0.32f), Offset(c.x + r * 0.32f, c.y + r * 0.32f), r * 0.1f)
    drawCircle(fire, r * 0.06f, Offset(c.x, c.y + r * 0.32f))
    // Stachelige Obsidian-Schulterpanzer
    for (side in listOf(-1f, 1f)) {
        val p = Offset(c.x + side * r * 0.45f, c.y - r * 0.48f)
        drawOval(obsidian, p - Offset(r * 0.22f, r * 0.13f), Size(r * 0.44f, r * 0.28f))
        for (k in 0..2) {
            val b0 = p + Offset(side * r * (-0.1f + k * 0.1f), -r * 0.08f)
            drawPath(Path().apply {
                moveTo(b0.x - r * 0.05f, b0.y)
                lineTo(b0.x + side * r * 0.04f, b0.y - r * 0.2f)
                lineTo(b0.x + r * 0.05f, b0.y)
                close()
            }, obsidian)
        }
        drawArc(fire.copy(alpha = 0.6f), 200f, 60f, false, p - Offset(r * 0.18f, r * 0.1f), Size(r * 0.36f, r * 0.2f), style = Stroke(r * 0.02f))
    }
    // Kopf mit Flammenmähne, Widderhörnern, Augen und Fangzähnen
    val head = Offset(c.x, c.y - r * 0.75f)
    val hr = r * 0.25f
    for (i in -3..3) {
        val flick = sin(time * 9f + i * 1.3f) * hr * 0.25f
        val tip = Offset(head.x + i * hr * 0.32f, head.y - hr * (1.5f + (3 - abs(i)) * 0.25f) + flick)
        drawPath(Path().apply {
            moveTo(head.x + i * hr * 0.32f - hr * 0.22f, head.y - hr * 0.4f)
            quadraticTo(head.x + i * hr * 0.3f, head.y - hr * 1.0f, tip.x, tip.y)
            quadraticTo(head.x + i * hr * 0.34f + hr * 0.1f, head.y - hr * 0.9f, head.x + i * hr * 0.32f + hr * 0.22f, head.y - hr * 0.4f)
            close()
        }, if (abs(i) % 2 == 0) fire else flame)
    }
    for (side in listOf(-1f, 1f)) {
        drawPath(Path().apply {
            moveTo(head.x + side * hr * 0.55f, head.y - hr * 0.7f)
            cubicTo(head.x + side * hr * 1.6f, head.y - hr * 1.6f, head.x + side * hr * 2.3f, head.y - hr * 0.2f, head.x + side * hr * 1.5f, head.y + hr * 0.4f)
            quadraticTo(head.x + side * hr * 1.9f, head.y - hr * 0.5f, head.x + side * hr * 0.85f, head.y - hr * 0.3f)
            close()
        }, horn)
        for (k in 0..2) {
            drawLine(ink(Color(0xFF57534E)), head + Offset(side * hr * (1.0f + k * 0.3f), -hr * (1.05f - k * 0.2f)), head + Offset(side * hr * (1.15f + k * 0.3f), -hr * (0.8f - k * 0.2f)), hr * 0.06f)
        }
    }
    drawRoundRect(color, Offset(head.x - hr * 0.85f, head.y - hr * 0.85f), Size(hr * 1.7f, hr * 1.85f), CornerRadius(hr * 0.6f))
    drawPath(Path().apply {
        moveTo(head.x - hr * 0.6f, head.y + hr * 0.6f)
        lineTo(head.x, head.y + hr * 1.3f)
        lineTo(head.x + hr * 0.6f, head.y + hr * 0.6f)
        close()
    }, color.darker(0.15f))
    for (side in listOf(-1f, 1f)) {
        val e = Offset(head.x + side * hr * 0.36f, head.y - hr * 0.1f)
        if (!bodyOnly) drawCircle(flame.copy(alpha = 0.4f), hr * 0.32f, e)
        drawPath(Path().apply {
            moveTo(e.x - side * hr * 0.22f, e.y + hr * 0.05f)
            lineTo(e.x + side * hr * 0.25f, e.y - hr * 0.12f)
            lineTo(e.x + side * hr * 0.15f, e.y + hr * 0.1f)
            close()
        }, flame)
        drawLine(color.darker(0.5f), e + Offset(-side * hr * 0.25f, -hr * 0.08f), e + Offset(side * hr * 0.3f, -hr * 0.32f), hr * 0.12f, StrokeCap.Round)
    }
    drawArc(color.darker(0.6f), 0f, 180f, true, Offset(head.x - hr * 0.45f, head.y + hr * 0.25f), Size(hr * 0.9f, hr * 0.55f))
    for (side in listOf(-1f, 1f)) {
        drawPath(Path().apply {
            moveTo(head.x + side * hr * 0.2f, head.y + hr * 0.5f)
            lineTo(head.x + side * hr * 0.35f, head.y + hr * 0.5f)
            lineTo(head.x + side * hr * 0.28f, head.y + hr * 0.78f)
            close()
        }, ink(Color(0xFFF5F5F4)))
    }
    // Flammendreizack in der rechten Hand
    val shaftTop = rightHand + Offset(r * 0.05f, -r * 1.1f)
    drawLine(obsidian, rightHand + Offset(0f, r * 0.55f), shaftTop, r * 0.07f, StrokeCap.Round)
    for (k in -1..1) {
        val prong = shaftTop + Offset(k * r * 0.15f, -r * (if (k == 0) 0.32f else 0.24f))
        drawLine(obsidian, shaftTop + Offset(k * r * 0.15f, 0f), prong, r * 0.05f, StrokeCap.Round)
        drawPath(Path().apply {
            moveTo(prong.x - r * 0.05f, prong.y)
            lineTo(prong.x, prong.y - r * 0.12f)
            lineTo(prong.x + r * 0.05f, prong.y)
            close()
        }, obsidian)
        if (!bodyOnly) drawCircle(fire.copy(alpha = 0.5f + 0.3f * sin(time * 10f + k)), r * 0.07f, prong + Offset(0f, -r * 0.14f))
    }
    drawLine(obsidian, shaftTop + Offset(-r * 0.17f, 0f), shaftTop + Offset(r * 0.17f, 0f), r * 0.05f, StrokeCap.Round)
    drawCircle(color.darker(0.05f), r * 0.1f, rightHand)
    drawCircle(color.darker(0.05f), r * 0.1f, leftHand)
    // Feuerball in der linken Hand
    if (!bodyOnly) {
        val fb = leftHand + Offset(0f, -r * 0.2f)
        drawCircle(fire.copy(alpha = 0.35f), r * 0.2f, fb)
        drawCircle(fire, r * 0.11f, fb)
        drawCircle(flame, r * 0.06f, fb)
    }
}

// --- Zombie ---

/** Wankender Zombie: zerrissenes Hemd mit Rippen, ausgestreckte Arme, schiefer Kopf mit Naht und Glubschauge. */
internal fun DrawScope.drawZombieFigure(c: Offset, r: Float, color: Color, ink: EnemyInk, time: Float, phase: Float) {
    val sway = sin(time * 3f + phase) * r * 0.12f
    val shirt = ink(Color(0xFF4B5563))
    val pants = ink(Color(0xFF44403C))
    val bone = ink(Color(0xFFE7E5E4))
    val blood = ink(Color(0xFF7F1D1D))
    val step = sin(time * 3f + phase) * r * 0.08f
    // Schlurfende Beine, ein Schuh fehlt
    for (side in listOf(-1f, 1f)) {
        val lift = (if (side > 0) step else -step).coerceAtLeast(0f)
        val x = c.x + side * r * 0.2f
        drawRoundRect(pants, Offset(x - r * 0.13f, c.y + r * 0.3f), Size(r * 0.26f, r * 0.5f - lift), CornerRadius(r * 0.08f))
        // Zerfranste Hosenbeine
        for (k in 0..2) drawLine(pants, Offset(x - r * 0.12f + k * r * 0.12f, c.y + r * 0.78f - lift), Offset(x - r * 0.1f + k * r * 0.12f, c.y + r * 0.86f - lift), r * 0.05f, StrokeCap.Round)
        if (side < 0) {
            drawOval(ink(Color(0xFF292524)), Offset(x - r * 0.17f, c.y + r * 0.86f - lift), Size(r * 0.34f, r * 0.14f))
        } else {
            drawOval(color.darker(0.1f), Offset(x - r * 0.13f, c.y + r * 0.84f - lift), Size(r * 0.26f, r * 0.14f))
            for (t in 0..2) drawCircle(color.darker(0.3f), r * 0.025f, Offset(x - r * 0.07f + t * r * 0.07f, c.y + r * 0.9f - lift))
        }
    }
    // Ausgestreckte Arme, einer mit freiliegendem Knochen
    val leftHand = Offset(c.x - r * 0.95f + sway, c.y - r * 0.2f)
    val rightHand = Offset(c.x + r * 0.98f + sway, c.y - r * 0.12f)
    for (hand in listOf(leftHand, rightHand)) {
        val s = if (hand.x > c.x) 1f else -1f
        val shoulder = Offset(c.x + s * r * 0.35f + sway * 0.5f, c.y - r * 0.15f)
        drawLine(shirt, shoulder, shoulder + (hand - shoulder) * 0.4f, r * 0.24f, StrokeCap.Round)
        if (s > 0) {
            drawLine(color, shoulder + (hand - shoulder) * 0.4f, shoulder + (hand - shoulder) * 0.6f, r * 0.18f)
            drawLine(bone, shoulder + (hand - shoulder) * 0.58f, shoulder + (hand - shoulder) * 0.8f, r * 0.08f, StrokeCap.Round)
            drawLine(color, shoulder + (hand - shoulder) * 0.8f, hand, r * 0.17f, StrokeCap.Round)
        } else {
            drawLine(color, shoulder + (hand - shoulder) * 0.4f, hand, r * 0.18f, StrokeCap.Round)
        }
        // Hand mit krummen Fingern
        for (f in -1..1) drawLine(color.darker(0.1f), hand, hand + Offset(s * r * 0.14f, f * r * 0.07f + r * 0.04f), r * 0.05f, StrokeCap.Round)
        drawCircle(color, r * 0.09f, hand)
    }
    // Zerrissenes Hemd mit Löchern, durch die Rippen schauen
    val body = Path().apply {
        moveTo(c.x - r * 0.4f + sway * 0.5f, c.y - r * 0.25f)
        lineTo(c.x + r * 0.4f + sway * 0.5f, c.y - r * 0.25f)
        lineTo(c.x + r * 0.36f, c.y + r * 0.4f)
        lineTo(c.x + r * 0.22f, c.y + r * 0.32f)
        lineTo(c.x + r * 0.08f, c.y + r * 0.44f)
        lineTo(c.x - r * 0.1f, c.y + r * 0.33f)
        lineTo(c.x - r * 0.24f, c.y + r * 0.45f)
        lineTo(c.x - r * 0.36f, c.y + r * 0.38f)
        close()
    }
    drawPath(body, shirt)
    clipPath(body) {
        drawRect(Color.Black.copy(alpha = 0.2f), Offset(c.x + r * 0.15f, c.y - r * 0.4f), Size(r * 0.5f, r))
        val hole = Offset(c.x - r * 0.12f + sway * 0.4f, c.y + r * 0.05f)
        drawOval(color.darker(0.25f), hole - Offset(r * 0.16f, r * 0.14f), Size(r * 0.32f, r * 0.3f))
        for (i in 0..2) drawArc(bone, 200f, 140f, false, Offset(hole.x - r * 0.13f, hole.y - r * 0.1f + i * r * 0.07f), Size(r * 0.26f, r * 0.12f), style = Stroke(r * 0.03f, cap = StrokeCap.Round))
        drawCircle(blood, r * 0.05f, Offset(c.x + r * 0.22f, c.y - r * 0.05f))
    }
    drawLine(shirt.darker(0.3f), Offset(c.x + r * 0.05f + sway * 0.5f, c.y - r * 0.24f), Offset(c.x + r * 0.1f, c.y + r * 0.2f), r * 0.025f)
    // Schiefer Kopf
    val head = Offset(c.x + sway, c.y - r * 0.6f)
    val hr = r * 0.38f
    drawLine(color.darker(0.1f), Offset(c.x + sway * 0.5f, c.y - r * 0.25f), head, r * 0.18f)
    drawCircle(color, hr, head)
    drawCircle(color.darker(0.12f), hr * 0.35f, head + Offset(hr * 0.45f, hr * 0.35f))
    // Schüttere Haare
    for (i in -2..2) {
        drawLine(ink(Color(0xFF292524)), head + Offset(i * hr * 0.25f, -hr * 0.85f), head + Offset(i * hr * 0.35f + hr * 0.1f, -hr * (1.2f + (i % 2) * 0.12f)), hr * 0.07f, StrokeCap.Round)
    }
    // Naht über der Stirn
    drawLine(color.darker(0.4f), head + Offset(-hr * 0.6f, -hr * 0.45f), head + Offset(hr * 0.1f, -hr * 0.6f), hr * 0.05f)
    for (k in 0..3) {
        val p = head + Offset(-hr * 0.55f + k * hr * 0.2f, -hr * (0.46f + k * 0.035f))
        drawLine(color.darker(0.4f), p + Offset(0f, -hr * 0.08f), p + Offset(0f, hr * 0.08f), hr * 0.04f)
    }
    // Ein Glubschauge, ein zugekniffenes
    val big = head + Offset(-hr * 0.35f, -hr * 0.05f)
    drawCircle(ink(Color(0xFFFEF9C3)), hr * 0.28f, big)
    drawCircle(ink(Color(0xFFDC2626)), hr * 0.1f, big + Offset(hr * 0.05f, hr * 0.06f))
    drawLine(color.darker(0.45f), head + Offset(hr * 0.18f, -hr * 0.05f), head + Offset(hr * 0.55f, hr * 0.02f), hr * 0.08f, StrokeCap.Round)
    // Offener Mund mit schiefen Zähnen
    val mouth = head + Offset(hr * 0.05f, hr * 0.45f)
    drawOval(color.darker(0.6f), mouth - Offset(hr * 0.32f, hr * 0.17f), Size(hr * 0.64f, hr * 0.36f))
    for (t in listOf(-0.18f, 0.02f, 0.2f)) {
        drawRect(ink(Color(0xFFFEF3C7)), Offset(mouth.x + t * hr - hr * 0.05f, mouth.y - hr * 0.17f), Size(hr * 0.1f, hr * 0.12f))
    }
}

// --- Knochenschütze ---

/**
 * Skelett-Bogenschütze mit zerschlissener Kapuze, Umhang und Köcher. Der Bogen zeigt in Richtung [aim]
 * (Bogenmaß); beim Zielen ([drawing]) liegt ein Knochenpfeil auf der gespannten Sehne.
 */
internal fun DrawScope.drawArcherFigure(c: Offset, r: Float, color: Color, ink: EnemyInk, time: Float, phase: Float, aim: Float, drawing: Boolean) {
    val cloth = ink(Color(0xFF1F2937))
    val wood = ink(Color(0xFF78350F))
    // Zerschlissener Umhang hinter dem Körper
    drawPath(Path().apply {
        moveTo(c.x - r * 0.35f, c.y - r * 0.2f)
        lineTo(c.x + r * 0.35f, c.y - r * 0.2f)
        lineTo(c.x + r * 0.5f, c.y + r * 0.7f)
        lineTo(c.x + r * 0.32f, c.y + r * 0.58f)
        lineTo(c.x + r * 0.15f, c.y + r * 0.76f)
        lineTo(c.x - r * 0.05f, c.y + r * 0.6f)
        lineTo(c.x - r * 0.25f, c.y + r * 0.74f)
        lineTo(c.x - r * 0.5f, c.y + r * 0.62f)
        close()
    }, cloth)
    // Köcher mit Knochenpfeilen über der linken Schulter
    val qx = c.x - r * 0.3f
    for (i in 0..2) {
        val top = Offset(qx - r * (0.12f + i * 0.08f), c.y - r * 0.85f + i * r * 0.05f)
        drawLine(ink(Color(0xFFE7E5E4)), Offset(qx - r * 0.05f - i * r * 0.04f, c.y - r * 0.3f), top, r * 0.04f)
        drawPath(Path().apply {
            moveTo(top.x, top.y)
            lineTo(top.x - r * 0.06f, top.y + r * 0.12f)
            lineTo(top.x + r * 0.06f, top.y + r * 0.12f)
            close()
        }, ink(Color(0xFF7F1D1D)))
    }
    drawRoundRect(wood, Offset(qx - r * 0.2f, c.y - r * 0.55f), Size(r * 0.2f, r * 0.55f), CornerRadius(r * 0.06f))
    val (left, right) = skeletonBody(c, r, color, ink(Color(0xFF22D3EE)), ink, time, phase)
    // Gürtel mit Lederbeutel
    drawLine(wood, Offset(c.x - r * 0.26f, c.y + r * 0.28f), Offset(c.x + r * 0.26f, c.y + r * 0.28f), r * 0.06f)
    drawRoundRect(wood.lighter(0.1f), Offset(c.x + r * 0.1f, c.y + r * 0.28f), Size(r * 0.14f, r * 0.14f), CornerRadius(r * 0.03f))
    // Kapuze um den Schädel
    val head = Offset(c.x, c.y - r * 0.56f)
    val hr = r * 0.4f
    drawArc(cloth, 160f, 220f, false, Offset(head.x - hr * 1.15f, head.y - hr * 1.2f), Size(hr * 2.3f, hr * 2.2f), style = Stroke(hr * 0.32f, cap = StrokeCap.Round))
    drawPath(Path().apply {
        moveTo(head.x - hr * 0.5f, head.y - hr * 1.25f)
        quadraticTo(head.x - hr * 0.2f, head.y - hr * 1.9f, head.x + hr * 0.35f, head.y - hr * 1.75f)
        quadraticTo(head.x + hr * 0.1f, head.y - hr * 1.45f, head.x + hr * 0.5f, head.y - hr * 1.25f)
        close()
    }, cloth)
    // Bogen in Zielrichtung, beim Zielen mit gespanntem Pfeil
    val dir = Offset(kotlin.math.cos(aim), sin(aim))
    val n = Offset(-dir.y, dir.x)
    val grip = c + Offset(0f, -r * 0.05f) + dir * (r * 0.7f)
    val tipA = grip + n * (r * 0.62f) - dir * (r * 0.18f)
    val tipB = grip - n * (r * 0.62f) - dir * (r * 0.18f)
    val pull = if (drawing) r * 0.4f else r * 0.05f
    val nock = grip - dir * (r * 0.18f + pull)
    drawLine(ink(Color(0xFFE7E5E4)).copy(alpha = 0.9f), tipA, nock, r * 0.025f)
    drawLine(ink(Color(0xFFE7E5E4)).copy(alpha = 0.9f), tipB, nock, r * 0.025f)
    drawPath(Path().apply {
        moveTo(tipA.x, tipA.y)
        quadraticTo(grip.x + dir.x * r * 0.25f + n.x * r * 0.3f, grip.y + dir.y * r * 0.25f + n.y * r * 0.3f, grip.x, grip.y)
        quadraticTo(grip.x + dir.x * r * 0.25f - n.x * r * 0.3f, grip.y + dir.y * r * 0.25f - n.y * r * 0.3f, tipB.x, tipB.y)
    }, wood, style = Stroke(r * 0.08f, cap = StrokeCap.Round))
    if (drawing) {
        val head2 = grip + dir * (r * 0.2f)
        drawLine(ink(Color(0xFFE7E5E4)), nock, head2, r * 0.04f)
        drawPath(Path().apply {
            moveTo(head2.x + dir.x * r * 0.12f, head2.y + dir.y * r * 0.12f)
            lineTo(head2.x + n.x * r * 0.06f, head2.y + n.y * r * 0.06f)
            lineTo(head2.x - n.x * r * 0.06f, head2.y - n.y * r * 0.06f)
            close()
        }, ink(Color(0xFFF43F5E)))
    }
    // Bogenhand auf der Zielseite, die andere Hand spannt nur beim Zielen die Sehne
    val (bowHand, drawHand) = if (dir.x >= 0f) right to left else left to right
    drawLine(color, bowHand, grip, r * 0.06f, StrokeCap.Round)
    drawCircle(color, r * 0.07f, grip)
    if (drawing) {
        drawLine(color, drawHand, nock, r * 0.06f, StrokeCap.Round)
        drawCircle(color, r * 0.07f, nock)
    }
}

// --- Grabhexe ---

/**
 * Dunkle Hexe: blass-grünliche Haut, lange schwarze Haare, tailliertes, zerfetztes Kleid mit Ausschnitt,
 * Spitzhut und Fluchflamme in der Hand, die beim Zaubern ([casting]) auflodert.
 */
internal fun DrawScope.drawWitchFigure(c: Offset, r: Float, color: Color, ink: EnemyInk, time: Float, phase: Float, casting: Boolean, bodyOnly: Boolean) {
    val skin = ink(Color(0xFFCFE3C8))
    val hair = ink(Color(0xFF111827))
    val dress = color.darker(0.35f)
    val curse = ink(Color(0xFFA3E635))
    val sway = sin(time * 2.5f + phase) * r * 0.05f
    val head = Offset(c.x, c.y - r * 0.68f)
    val hr = r * 0.3f
    val shoulderY = c.y - r * 0.3f
    val waistY = c.y + r * 0.06f
    val hemY = c.y + r * 0.85f
    // Lange Haare hinter dem Rücken
    drawPath(Path().apply {
        moveTo(head.x - hr * 1.05f, head.y - hr * 0.3f)
        lineTo(head.x + hr * 1.05f, head.y - hr * 0.3f)
        quadraticTo(head.x + hr * 1.5f, c.y - r * 0.1f, head.x + hr * 1.2f + sway, c.y + r * 0.25f)
        lineTo(head.x - hr * 1.2f + sway, c.y + r * 0.25f)
        quadraticTo(head.x - hr * 1.5f, c.y - r * 0.1f, head.x - hr * 1.05f, head.y - hr * 0.3f)
        close()
    }, hair)
    // Zerfetzter Rock
    val skirt = Path().apply {
        moveTo(c.x - r * 0.2f, waistY)
        lineTo(c.x + r * 0.2f, waistY)
        cubicTo(c.x + r * 0.35f, c.y + r * 0.3f, c.x + r * 0.6f, c.y + r * 0.55f, c.x + r * 0.62f + sway, hemY)
        val teeth = 6
        for (i in 0 until teeth) {
            val x0 = c.x + r * 0.62f - i * (r * 1.24f / teeth) + sway * (1f - i / teeth.toFloat())
            lineTo(x0 - r * 0.1f, hemY - r * (if (i % 2 == 0) 0.14f else 0.06f))
            lineTo(x0 - r * 0.2f, hemY + r * (if (i % 2 == 0) 0.04f else 0.08f))
        }
        cubicTo(c.x - r * 0.6f, c.y + r * 0.55f, c.x - r * 0.35f, c.y + r * 0.3f, c.x - r * 0.2f, waistY)
        close()
    }
    drawPath(skirt, dress)
    clipPath(skirt) {
        drawRect(Color.Black.copy(alpha = 0.25f), Offset(c.x + r * 0.15f, waistY), Size(r * 0.6f, r))
        // Schlitz mit Bein
        drawPath(Path().apply {
            moveTo(c.x + r * 0.05f, c.y + r * 0.35f)
            lineTo(c.x + r * 0.22f, hemY + r * 0.1f)
            lineTo(c.x - r * 0.04f, hemY + r * 0.1f)
            close()
        }, skin)
    }
    drawOval(hair, Offset(c.x - r * 0.02f, hemY - r * 0.02f), Size(r * 0.2f, r * 0.1f))
    // Arme mit weiten Ärmeln
    val leftHand = Offset(c.x - r * 0.62f, c.y + r * 0.0f)
    val rightHand = Offset(c.x + r * 0.6f, c.y - (if (casting) r * 0.35f else r * 0.05f))
    for (hand in listOf(leftHand, rightHand)) {
        val s = if (hand.x > c.x) 1f else -1f
        val shoulder = Offset(c.x + s * r * 0.22f, shoulderY + r * 0.05f)
        drawLine(skin, shoulder, hand, r * 0.09f, StrokeCap.Round)
        val mid = shoulder + (hand - shoulder) * 0.55f
        drawPath(Path().apply {
            moveTo(shoulder.x, shoulder.y - r * 0.05f)
            lineTo(mid.x + s * r * 0.08f, mid.y - r * 0.08f)
            lineTo(mid.x + s * r * 0.04f, mid.y + r * 0.18f)
            lineTo(mid.x - s * r * 0.14f, mid.y + r * 0.12f)
            close()
        }, dress.lighter(0.08f))
        drawCircle(skin, r * 0.06f, hand)
    }
    // Mieder mit Taille, Ausschnitt und Schnürung
    val bodice = Path().apply {
        moveTo(c.x - r * 0.17f, shoulderY)
        lineTo(c.x + r * 0.17f, shoulderY)
        quadraticTo(c.x + r * 0.27f, shoulderY + r * 0.08f, c.x + r * 0.24f, shoulderY + r * 0.18f)
        quadraticTo(c.x + r * 0.2f, waistY - r * 0.08f, c.x + r * 0.15f, waistY + r * 0.02f)
        lineTo(c.x - r * 0.15f, waistY + r * 0.02f)
        quadraticTo(c.x - r * 0.2f, waistY - r * 0.08f, c.x - r * 0.24f, shoulderY + r * 0.18f)
        quadraticTo(c.x - r * 0.27f, shoulderY + r * 0.08f, c.x - r * 0.17f, shoulderY)
        close()
    }
    drawPath(bodice, dress)
    drawPath(Path().apply {
        moveTo(c.x - r * 0.15f, shoulderY)
        lineTo(c.x + r * 0.15f, shoulderY)
        lineTo(c.x, shoulderY + r * 0.2f)
        close()
    }, skin)
    for (side in listOf(-1f, 1f)) {
        drawArc(dress.lighter(0.15f), 200f, 80f, false, Offset(c.x + side * r * 0.11f - r * 0.1f, shoulderY + r * 0.06f), Size(r * 0.2f, r * 0.16f), style = Stroke(r * 0.02f, cap = StrokeCap.Round))
    }
    for (i in 0..1) {
        val y = shoulderY + r * (0.24f + i * 0.08f)
        drawLine(curse.copy(alpha = 0.8f), Offset(c.x - r * 0.05f, y), Offset(c.x + r * 0.05f, y + r * 0.04f), r * 0.015f)
        drawLine(curse.copy(alpha = 0.8f), Offset(c.x + r * 0.05f, y), Offset(c.x - r * 0.05f, y + r * 0.04f), r * 0.015f)
    }
    drawLine(hair, Offset(c.x - r * 0.17f, waistY), Offset(c.x + r * 0.17f, waistY), r * 0.05f)
    // Kopf mit glühenden Augen, Wimpern und dunklen Lippen
    drawLine(skin, Offset(head.x, head.y + hr * 0.7f), Offset(head.x, shoulderY), hr * 0.35f)
    drawCircle(skin, hr, head)
    val glow = if (bodyOnly) 1f else 0.75f + 0.25f * sin(time * 5f + phase)
    for (side in listOf(-1f, 1f)) {
        val e = Offset(head.x + side * hr * 0.36f, head.y + hr * 0.08f)
        drawArc(hair, if (side > 0) 200f else 230f, 110f, false, e - Offset(hr * 0.19f, hr * 0.19f), Size(hr * 0.38f, hr * 0.38f), style = Stroke(hr * 0.07f, cap = StrokeCap.Round))
        drawLine(hair, e + Offset(side * hr * 0.13f, -hr * 0.1f), e + Offset(side * hr * 0.25f, -hr * 0.22f), hr * 0.06f, StrokeCap.Round)
        drawOval(curse.copy(alpha = glow), e - Offset(hr * 0.16f, hr * 0.11f), Size(hr * 0.32f, hr * 0.22f))
        drawCircle(ink(Color(0xFF14532D)), hr * 0.06f, e)
        drawLine(hair, e + Offset(-side * hr * 0.12f, -hr * 0.24f), e + Offset(side * hr * 0.18f, -hr * 0.32f), hr * 0.07f, StrokeCap.Round)
    }
    drawArc(ink(Color(0xFF4C1D95)), 0f, 180f, true, Offset(head.x - hr * 0.18f, head.y + hr * 0.42f), Size(hr * 0.36f, hr * 0.2f))
    drawLine(ink(Color(0xFF2E1065)), Offset(head.x - hr * 0.18f, head.y + hr * 0.52f), Offset(head.x + hr * 0.18f, head.y + hr * 0.52f), hr * 0.04f, StrokeCap.Round)
    // Seitliche Haarsträhnen, nur unter der Krempe
    val brimY = head.y - hr * 0.55f
    clipRect(top = brimY) {
        drawPath(Path().apply {
            moveTo(head.x - hr * 1.06f, head.y + hr * 0.9f)
            cubicTo(head.x - hr * 1.2f, head.y - hr * 1.4f, head.x + hr * 1.2f, head.y - hr * 1.4f, head.x + hr * 1.06f, head.y + hr * 0.9f)
            lineTo(head.x + hr * 0.8f, head.y + hr * 0.5f)
            quadraticTo(head.x + hr * 0.85f, head.y - hr * 0.4f, head.x + hr * 0.05f, head.y - hr * 0.55f)
            lineTo(head.x - hr * 0.05f, head.y - hr * 0.55f)
            quadraticTo(head.x - hr * 0.85f, head.y - hr * 0.4f, head.x - hr * 0.8f, head.y + hr * 0.5f)
            close()
        }, hair)
    }
    // Spitzhut mit geknickter Spitze und Schnalle
    val hat = ink(Color(0xFF1E1B4B))
    drawOval(hat, Offset(head.x - hr * 1.6f, brimY - hr * 0.25f), Size(hr * 3.2f, hr * 0.55f))
    drawPath(Path().apply {
        moveTo(head.x - hr * 0.8f, brimY)
        quadraticTo(head.x - hr * 0.4f, head.y - hr * 1.6f, head.x + hr * 0.1f, head.y - hr * 2.0f)
        quadraticTo(head.x + hr * 0.6f, head.y - hr * 2.4f, head.x + hr * 1.2f + sway, head.y - hr * 2.2f)
        quadraticTo(head.x + hr * 0.5f, head.y - hr * 1.9f, head.x + hr * 0.6f, head.y - hr * 1.2f)
        lineTo(head.x + hr * 0.8f, brimY)
        close()
    }, hat)
    drawLine(curse.darker(0.3f), Offset(head.x - hr * 0.78f, brimY - hr * 0.18f), Offset(head.x + hr * 0.78f, brimY - hr * 0.18f), hr * 0.18f)
    drawRect(ink(Color(0xFFFACC15)), Offset(head.x - hr * 0.13f, brimY - hr * 0.3f), Size(hr * 0.26f, hr * 0.24f), style = Stroke(hr * 0.06f))
    // Fluchflamme über der rechten Hand
    val f = rightHand + Offset(0f, -r * 0.2f)
    val flick = sin(time * 12f + phase) * r * 0.03f
    val size = if (casting) 1.5f else 1f
    if (!bodyOnly) drawCircle(curse.copy(alpha = 0.3f), r * 0.2f * size, f)
    drawPath(Path().apply {
        moveTo(f.x - r * 0.1f * size, f.y + r * 0.06f)
        quadraticTo(f.x - r * 0.1f * size, f.y - r * 0.1f * size, f.x + flick, f.y - r * 0.25f * size)
        quadraticTo(f.x + r * 0.1f * size, f.y - r * 0.1f * size, f.x + r * 0.1f * size, f.y + r * 0.06f)
        quadraticTo(f.x, f.y + r * 0.14f, f.x - r * 0.1f * size, f.y + r * 0.06f)
        close()
    }, curse)
    drawCircle(ink(Color(0xFFECFCCB)), r * 0.045f * size, f + Offset(0f, r * 0.01f))
}

// --- Grabwächter ---

/** Lebende Steinstatue: Grabstein-Schulterplatten, leuchtende Runen, Grabsteinschild und steinernes Großschwert. */
internal fun DrawScope.drawGuardianFigure(c: Offset, r: Float, color: Color, ink: EnemyInk, time: Float, phase: Float, charging: Boolean, bodyOnly: Boolean) {
    val stone = color
    val dark = color.darker(0.3f)
    val light = color.lighter(0.18f)
    val moss = ink(Color(0xFF3F6212))
    val rune = if (charging) ink(Color(0xFFF43F5E)) else ink(Color(0xFF22D3EE))
    val glow = if (bodyOnly) 1f else 0.65f + 0.35f * sin(time * 3f + phase)
    val step = sin(time * 4f + phase) * r * 0.04f
    // Säulenbeine mit Sockelfüßen
    for (side in listOf(-1f, 1f)) {
        val x = c.x + side * r * 0.3f
        val lift = (if (side > 0) step else -step).coerceAtLeast(0f)
        drawRect(stone.darker(0.12f), Offset(x - r * 0.15f, c.y + r * 0.3f - lift), Size(r * 0.3f, r * 0.42f))
        drawRect(dark, Offset(x - r * 0.15f, c.y + r * 0.42f - lift), Size(r * 0.3f, r * 0.04f))
        drawRect(stone, Offset(x - r * 0.22f, c.y + r * 0.68f - lift), Size(r * 0.44f, r * 0.14f))
        drawRect(light, Offset(x - r * 0.22f, c.y + r * 0.68f - lift), Size(r * 0.44f, r * 0.03f))
    }
    // Rumpf als behauener Block mit Rune im Kreis
    val torso = Path().apply {
        moveTo(c.x - r * 0.6f, c.y - r * 0.55f)
        lineTo(c.x + r * 0.6f, c.y - r * 0.55f)
        lineTo(c.x + r * 0.45f, c.y + r * 0.35f)
        lineTo(c.x - r * 0.45f, c.y + r * 0.35f)
        close()
    }
    drawPath(torso, stone)
    clipPath(torso) {
        drawRect(Color.Black.copy(alpha = 0.18f), Offset(c.x + r * 0.25f, c.y - r * 0.6f), Size(r * 0.5f, r))
        drawRect(light, Offset(c.x - r * 0.6f, c.y - r * 0.55f), Size(r * 1.2f, r * 0.04f))
        for (i in 0..1) drawLine(dark, Offset(c.x - r * 0.55f, c.y + r * (0.02f + i * 0.17f)), Offset(c.x + r * 0.55f, c.y + r * (0.02f + i * 0.17f)), r * 0.025f)
        drawCircle(moss, r * 0.12f, Offset(c.x - r * 0.42f, c.y + r * 0.28f))
        drawCircle(moss, r * 0.08f, Offset(c.x - r * 0.3f, c.y + r * 0.33f))
    }
    val core = Offset(c.x, c.y - r * 0.22f)
    if (!bodyOnly) drawCircle(rune.copy(alpha = 0.25f * glow), r * 0.3f, core)
    drawCircle(dark, r * 0.2f, core)
    drawCircle(rune.copy(alpha = glow), r * 0.2f, core, style = Stroke(r * 0.035f))
    drawLine(rune.copy(alpha = glow), core + Offset(0f, -r * 0.12f), core + Offset(0f, r * 0.12f), r * 0.035f)
    drawLine(rune.copy(alpha = glow), core + Offset(0f, -r * 0.02f), core + Offset(r * 0.08f, -r * 0.1f), r * 0.035f)
    drawLine(rune.copy(alpha = glow), core + Offset(0f, r * 0.04f), core + Offset(-r * 0.08f, -r * 0.04f), r * 0.035f)
    // Risse
    drawLine(dark, Offset(c.x + r * 0.35f, c.y - r * 0.5f), Offset(c.x + r * 0.25f, c.y - r * 0.3f), r * 0.025f, StrokeCap.Round)
    drawLine(dark, Offset(c.x + r * 0.25f, c.y - r * 0.3f), Offset(c.x + r * 0.32f, c.y - r * 0.15f), r * 0.025f, StrokeCap.Round)
    // Arme aus Steinsegmenten
    val leftHand = Offset(c.x - r * 0.85f, c.y + r * 0.1f)
    val rightHand = Offset(c.x + r * 0.85f, c.y + r * 0.15f)
    for (hand in listOf(leftHand, rightHand)) {
        val s = if (hand.x > c.x) 1f else -1f
        val elbow = Offset(c.x + s * r * 0.8f, c.y - r * 0.2f)
        drawLine(stone.darker(0.08f), Offset(c.x + s * r * 0.6f, c.y - r * 0.45f), elbow, r * 0.22f, StrokeCap.Square)
        drawLine(stone.darker(0.08f), elbow, hand, r * 0.2f, StrokeCap.Square)
        drawCircle(dark, r * 0.08f, elbow)
        drawRect(stone, Offset(hand.x - r * 0.12f, hand.y - r * 0.1f), Size(r * 0.24f, r * 0.2f))
    }
    // Steinernes Großschwert, Spitze am Boden
    val hilt = rightHand + Offset(0f, -r * 0.2f)
    drawPath(Path().apply {
        moveTo(hilt.x - r * 0.09f, hilt.y + r * 0.2f)
        lineTo(hilt.x + r * 0.09f, hilt.y + r * 0.2f)
        lineTo(hilt.x + r * 0.07f, c.y + r * 0.78f)
        lineTo(hilt.x, c.y + r * 0.88f)
        lineTo(hilt.x - r * 0.07f, c.y + r * 0.78f)
        close()
    }, light)
    drawLine(dark, Offset(hilt.x, hilt.y + r * 0.22f), Offset(hilt.x, c.y + r * 0.78f), r * 0.025f)
    drawRect(dark, Offset(hilt.x - r * 0.2f, hilt.y + r * 0.12f), Size(r * 0.4f, r * 0.08f))
    drawRect(stone.darker(0.2f), Offset(hilt.x - r * 0.04f, hilt.y - r * 0.12f), Size(r * 0.08f, r * 0.24f))
    drawCircle(rune.copy(alpha = glow), r * 0.05f, hilt + Offset(0f, -r * 0.14f))
    drawRect(stone, Offset(rightHand.x - r * 0.12f, rightHand.y - r * 0.1f), Size(r * 0.24f, r * 0.2f))
    // Grabsteinschild am linken Arm
    val sc = leftHand + Offset(-r * 0.05f, -r * 0.05f)
    val tomb = Path().apply {
        moveTo(sc.x - r * 0.3f, sc.y + r * 0.42f)
        lineTo(sc.x - r * 0.3f, sc.y - r * 0.2f)
        quadraticTo(sc.x - r * 0.3f, sc.y - r * 0.52f, sc.x, sc.y - r * 0.52f)
        quadraticTo(sc.x + r * 0.3f, sc.y - r * 0.52f, sc.x + r * 0.3f, sc.y - r * 0.2f)
        lineTo(sc.x + r * 0.3f, sc.y + r * 0.42f)
        close()
    }
    drawPath(tomb, light)
    drawPath(tomb, dark, style = Stroke(r * 0.03f))
    drawLine(dark, Offset(sc.x, sc.y - r * 0.38f), Offset(sc.x, sc.y + r * 0.05f), r * 0.05f)
    drawLine(dark, Offset(sc.x - r * 0.13f, sc.y - r * 0.24f), Offset(sc.x + r * 0.13f, sc.y - r * 0.24f), r * 0.05f)
    for (i in 0..1) drawLine(dark.copy(alpha = 0.6f), Offset(sc.x - r * 0.18f, sc.y + r * (0.16f + i * 0.1f)), Offset(sc.x + r * 0.18f, sc.y + r * (0.16f + i * 0.1f)), r * 0.025f)
    drawCircle(moss, r * 0.07f, Offset(sc.x + r * 0.22f, sc.y + r * 0.36f))
    // Grabstein-Schulterplatten
    for (side in listOf(-1f, 1f)) {
        val p = Offset(c.x + side * r * 0.62f, c.y - r * 0.55f)
        val slab = Path().apply {
            moveTo(p.x - r * 0.22f, p.y + r * 0.25f)
            lineTo(p.x - r * 0.22f, p.y - r * 0.1f)
            quadraticTo(p.x - r * 0.22f, p.y - r * 0.32f, p.x, p.y - r * 0.32f)
            quadraticTo(p.x + r * 0.22f, p.y - r * 0.32f, p.x + r * 0.22f, p.y - r * 0.1f)
            lineTo(p.x + r * 0.22f, p.y + r * 0.25f)
            close()
        }
        drawPath(slab, stone.darker(0.05f))
        drawPath(slab, dark, style = Stroke(r * 0.025f))
        drawCircle(rune.copy(alpha = glow * 0.8f), r * 0.04f, p + Offset(0f, -r * 0.08f))
    }
    // Kopf: Helmblock mit leuchtendem T-Schlitz
    val head = Offset(c.x, c.y - r * 0.8f)
    drawRoundRect(stone, Offset(head.x - r * 0.24f, head.y - r * 0.28f), Size(r * 0.48f, r * 0.5f), CornerRadius(r * 0.08f))
    drawRect(light, Offset(head.x - r * 0.24f, head.y - r * 0.28f), Size(r * 0.48f, r * 0.04f))
    drawPath(Path().apply {
        moveTo(head.x - r * 0.12f, head.y - r * 0.28f)
        lineTo(head.x, head.y - r * 0.42f)
        lineTo(head.x + r * 0.12f, head.y - r * 0.28f)
        close()
    }, stone.darker(0.05f))
    if (!bodyOnly) drawLine(rune.copy(alpha = 0.35f * glow), Offset(head.x - r * 0.16f, head.y - r * 0.05f), Offset(head.x + r * 0.16f, head.y - r * 0.05f), r * 0.12f, StrokeCap.Round)
    drawLine(rune.copy(alpha = glow), Offset(head.x - r * 0.15f, head.y - r * 0.05f), Offset(head.x + r * 0.15f, head.y - r * 0.05f), r * 0.045f)
    drawLine(rune.copy(alpha = glow), Offset(head.x, head.y - r * 0.05f), Offset(head.x, head.y + r * 0.15f), r * 0.04f)
}

// --- Lichkönig ---

/**
 * Schwebender Lichkönig: zerfetzte Robe ohne Beine, Stehkragen mit Goldbesatz, Schädel mit Zackenkrone,
 * Seelenstein-Amulett, Knochenhände und Stab mit Phylakterium; kreisende Seelen.
 */
internal fun DrawScope.drawLichFigure(c: Offset, r: Float, color: Color, ink: EnemyInk, time: Float, phase: Float, casting: Boolean, bodyOnly: Boolean) {
    val gold = ink(Color(0xFFFACC15))
    val bone = ink(Color(0xFFE7E5E4))
    val soul = ink(Color(0xFF67E8F9))
    val hover = sin(time * 2f + phase) * r * 0.05f
    val cc = c + Offset(0f, hover)
    val glow = if (bodyOnly) 1f else 0.65f + 0.35f * sin(time * 4f + phase)
    if (!bodyOnly) {
        // Kreisende Seelen mit Schweif
        for (i in 0 until 4) {
            val a = time * 1.5f + i * PI_F / 2f
            val p = cc + Offset(kotlin.math.cos(a) * r * 1.35f, sin(a) * r * 1.35f * 0.55f)
            val back = cc + Offset(kotlin.math.cos(a - 0.35f) * r * 1.35f, sin(a - 0.35f) * r * 1.35f * 0.55f)
            drawLine(soul.copy(alpha = 0.35f), back, p, r * 0.08f, StrokeCap.Round)
            drawCircle(soul.copy(alpha = 0.7f), r * 0.1f, p)
            drawCircle(Color.White.copy(alpha = 0.8f), r * 0.04f, p)
        }
        // Schattendunst unter dem Saum
        drawOval(color.copy(alpha = 0.35f), Offset(cc.x - r * 0.8f, cc.y + r * 0.6f), Size(r * 1.6f, r * 0.35f))
    }
    // Zerfetzte Robe, die in Fetzen ausläuft
    val wave = sin(time * 3f + phase) * r * 0.06f
    val robe = Path().apply {
        moveTo(cc.x - r * 0.45f, cc.y - r * 0.55f)
        lineTo(cc.x + r * 0.45f, cc.y - r * 0.55f)
        quadraticTo(cc.x + r * 0.75f, cc.y + r * 0.2f, cc.x + r * 0.8f + wave, cc.y + r * 0.7f)
        lineTo(cc.x + r * 0.55f, cc.y + r * 0.55f)
        lineTo(cc.x + r * 0.4f - wave, cc.y + r * 0.85f)
        lineTo(cc.x + r * 0.2f, cc.y + r * 0.6f)
        lineTo(cc.x + wave, cc.y + r * 0.9f)
        lineTo(cc.x - r * 0.2f, cc.y + r * 0.6f)
        lineTo(cc.x - r * 0.4f + wave, cc.y + r * 0.85f)
        lineTo(cc.x - r * 0.55f, cc.y + r * 0.55f)
        lineTo(cc.x - r * 0.8f - wave, cc.y + r * 0.7f)
        quadraticTo(cc.x - r * 0.75f, cc.y + r * 0.2f, cc.x - r * 0.45f, cc.y - r * 0.55f)
        close()
    }
    drawPath(robe, color)
    clipPath(robe) {
        drawRect(Color.Black.copy(alpha = 0.25f), Offset(cc.x + r * 0.25f, cc.y - r * 0.6f), Size(r, r * 2f))
        // Offene Vorderbahn mit Rippen
        drawPath(Path().apply {
            moveTo(cc.x - r * 0.12f, cc.y - r * 0.45f)
            lineTo(cc.x + r * 0.12f, cc.y - r * 0.45f)
            lineTo(cc.x + r * 0.22f, cc.y + r * 0.9f)
            lineTo(cc.x - r * 0.22f, cc.y + r * 0.9f)
            close()
        }, color.darker(0.45f))
        for (i in 0..2) drawArc(bone.copy(alpha = 0.85f), 200f, 140f, false, Offset(cc.x - r * 0.12f, cc.y - r * 0.32f + i * r * 0.09f), Size(r * 0.24f, r * 0.12f), style = Stroke(r * 0.025f, cap = StrokeCap.Round))
        // Goldbordüre
        drawLine(gold, Offset(cc.x - r * 0.12f, cc.y - r * 0.45f), Offset(cc.x - r * 0.22f, cc.y + r * 0.9f), r * 0.035f)
        drawLine(gold, Offset(cc.x + r * 0.12f, cc.y - r * 0.45f), Offset(cc.x + r * 0.22f, cc.y + r * 0.9f), r * 0.035f)
        for (i in 0..2) drawCircle(soul.copy(alpha = 0.6f * glow), r * 0.025f, Offset(cc.x - r * 0.5f + i * r * 0.07f, cc.y + r * (0.2f + i * 0.12f)))
    }
    // Arme mit weiten Ärmeln und Knochenhänden
    val leftHand = Offset(cc.x - r * 0.7f, cc.y + r * (if (casting) -0.2f else 0.05f))
    val rightHand = Offset(cc.x + r * 0.72f, cc.y + r * 0.0f)
    for (hand in listOf(leftHand, rightHand)) {
        val s = if (hand.x > cc.x) 1f else -1f
        val shoulder = Offset(cc.x + s * r * 0.4f, cc.y - r * 0.45f)
        val cuff = shoulder + (hand - shoulder) * 0.75f
        drawPath(Path().apply {
            moveTo(shoulder.x - s * r * 0.08f, shoulder.y - r * 0.05f)
            lineTo(shoulder.x + s * r * 0.12f, shoulder.y)
            lineTo(cuff.x + s * r * 0.14f, cuff.y + r * 0.02f)
            lineTo(cuff.x + s * r * 0.02f, cuff.y + r * 0.2f)
            lineTo(cuff.x - s * r * 0.16f, cuff.y + r * 0.12f)
            close()
        }, color.lighter(0.08f))
        drawLine(gold, Offset(cuff.x + s * r * 0.14f, cuff.y + r * 0.02f), Offset(cuff.x - s * r * 0.16f, cuff.y + r * 0.12f), r * 0.03f)
        for (f in -1..1) drawLine(bone, hand, hand + Offset(s * r * 0.08f + f * r * 0.04f, r * 0.12f), r * 0.025f, StrokeCap.Round)
        drawCircle(bone, r * 0.06f, hand)
    }
    // Stab mit Phylakterium in der rechten Hand
    val top = rightHand + Offset(r * 0.05f, -r * 1.05f)
    drawLine(ink(Color(0xFF1E1B4B)), rightHand + Offset(0f, r * 0.6f), top, r * 0.06f, StrokeCap.Round)
    for (side in listOf(-1f, 1f)) {
        drawPath(Path().apply {
            moveTo(top.x, top.y + r * 0.05f)
            quadraticTo(top.x + side * r * 0.2f, top.y, top.x + side * r * 0.12f, top.y - r * 0.28f)
        }, bone, style = Stroke(r * 0.04f, cap = StrokeCap.Round))
    }
    val gem = top + Offset(0f, -r * 0.13f)
    if (!bodyOnly) drawCircle(soul.copy(alpha = 0.3f * glow), r * 0.22f, gem)
    drawPath(Path().apply {
        moveTo(gem.x, gem.y - r * 0.13f)
        lineTo(gem.x + r * 0.08f, gem.y)
        lineTo(gem.x, gem.y + r * 0.13f)
        lineTo(gem.x - r * 0.08f, gem.y)
        close()
    }, soul)
    drawCircle(bone, r * 0.06f, rightHand)
    // Seelenblitz in der linken Hand beim Zaubern
    if (casting && !bodyOnly) {
        drawCircle(soul.copy(alpha = 0.35f), r * 0.2f, leftHand + Offset(0f, -r * 0.15f))
        drawCircle(soul, r * 0.09f, leftHand + Offset(0f, -r * 0.15f))
    }
    // Hoher, gezackter Stehkragen mit Goldrand
    for (side in listOf(-1f, 1f)) {
        val collar = Path().apply {
            moveTo(cc.x + side * r * 0.12f, cc.y - r * 0.45f)
            lineTo(cc.x + side * r * 0.5f, cc.y - r * 0.62f)
            lineTo(cc.x + side * r * 0.42f, cc.y - r * 0.95f)
            lineTo(cc.x + side * r * 0.32f, cc.y - r * 0.8f)
            lineTo(cc.x + side * r * 0.24f, cc.y - r * 1.0f)
            lineTo(cc.x + side * r * 0.16f, cc.y - r * 0.62f)
            close()
        }
        drawPath(collar, color.darker(0.2f))
        drawPath(collar, gold, style = Stroke(r * 0.025f))
    }
    // Amulett mit Seelenstein
    for (i in -2..2) drawCircle(gold, r * 0.025f, Offset(cc.x + i * r * 0.06f, cc.y - r * 0.42f + (4 - i * i) * r * 0.012f))
    if (!bodyOnly) drawCircle(soul.copy(alpha = 0.35f * glow), r * 0.1f, Offset(cc.x, cc.y - r * 0.3f))
    drawCircle(gold, r * 0.06f, Offset(cc.x, cc.y - r * 0.3f))
    drawCircle(soul, r * 0.04f, Offset(cc.x, cc.y - r * 0.3f))
    // Schädel mit glühenden Augen, Kiefer und Zackenkrone
    val head = Offset(cc.x, cc.y - r * 0.72f)
    val hr = r * 0.26f
    drawCircle(bone, hr, head)
    drawRoundRect(bone, Offset(head.x - hr * 0.55f, head.y + hr * 0.35f), Size(hr * 1.1f, hr * 0.6f), CornerRadius(hr * 0.2f))
    drawArc(Color.White.copy(alpha = 0.5f), 200f, 60f, false, Offset(head.x - hr * 0.75f, head.y - hr * 0.8f), Size(hr * 1.5f, hr * 1.5f), style = Stroke(hr * 0.1f, cap = StrokeCap.Round))
    for (side in listOf(-1f, 1f)) {
        val e = Offset(head.x + side * hr * 0.38f, head.y + hr * 0.05f)
        drawOval(ink(Color(0xFF0F172A)), e - Offset(hr * 0.26f, hr * 0.28f), Size(hr * 0.52f, hr * 0.52f))
        if (!bodyOnly) drawCircle(soul.copy(alpha = 0.4f * glow), hr * 0.3f, e)
        drawCircle(soul.copy(alpha = glow), hr * 0.12f, e)
        if (!bodyOnly) drawLine(soul.copy(alpha = 0.35f * glow), e, e + Offset(side * hr * 0.4f, -hr * 0.35f), hr * 0.08f, StrokeCap.Round)
    }
    drawPath(Path().apply {
        moveTo(head.x, head.y + hr * 0.3f)
        lineTo(head.x - hr * 0.1f, head.y + hr * 0.5f)
        lineTo(head.x + hr * 0.1f, head.y + hr * 0.5f)
        close()
    }, ink(Color(0xFF0F172A)))
    for (i in -2..2) drawLine(bone.darker(0.35f), Offset(head.x + i * hr * 0.18f, head.y + hr * 0.6f), Offset(head.x + i * hr * 0.18f, head.y + hr * 0.88f), hr * 0.05f)
    val crownBase = head.y - hr * 0.62f
    drawPath(Path().apply {
        moveTo(head.x - hr * 0.85f, crownBase + hr * 0.12f)
        lineTo(head.x - hr * 0.95f, crownBase - hr * 0.7f)
        lineTo(head.x - hr * 0.55f, crownBase - hr * 0.25f)
        lineTo(head.x - hr * 0.3f, crownBase - hr * 0.95f)
        lineTo(head.x, crownBase - hr * 0.35f)
        lineTo(head.x + hr * 0.3f, crownBase - hr * 0.95f)
        lineTo(head.x + hr * 0.55f, crownBase - hr * 0.25f)
        lineTo(head.x + hr * 0.95f, crownBase - hr * 0.7f)
        lineTo(head.x + hr * 0.85f, crownBase + hr * 0.12f)
        close()
    }, gold)
    drawLine(gold.darker(0.25f), Offset(head.x - hr * 0.85f, crownBase), Offset(head.x + hr * 0.85f, crownBase), hr * 0.12f)
    drawCircle(soul, hr * 0.1f, Offset(head.x, crownBase - hr * 0.12f))
    for (side in listOf(-1f, 1f)) drawCircle(ink(Color(0xFF7C3AED)), hr * 0.07f, Offset(head.x + side * hr * 0.5f, crownBase - hr * 0.08f))
}

private const val PI_F = 3.1415927f
