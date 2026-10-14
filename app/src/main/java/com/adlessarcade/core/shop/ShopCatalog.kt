package com.adlessarcade.core.shop

enum class ShopCategory(val title: String) {
    BLOCK_SKIN("Block Break"),
    HERO_SKIN("Gate Rush"),
    SURVIVOR_CLASS("Survivors-Klassen"),
    SURVIVOR_THEME("Survivors-Welten"),
    APP_THEME("Themes"),
    AVATAR("Avatare"),
    TITLE("Titel"),
}

/**
 * Ein Shop-Artikel. Die Optik (Farben, Stile) wird in `ui/theme/Cosmetics.kt` über die [id] zugeordnet.
 * [symbol] ist das Emoji bei Avataren.
 */
data class ShopItem(
    val id: String,
    val category: ShopCategory,
    val name: String,
    val price: Int,
    val symbol: String? = null,
)

object ShopCatalog {
    val items: List<ShopItem> = listOf(
        // Block Break
        ShopItem("block_classic", ShopCategory.BLOCK_SKIN, "Klassik", 0),
        ShopItem("block_pastel", ShopCategory.BLOCK_SKIN, "Pastell", 150),
        ShopItem("block_neon", ShopCategory.BLOCK_SKIN, "Neon", 250),
        ShopItem("block_ice", ShopCategory.BLOCK_SKIN, "Eis", 300),
        ShopItem("block_gem", ShopCategory.BLOCK_SKIN, "Edelstein", 450),
        ShopItem("block_gold", ShopCategory.BLOCK_SKIN, "Gold", 600),
        // Gate Rush
        ShopItem("hero_default", ShopCategory.HERO_SKIN, "Standard", 0),
        ShopItem("hero_ninja", ShopCategory.HERO_SKIN, "Ninja", 200),
        ShopItem("hero_toxic", ShopCategory.HERO_SKIN, "Toxic", 250),
        ShopItem("hero_plasma", ShopCategory.HERO_SKIN, "Plasma", 300),
        ShopItem("hero_gold", ShopCategory.HERO_SKIN, "Gold", 500),
        ShopItem("hero_rainbow", ShopCategory.HERO_SKIN, "Regenbogen", 700),
        // Rune Survivors – Klassen (IDs entsprechen HeroClass.shopId)
        ShopItem("class_knight", ShopCategory.SURVIVOR_CLASS, "Krieger", 0),
        ShopItem("class_mage", ShopCategory.SURVIVOR_CLASS, "Magierin", 400),
        ShopItem("class_ranger", ShopCategory.SURVIVOR_CLASS, "Waldläuferin", 500),
        ShopItem("class_necro", ShopCategory.SURVIVOR_CLASS, "Nekromant", 600),
        // Rune Survivors – Welten
        ShopItem("world_meadow", ShopCategory.SURVIVOR_THEME, "Fantasy-Wiese", 0),
        ShopItem("world_frost", ShopCategory.SURVIVOR_THEME, "Frostreich", 300),
        ShopItem("world_volcano", ShopCategory.SURVIVOR_THEME, "Vulkan", 300),
        ShopItem("world_graveyard", ShopCategory.SURVIVOR_THEME, "Spukfriedhof", 400),
        ShopItem("world_neon", ShopCategory.SURVIVOR_THEME, "Neon-Dungeon", 500),
        // App-Themes
        ShopItem("theme_neon", ShopCategory.APP_THEME, "Neon", 0),
        ShopItem("theme_ocean", ShopCategory.APP_THEME, "Ozean", 300),
        ShopItem("theme_sunset", ShopCategory.APP_THEME, "Sunset", 300),
        ShopItem("theme_matrix", ShopCategory.APP_THEME, "Matrix", 400),
        ShopItem("theme_gold", ShopCategory.APP_THEME, "Royal Gold", 500),
        // Avatare
        ShopItem("avatar_gamepad", ShopCategory.AVATAR, "Gamepad", 0, symbol = "🎮"),
        ShopItem("avatar_fox", ShopCategory.AVATAR, "Fuchs", 60, symbol = "🦊"),
        ShopItem("avatar_cat", ShopCategory.AVATAR, "Katze", 60, symbol = "🐱"),
        ShopItem("avatar_robot", ShopCategory.AVATAR, "Roboter", 100, symbol = "🤖"),
        ShopItem("avatar_alien", ShopCategory.AVATAR, "Alien", 100, symbol = "👾"),
        ShopItem("avatar_dragon", ShopCategory.AVATAR, "Drache", 150, symbol = "🐉"),
        ShopItem("avatar_rocket", ShopCategory.AVATAR, "Rakete", 150, symbol = "🚀"),
        // Titel
        ShopItem("title_rookie", ShopCategory.TITLE, "Neuling", 0),
        ShopItem("title_blockmaster", ShopCategory.TITLE, "Blockmeister", 150),
        ShopItem("title_gatekeeper", ShopCategory.TITLE, "Torwächter", 150),
        ShopItem("title_comboking", ShopCategory.TITLE, "Combo-König", 250),
        ShopItem("title_collector", ShopCategory.TITLE, "Coin-Sammler", 300),
        ShopItem("title_legend", ShopCategory.TITLE, "Arcade-Legende", 500),
    )

    private val byId = items.associateBy { it.id }

    fun find(id: String?): ShopItem? = id?.let(byId::get)

    fun inCategory(category: ShopCategory): List<ShopItem> = items.filter { it.category == category }

    /** Der kostenlose Standard-Artikel einer Kategorie. */
    fun default(category: ShopCategory): ShopItem = items.first { it.category == category && it.price == 0 }
}

object CoinRewards {
    /** Bonus am Rundenende, wenn ein neuer Rekord aufgestellt wurde (alle Spiele). */
    const val NEW_RECORD_BONUS = 25
}

/** Coins und freigeschaltete Artikel des Spielers. */
data class Wallet(val coins: Int, val owned: Set<String>)

object ShopLogic {
    fun isOwned(owned: Set<String>, item: ShopItem): Boolean = item.price == 0 || item.id in owned

    /** Neuer Wallet-Zustand nach dem Kauf, oder null wenn bereits besessen bzw. nicht genug Coins. */
    fun buy(wallet: Wallet, item: ShopItem): Wallet? {
        if (isOwned(wallet.owned, item) || wallet.coins < item.price) return null
        return Wallet(wallet.coins - item.price, wallet.owned + item.id)
    }

    /** Ausgerüsteter Artikel einer Kategorie; fällt auf den Standard zurück, wenn ungültig oder nicht besessen. */
    fun equipped(category: ShopCategory, equippedIds: Map<ShopCategory, String>, owned: Set<String>): ShopItem =
        ShopCatalog.find(equippedIds[category])
            ?.takeIf { it.category == category && isOwned(owned, it) }
            ?: ShopCatalog.default(category)
}
