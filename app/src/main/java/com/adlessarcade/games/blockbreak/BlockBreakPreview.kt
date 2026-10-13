package com.adlessarcade.games.blockbreak

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import com.adlessarcade.ui.components.drawBlock
import com.adlessarcade.ui.components.drawEmptyCell
import com.adlessarcade.ui.theme.EmptyCell
import com.adlessarcade.ui.theme.blockColor

// Ziffer = Blockfarbe, "." = leeres Feld.
private val PreviewPattern = listOf(
    "....3",
    ".1..3",
    "11.53",
    "22755",
    "62777",
)

/** Kleine Spielfeld-Illustration für Hauptmenü und Modus-Menü. */
@Composable
fun BlockBreakPreview(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val rows = PreviewPattern.size
        val cols = PreviewPattern.first().length
        val cell = minOf(size.width / cols, size.height / rows)
        val origin = Offset((size.width - cell * cols) / 2f, (size.height - cell * rows) / 2f)
        PreviewPattern.forEachIndexed { r, line ->
            line.forEachIndexed { c, ch ->
                val topLeft = origin + Offset(c * cell, r * cell)
                if (ch == '.') {
                    drawEmptyCell(topLeft, cell, EmptyCell.copy(alpha = 0.6f))
                } else {
                    drawBlock(topLeft, cell, blockColor(ch.digitToInt()))
                }
            }
        }
    }
}
