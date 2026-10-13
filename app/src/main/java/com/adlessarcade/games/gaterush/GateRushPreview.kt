package com.adlessarcade.games.gaterush

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import com.adlessarcade.ui.theme.NeonCyan
import com.adlessarcade.ui.theme.NeonPink
import com.adlessarcade.ui.theme.NeonViolet

/** Mini-Illustration: perspektivische Straße mit Torpaar, Gegnern und Held. */
@Composable
fun GateRushPreview(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val road = Path().apply {
            moveTo(cx - w * 0.1f, 0f)
            lineTo(cx + w * 0.1f, 0f)
            lineTo(cx + w * 0.48f, h)
            lineTo(cx - w * 0.48f, h)
            close()
        }
        drawPath(road, Color(0xFF181A28))
        drawPath(road, NeonViolet.copy(alpha = 0.8f), style = Stroke(width = w * 0.012f))

        // Torpaar
        val gateY = h * 0.42f
        val gateH = h * 0.2f
        val halfW = w * 0.27f
        drawRoundRect(
            Color(0xFF4ADE80).copy(alpha = 0.45f),
            Offset(cx - halfW, gateY - gateH), Size(halfW - w * 0.01f, gateH), CornerRadius(w * 0.02f),
        )
        drawRoundRect(
            Color(0xFFFF5C6C).copy(alpha = 0.45f),
            Offset(cx + w * 0.01f, gateY - gateH), Size(halfW - w * 0.01f, gateH), CornerRadius(w * 0.02f),
        )

        // Gegner in der Ferne
        listOf(-0.05f to 0.15f, 0.06f to 0.2f, -0.12f to 0.27f).forEach { (dx, y) ->
            drawCircle(NeonPink, radius = w * 0.025f + y * w * 0.04f, center = Offset(cx + dx * w, h * y))
        }

        // Projektile + Held
        listOf(0.55f, 0.65f, 0.75f).forEach { y ->
            drawCircle(NeonCyan, radius = w * 0.012f, center = Offset(cx, h * y))
        }
        drawRoundRect(
            NeonCyan,
            Offset(cx - w * 0.05f, h * 0.8f), Size(w * 0.1f, h * 0.15f), CornerRadius(w * 0.03f),
        )
        drawCircle(Color.White, radius = w * 0.03f, center = Offset(cx, h * 0.78f))
    }
}
