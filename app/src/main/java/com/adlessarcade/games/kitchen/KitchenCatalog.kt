package com.adlessarcade.games.kitchen

import com.adlessarcade.games.kitchen.ItemKind.APPLE
import com.adlessarcade.games.kitchen.ItemKind.APPLE_CUT
import com.adlessarcade.games.kitchen.ItemKind.BANANA
import com.adlessarcade.games.kitchen.ItemKind.BANANA_CUT
import com.adlessarcade.games.kitchen.ItemKind.BATTER
import com.adlessarcade.games.kitchen.ItemKind.BREAD
import com.adlessarcade.games.kitchen.ItemKind.BREAD_SLICES
import com.adlessarcade.games.kitchen.ItemKind.CHEESE
import com.adlessarcade.games.kitchen.ItemKind.CHEESE_GRATED
import com.adlessarcade.games.kitchen.ItemKind.EGG
import com.adlessarcade.games.kitchen.ItemKind.EGG_MIX
import com.adlessarcade.games.kitchen.ItemKind.FLOUR
import com.adlessarcade.games.kitchen.ItemKind.FRIED_EGG
import com.adlessarcade.games.kitchen.ItemKind.MILK
import com.adlessarcade.games.kitchen.ItemKind.OMELETTE
import com.adlessarcade.games.kitchen.ItemKind.PANCAKE
import com.adlessarcade.games.kitchen.ItemKind.STRAWBERRY
import com.adlessarcade.games.kitchen.ItemKind.STRAWBERRY_CUT
import com.adlessarcade.games.kitchen.ItemKind.TOAST
import com.adlessarcade.games.kitchen.MiniGameKind.CHOP
import com.adlessarcade.games.kitchen.MiniGameKind.CRACK
import com.adlessarcade.games.kitchen.MiniGameKind.GRATE
import com.adlessarcade.games.kitchen.MiniGameKind.HEAT
import com.adlessarcade.games.kitchen.MiniGameKind.PEEL
import com.adlessarcade.games.kitchen.MiniGameKind.PLATE
import com.adlessarcade.games.kitchen.MiniGameKind.POUR
import com.adlessarcade.games.kitchen.MiniGameKind.SEASON

/** Brett: aus einer rohen Zutat wird über eine Folge von Minispielen eine vorbereitete. */
data class BoardRule(val input: ItemKind, val steps: List<MiniGameKind>, val output: ItemKind)

/** Pfanne: was hineinkommt, welches Minispiel beim Hineingeben läuft, Garzeit, ob gewendet werden muss. */
data class PanRule(
    val input: ItemKind,
    val output: ItemKind,
    val cookTime: Float,
    val onStart: MiniGameKind? = null,
    val flip: Boolean = false,
)

/** Schüssel: Zutaten (mit Anzahl), die zu einem Produkt verrührt werden. */
data class BowlRecipe(val ingredients: Map<ItemKind, Int>, val output: ItemKind)

object KitchenCatalog {
    /** Ab hier wird ein Gericht in der Pfanne zu dunkel, bei [BURN_AT] ist es verbrannt (relativ zur Garzeit). */
    const val OVERCOOK_FROM = 1.35f
    const val BURN_AT = 1.8f
    /** Ab diesem Garfortschritt kann gewendet werden. */
    const val FLIP_FROM = 0.4f
    const val STOVES = 2
    const val PLATES = 2
    const val BOWL_CAPACITY = 4

    val crates: List<ItemKind> = listOf(EGG, MILK, FLOUR, BREAD, CHEESE, STRAWBERRY, BANANA, APPLE)

    val boardRules: List<BoardRule> = listOf(
        BoardRule(BREAD, listOf(CHOP), BREAD_SLICES),
        BoardRule(CHEESE, listOf(GRATE), CHEESE_GRATED),
        BoardRule(STRAWBERRY, listOf(CHOP), STRAWBERRY_CUT),
        BoardRule(BANANA, listOf(PEEL, CHOP), BANANA_CUT),
        BoardRule(APPLE, listOf(PEEL, CHOP), APPLE_CUT),
    )

    /** Minispiel beim Hineingeben in die Schüssel. */
    val bowlAdd: Map<ItemKind, MiniGameKind> = mapOf(EGG to CRACK, MILK to POUR, FLOUR to POUR)

    val bowlRecipes: List<BowlRecipe> = listOf(
        BowlRecipe(mapOf(EGG to 1, MILK to 1, FLOUR to 1), BATTER),
        BowlRecipe(mapOf(EGG to 1), EGG_MIX),
    )

    val panRules: List<PanRule> = listOf(
        PanRule(EGG, FRIED_EGG, cookTime = 6f, onStart = CRACK),
        PanRule(BREAD_SLICES, TOAST, cookTime = 5f, flip = true),
        PanRule(EGG_MIX, OMELETTE, cookTime = 7f, onStart = HEAT),
        PanRule(BATTER, PANCAKE, cookTime = 8f, onStart = POUR, flip = true),
    )

    val dishes: List<Dish> = listOf(
        Dish("fried_egg", "Spiegelei", listOf(FRIED_EGG), listOf(SEASON), basePoints = 40),
        Dish("toast", "Toast", listOf(TOAST), listOf(PLATE), basePoints = 50),
        Dish("fruit_salad", "Obstsalat", listOf(STRAWBERRY_CUT, BANANA_CUT, APPLE_CUT), listOf(PLATE), basePoints = 90),
        Dish("omelette", "Käse-Omelett", listOf(OMELETTE, CHEESE_GRATED), listOf(SEASON), basePoints = 100),
        Dish("pancake", "Pfannkuchen", listOf(PANCAKE, STRAWBERRY_CUT), listOf(PLATE), basePoints = 120),
    )

    val levels: List<LevelDef> = listOf(
        LevelDef("cafe_1", Restaurant.CAFE, 1, listOf("fried_egg", "toast"), 150f, 24f, 75f, 2, 0.05f, listOf(300, 600, 950)),
        LevelDef("cafe_2", Restaurant.CAFE, 2, listOf("fried_egg", "toast", "fruit_salad"), 165f, 22f, 80f, 3, 0.2f, listOf(300, 550, 850)),
        LevelDef("cafe_3", Restaurant.CAFE, 3, listOf("toast", "fruit_salad", "omelette"), 180f, 22f, 85f, 3, 0.35f, listOf(300, 550, 850)),
        LevelDef("cafe_4", Restaurant.CAFE, 4, listOf("fried_egg", "omelette", "pancake"), 180f, 20f, 70f, 3, 0.5f, listOf(320, 600, 900)),
        LevelDef("cafe_5", Restaurant.CAFE, 5, dishes.map { it.id }, 180f, 16f, 60f, 4, 0.7f, listOf(350, 650, 950)),
    )

    private val dishById = dishes.associateBy { it.id }
    private val levelById = levels.associateBy { it.id }

    fun dish(id: String): Dish = dishById.getValue(id)
    fun findDish(id: String): Dish? = dishById[id]
    fun level(id: String): LevelDef = levelById.getValue(id)
    fun findLevel(id: String): LevelDef? = levelById[id]

    fun boardRule(kind: ItemKind): BoardRule? = boardRules.firstOrNull { it.input == kind }
    fun panRule(kind: ItemKind): PanRule? = panRules.firstOrNull { it.input == kind }

    /** Rezept, das genau zum Schüsselinhalt passt. */
    fun bowlRecipe(contents: List<ItemKind>): BowlRecipe? {
        val counts = contents.groupingBy { it }.eachCount()
        return bowlRecipes.firstOrNull { it.ingredients == counts }
    }

    /** Kann die Zutat noch in die Schüssel, ohne dass kein Rezept mehr passt? */
    fun bowlAccepts(contents: List<ItemKind>, add: ItemKind): Boolean {
        if (add !in bowlAdd || contents.size >= BOWL_CAPACITY) return false
        val counts = (contents + add).groupingBy { it }.eachCount()
        return bowlRecipes.any { r -> counts.all { (k, n) -> (r.ingredients[k] ?: 0) >= n } }
    }

    /** Gerichte aus [pool], zu denen die Teller-Komponenten (noch) passen. */
    fun dishesFor(components: List<ItemKind>, pool: Collection<String>): List<Dish> =
        pool.map(::dish).filter { d ->
            val need = d.components.groupingBy { it }.eachCount()
            components.groupingBy { it }.eachCount().all { (k, n) -> (need[k] ?: 0) >= n }
        }

    /** Gericht, dessen Komponenten genau auf dem Teller liegen. */
    fun completedDish(components: List<ItemKind>, pool: Collection<String>): Dish? =
        dishesFor(components, pool).firstOrNull { it.components.sorted() == components.sorted() }
}
