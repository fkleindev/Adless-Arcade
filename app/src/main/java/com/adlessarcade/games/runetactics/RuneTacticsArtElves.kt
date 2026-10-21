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
import kotlin.math.cos
import kotlin.math.sin

internal val elfArt: Map<String, DrawScope.(ArtBody) -> Unit> = mapOf(
    "bladedancer" to DrawScope::drawBladedancer,
    "apprentice" to DrawScope::drawApprentice,
    "warden" to DrawScope::drawElfWarden,
    "stormmage" to DrawScope::drawStormmage,
    "queen" to DrawScope::drawElfQueen,
    "huntress" to DrawScope::drawHuntress,
)

private val ElfSkin = Color(0xFFFDE2C4)

/** Lange Spitzohren, die seitlich durch das Haar ragen. */
private fun DrawScope.elfEars(b: ArtBody, skin: Color = ElfSkin) {
    val h = b.head
    val hr = b.headR
    for (side in listOf(-1f, 1f)) {
        val ear = Path().apply {
            moveTo(h.x + side * hr * 0.78f, h.y - hr * 0.05f)
            quadraticTo(h.x + side * hr * 1.2f, h.y - hr * 0.2f, h.x + side * hr * 1.55f, h.y - hr * 0.62f)
            quadraticTo(h.x + side * hr * 1.3f, h.y + hr * 0.05f, h.x + side * hr * 0.85f, h.y + hr * 0.32f)
            close()
        }
        drawPath(ear, skin.shade(0.04f))
        drawLine(skin.shade(0.18f), Offset(h.x + side * hr * 0.92f, h.y + hr * 0.08f), Offset(h.x + side * hr * 1.35f, h.y - hr * 0.4f), hr * 0.06f, StrokeCap.Round)
    }
}

/** Langes Haar hinter Kopf und Rücken bis [bottomY]. */
private fun DrawScope.hairBack(b: ArtBody, color: Color, bottomY: Float, width: Float = 1.12f, sway: Float = 0f) {
    val h = b.head
    val hr = b.headR
    val back = Path().apply {
        moveTo(h.x - hr * width, h.y - hr * 0.2f)
        cubicTo(h.x - hr * (width + 0.1f), h.y - hr * 1.5f, h.x + hr * (width + 0.1f), h.y - hr * 1.5f, h.x + hr * width, h.y - hr * 0.2f)
        quadraticTo(h.x + hr * (width + 0.15f) + sway, (h.y + bottomY) / 2f, h.x + hr * (width - 0.1f) + sway, bottomY)
        quadraticTo(h.x + sway, bottomY + hr * 0.25f, h.x - hr * (width - 0.1f) + sway, bottomY)
        quadraticTo(h.x - hr * (width + 0.15f) + sway, (h.y + bottomY) / 2f, h.x - hr * width, h.y - hr * 0.2f)
        close()
    }
    drawPath(back, color.shade(0.12f))
    drawLine(color.shade(0.25f), Offset(h.x - hr * 0.5f + sway * 0.5f, h.y + hr * 0.6f), Offset(h.x - hr * 0.55f + sway, bottomY - hr * 0.1f), hr * 0.05f, StrokeCap.Round)
    drawLine(color.shade(0.25f), Offset(h.x + hr * 0.5f + sway * 0.5f, h.y + hr * 0.6f), Offset(h.x + hr * 0.55f + sway, bottomY - hr * 0.1f), hr * 0.05f, StrokeCap.Round)
}

/**
 * Haarkappe als eine Form: oben über dem Kopf, mit Mittel- oder Seitenscheitel und seitlichen Strähnen, die das
 * Gesicht rahmen. [topClip] begrenzt die Höhe (unter Hüten/Kronen).
 */
private fun DrawScope.hairCap(b: ArtBody, color: Color, sideLength: Float = 1.1f, parting: Float = 0f, topClip: Float? = null) {
    val h = b.head
    val hr = b.headR
    val p = h.x + parting * hr
    val cap = Path().apply {
        moveTo(h.x - hr * 1.02f, h.y + hr * sideLength)
        lineTo(h.x - hr * 1.04f, h.y - hr * 0.1f)
        cubicTo(h.x - hr * 1.14f, h.y - hr * 1.42f, h.x + hr * 1.14f, h.y - hr * 1.42f, h.x + hr * 1.04f, h.y - hr * 0.1f)
        lineTo(h.x + hr * 1.02f, h.y + hr * sideLength)
        lineTo(h.x + hr * 0.76f, h.y + hr * sideLength * 0.85f)
        quadraticTo(h.x + hr * 0.88f, h.y - hr * 0.2f, p + hr * 0.15f, h.y - hr * 0.58f)
        lineTo(p, h.y - hr * 0.42f)
        lineTo(p - hr * 0.15f, h.y - hr * 0.58f)
        quadraticTo(h.x - hr * 0.88f, h.y - hr * 0.2f, h.x - hr * 0.76f, h.y + hr * sideLength * 0.85f)
        close()
    }
    if (topClip != null) clipRect(top = topClip) { drawPath(cap, color) } else drawPath(cap, color)
    // Dezenter Glanz und Scheitellinie
    val shine = Path().apply {
        moveTo(h.x - hr * 0.75f, h.y - hr * 0.55f)
        quadraticTo(h.x - hr * 0.55f, h.y - hr * 0.95f, h.x - hr * 0.15f, h.y - hr * 1.0f)
    }
    if (topClip == null || topClip < h.y - hr * 0.9f) drawPath(shine, color.bright(0.3f), style = Stroke(hr * 0.08f, cap = StrokeCap.Round))
}

/** Schlanker Arm mit Hand in Hautfarbe. */
private fun DrawScope.elfArm(b: ArtBody, hand: Offset, sleeve: Color, skin: Color = ElfSkin, sleeveLength: Float = 0.45f, shoulderX: Float = 0.28f, width: Float = 0.14f) {
    val s = if (hand.x > b.c.x) 1f else -1f
    val shoulder = Offset(b.c.x + s * b.u * shoulderX, b.shoulderY + b.u * 0.08f)
    drawLine(skin.shade(0.05f), shoulder, hand, b.u * width, StrokeCap.Round)
    if (sleeveLength > 0f) drawLine(sleeve, shoulder, shoulder + (hand - shoulder) * sleeveLength, b.u * 0.19f, StrokeCap.Round)
}

/** Stirnreif mit Edelstein. */
private fun DrawScope.circlet(b: ArtBody, metal: Color, gem: Color) {
    val h = b.head
    val hr = b.headR
    drawArc(metal, 195f, 150f, false, h + Offset(-hr * 0.98f, -hr * 0.82f), Size(hr * 1.96f, hr * 1.2f), style = Stroke(hr * 0.11f, cap = StrokeCap.Round))
    val g = Offset(h.x, h.y - hr * 0.84f)
    val diamond = Path().apply {
        moveTo(g.x, g.y - hr * 0.16f)
        lineTo(g.x + hr * 0.11f, g.y)
        lineTo(g.x, g.y + hr * 0.16f)
        lineTo(g.x - hr * 0.11f, g.y)
        close()
    }
    drawPath(diamond, gem)
    drawPath(diamond, metal, style = Stroke(hr * 0.04f))
    drawCircle(Color.White.copy(alpha = 0.8f), hr * 0.035f, g + Offset(-hr * 0.03f, -hr * 0.05f))
}

/** Blattförmige Klinge von [from] nach [to]. */
private fun DrawScope.leafBlade(from: Offset, to: Offset, halfWidth: Float, color: Color) {
    val d = to - from
    val n = Offset(-d.y, d.x) * (halfWidth / d.getDistance().coerceAtLeast(0.001f))
    val mid = from + d * 0.45f
    val leaf = Path().apply {
        moveTo(from.x, from.y)
        quadraticTo(mid.x + n.x * 2f, mid.y + n.y * 2f, to.x, to.y)
        quadraticTo(mid.x - n.x * 2f, mid.y - n.y * 2f, from.x, from.y)
        close()
    }
    drawPath(leaf, color)
    drawLine(color.shade(0.3f), from, from + d * 0.85f, halfWidth * 0.3f)
    drawLine(Color.White.copy(alpha = 0.5f), from + n * 0.6f + d * 0.15f, from + n * 0.7f + d * 0.6f, halfWidth * 0.25f, StrokeCap.Round)
}

// --- Klingentänzerin ---

private fun DrawScope.drawBladedancer(b: ArtBody) {
    val u = b.u
    val c = b.c
    val dir = b.dir
    val time = b.time
    val green = Color(0xFF15803D)
    val gold = Color(0xFFFDE68A)
    val hair = Color(0xFFF5D67A)
    val h = b.head
    val hr = b.headR
    val sway = sin(time * 3f) * u * 0.05f

    hairBack(b, hair, b.hipY - u * 0.12f, width = 1.0f, sway = -dir * sway)
    // Wehende Seidenbänder hinter dem Körper
    for (k in 0..1) {
        val wave = sin(time * 4f + k * 1.6f) * u * 0.1f
        val ribbon = Path().apply {
            moveTo(c.x - dir * u * 0.15f, b.hipY - u * 0.05f)
            quadraticTo(c.x - dir * u * (0.45f + k * 0.1f), b.hipY - u * 0.05f + wave, c.x - dir * u * (0.62f + k * 0.08f), b.hipY + u * (0.22f + k * 0.12f) + wave)
        }
        drawPath(ribbon, (if (k == 0) gold else green.bright(0.25f)).copy(alpha = 0.9f), style = Stroke(u * 0.06f, cap = StrokeCap.Round))
    }

    // Beine in Wickelsandalen
    artFemaleLegs(b, ElfSkin, Color(0xFF7C5A3A), bootHeight = 0.18f)
    for (side in listOf(-1f, 1f)) {
        val x = c.x + side * u * 0.17f
        for (k in 0..2) {
            val y = b.footY - u * (0.22f + k * 0.12f)
            drawLine(Color(0xFF7C5A3A), Offset(x - u * 0.09f, y), Offset(x + u * 0.09f, y - u * 0.05f), u * 0.03f, StrokeCap.Round)
        }
    }

    // Arme mit goldenen Armreifen
    elfArm(b, b.hand, green, sleeveLength = 0f, shoulderX = 0.2f, width = 0.12f)
    elfArm(b, b.offHand, green, sleeveLength = 0f, shoulderX = 0.2f, width = 0.12f)
    for (hd in listOf(b.hand, b.offHand)) {
        val s = if (hd.x > c.x) 1f else -1f
        val wrist = hd - (hd - Offset(c.x + s * u * 0.2f, b.shoulderY)) * 0.2f
        drawLine(gold, wrist - Offset(u * 0.07f, 0f), wrist + Offset(u * 0.07f, 0f), u * 0.05f, StrokeCap.Round)
    }

    // Geteilter Tänzerinnenrock mit Goldsaum, freier Bauch, Bustier
    // Schmale Schultern und Taille, runde Brust und Hüfte
    val waistY = b.hipY - u * 0.1f
    val torso = femaleTorso(b, waistY, b.hipY + u * 0.02f, hip = 0.31f, waist = 0.14f, shoulder = 0.2f, bust = 0.28f)
    drawPath(torso, ElfSkin)
    clipPath(torso) {
        drawRect(ElfSkin.shade(0.08f), Offset(c.x + dir * u * 0.12f - (if (dir < 0) u * 0.3f else 0f), b.shoulderY), Size(u * 0.3f, b.hipY - b.shoulderY))
    }
    for (side in listOf(-1f, 1f)) {
        val panel = Path().apply {
            moveTo(c.x + side * u * 0.02f, b.hipY - u * 0.04f)
            lineTo(c.x + side * u * 0.3f, b.hipY - u * 0.04f)
            quadraticTo(c.x + side * u * 0.5f, b.hipY + u * 0.3f, c.x + side * u * 0.42f + sway, b.footY - u * 0.3f)
            lineTo(c.x + side * u * 0.12f + sway, b.hipY + u * 0.3f)
            close()
        }
        drawPath(panel, green)
        drawPath(panel, gold, style = Stroke(u * 0.03f))
    }
    drawLine(gold, Offset(c.x - u * 0.3f, b.hipY - u * 0.04f), Offset(c.x + u * 0.3f, b.hipY - u * 0.04f), u * 0.07f)
    for (i in -2..2) drawCircle(gold, u * 0.03f, Offset(c.x + i * u * 0.11f, b.hipY + u * 0.04f + (2 - kotlin.math.abs(i)) * u * 0.015f))
    drawCircle(ElfSkin.shade(0.25f), u * 0.018f, Offset(c.x, b.hipY - u * 0.15f))
    // Bustier aus zwei runden Körbchen mit Goldrand und Nackenträgern
    for (side in listOf(-1f, 1f)) {
        drawLine(gold.shade(0.15f), Offset(c.x + side * u * 0.12f, b.shoulderY + u * 0.1f), Offset(c.x + side * u * 0.08f, b.shoulderY - u * 0.04f), u * 0.025f, StrokeCap.Round)
    }
    for (side in listOf(-1f, 1f)) {
        val cup = Offset(c.x + side * u * 0.13f, b.shoulderY + u * 0.2f)
        val cupPath = Path().apply {
            moveTo(c.x + side * u * 0.01f, cup.y - u * 0.02f)
            quadraticTo(c.x + side * u * 0.1f, cup.y - u * 0.15f, c.x + side * u * 0.26f, cup.y - u * 0.06f)
            quadraticTo(c.x + side * u * 0.32f, cup.y + u * 0.06f, c.x + side * u * 0.22f, cup.y + u * 0.12f)
            quadraticTo(c.x + side * u * 0.1f, cup.y + u * 0.15f, c.x + side * u * 0.01f, cup.y + u * 0.06f)
            close()
        }
        drawPath(cupPath, green)
        drawArc(green.bright(0.25f), if (side < 0) 200f else 260f, 70f, false, cup - Offset(u * 0.09f, u * 0.08f), Size(u * 0.18f, u * 0.14f), style = Stroke(u * 0.025f, cap = StrokeCap.Round))
        drawPath(cupPath, gold, style = Stroke(u * 0.025f))
    }
    drawCircle(gold, u * 0.035f, Offset(c.x, b.shoulderY + u * 0.2f))

    // Gesicht, Haare, Stirnreif, Ohren
    artFemaleFace(b, ElfSkin, hair.shade(0.45f), pupil = Color(0xFF15803D))
    hairCap(b, hair, sideLength = 1.3f, parting = -dir * 0.25f)
    elfEars(b)
    circlet(b, gold.shade(0.15f), Color(0xFF22C55E))

    // Elfensäbel, im Schwung erhoben
    val hand = b.hand
    val guard = hand + Offset(dir * u * 0.02f, -u * 0.15f)
    val tip = hand + Offset(dir * u * 0.55f, -u * 1.0f)
    val saber = Path().apply {
        moveTo(guard.x - u * 0.05f, guard.y)
        quadraticTo(guard.x + dir * u * 0.35f, guard.y - u * 0.35f, tip.x, tip.y)
        quadraticTo(guard.x + dir * u * 0.15f, guard.y - u * 0.5f, guard.x + u * 0.05f, guard.y - u * 0.02f)
        close()
    }
    drawPath(saber, Color(0xFFE5E7EB))
    drawPath(Path().apply {
        moveTo(guard.x, guard.y - u * 0.02f)
        quadraticTo(guard.x + dir * u * 0.22f, guard.y - u * 0.45f, tip.x, tip.y)
    }, Color.White.copy(alpha = 0.7f), style = Stroke(u * 0.025f))
    drawLine(gold.shade(0.2f), guard + Offset(-u * 0.14f, u * 0.04f), guard + Offset(u * 0.14f, -u * 0.04f), u * 0.06f, StrokeCap.Round)
    drawLine(green.shade(0.3f), hand + Offset(0f, u * 0.1f), guard, u * 0.07f, StrokeCap.Round)
    artHand(b, hand, ElfSkin.shade(0.06f), 0.09f)
    artHand(b, b.offHand, ElfSkin.shade(0.06f), 0.09f)
}

// --- Elfen-Lehrling ---

private fun DrawScope.drawApprentice(b: ArtBody) {
    val u = b.u
    val c = b.c
    val dir = b.dir
    val time = b.time
    val blue = Color(0xFF2563EB)
    val pink = Color(0xFFF472B6)
    val hair = Color(0xFFE5E7EB)
    val h = b.head
    val hr = b.headR
    val p = b.pulse(6f)

    // Kurzer Umhang
    val cape = Path().apply {
        moveTo(c.x - u * 0.3f, b.shoulderY)
        lineTo(c.x + u * 0.3f, b.shoulderY)
        lineTo(c.x + u * 0.48f, b.hipY + u * 0.15f)
        quadraticTo(c.x, b.hipY + u * 0.28f, c.x - u * 0.48f, b.hipY + u * 0.15f)
        close()
    }
    drawPath(cape, blue.shade(0.45f))

    // Bob-Frisur hinten
    hairBack(b, hair, h.y + hr * 0.95f, width = 1.1f)

    // Beine mit Strümpfen und Schnallenschuhen
    artFemaleLegs(b, Color(0xFFEDE9FE), Color(0xFF3B2F5C), bootHeight = 0.16f)
    for (side in listOf(-1f, 1f)) drawCircle(ArtGold, u * 0.03f, Offset(c.x + side * u * 0.17f + dir * u * 0.04f, b.footY - u * 0.08f))

    // Ärmel mit Puffschultern
    elfArm(b, b.hand, blue.bright(0.1f), sleeveLength = 0.6f)
    elfArm(b, b.offHand, blue.bright(0.1f), sleeveLength = 0.6f)

    // Kurzes Kleid mit weißem Kragen und Schleife
    val waistY = b.hipY - u * 0.08f
    val hemY = b.hipY + u * 0.32f
    val dress = Path().apply {
        addPath(femaleTorso(b, waistY, waistY + u * 0.02f, hip = 0.24f))
        moveTo(c.x - u * 0.2f, waistY)
        lineTo(c.x + u * 0.2f, waistY)
        quadraticTo(c.x + u * 0.36f, b.hipY + u * 0.1f, c.x + u * 0.48f, hemY)
        quadraticTo(c.x, hemY + u * 0.1f, c.x - u * 0.48f, hemY)
        quadraticTo(c.x - u * 0.36f, b.hipY + u * 0.1f, c.x - u * 0.2f, waistY)
        close()
    }
    drawPath(dress, blue)
    clipPath(dress) { drawRect(blue.shade(0.2f), Offset(c.x + u * 0.16f, b.shoulderY - u * 0.1f), Size(u * 0.5f, u * 1.2f)) }
    // Rüschensaum
    for (i in -3..3) drawCircle(Color.White, u * 0.055f, Offset(c.x + i * u * 0.13f, hemY + u * 0.02f + (9 - i * i) * u * 0.004f))
    // Matrosenkragen
    val collar = Path().apply {
        moveTo(c.x - u * 0.28f, b.shoulderY - u * 0.02f)
        lineTo(c.x + u * 0.28f, b.shoulderY - u * 0.02f)
        lineTo(c.x + u * 0.22f, b.shoulderY + u * 0.14f)
        lineTo(c.x, b.shoulderY + u * 0.22f)
        lineTo(c.x - u * 0.22f, b.shoulderY + u * 0.14f)
        close()
    }
    drawPath(collar, Color.White)
    drawPath(collar, blue.shade(0.3f), style = Stroke(u * 0.025f))
    // Schleife
    val bow = Offset(c.x, b.shoulderY + u * 0.22f)
    for (side in listOf(-1f, 1f)) {
        drawPath(Path().apply {
            moveTo(bow.x, bow.y)
            lineTo(bow.x + side * u * 0.14f, bow.y - u * 0.08f)
            lineTo(bow.x + side * u * 0.14f, bow.y + u * 0.08f)
            close()
        }, pink)
        drawLine(pink.shade(0.2f), bow, bow + Offset(side * u * 0.06f, u * 0.18f), u * 0.04f, StrokeCap.Round)
    }
    drawCircle(pink.shade(0.15f), u * 0.04f, bow)
    // Gürtel mit Sternschnalle
    drawLine(pink.shade(0.25f), Offset(c.x - u * 0.2f, waistY), Offset(c.x + u * 0.2f, waistY), u * 0.06f)
    drawCircle(ArtGold, u * 0.04f, Offset(c.x, waistY))

    // Gesicht mit Sommersprossen
    artFemaleFace(b, ElfSkin, hair.shade(0.45f), pupil = Color(0xFF7C3AED))
    for (side in listOf(-1f, 1f)) for (i in 0..2) {
        drawCircle(Color(0xFFD97706).copy(alpha = 0.5f), hr * 0.03f, Offset(h.x + side * hr * 0.55f + (i - 1) * hr * 0.09f, h.y + hr * 0.32f + (i % 2) * hr * 0.05f))
    }
    // Pony unter der Hutkrempe
    val brimY = h.y - hr * 0.6f
    hairCap(b, hair, sideLength = 0.95f, topClip = brimY)
    // Gerader Pony
    clipRect(top = brimY) {
        drawPath(Path().apply {
            moveTo(h.x - hr * 0.9f, h.y - hr * 0.6f)
            lineTo(h.x + hr * 0.9f, h.y - hr * 0.6f)
            lineTo(h.x + hr * 0.75f, h.y - hr * 0.3f)
            lineTo(h.x + hr * 0.4f, h.y - hr * 0.38f)
            lineTo(h.x, h.y - hr * 0.3f)
            lineTo(h.x - hr * 0.4f, h.y - hr * 0.38f)
            lineTo(h.x - hr * 0.75f, h.y - hr * 0.3f)
            close()
        }, hair)
    }
    elfEars(b)

    // Kleiner Spitzhut mit rosa Band
    val tip = Offset(h.x - dir * hr * 0.75f, h.y - hr * 2.0f + sin(time * 2.5f) * hr * 0.05f)
    val cone = Path().apply {
        moveTo(h.x - hr * 0.78f, brimY)
        quadraticTo(h.x - hr * 0.3f, h.y - hr * 1.5f, tip.x, tip.y)
        quadraticTo(h.x + hr * 0.2f, h.y - hr * 1.3f, h.x + hr * 0.78f, brimY)
        close()
    }
    drawPath(cone, blue.shade(0.15f))
    drawOval(blue.shade(0.35f), Offset(h.x - hr * 1.3f, brimY - hr * 0.2f), Size(hr * 2.6f, hr * 0.44f))
    drawOval(blue.shade(0.05f), Offset(h.x - hr * 1.26f, brimY - hr * 0.22f), Size(hr * 2.52f, hr * 0.32f))
    drawLine(pink, Offset(h.x - hr * 0.74f, brimY - hr * 0.2f), Offset(h.x + hr * 0.74f, brimY - hr * 0.2f), hr * 0.18f)
    drawCircle(Color(0xFFFDE68A), hr * 0.1f, tip)

    // Zauberbuch in der zweiten Hand
    val book = b.offHand + Offset(-dir * u * 0.05f, -u * 0.05f)
    drawRoundRect(Color(0xFF7C2D12), book - Offset(u * 0.2f, u * 0.15f), Size(u * 0.4f, u * 0.3f), CornerRadius(u * 0.04f))
    drawRoundRect(Color(0xFFFEF3C7), book - Offset(u * 0.17f, u * 0.12f), Size(u * 0.34f, u * 0.05f))
    drawCircle(ArtGold, u * 0.05f, book + Offset(0f, u * 0.02f))

    // Zauberstab mit Stern
    val hand = b.hand
    val top = hand + Offset(dir * u * 0.15f, -u * 0.75f)
    drawLine(Color(0xFFF5F5F4), hand + Offset(0f, u * 0.1f), top, u * 0.05f, StrokeCap.Round)
    drawCircle(pink.copy(alpha = 0.35f * p), u * 0.22f, top)
    val star = Path().apply {
        for (i in 0 until 10) {
            val a = -Math.PI.toFloat() / 2f + i * Math.PI.toFloat() / 5f
            val r = if (i % 2 == 0) u * 0.15f else u * 0.065f
            val pt = top + Offset(cos(a) * r, sin(a) * r)
            if (i == 0) moveTo(pt.x, pt.y) else lineTo(pt.x, pt.y)
        }
        close()
    }
    drawPath(star, pink)
    drawCircle(Color.White.copy(alpha = 0.8f), u * 0.03f, top)
    artHand(b, hand, ElfSkin.shade(0.06f), 0.09f)
    artHand(b, b.offHand + Offset(-dir * u * 0.12f, u * 0.08f), ElfSkin.shade(0.06f), 0.08f)
}

// --- Elfen-Hüterin ---

private fun DrawScope.drawElfWarden(b: ArtBody) {
    val u = b.u
    val c = b.c
    val dir = b.dir
    val time = b.time
    val teal = Color(0xFF0F766E)
    val gold = Color(0xFFFDE68A)
    val leaf = Color(0xFF65A30D)
    val hair = Color(0xFFF1F5F9)
    val h = b.head
    val hr = b.headR

    hairBack(b, hair, b.hipY + u * 0.05f, sway = -dir * sin(time * 2.5f) * u * 0.04f)

    // Beine mit blattförmigen Beinschienen
    artFemaleLegs(b, teal.shade(0.4f), teal.shade(0.1f), bootHeight = 0.5f)
    for (side in listOf(-1f, 1f)) {
        val x = c.x + side * u * 0.17f
        drawPath(Path().apply {
            moveTo(x, b.footY - u * 0.62f)
            quadraticTo(x + u * 0.13f, b.footY - u * 0.4f, x, b.footY - u * 0.12f)
            quadraticTo(x - u * 0.13f, b.footY - u * 0.4f, x, b.footY - u * 0.62f)
            close()
        }, gold.shade(0.2f))
    }

    // Arme in Armschienen
    elfArm(b, b.hand, teal.bright(0.1f), sleeveLength = 1f)
    elfArm(b, b.offHand, teal.bright(0.1f), sleeveLength = 1f)

    // Rüstkleid: Plattenschöße wie Blätter, taillierter Harnisch
    val waistY = b.hipY - u * 0.08f
    for (i in -2..2) {
        val x = c.x + i * u * 0.13f
        drawPath(Path().apply {
            moveTo(x - u * 0.09f, waistY)
            lineTo(x + u * 0.09f, waistY)
            quadraticTo(x + u * 0.12f + i * u * 0.03f, b.hipY + u * 0.25f, x + i * u * 0.05f, b.hipY + u * (0.42f - kotlin.math.abs(i) * 0.05f))
            quadraticTo(x - u * 0.12f + i * u * 0.03f, b.hipY + u * 0.25f, x - u * 0.09f, waistY)
            close()
        }, if (i % 2 == 0) teal else teal.shade(0.2f))
    }
    val torso = femaleTorso(b, waistY, waistY + u * 0.06f, hip = 0.26f)
    drawPath(torso, teal.bright(0.12f))
    clipPath(torso) {
        drawRect(teal.shade(0.15f), Offset(c.x + u * 0.12f, b.shoulderY - u * 0.1f), Size(u * 0.4f, u * 0.8f))
    }
    // Goldene Blattranken auf dem Harnisch
    drawPath(Path().apply {
        moveTo(c.x, waistY)
        quadraticTo(c.x - u * 0.06f, b.shoulderY + u * 0.3f, c.x, b.shoulderY + u * 0.08f)
    }, gold, style = Stroke(u * 0.03f))
    for (side in listOf(-1f, 1f)) drawOval(gold, Offset(c.x + side * u * 0.1f - u * 0.05f, b.shoulderY + u * 0.2f), Size(u * 0.1f, u * 0.06f))
    artNeckline(b, ElfSkin, gold, depth = 0.18f, width = 0.14f)
    drawLine(gold.shade(0.2f), Offset(c.x - u * 0.2f, waistY), Offset(c.x + u * 0.2f, waistY), u * 0.06f)
    // Blatt-Schulterstücke
    for (side in listOf(-1f, 1f)) {
        val sp = Offset(c.x + side * u * 0.3f, b.shoulderY)
        drawPath(Path().apply {
            moveTo(sp.x - side * u * 0.1f, sp.y - u * 0.04f)
            quadraticTo(sp.x + side * u * 0.2f, sp.y - u * 0.16f, sp.x + side * u * 0.3f, sp.y + u * 0.12f)
            quadraticTo(sp.x + side * u * 0.05f, sp.y + u * 0.12f, sp.x - side * u * 0.1f, sp.y - u * 0.04f)
            close()
        }, gold.shade(0.15f))
    }

    // Gesicht, Haare, Ohren, Stirnreif
    artFemaleFace(b, ElfSkin, Color(0xFF94A3B8), pupil = Color(0xFF0F766E))
    hairCap(b, hair, sideLength = 1.35f)
    elfEars(b)
    circlet(b, gold.shade(0.15f), leaf.bright(0.2f))

    // Großer Blattschild
    val sc = b.offHand + Offset(-dir * u * 0.12f, -u * 0.08f)
    val shield = Path().apply {
        moveTo(sc.x, sc.y - u * 0.62f)
        quadraticTo(sc.x + u * 0.5f, sc.y - u * 0.2f, sc.x, sc.y + u * 0.62f)
        quadraticTo(sc.x - u * 0.5f, sc.y - u * 0.2f, sc.x, sc.y - u * 0.62f)
        close()
    }
    drawPath(shield, leaf)
    clipPath(shield) { drawRect(leaf.shade(0.2f), Offset(sc.x, sc.y - u * 0.7f), Size(u * 0.6f, u * 1.4f)) }
    drawPath(shield, gold.shade(0.2f), style = Stroke(u * 0.05f))
    drawLine(gold.shade(0.1f), sc + Offset(0f, -u * 0.5f), sc + Offset(0f, u * 0.5f), u * 0.04f)
    for (k in 0..2) for (side in listOf(-1f, 1f)) {
        val y = sc.y - u * 0.25f + k * u * 0.22f
        drawLine(gold.shade(0.1f), Offset(sc.x, y), Offset(sc.x + side * u * 0.2f, y - u * 0.12f), u * 0.025f, StrokeCap.Round)
    }

    // Speer mit Blattklinge
    val hand = b.hand
    val top = hand + Offset(dir * u * 0.05f, -u * 1.45f)
    drawLine(Color(0xFFE7E5E4), hand + Offset(-dir * u * 0.02f, u * 0.7f), top, u * 0.07f, StrokeCap.Round)
    drawLine(gold.shade(0.2f), top + Offset(-u * 0.08f, u * 0.05f), top + Offset(u * 0.08f, u * 0.05f), u * 0.05f, StrokeCap.Round)
    leafBlade(top, top + Offset(dir * u * 0.04f, -u * 0.42f), u * 0.06f, Color(0xFFE5E7EB))
    artHand(b, hand, ElfSkin.shade(0.06f), 0.09f)
}

// --- Sturmmagierin ---

private fun DrawScope.drawStormmage(b: ArtBody) {
    val u = b.u
    val c = b.c
    val dir = b.dir
    val time = b.time
    val blue = Color(0xFF1D4ED8)
    val cyan = Color(0xFF67E8F9)
    val hair = Color(0xFFE0F2FE)
    val h = b.head
    val hr = b.headR
    val p = b.pulse(7f)

    drawCircle(cyan.copy(alpha = 0.1f + 0.05f * p), u * 1.3f, c + Offset(0f, -u * 0.2f))
    // Umhang mit Sturmfutter
    val sway = sin(time * 3f) * u * 0.07f
    val cape = Path().apply {
        moveTo(c.x - u * 0.3f, b.shoulderY)
        lineTo(c.x + u * 0.3f, b.shoulderY)
        lineTo(c.x + u * 0.72f - dir * sway, b.footY - u * 0.05f)
        quadraticTo(c.x, b.footY + u * 0.05f, c.x - u * 0.72f - dir * sway, b.footY - u * 0.05f)
        close()
    }
    drawPath(cape, blue.shade(0.45f))
    clipPath(cape) { drawRect(cyan.copy(alpha = 0.15f), Offset(c.x - u, b.footY - u * 0.25f), Size(u * 2f, u * 0.3f)) }

    // Langes Haar, im Wind nach hinten wehend
    hairBack(b, hair, b.hipY - u * 0.05f, width = 1.12f, sway = -dir * sway * 0.5f)

    // Ein Bein im Beinschlitz sichtbar
    artFemaleLegs(b, ElfSkin, Color(0xFF1E3A8A), bootHeight = 0.36f)

    // Ärmel mit Trompetenenden
    for (hd in listOf(b.hand, b.offHand)) {
        elfArm(b, hd, blue.bright(0.05f), sleeveLength = 0.75f)
        val s = if (hd.x > c.x) 1f else -1f
        val shoulder = Offset(c.x + s * u * 0.28f, b.shoulderY + u * 0.08f)
        val cuffAt = shoulder + (hd - shoulder) * 0.75f
        drawOval(blue.bright(0.1f), cuffAt - Offset(u * 0.13f, u * 0.08f), Size(u * 0.26f, u * 0.16f))
        drawOval(cyan, cuffAt - Offset(u * 0.13f, u * 0.08f), Size(u * 0.26f, u * 0.16f), style = Stroke(u * 0.025f))
    }

    // Robe mit Taille und hohem Beinschlitz
    val waistY = b.hipY - u * 0.08f
    val hemY = b.footY - u * 0.1f
    val gown = Path().apply {
        addPath(femaleTorso(b, waistY, waistY + u * 0.02f))
        moveTo(c.x - u * 0.2f, waistY)
        lineTo(c.x + u * 0.2f, waistY)
        cubicTo(c.x + u * 0.32f, b.hipY + u * 0.1f, c.x + u * 0.5f, b.hipY + u * 0.3f, c.x + u * 0.6f, hemY)
        lineTo(c.x + dir * u * 0.05f, hemY)
        lineTo(c.x + dir * u * 0.02f, b.hipY + u * 0.1f)
        lineTo(c.x - dir * u * 0.1f, hemY)
        lineTo(c.x - u * 0.6f, hemY)
        cubicTo(c.x - u * 0.5f, b.hipY + u * 0.3f, c.x - u * 0.32f, b.hipY + u * 0.1f, c.x - u * 0.2f, waistY)
        close()
    }
    drawPath(gown, blue)
    clipPath(gown) { drawRect(blue.shade(0.25f), Offset(c.x + u * 0.18f, b.shoulderY - u * 0.1f), Size(u * 0.6f, u * 1.6f)) }
    // Blitzmuster auf dem Rock
    for ((x0, y0) in listOf(-0.4f to 0.5f, 0.32f to 0.6f)) {
        val s0 = Offset(c.x + x0 * u, c.y + y0 * u)
        drawPath(Path().apply {
            moveTo(s0.x, s0.y)
            lineTo(s0.x + u * 0.08f, s0.y + u * 0.12f)
            lineTo(s0.x - u * 0.02f, s0.y + u * 0.14f)
            lineTo(s0.x + u * 0.06f, s0.y + u * 0.28f)
        }, cyan.copy(alpha = 0.8f), style = Stroke(u * 0.03f, cap = StrokeCap.Round))
    }
    artNeckline(b, ElfSkin, cyan, depth = 0.3f, width = 0.17f)
    drawLine(cyan, Offset(c.x - u * 0.2f, waistY), Offset(c.x + u * 0.2f, waistY), u * 0.06f)
    drawCircle(Color.White, u * 0.045f, Offset(c.x, waistY))
    // Schulterspangen des Umhangs
    for (side in listOf(-1f, 1f)) drawCircle(cyan.shade(0.2f), u * 0.07f, Offset(c.x + side * u * 0.27f, b.shoulderY + u * 0.02f))

    // Gesicht, Haare, Ohren, Stirnreif
    artFemaleFace(b, ElfSkin, Color(0xFF64748B), pupil = Color(0xFF0891B2))
    hairCap(b, hair, sideLength = 1.2f, parting = dir * 0.2f)
    elfEars(b)
    circlet(b, Color(0xFFCBD5E1), cyan)

    // Stab mit knisternder Sturmkugel
    val hand = b.hand
    val top = hand + Offset(dir * u * 0.04f, -u * 1.35f)
    drawLine(Color(0xFFCBD5E1), hand + Offset(-dir * u * 0.02f, u * 0.75f), top + Offset(0f, u * 0.1f), u * 0.07f, StrokeCap.Round)
    for (side in listOf(-1f, 1f)) {
        drawPath(Path().apply {
            moveTo(top.x, top.y + u * 0.12f)
            quadraticTo(top.x + side * u * 0.25f, top.y, top.x + side * u * 0.1f, top.y - u * 0.18f)
        }, Color(0xFFCBD5E1), style = Stroke(u * 0.045f, cap = StrokeCap.Round))
    }
    drawCircle(cyan.copy(alpha = 0.35f * p), u * 0.28f, top)
    drawCircle(cyan, u * 0.12f, top)
    drawCircle(Color.White, u * 0.05f, top)
    for (i in 0 until 3) {
        val a = time * 6f + i * 2.1f
        val mid = top + Offset(cos(a + 0.5f) * u * 0.18f, sin(a + 0.5f) * u * 0.18f)
        val end = top + Offset(cos(a) * u * 0.34f, sin(a) * u * 0.34f)
        drawLine(Color.White.copy(alpha = 0.8f), top, mid, u * 0.025f)
        drawLine(Color.White.copy(alpha = 0.8f), mid, end, u * 0.025f)
    }
    // Blitz über der zweiten Hand
    val off = b.offHand
    val bolt = Path().apply {
        moveTo(off.x + u * 0.04f, off.y - u * 0.55f)
        lineTo(off.x - u * 0.08f, off.y - u * 0.3f)
        lineTo(off.x + u * 0.03f, off.y - u * 0.28f)
        lineTo(off.x - u * 0.05f, off.y - u * 0.1f)
    }
    drawPath(bolt, cyan.copy(alpha = 0.5f * p), style = Stroke(u * 0.09f, cap = StrokeCap.Round))
    drawPath(bolt, Color.White, style = Stroke(u * 0.035f, cap = StrokeCap.Round))
    artHand(b, hand, ElfSkin.shade(0.06f), 0.09f)
    artHand(b, off, ElfSkin.shade(0.06f), 0.09f)
}

// --- Elfenkönigin ---

private fun DrawScope.drawElfQueen(b: ArtBody) {
    val u = b.u
    val c = b.c
    val dir = b.dir
    val time = b.time
    val silver = Color(0xFFE2E8F0)
    val gold = Color(0xFFFACC15)
    val royal = Color(0xFF1E3A8A)
    val hair = Color(0xFFFEF3C7)
    val h = b.head
    val hr = b.headR
    val p = b.pulse(2.5f)

    drawCircle(gold.copy(alpha = 0.1f + 0.06f * p), u * 1.35f, c + Offset(0f, -u * 0.25f))
    // Königsmantel: außen tiefblau, innen Gold, Hermelinkragen
    val sway = sin(time * 2f) * u * 0.05f
    val cape = Path().apply {
        moveTo(c.x - u * 0.34f, b.shoulderY)
        lineTo(c.x + u * 0.34f, b.shoulderY)
        lineTo(c.x + u * 0.85f - dir * sway, b.footY)
        quadraticTo(c.x, b.footY + u * 0.1f, c.x - u * 0.85f - dir * sway, b.footY)
        close()
    }
    drawPath(cape, royal)
    drawPath(cape, gold, style = Stroke(u * 0.04f))
    clipPath(cape) { drawRect(royal.shade(0.3f), Offset(c.x + u * 0.25f, b.shoulderY), Size(u * 0.7f, u * 1.6f)) }

    hairBack(b, hair, b.hipY + u * 0.2f, width = 1.15f, sway = -dir * sway)

    // Arme in Silberschienen
    elfArm(b, b.hand, silver.shade(0.1f), sleeveLength = 1f)
    elfArm(b, b.offHand, silver.shade(0.1f), sleeveLength = 1f)

    // Langes Rüstkleid, schmale Taille, fließender Rock
    val waistY = b.hipY - u * 0.08f
    val hemY = b.footY - u * 0.02f
    val gown = Path().apply {
        addPath(femaleTorso(b, waistY, waistY + u * 0.02f))
        moveTo(c.x - u * 0.2f, waistY)
        lineTo(c.x + u * 0.2f, waistY)
        cubicTo(c.x + u * 0.3f, b.hipY + u * 0.1f, c.x + u * 0.5f, b.hipY + u * 0.3f, c.x + u * 0.58f, hemY)
        quadraticTo(c.x, hemY + u * 0.1f, c.x - u * 0.58f, hemY)
        cubicTo(c.x - u * 0.5f, b.hipY + u * 0.3f, c.x - u * 0.3f, b.hipY + u * 0.1f, c.x - u * 0.2f, waistY)
        close()
    }
    drawPath(gown, silver)
    clipPath(gown) { drawRect(Color(0xFFB8C2D1), Offset(c.x + u * 0.16f, b.shoulderY - u * 0.1f), Size(u * 0.6f, u * 1.8f)) }
    // Goldene Mittelbahn mit Baum-Ornament
    val panel = Path().apply {
        moveTo(c.x - u * 0.07f, waistY)
        lineTo(c.x + u * 0.07f, waistY)
        lineTo(c.x + u * 0.2f, hemY)
        quadraticTo(c.x, hemY + u * 0.06f, c.x - u * 0.2f, hemY)
        close()
    }
    drawPath(panel, gold.shade(0.1f))
    drawPath(panel, gold.shade(0.35f), style = Stroke(u * 0.025f))
    drawLine(gold.bright(0.4f), Offset(c.x, waistY + u * 0.1f), Offset(c.x, hemY - u * 0.12f), u * 0.025f)
    for (k in 0..1) for (side in listOf(-1f, 1f)) {
        val y = waistY + u * (0.18f + k * 0.18f)
        drawLine(gold.bright(0.4f), Offset(c.x, y), Offset(c.x + side * u * 0.08f, y - u * 0.08f), u * 0.02f, StrokeCap.Round)
    }
    // Harnisch-Mieder mit Goldkanten
    for (side in listOf(-1f, 1f)) {
        drawArc(gold.shade(0.2f), 20f, 140f, false, Offset(c.x + side * u * 0.14f - u * 0.15f, b.shoulderY + u * 0.08f), Size(u * 0.3f, u * 0.22f), style = Stroke(u * 0.03f, cap = StrokeCap.Round))
    }
    artNeckline(b, ElfSkin, gold, depth = 0.24f, width = 0.17f)
    drawLine(gold, Offset(c.x - u * 0.2f, waistY), Offset(c.x + u * 0.2f, waistY), u * 0.07f)
    drawCircle(Color(0xFF38BDF8), u * 0.04f, Offset(c.x, waistY))
    // Zierliche Schulterplatten
    for (side in listOf(-1f, 1f)) artPauldron(b, side * 0.8f, silver, gold, 0.9f)

    // Gesicht, Haare, Ohren, Krone
    artFemaleFace(b, ElfSkin, hair.shade(0.45f), pupil = Color(0xFF2563EB))
    val crownBase = h.y - hr * 0.6f
    hairCap(b, hair, sideLength = 1.4f, topClip = crownBase - hr * 0.15f)
    elfEars(b)
    val crown = Path().apply {
        moveTo(h.x - hr * 0.88f, crownBase)
        lineTo(h.x - hr * 0.95f, h.y - hr * 1.15f)
        lineTo(h.x - hr * 0.55f, h.y - hr * 0.95f)
        lineTo(h.x - hr * 0.3f, h.y - hr * 1.55f)
        lineTo(h.x, h.y - hr * 1.15f)
        lineTo(h.x + hr * 0.3f, h.y - hr * 1.55f)
        lineTo(h.x + hr * 0.55f, h.y - hr * 0.95f)
        lineTo(h.x + hr * 0.95f, h.y - hr * 1.15f)
        lineTo(h.x + hr * 0.88f, crownBase)
        close()
    }
    drawPath(crown, gold)
    clipPath(crown) { drawRect(gold.shade(0.2f), Offset(h.x + hr * 0.3f, h.y - hr * 1.7f), Size(hr, hr * 1.2f)) }
    drawLine(gold.shade(0.3f), Offset(h.x - hr * 0.9f, crownBase - hr * 0.08f), Offset(h.x + hr * 0.9f, crownBase - hr * 0.08f), hr * 0.1f)
    drawCircle(Color(0xFF38BDF8), hr * 0.13f, Offset(h.x, crownBase - hr * 0.25f))
    for (side in listOf(-1f, 1f)) drawCircle(Color(0xFF22C55E), hr * 0.07f, Offset(h.x + side * hr * 0.5f, crownBase - hr * 0.2f))
    for (side in listOf(-1f, 1f)) drawCircle(Color.White, hr * 0.06f, Offset(h.x + side * hr * 0.3f, h.y - hr * 1.55f))

    // Silberschild mit goldenem Lebensbaum
    val sc = b.offHand + Offset(-dir * u * 0.1f, -u * 0.05f)
    val shield = Path().apply {
        moveTo(sc.x, sc.y - u * 0.5f)
        lineTo(sc.x + u * 0.38f, sc.y - u * 0.38f)
        lineTo(sc.x + u * 0.34f, sc.y + u * 0.12f)
        quadraticTo(sc.x + u * 0.22f, sc.y + u * 0.45f, sc.x, sc.y + u * 0.6f)
        quadraticTo(sc.x - u * 0.22f, sc.y + u * 0.45f, sc.x - u * 0.34f, sc.y + u * 0.12f)
        lineTo(sc.x - u * 0.38f, sc.y - u * 0.38f)
        close()
    }
    drawPath(shield, Color(0xFFCBD5E1))
    clipPath(shield) { drawRect(Color(0xFF94A3B8), Offset(sc.x + u * 0.1f, sc.y - u * 0.6f), Size(u * 0.4f, u * 1.3f)) }
    drawLine(gold, sc + Offset(0f, u * 0.35f), sc + Offset(0f, -u * 0.15f), u * 0.05f, StrokeCap.Round)
    drawCircle(gold, u * 0.16f, sc + Offset(0f, -u * 0.18f))
    drawCircle(gold.shade(0.2f), u * 0.16f, sc + Offset(0f, -u * 0.18f), style = Stroke(u * 0.025f))
    for (side in listOf(-1f, 1f)) drawLine(gold, sc + Offset(0f, u * 0.3f), sc + Offset(side * u * 0.14f, u * 0.4f), u * 0.035f, StrokeCap.Round)
    drawPath(shield, gold, style = Stroke(u * 0.06f))

    // Prunkspeer
    val hand = b.hand
    val top = hand + Offset(dir * u * 0.05f, -u * 1.5f)
    drawLine(gold.shade(0.15f), hand + Offset(-dir * u * 0.02f, u * 0.72f), top, u * 0.07f, StrokeCap.Round)
    drawCircle(Color(0xFF38BDF8), u * 0.06f, top + Offset(0f, u * 0.08f))
    for (side in listOf(-1f, 1f)) {
        drawPath(Path().apply {
            moveTo(top.x, top.y + u * 0.04f)
            quadraticTo(top.x + side * u * 0.2f, top.y, top.x + side * u * 0.14f, top.y - u * 0.16f)
        }, gold, style = Stroke(u * 0.04f, cap = StrokeCap.Round))
    }
    leafBlade(top, top + Offset(dir * u * 0.03f, -u * 0.45f), u * 0.065f, Color(0xFFF1F5F9))
    artHand(b, hand, silver.shade(0.15f), 0.1f)
}

// --- Elfenschützin ---

private fun DrawScope.drawHuntress(b: ArtBody) {
    val u = b.u
    val c = b.c
    val dir = b.dir
    val time = b.time
    val green = Color(0xFF166534)
    val lime = Color(0xFFA3E635)
    val hair = Color(0xFFFCD34D)
    val leather = Color(0xFF7C4A21)
    val h = b.head
    val hr = b.headR

    // Hoher Seitenzopf, über die hintere Schulter wehend
    val swing = sin(time * 3.5f) * u * 0.05f
    val tail = Path().apply {
        moveTo(h.x - dir * hr * 0.4f, h.y - hr * 0.95f)
        quadraticTo(h.x - dir * hr * 1.6f, h.y - hr * 0.9f, h.x - dir * hr * 1.35f + swing, h.y + hr * 0.8f)
        quadraticTo(h.x - dir * hr * 1.25f + swing, h.y + hr * 1.7f, h.x - dir * hr * 0.85f + swing, h.y + hr * 2.3f)
        quadraticTo(h.x - dir * hr * 0.95f, h.y + hr * 0.6f, h.x - dir * hr * 0.75f, h.y - hr * 0.5f)
        close()
    }
    drawPath(tail, hair.shade(0.12f))

    // Köcher mit Pfeilen auf dem Rücken
    val qx = c.x - dir * u * 0.3f
    for (i in 0..2) {
        val ax = qx - dir * u * (0.02f + i * 0.08f)
        val tip = Offset(ax - dir * u * 0.1f, b.shoulderY - u * 0.5f + i * u * 0.04f)
        drawLine(Color(0xFFD6D3D1), Offset(ax, b.shoulderY), tip, u * 0.035f)
        drawPath(Path().apply {
            moveTo(tip.x, tip.y)
            lineTo(tip.x - u * 0.07f, tip.y + u * 0.14f)
            lineTo(tip.x + u * 0.07f, tip.y + u * 0.14f)
            close()
        }, if (i == 1) Color(0xFFF5F5F4) else lime)
    }
    drawRoundRect(leather, Offset(qx - dir * u * 0.12f - u * 0.11f, b.shoulderY - u * 0.22f), Size(u * 0.22f, u * 0.6f), CornerRadius(u * 0.07f))

    // Beine: kurze Hose, Overknee-Stiefel mit Umschlag
    artFemaleLegs(b, ElfSkin, leather.shade(0.25f), bootHeight = 0.58f)
    for (side in listOf(-1f, 1f)) {
        val x = c.x + side * u * 0.17f
        drawRoundRect(leather.shade(0.05f), Offset(x - u * 0.11f, b.footY - u * 0.62f), Size(u * 0.22f, u * 0.08f), CornerRadius(u * 0.03f))
    }
    val shorts = Path().apply {
        moveTo(c.x - u * 0.3f, b.hipY - u * 0.06f)
        lineTo(c.x + u * 0.3f, b.hipY - u * 0.06f)
        lineTo(c.x + u * 0.32f, b.hipY + u * 0.16f)
        lineTo(c.x + u * 0.04f, b.hipY + u * 0.16f)
        lineTo(c.x, b.hipY + u * 0.06f)
        lineTo(c.x - u * 0.04f, b.hipY + u * 0.16f)
        lineTo(c.x - u * 0.32f, b.hipY + u * 0.16f)
        close()
    }
    drawPath(shorts, green.shade(0.3f))

    // Arme, Bogenarm mit Lederschiene
    elfArm(b, b.hand, green, sleeveLength = 0f)
    elfArm(b, b.offHand, green, sleeveLength = 0f)
    val bracer = b.hand - (b.hand - Offset(c.x + dir * u * 0.28f, b.shoulderY)) * 0.3f
    drawLine(leather, b.hand, bracer, u * 0.17f, StrokeCap.Round)

    // Kurzes Wams mit Schnürung und Blattsaum
    val waistY = b.hipY - u * 0.08f
    drawPath(femaleTorso(b, waistY, b.hipY - u * 0.04f), ElfSkin)
    val vest = Path().apply {
        moveTo(c.x - u * 0.3f, b.shoulderY + u * 0.06f)
        quadraticTo(c.x - u * 0.15f, b.shoulderY + u * 0.02f, c.x, b.shoulderY + u * 0.16f)
        quadraticTo(c.x + u * 0.15f, b.shoulderY + u * 0.02f, c.x + u * 0.3f, b.shoulderY + u * 0.06f)
        lineTo(c.x + u * 0.25f, waistY - u * 0.08f)
        for (i in 0..3) {
            val x0 = c.x + u * 0.25f - i * u * 0.125f
            lineTo(x0 - u * 0.0625f, waistY + u * 0.02f)
            lineTo(x0 - u * 0.125f, waistY - u * 0.08f)
        }
        close()
    }
    drawPath(vest, green)
    clipPath(vest) { drawRect(green.shade(0.2f), Offset(c.x + u * 0.12f, b.shoulderY), Size(u * 0.3f, u * 0.6f)) }
    for (k in 0..2) {
        val y = b.shoulderY + u * (0.2f + k * 0.08f)
        drawLine(lime, Offset(c.x - u * 0.05f, y), Offset(c.x + u * 0.05f, y + u * 0.04f), u * 0.02f)
        drawLine(lime, Offset(c.x + u * 0.05f, y), Offset(c.x - u * 0.05f, y + u * 0.04f), u * 0.02f)
    }
    drawLine(leather, Offset(c.x - u * 0.3f, b.hipY - u * 0.05f), Offset(c.x + u * 0.3f, b.hipY - u * 0.05f), u * 0.06f)
    drawLine(leather.shade(0.2f), Offset(c.x + dir * u * 0.3f, b.shoulderY), Offset(c.x - dir * u * 0.26f, waistY - u * 0.05f), u * 0.05f)

    // Gesicht, Haare, Stirnband mit Federn, Ohren
    artFemaleFace(b, ElfSkin, hair.shade(0.45f), pupil = Color(0xFF15803D))
    hairCap(b, hair, sideLength = 1.0f, parting = dir * 0.3f)
    elfEars(b)
    drawArc(green.bright(0.15f), 195f, 150f, false, h + Offset(-hr * 1.0f, -hr * 0.75f), Size(hr * 2.0f, hr * 1.1f), style = Stroke(hr * 0.14f, cap = StrokeCap.Round))
    for (k in 0..1) {
        val base = Offset(h.x - dir * hr * (0.85f + k * 0.1f), h.y - hr * 0.55f)
        val tipF = base + Offset(-dir * hr * (0.6f + k * 0.15f), -hr * (0.55f - k * 0.2f))
        drawPath(Path().apply {
            moveTo(base.x, base.y)
            quadraticTo(base.x - dir * hr * 0.2f, tipF.y, tipF.x, tipF.y)
            quadraticTo(base.x - dir * hr * 0.5f, base.y, base.x, base.y)
            close()
        }, if (k == 0) lime else Color(0xFFF5F5F4))
    }

    // Langbogen mit Sehne
    val hand = b.hand
    val grip = hand + Offset(-dir * u * 0.08f, 0f)
    val top = grip + Offset(-dir * u * 0.05f, -u * 0.85f)
    val bottom = grip + Offset(-dir * u * 0.05f, u * 0.85f)
    drawLine(Color(0xFFE7E5E4).copy(alpha = 0.9f), top + Offset(dir * u * 0.04f, 0f), bottom + Offset(dir * u * 0.04f, 0f), u * 0.02f)
    val bow = Path().apply {
        moveTo(top.x - dir * u * 0.06f, top.y - u * 0.06f)
        quadraticTo(top.x + dir * u * 0.02f, top.y + u * 0.02f, top.x + dir * u * 0.06f, top.y + u * 0.06f)
        quadraticTo(grip.x + dir * u * 0.38f, grip.y - u * 0.45f, grip.x + dir * u * 0.12f, grip.y)
        quadraticTo(grip.x + dir * u * 0.38f, grip.y + u * 0.45f, bottom.x + dir * u * 0.06f, bottom.y - u * 0.06f)
        quadraticTo(bottom.x + dir * u * 0.02f, bottom.y - u * 0.02f, bottom.x - dir * u * 0.06f, bottom.y + u * 0.06f)
    }
    drawPath(bow, Color(0xFF7C4A21), style = Stroke(u * 0.09f, cap = StrokeCap.Round))
    drawPath(bow, lime.shade(0.2f), style = Stroke(u * 0.03f, cap = StrokeCap.Round))
    drawLine(green.shade(0.3f), grip + Offset(dir * u * 0.12f, -u * 0.1f), grip + Offset(dir * u * 0.12f, u * 0.1f), u * 0.11f)
    artHand(b, hand, ElfSkin.shade(0.06f), 0.09f)
    artHand(b, b.offHand, ElfSkin.shade(0.06f), 0.09f)
}
