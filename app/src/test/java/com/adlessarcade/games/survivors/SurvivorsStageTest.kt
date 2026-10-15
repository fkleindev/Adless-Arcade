package com.adlessarcade.games.survivors

import com.adlessarcade.core.SaveJson
import com.adlessarcade.games.survivors.progress.SurvivorsProgress
import com.adlessarcade.games.survivors.progress.SurvivorsStats
import com.adlessarcade.games.survivors.progress.SurvivorsUnlocks
import com.adlessarcade.games.survivors.progress.UnlockLogic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class SurvivorsStageTest {

    private fun graveyard(heroClass: HeroClass = HeroClass.KNIGHT, mode: SurvivorsMode = SurvivorsMode.TIMED) =
        SurvivorsEngine.newGame(heroClass, mode, seed = 5, stage = SurvivorsStage.GRAVEYARD).apply {
            state.enemies.clear()
            state.spawner.spawnBudget = -1_000_000f
            state.spawner.eliteTimer = 100_000f
            state.spawner.swarmTimer = 100_000f
            state.spawner.nextBossTime = 100_000f
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

    /** Ein Hindernis, das nicht am Start liegt (für Kollisionstests). */
    private fun someObstacle(): Obstacle {
        for (cx in 1..20) SurvivorsObstacles.inCell(cx, 0)?.let { return it }
        error("kein Hindernis gefunden")
    }

    // --- Freischaltung ---

    @Test
    fun graveyard_needsDemonVictory_and100Runes() {
        val unlock = SurvivorsUnlocks.forStage(SurvivorsStage.GRAVEYARD)!!
        assertEquals(100, unlock.runePrice)
        assertNull(SurvivorsUnlocks.forStage(SurvivorsStage.MEADOW))
        assertTrue(UnlockLogic.isUnlocked(SurvivorsProgress(), SurvivorsStage.MEADOW))
        assertFalse(UnlockLogic.isUnlocked(SurvivorsProgress(), SurvivorsStage.GRAVEYARD))

        assertNull(UnlockLogic.buy(SurvivorsProgress(runes = 500), unlock)) // Dämonenfürst fehlt
        val won = SurvivorsProgress(runes = 100, stats = mapOf(SurvivorsStats.DEMON_DEFEATED to 1))
        val after = UnlockLogic.buy(won, unlock)!!
        assertEquals(0, after.runes)
        assertTrue(UnlockLogic.isUnlocked(after, SurvivorsStage.GRAVEYARD))
        assertNull(UnlockLogic.buy(SurvivorsProgress(runes = 99, stats = won.stats), unlock))
    }

    @Test
    fun selectedStage_fallsBackToMeadow_whenLocked() {
        assertEquals(SurvivorsStage.MEADOW, UnlockLogic.selectedStage(SurvivorsProgress(selectedStage = "graveyard")))
        val owned = SurvivorsProgress(unlocked = setOf("stage_graveyard"), selectedStage = "graveyard")
        assertEquals(SurvivorsStage.GRAVEYARD, UnlockLogic.selectedStage(owned))
    }

    @Test
    fun victory_countsForTheStageBoss() {
        val meadow = UnlockLogic.afterRun(emptyMap(), victory = true, bossesKilled = 2, seconds = 900, stage = SurvivorsStage.MEADOW)
        assertEquals(1, meadow[SurvivorsStats.DEMON_DEFEATED])
        val grave = UnlockLogic.afterRun(emptyMap(), victory = true, bossesKilled = 2, seconds = 900, stage = SurvivorsStage.GRAVEYARD)
        assertEquals(1, grave[SurvivorsStats.LICH_DEFEATED])
        assertNull(grave[SurvivorsStats.DEMON_DEFEATED])
    }

    @Test
    fun scoreKeys_keepMeadowCompatible_andSeparateStages() {
        assertEquals("timed", SurvivorsStage.MEADOW.scoreKey(SurvivorsMode.TIMED))
        assertEquals("timed_graveyard", SurvivorsStage.GRAVEYARD.scoreKey(SurvivorsMode.TIMED))
        assertEquals("class_mage", SurvivorsStage.MEADOW.leaderboardVariant(HeroClass.MAGE))
        assertEquals("graveyard_class_mage", SurvivorsStage.GRAVEYARD.leaderboardVariant(HeroClass.MAGE))
    }

    // --- Gegner & Bosse ---

    @Test
    fun graveyard_spawnsItsOwnEnemies_andBosses() {
        val e = SurvivorsEngine.newGame(HeroClass.MAGE, SurvivorsMode.TIMED, seed = 9, stage = SurvivorsStage.GRAVEYARD)
        e.state.invulnerable = 100_000f
        val kinds = HashSet<EnemyKind>()
        val bosses = ArrayList<EnemyKind>()
        var t = 0f
        while (t < 16 * 60f && !e.isOver) {
            if (e.awaitingChest) e.chooseChestReward(0)
            if (e.awaitingChoice) e.choose(0)
            e.update(1f / 10f)
            e.state.invulnerable = 100_000f
            e.state.enemies.forEach { kinds += it.kind }
            e.drainEvents().filterIsInstance<SurvivorsEvent.BossSpawned>().forEach { bosses += it.kind }
            // Die Bosse nicht ewig mitschleppen
            e.state.enemies.removeAll { it.isBoss && !it.kind.isFinalBoss }
            t += 1f / 10f
        }
        assertTrue(EnemyKind.ZOMBIE in kinds)
        assertTrue(EnemyKind.ARCHER in kinds)
        assertTrue(EnemyKind.WITCH in kinds)
        assertFalse(EnemyKind.SLIME in kinds)
        assertEquals(listOf(EnemyKind.GRAVE_GUARDIAN, EnemyKind.GRAVE_GUARDIAN, EnemyKind.LICH), bosses)
    }

    @Test
    fun meadow_hasNoGraveyardEnemies_orObstacles() {
        val e = SurvivorsEngine.newGame(HeroClass.MAGE, SurvivorsMode.TIMED, seed = 9)
        e.state.invulnerable = 100_000f
        val kinds = HashSet<EnemyKind>()
        var t = 0f
        while (t < 6 * 60f && !e.isOver) {
            if (e.awaitingChest) e.chooseChestReward(0)
            if (e.awaitingChoice) e.choose(0)
            e.update(1f / 10f)
            e.state.enemies.forEach { kinds += it.kind }
            t += 1f / 10f
        }
        assertTrue(kinds.none { it == EnemyKind.ZOMBIE || it == EnemyKind.ARCHER || it == EnemyKind.WITCH })
        assertTrue(e.obstacles.isEmpty())
        assertTrue(e.state.enemyAttacks.isEmpty())
    }

    @Test
    fun graveyard_isHarder_andGivesMoreRunes() {
        val meadow = SurvivorsEngine.newGame(HeroClass.KNIGHT, SurvivorsMode.TIMED, seed = 1)
        val grave = graveyard()
        assertTrue(grave.hpMultiplier() > meadow.hpMultiplier())
        assertTrue(grave.enemyHitScale() > meadow.enemyHitScale())
        // 4 Minuten → 8 Runen auf der Wiese, 10 auf dem Friedhof
        grave.state.invulnerable = 100_000f
        grave.run(4 * 60f + 0.1f)
        assertEquals(10, grave.state.runesEarned)
    }

    // --- Hindernisse ---

    @Test
    fun obstacles_areDeterministic_andStartIsFree() {
        val a = SurvivorsObstacles.near(30f, -12f, 20f).toList()
        val b = SurvivorsObstacles.near(30f, -12f, 20f).toList()
        assertEquals(a, b)
        assertTrue(a.isNotEmpty())
        assertTrue(SurvivorsObstacles.near(0f, 0f, 10f).none { hypot(it.x, it.y) < SurvivorsObstacles.SAFE_RADIUS + it.radius })
    }

    @Test
    fun hero_cannotWalkThroughObstacles() {
        val e = graveyard()
        e.state.invulnerable = 100_000f
        e.state.weapons.clear()
        val o = someObstacle()
        // Held steht links neben dem Hindernis und läuft direkt darauf zu
        e.state.px = o.x - o.radius - 1f
        e.state.py = o.y
        e.inputX = 1f
        repeat(120) {
            e.update(1f / 60f)
            assertTrue(hypot(e.state.px - o.x, e.state.py - o.y) >= o.radius + SurvivorsEngine.PLAYER_RADIUS - 0.01f)
        }
    }

    @Test
    fun enemies_arePushedOut_butGhostsFloatThrough() {
        val e = graveyard()
        e.state.invulnerable = 100_000f
        e.state.weapons.clear()
        val o = someObstacle()
        e.state.px = o.x - o.radius - 3f
        e.state.py = o.y
        val zombie = e.spawnEnemy(EnemyKind.ZOMBIE, o.x, o.y + 0.1f)
        val ghost = e.spawnEnemy(EnemyKind.GHOST, o.x + 0.1f, o.y)
        e.update(1f / 60f)
        assertTrue(hypot(zombie.x - o.x, zombie.y - o.y) >= o.radius + zombie.radius - 0.01f)
        assertTrue(hypot(ghost.x - o.x, ghost.y - o.y) < o.radius)
    }

    @Test
    fun enemyShots_areBlockedByObstacles() {
        val e = graveyard()
        e.state.weapons.clear()
        val o = someObstacle()
        e.state.px = o.x + o.radius + 2f
        e.state.py = o.y
        // Pfeil fliegt von links durch das Hindernis auf den Helden zu
        e.state.enemyShots += EnemyShot(o.x - o.radius - 1f, o.y, 7f, 0f, 0.2f, 50f, life = 3f)
        val hp = e.state.hp
        e.run(1.5f)
        assertEquals(hp, e.state.hp, 0.001f)
        assertTrue(e.state.enemyShots.isEmpty())
    }

    // --- Angekündigte Angriffe ---

    @Test
    fun indicator_onlyHits_whenHeroStaysInside() {
        val stay = graveyard()
        stay.state.weapons.clear()
        stay.state.enemyAttacks += EnemyAttack(AttackShape.CIRCLE, stay.state.px, stay.state.py, 1.2f, 0.5f, 40f)
        val hp = stay.state.hp
        val events = stay.run(0.6f)
        assertTrue(stay.state.hp < hp)
        assertTrue(events.any { it is SurvivorsEvent.AttackImpact })

        val dodge = graveyard()
        dodge.state.weapons.clear()
        dodge.state.enemyAttacks += EnemyAttack(AttackShape.CIRCLE, dodge.state.px, dodge.state.py, 1.2f, 1f, 40f)
        dodge.inputX = 1f // rechtzeitig rauslaufen
        dodge.run(1.1f)
        assertEquals(dodge.stats.maxHp, dodge.state.hp, 0.001f)
    }

    @Test
    fun lineAttack_hitsAlongTheLine() {
        val e = graveyard()
        e.state.weapons.clear()
        e.state.enemyAttacks += EnemyAttack(
            AttackShape.LINE, e.state.px - 5f, e.state.py, 0.5f, 0.2f, 30f, x2 = e.state.px + 5f, y2 = e.state.py,
        )
        val hp = e.state.hp
        e.run(0.3f)
        assertTrue(e.state.hp < hp)
        assertTrue(distanceToSegment(0f, 3f, -5f, 0f, 5f, 0f) > 2.9f)
    }

    @Test
    fun archer_aims_thenShoots_andWitchCastsCircle() {
        val e = graveyard()
        e.state.weapons.clear()
        e.state.invulnerable = 100_000f
        val archer = e.spawnEnemy(EnemyKind.ARCHER, 5f, 0f)
        archer.attackTimer = 0f
        e.update(1f / 60f)
        assertTrue(archer.windup > 0f)
        assertTrue(e.state.enemyShots.isEmpty())
        e.run(SurvivorsEnemyAttacks.ARCHER_AIM_TIME + 0.05f)
        assertTrue(e.state.enemyShots.isNotEmpty())

        val witch = e.spawnEnemy(EnemyKind.WITCH, -5f, 0f)
        witch.attackTimer = 0f
        e.update(1f / 60f)
        assertTrue(e.state.enemyAttacks.any { it.shape == AttackShape.CIRCLE })
    }

    @Test
    fun bosses_telegraphTheirAttacks() {
        val e = graveyard()
        e.state.weapons.clear()
        e.state.invulnerable = 100_000f
        val guardian = e.spawnEnemy(EnemyKind.GRAVE_GUARDIAN, 6f, 0f)
        guardian.attackTimer = 0f
        e.update(1f / 60f)
        assertTrue(e.state.enemyAttacks.any { it.shape == AttackShape.LINE }) // Sturmangriff
        e.run(1.2f)
        assertTrue(guardian.x < 6f - 3f) // ist losgestürmt

        val f = graveyard()
        f.state.weapons.clear()
        f.state.invulnerable = 100_000f
        val lich = f.spawnEnemy(EnemyKind.LICH, 7f, 0f, hpMultiplier = 100f)
        var shots = false
        var circles = false
        var lines = false
        val before = f.state.enemies.size
        var summoned = false
        repeat(60 * 18) {
            f.update(1f / 60f)
            if (f.state.enemyShots.isNotEmpty()) shots = true
            if (f.state.enemyAttacks.any { it.shape == AttackShape.CIRCLE }) circles = true
            if (f.state.enemyAttacks.any { it.shape == AttackShape.LINE }) lines = true
            if (f.state.enemies.count { it.kind == EnemyKind.SKELETON } > 0) summoned = true
            f.state.invulnerable = 100_000f
            lich.hp = lich.maxHp
            // auf Abstand halten, damit die Seelenkugeln nicht sofort am Helden zerplatzen
            lich.x = 7f
            lich.y = 0f
        }
        assertTrue("Seelenring", shots)
        assertTrue("Knochenfeld", circles)
        assertTrue("Todesstrahl", lines)
        assertTrue("Beschwörung", summoned)
        assertTrue(f.state.enemies.size > before)
    }

    // --- Spielstand ---

    @Test
    fun save_keepsStage_attacksAndShots_andOldSavesAreMeadow() {
        val e = graveyard()
        e.state.enemyAttacks += EnemyAttack(AttackShape.LINE, 1f, 2f, 0.5f, 0.8f, 20f, x2 = 5f, y2 = 2f)
        e.state.enemyShots += EnemyShot(1f, 1f, 2f, 0f, 0.2f, 10f, life = 2f, soul = true)
        e.spawnEnemy(EnemyKind.ARCHER, 4f, 4f).apply { windup = 0.3f; dashVx = 1f }
        val json = SurvivorsSaves.encode(e.state, 0)
        val restored = SurvivorsSaves.decode(json)!!.state
        assertEquals(SurvivorsStage.GRAVEYARD, restored.stage)
        assertEquals(0.8f, restored.enemyAttacks.single().maxDelay, 0.001f)
        assertEquals(5f, restored.enemyAttacks.single().x2, 0.001f)
        assertTrue(restored.enemyShots.single().soul)
        assertEquals(0.3f, restored.enemies.single().windup, 0.001f)
        assertEquals(json, SurvivorsSaves.encode(restored, 0))

        val old = SaveJson.encodeToString(SurvivorsSave.serializer(), SurvivorsSave(3, SurvivorsEngine.newGame(HeroClass.KNIGHT, SurvivorsMode.TIMED, 1).state))
            .replace(Regex(""","(stage|enemyAttacks|enemyShots|runeRemainder)":("[a-zA-Z_]+"|\[\]|[0-9.]+)"""), "")
        assertFalse(old.contains("enemyShots"))
        val loaded = SurvivorsSaves.decode(old)
        assertNotNull(loaded)
        assertEquals(SurvivorsStage.MEADOW, loaded!!.state.stage)
    }
}
