package com.adlessarcade.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adlessarcade.core.achievements.Achievement
import com.adlessarcade.ui.theme.ArcadeSurface
import com.adlessarcade.ui.theme.ArcadeText
import com.adlessarcade.ui.theme.ArcadeTextMuted
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

private const val SHOW_MILLIS = 3200L

/**
 * Blendet neu freigeschaltete Erfolge app-weit oben ein, einen nach dem anderen.
 * Liegt über allen Screens, fängt aber keine Berührungen ab.
 */
@Composable
fun AchievementToastHost(unlocks: SharedFlow<Achievement>) {
    val haptics = LocalHapticFeedback.current
    var current by remember { mutableStateOf<Achievement?>(null) }
    // Letzter Erfolg bleibt für die Ausblend-Animation stehen.
    var shown by remember { mutableStateOf<Achievement?>(null) }

    LaunchedEffect(unlocks) {
        val queue = Channel<Achievement>(Channel.UNLIMITED)
        launch { unlocks.collect { queue.send(it) } }
        for (achievement in queue) {
            shown = achievement
            current = achievement
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            delay(SHOW_MILLIS)
            current = null
            delay(400)
        }
    }

    Box(Modifier.fillMaxSize().safeDrawingPadding()) {
        AnimatedVisibility(
            visible = current != null,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter).padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            shown?.let { AchievementToast(it) }
        }
    }
}

@Composable
private fun AchievementToast(achievement: Achievement) {
    val gold = Color(0xFFFACC15)
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.horizontalGradient(listOf(Color(0xFF3B2F0B), ArcadeSurface)))
            .border(1.5.dp, gold, shape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(gold.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(achievement.icon, fontSize = 24.sp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("ERFOLG FREIGESCHALTET", style = MaterialTheme.typography.labelSmall, color = gold)
            Text(achievement.name, style = MaterialTheme.typography.titleMedium, color = ArcadeText)
            Text("Neuer Titel: ${achievement.titleName}", style = MaterialTheme.typography.bodySmall, color = ArcadeTextMuted)
        }
    }
}
