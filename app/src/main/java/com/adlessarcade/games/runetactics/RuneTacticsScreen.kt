package com.adlessarcade.games.runetactics

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.adlessarcade.ui.components.ArcadeIconButton
import com.adlessarcade.ui.components.ArcadeScaffold
import com.adlessarcade.ui.components.GameOverOverlay
import com.adlessarcade.ui.components.PrimaryButton
import com.adlessarcade.ui.components.SecondaryButton
import com.adlessarcade.ui.theme.ArcadeOutline
import com.adlessarcade.ui.theme.ArcadeSurface
import com.adlessarcade.ui.theme.ArcadeSurfaceHigh
import com.adlessarcade.ui.theme.ArcadeText
import com.adlessarcade.ui.theme.ArcadeTextMuted
import com.adlessarcade.ui.theme.NeonPink
import kotlinx.coroutines.isActive
import kotlin.math.roundToInt

@Composable
fun RuneTacticsScreen(
    onExit: () -> Unit,
    viewModel: RuneTacticsViewModel = viewModel(),
) {
    val haptics = LocalHapticFeedback.current
    val textMeasurer = rememberTextMeasurer(cacheSize = 64)
    var drag by remember { mutableStateOf<DragInfo?>(null) }
    var traitInfo by remember { mutableStateOf<Trait?>(null) }

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

    viewModel.revision // Lesen: Leisten neu aufbauen, wenn sich Gold, Shop oder Bank ändern
    val engine = viewModel.engine
    val planning = engine.phase == Phase.PLANNING

    ArcadeScaffold(
        title = "Rune Tactics",
        subtitle = "Endlos",
        onBack = onExit,
        actions = {
            ArcadeIconButton(onClick = { if (viewModel.paused) viewModel.resume() else viewModel.pause() }) {
                PauseGlyph()
            }
        },
    ) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                TopHud(engine, viewModel.best)
                TraitBar(engine.activeTraits(), onTrait = { traitInfo = if (traitInfo == it) null else it })

                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    Canvas(
                        Modifier
                            .fillMaxSize()
                            .pointerInput(viewModel) {
                                awaitEachGesture {
                                    val down = awaitFirstDown()
                                    val layout = TacticsLayout(size.width.toFloat(), size.height.toFloat())
                                    val current = viewModel.engine
                                    val from = layout.slotAt(down.position)
                                    val unit = from?.let { current.unitAt(it) }
                                    var pos = down.position
                                    var dragging = false
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                        if (!change.pressed) break
                                        pos = change.position
                                        val canDrag = unit != null && current.phase == Phase.PLANNING && !viewModel.paused
                                        if (!dragging && canDrag && (pos - down.position).getDistance() > viewConfiguration.touchSlop) {
                                            dragging = true
                                            viewModel.inspect(null)
                                            traitInfo = null
                                        }
                                        if (dragging) {
                                            drag = DragInfo(from!!, unit!!, pos)
                                            change.consume()
                                        }
                                    }
                                    if (dragging) {
                                        val target = layout.slotAt(pos)
                                        when {
                                            pos.y > size.height -> viewModel.sell(from!!)
                                            target != null -> viewModel.move(from!!, target)
                                        }
                                        drag = null
                                    } else {
                                        traitInfo = null
                                        viewModel.inspect(hitTest(layout, current, down.position))
                                    }
                                }
                            }
                    ) {
                        viewModel.frame // Lesen registriert das Neuzeichnen pro Frame
                        drawTactics(
                            TacticsLayout(size.width, size.height),
                            viewModel.engine, viewModel.effects, drag, textMeasurer,
                        )
                    }

                    val info = viewModel.inspected
                    if (info != null) UnitCard(info, Modifier.align(Alignment.TopCenter).padding(8.dp))
                    traitInfo?.let { TraitCard(it, engine.activeTraits()[it] ?: 0, Modifier.align(Alignment.TopCenter).padding(8.dp)) }
                    Banner(viewModel.banner, Modifier.align(Alignment.Center))
                }

                Box {
                    BottomPanel(viewModel, engine, planning)
                    val dragged = drag
                    if (dragged != null) SellZone(Economy.sellValue(dragged.unit), Modifier.matchParentSize())
                }
            }

            if (viewModel.paused) {
                PauseOverlay(onResume = viewModel::resume, onRestart = viewModel::restart, onMenu = onExit)
            }

            GameOverOverlay(
                visible = viewModel.gameOver,
                score = engine.round,
                best = viewModel.best,
                isNewBest = viewModel.isNewBest,
                onPlayAgain = viewModel::restart,
                onMenu = onExit,
                message = "Deine Armee ist in Runde ${engine.round} gefallen",
                coinsEarned = engine.coinsEarned + viewModel.recordBonus,
                bonusCoins = viewModel.recordBonus,
                leaderboardRank = viewModel.leaderboardRank,
            )
        }
    }
}

/** Einheit unter dem Finger: eigene (Brett/Bank), gegnerische oder im Kampf. */
private fun hitTest(layout: TacticsLayout, engine: RuneTacticsEngine, p: Offset): Inspected? {
    val fight = engine.combat
    if (engine.phase == Phase.COMBAT && fight != null) {
        val hit = fight.fighters.filter { it.alive }.minByOrNull {
            (layout.center(it.visualCol, it.visualRow) - p).getDistance()
        }
        if (hit != null && (layout.center(hit.visualCol, hit.visualRow) - p).getDistance() < layout.cell * 0.6f) {
            return Inspected(hit.def, hit.star, hit.side == 1)
        }
    }
    layout.benchAt(p)?.let { i -> engine.bench[i]?.let { return Inspected(it.def, it.star, false) } }
    if (engine.phase == Phase.COMBAT) return null
    val cell = layout.cellAt(p) ?: return null
    engine.board[cell]?.let { return Inspected(it.def, it.star, false) }
    engine.enemyArmy.firstOrNull { it.col == cell.col && it.row == cell.row }?.let { return Inspected(it.def, it.star, true) }
    return null
}

@Composable
private fun TopHud(engine: RuneTacticsEngine, best: Int) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Favorite, contentDescription = null, tint = NeonPink, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(4.dp))
        Text("${engine.hp}", style = MaterialTheme.typography.titleMedium, color = ArcadeText)
        Spacer(Modifier.width(14.dp))
        Text("● ${engine.gold}", style = MaterialTheme.typography.titleMedium, color = StarGold)
        Spacer(Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("RUNDE ${engine.round}", style = MaterialTheme.typography.titleMedium, color = ArcadeText)
            val scale = engine.enemyScale
            Text(
                if (scale > 1.001f) "Aufstieg +${((scale - 1f) * 100).roundToInt()} %" else "Rekord $best",
                style = MaterialTheme.typography.labelSmall,
                color = if (scale > 1.001f) LossRed else ArcadeTextMuted,
            )
        }
        Spacer(Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                "Lv ${engine.level} · ${engine.board.size}/${engine.boardCap}",
                style = MaterialTheme.typography.titleSmall,
                color = ArcadeText,
            )
            val need = Economy.xpToNext(engine.level)
            Text(
                if (need > 0) "XP ${engine.xp}/$need" else "Max. Level",
                style = MaterialTheme.typography.labelSmall,
                color = ArcadeTextMuted,
            )
        }
    }
}

@Composable
private fun TraitBar(traits: Map<Trait, Int>, onTrait: (Trait) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(30.dp)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (traits.isEmpty()) {
            Text(
                "Stelle Einheiten aufs Brett, um Synergien zu aktivieren",
                style = MaterialTheme.typography.labelSmall,
                color = ArcadeTextMuted,
            )
        }
        traits.entries
            .sortedWith(compareByDescending<Map.Entry<Trait, Int>> { it.key.level(it.value) }.thenByDescending { it.value })
            .forEach { (trait, count) ->
                val active = trait.level(count) > 0
                val color = if (trait.kind == TraitKind.ORIGIN) originColor(trait) else StarGold
                val shape = RoundedCornerShape(8.dp)
                Row(
                    Modifier
                        .clip(shape)
                        .background(if (active) color.copy(alpha = 0.2f) else ArcadeSurface)
                        .border(1.dp, if (active) color else ArcadeOutline, shape)
                        .clickable { onTrait(trait) }
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(trait.title, style = MaterialTheme.typography.labelMedium, color = if (active) color else ArcadeTextMuted)
                    Spacer(Modifier.width(4.dp))
                    val next = trait.nextTier(count) ?: trait.tiers.last()
                    Text("$count/$next", style = MaterialTheme.typography.labelSmall, color = ArcadeTextMuted)
                }
            }
    }
}

@Composable
private fun BottomPanel(viewModel: RuneTacticsViewModel, engine: RuneTacticsEngine, planning: Boolean) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            engine.shop.forEachIndexed { index, id ->
                ShopCard(
                    def = id?.let { RuneTacticsUnits.byId(it) },
                    affordable = id != null && RuneTacticsUnits.byId(id).cost <= engine.gold,
                    combines = id != null && engine.wouldCombine(id),
                    enabled = planning,
                    onBuy = { viewModel.buy(index) },
                    onInfo = { def -> viewModel.inspect(Inspected(def, 1, false)) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (planning) {
                SecondaryButton(
                    "↻ Würfeln · ${Economy.REROLL_COST}", viewModel::reroll,
                    Modifier.weight(1f).alpha(if (engine.gold >= Economy.REROLL_COST) 1f else 0.45f),
                )
                SecondaryButton(
                    "▲ XP · ${Economy.XP_COST}", viewModel::buyXp,
                    Modifier.weight(1f).alpha(
                        if (engine.gold >= Economy.XP_COST && engine.level < Economy.MAX_LEVEL) 1f else 0.45f,
                    ),
                )
                PrimaryButton("⚔ Kampf!", viewModel::fight, Modifier.weight(1.2f))
            } else {
                Box(
                    Modifier
                        .weight(2f)
                        .height(54.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Kampf läuft …", style = MaterialTheme.typography.titleMedium, color = ArcadeTextMuted)
                }
                SecondaryButton("Tempo ${viewModel.speed}×", viewModel::toggleSpeed, Modifier.weight(1.2f))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ShopCard(
    def: UnitDef?,
    affordable: Boolean,
    combines: Boolean,
    enabled: Boolean,
    onBuy: () -> Unit,
    onInfo: (UnitDef) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(10.dp)
    if (def == null) {
        Box(
            modifier
                .height(84.dp)
                .clip(shape)
                .background(ArcadeSurface.copy(alpha = 0.5f))
                .border(1.dp, ArcadeOutline.copy(alpha = 0.5f), shape)
        )
        return
    }
    val cost = costColor(def.cost)
    Column(
        modifier
            .height(84.dp)
            .alpha(if (enabled && affordable) 1f else 0.45f)
            .clip(shape)
            .background(ArcadeSurfaceHigh)
            .border(if (combines) 2.dp else 1.dp, if (combines) StarGold else cost.copy(alpha = 0.7f), shape)
            .combinedClickable(onClick = onBuy, onLongClick = { onInfo(def) })
            .padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Canvas(Modifier.size(30.dp)) {
            drawUnit(Offset(size.width / 2f, size.height / 2f - 2f), size.width * 1.1f, def, 1, enemy = false)
        }
        Text(
            def.name,
            style = MaterialTheme.typography.labelSmall,
            color = ArcadeText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Text(
            "${def.origin.title.take(3)}·${def.role.title.take(3)}",
            style = MaterialTheme.typography.labelSmall,
            color = originColor(def.origin),
            maxLines = 1,
        )
        Text("● ${def.cost}", style = MaterialTheme.typography.labelSmall, color = StarGold)
    }
}

@Composable
private fun SellZone(value: Int, modifier: Modifier = Modifier) {
    Box(
        modifier
            .padding(6.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(LossRed.copy(alpha = 0.25f))
            .border(2.dp, LossRed, RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text("Hier ablegen zum Verkaufen · +$value Gold", style = MaterialTheme.typography.titleMedium, color = ArcadeText)
    }
}

@Composable
private fun UnitCard(info: Inspected, modifier: Modifier = Modifier) {
    val def = info.def
    val shape = RoundedCornerShape(16.dp)
    val star = Stars.stat(info.star)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(ArcadeSurface.copy(alpha = 0.96f))
            .border(1.dp, costColor(def.cost), shape)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(def.name, style = MaterialTheme.typography.titleMedium, color = ArcadeText)
            Spacer(Modifier.width(6.dp))
            Text("★".repeat(info.star), style = MaterialTheme.typography.titleMedium, color = StarGold)
            Spacer(Modifier.weight(1f))
            if (info.enemy) Text("Gegner", style = MaterialTheme.typography.labelMedium, color = LossRed)
            else Text("● ${def.cost}", style = MaterialTheme.typography.labelMedium, color = StarGold)
        }
        Text(
            "${def.origin.title} · ${def.role.title}",
            style = MaterialTheme.typography.labelMedium,
            color = originColor(def.origin),
        )
        Text(
            "Leben ${(def.hp * star).roundToInt()} · Angriff ${(def.ad * star).roundToInt()} · " +
                "Tempo ${"%.2f".format(def.attackSpeed)} · Reichweite ${def.range}",
            style = MaterialTheme.typography.bodySmall,
            color = ArcadeTextMuted,
        )
        Text(
            "Rüstung ${def.armor.roundToInt()} · Magieresistenz ${def.mr.roundToInt()} · Mana ${def.startMana}/${def.maxMana}",
            style = MaterialTheme.typography.bodySmall,
            color = ArcadeTextMuted,
        )
        Text(def.ability.name, style = MaterialTheme.typography.labelLarge, color = MagicViolet)
        Text(def.ability.description, style = MaterialTheme.typography.bodySmall, color = ArcadeText)
    }
}

@Composable
private fun TraitCard(trait: Trait, count: Int, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(16.dp)
    val color = if (trait.kind == TraitKind.ORIGIN) originColor(trait) else StarGold
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(ArcadeSurface.copy(alpha = 0.96f))
            .border(1.dp, color, shape)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            "${trait.title} · ${trait.tiers.joinToString(" / ")}",
            style = MaterialTheme.typography.titleMedium,
            color = color,
        )
        Text(trait.description, style = MaterialTheme.typography.bodySmall, color = ArcadeText)
        Text(
            RuneTacticsUnits.all.filter { trait in it.traits }.joinToString(", ") { it.name },
            style = MaterialTheme.typography.bodySmall,
            color = ArcadeTextMuted,
        )
        Text("Aktiv: $count verschiedene Einheiten", style = MaterialTheme.typography.labelSmall, color = ArcadeTextMuted)
    }
}

@Composable
private fun Banner(banner: TacticsBanner?, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = banner != null,
        enter = fadeIn() + scaleIn(initialScale = 0.7f),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        // Letzten Inhalt merken, damit er beim Ausblenden stehen bleibt.
        var shown by remember { mutableStateOf(TacticsBanner("", null, Color.White)) }
        if (banner != null) shown = banner
        val shadow = TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.8f), Offset(0f, 4f), 16f))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(shown.title, style = MaterialTheme.typography.displaySmall.merge(shadow), color = shown.color)
            shown.subtitle?.let {
                Text(it, style = MaterialTheme.typography.titleMedium.merge(shadow), color = ArcadeText)
            }
        }
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
            Text(
                "Tippe auf Einheiten für Infos, halte Shop-Karten gedrückt.\nZiehe Einheiten in den Shop, um sie zu verkaufen.",
                style = MaterialTheme.typography.bodySmall,
                color = ArcadeTextMuted,
                textAlign = TextAlign.Center,
            )
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
