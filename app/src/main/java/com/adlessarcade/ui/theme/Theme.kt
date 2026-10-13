package com.adlessarcade.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val ArcadeColorScheme = darkColorScheme(
    primary = NeonViolet,
    onPrimary = ArcadeText,
    secondary = NeonCyan,
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

@Composable
fun AdlessArcadeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ArcadeColorScheme,
        typography = ArcadeTypography,
        shapes = ArcadeShapes,
        content = content,
    )
}
