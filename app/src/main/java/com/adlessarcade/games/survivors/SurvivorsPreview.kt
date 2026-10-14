package com.adlessarcade.games.survivors

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import com.adlessarcade.ui.theme.LocalCosmetics

/** Menübild: Held in der ausgewählten Welt, umringt von Gegnern, mit Knochenorbit. */
@Composable
fun SurvivorsPreview(modifier: Modifier = Modifier) {
    val theme = LocalCosmetics.current.survivorTheme
    Canvas(modifier) {
        val c = Offset(size.width / 2f, size.height / 2f)
        val u = size.minDimension
        drawCircle(theme.ground, u * 0.48f, c)
        drawCircle(theme.detail.copy(alpha = 0.35f), u * 0.48f, c, style = Stroke(width = u * 0.02f))
        // Gegner im Kreis
        val ring = listOf(0f, 1.1f, 2.2f, 3.3f, 4.4f, 5.4f)
        ring.forEachIndexed { i, a ->
            val p = c + Offset(kotlin.math.cos(a) * u * 0.36f, kotlin.math.sin(a) * u * 0.36f)
            val color = if (i % 2 == 0) Color(0xFF4ADE80) else Color(0xFF8B5CF6)
            drawOval(color, p - Offset(u * 0.05f, u * 0.035f), Size(u * 0.1f, u * 0.07f))
        }
        // Orbit
        drawCircle(Color(0xFFA78BFA).copy(alpha = 0.4f), u * 0.2f, c, style = Stroke(width = u * 0.01f))
        listOf(0.5f, 2.6f, 4.7f).forEach { a ->
            drawCircle(Color(0xFFF5F5F4), u * 0.035f, c + Offset(kotlin.math.cos(a) * u * 0.2f, kotlin.math.sin(a) * u * 0.2f))
        }
        drawHeroClass(HeroClass.KNIGHT, c, u * 0.22f, 0f)
    }
}
