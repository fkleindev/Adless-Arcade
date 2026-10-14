package com.adlessarcade.ui.shop

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.adlessarcade.arcadeApp
import com.adlessarcade.core.shop.ProfileState
import com.adlessarcade.core.shop.ShopCatalog
import com.adlessarcade.core.shop.ShopCategory
import com.adlessarcade.core.shop.ShopItem
import com.adlessarcade.games.gaterush.bulletColor
import com.adlessarcade.games.gaterush.drawHeroFigure
import com.adlessarcade.ui.components.ArcadeScaffold
import com.adlessarcade.ui.components.AvatarCircle
import com.adlessarcade.ui.components.CoinChip
import com.adlessarcade.ui.components.CoinGold
import com.adlessarcade.ui.components.CoinIcon
import com.adlessarcade.ui.components.TitleBadge
import com.adlessarcade.ui.components.drawEmptyCell
import com.adlessarcade.ui.components.drawSkinBlock
import com.adlessarcade.ui.theme.ArcadeOutline
import com.adlessarcade.ui.theme.ArcadeSurface
import com.adlessarcade.ui.theme.ArcadeSurfaceHigh
import com.adlessarcade.ui.theme.ArcadeText
import com.adlessarcade.ui.theme.ArcadeTextMuted
import com.adlessarcade.ui.theme.BoardBackground
import com.adlessarcade.ui.theme.CosmeticRegistry
import com.adlessarcade.ui.theme.EmptyCell
import com.adlessarcade.ui.theme.LocalCosmetics
import kotlinx.coroutines.launch

@Composable
fun ShopScreen(onBack: () -> Unit) {
    val repository = LocalContext.current.arcadeApp.profile
    val profile by repository.state.collectAsState(initial = ProfileState())
    var category by rememberSaveable { mutableStateOf(ShopCategory.BLOCK_SKIN) }
    var pendingPurchase by remember { mutableStateOf<ShopItem?>(null) }
    var purchaseFailed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    ArcadeScaffold(
        title = "Shop",
        subtitle = "Coins eintauschen",
        onBack = onBack,
        actions = { CoinChip(profile.coins) },
    ) {
        Column(Modifier.fillMaxSize()) {
            CategoryChips(selected = category, onSelect = { category = it })
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 150.dp),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(ShopCatalog.inCategory(category), key = { it.id }) { item ->
                    val owned = profile.isOwned(item)
                    ShopItemCard(
                        item = item,
                        owned = owned,
                        equipped = profile.equipped(item.category).id == item.id,
                        affordable = profile.coins >= item.price,
                        onClick = { if (owned) repository.equip(item) else pendingPurchase = item },
                    )
                }
            }
        }
    }

    pendingPurchase?.let { item ->
        val missing = item.price - profile.coins
        AlertDialog(
            onDismissRequest = { pendingPurchase = null },
            containerColor = ArcadeSurface,
            title = { Text(if (missing > 0) "Nicht genug Coins" else "„${item.name}“ kaufen?") },
            text = {
                Text(
                    if (missing > 0) {
                        "Dir fehlen noch $missing Coins. Spiel eine Runde – Coins gibt es für Combos, Kills und Kisten."
                    } else {
                        "Für ${item.price} Coins freischalten und direkt ausrüsten."
                    },
                    color = ArcadeTextMuted,
                )
            },
            confirmButton = {
                if (missing > 0) {
                    TextButton(onClick = { pendingPurchase = null }) { Text("OK", color = ArcadeText) }
                } else {
                    TextButton(onClick = {
                        pendingPurchase = null
                        scope.launch { purchaseFailed = !repository.purchase(item) }
                    }) { Text("Kaufen", color = CoinGold) }
                }
            },
            dismissButton = {
                if (missing <= 0) {
                    TextButton(onClick = { pendingPurchase = null }) { Text("Abbrechen", color = ArcadeTextMuted) }
                }
            },
        )
    }

    if (purchaseFailed) {
        AlertDialog(
            onDismissRequest = { purchaseFailed = false },
            containerColor = ArcadeSurface,
            title = { Text("Kauf fehlgeschlagen") },
            text = { Text("Bitte versuche es noch einmal.", color = ArcadeTextMuted) },
            confirmButton = { TextButton(onClick = { purchaseFailed = false }) { Text("OK", color = ArcadeText) } },
        )
    }
}

@Composable
private fun CategoryChips(selected: ShopCategory, onSelect: (ShopCategory) -> Unit) {
    val palette = LocalCosmetics.current.palette
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ShopCategory.entries.forEach { category ->
            val isSelected = category == selected
            val shape = RoundedCornerShape(50)
            Text(
                category.title,
                style = MaterialTheme.typography.labelLarge,
                color = if (isSelected) ArcadeText else ArcadeTextMuted,
                modifier = Modifier
                    .clip(shape)
                    .then(
                        if (isSelected) Modifier.background(palette.accentBrush)
                        else Modifier.background(ArcadeSurfaceHigh).border(1.dp, ArcadeOutline, shape)
                    )
                    .clickable { onSelect(category) }
                    .padding(horizontal = 16.dp, vertical = 9.dp),
            )
        }
    }
}

@Composable
private fun ShopItemCard(
    item: ShopItem,
    owned: Boolean,
    equipped: Boolean,
    affordable: Boolean,
    onClick: () -> Unit,
) {
    val palette = LocalCosmetics.current.palette
    val shape = RoundedCornerShape(22.dp)
    Column(
        Modifier
            .clip(shape)
            .background(ArcadeSurface)
            .border(if (equipped) 2.dp else 1.dp, if (equipped) palette.accent2 else ArcadeOutline, shape)
            .clickable(enabled = !equipped, onClick = onClick)
            .padding(12.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1.15f)
                .clip(RoundedCornerShape(16.dp))
                .background(BoardBackground),
            contentAlignment = Alignment.Center,
        ) {
            ItemPreview(item)
        }
        Spacer(Modifier.height(10.dp))
        Text(item.name, style = MaterialTheme.typography.titleMedium, color = ArcadeText)
        Spacer(Modifier.height(6.dp))
        when {
            equipped -> Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = palette.accent2, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Ausgerüstet", style = MaterialTheme.typography.labelLarge, color = palette.accent2)
            }
            owned -> Text("Ausrüsten", style = MaterialTheme.typography.labelLarge, color = ArcadeText)
            else -> Row(
                Modifier.alpha(if (affordable) 1f else 0.45f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CoinIcon(16.dp)
                Spacer(Modifier.width(4.dp))
                Text("${item.price}", style = MaterialTheme.typography.labelLarge, color = CoinGold)
            }
        }
    }
}

// Ziffer = Blockfarbe, "." = leer.
private val SkinPreviewPattern = listOf("12.", "345", "6.7")

@Composable
private fun ItemPreview(item: ShopItem) {
    when (item.category) {
        ShopCategory.BLOCK_SKIN -> {
            val skin = CosmeticRegistry.blockSkin(item.id)
            Canvas(Modifier.fillMaxSize().padding(16.dp)) {
                val cell = minOf(size.width, size.height) / 3f
                val origin = Offset((size.width - cell * 3) / 2f, (size.height - cell * 3) / 2f)
                SkinPreviewPattern.forEachIndexed { r, line ->
                    line.forEachIndexed { c, ch ->
                        val topLeft = origin + Offset(c * cell, r * cell)
                        if (ch == '.') drawEmptyCell(topLeft, cell, EmptyCell) else drawSkinBlock(skin, topLeft, cell, ch.digitToInt())
                    }
                }
            }
        }
        ShopCategory.HERO_SKIN -> {
            val skin = CosmeticRegistry.heroSkin(item.id)
            Canvas(Modifier.fillMaxSize().padding(12.dp)) {
                val w = size.minDimension * 0.3f
                val cx = size.width / 2f
                drawHeroFigure(Offset(cx, size.height * 0.95f), w, skin)
                repeat(3) { i ->
                    val y = size.height * (0.38f - i * 0.13f)
                    val color = skin.bulletColor(i * 4f, 0f)
                    drawCircle(color.copy(alpha = 0.3f), w * 0.13f, Offset(cx + w * 0.26f, y))
                    drawCircle(color, w * 0.07f, Offset(cx + w * 0.26f, y))
                }
            }
        }
        ShopCategory.APP_THEME -> {
            val palette = CosmeticRegistry.palette(item.id)
            Box(Modifier.fillMaxSize().background(palette.backgroundBrush)) {
                Canvas(Modifier.fillMaxSize().padding(16.dp)) {
                    val barH = size.height * 0.22f
                    drawRoundRect(
                        palette.accentBrush,
                        Offset(0f, size.height - barH),
                        Size(size.width, barH),
                        CornerRadius(barH / 2f),
                    )
                    drawCircle(palette.accent, size.minDimension * 0.16f, Offset(size.width * 0.3f, size.height * 0.35f))
                    drawCircle(palette.accent2, size.minDimension * 0.16f, Offset(size.width * 0.7f, size.height * 0.35f))
                }
            }
        }
        ShopCategory.AVATAR -> AvatarCircle(item.symbol ?: "🎮", size = 72.dp)
        ShopCategory.TITLE -> TitleBadge(item.name)
    }
}
