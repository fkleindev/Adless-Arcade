package com.adlessarcade.games.runetactics

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.lerp
import com.adlessarcade.ui.theme.ArenaStyle
import com.adlessarcade.ui.theme.ArenaTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Fester Pseudo-Zufall pro Feld (gleiches Muster in jedem Frame, ohne Speicher). */
private fun hash(a: Int, b: Int, salt: Int): Float {
    var x = a * 374761393 + b * 668265263 + salt * 2147483647
    x = (x xor (x ushr 13)) * 1274126177
    x = x xor (x ushr 16)
    return (x and 0xFFFF) / 65536f
}

private fun Color.darker(f: Float = 0.3f) = lerp(this, Color.Black, f)
private fun Color.lighter(f: Float = 0.3f) = lerp(this, Color.White, f)

/** Hintergrund, Rahmen mit Requisiten, texturierte Kacheln, Trennlinie und Umgebungs-Partikel. */
internal fun DrawScope.drawArena(layout: TacticsLayout, arena: ArenaTheme, time: Float, drag: DragInfo?) {
    val cell = layout.cell
    val boardW = cell * Board.COLS
    val boardH = cell * Board.ROWS

    drawRect(Brush.verticalGradient(listOf(arena.backdrop.lighter(0.06f), arena.backdrop)), Offset.Zero, size)
    drawSideProps(layout, arena, time)

    // Rahmen
    val frame = cell * TacticsLayout.FRAME
    val outer = Offset(layout.boardLeft - frame, layout.boardTop - frame)
    val outerSize = Size(boardW + frame * 2f, boardH + frame * 2f)
    drawRoundRect(arena.frame.darker(0.35f), outer + Offset(0f, frame * 0.35f), outerSize, CornerRadius(frame))
    drawRoundRect(arena.frame, outer, outerSize, CornerRadius(frame))
    drawFrameTexture(layout, arena, outer, outerSize, frame)

    // Kacheln
    val hover = drag?.let { layout.cellAt(it.pos) }
    for (row in 0 until Board.ROWS) {
        for (col in 0 until Board.COLS) {
            val enemyHalf = row < Board.PLAYER_FIRST_ROW
            val dark = (row + col) % 2 == 0
            var base = when {
                enemyHalf && dark -> arena.enemyDark
                enemyHalf -> arena.enemyLight
                dark -> arena.playerDark
                else -> arena.playerLight
            }
            if (drag != null && !enemyHalf) base = lerp(base, ArcadeCyan, if (hover == Cell(col, row)) 0.3f else 0.07f)
            val topLeft = Offset(layout.boardLeft + col * cell, layout.boardTop + row * cell)
            clipRect(topLeft.x, topLeft.y, topLeft.x + cell, topLeft.y + cell) { drawTile(arena, topLeft, cell, col, row, base, time) }
        }
    }
    drawDivider(layout, arena, time)
    drawCornerProps(layout, arena, outer, outerSize, frame, time)
    drawAmbient(arena.style, time)
}

private fun DrawScope.drawTile(arena: ArenaTheme, topLeft: Offset, cell: Float, col: Int, row: Int, base: Color, time: Float) {
    val inset = cell * 0.03f
    val tl = topLeft + Offset(inset, inset)
    val s = Size(cell - inset * 2f, cell - inset * 2f)
    val r1 = hash(col, row, 1)
    val r2 = hash(col, row, 2)
    val r3 = hash(col, row, 3)
    when (arena.style) {
        ArenaStyle.DUSK -> {
            // Steinplatte mit Fase, Fugen und feinen Rissen
            drawRoundRect(base, tl, s, CornerRadius(cell * 0.06f))
            drawRoundRect(base.lighter(0.06f), tl + Offset(cell * 0.06f, cell * 0.06f), Size(s.width - cell * 0.12f, s.height - cell * 0.12f), CornerRadius(cell * 0.04f))
            if (r1 < 0.45f) {
                val a = tl + Offset(s.width * r2, s.height * 0.15f)
                val m = a + Offset(cell * (r3 - 0.5f) * 0.3f, cell * 0.3f)
                drawLine(base.darker(0.45f), a, m, cell * 0.018f)
                drawLine(base.darker(0.45f), m, m + Offset(cell * 0.12f, cell * 0.2f), cell * 0.015f)
            }
            if (r3 > 0.9f) drawRuneMark(tl + Offset(s.width / 2f, s.height / 2f), cell * 0.16f, arena.divider.copy(alpha = 0.25f + 0.15f * sin(time * 2f + r1 * 6f)))
        }
        ArenaStyle.FOREST -> {
            drawRoundRect(base, tl, s, CornerRadius(cell * 0.1f))
            // Moosflecken und Grashalme
            drawCircle(base.lighter(0.1f), cell * (0.1f + r1 * 0.08f), tl + Offset(s.width * (0.2f + r2 * 0.6f), s.height * (0.2f + r3 * 0.6f)))
            val grass = base.lighter(0.25f)
            for (i in 0 until 4) {
                val p = tl + Offset(s.width * hash(col, row, 10 + i), s.height * hash(col, row, 20 + i))
                val sway = sin(time * 1.8f + p.x * 0.05f) * cell * 0.02f
                for (k in -1..1) drawLine(grass, p, p + Offset(k * cell * 0.04f + sway, -cell * 0.1f), cell * 0.015f)
            }
            if (r1 > 0.82f) {
                val f = tl + Offset(s.width * r3, s.height * r2)
                val petal = if (r2 > 0.5f) Color(0xFFF9A8D4) else Color(0xFFFDE68A)
                for (k in 0 until 5) {
                    val a = k * 2f * PI.toFloat() / 5f
                    drawCircle(petal, cell * 0.035f, f + Offset(cos(a) * cell * 0.045f, sin(a) * cell * 0.045f))
                }
                drawCircle(Color(0xFFFACC15), cell * 0.025f, f)
            }
        }
        ArenaStyle.CRYPT -> {
            // Kopfsteinpflaster: 2×2 Steine mit Versatz
            drawRect(base.darker(0.35f), tl, s)
            val half = s.width / 2f
            for (i in 0..1) for (j in 0..1) {
                val jitter = (hash(col * 2 + i, row * 2 + j, 4) - 0.5f) * cell * 0.04f
                val stone = base.lighter(0.04f + hash(col * 2 + i, row * 2 + j, 5) * 0.08f)
                drawRoundRect(stone, tl + Offset(i * half + cell * 0.025f + jitter, j * half + cell * 0.025f), Size(half - cell * 0.05f, half - cell * 0.05f), CornerRadius(cell * 0.07f))
            }
            if (r1 > 0.86f) {
                // Verstreuter Knochen
                val a = tl + Offset(s.width * 0.25f, s.height * (0.3f + r2 * 0.4f))
                val b = a + Offset(s.width * 0.45f, (r3 - 0.5f) * cell * 0.2f)
                val bone = Color(0xFFD6D3D1).copy(alpha = 0.75f)
                drawLine(bone, a, b, cell * 0.035f, StrokeCap.Round)
                for (e in listOf(a, b)) {
                    drawCircle(bone, cell * 0.03f, e + Offset(0f, -cell * 0.02f))
                    drawCircle(bone, cell * 0.03f, e + Offset(0f, cell * 0.02f))
                }
            } else if (r1 < 0.2f) {
                drawLine(base.darker(0.6f), tl + Offset(s.width * r2, 0f), tl + Offset(s.width * r3, s.height * 0.6f), cell * 0.02f)
            }
        }
        ArenaStyle.FROST -> {
            drawRoundRect(base, tl, s, CornerRadius(cell * 0.05f))
            // Glanzstreifen und Frostrisse
            drawLine(Color.White.copy(alpha = 0.08f), tl + Offset(s.width * 0.1f, s.height * 0.9f), tl + Offset(s.width * 0.9f, s.height * 0.1f), cell * 0.12f)
            drawLine(Color.White.copy(alpha = 0.05f), tl + Offset(s.width * 0.45f, s.height * 0.95f), tl + Offset(s.width * 0.95f, s.height * 0.45f), cell * 0.05f)
            if (r1 < 0.5f) {
                var p = tl + Offset(s.width * r2, s.height * 0.1f)
                repeat(3) { k ->
                    val n = p + Offset((hash(col, row, 30 + k) - 0.5f) * cell * 0.3f, cell * 0.22f)
                    drawLine(Color(0xFFE0F2FE).copy(alpha = 0.35f), p, n, cell * 0.015f)
                    p = n
                }
            }
            if (r3 > 0.7f) {
                // Schneefleck
                val sn = tl + Offset(s.width * r1, s.height * r2)
                drawCircle(Color.White.copy(alpha = 0.32f), cell * 0.14f, sn)
                drawCircle(Color.White.copy(alpha = 0.28f), cell * 0.1f, sn + Offset(cell * 0.12f, cell * 0.04f))
            }
        }
        ArenaStyle.LAVA -> {
            drawRoundRect(base, tl, s, CornerRadius(cell * 0.06f))
            drawRoundRect(base.lighter(0.05f), tl + Offset(cell * 0.05f, cell * 0.05f), Size(s.width * 0.55f, s.height * 0.45f), CornerRadius(cell * 0.08f))
            if (r1 < 0.55f) {
                // Glühender Riss
                val glow = 0.45f + 0.35f * sin(time * 2.2f + r2 * 6f)
                val a = tl + Offset(s.width * r2, s.height * 0.05f)
                val m = a + Offset((r3 - 0.5f) * cell * 0.35f, cell * 0.45f)
                val e = m + Offset((r1 - 0.25f) * cell * 0.5f, cell * 0.4f)
                drawLine(Color(0xFFF97316).copy(alpha = glow * 0.5f), a, m, cell * 0.06f, StrokeCap.Round)
                drawLine(Color(0xFFFDBA74).copy(alpha = glow), a, m, cell * 0.022f, StrokeCap.Round)
                drawLine(Color(0xFFF97316).copy(alpha = glow * 0.5f), m, e, cell * 0.05f, StrokeCap.Round)
                drawLine(Color(0xFFFDBA74).copy(alpha = glow), m, e, cell * 0.018f, StrokeCap.Round)
            }
        }
    }
}

private fun DrawScope.drawRuneMark(c: Offset, s: Float, color: Color) {
    drawLine(color, c + Offset(0f, -s), c + Offset(0f, s), s * 0.18f)
    drawLine(color, c + Offset(0f, -s * 0.4f), c + Offset(s * 0.6f, -s), s * 0.18f)
    drawLine(color, c + Offset(0f, s * 0.1f), c + Offset(-s * 0.6f, -s * 0.5f), s * 0.18f)
}

private fun DrawScope.drawDivider(layout: TacticsLayout, arena: ArenaTheme, time: Float) {
    val cell = layout.cell
    val y = layout.boardTop + Board.PLAYER_FIRST_ROW * cell
    val left = layout.boardLeft
    val right = left + Board.COLS * cell
    when (arena.style) {
        ArenaStyle.DUSK -> {
            drawLine(arena.divider.copy(alpha = 0.25f), Offset(left, y), Offset(right, y), cell * 0.08f)
            drawLine(arena.divider.copy(alpha = 0.6f), Offset(left, y), Offset(right, y), cell * 0.025f)
            for (i in 0..Board.COLS) {
                val p = Offset(left + i * cell, y)
                val pulse = 0.5f + 0.5f * sin(time * 2f + i)
                drawPath(diamond(p, cell * 0.07f), arena.divider.copy(alpha = 0.4f + 0.4f * pulse))
            }
        }
        ArenaStyle.FOREST -> {
            drawLine(Color(0xFF14532D), Offset(left, y), Offset(right, y), cell * 0.06f)
            for (i in 0 until Board.COLS * 3) {
                val p = Offset(left + (i + 0.5f) * cell / 3f, y)
                val leaf = Path().apply {
                    moveTo(p.x - cell * 0.07f, p.y)
                    quadraticTo(p.x, p.y - cell * 0.09f, p.x + cell * 0.07f, p.y)
                    quadraticTo(p.x, p.y + cell * 0.09f, p.x - cell * 0.07f, p.y)
                }
                drawPath(leaf, if (i % 2 == 0) Color(0xFF22C55E) else Color(0xFF15803D))
            }
        }
        ArenaStyle.CRYPT -> {
            val link = cell * 0.16f
            var x = left
            var i = 0
            while (x < right) {
                drawOval(Color(0xFF9CA3AF), Offset(x, y - cell * 0.045f), Size(link, cell * 0.09f), style = Stroke(cell * 0.025f))
                x += link * 0.8f
                i++
            }
            drawLine(arena.divider.copy(alpha = 0.18f + 0.1f * sin(time * 1.5f)), Offset(left, y), Offset(right, y), cell * 0.12f)
        }
        ArenaStyle.FROST -> {
            val path = Path().apply {
                moveTo(left, y)
                for (i in 0..Board.COLS * 4) {
                    val px = left + i * cell / 4f
                    lineTo(px, y + if (i % 2 == 0) -cell * 0.04f else cell * 0.05f)
                }
            }
            drawPath(path, Color(0xFFBAE6FD).copy(alpha = 0.8f), style = Stroke(cell * 0.03f))
            drawLine(Color.White.copy(alpha = 0.15f), Offset(left, y), Offset(right, y), cell * 0.1f)
        }
        ArenaStyle.LAVA -> {
            val glow = 0.6f + 0.3f * sin(time * 2.5f)
            drawLine(Color(0xFF7C2D12), Offset(left, y), Offset(right, y), cell * 0.14f)
            drawLine(Color(0xFFF97316).copy(alpha = glow), Offset(left, y), Offset(right, y), cell * 0.07f)
            drawLine(Color(0xFFFDE68A).copy(alpha = glow * 0.8f), Offset(left, y), Offset(right, y), cell * 0.02f)
            for (i in 0 until 5) {
                val bx = left + ((hash(i, 0, 40) * Board.COLS + time * 0.15f) % Board.COLS) * cell
                val br = cell * 0.04f * (0.5f + 0.5f * sin(time * 3f + i * 2f))
                drawCircle(Color(0xFFFDBA74), br.coerceAtLeast(0.5f), Offset(bx, y))
            }
        }
    }
}

private fun DrawScope.drawFrameTexture(layout: TacticsLayout, arena: ArenaTheme, outer: Offset, size: Size, frame: Float) {
    val cell = layout.cell
    when (arena.style) {
        ArenaStyle.FOREST -> {
            // Holzmaserung auf dem Rahmen
            for (i in 0 until 6) {
                val y = outer.y + frame * 0.3f + i * (size.height - frame * 0.6f) / 5f
                drawLine(arena.frame.lighter(0.15f), Offset(outer.x + frame * 0.2f, y), Offset(outer.x + frame * 0.8f, y + frame * 0.2f), cell * 0.015f)
                drawLine(arena.frame.lighter(0.15f), Offset(outer.x + size.width - frame * 0.8f, y), Offset(outer.x + size.width - frame * 0.2f, y + frame * 0.2f), cell * 0.015f)
            }
        }
        ArenaStyle.FROST -> {
            // Eiszapfen am oberen Rand
            for (i in 0 until 14) {
                val x = outer.x + frame + i * (size.width - frame * 2f) / 13f
                val len = frame * (0.5f + hash(i, 7, 50) * 0.8f)
                drawPath(Path().apply {
                    moveTo(x - frame * 0.15f, outer.y + frame * 0.6f)
                    lineTo(x, outer.y + frame * 0.6f + len)
                    lineTo(x + frame * 0.15f, outer.y + frame * 0.6f)
                    close()
                }, Color(0xFFE0F2FE).copy(alpha = 0.7f))
            }
        }
        else -> {
            // Steinquader im Rahmen
            val brick = frame * 1.6f
            var x = outer.x + frame * 0.5f
            while (x < outer.x + size.width - frame) {
                drawLine(arena.frame.darker(0.3f), Offset(x, outer.y + frame * 0.1f), Offset(x, outer.y + frame * 0.9f), cell * 0.012f)
                drawLine(arena.frame.darker(0.3f), Offset(x + brick / 2f, outer.y + size.height - frame * 0.9f), Offset(x + brick / 2f, outer.y + size.height - frame * 0.1f), cell * 0.012f)
                x += brick
            }
        }
    }
    drawRoundRect(Color.Black.copy(alpha = 0.35f), Offset(layout.boardLeft, layout.boardTop), Size(cell * Board.COLS, cell * Board.ROWS), CornerRadius(cell * 0.06f), style = Stroke(cell * 0.04f))
}

/** Ecken des Rahmens: Fackeln, Bäume, Grabsteine mit Kerzen, Eiskristalle oder Feuerschalen. */
private fun DrawScope.drawCornerProps(layout: TacticsLayout, arena: ArenaTheme, outer: Offset, size: Size, frame: Float, time: Float) {
    val cell = layout.cell
    val corners = listOf(
        outer + Offset(frame * 0.5f, frame * 0.5f),
        outer + Offset(size.width - frame * 0.5f, frame * 0.5f),
        outer + Offset(frame * 0.5f, size.height - frame * 0.5f),
        outer + Offset(size.width - frame * 0.5f, size.height - frame * 0.5f),
    )
    corners.forEachIndexed { i, p ->
        val s = cell * 0.36f
        when (arena.style) {
            ArenaStyle.DUSK -> torch(p, s, time + i, Color(0xFF6B7280))
            ArenaStyle.FOREST -> {
                drawCircle(Color(0xFF14532D), s * 0.8f, p)
                drawCircle(Color(0xFF22C55E).copy(alpha = 0.6f), s * 0.4f, p + Offset(-s * 0.2f, -s * 0.2f))
            }
            ArenaStyle.CRYPT -> {
                drawRoundRect(Color(0xFF52525B), p + Offset(-s * 0.4f, -s * 0.55f), Size(s * 0.8f, s * 1.0f), CornerRadius(s * 0.4f, s * 0.4f))
                drawLine(Color(0xFF27272A), p + Offset(0f, -s * 0.35f), p + Offset(0f, s * 0.1f), s * 0.1f)
                drawLine(Color(0xFF27272A), p + Offset(-s * 0.18f, -s * 0.18f), p + Offset(s * 0.18f, -s * 0.18f), s * 0.1f)
                candle(p + Offset(s * 0.55f, s * 0.35f), s * 0.5f, time + i)
            }
            ArenaStyle.FROST -> iceCrystal(p, s * 1.1f)
            ArenaStyle.LAVA -> brazier(p, s, time + i)
        }
    }
}

/** Requisiten im freien Raum neben und über dem Brett. */
private fun DrawScope.drawSideProps(layout: TacticsLayout, arena: ArenaTheme, time: Float) {
    val cell = layout.cell
    val frame = cell * TacticsLayout.FRAME
    val sideSpace = layout.boardLeft - frame
    val spots = ArrayList<Offset>()
    if (sideSpace > cell * 0.45f) {
        for (i in 0 until 5) {
            val y = layout.boardTop + (i + 0.5f) * cell * Board.ROWS / 5f
            spots += Offset(sideSpace / 2f, y)
            spots += Offset(size.width - sideSpace / 2f, y + cell * 0.6f)
        }
    }
    val topSpace = layout.boardTop - frame
    if (topSpace > cell * 0.45f) {
        for (i in 0 until 4) spots += Offset(layout.boardLeft + (i + 0.5f) * cell * Board.COLS / 4f, topSpace / 2f)
    }
    val s = (minOf(sideSpace.takeIf { it > 0f } ?: cell, cell) * 0.55f).coerceAtLeast(cell * 0.25f)
    spots.forEachIndexed { i, p ->
        val v = hash(i, 3, 60)
        when (arena.style) {
            ArenaStyle.DUSK -> if (v > 0.5f) pillar(p, s) else rock(p, s * 0.7f, Color(0xFF3F3A47))
            ArenaStyle.FOREST -> if (v > 0.35f) tree(p, s, Color(0xFF14532D), Color(0xFF22C55E)) else bush(p, s * 0.7f)
            ArenaStyle.CRYPT -> if (v > 0.45f) gravestone(p, s, Color(0xFF52525B)) else deadTree(p, s, Color(0xFF3F3F46))
            ArenaStyle.FROST -> if (v > 0.4f) iceCrystal(p, s) else snowPine(p, s)
            ArenaStyle.LAVA -> if (v > 0.5f) rock(p, s * 0.8f, Color(0xFF292524)) else lavaPool(p, s * 0.7f, time + i)
        }
    }
}

/** Zeitbasierte Partikel ohne Liste: Position aus Index und Zeit berechnet. */
private fun DrawScope.drawAmbient(style: ArenaStyle, time: Float) {
    val w = size.width
    val h = size.height
    val count = 16
    for (i in 0 until count) {
        val a = hash(i, 1, 70)
        val b = hash(i, 2, 70)
        when (style) {
            ArenaStyle.DUSK -> {
                val y = h - ((time * 12f + b * h) % h)
                val x = a * w + sin(time * 0.7f + i) * 10f
                drawCircle(Color(0xFFFDE68A).copy(alpha = 0.12f), 1.6f + b * 1.5f, Offset(x, y))
            }
            ArenaStyle.FOREST -> {
                val x = a * w + sin(time * 0.6f + i * 1.3f) * 30f
                val y = b * h + cos(time * 0.5f + i) * 20f
                val glow = 0.3f + 0.5f * (0.5f + 0.5f * sin(time * 3f + i * 2f))
                drawCircle(Color(0xFFD9F99D).copy(alpha = glow * 0.35f), 6f, Offset(x, y))
                drawCircle(Color(0xFFECFCCB).copy(alpha = glow), 2f, Offset(x, y))
            }
            ArenaStyle.CRYPT -> if (i < 6) {
                val x = ((a * w + time * (8f + b * 8f)) % (w + 200f)) - 100f
                val y = b * h
                drawOval(Color(0xFFE5E7EB).copy(alpha = 0.035f), Offset(x - 90f, y - 18f), Size(180f, 36f))
            }
            ArenaStyle.FROST -> {
                val y = (time * (20f + b * 25f) + a * h) % h
                val x = a * w + sin(time + i) * 12f
                drawCircle(Color.White.copy(alpha = 0.55f), 1.5f + b * 1.8f, Offset(x, y))
            }
            ArenaStyle.LAVA -> {
                val y = h - ((time * (25f + b * 20f) + a * h) % h)
                val x = a * w + sin(time * 2f + i) * 8f
                drawCircle(Color(0xFFFB923C).copy(alpha = 0.6f * (y / h)), 1.5f + b * 1.5f, Offset(x, y))
            }
        }
    }
}

/** Bank-Fächer im Stil der Arena. */
internal fun DrawScope.drawArenaBenchSlot(arena: ArenaTheme, topLeft: Offset, size: Size, highlighted: Boolean, index: Int) {
    val corner = CornerRadius(size.width * 0.16f)
    val (fill, rim) = when (arena.style) {
        ArenaStyle.DUSK -> Color(0xFF221D29) to Color(0xFF4B4453)
        ArenaStyle.FOREST -> Color(0xFF3B2614) to Color(0xFF6B4423)
        ArenaStyle.CRYPT -> Color(0xFF1F1F24) to Color(0xFF52525B)
        ArenaStyle.FROST -> Color(0xFF1B2B40) to Color(0xFF7DD3FC).copy(alpha = 0.6f)
        ArenaStyle.LAVA -> Color(0xFF1C1210) to Color(0xFFC2410C)
    }
    drawRoundRect(if (highlighted) fill.lighter(0.2f) else fill, topLeft, size, corner)
    when (arena.style) {
        ArenaStyle.FOREST -> for (k in 1..2) {
            val y = topLeft.y + size.height * k / 3f
            drawLine(rim.copy(alpha = 0.6f), Offset(topLeft.x + size.width * 0.1f, y), Offset(topLeft.x + size.width * 0.9f, y), size.width * 0.03f)
        }
        ArenaStyle.FROST -> drawLine(Color.White.copy(alpha = 0.1f), topLeft + Offset(size.width * 0.15f, size.height * 0.85f), topLeft + Offset(size.width * 0.85f, size.height * 0.15f), size.width * 0.12f)
        ArenaStyle.LAVA -> drawLine(Color(0xFFF97316).copy(alpha = 0.25f), topLeft + Offset(size.width * (0.2f + hash(index, 0, 80) * 0.3f), size.height * 0.1f), topLeft + Offset(size.width * 0.6f, size.height * 0.9f), size.width * 0.03f)
        else -> Unit
    }
    drawRoundRect(rim, topLeft, size, corner, style = Stroke(size.width * 0.04f))
}

// --- Requisiten ---

private fun diamond(c: Offset, r: Float) = Path().apply {
    moveTo(c.x, c.y - r)
    lineTo(c.x + r, c.y)
    lineTo(c.x, c.y + r)
    lineTo(c.x - r, c.y)
    close()
}

private fun DrawScope.flame(base: Offset, s: Float, time: Float) {
    val flicker = sin(time * 11f) * s * 0.12f
    drawCircle(Color(0xFFF97316).copy(alpha = 0.22f), s * 1.4f, base + Offset(0f, -s * 0.5f))
    drawPath(Path().apply {
        moveTo(base.x - s * 0.4f, base.y)
        quadraticTo(base.x - s * 0.35f, base.y - s * 0.6f, base.x + flicker * 0.5f, base.y - s * 1.2f + flicker)
        quadraticTo(base.x + s * 0.35f, base.y - s * 0.6f, base.x + s * 0.4f, base.y)
        close()
    }, Color(0xFFF97316))
    drawPath(Path().apply {
        moveTo(base.x - s * 0.2f, base.y)
        quadraticTo(base.x, base.y - s * 0.7f + flicker, base.x + s * 0.2f, base.y)
        close()
    }, Color(0xFFFDE68A))
}

private fun DrawScope.torch(p: Offset, s: Float, time: Float, metal: Color) {
    drawCircle(Color(0xFF27272A), s * 0.55f, p)
    drawRoundRect(metal, p + Offset(-s * 0.18f, -s * 0.2f), Size(s * 0.36f, s * 0.5f), CornerRadius(s * 0.08f))
    flame(p + Offset(0f, -s * 0.2f), s * 0.5f, time)
}

private fun DrawScope.candle(p: Offset, s: Float, time: Float) {
    drawRect(Color(0xFFE7E5E4), p + Offset(-s * 0.15f, -s * 0.5f), Size(s * 0.3f, s * 0.6f))
    flame(p + Offset(0f, -s * 0.5f), s * 0.3f, time)
}

private fun DrawScope.brazier(p: Offset, s: Float, time: Float) {
    drawArc(Color(0xFF44403C), 0f, 180f, true, p + Offset(-s * 0.55f, -s * 0.35f), Size(s * 1.1f, s * 0.8f))
    drawOval(Color(0xFFEA580C), p + Offset(-s * 0.5f, -s * 0.12f), Size(s, s * 0.25f))
    flame(p + Offset(0f, -s * 0.05f), s * 0.6f, time)
}

private fun DrawScope.pillar(p: Offset, s: Float) {
    drawOval(Color.Black.copy(alpha = 0.3f), p + Offset(-s * 0.4f, s * 0.35f), Size(s * 0.8f, s * 0.2f))
    drawRect(Color(0xFF4B4453), p + Offset(-s * 0.22f, -s * 0.9f), Size(s * 0.44f, s * 1.3f))
    drawRect(Color(0xFF5E5668), p + Offset(-s * 0.32f, -s * 1.0f), Size(s * 0.64f, s * 0.16f))
    drawRect(Color(0xFF5E5668), p + Offset(-s * 0.32f, s * 0.3f), Size(s * 0.64f, s * 0.14f))
    drawLine(Color(0xFF3A3340), p + Offset(-s * 0.05f, -s * 0.8f), p + Offset(-s * 0.05f, s * 0.25f), s * 0.05f)
}

private fun DrawScope.rock(p: Offset, s: Float, color: Color) {
    drawOval(Color.Black.copy(alpha = 0.3f), p + Offset(-s * 0.6f, s * 0.2f), Size(s * 1.2f, s * 0.3f))
    drawPath(Path().apply {
        moveTo(p.x - s * 0.6f, p.y + s * 0.3f)
        lineTo(p.x - s * 0.45f, p.y - s * 0.25f)
        lineTo(p.x - s * 0.05f, p.y - s * 0.5f)
        lineTo(p.x + s * 0.45f, p.y - s * 0.2f)
        lineTo(p.x + s * 0.6f, p.y + s * 0.3f)
        close()
    }, color)
    drawLine(color.lighter(0.2f), p + Offset(-s * 0.4f, -s * 0.2f), p + Offset(-s * 0.05f, -s * 0.45f), s * 0.06f)
}

private fun DrawScope.tree(p: Offset, s: Float, dark: Color, light: Color) {
    drawOval(Color.Black.copy(alpha = 0.25f), p + Offset(-s * 0.5f, s * 0.35f), Size(s, s * 0.25f))
    drawRect(Color(0xFF5B3A1E), p + Offset(-s * 0.08f, 0f), Size(s * 0.16f, s * 0.45f))
    drawCircle(dark, s * 0.5f, p + Offset(0f, -s * 0.2f))
    drawCircle(dark.lighter(0.1f), s * 0.35f, p + Offset(-s * 0.25f, -s * 0.05f))
    drawCircle(light.copy(alpha = 0.55f), s * 0.22f, p + Offset(-s * 0.12f, -s * 0.38f))
}

private fun DrawScope.bush(p: Offset, s: Float) {
    drawCircle(Color(0xFF166534), s * 0.4f, p + Offset(-s * 0.25f, 0f))
    drawCircle(Color(0xFF15803D), s * 0.45f, p + Offset(s * 0.2f, -s * 0.1f))
    drawCircle(Color(0xFFF9A8D4), s * 0.07f, p + Offset(s * 0.1f, -s * 0.25f))
}

private fun DrawScope.gravestone(p: Offset, s: Float, color: Color) {
    val w = s * 0.6f
    val h = s * 0.8f
    drawOval(Color.Black.copy(alpha = 0.3f), p + Offset(-w * 0.7f, -s * 0.05f), Size(w * 1.4f, s * 0.2f))
    drawRoundRect(color, p - Offset(w / 2f, h), Size(w, h), CornerRadius(w / 2f, w / 2f))
    drawLine(color.darker(0.4f), p + Offset(0f, -h * 0.8f), p + Offset(0f, -h * 0.35f), s * 0.06f)
    drawLine(color.darker(0.4f), p + Offset(-w * 0.25f, -h * 0.62f), p + Offset(w * 0.25f, -h * 0.62f), s * 0.06f)
}

private fun DrawScope.deadTree(p: Offset, s: Float, color: Color) {
    drawLine(color, p, p + Offset(0f, -s), s * 0.12f, StrokeCap.Round)
    drawLine(color, p + Offset(0f, -s * 0.55f), p + Offset(s * 0.4f, -s * 0.9f), s * 0.08f, StrokeCap.Round)
    drawLine(color, p + Offset(0f, -s * 0.75f), p + Offset(-s * 0.35f, -s * 1.05f), s * 0.08f, StrokeCap.Round)
}

private fun DrawScope.iceCrystal(p: Offset, s: Float) {
    for ((dx, h) in listOf(-0.3f to 0.7f, 0f to 1f, 0.3f to 0.6f)) {
        val base = p + Offset(dx * s, s * 0.3f)
        val path = Path().apply {
            moveTo(base.x - s * 0.14f, base.y)
            lineTo(base.x, base.y - s * h)
            lineTo(base.x + s * 0.14f, base.y)
            close()
        }
        drawPath(path, Color(0xFFBAE6FD).copy(alpha = 0.8f))
        drawPath(path, Color.White.copy(alpha = 0.6f), style = Stroke(s * 0.03f))
    }
}

private fun DrawScope.snowPine(p: Offset, s: Float) {
    drawRect(Color(0xFF5B3A1E), p + Offset(-s * 0.06f, 0f), Size(s * 0.12f, s * 0.3f))
    for (k in 0..2) {
        val y = p.y - k * s * 0.3f
        val w = s * (0.55f - k * 0.13f)
        drawPath(Path().apply {
            moveTo(p.x - w, y)
            lineTo(p.x, y - s * 0.45f)
            lineTo(p.x + w, y)
            close()
        }, Color(0xFF14532D))
        drawLine(Color.White.copy(alpha = 0.8f), Offset(p.x - w * 0.6f, y - s * 0.08f), Offset(p.x, y - s * 0.4f), s * 0.05f)
    }
}

private fun DrawScope.lavaPool(p: Offset, s: Float, time: Float) {
    drawOval(Color(0xFF431407), p + Offset(-s * 0.7f, -s * 0.3f), Size(s * 1.4f, s * 0.6f))
    drawOval(Color(0xFFEA580C).copy(alpha = 0.8f + 0.2f * sin(time * 2f)), p + Offset(-s * 0.55f, -s * 0.2f), Size(s * 1.1f, s * 0.4f))
    val bubble = (time * 0.8f) % 1f
    drawCircle(Color(0xFFFDBA74).copy(alpha = 1f - bubble), s * 0.12f * (0.5f + bubble), p + Offset(s * 0.15f, -s * 0.05f - bubble * s * 0.2f))
}
