package com.adlessarcade.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.adlessarcade.core.GameInfo
import com.adlessarcade.core.ModeInfo
import com.adlessarcade.ui.theme.ArcadeOutline
import com.adlessarcade.ui.theme.ArcadeSurface
import com.adlessarcade.ui.theme.ArcadeSurfaceHigh
import com.adlessarcade.ui.theme.ArcadeText
import com.adlessarcade.ui.theme.ArcadeTextMuted
import com.adlessarcade.ui.theme.NeonYellow

private val CardShape = RoundedCornerShape(24.dp)

@Composable
fun GameCard(game: GameInfo, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(CardShape)
            .background(ArcadeSurface)
            .border(
                1.dp,
                Brush.linearGradient(listOf(game.accent.copy(alpha = 0.7f), game.accentSecondary.copy(alpha = 0.2f))),
                CardShape,
            )
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1.2f)
                .clip(RoundedCornerShape(18.dp))
                .background(
                    Brush.linearGradient(
                        listOf(game.accent.copy(alpha = 0.28f), game.accentSecondary.copy(alpha = 0.10f))
                    )
                ),
            contentAlignment = Alignment.Center,
        ) {
            game.preview(Modifier.fillMaxSize().padding(18.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text(
            game.title,
            style = MaterialTheme.typography.titleMedium,
            color = ArcadeText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        // Feste Zeilenzahl, damit alle Kacheln einer Reihe gleich hoch sind.
        Text(
            game.tagline,
            style = MaterialTheme.typography.bodySmall,
            color = ArcadeTextMuted,
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun ComingSoonCard(modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(CardShape)
            .background(ArcadeSurface.copy(alpha = 0.5f))
            .border(1.dp, ArcadeOutline, CardShape)
            .padding(14.dp)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1.2f)
                .clip(RoundedCornerShape(18.dp))
                .background(ArcadeSurfaceHigh.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, tint = ArcadeTextMuted, modifier = Modifier.size(36.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text("Bald mehr", style = MaterialTheme.typography.titleMedium, color = ArcadeTextMuted)
        Text("Neue Spiele folgen", style = MaterialTheme.typography.bodySmall, color = ArcadeTextMuted, minLines = 2)
    }
}

@Composable
fun ModeCard(
    mode: ModeInfo,
    game: GameInfo,
    best: Int?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    savedScore: Int? = null,
) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier
            .fillMaxWidth()
            .alpha(if (mode.available) 1f else 0.5f)
            .clip(shape)
            .background(ArcadeSurface)
            .border(1.dp, if (mode.available) game.accent.copy(alpha = 0.45f) else ArcadeOutline, shape)
            .clickable(enabled = mode.available, onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(
                    if (mode.available) Brush.linearGradient(listOf(game.accent, game.accentSecondary))
                    else Brush.linearGradient(listOf(ArcadeSurfaceHigh, ArcadeSurfaceHigh))
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (mode.available) Icons.Filled.PlayArrow else Icons.Filled.Lock,
                contentDescription = null,
                tint = ArcadeText,
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(mode.title, style = MaterialTheme.typography.titleMedium, color = ArcadeText)
            Text(mode.description, style = MaterialTheme.typography.bodySmall, color = ArcadeTextMuted)
            if (mode.available && savedScore != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Gespeicherter Lauf · $savedScore Punkte",
                    style = MaterialTheme.typography.labelLarge,
                    color = game.accentSecondary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(game.accentSecondary.copy(alpha = 0.12f))
                        .padding(horizontal = 10.dp, vertical = 3.dp),
                )
            }
        }
        if (mode.available && best != null) {
            Spacer(Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Star, contentDescription = null, tint = NeonYellow, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("BEST", style = MaterialTheme.typography.labelSmall, color = ArcadeTextMuted)
                }
                Text("$best", style = MaterialTheme.typography.titleLarge, color = ArcadeText)
            }
        }
    }
}
