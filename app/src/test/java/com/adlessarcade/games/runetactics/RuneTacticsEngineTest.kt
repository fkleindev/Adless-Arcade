package com.adlessarcade.games.runetactics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class RuneTacticsEngineTest {

    /** Setzt einen festen Shop, damit Käufe vorhersagbar sind. */
    private fun RuneTacticsEngine.forceShop(vararg ids: String) {
        for (i in shop.indices) shop[i] = ids.getOrNull(i)
    }

    private fun RuneTacticsEngine.giveGold(amount: Int) {
        // Gold nur über Verkäufe erhöhbar – deshalb per Reflection für Tests.
        val field = RuneTacticsEngine::class.java.getDeclaredField("gold")
        field.isAccessible = true
        field.setInt(this, amount)
    }

    // --- Shop & Sterne ---

    @Test
    fun startState() {
        val e = RuneTacticsEngine(1)
        assertEquals(Economy.START_GOLD, e.gold)
        assertEquals(Economy.START_LEVEL, e.level)
        assertEquals(1, e.round)
        assertTrue(e.shop.all { it != null })
        // Level 2: nur 1-Gold-Einheiten im Shop
        assertTrue(e.shop.all { RuneTacticsUnits.byId(it!!).cost == 1 })
        assertEquals(2, e.enemyArmy.size)
    }

    @Test
    fun threeCopies_combineToTwoStar() {
        val e = RuneTacticsEngine(2)
        e.giveGold(20)
        e.forceShop("skeleton", "skeleton", "skeleton")
        assertEquals(1, e.buy(0))
        assertEquals(1, e.buy(1))
        assertTrue(e.wouldCombine("skeleton"))
        assertEquals(2, e.buy(2))
        val units = e.bench.filterNotNull()
        assertEquals(1, units.size)
        assertEquals(2, units[0].star)
        assertEquals(17, e.gold)
    }

    @Test
    fun combine_keepsBoardPosition() {
        val e = RuneTacticsEngine(3)
        e.giveGold(20)
        e.forceShop("wisp", "wisp", "wisp")
        e.buy(0)
        val cell = Cell(3, 7)
        assertTrue(e.move(Slot.Bench(0), Slot.Board(cell)))
        e.buy(1)
        e.buy(2)
        assertEquals(2, e.board[cell]?.star)
        assertTrue(e.bench.all { it == null })
    }

    @Test
    fun nineCopies_combineToThreeStar() {
        val e = RuneTacticsEngine(4)
        e.giveGold(50)
        repeat(3) {
            e.forceShop("wisp", "wisp", "wisp")
            repeat(3) { i -> e.buy(i) }
        }
        val units = e.bench.filterNotNull() + e.board.values
        assertEquals(1, units.size)
        assertEquals(3, units[0].star)
    }

    @Test
    fun fullBench_onlyBuysWhenCombining() {
        val e = RuneTacticsEngine(5)
        e.giveGold(100)
        // 6 verschiedene Einheiten + 2 Grablichter füllen die Bank
        listOf("skeleton", "bladedancer", "dwarfguard", "apprentice", "axeman", "runesmith").forEach {
            e.forceShop(it)
            assertNotNull(e.buy(0))
        }
        e.forceShop("wisp", "wisp")
        e.buy(0)
        e.buy(1)
        assertTrue(e.bench.all { it != null })
        e.forceShop("lich", "wisp")
        assertNull(e.buy(0)) // kein Platz, keine Verschmelzung
        assertEquals(2, e.buy(1)) // dritte Kopie verschmilzt trotz voller Bank
        assertEquals(1, e.bench.count { it == null })
    }

    @Test
    fun sell_refundsGoldAndPool() {
        val e = RuneTacticsEngine(6)
        e.giveGold(10)
        e.forceShop("skeleton")
        val before = e.poolCount("skeleton")
        e.buy(0)
        assertEquals(9, e.gold)
        assertEquals(1, e.sell(Slot.Bench(0)))
        assertEquals(10, e.gold)
        assertEquals(before + 1, e.poolCount("skeleton"))
    }

    @Test
    fun reroll_returnsUnboughtToPool() {
        val e = RuneTacticsEngine(7)
        val total = { RuneTacticsUnits.all.sumOf { e.poolCount(it.id) } + e.shop.count { it != null } }
        val before = total()
        assertTrue(e.reroll())
        assertEquals(before, total())
        assertEquals(Economy.START_GOLD - Economy.REROLL_COST, e.gold)
    }

    @Test
    fun boardCap_limitsPlacement() {
        val e = RuneTacticsEngine(8)
        e.giveGold(20)
        e.forceShop("skeleton", "wisp", "apprentice")
        repeat(3) { e.buy(it) }
        assertTrue(e.move(Slot.Bench(0), Slot.Board(Cell(0, 4))))
        assertTrue(e.move(Slot.Bench(1), Slot.Board(Cell(1, 4))))
        assertFalse(e.move(Slot.Bench(2), Slot.Board(Cell(2, 4)))) // Level 2 = 2 Plätze
        // Tauschen geht trotzdem
        assertTrue(e.move(Slot.Bench(2), Slot.Board(Cell(0, 4))))
        // Gegnerische Hälfte ist tabu
        assertFalse(e.move(Slot.Board(Cell(1, 4)), Slot.Board(Cell(1, 2))))
    }

    @Test
    fun buyXp_levelsUp() {
        val e = RuneTacticsEngine(9)
        e.giveGold(20)
        assertTrue(e.buyXp()) // 4 XP: Level 2 → 3 (2 XP), Rest 2
        assertEquals(3, e.level)
        assertEquals(2, e.xp)
    }

    // --- Wirtschaft ---

    @Test
    fun economy_rules() {
        assertEquals(0, Economy.interest(9))
        assertEquals(3, Economy.interest(34))
        assertEquals(5, Economy.interest(120))
        assertEquals(0, Economy.streakBonus(1))
        assertEquals(1, Economy.streakBonus(2))
        assertEquals(2, Economy.streakBonus(4))
        assertEquals(3, Economy.streakBonus(7))
    }

    @Test
    fun shopOdds_respectLevel() {
        val rng = Random(1)
        repeat(500) {
            val cost = Economy.rollCost(3, rng) { true }!!
            assertTrue(cost <= 2)
        }
        val high = (0 until 2000).map { Economy.rollCost(9, rng) { true }!! }
        assertTrue(high.count { it == 5 } > 150)
    }

    @Test
    fun roundFlow_incomeAndDamage() {
        val e = RuneTacticsEngine(10)
        // Ohne Einheiten verliert man sofort
        assertTrue(e.startCombat())
        val result = e.finishCombat()!!
        assertEquals(Outcome.LOSS, result.outcome)
        assertEquals(Economy.damage(1, 2), result.hpLost)
        assertEquals(Economy.START_HP - result.hpLost, e.hp)
        assertEquals(2, e.round)
        assertEquals(Phase.PLANNING, e.phase)
        assertEquals(Economy.START_GOLD + Economy.BASE_INCOME, e.gold)
    }

    @Test
    fun startCombat_autoFillsFromBench() {
        val e = RuneTacticsEngine(11)
        e.forceShop("skeleton", "wisp")
        e.buy(0)
        e.buy(1)
        e.startCombat()
        assertEquals(2, e.board.size)
        assertTrue(e.board.keys.all { Board.isPlayerCell(it) })
    }

    @Test
    fun gameOver_whenHpZero() {
        val e = RuneTacticsEngine(12)
        var guard = 0
        while (!e.isGameOver && guard++ < 200) {
            if (e.pendingAugments.isNotEmpty()) e.chooseAugment(0)
            if (e.pendingRewards.isNotEmpty()) e.chooseReward(0)
            e.startCombat()
            e.finishCombat()
        }
        assertTrue(e.isGameOver)
        assertEquals(0, e.hp)
    }

    // --- Kampf ---

    private fun army(vararg ids: Pair<String, Int>): List<Pair<UnitInstance, Cell>> =
        ids.mapIndexed { i, (id, star) -> UnitInstance(i.toLong(), id, star) to Cell(i % Board.COLS, 4 + i / Board.COLS) }

    private fun enemies(vararg ids: Pair<String, Int>): List<PlacedUnit> =
        ids.mapIndexed { i, (id, star) -> PlacedUnit(id, star, i % Board.COLS, 3 - i / Board.COLS) }

    @Test
    fun combat_isDeterministic() {
        fun run(): Pair<Outcome?, Float> {
            val c = RuneTacticsCombat(
                army("skeleton" to 1, "apprentice" to 1, "warden" to 1),
                enemies("bladedancer" to 1, "wisp" to 1, "dwarfguard" to 1),
                1f, seed = 42,
            )
            c.runToEnd()
            return c.outcome to c.time
        }
        assertEquals(run(), run())
    }

    @Test
    fun combat_strongerArmyWins() {
        repeat(5) { seed ->
            val c = RuneTacticsCombat(
                army("deathknight" to 2, "lich" to 2, "thane" to 2),
                enemies("skeleton" to 1, "wisp" to 1),
                1f, seed = seed.toLong(),
            )
            c.runToEnd()
            assertEquals(Outcome.WIN, c.outcome)
        }
    }

    @Test
    fun combat_alwaysEnds() {
        val c = RuneTacticsCombat(
            army("dwarfguard" to 3, "warden" to 3),
            enemies("dwarfguard" to 3, "warden" to 3),
            1f, seed = 3,
        )
        c.runToEnd()
        assertNotNull(c.outcome)
        assertTrue(c.time <= RuneTacticsCombat.TIME_LIMIT + RuneTacticsCombat.TICK)
    }

    @Test
    fun undead_revive() {
        val c = RuneTacticsCombat(army("skeleton" to 1, "wisp" to 1), enemies("queen" to 3), 1f, seed = 1)
        var revived = 0
        while (!c.isOver) {
            c.step()
            revived += c.drainEvents().count { it is CombatEvent.Revive }
        }
        assertEquals(2, revived)
    }

    @Test
    fun allAbilities_castWithoutCrash() {
        RuneTacticsUnits.all.forEach { def ->
            val c = RuneTacticsCombat(
                army(def.id to 2, "skeleton" to 1),
                enemies("dwarfguard" to 2, "wisp" to 1, "apprentice" to 1),
                1f, seed = 5,
            )
            var casts = 0
            while (!c.isOver) {
                c.step()
                casts += c.drainEvents().count { it is CombatEvent.Cast && c.find(it.caster)?.def == def }
            }
            assertTrue("${def.name} hat nie gezaubert", casts > 0)
        }
    }

    // --- KI ---

    @Test
    fun enemyBudget_growsAndArmyFits() {
        var last = 0f
        for (round in 1..60) {
            val budget = RuneTacticsEnemyAI.budget(round)
            assertTrue(budget > last)
            last = budget
            val army = RuneTacticsEnemyAI.army(99, round)
            assertTrue(army.all { it.row in 0 until Board.PLAYER_FIRST_ROW && it.col in 0 until Board.COLS })
            assertEquals(army.size, army.map { Cell(it.col, it.row) }.toSet().size)
            assertTrue(army.all { it.items.size <= RuneTacticsItems.MAX_PER_UNIT })
            when (RoundType.of(round)) {
                RoundType.NORMAL -> {
                    assertEquals(RuneTacticsEnemyAI.unitCount(round), army.size)
                    assertTrue(army.all { it.def.cost <= RuneTacticsEnemyAI.maxCost(round) })
                    assertTrue(army.all { it.star <= RuneTacticsEnemyAI.maxStar(round) })
                    assertEquals(RuneTacticsEnemyAI.itemCount(round), army.sumOf { it.items.size })
                }
                RoundType.CREEPS -> {
                    assertEquals(RuneTacticsEnemyAI.creatureCount(round), army.size)
                    assertTrue(army.all { it.def in RuneTacticsUnits.creatures })
                }
                RoundType.BOSS -> assertEquals(1, army.count { it.def.isBoss })
            }
        }
        assertEquals(1f, RuneTacticsEnemyAI.scale(25))
        assertTrue(RuneTacticsEnemyAI.scale(45) > RuneTacticsEnemyAI.scale(40))
    }

    @Test
    fun enemyArmy_isStablePerSeedAndRound() {
        assertEquals(RuneTacticsEnemyAI.army(5, 12), RuneTacticsEnemyAI.army(5, 12))
    }

    // --- Items ---

    private fun RuneTacticsEngine.giveItems(vararg ids: String) {
        items.clear()
        items += ids
    }

    @Test
    fun itemCatalog_hasAllRecipes() {
        assertEquals(6, RuneTacticsItems.components.size)
        assertEquals(21, RuneTacticsItems.combinedItems.size)
        val comps = RuneTacticsItems.components.map { it.id }
        for (a in comps) for (b in comps) assertNotNull("$a+$b", RuneTacticsItems.combine(a, b))
        assertEquals(RuneTacticsItems.combine("sword", "bow"), RuneTacticsItems.combine("bow", "sword"))
    }

    @Test
    fun equip_combinesComponentsOnUnit() {
        val e = RuneTacticsEngine(20)
        e.forceShop("skeleton")
        e.buy(0)
        e.giveItems("sword", "bow", "vest", "cloak", "belt")
        val slot = Slot.Bench(0)
        assertTrue(e.equip(0, slot)!!.isComponent)
        assertEquals("Raserklinge", e.equip(0, slot)!!.name) // Schwert + Bogen
        assertEquals(listOf("sword_bow"), e.unitAt(slot)!!.items)
        e.equip(0, slot) // Weste (Komponente)
        e.equip(0, slot) // Umhang verschmilzt mit der Weste
        assertEquals(listOf("sword_bow", "vest_cloak"), e.unitAt(slot)!!.items)
        e.giveItems("sword_sword", "rod_rod")
        assertNotNull(e.equip(0, slot))
        assertNull(e.equip(0, slot)) // maximal 3 Items
        assertEquals(listOf("rod_rod"), e.items)
    }

    @Test
    fun itemsReturn_onSellAndMoveOnCombine() {
        val e = RuneTacticsEngine(21)
        e.giveGold(20)
        e.forceShop("wisp", "wisp", "wisp")
        e.buy(0)
        e.giveItems("rod_rod")
        e.equip(0, Slot.Bench(0))
        e.buy(1)
        e.buy(2)
        val upgraded = e.bench.filterNotNull().single()
        assertEquals(2, upgraded.star)
        assertEquals(listOf("rod_rod"), upgraded.items)
        e.sell(Slot.Bench(e.bench.indexOf(upgraded)))
        assertEquals(listOf("rod_rod"), e.items)
    }

    @Test
    fun itemEffects_changeFighterStats() {
        val unit = UnitInstance(1, "skeleton", 1, listOf("belt_belt", "bow_vest", "sword_sword"))
        val c = RuneTacticsCombat(listOf(unit to Cell(3, 4)), enemies("wisp" to 1), 1f, seed = 1)
        val f = c.fighters[0]
        assertEquals(600f + 600f, f.maxHp)
        assertEquals(2, f.range)
        assertEquals(0.3f, f.damageBonus, 0.001f)
    }

    @Test
    fun allItems_workInCombat() {
        RuneTacticsItems.combinedItems.forEach { item ->
            val c = RuneTacticsCombat(
                army("skeleton" to 2, "wisp" to 2).mapIndexed { i, (u, cell) -> (if (i == 0) u.copy(items = listOf(item.id)) else u) to cell },
                enemies("dwarfguard" to 2, "lich" to 1, "apprentice" to 1),
                1f, seed = 9,
            )
            c.runToEnd()
            assertNotNull(item.name, c.outcome)
        }
    }

    // --- Kreaturen & Bosse ---

    @Test
    fun roundTypes_followCycle() {
        assertEquals(RoundType.NORMAL, RoundType.of(3))
        assertEquals(RoundType.CREEPS, RoundType.of(4))
        assertEquals(RoundType.BOSS, RoundType.of(5))
        assertEquals(RoundType.CREEPS, RoundType.of(9))
        assertEquals(RoundType.BOSS, RoundType.of(10))
    }

    /** Spielt Runden mit einer starken Armee bis zur gewünschten Runde. */
    private fun RuneTacticsEngine.advanceTo(round: Int) {
        while (this.round < round) {
            if (pendingRewards.isNotEmpty()) chooseReward(0)
            if (pendingAugments.isNotEmpty()) chooseAugment(0)
            board.clear()
            board[Cell(3, 4)] = UnitInstance(900, "queen", 3, listOf("sword_sword", "belt_belt", "rod_rod"))
            board[Cell(2, 4)] = UnitInstance(901, "thane", 3)
            board[Cell(4, 4)] = UnitInstance(902, "warlord", 3)
            board[Cell(3, 6)] = UnitInstance(903, "stormmage", 3)
            startCombat()
            finishCombat()
        }
    }

    @Test
    fun creatureRound_dropsComponents() {
        val e = RuneTacticsEngine(30)
        e.advanceTo(4)
        assertEquals(RoundType.CREEPS, e.roundType)
        e.startCombat()
        val result = e.finishCombat()!!
        assertEquals(Outcome.WIN, result.outcome)
        assertEquals(2, result.loot.size)
        assertTrue(result.loot.all { RuneTacticsItems.byId(it).isComponent })
        assertEquals(result.loot, e.items)
    }

    @Test
    fun bossRound_offersRewardChoice() {
        val e = RuneTacticsEngine(31)
        e.advanceTo(5)
        assertEquals(RoundType.BOSS, e.roundType)
        e.items.clear()
        e.startCombat()
        assertEquals(Outcome.WIN, e.finishCombat()!!.outcome)
        assertEquals(3, e.pendingRewards.size)
        assertFalse(e.startCombat()) // erst wählen
        val goldBefore = e.gold
        val reward = e.chooseReward(e.pendingRewards.indexOfFirst { it.kind != RewardKind.UNIT })!!
        when (reward.kind) {
            RewardKind.GOLD -> assertEquals(goldBefore + reward.amount, e.gold)
            RewardKind.COMPONENTS -> assertEquals(reward.amount, e.items.size)
            RewardKind.ITEM -> assertEquals(listOf(reward.id), e.items)
            RewardKind.UNIT -> Unit
        }
        assertTrue(e.pendingRewards.isEmpty())
        assertTrue(e.startCombat())
    }

    @Test
    fun boss_telegraphsBeforeStriking() {
        val boss = RuneTacticsUnits.byId("bonedragon")
        val c = RuneTacticsCombat(
            army("dwarfguard" to 3, "warden" to 3, "skeleton" to 3),
            listOf(PlacedUnit(boss.id, 1, 3, 2)),
            1f, seed = 2,
        )
        var telegraphAt = -1f
        var strikeAt = -1f
        while (!c.isOver && strikeAt < 0f) {
            c.step()
            for (ev in c.drainEvents()) {
                if (ev is CombatEvent.Telegraph && telegraphAt < 0f) telegraphAt = c.time
                if (ev is CombatEvent.Area && ev.kind == AbilityKind.SLAM && strikeAt < 0f) strikeAt = c.time
            }
        }
        assertTrue("Boss hat nie angekündigt", telegraphAt > 0f)
        assertTrue(strikeAt - telegraphAt >= 1.2f)
    }

    // --- Runen-Segen & Flüche ---

    private fun RuneTacticsEngine.forceAugment(augment: Augment) {
        val field = RuneTacticsEngine::class.java.getDeclaredField("pendingAugments")
        field.isAccessible = true
        field.set(this, listOf(augment))
        assertEquals(augment, chooseAugment(0))
    }

    @Test
    fun augmentRounds_schedule() {
        val rounds = (1..60).filter(Augment::isAugmentRound)
        assertEquals(listOf(3, 12, 22, 37, 52), rounds)
    }

    @Test
    fun augmentOffer_blocksCombatUntilChosen() {
        val e = RuneTacticsEngine(40)
        e.advanceTo(3)
        assertEquals(Augment.CHOICES, e.pendingAugments.size)
        assertEquals(Augment.CHOICES, e.pendingAugments.toSet().size)
        assertFalse(e.startCombat())
        e.chooseAugment(1)
        assertEquals(1, e.augments.size)
        assertTrue(e.startCombat())
    }

    @Test
    fun augmentRoll_skipsOwnedUnlessRepeatable() {
        val owned = Augment.entries.filter { !it.repeatable }
        repeat(20) {
            val offer = Augment.roll(owned, Random(it))
            assertTrue(offer.all { a -> a.repeatable })
        }
    }

    @Test
    fun augments_affectEconomy() {
        val e = RuneTacticsEngine(41)
        e.forceAugment(Augment.COMMANDER)
        assertEquals(e.level + 1, e.boardCap)
        e.forceAugment(Augment.LUCKY_DICE)
        val gold = e.gold
        assertTrue(e.reroll())
        assertEquals(gold, e.gold) // gratis
        assertTrue(e.reroll())
        assertEquals(gold - Economy.REROLL_COST, e.gold)
        e.forceAugment(Augment.WAR_CHEST)
        assertEquals(gold - Economy.REROLL_COST + 15, e.gold)
        e.forceAugment(Augment.ARMORY)
        assertEquals(3, e.items.size)
    }

    @Test
    fun treasury_raisesInterestCap() {
        val e = RuneTacticsEngine(42)
        e.forceAugment(Augment.TREASURY)
        e.giveGold(90)
        e.startCombat()
        assertEquals(8, e.finishCombat()!!.interest)
    }

    @Test
    fun emblem_countsOnlyWithTraitPresent() {
        val e = RuneTacticsEngine(43)
        e.forceAugment(Augment.EMBLEM_UNDEAD)
        assertNull(e.activeTraits()[Trait.UNDEAD])
        e.board[Cell(3, 4)] = UnitInstance(500, "skeleton")
        assertEquals(2, e.activeTraits()[Trait.UNDEAD])
        assertEquals(1, Trait.UNDEAD.level(e.activeTraits()[Trait.UNDEAD]!!))
    }

    @Test
    fun combatModifiers_applyToPlayer() {
        val mods = CombatModifiers.of(listOf(Augment.VITALITY, Augment.WARD, Augment.WELLSPRING, Augment.FURY), null)
        val c = RuneTacticsCombat(army("skeleton" to 1), enemies("wisp" to 1), 1f, seed = 1, modifiers = mods)
        val f = c.fighters[0]
        assertEquals(600f * 1.15f, f.maxHp, 0.01f)
        assertEquals(200f, f.shield, 0.01f)
        assertEquals(20f, f.mana, 0.01f)
        assertEquals(0.12f, f.damageBonus, 0.001f)
    }

    @Test
    fun curses_onlyFromRound15OnNormalRounds() {
        for (round in 1..80) {
            val curse = Curse.roll(7, round)
            if (round < Curse.START_ROUND || RoundType.of(round) != RoundType.NORMAL) assertNull("Runde $round", curse)
        }
        val cursed = (15..200).count { Curse.roll(7, it) != null }
        assertTrue(cursed > 30)
        assertEquals(Curse.roll(7, 33), Curse.roll(7, 33))
    }

    @Test
    fun curses_changeCombatStart() {
        fun combat(curse: Curse) = RuneTacticsCombat(
            army("skeleton" to 1, "wisp" to 1),
            enemies("axeman" to 2, "wisp" to 1, "skeleton" to 1),
            1f, seed = 1, modifiers = CombatModifiers(curse = curse),
        )
        val armor = combat(Curse.BONE_ARMOR).fighters.filter { it.side == 1 }
        assertTrue(armor.all { it.shield >= it.maxHp * 0.15f - 0.01f })
        assertTrue(combat(Curse.SILENCE).fighters.filter { it.side == 0 }.all { it.mana == 0f })
        val ambush = combat(Curse.AMBUSH).fighters.filter { it.side == 1 }
        assertEquals(2, ambush.count { it.row == Board.ROWS - 1 })
        val moon = combat(Curse.BLOOD_MOON).fighters.first { it.side == 1 }
        assertEquals(moon.def.attackSpeed * 1.25f, moon.attackSpeed, 0.001f)
    }

    // --- Katalog & neue Synergien ---

    @Test
    fun catalog_isBalancedAcrossTraitsAndCosts() {
        assertEquals(30, RuneTacticsUnits.all.size)
        assertEquals(listOf(9, 8, 6, 4, 3), (1..5).map { RuneTacticsUnits.ofCost(it).size })
        Trait.entries.filter { it.kind != TraitKind.MONSTER }.forEach { trait ->
            assertEquals(trait.title, 6, RuneTacticsUnits.all.count { trait in it.traits })
            assertEquals(trait.tiers.size, trait.values.size)
        }
        assertEquals(RuneTacticsUnits.all.size, RuneTacticsUnits.all.map { it.id }.toSet().size)
        // Für jede Synergie gibt es ein Banner
        val bannered = Augment.entries.mapNotNull { it.emblem }.toSet()
        assertEquals(Trait.entries.filter { it.kind != TraitKind.MONSTER }.toSet(), bannered)
    }

    @Test
    fun everyUnitHasFigureStyle_andEveryArenaStyleIsUsed() {
        assertEquals(emptyList<String>(), RuneTacticsFigures.missing())
        val arenaStyles = com.adlessarcade.core.shop.ShopCatalog.inCategory(com.adlessarcade.core.shop.ShopCategory.ARENA)
            .map { com.adlessarcade.ui.theme.CosmeticRegistry.arena(it.id).style }.toSet()
        assertEquals(com.adlessarcade.ui.theme.ArenaStyle.entries.toSet(), arenaStyles)
    }

    @Test
    fun traitTier6_reachable() {
        val undead = RuneTacticsUnits.all.filter { Trait.UNDEAD in it.traits }
        assertEquals(3, Trait.UNDEAD.level(Traits.count(undead)[Trait.UNDEAD]!!))
        assertEquals(0.9f, Trait.UNDEAD.value(3))
    }

    @Test
    fun newTraits_applyInCombat() {
        val c = RuneTacticsCombat(
            army("imp" to 1, "succubus" to 1, "sparkling" to 1, "rockwarden" to 1, "huntress" to 1, "crossbow" to 1),
            enemies("dwarfguard" to 1, "wisp" to 1),
            1f, seed = 4,
        )
        val byId = c.fighters.filter { it.side == 0 }.associateBy { it.def.id }
        assertEquals(0.15f, byId.getValue("imp").vamp, 0.001f) // 2 Dämonen
        assertEquals(10f + 20f, byId.getValue("sparkling").mana, 0.001f) // 2 Elementare
        assertEquals(0.05f, byId.getValue("huntress").rangerStep, 0.001f) // 3 Waldläufer → Stufe 1
        assertEquals(0f, byId.getValue("imp").critChance, 0.001f) // nur 1 Assassine → inaktiv
    }

    @Test
    fun assassins_jumpBehindLinesAndCrit() {
        val c = RuneTacticsCombat(
            army("imp" to 2, "shadowblade" to 2, "dwarfguard" to 1),
            enemies("dwarfguard" to 1, "wisp" to 1, "apprentice" to 1, "skeleton" to 1),
            1f, seed = 8,
        )
        val assassins = c.fighters.filter { it.side == 0 && Trait.ASSASSIN in it.def.traits }
        assertTrue(assassins.all { it.critChance == 0.2f })
        // Gegner stehen in Reihe 3 (Reihe 2 für den fünften); Assassinen landen daneben, also weit vorne.
        assertTrue(assassins.all { it.row <= 3 })
        var crits = 0
        while (!c.isOver) {
            c.step()
            crits += c.drainEvents().count { it is CombatEvent.Damage && it.crit }
        }
        assertTrue(crits > 0)
    }

    // --- Spielstand ---

    @Test
    fun saveRoundTrip() {
        val e = RuneTacticsEngine(13)
        e.forceShop("skeleton", "wisp", "apprentice")
        e.buy(0)
        e.move(Slot.Bench(0), Slot.Board(Cell(3, 4)))
        val json = RuneTacticsSaves.encode(e.snapshot(), bestAtStart = 7, seed = e.seed)
        val save = RuneTacticsSaves.decode(json)!!
        val copy = RuneTacticsEngine(save.seed).also { it.restore(save.snapshot) }
        assertEquals(e.snapshot(), copy.snapshot())
        assertEquals(7, save.bestAtStart)
        // Gleicher Zufall nach dem Laden
        e.reroll()
        copy.reroll()
        assertEquals(e.shop.toList(), copy.shop.toList())
    }

    @Test
    fun saveDuringCombat_replaysSameFight() {
        val e = RuneTacticsEngine(14)
        e.forceShop("skeleton", "wisp")
        e.buy(0)
        e.buy(1)
        e.startCombat()
        repeat(40) { e.combat!!.step() }
        val snap = e.snapshot()
        assertTrue(snap.inCombat)
        val copy = RuneTacticsEngine(e.seed).also { it.restore(snap) }
        assertEquals(Phase.COMBAT, copy.phase)
        val a = e.finishCombat()!!
        val b = copy.finishCombat()!!
        assertEquals(a, b)
    }
}
