package com.adlessarcade.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

val ArcadeBackground = Color(0xFF0B0C12)
val ArcadeBackgroundTop = Color(0xFF14152A)
val ArcadeSurface = Color(0xFF151722)
val ArcadeSurfaceHigh = Color(0xFF1E2130)
val ArcadeOutline = Color(0xFF2A2E40)
val ArcadeText = Color(0xFFECEDF5)
val ArcadeTextMuted = Color(0xFF9097B0)

val NeonViolet = Color(0xFF8B5CF6)
val NeonCyan = Color(0xFF22D3EE)
val NeonPink = Color(0xFFF472B6)
val NeonYellow = Color(0xFFFFD43B)

val BoardBackground = Color(0xFF10121C)
val EmptyCell = Color(0xFF1B1E2C)

/** Blockfarben, Index 1..7 entspricht den Farbwerten im Spielfeld. */
val BlockColors = listOf(
    Color(0xFFFF5C6C),
    Color(0xFFFF9F43),
    Color(0xFFFFD43B),
    Color(0xFF4ADE80),
    Color(0xFF22D3EE),
    Color(0xFF4C8DFF),
    Color(0xFFA78BFA),
)

fun blockColor(value: Int): Color = BlockColors[(value - 1).mod(BlockColors.size)]

val ArcadeBackgroundBrush = Brush.verticalGradient(listOf(ArcadeBackgroundTop, ArcadeBackground, ArcadeBackground))
val ArcadeAccentBrush = Brush.linearGradient(listOf(NeonViolet, NeonCyan))
