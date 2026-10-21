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
import kotlin.math.sin

internal val demonArt: Map<String, DrawScope.(ArtBody) -> Unit> = mapOf(
    "imp" to DrawScope::drawImp,
    "succubus" to DrawScope::drawSuccubus,
    "demonhunter" to DrawScope::drawDemonHunter,
    "nightstalker" to DrawScope::drawNightstalker,
    "bladedemon" to DrawScope::drawBladeDemon,
    "archdemon" to DrawScope::drawArchdemon,
)

private val HornDark = Color(0xFF292524)

/** Geschwungene Hörner; [curl] > 1 lässt sie weiter nach außen und oben ragen. */
private fun DrawScope.demonHorns(b: ArtBody, color: Color, curl: Float = 1f, back: Boolean = false) {
    val h = b.head
    val hr = b.headR
    for (side in listOf(-1f, 1f)) {
        val horn = Path().apply {
            moveTo(h.x + side * hr * 0.3f, h.y - hr * 0.8f)
            if (back) {
                quadraticTo(h.x + side * hr * 1.3f * curl, h.y - hr * 1.5f * curl, h.x + side * hr * 1.6f * curl, h.y - hr * 0.6f)
                quadraticTo(h.x + side * hr * 1.0f * curl, h.y - hr * 1.0f * curl, h.x + side * hr * 0.75f, h.y - hr * 0.5f)
            } else {
                quadraticTo(h.x + side * hr * 1.25f * curl, h.y - hr * 1.0f, h.x + side * hr * 1.0f * curl, h.y - hr * 1.85f * curl)
                quadraticTo(h.x + side * hr * 0.85f * curl, h.y - hr * 1.15f, h.x + side * hr * 0.72f, h.y - hr * 0.5f)
            }
            close()
        }
        drawPath(horn, color)
        clipPath(horn) {
            for (k in 1..3) drawLine(color.bright(0.2f), Offset(h.x + side * hr * (0.5f + k * 0.2f * curl), h.y - hr * (0.6f + k * 0.3f)), Offset(h.x + side * hr * (0.8f + k * 0.2f * curl), h.y - hr * (0.75f + k * 0.3f)), hr * 0.05f)
        }
    }
}

/** Peitschender Schwanz mit Pfeilspitze hinter dem Körper. */
private fun DrawScope.demonTail(b: ArtBody, color: Color, length: Float = 1f) {
    val u = b.u
    val c = b.c
    val dir = b.dir
    val wag = sin(b.time * 3f) * u * 0.1f
    val start = Offset(c.x - dir * u * 0.15f, b.hipY)
    val end = Offset(c.x - dir * u * 1.05f * length, b.hipY - u * 0.55f * length + wag)
    drawPath(Path().apply {
        moveTo(start.x, start.y)
        cubicTo(c.x - dir * u * 0.9f * length, b.hipY + u * 0.35f, c.x - dir * u * 1.1f * length, b.hipY - u * 0.1f, end.x, end.y)
    }, color, style = Stroke(u * 0.09f, cap = StrokeCap.Round))
    drawPath(Path().apply {
        moveTo(end.x - u * 0.13f, end.y + u * 0.04f)
        lineTo(end.x - dir * u * 0.03f, end.y - u * 0.2f)
        lineTo(end.x + u * 0.13f, end.y + u * 0.04f)
        lineTo(end.x, end.y - u * 0.03f)
        close()
    }, color)
}

/** Fledermausflügel mit Knochenarm, Fingern und gewellter Haut. */
private fun DrawScope.batWings(b: ArtBody, membrane: Color, bone: Color, size: Float = 1f) {
    val u = b.u * size
    val c = Offset(b.c.x, b.shoulderY + b.u * 0.1f)
    val flap = sin(b.time * 4f) * u * 0.1f
    for (side in listOf(-1f, 1f)) {
        val wrist = c + Offset(side * u * 0.95f, -u * 0.75f - flap)
        val tips = listOf(
            c + Offset(side * u * 1.55f, -u * 0.35f - flap * 0.6f),
            c + Offset(side * u * 1.45f, u * 0.25f - flap * 0.3f),
            c + Offset(side * u * 0.95f, u * 0.5f),
        )
        val wing = Path().apply {
            moveTo(c.x + side * u * 0.15f, c.y)
            lineTo(wrist.x, wrist.y)
            var prev = wrist
            for (t in tips) {
                val mid = (prev + t) * 0.5f + Offset(-side * u * 0.12f, u * 0.1f)
                quadraticTo(mid.x, mid.y, t.x, t.y)
                prev = t
            }
            quadraticTo(c.x + side * u * 0.5f, c.y + u * 0.2f, c.x + side * u * 0.15f, c.y + u * 0.2f)
            close()
        }
        drawPath(wing, membrane)
        clipPath(wing) { drawRect(membrane.shade(0.2f), Offset(c.x + (if (side > 0) u * 0.9f else -u * 2f), c.y - u), Size(u * 1.1f, u * 2f)) }
        drawLine(bone, c + Offset(side * u * 0.15f, 0f), wrist, u * 0.08f, StrokeCap.Round)
        for (t in tips) drawLine(bone, wrist, t, u * 0.045f, StrokeCap.Round)
        drawPath(triangle3dm(wrist, u * 0.05f, u * 0.16f), bone)
    }
}

private fun triangle3dm(base: Offset, halfW: Float, h: Float) = Path().apply {
    moveTo(base.x - halfW, base.y)
    lineTo(base.x, base.y - h)
    lineTo(base.x + halfW, base.y)
    close()
}

/** Glühende Schlitzaugen. */
private fun DrawScope.demonEyes(b: ArtBody, glow: Color, angry: Float = 0.06f, y: Float = b.head.y + b.headR * 0.05f) {
    val hr = b.headR
    val p = b.pulse(5f)
    for (side in listOf(-1f, 1f)) {
        val e = Offset(b.head.x + side * hr * 0.36f + b.dir * hr * 0.06f, y)
        drawCircle(glow.copy(alpha = 0.35f * p), hr * 0.2f, e)
        drawLine(glow, e + Offset(-hr * 0.15f, -side * hr * angry), e + Offset(hr * 0.15f, side * hr * angry), hr * 0.13f, StrokeCap.Round)
        drawLine(Color(0xFF1C1917), e + Offset(-hr * 0.17f, -hr * (0.2f + side * angry * 1.5f)), e + Offset(hr * 0.17f, -hr * (0.2f - side * angry * 1.5f)), hr * 0.08f, StrokeCap.Round)
    }
}

/** Grinsender Mund mit zwei Fangzähnen. */
private fun DrawScope.fangGrin(b: ArtBody, width: Float = 0.3f, inside: Color = Color(0xFF450A0A)) {
    val h = b.head
    val hr = b.headR
    val m = Offset(h.x + b.dir * hr * 0.06f, h.y + hr * 0.5f)
    drawArc(inside, 0f, 180f, true, m - Offset(hr * width, hr * 0.14f), Size(hr * width * 2f, hr * 0.28f))
    for (side in listOf(-1f, 1f)) drawPath(Path().apply {
        moveTo(m.x + side * hr * width * 0.5f - hr * 0.05f, m.y)
        lineTo(m.x + side * hr * width * 0.5f, m.y + hr * 0.12f)
        lineTo(m.x + side * hr * width * 0.5f + hr * 0.05f, m.y)
        close()
    }, Color.White)
}

// --- Teufelchen ---

private fun DrawScope.drawImp(b: ArtBody) {
    val u = b.u
    val c = b.c
    val dir = b.dir
    val skin = Color(0xFFDC4A26)
    val orange = Color(0xFFF97316)
    val cloth = Color(0xFF1F2937)
    // Kleiner, geduckter Körper: alles etwas tiefer
    val low = ArtBody(c + Offset(0f, u * 0.12f), u * 0.95f, dir, b.time)
    val h = low.head
    val hr = low.headR * 1.12f

    demonTail(low, skin.shade(0.15f), 1.1f)
    // Kleine Flügelstummel
    batWings(low, Color(0xFF7C2D12), skin.shade(0.3f), 0.55f)

    // Krumme Beine mit Klauenfüßen
    for (side in listOf(-1f, 1f)) {
        val hip = Offset(c.x + side * u * 0.18f, low.hipY)
        val knee = Offset(c.x + side * u * 0.32f, (low.hipY + low.footY) / 2f)
        val foot = Offset(c.x + side * u * 0.24f, low.footY - u * 0.05f)
        drawLine(skin.shade(0.1f), hip, knee, u * 0.17f, StrokeCap.Round)
        drawLine(skin.shade(0.1f), knee, foot, u * 0.14f, StrokeCap.Round)
        for (k in -1..1) drawLine(HornDark, foot, foot + Offset(dir * u * 0.12f + k * u * 0.06f, u * 0.07f), u * 0.04f, StrokeCap.Round)
    }
    // Lendentuch
    drawPath(Path().apply {
        moveTo(c.x - u * 0.28f, low.hipY - u * 0.05f)
        lineTo(c.x + u * 0.28f, low.hipY - u * 0.05f)
        lineTo(c.x + u * 0.16f, low.hipY + u * 0.25f)
        lineTo(c.x, low.hipY + u * 0.15f)
        lineTo(c.x - u * 0.16f, low.hipY + u * 0.25f)
        close()
    }, cloth)

    // Arme
    val hand = low.hand
    val off = low.offHand
    artArm(low, hand, skin.shade(0.05f), 0.15f, 0.3f)
    artArm(low, off, skin.shade(0.05f), 0.15f, 0.3f)
    // Runder Bauch, Brustgurt
    drawOval(skin, Offset(c.x - u * 0.34f, low.shoulderY - u * 0.05f), Size(u * 0.68f, low.hipY - low.shoulderY + u * 0.1f))
    drawOval(Color(0xFFF59E5B), Offset(c.x - u * 0.2f, low.shoulderY + u * 0.15f), Size(u * 0.4f, low.hipY - low.shoulderY - u * 0.15f))
    drawLine(cloth, Offset(c.x - dir * u * 0.3f, low.shoulderY), Offset(c.x + dir * u * 0.3f, low.hipY - u * 0.05f), u * 0.08f)
    drawCircle(orange, u * 0.05f, Offset(c.x, (low.shoulderY + low.hipY) / 2f))

    // Großer Kopf mit Fledermausohren, Hörnchen und breitem Grinsen
    for (side in listOf(-1f, 1f)) {
        drawPath(Path().apply {
            moveTo(h.x + side * hr * 0.7f, h.y - hr * 0.3f)
            lineTo(h.x + side * hr * 1.6f, h.y - hr * 0.55f)
            lineTo(h.x + side * hr * 0.85f, h.y + hr * 0.25f)
            close()
        }, skin.shade(0.1f))
        drawPath(Path().apply {
            moveTo(h.x + side * hr * 0.85f, h.y - hr * 0.2f)
            lineTo(h.x + side * hr * 1.35f, h.y - hr * 0.42f)
            lineTo(h.x + side * hr * 0.9f, h.y + hr * 0.1f)
            close()
        }, Color(0xFF7C2D12))
    }
    // Gerüst mit vergrößertem Kopf an derselben Stelle (für Hörner und Mund)
    val hu = hr / 0.42f
    val headBody = ArtBody(Offset(c.x, h.y + hu * 0.9f), hu, dir, b.time)
    demonHorns(headBody, HornDark, 0.7f)
    drawCircle(skin, hr, h)
    drawArc(skin.shade(0.15f), if (dir > 0) 100f else -80f, 160f, false, h - Offset(hr * 0.92f, hr * 0.92f), Size(hr * 1.84f, hr * 1.84f), style = Stroke(hr * 0.12f))
    // Große runde Augen
    for (side in listOf(-1f, 1f)) {
        val e = Offset(h.x + side * hr * 0.38f + dir * hr * 0.06f, h.y - hr * 0.05f)
        drawCircle(Color(0xFFFDE047), hr * 0.22f, e)
        drawCircle(Color(0xFF1C1917), hr * 0.11f, e + Offset(dir * hr * 0.05f, 0f))
        drawCircle(Color.White, hr * 0.04f, e + Offset(-hr * 0.05f, -hr * 0.07f))
        drawLine(HornDark, e + Offset(-hr * 0.2f, -hr * (0.3f + side * 0.08f)), e + Offset(hr * 0.2f, -hr * (0.3f - side * 0.08f)), hr * 0.08f, StrokeCap.Round)
    }
    drawCircle(skin.shade(0.2f), hr * 0.06f, Offset(h.x + dir * hr * 0.1f, h.y + hr * 0.22f))
    fangGrin(headBody, 0.42f)

    // Zwei krumme Dolche
    for (hd in listOf(hand, off)) {
        val s = if (hd == hand) dir else -dir
        artBlade(hd + Offset(0f, -u * 0.05f), hd + Offset(s * u * 0.3f, -u * 0.45f), u * 0.055f, Color(0xFFD1D5DB))
        drawLine(orange, hd + Offset(-u * 0.09f, -u * 0.05f), hd + Offset(u * 0.09f, -u * 0.05f), u * 0.05f, StrokeCap.Round)
        artHand(low, hd, skin.shade(0.1f), 0.1f)
    }
}

// --- Sukkubus ---

private fun DrawScope.drawSuccubus(b: ArtBody) {
    val u = b.u
    val c = b.c
    val dir = b.dir
    val time = b.time
    val skin = Color(0xFFF0A6B6)
    val wine = Color(0xFF831843)
    val pink = Color(0xFFF472B6)
    val hair = Color(0xFF1F1A24)
    val h = b.head
    val hr = b.headR
    val p = b.pulse(4f)

    batWings(b, wine.copy(alpha = 0.95f), wine.shade(0.4f), 0.95f)
    demonTail(b, wine, 0.9f)
    // Langes schwarzes Haar hinten, schmal zum Rücken auslaufend
    drawPath(Path().apply {
        moveTo(h.x - hr * 1.05f, h.y - hr * 0.2f)
        cubicTo(h.x - hr * 1.15f, h.y - hr * 1.5f, h.x + hr * 1.15f, h.y - hr * 1.5f, h.x + hr * 1.05f, h.y - hr * 0.2f)
        quadraticTo(h.x + hr * 1.15f, b.shoulderY + u * 0.15f, h.x + hr * 0.55f, b.shoulderY + u * 0.4f)
        quadraticTo(h.x, b.shoulderY + u * 0.48f, h.x - hr * 0.55f, b.shoulderY + u * 0.4f)
        quadraticTo(h.x - hr * 1.15f, b.shoulderY + u * 0.15f, h.x - hr * 1.05f, h.y - hr * 0.2f)
        close()
    }, hair)

    // Lange, schlanke Beine in Overknee-Stiefeln mit Strumpfbändern
    artFemaleLegs(b, skin, wine.shade(0.35f), bootHeight = 0.6f, spacing = 0.13f, thigh = 0.11f, ankle = 0.055f, bootWidth = 0.08f)
    for (side in listOf(-1f, 1f)) {
        val x = c.x + side * u * 0.13f
        val bootTop = b.footY - u * 0.6f
        drawLine(pink.shade(0.1f), Offset(x - u * 0.085f, bootTop + u * 0.02f), Offset(x + u * 0.085f, bootTop + u * 0.02f), u * 0.035f, StrokeCap.Round)
        drawLine(HornDark, Offset(x + side * u * 0.03f, b.hipY + u * 0.06f), Offset(x + side * u * 0.02f, bootTop), u * 0.018f)
        drawCircle(pink, u * 0.02f, Offset(x + side * u * 0.02f, bootTop))
    }
    // Arme in langen Handschuhen
    for (hd in listOf(b.hand, b.offHand)) {
        val s = if (hd.x > c.x) 1f else -1f
        val shoulder = Offset(c.x + s * u * 0.18f, b.shoulderY + u * 0.08f)
        drawLine(skin.shade(0.05f), shoulder, hd, u * 0.11f, StrokeCap.Round)
        drawLine(wine.shade(0.35f), shoulder + (hd - shoulder) * 0.4f, hd, u * 0.13f, StrokeCap.Round)
        drawLine(pink.shade(0.1f), shoulder + (hd - shoulder) * 0.4f + Offset(-u * 0.07f, 0f), shoulder + (hd - shoulder) * 0.4f + Offset(u * 0.07f, 0f), u * 0.03f, StrokeCap.Round)
    }

    // Weibliche Kurven mit natürlichen Proportionen, freier Bauch
    val waistY = b.hipY - u * 0.12f
    val torso = femaleTorso(b, waistY, b.hipY + u * 0.06f, hip = 0.27f, waist = 0.15f, shoulder = 0.2f, bust = 0.27f)
    drawPath(torso, skin)
    drawCircle(skin.shade(0.28f), u * 0.018f, Offset(c.x, b.hipY - u * 0.15f))
    // Transparente Seitenschleier
    for (side in listOf(-1f, 1f)) {
        drawPath(Path().apply {
            moveTo(c.x + side * u * 0.22f, b.hipY - u * 0.01f)
            quadraticTo(c.x + side * u * 0.32f, b.hipY + u * 0.2f, c.x + side * u * 0.26f, b.hipY + u * 0.38f)
            lineTo(c.x + side * u * 0.14f, b.hipY + u * 0.26f)
            close()
        }, pink.copy(alpha = 0.3f))
    }
    // Knappes V-Höschen, auf der Hüfte sitzend
    drawPath(Path().apply {
        moveTo(c.x - u * 0.27f, b.hipY - u * 0.01f)
        quadraticTo(c.x, b.hipY + u * 0.03f, c.x + u * 0.27f, b.hipY - u * 0.01f)
        lineTo(c.x + u * 0.25f, b.hipY + u * 0.05f)
        quadraticTo(c.x + u * 0.1f, b.hipY + u * 0.1f, c.x + u * 0.05f, b.hipY + u * 0.2f)
        lineTo(c.x - u * 0.05f, b.hipY + u * 0.2f)
        quadraticTo(c.x - u * 0.1f, b.hipY + u * 0.1f, c.x - u * 0.25f, b.hipY + u * 0.05f)
        close()
    }, wine.shade(0.15f))
    drawPath(Path().apply {
        moveTo(c.x - u * 0.27f, b.hipY - u * 0.01f)
        quadraticTo(c.x, b.hipY + u * 0.03f, c.x + u * 0.27f, b.hipY - u * 0.01f)
    }, pink.shade(0.1f), style = Stroke(u * 0.025f))
    // Hüftkettchen mit Herz
    drawArc(ArtGold, 20f, 140f, false, Offset(c.x - u * 0.24f, b.hipY - u * 0.12f), Size(u * 0.48f, u * 0.14f), style = Stroke(u * 0.016f))
    drawCircle(pink, u * 0.028f, Offset(c.x, b.hipY + u * 0.02f))
    // Bustier mit herzförmigen Körbchen und Schnürung dazwischen
    for (side in listOf(-1f, 1f)) {
        // Rundung der Brust: weicher Schatten unter und neben dem Körbchen
        val cup = Offset(c.x + side * u * 0.13f, b.shoulderY + u * 0.24f)
        drawArc(skin.shade(0.18f), 20f, 140f, false, Offset(cup.x - u * 0.12f, cup.y - u * 0.06f), Size(u * 0.24f, u * 0.2f), style = Stroke(u * 0.022f, cap = StrokeCap.Round))
        // Knappe, gerundete Dreieck-Körbchen
        val cupPath = Path().apply {
            moveTo(c.x + side * u * 0.025f, cup.y + u * 0.07f)
            quadraticTo(c.x + side * u * 0.06f, cup.y - u * 0.06f, c.x + side * u * 0.15f, cup.y - u * 0.12f)
            quadraticTo(c.x + side * u * 0.25f, cup.y - u * 0.02f, c.x + side * u * 0.24f, cup.y + u * 0.08f)
            quadraticTo(c.x + side * u * 0.14f, cup.y + u * 0.13f, c.x + side * u * 0.025f, cup.y + u * 0.07f)
            close()
        }
        drawPath(cupPath, wine.shade(0.1f))
        // Glanzlicht für Volumen
        drawOval(pink.copy(alpha = 0.35f), Offset(cup.x - u * 0.045f + side * u * 0.01f, cup.y - u * 0.05f), Size(u * 0.07f, u * 0.05f))
        drawArc(pink.copy(alpha = 0.55f), if (side < 0) 210f else 270f, 60f, false, cup - Offset(u * 0.06f, u * 0.06f), Size(u * 0.12f, u * 0.1f), style = Stroke(u * 0.02f, cap = StrokeCap.Round))
        drawPath(cupPath, pink.shade(0.1f), style = Stroke(u * 0.02f))
        // Nackenträger und Band zwischen den Körbchen
        drawLine(HornDark, Offset(c.x + side * u * 0.15f, cup.y - u * 0.12f), Offset(c.x + side * u * 0.1f, b.shoulderY - u * 0.05f), u * 0.016f)
        drawLine(HornDark, Offset(c.x + side * u * 0.24f, cup.y + u * 0.08f), Offset(c.x + side * u * 0.27f, cup.y + u * 0.1f), u * 0.016f)
    }
    // Dekolleté-Linie
    drawPath(Path().apply {
        moveTo(c.x - u * 0.045f, b.shoulderY + u * 0.15f)
        quadraticTo(c.x, b.shoulderY + u * 0.29f, c.x + u * 0.045f, b.shoulderY + u * 0.15f)
    }, skin.shade(0.1f), style = Stroke(u * 0.016f, cap = StrokeCap.Round))
    drawCircle(pink, u * 0.025f, Offset(c.x, b.shoulderY + u * 0.31f))
    // Halsband mit Herzanhänger
    drawLine(HornDark, Offset(c.x - u * 0.1f, b.shoulderY - u * 0.06f), Offset(c.x + u * 0.1f, b.shoulderY - u * 0.06f), u * 0.05f)
    drawCircle(pink, u * 0.035f, Offset(c.x, b.shoulderY - u * 0.01f))

    // Flirtendes Gesicht: ein Auge zwinkert, das andere mit geschwungenen Wimpern, verschmitztes Lächeln
    artHead(b, skin)
    for (side in listOf(-1f, 1f)) {
        val e = Offset(h.x + side * hr * 0.36f + dir * hr * 0.06f, h.y + hr * 0.08f)
        val winking = side == -dir
        if (winking) {
            // Geschlossenes, lachendes Auge mit Wimpernschwung
            drawArc(ArtInk, 200f, 140f, false, e - Offset(hr * 0.16f, hr * 0.1f), Size(hr * 0.32f, hr * 0.22f), style = Stroke(hr * 0.06f, cap = StrokeCap.Round))
            drawLine(ArtInk, e + Offset(side * hr * 0.15f, -hr * 0.04f), e + Offset(side * hr * 0.28f, -hr * 0.12f), hr * 0.05f, StrokeCap.Round)
        } else {
            drawCircle(Color.White, hr * 0.16f, e)
            drawCircle(Color(0xFFBE185D), hr * 0.11f, e + Offset(dir * hr * 0.04f, hr * 0.01f))
            drawCircle(Color(0xFF500724), hr * 0.05f, e + Offset(dir * hr * 0.05f, hr * 0.01f))
            drawCircle(Color.White, hr * 0.04f, e + Offset(dir * hr * 0.01f - hr * 0.03f, -hr * 0.05f))
            drawArc(ArtInk, 195f, 150f, false, e - Offset(hr * 0.18f, hr * 0.18f), Size(hr * 0.36f, hr * 0.36f), style = Stroke(hr * 0.07f, cap = StrokeCap.Round))
            for (k in 0..1) drawLine(ArtInk, e + Offset(side * hr * (0.14f - k * 0.05f), -hr * (0.12f + k * 0.04f)), e + Offset(side * hr * (0.27f - k * 0.04f), -hr * (0.22f + k * 0.06f)), hr * 0.04f, StrokeCap.Round)
        }
        // Geschwungene Brauen, die offene Seite keck hochgezogen
        val lift = if (winking) 0f else hr * 0.06f
        drawArc(hair, 200f, 140f, false, e - Offset(hr * 0.17f, hr * 0.36f + lift), Size(hr * 0.34f, hr * 0.18f), style = Stroke(hr * 0.05f, cap = StrokeCap.Round))
    }
    drawCircle(Color(0xFFFB7185).copy(alpha = 0.35f), hr * 0.13f, Offset(h.x - dir * hr * 0.5f, h.y + hr * 0.36f))
    // Schiefes, verschmitztes Lächeln
    val mouth = Offset(h.x + dir * hr * 0.08f, h.y + hr * 0.5f)
    val smile = Path().apply {
        moveTo(mouth.x - hr * 0.16f, mouth.y - hr * 0.01f)
        quadraticTo(mouth.x, mouth.y + hr * 0.12f, mouth.x + dir * hr * 0.18f, mouth.y - hr * 0.07f)
        quadraticTo(mouth.x, mouth.y + hr * 0.02f, mouth.x - hr * 0.16f, mouth.y - hr * 0.01f)
        close()
    }
    drawPath(smile, Color(0xFFBE185D))
    drawPath(smile, Color(0xFF831843), style = Stroke(hr * 0.025f))
    drawCircle(Color(0xFF3B1F2E), hr * 0.035f, Offset(h.x + dir * hr * 0.4f, h.y + hr * 0.44f))
    drawPath(Path().apply {
        moveTo(h.x - hr * 1.02f, h.y + hr * 0.5f)
        lineTo(h.x - hr * 1.04f, h.y - hr * 0.1f)
        cubicTo(h.x - hr * 1.14f, h.y - hr * 1.42f, h.x + hr * 1.14f, h.y - hr * 1.42f, h.x + hr * 1.04f, h.y - hr * 0.1f)
        lineTo(h.x + hr * 1.02f, h.y + hr * 0.5f)
        quadraticTo(h.x + hr * 0.75f, h.y - hr * 0.3f, h.x - dir * hr * 0.1f, h.y - hr * 0.5f)
        quadraticTo(h.x - dir * hr * 0.6f, h.y - hr * 0.35f, h.x - hr * 1.02f, h.y + hr * 0.5f)
        close()
    }, hair)
    drawArc(Color(0xFF4C3A5C), 210f, 50f, false, h + Offset(-hr * 0.75f, -hr * 0.98f), Size(hr * 1.5f, hr * 0.9f), style = Stroke(hr * 0.08f, cap = StrokeCap.Round))
    demonHorns(b, Color(0xFF8E3A5E), 1.05f)

    // Stab mit Herz-Kristall
    val hand = b.hand
    val top = hand + Offset(dir * u * 0.04f, -u * 1.3f)
    drawLine(HornDark, hand + Offset(-dir * u * 0.02f, u * 0.7f), top + Offset(0f, u * 0.08f), u * 0.07f, StrokeCap.Round)
    for (side in listOf(-1f, 1f)) drawPath(Path().apply {
        moveTo(top.x, top.y + u * 0.1f)
        quadraticTo(top.x + side * u * 0.28f, top.y + u * 0.05f, top.x + side * u * 0.2f, top.y - u * 0.25f)
    }, HornDark, style = Stroke(u * 0.05f, cap = StrokeCap.Round))
    drawCircle(pink.copy(alpha = 0.35f * p), u * 0.26f, top)
    val heart = Path().apply {
        moveTo(top.x, top.y + u * 0.12f)
        cubicTo(top.x - u * 0.2f, top.y - u * 0.02f, top.x - u * 0.1f, top.y - u * 0.16f, top.x, top.y - u * 0.06f)
        cubicTo(top.x + u * 0.1f, top.y - u * 0.16f, top.x + u * 0.2f, top.y - u * 0.02f, top.x, top.y + u * 0.12f)
        close()
    }
    drawPath(heart, pink)
    drawCircle(Color.White.copy(alpha = 0.8f), u * 0.025f, top + Offset(-u * 0.05f, -u * 0.05f))
    // Kleine Herzflamme über der zweiten Hand
    val f = b.offHand + Offset(0f, -u * 0.28f)
    drawCircle(pink.copy(alpha = 0.3f * p), u * 0.16f, f)
    drawCircle(pink.bright(0.3f), u * 0.06f, f)
    artHand(b, hand, skin.shade(0.1f), 0.09f)
    artHand(b, b.offHand, skin.shade(0.1f), 0.09f)
}

// --- Dämonenjäger ---

private fun DrawScope.drawDemonHunter(b: ArtBody) {
    val u = b.u
    val c = b.c
    val dir = b.dir
    val time = b.time
    val skin = Color(0xFF9F3A3A)
    val leather = Color(0xFF3F3F46)
    val red = Color(0xFFEF4444)
    val fel = Color(0xFF4ADE80)
    val h = b.head
    val hr = b.headR
    val p = b.pulse(5f)

    // Langes schwarzes Haar mit Zopf, wehender Mantelschoß
    val sway = sin(time * 3f) * u * 0.05f
    drawPath(Path().apply {
        moveTo(c.x - u * 0.38f, b.shoulderY)
        lineTo(c.x + u * 0.38f, b.shoulderY)
        lineTo(c.x + u * 0.6f - dir * sway, b.footY - u * 0.25f)
        lineTo(c.x + u * 0.2f - dir * sway, b.footY - u * 0.35f)
        lineTo(c.x - u * 0.2f - dir * sway, b.footY - u * 0.3f)
        lineTo(c.x - u * 0.6f - dir * sway, b.footY - u * 0.22f)
        close()
    }, leather.shade(0.35f))
    // Langer Zopf hinter dem Rücken
    val braidSway = sin(time * 3f) * u * 0.04f
    var bp = Offset(h.x - dir * hr * 0.5f, h.y + hr * 0.5f)
    for (k in 0..5) {
        drawOval(Color(0xFF111111).bright(0.06f * (k % 2)), bp - Offset(u * 0.1f, u * 0.08f), Size(u * 0.2f, u * 0.17f))
        bp += Offset(-dir * u * 0.04f + braidSway * 0.3f, u * 0.13f)
    }
    drawLine(Color(0xFFEF4444), bp - Offset(u * 0.07f, u * 0.1f), bp + Offset(u * 0.07f, -u * 0.1f), u * 0.05f, StrokeCap.Round)
    demonTail(b, skin.shade(0.25f), 0.85f)

    // Köcher
    val qx = c.x - dir * u * 0.3f
    for (i in 0..2) {
        val ax = qx - dir * u * (0.02f + i * 0.08f)
        val tip = Offset(ax - dir * u * 0.1f, b.shoulderY - u * 0.5f + i * u * 0.04f)
        drawLine(Color(0xFFD6D3D1), Offset(ax, b.shoulderY), tip, u * 0.035f)
        drawPath(triangle3dm(tip + Offset(0f, u * 0.12f), u * 0.06f, u * 0.12f), red)
    }
    drawRoundRect(ArtLeather.shade(0.3f), Offset(qx - dir * u * 0.12f - u * 0.11f, b.shoulderY - u * 0.22f), Size(u * 0.22f, u * 0.6f), CornerRadius(u * 0.07f))

    artLegs(b, leather.shade(0.25f), Color(0xFF1C1917), bootHeight = 0.45f, knee = Color(0xFF52525B))
    artArm(b, b.hand, skin.shade(0.05f), 0.19f)
    artArm(b, b.offHand, skin.shade(0.05f), 0.19f)
    for (hd in listOf(b.hand, b.offHand)) {
        val s = if (hd.x > c.x) 1f else -1f
        val sh = Offset(c.x + s * u * 0.4f, b.shoulderY + u * 0.1f)
        drawLine(leather, hd - (hd - sh) * 0.05f, hd - (hd - sh) * 0.45f, u * 0.22f, StrokeCap.Round)
        for (k in 0..1) drawPath(triangle3dm(hd - (hd - sh) * (0.2f + k * 0.15f) + Offset(-s * u * 0.08f, 0f), u * 0.035f, u * 0.1f), Color(0xFF9CA3AF))
    }
    // Nackter Oberkörper mit Fel-Tätowierungen, Kreuzgurte
    val top = Offset(c.x - u * 0.44f, b.shoulderY - u * 0.06f)
    val torso = Size(u * 0.88f, b.hipY - b.shoulderY + u * 0.1f)
    drawRoundRect(skin, top, torso, CornerRadius(u * 0.22f))
    drawRoundRect(skin.shade(0.18f), top + Offset(torso.width * 0.55f, 0f), Size(torso.width * 0.45f, torso.height), CornerRadius(u * 0.22f))
    for (side in listOf(-1f, 1f)) drawArc(skin.shade(0.25f), 20f, 140f, false, Offset(c.x + side * u * 0.17f - u * 0.17f, b.shoulderY + u * 0.04f), Size(u * 0.34f, u * 0.22f), style = Stroke(u * 0.03f))
    for (k in 0..2) drawLine(skin.shade(0.22f), Offset(c.x - u * 0.12f, b.shoulderY + u * (0.33f + k * 0.1f)), Offset(c.x + u * 0.12f, b.shoulderY + u * (0.33f + k * 0.1f)), u * 0.025f)
    for (side in listOf(-1f, 1f)) {
        drawPath(Path().apply {
            moveTo(c.x + side * u * 0.3f, b.shoulderY + u * 0.06f)
            lineTo(c.x + side * u * 0.22f, b.shoulderY + u * 0.22f)
            lineTo(c.x + side * u * 0.32f, b.shoulderY + u * 0.36f)
            lineTo(c.x + side * u * 0.24f, b.hipY - u * 0.1f)
        }, fel.copy(alpha = 0.5f + 0.4f * p), style = Stroke(u * 0.03f, cap = StrokeCap.Round))
    }
    drawLine(ArtLeather.shade(0.3f), Offset(c.x - dir * u * 0.42f, b.shoulderY), Offset(c.x + dir * u * 0.4f, b.hipY - u * 0.05f), u * 0.08f)
    artBelt(b, b.hipY - u * 0.02f, 0.44f, Color(0xFF1C1917), Color(0xFF9CA3AF))
    for (side in listOf(-1f, 1f)) {
        artPauldron(b, side, leather.bright(0.1f), red.shade(0.2f))
        drawPath(triangle3dm(Offset(c.x + side * u * 0.44f, b.shoulderY - u * 0.08f), u * 0.05f, u * 0.18f), Color(0xFF9CA3AF))
    }

    // Kopf mit Augenbinde, Hörnern und langem Haar
    artHead(b, skin)
    drawLine(Color(0xFF1C1917), Offset(h.x - hr * 1.02f, h.y), Offset(h.x + hr * 1.02f, h.y - hr * 0.04f), hr * 0.32f)
    for (side in listOf(-1f, 1f)) {
        val e = Offset(h.x + side * hr * 0.36f + dir * hr * 0.06f, h.y - hr * 0.02f)
        drawCircle(fel.copy(alpha = 0.4f * p), hr * 0.2f, e)
        drawLine(fel, e - Offset(hr * 0.1f, 0f), e + Offset(hr * 0.1f, 0f), hr * 0.06f, StrokeCap.Round)
    }
    drawLine(skin.shade(0.3f), Offset(h.x - hr * 0.15f + dir * hr * 0.06f, h.y + hr * 0.52f), Offset(h.x + hr * 0.15f + dir * hr * 0.06f, h.y + hr * 0.5f), hr * 0.06f, StrokeCap.Round)
    drawLine(skin.shade(0.25f), Offset(h.x + dir * hr * 0.12f, h.y + hr * 0.2f), Offset(h.x + dir * hr * 0.18f, h.y + hr * 0.35f), hr * 0.05f, StrokeCap.Round)
    // Haar: glatt zurück mit Seitensträhnen
    drawPath(Path().apply {
        moveTo(h.x - hr * 1.04f, h.y - hr * 0.15f)
        cubicTo(h.x - hr * 1.14f, h.y - hr * 1.42f, h.x + hr * 1.14f, h.y - hr * 1.42f, h.x + hr * 1.04f, h.y - hr * 0.15f)
        quadraticTo(h.x + hr * 0.6f, h.y - hr * 0.7f, h.x, h.y - hr * 0.62f)
        quadraticTo(h.x - hr * 0.6f, h.y - hr * 0.7f, h.x - hr * 1.04f, h.y - hr * 0.15f)
        close()
    }, Color(0xFF111111))
    demonHorns(b, HornDark, 1.05f)

    // Gezackter Dämonenbogen
    val hand = b.hand
    val grip = hand + Offset(-dir * u * 0.08f, 0f)
    val bt = grip + Offset(-dir * u * 0.05f, -u * 0.85f)
    val bb = grip + Offset(-dir * u * 0.05f, u * 0.85f)
    drawLine(red.copy(alpha = 0.8f), bt + Offset(dir * u * 0.04f, 0f), bb + Offset(dir * u * 0.04f, 0f), u * 0.02f)
    val bow = Path().apply {
        moveTo(bt.x, bt.y)
        quadraticTo(grip.x + dir * u * 0.45f, grip.y - u * 0.5f, grip.x + dir * u * 0.12f, grip.y)
        quadraticTo(grip.x + dir * u * 0.45f, grip.y + u * 0.5f, bb.x, bb.y)
    }
    drawPath(bow, Color(0xFF1C1917), style = Stroke(u * 0.1f, cap = StrokeCap.Round))
    drawPath(bow, red.shade(0.2f), style = Stroke(u * 0.03f, cap = StrokeCap.Round))
    for (t in listOf(-0.5f, 0.5f)) drawPath(triangle3dm(grip + Offset(dir * u * 0.38f, t * u * 0.9f), u * 0.04f, u * 0.14f), red)
    drawLine(Color(0xFF52525B), grip + Offset(dir * u * 0.12f, -u * 0.1f), grip + Offset(dir * u * 0.12f, u * 0.1f), u * 0.11f)
    artHand(b, hand, skin.shade(0.1f), 0.1f)
    artHand(b, b.offHand, skin.shade(0.1f), 0.1f)
}

// --- Nachtpirscherin ---

private fun DrawScope.drawNightstalker(b: ArtBody) {
    val u = b.u
    val c = b.c
    val dir = b.dir
    val time = b.time
    val skin = Color(0xFFB4536A)
    val dark = Color(0xFF2A0A12)
    val crimson = Color(0xFFE11D48)
    val hair = Color(0xFFF1F5F9)
    val h = b.head
    val hr = b.headR
    val p = b.pulse(4.5f)

    demonTail(b, skin.shade(0.2f), 1f)
    // Lange weiße Mähne, im Wind
    val sway = sin(time * 3f) * u * 0.05f
    drawPath(Path().apply {
        moveTo(h.x - hr * 1.05f, h.y - hr * 0.2f)
        cubicTo(h.x - hr * 1.15f, h.y - hr * 1.5f, h.x + hr * 1.15f, h.y - hr * 1.5f, h.x + hr * 1.05f, h.y - hr * 0.2f)
        quadraticTo(h.x + hr * 1.1f - dir * sway, b.shoulderY + u * 0.4f, h.x - dir * hr * 0.6f - dir * sway * 2f, b.hipY + u * 0.1f)
        quadraticTo(h.x - dir * hr * 1.4f, b.shoulderY + u * 0.3f, h.x - hr * 1.05f, h.y - hr * 0.2f)
        close()
    }, hair.shade(0.12f))

    // Lange Beine in Overknee-Stiefeln mit Klauenspitzen
    artFemaleLegs(b, skin, dark, bootHeight = 0.62f)
    for (side in listOf(-1f, 1f)) {
        val x = c.x + side * u * 0.17f
        drawPath(triangle3dm(Offset(x, b.footY - u * 0.62f), u * 0.06f, u * 0.14f), crimson.shade(0.3f))
    }
    // Arme mit Krallenhandschuhen
    for (hd in listOf(b.hand, b.offHand)) {
        val s = if (hd.x > c.x) 1f else -1f
        val shoulder = Offset(c.x + s * u * 0.22f, b.shoulderY + u * 0.08f)
        drawLine(skin.shade(0.05f), shoulder, hd, u * 0.12f, StrokeCap.Round)
        drawLine(dark, shoulder + (hd - shoulder) * 0.5f, hd, u * 0.15f, StrokeCap.Round)
    }

    // Knappes Rüstzeug: Brustplatte, freie Taille, geschlitzter Lendenschurz
    val waistY = b.hipY - u * 0.1f
    drawPath(femaleTorso(b, waistY, b.hipY + u * 0.02f, hip = 0.31f, waist = 0.14f, shoulder = 0.21f, bust = 0.29f), skin)
    for (side in listOf(-1f, 1f)) {
        drawPath(Path().apply {
            moveTo(c.x + side * u * 0.03f, b.hipY - u * 0.04f)
            lineTo(c.x + side * u * 0.3f, b.hipY - u * 0.04f)
            lineTo(c.x + side * u * 0.26f, b.hipY + u * 0.36f)
            lineTo(c.x + side * u * 0.08f, b.hipY + u * 0.3f)
            close()
        }, dark)
    }
    drawLine(crimson.shade(0.2f), Offset(c.x - u * 0.3f, b.hipY - u * 0.04f), Offset(c.x + u * 0.3f, b.hipY - u * 0.04f), u * 0.06f)
    drawCircle(ArtBone, u * 0.04f, Offset(c.x, b.hipY - u * 0.04f))
    val chest = Path().apply {
        moveTo(c.x - u * 0.27f, b.shoulderY + u * 0.1f)
        quadraticTo(c.x - u * 0.13f, b.shoulderY + u * 0.03f, c.x, b.shoulderY + u * 0.18f)
        quadraticTo(c.x + u * 0.13f, b.shoulderY + u * 0.03f, c.x + u * 0.27f, b.shoulderY + u * 0.1f)
        quadraticTo(c.x + u * 0.25f, b.shoulderY + u * 0.33f, c.x, b.shoulderY + u * 0.38f)
        quadraticTo(c.x - u * 0.25f, b.shoulderY + u * 0.33f, c.x - u * 0.27f, b.shoulderY + u * 0.1f)
        close()
    }
    drawPath(chest, dark)
    drawPath(chest, crimson.shade(0.1f), style = Stroke(u * 0.025f))
    for (side in listOf(-1f, 1f)) {
        drawArc(crimson.copy(alpha = 0.6f), if (side < 0) 210f else 270f, 60f, false, Offset(c.x + side * u * 0.13f - u * 0.09f, b.shoulderY + u * 0.13f), Size(u * 0.18f, u * 0.14f), style = Stroke(u * 0.02f, cap = StrokeCap.Round))
        // Gezackte Schulterklingen
        artPauldron(b, side * 0.75f, dark.bright(0.08f), crimson.shade(0.2f), 0.8f)
        drawPath(triangle3dm(Offset(c.x + side * u * 0.36f, b.shoulderY - u * 0.06f), u * 0.045f, u * 0.2f), crimson.shade(0.25f))
    }

    // Gesicht mit glühenden Augen, Pony, nach hinten geschwungene Hörner
    artHead(b, skin)
    for (side in listOf(-1f, 1f)) {
        val e = Offset(h.x + side * hr * 0.36f + dir * hr * 0.06f, h.y + hr * 0.08f)
        drawArc(ArtInk, if (side > 0) 200f else 230f, 110f, false, e - Offset(hr * 0.18f, hr * 0.18f), Size(hr * 0.36f, hr * 0.36f), style = Stroke(hr * 0.06f, cap = StrokeCap.Round))
        drawLine(ArtInk, e + Offset(side * hr * 0.13f, -hr * 0.1f), e + Offset(side * hr * 0.26f, -hr * 0.2f), hr * 0.05f, StrokeCap.Round)
        drawCircle(crimson.copy(alpha = 0.4f * p), hr * 0.2f, e)
        drawCircle(Color(0xFFFDE68A), hr * 0.13f, e)
        drawLine(Color(0xFF450A0A), e + Offset(dir * hr * 0.03f, -hr * 0.1f), e + Offset(dir * hr * 0.03f, hr * 0.1f), hr * 0.05f, StrokeCap.Round)
        drawLine(hair.shade(0.4f), e + Offset(-hr * 0.15f, -hr * (0.27f - side * 0.05f)), e + Offset(hr * 0.15f, -hr * (0.27f + side * 0.05f)), hr * 0.06f, StrokeCap.Round)
    }
    val mouth = Offset(h.x + dir * hr * 0.06f, h.y + hr * 0.5f)
    drawArc(Color(0xFF4C0519), 0f, 180f, true, mouth - Offset(hr * 0.14f, hr * 0.08f), Size(hr * 0.28f, hr * 0.16f))
    drawPath(Path().apply {
        moveTo(mouth.x + dir * hr * 0.05f, mouth.y)
        lineTo(mouth.x + dir * hr * 0.08f, mouth.y + hr * 0.1f)
        lineTo(mouth.x + dir * hr * 0.11f, mouth.y)
        close()
    }, Color.White)
    drawPath(Path().apply {
        moveTo(h.x - hr * 1.04f, h.y + hr * 0.2f)
        cubicTo(h.x - hr * 1.14f, h.y - hr * 1.42f, h.x + hr * 1.14f, h.y - hr * 1.42f, h.x + hr * 1.04f, h.y + hr * 0.2f)
        quadraticTo(h.x + hr * 0.8f, h.y - hr * 0.35f, h.x + dir * hr * 0.25f, h.y - hr * 0.55f)
        lineTo(h.x - dir * hr * 0.05f, h.y - hr * 0.35f)
        quadraticTo(h.x - dir * hr * 0.6f, h.y - hr * 0.55f, h.x - hr * 1.04f, h.y + hr * 0.2f)
        close()
    }, hair)
    drawArc(hair.shade(0.15f), 220f, 60f, false, h + Offset(-hr * 0.7f, -hr * 0.95f), Size(hr * 1.4f, hr * 0.9f), style = Stroke(hr * 0.05f, cap = StrokeCap.Round))
    demonHorns(b, Color(0xFF3F0A16), 0.95f, back = true)

    // Sense mit Blutklinge
    val hand = b.hand
    val top = hand + Offset(dir * u * 0.18f, -u * 1.35f)
    drawLine(Color(0xFF1C1917), hand + Offset(-dir * u * 0.1f, u * 0.6f), top, u * 0.08f, StrokeCap.Round)
    for (k in 0..2) drawLine(crimson.shade(0.3f), hand + Offset(-u * 0.05f, -u * (0.1f + k * 0.4f)), hand + Offset(u * 0.05f, -u * (0.14f + k * 0.4f)), u * 0.03f)
    val blade = Path().apply {
        moveTo(top.x, top.y - u * 0.04f)
        quadraticTo(top.x + dir * u * 0.5f, top.y - u * 0.25f, top.x + dir * u * 0.8f, top.y + u * 0.42f)
        quadraticTo(top.x + dir * u * 0.5f, top.y + u * 0.02f, top.x, top.y + u * 0.14f)
        close()
    }
    drawPath(blade, Color(0xFFD1D5DB))
    drawPath(Path().apply {
        moveTo(top.x, top.y + u * 0.12f)
        quadraticTo(top.x + dir * u * 0.5f, top.y + u * 0.0f, top.x + dir * u * 0.8f, top.y + u * 0.42f)
    }, crimson.copy(alpha = 0.6f + 0.4f * p), style = Stroke(u * 0.04f, cap = StrokeCap.Round))
    drawCircle(crimson, u * 0.06f, top)
    artHand(b, hand, dark.bright(0.1f), 0.09f)
    artHand(b, b.offHand, dark.bright(0.1f), 0.09f)
}

// --- Klingendämon ---

private fun DrawScope.drawBladeDemon(b: ArtBody) {
    val u = b.u
    val c = b.c
    val dir = b.dir
    val skin = Color(0xFF7F1D1D)
    val glow = Color(0xFFF87171)
    val steel = Color(0xFFD1D5DB)
    val p = b.pulse(3f)

    drawCircle(glow.copy(alpha = 0.1f + 0.06f * p), u * 1.35f, c + Offset(0f, -u * 0.2f))
    batWings(b, Color(0xFF450A0A), skin.shade(0.3f), 1.1f)
    demonTail(b, skin.shade(0.15f), 1.1f)

    // Muskulöse Beine mit Hufen
    for (side in listOf(-1f, 1f)) {
        val hip = Offset(c.x + side * u * 0.24f, b.hipY)
        val knee = Offset(c.x + side * u * 0.32f, (b.hipY + b.footY) / 2f - u * 0.05f)
        val hock = Offset(c.x + side * u * 0.22f, b.footY - u * 0.25f)
        drawLine(skin.shade(0.1f), hip, knee, u * 0.3f, StrokeCap.Round)
        drawLine(skin.shade(0.15f), knee, hock, u * 0.2f, StrokeCap.Round)
        drawLine(skin.shade(0.15f), hock, Offset(hock.x, b.footY - u * 0.08f), u * 0.16f, StrokeCap.Round)
        drawRoundRect(HornDark, Offset(hock.x - u * 0.12f, b.footY - u * 0.14f), Size(u * 0.24f, u * 0.14f), CornerRadius(u * 0.04f))
    }
    // Lendenschurz mit Klingen-Gürtel
    drawPath(Path().apply {
        moveTo(c.x - u * 0.38f, b.hipY - u * 0.05f)
        lineTo(c.x + u * 0.38f, b.hipY - u * 0.05f)
        lineTo(c.x + u * 0.22f, b.hipY + u * 0.38f)
        lineTo(c.x - u * 0.22f, b.hipY + u * 0.38f)
        close()
    }, Color(0xFF1C1917))

    // Arme mit Armklingen
    artArm(b, b.hand, skin.shade(0.05f), 0.26f, 0.45f)
    artArm(b, b.offHand, skin.shade(0.05f), 0.26f, 0.45f)

    // Muskulöser Oberkörper mit glühenden Rissen
    val top = Offset(c.x - u * 0.5f, b.shoulderY - u * 0.08f)
    val torsoPath = Path().apply {
        moveTo(top.x, top.y)
        lineTo(c.x + u * 0.5f, top.y)
        quadraticTo(c.x + u * 0.45f, b.hipY - u * 0.2f, c.x + u * 0.32f, b.hipY)
        lineTo(c.x - u * 0.32f, b.hipY)
        quadraticTo(c.x - u * 0.45f, b.hipY - u * 0.2f, top.x, top.y)
        close()
    }
    drawPath(torsoPath, skin)
    clipPath(torsoPath) { drawRect(skin.shade(0.25f), Offset(c.x + dir * u * 0.15f - (if (dir < 0) u * 0.5f else 0f), top.y), Size(u * 0.5f, u)) }
    for (side in listOf(-1f, 1f)) drawArc(skin.shade(0.35f), 20f, 140f, false, Offset(c.x + side * u * 0.2f - u * 0.2f, b.shoulderY + u * 0.02f), Size(u * 0.4f, u * 0.26f), style = Stroke(u * 0.035f))
    for (k in 0..2) for (side in listOf(-1f, 1f)) drawRoundRect(skin.shade(0.15f), Offset(c.x + side * u * 0.11f - u * 0.08f, b.shoulderY + u * (0.32f + k * 0.12f)), Size(u * 0.16f, u * 0.1f), CornerRadius(u * 0.04f))
    drawPath(Path().apply {
        moveTo(c.x - u * 0.05f, b.shoulderY + u * 0.1f)
        lineTo(c.x + u * 0.08f, b.shoulderY + u * 0.25f)
        lineTo(c.x - u * 0.03f, b.shoulderY + u * 0.35f)
    }, glow.copy(alpha = 0.5f + 0.5f * p), style = Stroke(u * 0.035f, cap = StrokeCap.Round))
    // Knochenstacheln auf den Schultern
    for (side in listOf(-1f, 1f)) for (k in 0..2) {
        drawPath(triangle3dm(Offset(c.x + side * u * (0.28f + k * 0.1f), b.shoulderY - u * 0.06f), u * 0.05f, u * (0.24f - k * 0.05f)), ArtBone.shade(0.15f))
    }

    // Kopf: kantig, Kinnzacken, große Hörner, Fangzähne
    val h = b.head
    val hr = b.headR
    demonHorns(b, HornDark, 1.2f)
    drawPath(Path().apply {
        moveTo(h.x - hr * 0.95f, h.y - hr * 0.3f)
        quadraticTo(h.x, h.y - hr * 1.3f, h.x + hr * 0.95f, h.y - hr * 0.3f)
        lineTo(h.x + hr * 0.75f, h.y + hr * 0.55f)
        lineTo(h.x, h.y + hr * 0.95f)
        lineTo(h.x - hr * 0.75f, h.y + hr * 0.55f)
        close()
    }, skin.bright(0.08f))
    drawLine(skin.shade(0.3f), Offset(h.x - hr * 0.6f, h.y - hr * 0.3f), Offset(h.x + hr * 0.6f, h.y - hr * 0.3f), hr * 0.12f, StrokeCap.Round)
    demonEyes(b, Color(0xFFFDE047), 0.08f)
    fangGrin(b, 0.3f)
    for (side in listOf(-1f, 1f)) drawPath(triangle3dm(Offset(h.x + side * hr * 0.55f, h.y + hr * 0.75f), hr * 0.08f, -hr * 0.2f), HornDark)

    // Armklingen an beiden Unterarmen
    for (hd in listOf(b.hand, b.offHand)) {
        val s = if (hd.x > c.x) 1f else -1f
        val elbow = Offset(c.x + s * u * 0.55f, (b.shoulderY + hd.y) / 2f)
        val bladeTip = hd + Offset(s * u * 0.35f, -u * 0.85f)
        val blade = Path().apply {
            moveTo(elbow.x, elbow.y)
            quadraticTo(hd.x + s * u * 0.35f, hd.y - u * 0.1f, bladeTip.x, bladeTip.y)
            quadraticTo(hd.x + s * u * 0.1f, hd.y - u * 0.25f, hd.x, hd.y)
            close()
        }
        drawPath(blade, steel)
        drawPath(Path().apply {
            moveTo(hd.x + s * u * 0.05f, hd.y - u * 0.05f)
            quadraticTo(hd.x + s * u * 0.32f, hd.y - u * 0.15f, bladeTip.x, bladeTip.y)
        }, glow.copy(alpha = 0.6f + 0.4f * p), style = Stroke(u * 0.03f, cap = StrokeCap.Round))
        artHand(b, hd, skin.shade(0.1f), 0.13f)
        for (k in -1..1) drawLine(HornDark, hd, hd + Offset(s * u * 0.08f + k * u * 0.05f, u * 0.14f), u * 0.035f, StrokeCap.Round)
    }
}

// --- Erzdämon ---

private fun DrawScope.drawArchdemon(b: ArtBody) {
    val u = b.u
    val c = b.c
    val dir = b.dir
    val time = b.time
    val skin = Color(0xFF2A2420)
    val red = Color(0xFFEF4444)
    val fire = Color(0xFFF97316)
    val gold = Color(0xFFD4A017)
    val p = b.pulse(3f)

    drawCircle(red.copy(alpha = 0.12f + 0.06f * p), u * 1.45f, c + Offset(0f, -u * 0.25f))
    batWings(b, Color(0xFF3B0A0A), Color(0xFF1C1917), 1.25f)
    // Mantel mit glühendem Saum
    val sway = sin(time * 2f) * u * 0.05f
    val cape = Path().apply {
        moveTo(c.x - u * 0.5f, b.shoulderY - u * 0.05f)
        lineTo(c.x + u * 0.5f, b.shoulderY - u * 0.05f)
        lineTo(c.x + u * 0.85f - dir * sway, b.footY)
        lineTo(c.x + u * 0.45f - dir * sway, b.footY - u * 0.12f)
        lineTo(c.x - dir * sway, b.footY + u * 0.02f)
        lineTo(c.x - u * 0.45f - dir * sway, b.footY - u * 0.12f)
        lineTo(c.x - u * 0.85f - dir * sway, b.footY)
        close()
    }
    drawPath(cape, Color(0xFF450A0A))
    clipPath(cape) { drawRect(fire.copy(alpha = 0.35f + 0.2f * p), Offset(c.x - u, b.footY - u * 0.2f), Size(u * 2f, u * 0.3f)) }
    demonTail(b, skin.bright(0.1f), 1.2f)

    // Schwere Beine in Plattenstiefeln
    artLegs(b, Color(0xFF1C1917), Color(0xFF292524), width = 1.15f, knee = gold.shade(0.2f), bootHeight = 0.4f, legWidth = 0.3f)
    artArm(b, b.hand, skin.bright(0.08f), 0.27f, 0.46f)
    artArm(b, b.offHand, skin.bright(0.08f), 0.27f, 0.46f)

    // Gepanzerter Oberkörper mit Lavarissen
    val top = Offset(c.x - u * 0.5f, b.shoulderY - u * 0.08f)
    val torso = Size(u, b.hipY - b.shoulderY + u * 0.14f)
    drawRoundRect(skin, top, torso, CornerRadius(u * 0.24f))
    for (side in listOf(-1f, 1f)) drawArc(skin.bright(0.15f), 20f, 140f, false, Offset(c.x + side * u * 0.2f - u * 0.2f, b.shoulderY + u * 0.02f), Size(u * 0.4f, u * 0.26f), style = Stroke(u * 0.035f))
    drawPath(Path().apply {
        moveTo(c.x - u * 0.25f, b.shoulderY + u * 0.3f)
        lineTo(c.x - u * 0.05f, b.shoulderY + u * 0.45f)
        lineTo(c.x + u * 0.12f, b.shoulderY + u * 0.38f)
        lineTo(c.x + u * 0.28f, b.hipY - u * 0.05f)
        moveTo(c.x - u * 0.05f, b.shoulderY + u * 0.45f)
        lineTo(c.x - u * 0.1f, b.hipY - u * 0.02f)
    }, fire.copy(alpha = 0.6f + 0.4f * p), style = Stroke(u * 0.035f, cap = StrokeCap.Round))
    drawCircle(red.copy(alpha = 0.4f * p), u * 0.14f, Offset(c.x, b.shoulderY + u * 0.2f))
    drawCircle(fire, u * 0.06f, Offset(c.x, b.shoulderY + u * 0.2f))
    artBelt(b, b.hipY, 0.5f, Color(0xFF1C1917), gold)
    artSkull(Offset(c.x, b.hipY + u * 0.02f), u * 0.08f)
    // Große Schulterplatten mit Stacheln
    for (side in listOf(-1f, 1f)) {
        artPauldron(b, side * 1.1f, Color(0xFF292524), gold, 1.35f)
        for (k in 0..2) drawPath(triangle3dm(Offset(c.x + side * u * (0.36f + k * 0.13f), b.shoulderY - u * 0.12f), u * 0.05f, u * (0.26f - k * 0.05f)), gold.shade(0.15f))
    }

    // Kopf: dunkel, glühende Augen, gewaltige Hörner, Flammenkrone
    val h = b.head
    val hr = b.headR
    for (i in -2..2) {
        val flick = sin(time * 9f + i * 1.7f) * hr * 0.12f
        val tipY = h.y - hr * (1.45f + (2 - kotlin.math.abs(i)) * 0.25f) + flick
        drawPath(Path().apply {
            moveTo(h.x + i * hr * 0.3f - hr * 0.2f, h.y - hr * 0.8f)
            quadraticTo(h.x + i * hr * 0.32f, tipY + hr * 0.4f, h.x + i * hr * 0.3f + flick * 0.5f, tipY)
            quadraticTo(h.x + i * hr * 0.3f + hr * 0.2f, tipY + hr * 0.4f, h.x + i * hr * 0.3f + hr * 0.2f, h.y - hr * 0.8f)
            close()
        }, (if (kotlin.math.abs(i) == 2) red else fire).copy(alpha = 0.9f))
    }
    demonHorns(b, Color(0xFF0C0A09), 1.35f)
    drawPath(Path().apply {
        moveTo(h.x - hr * 0.95f, h.y - hr * 0.3f)
        quadraticTo(h.x, h.y - hr * 1.3f, h.x + hr * 0.95f, h.y - hr * 0.3f)
        lineTo(h.x + hr * 0.7f, h.y + hr * 0.6f)
        lineTo(h.x, h.y + hr * 1.0f)
        lineTo(h.x - hr * 0.7f, h.y + hr * 0.6f)
        close()
    }, skin.bright(0.12f))
    // Krone aus schwarzem Gold
    clipRect(bottom = h.y - hr * 0.45f) {
        drawPath(Path().apply {
            moveTo(h.x - hr * 0.8f, h.y - hr * 0.45f)
            lineTo(h.x - hr * 0.7f, h.y - hr * 1.0f)
            lineTo(h.x - hr * 0.35f, h.y - hr * 0.75f)
            lineTo(h.x, h.y - hr * 1.2f)
            lineTo(h.x + hr * 0.35f, h.y - hr * 0.75f)
            lineTo(h.x + hr * 0.7f, h.y - hr * 1.0f)
            lineTo(h.x + hr * 0.8f, h.y - hr * 0.45f)
            close()
        }, gold)
    }
    drawCircle(red, hr * 0.1f, Offset(h.x, h.y - hr * 0.62f))
    demonEyes(b, Color(0xFFFDE047), 0.09f, h.y + hr * 0.05f)
    fangGrin(b, 0.32f, Color(0xFF7F1D1D))

    // Große Sense mit Flammenklinge
    val hand = b.hand
    val st = hand + Offset(dir * u * 0.2f, -u * 1.5f)
    drawLine(Color(0xFF0C0A09), hand + Offset(-dir * u * 0.12f, u * 0.65f), st, u * 0.1f, StrokeCap.Round)
    for (k in 0..2) drawLine(gold.shade(0.2f), hand + Offset(-u * 0.06f, -u * (0.2f + k * 0.45f)), hand + Offset(u * 0.06f, -u * (0.24f + k * 0.45f)), u * 0.035f)
    val blade = Path().apply {
        moveTo(st.x, st.y - u * 0.05f)
        quadraticTo(st.x + dir * u * 0.6f, st.y - u * 0.3f, st.x + dir * u * 0.95f, st.y + u * 0.5f)
        quadraticTo(st.x + dir * u * 0.6f, st.y + u * 0.02f, st.x, st.y + u * 0.16f)
        close()
    }
    drawPath(blade, Color(0xFF374151))
    drawPath(Path().apply {
        moveTo(st.x, st.y + u * 0.14f)
        quadraticTo(st.x + dir * u * 0.6f, st.y + u * 0.0f, st.x + dir * u * 0.95f, st.y + u * 0.5f)
    }, fire.copy(alpha = 0.7f + 0.3f * p), style = Stroke(u * 0.06f, cap = StrokeCap.Round))
    drawCircle(red, u * 0.08f, st)
    drawPath(triangle3dm(st + Offset(0f, -u * 0.05f), u * 0.06f, u * 0.25f), gold)
    artHand(b, hand, skin.bright(0.15f), 0.14f)
    artHand(b, b.offHand, skin.bright(0.15f), 0.14f)
    // Flamme über der zweiten Hand
    val f = b.offHand + Offset(0f, -u * 0.3f)
    drawCircle(fire.copy(alpha = 0.3f * p), u * 0.22f, f)
    drawPath(Path().apply {
        moveTo(f.x - u * 0.12f, f.y + u * 0.08f)
        quadraticTo(f.x - u * 0.12f, f.y - u * 0.1f, f.x + sin(time * 12f) * u * 0.04f, f.y - u * 0.28f)
        quadraticTo(f.x + u * 0.12f, f.y - u * 0.1f, f.x + u * 0.12f, f.y + u * 0.08f)
        close()
    }, fire)
    drawCircle(Color(0xFFFDE047), u * 0.05f, f + Offset(0f, u * 0.02f))
}
