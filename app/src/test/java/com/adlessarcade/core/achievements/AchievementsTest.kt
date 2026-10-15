package com.adlessarcade.core.achievements

import com.adlessarcade.core.GameRegistry
import com.adlessarcade.core.shop.ShopCatalog
import com.adlessarcade.core.shop.ShopCategory
import com.adlessarcade.core.shop.ShopLogic
import com.adlessarcade.core.shop.Wallet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AchievementsTest {

    @Test
    fun everyAchievement_hasAnExclusiveTitle() {
        val ids = Achievements.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        Achievements.all.forEach { a ->
            val title = ShopCatalog.find(a.titleId)
            assertNotNull("Titel für ${a.id}", title)
            assertEquals(ShopCategory.TITLE, title!!.category)
            assertEquals(a.id, title.achievementId)
            assertFalse(title.purchasable)
        }
    }

    @Test
    fun achievementTitles_cannotBeBought_andAreNotOwnedByDefault() {
        val title = ShopCatalog.find(Achievements.all.first().titleId)!!
        assertFalse(ShopLogic.isOwned(emptySet(), title))
        assertNull(ShopLogic.buy(Wallet(coins = 100_000, owned = emptySet()), title))
        assertTrue(ShopLogic.isOwned(setOf(title.id), title))
        // Nicht besessen → kann auch nicht ausgerüstet werden
        val default = ShopCatalog.default(ShopCategory.TITLE)
        assertEquals(default, ShopLogic.equipped(ShopCategory.TITLE, mapOf(ShopCategory.TITLE to title.id), emptySet()))
    }

    @Test
    fun achievementGames_exist() {
        Achievements.gameIds.forEach { assertNotNull(GameRegistry.find(it)) }
        assertEquals(GameRegistry.games.map { it.id }.toSet(), Achievements.gameIds)
    }

    @Test
    fun allRounder_needsOneAchievementPerGame() {
        assertTrue(Achievements.derived(setOf(Achievements.BB_FULL_CLEAR, Achievements.GR_BOSS)).isEmpty())
        val all = setOf(Achievements.BB_FULL_CLEAR, Achievements.GR_BOSS, Achievements.SV_EVOLUTION)
        assertEquals(listOf(Achievements.ALL_ROUNDER), Achievements.derived(all).map { it.id })
        assertTrue(Achievements.derived(all + Achievements.ALL_ROUNDER).isEmpty())
    }
}
