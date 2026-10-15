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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import com.adlessarcade.ui.components.SecondaryButton
import com.adlessarcade.ui.theme.ArcadeSurface
import com.adlessarcade.ui.theme.ArcadeTextMuted
import kotlinx.coroutines.launch

/** Generisches Modus-Menü, das jedes Spiel aus seiner [GameInfo] bekommt. */
@Composable
fun ModeSelectScreen(
    game: GameInfo,
    onBack: () -> Unit,
    onModeClick: (ModeInfo) -> Unit,
    onLeaderboardClick: () -> Unit,
    onExtraActionClick: (String) -> Unit,
) {
    val app = LocalContext.current.arcadeApp
    val scope = rememberCoroutineScope()
    /** Modus mit gespeichertem Lauf, für den gerade der Fortsetzen-Dialog offen ist. */
    var resumeMode by remember { mutableStateOf<Pair<ModeInfo, Int>?>(null) }

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
                    val key = mode.scoreKey()
                    val bestFlow = remember(key) { app.highscores.highscore(game.id, key) }
                    val best by bestFlow.collectAsState(initial = null)
                    val savedFlow = remember(key) { app.saves.savedScore(game.id, key) }
                    val saved by savedFlow.collectAsState(initial = null)
                    ModeCard(
                        mode = mode,
                        game = game,
                        best = best,
                        savedScore = saved,
                        onClick = {
                            val savedScore = saved
                            if (savedScore != null) resumeMode = mode to savedScore else onModeClick(mode)
                        },
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            SecondaryButton("🏅  Bestenliste", onLeaderboardClick)
            game.extraAction?.let { action ->
                Spacer(Modifier.height(10.dp))
                SecondaryButton(action.title, { onExtraActionClick(action.route) })
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    resumeMode?.let { (mode, score) ->
        AlertDialog(
            onDismissRequest = { resumeMode = null },
            containerColor = ArcadeSurface,
            title = { Text("Lauf fortsetzen?") },
            text = {
                Text(
                    "Du hast einen unterbrochenen ${mode.title}-Lauf mit $score Punkten.",
                    color = ArcadeTextMuted,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    resumeMode = null
                    onModeClick(mode)
                }) { Text("Fortsetzen", color = game.accentSecondary) }
            },
            dismissButton = {
                TextButton(onClick = {
                    resumeMode = null
                    scope.launch {
                        app.saves.clear(game.id, mode.scoreKey()).join()
                        onModeClick(mode)
                    }
                }) { Text("Neues Spiel", color = ArcadeTextMuted) }
            },
        )
    }
}
