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
                val distance = when (weapon) {
                    WeaponKind.BONE_ORBIT -> weapon.area(level)
                    WeaponKind.AURA, WeaponKind.FROST_NOVA, WeaponKind.SWORD -> 1.2f
                    else -> 3f
                }
                val target = e.spawnEnemy(EnemyKind.SLIME, distance, 0f)
                target.hp = 1f
                e.state.invulnerable = 100f // der Held soll in diesem Test nicht sterben
                var t = 0f
                while (t < 3f && target in e.state.enemies) {
                    e.update(1f / 60f)
                    // Gegner festhalten, damit die Geometrie stimmt
                    target.x = distance
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
        val levelsBefore = e.state.weapons.values.sum() + e.state.passives.values.sum()
        e.state.px = chest!!.x
        e.state.py = chest.y
        e.run(0.1f)
        assertTrue(e.awaitingChest)
        assertTrue(e.drainEvents().any { it is SurvivorsEvent.ChestReward })
        while (e.awaitingChest) e.acceptChestReward()
        val levelsAfter = e.state.weapons.values.sum() + e.state.passives.values.sum()
        assertTrue(levelsAfter > levelsBefore)
    }

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
        while (e.awaitingChest) e.acceptChestReward()
        e.update(0.1f)
        assertTrue(e.state.time > time)
    }

    @Test
    fun chest_decline_keepsBuild_andHeals() {
        val e = engine()
        e.clearSpawns()
        e.state.hp = 50f
        e.openChestHere()
        val weapons = e.state.weapons.toMap()
        val passives = e.state.passives.toMap()
        val total = e.state.chestTotal
        repeat(total) { e.declineChestReward() }
        assertFalse(e.awaitingChest)
        assertEquals(weapons, e.state.weapons.toMap())
        assertEquals(passives, e.state.passives.toMap())
        assertEquals(50f + total * SurvivorsEngine.CHEST_DECLINE_HEAL, e.state.hp, 0.01f)
    }

    @Test
    fun chest_rewardsComeOneAfterAnother() {
        val e = engine()
        e.clearSpawns()
        e.openChestHere()
        e.state.chestTotal = 3 // feste Anzahl für den Test
        e.drainEvents()
        val seen = ArrayList<Int>()
        while (e.awaitingChest) {
            seen += e.state.chestIndex
            e.acceptChestReward()
            e.drainEvents().filterIsInstance<SurvivorsEvent.ChestReward>().forEach { assertEquals(3, it.total) }
        }
        assertEquals(listOf(0, 1, 2), seen)
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
        while (e.awaitingChest) e.acceptChestReward()
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
        assertEquals(UpgradeOffer(evolution = WeaponKind.FIREBALL), e.state.chestReward)
        e.drainEvents()
        e.acceptChestReward()
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
        while (e.awaitingChest) {
            assertTrue(e.state.chestReward?.evolution == null)
            e.declineChestReward()
        }
        // Danach: genau eine Entwicklung, obwohl zwei bereit sind
        e.state.time = SurvivorsEngine.EVOLUTION_MIN_TIME
        e.openChestHere()
        e.state.chestTotal = 3
        var evolutions = 0
        while (e.awaitingChest) {
            if (e.state.chestReward?.evolution != null) evolutions++
            e.acceptChestReward()
        }
        assertEquals(1, evolutions)
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
            val distance = when (weapon) {
                WeaponKind.BONE_ORBIT -> weapon.area(MAX_LEVEL) * e.stats.areaMultiplier
                WeaponKind.AURA, WeaponKind.FROST_NOVA, WeaponKind.SWORD -> 1.2f
                else -> 3f
            }
            val target = e.spawnEnemy(EnemyKind.ORC, distance, 0f)
            e.state.invulnerable = 100f
            var t = 0f
            while (t < 5f && target in e.state.enemies) {
                e.update(1f / 60f)
                target.x = distance
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
            if (e.awaitingChest) e.acceptChestReward()
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
        assertEquals(UpgradeOffer(evolution = WeaponKind.SWORD), restored.state.chestReward)
        assertTrue(restored.isEvolved(WeaponKind.FIREBALL))

        // Spielstände der Version 1 (ohne neue Felder) laden weiterhin
        val v1 = SaveJson.encodeToString(SurvivorsSave.serializer(), SurvivorsSave(1, engine().state))
            .replace(Regex(""","(evolved|chestReward|chestIndex|chestTotal|chestEvolutionGiven|pendingStrikes|hazards|orbitAngle|leechPool)":(\[\]|null|false|[0-9.]+)"""), "")
        assertFalse(v1.contains("evolved"))
        assertNotNull(SurvivorsSaves.decode(v1))
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
