package com.adlessarcade.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adlessarcade.ui.theme.ArcadeOutline
import com.adlessarcade.ui.theme.ArcadeSurfaceHigh
import com.adlessarcade.ui.theme.ArcadeText
import com.adlessarcade.ui.theme.ArcadeTextMuted
import com.adlessarcade.ui.theme.LocalCosmetics

val CoinGold = Color(0xFFFFC83D)
private val CoinDark = Color(0xFFB7791F)

/** Münze direkt im Canvas zeichnen (für Spiel-Renderer). */
fun DrawScope.drawCoin(center: Offset, radius: Float, alpha: Float = 1f) {
    drawCircle(CoinDark, radius, center, alpha = alpha)
    drawCircle(CoinGold, radius * 0.86f, center - Offset(0f, radius * 0.06f), alpha = alpha)
    drawCircle(CoinDark.copy(alpha = 0.6f), radius * 0.55f, center, alpha = alpha, style = Stroke(width = radius * 0.14f))
    drawCircle(Color.White.copy(alpha = 0.55f), radius * 0.16f, center + Offset(-radius * 0.35f, -radius * 0.35f), alpha = alpha)
}

@Composable
fun CoinIcon(size: Dp = 18.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) { drawCoin(center, this.size.minDimension / 2f) }
}

/** Kompakte Coin-Anzeige, z. B. im Hauptmenü und im Shop. */
@Composable
fun CoinChip(coins: Int, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier
            .clip(shape)
            .background(ArcadeSurfaceHigh)
            .border(1.dp, CoinGold.copy(alpha = 0.4f), shape)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoinIcon(18.dp)
        Spacer(Modifier.width(6.dp))
        Text("$coins", style = MaterialTheme.typography.titleMedium, color = ArcadeText)
    }
}

@Composable
fun AvatarCircle(symbol: String, size: Dp = 44.dp, modifier: Modifier = Modifier) {
    val palette = LocalCosmetics.current.palette
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(palette.accentBrush)
            .padding(2.dp)
            .clip(CircleShape)
            .background(ArcadeSurfaceHigh),
        contentAlignment = Alignment.Center,
    ) {
        Text(symbol, fontSize = (size.value * 0.48f).sp)
    }
}

/** Avatar + Titel des Spielers. */
@Composable
fun ProfileBadge(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val cosmetics = LocalCosmetics.current
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AvatarCircle(cosmetics.avatar)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f, fill = false)) {
            Text("SPIELER", style = MaterialTheme.typography.labelSmall, color = ArcadeTextMuted)
            Text(
                cosmetics.title,
                style = MaterialTheme.typography.titleMedium,
                color = ArcadeText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun TitleBadge(title: String, modifier: Modifier = Modifier) {
    val palette = LocalCosmetics.current.palette
    val shape = RoundedCornerShape(50)
    Text(
        title,
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Black),
        color = ArcadeText,
        modifier = modifier
            .clip(shape)
            .background(palette.accent.copy(alpha = 0.25f))
            .border(1.dp, ArcadeOutline, shape)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}
