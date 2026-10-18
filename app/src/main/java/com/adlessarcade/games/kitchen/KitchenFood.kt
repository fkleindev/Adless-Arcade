package com.adlessarcade.games.kitchen

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

internal val Outline = Color(0xFF3B2A20)

internal fun Color.darker(f: Float = 0.25f) = lerp(this, Color.Black, f)
internal fun Color.lighter(f: Float = 0.3f) = lerp(this, Color.White, f)

/** Fläche mit Cartoon-Umriss. */
internal fun DrawScope.outlined(path: Path, fill: Color, width: Float, alpha: Float = 1f) {
    drawPath(path, fill.copy(alpha = fill.alpha * alpha))
    drawPath(path, Outline.copy(alpha = alpha), style = Stroke(width, cap = StrokeCap.Round))
}

internal fun DrawScope.outlinedCircle(c: Offset, r: Float, fill: Color, width: Float, alpha: Float = 1f) {
    drawCircle(fill.copy(alpha = fill.alpha * alpha), r, c)
    drawCircle(Outline.copy(alpha = alpha), r, c, style = Stroke(width))
}

internal fun DrawScope.outlinedOval(tl: Offset, size: Size, fill: Color, width: Float, alpha: Float = 1f) {
    drawOval(fill.copy(alpha = fill.alpha * alpha), tl, size)
    drawOval(Outline.copy(alpha = alpha), tl, size, style = Stroke(width))
}

internal fun DrawScope.outlinedRect(tl: Offset, size: Size, fill: Color, width: Float, corner: Float, alpha: Float = 1f) {
    drawRoundRect(fill.copy(alpha = fill.alpha * alpha), tl, size, CornerRadius(corner))
    drawRoundRect(Outline.copy(alpha = alpha), tl, size, CornerRadius(corner), style = Stroke(width))
}

private fun DrawScope.shine(c: Offset, r: Float, alpha: Float) {
    drawOval(Color.White.copy(alpha = 0.55f * alpha), c - Offset(r, r * 0.6f), Size(r * 2f, r * 1.2f))
}

/** Farbe beim Garen: roh → fertig (doneness 1) → dunkel bis verbrannt. */
internal fun cookColor(raw: Color, done: Color, doneness: Float): Color = when {
    doneness <= 1f -> lerp(raw, done, doneness.coerceIn(0f, 1f))
    else -> lerp(done, Color(0xFF3B1F0F), ((doneness - 1f) / (KitchenCatalog.BURN_AT - 1f)).coerceIn(0f, 1f) * 0.85f)
}

/**
 * Zeichnet eine Zutat oder ein Zwischenprodukt; [s] ist die Größe (≈ Durchmesser).
 * [doneness] färbt Gegartes (0 = roh, 1 = perfekt, darüber dunkler); [garnish] = Sirup u. Ä. (nicht in der Pfanne).
 */
internal fun DrawScope.drawFood(kind: ItemKind, c: Offset, s: Float, alpha: Float = 1f, doneness: Float = 1f, garnish: Boolean = true) {
    val w = s * 0.045f
    val r = s / 2f
    when (kind) {
        ItemKind.EGG -> {
            val egg = Path().apply {
                moveTo(c.x, c.y - r * 0.85f)
                cubicTo(c.x + r * 0.7f, c.y - r * 0.85f, c.x + r * 0.75f, c.y + r * 0.75f, c.x, c.y + r * 0.75f)
                cubicTo(c.x - r * 0.75f, c.y + r * 0.75f, c.x - r * 0.7f, c.y - r * 0.85f, c.x, c.y - r * 0.85f)
                close()
            }
            outlined(egg, Color(0xFFFFF7ED), w, alpha)
            shine(c + Offset(-r * 0.22f, -r * 0.35f), r * 0.16f, alpha)
        }
        ItemKind.MILK -> {
            val body = Size(r * 1.1f, r * 1.25f)
            val tl = Offset(c.x - body.width / 2f, c.y - r * 0.35f)
            val roof = Path().apply {
                moveTo(tl.x, tl.y)
                lineTo(c.x, tl.y - r * 0.5f)
                lineTo(tl.x + body.width, tl.y)
                close()
            }
            outlined(roof, Color(0xFFE0F2FE), w, alpha)
            outlinedRect(tl, body, Color.White, w, r * 0.06f, alpha)
            drawRect(Color(0xFF3B82F6).copy(alpha = alpha), tl + Offset(w, body.height * 0.35f), Size(body.width - w * 2f, body.height * 0.3f))
            drawCircle(Color.White.copy(alpha = alpha), r * 0.1f, c + Offset(0f, r * 0.3f))
        }
        ItemKind.FLOUR -> {
            val sack = Path().apply {
                moveTo(c.x - r * 0.45f, c.y - r * 0.55f)
                quadraticTo(c.x, c.y - r * 0.75f, c.x + r * 0.45f, c.y - r * 0.55f)
                quadraticTo(c.x + r * 0.85f, c.y + r * 0.2f, c.x + r * 0.6f, c.y + r * 0.8f)
                lineTo(c.x - r * 0.6f, c.y + r * 0.8f)
                quadraticTo(c.x - r * 0.85f, c.y + r * 0.2f, c.x - r * 0.45f, c.y - r * 0.55f)
                close()
            }
            outlined(sack, Color(0xFFF5E6C8), w, alpha)
            drawLine(Color(0xFFB45309).copy(alpha = alpha), c + Offset(-r * 0.35f, -r * 0.5f), c + Offset(r * 0.35f, -r * 0.5f), w * 1.5f)
            // Ähre
            drawLine(Color(0xFFD97706).copy(alpha = alpha), c + Offset(0f, r * 0.55f), c + Offset(0f, -r * 0.15f), w)
            for (i in 0..2) {
                val y = c.y + r * (0.35f - i * 0.18f)
                drawOval(Color(0xFFD97706).copy(alpha = alpha), Offset(c.x - r * 0.2f, y - r * 0.06f), Size(r * 0.18f, r * 0.1f))
                drawOval(Color(0xFFD97706).copy(alpha = alpha), Offset(c.x + r * 0.02f, y - r * 0.06f), Size(r * 0.18f, r * 0.1f))
            }
        }
        ItemKind.BREAD -> {
            outlinedRect(Offset(c.x - r * 0.85f, c.y - r * 0.4f), Size(r * 1.7f, r * 0.85f), Color(0xFFB45309), w, r * 0.4f, alpha)
            drawRoundRect(Color(0xFFD97706).copy(alpha = alpha), Offset(c.x - r * 0.7f, c.y - r * 0.32f), Size(r * 1.4f, r * 0.35f), CornerRadius(r * 0.2f))
            for (i in -1..1) drawLine(Outline.copy(alpha = alpha * 0.6f), c + Offset(i * r * 0.4f - r * 0.1f, -r * 0.3f), c + Offset(i * r * 0.4f + r * 0.1f, -r * 0.05f), w)
        }
        ItemKind.CHEESE -> {
            val wedge = Path().apply {
                moveTo(c.x - r * 0.8f, c.y + r * 0.45f)
                lineTo(c.x + r * 0.8f, c.y + r * 0.45f)
                lineTo(c.x + r * 0.8f, c.y - r * 0.15f)
                lineTo(c.x - r * 0.8f, c.y + r * 0.05f)
                close()
            }
            outlined(wedge, Color(0xFFFACC15), w, alpha)
            drawPath(Path().apply {
                moveTo(c.x - r * 0.8f, c.y + r * 0.05f)
                lineTo(c.x + r * 0.8f, c.y - r * 0.15f)
                lineTo(c.x + r * 0.55f, c.y - r * 0.45f)
                close()
            }, Color(0xFFFDE68A).copy(alpha = alpha))
            for ((dx, dy) in listOf(-0.4f to 0.25f, 0.2f to 0.15f, 0.55f to 0.3f)) drawCircle(Color(0xFFEAB308).copy(alpha = alpha), r * 0.1f, c + Offset(dx * r, dy * r))
        }
        ItemKind.STRAWBERRY -> drawStrawberry(c, r, w, alpha, cut = false)
        ItemKind.STRAWBERRY_CUT -> {
            drawStrawberry(c + Offset(-r * 0.3f, 0f), r * 0.7f, w, alpha, cut = true)
            drawStrawberry(c + Offset(r * 0.35f, r * 0.1f), r * 0.65f, w, alpha, cut = true)
        }
        ItemKind.BANANA -> {
            val banana = Path().apply {
                moveTo(c.x - r * 0.85f, c.y - r * 0.35f)
                quadraticTo(c.x, c.y + r * 0.9f, c.x + r * 0.85f, c.y - r * 0.35f)
                quadraticTo(c.x, c.y + r * 0.35f, c.x - r * 0.85f, c.y - r * 0.35f)
                close()
            }
            outlined(banana, Color(0xFFFDE047), w, alpha)
            drawCircle(Color(0xFF78350F).copy(alpha = alpha), r * 0.08f, c + Offset(-r * 0.82f, -r * 0.35f))
            drawCircle(Color(0xFF78350F).copy(alpha = alpha), r * 0.08f, c + Offset(r * 0.82f, -r * 0.35f))
        }
        ItemKind.BANANA_CUT -> for ((dx, dy) in listOf(-0.45f to 0.1f, 0f to -0.2f, 0.45f to 0.12f, 0f to 0.4f)) {
            outlinedCircle(c + Offset(dx * r, dy * r), r * 0.3f, Color(0xFFFEF9C3), w, alpha)
            drawCircle(Color(0xFFD6D3D1).copy(alpha = alpha), r * 0.08f, c + Offset(dx * r, dy * r))
        }
        ItemKind.APPLE -> {
            outlinedCircle(c + Offset(0f, r * 0.08f), r * 0.75f, Color(0xFFDC2626), w, alpha)
            drawLine(Color(0xFF78350F).copy(alpha = alpha), c + Offset(0f, -r * 0.6f), c + Offset(r * 0.08f, -r * 0.95f), w * 1.6f, StrokeCap.Round)
            val leaf = Path().apply {
                moveTo(c.x + r * 0.08f, c.y - r * 0.8f)
                quadraticTo(c.x + r * 0.45f, c.y - r * 1.05f, c.x + r * 0.55f, c.y - r * 0.7f)
                quadraticTo(c.x + r * 0.3f, c.y - r * 0.6f, c.x + r * 0.08f, c.y - r * 0.8f)
            }
            outlined(leaf, Color(0xFF22C55E), w * 0.8f, alpha)
            shine(c + Offset(-r * 0.3f, -r * 0.2f), r * 0.15f, alpha)
        }
        ItemKind.APPLE_CUT -> for (i in 0..2) {
            val p = c + Offset((i - 1) * r * 0.55f, (i % 2) * r * 0.2f)
            val wedge = Path().apply {
                moveTo(p.x - r * 0.35f, p.y + r * 0.25f)
                quadraticTo(p.x, p.y - r * 0.7f, p.x + r * 0.35f, p.y + r * 0.25f)
                close()
            }
            outlined(wedge, Color(0xFFFEF3C7), w, alpha)
            drawLine(Color(0xFFDC2626).copy(alpha = alpha), Offset(p.x - r * 0.32f, p.y + r * 0.22f), Offset(p.x + r * 0.32f, p.y + r * 0.22f), w * 2f, StrokeCap.Round)
        }
        ItemKind.BREAD_SLICES -> for (i in 0..1) drawSlice(c + Offset((i - 0.5f) * r * 0.75f, (i - 0.5f) * r * 0.2f), r * 0.6f, w, Color(0xFFFEF3C7), alpha)
        ItemKind.TOAST -> for (i in 0..1) drawSlice(c + Offset((i - 0.5f) * r * 0.75f, (i - 0.5f) * r * 0.2f), r * 0.6f, w, cookColor(Color(0xFFFEF3C7), Color(0xFFE6A85C), doneness), alpha)
        ItemKind.CHEESE_GRATED -> {
            outlinedOval(Offset(c.x - r * 0.7f, c.y), Size(r * 1.4f, r * 0.55f), Color(0xFFFDE047), w, alpha)
            for (i in 0 until 9) {
                val a = i * 0.7f
                val p = c + Offset(cos(a) * r * 0.45f * (i % 3 + 1) / 3f, -r * 0.05f + sin(a) * r * 0.15f)
                drawLine(Color(0xFFEAB308).copy(alpha = alpha), p, p + Offset(r * 0.22f, -r * 0.08f), w * 1.5f, StrokeCap.Round)
            }
        }
        ItemKind.EGG_MIX -> drawBowlOf(c, r, w, Color(0xFFFACC15), alpha)
        ItemKind.BATTER -> drawBowlOf(c, r, w, Color(0xFFFDE68A), alpha)
        ItemKind.FRIED_EGG -> {
            val white = Path().apply {
                moveTo(c.x - r * 0.8f, c.y)
                cubicTo(c.x - r * 0.9f, c.y - r * 0.7f, c.x + r * 0.3f, c.y - r * 0.85f, c.x + r * 0.75f, c.y - r * 0.3f)
                cubicTo(c.x + r * 1.0f, c.y + r * 0.3f, c.x + r * 0.2f, c.y + r * 0.8f, c.x - r * 0.3f, c.y + r * 0.65f)
                cubicTo(c.x - r * 0.75f, c.y + r * 0.55f, c.x - r * 0.7f, c.y + r * 0.2f, c.x - r * 0.8f, c.y)
                close()
            }
            // Rohes Eiweiß ist glasig, wird beim Braten weiß, am Rand später braun
            outlined(white, cookColor(Color(0xFFE2E8F0), Color.White, doneness).copy(alpha = if (doneness < 1f) 0.75f + 0.25f * doneness else 1f), w, alpha)
            if (doneness > 1f) drawPath(white, cookColor(Color.White, Color(0xFFD6A35C), doneness).copy(alpha = 0.6f * alpha), style = Stroke(w * 3f))
            outlinedCircle(c + Offset(r * 0.05f, -r * 0.05f), r * 0.3f, cookColor(Color(0xFFFBBF24), Color(0xFFF59E0B), doneness), w, alpha)
            shine(c + Offset(-r * 0.05f, -r * 0.15f), r * 0.08f, alpha)
        }
        ItemKind.OMELETTE -> {
            val half = Path().apply {
                moveTo(c.x - r * 0.85f, c.y + r * 0.25f)
                quadraticTo(c.x, c.y - r * 0.95f, c.x + r * 0.85f, c.y + r * 0.25f)
                close()
            }
            outlined(half, cookColor(Color(0xFFFEF9C3), Color(0xFFFCD34D), doneness), w, alpha)
            drawLine(Color(0xFFD97706).copy(alpha = alpha), c + Offset(-r * 0.4f, -r * 0.15f), c + Offset(-r * 0.1f, -r * 0.3f), w * 1.5f, StrokeCap.Round)
            drawLine(Color(0xFFD97706).copy(alpha = alpha), c + Offset(r * 0.2f, -r * 0.1f), c + Offset(r * 0.45f, 0f), w * 1.5f, StrokeCap.Round)
        }
        ItemKind.PANCAKE -> {
            val cake = cookColor(Color(0xFFFEF3C7), Color(0xFFE6A85C), doneness)
            if (!garnish) {
                outlinedOval(Offset(c.x - r * 0.85f, c.y - r * 0.35f), Size(r * 1.7f, r * 0.75f), cake, w, alpha)
                return
            }
            for (i in 0..1) outlinedOval(Offset(c.x - r * 0.85f, c.y - r * 0.05f - i * r * 0.28f), Size(r * 1.7f, r * 0.6f), cake, w, alpha)
            val syrup = Path().apply {
                moveTo(c.x - r * 0.4f, c.y - r * 0.2f)
                quadraticTo(c.x, c.y - r * 0.5f, c.x + r * 0.45f, c.y - r * 0.15f)
                lineTo(c.x + r * 0.35f, c.y + r * 0.1f)
                quadraticTo(c.x, c.y - r * 0.05f, c.x - r * 0.4f, c.y - r * 0.2f)
            }
            drawPath(syrup, Color(0xFF92400E).copy(alpha = 0.8f * alpha))
        }
        ItemKind.BURNT -> {
            outlinedOval(Offset(c.x - r * 0.75f, c.y - r * 0.35f), Size(r * 1.5f, r * 0.8f), Color(0xFF1C1917), w, alpha)
            for (i in 0..2) {
                val x = c.x + (i - 1) * r * 0.35f
                val smoke = Path().apply {
                    moveTo(x, c.y - r * 0.4f)
                    quadraticTo(x - r * 0.2f, c.y - r * 0.7f, x, c.y - r * 0.95f)
                    quadraticTo(x + r * 0.2f, c.y - r * 1.15f, x, c.y - r * 1.35f)
                }
                drawPath(smoke, Color(0xFF9CA3AF).copy(alpha = 0.7f * alpha), style = Stroke(w * 1.5f, cap = StrokeCap.Round))
            }
        }
    }
}

private fun DrawScope.drawStrawberry(c: Offset, r: Float, w: Float, alpha: Float, cut: Boolean) {
    val body = Path().apply {
        moveTo(c.x, c.y + r * 0.85f)
        cubicTo(c.x - r * 0.95f, c.y + r * 0.1f, c.x - r * 0.75f, c.y - r * 0.65f, c.x, c.y - r * 0.55f)
        cubicTo(c.x + r * 0.75f, c.y - r * 0.65f, c.x + r * 0.95f, c.y + r * 0.1f, c.x, c.y + r * 0.85f)
        close()
    }
    outlined(body, Color(0xFFEF4444), w, alpha)
    if (cut) {
        drawPath(Path().apply {
            moveTo(c.x, c.y + r * 0.6f)
            cubicTo(c.x - r * 0.55f, c.y + r * 0.05f, c.x - r * 0.45f, c.y - r * 0.4f, c.x, c.y - r * 0.32f)
            cubicTo(c.x + r * 0.45f, c.y - r * 0.4f, c.x + r * 0.55f, c.y + r * 0.05f, c.x, c.y + r * 0.6f)
        }, Color(0xFFFECACA).copy(alpha = alpha))
    } else {
        for ((dx, dy) in listOf(-0.3f to 0f, 0.25f to -0.1f, 0f to 0.3f, -0.15f to 0.5f, 0.3f to 0.3f)) {
            drawCircle(Color(0xFFFDE68A).copy(alpha = alpha), r * 0.05f, c + Offset(dx * r, dy * r))
        }
    }
    for (i in -1..1) {
        val leaf = Path().apply {
            moveTo(c.x, c.y - r * 0.55f)
            lineTo(c.x + i * r * 0.45f, c.y - r * (0.8f - if (i == 0) 0.15f else 0f))
            lineTo(c.x + i * r * 0.15f, c.y - r * 0.5f)
            close()
        }
        drawPath(leaf, Color(0xFF16A34A).copy(alpha = alpha))
    }
}

private fun DrawScope.drawSlice(c: Offset, r: Float, w: Float, inside: Color, alpha: Float) {
    val slice = Path().apply {
        moveTo(c.x - r * 0.7f, c.y + r * 0.75f)
        lineTo(c.x - r * 0.7f, c.y - r * 0.2f)
        cubicTo(c.x - r * 1.0f, c.y - r * 0.9f, c.x + r * 1.0f, c.y - r * 0.9f, c.x + r * 0.7f, c.y - r * 0.2f)
        lineTo(c.x + r * 0.7f, c.y + r * 0.75f)
        close()
    }
    drawPath(slice, Color(0xFFB45309).copy(alpha = alpha))
    val inner = Path().apply {
        moveTo(c.x - r * 0.55f, c.y + r * 0.62f)
        lineTo(c.x - r * 0.55f, c.y - r * 0.15f)
        cubicTo(c.x - r * 0.8f, c.y - r * 0.72f, c.x + r * 0.8f, c.y - r * 0.72f, c.x + r * 0.55f, c.y - r * 0.15f)
        lineTo(c.x + r * 0.55f, c.y + r * 0.62f)
        close()
    }
    drawPath(inner, inside.copy(alpha = alpha))
    drawPath(slice, Outline.copy(alpha = alpha), style = Stroke(w))
}

private fun DrawScope.drawBowlOf(c: Offset, r: Float, w: Float, content: Color, alpha: Float) {
    val bowl = Path().apply {
        moveTo(c.x - r * 0.85f, c.y - r * 0.15f)
        quadraticTo(c.x, c.y + r * 1.15f, c.x + r * 0.85f, c.y - r * 0.15f)
        close()
    }
    outlinedOval(Offset(c.x - r * 0.85f, c.y - r * 0.35f), Size(r * 1.7f, r * 0.42f), content, w, alpha)
    outlined(bowl, Color(0xFF60A5FA), w, alpha)
    shine(c + Offset(-r * 0.35f, r * 0.15f), r * 0.1f, alpha)
}

/** Teller mit Komponenten (bei fertigem Gericht mit Glanz). */
internal fun DrawScope.drawPlate(c: Offset, s: Float, components: List<ItemKind>, ready: Boolean, alpha: Float = 1f, time: Float = 0f) {
    val w = s * 0.035f
    outlinedOval(Offset(c.x - s * 0.5f, c.y - s * 0.32f), Size(s, s * 0.64f), Color.White, w, alpha)
    drawOval(Color(0xFFE5E7EB).copy(alpha = alpha), Offset(c.x - s * 0.36f, c.y - s * 0.22f), Size(s * 0.72f, s * 0.44f), style = Stroke(w))
    val n = components.size
    components.forEachIndexed { i, kind ->
        val a = if (n == 1) 0f else i * 2f * PI.toFloat() / n
        val off = if (n == 1) Offset.Zero else Offset(cos(a) * s * 0.17f, sin(a) * s * 0.09f)
        drawFood(kind, c + off - Offset(0f, s * 0.05f), s * (if (n == 1) 0.62f else 0.42f), alpha)
    }
    if (ready) {
        for (i in 0..2) {
            val a = time * 2f + i * 2.1f
            val p = c + Offset(cos(a) * s * 0.45f, sin(a) * s * 0.28f - s * 0.1f)
            sparkle(p, s * 0.06f, Color(0xFFFDE047).copy(alpha = alpha))
        }
    }
}

internal fun DrawScope.sparkle(p: Offset, r: Float, color: Color) {
    drawLine(color, p + Offset(-r, 0f), p + Offset(r, 0f), r * 0.35f, StrokeCap.Round)
    drawLine(color, p + Offset(0f, -r), p + Offset(0f, r), r * 0.35f, StrokeCap.Round)
}
