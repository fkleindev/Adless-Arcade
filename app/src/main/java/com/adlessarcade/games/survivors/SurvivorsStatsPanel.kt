package com.adlessarcade.games.survivors

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adlessarcade.ui.components.CoinGold
import com.adlessarcade.ui.theme.ArcadeOutline
import com.adlessarcade.ui.theme.ArcadeSurfaceHigh
import com.adlessarcade.ui.theme.ArcadeText
import com.adlessarcade.ui.theme.ArcadeTextMuted
import com.adlessarcade.ui.theme.NeonYellow
import java.util.Locale

private val HpRed = Color(0xFFEF4444)
private val ShieldGold = Color(0xFFFDE68A)
private val Gold = Color(0xFFFACC15)
private const val LOW_HP = 0.3f

private fun num(v: Float, decimals: Int = 1): String = "%.${decimals}f".format(Locale.GERMANY, v).removeSuffix(",0")
private fun times(v: Float) = "×${"%.2f".format(Locale.GERMANY, v)}"
private fun pct(v: Float) = "${ItemKind.percent(v)} %"
private fun big(v: Float): String = "%,d".format(Locale.GERMANY, v.toLong())
private fun clock(seconds: Float): String {
    val s = seconds.toInt()
    return "%d:%02d".format(s / 60, s % 60)
}

/** Lebensbalken im HUD mit Zahlen; pulsiert rot bei wenig Leben. */
@Composable
fun HpBar(hud: SurvivorsHud, modifier: Modifier = Modifier) {
    val fraction = (hud.hp / hud.maxHp).coerceIn(0f, 1f)
    val low = fraction < LOW_HP
    val pulse by rememberInfiniteTransition(label = "hp").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(450), RepeatMode.Reverse),
        label = "hpPulse",
    )
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier
            .fillMaxWidth()
            .height(16.dp)
            .clip(shape)
            .background(if (low) HpRed.copy(alpha = 0.15f + 0.25f * pulse) else Color.Black.copy(alpha = 0.5f))
            .border(if (low) 1.5.dp else 1.dp, if (low) HpRed.copy(alpha = 0.5f + 0.5f * pulse) else ArcadeOutline, shape)
    ) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(fraction).background(HpRed))
        if (hud.shield > 0f) {
            // Gottesschild: goldenes Segment am oberen Rand
            Box(Modifier.fillMaxHeight(0.4f).fillMaxWidth((hud.shield / hud.maxHp).coerceIn(0f, 1f)).background(ShieldGold))
        }
        val regen = if (hud.regen > 0f) "  ·  +${num(hud.regen)}/s" else ""
        val shield = if (hud.shield >= 1f) "  ·  ✚ ${hud.shield.toInt()}" else ""
        Text(
            "❤ ${hud.hp.toInt().coerceAtLeast(if (hud.hp > 0f) 1 else 0)} / ${hud.maxHp.toInt()}$shield$regen",
            style = BarLabelStyle(),
            color = Color.White,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

/** Beschriftung in HUD-Balken: fett mit Schatten, damit sie auf jeder Füllung lesbar ist. */
@Composable
fun BarLabelStyle(): TextStyle = MaterialTheme.typography.labelSmall.merge(
    TextStyle(fontWeight = FontWeight.Bold, shadow = Shadow(Color.Black, blurRadius = 4f))
)

/** Vollständige Werte des Laufs für das Pause-Menü: Held, Angriff, Waffen mit Schadensanteil, Build und Lauf. */
@Composable
fun StatsPanel(engine: SurvivorsEngine) {
    val s = engine.state
    val st = engine.stats
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (s.goals.isNotEmpty()) {
            StatSection("Lauf-Ziele") {
                s.goals.forEach { GoalLine(it, s) }
            }
        }

        Column {
            Text("${s.heroClass.passiveIcon} ${s.heroClass.passiveTitle}", style = MaterialTheme.typography.titleSmall, color = NeonYellow)
            Text(s.heroClass.passiveDescription, style = MaterialTheme.typography.bodySmall, color = ArcadeTextMuted)
        }

        StatSection("Held") {
            StatRow("Leben", "${s.hp.toInt()} / ${st.maxHp.toInt()}")
            if (st.regen > 0f) StatRow("Regeneration", "+${num(st.regen)} / s")
            StatRow("Rüstung", "−${st.armor} pro Treffer")
            if (st.dodge > 0f) StatRow("Ausweichen", pct(st.dodge))
            if (st.lifesteal > 0f) StatRow("Lebensraub", pct(st.lifesteal))
            StatRow("Lauftempo", num(st.speed, 2))
            StatRow("Sammelradius", num(st.pickupRadius, 2))
            if (s.heroClass == HeroClass.NECROMANCER) StatRow("Seelen", "${s.souls} / ${HeroStats.NECRO_MAX_SOULS}")
        }

        StatSection("Angriff") {
            StatRow("Schaden", times(st.damageMultiplier))
            if (st.classDamageBonus > 0f) StatRow("  davon Klassen-Passive", "+${pct(st.classDamageBonus)}")
            if (st.berserk > 0f) StatRow("  davon Berserkerblut", "+${pct(st.berserk * (1f - s.hp / st.maxHp).coerceIn(0f, 1f))}")
            StatRow("Abklingzeit", times(st.cooldownMultiplier))
            StatRow("Fläche & Reichweite", times(st.areaMultiplier))
            if (st.extraProjectiles > 0) StatRow("Zusätzliche Projektile", "+${st.extraProjectiles}")
            if (st.critChance > 0f) {
                StatRow("Krit-Chance", pct(st.critChance.coerceAtMost(1f)))
                StatRow("Krit-Schaden", times(st.critMultiplier))
            }
            if (st.superCritChance > 0f) StatRow("Superkrit-Chance", pct(st.superCritChance))
            if (st.poison > 0f) StatRow("Gift", "${pct(st.poison)} über ${num(st.poisonTime)} s")
            if (st.sparkChance > 0f) StatRow("Funken-Chance", pct(st.sparkChance))
            if (st.emberChance > 0f) StatRow("Glut-Chance", pct(st.emberChance))
            if (st.emberRadiusMultiplier > 1f) StatRow("Glut-Radius", times(st.emberRadiusMultiplier))
            if (st.freezeChance > 0f) StatRow("Frost-Chance", pct(st.freezeChance))
            if (st.execute > 0f) StatRow("Hinrichtung", "< ${pct(st.execute)} Leben")
            if (st.giantSlayer > 0f) StatRow("Gegen Bosse & Elite", "+${pct(st.giantSlayer)}")
            if (st.lootBonus > 0f) StatRow("Coins & Erfahrung", "+${pct(st.lootBonus)}")
        }

        val weaponTotal = s.weapons.keys.sumOf { (s.weaponDamage[it] ?: 0f).toDouble() }.toFloat()
        val effects = EffectDamage.entries.filter { (s.effectDamage[it] ?: 0f) > 0f }
        val total = weaponTotal + effects.sumOf { (s.effectDamage[it] ?: 0f).toDouble() }.toFloat()
        StatSection("Waffen · ${big(total)} Schaden gesamt") {
            s.weapons.keys.sortedByDescending { s.weaponDamage[it] ?: 0f }.forEach { w ->
                WeaponStatCard(engine, w, total)
            }
        }

        if (effects.isNotEmpty()) {
            StatSection("Weitere Schadensquellen") {
                effects.sortedByDescending { s.effectDamage[it] ?: 0f }.forEach { kind ->
                    val dealt = s.effectDamage[kind] ?: 0f
                    DamageCard(
                        icon = kind.icon,
                        title = kind.title,
                        badge = null,
                        highlight = false,
                        share = if (total > 0f) dealt / total else 0f,
                        summary = "${big(dealt)} Schaden · ${pct(if (total > 0f) dealt / total else 0f)} · ${big(engine.effectDps(kind))} DPS",
                        details = null,
                    )
                }
            }
        }

        if (s.passives.isNotEmpty() || s.items.isNotEmpty()) {
            StatSection("Talente & Items") {
                s.passives.forEach { (p, lv) -> BuildLine(p.icon, p.title, "Stufe $lv", p.description) }
                s.items.forEach { (i, n) -> BuildLine(i.icon, i.title, "×$n", "Gesamt ${itemTotal(i, n)}") }
            }
        }

        StatSection("Lauf") {
            StatRow("Stage", s.stage.title)
            StatRow("Modus", if (s.mode == SurvivorsMode.TIMED) "15 Minuten" else "Endlos")
            StatRow("Zeit", clock(s.time))
            StatRow("Stufe", "${s.level}")
            StatRow("Kills", big(s.kills.toFloat()))
            if (s.bossesKilled > 0) StatRow("Bosse besiegt", "${s.bossesKilled}")
            StatRow("Punkte", big(engine.score.toFloat()))
            StatRow("Coins", "${s.coinsEarned}", CoinGold)
            StatRow("Runen", "$RUNE_SYMBOL ${s.runesEarned}", RuneViolet)
            val rage = engine.enemyScaling()
            if (rage.speed > 1f) {
                fun x(v: Float) = "×${num(v)}"
                StatRow("Gegner-Raserei", "HP ${x(rage.hp)} · Schaden ${x(rage.damage)} · Tempo ${x(rage.speed)}", Color(0xFFF87171))
            }
        }
    }
}

/** Gesamtwirkung eines Items in Prozent, inklusive der Obergrenzen. */
private fun itemTotal(item: ItemKind, stacks: Int): String {
    val v = item.total(stacks)
    return when (item) {
        ItemKind.DODGE -> pct(v.coerceAtMost(ItemKind.MAX_DODGE))
        ItemKind.EXECUTE -> pct(v.coerceAtMost(ItemKind.MAX_EXECUTE))
        ItemKind.HOURGLASS -> "−" + pct(v.coerceAtMost(ItemKind.MAX_COOLDOWN_REDUCTION))
        else -> "+" + pct(v)
    }
}

@Composable
private fun StatSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title.uppercase(), style = MaterialTheme.typography.labelMedium, color = NeonYellow, fontWeight = FontWeight.Bold)
        Box(Modifier.fillMaxWidth().height(1.dp).background(ArcadeOutline))
        Spacer(Modifier.height(2.dp))
        content()
    }
}

@Composable
private fun StatRow(label: String, value: String, valueColor: Color = ArcadeText) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = ArcadeTextMuted, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        Text(value, style = MaterialTheme.typography.bodySmall, color = valueColor, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun BuildLine(icon: String, title: String, badge: String, detail: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(icon, fontSize = 16.sp, modifier = Modifier.width(26.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.bodySmall, color = ArcadeText, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(6.dp))
                Text(badge, style = MaterialTheme.typography.labelSmall, color = Color(0xFF60A5FA))
            }
            Text(detail, style = MaterialTheme.typography.labelSmall, color = ArcadeTextMuted)
        }
    }
}

@Composable
private fun GoalLine(goal: RunGoal, s: SurvivorsState) {
    val done = goal in s.goalsDone
    val progress = goal.progress(s)
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(if (done) "✔" else "○", color = if (done) Color(0xFF4ADE80) else ArcadeTextMuted, modifier = Modifier.width(22.dp))
        Column(Modifier.weight(1f)) {
            Text(goal.title, style = MaterialTheme.typography.bodySmall, color = if (done) ArcadeTextMuted else ArcadeText)
            if (goal.hasCounter && !done) {
                Box(Modifier.fillMaxWidth().padding(top = 2.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color.Black.copy(alpha = 0.4f))) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(progress.toFloat() / goal.target).background(RuneViolet))
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            if (goal.hasCounter && !done) "$progress / ${goal.target}" else "",
            style = MaterialTheme.typography.labelSmall,
            color = ArcadeTextMuted,
        )
        Spacer(Modifier.width(8.dp))
        Text("$RUNE_SYMBOL ${goal.runes}", style = MaterialTheme.typography.labelMedium, color = RuneViolet, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun WeaponStatCard(engine: SurvivorsEngine, weapon: WeaponKind, total: Float) {
    val s = engine.state
    val level = s.weapons[weapon] ?: 1
    val evolved = engine.isEvolved(weapon)
    val dealt = s.weaponDamage[weapon] ?: 0f
    val share = if (total > 0f) dealt / total else 0f
    val cooldown = engine.weaponCooldown(weapon)
    val details = buildList {
        add("${num(weapon.damage(level) * engine.stats.damageMultiplier)} / Treffer")
        if (cooldown > 0f) add("alle ${num(cooldown, 2)} s")
        if (weapon.usesExtraProjectiles || weapon.count(level) > 1) add("×${engine.weaponCount(weapon)}")
        add("☠ ${s.weaponKills[weapon] ?: 0}")
    }
    DamageCard(
        icon = if (evolved) weapon.evolutionIcon else weapon.icon,
        title = if (evolved) weapon.evolutionTitle else weapon.title,
        badge = if (evolved) "ENTWICKELT" else "Stufe $level",
        highlight = evolved,
        share = share,
        summary = "${big(dealt)} Schaden · ${pct(share)} · ${big(engine.weaponDps(weapon))} DPS",
        details = details.joinToString("  ·  "),
    )
}

/** Karte einer Schadensquelle: Kopfzeile, Anteil als Balken, Zusammenfassung und optionale Details. */
@Composable
private fun DamageCard(icon: String, title: String, badge: String?, highlight: Boolean, share: Float, summary: String, details: String?) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(shape)
            .background(ArcadeSurfaceHigh)
            .border(if (highlight) 1.5.dp else 1.dp, if (highlight) Gold else ArcadeOutline, shape)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(icon, fontSize = 18.sp)
            Spacer(Modifier.width(8.dp))
            Text(title, style = MaterialTheme.typography.titleSmall, color = ArcadeText, modifier = Modifier.weight(1f))
            if (badge != null) {
                Text(badge, style = MaterialTheme.typography.labelSmall, color = if (highlight) Gold else Color(0xFF60A5FA))
            }
        }
        Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color.Black.copy(alpha = 0.4f))) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(share).background(if (highlight) Gold else HpRed))
        }
        Text(summary, style = MaterialTheme.typography.labelSmall, color = ArcadeText)
        if (details != null) Text(details, style = MaterialTheme.typography.labelSmall, color = ArcadeTextMuted)
    }
}
