package com.adlessarcade.games.kitchen

import com.adlessarcade.core.SaveJson
import kotlinx.serialization.Serializable

@Serializable
data class KitchenSnapshot(
    val levelId: String?,
    val time: Float,
    val score: Int,
    val strikes: Int,
    val combo: Int,
    val served: Int,
    val missed: Int,
    val streak: Int,
    val bestStreak: Int,
    val coinsEarned: Int,
    val threeStarOrders: Int,
    val orders: List<Order>,
    val board: Item?,
    val bowl: List<Item>,
    val bowlProduct: Item?,
    val pans: List<Pan>,
    val plates: List<Plate>,
    /** Laufender Schritt; das Minispiel startet nach dem Laden neu. */
    val task: Task?,
    val nextOrderId: Int,
    val spawnTimer: Float,
    val rngCounter: Long,
)

@Serializable
data class KitchenSave(val version: Int, val snapshot: KitchenSnapshot, val bestAtStart: Int = 0, val seed: Long = 0)

object KitchenSaves {
    const val VERSION = 1

    fun encode(snapshot: KitchenSnapshot, bestAtStart: Int, seed: Long): String =
        SaveJson.encodeToString(KitchenSave.serializer(), KitchenSave(VERSION, snapshot, bestAtStart, seed))

    /** Liefert null bei defekten oder inkompatiblen Spielständen. */
    fun decode(json: String): KitchenSave? {
        val save = runCatching { SaveJson.decodeFromString(KitchenSave.serializer(), json) }.getOrNull() ?: return null
        if (save.version != VERSION) return null
        val levelOk = save.snapshot.levelId == null || KitchenCatalog.findLevel(save.snapshot.levelId) != null
        return if (levelOk) save else null
    }
}

/** Wie man eine Komponente herstellt, als kurze Kette für die Rezeptkarte (z. B. „Brot → Brett → Pfanne (wenden)“). */
object KitchenRecipesHelp {
    fun steps(kind: ItemKind): String {
        KitchenCatalog.panRules.firstOrNull { it.output == kind }?.let { rule ->
            val pan = "Pfanne" + if (rule.flip) " (wenden)" else ""
            return "${source(rule.input)} → $pan"
        }
        return source(kind)
    }

    /** Herkunft einer Zutat oder eines Zwischenprodukts ohne den letzten Garschritt. */
    private fun source(kind: ItemKind): String {
        KitchenCatalog.boardRules.firstOrNull { it.output == kind }?.let { return "${it.input.title} → Brett" }
        KitchenCatalog.bowlRecipes.firstOrNull { it.output == kind }?.let { r ->
            val parts = r.ingredients.entries.joinToString(" + ") { (k, n) -> if (n > 1) "$n× ${k.title}" else k.title }
            return "$parts → Schüssel (antippen zum Rühren)"
        }
        return kind.title
    }
}
