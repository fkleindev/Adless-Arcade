package com.adlessarcade.games.kitchen

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import kotlin.math.min
import kotlin.math.sin

internal val Wall = Color(0xFFFFF4E0)
internal val WallLine = Color(0xFFF1DDBB)
internal val Wood = Color(0xFFC8874B)
internal val WoodDark = Color(0xFF9A5B2A)
internal val Good = Color(0xFF22C55E)
internal val Warn = Color(0xFFF59E0B)
internal val Bad = Color(0xFFEF4444)

/** Was gerade gezogen wird: Startstation, Gegenstand (oder Teller) und Fingerposition. */
data class KitchenDrag(val from: Station, val item: Item?, val plate: Plate?, val pos: Offset)

/** Rein visuelle Einblendung (Text, der aufsteigt und verblasst). */
class KitchenEffect(val text: String, val pos: Offset, val color: Color, val lifetime: Float = 1.3f) {
    var age = 0f
    val progress: Float get() = (age / lifetime).coerceIn(0f, 1f)
}

internal fun DrawScope.label(
    tm: TextMeasurer, text: String, center: Offset, size: Float, color: Color,
    bold: Boolean = true, maxWidth: Float? = null, alpha: Float = 1f,
) {
    val style = TextStyle(
        color = color.copy(alpha = color.alpha * alpha),
        fontSize = size.toSp(),
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        textAlign = TextAlign.Center,
    )
    val measured = if (maxWidth != null) {
        tm.measure(text, style, constraints = androidx.compose.ui.unit.Constraints(maxWidth = maxWidth.toInt().coerceAtLeast(1)))
    } else {
        tm.measure(text, style)
    }
    drawText(measured, topLeft = Offset(center.x - measured.size.width / 2f, center.y - measured.size.height / 2f))
}

internal fun DrawScope.drawKitchen(
    layout: KitchenLayout,
    engine: KitchenEngine,
    drag: KitchenDrag?,
    effects: List<KitchenEffect>,
    time: Float,
    tm: TextMeasurer,
) {
    val unit = min(layout.kitchen.height, layout.width * 0.5f)
    drawBackground(layout, unit)
    drawOrders(layout, engine, time, tm)
    drawPantry(layout, unit, tm)
    drawStove(layout, engine, unit, time, tm)
    drawBowl(layout, engine, unit, time, tm)
    drawServe(layout, unit, tm)
    drawBoardStation(layout, engine, unit, tm)
    layout.plates.forEachIndexed { i, r -> drawPlateSpot(r, engine.plates[i], unit, time, tm, dragged = drag?.from == Station.PlateSpot(i)) }
    drawTrash(layout, unit, tm)

    if (drag != null) {
        layout.stationAt(drag.pos)?.let { hovered ->
            val r = layout.rectOf(hovered)
            drawRoundRect(Color(0xFF38BDF8).copy(alpha = 0.25f), r.topLeft, r.size, CornerRadius(unit * 0.04f))
            drawRoundRect(Color(0xFF0EA5E9), r.topLeft, r.size, CornerRadius(unit * 0.04f), style = Stroke(unit * 0.012f))
        }
        val s = unit * 0.22f
        drawOval(Color.Black.copy(alpha = 0.18f), drag.pos + Offset(-s * 0.4f, s * 0.35f), Size(s * 0.8f, s * 0.22f))
        when {
            drag.plate != null -> drawPlate(drag.pos, s * 1.3f, drag.plate.components.map { it.kind }, drag.plate.isReady, time = time)
            drag.item != null -> drawFood(drag.item.kind, drag.pos, s)
        }
    }

    for (e in effects) {
        label(tm, e.text, e.pos - Offset(0f, unit * 0.15f * e.progress), unit * 0.06f, e.color, alpha = 1f - e.progress)
    }
}

private fun DrawScope.drawBackground(layout: KitchenLayout, unit: Float) {
    drawRect(Wall, Offset.Zero, size)
    // Fliesenraster
    val tile = unit * 0.11f
    var x = 0f
    while (x < size.width) {
        drawLine(WallLine, Offset(x, 0f), Offset(x, layout.kitchen.top + layout.kitchen.height * 0.5f), 2f)
        x += tile
    }
    var y = 0f
    while (y < layout.kitchen.top + layout.kitchen.height * 0.5f) {
        drawLine(WallLine, Offset(0f, y), Offset(size.width, y), 2f)
        y += tile
    }
    // Hintere Arbeitsplatte und vordere Theke
    val k = layout.kitchen
    drawRect(Wood.lighter(0.15f), Offset(0f, k.top + k.height * 0.38f), Size(size.width, k.height * 0.12f))
    drawRect(WoodDark, Offset(0f, k.top + k.height * 0.495f), Size(size.width, k.height * 0.015f))
    drawRect(Wood, Offset(0f, k.top + k.height * 0.51f), Size(size.width, k.height * 0.49f))
    for (i in 1..3) {
        val ly = k.top + k.height * (0.51f + i * 0.12f)
        drawLine(WoodDark.copy(alpha = 0.35f), Offset(0f, ly), Offset(size.width, ly), unit * 0.006f)
    }
    // Schiene für die Bons
    val rail = layout.ordersArea
    drawRect(Color(0xFF94A3B8), Offset(0f, rail.top + rail.height * 0.04f), Size(size.width, rail.height * 0.05f))
}

private fun DrawScope.stationLabel(tm: TextMeasurer, r: Rect, text: String, unit: Float) {
    label(tm, text, Offset(r.center.x, r.bottom - unit * 0.035f), unit * 0.042f, Outline.copy(alpha = 0.75f))
}

private fun DrawScope.drawPantry(layout: KitchenLayout, unit: Float, tm: TextMeasurer) {
    val p = layout.pantry
    outlinedRect(p.topLeft, p.size, WoodDark.lighter(0.1f), unit * 0.01f, unit * 0.03f)
    for ((kind, r) in layout.crates) {
        outlinedRect(r.topLeft, r.size, Wood.lighter(0.2f), unit * 0.008f, unit * 0.02f)
        drawLine(WoodDark.copy(alpha = 0.5f), Offset(r.left, r.top + r.height * 0.72f), Offset(r.right, r.top + r.height * 0.72f), unit * 0.006f)
        drawFood(kind, Offset(r.center.x, r.top + r.height * 0.4f), min(r.width, r.height) * 0.62f)
        label(tm, kind.title, Offset(r.center.x, r.top + r.height * 0.86f), unit * 0.034f, Outline)
    }
}

private fun DrawScope.drawStove(layout: KitchenLayout, engine: KitchenEngine, unit: Float, time: Float, tm: TextMeasurer) {
    val s = layout.stove
    outlinedRect(s.topLeft, s.size, Color(0xFF4B5563), unit * 0.01f, unit * 0.03f)
    for (i in 0 until KitchenCatalog.STOVES) {
        val r = layout.burner(i)
        val c = Offset(r.center.x, r.top + r.height * 0.45f)
        val pr = min(r.width, r.height) * 0.33f
        val pan = engine.pans[i]
        val cooking = pan.item != null && pan.item.kind != ItemKind.BURNT
        // Herdplatte mit Glut
        drawCircle(Color(0xFF1F2937), pr * 1.15f, c)
        drawCircle((if (cooking) Color(0xFFEF4444) else Color(0xFF374151)).copy(alpha = if (cooking) 0.5f + 0.2f * sin(time * 6f) else 1f), pr * 1.05f, c, style = Stroke(pr * 0.12f))
        // Pfanne mit Stiel
        drawLine(Outline, c + Offset(pr * 0.8f, pr * 0.5f), c + Offset(pr * 1.55f, pr * 1.0f), pr * 0.22f, StrokeCap.Round)
        drawLine(Color(0xFF292524), c + Offset(pr * 0.8f, pr * 0.5f), c + Offset(pr * 1.55f, pr * 1.0f), pr * 0.14f, StrokeCap.Round)
        outlinedCircle(c, pr, Color(0xFF292524), unit * 0.008f)
        drawCircle(Color(0xFF44403C), pr * 0.82f, c)
        pan.item?.let { item ->
            val shown = if (item.kind == ItemKind.BURNT) ItemKind.BURNT else if (pan.progress >= 0.5f) pan.rule?.output ?: item.kind else item.kind
            drawFood(shown, c, pr * 1.45f)
            // Bräunung: Gerichte werden mit der Zeit dunkler
            if (pan.progress > 1f && item.kind != ItemKind.BURNT) {
                drawCircle(Color(0xFF451A03).copy(alpha = ((pan.progress - 1f) / (KitchenCatalog.BURN_AT - 1f) * 0.6f).coerceIn(0f, 0.6f)), pr * 0.7f, c)
            }
            // Fortschrittsring
            val color = when {
                item.kind == ItemKind.BURNT -> Bad
                pan.isBurning -> lerp(Warn, Bad, 0.5f + 0.5f * sin(time * 10f))
                pan.progress >= 1f -> Good
                else -> Color(0xFF38BDF8)
            }
            val sweep = (pan.progress.coerceAtMost(1f)) * 360f
            drawArc(Color.White.copy(alpha = 0.3f), -90f, 360f, false, c - Offset(pr * 1.25f, pr * 1.25f), Size(pr * 2.5f, pr * 2.5f), style = Stroke(pr * 0.14f))
            drawArc(color, -90f, sweep, false, c - Offset(pr * 1.25f, pr * 1.25f), Size(pr * 2.5f, pr * 2.5f), style = Stroke(pr * 0.14f, cap = StrokeCap.Round))
            when {
                pan.canFlip -> bubble(tm, Offset(c.x, r.top + r.height * 0.06f), "Antippen: wenden!", unit, Warn, time)
                item.kind == ItemKind.BURNT -> bubble(tm, Offset(c.x, r.top + r.height * 0.06f), "Verbrannt!", unit, Bad, time)
                pan.isBurning -> bubble(tm, Offset(c.x, r.top + r.height * 0.06f), "Brennt an!", unit, Bad, time)
                pan.isDone -> bubble(tm, Offset(c.x, r.top + r.height * 0.06f), "Fertig!", unit, Good, time)
            }
        }
    }
    stationLabel(tm, s, "Herd", unit)
}

private fun DrawScope.bubble(tm: TextMeasurer, c: Offset, text: String, unit: Float, color: Color, time: Float) {
    val scale = 1f + 0.06f * sin(time * 8f)
    val w = unit * 0.3f * scale
    val h = unit * 0.075f * scale
    outlinedRect(c - Offset(w / 2f, h / 2f), Size(w, h), color, unit * 0.006f, h / 2f)
    label(tm, text, c, unit * 0.038f, Color.White)
}

private fun DrawScope.drawBowl(layout: KitchenLayout, engine: KitchenEngine, unit: Float, time: Float, tm: TextMeasurer) {
    val r = layout.bowl
    val c = Offset(r.center.x, r.top + r.height * 0.48f)
    val br = min(r.width, r.height) * 0.42f
    val content = engine.bowlProduct?.let {
        if (it.kind == ItemKind.BATTER) Color(0xFFFDE68A) else Color(0xFFFACC15)
    }
    // Schüssel von schräg oben
    outlinedOval(Offset(c.x - br, c.y - br * 0.45f), Size(br * 2f, br * 0.9f), Color(0xFFDBEAFE), unit * 0.008f)
    drawOval(content ?: Color(0xFFBFDBFE), Offset(c.x - br * 0.85f, c.y - br * 0.35f), Size(br * 1.7f, br * 0.7f))
    val body = Path().apply {
        moveTo(c.x - br, c.y)
        quadraticTo(c.x, c.y + br * 1.5f, c.x + br, c.y)
        quadraticTo(c.x, c.y + br * 0.45f, c.x - br, c.y)
        close()
    }
    outlined(body, Color(0xFF3B82F6), unit * 0.008f)
    if (content == null) {
        engine.bowl.forEachIndexed { i, item ->
            drawFood(item.kind, c + Offset((i - (engine.bowl.size - 1) / 2f) * br * 0.55f, -br * 0.05f), br * 0.55f)
        }
    }
    if (engine.bowl.isNotEmpty() && engine.bowlProduct == null && KitchenCatalog.bowlRecipe(engine.bowl.map { it.kind }) != null) {
        bubble(tm, Offset(c.x, r.top + r.height * 0.06f), "Antippen: rühren", unit, Color(0xFF3B82F6), time)
    }
    engine.bowlProduct?.let { label(tm, it.kind.title, Offset(c.x, r.top + r.height * 0.06f), unit * 0.04f, Outline) }
    stationLabel(tm, r, "Schüssel", unit)
}

private fun DrawScope.drawServe(layout: KitchenLayout, unit: Float, tm: TextMeasurer) {
    val r = layout.serve
    outlinedRect(r.topLeft, r.size, Color(0xFFE2E8F0), unit * 0.01f, unit * 0.03f)
    drawRect(Color(0xFF475569), r.topLeft + Offset(r.width * 0.08f, r.height * 0.1f), Size(r.width * 0.84f, r.height * 0.45f))
    // Glocke
    val c = Offset(r.center.x, r.top + r.height * 0.7f)
    val bell = Path().apply {
        moveTo(c.x - unit * 0.06f, c.y + unit * 0.02f)
        quadraticTo(c.x - unit * 0.06f, c.y - unit * 0.07f, c.x, c.y - unit * 0.07f)
        quadraticTo(c.x + unit * 0.06f, c.y - unit * 0.07f, c.x + unit * 0.06f, c.y + unit * 0.02f)
        close()
    }
    outlined(bell, Color(0xFFFACC15), unit * 0.007f)
    drawLine(Outline, c + Offset(-unit * 0.08f, unit * 0.025f), c + Offset(unit * 0.08f, unit * 0.025f), unit * 0.012f, StrokeCap.Round)
    stationLabel(tm, r, "Ausgabe", unit)
}

private fun DrawScope.drawBoardStation(layout: KitchenLayout, engine: KitchenEngine, unit: Float, tm: TextMeasurer) {
    val r = layout.board
    val inset = Rect(r.left + r.width * 0.05f, r.top + r.height * 0.08f, r.right - r.width * 0.05f, r.bottom - r.height * 0.2f)
    outlinedRect(inset.topLeft, inset.size, Color(0xFFE9B97A), unit * 0.009f, unit * 0.04f)
    drawCircle(Color(0xFFC8874B), unit * 0.015f, Offset(inset.right - unit * 0.04f, inset.center.y))
    for (i in 1..2) drawLine(Color(0xFFD9A15E), Offset(inset.left + inset.width * 0.1f, inset.top + inset.height * i / 3f), Offset(inset.right - inset.width * 0.18f, inset.top + inset.height * i / 3f), unit * 0.005f)
    engine.board?.let { drawFood(it.kind, inset.center, min(inset.width, inset.height) * 0.75f) }
    stationLabel(tm, r, "Brett", unit)
}

private fun DrawScope.drawPlateSpot(r: Rect, plate: Plate, unit: Float, time: Float, tm: TextMeasurer, dragged: Boolean) {
    val c = Offset(r.center.x, r.top + r.height * 0.42f)
    drawOval(Color.Black.copy(alpha = 0.12f), Offset(c.x - r.width * 0.4f, c.y - r.height * 0.15f), Size(r.width * 0.8f, r.height * 0.55f))
    drawPlate(c, min(r.width * 0.85f, r.height * 1.2f), plate.components.map { it.kind }, plate.isReady, alpha = if (dragged) 0.3f else 1f, time = time)
    val text = plate.dishId?.let { KitchenCatalog.dish(it).name } ?: "Teller"
    stationLabel(tm, r, text, unit)
}

private fun DrawScope.drawTrash(layout: KitchenLayout, unit: Float, tm: TextMeasurer) {
    val r = layout.trash
    val body = Path().apply {
        moveTo(r.left + r.width * 0.2f, r.top + r.height * 0.22f)
        lineTo(r.right - r.width * 0.2f, r.top + r.height * 0.22f)
        lineTo(r.right - r.width * 0.27f, r.top + r.height * 0.8f)
        lineTo(r.left + r.width * 0.27f, r.top + r.height * 0.8f)
        close()
    }
    outlined(body, Color(0xFF64748B), unit * 0.008f)
    outlinedRect(Offset(r.left + r.width * 0.14f, r.top + r.height * 0.12f), Size(r.width * 0.72f, r.height * 0.1f), Color(0xFF94A3B8), unit * 0.008f, unit * 0.01f)
    for (i in 0..2) {
        val x = r.left + r.width * (0.38f + i * 0.12f)
        drawLine(Color(0xFF475569), Offset(x, r.top + r.height * 0.3f), Offset(x, r.top + r.height * 0.72f), unit * 0.007f)
    }
    stationLabel(tm, r, "Müll", unit)
}

private fun DrawScope.drawOrders(layout: KitchenLayout, engine: KitchenEngine, time: Float, tm: TextMeasurer) {
    val unit = layout.ordersArea.height
    engine.orders.take(KitchenLayout.MAX_TICKETS).forEachIndexed { i, order ->
        val r = layout.ticket(i)
        val urgent = order.timeFraction < 0.25f
        val shake = if (urgent) sin(time * 30f) * unit * 0.01f else 0f
        val tl = r.topLeft + Offset(shake, 0f)
        // Papier mit Klammer
        drawRoundRect(Color.Black.copy(alpha = 0.12f), tl + Offset(unit * 0.02f, unit * 0.03f), r.size, CornerRadius(unit * 0.04f))
        outlinedRect(tl, r.size, Color.White, unit * 0.012f, unit * 0.04f)
        drawRect(Color(0xFF94A3B8), Offset(tl.x + r.width * 0.42f, tl.y - unit * 0.03f), Size(r.width * 0.16f, unit * 0.08f))
        val dish = order.dish
        drawPlate(Offset(tl.x + r.width * 0.25f, tl.y + r.height * 0.45f), min(r.width * 0.42f, r.height * 0.8f), dish.components, ready = false)
        label(tm, dish.name, Offset(tl.x + r.width * 0.69f, tl.y + r.height * 0.22f), unit * 0.12f, Outline, maxWidth = r.width * 0.55f)
        // Komponenten als kleine Symbole
        dish.components.forEachIndexed { k, kind ->
            drawFood(kind, Offset(tl.x + r.width * (0.53f + k * 0.15f), tl.y + r.height * 0.55f), unit * 0.2f)
        }
        // Zeitbalken
        val barTop = tl.y + r.height * 0.8f
        val barW = r.width * 0.88f
        val barX = tl.x + r.width * 0.06f
        drawRoundRect(Color(0xFFE5E7EB), Offset(barX, barTop), Size(barW, r.height * 0.1f), CornerRadius(r.height * 0.05f))
        val f = order.timeFraction
        val color = when {
            f > 0.5f -> Good
            f > 0.25f -> Warn
            else -> Bad
        }
        drawRoundRect(color, Offset(barX, barTop), Size(barW * f, r.height * 0.1f), CornerRadius(r.height * 0.05f))
    }
}
