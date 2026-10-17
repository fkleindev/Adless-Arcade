package com.adlessarcade.games.galaxytowers

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import com.adlessarcade.ui.theme.BackdropStyle
import com.adlessarcade.ui.theme.CardBack
import com.adlessarcade.ui.theme.CardBackStyle
import com.adlessarcade.ui.theme.SpaceBackdrop
import kotlin.math.sin

val CardRed = Color(0xFFE11D2E)
val CardBlack = Color(0xFF111111)
val HighlightYellow = Color(0xFFFACC15)
private val CardWhite = Color(0xFFFAFAF9)
private val CrownGold = Color(0xFFF59E0B)

/** Seitenverhältnis Höhe/Breite einer Karte. */
const val CARD_RATIO = 1.42f

private fun cornerOf(size: Size) = CornerRadius(size.width * 0.1f)

// --- Hintergrund ---

/** Schwarzer Sternenhimmel mit kleinen Kreuz-Sternen wie im ersten Teil, je nach Skin mit Nebel, Asteroiden oder Kern. */
fun DrawScope.drawSpaceBackdrop(backdrop: SpaceBackdrop, time: Float) {
    drawRect(Brush.verticalGradient(listOf(backdrop.top, backdrop.bottom)))
    when (backdrop.style) {
        BackdropStyle.STARS -> Unit
        BackdropStyle.NEBULA -> {
            drawCircle(
                Brush.radialGradient(listOf(backdrop.accent.copy(alpha = 0.22f), Color.Transparent), Offset(size.width * 0.2f, size.height * 0.25f), size.width * 0.7f),
                size.width * 0.7f, Offset(size.width * 0.2f, size.height * 0.25f),
            )
            drawCircle(
                Brush.radialGradient(listOf(Color(0xFF6D35A0).copy(alpha = 0.25f), Color.Transparent), Offset(size.width * 0.85f, size.height * 0.7f), size.width * 0.8f),
                size.width * 0.8f, Offset(size.width * 0.85f, size.height * 0.7f),
            )
        }
        BackdropStyle.CORE -> {
            val c = Offset(size.width * 0.5f, size.height * 0.45f)
            drawCircle(Brush.radialGradient(listOf(backdrop.accent.copy(alpha = 0.28f), Color(0xFF7C3AED).copy(alpha = 0.12f), Color.Transparent), c, size.width * 0.9f), size.width * 0.9f, c)
        }
        BackdropStyle.ASTEROIDS -> Unit
    }
    // Sterne auf festem Raster mit leichtem Versatz, sanftes Funkeln
    val step = size.width / 9f
    var i = 0
    var y = step * 0.4f
    while (y < size.height) {
        var x = step * 0.3f
        while (x < size.width) {
            val h = (i * 2654435761L).toInt()
            val p = Offset(x + ((h ushr 4) and 31) / 31f * step * 0.6f, y + ((h ushr 9) and 31) / 31f * step * 0.6f)
            val twinkle = 0.45f + 0.35f * sin(time * 1.5f + i)
            val s = step * if (h and 7 == 0) 0.07f else 0.04f
            val color = backdrop.accent.copy(alpha = twinkle)
            drawLine(color, p - Offset(s, 0f), p + Offset(s, 0f), strokeWidth = s * 0.7f)
            drawLine(color, p - Offset(0f, s), p + Offset(0f, s), strokeWidth = s * 0.7f)
            x += step
            i++
        }
        y += step
    }
    if (backdrop.style == BackdropStyle.ASTEROIDS) {
        repeat(9) { k ->
            val drift = (time * (6f + k)) % (size.width + 80f)
            val p = Offset((k * 97f + drift) % (size.width + 80f) - 40f, size.height * (0.08f + k * 0.105f))
            val r = size.width * (0.015f + (k % 3) * 0.008f)
            drawCircle(Color(0xFF57534E), r, p)
            drawCircle(Color(0xFF3F3B38), r * 0.35f, p + Offset(r * 0.3f, -r * 0.2f))
        }
    }
}

// --- Karten ---

/** Leerer Platz (z. B. Ablage) mit grauem Rahmen wie im ersten Teil. */
fun DrawScope.drawCardSlot(topLeft: Offset, size: Size) {
    val pad = size.width * 0.12f
    drawRoundRect(Color(0xFF3F3F46), topLeft - Offset(pad, pad), Size(size.width + pad * 2, size.height + pad * 2), CornerRadius(pad))
    drawRoundRect(Color(0xFF27272A), topLeft, size, cornerOf(size))
}

fun DrawScope.drawCardBack(topLeft: Offset, size: Size, back: CardBack, alpha: Float = 1f) {
    val w = size.width
    val h = size.height
    val corner = cornerOf(size)
    drawRoundRect(Color.Black.copy(alpha = 0.35f * alpha), topLeft + Offset(0f, w * 0.04f), size, corner)
    drawRoundRect(back.base.copy(alpha = alpha), topLeft, size, corner)
    clipRect(topLeft.x, topLeft.y, topLeft.x + w, topLeft.y + h) {
        when (back.style) {
            CardBackStyle.GALAXY -> {
                // Lila Ringplanet oben links, Asteroiden unten rechts
                val planet = topLeft + Offset(w * 0.38f, h * 0.3f)
                drawCircle(back.accent.copy(alpha = alpha), w * 0.22f, planet)
                drawCircle(Color.White.copy(alpha = 0.12f * alpha), w * 0.1f, planet - Offset(w * 0.07f, w * 0.07f))
                drawLine(
                    back.detail.copy(alpha = alpha),
                    planet + Offset(-w * 0.32f, w * 0.2f),
                    planet + Offset(w * 0.32f, -w * 0.2f),
                    strokeWidth = w * 0.07f,
                )
                val rock = if (back.base.luminance() < 0.1f) Color(0xFF52525B) else Color(0xFF3F3F46)
                listOf(Triple(0.68f, 0.78f, 0.09f), Triple(0.84f, 0.68f, 0.06f), Triple(0.52f, 0.88f, 0.05f), Triple(0.82f, 0.88f, 0.07f), Triple(0.6f, 0.66f, 0.035f))
                    .forEach { (x, y, r) -> drawCircle(rock.copy(alpha = 0.85f * alpha), w * r, topLeft + Offset(w * x, h * y)) }
            }
            CardBackStyle.SKULL -> {
                val c = topLeft + Offset(w * 0.5f, h * 0.45f)
                val r = w * 0.27f
                drawCircle(back.accent.copy(alpha = alpha), r, c)
                drawRoundRect(back.accent.copy(alpha = alpha), c + Offset(-r * 0.6f, r * 0.5f), Size(r * 1.2f, r * 0.7f), CornerRadius(r * 0.15f))
                drawCircle(back.detail.copy(alpha = alpha), r * 0.3f, c + Offset(-r * 0.4f, 0f))
                drawCircle(back.detail.copy(alpha = alpha), r * 0.3f, c + Offset(r * 0.4f, 0f))
                drawRect(back.detail.copy(alpha = alpha), c + Offset(-r * 0.08f, r * 0.35f), Size(r * 0.16f, r * 0.22f))
                for (k in -1..1) {
                    drawLine(back.detail.copy(alpha = alpha), c + Offset(k * r * 0.25f, r * 0.75f), c + Offset(k * r * 0.25f, r * 1.15f), strokeWidth = r * 0.07f)
                }
            }
            CardBackStyle.BRICKS -> {
                val rowH = h / 9f
                for (row in 0..9) {
                    val y = topLeft.y + row * rowH
                    drawLine(back.accent.copy(alpha = alpha), Offset(topLeft.x, y), Offset(topLeft.x + w, y), strokeWidth = w * 0.025f)
                    val shift = if (row % 2 == 0) 0f else w / 6f
                    var x = topLeft.x + shift
                    while (x < topLeft.x + w) {
                        drawLine(back.accent.copy(alpha = alpha), Offset(x, y), Offset(x, y + rowH), strokeWidth = w * 0.025f)
                        x += w / 3f
                    }
                }
                // Fragezeichen-Block in der Mitte
                val bs = w * 0.52f
                val bt = topLeft + Offset((w - bs) / 2f, (h - bs) / 2f)
                drawRect(back.accent.copy(alpha = alpha), bt - Offset(w * 0.03f, w * 0.03f), Size(bs + w * 0.06f, bs + w * 0.06f))
                drawRect(back.detail.copy(alpha = alpha), bt, Size(bs, bs))
                val q = Path().apply {
                    moveTo(bt.x + bs * 0.3f, bt.y + bs * 0.35f)
                    cubicTo(bt.x + bs * 0.3f, bt.y + bs * 0.1f, bt.x + bs * 0.72f, bt.y + bs * 0.1f, bt.x + bs * 0.7f, bt.y + bs * 0.35f)
                    cubicTo(bt.x + bs * 0.68f, bt.y + bs * 0.5f, bt.x + bs * 0.5f, bt.y + bs * 0.5f, bt.x + bs * 0.5f, bt.y + bs * 0.65f)
                }
                drawPath(q, back.accent.copy(alpha = alpha), style = Stroke(width = bs * 0.12f))
                drawRect(back.accent.copy(alpha = alpha), Offset(bt.x + bs * 0.44f, bt.y + bs * 0.74f), Size(bs * 0.12f, bs * 0.12f))
            }
            CardBackStyle.CAT -> {
                val c = topLeft + Offset(w * 0.5f, h * 0.55f)
                val u = w * 0.3f
                drawOval(back.accent.copy(alpha = alpha), c + Offset(-u * 0.75f, -u * 0.1f), Size(u * 1.5f, u * 1.2f))
                drawCircle(back.accent.copy(alpha = alpha), u * 0.55f, c + Offset(0f, -u * 0.45f))
                listOf(-1f, 1f).forEach { side ->
                    val ear = Path().apply {
                        moveTo(c.x + side * u * 0.5f, c.y - u * 0.6f)
                        lineTo(c.x + side * u * 0.45f, c.y - u * 1.15f)
                        lineTo(c.x + side * u * 0.1f, c.y - u * 0.9f)
                        close()
                    }
                    drawPath(ear, back.accent.copy(alpha = alpha))
                    drawCircle(back.detail.copy(alpha = alpha), u * 0.07f, c + Offset(side * u * 0.22f, -u * 0.5f))
                }
                drawLine(back.accent.copy(alpha = alpha), c + Offset(u * 0.6f, u * 0.7f), c + Offset(u * 0.95f, u * 0.2f), strokeWidth = u * 0.16f)
                drawLine(back.detail.copy(alpha = 0.5f * alpha), c + Offset(-u * 0.25f, u * 0.25f), c + Offset(-u * 0.25f, u * 0.6f), strokeWidth = u * 0.05f)
                drawLine(back.detail.copy(alpha = 0.5f * alpha), c + Offset(u * 0.05f, u * 0.25f), c + Offset(u * 0.05f, u * 0.6f), strokeWidth = u * 0.05f)
            }
        }
    }
    drawRoundRect(Color.Black.copy(alpha = 0.35f * alpha), topLeft, size, corner, style = Stroke(width = w * 0.03f))
}

private fun Color.luminance(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue

/** Weiße Kartenvorderseite: Rang und Farbe in den Ecken, großes Symbol bzw. Krone/Hut in der Mitte. */
fun DrawScope.drawCardFace(
    topLeft: Offset,
    size: Size,
    card: Card,
    textMeasurer: TextMeasurer,
    highlight: Boolean = false,
    alpha: Float = 1f,
) {
    val w = size.width
    val corner = cornerOf(size)
    drawRoundRect(Color.Black.copy(alpha = 0.35f * alpha), topLeft + Offset(0f, w * 0.04f), size, corner)
    if (card.isJoker) {
        drawRoundRect(Brush.linearGradient(listOf(Color(0xFF7C3AED), Color(0xFFEC4899)), topLeft, topLeft + Offset(w, size.height)), topLeft, size, corner, alpha = alpha)
        drawStar(topLeft + Offset(w / 2f, size.height / 2f), w * 0.32f, HighlightYellow.copy(alpha = alpha))
        drawLabel(textMeasurer, "JOKER", topLeft + Offset(w / 2f, size.height * 0.82f), w * 0.17f, Color.White.copy(alpha = alpha), center = true)
    } else {
        drawRoundRect(CardWhite.copy(alpha = alpha), topLeft, size, corner)
        val color = (if (card.isRed) CardRed else CardBlack).copy(alpha = alpha)
        val labelSize = w * 0.3f
        drawLabel(textMeasurer, card.label, topLeft + Offset(w * 0.08f, w * 0.02f), labelSize, color)
        drawSuit(card.suit, topLeft + Offset(w * 0.78f, w * 0.2f), w * 0.2f, color)
        rotate(180f, topLeft + Offset(w / 2f, size.height / 2f)) {
            drawLabel(textMeasurer, card.label, topLeft + Offset(w * 0.08f, w * 0.02f), labelSize, color)
            drawSuit(card.suit, topLeft + Offset(w * 0.78f, w * 0.2f), w * 0.2f, color)
        }
        val center = topLeft + Offset(w / 2f, size.height / 2f)
        when (card.rank) {
            13 -> drawCrown(center, w * 0.5f, points = 5, alpha = alpha)
            12 -> drawCrown(center, w * 0.42f, points = 3, alpha = alpha)
            11 -> drawJackHat(center, w * 0.45f, alpha)
            else -> drawSuit(card.suit, center, w * if (card.rank == 1) 0.48f else 0.36f, color)
        }
    }
    if (highlight) {
        drawRoundRect(HighlightYellow.copy(alpha = alpha), topLeft, size, corner, style = Stroke(width = w * 0.06f))
    }
}

private fun DrawScope.drawLabel(textMeasurer: TextMeasurer, text: String, at: Offset, px: Float, color: Color, center: Boolean = false) {
    val layout = textMeasurer.measure(
        text,
        TextStyle(color = color, fontSize = px.toSp(), fontWeight = FontWeight.Black),
    )
    val pos = if (center) at - Offset(layout.size.width / 2f, layout.size.height / 2f) else at
    drawText(layout, topLeft = pos)
}

/** Farbsymbol (0 Pik, 1 Herz, 2 Karo, 3 Kreuz) zentriert auf [c] mit Breite [s]. */
fun DrawScope.drawSuit(suit: Int, c: Offset, s: Float, color: Color) {
    val r = s * 0.27f
    when (suit) {
        1 -> { // Herz
            drawCircle(color, r, c + Offset(-r * 0.85f, -r * 0.45f))
            drawCircle(color, r, c + Offset(r * 0.85f, -r * 0.45f))
            drawPath(Path().apply {
                moveTo(c.x - r * 1.8f, c.y - r * 0.2f)
                lineTo(c.x + r * 1.8f, c.y - r * 0.2f)
                lineTo(c.x, c.y + r * 1.75f)
                close()
            }, color)
        }
        2 -> drawPath(Path().apply { // Karo
            moveTo(c.x, c.y - s * 0.55f)
            lineTo(c.x + s * 0.42f, c.y)
            lineTo(c.x, c.y + s * 0.55f)
            lineTo(c.x - s * 0.42f, c.y)
            close()
        }, color)
        0 -> { // Pik
            drawCircle(color, r, c + Offset(-r * 0.85f, r * 0.35f))
            drawCircle(color, r, c + Offset(r * 0.85f, r * 0.35f))
            drawPath(Path().apply {
                moveTo(c.x - r * 1.8f, c.y + r * 0.1f)
                lineTo(c.x + r * 1.8f, c.y + r * 0.1f)
                lineTo(c.x, c.y - r * 1.85f)
                close()
            }, color)
            drawPath(Path().apply {
                moveTo(c.x, c.y + r * 0.4f)
                lineTo(c.x + r * 0.6f, c.y + r * 1.7f)
                lineTo(c.x - r * 0.6f, c.y + r * 1.7f)
                close()
            }, color)
        }
        else -> { // Kreuz
            drawCircle(color, r, c + Offset(0f, -r * 0.95f))
            drawCircle(color, r, c + Offset(-r * 0.95f, r * 0.2f))
            drawCircle(color, r, c + Offset(r * 0.95f, r * 0.2f))
            drawPath(Path().apply {
                moveTo(c.x, c.y)
                lineTo(c.x + r * 0.6f, c.y + r * 1.7f)
                lineTo(c.x - r * 0.6f, c.y + r * 1.7f)
                close()
            }, color)
        }
    }
}

private fun DrawScope.drawCrown(c: Offset, w: Float, points: Int, alpha: Float) {
    val h = w * 0.6f
    val left = c.x - w / 2f
    val base = c.y + h * 0.4f
    val path = Path().apply {
        moveTo(left, base)
        lineTo(left, c.y - h * 0.5f)
        // Zacken: [points] Spitzen inkl. der beiden Außenkanten, dazwischen Täler
        val n = 2 * (points - 1)
        for (k in 1 until n) {
            lineTo(left + w * k / n, if (k % 2 == 1) c.y else c.y - h * 0.5f)
        }
        lineTo(left + w, c.y - h * 0.5f)
        lineTo(left + w, base)
        close()
    }
    drawPath(path, CrownGold.copy(alpha = alpha))
    drawRect(Color(0xFFB45309).copy(alpha = alpha), Offset(left, base - h * 0.22f), Size(w, h * 0.22f))
    drawCircle(CardRed.copy(alpha = alpha), w * 0.07f, Offset(c.x, base - h * 0.11f))
    if (points == 3) {
        // Perlen auf den drei Spitzen der Damenkrone
        listOf(0f, 0.5f, 1f).forEach { f -> drawCircle(CrownGold.copy(alpha = alpha), w * 0.07f, Offset(left + w * f, c.y - h * 0.55f)) }
    }
}

private fun DrawScope.drawJackHat(c: Offset, w: Float, alpha: Float) {
    val brown = Color(0xFF92400E).copy(alpha = alpha)
    drawPath(Path().apply {
        moveTo(c.x - w * 0.5f, c.y + w * 0.2f)
        lineTo(c.x + w * 0.5f, c.y + w * 0.2f)
        lineTo(c.x + w * 0.25f, c.y - w * 0.15f)
        lineTo(c.x - w * 0.2f, c.y - w * 0.2f)
        close()
    }, brown)
    drawRect(CrownGold.copy(alpha = alpha), Offset(c.x - w * 0.5f, c.y + w * 0.12f), Size(w, w * 0.1f))
    drawLine(Color(0xFFD6D3D1).copy(alpha = alpha), c + Offset(w * 0.1f, -w * 0.15f), c + Offset(w * 0.45f, -w * 0.45f), strokeWidth = w * 0.09f)
}

private fun DrawScope.drawStar(c: Offset, r: Float, color: Color) {
    val path = Path()
    for (k in 0 until 10) {
        val angle = Math.toRadians(-90.0 + k * 36.0)
        val rr = if (k % 2 == 0) r else r * 0.45f
        val p = Offset(c.x + (rr * kotlin.math.cos(angle)).toFloat(), c.y + (rr * kotlin.math.sin(angle)).toFloat())
        if (k == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
    }
    path.close()
    drawPath(path, color)
}
