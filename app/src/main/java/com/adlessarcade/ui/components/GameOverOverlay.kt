package com.adlessarcade.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.adlessarcade.ui.theme.ArcadeOutline
import com.adlessarcade.ui.theme.ArcadeSurface
import com.adlessarcade.ui.theme.ArcadeText
import com.adlessarcade.ui.theme.ArcadeTextMuted
import com.adlessarcade.ui.theme.NeonYellow

/** Einheitlicher Game-Over-Screen für alle Spiele. */
@Composable
fun GameOverOverlay(
    visible: Boolean,
    score: Int,
    best: Int,
    isNewBest: Boolean,
    onPlayAgain: () -> Unit,
    onMenu: () -> Unit,
    title: String = "Game Over",
    message: String = "Keine Züge mehr möglich",
    /** In der Runde verdiente Coins inkl. Bonus; null blendet die Zeile aus. */
    coinsEarned: Int? = null,
    bonusCoins: Int = 0,
    /** Erreichter Platz in der lokalen Bestenliste (1–10); null blendet die Zeile aus. */
    leaderboardRank: Int? = null,
    /** Bezeichnung der Bestenliste, z. B. „15 Minuten · Magierin“. */
    leaderboardName: String? = null,
    /** Zusätzliche Belohnungszeile, z. B. Runen bei Rune Survivors. */
    extraReward: String? = null,
    /** Zusätzlicher Inhalt vor den Buttons, z. B. die Schadensübersicht bei Rune Survivors. */
    extraContent: (@Composable ColumnScope.() -> Unit)? = null,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(250)),
        exit = fadeOut(tween(150)),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.72f))
                // Klicks auf das Spielfeld darunter abfangen.
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
            contentAlignment = Alignment.Center,
        ) {
            val shape = RoundedCornerShape(28.dp)
            Column(
                Modifier
                    .padding(28.dp)
                    .animateEnterExit(enter = scaleIn(initialScale = 0.85f, animationSpec = tween(300)))
                    .clip(shape)
                    .background(ArcadeSurface)
                    .border(1.dp, ArcadeOutline, shape)
                    // Scrollbar, falls die Höhe nicht reicht (Galaxy Towers 2 im Querformat)
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(title, style = MaterialTheme.typography.headlineMedium, color = ArcadeText)
                Text(message, style = MaterialTheme.typography.bodyMedium, color = ArcadeTextMuted)
                Spacer(Modifier.height(20.dp))
                Text("SCORE", style = MaterialTheme.typography.labelSmall, color = ArcadeTextMuted)
                Text("$score", style = MaterialTheme.typography.displayMedium, color = ArcadeText)
                Spacer(Modifier.height(6.dp))
                if (isNewBest) {
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(NeonYellow.copy(alpha = 0.15f))
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Star, contentDescription = null, tint = NeonYellow, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Neuer Rekord!", style = MaterialTheme.typography.labelLarge, color = NeonYellow)
                    }
                } else {
                    Text("Bester: $best", style = MaterialTheme.typography.bodyMedium, color = ArcadeTextMuted)
                }
                if (leaderboardRank != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "🏅 Platz $leaderboardRank in der Bestenliste" + (leaderboardName?.let { "\n$it" } ?: ""),
                        style = MaterialTheme.typography.labelLarge,
                        color = ArcadeText,
                        textAlign = TextAlign.Center,
                    )
                }
                if (coinsEarned != null) {
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CoinIcon(22.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("+$coinsEarned Coins verdient", style = MaterialTheme.typography.titleMedium, color = CoinGold)
                    }
                    if (bonusCoins > 0) {
                        Text(
                            "inkl. +$bonusCoins Rekordbonus",
                            style = MaterialTheme.typography.bodySmall,
                            color = ArcadeTextMuted,
                        )
                    }
                }
                if (extraReward != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(extraReward, style = MaterialTheme.typography.titleMedium, color = Color(0xFFA78BFA))
                }
                if (extraContent != null) {
                    Spacer(Modifier.height(20.dp))
                    extraContent()
                }
                Spacer(Modifier.height(24.dp))
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    PrimaryButton("Nochmal spielen", onPlayAgain)
                    SecondaryButton("Zum Menü", onMenu)
                }
            }
        }
    }
}
