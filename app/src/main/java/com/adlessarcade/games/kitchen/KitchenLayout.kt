package com.adlessarcade.games.kitchen

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect

/**
 * Aufteilung der Zeichenfläche im Querformat: oben die Bestell-Bons, darunter die Küche.
 * Vorrat links, hinten Herd/Schüssel/Ausgabe, vorne Brett/Teller/Müll.
 */
class KitchenLayout(val width: Float, val height: Float) {
    val ordersArea = Rect(0f, 0f, width * 0.78f, height * ORDERS_HEIGHT)
    val kitchen = Rect(0f, height * ORDERS_HEIGHT, width, height)

    private fun k(x0: Float, y0: Float, x1: Float, y1: Float) = Rect(
        kitchen.left + kitchen.width * x0, kitchen.top + kitchen.height * y0,
        kitchen.left + kitchen.width * x1, kitchen.top + kitchen.height * y1,
    )

    val pantry = k(0.005f, 0.03f, 0.2f, 0.97f)
    val stove = k(0.22f, 0.04f, 0.55f, 0.47f)
    val bowl = k(0.57f, 0.04f, 0.72f, 0.47f)
    val serve = k(0.8f, 0.04f, 0.995f, 0.47f)
    val board = k(0.22f, 0.53f, 0.45f, 0.96f)
    val plates = listOf(k(0.47f, 0.53f, 0.61f, 0.96f), k(0.63f, 0.53f, 0.77f, 0.96f))
    val trash = k(0.86f, 0.55f, 0.99f, 0.96f)

    /** Kisten im Vorrat: 2 Spalten × 4 Reihen. */
    val crates: List<Pair<ItemKind, Rect>> = KitchenCatalog.crates.mapIndexed { i, kind ->
        val col = i % 2
        val row = i / 2
        val w = pantry.width / 2f
        val h = pantry.height / 4f
        kind to Rect(pantry.left + col * w + w * 0.06f, pantry.top + row * h + h * 0.06f, pantry.left + (col + 1) * w - w * 0.06f, pantry.top + (row + 1) * h - h * 0.06f)
    }

    fun burner(index: Int): Rect {
        val w = stove.width / KitchenCatalog.STOVES
        return Rect(stove.left + index * w, stove.top, stove.left + (index + 1) * w, stove.bottom)
    }

    /** Bestell-Bons nebeneinander. */
    fun ticket(index: Int): Rect {
        val w = ordersArea.width / MAX_TICKETS
        return Rect(ordersArea.left + index * w + w * 0.04f, ordersArea.top + ordersArea.height * 0.06f, ordersArea.left + (index + 1) * w - w * 0.04f, ordersArea.bottom - ordersArea.height * 0.04f)
    }

    /** Minispiel-Fläche über der Küche (die Bestellungen bleiben sichtbar). */
    val panel = Rect(width * 0.08f, kitchen.top + kitchen.height * 0.03f, width * 0.92f, height - kitchen.height * 0.03f)
    val panelAspect: Float get() = panel.width / panel.height

    fun toPanel(p: Offset) = Offset((p.x - panel.left) / panel.width, (p.y - panel.top) / panel.height)
    fun fromPanel(x: Float, y: Float) = Offset(panel.left + x * panel.width, panel.top + y * panel.height)

    fun rectOf(station: Station): Rect = when (station) {
        is Station.Crate -> crates.first { it.first == station.kind }.second
        Station.Board -> board
        Station.Bowl -> bowl
        is Station.Stove -> burner(station.index)
        is Station.PlateSpot -> plates[station.index]
        Station.Serve -> serve
        Station.Trash -> trash
    }

    fun stationAt(p: Offset): Station? {
        crates.firstOrNull { it.second.contains(p) }?.let { return Station.Crate(it.first) }
        for (i in 0 until KitchenCatalog.STOVES) if (burner(i).contains(p)) return Station.Stove(i)
        plates.forEachIndexed { i, r -> if (r.contains(p)) return Station.PlateSpot(i) }
        return when {
            board.contains(p) -> Station.Board
            bowl.contains(p) -> Station.Bowl
            serve.contains(p) -> Station.Serve
            trash.contains(p) -> Station.Trash
            else -> null
        }
    }

    fun ticketAt(p: Offset): Int? = (0 until MAX_TICKETS).firstOrNull { ticket(it).contains(p) }

    companion object {
        const val ORDERS_HEIGHT = 0.27f
        const val MAX_TICKETS = 4
    }
}
