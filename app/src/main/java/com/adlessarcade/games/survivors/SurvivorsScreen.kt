package com.adlessarcade.games.survivors

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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.adlessarcade.arcadeApp
import com.adlessarcade.core.GameRegistry
import com.adlessarcade.games.survivors.progress.SurvivorsUnlocks
import com.adlessarcade.games.survivors.progress.UnlockLogic
import com.adlessarcade.ui.components.ArcadeIconButton
import com.adlessarcade.ui.components.ArcadeScaffold
import com.adlessarcade.ui.components.CoinGold
import com.adlessarcade.ui.components.CoinIcon
import com.adlessarcade.ui.components.GameOverOverlay
import com.adlessarcade.ui.components.PrimaryButton
import com.adlessarcade.ui.components.SecondaryButton
import com.adlessarcade.ui.theme.ArcadeOutline
import com.adlessarcade.ui.theme.ArcadeSurface
import com.adlessarcade.ui.theme.ArcadeSurfaceHigh
import com.adlessarcade.ui.theme.ArcadeText
import com.adlessarcade.ui.theme.ArcadeTextMuted
import com.adlessarcade.ui.theme.LocalCosmetics
import com.adlessarcade.ui.theme.NeonYellow
import kotlinx.coroutines.isActive
import kotlin.math.hypot

@Composable
fun SurvivorsScreen(onExit: () -> Unit, onOpenForge: () -> Unit, viewModel: SurvivorsViewModel = viewModel()) {
    val haptics = LocalHapticFeedback.current
    val modeTitle = GameRegistry.find(Survivors.GAME_ID)?.modes?.firstOrNull { it.id == viewModel.mode.id }?.title
    val engine = viewModel.engine

    LaunchedEffect(viewModel) {
        var last = 0L
        while (isActive) {
            withFrameNanos { now ->
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

    ArcadeScaffold(
        title = "Rune Survivors",
        subtitle = modeTitle,
        onBack = onExit,
        actions = {
            if (engine != null && !viewModel.finished) {
                ArcadeIconButton(onClick = { if (viewModel.paused) viewModel.resume() else viewModel.pause() }) {
                    PauseGlyph()
                }
            }
        },
    ) {
        when {
            !viewModel.loaded -> Unit
            engine == null -> ClassSelect(
                onStart = { heroClass, weapons, stage -> viewModel.start(heroClass, weapons, stage) },
                onOpenForge = onOpenForge,
            )
            else -> GameView(viewModel, onExit)
        }
    }
}

// --- Klassenwahl ---

@Composable
private fun ClassSelect(onStart: (HeroClass, Set<WeaponKind>, SurvivorsStage) -> Unit, onOpenForge: () -> Unit) {
    val progressFlow = LocalContext.current.arcadeApp.survivorsProgress.state
    val progress by progressFlow.collectAsState(initial = null)
    val loaded = progress ?: return
    val last = UnlockLogic.selectedClass(loaded)
    var selected by remember(last) { mutableStateOf(last) }
    val lastStage = UnlockLogic.selectedStage(loaded)
    var stage by remember(lastStage) { mutableStateOf(lastStage) }

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("STAGE WÄHLEN", style = MaterialTheme.typography.labelSmall, color = ArcadeTextMuted, modifier = Modifier.weight(1f))
            RuneChip(loaded.runes, onClick = onOpenForge)
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SurvivorsStage.entries.forEach { s ->
                val owned = UnlockLogic.isUnlocked(loaded, s)
                StageChip(
                    stage = s,
                    owned = owned,
                    selected = owned && stage == s,
                    modifier = Modifier.weight(1f),
                    onClick = { if (owned) stage = s else onOpenForge() },
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("KLASSE WÄHLEN", style = MaterialTheme.typography.labelSmall, color = ArcadeTextMuted)
        Spacer(Modifier.height(8.dp))
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            HeroClass.entries.forEach { heroClass ->
                val unlock = SurvivorsUnlocks.forClass(heroClass)
                val owned = UnlockLogic.isUnlocked(loaded, unlock)
                ClassCard(
                    heroClass = heroClass,
                    dimmed = !owned,
                    selected = owned && selected == heroClass,
                    onClick = { if (owned) selected = heroClass else onOpenForge() },
                ) {
                    if (!owned) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Lock, contentDescription = null, tint = ArcadeTextMuted, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("$RUNE_SYMBOL ${unlock.runePrice}", style = MaterialTheme.typography.labelLarge, color = RuneViolet)
                            Text(" · In der Runen-Schmiede freischalten", style = MaterialTheme.typography.labelLarge, color = ArcadeTextMuted)
                        }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }
        Spacer(Modifier.height(12.dp))
        PrimaryButton("Als ${selected.title} starten", onClick = { onStart(selected, UnlockLogic.availableWeapons(loaded), stage) })
        Spacer(Modifier.height(16.dp))
    }
}

/** Auswahlkarte einer Stage mit Mini-Vorschau; gesperrte Stages zeigen ein Schloss. */
@Composable
private fun StageChip(stage: SurvivorsStage, owned: Boolean, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val palette = LocalCosmetics.current.palette
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier
            .clip(shape)
            .background(ArcadeSurface)
            .border(if (selected) 2.dp else 1.dp, if (selected) palette.accent2 else ArcadeOutline, shape)
            .clickable(onClick = onClick)
            .padding(8.dp),
    ) {
        Box(Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(10.dp)).alpha(if (owned) 1f else 0.4f)) {
            Canvas(Modifier.fillMaxSize()) { drawSurvivorThemePreview(stage.theme) }
            if (!owned) {
                Icon(Icons.Filled.Lock, contentDescription = null, tint = ArcadeText, modifier = Modifier.align(Alignment.Center).size(20.dp))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "${stage.number}. ${stage.title}",
            style = MaterialTheme.typography.labelLarge,
            color = if (owned) ArcadeText else ArcadeTextMuted,
            maxLines = 1,
        )
        Text(
            if (owned) "Endboss: ${stage.finalBoss.title}" else "In der Runen-Schmiede",
            style = MaterialTheme.typography.labelSmall,
            color = ArcadeTextMuted,
            maxLines = 1,
        )
    }
}

/** Runen-Guthaben als Chip (öffnet die Runen-Schmiede). */
@Composable
internal fun RuneChip(runes: Int, onClick: (() -> Unit)? = null) {
    val shape = RoundedCornerShape(50)
    Row(
        Modifier
            .clip(shape)
            .background(RuneViolet.copy(alpha = 0.15f))
            .border(1.dp, RuneViolet.copy(alpha = 0.6f), shape)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(RUNE_SYMBOL, style = MaterialTheme.typography.titleMedium, color = RuneViolet)
        Spacer(Modifier.width(6.dp))
        Text("$runes", style = MaterialTheme.typography.titleMedium, color = ArcadeText)
    }
}

/** Karte einer Klasse – in der Klassenwahl und in der Runen-Schmiede. [footer] zeigt Preis bzw. Status. */
@Composable
internal fun ClassCard(
    heroClass: HeroClass,
    dimmed: Boolean,
    selected: Boolean,
    onClick: (() -> Unit)?,
    footer: @Composable () -> Unit = {},
) {
    val palette = LocalCosmetics.current.palette
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(ArcadeSurface)
            .border(if (selected) 2.dp else 1.dp, if (selected) palette.accent2 else ArcadeOutline, shape)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(72.dp).alpha(if (dimmed) 0.5f else 1f).clip(RoundedCornerShape(16.dp)).background(ArcadeSurfaceHigh),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize().padding(10.dp)) {
                drawHeroClass(heroClass, center, size.minDimension * 0.75f, 0f)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(heroClass.title, style = MaterialTheme.typography.titleMedium, color = if (dimmed) ArcadeTextMuted else ArcadeText)
            Text(heroClass.description, style = MaterialTheme.typography.bodySmall, color = ArcadeTextMuted)
            Spacer(Modifier.height(2.dp))
            Text(
                "${heroClass.passiveIcon} ${heroClass.passiveTitle}: ${heroClass.passiveDescription}",
                style = MaterialTheme.typography.bodySmall,
                color = if (dimmed) ArcadeTextMuted else NeonYellow.copy(alpha = 0.85f),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "❤ ${heroClass.maxHp.toInt()}  ·  👟 ${heroClass.speed}  ·  ${heroClass.startWeapon.icon} ${heroClass.startWeapon.title}",
                style = MaterialTheme.typography.labelLarge,
                color = if (dimmed) ArcadeTextMuted else ArcadeText,
            )
            Spacer(Modifier.height(4.dp))
            footer()
        }
    }
}

// --- Spiel ---

@Composable
private fun GameView(viewModel: SurvivorsViewModel, onExit: () -> Unit) {
    val engine = viewModel.engine ?: return
    val theme = engine.state.stage.theme
    val textMeasurer = rememberTextMeasurer(cacheSize = 48)
    val stickRadius = with(LocalDensity.current) { 56.dp.toPx() }
    var stickOrigin by remember { mutableStateOf<Offset?>(null) }
    var stickKnob by remember { mutableStateOf(Offset.Zero) }
    val hud = viewModel.hud

    Box(Modifier.fillMaxSize()) {
        Canvas(
            Modifier
                .fillMaxSize()
                .clipToBounds() // Boden-Raster und Gegner am Rand nicht über die Top-Bar zeichnen
                .pointerInput(viewModel) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        stickOrigin = down.position
                        stickKnob = down.position
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            change.consume()
                            var delta = change.position - down.position
                            val length = hypot(delta.x, delta.y)
                            if (length > stickRadius) delta = delta * (stickRadius / length)
                            stickKnob = down.position + delta
                            viewModel.setInput(delta.x / stickRadius, delta.y / stickRadius)
                        }
                        stickOrigin = null
                        viewModel.setInput(0f, 0f)
                    }
                }
        ) {
            viewModel.frame // Neuzeichnen pro Frame
            drawSurvivors(engine, viewModel.effects, theme, textMeasurer, viewModel.damageFlash)
            // Joystick
            stickOrigin?.let { origin ->
                drawCircle(Color.White.copy(alpha = 0.12f), stickRadius, origin)
                drawCircle(Color.White.copy(alpha = 0.35f), stickRadius, origin, style = Stroke(width = 3f))
                drawCircle(Color.White.copy(alpha = 0.6f), stickRadius * 0.4f, stickKnob)
            }
        }

        Hud(hud, viewModel.mode, Modifier.align(Alignment.TopCenter))

        if (engine.state.time < 4f && stickOrigin == null && !viewModel.paused) {
            Text(
                "Irgendwo tippen und ziehen zum Laufen",
                style = MaterialTheme.typography.titleMedium,
                color = ArcadeText,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 48.dp),
            )
        }

        viewModel.banner?.let {
            Text(
                it,
                style = MaterialTheme.typography.headlineMedium.merge(
                    TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.8f), Offset(0f, 4f), 16f))
                ),
                color = NeonYellow,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center).padding(horizontal = 24.dp),
            )
        }

        val chest = viewModel.chest
        if (chest != null && !viewModel.finished) {
            ChestOverlay(engine.state, chest, onChoose = viewModel::chooseChest)
        } else if (viewModel.offers.isNotEmpty() && !viewModel.finished) {
            LevelUpOverlay(engine.state, viewModel.offers, viewModel::choose)
        }

        if (viewModel.paused) {
            PauseOverlay(engine, onResume = viewModel::resume, onRestart = viewModel::restart, onMenu = onExit)
        }

        GameOverOverlay(
            visible = viewModel.finished,
            score = hud.score,
            best = viewModel.best,
            isNewBest = viewModel.isNewBest,
            onPlayAgain = viewModel::restart,
            onMenu = onExit,
            title = if (viewModel.victory) "Sieg!" else "Gefallen",
            message = if (viewModel.victory) "Der ${engine.state.stage.finalBoss.title} ist besiegt." else
                "Überlebt: ${formatTime(hud.time)} · ${hud.kills} Gegner besiegt",
            coinsEarned = hud.coins + viewModel.recordBonus,
            bonusCoins = viewModel.recordBonus,
            leaderboardRank = viewModel.leaderboardRank,
            leaderboardName = "${engine.state.stage.title} · ${engine.state.heroClass.title}",
            extraReward = if (hud.runes > 0) "$RUNE_SYMBOL +${hud.runes} Runen" else null,
        )
    }
}

private fun formatTime(seconds: Float): String {
    val s = seconds.toInt()
    return "%d:%02d".format(s / 60, s % 60)
}

@Composable
private fun Hud(hud: SurvivorsHud, mode: SurvivorsMode, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        // Erfahrungsbalken
        Box(
            Modifier
                .fillMaxWidth()
                .height(16.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black.copy(alpha = 0.5f))
                .border(1.dp, ArcadeOutline, RoundedCornerShape(8.dp))
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth((hud.xp.toFloat() / hud.xpNeeded).coerceIn(0f, 1f))
                    .background(Color(0xFF60A5FA))
            )
            Text(
                "STUFE ${hud.level}",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        Spacer(Modifier.height(4.dp))
        HpBar(hud)
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "☠ ${hud.kills}" + (hud.souls?.let { "  💀 $it" } ?: ""),
                style = MaterialTheme.typography.titleMedium,
                color = ArcadeText,
                modifier = Modifier.weight(1f),
            )
            val timeText = if (mode == SurvivorsMode.TIMED) "${formatTime(hud.time)} / 15:00" else formatTime(hud.time)
            Text(timeText, style = MaterialTheme.typography.titleLarge, color = ArcadeText)
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                Text(RUNE_SYMBOL, style = MaterialTheme.typography.titleMedium, color = RuneViolet)
                Spacer(Modifier.width(3.dp))
                Text("${hud.runes}", style = MaterialTheme.typography.titleMedium, color = RuneViolet)
                Spacer(Modifier.width(10.dp))
                CoinIcon(16.dp)
                Spacer(Modifier.width(4.dp))
                Text("${hud.coins}", style = MaterialTheme.typography.titleMedium, color = CoinGold)
            }
        }
        Spacer(Modifier.height(6.dp))
        BuildRow(hud.weapons)
        if (hud.passives.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            BuildRow(hud.passives)
        }
        if (hud.items.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            BuildRow(hud.items, stacks = true)
        }
    }
}

/**
 * Kompakte Anzeige des Builds: Symbol + Stufenpunkte, entwickelte Waffen golden umrandet.
 * [stacks]: Items – statt Stufenpunkten die Stapelanzahl.
 */
@Composable
private fun BuildRow(slots: List<BuildSlot>, stacks: Boolean = false) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        slots.forEach { slot -> if (stacks) ItemChip(slot) else BuildChip(slot) }
    }
}

@Composable
private fun ItemChip(slot: BuildSlot) {
    val shape = RoundedCornerShape(8.dp)
    Column(
        Modifier
            .width(30.dp)
            .clip(shape)
            .background(Color.Black.copy(alpha = 0.55f))
            .border(1.dp, Color(0xFFFB923C).copy(alpha = 0.7f), shape)
            .padding(vertical = 1.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(slot.icon, fontSize = 13.sp, lineHeight = 15.sp)
        Text("×${slot.level}", fontSize = 9.sp, lineHeight = 10.sp, color = Color(0xFFFB923C))
    }
}

@Composable
private fun BuildChip(slot: BuildSlot) {
    val shape = RoundedCornerShape(8.dp)
    val gold = Color(0xFFFACC15)
    Column(
        Modifier
            .width(30.dp)
            .clip(shape)
            .background(Color.Black.copy(alpha = 0.55f))
            .border(if (slot.evolved) 1.5.dp else 1.dp, if (slot.evolved) gold else ArcadeOutline, shape)
            .padding(vertical = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(slot.icon, fontSize = 14.sp, lineHeight = 16.sp)
        Spacer(Modifier.height(2.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(1.5.dp)) {
            repeat(MAX_LEVEL) { i ->
                Box(
                    Modifier
                        .size(3.5.dp)
                        .clip(RoundedCornerShape(50))
                        .background(
                            when {
                                slot.evolved -> gold
                                i < slot.level -> Color(0xFF60A5FA)
                                else -> Color.White.copy(alpha = 0.18f)
                            }
                        )
                )
            }
        }
    }
}

@Composable
private fun LevelUpOverlay(state: SurvivorsState, offers: List<UpgradeOffer>, onChoose: (Int) -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("STUFE ${state.level}!", style = MaterialTheme.typography.headlineMedium, color = NeonYellow)
            Text("Wähle eine Verbesserung", style = MaterialTheme.typography.bodyMedium, color = ArcadeTextMuted)
            Spacer(Modifier.height(16.dp))
            offers.forEachIndexed { i, offer ->
                UpgradeCard(state, offer, onClick = { onChoose(i) })
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun UpgradeCard(state: SurvivorsState, offer: UpgradeOffer, onClick: () -> Unit) {
    val palette = LocalCosmetics.current.palette
    val shape = RoundedCornerShape(18.dp)
    val label = Upgrades.levelLabel(state, offer)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(ArcadeSurface)
            .border(1.dp, if (label == "NEU") NeonYellow else palette.accent, shape)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(ArcadeSurfaceHigh),
            contentAlignment = Alignment.Center,
        ) {
            Text(Upgrades.icon(offer), fontSize = 24.sp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(Upgrades.title(offer), style = MaterialTheme.typography.titleMedium, color = ArcadeText)
                Spacer(Modifier.width(8.dp))
                Text(label, style = MaterialTheme.typography.labelSmall, color = if (label == "NEU") NeonYellow else palette.accent2)
            }
            Text(Upgrades.description(state, offer), style = MaterialTheme.typography.bodySmall, color = ArcadeTextMuted)
            Upgrades.synergy(state, offer)?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = NeonYellow.copy(alpha = 0.8f))
            }
        }
    }
}

@Composable
private fun PauseOverlay(engine: SurvivorsEngine, onResume: () -> Unit, onRestart: () -> Unit, onMenu: () -> Unit) {
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
                .padding(vertical = 20.dp, horizontal = 16.dp)
                .fillMaxWidth()
                .clip(shape)
                .background(ArcadeSurface)
                .border(1.dp, ArcadeOutline, shape)
                .verticalScroll(rememberScrollState())
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Pausiert", style = MaterialTheme.typography.headlineMedium, color = ArcadeText)
            StatsPanel(engine)
            Spacer(Modifier.height(4.dp))
            PrimaryButton("Weiter", onResume)
            SecondaryButton("Neuer Lauf", onRestart)
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
