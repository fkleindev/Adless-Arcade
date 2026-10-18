package com.adlessarcade.games.runetactics

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import com.adlessarcade.ui.theme.ArenaTheme
import com.adlessarcade.ui.theme.CosmeticRegistry
import com.adlessarcade.ui.theme.NeonCyan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sin

internal val StarGold = Color(0xFFFACC15)
internal val WinGreen = Color(0xFF4ADE80)
internal val LossRed = Color(0xFFFF5C6C)
internal val MagicViolet = Color(0xFFC084FC)
internal val ArcadeCyan = NeonCyan

internal fun originColor(trait: Trait): Color = when (trait) {
    Trait.UNDEAD -> Color(0xFFA78BFA)
    Trait.ELF -> Color(0xFF4ADE80)
    Trait.DWARF -> Color(0xFFF59E0B)
    Trait.DEMON -> Color(0xFFF43F5E)
    Trait.ELEMENTAL -> Color(0xFF38BDF8)
    Trait.BEAST -> Color(0xFFD6A35C)
    Trait.BOSS -> Color(0xFFFB923C)
    else -> Color.White
}

internal fun componentColor(id: String): Color = when (id) {
    RuneTacticsItems.SWORD -> Color(0xFFEF4444)
    RuneTacticsItems.BOW -> Color(0xFFFBBF24)
    RuneTacticsItems.ROD -> Color(0xFFA855F7)
    RuneTacticsItems.VEST -> Color(0xFF94A3B8)
    RuneTacticsItems.CLOAK -> Color(0xFF2DD4BF)
    else -> Color(0xFFF97316)
}

/** Item-Symbol: Komponenten mit eigenem Zeichen, fertige Items zweifarbig mit goldenem Rand. */
internal fun DrawScope.drawItemIcon(center: Offset, size: Float, item: ItemDef, alpha: Float = 1f) {
    val half = size / 2f
    val topLeft = Offset(center.x - half, center.y - half)
    val corner = CornerRadius(size * 0.2f)
    if (item.isComponent) {
        drawRoundRect(Color(0xFF1E2130).copy(alpha = alpha), topLeft, Size(size, size), corner)
        drawComponentGlyph(item.id, center, size * 0.36f, componentColor(item.id).copy(alpha = alpha))
        drawRoundRect(componentColor(item.id).copy(alpha = 0.7f * alpha), topLeft, Size(size, size), corner, style = Stroke(size * 0.07f))
    } else {
        val (a, b) = item.recipe
        drawRoundRect(lerp(componentColor(a), Color.Black, 0.35f).copy(alpha = alpha), topLeft, Size(size, size), corner)
        val path = Path().apply {
            moveTo(topLeft.x + size, topLeft.y)
            lineTo(topLeft.x + size, topLeft.y + size)
            lineTo(topLeft.x, topLeft.y + size)
            close()
        }
        drawPath(path, lerp(componentColor(b), Color.Black, 0.35f).copy(alpha = alpha))
        drawComponentGlyph(a, Offset(center.x - size * 0.17f, center.y - size * 0.17f), size * 0.22f, Color.White.copy(alpha = alpha))
        drawComponentGlyph(b, Offset(center.x + size * 0.17f, center.y + size * 0.17f), size * 0.22f, Color.White.copy(alpha = alpha))
        drawRoundRect(StarGold.copy(alpha = alpha), topLeft, Size(size, size), corner, style = Stroke(size * 0.08f))
    }
}

private fun DrawScope.drawComponentGlyph(id: String, c: Offset, s: Float, color: Color) {
    val w = s * 0.28f
    when (id) {
        RuneTacticsItems.SWORD -> {
            drawLine(color, Offset(c.x - s, c.y + s), Offset(c.x + s, c.y - s), w, StrokeCap.Round)
            drawLine(color, Offset(c.x - s * 0.7f, c.y), Offset(c.x, c.y + s * 0.7f), w, StrokeCap.Round)
        }
        RuneTacticsItems.BOW -> {
            drawArc(color, 120f, 120f, false, Offset(c.x - s * 0.4f, c.y - s), Size(s * 2f, s * 2f), style = Stroke(w))
            drawLine(color, Offset(c.x + s * 0.1f, c.y - s * 0.85f), Offset(c.x + s * 0.1f, c.y + s * 0.85f), w * 0.5f)
        }
        RuneTacticsItems.ROD -> {
            drawLine(color, Offset(c.x - s * 0.7f, c.y + s), Offset(c.x + s * 0.3f, c.y - s * 0.3f), w, StrokeCap.Round)
            drawCircle(color, s * 0.38f, Offset(c.x + s * 0.5f, c.y - s * 0.5f))
        }
        RuneTacticsItems.VEST -> {
            val p = Path().apply {
                moveTo(c.x - s * 0.9f, c.y - s * 0.7f)
                lineTo(c.x - s * 0.3f, c.y - s)
                lineTo(c.x + s * 0.3f, c.y - s)
                lineTo(c.x + s * 0.9f, c.y - s * 0.7f)
                lineTo(c.x + s * 0.7f, c.y + s)
                lineTo(c.x - s * 0.7f, c.y + s)
                close()
            }
            drawPath(p, color)
        }
        RuneTacticsItems.CLOAK -> {
            val p = Path().apply {
                moveTo(c.x - s * 0.35f, c.y - s)
                lineTo(c.x + s * 0.35f, c.y - s)
                lineTo(c.x + s, c.y + s)
                lineTo(c.x - s, c.y + s)
                close()
            }
            drawPath(p, color)
        }
        else -> {
            drawRoundRect(color, Offset(c.x - s, c.y - s * 0.4f), Size(s * 2f, s * 0.8f), CornerRadius(s * 0.3f))
            drawRect(Color.Black.copy(alpha = 0.5f), Offset(c.x - s * 0.25f, c.y - s * 0.3f), Size(s * 0.5f, s * 0.6f))
        }
    }
}

internal fun costColor(cost: Int): Color = when (cost) {
    1 -> Color(0xFF9CA3AF)
    2 -> Color(0xFF22C55E)
    3 -> Color(0xFF3B82F6)
    4 -> Color(0xFFA855F7)
    else -> Color(0xFFFACC15)
}

/** Aufteilung der Zeichenfläche: Brett (7×8) oben, Bank (8 Plätze) darunter. */
class TacticsLayout(width: Float, height: Float) {
    val cell: Float
    val boardLeft: Float
    val boardTop: Float
    val benchCell: Float
    val benchLeft: Float
    val benchTop: Float

    init {
        val rows = Board.ROWS + BENCH_GAP + 1.1f
        cell = min(width / Board.COLS, height / rows)
        boardLeft = (width - cell * Board.COLS) / 2f
        boardTop = ((height - cell * rows) / 2f).coerceAtLeast(0f)
        benchCell = min(width / Board.BENCH_SIZE, cell * 1.1f)
        benchLeft = (width - benchCell * Board.BENCH_SIZE) / 2f
        benchTop = boardTop + cell * (Board.ROWS + BENCH_GAP)
    }

    fun center(col: Float, row: Float) = Offset(boardLeft + (col + 0.5f) * cell, boardTop + (row + 0.5f) * cell)

    fun benchCenter(index: Int) = Offset(benchLeft + (index + 0.5f) * benchCell, benchTop + benchCell / 2f)

    /** Feld des gesamten Bretts (auch gegnerische Hälfte) unter dem Punkt. */
    fun cellAt(p: Offset): Cell? {
        val col = floor((p.x - boardLeft) / cell).toInt()
        val row = floor((p.y - boardTop) / cell).toInt()
        return if (Board.inside(col, row)) Cell(col, row) else null
    }

    fun benchAt(p: Offset): Int? {
        if (p.y < benchTop - benchCell * 0.15f || p.y > benchTop + benchCell * 1.15f) return null
        val index = floor((p.x - benchLeft) / benchCell).toInt()
        return index.takeIf { it in 0 until Board.BENCH_SIZE }
    }

    /** Eigener Platz (Brett-Hälfte oder Bank) unter dem Punkt. */
    fun slotAt(p: Offset): Slot? {
        benchAt(p)?.let { return Slot.Bench(it) }
        val cell = cellAt(p) ?: return null
        return if (Board.isPlayerCell(cell)) Slot.Board(cell) else null
    }

    companion object {
        const val BENCH_GAP = 0.35f
    }
}

/** Einheit, die gerade mit dem Finger verschoben wird. */
data class DragInfo(val from: Slot, val unit: UnitInstance, val pos: Offset)

internal fun DrawScope.drawTactics(
    layout: TacticsLayout,
    engine: RuneTacticsEngine,
    effects: List<TacticsEffect>,
    drag: DragInfo?,
    textMeasurer: TextMeasurer,
    arena: ArenaTheme = CosmeticRegistry.arena(""),
) {
    drawBoard(layout, drag, arena)
    drawBench(layout, engine, drag)

    val fight = engine.combat
    if (engine.phase == Phase.COMBAT && fight != null) {
        // Tote zuerst nicht zeichnen; Gegner und eigene gemischt nach Reihe, damit Überlappungen stimmen.
        fight.fighters.filter { it.alive }.sortedBy { it.visualRow }.forEach { f ->
            drawUnit(
                center = layout.center(f.visualCol, f.visualRow),
                size = layout.cell,
                def = f.def,
                star = f.star,
                enemy = f.side == 1,
                hp = f.hp / f.maxHp,
                shield = (f.shield / f.maxHp).coerceAtMost(1f),
                mana = if (f.maxMana > 0f) f.mana / f.maxMana else 0f,
                stunned = f.stun > 0f,
                items = f.items,
            )
        }
    } else {
        engine.enemyArmy.forEach {
            drawUnit(
                layout.center(it.col.toFloat(), it.row.toFloat()), layout.cell, it.def, it.star, enemy = true,
                items = it.items.map(RuneTacticsItems::byId),
            )
        }
        engine.board.forEach { (cell, unit) ->
            val dragged = drag?.from == Slot.Board(cell)
            drawUnit(
                layout.center(cell.col.toFloat(), cell.row.toFloat()), layout.cell, unit.def, unit.star,
                enemy = false, alpha = if (dragged) 0.3f else 1f, items = unit.items.map(RuneTacticsItems::byId),
            )
        }
    }

    drawEffects(layout, effects, textMeasurer)

    if (drag != null) {
        drawUnit(
            drag.pos, layout.cell * 1.15f, drag.unit.def, drag.unit.star, enemy = false, alpha = 0.92f,
            items = drag.unit.items.map(RuneTacticsItems::byId),
        )
    }
}

private fun DrawScope.drawBoard(layout: TacticsLayout, drag: DragInfo?, arena: ArenaTheme) {
    val cell = layout.cell
    val hover = drag?.let { layout.cellAt(it.pos) }
    for (row in 0 until Board.ROWS) {
        for (col in 0 until Board.COLS) {
            val enemyHalf = row < Board.PLAYER_FIRST_ROW
            val dark = (row + col) % 2 == 0
            var color = when {
                enemyHalf && dark -> arena.enemyDark
                enemyHalf -> arena.enemyLight
                dark -> arena.playerDark
                else -> arena.playerLight
            }
            if (drag != null && !enemyHalf) color = lerp(color, ArcadeCyan, 0.06f)
            if (drag != null && hover == Cell(col, row) && !enemyHalf) color = lerp(color, ArcadeCyan, 0.25f)
            drawRoundRect(
                color,
                topLeft = Offset(layout.boardLeft + col * cell + 1f, layout.boardTop + row * cell + 1f),
                size = Size(cell - 2f, cell - 2f),
                cornerRadius = CornerRadius(cell * 0.12f),
            )
        }
    }
    val midY = layout.boardTop + Board.PLAYER_FIRST_ROW * cell
    drawLine(
        arena.divider.copy(alpha = 0.35f),
        Offset(layout.boardLeft, midY), Offset(layout.boardLeft + Board.COLS * cell, midY),
        strokeWidth = cell * 0.04f,
    )
}

private fun DrawScope.drawBench(layout: TacticsLayout, engine: RuneTacticsEngine, drag: DragInfo?) {
    val hover = drag?.let { layout.benchAt(it.pos) }
    for (i in 0 until Board.BENCH_SIZE) {
        val size = layout.benchCell
        val topLeft = Offset(layout.benchLeft + i * size + 3f, layout.benchTop + 3f)
        drawRoundRect(
            if (hover == i) Color(0xFF263247) else Color(0xFF181B28),
            topLeft = topLeft,
            size = Size(size - 6f, size - 6f),
            cornerRadius = CornerRadius(size * 0.18f),
        )
        drawRoundRect(
            Color(0xFF2A2E40),
            topLeft = topLeft,
            size = Size(size - 6f, size - 6f),
            cornerRadius = CornerRadius(size * 0.18f),
            style = Stroke(width = 2f),
        )
        val unit = engine.bench[i] ?: continue
        val dragged = drag?.from == Slot.Bench(i)
        drawUnit(
            layout.benchCenter(i), size * 0.92f, unit.def, unit.star,
            enemy = false, alpha = if (dragged) 0.3f else 1f, items = unit.items.map(RuneTacticsItems::byId),
        )
    }
}

/** Zeichnet eine Figur: Kreis in Herkunftsfarbe, Ring in Kostenfarbe, Symbol der Klasse, Sterne und Balken. */
internal fun DrawScope.drawUnit(
    center: Offset,
    size: Float,
    def: UnitDef,
    star: Int,
    enemy: Boolean,
    hp: Float? = null,
    shield: Float = 0f,
    mana: Float = 0f,
    stunned: Boolean = false,
    alpha: Float = 1f,
    items: List<ItemDef> = emptyList(),
) {
    val r = size * if (def.isBoss) 0.5f else 0.34f
    val origin = originColor(def.origin)
    // Sockel in Seitenfarbe
    drawOval(
        (if (enemy) LossRed else ArcadeCyan).copy(alpha = 0.35f * alpha),
        topLeft = Offset(center.x - r * 1.05f, center.y + r * 0.45f),
        size = Size(r * 2.1f, r * 0.8f),
    )
    drawCircle(lerp(origin, Color.Black, 0.55f).copy(alpha = alpha), r, center)
    drawCircle(costColor(def.cost).copy(alpha = alpha), r, center, style = Stroke(width = r * 0.17f))
    drawRoleGlyph(def.role, center, r * 0.62f, origin.copy(alpha = alpha))

    if (stunned) {
        for (i in 0 until 3) {
            val a = i * 2.1f
            drawCircle(StarGold.copy(alpha = alpha), r * 0.1f, Offset(center.x + cos(a) * r * 0.8f, center.y - r * 1.05f + sin(a) * r * 0.2f))
        }
    }

    // Sterne unter der Figur
    val starSize = r * 0.32f
    val startX = center.x - (star - 1) * starSize * 1.1f
    for (i in 0 until star) {
        drawStar(Offset(startX + i * starSize * 2.2f, center.y + r * 0.95f), starSize, StarGold.copy(alpha = alpha))
    }

    // Items als kleine Symbole rechts neben der Figur
    val itemSize = size * 0.2f
    items.forEachIndexed { i, item ->
        drawItemIcon(
            Offset(center.x + r + itemSize * 0.15f, center.y - r * 0.55f + i * itemSize * 1.05f),
            itemSize, item, alpha,
        )
    }

    if (hp != null) {
        val w = r * 2.2f
        val h = r * 0.24f
        val left = center.x - w / 2f
        val top = center.y - r - h * 2.6f
        drawRect(Color.Black.copy(alpha = 0.7f * alpha), Offset(left - 1f, top - 1f), Size(w + 2f, h + 2f))
        drawRect((if (enemy) LossRed else WinGreen).copy(alpha = alpha), Offset(left, top), Size(w * hp.coerceIn(0f, 1f), h))
        if (shield > 0f) {
            drawRect(Color.White.copy(alpha = 0.8f * alpha), Offset(left, top), Size(w * shield, h * 0.45f))
        }
        val manaTop = top + h + 2f
        drawRect(Color.Black.copy(alpha = 0.7f * alpha), Offset(left - 1f, manaTop - 1f), Size(w + 2f, h * 0.55f + 2f))
        drawRect(Color(0xFF60A5FA).copy(alpha = alpha), Offset(left, manaTop), Size(w * mana.coerceIn(0f, 1f), h * 0.55f))
    }
}

private fun DrawScope.drawRoleGlyph(role: Trait, c: Offset, s: Float, color: Color) {
    when (role) {
        Trait.WARRIOR -> {
            drawLine(color, Offset(c.x - s * 0.6f, c.y + s * 0.6f), Offset(c.x + s * 0.6f, c.y - s * 0.6f), s * 0.22f, StrokeCap.Round)
            drawLine(color, Offset(c.x - s * 0.55f, c.y - s * 0.05f), Offset(c.x - s * 0.05f, c.y + s * 0.45f), s * 0.2f, StrokeCap.Round)
        }
        Trait.GUARDIAN -> {
            val path = Path().apply {
                moveTo(c.x, c.y - s * 0.75f)
                lineTo(c.x + s * 0.65f, c.y - s * 0.45f)
                lineTo(c.x + s * 0.55f, c.y + s * 0.2f)
                quadraticTo(c.x + s * 0.4f, c.y + s * 0.6f, c.x, c.y + s * 0.8f)
                quadraticTo(c.x - s * 0.4f, c.y + s * 0.6f, c.x - s * 0.55f, c.y + s * 0.2f)
                lineTo(c.x - s * 0.65f, c.y - s * 0.45f)
                close()
            }
            drawPath(path, color)
        }
        Trait.RANGER -> {
            // Bogen mit Sehne und Pfeil
            drawArc(color, 110f, 140f, false, Offset(c.x - s * 1.1f, c.y - s * 0.8f), Size(s * 1.6f, s * 1.6f), style = Stroke(s * 0.2f))
            drawLine(color, Offset(c.x - s * 0.55f, c.y - s * 0.75f), Offset(c.x - s * 0.55f, c.y + s * 0.75f), s * 0.1f)
            drawLine(color, Offset(c.x - s * 0.6f, c.y), Offset(c.x + s * 0.75f, c.y), s * 0.16f, StrokeCap.Round)
        }
        Trait.ASSASSIN -> {
            // Zwei gekreuzte Dolche
            drawLine(color, Offset(c.x - s * 0.6f, c.y - s * 0.6f), Offset(c.x + s * 0.6f, c.y + s * 0.6f), s * 0.2f, StrokeCap.Round)
            drawLine(color, Offset(c.x + s * 0.6f, c.y - s * 0.6f), Offset(c.x - s * 0.6f, c.y + s * 0.6f), s * 0.2f, StrokeCap.Round)
        }
        Trait.BEAST -> {
            // Drei Krallenspuren
            for (i in -1..1) {
                val x = c.x + i * s * 0.4f
                drawLine(color, Offset(x - s * 0.2f, c.y - s * 0.65f), Offset(x + s * 0.2f, c.y + s * 0.65f), s * 0.18f, StrokeCap.Round)
            }
        }
        Trait.BOSS -> {
            // Krone
            val path = Path().apply {
                moveTo(c.x - s * 0.8f, c.y + s * 0.5f)
                lineTo(c.x - s * 0.8f, c.y - s * 0.5f)
                lineTo(c.x - s * 0.4f, c.y)
                lineTo(c.x, c.y - s * 0.7f)
                lineTo(c.x + s * 0.4f, c.y)
                lineTo(c.x + s * 0.8f, c.y - s * 0.5f)
                lineTo(c.x + s * 0.8f, c.y + s * 0.5f)
                close()
            }
            drawPath(path, color)
        }
        else -> {
            val path = Path().apply {
                moveTo(c.x + s * 0.1f, c.y - s * 0.85f)
                lineTo(c.x + s * 0.5f, c.y + s * 0.4f)
                lineTo(c.x - s * 0.5f, c.y + s * 0.4f)
                close()
            }
            drawPath(path, color)
            drawLine(color, Offset(c.x - s * 0.75f, c.y + s * 0.5f), Offset(c.x + s * 0.75f, c.y + s * 0.5f), s * 0.2f, StrokeCap.Round)
        }
    }
}

private fun DrawScope.drawStar(c: Offset, r: Float, color: Color) {
    val path = Path()
    for (i in 0 until 10) {
        val angle = -Math.PI / 2 + i * Math.PI / 5
        val radius = if (i % 2 == 0) r else r * 0.45f
        val x = c.x + (cos(angle) * radius).toFloat()
        val y = c.y + (sin(angle) * radius).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path, color)
}

private fun DrawScope.drawEffects(layout: TacticsLayout, effects: List<TacticsEffect>, textMeasurer: TextMeasurer) {
    val cell = layout.cell
    for (e in effects) {
        val fade = 1f - e.progress
        when (e) {
            is TacticsEffect.Projectile -> {
                val t = e.progress
                val col = e.fromCol + (e.toCol - e.fromCol) * t
                val row = e.fromRow + (e.toRow - e.fromRow) * t
                val tail = (t - 0.25f).coerceAtLeast(0f)
                val head = layout.center(col, row)
                val back = layout.center(e.fromCol + (e.toCol - e.fromCol) * tail, e.fromRow + (e.toRow - e.fromRow) * tail)
                drawLine(e.color.copy(alpha = 0.5f), back, head, cell * 0.05f, StrokeCap.Round)
                drawCircle(e.color, cell * 0.07f, head)
            }
            is TacticsEffect.Slash -> {
                val c = layout.center(e.col, e.row)
                val s = cell * 0.3f
                drawLine(e.color.copy(alpha = fade), Offset(c.x - s, c.y - s * 0.6f), Offset(c.x + s, c.y + s * 0.6f), cell * 0.04f, StrokeCap.Round)
            }
            is TacticsEffect.Telegraph -> {
                // Pulsierende Warnzone, füllt sich bis zum Einschlag
                val left = layout.boardLeft + (e.col - e.radius).coerceAtLeast(0) * cell
                val top = layout.boardTop + (e.row - e.radius).coerceAtLeast(0) * cell
                val right = layout.boardLeft + (e.col + e.radius + 1).coerceAtMost(Board.COLS) * cell
                val bottom = layout.boardTop + (e.row + e.radius + 1).coerceAtMost(Board.ROWS) * cell
                val pulse = 0.5f + 0.5f * sin(e.age * 18f)
                drawRect(LossRed.copy(alpha = 0.12f + 0.3f * e.progress), Offset(left, top), Size(right - left, bottom - top))
                drawRect(
                    LossRed.copy(alpha = 0.5f + 0.4f * pulse), Offset(left, top), Size(right - left, bottom - top),
                    style = Stroke(width = cell * 0.06f),
                )
            }
            is TacticsEffect.Ring -> {
                val c = layout.center(e.col, e.row)
                drawCircle(
                    e.color.copy(alpha = 0.7f * fade), e.radius * cell * (0.35f + 0.65f * e.progress), c,
                    style = Stroke(width = cell * 0.08f * fade + 1f),
                )
            }
            is TacticsEffect.Number -> {
                val c = layout.center(e.col, e.row)
                val style = TextStyle(
                    color = e.color.copy(alpha = fade),
                    fontSize = (cell * 0.22f).toSp(),
                    fontWeight = FontWeight.Bold,
                )
                val measured = textMeasurer.measure(e.text, style)
                drawText(
                    measured,
                    topLeft = Offset(
                        c.x - measured.size.width / 2f,
                        c.y - cell * 0.45f - e.progress * cell * 0.45f - measured.size.height / 2f,
                    ),
                )
            }
        }
    }
}
