package com.adlessarcade.games.survivors

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SurvivorsItemsTest {

    private fun engine(heroClass: HeroClass = HeroClass.KNIGHT) =
        SurvivorsEngine.newGame(heroClass, SurvivorsMode.TIMED, seed = 7).apply {
            state.enemies.clear()
            state.spawner.spawnBudget = -1_000f
            state.spawner.eliteTimer = 10_000f
            state.spawner.swarmTimer = 10_000f
            state.spawner.nextBossTime = 10_000f
            state.invulnerable = 1_000f
        }

    private fun SurvivorsEngine.run(seconds: Float): List<SurvivorsEvent> {
        val events = ArrayList<SurvivorsEvent>()
        var t = 0f
        while (t < seconds && !isOver && !isWaiting) {
            update(1f / 60f)
            events += drainEvents()
            t += 1f / 60f
        }
        return events
    }

    /** Stapel, bis [item] mindestens [value] erreicht. */
    private fun stacksFor(item: ItemKind, value: Float): Int {
        var n = 0
        while (item.total(n) < value) n++
        return n
    }

    /** Krieger mit Schwert gegen einen zähen Ork; liefert die Treffer-Events. */
    private fun swordHits(e: SurvivorsEngine, seconds: Float = 2f): List<SurvivorsEvent.Damage> {
        val orc = e.spawnEnemy(EnemyKind.ORC, 1.2f, 0f, hpMultiplier = 10_000f)
        val events = ArrayList<SurvivorsEvent.Damage>()
        var t = 0f
        while (t < seconds) {
            e.update(1f / 60f)
            orc.x = 1.2f
            orc.y = 0f
            events += e.drainEvents().filterIsInstance<SurvivorsEvent.Damage>()
            t += 1f / 60f
        }
        return events
    }

    @Test
    fun stacking_hasDiminishingButUnlimitedReturns() {
        ItemKind.entries.forEach { item ->
            assertEquals(item.base, item.total(1), 0.0001f)
            var previousGain = Float.MAX_VALUE
            for (n in 1..30) {
                val gain = item.total(n) - item.total(n - 1)
                assertTrue("$item wächst", gain > 0f)
                assertTrue("$item Ertrag sinkt", gain <= previousGain)
                previousGain = gain
            }
            // unbegrenzt: viele Stapel übertreffen jede kleine Grenze
            assertTrue(item.total(400) > item.base * 10f)
        }
    }

    @Test
    fun noCritItems_meansNoCrits() {
        val hits = swordHits(engine())
        assertTrue(hits.isNotEmpty())
        assertTrue(hits.all { it.crit == 0 })
    }

    @Test
    fun hundredPercentCrit_alwaysCrits_andAboveGivesSuperCrits() {
        val e = engine()
        e.state.items[ItemKind.CRIT_CHANCE] = stacksFor(ItemKind.CRIT_CHANCE, 1f)
        val hits = swordHits(e)
        assertTrue(hits.isNotEmpty())
        assertTrue(hits.all { it.crit >= 1 })

        val f = engine()
        f.state.items[ItemKind.CRIT_CHANCE] = stacksFor(ItemKind.CRIT_CHANCE, 2f)
        assertEquals(1f, f.stats.superCritChance, 0.0001f)
        val superHits = swordHits(f)
        assertTrue(superHits.all { it.crit == 2 })
        // Superkrit = Krit-Multiplikator × 2
        val normal = swordHits(engine()).first().amount
        assertEquals(normal * HeroStats.BASE_CRIT * HeroStats.SUPER_CRIT_FACTOR, superHits.first().amount, normal * 0.01f)
    }

    @Test
    fun poison_ticks_andKillCountsForWeapon() {
        val e = engine()
        e.state.items[ItemKind.POISON] = 3
        val orc = e.spawnEnemy(EnemyKind.ORC, 1.2f, 0f, hpMultiplier = 1_000f)
        while (orc.poisonTimer <= 0f) e.update(1f / 60f)
        e.state.weapons.clear() // ab jetzt schadet nur noch das Gift
        orc.x = 30f // außer Reichweite
        val hp = orc.hp
        e.run(0.5f)
        assertTrue(orc.hp < hp)
        orc.hp = 0.01f
        e.run(0.2f)
        assertFalse(orc in e.state.enemies)
        assertEquals(1, e.state.weaponKills[WeaponKind.SWORD])
    }

    @Test
    fun execute_killsWeakenedEnemies_butNotBosses() {
        val e = engine()
        e.state.items[ItemKind.EXECUTE] = 50 // gedeckelt
        assertEquals(ItemKind.MAX_EXECUTE, e.stats.execute, 0.0001f)
        val orc = e.spawnEnemy(EnemyKind.ORC, 1.2f, 0f, hpMultiplier = 100f)
        orc.hp = orc.maxHp * 0.25f
        e.run(1f)
        assertFalse(orc in e.state.enemies)

        val f = engine()
        f.state.items[ItemKind.EXECUTE] = 50
        val boss = f.spawnEnemy(EnemyKind.BOSS, 1.2f, 0f, hpMultiplier = 10f)
        boss.hp = boss.maxHp * 0.25f
        f.run(0.5f)
        assertTrue(boss in f.state.enemies)
    }

    @Test
    fun caps_forDodgeAndCooldown() {
        val e = engine()
        e.state.items[ItemKind.DODGE] = 500
        e.state.items[ItemKind.HOURGLASS] = 500
        assertEquals(ItemKind.MAX_DODGE, e.stats.dodge, 0.0001f)
        assertTrue(e.stats.cooldownMultiplier >= 1f - ItemKind.MAX_COOLDOWN_REDUCTION - 0.0001f)
    }

    @Test
    fun dodge_avoidsContactDamage_sometimes() {
        val e = engine()
        e.state.invulnerable = 0f
        e.state.items[ItemKind.DODGE] = 500
        e.state.weapons.clear()
        e.state.hp = 10_000f
        val slime = e.spawnEnemy(EnemyKind.SLIME, 0.3f, 0f, hpMultiplier = 1_000f)
        val events = ArrayList<SurvivorsEvent>()
        repeat(60 * 12) {
            e.update(1f / 60f)
            slime.x = e.state.px + 0.3f
            slime.y = e.state.py
            events += e.drainEvents()
        }
        val dodged = events.count { it is SurvivorsEvent.Dodged }
        val hits = events.count { it is SurvivorsEvent.PlayerHit }
        assertTrue(dodged > 0 && hits > 0)
        assertTrue(dodged > hits) // 60 % Ausweichen
    }

    @Test
    fun goldenGoose_givesMoreXp() {
        val e = engine()
        e.state.items[ItemKind.GOLD] = 10
        val bonus = e.stats.lootBonus
        assertTrue(bonus > 0.4f)
        e.state.gems += Gem(e.state.px + 0.2f, e.state.py, 5) // bleibt unter dem ersten Aufstieg
        e.run(0.3f)
        assertEquals((5 * (1f + bonus)).toInt(), e.state.xp)
        assertTrue(e.state.xp > 5)
    }

    @Test
    fun amulet_raisesMaxHp_andHeals() {
        val e = engine()
        val before = e.stats.maxHp
        val hp = e.state.hp
        Upgrades.apply(e.state, UpgradeOffer(item = ItemKind.AMULET))
        assertTrue(e.stats.maxHp > before)
        assertTrue(e.state.hp > hp)
    }

    // --- Klassen-Passiven ---

    @Test
    fun knight_ironWill_armorAndLowHpGiveDamage() {
        val e = engine(HeroClass.KNIGHT)
        val base = e.stats.damageMultiplier
        assertEquals(1f + HeroStats.KNIGHT_DAMAGE_PER_ARMOR * HeroClass.KNIGHT.armor, base, 0.0001f)
        e.state.passives[PassiveKind.ARMOR] = 3
        val armored = e.stats.damageMultiplier
        assertTrue(armored > base)
        e.state.hp = e.stats.maxHp * 0.4f
        assertEquals(armored + HeroStats.KNIGHT_LOW_HP_BONUS, e.stats.damageMultiplier, 0.0001f)
        // andere Klassen haben das nicht
        assertEquals(1f, engine(HeroClass.MAGE).stats.damageMultiplier, 0.0001f)
    }

    @Test
    fun mage_elementalist_longerPoison_andStrongerProcs() {
        val mage = engine(HeroClass.MAGE)
        val knight = engine(HeroClass.KNIGHT)
        listOf(mage, knight).forEach { it.state.items[ItemKind.SPARK] = 1; it.state.items[ItemKind.FROST] = 1 }
        assertEquals(HeroStats.POISON_TIME * 1.5f, mage.stats.poisonTime, 0.0001f)
        assertEquals(HeroStats.POISON_TIME, knight.stats.poisonTime, 0.0001f)
        assertEquals(knight.stats.sparkChance * 1.5f, mage.stats.sparkChance, 0.0001f)
        assertEquals(knight.stats.freezeChance * 1.5f, mage.stats.freezeChance, 0.0001f)
    }

    @Test
    fun mage_dealsMoreDamage_toStatusAffectedEnemies() {
        fun hitOn(frozen: Boolean): Float {
            val e = engine(HeroClass.MAGE)
            e.state.weapons.clear()
            e.state.weapons[WeaponKind.FROST_NOVA] = 1 // trifft alle im Umkreis
            val orc = e.spawnEnemy(EnemyKind.ORC, 1.2f, 0f, hpMultiplier = 1_000f)
            orc.stunTimer = if (frozen) 10f else 0f
            var t = 0f
            while (t < 3f) {
                e.update(1f / 60f)
                e.drainEvents().filterIsInstance<SurvivorsEvent.Damage>().firstOrNull()?.let { return it.amount }
                t += 1f / 60f
            }
            error("kein Treffer")
        }
        assertEquals(hitOn(false) * (1f + HeroStats.MAGE_STATUS_BONUS), hitOn(true), 0.01f)
    }

    @Test
    fun ranger_huntersInstinct_baseCrit() {
        assertEquals(HeroStats.RANGER_CRIT, engine(HeroClass.RANGER).stats.critChance, 0.0001f)
        assertEquals(0f, engine(HeroClass.KNIGHT).stats.critChance, 0.0001f)
    }

    @Test
    fun necromancer_soulHarvest_capsSouls_andSpreadsPoison() {
        val e = engine(HeroClass.NECROMANCER)
        e.state.weapons.clear()
        e.state.souls = HeroStats.NECRO_MAX_SOULS - 1
        val base = e.stats.damageMultiplier
        val dying = e.spawnEnemy(EnemyKind.ORC, 8f, 0f)
        dying.hp = 0.001f
        dying.poisonDps = 5f
        dying.poisonTimer = 2f
        dying.poisonSource = WeaponKind.BONE_ORBIT
        val neighbor = e.spawnEnemy(EnemyKind.ORC, 8.8f, 0f, hpMultiplier = 100f)
        e.update(1f / 60f)
        assertFalse(dying in e.state.enemies)
        assertEquals(HeroStats.NECRO_MAX_SOULS, e.state.souls)
        assertTrue(neighbor.poisonTimer > 0f)
        assertEquals(WeaponKind.BONE_ORBIT, neighbor.poisonSource)
        assertTrue(e.stats.damageMultiplier > base)

        val again = e.spawnEnemy(EnemyKind.SLIME, -8f, 0f)
        again.hp = 0f
        e.update(1f / 60f)
        assertEquals(HeroStats.NECRO_MAX_SOULS, e.state.souls)
    }

    @Test
    fun itemOffers_showNextStackAndSynergy() {
        val e = engine(HeroClass.RANGER)
        val offer = UpgradeOffer(item = ItemKind.CRIT_CHANCE)
        assertEquals("ITEM · ×1", Upgrades.levelLabel(e.state, offer))
        val first = Upgrades.description(e.state, offer)
        e.state.items[ItemKind.CRIT_CHANCE] = 4
        assertEquals("ITEM · ×5", Upgrades.levelLabel(e.state, offer))
        assertTrue(first != Upgrades.description(e.state, offer))
        assertTrue(Upgrades.synergy(e.state, offer)!!.contains(HeroClass.RANGER.passiveTitle))
    }
}
