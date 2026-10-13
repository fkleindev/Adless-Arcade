package com.adlessarcade.games.gaterush

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.adlessarcade.core.GameRegistry
import com.adlessarcade.ui.components.ArcadeIconButton
import com.adlessarcade.ui.components.ArcadeScaffold
import com.adlessarcade.ui.components.GameOverOverlay
import com.adlessarcade.ui.components.PrimaryButton
import com.adlessarcade.ui.components.SecondaryButton
import com.adlessarcade.ui.theme.ArcadeOutline
import com.adlessarcade.ui.theme.ArcadeSurface
import com.adlessarcade.ui.theme.ArcadeText
import com.adlessarcade.ui.theme.ArcadeTextMuted
import com.adlessarcade.ui.theme.NeonPink
import com.adlessarcade.ui.theme.NeonYellow
import kotlinx.coroutines.isActive

@Composable
fun GateRushScreen(
    modeId: String,
    onExit: () -> Unit,
    viewModel: GateRushViewModel = viewModel(),
) {
    val haptics = LocalHapticFeedback.current
    val textMeasurer = rememberTextMeasurer(cacheSize = 32)
    val hud = viewModel.hud

    // Game-Loop: läuft solange der Screen sichtbar ist, das ViewModel ignoriert Frames in Pause.
    LaunchedEffect(viewModel) {
        var last = 0L
        while (isActive) {
            androidx.compose.runtime.withFrameNanos { now ->
                if (last != 0L) viewModel.step(((now - last) / 1_000_000_000f).coerceAtMost(0.05f))
                last = now
            }
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { viewModel.pause() }

    LaunchedEffect(viewModel.hurtTick) {
        if (viewModel.hurtTick > 0) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }
    LaunchedEffect(viewModel.rewardTick) {
        if (viewModel.rewardTick > 0) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    val modeTitle = GameRegistry.find(GateRush.GAME_ID)?.modes?.firstOrNull { it.id == modeId }?.title

    ArcadeScaffold(
        title = "Gate Rush",
        subtitle = modeTitle,
        onBack = onExit,
        actions = {
            ArcadeIconButton(onClick = { if (viewModel.paused) viewModel.resume() else viewModel.pause() }) {
                PauseGlyph()
            }
        },
    ) {
        Box(Modifier.fillMaxSize()) {
            Canvas(
                Modifier
                    .fillMaxSize()
                    .pointerInput(viewModel) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            var lastX = down.position.x
                            val roadPx = size.width * 0.44f
                            viewModel.movePlayer(0f)
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                val dx = change.position.x - lastX
                                lastX = change.position.x
                                if (dx != 0f) viewModel.movePlayer(dx / roadPx)
                                change.consume()
                            }
                        }
                    }
            ) {
                viewModel.frame // Lesen registriert das Neuzeichnen pro Frame
                drawGateRush(viewModel.engine, viewModel.effects, viewModel.damageFlash, textMeasurer)
            }

            Hud(hud = hud, best = viewModel.best, modifier = Modifier.align(Alignment.TopCenter))
            StatBar(hud = hud, modifier = Modifier.align(Alignment.BottomCenter))

            if (!viewModel.started && !viewModel.gameOver) {
                StartHint(Modifier.align(Alignment.Center))
            }

            if (viewModel.paused) {
                PauseOverlay(
                    onResume = viewModel::resume,
                    onRestart = viewModel::restart,
                    onMenu = onExit,
                )
            }

            GameOverOverlay(
                visible = viewModel.gameOver,
                score = hud.score,
                best = viewModel.best,
                isNewBest = viewModel.isNewBest,
                onPlayAgain = viewModel::restart,
                onMenu = onExit,
                message = "Die Gegner haben die Linie durchbrochen",
            )
        }
    }
}

@Composable
private fun Hud(hud: GateRushHud, best: Int, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f)) {
            repeat(hud.hearts) {
                Icon(Icons.Filled.Favorite, contentDescription = null, tint = NeonPink, modifier = Modifier.size(22.dp))
            }
        }
        Text("${hud.score}", style = MaterialTheme.typography.headlineMedium, color = ArcadeText)
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Star, contentDescription = null, tint = NeonYellow, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("$best", style = MaterialTheme.typography.titleMedium, color = ArcadeTextMuted)
        }
    }
}

@Composable
private fun StatBar(hud: GateRushHud, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        StatChip("DMG", formatStat(hud.damage))
        StatChip("RATE", "${formatStat(hud.fireRate)}/s")
        StatChip("MULTI", "x${hud.multishot}")
        StatChip("PIERCE", "${hud.pierce}")
    }
}

private fun formatStat(value: Float): String =
    if (value >= 10f || value % 1f == 0f) value.toInt().toString() else "%.1f".format(value)

@Composable
private fun StatChip(label: String, value: String) {
    Column(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(ArcadeSurface.copy(alpha = 0.85f))
            .border(1.dp, ArcadeOutline, RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = ArcadeTextMuted)
        Text(value, style = MaterialTheme.typography.titleMedium, color = ArcadeText)
    }
}

@Composable
private fun StartHint(modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(ArcadeSurface.copy(alpha = 0.9f))
            .border(1.dp, ArcadeOutline, RoundedCornerShape(20.dp))
            .padding(horizontal = 24.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("⟵  Ziehen zum Bewegen  ⟶", style = MaterialTheme.typography.titleMedium, color = ArcadeText)
        Spacer(Modifier.height(6.dp))
        Text(
            "Schieß Gegner ab, bevor sie die Linie erreichen.\nLauf durch grüne Tore, schieß auf Kisten.",
            style = MaterialTheme.typography.bodySmall,
            color = ArcadeTextMuted,
        )
    }
}

@Composable
private fun PauseOverlay(onResume: () -> Unit, onRestart: () -> Unit, onMenu: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        val shape = RoundedCornerShape(28.dp)
        Column(
            Modifier
                .padding(28.dp)
                .clip(shape)
                .background(ArcadeSurface)
                .border(1.dp, ArcadeOutline, shape)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Pausiert", style = MaterialTheme.typography.headlineMedium, color = ArcadeText)
            Spacer(Modifier.height(8.dp))
            PrimaryButton("Weiter", onResume)
            SecondaryButton("Neu starten", onRestart)
            SecondaryButton("Zum Menü", onMenu)
        }
    }
}

@Composable
private fun PauseGlyph() {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(2) {
            Box(
                Modifier
                    .size(width = 4.dp, height = 14.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(ArcadeText)
            )
        }
    }
}
