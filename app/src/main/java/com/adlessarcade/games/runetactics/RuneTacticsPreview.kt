package com.adlessarcade.games.runetactics

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import com.adlessarcade.ui.theme.ArenaTheme
import com.adlessarcade.ui.theme.LocalCosmetics

/** Mini-Illustration: Ausschnitt der Arena (Mitte des Bretts) mit zwei Armeen. */
@Composable
fun RuneTacticsPreview(modifier: Modifier = Modifier, arena: ArenaTheme = LocalCosmetics.current.arena) {
    Canvas(modifier) {
        // Virtuelles Brett, von dem vier Reihen um die Mittellinie sichtbar sind
        val cell = size.height / 4f
        val rows = TacticsLayout.FRAME + Board.ROWS + TacticsLayout.BENCH_GAP + 1.1f
        val layout = TacticsLayout(cell * (Board.COLS + TacticsLayout.FRAME * 2f), cell * rows)
        val dx = size.width / 2f - layout.center(2.5f, 0f).x
        val dy = size.height / 2f - (layout.boardTop + Board.PLAYER_FIRST_ROW * layout.cell)
        clipRect {
            translate(dx, dy) {
                drawArena(layout, arena, time = 0f, drag = null)
                fun unit(id: String, star: Int, col: Int, row: Int, enemy: Boolean) =
                    drawUnit(layout.center(col.toFloat(), row.toFloat()), layout.cell, RuneTacticsUnits.byId(id), star, enemy)
                unit("lich", 2, 2, 2, enemy = true)
                unit("archdemon", 1, 4, 2, enemy = true)
                unit("bonewarden", 1, 3, 3, enemy = true)
                unit("skeleton", 1, 1, 3, enemy = true)
                unit("bladedancer", 1, 2, 4, enemy = false)
                unit("thane", 2, 3, 4, enemy = false)
                unit("warlord", 1, 4, 4, enemy = false)
                unit("stormmage", 1, 2, 5, enemy = false)
                unit("queen", 1, 4, 5, enemy = false)
            }
        }
    }
}
