package com.adlessarcade.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.adlessarcade.core.GameInfo
import com.adlessarcade.core.GameRegistry
import com.adlessarcade.ui.components.ArcadeScaffold
import com.adlessarcade.ui.components.ComingSoonCard
import com.adlessarcade.ui.components.GameCard
import com.adlessarcade.ui.theme.ArcadeAccentBrush
import com.adlessarcade.ui.theme.ArcadeTextMuted

@Composable
fun HomeScreen(onGameClick: (GameInfo) -> Unit) {
    ArcadeScaffold {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 150.dp),
            contentPadding = PaddingValues(20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
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
private fun Header() {
    Column(Modifier.padding(top = 20.dp, bottom = 12.dp)) {
        Text("WERBEFREI · OFFLINE", style = MaterialTheme.typography.labelSmall, color = ArcadeTextMuted)
        Spacer(Modifier.height(6.dp))
        Text(
            "Adless\nArcade",
            style = MaterialTheme.typography.displayMedium.merge(TextStyle(brush = ArcadeAccentBrush)),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Kleine Spiele. Keine Werbung. Einfach spielen.",
            style = MaterialTheme.typography.bodyMedium,
            color = ArcadeTextMuted,
        )
    }
}
