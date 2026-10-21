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
import androidx.compose.ui.graphics.drawscope.clipRect
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

internal val creatureArt: Map<String, DrawScope.(ArtBody) -> Unit> = mapOf(
    "wolf" to DrawScope::drawWolf,
    "golem" to DrawScope::drawGolem,
    "spider" to DrawScope::drawSpider,
    "harpy" to DrawScope::drawHarpy,
)

internal val bossArt: Map<String, DrawScope.(ArtBody) -> Unit> = mapOf(
    "bonedragon" to DrawScope::drawBoneDragon,
    "stonetitan" to DrawScope::drawStoneTitan,
    "stormwitch" to DrawScope::drawStormWitch,
)

private fun spike(base: Offset, halfW: Float, tip: Offset) = Path().apply {
    moveTo(base.x - halfW, base.y)
    lineTo(tip.x, tip.y)
    lineTo(base.x + halfW, base.y)
    close()
}

/** Unregelmäßiger Steinblock um [center]; [seed] variiert die Ecken. */
private fun rock(center: Offset, rx: Float, ry: Float, seed: Int): Path = Path().apply {
    val n = 7
    for (i in 0 until n) {
        val a = i * 2f * Math.PI.toFloat() / n + seed * 0.4f
        val r = 0.82f + 0.18f * sin(seed * 3.1f + i * 1.7f)
        val p = center + Offset(cos(a) * rx * r, sin(a) * ry * r)
        if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
    }
    close()
}

// --- Grauwolf ---

private fun DrawScope.drawWolf(b: ArtBody) {
    val u = b.u
    val c = b.c
    val dir = b.dir
    val time = b.time
    val fur = Color(0xFF6B7280)
    val dark = Color(0xFF374151)
    val belly = Color(0xFFD1D5DB)
    val step = sin(time * 6f) * u * 0.08f

    // Buschiger Schwanz mit Fellstreifen und Zotteln
    val tail = Path().apply {
        moveTo(c.x - dir * u * 0.75f, c.y - u * 0.15f)
        quadraticTo(c.x - dir * u * 1.45f, c.y - u * 0.2f, c.x - dir * u * 1.4f, c.y - u * 0.8f)
        quadraticTo(c.x - dir * u * 1.2f, c.y - u * 0.35f, c.x - dir * u * 0.75f, c.y + u * 0.1f)
        close()
    }
    drawPath(tail, fur)
    for (k in 0..3) {
        val t = k / 4f
        val p = Offset(c.x - dir * u * (0.85f + t * 0.6f), c.y - u * (0.05f + t * 0.6f))
        drawPath(spike(p, u * 0.06f, p + Offset(-dir * u * 0.14f, u * 0.05f)), fur.bright(0.08f))
    }
    drawPath(Path().apply {
        moveTo(c.x - dir * u * 0.95f, c.y - u * 0.12f)
        quadraticTo(c.x - dir * u * 1.3f, c.y - u * 0.25f, c.x - dir * u * 1.33f, c.y - u * 0.6f)
    }, dark, style = Stroke(u * 0.05f, cap = StrokeCap.Round))
    drawPath(Path().apply {
        moveTo(c.x - dir * u * 1.3f, c.y - u * 0.55f)
        quadraticTo(c.x - dir * u * 1.42f, c.y - u * 0.7f, c.x - dir * u * 1.4f, c.y - u * 0.8f)
    }, belly, style = Stroke(u * 0.1f, cap = StrokeCap.Round))
    // Beine: hinten dunkler, vorne mit Muskel, Pfoten mit Krallen
    for ((i, x) in listOf(-0.6f, -0.35f, 0.4f, 0.65f).withIndex()) {
        val back = i % 2 == 0
        val px = c.x + dir * u * x
        val swing = if (i % 2 == 0) step else -step
        val col = if (back) dark else fur.shade(0.1f)
        val knee = Offset(px + swing * 0.5f, c.y + u * 0.55f)
        val paw = Offset(px + swing, c.y + u * 0.92f)
        drawLine(col, Offset(px, c.y + u * 0.15f), knee, u * 0.2f, StrokeCap.Round)
        drawLine(col, knee, paw, u * 0.15f, StrokeCap.Round)
        if (!back) drawLine(col.bright(0.15f), Offset(px - dir * u * 0.03f, c.y + u * 0.2f), knee + Offset(-dir * u * 0.03f, 0f), u * 0.05f, StrokeCap.Round)
        drawOval(col.shade(0.15f), Offset(paw.x - u * 0.1f + dir * u * 0.04f, paw.y - u * 0.04f), Size(u * 0.22f, u * 0.12f))
        for (k in 0..2) drawLine(Color(0xFF111827), paw + Offset(dir * u * (0.08f + k * 0.03f), u * 0.02f - k * u * 0.02f), paw + Offset(dir * u * (0.14f + k * 0.03f), u * 0.07f - k * u * 0.02f), u * 0.02f, StrokeCap.Round)
    }
    // Körper mit Sattelzeichnung, hellem Bauch und Fellstrichen
    val body = Path().apply {
        moveTo(c.x - dir * u * 0.85f, c.y - u * 0.05f)
        quadraticTo(c.x - dir * u * 0.6f, c.y - u * 0.5f, c.x + dir * u * 0.3f, c.y - u * 0.45f)
        quadraticTo(c.x + dir * u * 0.85f, c.y - u * 0.4f, c.x + dir * u * 0.8f, c.y + u * 0.15f)
        quadraticTo(c.x + dir * u * 0.3f, c.y + u * 0.45f, c.x - dir * u * 0.3f, c.y + u * 0.3f)
        quadraticTo(c.x - dir * u * 0.8f, c.y + u * 0.35f, c.x - dir * u * 0.85f, c.y - u * 0.05f)
        close()
    }
    drawPath(body, fur)
    clipPath(body) {
        drawOval(dark, Offset(c.x - u * 0.9f, c.y - u * 0.65f), Size(u * 1.8f, u * 0.45f))
        drawOval(belly, Offset(c.x - u * 0.5f, c.y + u * 0.12f), Size(u * 1.0f, u * 0.4f))
        for (k in 0..6) {
            val x = c.x + dir * u * (-0.6f + k * 0.2f)
            drawLine(fur.shade(0.2f), Offset(x, c.y - u * 0.15f + (k % 2) * u * 0.08f), Offset(x - dir * u * 0.1f, c.y + (k % 2) * u * 0.08f), u * 0.025f, StrokeCap.Round)
        }
    }
    // Zottel am Rücken und Bauch
    for (k in 0..5) {
        val t = k / 5f
        val top = Offset(c.x + dir * u * (-0.7f + t * 1.1f), c.y - u * (0.4f + sin(t * 3f) * 0.08f))
        drawPath(spike(top, u * 0.06f, top + Offset(-dir * u * 0.1f, -u * 0.08f)), dark)
        val bot = Offset(c.x + dir * u * (-0.4f + t * 0.8f), c.y + u * 0.33f)
        drawPath(spike(bot, u * 0.05f, bot + Offset(-dir * u * 0.06f, u * 0.08f)), belly)
    }
    // Zottelige Halskrause
    for (k in 0..4) {
        val p = Offset(c.x + dir * u * (0.45f + k * 0.07f), c.y - u * (0.35f - k * 0.12f))
        drawPath(spike(p, u * 0.08f, p + Offset(-dir * u * 0.12f, u * 0.14f)), fur.bright(0.1f))
    }
    // Kopf mit Wangenfell, Schnauze, geöffnetem Maul
    val head = Offset(c.x + dir * u * 0.95f, c.y - u * 0.45f)
    for (side in listOf(-1f, 1f)) {
        val ear = head + Offset(side * u * 0.15f - dir * u * 0.1f, -u * 0.3f)
        drawPath(spike(ear + Offset(0f, u * 0.12f), u * 0.1f, ear + Offset(-dir * u * 0.04f, -u * 0.2f)), fur.shade(0.2f))
        drawPath(spike(ear + Offset(0f, u * 0.1f), u * 0.05f, ear + Offset(-dir * u * 0.03f, -u * 0.1f)), Color(0xFFF9A8D4).copy(alpha = 0.6f))
    }
    drawCircle(fur, u * 0.36f, head)
    for (k in 0..2) {
        val cheek = head + Offset(-dir * u * (0.2f + k * 0.06f), u * (0.12f + k * 0.08f))
        drawPath(spike(cheek, u * 0.06f, cheek + Offset(-dir * u * 0.14f, u * 0.06f)), belly)
    }
    // Unterkiefer leicht geöffnet mit Zunge
    drawPath(Path().apply {
        moveTo(head.x + dir * u * 0.12f, head.y + u * 0.16f)
        lineTo(head.x + dir * u * 0.52f, head.y + u * 0.2f)
        lineTo(head.x + dir * u * 0.12f, head.y + u * 0.32f)
        close()
    }, fur.shade(0.1f))
    drawOval(Color(0xFFF472B6), Offset(head.x + dir * u * 0.2f - u * 0.08f, head.y + u * 0.18f), Size(u * 0.2f, u * 0.08f))
    val snout = Path().apply {
        moveTo(head.x + dir * u * 0.1f, head.y - u * 0.15f)
        lineTo(head.x + dir * u * 0.62f, head.y + u * 0.02f)
        lineTo(head.x + dir * u * 0.6f, head.y + u * 0.14f)
        lineTo(head.x + dir * u * 0.1f, head.y + u * 0.18f)
        close()
    }
    drawPath(snout, fur.bright(0.12f))
    drawLine(fur.shade(0.15f), head + Offset(dir * u * 0.15f, -u * 0.1f), head + Offset(dir * u * 0.55f, u * 0.01f), u * 0.025f, StrokeCap.Round)
    drawCircle(Color(0xFF111827), u * 0.07f, head + Offset(dir * u * 0.6f, u * 0.04f))
    drawCircle(Color.White.copy(alpha = 0.6f), u * 0.02f, head + Offset(dir * u * 0.58f, u * 0.02f))
    for (k in 0..2) drawCircle(Color(0xFF374151), u * 0.012f, head + Offset(dir * u * (0.38f + k * 0.05f), u * 0.1f))
    for (k in 0..1) drawPath(spike(head + Offset(dir * u * (0.3f + k * 0.15f), u * 0.15f), u * 0.025f, head + Offset(dir * u * (0.3f + k * 0.15f), u * 0.24f)), Color.White)
    // Gelbes Auge unter grimmiger Braue
    val eye = head + Offset(dir * u * 0.12f, -u * 0.08f)
    drawOval(Color(0xFFFACC15), eye - Offset(u * 0.07f, u * 0.05f), Size(u * 0.14f, u * 0.1f))
    drawCircle(Color(0xFF111827), u * 0.03f, eye + Offset(dir * u * 0.02f, 0f))
    drawCircle(Color.White, u * 0.012f, eye + Offset(dir * u * 0.01f - u * 0.02f, -u * 0.02f))
    drawLine(dark, eye + Offset(-dir * u * 0.09f, -u * 0.1f), eye + Offset(dir * u * 0.1f, -u * 0.04f), u * 0.04f, StrokeCap.Round)
}

// --- Steingolem ---

private fun DrawScope.drawGolem(b: ArtBody) {
    val u = b.u
    val c = b.c
    val dir = b.dir
    val stone = Color(0xFF8A817C)
    val rune = Color(0xFF38BDF8)
    val moss = Color(0xFF65A30D)
    val p = b.pulse(3f)

    // Kieselsteine am Boden
    for (k in -2..2) drawOval(stone.shade(0.3f), Offset(c.x + k * u * 0.35f - u * 0.06f, c.y + u * 0.95f), Size(u * 0.12f, u * 0.06f))
    // Beine aus gestapelten Blöcken
    for (side in listOf(-1f, 1f)) {
        val x = c.x + side * u * 0.45f
        drawPath(rock(Offset(x, c.y + u * 0.5f), u * 0.24f, u * 0.2f, 3 + side.toInt()), stone.shade(0.22f))
        drawPath(rock(Offset(x, c.y + u * 0.82f), u * 0.27f, u * 0.17f, 5 + side.toInt()), stone.shade(0.3f))
    }
    // Lange Arme aus Felssegmenten mit Runenkanälen, Fäuste mit Fingern
    for (side in listOf(-1f, 1f)) {
        val shoulder = Offset(c.x + side * u * 0.85f, c.y - u * 0.55f)
        val elbow = Offset(c.x + side * u * 1.08f, c.y + u * 0.05f)
        val fist = Offset(c.x + side * u * 1.15f, c.y + u * 0.72f)
        drawPath(rock(shoulder + (elbow - shoulder) * 0.5f, u * 0.25f, u * 0.32f, 7 + side.toInt()), stone.shade(0.12f))
        drawPath(rock(elbow + (fist - elbow) * 0.45f, u * 0.22f, u * 0.3f, 9 + side.toInt()), stone.shade(0.18f))
        drawLine(rune.copy(alpha = 0.5f + 0.5f * p), shoulder + Offset(0f, u * 0.2f), elbow + Offset(0f, u * 0.3f), u * 0.045f, StrokeCap.Round)
        drawRoundRect(stone.shade(0.05f), fist - Offset(u * 0.3f, u * 0.22f), Size(u * 0.6f, u * 0.44f), CornerRadius(u * 0.14f))
        for (k in 0..3) drawRoundRect(stone.shade(0.15f), Offset(fist.x - u * 0.27f + k * u * 0.14f, fist.y + u * 0.08f), Size(u * 0.12f, u * 0.14f), CornerRadius(u * 0.04f))
        drawCircle(stone.bright(0.15f), u * 0.04f, fist + Offset(-u * 0.12f, -u * 0.1f))
    }
    // Gebeugter Rumpf aus Steinblöcken mit Fugen
    val torso = Path().apply {
        moveTo(c.x - u * 0.95f, c.y - u * 0.55f)
        quadraticTo(c.x, c.y - u * 1.15f, c.x + u * 0.95f, c.y - u * 0.55f)
        lineTo(c.x + u * 0.7f, c.y + u * 0.5f)
        lineTo(c.x - u * 0.7f, c.y + u * 0.5f)
        close()
    }
    drawPath(torso, stone)
    clipPath(torso) {
        // Blockfugen
        val seams = listOf(
            Offset(-0.95f, -0.2f) to Offset(0.95f, -0.25f),
            Offset(-0.9f, 0.15f) to Offset(0.9f, 0.12f),
            Offset(-0.3f, -0.9f) to Offset(-0.35f, -0.2f),
            Offset(0.4f, -0.85f) to Offset(0.35f, -0.25f),
            Offset(0.05f, -0.22f) to Offset(0f, 0.13f),
            Offset(-0.45f, 0.13f) to Offset(-0.4f, 0.5f),
            Offset(0.35f, 0.12f) to Offset(0.3f, 0.5f),
        )
        for ((a, e) in seams) drawLine(stone.shade(0.35f), c + Offset(a.x * u, a.y * u), c + Offset(e.x * u, e.y * u), u * 0.035f)
        drawRect(stone.bright(0.08f), Offset(c.x - u * 0.28f, c.y - u * 0.18f), Size(u * 0.3f, u * 0.28f))
        drawOval(moss, Offset(c.x - u * 0.9f, c.y - u * 1.2f), Size(u * 1.8f, u * 0.55f))
        for (k in -3..3) drawPath(spike(Offset(c.x + k * u * 0.22f, c.y - u * 0.72f + abs(k) * u * 0.06f), u * 0.05f, Offset(c.x + k * u * 0.22f, c.y - u * 0.6f + abs(k) * u * 0.06f)), moss.shade(0.15f))
    }
    // Kleiner Pilz und Kristall im Moos
    val mush = Offset(c.x - dir * u * 0.55f, c.y - u * 0.8f)
    drawLine(Color(0xFFF5F5F4), mush, mush + Offset(0f, u * 0.1f), u * 0.04f)
    drawArc(Color(0xFFDC2626), 180f, 180f, true, mush - Offset(u * 0.08f, u * 0.06f), Size(u * 0.16f, u * 0.12f))
    drawCircle(Color.White, u * 0.012f, mush + Offset(-u * 0.02f, -u * 0.03f))
    val shard = Offset(c.x + dir * u * 0.65f, c.y - u * 0.7f)
    drawPath(spike(shard, u * 0.06f, shard + Offset(dir * u * 0.08f, -u * 0.25f)), rune.copy(alpha = 0.85f))
    // Riss mit Glimmen
    drawLine(stone.shade(0.45f), Offset(c.x - u * 0.55f, c.y - u * 0.35f), Offset(c.x - u * 0.2f, c.y - u * 0.05f), u * 0.04f)
    drawLine(stone.shade(0.45f), Offset(c.x + u * 0.1f, c.y + u * 0.2f), Offset(c.x + u * 0.25f, c.y + u * 0.42f), u * 0.04f)
    // Leuchtende Rune auf der Brust
    val r = Offset(c.x + dir * u * 0.15f, c.y - u * 0.25f)
    drawCircle(rune.copy(alpha = 0.3f * p), u * 0.28f, r)
    drawPath(Path().apply {
        moveTo(r.x - u * 0.12f, r.y + u * 0.15f)
        lineTo(r.x, r.y - u * 0.15f)
        lineTo(r.x + u * 0.12f, r.y + u * 0.15f)
        moveTo(r.x - u * 0.07f, r.y + u * 0.03f)
        lineTo(r.x + u * 0.07f, r.y + u * 0.03f)
    }, rune, style = Stroke(u * 0.06f, cap = StrokeCap.Round))
    // Kleiner Kopf mit Stirnwulst und Kieferplatte
    val h = Offset(c.x + dir * u * 0.1f, c.y - u * 1.0f)
    drawPath(rock(h, u * 0.38f, u * 0.3f, 11), stone.bright(0.1f))
    drawRoundRect(stone.shade(0.1f), Offset(h.x - u * 0.25f, h.y + u * 0.12f), Size(u * 0.5f, u * 0.16f), CornerRadius(u * 0.05f))
    drawLine(stone.shade(0.3f), h + Offset(-u * 0.34f, -u * 0.08f), h + Offset(u * 0.34f, -u * 0.08f), u * 0.1f, StrokeCap.Round)
    drawLine(stone.shade(0.4f), h + Offset(u * 0.12f, -u * 0.25f), h + Offset(u * 0.2f, -u * 0.12f), u * 0.025f)
    for (side in listOf(-1f, 1f)) {
        val e = h + Offset(side * u * 0.15f + dir * u * 0.04f, u * 0.03f)
        drawCircle(rune.copy(alpha = 0.4f * p), u * 0.12f, e)
        drawCircle(Color(0xFFE0F2FE), u * 0.06f, e)
    }
}

// --- Riesenspinne ---

private fun DrawScope.drawSpider(b: ArtBody) {
    val u = b.u
    val c = b.c
    val dir = b.dir
    val time = b.time
    val body = Color(0xFF292524)
    val hair = Color(0xFF57534E)
    val band = Color(0xFF7F1D1D)

    // Seidenfaden nach oben
    drawLine(Color.White.copy(alpha = 0.35f), c + Offset(-dir * u * 0.15f, -u * 0.1f), c + Offset(-dir * u * 0.15f, -u * 1.6f), u * 0.015f)
    // Acht gegliederte, behaarte Beine mit roten Gelenkringen
    for (side in listOf(-1f, 1f)) for (i in 0..3) {
        val wiggle = sin(time * 8f + i + side) * u * 0.06f
        val hip = c + Offset(side * u * 0.22f, -u * 0.05f + i * u * 0.06f)
        val knee = Offset(c.x + side * u * (0.75f + i * 0.12f), c.y - u * (0.55f - i * 0.28f) + wiggle)
        val foot = knee + Offset(side * u * (0.3f + i * 0.05f), u * (0.75f - i * 0.1f))
        drawLine(body.bright(0.1f), hip, knee, u * 0.11f, StrokeCap.Round)
        drawLine(body.bright(0.1f), knee, foot, u * 0.09f, StrokeCap.Round)
        drawLine(Color.White.copy(alpha = 0.15f), hip + Offset(0f, -u * 0.03f), knee + Offset(0f, -u * 0.03f), u * 0.025f, StrokeCap.Round)
        drawCircle(band, u * 0.07f, knee)
        drawCircle(band, u * 0.05f, knee + (foot - knee) * 0.5f)
        for (k in 1..4) {
            val q = hip + (knee - hip) * (k / 5f)
            drawLine(hair, q, q + Offset(0f, -u * 0.07f), u * 0.02f)
            val r = knee + (foot - knee) * (k / 5f)
            drawLine(hair, r, r + Offset(side * u * 0.06f, -u * 0.02f), u * 0.02f)
        }
        drawLine(Color(0xFF111111), foot, foot + Offset(side * u * 0.05f, u * 0.06f), u * 0.035f, StrokeCap.Round)
    }
    // Hinterleib mit Streifen, Sanduhr, Haarsaum und Spinnwarzen
    val abdomen = c + Offset(-dir * u * 0.15f, u * 0.25f)
    for (k in 0 until 14) {
        val a = k * 0.45f
        drawLine(hair, abdomen + Offset(cos(a) * u * 0.56f, sin(a) * u * 0.46f), abdomen + Offset(cos(a) * u * 0.66f, sin(a) * u * 0.55f), u * 0.025f)
    }
    drawOval(body, abdomen - Offset(u * 0.6f, u * 0.5f), Size(u * 1.2f, u * 1.0f))
    for (k in 0..2) drawArc(Color(0xFF44403C), 200f, 140f, false, abdomen - Offset(u * (0.45f - k * 0.05f), u * (0.4f - k * 0.18f)), Size(u * (0.9f - k * 0.1f), u * 0.3f), style = Stroke(u * 0.035f))
    drawOval(Color.White.copy(alpha = 0.12f), abdomen - Offset(u * 0.4f, u * 0.42f), Size(u * 0.45f, u * 0.22f))
    drawPath(Path().apply {
        moveTo(abdomen.x - u * 0.15f, abdomen.y - u * 0.18f)
        lineTo(abdomen.x + u * 0.15f, abdomen.y - u * 0.18f)
        lineTo(abdomen.x, abdomen.y)
        lineTo(abdomen.x + u * 0.15f, abdomen.y + u * 0.18f)
        lineTo(abdomen.x - u * 0.15f, abdomen.y + u * 0.18f)
        lineTo(abdomen.x, abdomen.y)
        close()
    }, Color(0xFFDC2626))
    for (side in listOf(-1f, 1f)) drawCircle(Color(0xFFDC2626).copy(alpha = 0.6f), u * 0.04f, abdomen + Offset(side * u * 0.3f, u * 0.05f))
    for (k in -1..1) drawOval(Color(0xFF44403C), Offset(abdomen.x - dir * u * 0.55f + k * u * 0.05f, abdomen.y + u * 0.25f), Size(u * 0.06f, u * 0.1f))
    // Kopfbruststück mit Haarsaum, Augengruppe, Tastern und Giftklauen
    val head = c + Offset(dir * u * 0.15f, -u * 0.35f)
    for (k in 0 until 10) {
        val a = 3.4f + k * 0.29f
        drawLine(hair, head + Offset(cos(a) * u * 0.34f, sin(a) * u * 0.34f), head + Offset(cos(a) * u * 0.43f, sin(a) * u * 0.43f), u * 0.022f)
    }
    drawCircle(body.bright(0.08f), u * 0.36f, head)
    drawCircle(Color.White.copy(alpha = 0.1f), u * 0.12f, head + Offset(-u * 0.12f, -u * 0.15f))
    val eyes = listOf(Offset(-0.12f, -0.1f), Offset(0.12f, -0.1f), Offset(-0.05f, -0.02f), Offset(0.05f, -0.02f), Offset(-0.2f, 0.0f), Offset(0.2f, 0.0f), Offset(-0.08f, -0.18f), Offset(0.08f, -0.18f))
    for ((i, e) in eyes.withIndex()) {
        val pos = head + Offset(e.x * u + dir * u * 0.05f, e.y * u)
        drawCircle(Color(0xFF450A0A), u * (if (i < 4) 0.075f else 0.055f), pos)
        drawCircle(Color(0xFFEF4444), u * (if (i < 4) 0.06f else 0.04f), pos)
        drawCircle(Color.White.copy(alpha = 0.8f), u * 0.015f, pos + Offset(-u * 0.015f, -u * 0.015f))
    }
    for (side in listOf(-1f, 1f)) {
        // Taster
        val palp = head + Offset(side * u * 0.22f, u * 0.2f)
        drawLine(body.bright(0.15f), head + Offset(side * u * 0.18f, u * 0.12f), palp, u * 0.05f, StrokeCap.Round)
        drawCircle(hair, u * 0.035f, palp)
        // Giftklauen mit Tropfen
        val base = head + Offset(side * u * 0.1f + dir * u * 0.05f, u * 0.22f)
        drawPath(Path().apply {
            moveTo(base.x - u * 0.06f, base.y)
            quadraticTo(base.x + side * u * 0.08f, base.y + u * 0.15f, base.x - side * u * 0.02f, base.y + u * 0.25f)
            lineTo(base.x + u * 0.06f, base.y)
            close()
        }, band)
    }
    val drop = head + Offset(dir * u * 0.05f, u * 0.5f + ((time * 0.7f) % 1f) * u * 0.1f)
    drawCircle(Color(0xFF84CC16), u * 0.03f, drop)
}

// --- Harpyie ---

/** Einzelne Schwungfeder von [base] nach [end]: Fahne mit Kiel, Querbändern und dunkler Spitze. */
private fun DrawScope.harpyFeather(base: Offset, end: Offset, halfWidth: Float, vane: Color, band: Color, tipColor: Color) {
    val d = end - base
    val len = d.getDistance().coerceAtLeast(0.001f)
    val n = Offset(-d.y / len, d.x / len) * halfWidth
    val shape = Path().apply {
        moveTo(base.x, base.y)
        quadraticTo(base.x + d.x * 0.5f + n.x * 1.3f, base.y + d.y * 0.5f + n.y * 1.3f, end.x, end.y)
        quadraticTo(base.x + d.x * 0.5f - n.x * 1.1f, base.y + d.y * 0.5f - n.y * 1.1f, base.x, base.y)
        close()
    }
    drawPath(shape, vane)
    clipPath(shape) {
        for (k in 1..3) {
            val q = base + d * (k * 0.22f)
            drawLine(band.copy(alpha = 0.3f), q - n * 1.5f, q + n * 1.5f + d * 0.04f, halfWidth * 0.3f)
        }
        drawCircle(tipColor, halfWidth * 1.6f, end - d * 0.08f)
    }
    drawLine(band.bright(0.2f), base, base + d * 0.92f, halfWidth * 0.18f, StrokeCap.Round)
    drawPath(shape, vane.shade(0.3f), style = Stroke(halfWidth * 0.15f))
}

private fun DrawScope.drawHarpy(b: ArtBody) {
    val u = b.u
    val c = b.c
    val dir = b.dir
    val time = b.time
    val feather = Color(0xFF7C5E3C)
    val light = Color(0xFFD6B48A)
    val skin = Color(0xFFE8C4A0)
    val flap = sin(time * 7f) * u * 0.25f
    // Weibliches Gerüst innerhalb des Kreaturenrahmens
    val f = ArtBody(c + Offset(0f, -u * 0.05f), u * 0.95f, dir, time)
    val fu = f.u

    // Schwingen aus einzelnen Federn: Hand- und Armschwingen, Deckfedern, Flügelbug mit Kralle
    for (side in listOf(-1f, 1f)) {
        val root = Offset(c.x + side * fu * 0.2f, f.shoulderY + fu * 0.05f)
        val bend = Offset(c.x + side * u * 0.85f, c.y - u * 0.85f - flap * 0.7f)
        val tip = Offset(c.x + side * u * 1.5f, c.y - u * 1.0f - flap)
        // Geschlossene Flügelfläche hinter den Federn
        drawPath(Path().apply {
            moveTo(root.x, root.y)
            lineTo(bend.x, bend.y)
            lineTo(tip.x, tip.y)
            lineTo(tip.x + side * u * 0.2f, tip.y + u * 0.5f)
            lineTo(bend.x + side * u * 0.1f, bend.y + u * 0.75f)
            lineTo(root.x + side * u * 0.1f, root.y + u * 0.6f)
            close()
        }, feather.shade(0.25f))
        // Handschwingen: lang, vom Bug bis zur Spitze, dicht überlappend nach außen-unten gefächert
        for (k in 7 downTo 0) {
            val t = k / 7f
            val base = bend + (tip - bend) * t
            val end = base + Offset(side * u * (0.05f + t * 0.3f), u * (0.8f - t * 0.3f))
            harpyFeather(base, end, u * 0.13f, if (k % 2 == 0) feather else feather.shade(0.08f), light, Color(0xFF3F2E1E))
        }
        // Armschwingen: kürzer, zwischen Schulter und Bug
        for (k in 5 downTo 0) {
            val t = k / 5f
            val base = root + (bend - root) * (0.15f + t * 0.85f)
            val end = base + Offset(side * u * 0.08f, u * (0.6f + t * 0.12f))
            harpyFeather(base, end, u * 0.12f, if (k % 2 == 0) feather.bright(0.06f) else feather, light, Color(0xFF3F2E1E))
        }
        // Zwei Reihen Deckfedern entlang der Vorderkante
        for (row in 0..1) for (k in 0..6) {
            val t = k / 6f
            val along = root + (tip - root) * (0.15f + t * 0.75f)
            val edge = if (t < 0.55f) root + (bend - root) * ((0.15f + t * 0.75f) / 0.55f).coerceAtMost(1f) else along
            val pos = Offset(edge.x, edge.y + u * (0.1f + row * 0.13f))
            drawArc(if ((k + row) % 2 == 0) light else feather.bright(0.12f), 0f, 180f, true, pos - Offset(u * 0.08f, u * 0.07f), Size(u * 0.16f, u * 0.15f))
            drawArc(feather.shade(0.25f), 0f, 180f, false, pos - Offset(u * 0.08f, u * 0.07f), Size(u * 0.16f, u * 0.15f), style = Stroke(u * 0.015f))
        }
        // Flügelknochen mit Bug und Kralle
        drawLine(feather.shade(0.3f), root, bend, u * 0.08f, StrokeCap.Round)
        drawLine(feather.shade(0.3f), bend, tip, u * 0.06f, StrokeCap.Round)
        drawLine(light.copy(alpha = 0.5f), root + Offset(0f, -u * 0.02f), bend + Offset(0f, -u * 0.02f), u * 0.02f, StrokeCap.Round)
        drawPath(spike(bend, u * 0.035f, bend + Offset(side * u * 0.05f, -u * 0.15f)), Color(0xFF292524))
    }
    // Lange Federmähne hinter Kopf und Rücken
    run {
        val head = f.head
        val hr = f.headR
        drawPath(Path().apply {
            moveTo(head.x - hr * 1.05f, head.y - hr * 0.1f)
            cubicTo(head.x - hr * 1.15f, head.y - hr * 1.5f, head.x + hr * 1.15f, head.y - hr * 1.5f, head.x + hr * 1.05f, head.y - hr * 0.1f)
            quadraticTo(head.x + hr * 1.25f, f.shoulderY + fu * 0.15f, head.x + hr * 0.7f, f.shoulderY + fu * 0.35f)
            lineTo(head.x - hr * 0.7f, f.shoulderY + fu * 0.35f)
            quadraticTo(head.x - hr * 1.25f, f.shoulderY + fu * 0.15f, head.x - hr * 1.05f, head.y - hr * 0.1f)
            close()
        }, feather.shade(0.1f))
    }
    // Lange Schwanzfedern
    for (k in -1..1) drawPath(spike(Offset(c.x + k * u * 0.08f, f.hipY + fu * 0.1f), u * 0.06f, Offset(c.x - dir * u * 0.15f + k * u * 0.15f, f.hipY + fu * 0.6f)), feather.shade(0.15f))
    // Schlanke Beine: Oberschenkel befiedert, ab dem Knie Vogelläufe mit Krallen
    for (side in listOf(-1f, 1f)) {
        val hip = Offset(c.x + side * fu * 0.13f, f.hipY + fu * 0.05f)
        val knee = Offset(c.x + side * fu * 0.15f, (f.hipY + f.footY) / 2f + fu * 0.05f)
        val ankle = Offset(c.x + side * fu * 0.13f, f.footY - fu * 0.08f)
        drawLine(skin.shade(0.05f), hip, knee, fu * 0.17f, StrokeCap.Round)
        drawLine(Color(0xFFCA8A04), knee, ankle, fu * 0.07f, StrokeCap.Round)
        for (k in 1..3) {
            val q = knee + (ankle - knee) * (k / 4f)
            drawLine(Color(0xFFA16207), q - Offset(fu * 0.04f, 0f), q + Offset(fu * 0.04f, 0f), fu * 0.02f)
        }
        for (k in -1..1) drawLine(Color(0xFF292524), ankle, ankle + Offset(k * fu * 0.1f + dir * fu * 0.06f, fu * 0.12f), fu * 0.035f, StrokeCap.Round)
        // Federmanschette am Knie
        for (k in -1..1) drawPath(spike(knee + Offset(k * fu * 0.06f, -fu * 0.04f), fu * 0.05f, knee + Offset(k * fu * 0.08f, fu * 0.1f)), light)
    }
    // Weiblicher Körper: Sanduhr, Federn um Brust und Hüfte
    val waistY = f.hipY - fu * 0.1f
    val torso = femaleTorso(f, waistY, f.hipY + fu * 0.06f, hip = 0.28f, waist = 0.15f, shoulder = 0.2f, bust = 0.27f)
    drawPath(torso, skin)
    // Runde Federkörbchen mit Schuppenmuster
    for (side in listOf(-1f, 1f)) {
        val cup = Offset(c.x + side * fu * 0.13f, f.shoulderY + fu * 0.23f)
        drawArc(skin.shade(0.15f), 20f, 140f, false, Offset(cup.x - fu * 0.12f, cup.y - fu * 0.06f), Size(fu * 0.24f, fu * 0.2f), style = Stroke(fu * 0.02f, cap = StrokeCap.Round))
        val cupPath = Path().apply {
            moveTo(c.x + side * fu * 0.02f, cup.y + fu * 0.06f)
            quadraticTo(c.x + side * fu * 0.04f, cup.y - fu * 0.12f, c.x + side * fu * 0.15f, cup.y - fu * 0.12f)
            quadraticTo(c.x + side * fu * 0.27f, cup.y - fu * 0.08f, c.x + side * fu * 0.25f, cup.y + fu * 0.07f)
            quadraticTo(c.x + side * fu * 0.14f, cup.y + fu * 0.14f, c.x + side * fu * 0.02f, cup.y + fu * 0.06f)
            close()
        }
        drawPath(cupPath, feather.bright(0.05f))
        clipPath(cupPath) {
            for (row in 0..2) for (k in -2..2) {
                val pos = cup + Offset(k * fu * 0.055f + (row % 2) * fu * 0.027f, row * fu * 0.055f - fu * 0.09f)
                drawArc(if ((k + row) % 2 == 0) light else feather.bright(0.15f), 0f, 180f, true, pos - Offset(fu * 0.035f, fu * 0.03f), Size(fu * 0.07f, fu * 0.07f))
            }
        }
        drawPath(cupPath, feather.shade(0.2f), style = Stroke(fu * 0.018f))
    }
    // Kurzer Federschurz, der Hüftrundung folgend
    for (k in -4..4) {
        val x = c.x + k * fu * 0.065f
        val top = Offset(x, f.hipY - fu * 0.02f + abs(k) * fu * 0.008f)
        drawPath(spike(top, fu * 0.045f, top + Offset(k * fu * 0.025f, fu * (0.2f - abs(k) * 0.02f))), if (k % 2 == 0) feather else light)
    }
    drawPath(Path().apply {
        moveTo(c.x - fu * 0.28f, f.hipY + fu * 0.01f)
        quadraticTo(c.x, f.hipY - fu * 0.05f, c.x + fu * 0.28f, f.hipY + fu * 0.01f)
    }, feather.shade(0.2f), style = Stroke(fu * 0.035f))
    drawCircle(skin.shade(0.25f), fu * 0.016f, Offset(c.x, f.hipY - fu * 0.14f))
    // Kopf: Federkranz, weibliches Raubvogelgesicht
    val head = f.head
    val hr = f.headR
    for (k in -3..3) {
        val a = -1.57f + k * 0.38f
        val tip = head + Offset(cos(a) * hr * 1.6f, sin(a) * hr * 1.4f + hr * 0.15f)
        drawPath(spike(head + Offset(k * hr * 0.2f, -hr * 0.3f), hr * 0.2f, tip), if (k % 2 == 0) feather.shade(0.2f) else Color(0xFFF5F5F4))
    }
    artHead(f, skin)
    for (side in listOf(-1f, 1f)) {
        val e = head + Offset(side * hr * 0.36f + dir * hr * 0.06f, hr * 0.06f)
        drawOval(Color(0xFFFACC15), e - Offset(hr * 0.15f, hr * 0.11f), Size(hr * 0.3f, hr * 0.22f))
        drawOval(Color(0xFF1C1917), e - Offset(hr * 0.035f - dir * hr * 0.03f, hr * 0.09f), Size(hr * 0.07f, hr * 0.18f))
        drawArc(Color(0xFF1C1917), if (side > 0) 200f else 230f, 110f, false, e - Offset(hr * 0.17f, hr * 0.15f), Size(hr * 0.34f, hr * 0.3f), style = Stroke(hr * 0.06f, cap = StrokeCap.Round))
        drawLine(Color(0xFF1C1917), e + Offset(side * hr * 0.14f, -hr * 0.08f), e + Offset(side * hr * 0.27f, -hr * 0.17f), hr * 0.05f, StrokeCap.Round)
        drawArc(feather.shade(0.3f), 200f, 140f, false, e - Offset(hr * 0.15f, hr * 0.33f), Size(hr * 0.3f, hr * 0.16f), style = Stroke(hr * 0.045f, cap = StrokeCap.Round))
    }
    drawCircle(skin.shade(0.12f), hr * 0.06f, head + Offset(dir * hr * 0.1f, hr * 0.3f))
    val mouth = head + Offset(dir * hr * 0.06f, hr * 0.55f)
    drawArc(Color(0xFFBE123C), 0f, 180f, true, mouth - Offset(hr * 0.14f, hr * 0.08f), Size(hr * 0.28f, hr * 0.16f))
    drawLine(Color(0xFF881337), mouth - Offset(hr * 0.14f, 0f), mouth + Offset(hr * 0.14f, 0f), hr * 0.035f, StrokeCap.Round)
    // Federpony
    clipRect(top = head.y - hr * 1.2f) {
        drawPath(Path().apply {
            moveTo(head.x - hr * 1.0f, head.y - hr * 0.1f)
            cubicTo(head.x - hr * 1.1f, head.y - hr * 1.35f, head.x + hr * 1.1f, head.y - hr * 1.35f, head.x + hr * 1.0f, head.y - hr * 0.1f)
            lineTo(head.x + hr * 0.6f, head.y - hr * 0.5f)
            lineTo(head.x + hr * 0.3f, head.y - hr * 0.35f)
            lineTo(head.x, head.y - hr * 0.55f)
            lineTo(head.x - hr * 0.3f, head.y - hr * 0.35f)
            lineTo(head.x - hr * 0.6f, head.y - hr * 0.5f)
            close()
        }, feather)
    }
}

// --- Knochendrache ---

private fun DrawScope.drawBoneDragon(b: ArtBody) {
    val u = b.u
    val c = b.c
    val dir = b.dir
    val time = b.time
    val bone = Color(0xFFE7E5E4)
    val shade = Color(0xFFA8A29E)
    val soul = Color(0xFFA855F7)
    val hole = Color(0xFF1E1035)
    val p = b.pulse(4f)
    val flap = sin(time * 2.5f) * u * 0.15f

    // Aufsteigende Seelenfetzen
    for (k in 0 until 4) {
        val t = (time * 0.4f + k * 0.25f) % 1f
        drawCircle(soul.copy(alpha = 0.4f * (1f - t)), u * (0.05f + t * 0.05f), c + Offset(sin(k * 2.1f + time) * u * 0.6f, u * 0.3f - t * u * 1.4f))
    }
    // Zerfetzte Hautflügel mit Knochenfingern und Rissen
    for (side in listOf(-1f, 1f)) {
        val root = c + Offset(side * u * 0.25f, -u * 0.35f)
        val wrist = c + Offset(side * u * 0.95f, -u * 1.05f - flap)
        val tips = listOf(Offset(1.55f, -0.7f), Offset(1.5f, -0.15f), Offset(1.2f, 0.15f), Offset(0.8f, 0.1f)).map { c + Offset(side * u * it.x, u * it.y - flap * (1f + it.y)) }
        val membrane = Path().apply {
            moveTo(root.x, root.y)
            lineTo(wrist.x, wrist.y)
            var prev = wrist
            for ((k, t) in tips.withIndex()) {
                val mid = (prev + t) * 0.5f + Offset(-side * u * 0.1f, u * 0.08f)
                quadraticTo(mid.x, mid.y, t.x, t.y)
                if (k == 1) lineTo(t.x - side * u * 0.12f, t.y - u * 0.05f)
                prev = t
            }
            close()
        }
        drawPath(membrane, Color(0xFF4C1D95).copy(alpha = 0.85f))
        clipPath(membrane) {
            drawCircle(soul.copy(alpha = 0.25f * p), u * 0.5f, wrist + Offset(side * u * 0.2f, u * 0.5f))
            // Risse und Löcher in der Haut
            drawOval(hole, Offset(c.x + side * u * 1.15f - u * 0.08f, c.y - u * 0.45f - flap), Size(u * 0.16f, u * 0.1f))
            drawOval(hole, Offset(c.x + side * u * 0.75f - u * 0.05f, c.y - u * 0.25f - flap * 0.6f), Size(u * 0.1f, u * 0.07f))
            drawPath(spike(tips[2] + Offset(-side * u * 0.05f, u * 0.05f), u * 0.06f, tips[2] + Offset(-side * u * 0.12f, -u * 0.2f)), hole)
            for (t in tips) drawLine(Color(0xFF6D28D9), wrist + (t - wrist) * 0.3f, t, u * 0.08f)
        }
        drawLine(bone, root, wrist, u * 0.09f, StrokeCap.Round)
        drawCircle(shade, u * 0.06f, root + (wrist - root) * 0.5f)
        for (t in tips) {
            drawLine(bone, wrist, t, u * 0.045f, StrokeCap.Round)
            drawCircle(shade, u * 0.03f, wrist + (t - wrist) * 0.5f)
        }
        drawCircle(bone, u * 0.07f, wrist)
        drawPath(spike(wrist, u * 0.04f, wrist + Offset(side * u * 0.05f, -u * 0.18f)), bone)
    }
    // Knöcherner Schwanz mit Wirbeldornen und Stachelende
    var tp = c + Offset(0f, u * 0.35f)
    for (k in 0..7) {
        val next = c + Offset(-dir * u * (0.15f + k * 0.12f), u * (0.45f + sin(k * 0.6f + time * 2f) * 0.08f + k * 0.02f))
        drawLine(bone, tp, next, u * (0.08f - k * 0.006f), StrokeCap.Round)
        drawCircle(shade, u * (0.05f - k * 0.004f), next)
        drawPath(spike(next, u * 0.02f, next + Offset(0f, -u * (0.08f - k * 0.006f))), shade)
        tp = next
    }
    drawPath(Path().apply {
        moveTo(tp.x, tp.y - u * 0.06f)
        lineTo(tp.x - dir * u * 0.22f, tp.y - u * 0.02f)
        lineTo(tp.x, tp.y + u * 0.06f)
        close()
    }, bone)
    // Becken und Klauenbeine
    drawOval(bone, Offset(c.x - u * 0.28f, c.y + u * 0.18f), Size(u * 0.56f, u * 0.16f))
    for (side in listOf(-1f, 1f)) drawCircle(hole, u * 0.035f, c + Offset(side * u * 0.12f, u * 0.26f))
    for (side in listOf(-1f, 1f)) {
        val hip = c + Offset(side * u * 0.25f, u * 0.24f)
        val knee = hip + Offset(side * u * 0.12f, u * 0.28f)
        val foot = c + Offset(side * u * 0.35f, u * 0.82f)
        drawLine(bone, hip, knee, u * 0.08f, StrokeCap.Round)
        drawCircle(shade, u * 0.06f, knee)
        drawLine(bone, knee, foot, u * 0.07f, StrokeCap.Round)
        for (k in -1..1) drawLine(shade, foot, foot + Offset(k * u * 0.09f + dir * u * 0.05f, u * 0.12f), u * 0.035f, StrokeCap.Round)
    }
    // Brustkorb mit Seelenfeuer, Schulterblätter, kleine Vorderklauen
    val chest = c + Offset(0f, -u * 0.05f)
    for (side in listOf(-1f, 1f)) {
        drawPath(spike(chest + Offset(side * u * 0.32f, -u * 0.25f), u * 0.12f, chest + Offset(side * u * 0.45f, -u * 0.5f)), shade)
        val elbow = chest + Offset(side * u * 0.5f, u * 0.05f)
        val claw = chest + Offset(side * u * 0.42f, u * 0.32f)
        drawLine(bone, chest + Offset(side * u * 0.3f, -u * 0.25f), elbow, u * 0.06f, StrokeCap.Round)
        drawLine(bone, elbow, claw, u * 0.05f, StrokeCap.Round)
        for (k in -1..1) drawLine(shade, claw, claw + Offset(k * u * 0.06f, u * 0.09f), u * 0.025f, StrokeCap.Round)
    }
    drawOval(Color(0xFF1C1917), chest - Offset(u * 0.38f, u * 0.38f), Size(u * 0.76f, u * 0.72f))
    drawCircle(soul.copy(alpha = 0.45f * p), u * 0.25f, chest)
    drawCircle(Color(0xFFE9D5FF), u * 0.08f, chest)
    drawLine(bone, chest + Offset(0f, -u * 0.38f), chest + Offset(0f, u * 0.35f), u * 0.06f)
    for (i in 0..4) drawCircle(shade, u * 0.025f, chest + Offset(0f, -u * 0.3f + i * u * 0.15f))
    for (i in 0..3) {
        val y = chest.y - u * 0.28f + i * u * 0.16f
        val w = u * (0.4f - abs(i - 1.5f) * 0.05f)
        drawArc(bone, 200f, 140f, false, Offset(c.x - w, y - u * 0.05f), Size(w * 2f, u * 0.3f), style = Stroke(u * 0.045f, cap = StrokeCap.Round))
    }
    // Hals aus Wirbeln mit Dornen
    val np = chest + Offset(0f, -u * 0.35f)
    val head = c + Offset(dir * u * 0.1f, -u * 0.85f)
    for (k in 1..3) {
        val q = np + (head - np) * (k / 4f)
        drawCircle(bone, u * 0.075f, q)
        drawCircle(shade, u * 0.03f, q)
        drawPath(spike(q, u * 0.03f, q + Offset(-dir * u * 0.1f, -u * 0.1f)), shade)
    }
    // Drachenschädel: Hörner mit Rillen, Stirnwülste, Nasenlöcher, Zähne in beiden Kiefern
    for (side in listOf(-1f, 1f)) {
        val horn = Path().apply {
            moveTo(head.x + side * u * 0.12f, head.y - u * 0.15f)
            quadraticTo(head.x + side * u * 0.4f, head.y - u * 0.25f, head.x + side * u * 0.38f, head.y - u * 0.55f)
            lineTo(head.x + side * u * 0.22f, head.y - u * 0.2f)
            close()
        }
        drawPath(horn, shade)
        clipPath(horn) { for (k in 1..3) drawLine(Color(0xFF78716C), head + Offset(side * u * (0.15f + k * 0.06f), -u * (0.12f + k * 0.1f)), head + Offset(side * u * (0.28f + k * 0.04f), -u * (0.2f + k * 0.1f)), u * 0.015f) }
        drawPath(spike(head + Offset(side * u * 0.28f, -u * 0.02f), u * 0.04f, head + Offset(side * u * 0.42f, -u * 0.1f)), shade)
    }
    drawOval(bone, Offset(head.x - u * 0.3f, head.y - u * 0.22f), Size(u * 0.6f, u * 0.42f))
    drawArc(shade, 200f, 140f, false, Offset(head.x - u * 0.27f, head.y - u * 0.2f), Size(u * 0.54f, u * 0.25f), style = Stroke(u * 0.03f))
    // Unterkiefer leicht geöffnet mit Zahnreihe
    drawRoundRect(shade, Offset(head.x - u * 0.2f + dir * u * 0.05f, head.y + u * 0.14f), Size(u * 0.4f, u * 0.14f), CornerRadius(u * 0.05f))
    drawRect(hole, Offset(head.x - u * 0.16f + dir * u * 0.07f, head.y + u * 0.12f), Size(u * 0.32f, u * 0.05f))
    for (i in -2..2) {
        drawPath(spike(Offset(head.x + i * u * 0.07f + dir * u * 0.07f, head.y + u * 0.12f), u * 0.02f, Offset(head.x + i * u * 0.07f + dir * u * 0.07f, head.y + u * 0.18f)), Color.White)
        drawPath(spike(Offset(head.x + i * u * 0.07f + dir * u * 0.1f, head.y + u * 0.17f), u * 0.018f, Offset(head.x + i * u * 0.07f + dir * u * 0.1f, head.y + u * 0.12f)), Color.White)
    }
    drawRoundRect(bone.shade(0.05f), Offset(head.x - u * 0.16f + dir * u * 0.08f, head.y + u * 0.0f), Size(u * 0.32f, u * 0.12f), CornerRadius(u * 0.05f))
    for (side in listOf(-1f, 1f)) {
        val e = head + Offset(side * u * 0.13f + dir * u * 0.03f, -u * 0.05f)
        drawOval(hole, e - Offset(u * 0.08f, u * 0.06f), Size(u * 0.16f, u * 0.12f))
        drawCircle(soul.copy(alpha = 0.5f * p), u * 0.07f, e)
        drawCircle(Color(0xFFE9D5FF), u * 0.03f, e)
        drawLine(shade, e + Offset(-u * 0.08f, -u * (0.09f + side * 0.02f)), e + Offset(u * 0.08f, -u * (0.09f - side * 0.02f)), u * 0.03f, StrokeCap.Round)
    }
    for (side in listOf(-1f, 1f)) drawOval(hole, Offset(head.x + side * u * 0.04f + dir * u * 0.1f - u * 0.015f, head.y + u * 0.04f), Size(u * 0.03f, u * 0.04f))
}

// --- Steintitan ---

private fun DrawScope.drawStoneTitan(b: ArtBody) {
    val u = b.u
    val c = b.c
    val dir = b.dir
    val time = b.time
    val stone = Color(0xFF57534E)
    val lava = Color(0xFFFB923C)
    val moss = Color(0xFF4D7C0F)
    val p = b.pulse(2.2f)

    drawCircle(lava.copy(alpha = 0.1f + 0.05f * p), u * 1.3f, c)
    // Geröll am Boden
    for (k in -3..3) drawPath(rock(Offset(c.x + k * u * 0.28f, c.y + u * 0.97f), u * 0.09f, u * 0.05f, k + 4), stone.shade(0.3f))
    // Säulenbeine aus Felsblöcken mit Lavaadern
    for (side in listOf(-1f, 1f)) {
        val x = c.x + side * u * 0.38f
        drawPath(rock(Offset(x, c.y + u * 0.48f), u * 0.25f, u * 0.2f, 2 + side.toInt()), stone.shade(0.18f))
        drawPath(rock(Offset(x, c.y + u * 0.8f), u * 0.28f, u * 0.17f, 4 + side.toInt()), stone.shade(0.25f))
        drawLine(lava.copy(alpha = 0.6f * p), Offset(x - side * u * 0.02f, c.y + u * 0.4f), Offset(x + side * u * 0.04f, c.y + u * 0.75f), u * 0.04f, StrokeCap.Round)
    }
    // Gewaltige Arme aus Felssegmenten, geballte Fäuste mit gesprengten Ketten
    for (side in listOf(-1f, 1f)) {
        val shoulder = c + Offset(side * u * 0.85f, -u * 0.55f)
        val fist = c + Offset(side * u * 1.05f, u * 0.35f)
        drawPath(rock(shoulder + (fist - shoulder) * 0.35f, u * 0.25f, u * 0.3f, 6 + side.toInt()), stone.shade(0.08f))
        drawPath(rock(shoulder + (fist - shoulder) * 0.7f, u * 0.22f, u * 0.25f, 8 + side.toInt()), stone.shade(0.14f))
        drawLine(lava.copy(alpha = 0.5f + 0.4f * p), shoulder + (fist - shoulder) * 0.2f, shoulder + (fist - shoulder) * 0.8f, u * 0.035f, StrokeCap.Round)
        drawCircle(stone.bright(0.06f), u * 0.3f, fist)
        for (k in 0..3) drawRoundRect(stone.shade(0.12f), Offset(fist.x - u * 0.24f + k * u * 0.12f, fist.y - u * 0.08f), Size(u * 0.11f, u * 0.2f), CornerRadius(u * 0.04f))
        drawRoundRect(Color(0xFF374151), fist + Offset(-u * 0.28f, -u * 0.45f), Size(u * 0.56f, u * 0.14f), CornerRadius(u * 0.04f))
        for (k in 0..2) drawCircle(Color(0xFF9CA3AF), u * 0.02f, fist + Offset(-u * 0.18f + k * u * 0.18f, -u * 0.38f))
        for (k in 0..2) drawOval(Color(0xFF4B5563), Offset(fist.x + side * u * (0.25f + k * 0.12f) - u * 0.07f, fist.y - u * 0.42f + k * u * 0.08f), Size(u * 0.14f, u * 0.09f), style = Stroke(u * 0.03f))
    }
    // Massiger Rumpf aus Platten mit Lavarissen
    val body = Path().apply {
        moveTo(c.x - u * 0.95f, c.y - u * 0.75f)
        lineTo(c.x + u * 0.9f, c.y - u * 0.85f)
        lineTo(c.x + u * 0.7f, c.y + u * 0.45f)
        lineTo(c.x - u * 0.62f, c.y + u * 0.5f)
        close()
    }
    drawPath(body, stone)
    clipPath(body) {
        val plates = listOf(Offset(-0.5f, -0.5f), Offset(0.45f, -0.55f), Offset(-0.35f, 0.25f), Offset(0.4f, 0.2f))
        for ((i, o) in plates.withIndex()) drawPath(rock(c + Offset(o.x * u, o.y * u), u * 0.38f, u * 0.32f, i + 12), stone.bright(0.04f * (i % 2)))
        // Lavaschimmer hinter den Rissen
        drawCircle(lava.copy(alpha = 0.2f * p), u * 0.5f, c + Offset(0f, -u * 0.15f))
    }
    val cracks = Path().apply {
        moveTo(c.x - u * 0.45f, c.y - u * 0.5f)
        lineTo(c.x + u * 0.05f, c.y - u * 0.1f)
        lineTo(c.x - u * 0.15f, c.y + u * 0.3f)
        lineTo(c.x - u * 0.05f, c.y + u * 0.45f)
        moveTo(c.x + u * 0.05f, c.y - u * 0.1f)
        lineTo(c.x + u * 0.45f, c.y - u * 0.25f)
        lineTo(c.x + u * 0.6f, c.y - u * 0.05f)
        moveTo(c.x + u * 0.2f, c.y - u * 0.6f)
        lineTo(c.x + u * 0.4f, c.y - u * 0.25f)
        moveTo(c.x - u * 0.15f, c.y + u * 0.3f)
        lineTo(c.x - u * 0.4f, c.y + u * 0.35f)
    }
    drawPath(cracks, lava.copy(alpha = 0.25f * p), style = Stroke(u * 0.14f, cap = StrokeCap.Round))
    drawPath(cracks, lava.copy(alpha = 0.6f + 0.4f * p), style = Stroke(u * 0.055f, cap = StrokeCap.Round))
    drawPath(cracks, Color(0xFFFDE68A).copy(alpha = 0.7f), style = Stroke(u * 0.018f, cap = StrokeCap.Round))
    drawCircle(lava.copy(alpha = 0.35f * p), u * 0.26f, c + Offset(0f, -u * 0.15f))
    drawCircle(Color(0xFFFDE68A), u * 0.09f, c + Offset(0f, -u * 0.15f))
    // Tropfende Lava
    for (k in 0..1) {
        val t = (time * 0.6f + k * 0.5f) % 1f
        drawCircle(lava.copy(alpha = 1f - t), u * 0.035f, Offset(c.x - u * 0.05f + k * u * 0.65f, c.y + u * 0.45f + t * u * 0.4f))
    }
    // Felsschultern mit Moos, Runen und Rauch
    for (side in listOf(-1f, 1f)) {
        val sp = Path().apply {
            moveTo(c.x + side * u * 0.5f, c.y - u * 0.8f)
            lineTo(c.x + side * u * 0.85f, c.y - u * 1.05f)
            lineTo(c.x + side * u * 1.15f, c.y - u * 0.75f)
            lineTo(c.x + side * u * 1.05f, c.y - u * 0.45f)
            lineTo(c.x + side * u * 0.6f, c.y - u * 0.5f)
            close()
        }
        drawPath(sp, stone.bright(0.1f))
        clipPath(sp) { drawOval(moss, Offset(c.x + side * u * 0.82f - u * 0.3f, c.y - u * 1.12f), Size(u * 0.6f, u * 0.2f)) }
        val rc = c + Offset(side * u * 0.85f, -u * 0.72f)
        drawLine(lava.copy(alpha = 0.7f * p), rc + Offset(0f, -u * 0.08f), rc + Offset(0f, u * 0.08f), u * 0.03f, StrokeCap.Round)
        drawLine(lava.copy(alpha = 0.7f * p), rc + Offset(-u * 0.06f, -u * 0.02f), rc + Offset(u * 0.06f, u * 0.04f), u * 0.03f, StrokeCap.Round)
        val smoke = ((time * 0.5f + (side + 1f) * 0.3f) % 1f)
        drawCircle(Color(0xFF9CA3AF).copy(alpha = 0.3f * (1f - smoke)), u * (0.08f + smoke * 0.1f), c + Offset(side * u * 0.9f, -u * (1.1f + smoke * 0.4f)))
    }
    // Kopf mit Felsenkrone, Rissen, Lavaaugen und glühendem Maul
    val h = c + Offset(dir * u * 0.05f, -u * 1.0f)
    for (i in -2..2) {
        val base = h + Offset(i * u * 0.13f, -u * 0.2f)
        drawPath(spike(base, u * 0.07f, base + Offset(i * u * 0.04f, -u * (0.25f + (2 - abs(i)) * 0.08f))), stone.bright(0.15f))
    }
    drawPath(rock(h, u * 0.34f, u * 0.28f, 21), stone.bright(0.12f))
    drawLine(stone.shade(0.3f), h + Offset(-u * 0.28f, -u * 0.06f), h + Offset(u * 0.28f, -u * 0.06f), u * 0.07f, StrokeCap.Round)
    drawLine(lava.copy(alpha = 0.6f), h + Offset(-dir * u * 0.22f, -u * 0.2f), h + Offset(-dir * u * 0.12f, -u * 0.08f), u * 0.025f, StrokeCap.Round)
    for (side in listOf(-1f, 1f)) {
        val e = h + Offset(side * u * 0.13f + dir * u * 0.03f, u * 0.03f)
        drawCircle(lava.copy(alpha = 0.5f * p), u * 0.09f, e)
        drawPath(Path().apply {
            moveTo(e.x + side * u * 0.07f, e.y - u * 0.03f)
            lineTo(e.x - side * u * 0.07f, e.y + u * 0.01f)
            quadraticTo(e.x, e.y + u * 0.05f, e.x + side * u * 0.07f, e.y - u * 0.03f)
            close()
        }, Color(0xFFFDE68A))
    }
    drawRoundRect(Color(0xFF292524), Offset(h.x - u * 0.14f, h.y + u * 0.13f), Size(u * 0.28f, u * 0.09f), CornerRadius(u * 0.03f))
    drawRoundRect(lava.copy(alpha = 0.6f + 0.4f * p), Offset(h.x - u * 0.11f, h.y + u * 0.15f), Size(u * 0.22f, u * 0.05f), CornerRadius(u * 0.02f))
    for (k in -1..1) drawPath(spike(Offset(h.x + k * u * 0.07f, h.y + u * 0.13f), u * 0.02f, Offset(h.x + k * u * 0.07f, h.y + u * 0.18f)), stone.bright(0.2f))
}

// --- Sturmhexe ---

private fun DrawScope.drawStormWitch(b: ArtBody) {
    val u = b.u
    val c = b.c
    val dir = b.dir
    val time = b.time
    val robe = Color(0xFF1E3A8A)
    val storm = Color(0xFF60A5FA)
    val bolt = Color(0xFFE0F2FE)
    val skin = Color(0xFFDDE3EC)
    val hair = Color(0xFFF1F5F9)
    val p = b.pulse(6f)
    val hover = sin(time * 2f) * u * 0.06f
    // Schlankes Gerüst etwas kleiner als der Boss-Rahmen
    val w = ArtBody(c + Offset(0f, -u * 0.05f + hover), u * 0.85f, dir, time)
    val wu = w.u
    val h = w.head
    val hr = w.headR

    // Sturmwolke unter ihr
    for (k in -2..2) drawCircle(Color(0xFF475569).copy(alpha = 0.85f), u * (0.24f - abs(k) * 0.03f), Offset(c.x + k * u * 0.25f, c.y + u * 0.85f + sin(time * 3f + k) * u * 0.03f))
    drawOval(storm.copy(alpha = 0.2f + 0.1f * p), Offset(c.x - u * 0.7f, c.y + u * 0.8f), Size(u * 1.4f, u * 0.25f))

    // Langes weißes Haar, im Sturm wehend
    val sway = sin(time * 3f) * u * 0.06f
    drawPath(Path().apply {
        moveTo(h.x - hr * 1.05f, h.y - hr * 0.2f)
        cubicTo(h.x - hr * 1.15f, h.y - hr * 1.5f, h.x + hr * 1.15f, h.y - hr * 1.5f, h.x + hr * 1.05f, h.y - hr * 0.2f)
        quadraticTo(h.x + hr * 1.2f - dir * sway, w.shoulderY + wu * 0.3f, h.x - dir * hr * 0.4f - dir * sway * 3f, w.hipY + wu * 0.1f)
        quadraticTo(h.x - dir * hr * 1.6f - dir * sway * 2f, w.shoulderY + wu * 0.2f, h.x - hr * 1.05f, h.y - hr * 0.2f)
        close()
    }, hair.shade(0.12f))

    // Ein Bein im hohen Schlitz sichtbar
    artFemaleLegs(w, skin, Color(0xFF172554), bootHeight = 0.5f, spacing = 0.13f, thigh = 0.11f, ankle = 0.055f, bootWidth = 0.075f)

    // Ärmel mit Fransen, schlanke Hände
    val hand = w.hand + Offset(dir * wu * 0.1f, 0f)
    val off = w.offHand + Offset(0f, -wu * 0.2f)
    for (hd in listOf(hand, off)) {
        val s = if (hd.x > c.x) 1f else -1f
        val shoulder = Offset(c.x + s * wu * 0.2f, w.shoulderY + wu * 0.08f)
        drawLine(skin.shade(0.05f), shoulder, hd, wu * 0.11f, StrokeCap.Round)
        drawPath(Path().apply {
            moveTo(shoulder.x + s * wu * 0.05f, shoulder.y)
            lineTo(hd.x - s * wu * 0.12f, hd.y - wu * 0.12f)
            lineTo(hd.x - s * wu * 0.02f, hd.y + wu * 0.1f)
            lineTo(hd.x - s * wu * 0.18f, hd.y + wu * 0.02f)
            lineTo(hd.x - s * wu * 0.3f, hd.y + wu * 0.14f)
            lineTo(shoulder.x - s * wu * 0.02f, shoulder.y + wu * 0.25f)
            close()
        }, robe.bright(0.08f))
    }
    // Sanduhr-Silhouette: Schnürmieder, fließender Rock mit hohem Schlitz
    val waistY = w.hipY - wu * 0.12f
    val hemY = c.y + u * 0.8f
    val bodice = femaleTorso(w, waistY, w.hipY + wu * 0.04f, hip = 0.29f, waist = 0.14f, shoulder = 0.2f, bust = 0.29f)
    drawPath(bodice, robe.shade(0.2f))
    val skirt = Path().apply {
        moveTo(c.x - wu * 0.28f, w.hipY)
        lineTo(c.x + wu * 0.28f, w.hipY)
        cubicTo(c.x + wu * 0.42f, w.hipY + wu * 0.25f, c.x + wu * 0.58f, w.hipY + wu * 0.5f, c.x + wu * 0.66f, hemY - wu * 0.05f)
        for (i in 0..2) {
            val x0 = c.x + wu * 0.66f - i * wu * 0.17f
            lineTo(x0 - wu * 0.085f, hemY + wu * (if (i % 2 == 0) 0.1f else 0.03f))
            lineTo(x0 - wu * 0.17f, hemY - wu * 0.05f)
        }
        // Hoher Schlitz in der Mitte, das Bein schaut heraus
        lineTo(c.x + wu * 0.13f, hemY - wu * 0.05f)
        lineTo(c.x, w.hipY + wu * 0.12f)
        lineTo(c.x - wu * 0.13f, hemY - wu * 0.05f)
        for (i in 0..2) {
            val x0 = c.x - wu * 0.15f - i * wu * 0.17f
            lineTo(x0 - wu * 0.085f, hemY + wu * (if (i % 2 == 0) 0.1f else 0.03f))
            lineTo(x0 - wu * 0.17f, hemY - wu * 0.05f)
        }
        cubicTo(c.x - wu * 0.58f, w.hipY + wu * 0.5f, c.x - wu * 0.42f, w.hipY + wu * 0.25f, c.x - wu * 0.28f, w.hipY)
        close()
    }
    drawPath(skirt, robe)
    clipPath(skirt) { drawRect(robe.shade(0.3f), Offset(c.x - dir * wu * 0.15f - (if (dir > 0) wu * 0.7f else 0f), w.hipY), Size(wu * 0.7f, u * 2f)) }
    for ((x0, y0) in listOf(-0.4f to 0.75f, 0.42f to 0.9f)) {
        val s0 = Offset(c.x + x0 * wu, w.c.y + y0 * wu)
        drawPath(Path().apply {
            moveTo(s0.x, s0.y)
            lineTo(s0.x + wu * 0.08f, s0.y + wu * 0.12f)
            lineTo(s0.x - wu * 0.02f, s0.y + wu * 0.14f)
            lineTo(s0.x + wu * 0.06f, s0.y + wu * 0.28f)
        }, storm.copy(alpha = 0.8f), style = Stroke(wu * 0.03f, cap = StrokeCap.Round))
    }
    // Mieder: Herzausschnitt, Schnürung, Brustrundung
    val neckline = Path().apply {
        moveTo(c.x - wu * 0.2f, w.shoulderY - wu * 0.04f)
        lineTo(c.x + wu * 0.2f, w.shoulderY - wu * 0.04f)
        lineTo(c.x + wu * 0.22f, w.shoulderY + wu * 0.12f)
        quadraticTo(c.x + wu * 0.11f, w.shoulderY + wu * 0.06f, c.x, w.shoulderY + wu * 0.2f)
        quadraticTo(c.x - wu * 0.11f, w.shoulderY + wu * 0.06f, c.x - wu * 0.22f, w.shoulderY + wu * 0.12f)
        close()
    }
    drawPath(neckline, skin.shade(0.04f))
    drawPath(Path().apply {
        moveTo(c.x - wu * 0.03f, w.shoulderY + wu * 0.1f)
        quadraticTo(c.x, w.shoulderY + wu * 0.19f, c.x + wu * 0.03f, w.shoulderY + wu * 0.1f)
    }, skin.shade(0.1f), style = Stroke(wu * 0.015f, cap = StrokeCap.Round))
    for (side in listOf(-1f, 1f)) {
        drawArc(storm.copy(alpha = 0.5f), if (side < 0) 160f else 200f, 140f, false, Offset(c.x + side * wu * 0.12f - wu * 0.11f, w.shoulderY + wu * 0.1f), Size(wu * 0.22f, wu * 0.18f), style = Stroke(wu * 0.02f, cap = StrokeCap.Round))
    }
    drawPath(Path().apply {
        moveTo(c.x - wu * 0.22f, w.shoulderY + wu * 0.12f)
        quadraticTo(c.x - wu * 0.11f, w.shoulderY + wu * 0.06f, c.x, w.shoulderY + wu * 0.2f)
        quadraticTo(c.x + wu * 0.11f, w.shoulderY + wu * 0.06f, c.x + wu * 0.22f, w.shoulderY + wu * 0.12f)
    }, storm, style = Stroke(wu * 0.025f))
    for (k in 0..2) {
        val y = w.shoulderY + wu * (0.27f + k * 0.07f)
        drawLine(bolt, Offset(c.x - wu * 0.05f, y), Offset(c.x + wu * 0.05f, y + wu * 0.04f), wu * 0.018f)
        drawLine(bolt, Offset(c.x + wu * 0.05f, y), Offset(c.x - wu * 0.05f, y + wu * 0.04f), wu * 0.018f)
    }
    drawLine(storm, Offset(c.x - wu * 0.15f, waistY + wu * 0.02f), Offset(c.x + wu * 0.15f, waistY + wu * 0.02f), wu * 0.05f)
    drawCircle(bolt, wu * 0.04f, Offset(c.x, waistY + wu * 0.02f))
    // Halsband mit Blitzstein
    drawLine(Color(0xFF0F172A), Offset(c.x - wu * 0.09f, w.shoulderY - wu * 0.08f), Offset(c.x + wu * 0.09f, w.shoulderY - wu * 0.08f), wu * 0.035f)
    drawCircle(storm, wu * 0.03f, Offset(c.x, w.shoulderY - wu * 0.05f))

    // Gesicht: blass, eisblaue Augen, dunkle Lippen
    artFemaleFace(w, skin, Color(0xFF64748B), pupil = Color(0xFF0EA5E9), lips = Color(0xFF4C1D95), blush = false)
    val brimY = h.y - hr * 0.6f
    // Pony unter der Hutkrempe
    clipRect(top = brimY) {
        drawPath(Path().apply {
            moveTo(h.x - hr * 1.04f, h.y + hr * 0.6f)
            lineTo(h.x - hr * 1.04f, h.y - hr * 0.6f)
            lineTo(h.x + hr * 1.04f, h.y - hr * 0.6f)
            lineTo(h.x + hr * 1.04f, h.y + hr * 0.6f)
            quadraticTo(h.x + hr * 0.8f, h.y - hr * 0.3f, h.x + dir * hr * 0.2f, h.y - hr * 0.45f)
            quadraticTo(h.x - hr * 0.8f, h.y - hr * 0.3f, h.x - hr * 1.04f, h.y + hr * 0.6f)
            close()
        }, hair)
    }
    // Spitzhut mit geknickter Spitze und Blitzband
    val tip = Offset(h.x - dir * hr * 1.0f, h.y - hr * 2.3f + sin(time * 2f) * hr * 0.06f)
    drawPath(Path().apply {
        moveTo(h.x - hr * 0.82f, brimY)
        quadraticTo(h.x - hr * 0.3f, h.y - hr * 1.6f, h.x - dir * hr * 0.1f, h.y - hr * 2.0f)
        quadraticTo(h.x - dir * hr * 0.5f, h.y - hr * 2.25f, tip.x, tip.y)
        quadraticTo(h.x + dir * hr * 0.1f, h.y - hr * 1.7f, h.x + hr * 0.82f, brimY)
        close()
    }, robe.shade(0.35f))
    drawOval(robe.shade(0.5f), Offset(h.x - hr * 1.5f, brimY - hr * 0.22f), Size(hr * 3f, hr * 0.48f))
    drawOval(robe.shade(0.25f), Offset(h.x - hr * 1.45f, brimY - hr * 0.24f), Size(hr * 2.9f, hr * 0.34f))
    drawLine(storm, Offset(h.x - hr * 0.8f, brimY - hr * 0.2f), Offset(h.x + hr * 0.8f, brimY - hr * 0.2f), hr * 0.16f)
    drawPath(Path().apply {
        moveTo(h.x + dir * hr * 0.05f, brimY - hr * 0.35f)
        lineTo(h.x - dir * hr * 0.08f, brimY - hr * 0.18f)
        lineTo(h.x + dir * hr * 0.04f, brimY - hr * 0.16f)
        lineTo(h.x - dir * hr * 0.06f, brimY + hr * 0.02f)
    }, ArtGold, style = Stroke(hr * 0.06f, cap = StrokeCap.Round))

    // Stab mit knisternder Blitzkugel
    val top = hand + Offset(dir * wu * 0.05f, -wu * 1.4f)
    drawLine(Color(0xFF334155), hand + Offset(-dir * wu * 0.02f, wu * 0.8f), top + Offset(0f, wu * 0.12f), wu * 0.08f, StrokeCap.Round)
    for (side in listOf(-1f, 1f)) drawPath(Path().apply {
        moveTo(top.x, top.y + wu * 0.15f)
        quadraticTo(top.x + side * wu * 0.3f, top.y, top.x + side * wu * 0.12f, top.y - wu * 0.22f)
    }, Color(0xFF334155), style = Stroke(wu * 0.05f, cap = StrokeCap.Round))
    drawCircle(storm.copy(alpha = 0.4f * p), wu * 0.34f, top)
    drawCircle(Color(0xFF93C5FD), wu * 0.16f, top)
    drawCircle(Color.White, wu * 0.07f, top)
    for (i in 0 until 3) {
        val a = time * 5f + i * 2.1f
        val mid = top + Offset(cos(a + 0.5f) * wu * 0.22f, sin(a + 0.5f) * wu * 0.22f)
        val end = top + Offset(cos(a) * wu * 0.45f, sin(a) * wu * 0.45f)
        drawLine(bolt, top, mid, wu * 0.03f)
        drawLine(bolt, mid, end, wu * 0.03f)
    }
    // Blitz in der zweiten Hand
    val boltPath = Path().apply {
        moveTo(off.x + wu * 0.05f, off.y - wu * 0.6f)
        lineTo(off.x - wu * 0.08f, off.y - wu * 0.32f)
        lineTo(off.x + wu * 0.04f, off.y - wu * 0.3f)
        lineTo(off.x - wu * 0.05f, off.y - wu * 0.08f)
    }
    drawPath(boltPath, storm.copy(alpha = 0.5f * p), style = Stroke(wu * 0.1f, cap = StrokeCap.Round))
    drawPath(boltPath, Color.White, style = Stroke(wu * 0.035f, cap = StrokeCap.Round))
    artHand(w, hand, skin.shade(0.08f), 0.09f)
    artHand(w, off, skin.shade(0.08f), 0.09f)
}
