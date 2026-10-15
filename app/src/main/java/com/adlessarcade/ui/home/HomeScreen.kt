package com.adlessarcade.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adlessarcade.arcadeApp
import com.adlessarcade.core.GameInfo
import com.adlessarcade.core.GameRegistry
import com.adlessarcade.core.shop.ProfileState
import com.adlessarcade.ui.components.ArcadeIconButton
import com.adlessarcade.ui.components.ArcadeScaffold
import com.adlessarcade.ui.components.CoinChip
import com.adlessarcade.ui.components.ComingSoonCard
import com.adlessarcade.ui.components.GameCard
import com.adlessarcade.ui.components.ProfileBadge
import com.adlessarcade.ui.theme.ArcadeTextMuted
import com.adlessarcade.ui.theme.LocalCosmetics

@Composable
fun HomeScreen(onGameClick: (GameInfo) -> Unit, onShopClick: () -> Unit, onAchievementsClick: () -> Unit) {
    val profileFlow = LocalContext.current.arcadeApp.profile.state
    val profile by profileFlow.collectAsState(initial = ProfileState())

    ArcadeScaffold {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 150.dp),
            contentPadding = PaddingValues(20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                TopBar(coins = profile.coins, onShopClick = onShopClick, onAchievementsClick = onAchievementsClick)
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Header()
            }
            items(GameRegistry.games, key = { it.id }) { game ->
                GameCard(game = game, onClick = { onGameClick(game) })
            }
            item { ComingSoonCard() }
        }
    }
}

@Composable
private fun TopBar(coins: Int, onShopClick: () -> Unit, onAchievementsClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        // Die Spieler-Anzeige bekommt den gesamten freien Platz, damit lange Titel nicht umbrechen.
        ProfileBadge(Modifier.weight(1f), onClick = onShopClick)
        Spacer(Modifier.width(8.dp))
        CoinChip(coins, onClick = onShopClick)
        Spacer(Modifier.width(8.dp))
        ArcadeIconButton(onClick = onAchievementsClick) {
            Text("🏆", fontSize = 18.sp)
        }
        Spacer(Modifier.width(8.dp))
        ArcadeIconButton(onClick = onShopClick) {
            Icon(Icons.Filled.ShoppingCart, contentDescription = "Shop")
        }
    }
}

@Composable
private fun Header() {
    Column(Modifier.padding(top = 12.dp, bottom = 12.dp)) {
        Text("WERBEFREI · OFFLINE", style = MaterialTheme.typography.labelSmall, color = ArcadeTextMuted)
        Spacer(Modifier.height(6.dp))
        Text(
            "Adless\nArcade",
            style = MaterialTheme.typography.displayMedium.merge(
                TextStyle(brush = LocalCosmetics.current.palette.accentBrush)
            ),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Kleine Spiele. Keine Werbung. Einfach spielen.",
            style = MaterialTheme.typography.bodyMedium,
            color = ArcadeTextMuted,
        )
    }
}
