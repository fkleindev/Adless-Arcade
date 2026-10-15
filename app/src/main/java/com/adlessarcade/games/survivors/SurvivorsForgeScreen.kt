package com.adlessarcade.games.survivors

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.adlessarcade.arcadeApp
import com.adlessarcade.games.survivors.progress.SurvivorsProgress
import com.adlessarcade.games.survivors.progress.SurvivorsUnlock
import com.adlessarcade.games.survivors.progress.SurvivorsUnlocks
import com.adlessarcade.games.survivors.progress.UnlockKind
import com.adlessarcade.games.survivors.progress.UnlockLogic
import com.adlessarcade.ui.components.ArcadeScaffold
import com.adlessarcade.ui.theme.ArcadeOutline
import com.adlessarcade.ui.theme.ArcadeSurface
import com.adlessarcade.ui.theme.ArcadeSurfaceHigh
import com.adlessarcade.ui.theme.ArcadeText
import com.adlessarcade.ui.theme.ArcadeTextMuted
import com.adlessarcade.ui.theme.LocalCosmetics
import kotlinx.coroutines.launch

/**
 * Runen-Schmiede: eigener Shop von Rune Survivors. Bezahlt wird mit Runen, die es nur in Rune Survivors gibt;
 * manche Freischaltungen haben zusätzliche Kriterien (z. B. „Dämonenfürst besiegt“).
 */
@Composable
fun SurvivorsForgeScreen(onBack: () -> Unit) {
    val repository = LocalContext.current.arcadeApp.survivorsProgress
    val progressState by repository.state.collectAsState(initial = null)
    var tab by rememberSaveable { mutableStateOf(UnlockKind.CLASS) }
    var pending by remember { mutableStateOf<SurvivorsUnlock?>(null) }
    var failed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    ArcadeScaffold(
        title = "Runen-Schmiede",
        subtitle = "Rune Survivors",
        onBack = onBack,
        actions = { progressState?.let { RuneChip(it.runes) } },
    ) {
        val progress = progressState ?: return@ArcadeScaffold
        Column(Modifier.fillMaxSize()) {
            Tabs(selected = tab, onSelect = { tab = it })
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Text(
                        "Runen verdienst du nur in Rune Survivors: pro überlebter Minute, für Elites, Bosse und Siege. Neue Waffen brauchen zusätzlich Kills mit der Grundwaffe desselben Talents.",
                        style = MaterialTheme.typography.bodySmall,
                        color = ArcadeTextMuted,
                    )
                }
                when (tab) {
                    UnlockKind.CLASS -> items(SurvivorsUnlocks.ofKind(UnlockKind.CLASS), key = { it.id }) { unlock ->
                        val heroClass = HeroClass.fromShopId(unlock.id) ?: return@items
                        val owned = UnlockLogic.isUnlocked(progress, unlock)
                        ClassCard(
                            heroClass = heroClass,
                            dimmed = !owned,
                            selected = false,
                            onClick = if (owned) null else ({ pending = unlock }),
                        ) {
                            UnlockFooter(progress, unlock, owned)
                        }
                    }
                    UnlockKind.WEAPON -> items(SurvivorsUnlocks.ofKind(UnlockKind.WEAPON), key = { it.id }) { unlock ->
                        val weapon = WeaponKind.entries.firstOrNull { it.unlockId == unlock.id } ?: return@items
                        val owned = UnlockLogic.isUnlocked(progress, unlock)
                        WeaponCard(weapon, dimmed = !owned, onClick = if (owned) null else ({ pending = unlock })) {
                            UnlockFooter(progress, unlock, owned)
                        }
                    }
                    UnlockKind.STAGE -> {
                        item { CurrentStage() }
                        item { ComingSoon("Neue Stages", "Weitere Stages mit eigenen Gegnern und Endbossen folgen.") }
                    }
                }
            }
        }
    }

    pending?.let { unlock ->
        val canBuy = UnlockLogic.canBuy(progressState ?: SurvivorsProgress(), unlock)
        val missing = unlock.runePrice - (progressState?.runes ?: 0)
        AlertDialog(
            onDismissRequest = { pending = null },
            containerColor = ArcadeSurface,
            title = { Text(if (canBuy) "„${unlock.name}“ freischalten?" else "Noch nicht freischaltbar") },
            text = {
                Text(
                    when {
                        canBuy -> "Für $RUNE_SYMBOL ${unlock.runePrice} Runen freischalten."
                        missing > 0 -> "Dir fehlen noch $missing Runen. Spiel eine Runde Rune Survivors."
                        else -> "Erfülle zuerst alle Bedingungen."
                    },
                    color = ArcadeTextMuted,
                )
            },
            confirmButton = {
                if (canBuy) {
                    TextButton(onClick = {
                        pending = null
                        scope.launch { failed = !repository.purchase(unlock) }
                    }) { Text("Freischalten", color = RuneViolet) }
                } else {
                    TextButton(onClick = { pending = null }) { Text("OK", color = ArcadeText) }
                }
            },
            dismissButton = {
                if (canBuy) TextButton(onClick = { pending = null }) { Text("Abbrechen", color = ArcadeTextMuted) }
            },
        )
    }

    if (failed) {
        AlertDialog(
            onDismissRequest = { failed = false },
            containerColor = ArcadeSurface,
            title = { Text("Freischalten fehlgeschlagen") },
            text = { Text("Bitte versuche es noch einmal.", color = ArcadeTextMuted) },
            confirmButton = { TextButton(onClick = { failed = false }) { Text("OK", color = ArcadeText) } },
        )
    }
}

/** Preis, Status und Kriterien-Checkliste unter einer Karte. */
@Composable
private fun UnlockFooter(progress: SurvivorsProgress, unlock: SurvivorsUnlock, owned: Boolean) {
    val palette = LocalCosmetics.current.palette
    if (owned) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = palette.accent2, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Freigeschaltet", style = MaterialTheme.typography.labelLarge, color = palette.accent2)
        }
        return
    }
    val affordable = progress.runes >= unlock.runePrice
    Text(
        "$RUNE_SYMBOL ${unlock.runePrice} Runen",
        style = MaterialTheme.typography.labelLarge,
        color = if (affordable) RuneViolet else RuneViolet.copy(alpha = 0.5f),
    )
    unlock.requirements.forEach { requirement ->
        val met = requirement.isMet(progress)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (met) Icons.Filled.Check else Icons.Filled.Lock,
                contentDescription = null,
                tint = if (met) palette.accent2 else ArcadeTextMuted,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(requirement.text, style = MaterialTheme.typography.bodySmall, color = if (met) ArcadeText else ArcadeTextMuted)
        }
    }
}

/** Karte einer freischaltbaren Waffe: Symbol, Beschreibung und Entwicklungs-Hinweis. */
@Composable
private fun WeaponCard(weapon: WeaponKind, dimmed: Boolean, onClick: (() -> Unit)?, footer: @Composable () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(ArcadeSurface)
            .border(1.dp, ArcadeOutline, shape)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(64.dp).clip(RoundedCornerShape(16.dp)).background(ArcadeSurfaceHigh),
            contentAlignment = Alignment.Center,
        ) {
            Text(weapon.icon, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.alpha(if (dimmed) 0.5f else 1f))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(weapon.title, style = MaterialTheme.typography.titleMedium, color = if (dimmed) ArcadeTextMuted else ArcadeText)
            Text(weapon.description(1), style = MaterialTheme.typography.bodySmall, color = ArcadeTextMuted)
            Text(
                "Entwicklung: ${weapon.evolutionPassive.icon} ${weapon.evolutionPassive.title} → ${weapon.evolutionTitle}",
                style = MaterialTheme.typography.labelMedium,
                color = RuneViolet,
            )
            Spacer(Modifier.height(4.dp))
            footer()
        }
    }
}

@Composable
private fun Tabs(selected: UnlockKind, onSelect: (UnlockKind) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        UnlockKind.entries.forEach { kind ->
            val isSelected = kind == selected
            val shape = RoundedCornerShape(50)
            Text(
                kind.title,
                style = MaterialTheme.typography.labelLarge,
                color = if (isSelected) ArcadeText else ArcadeTextMuted,
                modifier = Modifier
                    .clip(shape)
                    .then(
                        if (isSelected) Modifier.background(RuneViolet.copy(alpha = 0.35f)).border(1.dp, RuneViolet, shape)
                        else Modifier.background(ArcadeSurfaceHigh).border(1.dp, ArcadeOutline, shape)
                    )
                    .clickable { onSelect(kind) }
                    .padding(horizontal = 16.dp, vertical = 9.dp),
            )
        }
    }
}

@Composable
private fun CurrentStage() {
    val shape = RoundedCornerShape(20.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(ArcadeSurface).border(1.dp, ArcadeOutline, shape).padding(12.dp),
    ) {
        Box(Modifier.fillMaxWidth().height(110.dp).clip(RoundedCornerShape(14.dp))) {
            Canvas(Modifier.fillMaxSize()) { drawSurvivorThemePreview(SurvivorsStages.MEADOW) }
        }
        Spacer(Modifier.height(10.dp))
        Text("Fantasy-Wiese", style = MaterialTheme.typography.titleMedium, color = ArcadeText)
        Text("Stage 1 · Endboss: Dämonenfürst", style = MaterialTheme.typography.bodySmall, color = ArcadeTextMuted)
    }
}

@Composable
private fun ComingSoon(title: String, text: String) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(ArcadeSurface)
            .border(1.dp, ArcadeOutline, shape)
            .padding(vertical = 28.dp, horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(RUNE_SYMBOL, style = MaterialTheme.typography.headlineMedium, color = RuneViolet)
        Spacer(Modifier.height(6.dp))
        Text("$title – bald verfügbar", style = MaterialTheme.typography.titleMedium, color = ArcadeText)
        Text(text, style = MaterialTheme.typography.bodySmall, color = ArcadeTextMuted, textAlign = TextAlign.Center)
    }
}
