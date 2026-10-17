package com.adlessarcade.games.survivors.progress

import com.adlessarcade.core.shop.ShopCatalog
import com.adlessarcade.games.survivors.HeroClass
import com.adlessarcade.games.survivors.WeaponKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SurvivorsUnlocksTest {

    private val mage = SurvivorsUnlocks.forClass(HeroClass.MAGE)

    @Test
    fun everyClass_hasAnUnlock_andStarterClassesAreFree() {
        HeroClass.entries.forEach { SurvivorsUnlocks.forClass(it) }
        val free = setOf(HeroClass.KNIGHT, HeroClass.PALADIN, HeroClass.ASSASSIN, HeroClass.STORMCALLER, HeroClass.PIRATE)
        free.forEach { assertTrue(UnlockLogic.isUnlocked(SurvivorsProgress(), it)) }
        HeroClass.entries.filter { it !in free }.forEach {
            assertFalse(UnlockLogic.isUnlocked(SurvivorsProgress(), it))
        }
    }

    @Test
    fun buy_deductsRunes_andUnlocks() {
        val after = UnlockLogic.buy(SurvivorsProgress(runes = 100), mage)!!
        assertEquals(100 - mage.runePrice, after.runes)
        assertTrue(UnlockLogic.isUnlocked(after, HeroClass.MAGE))
        assertNull(UnlockLogic.buy(after, mage)) // nicht doppelt
    }

    @Test
    fun buy_failsWithTooFewRunes() {
        assertNull(UnlockLogic.buy(SurvivorsProgress(runes = mage.runePrice - 1), mage))
    }

    @Test
    fun requirements_blockPurchase_untilMet() {
        val gated = SurvivorsUnlock(
            "test_stage", UnlockKind.STAGE, "Test", runePrice = 10,
            requirements = listOf(StatAtLeast(SurvivorsStats.DEMON_DEFEATED, 1, "Dämonenfürst besiegt")),
        )
        assertNull(UnlockLogic.buy(SurvivorsProgress(runes = 50), gated))
        val stats = UnlockLogic.afterRun(emptyMap(), victory = true, bossesKilled = 2, seconds = 930)
        val after = UnlockLogic.buy(SurvivorsProgress(runes = 50, stats = stats), gated)!!
        assertEquals(40, after.runes)
    }

    @Test
    fun weapons_needRunesAndPartnerKills_gravityOnlyRunes() {
        val hammer = SurvivorsUnlocks.find(WeaponKind.HAMMER.unlockId)!!
        assertNull(UnlockLogic.buy(SurvivorsProgress(runes = 1_000), hammer)) // Kills fehlen
        val killsOk = SurvivorsProgress(
            runes = 1_000,
            stats = mapOf(SurvivorsStats.weaponKills(WeaponKind.SWORD) to SurvivorsUnlocks.PARTNER_KILLS),
        )
        val after = UnlockLogic.buy(killsOk, hammer)!!
        assertEquals(WeaponKind.BASE + WeaponKind.HAMMER, UnlockLogic.availableWeapons(after))

        val gravity = SurvivorsUnlocks.find(WeaponKind.GRAVITY.unlockId)!!
        assertTrue(gravity.requirements.isEmpty())
        assertNotNull(UnlockLogic.buy(SurvivorsProgress(runes = gravity.runePrice), gravity))

        // Jede neue Waffe ist in der Schmiede, jede Grundwaffe nicht
        WeaponKind.entries.forEach { w -> assertEquals(!w.isBase, SurvivorsUnlocks.find(w.unlockId) != null) }
    }

    @Test
    fun afterRun_countsStats() {
        var stats = UnlockLogic.afterRun(emptyMap(), victory = false, bossesKilled = 1, seconds = 400)
        stats = UnlockLogic.afterRun(stats, victory = true, bossesKilled = 2, seconds = 300)
        assertEquals(2, stats[SurvivorsStats.RUNS])
        assertEquals(1, stats[SurvivorsStats.DEMON_DEFEATED])
        assertEquals(3, stats[SurvivorsStats.BOSSES_KILLED])
        assertEquals(400, stats[SurvivorsStats.BEST_TIME])
    }

    @Test
    fun selectedClass_fallsBackToKnight_whenLocked() {
        assertEquals(HeroClass.KNIGHT, UnlockLogic.selectedClass(SurvivorsProgress(selectedClass = "class_mage")))
        val owned = SurvivorsProgress(unlocked = setOf("class_mage"), selectedClass = "class_mage")
        assertEquals(HeroClass.MAGE, UnlockLogic.selectedClass(owned))
    }

    @Test
    fun legacyMigration_keepsClasses_andRefundsWorlds() {
        val owned = setOf("class_mage", "class_necro", "world_frost", "world_neon", "block_neon")
        val m = SurvivorsLegacy.migrate(owned, equippedClass = "class_necro")
        assertEquals(setOf("class_mage", "class_necro"), m.unlockedClasses)
        assertEquals("class_necro", m.selectedClass)
        assertEquals(300 + 500, m.refundCoins)
        // Eine nicht (mehr) besessene Klasse wird nicht übernommen
        assertNull(SurvivorsLegacy.migrate(setOf("world_frost"), equippedClass = "class_mage").selectedClass)
    }

    @Test
    fun generalShop_hasNoSurvivorsItemsAnymore() {
        SurvivorsLegacy.LEGACY_IDS.forEach { assertNull(it, ShopCatalog.find(it)) }
    }
}
