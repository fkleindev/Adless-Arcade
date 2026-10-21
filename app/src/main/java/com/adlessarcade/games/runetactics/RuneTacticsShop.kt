package com.adlessarcade.games.runetactics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adlessarcade.ui.theme.ArcadeOutline
import com.adlessarcade.ui.theme.ArcadeSurface
import com.adlessarcade.ui.theme.ArcadeSurfaceHigh
import com.adlessarcade.ui.theme.ArcadeText
import com.adlessarcade.ui.theme.ArcadeTextMuted

/** Ein Shop-Platz, wie ihn die Fußleiste zeigt (null-[def] = leer). */
internal data class ShopOffer(
    val def: UnitDef?,
    val star: Int = 1,
    val price: Int = 0,
    val affordable: Boolean = false,
    /** Kauf ergibt eine höhere Sternstufe. */
    val combines: Boolean = false,
    val locked: Boolean = false,
)

internal fun RuneTacticsEngine.shopOffers(): List<ShopOffer> = shop.indices.map { i ->
    val id = shop[i] ?: return@map ShopOffer(null)
    val price = shopPrice(i) ?: 0
    ShopOffer(RuneTacticsUnits.byId(id), shopStars[i], price, price <= gold, wouldCombine(id, shopStars[i]), shopLocked[i])
}

/** Shop als Tresen: Kopfzeile mit den Wahrscheinlichkeiten des Levels, darunter die Karten und die Aktionen. */
@Composable
internal fun ShopPanel(
    offers: List<ShopOffer>,
    level: Int,
    enabled: Boolean,
    onBuy: (Int) -> Unit,
    onLock: (Int) -> Unit,
    onInfo: (UnitDef, Int) -> Unit,
    actions: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.verticalGradient(listOf(ArcadeSurfaceHigh, ArcadeSurface)))
            .border(1.dp, StarGold.copy(alpha = 0.35f), shape)
            .padding(horizontal = 6.dp, vertical = 5.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        OddsHeader(level)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            offers.forEachIndexed { index, offer ->
                ShopCard(
                    offer,
                    enabled = enabled,
                    onBuy = { onBuy(index) },
                    onLock = { onLock(index) },
                    onInfo = onInfo,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        actions()
    }
}

@Composable
private fun OddsHeader(level: Int) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "SHOP",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
            color = StarGold,
        )
        Spacer(Modifier.weight(1f))
        val odds = Economy.odds(level)
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
            (1..5).filter { odds[it - 1] > 0 }.forEach { cost ->
                Text("●${odds[cost - 1]}%", style = MaterialTheme.typography.labelSmall, color = costColor(cost))
            }
            val star2 = Economy.star2Odds(level)
            if (star2 > 0) {
                val percent = if (star2 % 10 == 0) "${star2 / 10}" else "${star2 / 10},${star2 % 10}"
                Text("★★$percent%", style = MaterialTheme.typography.labelSmall, color = StarGold)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ShopCard(
    offer: ShopOffer,
    enabled: Boolean,
    onBuy: () -> Unit,
    onLock: () -> Unit,
    onInfo: (UnitDef, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(10.dp)
    val def = offer.def
    if (def == null) {
        Box(
            modifier
                .height(CARD_HEIGHT)
                .clip(shape)
                .background(ArcadeSurface.copy(alpha = 0.5f))
                .border(1.dp, ArcadeOutline.copy(alpha = 0.4f), shape)
        )
        return
    }
    val cost = costColor(def.cost)
    val star2 = offer.star > 1
    val border = when {
        offer.combines -> StarGold
        offer.locked -> ArcadeCyan
        else -> cost.copy(alpha = 0.8f)
    }
    Box(
        modifier
            .height(CARD_HEIGHT)
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(cost.copy(alpha = 0.45f), lerp(ArcadeSurfaceHigh, cost, 0.08f), ArcadeSurface),
                )
            )
            .background(if (star2) StarGold.copy(alpha = 0.14f) else Color.Transparent)
            .border(if (offer.combines || offer.locked || star2) 2.dp else 1.dp, border, shape)
            .combinedClickable(onClick = onBuy, onLongClick = { onInfo(def, offer.star) }),
    ) {
        // Figur auf leuchtendem Runenpodest; ausgegraut, wenn nicht kaufbar.
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .alpha(if (enabled && offer.affordable) 1f else 0.45f)
        ) {
            val figure = size.height * 0.95f
            val c = Offset(size.width / 2f, size.height * 0.5f)
            drawPodium(Offset(c.x, c.y + figure * 0.33f), figure * 0.42f, if (star2) StarGold else cost)
            drawFigure(def, c, figure, time = 0f)
        }
        if (star2) {
            Text(
                "★".repeat(offer.star),
                style = MaterialTheme.typography.labelSmall,
                color = StarGold,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 4.dp, top = 2.dp),
            )
        }
        LockButton(offer.locked, enabled, onLock, Modifier.align(Alignment.TopEnd))
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .alpha(if (enabled && offer.affordable) 1f else 0.55f),
        ) {
            Text(
                def.name,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                letterSpacing = 0.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (star2) StarGold else ArcadeText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.35f))
                    .padding(horizontal = 2.dp, vertical = 1.dp),
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(cost.copy(alpha = 0.28f))
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TraitIcon(def.origin)
                Spacer(Modifier.width(2.dp))
                TraitIcon(def.role)
                Spacer(Modifier.weight(1f))
                Text(
                    "● ${offer.price}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (offer.affordable) StarGold else LossRed,
                )
            }
        }
    }
}

@Composable
private fun LockButton(locked: Boolean, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(26.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(3.dp)
            .clip(CircleShape)
            .background(if (locked) ArcadeCyan.copy(alpha = 0.3f) else Color.Black.copy(alpha = 0.3f)),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(12.dp)) {
            drawLock(Offset(size.width / 2f, size.height / 2f), size.width, locked, if (locked) ArcadeCyan else ArcadeTextMuted)
        }
    }
}

/** Kleines Synergie-Symbol: Herkunft in ihrer Farbe, Klassen in Gold. */
@Composable
internal fun TraitIcon(trait: Trait, modifier: Modifier = Modifier) {
    val color = if (trait.kind == TraitKind.ORIGIN) originColor(trait) else StarGold
    Canvas(modifier.size(15.dp)) {
        val c = Offset(size.width / 2f, size.height / 2f)
        drawCircle(color.copy(alpha = 0.22f), size.width / 2f, c)
        drawCircle(color.copy(alpha = 0.8f), size.width / 2f - 0.5.dp.toPx(), c, style = Stroke(1.dp.toPx()))
        drawTraitGlyph(trait, c, size.width * 0.3f, color)
    }
}

private fun DrawScope.drawPodium(center: Offset, radius: Float, color: Color) {
    drawOval(
        Brush.radialGradient(listOf(color.copy(alpha = 0.55f), Color.Transparent), center, radius * 1.3f),
        Offset(center.x - radius * 1.3f, center.y - radius * 0.9f), Size(radius * 2.6f, radius * 1.8f),
    )
    drawOval(
        color.copy(alpha = 0.7f), Offset(center.x - radius, center.y - radius * 0.32f), Size(radius * 2f, radius * 0.64f),
        style = Stroke(radius * 0.08f),
    )
    drawOval(
        color.copy(alpha = 0.35f), Offset(center.x - radius * 0.7f, center.y - radius * 0.22f),
        Size(radius * 1.4f, radius * 0.44f), style = Stroke(radius * 0.05f),
    )
}

private fun DrawScope.drawLock(c: Offset, s: Float, locked: Boolean, color: Color) {
    val bodyTop = c.y - s * 0.05f
    drawRoundRect(color, Offset(c.x - s * 0.42f, bodyTop), Size(s * 0.84f, s * 0.55f), CornerRadius(s * 0.1f))
    // Bügel: geschlossen mittig, offen nach rechts versetzt und angehoben
    val shiftX = if (locked) 0f else s * 0.28f
    val shiftY = if (locked) 0f else -s * 0.12f
    drawArc(
        color, 180f, 180f, false, Offset(c.x - s * 0.28f + shiftX, c.y - s * 0.5f + shiftY), Size(s * 0.56f, s * 0.6f),
        style = Stroke(s * 0.13f),
    )
    drawLine(color, Offset(c.x - s * 0.28f + shiftX, c.y - s * 0.2f + shiftY), Offset(c.x - s * 0.28f + shiftX, bodyTop), s * 0.13f)
    drawLine(color, Offset(c.x + s * 0.28f + shiftX, c.y - s * 0.2f + shiftY), Offset(c.x + s * 0.28f + shiftX, bodyTop + if (locked) 0f else -s * 0.05f), s * 0.13f)
    drawCircle(Color.Black.copy(alpha = 0.5f), s * 0.07f, Offset(c.x, bodyTop + s * 0.26f))
}

private fun DrawScope.drawTraitGlyph(trait: Trait, c: Offset, s: Float, color: Color) {
    val w = s * 0.32f
    when (trait) {
        Trait.UNDEAD -> {
            drawCircle(color, s * 0.72f, Offset(c.x, c.y - s * 0.15f))
            drawRect(color, Offset(c.x - s * 0.4f, c.y + s * 0.3f), Size(s * 0.8f, s * 0.55f))
            drawCircle(Color.Black.copy(alpha = 0.75f), s * 0.2f, Offset(c.x - s * 0.28f, c.y - s * 0.15f))
            drawCircle(Color.Black.copy(alpha = 0.75f), s * 0.2f, Offset(c.x + s * 0.28f, c.y - s * 0.15f))
        }
        Trait.ELF -> {
            val leaf = Path().apply {
                moveTo(c.x - s, c.y + s)
                quadraticTo(c.x - s, c.y - s, c.x + s, c.y - s)
                quadraticTo(c.x + s, c.y + s, c.x - s, c.y + s)
                close()
            }
            drawPath(leaf, color)
            drawLine(Color.Black.copy(alpha = 0.5f), Offset(c.x - s * 0.8f, c.y + s * 0.8f), Offset(c.x + s * 0.6f, c.y - s * 0.6f), w * 0.5f)
        }
        Trait.DWARF -> {
            drawLine(color, Offset(c.x, c.y - s * 0.3f), Offset(c.x, c.y + s), w, StrokeCap.Round)
            drawRoundRect(color, Offset(c.x - s, c.y - s), Size(s * 2f, s * 0.75f), CornerRadius(s * 0.15f))
        }
        Trait.DEMON -> {
            val left = Path().apply {
                moveTo(c.x - s * 0.55f, c.y + s * 0.4f)
                quadraticTo(c.x - s * 1.1f, c.y - s * 0.2f, c.x - s * 0.7f, c.y - s)
                quadraticTo(c.x - s * 0.5f, c.y - s * 0.3f, c.x - s * 0.1f, c.y + s * 0.1f)
                close()
            }
            val right = Path().apply {
                moveTo(c.x + s * 0.55f, c.y + s * 0.4f)
                quadraticTo(c.x + s * 1.1f, c.y - s * 0.2f, c.x + s * 0.7f, c.y - s)
                quadraticTo(c.x + s * 0.5f, c.y - s * 0.3f, c.x + s * 0.1f, c.y + s * 0.1f)
                close()
            }
            drawPath(left, color)
            drawPath(right, color)
            drawCircle(color, s * 0.42f, Offset(c.x, c.y + s * 0.5f))
        }
        Trait.ELEMENTAL -> {
            val flame = Path().apply {
                moveTo(c.x, c.y - s * 1.1f)
                quadraticTo(c.x + s * 1.1f, c.y + s * 0.1f, c.x + s * 0.55f, c.y + s * 0.85f)
                quadraticTo(c.x, c.y + s * 1.2f, c.x - s * 0.55f, c.y + s * 0.85f)
                quadraticTo(c.x - s * 1.1f, c.y + s * 0.1f, c.x, c.y - s * 1.1f)
                close()
            }
            drawPath(flame, color)
            drawCircle(Color.White.copy(alpha = 0.6f), s * 0.28f, Offset(c.x, c.y + s * 0.45f))
        }
        Trait.WARRIOR -> {
            drawLine(color, Offset(c.x, c.y - s * 1.05f), Offset(c.x, c.y + s * 0.6f), w, StrokeCap.Round)
            drawLine(color, Offset(c.x - s * 0.6f, c.y + s * 0.35f), Offset(c.x + s * 0.6f, c.y + s * 0.35f), w, StrokeCap.Round)
            drawLine(color, Offset(c.x, c.y + s * 0.6f), Offset(c.x, c.y + s), w * 1.2f, StrokeCap.Round)
        }
        Trait.GUARDIAN -> {
            val shield = Path().apply {
                moveTo(c.x - s * 0.85f, c.y - s * 0.9f)
                lineTo(c.x + s * 0.85f, c.y - s * 0.9f)
                lineTo(c.x + s * 0.8f, c.y)
                quadraticTo(c.x + s * 0.6f, c.y + s * 0.8f, c.x, c.y + s * 1.1f)
                quadraticTo(c.x - s * 0.6f, c.y + s * 0.8f, c.x - s * 0.8f, c.y)
                close()
            }
            drawPath(shield, color)
        }
        Trait.MAGE -> {
            val star = Path().apply {
                moveTo(c.x, c.y - s * 1.1f)
                quadraticTo(c.x, c.y, c.x + s * 1.1f, c.y)
                quadraticTo(c.x, c.y, c.x, c.y + s * 1.1f)
                quadraticTo(c.x, c.y, c.x - s * 1.1f, c.y)
                quadraticTo(c.x, c.y, c.x, c.y - s * 1.1f)
                close()
            }
            drawPath(star, color)
        }
        Trait.RANGER -> {
            drawLine(color, Offset(c.x - s * 0.9f, c.y + s * 0.9f), Offset(c.x + s * 0.6f, c.y - s * 0.6f), w * 0.8f, StrokeCap.Round)
            val head = Path().apply {
                moveTo(c.x + s, c.y - s)
                lineTo(c.x + s * 0.2f, c.y - s * 0.7f)
                lineTo(c.x + s * 0.7f, c.y - s * 0.2f)
                close()
            }
            drawPath(head, color)
            drawLine(color, Offset(c.x - s * 0.9f, c.y + s * 0.9f), Offset(c.x - s * 0.9f, c.y + s * 0.35f), w * 0.6f)
            drawLine(color, Offset(c.x - s * 0.9f, c.y + s * 0.9f), Offset(c.x - s * 0.35f, c.y + s * 0.9f), w * 0.6f)
        }
        Trait.ASSASSIN -> {
            drawLine(color, Offset(c.x - s * 0.9f, c.y - s * 0.9f), Offset(c.x + s * 0.9f, c.y + s * 0.9f), w, StrokeCap.Round)
            drawLine(color, Offset(c.x + s * 0.9f, c.y - s * 0.9f), Offset(c.x - s * 0.9f, c.y + s * 0.9f), w, StrokeCap.Round)
        }
        else -> drawCircle(color, s * 0.6f, c)
    }
}

private val CARD_HEIGHT = 96.dp
