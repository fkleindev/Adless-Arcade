package com.adlessarcade.games.survivors

import com.adlessarcade.core.SaveJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SurvivorsEngineTest {

    /** Engine ohne automatischen Spawner-Einfluss: Zeit bleibt klein, Gegner setzen wir selbst. */
    private fun engine(heroClass: HeroClass = HeroClass.KNIGHT, mode: SurvivorsMode = SurvivorsMode.TIMED) =
        SurvivorsEngine.newGame(heroClass, mode, seed = 3)

    /** Nur wenige Schritte laufen lassen – vor dem ersten Spawn-Schub. */
    private fun SurvivorsEngine.run(seconds: Float, dt: Float = 1f / 60f) {
        var t = 0f
        while (t < seconds && !isOver && !isWaiting) {
            update(dt)
            t += dt
        }
    }

    private fun SurvivorsEngine.clearSpawns() {
        state.enemies.clear()
        state.spawner.spawnBudget = -1_000f // keine normalen Spawns
        state.spawner.eliteTimer = 10_000f
        state.spawner.swarmTimer = 10_000f
        state.spawner.nextBossTime = 10_000f
    }

    /** Abstand eines Testgegners, bei dem die Waffe sicher trifft. */
    private fun testDistance(weapon: WeaponKind, orbitRadius: Float): Float = when (weapon) {
        WeaponKind.BONE_ORBIT -> orbitRadius
        WeaponKind.AURA, WeaponKind.FROST_NOVA, WeaponKind.SWORD, WeaponKind.HAMMER, WeaponKind.THORNS -> 1.2f
        WeaponKind.WIND -> -0.4f // Klingen bleiben hinter dem Helden liegen
        else -> 3f
    }

    @Test
    fun everyEvolutionTalent_coversOneBaseAndOneForgeWeapon() {
        PassiveKind.entries.forEach { passive ->
            val weapons = WeaponKind.entries.filter { it.evolutionPassive == passive }
            assertEquals("$passive", 2, weapons.size)
            assertEquals("$passive", 1, weapons.count { it.isBase })
        }
        // Partnerwaffe = Grundwaffe mit gleichem Talent
        assertEquals(WeaponKind.SWORD, WeaponKind.HAMMER.partner)
        assertEquals(WeaponKind.ANCHOR, WeaponKind.GRAVITY.partner)
        assertTrue(WeaponKind.ANCHOR in WeaponKind.BASE)
    }

    @Test
    fun anchor_pullsFarthestEnemy_andRangeGrowsWithMagnet() {
        val e = engine(HeroClass.KNIGHT)
        e.clearSpawns()
        e.state.weapons.clear()
        e.state.weapons[WeaponKind.ANCHOR] = 1
        e.state.invulnerable = 1_000f
        val near = e.spawnEnemy(EnemyKind.ORC, 1.5f, 0f, hpMultiplier = 100f)
        val far = e.spawnEnemy(EnemyKind.ORC, 3.5f, 0.1f, hpMultiplier = 100f)
        e.update(1f / 60f)
        // Kette trifft beide (der nahe liegt auf der Linie), der ferne wird herangezogen
        assertTrue(far.hp < far.maxHp)
        assertTrue(near.hp < near.maxHp)
        assertTrue(far.x < 3f)
        assertTrue(e.drainEvents().any { it is SurvivorsEvent.Anchor })

        // Magnet vergrößert die Reichweite
        val baseRange = e.anchorRange()
        e.state.passives[PassiveKind.MAGNET] = 3
        assertEquals(baseRange * (1f + 0.3f * 3), e.anchorRange(), 0.001f)

        // Außerhalb der Reichweite: kein Wurf
        val f = engine(HeroClass.KNIGHT)
        f.clearSpawns()
        f.state.weapons.clear()
        f.state.weapons[WeaponKind.ANCHOR] = 1
        val distant = f.spawnEnemy(EnemyKind.ORC, f.anchorRange() + 2f, 0f, hpMultiplier = 100f)
        f.update(1f / 60f)
        assertEquals(distant.maxHp, distant.hp, 0.001f)
    }

    @Test
    fun leviathan_dragsGroupAndStunsOnImpact() {
        val e = engine(HeroClass.KNIGHT)
        e.clearSpawns()
        e.state.weapons.clear()
        e.state.weapons[WeaponKind.ANCHOR] = MAX_LEVEL
        e.state.evolved += WeaponKind.ANCHOR
        e.state.invulnerable = 1_000f
        val target = e.spawnEnemy(EnemyKind.ORC, 3.8f, 0f, hpMultiplier = 100f)
        val buddy = e.spawnEnemy(EnemyKind.ORC, 3.6f, 0.9f, hpMultiplier = 100f)
        e.update(1f / 60f)
        assertTrue(buddy.x < 3.6f)
        assertTrue(target.stunTimer > 0f || buddy.stunTimer > 0f)
    }

    @Test
    fun lockedWeapons_areNotOffered() {
        val e = SurvivorsEngine.newGame(HeroClass.KNIGHT, SurvivorsMode.TIMED, seed = 1, availableWeapons = WeaponKind.BASE)
        repeat(30) {
            val pool = Upgrades.pool(e.state)
            assertTrue(pool.none { it.weapon != null && !it.weapon.isBase })
            e.addXp(SurvivorsEngine.xpForLevel(e.state.level))
            while (e.awaitingChoice) e.choose(0)
        }
        val f = SurvivorsEngine.newGame(HeroClass.KNIGHT, SurvivorsMode.TIMED, seed = 1,
            availableWeapons = WeaponKind.BASE + WeaponKind.HAMMER)
        assertTrue(Upgrades.pool(f.state).any { it.weapon == WeaponKind.HAMMER })
        assertTrue(Upgrades.pool(f.state).none { it.weapon == WeaponKind.SPEAR })
    }

    @Test
    fun kills_areCountedPerWeapon() {
        val e = engine(HeroClass.KNIGHT)
        e.clearSpawns()
        e.state.invulnerable = 100f
        repeat(3) { e.spawnEnemy(EnemyKind.SLIME, 1f, 0f).hp = 0.1f }
        e.run(1.5f)
        assertEquals(3, e.state.weaponKills[WeaponKind.SWORD])
    }

    @Test
    fun damage_isCreditedPerWeapon_withoutOverkill() {
        val e = engine(HeroClass.KNIGHT)
        e.clearSpawns()
        e.state.invulnerable = 100f
        e.spawnEnemy(EnemyKind.SLIME, 1f, 0f).hp = 2f
        e.run(1.5f)
        assertEquals(2f, e.state.weaponDamage[WeaponKind.SWORD]!!, 0.001f)
        assertTrue(e.weaponDps(WeaponKind.SWORD) > 0f)
    }

    @Test
    fun poison_dealsCreditedDamage_andShowsNumbers() {
        val e = engine(HeroClass.KNIGHT)
        e.clearSpawns()
        e.state.weapons.clear()
        e.state.invulnerable = 100f
        val orc = e.spawnEnemy(EnemyKind.ORC, 5f, 0f, hpMultiplier = 100f)
        orc.poisonDps = 10f
        orc.poisonTimer = 2f
        orc.poisonSource = WeaponKind.DAGGERS
        val numbers = ArrayList<SurvivorsEvent.Damage>()
        var t = 0f
        while (t < 2.2f) {
            e.update(1f / 60f)
            numbers += e.drainEvents().filterIsInstance<SurvivorsEvent.Damage>()
            t += 1f / 60f
        }
        assertEquals(20f, e.state.effectDamage[EffectDamage.POISON]!!, 0.5f)
        assertEquals(null, e.state.weaponDamage[WeaponKind.DAGGERS])
        assertTrue(numbers.size >= 3)
        assertTrue(numbers.all { it.kind == DamageKind.POISON })
        assertEquals(20f, numbers.sumOf { it.amount.toDouble() }.toFloat(), 0.5f)
    }

    @Test
    fun maxFourPassives() {
        val e = engine()
        PassiveKind.entries.take(MAX_PASSIVES).forEach { e.state.passives[it] = 1 }
        assertTrue(Upgrades.pool(e.state).none { it.passive != null && it.passive !in e.state.passives })
        assertEquals(4, MAX_PASSIVES)
    }

    @Test
    fun newGame_startsWithClassWeapon() {
        HeroClass.entries.forEach { c ->
            val e = engine(c)
            assertEquals(mapOf(c.startWeapon to 1), e.state.weapons.toMap())
            assertEquals(c.maxHp, e.state.hp, 0.001f)
        }
    }

    @Test
    fun joystick_movesHero() {
        val e = engine()
        e.clearSpawns()
        e.inputX = 1f
        e.run(1f)
        assertEquals(HeroClass.KNIGHT.speed, e.state.px, 0.15f)
        assertEquals(0f, e.state.py, 0.001f)
        e.inputX = 0f
        e.inputY = -0.5f
        e.run(1f)
        assertEquals(-HeroClass.KNIGHT.speed * 0.5f, e.state.py, 0.15f)
    }

    @Test
    fun contactDamage_isReducedByArmor_andHasInvulnerability() {
        val e = engine(HeroClass.KNIGHT) // Rüstung 1
        e.clearSpawns()
        e.state.weapons.clear() // keine Waffen, damit der Gegner überlebt
        e.spawnEnemy(EnemyKind.SKELETON, 0.2f, 0f)
        e.run(0.1f)
        val expected = EnemyKind.SKELETON.damage * (1f + e.state.time / 600f) - 1f
        assertEquals(HeroClass.KNIGHT.maxHp - expected, e.state.hp, 0.05f)
        // Während der Unverwundbarkeit kein weiterer Schaden
        val hpAfter = e.state.hp
        e.run(0.3f)
        assertEquals(hpAfter, e.state.hp, 0.001f)
    }

    @Test
    fun death_endsRun() {
        val e = engine(HeroClass.MAGE)
        e.clearSpawns()
        e.state.weapons.clear()
        e.state.hp = 1f
        e.spawnEnemy(EnemyKind.ORC, 0.2f, 0f)
        e.run(0.5f)
        assertTrue(e.state.dead)
        assertTrue(e.isOver)
    }

    @Test
    fun xp_triggersLevelUp_withThreeValidOffers() {
        val e = engine()
        e.addXp(SurvivorsEngine.xpForLevel(1))
        assertEquals(2, e.state.level)
        assertTrue(e.awaitingChoice)
        assertEquals(3, e.state.offers.size)
        assertEquals(3, e.state.offers.toSet().size)
        val pool = Upgrades.pool(e.state)
        assertTrue(e.state.offers.all { it in pool })

        // Während der Wahl steht das Spiel still
        val time = e.state.time
        e.update(1f)
        assertEquals(time, e.state.time, 0.001f)

        val offer = e.state.offers[0]
        e.choose(0)
        assertFalse(e.awaitingChoice)
        if (offer.weapon != null) assertTrue(offer.weapon in e.state.weapons)
        if (offer.passive != null) assertEquals(1, e.state.passives[offer.passive])
    }

    @Test
    fun multipleLevelUps_offerAgainAfterChoice() {
        val e = engine()
        e.addXp(SurvivorsEngine.xpForLevel(1) + SurvivorsEngine.xpForLevel(2))
        assertEquals(2, e.state.pendingLevelUps)
        e.choose(0)
        assertTrue(e.awaitingChoice)
        e.choose(0)
        assertFalse(e.awaitingChoice)
    }

    @Test
    fun gems_areCollected_andGiveXp() {
        val e = engine()
        e.clearSpawns()
        e.state.gems += Gem(1f, 0f, 3)
        e.run(0.5f)
        assertTrue(e.state.gems.isEmpty())
        assertEquals(3, e.state.xp)
    }

    @Test
    fun upgrades_respectLimits() {
        val e = engine()
        WeaponKind.entries.forEach { e.state.weapons[it] = MAX_LEVEL }
        PassiveKind.entries.take(MAX_PASSIVES).forEach { e.state.passives[it] = MAX_LEVEL }
        assertTrue(Upgrades.pool(e.state).isEmpty())
        assertEquals(listOf(UpgradeOffer(heal = true)), Upgrades.offers(e.state, Random(1)))

        val f = engine()
        WeaponKind.entries.take(MAX_WEAPONS).forEach { f.state.weapons[it] = 1 }
        assertTrue(Upgrades.pool(f.state).none { it.weapon != null && it.weapon !in f.state.weapons })
    }

    @Test
    fun passives_changeStats() {
        val e = engine()
        val before = e.stats
        PassiveKind.entries.forEach { e.state.passives[it] = 2 }
        val after = e.stats
        assertTrue(after.maxHp > before.maxHp)
        assertTrue(after.damageMultiplier > before.damageMultiplier)
        assertTrue(after.speed > before.speed)
        assertTrue(after.cooldownMultiplier < before.cooldownMultiplier)
        assertTrue(after.areaMultiplier > before.areaMultiplier)
        assertTrue(after.pickupRadius > before.pickupRadius)
        assertEquals(before.armor + 2, after.armor)
        assertTrue(after.regen > 0f)
        assertEquals(2, after.extraProjectiles)
    }

    @Test
    fun weaponStats_growWithLevel() {
        WeaponKind.entries.forEach { w ->
            assertTrue("${w.name} Schaden", w.damage(5) > w.damage(1))
            assertTrue("${w.name} Abklingzeit", w.cooldown(5) <= w.cooldown(1))
            assertTrue("${w.name} Anzahl", w.count(5) >= w.count(1))
        }
    }

    /** Jede Waffe tötet einen schwachen Gegner in passender Position innerhalb kurzer Zeit. */
    @Test
    fun everyWeapon_hitsEnemies() {
        WeaponKind.entries.forEach { weapon ->
            listOf(1, MAX_LEVEL).forEach { level ->
                val e = engine()
                e.clearSpawns()
                e.state.weapons.clear()
                e.state.weapons[weapon] = level
                e.state.faceX = 1f
                e.state.faceY = 0f
                val distance = testDistance(weapon, weapon.area(level))
                val target = e.spawnEnemy(EnemyKind.SLIME, distance, 0f)
                if (weapon == WeaponKind.WIND) e.inputX = 1f // Windklingen entstehen nur in Bewegung
                target.hp = 1f
                e.state.invulnerable = 100f // der Held soll in diesem Test nicht sterben
                var t = 0f
                while (t < 3f && target in e.state.enemies) {
                    e.update(1f / 60f)
                    // Gegner festhalten, damit die Geometrie stimmt
                    target.x = if (weapon == WeaponKind.WIND) e.state.px - 0.4f else distance
                    target.y = 0f
                    t += 1f / 60f
                }
                assertFalse("$weapon Stufe $level trifft nicht", target in e.state.enemies)
            }
        }
    }

    @Test
    fun eliteDropsChest_whichGivesUpgrades() {
        val e = engine()
        e.clearSpawns()
        val elite = e.spawnEnemy(EnemyKind.SLIME, 1f, 0f, elite = true)
        elite.hp = 0.1f
        e.state.invulnerable = 100f
        e.run(1.5f)
        val chest = e.state.pickups.firstOrNull { it.kind == PickupKind.CHEST }
        assertNotNull(chest)
        val levelsBefore = e.buildSize()
        e.state.px = chest!!.x
        e.state.py = chest.y
        e.run(0.1f)
        assertTrue(e.awaitingChest)
        assertTrue(e.drainEvents().any { it is SurvivorsEvent.ChestOpened })
        while (e.awaitingChest) e.chooseChestReward(0)
        assertTrue(e.buildSize() > levelsBefore)
    }

    private fun SurvivorsEngine.buildSize(): Int =
        state.weapons.values.sum() + state.passives.values.sum() + state.items.values.sum() + state.evolved.size

    /** Öffnet eine Kiste direkt beim Helden. */
    private fun SurvivorsEngine.openChestHere() {
        spawnPickup(PickupKind.CHEST, state.px, state.py)
        update(1f / 60f)
    }

    @Test
    fun chest_pausesGame_untilRewardIsHandled() {
        val e = engine()
        e.clearSpawns()
        e.openChestHere()
        assertTrue(e.awaitingChest)
        assertTrue(e.isWaiting)
        val time = e.state.time
        e.update(1f)
        assertEquals(time, e.state.time, 0.001f)
        while (e.awaitingChest) e.chooseChestReward(0)
        e.update(0.1f)
        assertTrue(e.state.time > time)
    }

    @Test
    fun chest_offersThreeDistinctRewards_andAppliesTheChosenOne() {
        repeat(20) { seed ->
            val e = SurvivorsEngine.newGame(HeroClass.KNIGHT, SurvivorsMode.TIMED, seed.toLong())
            e.clearSpawns()
            e.openChestHere()
            val offers = e.state.chestOffers
            assertEquals(3, offers.size)
            assertEquals(3, offers.distinct().size)
            assertTrue(offers.none { it.heal })
            val pick = offers[1]
            e.chooseChestReward(1)
            assertFalse(e.awaitingChest)
            when {
                pick.item != null -> assertEquals(1, e.state.items[pick.item])
                pick.weapon != null -> assertTrue((e.state.weapons[pick.weapon] ?: 0) >= 1)
                pick.passive != null -> assertEquals(1, e.state.passives[pick.passive])
            }
        }
    }

    @Test
    fun chest_invalidIndex_isIgnored() {
        val e = engine()
        e.clearSpawns()
        e.openChestHere()
        e.chooseChestReward(5)
        assertTrue(e.awaitingChest)
    }

    @Test
    fun chests_atTheSameTime_openOneAfterAnother() {
        val e = engine()
        e.clearSpawns()
        repeat(3) { e.spawnPickup(PickupKind.CHEST, e.state.px, e.state.py) }
        e.update(1f / 60f)
        assertEquals(2, e.state.pendingChests)
        var opened = e.drainEvents().count { it is SurvivorsEvent.ChestOpened }
        var picks = 0
        while (e.awaitingChest) {
            e.chooseChestReward(0)
            picks++
            opened += e.drainEvents().count { it is SurvivorsEvent.ChestOpened }
        }
        assertEquals(3, picks)
        assertEquals(3, opened)
    }

    @Test
    fun chest_hasPriorityOverLevelUp() {
        val e = engine()
        e.clearSpawns()
        e.openChestHere()
        e.addXp(SurvivorsEngine.xpForLevel(1))
        assertTrue(e.awaitingChest)
        assertFalse(e.awaitingChoice) // Karten warten, bis die Kiste erledigt ist
        val levelBefore = e.state.weapons.toMap()
        e.choose(0) // wird ignoriert
        assertEquals(levelBefore, e.state.weapons.toMap())
        while (e.awaitingChest) e.chooseChestReward(0)
        assertTrue(e.awaitingChoice)
    }

    @Test
    fun evolution_needsMaxLevelAndPassive() {
        val e = engine(HeroClass.KNIGHT)
        assertTrue(Upgrades.evolutionsReady(e.state).isEmpty())
        e.state.weapons[WeaponKind.SWORD] = MAX_LEVEL
        assertTrue(Upgrades.evolutionsReady(e.state).isEmpty())
        e.state.passives[PassiveKind.MIGHT] = 1
        assertEquals(listOf(WeaponKind.SWORD), Upgrades.evolutionsReady(e.state))
        e.state.evolved += WeaponKind.SWORD
        assertTrue(Upgrades.evolutionsReady(e.state).isEmpty())
    }

    @Test
    fun chest_guaranteesReadyEvolution() {
        val e = engine(HeroClass.MAGE)
        e.clearSpawns()
        e.state.weapons[WeaponKind.FIREBALL] = MAX_LEVEL
        e.state.passives[PassiveKind.WISDOM] = 1
        e.state.time = SurvivorsEngine.EVOLUTION_MIN_TIME
        e.openChestHere()
        val index = e.state.chestOffers.indexOf(UpgradeOffer(evolution = WeaponKind.FIREBALL))
        assertTrue(index >= 0)
        e.drainEvents()
        e.chooseChestReward(index)
        assertTrue(e.isEvolved(WeaponKind.FIREBALL))
        assertTrue(e.drainEvents().contains(SurvivorsEvent.Evolved(WeaponKind.FIREBALL)))
        assertEquals(MAX_LEVEL, e.state.weapons[WeaponKind.FIREBALL])
    }

    @Test
    fun evolution_onlyAfterFirstBoss_andOncePerChest() {
        val e = engine(HeroClass.KNIGHT)
        e.clearSpawns()
        e.state.weapons[WeaponKind.SWORD] = MAX_LEVEL
        e.state.passives[PassiveKind.MIGHT] = 1
        e.state.weapons[WeaponKind.FROST_NOVA] = MAX_LEVEL
        e.state.passives[PassiveKind.ARMOR] = 1
        // Vor Minute 5: keine Entwicklung
        e.openChestHere()
        assertTrue(e.state.chestOffers.none { it.evolution != null })
        e.chooseChestReward(0)
        // Danach: genau eine Entwicklung pro Kiste, obwohl zwei bereit sind
        e.state.time = SurvivorsEngine.EVOLUTION_MIN_TIME
        e.openChestHere()
        assertEquals(1, e.state.chestOffers.count { it.evolution != null })
    }

    /** Jede entwickelte Waffe tötet einen schwachen Gegner. */
    @Test
    fun everyEvolution_hitsEnemies() {
        WeaponKind.entries.forEach { weapon ->
            val e = engine()
            e.clearSpawns()
            e.state.weapons.clear()
            e.state.weapons[weapon] = MAX_LEVEL
            e.state.passives[weapon.evolutionPassive] = 1
            e.state.evolved += weapon
            val distance = testDistance(weapon, weapon.area(MAX_LEVEL) * e.stats.areaMultiplier)
            val target = e.spawnEnemy(EnemyKind.ORC, distance, 0f)
            if (weapon == WeaponKind.WIND) e.inputX = 1f
            e.state.invulnerable = 100f
            var t = 0f
            while (t < 5f && target in e.state.enemies) {
                e.update(1f / 60f)
                target.x = if (weapon == WeaponKind.WIND) e.state.px - 0.4f else distance
                target.y = 0f
                t += 1f / 60f
            }
            assertFalse("$weapon (entwickelt) trifft nicht", target in e.state.enemies)
        }
    }

    @Test
    fun evolution_specialEffects() {
        // Eiszeit friert ein, eingefrorene Gegner bewegen sich nicht
        val e = engine()
        e.clearSpawns()
        e.state.weapons.clear()
        e.state.weapons[WeaponKind.FROST_NOVA] = MAX_LEVEL
        e.state.evolved += WeaponKind.FROST_NOVA
        val orc = e.spawnEnemy(EnemyKind.ORC, 2f, 0f, hpMultiplier = 100f)
        e.update(1f / 60f)
        assertTrue(orc.freezeTimer > 0f)
        val x = orc.x
        e.update(0.5f)
        assertEquals(x, orc.x, 0.001f)

        // Meteorregen hinterlässt brennenden Boden
        val m = engine(HeroClass.MAGE)
        m.clearSpawns()
        m.state.weapons[WeaponKind.FIREBALL] = MAX_LEVEL
        m.state.evolved += WeaponKind.FIREBALL
        m.spawnEnemy(EnemyKind.ORC, 3f, 0f, hpMultiplier = 100f)
        m.state.invulnerable = 100f
        var t = 0f
        while (t < 1.5f && m.state.hazards.isEmpty()) {
            m.update(1f / 60f)
            t += 1f / 60f
        }
        assertTrue(m.state.hazards.isNotEmpty())
    }

    @Test
    fun passives_affectAllWeapons() {
        // Weisheit: Orbit dreht schneller
        fun orbitAngleAfter(wisdom: Int): Float {
            val e = engine(HeroClass.NECROMANCER)
            e.clearSpawns()
            if (wisdom > 0) e.state.passives[PassiveKind.WISDOM] = wisdom
            e.update(0.5f)
            return e.state.orbitAngle
        }
        assertTrue(orbitAngleAfter(3) > orbitAngleAfter(0))

        // Reichweite: größere Pfeile und Dolche
        val e = engine(HeroClass.RANGER)
        e.clearSpawns()
        e.state.weapons[WeaponKind.DAGGERS] = 1
        e.state.passives[PassiveKind.REACH] = 5
        e.spawnEnemy(EnemyKind.ORC, 6f, 0f, hpMultiplier = 100f)
        e.update(1f / 60f)
        assertTrue(e.state.projectiles.isNotEmpty())
        val daggers = e.state.projectiles.filter { it.weapon == WeaponKind.DAGGERS }
        assertTrue(daggers.isNotEmpty())
        assertTrue(daggers.all { it.radius > 0.18f * 1.4f && it.life > 0.9f * 1.4f })

        // Vielfalt: Folgeschläge beim Schwert und Echos bei der Frostnova
        val k = engine(HeroClass.KNIGHT)
        k.clearSpawns()
        k.state.weapons[WeaponKind.FROST_NOVA] = 1
        k.state.passives[PassiveKind.MULTI] = 2
        k.spawnEnemy(EnemyKind.ORC, 1f, 0f, hpMultiplier = 100f)
        k.state.invulnerable = 100f
        k.update(1f / 60f)
        assertEquals(4, k.state.pendingStrikes.size)
        var swings = 0
        var novas = 0
        k.drainEvents()
        repeat(60) {
            k.update(1f / 60f)
            k.drainEvents().forEach { ev ->
                if (ev is SurvivorsEvent.Swing) swings++
                if (ev is SurvivorsEvent.Nova) novas++
            }
        }
        assertTrue("Folgeschläge: $swings", swings >= 2)
        assertTrue("Echos: $novas", novas >= 2)
    }

    @Test
    fun timedMode_spawnsFinalBoss_andVictoryOnKill() {
        val e = engine(mode = SurvivorsMode.TIMED)
        e.clearSpawns()
        e.state.time = 899.9f
        e.state.invulnerable = 1_000f
        e.update(0.2f)
        val boss = e.state.enemies.firstOrNull { it.kind == EnemyKind.DEMON }
        assertNotNull(boss)
        assertTrue(e.drainEvents().contains(SurvivorsEvent.BossSpawned(EnemyKind.DEMON)))
        boss!!.hp = 0.1f
        boss.x = 1f
        boss.y = 0f
        e.state.weapons[WeaponKind.AURA] = MAX_LEVEL
        e.run(1f)
        assertTrue(e.state.victory)
        assertTrue(e.score >= SurvivorsEngine.VICTORY_BONUS_SCORE)
    }

    @Test
    fun coins_areAttractedByPickupRadius_andMagnet() {
        val e = engine()
        e.clearSpawns()
        // Im Sammelradius (1,5), aber außerhalb der direkten Aufnahme → wird angezogen
        e.spawnPickup(PickupKind.COIN, 1.3f, 0f)
        e.run(0.5f)
        assertEquals(1, e.state.coinsEarned)

        // Weit entfernte Coins holt erst der Magnet
        val far = e.spawnPickup(PickupKind.COIN, 12f, 0f)
        e.run(0.3f)
        assertTrue(far in e.state.pickups)
        e.spawnPickup(PickupKind.MAGNET, e.state.px, e.state.py)
        e.run(3f)
        assertFalse(far in e.state.pickups)
        assertEquals(2, e.state.coinsEarned)
    }

    @Test
    fun victory_collectsAllDrops() {
        val e = engine(mode = SurvivorsMode.TIMED)
        e.clearSpawns()
        e.state.time = 899.9f
        e.state.invulnerable = 1_000f
        e.update(0.2f)
        val boss = e.state.enemies.first { it.kind == EnemyKind.DEMON }
        // Liegengebliebene Drops weit verstreut
        e.state.gems += Gem(15f, 4f, 5)
        e.state.gems += Gem(-9f, -12f, 5)
        e.spawnPickup(PickupKind.COIN, 20f, 0f)
        e.spawnPickup(PickupKind.CHEST, -14f, 6f)
        e.spawnEnemy(EnemyKind.ORC, 8f, 8f, hpMultiplier = 100f)
        boss.hp = 0.1f
        boss.x = 1f
        boss.y = 0f
        e.state.weapons[WeaponKind.AURA] = MAX_LEVEL
        val coinsBefore = e.state.coinsEarned
        e.update(1f / 60f)
        e.update(1f / 60f)
        assertTrue("Siegesrunde läuft", e.state.victorySweep)
        assertFalse(e.isOver)
        assertTrue(e.state.enemies.isEmpty())
        e.run(4f)
        assertTrue(e.state.victory)
        assertTrue(e.state.gems.isEmpty())
        assertTrue(e.state.pickups.isEmpty())
        assertFalse(e.awaitingChest) // Truhe wird nach dem Sieg zu Coins
        assertEquals(
            coinsBefore + SurvivorsEngine.VICTORY_COINS + 1 + SurvivorsEngine.ELITE_COINS,
            e.state.coinsEarned,
        )
        assertTrue(e.state.offers.isEmpty()) // keine Level-up-Karten nach dem Sieg
        assertTrue(e.state.runesEarned >= SurvivorsEngine.VICTORY_RUNES)
    }

    @Test
    fun runes_forMinutesElitesBossesAndVictory() {
        val e = engine()
        e.clearSpawns()
        e.state.invulnerable = 1_000f
        e.state.minuteTimer = 59.99f
        e.update(1f / 30f)
        assertEquals(SurvivorsEngine.MINUTE_RUNES, e.state.runesEarned)

        e.state.weapons[WeaponKind.AURA] = MAX_LEVEL // trifft rundherum
        e.spawnEnemy(EnemyKind.SLIME, 1f, 0f, elite = true).hp = 0.1f
        e.spawnEnemy(EnemyKind.BOSS, -1f, 0f).hp = 0.1f
        e.run(0.3f)
        // Elite und Boss lassen Runen-Kristalle fallen, die erst beim Einsammeln zählen (die Boss-Rune liegt
        // direkt beim Helden und ist schon eingesammelt, die Elite-Rune liegt außerhalb des Sammelradius).
        assertTrue(e.state.pickups.any { it.kind == PickupKind.RUNE && it.amount == SurvivorsEngine.ELITE_RUNES })
        assertTrue(e.state.runesEarned < SurvivorsEngine.MINUTE_RUNES + SurvivorsEngine.ELITE_RUNES + SurvivorsEngine.BOSS_RUNES)
        e.state.pickups.forEach { if (it.kind == PickupKind.RUNE) it.magnetized = true }
        repeat(120) {
            if (e.awaitingChest) e.chooseChestReward(0)
            if (e.awaitingChoice) e.choose(0)
            e.update(1f / 60f)
        }
        assertEquals(SurvivorsEngine.MINUTE_RUNES + SurvivorsEngine.ELITE_RUNES + SurvivorsEngine.BOSS_RUNES, e.state.runesEarned)
        assertEquals(1, e.state.bossesKilled)
        assertEquals(1, e.state.elitesKilled)
        assertTrue(e.drainEvents().any { it is SurvivorsEvent.RunesEarned })
    }

    @Test
    fun runeStone_chargesNextToHero_andGivesRunes() {
        val e = engine()
        e.clearSpawns()
        e.state.invulnerable = 1_000f
        e.state.spawner.runeStoneTimer = 0.01f
        e.run(0.1f)
        val stone = e.state.runeStone!!
        e.state.px = stone.x
        e.state.py = stone.y
        e.run(SurvivorsEngine.RUNE_STONE_CHARGE_TIME - 0.5f)
        assertNotNull(e.state.runeStone) // noch nicht voll
        e.run(1f)
        assertEquals(null, e.state.runeStone)
        assertEquals(1, e.state.runeStonesActivated)
        assertEquals(SurvivorsEngine.RUNE_STONE_RUNES, e.state.runesEarned)
    }

    @Test
    fun runeStone_expiresWhenIgnored() {
        val e = engine()
        e.clearSpawns()
        e.state.invulnerable = 1_000f
        e.state.spawner.runeStoneTimer = 0.01f
        e.run(SurvivorsEngine.RUNE_STONE_LIFE + 1f)
        assertEquals(0, e.state.runeStonesActivated)
        assertEquals(0, e.state.runesEarned - e.state.time.toInt() / 60 * SurvivorsEngine.MINUTE_RUNES)
    }

    @Test
    fun runGoal_isRewardedOnce() {
        val e = engine()
        e.clearSpawns()
        e.state.invulnerable = 1_000f
        e.state.goals = listOf(RunGoal.ELITES)
        e.state.elitesKilled = RunGoal.ELITES.target
        e.run(0.1f)
        e.run(0.1f)
        assertEquals(setOf(RunGoal.ELITES), e.state.goalsDone)
        assertEquals(RunGoal.ELITES.runes, e.state.runesEarned)
        assertEquals(RunGoal.PER_RUN, engine().state.goals.distinct().size)
    }

    @Test
    fun sparkDamage_isCreditedSeparately() {
        val e = engine(HeroClass.KNIGHT)
        e.clearSpawns()
        e.state.invulnerable = 100f
        e.state.items[ItemKind.SPARK] = 100 // springt (fast) immer über
        e.spawnEnemy(EnemyKind.ORC, 1.2f, 0f, hpMultiplier = 1_000f)
        e.spawnEnemy(EnemyKind.ORC, 1.6f, 0.4f, hpMultiplier = 1_000f)
        e.run(1.5f)
        assertTrue((e.state.effectDamage[EffectDamage.SPARK] ?: 0f) > 0f)
        assertTrue((e.state.weaponDamage[WeaponKind.SWORD] ?: 0f) > 0f)
    }

    @Test
    fun endlessMode_hasNoFinalBoss() {
        val e = engine(mode = SurvivorsMode.ENDLESS)
        e.clearSpawns()
        e.state.time = 905f
        e.state.invulnerable = 1_000f
        e.update(0.2f)
        assertTrue(e.state.enemies.none { it.kind == EnemyKind.DEMON })
        assertFalse(e.state.victory)
    }

    @Test
    fun difficulty_increasesOverTime() {
        val e = engine()
        val early = e.hpMultiplier()
        e.state.time = 600f
        assertTrue(e.hpMultiplier() > early * 5f)
    }

    @Test
    fun gridQuery_matchesNaiveQuery() {
        val e = engine()
        e.clearSpawns()
        val rnd = Random(5)
        repeat(300) { e.spawnEnemy(EnemyKind.entries.random(rnd), rnd.nextFloat() * 30f - 15f, rnd.nextFloat() * 30f - 15f) }
        repeat(50) {
            val x = rnd.nextFloat() * 30f - 15f
            val y = rnd.nextFloat() * 30f - 15f
            val r = rnd.nextFloat() * 4f
            assertEquals(e.enemiesNearNaive(x, y, r).map { it.id }.toSet(), e.enemiesNear(x, y, r).map { it.id }.toSet())
        }
    }

    @Test
    fun save_roundTrip_restoresState() {
        val e = engine(HeroClass.NECROMANCER)
        e.state.invulnerable = 1_000f
        e.inputX = 0.7f
        var t = 0f
        while (t < 30f) {
            if (e.awaitingChest) e.chooseChestReward(0)
            if (e.awaitingChoice) e.choose(0)
            e.update(1f / 30f)
            t += 1f / 30f
        }
        val json = SurvivorsSaves.encode(e.state, bestAtStart = 12)
        val decoded = SurvivorsSaves.decode(json)
        assertNotNull(decoded)
        assertEquals(12, decoded!!.bestAtStart)
        assertEquals(json, SurvivorsSaves.encode(decoded.state, 12))

        val restored = SurvivorsEngine(decoded.state, seed = 99)
        assertEquals(e.score, restored.score)
        val before = restored.state.time
        restored.state.invulnerable = 1_000f
        if (restored.awaitingChoice) restored.choose(0)
        restored.update(1f)
        assertTrue(restored.state.time > before)
    }

    @Test
    fun save_roundTrip_withOpenChestAndEvolutions() {
        val e = engine(HeroClass.MAGE)
        e.clearSpawns()
        e.state.weapons[WeaponKind.FIREBALL] = MAX_LEVEL
        e.state.evolved += WeaponKind.FIREBALL
        e.state.weapons[WeaponKind.SWORD] = MAX_LEVEL
        e.state.passives[PassiveKind.MIGHT] = 1
        e.state.time = SurvivorsEngine.EVOLUTION_MIN_TIME
        e.spawnEnemy(EnemyKind.ORC, 3f, 0f, hpMultiplier = 100f)
        e.state.invulnerable = 100f
        e.update(1f / 60f) // Meteor in der Warteschlange
        e.openChestHere()
        assertTrue(e.awaitingChest)
        assertTrue(e.state.pendingStrikes.isNotEmpty())

        val json = SurvivorsSaves.encode(e.state, bestAtStart = 0)
        val restored = SurvivorsEngine(SurvivorsSaves.decode(json)!!.state, seed = 1)
        assertEquals(json, SurvivorsSaves.encode(restored.state, 0))
        assertTrue(restored.awaitingChest)
        assertTrue(UpgradeOffer(evolution = WeaponKind.SWORD) in restored.state.chestOffers)
        assertTrue(restored.isEvolved(WeaponKind.FIREBALL))

        // Spielstände der Version 1 (ohne neue Felder) laden weiterhin
        val v1 = SaveJson.encodeToString(SurvivorsSave.serializer(), SurvivorsSave(1, engine().state))
            .replace(Regex(""","(evolved|items|chestOffers|pendingChests|souls|coinRemainder|xpRemainder|pendingStrikes|hazards|orbitAngle|leechPool)":(\[\]|\{\}|null|false|[0-9.]+)"""), "")
        assertFalse(v1.contains("evolved"))
        assertFalse(v1.contains("items"))
        assertNotNull(SurvivorsSaves.decode(v1))

        // Version 2 mit offener (alter) Einzel-Belohnung: lädt, die Kiste verfällt
        val v2 = SaveJson.encodeToString(SurvivorsSave.serializer(), SurvivorsSave(2, engine().state))
            .replace(""""chestOffers":[]""", """"chestReward":{"weapon":"SWORD"},"chestIndex":0,"chestTotal":2""")
        val loaded = SurvivorsSaves.decode(v2)
        assertNotNull(loaded)
        assertFalse(SurvivorsEngine(loaded!!.state, 1).awaitingChest)
    }

    @Test
    fun cryomancer_startsWithFrostNova_andHitsSlowedEnemiesHarder() {
        val base = engine(HeroClass.CRYOMANCER)
        assertEquals(mapOf(WeaponKind.FROST_NOVA to 1), base.state.weapons.toMap())
        assertEquals(100f, base.state.hp, 0.001f)

        // Gleiche Nova gegen einen unverlangsamten und einen verlangsamten Gegner
        fun novaDamage(slowed: Boolean): Float {
            val e = engine(HeroClass.CRYOMANCER)
            e.clearSpawns()
            e.state.invulnerable = 100f
            e.spawnEnemy(EnemyKind.ORC, 1.2f, 0f, hpMultiplier = 100f).apply { if (slowed) slowTimer = 5f }
            e.update(1f / 60f)
            return e.state.weaponDamage[WeaponKind.FROST_NOVA] ?: 0f
        }
        val normal = novaDamage(slowed = false)
        assertTrue(normal > 0f)
        assertEquals(normal * (1f + HeroStats.CRYO_COLD_BONUS), novaDamage(slowed = true), 0.01f)
    }

    @Test
    fun cryomancer_frozenEnemiesShatterOnDeath() {
        fun shatterRun(frozen: Boolean, heroClass: HeroClass = HeroClass.CRYOMANCER): Enemy {
            val e = engine(heroClass)
            e.clearSpawns()
            e.state.weapons.clear()
            e.state.invulnerable = 100f
            e.spawnEnemy(EnemyKind.SLIME, 6f, 0f).apply {
                if (frozen) freezeTimer = 1f
                hp = 0f
            }
            val neighbour = e.spawnEnemy(EnemyKind.ORC, 7f, 0f, hpMultiplier = 100f)
            e.update(1f / 60f)
            if (frozen && heroClass == HeroClass.CRYOMANCER) {
                assertTrue((e.state.effectDamage[EffectDamage.SHATTER] ?: 0f) > 0f)
            }
            return neighbour
        }
        val hit = shatterRun(frozen = true)
        assertTrue(hit.hp < hit.maxHp)
        assertTrue(hit.freezeTimer > 0f)
        // Nicht gefroren oder andere Klasse: keine Splitter
        val untouched = shatterRun(frozen = false)
        assertEquals(untouched.maxHp, untouched.hp, 0.001f)
        val knight = shatterRun(frozen = true, heroClass = HeroClass.KNIGHT)
        assertEquals(knight.maxHp, knight.hp, 0.001f)
    }

    @Test
    fun cryomancer_doublesFrostShards_andCostsRunes() {
        val cryo = engine(HeroClass.CRYOMANCER).apply { state.items[ItemKind.FROST] = 2 }
        val knight = engine(HeroClass.KNIGHT).apply { state.items[ItemKind.FROST] = 2 }
        assertEquals(HeroStats(knight.state).freezeChance * 2f, HeroStats(cryo.state).freezeChance, 0.0001f)
        val unlock = com.adlessarcade.games.survivors.progress.SurvivorsUnlocks.forClass(HeroClass.CRYOMANCER)
        assertEquals(120, unlock.runePrice)
        assertNotNull(Upgrades.itemSynergy(HeroClass.CRYOMANCER, ItemKind.FROST))
    }

    @Test
    fun paladin_startsWithAura_andIsFree() {
        val e = engine(HeroClass.PALADIN)
        assertEquals(mapOf(WeaponKind.AURA to 1), e.state.weapons.toMap())
        assertEquals(130f, e.state.hp, 0.001f)
        assertEquals(1, HeroStats(e.state).armor)
        val progress = com.adlessarcade.games.survivors.progress.SurvivorsProgress()
        assertTrue(com.adlessarcade.games.survivors.progress.UnlockLogic.isUnlocked(progress, HeroClass.PALADIN))
    }

    @Test
    fun paladin_overhealBecomesCappedShield_thatAbsorbsHits() {
        val e = engine(HeroClass.PALADIN)
        e.clearSpawns()
        e.state.weapons.clear()
        e.state.passives[PassiveKind.REGEN] = 5 // 3 Leben/s beim Paladin
        e.run(5f)
        val st = HeroStats(e.state)
        assertEquals(st.maxHp, e.state.hp, 0.001f)
        assertTrue(e.state.shield > 10f)
        e.run(60f)
        assertEquals(st.maxHp * HeroStats.PALADIN_SHIELD_SHARE, e.state.shield, 0.01f) // gedeckelt
        // Treffer zieht zuerst den Schild ab
        val before = e.state.shield
        e.hurtPlayer(10f, st)
        assertEquals(st.maxHp, e.state.hp, 0.001f)
        assertEquals(before - (10f - st.armor), e.state.shield, 0.01f)

        // Regeneration doppelt so stark wie beim Krieger, und kein Schild beim Krieger
        val k = engine(HeroClass.KNIGHT).apply { state.passives[PassiveKind.REGEN] = 5 }
        assertEquals(HeroStats(k.state).regen * 2f, st.regen, 0.0001f)
        k.clearSpawns()
        k.state.weapons.clear()
        k.run(5f)
        assertEquals(0f, k.state.shield, 0f)
    }

    @Test
    fun paladin_judgmentSmitesAfterSixAuraHits() {
        val e = engine(HeroClass.PALADIN)
        e.clearSpawns()
        e.state.invulnerable = 1_000f
        val orc = e.spawnEnemy(EnemyKind.ORC, 1f, 0f, hpMultiplier = 1_000f)
        var smites = 0
        var t = 0f
        while (t < 4f) {
            e.update(1f / 60f)
            orc.x = e.state.px + 1f // in der Aura halten
            orc.y = e.state.py
            smites += e.drainEvents().count { it is SurvivorsEvent.Smite }
            t += 1f / 60f
        }
        assertTrue(smites >= 1)
        assertTrue((e.state.effectDamage[EffectDamage.SMITE] ?: 0f) > 0f)
        assertTrue(orc.judgment < SurvivorsEngine.JUDGMENT_STACKS)

        // Krieger mit Aura baut kein Urteil auf
        val k = engine(HeroClass.KNIGHT)
        k.clearSpawns()
        k.state.weapons[WeaponKind.AURA] = 1
        k.state.invulnerable = 1_000f
        val other = k.spawnEnemy(EnemyKind.ORC, 1f, 0f, hpMultiplier = 1_000f)
        k.run(2f)
        assertEquals(0, other.judgment)
    }

    @Test
    fun paladin_hitsUndeadHarder() {
        fun auraDamage(kind: EnemyKind): Float {
            val e = engine(HeroClass.PALADIN)
            e.clearSpawns()
            e.state.invulnerable = 1_000f
            e.spawnEnemy(kind, 1f, 0f, hpMultiplier = 1_000f)
            e.update(1f / 60f)
            return e.state.weaponDamage[WeaponKind.AURA] ?: 0f
        }
        val living = auraDamage(EnemyKind.ORC)
        assertTrue(living > 0f)
        assertEquals(living * (1f + HeroStats.PALADIN_UNDEAD_BONUS), auraDamage(EnemyKind.SKELETON), 0.01f)
    }

    @Test
    fun assassin_firstHitIsAmbushCrit_andCritKillsGiveShadowStep() {
        val start = engine(HeroClass.ASSASSIN)
        assertEquals(mapOf(WeaponKind.DAGGERS to 1), start.state.weapons.toMap())
        assertEquals(85f, start.state.hp, 0.001f)

        // Gleicher Dolchtreffer auf einen frischen und einen bereits getroffenen Gegner
        fun daggerDamage(ambushUsed: Boolean): Float {
            val e = engine(HeroClass.ASSASSIN)
            e.clearSpawns()
            e.state.invulnerable = 1_000f
            e.spawnEnemy(EnemyKind.ORC, 2f, 0f, hpMultiplier = 1_000f).ambushed = ambushUsed
            e.inputX = 1f
            var t = 0f
            while (t < 1.5f && (e.state.weaponDamage[WeaponKind.DAGGERS] ?: 0f) == 0f) {
                e.update(1f / 60f)
                t += 1f / 60f
            }
            return e.state.weaponDamage[WeaponKind.DAGGERS] ?: 0f
        }
        val normal = daggerDamage(ambushUsed = true)
        assertTrue(normal > 0f)
        val factor = HeroStats.BASE_CRIT + HeroStats.ASSASSIN_AMBUSH_CRIT_DAMAGE
        assertEquals(normal * factor, daggerDamage(ambushUsed = false), 0.01f)

        // Krit-Kill → Schattenschritt mit mehr Tempo
        val e = engine(HeroClass.ASSASSIN)
        e.clearSpawns()
        e.state.weapons.clear()
        val baseSpeed = HeroStats(e.state).speed
        e.spawnEnemy(EnemyKind.SLIME, 6f, 0f).apply { lastCrit = true; hp = 0f }
        e.update(1f / 60f)
        assertTrue(e.state.shadowStep > 0f)
        assertEquals(baseSpeed * (1f + HeroStats.ASSASSIN_SHADOW_STEP_SPEED), HeroStats(e.state).speed, 0.001f)
        e.run(1.2f)
        assertEquals(0f, e.state.shadowStep, 0f)
    }

    @Test
    fun stormcaller_everyThirdLightningSalvoThunders() {
        val e = engine(HeroClass.STORMCALLER)
        assertEquals(mapOf(WeaponKind.LIGHTNING to 1), e.state.weapons.toMap())
        assertEquals(95f, e.state.hp, 0.001f)
        e.clearSpawns()
        e.state.invulnerable = 1_000f
        // Zäher Gegner direkt neben dem Helden: wird von Blitz und Donnerschlag getroffen
        val orc = e.spawnEnemy(EnemyKind.ORC, 1.5f, 0f, hpMultiplier = 1_000f)
        var salvos = 0
        var thunders = 0
        var t = 0f
        while (t < 8f && thunders == 0) {
            e.update(1f / 60f)
            orc.x = e.state.px + 1.5f
            orc.y = e.state.py
            e.drainEvents().forEach { ev ->
                if (ev is SurvivorsEvent.Lightning) salvos++
                if (ev is SurvivorsEvent.Thunder) thunders++
            }
            t += 1f / 60f
        }
        assertEquals(1, thunders)
        assertEquals(SurvivorsEngine.THUNDER_SALVOS, salvos)
        assertEquals(0, e.state.thunderCount)
        assertTrue((e.state.effectDamage[EffectDamage.THUNDER] ?: 0f) > 0f)
        assertTrue(orc.stunTimer > 0f)

        // Andere Klassen mit Blitz donnern nicht
        val k = engine(HeroClass.KNIGHT)
        k.clearSpawns()
        k.state.weapons[WeaponKind.LIGHTNING] = 1
        k.state.invulnerable = 1_000f
        k.spawnEnemy(EnemyKind.ORC, 1.5f, 0f, hpMultiplier = 1_000f)
        var knightThunder = false
        repeat(60 * 8) {
            k.update(1f / 60f)
            if (k.drainEvents().any { it is SurvivorsEvent.Thunder }) knightThunder = true
        }
        assertFalse(knightThunder)
    }

    @Test
    fun pirate_getsFourChestChoices_andMoreCoins() {
        val p = engine(HeroClass.PIRATE)
        assertEquals(mapOf(WeaponKind.ANCHOR to 1), p.state.weapons.toMap())
        assertEquals(105f, p.state.hp, 0.001f)
        p.clearSpawns()
        p.openChestHere()
        assertEquals(SurvivorsEngine.PIRATE_CHEST_CHOICES, p.state.chestOffers.size)
        assertEquals(p.state.chestOffers.size, p.state.chestOffers.toSet().size)

        val k = engine(HeroClass.KNIGHT)
        k.clearSpawns()
        k.openChestHere()
        assertEquals(3, k.state.chestOffers.size)

        // Elite-Kill: Basis-Münzen + 3 Extra, dazu +25 %
        fun eliteCoins(heroClass: HeroClass): Int {
            val e = engine(heroClass)
            e.clearSpawns()
            e.state.weapons.clear()
            e.spawnEnemy(EnemyKind.SLIME, 6f, 0f, elite = true).hp = 0f
            e.update(1f / 60f)
            return e.state.coinsEarned
        }
        val knightCoins = eliteCoins(HeroClass.KNIGHT)
        val pirateCoins = eliteCoins(HeroClass.PIRATE)
        assertEquals(((knightCoins + SurvivorsEngine.PIRATE_ELITE_COINS) * (1f + SurvivorsEngine.PIRATE_COIN_BONUS)).toInt(), pirateCoins)
    }

    @Test
    fun save_roundTrip_keepsShieldAndJudgment() {
        val e = engine(HeroClass.PALADIN)
        e.state.shield = 17f
        e.spawnEnemy(EnemyKind.ORC, 5f, 0f).judgment = 4
        val restored = SurvivorsSaves.decode(SurvivorsSaves.encode(e.state, 0))!!.state
        assertEquals(17f, restored.shield, 0.001f)
        assertEquals(4, restored.enemies.single().judgment)
    }

    @Test
    fun save_roundTrip_keepsItemsAndPoison() {
        val e = engine(HeroClass.NECROMANCER)
        e.state.items[ItemKind.CRIT_CHANCE] = 3
        e.state.items[ItemKind.POISON] = 2
        e.state.souls = 42
        e.state.weaponDamage[WeaponKind.BONE_ORBIT] = 1234f
        e.state.effectDamage[EffectDamage.POISON] = 56f
        e.state.runeStone = RuneStone(3f, 4f, 20f).apply { charge = 0.5f }
        e.spawnEnemy(EnemyKind.ORC, 5f, 0f).apply { poisonDps = 3f; poisonTimer = 2f; poisonSource = WeaponKind.BONE_ORBIT }
        val restored = SurvivorsSaves.decode(SurvivorsSaves.encode(e.state, 0))!!.state
        assertEquals(mapOf(ItemKind.CRIT_CHANCE to 3, ItemKind.POISON to 2), restored.items.toMap())
        assertEquals(42, restored.souls)
        assertEquals(1234f, restored.weaponDamage[WeaponKind.BONE_ORBIT]!!, 0.001f)
        assertEquals(56f, restored.effectDamage[EffectDamage.POISON]!!, 0.001f)
        assertEquals(0.5f, restored.runeStone!!.charge, 0.001f)
        assertEquals(e.state.goals, restored.goals)
        assertEquals(0f, restored.weaponSince[HeroClass.NECROMANCER.startWeapon]!!, 0.001f)
        assertEquals(3f, restored.enemies.single().poisonDps, 0.001f)
        assertEquals(WeaponKind.BONE_ORBIT, restored.enemies.single().poisonSource)
    }

    @Test
    fun save_rejectsFinishedOrBrokenRuns() {
        val e = engine()
        e.state.dead = true
        assertTrue(SurvivorsSaves.decode(SurvivorsSaves.encode(e.state, 0)) == null)
        assertTrue(SurvivorsSaves.decode("kaputt") == null)
        assertTrue(SaveJson.encodeToString(SurvivorsSave.serializer(), SurvivorsSave(1, engine().state)).isNotEmpty())
    }
}
