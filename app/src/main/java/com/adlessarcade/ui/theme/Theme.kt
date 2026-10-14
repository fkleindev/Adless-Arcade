package com.adlessarcade.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp

private fun colorSchemeFor(palette: ArcadePalette) = darkColorScheme(
    primary = palette.accent,
    onPrimary = ArcadeText,
    secondary = palette.accent2,
    onSecondary = ArcadeBackground,
    tertiary = NeonPink,
    background = ArcadeBackground,
    onBackground = ArcadeText,
    surface = ArcadeSurface,
    onSurface = ArcadeText,
    surfaceVariant = ArcadeSurfaceHigh,
    onSurfaceVariant = ArcadeTextMuted,
    surfaceContainerHigh = ArcadeSurfaceHigh,
    outline = ArcadeOutline,
)

private val ArcadeShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
)

/** Dunkles Arcade-Theme; Akzentfarben kommen aus dem im Shop gewählten App-Theme. */
@Composable
fun AdlessArcadeTheme(palette: ArcadePalette = LocalCosmetics.current.palette, content: @Composable () -> Unit) {
    val colorScheme = remember(palette) { colorSchemeFor(palette) }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = ArcadeTypography,
        shapes = ArcadeShapes,
        content = content,
    )
}
