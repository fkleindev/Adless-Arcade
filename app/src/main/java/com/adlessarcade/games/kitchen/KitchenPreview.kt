package com.adlessarcade.games.kitchen

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import kotlin.math.min

/** Mini-Illustration: Fliesenwand, Theke, Pfanne mit Pfannkuchen, Brett mit Erdbeere und ein Bon. */
@Composable
fun KitchenPreview(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val u = min(w, h)
        drawRect(Wall, Offset.Zero, size)
        val tile = u * 0.16f
        var x = 0f
        while (x < w) { drawLine(WallLine, Offset(x, 0f), Offset(x, h * 0.55f), 2f); x += tile }
        var y = 0f
        while (y < h * 0.55f) { drawLine(WallLine, Offset(0f, y), Offset(w, y), 2f); y += tile }
        drawRect(Wood, Offset(0f, h * 0.55f), Size(w, h * 0.45f))
        drawRect(WoodDark, Offset(0f, h * 0.55f), Size(w, h * 0.03f))
        // Bon
        outlinedRect(Offset(w * 0.06f, h * 0.08f), Size(w * 0.24f, h * 0.36f), Color.White, u * 0.012f, u * 0.03f)
        drawFood(ItemKind.PANCAKE, Offset(w * 0.18f, h * 0.24f), u * 0.18f)
        drawRoundRect(Good, Offset(w * 0.09f, h * 0.36f), Size(w * 0.13f, h * 0.04f))
        // Pfanne
        val pan = Offset(w * 0.52f, h * 0.62f)
        drawLine(Outline, pan + Offset(u * 0.15f, u * 0.08f), pan + Offset(u * 0.32f, u * 0.18f), u * 0.05f, StrokeCap.Round)
        outlinedCircle(pan, u * 0.2f, Color(0xFF292524), u * 0.012f)
        drawFood(ItemKind.PANCAKE, pan, u * 0.3f)
        // Brett mit Erdbeere
        outlinedRect(Offset(w * 0.72f, h * 0.6f), Size(w * 0.24f, h * 0.3f), Color(0xFFE9B97A), u * 0.012f, u * 0.04f)
        drawFood(ItemKind.STRAWBERRY, Offset(w * 0.84f, h * 0.75f), u * 0.2f)
        // Eier im Hintergrund
        drawFood(ItemKind.EGG, Offset(w * 0.12f, h * 0.75f), u * 0.16f)
        drawFood(ItemKind.EGG, Offset(w * 0.22f, h * 0.78f), u * 0.16f)
    }
}
