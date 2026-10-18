package com.adlessarcade.games.kitchen

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextMeasurer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Welche Zutat die Nahaufnahme zeigt. */
private fun Task.food(engine: KitchenEngine): ItemKind = when (this) {
    is Task.BoardPrep -> item.kind
    is Task.BowlAdd -> item.kind
    is Task.BowlMix -> output
    is Task.PanStart -> item.kind
    is Task.PanFlip -> engine.pans[stove].rule?.output ?: ItemKind.PANCAKE
    is Task.Plating -> KitchenCatalog.dish(dishId).components.first()
}

internal fun DrawScope.drawMiniGameOverlay(layout: KitchenLayout, engine: KitchenEngine, time: Float, tm: TextMeasurer) {
    val task = engine.task ?: return
    val game = engine.miniGame
    // Küche abdunkeln, Bestellungen bleiben sichtbar
    drawRect(Color.Black.copy(alpha = 0.45f), Offset(0f, layout.kitchen.top), Size(size.width, layout.kitchen.height))
    val p = layout.panel
    val ph = p.height
    drawRoundRect(Color.Black.copy(alpha = 0.2f), p.topLeft + Offset(ph * 0.015f, ph * 0.02f), p.size, CornerRadius(ph * 0.06f))
    outlinedRect(p.topLeft, p.size, Color(0xFFFFFBEB), ph * 0.012f, ph * 0.06f)

    fun at(x: Float, y: Float) = layout.fromPanel(x, y)
    val food = task.food(engine)

    if (game != null && engine.mamaTimer <= 0f) {
        when (game) {
            is Chop -> drawChop(game, food, ::at, ph)
            is Peel -> drawPeel(game, food, ::at, ph)
            is Grate -> drawGrate(game, ::at, ph)
            is Crack -> drawCrack(game, task is Task.PanStart, ::at, ph)
            is Pour -> drawPour(game, food, ::at, ph, time)
            is Stir -> drawStir(game, food, ::at, ph)
            is Flip -> drawFlipGame(game, food, ::at, ph)
            is Heat -> drawHeat(game, ::at, ph, time)
            is PlateUp -> drawPlateUp(game, task, ::at, ph)
            is Season -> drawSeason(game, ::at, ph, time)
        }
        // Titel, Hinweis, Zeitbalken
        label(tm, game.kind.title, at(0.5f, 0.07f), ph * 0.07f, Outline)
        label(tm, game.kind.hint, at(0.5f, 0.15f), ph * 0.042f, Outline.copy(alpha = 0.7f), bold = false, maxWidth = p.width * 0.8f)
        val barW = p.width * 0.5f
        val tl = at(0.25f, 0.93f)
        drawRoundRect(Color(0xFFE5E7EB), tl, Size(barW, ph * 0.025f), CornerRadius(ph * 0.012f))
        drawRoundRect(lerp(Good, Bad, game.timeShare), tl, Size(barW * (1f - game.timeShare), ph * 0.025f), CornerRadius(ph * 0.012f))
    }
    if (engine.mamaTimer > 0f) drawMama(layout, engine.mamaTimer, food, tm, time)
}

private fun DrawScope.drawChop(g: Chop, food: ItemKind, at: (Float, Float) -> Offset, ph: Float) {
    val boardTl = at(0.06f, 0.36f)
    val boardBr = at(0.94f, 0.84f)
    outlinedRect(boardTl, Size(boardBr.x - boardTl.x, boardBr.y - boardTl.y), Color(0xFFE9B97A), ph * 0.01f, ph * 0.05f)
    // Zutat als Reihe über die ganze Länge
    val n = 4
    for (i in 0 until n) {
        val x = Chop.START + (Chop.END - Chop.START) * (i + 0.5f) / n
        drawFood(food, at(x, 0.6f), ph * 0.3f)
    }
    g.lines.forEachIndexed { i, x ->
        val top = at(x, 0.4f)
        val bottom = at(x, 0.8f)
        val cut = g.cuts[i]
        if (cut == null) {
            drawLine(Outline.copy(alpha = 0.6f), top, bottom, ph * 0.008f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(ph * 0.025f, ph * 0.02f)))
        } else {
            drawLine(Color.White, top, bottom, ph * 0.014f)
            drawLine(if (cut > 0.85f) Good else Warn, top, bottom, ph * 0.006f)
        }
    }
    // Messer
    val k = at(g.knifeX, 0.3f)
    val blade = Path().apply {
        moveTo(k.x - ph * 0.02f, k.y)
        lineTo(k.x + ph * 0.02f, k.y)
        lineTo(k.x + ph * 0.02f, k.y + ph * 0.2f)
        lineTo(k.x - ph * 0.02f, k.y + ph * 0.17f)
        close()
    }
    outlined(blade, Color(0xFFE5E7EB), ph * 0.006f)
    outlinedRect(Offset(k.x - ph * 0.025f, k.y - ph * 0.1f), Size(ph * 0.05f, ph * 0.1f), Color(0xFF7C2D12), ph * 0.006f, ph * 0.015f)
}

private fun DrawScope.drawPeel(g: Peel, food: ItemKind, at: (Float, Float) -> Offset, ph: Float) {
    val skin = if (food == ItemKind.BANANA) Color(0xFFFDE047) else Color(0xFFDC2626)
    val flesh = if (food == ItemKind.BANANA) Color(0xFFFEF9C3) else Color(0xFFFEF3C7)
    val tl = at(Peel.LEFT - 0.03f, Peel.TOP - 0.04f)
    val br = at(Peel.RIGHT + 0.03f, Peel.BOTTOM + 0.04f)
    outlinedOval(tl, Size(br.x - tl.x, br.y - tl.y), skin, ph * 0.012f)
    // Geschälte Stellen nur innerhalb der Frucht
    val fruit = Path().apply { addOval(androidx.compose.ui.geometry.Rect(tl, br)) }
    clipPath(fruit) {
        for (b in 0 until g.bands) {
            val yTop = Peel.TOP + (Peel.BOTTOM - Peel.TOP) * b / g.bands
            val yBottom = Peel.TOP + (Peel.BOTTOM - Peel.TOP) * (b + 1) / g.bands
            for (s in 0 until Peel.SEGMENTS) {
                if (!g.peeled[b][s]) continue
                val x0 = Peel.LEFT + (Peel.RIGHT - Peel.LEFT) * s / Peel.SEGMENTS
                val x1 = Peel.LEFT + (Peel.RIGHT - Peel.LEFT) * (s + 1) / Peel.SEGMENTS
                val a = at(x0, yTop + 0.01f)
                val c = at(x1, yBottom - 0.01f)
                drawRect(flesh, a, Size(c.x - a.x + 1f, c.y - a.y))
            }
            if (b > 0) drawLine(skin.darker(0.3f), at(Peel.LEFT, yTop), at(Peel.RIGHT, yTop), ph * 0.005f)
    }
    }
    drawOval(Outline, tl, Size(br.x - tl.x, br.y - tl.y), style = Stroke(ph * 0.012f))
}

private fun DrawScope.drawGrate(g: Grate, at: (Float, Float) -> Offset, ph: Float) {
    // Reibe
    val tl = at(0.4f, 0.3f)
    val br = at(0.6f, 0.82f)
    val body = Path().apply {
        moveTo(tl.x + (br.x - tl.x) * 0.15f, tl.y)
        lineTo(br.x - (br.x - tl.x) * 0.15f, tl.y)
        lineTo(br.x, br.y)
        lineTo(tl.x, br.y)
        close()
    }
    outlined(body, Color(0xFFCBD5E1), ph * 0.01f)
    for (row in 0 until 6) for (col in 0 until 4) {
        val p = Offset(tl.x + (br.x - tl.x) * (0.25f + col * 0.17f), tl.y + (br.y - tl.y) * (0.15f + row * 0.13f))
        drawOval(Color(0xFF64748B), p - Offset(ph * 0.012f, ph * 0.006f), Size(ph * 0.024f, ph * 0.012f))
    }
    // Käse schrumpft, Häufchen wächst
    val cheeseH = 0.18f * (1f - g.progress) + 0.03f
    outlinedRect(at(0.43f, 0.32f - cheeseH * 0.3f), Size(ph * 0.25f, ph * cheeseH), Color(0xFFFACC15), ph * 0.008f, ph * 0.02f)
    val pile = g.progress
    if (pile > 0f) {
        drawOval(Color(0xFFFDE047), at(0.68f - 0.06f * pile, 0.8f - 0.08f * pile), Size(ph * 0.4f * pile + 2f, ph * 0.14f * pile + 2f))
    }
    val ind = at(0.85f, 0.3f)
    drawRoundRect(Color(0xFFE5E7EB), ind, Size(ph * 0.05f, ph * 0.5f), CornerRadius(ph * 0.02f))
    drawRoundRect(Good, ind + Offset(0f, ph * 0.5f * (1f - g.progress)), Size(ph * 0.05f, ph * 0.5f * g.progress), CornerRadius(ph * 0.02f))
}

private fun DrawScope.gaugeH(at: (Float, Float) -> Offset, ph: Float, y: Float, zoneFrom: Float, zoneTo: Float, value: Float, x0: Float = 0.15f, x1: Float = 0.85f) {
    val tl = at(x0, y)
    val br = at(x1, y)
    val w = br.x - tl.x
    val h = ph * 0.06f
    outlinedRect(tl - Offset(0f, h / 2f), Size(w, h), Color(0xFFFECACA), ph * 0.008f, h / 2f)
    drawRect(Good, Offset(tl.x + w * zoneFrom.coerceIn(0f, 1f), tl.y - h / 2f + 2f), Size(w * (zoneTo - zoneFrom).coerceIn(0f, 1f), h - 4f))
    val px = tl.x + w * value.coerceIn(0f, 1f)
    val tri = Path().apply {
        moveTo(px, tl.y - h * 0.3f)
        lineTo(px - h * 0.4f, tl.y - h * 1.1f)
        lineTo(px + h * 0.4f, tl.y - h * 1.1f)
        close()
    }
    outlined(tri, Color(0xFF1E293B), ph * 0.005f)
    drawLine(Color(0xFF1E293B), Offset(px, tl.y - h / 2f), Offset(px, tl.y + h / 2f), ph * 0.008f)
}

private fun DrawScope.drawCrack(g: Crack, intoPan: Boolean, at: (Float, Float) -> Offset, ph: Float) {
    // Ziel: Schüssel oder Pfanne
    val c = at(0.5f, 0.62f)
    if (intoPan) {
        outlinedCircle(c, ph * 0.18f, Color(0xFF292524), ph * 0.01f)
    } else {
        outlinedOval(c - Offset(ph * 0.25f, ph * 0.08f), Size(ph * 0.5f, ph * 0.16f), Color(0xFFDBEAFE), ph * 0.01f)
        val body = Path().apply {
            moveTo(c.x - ph * 0.25f, c.y)
            quadraticTo(c.x, c.y + ph * 0.3f, c.x + ph * 0.25f, c.y)
            close()
        }
        outlined(body, Color(0xFF3B82F6), ph * 0.01f)
    }
    val wobble = sin(g.time * 6f) * 4f
    rotate(wobble, at(0.5f, 0.36f)) { drawFood(ItemKind.EGG, at(0.5f, 0.36f), ph * 0.22f) }
    gaugeH(at, ph, 0.84f, g.zoneCenter - g.zoneHalf, g.zoneCenter + g.zoneHalf, g.pointer)
}

private fun DrawScope.drawPour(g: Pour, food: ItemKind, at: (Float, Float) -> Offset, ph: Float, time: Float) {
    // Gefäß (Messbecher) mit Füllstand und Ziellinie
    val tl = at(0.55f, 0.32f)
    val w = ph * 0.32f
    val h = ph * 0.5f
    val liquid = when (food) {
        ItemKind.FLOUR -> Color(0xFFF5F5F4)
        ItemKind.BATTER -> Color(0xFFFDE68A)
        else -> Color(0xFFF8FAFC)
    }
    outlinedRect(tl, Size(w, h), Color(0xFFE0F2FE).copy(alpha = 0.6f), ph * 0.01f, ph * 0.03f)
    drawRect(liquid, Offset(tl.x + 3f, tl.y + h * (1f - g.level)), Size(w - 6f, h * g.level - 3f))
    drawRect(Outline.copy(alpha = 0.25f), Offset(tl.x + 3f, tl.y + h * (1f - g.level)), Size(w - 6f, 3f))
    val ty = tl.y + h * (1f - g.target)
    drawLine(Bad, Offset(tl.x - ph * 0.04f, ty), Offset(tl.x + w + ph * 0.04f, ty), ph * 0.01f)
    // Toleranzband
    drawRect(Good.copy(alpha = 0.2f), Offset(tl.x, tl.y + h * (1f - g.target - g.tolerance * 0.4f)), Size(w, h * g.tolerance * 0.8f))
    // Ausgießendes Gefäß links oben
    val source = at(0.36f, 0.3f)
    rotate(if (g.pouring) 50f else 15f, source) { drawFood(if (food == ItemKind.BATTER) ItemKind.BATTER else food, source, ph * 0.26f) }
    if (g.pouring) {
        val from = source + Offset(ph * 0.12f, ph * 0.04f)
        val to = Offset(tl.x + w * 0.4f, tl.y + h * (1f - g.level))
        drawLine(liquid.darker(0.08f), from, to, ph * 0.03f + sin(time * 30f) * 2f, StrokeCap.Round)
    }
}

private fun DrawScope.drawStir(g: Stir, food: ItemKind, at: (Float, Float) -> Offset, ph: Float) {
    val c = at(0.5f, 0.5f) // gleiche Mitte wie Stir.angle()
    val r = ph * 0.27f
    outlinedCircle(c, r * 1.1f, Color(0xFF3B82F6), ph * 0.012f)
    val batter = if (food == ItemKind.BATTER) Color(0xFFFDE68A) else Color(0xFFFACC15)
    drawCircle(batter, r * 0.95f, c)
    // Wirbel, der mit dem Rühren mitdreht
    val swirl = Path()
    for (i in 0..40) {
        val t = i / 40f
        val a = g.turned + t * 4f * PI.toFloat()
        val rr = r * 0.85f * t
        val p = c + Offset(cos(a) * rr, sin(a) * rr)
        if (i == 0) swirl.moveTo(p.x, p.y) else swirl.lineTo(p.x, p.y)
    }
    drawPath(swirl, batter.darker(0.15f), style = Stroke(ph * 0.01f, cap = StrokeCap.Round))
    // Kochlöffel zeigt in Rührrichtung
    val a = g.turned
    val tip = c + Offset(cos(a) * r * 0.6f, sin(a) * r * 0.6f)
    drawLine(Outline, tip, tip + Offset(ph * 0.06f, -ph * 0.32f), ph * 0.04f, StrokeCap.Round)
    drawLine(Color(0xFFD97706), tip, tip + Offset(ph * 0.06f, -ph * 0.32f), ph * 0.026f, StrokeCap.Round)
    outlinedOval(tip - Offset(ph * 0.035f, ph * 0.025f), Size(ph * 0.07f, ph * 0.05f), Color(0xFFD97706), ph * 0.006f)
    // Fortschrittsring und Tempo-Anzeige
    drawArc(Good, -90f, 360f * g.progress, false, c - Offset(r * 1.25f, r * 1.25f), Size(r * 2.5f, r * 2.5f), style = Stroke(ph * 0.02f, cap = StrokeCap.Round))
    val maxShown = g.maxSpeed * 1.5f
    gaugeH(at, ph, 0.8f, g.minSpeed / maxShown, g.maxSpeed / maxShown, g.speed / maxShown, 0.72f, 0.95f)
}

private fun DrawScope.drawFlipGame(g: Flip, food: ItemKind, at: (Float, Float) -> Offset, ph: Float) {
    val c = at(0.5f, 0.5f)
    val r = ph * 0.26f
    drawLine(Outline, c + Offset(r * 0.9f, r * 0.3f), c + Offset(r * 2.0f, r * 0.7f), ph * 0.05f, StrokeCap.Round)
    outlinedCircle(c, r, Color(0xFF292524), ph * 0.012f)
    val tint = when {
        g.browning < 0.55f -> lerp(Color(0xFFFEF3C7), Color(0xFFE6A85C), g.browning / 0.55f)
        else -> lerp(Color(0xFFE6A85C), Color(0xFF1C1917), ((g.browning - 0.55f) / 0.45f).coerceIn(0f, 1f))
    }
    if (food == ItemKind.TOAST) {
        outlinedRect(c - Offset(r * 0.55f, r * 0.55f), Size(r * 1.1f, r * 1.1f), tint, ph * 0.008f, r * 0.2f)
    } else {
        outlinedCircle(c, r * 0.75f, tint, ph * 0.008f)
    }
    // Pfeil nach oben
    val arrow = at(0.82f, 0.5f)
    drawLine(Good, arrow + Offset(0f, ph * 0.12f), arrow - Offset(0f, ph * 0.1f), ph * 0.02f, StrokeCap.Round)
    drawLine(Good, arrow - Offset(0f, ph * 0.1f), arrow + Offset(-ph * 0.05f, -ph * 0.04f), ph * 0.02f, StrokeCap.Round)
    drawLine(Good, arrow - Offset(0f, ph * 0.1f), arrow + Offset(ph * 0.05f, -ph * 0.04f), ph * 0.02f, StrokeCap.Round)
    gaugeH(at, ph, 0.82f, g.windowStart, g.windowEnd, g.browning)
}

private fun DrawScope.drawHeat(g: Heat, at: (Float, Float) -> Offset, ph: Float, time: Float) {
    val c = at(0.42f, 0.5f)
    val r = ph * 0.22f
    // Flamme je nach Temperatur
    for (i in -2..2) {
        val h = ph * (0.06f + 0.14f * g.temperature) * (1f + 0.15f * sin(time * 14f + i))
        val base = c + Offset(i * r * 0.35f, r * 1.25f)
        drawPath(Path().apply {
            moveTo(base.x - r * 0.15f, base.y)
            quadraticTo(base.x, base.y - h * 1.4f, base.x + r * 0.15f, base.y)
            close()
        }, if (g.temperature > g.zoneCenter + g.zoneHalf) Bad else Color(0xFFF97316))
    }
    drawLine(Outline, c + Offset(r * 0.9f, r * 0.2f), c + Offset(r * 1.9f, r * 0.55f), ph * 0.05f, StrokeCap.Round)
    outlinedCircle(c, r, Color(0xFF292524), ph * 0.012f)
    drawFood(ItemKind.OMELETTE, c, r * 1.3f)
    // Thermometer
    val tTop = at(0.75f, 0.28f)
    val tH = ph * 0.5f
    val tW = ph * 0.07f
    outlinedRect(tTop, Size(tW, tH), Color.White, ph * 0.008f, tW / 2f)
    drawRect(Good.copy(alpha = 0.5f), Offset(tTop.x + 2f, tTop.y + tH * (1f - g.zoneCenter - g.zoneHalf)), Size(tW - 4f, tH * g.zoneHalf * 2f))
    drawRoundRect(Bad, Offset(tTop.x + tW * 0.25f, tTop.y + tH * (1f - g.temperature)), Size(tW * 0.5f, tH * g.temperature), CornerRadius(tW * 0.25f))
    outlinedCircle(Offset(tTop.x + tW / 2f, tTop.y + tH + tW * 0.4f), tW * 0.6f, Bad, ph * 0.008f)
}

private fun DrawScope.drawPlateUp(g: PlateUp, task: Task, at: (Float, Float) -> Offset, ph: Float) {
    val dish = (task as? Task.Plating)?.let { KitchenCatalog.dish(it.dishId) }
    val c = at(0.6f, 0.5f)
    outlinedOval(c - Offset(ph * 0.4f, ph * 0.33f), Size(ph * 0.8f, ph * 0.66f), Color.White, ph * 0.012f)
    drawOval(Color(0xFFE5E7EB), c - Offset(ph * 0.3f, ph * 0.25f), Size(ph * 0.6f, ph * 0.5f), style = Stroke(ph * 0.008f))
    // Ablage links
    val trayTl = at(0.04f, 0.18f)
    val trayBr = at(0.28f, 0.88f)
    outlinedRect(trayTl, Size(trayBr.x - trayTl.x, trayBr.y - trayTl.y), Color(0xFFE9B97A), ph * 0.008f, ph * 0.03f)
    val kinds = dish?.components ?: listOf(ItemKind.STRAWBERRY_CUT)
    g.pieces.forEachIndexed { i, piece ->
        val target = at(piece.targetX, piece.targetY)
        if (piece.placed == null) {
            drawCircle(Outline.copy(alpha = 0.35f), ph * 0.06f, target, style = Stroke(ph * 0.006f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(ph * 0.02f, ph * 0.015f))))
        }
        drawFood(kinds[i % kinds.size], at(piece.x, piece.y), ph * 0.16f)
    }
}

private fun DrawScope.drawSeason(g: Season, at: (Float, Float) -> Offset, ph: Float, time: Float) {
    val c = at(0.5f, 0.62f)
    outlinedOval(c - Offset(ph * 0.3f, ph * 0.12f), Size(ph * 0.6f, ph * 0.24f), Color.White, ph * 0.01f)
    drawFood(ItemKind.FRIED_EGG, c, ph * 0.28f)
    // Streuer, der beim Schütteln kippt
    val s = at(0.5f, 0.3f)
    val tilt = if (time % 0.3f < 0.15f && g.amount > 0f) 160f else 180f
    rotate(tilt, s) {
        outlinedRect(s - Offset(ph * 0.05f, ph * 0.08f), Size(ph * 0.1f, ph * 0.16f), Color(0xFFF8FAFC), ph * 0.008f, ph * 0.02f)
        outlinedRect(s - Offset(ph * 0.05f, ph * 0.11f), Size(ph * 0.1f, ph * 0.04f), Color(0xFF94A3B8), ph * 0.008f, ph * 0.01f)
    }
    // Körnchen
    val grains = (g.amount * 30).toInt()
    for (i in 0 until grains) {
        val a = i * 2.4f
        drawCircle(Color(0xFF475569), ph * 0.005f, c + Offset(cos(a) * ph * 0.12f * (i % 5 + 1) / 5f, sin(a) * ph * 0.04f * (i % 4 + 1) / 4f))
    }
    gaugeH(at, ph, 0.86f, g.target - g.tolerance, g.target + g.tolerance, g.amount)
}

/** Mama springt ein: Köchin mit Sprechblase, die den Schritt rettet. */
private fun DrawScope.drawMama(layout: KitchenLayout, timer: Float, food: ItemKind, tm: TextMeasurer, time: Float) {
    val p = layout.panel
    clipRect(p.left, p.top, p.right, p.bottom) { drawMamaInPanel(layout, timer, food, tm, time) }
}

private fun DrawScope.drawMamaInPanel(layout: KitchenLayout, timer: Float, food: ItemKind, tm: TextMeasurer, time: Float) {
    val p = layout.panel
    val ph = p.height
    val t = 1f - timer / KitchenEngine.MAMA_TIME
    val slide = (t * 4f).coerceAtMost(1f)
    val c = Offset(p.right - ph * 0.35f, p.bottom - ph * 0.35f * slide)
    // Körper mit Schürze
    outlinedOval(Offset(c.x - ph * 0.22f, c.y + ph * 0.05f), Size(ph * 0.44f, ph * 0.5f), Color(0xFFF472B6), ph * 0.01f)
    outlinedRect(Offset(c.x - ph * 0.12f, c.y + ph * 0.12f), Size(ph * 0.24f, ph * 0.3f), Color.White, ph * 0.008f, ph * 0.04f)
    // Kopf
    outlinedCircle(c, ph * 0.13f, Color(0xFFFDE2C4), ph * 0.01f)
    drawCircle(Color(0xFFFCA5A5), ph * 0.025f, c + Offset(-ph * 0.07f, ph * 0.04f))
    drawCircle(Color(0xFFFCA5A5), ph * 0.025f, c + Offset(ph * 0.07f, ph * 0.04f))
    drawArc(Outline, 200f, 140f, false, c - Offset(ph * 0.08f, ph * 0.06f), Size(ph * 0.05f, ph * 0.04f), style = Stroke(ph * 0.008f))
    drawArc(Outline, 200f, 140f, false, c + Offset(ph * 0.03f, -ph * 0.06f), Size(ph * 0.05f, ph * 0.04f), style = Stroke(ph * 0.008f))
    drawArc(Outline, 20f, 140f, false, c - Offset(ph * 0.05f, ph * 0.0f), Size(ph * 0.1f, ph * 0.07f), style = Stroke(ph * 0.008f, cap = StrokeCap.Round))
    // Kochmütze
    outlinedRect(Offset(c.x - ph * 0.11f, c.y - ph * 0.17f), Size(ph * 0.22f, ph * 0.06f), Color.White, ph * 0.008f, ph * 0.01f)
    for (dx in listOf(-0.07f, 0f, 0.07f)) outlinedCircle(c + Offset(dx * ph, -ph * 0.22f), ph * 0.07f, Color.White, ph * 0.008f)
    // Gerettete Zutat mit Glitzer
    val fixed = Offset(p.left + p.width * 0.4f, p.top + ph * 0.55f)
    drawFood(food, fixed, ph * 0.3f)
    for (i in 0..3) {
        val a = time * 3f + i * 1.6f
        sparkle(fixed + Offset(cos(a) * ph * 0.22f, sin(a) * ph * 0.15f), ph * 0.03f, Color(0xFFFACC15))
    }
    // Sprechblase
    val bubble = Offset(c.x - ph * 0.55f, c.y - ph * 0.3f)
    outlinedRect(bubble - Offset(ph * 0.3f, ph * 0.08f), Size(ph * 0.6f, ph * 0.16f), Color.White, ph * 0.008f, ph * 0.08f)
    label(tm, "Keine Sorge – Mama hilft!", bubble, ph * 0.04f, Outline, maxWidth = ph * 0.56f)
    label(tm, "0 Punkte für diesen Schritt", Offset(p.left + p.width * 0.4f, p.top + ph * 0.15f), ph * 0.045f, Bad)
}
