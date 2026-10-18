package com.adlessarcade.games.kitchen

import kotlinx.serialization.Serializable

/** Alles, was in der Küche herumliegen kann: rohe Zutaten, Zwischenprodukte, gegarte Teile, Verbranntes. */
enum class ItemKind(val title: String) {
    // Roh aus dem Vorrat
    EGG("Ei"),
    MILK("Milch"),
    FLOUR("Mehl"),
    BREAD("Brot"),
    CHEESE("Käse"),
    STRAWBERRY("Erdbeere"),
    BANANA("Banane"),
    APPLE("Apfel"),

    // Vom Schneidebrett
    BREAD_SLICES("Brotscheiben"),
    CHEESE_GRATED("Käse gerieben"),
    STRAWBERRY_CUT("Erdbeeren geschnitten"),
    BANANA_CUT("Banane geschnitten"),
    APPLE_CUT("Apfel geschnitten"),

    // Aus der Schüssel
    EGG_MIX("Eimasse"),
    BATTER("Pfannkuchenteig"),

    // Aus der Pfanne
    FRIED_EGG("Spiegelei"),
    TOAST("Toast"),
    OMELETTE("Omelett"),
    PANCAKE("Pfannkuchen"),

    BURNT("Verbrannt");

    val isRaw: Boolean get() = ordinal <= APPLE.ordinal
}

/**
 * Ein Gegenstand mit den Genauigkeiten aller Schritte, die er durchlaufen hat (0..1).
 * Daraus ergibt sich am Ende die Qualität des Gerichts.
 */
@Serializable
data class Item(val kind: ItemKind, val scores: List<Float> = emptyList()) {
    fun with(score: Float) = copy(scores = scores + score)
}

enum class MiniGameKind(val title: String, val hint: String) {
    CHOP("Schneiden", "Tippe, wenn das Messer über einer Linie ist"),
    PEEL("Schälen", "Wische über die Streifen der Schale"),
    GRATE("Reiben", "Wische schnell auf und ab"),
    CRACK("Aufschlagen", "Tippe, wenn der Zeiger im grünen Bereich ist"),
    POUR("Gießen", "Halte gedrückt und lass bei der Linie los"),
    STIR("Rühren", "Ziehe gleichmäßige Kreise – nicht zu schnell"),
    FLIP("Wenden", "Wische nach oben, wenn es goldbraun ist"),
    HEAT("Hitze halten", "Halten heizt, Loslassen kühlt – bleib im grünen Bereich"),
    PLATE("Anrichten", "Ziehe jede Zutat auf einen Platz, der sie blass zeigt"),
    SEASON("Würzen", "Tippe auf den Streuer, bis die Anzeige im grünen Bereich ist"),
}

/** Ein Gericht: Komponenten auf dem Teller, Minispiele beim Anrichten, Grundpunkte. */
data class Dish(
    val id: String,
    val name: String,
    val components: List<ItemKind>,
    val plating: List<MiniGameKind>,
    val basePoints: Int,
)

@Serializable
data class Order(
    val id: Int,
    val dishId: String,
    val timeLimit: Float,
    var remaining: Float,
) {
    val dish: Dish get() = KitchenCatalog.dish(dishId)
    val timeFraction: Float get() = (remaining / timeLimit).coerceIn(0f, 1f)
}

/** Herdplatte mit Pfanne: gart im Hintergrund. 1 = fertig, ab [KitchenCatalog.BURN_AT] verbrannt. */
@Serializable
data class Pan(
    val item: Item? = null,
    val progress: Float = 0f,
    val flipped: Boolean = false,
) {
    val rule: PanRule? get() = item?.let { KitchenCatalog.panRule(it.kind) }
    val needsFlip: Boolean get() = rule?.flip == true && !flipped
    val canFlip: Boolean get() = needsFlip && progress >= KitchenCatalog.FLIP_FROM && progress < KitchenCatalog.BURN_AT
    val isDone: Boolean get() = item != null && progress >= 1f
    val isBurning: Boolean get() = item != null && progress >= KitchenCatalog.OVERCOOK_FROM
}

@Serializable
data class Plate(
    val components: List<Item> = emptyList(),
    val dishId: String? = null,
    /** Genauigkeiten der Anrichte-Minispiele. */
    val scores: List<Float> = emptyList(),
) {
    val isReady: Boolean get() = dishId != null
}

/** Station in der Küche, für Drag & Drop. Vorratskisten tragen ihre Zutat, Herd und Teller ihren Index. */
@Serializable
sealed interface Station {
    @Serializable data class Crate(val kind: ItemKind) : Station
    @Serializable data object Board : Station
    @Serializable data object Bowl : Station
    @Serializable data class Stove(val index: Int) : Station
    @Serializable data class PlateSpot(val index: Int) : Station
    @Serializable data object Serve : Station
    @Serializable data object Trash : Station
}

/** Was nach einem Minispiel passiert – serialisierbar, damit ein laufender Schritt beim Fortsetzen neu starten kann. */
@Serializable
sealed interface Task {
    val kind: MiniGameKind

    /** Zutat auf dem Brett; [remaining] sind die noch folgenden Minispiele. */
    @Serializable data class BoardPrep(val item: Item, val remaining: List<MiniGameKind>) : Task {
        override val kind get() = remaining.first()
    }

    /** Zutat wird in die Schüssel gegeben (Aufschlagen, Gießen …). */
    @Serializable data class BowlAdd(val item: Item, override val kind: MiniGameKind) : Task

    /** Schüsselinhalt wird zu [output] verrührt. */
    @Serializable data class BowlMix(val output: ItemKind) : Task {
        override val kind get() = MiniGameKind.STIR
    }

    /** Zutat kommt in die Pfanne, mit Minispiel beim Hineingeben. */
    @Serializable data class PanStart(val stove: Int, val item: Item, override val kind: MiniGameKind) : Task

    @Serializable data class PanFlip(val stove: Int) : Task {
        override val kind get() = MiniGameKind.FLIP
    }

    @Serializable data class Plating(val plate: Int, val dishId: String, val remaining: List<MiniGameKind>) : Task {
        override val kind get() = remaining.first()
    }
}

/** Ergebnis eines servierten Auftrags (für Einblendung und Statistik). */
data class ServeResult(val dish: Dish, val quality: Float, val points: Int, val stars: Int, val combo: Int)

data class LevelDef(
    val id: String,
    val restaurant: Restaurant,
    val number: Int,
    val dishes: List<String>,
    val duration: Float,
    val spawnInterval: Float,
    val orderTime: Float,
    val maxOrders: Int,
    val difficulty: Float,
    /** Punkte-Ziele für 1/2/3 Sterne (per Simulation abgestimmt: guter Spieler ≈ 2 Sterne, perfekt = 3). */
    val starTargets: List<Int>,
) {
    val title: String get() = "${restaurant.title} ${number}"

    fun stars(score: Int): Int = starTargets.count { score >= it }
}

enum class Restaurant(val title: String, val available: Boolean) {
    CAFE("Frühstücks-Café", true),
    PIZZERIA("Pizzeria", false),
    DINER("Burger-Diner", false),
}
