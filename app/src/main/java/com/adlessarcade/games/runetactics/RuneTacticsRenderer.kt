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
    else -> Color.White
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
) {
    drawBoard(layout, drag)
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
            )
        }
    } else {
        engine.enemyArmy.forEach {
            drawUnit(layout.center(it.col.toFloat(), it.row.toFloat()), layout.cell, it.def, it.star, enemy = true)
        }
        engine.board.forEach { (cell, unit) ->
            val dragged = drag?.from == Slot.Board(cell)
            drawUnit(
                layout.center(cell.col.toFloat(), cell.row.toFloat()), layout.cell, unit.def, unit.star,
                enemy = false, alpha = if (dragged) 0.3f else 1f,
            )
        }
    }

    drawEffects(layout, effects, textMeasurer)

    if (drag != null) {
        drawUnit(drag.pos, layout.cell * 1.15f, drag.unit.def, drag.unit.star, enemy = false, alpha = 0.92f)
    }
}

private fun DrawScope.drawBoard(layout: TacticsLayout, drag: DragInfo?) {
    val cell = layout.cell
    val hover = drag?.let { layout.cellAt(it.pos) }
    for (row in 0 until Board.ROWS) {
        for (col in 0 until Board.COLS) {
            val enemyHalf = row < Board.PLAYER_FIRST_ROW
            val dark = (row + col) % 2 == 0
            var color = when {
                enemyHalf && dark -> Color(0xFF1C1520)
                enemyHalf -> Color(0xFF221A27)
                dark -> Color(0xFF111A24)
                else -> Color(0xFF16212D)
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
        LossRed.copy(alpha = 0.35f),
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
            enemy = false, alpha = if (dragged) 0.3f else 1f,
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
) {
    val r = size * 0.34f
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
