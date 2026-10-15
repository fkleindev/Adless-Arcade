package com.adlessarcade.ui.leaderboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adlessarcade.arcadeApp
import com.adlessarcade.core.GameInfo
import com.adlessarcade.core.leaderboard.LeaderboardEntry
import com.adlessarcade.core.leaderboard.LeaderboardKey
import com.adlessarcade.ui.components.ArcadeScaffold
import com.adlessarcade.ui.theme.ArcadeOutline
import com.adlessarcade.ui.theme.ArcadeSurface
import com.adlessarcade.ui.theme.ArcadeSurfaceHigh
import com.adlessarcade.ui.theme.ArcadeText
import com.adlessarcade.ui.theme.ArcadeTextMuted
import com.adlessarcade.ui.theme.LocalCosmetics
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Gold = Color(0xFFFACC15)
private val Silver = Color(0xFFCBD5E1)
private val Bronze = Color(0xFFD97706)

/** Lokale Bestenliste eines Spiels: ein Reiter pro Modus, bei Spielen mit Varianten (Klassen) zusätzlich pro Variante. */
@Composable
fun LeaderboardScreen(game: GameInfo, onBack: () -> Unit) {
    val app = LocalContext.current.arcadeApp
    val modes = game.modes.filter { it.available }
    var modeId by rememberSaveable { mutableStateOf(modes.first().id) }

    // Standard-Variante: die zuletzt gespielte (bei Rune Survivors die Klasse des letzten Laufs)
    val lastVariantFlow = remember(game.id) { app.leaderboards.lastVariant(game.id) }
    val lastVariant by lastVariantFlow.collectAsState(initial = null)
    // Zuletzt gespielte Gruppe (Stage) aus dem gespeicherten Schlüssel ablesen, z. B. „graveyard_class_mage“
    val lastGroup = game.leaderboardGroups.firstOrNull { it.id.isNotEmpty() && lastVariant?.startsWith("${it.id}_") == true }
        ?: game.leaderboardGroups.firstOrNull()
    val lastPlain = lastGroup?.id?.takeIf { it.isNotEmpty() }?.let { lastVariant?.removePrefix("${it}_") } ?: lastVariant
    val equippedVariant = game.leaderboardVariants.firstOrNull { it.id == lastPlain }?.id
        ?: game.leaderboardVariants.firstOrNull()?.id
    var chosenVariant by rememberSaveable { mutableStateOf<String?>(null) }
    val variant = chosenVariant ?: equippedVariant
    var chosenGroup by rememberSaveable { mutableStateOf<String?>(null) }
    val group = chosenGroup ?: lastGroup?.id

    val boardVariant = if (group.isNullOrEmpty() || variant == null) variant else "${group}_$variant"
    val board = LeaderboardKey(game.id, modeId, boardVariant)
    val entriesFlow = remember(board) { app.leaderboards.entries(board) }
    val entries by entriesFlow.collectAsState(initial = null)

    ArcadeScaffold(title = "Bestenliste", subtitle = game.title, onBack = onBack) {
        Column(Modifier.fillMaxSize()) {
            ChipRow(
                options = modes.map { it.id to it.title },
                selected = modeId,
                onSelect = { modeId = it },
            )
            if (game.leaderboardGroups.isNotEmpty()) {
                ChipRow(
                    options = game.leaderboardGroups.map { it.id to it.title },
                    selected = group,
                    onSelect = { chosenGroup = it },
                    small = true,
                )
            }
            if (game.leaderboardVariants.isNotEmpty()) {
                ChipRow(
                    options = game.leaderboardVariants.map { it.id to it.title },
                    selected = variant,
                    onSelect = { chosenVariant = it },
                    small = true,
                )
            }
            val list = entries
            when {
                list == null -> Unit
                list.isEmpty() -> EmptyState()
                else -> LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    itemsIndexed(list) { index, entry -> EntryRow(index + 1, entry) }
                }
            }
        }
    }
}

@Composable
private fun ChipRow(options: List<Pair<String, String>>, selected: String?, onSelect: (String) -> Unit, small: Boolean = false) {
    val palette = LocalCosmetics.current.palette
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = if (small) 4.dp else 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { (id, title) ->
            val isSelected = id == selected
            val shape = RoundedCornerShape(50)
            Text(
                title,
                style = if (small) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
                color = if (isSelected) ArcadeText else ArcadeTextMuted,
                modifier = Modifier
                    .clip(shape)
                    .then(
                        when {
                            isSelected && !small -> Modifier.background(palette.accentBrush)
                            isSelected -> Modifier.background(palette.accent2.copy(alpha = 0.3f)).border(1.dp, palette.accent2, shape)
                            else -> Modifier.background(ArcadeSurfaceHigh).border(1.dp, ArcadeOutline, shape)
                        }
                    )
                    .clickable { onSelect(id) }
                    .padding(horizontal = if (small) 12.dp else 16.dp, vertical = if (small) 6.dp else 9.dp),
            )
        }
    }
}

@Composable
private fun EntryRow(rank: Int, entry: LeaderboardEntry) {
    val medal = when (rank) {
        1 -> Gold
        2 -> Silver
        3 -> Bronze
        else -> null
    }
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(ArcadeSurface)
            .border(1.dp, medal?.copy(alpha = 0.6f) ?: ArcadeOutline, shape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(50))
                .background(medal?.copy(alpha = 0.2f) ?: ArcadeSurfaceHigh),
            contentAlignment = Alignment.Center,
        ) {
            Text("$rank", style = MaterialTheme.typography.titleMedium, color = medal ?: ArcadeTextMuted)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("%,d".format(Locale.GERMANY, entry.score), style = MaterialTheme.typography.titleLarge, color = ArcadeText)
                if (entry.victory) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "SIEG",
                        style = MaterialTheme.typography.labelSmall,
                        color = Gold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Gold.copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
            entry.details?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = ArcadeTextMuted) }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(formatDate(entry.timestamp), style = MaterialTheme.typography.labelSmall, color = ArcadeTextMuted)
            (entry.appVersion?.let { "v$it" } ?: "ältere Version").let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = ArcadeTextMuted,
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .clip(RoundedCornerShape(50))
                        .background(ArcadeSurfaceHigh)
                        .padding(horizontal = 6.dp, vertical = 1.dp),
                )
            }
        }
    }
}

private fun formatDate(timestamp: Long): String =
    if (timestamp <= 0L) "früher" else SimpleDateFormat("dd.MM.yy", Locale.GERMANY).format(Date(timestamp))

@Composable
private fun EmptyState() {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("🏅", fontSize = 40.sp)
        Spacer(Modifier.size(12.dp))
        Text("Noch keine Einträge", style = MaterialTheme.typography.titleMedium, color = ArcadeText)
        Text(
            "Spiel eine Runde – deine 10 besten Ergebnisse landen hier.",
            style = MaterialTheme.typography.bodyMedium,
            color = ArcadeTextMuted,
            textAlign = TextAlign.Center,
        )
    }
}
