package com.adlessarcade.ui.components

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp

/** Zeichnet einen Block mit leichtem 3D-Effekt (dunkle Kante unten, Glanz oben). */
fun DrawScope.drawBlock(topLeft: Offset, size: Float, color: Color, alpha: Float = 1f) {
    val inset = size * 0.05f
    val s = size - inset * 2
    val tl = topLeft + Offset(inset, inset)
    val radius = CornerRadius(s * 0.2f)
    drawRoundRect(lerp(color, Color.Black, 0.35f), tl, Size(s, s), radius, alpha = alpha)
    drawRoundRect(color, tl, Size(s, s * 0.88f), radius, alpha = alpha)
    drawRoundRect(
        Color.White.copy(alpha = 0.28f),
        tl + Offset(s * 0.16f, s * 0.1f),
        Size(s * 0.68f, s * 0.13f),
        CornerRadius(s * 0.07f),
        alpha = alpha,
    )
}

fun DrawScope.drawEmptyCell(topLeft: Offset, size: Float, color: Color) {
    val inset = size * 0.05f
    val s = size - inset * 2
    drawRoundRect(color, topLeft + Offset(inset, inset), Size(s, s), CornerRadius(s * 0.2f))
}
