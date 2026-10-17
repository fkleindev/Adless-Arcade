package com.adlessarcade.games.galaxytowers

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.rememberTextMeasurer
import com.adlessarcade.ui.theme.LocalCosmetics

private val PreviewFaces = listOf(Card(13, 1), Card(12, 0), Card(1, 2))

/** Kleine Illustration für Hauptmenü und Modus-Menü: drei Kartentürme vor Sternen. */
@Composable
fun GalaxyTowersPreview(modifier: Modifier = Modifier) {
    val cosmetics = LocalCosmetics.current
    val textMeasurer = rememberTextMeasurer(cacheSize = 8)
    Canvas(modifier) {
        val w = minOf(size.width / 6.4f, size.height / 3.6f)
        val cardSize = Size(w, w * CARD_RATIO)
        val origin = Offset((size.width - w * 6f) / 2f, (size.height - (cardSize.height + w * 1.2f)) / 2f)
        repeat(3) { peak ->
            val baseX = origin.x + peak * w * 2.1f
            drawCardBack(Offset(baseX + w * 0.5f, origin.y), cardSize, cosmetics.cardBack)
            drawCardFace(Offset(baseX, origin.y + w * 0.6f), cardSize, PreviewFaces[peak], textMeasurer, highlight = peak == 1)
        }
    }
}
