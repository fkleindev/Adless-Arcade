package com.adlessarcade.ui.components

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import com.adlessarcade.ui.theme.BlockSkin
import com.adlessarcade.ui.theme.BlockStyle

/** Zeichnet einen Block im gewünschten Stil. */
fun DrawScope.drawBlock(
    topLeft: Offset,
    size: Float,
    color: Color,
    alpha: Float = 1f,
    style: BlockStyle = BlockStyle.BEVEL,
) {
    val inset = size * 0.05f
    val s = size - inset * 2
    val tl = topLeft + Offset(inset, inset)
    val radius = CornerRadius(s * 0.2f)
    when (style) {
        BlockStyle.BEVEL -> {
            // Leichter 3D-Effekt: dunkle Kante unten, Glanz oben.
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
        BlockStyle.FLAT -> {
            drawRoundRect(color, tl, Size(s, s), CornerRadius(s * 0.28f), alpha = alpha)
            drawCircle(Color.White.copy(alpha = 0.35f), s * 0.09f, tl + Offset(s * 0.28f, s * 0.28f), alpha = alpha)
        }
        BlockStyle.OUTLINE -> {
            drawRoundRect(color.copy(alpha = 0.14f), tl, Size(s, s), radius, alpha = alpha)
            drawRoundRect(color.copy(alpha = 0.35f), tl, Size(s, s), radius, alpha = alpha, style = Stroke(width = s * 0.16f))
            drawRoundRect(color, tl, Size(s, s), radius, alpha = alpha, style = Stroke(width = s * 0.07f))
        }
        BlockStyle.GEM -> {
            // Facettierter Edelstein: vier Dreiecke um ein helles Mittelfeld.
            val c = tl + Offset(s / 2f, s / 2f)
            val i = s * 0.24f
            val corners = listOf(tl, tl + Offset(s, 0f), tl + Offset(s, s), tl + Offset(0f, s))
            val inner = listOf(c + Offset(-i, -i), c + Offset(i, -i), c + Offset(i, i), c + Offset(-i, i))
            val shades = listOf(0.35f, -0.25f, -0.45f, 0.1f) // oben, rechts, unten, links
            for (k in 0 until 4) {
                val facet = Path().apply {
                    moveTo(corners[k].x, corners[k].y)
                    lineTo(corners[(k + 1) % 4].x, corners[(k + 1) % 4].y)
                    lineTo(inner[(k + 1) % 4].x, inner[(k + 1) % 4].y)
                    lineTo(inner[k].x, inner[k].y)
                    close()
                }
                val shade = shades[k]
                val facetColor = if (shade > 0) lerp(color, Color.White, shade) else lerp(color, Color.Black, -shade)
                drawPath(facet, facetColor, alpha = alpha)
            }
            drawRect(lerp(color, Color.White, 0.15f), inner[0], Size(i * 2, i * 2), alpha = alpha)
            drawRect(Color.White.copy(alpha = 0.45f), inner[0], Size(i * 0.7f, i * 0.5f), alpha = alpha)
        }
    }
}

/** Block mit Farbe und Stil aus einem Skin. */
fun DrawScope.drawSkinBlock(skin: BlockSkin, topLeft: Offset, size: Float, value: Int, alpha: Float = 1f) =
    drawBlock(topLeft, size, skin.color(value), alpha, skin.style)

fun DrawScope.drawEmptyCell(topLeft: Offset, size: Float, color: Color) {
    val inset = size * 0.05f
    val s = size - inset * 2
    drawRoundRect(color, topLeft + Offset(inset, inset), Size(s, s), CornerRadius(s * 0.2f))
}
