package com.adlessarcade.core.shop

import com.adlessarcade.ui.theme.CosmeticRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShopLogicTest {

    private val skin = ShopCatalog.find("block_neon")!!
    private val free = ShopCatalog.default(ShopCategory.BLOCK_SKIN)

    @Test
    fun buy_withEnoughCoins_deductsAndUnlocks() {
        val result = ShopLogic.buy(Wallet(coins = 1000, owned = emptySet()), skin)!!
        assertEquals(1000 - skin.price, result.coins)
        assertTrue(skin.id in result.owned)
    }

    @Test
    fun buy_withTooFewCoins_fails() {
        assertNull(ShopLogic.buy(Wallet(coins = skin.price - 1, owned = emptySet()), skin))
    }

    @Test
    fun buy_alreadyOwnedOrFree_fails() {
        assertNull(ShopLogic.buy(Wallet(coins = 1000, owned = setOf(skin.id)), skin))
        assertNull(ShopLogic.buy(Wallet(coins = 1000, owned = emptySet()), free))
        assertTrue(ShopLogic.isOwned(emptySet(), free))
    }

    @Test
    fun equipped_fallsBackToDefaultWhenNotOwnedOrInvalid() {
        val notOwned = mapOf(ShopCategory.BLOCK_SKIN to skin.id)
        assertEquals(free, ShopLogic.equipped(ShopCategory.BLOCK_SKIN, notOwned, emptySet()))
        assertEquals(skin, ShopLogic.equipped(ShopCategory.BLOCK_SKIN, notOwned, setOf(skin.id)))
        val wrongCategory = mapOf(ShopCategory.BLOCK_SKIN to "hero_ninja")
        assertEquals(free, ShopLogic.equipped(ShopCategory.BLOCK_SKIN, wrongCategory, setOf("hero_ninja")))
        val unknown = mapOf(ShopCategory.BLOCK_SKIN to "gibt_es_nicht")
        assertEquals(free, ShopLogic.equipped(ShopCategory.BLOCK_SKIN, unknown, emptySet()))
    }

    @Test
    fun catalog_hasUniqueIdsAndExactlyOneFreeItemPerCategory() {
        val ids = ShopCatalog.items.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        ShopCategory.entries.forEach { category ->
            assertEquals(1, ShopCatalog.inCategory(category).count { it.isFree })
        }
    }

    @Test
    fun everyCatalogItem_hasVisuals() {
        assertEquals(emptyList<String>(), CosmeticRegistry.missingVisuals())
    }
}
