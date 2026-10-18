package com.adlessarcade.games.runetactics

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.adlessarcade.ui.theme.ArenaTheme
import com.adlessarcade.ui.theme.LocalCosmetics
import kotlin.math.min

/** Mini-Illustration: Ausschnitt des Bretts mit zwei Armeen. */
@Composable
fun RuneTacticsPreview(modifier: Modifier = Modifier, arena: ArenaTheme = LocalCosmetics.current.arena) {
    Canvas(modifier) {
        val cols = 5
        val rows = 4
        val cell = min(size.width / cols, size.height / rows)
        val left = (size.width - cell * cols) / 2f
        val top = (size.height - cell * rows) / 2f
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val enemy = r < rows / 2
                val dark = (r + c) % 2 == 0
                drawRoundRect(
                    when {
                        enemy && dark -> arena.enemyDark
                        enemy -> arena.enemyLight
                        dark -> arena.playerDark
                        else -> arena.playerLight
                    },
                    topLeft = Offset(left + c * cell + 1f, top + r * cell + 1f),
                    size = Size(cell - 2f, cell - 2f),
                    cornerRadius = CornerRadius(cell * 0.12f),
                )
            }
        }
        fun at(c: Int, r: Int) = Offset(left + (c + 0.5f) * cell, top + (r + 0.5f) * cell)
        drawUnit(at(1, 0), cell, RuneTacticsUnits.byId("lich"), 2, enemy = true)
        drawUnit(at(3, 1), cell, RuneTacticsUnits.byId("skeleton"), 1, enemy = true)
        drawUnit(at(2, 1), cell, RuneTacticsUnits.byId("bonewarden"), 1, enemy = true)
        drawUnit(at(2, 2), cell, RuneTacticsUnits.byId("thane"), 2, enemy = false)
        drawUnit(at(1, 2), cell, RuneTacticsUnits.byId("bladedancer"), 1, enemy = false)
        drawUnit(at(3, 3), cell, RuneTacticsUnits.byId("stormmage"), 1, enemy = false)
    }
}
