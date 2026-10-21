package com.adlessarcade.games.runetactics

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

internal val elementalArt: Map<String, DrawScope.(ArtBody) -> Unit> = mapOf(
    "sparkling" to DrawScope::drawSparkling,
    "emberling" to DrawScope::drawEmberling,
    "rockwarden" to DrawScope::drawRockwarden,
    "stormspirit" to DrawScope::drawStormspirit,
    "fireelemental" to DrawScope::drawFireElemental,
    "primal" to DrawScope::drawPrimal,
)

private val FireOrange = Color(0xFFF97316)
private val FireYellow = Color(0xFFFDE047)
private val FireRed = Color(0xFFDC2626)

/** Flackernde Flamme mit Fuß bei [base], Breite [w] und Höhe [h]. */
private fun DrawScope.flame(base: Offset, w: Float, h: Float, color: Color, time: Float, phase: Float = 0f) {
    val flick = sin(time * 10f + phase) * w * 0.25f
    drawPath(Path().apply {
        moveTo(base.x - w, base.y)
        quadraticTo(base.x - w * 1.05f, base.y - h * 0.55f, base.x + flick, base.y - h)
        quadraticTo(base.x + w * 1.05f, base.y - h * 0.55f, base.x + w, base.y)
        quadraticTo(base.x, base.y + w * 0.5f, base.x - w, base.y)
        close()
    }, color)
}

/** Lodernder Flammenkranz um einen Kopf: außen rot/orange, innen gelb. */
private fun DrawScope.flameCrown(h: Offset, hr: Float, time: Float, size: Float = 1f) {
    for (layer in 0..2) {
        val color = listOf(FireRed, FireOrange, FireYellow)[layer]
        val shrink = 1f - layer * 0.22f
        for (i in -2..2) {
            val base = Offset(h.x + i * hr * 0.36f * shrink, h.y - hr * 0.35f)
            flame(base, hr * 0.28f * shrink, hr * (1.0f + (2 - kotlin.math.abs(i)) * 0.35f) * size * shrink, color, time, i * 1.7f + layer)
        }
    }
}

/** Zickzack-Blitz von [a] nach [b]. */
private fun DrawScope.bolt(a: Offset, b: Offset, color: Color, width: Float, jag: Float, seed: Float) {
    val d = b - a
    val n = Offset(-d.y, d.x) * (1f / d.getDistance().coerceAtLeast(0.001f))
    val path = Path().apply {
        moveTo(a.x, a.y)
        for (k in 1..3) {
            val t = k / 4f
            val off = (if (k % 2 == 0) 1f else -1f) * jag * (0.6f + 0.4f * sin(seed + k))
            val p = a + d * t + n * off
            lineTo(p.x, p.y)
        }
        lineTo(b.x, b.y)
    }
    drawPath(path, color, style = Stroke(width, cap = StrokeCap.Round))
}

/** Leuchtende, schmale Schlitzaugen, innen tiefer gezogen – ernst statt niedlich. */
private fun DrawScope.glowEyes(h: Offset, hr: Float, dir: Float, glow: Color, core: Color = Color.White, size: Float = 1f, pulse: Float = 1f) {
    for (side in listOf(-1f, 1f)) {
        val e = Offset(h.x + side * hr * 0.34f + dir * hr * 0.08f, h.y + hr * 0.02f)
        val w = hr * 0.2f * size
        drawOval(glow.copy(alpha = 0.4f * pulse), e - Offset(w * 1.5f, w * 0.8f), Size(w * 3f, w * 1.6f))
        val eye = Path().apply {
            // Außen höher, innen tiefer; Oberkante gerade, Unterkante gerundet
            moveTo(e.x + side * w, e.y - w * 0.45f)
            lineTo(e.x - side * w, e.y + w * 0.05f)
            quadraticTo(e.x, e.y + w * 0.55f, e.x + side * w, e.y - w * 0.45f)
            close()
        }
        drawPath(eye, core)
    }
}

// --- Funkengeist ---

private fun DrawScope.drawSparkling(b: ArtBody) {
    val u = b.u
    val c = b.c
    val dir = b.dir
    val time = b.time
    val yellow = Color(0xFFFACC15)
    val white = Color(0xFFFFFBEB)
    val p = b.pulse(8f)
    val hover = sin(time * 3f) * u * 0.08f
    val core = c + Offset(0f, -u * 0.15f + hover)

    drawCircle(yellow.copy(alpha = 0.15f + 0.08f * p), u * 1.0f, core)
    // Spitz zulaufender Energieschweif statt Beinen
    val wave = sin(time * 4f) * u * 0.1f
    val tailTip = Offset(c.x - dir * u * 0.15f + wave, b.footY - u * 0.12f)
    val tail = Path().apply {
        moveTo(core.x - u * 0.3f, core.y + u * 0.2f)
        quadraticTo(core.x - u * 0.22f + wave * 0.5f, core.y + u * 0.7f, tailTip.x, tailTip.y)
        quadraticTo(core.x + u * 0.22f + wave * 0.5f, core.y + u * 0.7f, core.x + u * 0.3f, core.y + u * 0.2f)
        close()
    }
    drawPath(tail, Brush.verticalGradient(listOf(yellow.copy(alpha = 0.85f), yellow.copy(alpha = 0.1f)), core.y + u * 0.2f, tailTip.y))
    bolt(core + Offset(-u * 0.08f, u * 0.4f), tailTip + Offset(0f, -u * 0.1f), white.copy(alpha = 0.7f), u * 0.025f, u * 0.05f, time * 8f)
    // Strahlenkranz
    for (i in 0 until 8) {
        val a = time * 1.5f + i * PI.toFloat() / 4f
        val len = u * (0.55f + 0.12f * sin(time * 9f + i))
        drawLine(yellow.copy(alpha = 0.7f), core + Offset(cos(a) * u * 0.38f, sin(a) * u * 0.38f), core + Offset(cos(a) * len, sin(a) * len), u * 0.05f, StrokeCap.Round)
    }
    // Kleine Ärmchen
    val hand = core + Offset(dir * u * 0.55f, u * 0.1f)
    val off = core + Offset(-dir * u * 0.5f, u * 0.12f)
    drawLine(yellow, core + Offset(dir * u * 0.3f, u * 0.05f), hand, u * 0.1f, StrokeCap.Round)
    drawLine(yellow, core + Offset(-dir * u * 0.3f, u * 0.05f), off, u * 0.1f, StrokeCap.Round)
    // Leuchtender Kugelkörper
    drawCircle(Brush.radialGradient(listOf(white, yellow, yellow.shade(0.15f)), core + Offset(-u * 0.1f, -u * 0.1f), u * 0.5f), u * 0.42f, core)
    // Zackige Blitzhaare
    for (i in -2..2) {
        val base = core + Offset(i * u * 0.14f, -u * 0.32f)
        val tip = base + Offset(i * u * 0.08f + sin(time * 12f + i) * u * 0.03f, -u * (0.3f + (2 - kotlin.math.abs(i)) * 0.08f))
        drawPath(Path().apply {
            moveTo(base.x - u * 0.07f, base.y)
            lineTo((base.x + tip.x) / 2f + u * 0.05f, (base.y + tip.y) / 2f)
            lineTo(tip.x, tip.y)
            lineTo((base.x + tip.x) / 2f - u * 0.02f, (base.y + tip.y) / 2f + u * 0.04f)
            lineTo(base.x + u * 0.07f, base.y)
            close()
        }, yellow)
    }
    // Gesicht: weiß glühende Schlitzaugen
    glowEyes(core + Offset(0f, -u * 0.04f), u * 0.42f, dir, Color(0xFFB45309), Color.White, 0.9f, 1.6f)
    // Kreisende Funken
    for (i in 0 until 3) {
        val a = time * 4f + i * 2.1f
        val s = core + Offset(cos(a) * u * 0.75f, sin(a) * u * 0.35f)
        drawCircle(white, u * 0.04f, s)
        drawLine(yellow, s - Offset(u * 0.07f, 0f), s + Offset(u * 0.07f, 0f), u * 0.02f)
        drawLine(yellow, s - Offset(0f, u * 0.07f), s + Offset(0f, u * 0.07f), u * 0.02f)
    }
    // Bogen aus Licht
    val grip = hand
    val top = grip + Offset(-dir * u * 0.05f, -u * 0.6f)
    val bottom = grip + Offset(-dir * u * 0.05f, u * 0.6f)
    drawLine(white.copy(alpha = 0.7f), top + Offset(dir * u * 0.03f, 0f), bottom + Offset(dir * u * 0.03f, 0f), u * 0.02f)
    val bow = Path().apply {
        moveTo(top.x, top.y)
        quadraticTo(grip.x + dir * u * 0.38f, grip.y - u * 0.3f, grip.x + dir * u * 0.1f, grip.y)
        quadraticTo(grip.x + dir * u * 0.38f, grip.y + u * 0.3f, bottom.x, bottom.y)
    }
    drawPath(bow, yellow.copy(alpha = 0.35f * p), style = Stroke(u * 0.16f, cap = StrokeCap.Round))
    drawPath(bow, white, style = Stroke(u * 0.05f, cap = StrokeCap.Round))
    drawCircle(white, u * 0.07f, hand)
    drawCircle(white, u * 0.07f, off)
}

// --- Glutwicht ---

private fun DrawScope.drawEmberling(b: ArtBody) {
    val u = b.u
    val c = b.c
    val dir = b.dir
    val time = b.time
    val coal = Color(0xFF292524)
    val p = b.pulse(6f)
    // Gedrungen: Körper tiefer, großer Kopf
    val body = c + Offset(0f, u * 0.25f)

    // Aufsteigende Funken
    for (i in 0 until 4) {
        val t = (time * 0.8f + i * 0.25f) % 1f
        drawCircle(FireYellow.copy(alpha = 1f - t), u * 0.035f, Offset(c.x + sin(i * 2.3f + time * 2f) * u * 0.5f, c.y + u * 0.4f - t * u * 1.6f))
    }
    // Stummelbeine
    for (side in listOf(-1f, 1f)) {
        drawRoundRect(coal, Offset(c.x + side * u * 0.22f - u * 0.13f, body.y + u * 0.35f), Size(u * 0.26f, b.footY - body.y - u * 0.35f), CornerRadius(u * 0.1f))
        drawLine(FireOrange.copy(alpha = 0.5f + 0.4f * p), Offset(c.x + side * u * 0.2f, body.y + u * 0.45f), Offset(c.x + side * u * 0.25f, b.footY - u * 0.12f), u * 0.03f, StrokeCap.Round)
    }
    // Arme
    val hand = body + Offset(dir * u * 0.68f, -u * 0.05f)
    val off = body + Offset(-dir * u * 0.62f, u * 0.05f)
    drawLine(coal.bright(0.1f), body + Offset(dir * u * 0.35f, -u * 0.15f), hand, u * 0.2f, StrokeCap.Round)
    drawLine(coal.bright(0.1f), body + Offset(-dir * u * 0.35f, -u * 0.15f), off, u * 0.2f, StrokeCap.Round)
    // Kohlekörper mit glühenden Rissen
    val torso = Path().apply {
        moveTo(body.x - u * 0.45f, body.y - u * 0.3f)
        quadraticTo(body.x, body.y - u * 0.5f, body.x + u * 0.45f, body.y - u * 0.3f)
        quadraticTo(body.x + u * 0.55f, body.y + u * 0.2f, body.x + u * 0.3f, body.y + u * 0.45f)
        lineTo(body.x - u * 0.3f, body.y + u * 0.45f)
        quadraticTo(body.x - u * 0.55f, body.y + u * 0.2f, body.x - u * 0.45f, body.y - u * 0.3f)
        close()
    }
    drawPath(torso, coal)
    clipPath(torso) {
        drawCircle(FireOrange.copy(alpha = 0.35f * p), u * 0.35f, body + Offset(0f, u * 0.05f))
        drawRect(Color.Black.copy(alpha = 0.25f), Offset(body.x + dir * u * 0.15f - (if (dir < 0) u * 0.5f else 0f), body.y - u * 0.5f), Size(u * 0.5f, u))
    }
    drawPath(Path().apply {
        moveTo(body.x - u * 0.3f, body.y - u * 0.2f)
        lineTo(body.x - u * 0.1f, body.y)
        lineTo(body.x - u * 0.2f, body.y + u * 0.2f)
        moveTo(body.x - u * 0.1f, body.y)
        lineTo(body.x + u * 0.15f, body.y - u * 0.05f)
        lineTo(body.x + u * 0.3f, body.y + u * 0.25f)
    }, FireOrange.copy(alpha = 0.6f + 0.4f * p), style = Stroke(u * 0.04f, cap = StrokeCap.Round))
    drawCircle(FireYellow, u * 0.06f, body + Offset(-u * 0.1f, 0f))

    // Großer Kopf mit Flammenhaar
    val h = body + Offset(0f, -u * 0.72f)
    val hr = u * 0.42f
    flameCrown(h, hr, time, 0.9f)
    drawCircle(coal.bright(0.05f), hr, h)
    drawArc(FireOrange.copy(alpha = 0.6f), if (dir > 0) 100f else -80f, 160f, false, h - Offset(hr * 0.92f, hr * 0.92f), Size(hr * 1.84f, hr * 1.84f), style = Stroke(hr * 0.08f))
    glowEyes(h, hr, dir, FireOrange, FireYellow, 1.1f, p)
    // Schmaler glühender Riss als Mund
    drawPath(Path().apply {
        moveTo(h.x - hr * 0.25f + dir * hr * 0.06f, h.y + hr * 0.45f)
        lineTo(h.x - hr * 0.08f + dir * hr * 0.06f, h.y + hr * 0.5f)
        lineTo(h.x + hr * 0.06f + dir * hr * 0.06f, h.y + hr * 0.44f)
        lineTo(h.x + hr * 0.25f + dir * hr * 0.06f, h.y + hr * 0.49f)
    }, FireOrange.copy(alpha = 0.6f + 0.4f * p), style = Stroke(hr * 0.07f, cap = StrokeCap.Round))

    // Obsidianbeil mit glühender Schneide
    val top = hand + Offset(dir * u * 0.1f, -u * 0.7f)
    drawLine(Color(0xFF44403C), hand + Offset(0f, u * 0.15f), top, u * 0.08f, StrokeCap.Round)
    // Sichelförmiges Obsidianblatt mit glühender Schneide
    val blade = Path().apply {
        moveTo(top.x, top.y - u * 0.1f)
        quadraticTo(top.x + dir * u * 0.2f, top.y - u * 0.12f, top.x + dir * u * 0.3f, top.y - u * 0.32f)
        quadraticTo(top.x + dir * u * 0.52f, top.y, top.x + dir * u * 0.3f, top.y + u * 0.32f)
        quadraticTo(top.x + dir * u * 0.2f, top.y + u * 0.12f, top.x, top.y + u * 0.1f)
        close()
    }
    drawPath(blade, Color(0xFF3F3A46))
    clipPath(blade) { drawRect(Color(0xFF1C1917), Offset(top.x - u, top.y), Size(u * 2f, u)) }
    drawPath(Path().apply {
        moveTo(top.x + dir * u * 0.3f, top.y - u * 0.3f)
        quadraticTo(top.x + dir * u * 0.52f, top.y, top.x + dir * u * 0.3f, top.y + u * 0.3f)
    }, FireOrange.copy(alpha = 0.7f + 0.3f * p), style = Stroke(u * 0.05f, cap = StrokeCap.Round))
    drawLine(Color.White.copy(alpha = 0.35f), top + Offset(dir * u * 0.12f, -u * 0.08f), top + Offset(dir * u * 0.26f, -u * 0.22f), u * 0.025f, StrokeCap.Round)
    drawCircle(coal.bright(0.2f), u * 0.12f, hand)
    drawCircle(coal.bright(0.2f), u * 0.12f, off)
}

// --- Felswächter ---

private fun DrawScope.drawRockwarden(b: ArtBody) {
    val u = b.u
    val c = b.c
    val dir = b.dir
    val stone = Color(0xFF78716C)
    val moss = Color(0xFF84CC16)
    val crystal = Color(0xFF4ADE80)
    val p = b.pulse(2.5f)

    // Säulenbeine aus Fels
    for (side in listOf(-1f, 1f)) {
        val x = c.x + side * u * 0.3f
        drawRoundRect(stone.shade(0.2f), Offset(x - u * 0.2f, b.hipY), Size(u * 0.4f, b.footY - b.hipY), CornerRadius(u * 0.1f))
        drawRoundRect(stone.shade(0.05f), Offset(x - u * 0.24f, b.footY - u * 0.2f), Size(u * 0.48f, u * 0.2f), CornerRadius(u * 0.06f))
        drawLine(stone.shade(0.4f), Offset(x - u * 0.05f, b.hipY + u * 0.15f), Offset(x + u * 0.06f, b.hipY + u * 0.35f), u * 0.03f)
    }
    // Riesige Steinarme mit Fäusten
    val hand = b.hand + Offset(dir * u * 0.12f, u * 0.15f)
    val off = b.offHand + Offset(-dir * u * 0.1f, u * 0.05f)
    for (hd in listOf(hand, off)) {
        val s = if (hd.x > c.x) 1f else -1f
        val shoulder = Offset(c.x + s * u * 0.55f, b.shoulderY + u * 0.05f)
        drawLine(stone.shade(0.12f), shoulder, hd, u * 0.32f, StrokeCap.Round)
        drawRoundRect(stone.shade(0.05f), hd - Offset(u * 0.2f, u * 0.17f), Size(u * 0.4f, u * 0.36f), CornerRadius(u * 0.1f))
        for (k in 0..2) drawLine(stone.shade(0.35f), hd + Offset(-u * 0.12f + k * u * 0.12f, -u * 0.15f), hd + Offset(-u * 0.12f + k * u * 0.12f, -u * 0.02f), u * 0.025f)
    }
    // Felsrumpf aus Blöcken mit Moos und Kristallkern
    val top = b.shoulderY - u * 0.15f
    val torso = Path().apply {
        moveTo(c.x - u * 0.62f, top + u * 0.1f)
        lineTo(c.x - u * 0.2f, top - u * 0.05f)
        lineTo(c.x + u * 0.35f, top)
        lineTo(c.x + u * 0.65f, top + u * 0.15f)
        lineTo(c.x + u * 0.5f, b.hipY + u * 0.1f)
        lineTo(c.x - u * 0.45f, b.hipY + u * 0.12f)
        close()
    }
    drawPath(torso, stone)
    clipPath(torso) {
        drawRect(stone.shade(0.25f), Offset(c.x + dir * u * 0.2f - (if (dir < 0) u * 0.7f else 0f), top - u * 0.1f), Size(u * 0.7f, u * 1.2f))
        drawCircle(crystal.copy(alpha = 0.25f * p), u * 0.35f, Offset(c.x, (top + b.hipY) / 2f))
    }
    drawLine(stone.shade(0.4f), Offset(c.x - u * 0.55f, (top + b.hipY) / 2f), Offset(c.x - u * 0.15f, (top + b.hipY) / 2f + u * 0.05f), u * 0.03f)
    drawLine(stone.shade(0.4f), Offset(c.x + u * 0.18f, top + u * 0.05f), Offset(c.x + u * 0.25f, top + u * 0.3f), u * 0.03f)
    // Kristallkern
    val core = Offset(c.x, (top + b.hipY) / 2f + u * 0.02f)
    drawPath(Path().apply {
        moveTo(core.x, core.y - u * 0.18f)
        lineTo(core.x + u * 0.12f, core.y)
        lineTo(core.x, core.y + u * 0.18f)
        lineTo(core.x - u * 0.12f, core.y)
        close()
    }, crystal)
    drawLine(Color.White.copy(alpha = 0.7f), core + Offset(-u * 0.03f, -u * 0.1f), core + Offset(-u * 0.06f, 0f), u * 0.025f)
    // Moospolster und Schulterkristalle
    for (side in listOf(-1f, 1f)) {
        drawOval(moss.shade(0.15f), Offset(c.x + side * u * 0.42f - u * 0.2f, top - u * 0.02f), Size(u * 0.4f, u * 0.14f))
        for (k in 0..1) {
            val base = Offset(c.x + side * u * (0.35f + k * 0.15f), top + u * 0.02f)
            drawPath(Path().apply {
                moveTo(base.x - u * 0.06f, base.y)
                lineTo(base.x + side * u * 0.03f, base.y - u * (0.28f - k * 0.08f))
                lineTo(base.x + u * 0.06f, base.y)
                close()
            }, crystal.shade(0.1f * k))
        }
    }

    // Felskopf ohne Hals, tief zwischen den Schultern
    val h = Offset(c.x, b.shoulderY - u * 0.3f)
    val hr = u * 0.36f
    drawRoundRect(stone.bright(0.08f), h - Offset(hr * 1.05f, hr * 0.85f), Size(hr * 2.1f, hr * 1.7f), CornerRadius(hr * 0.35f))
    drawRoundRect(stone.shade(0.2f), h - Offset(hr * 1.05f, hr * 0.85f), Size(hr * 2.1f, hr * 0.45f), CornerRadius(hr * 0.35f))
    drawOval(moss, Offset(h.x - hr * 0.7f, h.y - hr * 0.95f), Size(hr * 1.2f, hr * 0.35f))
    glowEyes(h + Offset(0f, hr * 0.05f), hr, dir, crystal, Color(0xFFDCFCE7), 0.9f, p)
    drawLine(stone.shade(0.45f), Offset(h.x - hr * 0.35f + dir * hr * 0.08f, h.y + hr * 0.5f), Offset(h.x + hr * 0.35f + dir * hr * 0.08f, h.y + hr * 0.5f), hr * 0.1f, StrokeCap.Round)

    // Felsplatte als Schild mit eingemeißelter Rune
    val sc = off + Offset(-dir * u * 0.15f, -u * 0.25f)
    val slab = Path().apply {
        moveTo(sc.x - u * 0.38f, sc.y - u * 0.58f)
        lineTo(sc.x + u * 0.32f, sc.y - u * 0.62f)
        lineTo(sc.x + u * 0.4f, sc.y + u * 0.5f)
        lineTo(sc.x - u * 0.34f, sc.y + u * 0.58f)
        close()
    }
    drawPath(slab, Color(0xFF57534E))
    clipPath(slab) { drawRect(Color(0xFF44403C), Offset(sc.x + u * 0.1f, sc.y - u * 0.7f), Size(u * 0.5f, u * 1.4f)) }
    drawPath(slab, Color(0xFF3F3A36), style = Stroke(u * 0.04f))
    drawPath(Path().apply {
        moveTo(sc.x, sc.y - u * 0.3f)
        lineTo(sc.x, sc.y + u * 0.3f)
        moveTo(sc.x, sc.y - u * 0.1f)
        lineTo(sc.x + u * 0.15f, sc.y - u * 0.25f)
        moveTo(sc.x, sc.y + u * 0.05f)
        lineTo(sc.x - u * 0.15f, sc.y - u * 0.1f)
    }, crystal.copy(alpha = 0.55f + 0.45f * p), style = Stroke(u * 0.045f, cap = StrokeCap.Round))
    drawOval(moss.shade(0.1f), Offset(sc.x - u * 0.35f, sc.y + u * 0.42f), Size(u * 0.35f, u * 0.12f))
}

// --- Sturmelementar ---

private fun DrawScope.drawStormspirit(b: ArtBody) {
    val u = b.u
    val c = b.c
    val dir = b.dir
    val time = b.time
    val cloud = Color(0xFF5B82B8)
    val light = Color(0xFF93C5FD)
    val white = Color(0xFFE0F2FE)
    val p = b.pulse(9f)
    val hover = sin(time * 2.5f) * u * 0.06f

    // Wirbelsturm als Unterkörper
    for (k in 0..4) {
        val y = b.hipY + u * 0.1f + k * u * 0.14f + hover
        val w = u * (0.5f - k * 0.08f)
        val sway = sin(time * 6f + k) * u * 0.06f
        drawOval(cloud.bright(0.08f * (k % 2)).copy(alpha = 0.85f), Offset(c.x - w + sway, y - u * 0.06f), Size(w * 2f, u * 0.14f))
        drawArc(light.copy(alpha = 0.6f), 200f, 140f, false, Offset(c.x - w + sway, y - u * 0.06f), Size(w * 2f, u * 0.14f), style = Stroke(u * 0.02f))
    }
    // Wolkenarme
    val hand = b.hand + Offset(0f, hover)
    val off = b.offHand + Offset(0f, hover)
    for (hd in listOf(hand, off)) {
        val s = if (hd.x > c.x) 1f else -1f
        val shoulder = Offset(c.x + s * u * 0.38f, b.shoulderY + u * 0.08f + hover)
        for (k in 0..3) {
            val pt = shoulder + (hd - shoulder) * (k / 3f)
            drawCircle(cloud.bright(0.05f * k), u * (0.14f - k * 0.015f), pt)
        }
    }
    // Wolkenrumpf aus Ballen mit Blitzadern
    val chest = Offset(c.x, (b.shoulderY + b.hipY) / 2f + hover)
    val puffs = listOf(Offset(-0.25f, -0.15f), Offset(0.25f, -0.15f), Offset(0f, -0.25f), Offset(-0.18f, 0.12f), Offset(0.18f, 0.12f), Offset(0f, 0.2f))
    for ((i, o) in puffs.withIndex()) drawCircle(cloud.bright(0.05f * (i % 3)), u * 0.28f, chest + Offset(o.x * u, o.y * u))
    bolt(chest + Offset(-u * 0.2f, -u * 0.25f), chest + Offset(u * 0.05f, u * 0.25f), white.copy(alpha = 0.5f + 0.5f * p), u * 0.035f, u * 0.08f, time * 7f)
    drawCircle(white.copy(alpha = 0.6f * p), u * 0.1f, chest)

    // Kopf: Wolkenballen mit wehenden Sturmsträhnen
    val h = b.head + Offset(0f, hover)
    val hr = b.headR
    for (k in 0..2) {
        val wave = sin(time * 5f + k) * hr * 0.15f
        drawPath(Path().apply {
            moveTo(h.x - hr * 0.5f + k * hr * 0.5f, h.y - hr * 0.7f)
            quadraticTo(h.x - dir * hr * (0.8f + k * 0.2f), h.y - hr * (1.4f + k * 0.1f) + wave, h.x - dir * hr * (1.6f + k * 0.15f), h.y - hr * (0.9f - k * 0.3f) + wave)
        }, light.copy(alpha = 0.7f), style = Stroke(hr * 0.22f, cap = StrokeCap.Round))
    }
    drawCircle(cloud.bright(0.1f), hr, h)
    drawCircle(cloud.bright(0.18f), hr * 0.6f, h + Offset(-hr * 0.35f, -hr * 0.4f))
    drawCircle(cloud.bright(0.18f), hr * 0.55f, h + Offset(hr * 0.4f, -hr * 0.35f))
    glowEyes(h, hr, dir, light, Color.White, 1.0f, p)

    // Blitzklingen in beiden Händen
    for (hd in listOf(hand, off)) {
        val s = if (hd == hand) dir else -dir
        val tip = hd + Offset(s * u * 0.3f, -u * 0.6f)
        bolt(hd, tip, light.copy(alpha = 0.5f), u * 0.12f, u * 0.06f, time * 9f + s)
        bolt(hd, tip, Color.White, u * 0.045f, u * 0.06f, time * 9f + s)
        drawCircle(white, u * 0.09f, hd)
    }
    // Kleine Begleitwolke
    val cl = Offset(c.x - dir * u * 0.8f, b.head.y - u * 0.2f + sin(time * 2f) * u * 0.06f)
    for (o in listOf(-0.1f, 0.1f, 0f)) drawCircle(cloud.bright(0.15f), u * 0.1f, cl + Offset(o * u, if (o == 0f) -u * 0.06f else 0f))
}

// --- Feuerelementar ---

private fun DrawScope.drawFireElemental(b: ArtBody) {
    val u = b.u
    val c = b.c
    val dir = b.dir
    val time = b.time
    val p = b.pulse(7f)
    val hover = sin(time * 2.5f) * u * 0.05f

    drawCircle(FireOrange.copy(alpha = 0.14f + 0.06f * p), u * 1.35f, c + Offset(0f, -u * 0.2f))
    // Flammenschweif als Unterkörper
    for (layer in 0..2) {
        val color = listOf(FireRed, FireOrange, FireYellow)[layer]
        val w = u * (0.48f - layer * 0.13f)
        val sway = sin(time * 4f + layer) * u * 0.08f
        drawPath(Path().apply {
            moveTo(c.x - w, b.hipY - u * 0.05f + hover)
            quadraticTo(c.x - w * 0.8f + sway, b.hipY + u * 0.5f, c.x - dir * u * 0.1f + sway * 2f, b.footY - layer * u * 0.12f)
            quadraticTo(c.x + w * 0.8f + sway, b.hipY + u * 0.5f, c.x + w, b.hipY - u * 0.05f + hover)
            close()
        }, color)
    }
    // Flammenarme
    val hand = b.hand + Offset(0f, hover)
    val off = b.offHand + Offset(dir * u * 0.15f, hover - u * 0.12f)
    for (hd in listOf(hand, off)) {
        val s = if (hd.x > c.x) 1f else -1f
        val shoulder = Offset(c.x + s * u * 0.36f, b.shoulderY + u * 0.08f + hover)
        drawLine(FireRed, shoulder, hd, u * 0.22f, StrokeCap.Round)
        drawLine(FireOrange, shoulder, hd, u * 0.13f, StrokeCap.Round)
    }
    // Flammenrumpf mit glühendem Kern
    val chest = Offset(c.x, (b.shoulderY + b.hipY) / 2f + hover)
    val torso = Path().apply {
        moveTo(c.x - u * 0.45f, b.shoulderY - u * 0.05f + hover)
        quadraticTo(c.x, b.shoulderY - u * 0.15f + hover, c.x + u * 0.45f, b.shoulderY - u * 0.05f + hover)
        quadraticTo(c.x + u * 0.42f, b.hipY - u * 0.15f, c.x + u * 0.3f, b.hipY + hover)
        lineTo(c.x - u * 0.3f, b.hipY + hover)
        quadraticTo(c.x - u * 0.42f, b.hipY - u * 0.15f, c.x - u * 0.45f, b.shoulderY - u * 0.05f + hover)
        close()
    }
    drawPath(torso, FireOrange)
    clipPath(torso) {
        drawRect(FireRed, Offset(c.x + dir * u * 0.15f - (if (dir < 0) u * 0.5f else 0f), b.shoulderY - u * 0.2f), Size(u * 0.5f, u * 1.2f))
        for (k in 0..2) flame(Offset(c.x - u * 0.25f + k * u * 0.25f, b.hipY + u * 0.05f + hover), u * 0.12f, u * 0.4f, FireYellow.copy(alpha = 0.7f), time, k * 2f)
    }
    drawCircle(FireYellow.copy(alpha = 0.6f * p), u * 0.2f, chest)
    drawCircle(Color.White, u * 0.08f, chest)
    // Schultern als Flammenzungen
    for (side in listOf(-1f, 1f)) flame(Offset(c.x + side * u * 0.38f, b.shoulderY + u * 0.08f + hover), u * 0.12f, u * 0.35f, FireYellow, time, side * 3f)

    // Kopf in einer großen Flammenkrone
    val h = b.head + Offset(0f, hover)
    val hr = b.headR
    flameCrown(h, hr, time, 1.25f)
    drawCircle(FireOrange, hr, h)
    drawCircle(FireYellow.copy(alpha = 0.5f), hr * 0.65f, h + Offset(-dir * hr * 0.15f, hr * 0.1f))
    glowEyes(h, hr, dir, FireYellow, Color.White, 0.95f, p)

    // Feuerbogen mit gespanntem Flammenpfeil
    val grip = hand
    val top = grip + Offset(-dir * u * 0.05f, -u * 0.8f)
    val bottom = grip + Offset(-dir * u * 0.05f, u * 0.8f)
    val bow = Path().apply {
        moveTo(top.x, top.y)
        quadraticTo(grip.x + dir * u * 0.45f, grip.y - u * 0.4f, grip.x + dir * u * 0.12f, grip.y)
        quadraticTo(grip.x + dir * u * 0.45f, grip.y + u * 0.4f, bottom.x, bottom.y)
    }
    drawPath(bow, FireRed, style = Stroke(u * 0.12f, cap = StrokeCap.Round))
    drawPath(bow, FireYellow, style = Stroke(u * 0.04f, cap = StrokeCap.Round))
    val nock = Offset(grip.x - dir * u * 0.3f, grip.y)
    drawLine(FireYellow.copy(alpha = 0.8f), top, nock, u * 0.025f)
    drawLine(FireYellow.copy(alpha = 0.8f), bottom, nock, u * 0.025f)
    drawLine(FireOrange, nock, grip + Offset(dir * u * 0.35f, 0f), u * 0.05f, StrokeCap.Round)
    flame(grip + Offset(dir * u * 0.42f, u * 0.06f), u * 0.07f, u * 0.2f, FireYellow, time, 1f)
    drawCircle(FireYellow, u * 0.09f, hand)
    drawCircle(FireYellow, u * 0.09f, nock)
}

// --- Urelementar ---

private fun DrawScope.drawPrimal(b: ArtBody) {
    val u = b.u
    val c = b.c
    val dir = b.dir
    val time = b.time
    val violet = Color(0xFF8B5CF6)
    val cyan = Color(0xFF22D3EE)
    val p = b.pulse(3f)
    val hover = sin(time * 2f) * u * 0.06f
    val elements = listOf(Color(0xFFF97316), Color(0xFF38BDF8), Color(0xFF84CC16), Color(0xFFFACC15))

    drawCircle(violet.copy(alpha = 0.14f + 0.06f * p), u * 1.45f, c + Offset(0f, -u * 0.2f))
    // Schwebende Felsbrocken statt Beinen
    for (k in 0..2) {
        val bob = sin(time * 3f + k * 2f) * u * 0.05f
        val pos = Offset(c.x + (k - 1) * u * 0.3f, b.hipY + u * (0.35f + (k % 2) * 0.25f) + bob)
        val sz = u * (0.22f - (k % 2) * 0.05f)
        drawPath(Path().apply {
            moveTo(pos.x - sz, pos.y - sz * 0.3f)
            lineTo(pos.x - sz * 0.3f, pos.y - sz)
            lineTo(pos.x + sz, pos.y - sz * 0.5f)
            lineTo(pos.x + sz * 0.7f, pos.y + sz * 0.7f)
            lineTo(pos.x - sz * 0.6f, pos.y + sz * 0.8f)
            close()
        }, violet.shade(0.45f))
        drawLine(cyan.copy(alpha = 0.6f), pos - Offset(sz * 0.4f, 0f), pos + Offset(sz * 0.3f, sz * 0.2f), u * 0.025f)
    }
    // Arme aus Kristallsegmenten
    val hand = b.hand + Offset(0f, hover)
    val off = b.offHand + Offset(0f, hover)
    for (hd in listOf(hand, off)) {
        val s = if (hd.x > c.x) 1f else -1f
        val shoulder = Offset(c.x + s * u * 0.38f, b.shoulderY + u * 0.08f + hover)
        val elbow = shoulder + (hd - shoulder) * 0.5f + Offset(s * u * 0.05f, 0f)
        drawLine(violet.shade(0.2f), shoulder, elbow, u * 0.2f, StrokeCap.Round)
        drawLine(violet.shade(0.1f), elbow, hd, u * 0.17f, StrokeCap.Round)
        drawCircle(cyan, u * 0.05f, elbow)
    }
    // Kristallrumpf mit Facetten
    val top = b.shoulderY - u * 0.08f + hover
    val bottom = b.hipY + u * 0.1f + hover
    val torso = Path().apply {
        moveTo(c.x - u * 0.5f, top)
        lineTo(c.x + u * 0.5f, top)
        lineTo(c.x + u * 0.32f, bottom)
        lineTo(c.x, bottom + u * 0.12f)
        lineTo(c.x - u * 0.32f, bottom)
        close()
    }
    drawPath(torso, violet)
    clipPath(torso) {
        drawPath(Path().apply {
            moveTo(c.x, top)
            lineTo(c.x + u * 0.5f * dir, top)
            lineTo(c.x + u * 0.32f * dir, bottom)
            lineTo(c.x, bottom + u * 0.12f)
            close()
        }, violet.shade(0.25f))
        drawLine(violet.bright(0.3f), Offset(c.x - u * 0.4f, top + u * 0.05f), Offset(c.x - u * 0.1f, bottom - u * 0.1f), u * 0.04f)
    }
    drawPath(torso, cyan.copy(alpha = 0.6f), style = Stroke(u * 0.025f))
    // Herz aus vier Elementen
    val heart = Offset(c.x, (top + bottom) / 2f)
    drawCircle(Color.White.copy(alpha = 0.3f * p), u * 0.2f, heart)
    elements.forEachIndexed { i, col -> drawArc(col, i * 90f + time * 60f, 90f, true, heart - Offset(u * 0.12f, u * 0.12f), Size(u * 0.24f, u * 0.24f)) }
    drawCircle(Color.White, u * 0.05f, heart)
    // Kristallschultern
    for (side in listOf(-1f, 1f)) for (k in 0..1) {
        val base = Offset(c.x + side * u * (0.35f + k * 0.14f), top + u * 0.02f)
        drawPath(Path().apply {
            moveTo(base.x - u * 0.06f, base.y)
            lineTo(base.x + side * u * 0.05f, base.y - u * (0.3f - k * 0.1f))
            lineTo(base.x + u * 0.06f, base.y)
            close()
        }, cyan.shade(0.15f * k))
    }

    // Kopf: Kristallgesicht mit Kristallkrone
    val h = b.head + Offset(0f, hover)
    val hr = b.headR
    for (i in -2..2) {
        val base = Offset(h.x + i * hr * 0.35f, h.y - hr * 0.6f)
        drawPath(Path().apply {
            moveTo(base.x - hr * 0.15f, base.y)
            lineTo(base.x + i * hr * 0.08f, base.y - hr * (0.7f + (2 - kotlin.math.abs(i)) * 0.3f))
            lineTo(base.x + hr * 0.15f, base.y)
            close()
        }, if (i == 0) cyan else violet.bright(0.2f))
    }
    val face = Path().apply {
        moveTo(h.x - hr * 0.9f, h.y - hr * 0.55f)
        lineTo(h.x + hr * 0.9f, h.y - hr * 0.55f)
        lineTo(h.x + hr * 0.8f, h.y + hr * 0.4f)
        lineTo(h.x, h.y + hr * 0.95f)
        lineTo(h.x - hr * 0.8f, h.y + hr * 0.4f)
        close()
    }
    drawPath(face, violet.bright(0.1f))
    clipPath(face) { drawRect(violet.shade(0.15f), Offset(h.x + (if (dir > 0) 0f else -hr), h.y - hr), Size(hr, hr * 2f)) }
    glowEyes(h, hr, dir, cyan, Color.White, 1f, p)
    // Kreisende Elementarkugeln
    elements.forEachIndexed { i, col ->
        val a = time * 1.6f + i * PI.toFloat() / 2f
        val pos = c + Offset(cos(a) * u * 1.0f, sin(a) * u * 0.35f - u * 0.3f + hover)
        drawCircle(col.copy(alpha = 0.35f), u * 0.13f, pos)
        drawCircle(col, u * 0.08f, pos)
        drawCircle(Color.White.copy(alpha = 0.7f), u * 0.025f, pos + Offset(-u * 0.025f, -u * 0.025f))
    }

    // Kristallbogen mit Prismasehne
    val grip = hand
    val bt = grip + Offset(-dir * u * 0.05f, -u * 0.85f)
    val bb = grip + Offset(-dir * u * 0.05f, u * 0.85f)
    val stringColors = listOf(elements[0], elements[3], elements[2], elements[1])
    stringColors.forEachIndexed { i, col ->
        val x = dir * u * (0.035f + i * 0.012f)
        drawLine(col.copy(alpha = 0.8f), bt + Offset(x, 0f), bb + Offset(x, 0f), u * 0.012f)
    }
    val bow = Path().apply {
        moveTo(bt.x, bt.y)
        quadraticTo(grip.x + dir * u * 0.45f, grip.y - u * 0.45f, grip.x + dir * u * 0.12f, grip.y)
        quadraticTo(grip.x + dir * u * 0.45f, grip.y + u * 0.45f, bb.x, bb.y)
    }
    drawPath(bow, cyan.copy(alpha = 0.35f * p), style = Stroke(u * 0.16f, cap = StrokeCap.Round))
    drawPath(bow, violet.bright(0.3f), style = Stroke(u * 0.07f, cap = StrokeCap.Round))
    drawPath(bow, Color.White.copy(alpha = 0.6f), style = Stroke(u * 0.02f, cap = StrokeCap.Round))
    for (t in listOf(bt, bb)) drawPath(Path().apply {
        moveTo(t.x - u * 0.06f, t.y)
        lineTo(t.x, t.y - u * 0.12f * (if (t == bt) 1f else -1f))
        lineTo(t.x + u * 0.06f, t.y)
        close()
    }, cyan)
    drawCircle(violet.bright(0.2f), u * 0.1f, hand)
    drawCircle(violet.bright(0.2f), u * 0.1f, off)
}
