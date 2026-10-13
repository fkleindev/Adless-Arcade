package com.adlessarcade.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.adlessarcade.ui.theme.ArcadeAccentBrush
import com.adlessarcade.ui.theme.ArcadeOutline
import com.adlessarcade.ui.theme.ArcadeSurfaceHigh
import com.adlessarcade.ui.theme.ArcadeText

private val ButtonShape = RoundedCornerShape(16.dp)

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(ButtonShape)
            .background(ArcadeAccentBrush)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = ArcadeText)
    }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(ButtonShape)
            .background(ArcadeSurfaceHigh)
            .border(1.dp, ArcadeOutline, ButtonShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = ArcadeText)
    }
}
