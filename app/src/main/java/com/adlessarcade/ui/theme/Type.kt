package com.adlessarcade.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Base = Typography()

val ArcadeTypography = Typography(
    displayLarge = Base.displayLarge.copy(fontWeight = FontWeight.Black, letterSpacing = (-1).sp),
    displayMedium = Base.displayMedium.copy(fontWeight = FontWeight.Black),
    headlineLarge = Base.headlineLarge.copy(fontWeight = FontWeight.ExtraBold),
    headlineMedium = Base.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
    titleLarge = Base.titleLarge.copy(fontWeight = FontWeight.Bold),
    titleMedium = Base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = Base.labelLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp),
)
