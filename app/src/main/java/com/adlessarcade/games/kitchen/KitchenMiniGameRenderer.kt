package com.adlessarcade.games.kitchen

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
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
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextMeasurer
import kotlin.math.PI
import kotlin.math.abs
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

/** Fruchtfleisch bzw. Schnittfläche einer Zutat. */
private fun innerColor(food: ItemKind): Color = when (food) {
    ItemKind.STRAWBERRY -> Color(0xFFFECACA)
    ItemKind.BANANA -> Color(0xFFFEF9C3)
    else -> Color(0xFFFEF3C7)
}

private fun ease(t: Float) = t.coerceIn(0f, 1f).let { it * it * (3f - 2f * it) }

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
    val f = engine.finishProgress

    if (game != null && engine.mamaTimer <= 0f) {
        clipRect(p.left, p.top, p.right, p.bottom) {
            when (game) {
                is Chop -> drawChop(game, food, ::at, ph)
                is Peel -> drawPeel(game, food, ::at, ph, f)
                is Grate -> drawGrate(game, ::at, ph)
                is Crack -> drawCrack(game, task is Task.PanStart, ::at, ph, f)
                is Pour -> drawPour(game, food, ::at, ph, time)
                is Stir -> drawStir(game, food, ::at, ph)
                is Flip -> drawFlipGame(game, food, ::at, ph, f)
                is Heat -> drawHeat(game, ::at, ph, time)
                is PlateUp -> drawPlateUp(game, ::at, ph, f, time)
                is Season -> drawSeason(game, food, ::at, ph)
            }
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

// --- Schneiden: Linien liegen in der Mitte jedes Stücks, Hälften gehen nach dem Schnitt auseinander ---

private fun DrawScope.drawChop(g: Chop, food: ItemKind, at: (Float, Float) -> Offset, ph: Float) {
    val boardColor = Color(0xFFE9B97A)
    val boardTl = at(0.06f, 0.36f)
    val boardBr = at(0.94f, 0.84f)
    outlinedRect(boardTl, Size(boardBr.x - boardTl.x, boardBr.y - boardTl.y), boardColor, ph * 0.01f, ph * 0.05f)
    val y = 0.6f
    val inner = innerColor(food)
    fun opening(i: Int): Float = g.cutTimes[i].let { if (it < 0f) 0f else ease((g.time - it) / 0.2f) }

    if (food == ItemKind.BREAD) {
        // Ein Laib über die ganze Länge; jeder Schnitt öffnet eine Fuge
        val l = at(Chop.START, y - 0.13f)
        val r = at(Chop.END, y + 0.12f)
        outlinedRect(l, Size(r.x - l.x, r.y - l.y), Color(0xFFB45309), ph * 0.01f, ph * 0.08f)
        drawRoundRect(Color(0xFFD97706), l + Offset(ph * 0.03f, ph * 0.02f), Size(r.x - l.x - ph * 0.06f, ph * 0.07f), CornerRadius(ph * 0.04f))
        g.lines.forEachIndexed { i, x ->
            val open = opening(i) * ph * 0.03f
            if (open <= 0f) return@forEachIndexed
            val c = at(x, y)
            drawRect(boardColor, Offset(c.x - open / 2f, l.y - 2f), Size(open, r.y - l.y + 4f))
            drawLine(inner, Offset(c.x - open / 2f, l.y + ph * 0.02f), Offset(c.x - open / 2f, r.y - ph * 0.02f), ph * 0.012f)
            drawLine(inner, Offset(c.x + open / 2f, l.y + ph * 0.02f), Offset(c.x + open / 2f, r.y - ph * 0.02f), ph * 0.012f)
        }
    } else {
        // Ein Stück je Linie, Linie genau durch die Mitte
        g.lines.forEachIndexed { i, x ->
            val c = at(x, y)
            val s = ph * 0.24f
            val open = opening(i) * ph * 0.035f
            if (g.cutTimes[i] < 0f) {
                drawFood(food, c, s)
            } else {
                clipRect(right = c.x) { drawFood(food, c - Offset(open, 0f), s) }
                clipRect(left = c.x) { drawFood(food, c + Offset(open, 0f), s) }
                drawLine(inner, c + Offset(-open, -s * 0.3f), c + Offset(-open, s * 0.35f), ph * 0.014f, StrokeCap.Round)
                drawLine(inner, c + Offset(open, -s * 0.3f), c + Offset(open, s * 0.35f), ph * 0.014f, StrokeCap.Round)
            }
        }
    }
    // Schnittlinien (nur noch offene)
    g.lines.forEachIndexed { i, x ->
        if (g.cutTimes[i] >= 0f) return@forEachIndexed
        drawLine(Outline.copy(alpha = 0.55f), at(x, 0.4f), at(x, 0.8f), ph * 0.008f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(ph * 0.025f, ph * 0.02f)))
    }
    // Messer, das beim Tippen kurz herunterfährt
    val dip = if (g.lastTap >= 0f && g.time - g.lastTap < 0.18f) sin((g.time - g.lastTap) / 0.18f * PI.toFloat()) * 0.2f else 0f
    val k = at(g.knifeX, 0.3f + dip)
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

// --- Schälen: geschälte Stellen zeigen das Fruchtfleisch, Schalenstreifen rollen sich ein und fallen ---

private fun DrawScope.drawPeel(g: Peel, food: ItemKind, at: (Float, Float) -> Offset, ph: Float, f: Float) {
    val skin = if (food == ItemKind.BANANA) Color(0xFFFDE047) else Color(0xFFDC2626)
    val flesh = innerColor(food)
    val tl = at(Peel.LEFT - 0.03f, Peel.TOP - 0.04f)
    val br = at(Peel.RIGHT + 0.03f, Peel.BOTTOM + 0.04f)
    if (food == ItemKind.APPLE) {
        val top = Offset((tl.x + br.x) / 2f, tl.y)
        drawLine(Color(0xFF78350F), top, top + Offset(ph * 0.02f, -ph * 0.07f), ph * 0.02f, StrokeCap.Round)
    }
    outlinedOval(tl, Size(br.x - tl.x, br.y - tl.y), skin, ph * 0.012f)
    val fruit = Path().apply { addOval(Rect(tl, br)) }
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
        // Glanz
        drawOval(Color.White.copy(alpha = 0.35f), tl + Offset(ph * 0.08f, ph * 0.05f), Size(ph * 0.25f, ph * 0.08f))
    }
    drawOval(Outline, tl, Size(br.x - tl.x, br.y - tl.y), style = Stroke(ph * 0.012f))
    // Fallende, eingerollte Schalenstreifen
    for (b in 0 until g.bands) for (s in 0 until Peel.SEGMENTS step 2) {
        val t0 = g.peelTimes[b][s]
        if (t0 < 0f) continue
        val t = g.time - t0
        if (t > 0.8f) continue
        val x = Peel.LEFT + (Peel.RIGHT - Peel.LEFT) * (s + 0.5f) / Peel.SEGMENTS
        val pos = at(x + (s % 3 - 1) * 0.04f * t, g.bandCenter(b) + t * 0.15f + t * t * 0.9f)
        rotate(t * 400f * (if (s % 4 == 0) 1f else -1f), pos) {
            val tl = pos - Offset(ph * 0.035f, ph * 0.028f)
            val sz = Size(ph * 0.07f, ph * 0.056f)
            drawArc(skin.darker(0.45f), 0f, 260f, false, tl, sz, style = Stroke(ph * 0.026f, cap = StrokeCap.Round))
            drawArc(skin, 0f, 260f, false, tl, sz, style = Stroke(ph * 0.016f, cap = StrokeCap.Round))
        }
    }
    if (f > 0f) sparkles(at(0.5f, 0.5f), ph * 0.3f, f, ph)
}

// --- Reiben: Käse wird kleiner, Späne fallen, solange gerieben wird ---

private fun DrawScope.drawGrate(g: Grate, at: (Float, Float) -> Offset, ph: Float) {
    val active = g.lastMove >= 0f && g.time - g.lastMove < 0.25f
    // Häufchen unter der Reibe
    val pile = g.progress
    if (pile > 0f) drawOval(Color(0xFFFDE047), at(0.5f, 0.8f) - Offset(ph * 0.25f * pile, ph * 0.06f * pile), Size(ph * 0.5f * pile + 2f, ph * 0.12f * pile + 2f))
    val tl = at(0.4f, 0.3f)
    val br = at(0.6f, 0.78f)
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
    // Käseblock reibt auf und ab
    val jiggle = if (active) sin(g.time * 40f) * ph * 0.04f else 0f
    val cheeseH = 0.2f * (1f - g.progress) + 0.03f
    outlinedRect(at(0.43f, 0.36f) + Offset(0f, jiggle), Size(ph * 0.25f, ph * cheeseH), Color(0xFFFACC15), ph * 0.008f, ph * 0.02f)
    if (active) {
        for (k in 0 until 10) {
            val phase = (g.time * 2.5f + k * 0.1f) % 1f
            val x = 0.47f + (k % 5) * 0.015f + sin(k + g.time * 5f) * 0.005f
            val p = at(x, 0.6f + phase * 0.2f)
            drawLine(Color(0xFFEAB308), p, p + Offset(ph * 0.025f, ph * 0.01f), ph * 0.01f, StrokeCap.Round)
        }
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

// --- Aufschlagen: Ei bricht in zwei Schalenhälften, Eigelb fällt in Schüssel oder Pfanne ---

private fun DrawScope.drawCrack(g: Crack, intoPan: Boolean, at: (Float, Float) -> Offset, ph: Float, f: Float) {
    val c = at(0.5f, 0.62f)
    if (intoPan) {
        outlinedCircle(c, ph * 0.2f, Color(0xFF292524), ph * 0.01f)
        drawCircle(Color(0xFF44403C), ph * 0.16f, c)
    } else {
        outlinedOval(c - Offset(ph * 0.25f, ph * 0.08f), Size(ph * 0.5f, ph * 0.16f), Color(0xFFDBEAFE), ph * 0.01f)
    }
    val eggAt = at(0.5f, 0.36f)
    val s = ph * 0.22f
    if (g.tapTime < 0f) {
        val wobble = sin(g.time * 6f) * 4f
        rotate(wobble, eggAt) { drawFood(ItemKind.EGG, eggAt, s) }
    } else {
        // Schalenhälften gehen auseinander
        val t = ease(if (f > 0f) f else 1f)
        rotate(-35f * t, eggAt) { clipRect(right = eggAt.x) { drawFood(ItemKind.EGG, eggAt - Offset(ph * 0.08f * t, ph * 0.04f * t), s) } }
        rotate(35f * t, eggAt) { clipRect(left = eggAt.x) { drawFood(ItemKind.EGG, eggAt + Offset(ph * 0.08f * t, -ph * 0.04f * t), s) } }
        // Inhalt fällt
        val fall = ((if (f > 0f) f else 1f) * 1.6f).coerceIn(0f, 1f)
        val drop = eggAt + (c - eggAt) * (fall * fall)
        when {
            intoPan -> drawFood(ItemKind.FRIED_EGG, drop, ph * (0.12f + 0.2f * fall), doneness = 0f)
            fall < 1f -> {
                drawCircle(Color(0xFFF59E0B), ph * 0.035f, drop)
                drawCircle(Color.White.copy(alpha = 0.6f), ph * 0.05f, drop + Offset(0f, -ph * 0.02f), style = Stroke(ph * 0.01f))
            }
            else -> drawCircle(Color(0xFFF59E0B), ph * 0.04f, c)
        }
    }
    if (!intoPan) {
        val body = Path().apply {
            moveTo(c.x - ph * 0.25f, c.y)
            quadraticTo(c.x, c.y + ph * 0.3f, c.x + ph * 0.25f, c.y)
            close()
        }
        outlined(body, Color(0xFF3B82F6), ph * 0.01f)
    }
    gaugeH(at, ph, 0.86f, g.zoneCenter - g.zoneHalf, g.zoneCenter + g.zoneHalf, g.pointer)
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
    // Oberfläche wellt sich, solange gegossen wird
    val surfaceY = tl.y + h * (1f - g.level)
    val wave = if (g.pouring) ph * 0.008f else 0f
    val fill = Path().apply {
        moveTo(tl.x + 3f, tl.y + h - 3f)
        lineTo(tl.x + 3f, surfaceY)
        for (i in 0..10) {
            val x = tl.x + 3f + (w - 6f) * i / 10f
            lineTo(x, surfaceY + sin(time * 20f + i) * wave)
        }
        lineTo(tl.x + w - 3f, tl.y + h - 3f)
        close()
    }
    drawPath(fill, liquid)
    drawLine(Outline.copy(alpha = 0.25f), Offset(tl.x + 3f, surfaceY), Offset(tl.x + w - 3f, surfaceY), 3f)
    val ty = tl.y + h * (1f - g.target)
    drawRect(Good.copy(alpha = 0.2f), Offset(tl.x, tl.y + h * (1f - g.target - g.tolerance * 0.4f)), Size(w, h * g.tolerance * 0.8f))
    drawLine(Bad, Offset(tl.x - ph * 0.04f, ty), Offset(tl.x + w + ph * 0.04f, ty), ph * 0.01f)
    // Ausgießendes Gefäß links oben
    val source = at(0.36f, 0.3f)
    rotate(if (g.pouring) 50f else 15f, source) { drawFood(food, source, ph * 0.26f) }
    if (g.pouring) {
        val from = source + Offset(ph * 0.12f, ph * 0.04f)
        val to = Offset(tl.x + w * 0.4f, surfaceY)
        drawLine(liquid.darker(0.08f), from, to, ph * 0.03f + sin(time * 30f) * 2f, StrokeCap.Round)
        for (k in 0..2) {
            val a = time * 9f + k * 2.1f
            drawCircle(liquid.darker(0.05f), ph * 0.012f, to + Offset(cos(a) * ph * 0.04f, -abs(sin(a)) * ph * 0.04f))
        }
    }
}

private fun DrawScope.drawStir(g: Stir, food: ItemKind, at: (Float, Float) -> Offset, ph: Float) {
    val c = at(0.5f, 0.5f) // gleiche Mitte wie Stir.angle()
    val r = ph * 0.27f
    outlinedCircle(c, r * 1.1f, Color(0xFF3B82F6), ph * 0.012f)
    val batter = if (food == ItemKind.BATTER) Color(0xFFFDE68A) else Color(0xFFFACC15)
    drawCircle(batter, r * 0.95f, c)
    // Zutaten-Kleckse verschwinden mit dem Rühren
    val mix = g.progress
    if (mix < 1f) {
        for (k in 0 until 6) {
            val a = g.turned * 0.9f + k * 1.05f
            val rr = r * (0.3f + 0.1f * (k % 3))
            val blob = listOf(Color.White, Color(0xFFF5F5F4), Color(0xFFF59E0B))[k % 3]
            drawCircle(blob.copy(alpha = 1f - mix), r * 0.12f, c + Offset(cos(a) * rr, sin(a) * rr))
        }
    }
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

// --- Wenden: Unterseite bräunt (am Rand sichtbar), beim Wischen springt das Stück hoch und dreht sich ---

private fun DrawScope.drawFlipGame(g: Flip, food: ItemKind, at: (Float, Float) -> Offset, ph: Float, f: Float) {
    val c = at(0.5f, 0.52f)
    val r = ph * 0.26f
    drawLine(Outline, c + Offset(r * 0.9f, r * 0.3f), c + Offset(r * 2.0f, r * 0.7f), ph * 0.05f, StrokeCap.Round)
    outlinedCircle(c, r, Color(0xFF292524), ph * 0.012f)
    drawCircle(Color(0xFF44403C), r * 0.85f, c)
    val under = g.browning / 0.67f // goldbraun im Fenster ≈ 1
    val top = 0.15f
    val flipping = g.flipTime >= 0f
    if (!flipping) {
        drawFood(food, c, r * 1.5f, doneness = top, garnish = false)
        // Unterseite am Rand
        drawCircle(cookColor(Color(0xFFFEF3C7), Color(0xFFE6A85C), under), r * 0.72f, c, style = Stroke(ph * 0.012f))
        // Brutzel-Bläschen
        for (k in 0 until 5) {
            val a = g.time * 4f + k * 1.3f
            drawCircle(Color.White.copy(alpha = 0.4f), ph * 0.008f, c + Offset(cos(a) * r * 0.5f, sin(a * 1.4f) * r * 0.4f))
        }
    } else {
        // Sprung: hoch, einmal um die Querachse, wieder in die Pfanne
        val t = if (f > 0f) f else 1f
        val lift = sin(t * PI.toFloat()) * ph * 0.32f
        val pos = c - Offset(0f, lift)
        val turn = cos(t * PI.toFloat())
        val shownDoneness = if (t < 0.5f) top else under
        scale(1f, abs(turn).coerceAtLeast(0.08f), pos) {
            drawFood(food, pos, r * 1.5f, doneness = shownDoneness, garnish = false)
        }
        // Schatten in der Pfanne
        drawOval(Color.Black.copy(alpha = 0.25f * (1f - lift / (ph * 0.32f))), c - Offset(r * 0.6f, r * 0.15f), Size(r * 1.2f, r * 0.3f))
    }
    // Pfeil nach oben
    if (!flipping) {
        val arrow = at(0.82f, 0.5f)
        drawLine(Good, arrow + Offset(0f, ph * 0.12f), arrow - Offset(0f, ph * 0.1f), ph * 0.02f, StrokeCap.Round)
        drawLine(Good, arrow - Offset(0f, ph * 0.1f), arrow + Offset(-ph * 0.05f, -ph * 0.04f), ph * 0.02f, StrokeCap.Round)
        drawLine(Good, arrow - Offset(0f, ph * 0.1f), arrow + Offset(ph * 0.05f, -ph * 0.04f), ph * 0.02f, StrokeCap.Round)
    }
    gaugeH(at, ph, 0.84f, g.windowStart, g.windowEnd, g.browning)
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
    // Eimasse stockt nach und nach
    drawFood(ItemKind.OMELETTE, c, r * 1.3f, doneness = g.timeShare)
    if (g.temperature > g.zoneCenter + g.zoneHalf) {
        for (k in 0..2) {
            val x = c.x + (k - 1) * r * 0.4f
            val rise = (time * 1.5f + k * 0.3f) % 1f
            drawCircle(Color(0xFF9CA3AF).copy(alpha = 0.5f * (1f - rise)), r * 0.12f * (1f + rise), Offset(x, c.y - r * 0.4f - rise * r))
        }
    }
    // Thermometer
    val tTop = at(0.75f, 0.28f)
    val tH = ph * 0.5f
    val tW = ph * 0.07f
    outlinedRect(tTop, Size(tW, tH), Color.White, ph * 0.008f, tW / 2f)
    drawRect(Good.copy(alpha = 0.5f), Offset(tTop.x + 2f, tTop.y + tH * (1f - g.zoneCenter - g.zoneHalf)), Size(tW - 4f, tH * g.zoneHalf * 2f))
    drawRoundRect(Bad, Offset(tTop.x + tW * 0.25f, tTop.y + tH * (1f - g.temperature)), Size(tW * 0.5f, tH * g.temperature), CornerRadius(tW * 0.25f))
    outlinedCircle(Offset(tTop.x + tW / 2f, tTop.y + tH + tW * 0.4f), tW * 0.6f, Bad, ph * 0.008f)
}

// --- Anrichten: jeder Platz zeigt blass, was dorthin gehört ---

private fun DrawScope.drawPlateUp(g: PlateUp, at: (Float, Float) -> Offset, ph: Float, f: Float, time: Float) {
    val c = at(PlateUp.CENTER_X, PlateUp.CENTER_Y)
    outlinedOval(c - Offset(ph * 0.42f, ph * 0.36f), Size(ph * 0.84f, ph * 0.72f), Color.White, ph * 0.012f)
    drawOval(Color(0xFFE5E7EB), c - Offset(ph * 0.32f, ph * 0.27f), Size(ph * 0.64f, ph * 0.54f), style = Stroke(ph * 0.008f))
    // Ablage links
    val trayTl = at(0.04f, 0.18f)
    val trayBr = at(0.28f, 0.88f)
    outlinedRect(trayTl, Size(trayBr.x - trayTl.x, trayBr.y - trayTl.y), Color(0xFFE9B97A), ph * 0.008f, ph * 0.03f)
    val pieceSize = ph * 0.16f
    // Plätze: blasse Vorschau der passenden Zutat, leicht pulsierend
    val pulse = 0.28f + 0.1f * sin(time * 4f)
    g.spots.forEach { spot ->
        if (spot.filled) return@forEach
        val p = at(spot.x, spot.y)
        drawFood(spot.kind, p, pieceSize, alpha = pulse)
        drawCircle(Outline.copy(alpha = 0.4f), pieceSize * 0.6f, p, style = Stroke(ph * 0.006f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(ph * 0.02f, ph * 0.015f))))
    }
    // Stücke; abgelegte hüpfen kurz
    g.pieces.forEach { piece ->
        val hop = if (piece.placedAt >= 0f && g.time - piece.placedAt < 0.25f) sin((g.time - piece.placedAt) / 0.25f * PI.toFloat()) * ph * 0.04f else 0f
        val p = at(piece.x, piece.y) - Offset(0f, hop)
        if (piece.placed == null) drawOval(Color.Black.copy(alpha = 0.12f), p + Offset(-pieceSize * 0.35f, pieceSize * 0.3f), Size(pieceSize * 0.7f, pieceSize * 0.18f))
        drawFood(piece.kind, p, pieceSize)
    }
    if (f > 0f) sparkles(c, ph * 0.4f, f, ph)
}

// --- Würzen: Streuer kippt bei jedem Tippen, Körnchen rieseln aufs Essen ---

private fun DrawScope.drawSeason(g: Season, food: ItemKind, at: (Float, Float) -> Offset, ph: Float) {
    val c = at(0.5f, 0.64f)
    outlinedOval(c - Offset(ph * 0.3f, ph * 0.12f), Size(ph * 0.6f, ph * 0.24f), Color.White, ph * 0.01f)
    drawFood(food, c, ph * 0.28f)
    // Liegende Körnchen
    val grains = (g.amount * 30).toInt()
    for (i in 0 until grains) {
        val a = i * 2.4f
        drawCircle(Color(0xFF475569), ph * 0.005f, c + Offset(cos(a) * ph * 0.12f * (i % 5 + 1) / 5f, sin(a) * ph * 0.04f * (i % 4 + 1) / 4f))
    }
    // Rieselnde Körnchen der letzten Schüttler
    val s = at(0.5f, 0.3f)
    for (t0 in g.shakes) {
        val t = g.time - t0
        if (t < 0f || t > 0.45f) continue
        for (k in 0 until 6) {
            val p = s + Offset((k - 2.5f) * ph * 0.012f * (1f + t * 3f), ph * 0.1f + (c.y - s.y - ph * 0.1f) * (t / 0.45f) * (0.8f + k % 3 * 0.1f))
            drawCircle(Color(0xFF475569), ph * 0.006f, p)
        }
    }
    // Streuer: kippt kurz nach jedem Tippen
    val last = g.shakes.lastOrNull()
    val tilt = if (last != null && g.time - last < 0.15f) 155f else 180f
    rotate(tilt, s) {
        outlinedRect(s - Offset(ph * 0.05f, ph * 0.08f), Size(ph * 0.1f, ph * 0.16f), Color(0xFFF8FAFC), ph * 0.008f, ph * 0.02f)
        outlinedRect(s - Offset(ph * 0.05f, ph * 0.11f), Size(ph * 0.1f, ph * 0.04f), Color(0xFF94A3B8), ph * 0.008f, ph * 0.01f)
    }
    gaugeH(at, ph, 0.88f, g.target - g.tolerance, g.target + g.tolerance, g.amount)
}

/** Glitzer zum Abschluss eines Schritts. */
private fun DrawScope.sparkles(c: Offset, radius: Float, f: Float, ph: Float) {
    for (i in 0 until 6) {
        val a = i * PI.toFloat() / 3f + f * 2f
        val p = c + Offset(cos(a) * radius * (0.6f + 0.4f * f), sin(a) * radius * 0.7f * (0.6f + 0.4f * f))
        sparkle(p, ph * 0.035f * (1f - f * 0.5f), Color(0xFFFACC15).copy(alpha = 1f - f * 0.6f))
    }
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
