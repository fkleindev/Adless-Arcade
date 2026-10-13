package com.adlessarcade.ui.modes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.adlessarcade.arcadeApp
import com.adlessarcade.core.GameInfo
import com.adlessarcade.core.ModeInfo
import com.adlessarcade.ui.components.ArcadeScaffold
import com.adlessarcade.ui.components.ModeCard
import com.adlessarcade.ui.theme.ArcadeTextMuted

/** Generisches Modus-Menü, das jedes Spiel aus seiner [GameInfo] bekommt. */
@Composable
fun ModeSelectScreen(
    game: GameInfo,
    onBack: () -> Unit,
    onModeClick: (ModeInfo) -> Unit,
) {
    val highscores = LocalContext.current.arcadeApp.highscores
    ArcadeScaffold(title = game.title, subtitle = "Modus wählen", onBack = onBack) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(game.accent.copy(alpha = 0.35f), game.accentSecondary.copy(alpha = 0.12f))
                        )
                    ),
                contentAlignment = Alignment.Center,
            ) {
                game.preview(Modifier.fillMaxSize().padding(28.dp))
            }
            Spacer(Modifier.height(20.dp))
            Text(game.description, style = MaterialTheme.typography.bodyMedium, color = ArcadeTextMuted)
            Spacer(Modifier.height(24.dp))
            Text("MODI", style = MaterialTheme.typography.labelSmall, color = ArcadeTextMuted)
            Spacer(Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                game.modes.forEach { mode ->
                    val bestFlow = remember(mode.id) { highscores.highscore(game.id, mode.id) }
                    val best by bestFlow.collectAsState(initial = null)
                    ModeCard(mode = mode, game = game, best = best, onClick = { onModeClick(mode) })
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
