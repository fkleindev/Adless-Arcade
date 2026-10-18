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
import com.adlessarcade.ui.theme.LocalCosmetics
import kotlinx.coroutines.isActive
import kotlin.math.roundToInt

@Composable
fun RuneTacticsScreen(
    onExit: () -> Unit,
    viewModel: RuneTacticsViewModel = viewModel(),
) {
    val haptics = LocalHapticFeedback.current
    val textMeasurer = rememberTextMeasurer(cacheSize = 64)
    val arena = LocalCosmetics.current.arena
    var drag by remember { mutableStateOf<DragInfo?>(null) }
    var traitInfo by remember { mutableStateOf<Trait?>(null) }
    var showRunes by remember { mutableStateOf(false) }

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

    // Die Engine bleibt dieselbe Instanz und wird intern verändert. Die Revision löst das Neuzeichnen aus; die Leisten
    // bekommen eine Momentaufnahme der Werte (HudState), damit Compose Änderungen am Inhalt erkennt und sie nicht überspringt.
    viewModel.revision
    val engine = viewModel.engine
    val planning = engine.phase == Phase.PLANNING
    val hud = engine.hudState()

    ArcadeScaffold(
        title = "Rune Tactics",
        subtitle = if (viewModel.isDaily) "Tägliche Challenge" else "Endlos",
        onBack = onExit,
        actions = {
            ArcadeIconButton(onClick = { if (viewModel.paused) viewModel.resume() else viewModel.pause() }) {
                PauseGlyph()
            }
        },
    ) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                TopHud(hud, viewModel.best)
                TraitBar(
                    engine.activeTraits(),
                    runes = engine.augments.size,
                    curse = engine.curse.takeIf { planning || engine.phase == Phase.COMBAT },
                    onTrait = { traitInfo = if (traitInfo == it) null else it; showRunes = false },
                    onRunes = { showRunes = !showRunes; traitInfo = null },
                )

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
                                            showRunes = false
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
                                        showRunes = false
                                        val slot = layout.slotAt(down.position)
                                        val equipped = if (slot != null) viewModel.equipSelected(slot) else {
                                            viewModel.selectItem(null)
                                            false
                                        }
                                        if (!equipped) viewModel.inspect(hitTest(layout, current, down.position))
                                    }
                                }
                            }
                    ) {
                        viewModel.frame // Lesen registriert das Neuzeichnen pro Frame
                        drawTactics(
                            TacticsLayout(size.width, size.height),
                            viewModel.engine, viewModel.effects, drag, textMeasurer, arena, viewModel.animTime,
                        )
                    }

                    val info = viewModel.inspected
                    if (info != null) UnitCard(info, Modifier.align(Alignment.TopCenter).padding(8.dp))
                    viewModel.selectedItem?.let { engine.items.getOrNull(it) }?.let { id ->
                        ItemCard(RuneTacticsItems.byId(id), Modifier.align(Alignment.TopCenter).padding(8.dp))
                    }
                    traitInfo?.let { TraitCard(it, engine.activeTraits()[it] ?: 0, Modifier.align(Alignment.TopCenter).padding(8.dp)) }
                    if (showRunes) RunesCard(engine.augments.toList(), engine.curse, Modifier.align(Alignment.TopCenter).padding(8.dp))
                    Banner(viewModel.banner, Modifier.align(Alignment.Center))
                }

                Box {
                    BottomPanel(viewModel, hud, planning)
                    val dragged = drag
                    if (dragged != null) SellZone(Economy.sellValue(dragged.unit), Modifier.matchParentSize())
                }
            }

            if (planning && engine.pendingRewards.isNotEmpty() && !viewModel.paused && !viewModel.gameOver) {
                RewardOverlay(engine.pendingRewards, onChoose = viewModel::chooseReward)
            } else if (planning && engine.pendingAugments.isNotEmpty() && !viewModel.paused && !viewModel.gameOver) {
                AugmentOverlay(engine.pendingAugments, onChoose = viewModel::chooseAugment)
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
            val scale = if (hit.side == 1) engine.enemyScale * (engine.enemyArmy.getOrNull(hit.id - fight.playerUids.size)?.scale ?: 1f) else 1f
            return Inspected(hit.def, hit.star, hit.side == 1, hit.items.map { it.id }, scale)
        }
    }
    layout.benchAt(p)?.let { i -> engine.bench[i]?.let { return Inspected(it.def, it.star, false, it.items) } }
    if (engine.phase == Phase.COMBAT) return null
    val cell = layout.cellAt(p) ?: return null
    engine.board[cell]?.let { return Inspected(it.def, it.star, false, it.items) }
    engine.enemyArmy.firstOrNull { it.col == cell.col && it.row == cell.row }?.let { return Inspected(it.def, it.star, true, it.items, engine.enemyScale * it.scale) }
    return null
}

@Composable
private fun TopHud(hud: HudState, best: Int) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Favorite, contentDescription = null, tint = NeonPink, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(4.dp))
        Text("${hud.hp}", style = MaterialTheme.typography.titleMedium, color = ArcadeText)
        Spacer(Modifier.width(14.dp))
        Text("● ${hud.gold}", style = MaterialTheme.typography.titleMedium, color = StarGold)
        Spacer(Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("RUNDE ${hud.round}", style = MaterialTheme.typography.titleMedium, color = ArcadeText)
            val scale = hud.enemyScale
            val type = hud.roundType
            Text(
                when {
                    type != RoundType.NORMAL -> type.title
                    scale > 1.001f -> "Aufstieg +${((scale - 1f) * 100).roundToInt()} %"
                    else -> "Rekord $best"
                },
                style = MaterialTheme.typography.labelSmall,
                color = when {
                    type == RoundType.BOSS || scale > 1.001f -> LossRed
                    type == RoundType.CREEPS -> originColor(Trait.BEAST)
                    else -> ArcadeTextMuted
                },
            )
        }
        Spacer(Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                "Lv ${hud.level} · ${hud.boardCount}/${hud.boardCap}",
                style = MaterialTheme.typography.titleSmall,
                color = ArcadeText,
            )
            val need = Economy.xpToNext(hud.level)
            Text(
                if (need > 0) "XP ${hud.xp}/$need" else "Max. Level",
                style = MaterialTheme.typography.labelSmall,
                color = ArcadeTextMuted,
            )
        }
    }
}

@Composable
private fun TraitBar(
    traits: Map<Trait, Int>,
    runes: Int,
    curse: Curse?,
    onTrait: (Trait) -> Unit,
    onRunes: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(30.dp)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (runes > 0 || curse != null) {
            val color = if (curse != null) MagicViolet else StarGold
            val shape = RoundedCornerShape(8.dp)
            Text(
                listOfNotNull(if (runes > 0) "ᚱ $runes Segen" else null, curse?.let { "☠ ${it.title}" }).joinToString(" · "),
                style = MaterialTheme.typography.labelMedium,
                color = color,
                modifier = Modifier
                    .clip(shape)
                    .background(color.copy(alpha = 0.18f))
                    .border(1.dp, color, shape)
                    .clickable(onClick = onRunes)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
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
private fun BottomPanel(viewModel: RuneTacticsViewModel, hud: HudState, planning: Boolean) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ItemBar(hud.items, viewModel.selectedItem, enabled = planning, onSelect = viewModel::selectItem)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            hud.shop.forEachIndexed { index, id ->
                ShopCard(
                    def = id?.let { RuneTacticsUnits.byId(it) },
                    affordable = id != null && RuneTacticsUnits.byId(id).cost <= hud.gold,
                    combines = id != null && id in hud.combinable,
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
                    if (hud.freeRerolls > 0) "↻ Gratis" else "↻ Würfeln · ${Economy.REROLL_COST}", viewModel::reroll,
                    Modifier.weight(1f).alpha(if (hud.gold >= Economy.REROLL_COST || hud.freeRerolls > 0) 1f else 0.45f),
                )
                SecondaryButton(
                    "▲ XP · ${Economy.XP_COST}", viewModel::buyXp,
                    Modifier.weight(1f).alpha(
                        if (hud.gold >= Economy.XP_COST && hud.level < Economy.MAX_LEVEL) 1f else 0.45f,
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
                .height(90.dp)
                .clip(shape)
                .background(ArcadeSurface.copy(alpha = 0.5f))
                .border(1.dp, ArcadeOutline.copy(alpha = 0.5f), shape)
        )
        return
    }
    val cost = costColor(def.cost)
    Column(
        modifier
            .height(90.dp)
            .alpha(if (enabled && affordable) 1f else 0.45f)
            .clip(shape)
            .background(ArcadeSurfaceHigh)
            .border(if (combines) 2.dp else 1.dp, if (combines) StarGold else cost.copy(alpha = 0.7f), shape)
            .combinedClickable(onClick = onBuy, onLongClick = { onInfo(def) })
            .padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Canvas(Modifier.size(36.dp)) {
            drawFigure(def, Offset(size.width / 2f, size.height * 0.45f), size.width, time = 0f)
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
    val star = Stars.stat(info.star) * info.scale
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
            def.traits.joinToString(" · ") { it.title },
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
        if (def.maxMana > 0) {
            Text(def.ability.description, style = MaterialTheme.typography.bodySmall, color = ArcadeText)
        }
        info.items.map(RuneTacticsItems::byId).forEach { item ->
            Text(
                "${item.name}: ${item.description}",
                style = MaterialTheme.typography.bodySmall,
                color = StarGold,
            )
        }
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
                "Tippe auf Einheiten für Infos, halte Shop-Karten gedrückt.\nZiehe Einheiten in den Shop, um sie zu verkaufen.\nItems: antippen, dann eine eigene Einheit antippen.",
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

@Composable
private fun ItemBar(items: List<String>, selected: Int?, enabled: Boolean, onSelect: (Int) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(36.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (items.isEmpty()) {
            Text(
                "Items gibt es in Kreaturen- und Bossrunden",
                style = MaterialTheme.typography.labelSmall,
                color = ArcadeTextMuted,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
        items.forEachIndexed { index, id ->
            val item = RuneTacticsItems.byId(id)
            val isSelected = index == selected
            val shape = RoundedCornerShape(8.dp)
            Box(
                Modifier
                    .size(34.dp)
                    .alpha(if (enabled) 1f else 0.45f)
                    .clip(shape)
                    .background(if (isSelected) StarGold.copy(alpha = 0.3f) else Color.Transparent)
                    .border(if (isSelected) 2.dp else 0.dp, if (isSelected) StarGold else Color.Transparent, shape)
                    .clickable(enabled = enabled) { onSelect(index) },
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.size(28.dp)) {
                    drawItemIcon(Offset(size.width / 2f, size.height / 2f), size.width, item)
                }
            }
        }
    }
}

@Composable
private fun ItemCard(item: ItemDef, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(ArcadeSurface.copy(alpha = 0.96f))
            .border(1.dp, StarGold, shape)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(item.name, style = MaterialTheme.typography.titleMedium, color = StarGold)
        Text(item.statLine, style = MaterialTheme.typography.bodySmall, color = ArcadeTextMuted)
        if (!item.isComponent) Text(item.description, style = MaterialTheme.typography.bodySmall, color = ArcadeText)
        if (item.isComponent) {
            Text("Kombiniert mit:", style = MaterialTheme.typography.labelMedium, color = ArcadeText)
            RuneTacticsItems.components.forEach { other ->
                val result = RuneTacticsItems.combine(item.id, other.id) ?: return@forEach
                Text(
                    "+ ${other.name} → ${result.name}: ${result.description}",
                    style = MaterialTheme.typography.bodySmall,
                    color = ArcadeTextMuted,
                )
            }
        }
        Text(
            "Tippe auf eine eigene Einheit zum Ausrüsten (max. ${RuneTacticsItems.MAX_PER_UNIT}).",
            style = MaterialTheme.typography.labelSmall,
            color = ArcadeCyan,
        )
    }
}

/** Auswahl der Boss-Belohnung; blockiert das Spiel, bis gewählt wurde. */
@Composable
private fun RewardOverlay(rewards: List<Reward>, onChoose: (Int) -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("BOSS BESIEGT!", style = MaterialTheme.typography.headlineMedium, color = StarGold)
            Text("Wähle eine Belohnung", style = MaterialTheme.typography.bodyMedium, color = ArcadeTextMuted)
            Spacer(Modifier.height(4.dp))
            rewards.forEachIndexed { index, reward ->
                val shape = RoundedCornerShape(16.dp)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(shape)
                        .background(ArcadeSurfaceHigh)
                        .border(1.dp, StarGold.copy(alpha = 0.7f), shape)
                        .clickable { onChoose(index) }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Canvas(Modifier.size(40.dp)) {
                        val c = Offset(size.width / 2f, size.height / 2f)
                        when (reward.kind) {
                            RewardKind.ITEM -> drawItemIcon(c, size.width * 0.9f, RuneTacticsItems.byId(reward.id!!))
                            RewardKind.COMPONENTS -> {
                                drawItemIcon(Offset(c.x - size.width * 0.12f, c.y - size.width * 0.12f), size.width * 0.6f, RuneTacticsItems.components[0])
                                drawItemIcon(Offset(c.x + size.width * 0.12f, c.y + size.width * 0.12f), size.width * 0.6f, RuneTacticsItems.components[3])
                            }
                            RewardKind.UNIT -> drawUnit(c, size.width * 1.1f, RuneTacticsUnits.byId(reward.id!!), reward.star, enemy = false)
                            RewardKind.GOLD -> {
                                drawCircle(StarGold, size.width * 0.32f, c)
                                drawCircle(Color(0xFFB45309), size.width * 0.32f, c, style = androidx.compose.ui.graphics.drawscope.Stroke(size.width * 0.06f))
                            }
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(reward.title, style = MaterialTheme.typography.titleMedium, color = ArcadeText)
                        Text(reward.description, style = MaterialTheme.typography.bodySmall, color = ArcadeTextMuted)
                    }
                }
            }
        }
    }
}

@Composable
private fun RunesCard(augments: List<Augment>, curse: Curse?, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(ArcadeSurface.copy(alpha = 0.96f))
            .border(1.dp, MagicViolet, shape)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        if (curse != null) {
            Text("☠ Fluch dieser Runde: ${curse.title}", style = MaterialTheme.typography.titleSmall, color = MagicViolet)
            Text(curse.description, style = MaterialTheme.typography.bodySmall, color = ArcadeText)
            if (augments.isNotEmpty()) Spacer(Modifier.height(6.dp))
        }
        if (augments.isNotEmpty()) {
            Text("ᚱ Runen-Segen", style = MaterialTheme.typography.titleSmall, color = StarGold)
            augments.groupingBy { it }.eachCount().forEach { (augment, count) ->
                Text(
                    "${augment.title}${if (count > 1) " ×$count" else ""}: ${augment.description}",
                    style = MaterialTheme.typography.bodySmall,
                    color = ArcadeTextMuted,
                )
            }
        }
    }
}

/** Auswahl eines Runen-Segens; blockiert das Spiel, bis gewählt wurde. */
@Composable
private fun AugmentOverlay(options: List<Augment>, onChoose: (Int) -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("ᚱ RUNEN-SEGEN", style = MaterialTheme.typography.headlineMedium, color = MagicViolet)
            Text("Wähle einen Segen für den restlichen Lauf", style = MaterialTheme.typography.bodyMedium, color = ArcadeTextMuted)
            Spacer(Modifier.height(4.dp))
            options.forEachIndexed { index, augment ->
                val shape = RoundedCornerShape(16.dp)
                val color = augment.emblem?.let { if (it.kind == TraitKind.ORIGIN) originColor(it) else StarGold } ?: MagicViolet
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(shape)
                        .background(ArcadeSurfaceHigh)
                        .border(1.dp, color.copy(alpha = 0.8f), shape)
                        .clickable { onChoose(index) }
                        .padding(14.dp),
                ) {
                    Text(augment.title, style = MaterialTheme.typography.titleMedium, color = color)
                    Text(augment.description, style = MaterialTheme.typography.bodySmall, color = ArcadeText)
                }
            }
        }
    }
}

/**
 * Unveränderliche Momentaufnahme der Werte für Kopf- und Fußleiste. Die Engine selbst ist veränderlich und bleibt
 * dieselbe Instanz; Compose würde Leisten mit ihr als Parameter überspringen. Als Data Class vergleicht Compose den Inhalt.
 */
private data class HudState(
    val hp: Int,
    val gold: Int,
    val round: Int,
    val roundType: RoundType,
    val enemyScale: Float,
    val level: Int,
    val xp: Int,
    val boardCount: Int,
    val boardCap: Int,
    val shop: List<String?>,
    val combinable: Set<String>,
    val items: List<String>,
    val freeRerolls: Int,
)

private fun RuneTacticsEngine.hudState() = HudState(
    hp = hp,
    gold = gold,
    round = round,
    roundType = roundType,
    enemyScale = enemyScale,
    level = level,
    xp = xp,
    boardCount = board.size,
    boardCap = boardCap,
    shop = shop.toList(),
    combinable = shop.filterNotNull().filter(::wouldCombine).toSet(),
    items = items.toList(),
    freeRerolls = freeRerolls,
)
