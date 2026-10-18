package com.adlessarcade.games.kitchen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

private const val DT = 1f / 60f
private const val ASPECT = 1.8f

/** Löst ein Minispiel mit idealen Gesten (für Tests). [quality] < 1 macht absichtlich ungenaue Eingaben. */
fun solve(game: MiniGame, quality: Float = 1f, step: (Float) -> Unit = { game.update(it) }) {
    var guard = 0
    fun tick() { step(DT); guard++ }
    when (game) {
        is Chop -> while (!game.done && guard < 2000) {
            val next = game.lines.indices.firstOrNull { game.cuts[it] == null } ?: break
            val offset = (1f - quality) * game.tolerance * 0.9f
            if (game.knifeX >= game.lines[next] + offset - 0.002f) game.down(0.5f, 0.5f)
            tick()
        }
        is Peel -> {
            for (b in 0 until game.bands) {
                val y = game.bandCenter(b)
                game.down(Peel.LEFT - 0.02f, y)
                var x = Peel.LEFT
                val end = Peel.LEFT + (Peel.RIGHT - Peel.LEFT + 0.03f) * quality
                while (x <= end) { game.move(x, y); x += 0.02f; tick() }
                game.up(x, y)
            }
            while (!game.done && guard < 2000) tick()
        }
        is Grate -> {
            var y = 0.3f
            var dir = 1f
            game.down(0.5f, y)
            while (!game.done && guard < 2000) {
                y += dir * 0.06f * quality
                if (y > 0.8f || y < 0.2f) dir = -dir
                game.move(0.5f, y)
                tick()
            }
        }
        is Crack -> while (!game.done && guard < 2000) {
            val target = game.zoneCenter + (1f - quality) * game.zoneHalf * 0.8f
            if (abs(game.pointer - target) < 0.013f) game.down(0.5f, 0.5f) else tick()
        }
        is Pour -> {
            game.down(0.5f, 0.5f)
            val target = game.target + (1f - quality) * game.tolerance * 0.8f
            while (!game.done && game.level < target && guard < 2000) tick()
            game.up(0.5f, 0.5f)
        }
        is Stir -> {
            val omega = (game.minSpeed + game.maxSpeed) / 2f * (if (quality < 1f) 2.2f else 1f)
            var a = 0f
            game.down(0.5f + cos(a) * 0.2f / ASPECT, 0.5f + sin(a) * 0.2f)
            while (!game.done && guard < 3000) {
                a += omega * DT
                tick()
                game.move(0.5f + cos(a) * 0.2f / ASPECT, 0.5f + sin(a) * 0.2f)
            }
        }
        is Flip -> {
            val target = (game.windowStart + game.windowEnd) / 2f - (1f - quality) * 0.3f
            while (!game.done && game.browning < target && guard < 2000) tick()
            game.down(0.5f, 0.7f)
            game.move(0.5f, 0.4f)
        }
        is Heat -> while (!game.done && guard < 2000) {
            val aim = game.zoneCenter + (1f - quality) * 0.3f
            if (game.temperature < aim) game.down(0.5f, 0.5f) else game.up(0.5f, 0.5f)
            tick()
        }
        is PlateUp -> {
            for (p in game.pieces) {
                game.down(p.x, p.y)
                val tx = p.targetX + (1f - quality) * 0.15f
                game.move(tx, p.targetY)
                game.up(tx, p.targetY)
                tick()
            }
            while (!game.done && guard < 2000) tick()
        }
        is Season -> {
            val target = game.target + (1f - quality) * game.tolerance * 2f
            while (game.amount < target - 0.03f && !game.done) { game.down(0.5f, 0.5f); tick() }
            while (!game.done && guard < 2000) tick()
        }
    }
}

class KitchenMiniGameTest {
    private fun game(kind: MiniGameKind, difficulty: Float = 0.3f, seed: Long = 1) = MiniGame.create(kind, difficulty, ASPECT, seed)

    @Test
    fun everyMiniGame_perfectInputScoresHigh() {
        for (kind in MiniGameKind.entries) for (d in listOf(0f, 0.5f, 1f)) for (seed in 1L..3L) {
            val g = game(kind, d, seed)
            solve(g)
            assertTrue("$kind d=$d endet nicht", g.done)
            assertFalse("$kind d=$d gilt als misslungen (${g.accuracy})", g.failed)
            assertTrue("$kind d=$d zu ungenau: ${g.accuracy}", g.accuracy >= 0.85f)
        }
    }

    @Test
    fun everyMiniGame_endsWithoutInput() {
        for (kind in MiniGameKind.entries) {
            val g = game(kind)
            var t = 0f
            while (!g.done && t < 20f) { g.update(DT); t += DT }
            assertTrue("$kind endet nie ohne Eingabe", g.done)
            assertTrue("$kind ohne Eingabe nicht misslungen", g.failed)
        }
    }

    @Test
    fun sloppyInput_scoresLower() {
        for (kind in MiniGameKind.entries) {
            val good = game(kind).also { solve(it, 1f) }
            val sloppy = game(kind).also { solve(it, 0.4f) }
            assertTrue("$kind: schlampig ${sloppy.accuracy} >= gut ${good.accuracy}", sloppy.accuracy < good.accuracy)
        }
    }

    @Test
    fun criticalErrors() {
        val crack = game(MiniGameKind.CRACK) as Crack
        while (abs(crack.pointer - crack.zoneCenter) < crack.zoneHalf * 1.5f) crack.update(DT)
        crack.down(0.5f, 0.5f)
        assertTrue(crack.critical && crack.failed)

        val pour = game(MiniGameKind.POUR) as Pour
        pour.down(0.5f, 0.5f)
        repeat(1000) { pour.update(DT) }
        assertTrue(pour.critical && pour.done)

        val season = game(MiniGameKind.SEASON) as Season
        repeat(40) { season.down(0.5f, 0.5f) }
        assertTrue(season.critical)
    }
}

class KitchenEngineTest {
    private fun engine(level: String? = "cafe_5", seed: Long = 3) =
        KitchenEngine(seed, level?.let(KitchenCatalog::level)).also { it.aspect = ASPECT }

    /** Löst das laufende Minispiel perfekt und spult die Engine dabei weiter. */
    private fun KitchenEngine.solveTask() {
        var guard = 0
        while (busy && !isFinished && guard++ < 100) {
            val g = miniGame
            if (g == null) { update(DT); continue }
            wait(humanDelay * 0.5f)
            solve(g, humanQuality) { update(it) }
            update(DT)
        }
    }

    private fun KitchenEngine.cookUntil(stove: Int, progress: Float) {
        var guard = 0
        while (pans[stove].item != null && pans[stove].progress < progress && !isFinished && guard++ < 10_000) {
            update(DT)
            if (pans[stove].canFlip && pans[stove].progress >= 0.5f) {
                tap(Station.Stove(stove))
                solveTask()
            }
        }
    }

    private fun KitchenEngine.forceOrder(dishId: String) {
        orders.clear()
        orders += Order(999, dishId, 200f, 200f)
    }

    @Test
    fun catalog_isConsistent() {
        // Jede Komponente jedes Gerichts ist über Vorrat, Brett, Schüssel oder Pfanne herstellbar
        val producible = KitchenCatalog.crates.toMutableSet()
        repeat(4) {
            producible += KitchenCatalog.boardRules.filter { it.input in producible }.map { it.output }
            producible += KitchenCatalog.bowlRecipes.filter { r -> r.ingredients.keys.all { it in producible } }.map { it.output }
            producible += KitchenCatalog.panRules.filter { it.input in producible }.map { it.output }
        }
        KitchenCatalog.dishes.forEach { d -> d.components.forEach { assertTrue("${d.name}: $it", it in producible) } }
        KitchenCatalog.levels.forEach { l -> l.dishes.forEach { assertNotNull(KitchenCatalog.findDish(it)) } }
        assertEquals(5, KitchenCatalog.levels.count { it.restaurant == Restaurant.CAFE })
    }

    @Test
    fun friedEgg_fullFlow_servesWithPoints() {
        val e = engine()
        e.forceOrder("fried_egg")
        assertTrue(e.move(Station.Crate(ItemKind.EGG), Station.Stove(0)))
        assertTrue(e.busy) // Aufschlagen
        e.solveTask()
        assertEquals(ItemKind.EGG, e.pans[0].item?.kind)
        e.cookUntil(0, 1.1f)
        assertTrue(e.move(Station.Stove(0), Station.PlateSpot(0)))
        assertTrue(e.busy) // Würzen
        e.solveTask()
        assertEquals("fried_egg", e.plates[0].dishId)
        assertTrue(e.move(Station.PlateSpot(0), Station.Serve))
        assertEquals(1, e.served)
        assertTrue(e.score >= 40)
        val served = e.drainEvents().filterIsInstance<KitchenEvent.Served>().single()
        assertEquals(3, served.result.stars)
    }

    @Test
    fun pancake_fullFlow_withBowlAndFlip() {
        val e = engine()
        e.forceOrder("pancake")
        for (k in listOf(ItemKind.EGG, ItemKind.MILK, ItemKind.FLOUR)) {
            assertTrue(e.move(Station.Crate(k), Station.Bowl))
            e.solveTask()
        }
        assertTrue(e.tap(Station.Bowl))
        e.solveTask()
        assertEquals(ItemKind.BATTER, e.bowlProduct?.kind)
        assertTrue(e.move(Station.Bowl, Station.Stove(1)))
        e.solveTask()
        // Während die Pfanne gart: Erdbeere schneiden
        assertTrue(e.move(Station.Crate(ItemKind.STRAWBERRY), Station.Board))
        e.solveTask()
        assertEquals(ItemKind.STRAWBERRY_CUT, e.board?.kind)
        assertTrue(e.move(Station.Board, Station.PlateSpot(1)))
        e.cookUntil(1, 1.1f)
        assertTrue(e.pans[1].flipped)
        assertTrue(e.move(Station.Stove(1), Station.PlateSpot(1)))
        e.solveTask()
        assertEquals("pancake", e.plates[1].dishId)
        assertTrue(e.move(Station.PlateSpot(1), Station.Serve))
        assertEquals(1, e.served)
    }

    @Test
    fun wrongDrops_areRejected() {
        val e = engine()
        assertFalse(e.move(Station.Crate(ItemKind.MILK), Station.Board))
        assertFalse(e.move(Station.Crate(ItemKind.CHEESE), Station.Stove(0)))
        assertFalse(e.move(Station.Crate(ItemKind.BREAD), Station.Bowl))
        assertFalse(e.move(Station.Crate(ItemKind.EGG), Station.Serve))
        assertTrue(e.move(Station.Crate(ItemKind.MILK), Station.Bowl))
        e.solveTask()
        assertFalse(e.tap(Station.Bowl)) // nur Milch: kein Rezept
        assertTrue(e.drainEvents().any { it is KitchenEvent.Rejected })
    }

    @Test
    fun pan_burnsWhenForgotten() {
        val e = engine()
        e.move(Station.Crate(ItemKind.BREAD), Station.Board)
        e.solveTask()
        e.move(Station.Board, Station.Stove(0))
        repeat((5f * KitchenCatalog.BURN_AT / DT).toInt() + 10) { e.update(DT) }
        assertEquals(ItemKind.BURNT, e.pans[0].item?.kind)
        assertFalse(e.move(Station.Stove(0), Station.PlateSpot(0)))
        assertTrue(e.move(Station.Stove(0), Station.Trash))
        assertNull(e.pans[0].item)
    }

    @Test
    fun failedStep_mamaHelps_withZeroScore() {
        val e = engine()
        e.move(Station.Crate(ItemKind.EGG), Station.Bowl)
        val crack = e.miniGame as Crack
        while (abs(crack.pointer - crack.zoneCenter) < crack.zoneHalf * 1.5f) e.update(DT)
        e.gameDown(0.5f, 0.5f)
        e.update(DT)
        assertTrue(e.mamaTimer > 0f)
        assertTrue(e.drainEvents().any { it is KitchenEvent.MamaHelps })
        repeat((KitchenEngine.MAMA_TIME / DT).toInt() + 5) { e.update(DT) }
        assertFalse(e.busy)
        assertEquals(listOf(0f), e.bowl.single().scores)
    }

    @Test
    fun endless_endsAfterThreeMissedOrders() {
        val e = engine(level = null)
        var t = 0f
        while (!e.isFinished && t < 1000f) { e.update(0.1f); t += 0.1f }
        assertTrue(e.isFinished)
        assertEquals(KitchenEngine.MAX_STRIKES, e.strikes)
    }

    @Test
    fun level_endsAfterDuration_andStarsFromScore() {
        val level = KitchenCatalog.level("cafe_1")
        val e = engine("cafe_1")
        var t = 0f
        while (!e.isFinished && t < 1000f) { e.update(0.1f); t += 0.1f }
        assertTrue(e.isFinished)
        assertEquals(level.duration, e.time, 0.2f)
        val targets = level.starTargets
        assertTrue(targets[0] < targets[1] && targets[1] < targets[2])
        assertEquals(0, level.stars(targets[0] - 1))
        assertEquals(3, level.stars(targets[2]))
    }

    @Test
    fun comboAndStars() {
        assertEquals(3, KitchenEngine.stars(0.95f))
        assertEquals(2, KitchenEngine.stars(0.8f))
        assertEquals(1, KitchenEngine.stars(0.6f))
        assertEquals(0, KitchenEngine.stars(0.2f))
        assertEquals(1f, KitchenEngine.cookScore(1.1f))
        assertTrue(KitchenEngine.cookScore(0.5f) < 0.5f)
        assertTrue(KitchenEngine.cookScore(1.7f) < 0.8f)
    }

    @Test
    fun snapshotRoundTrip_restartsRunningStep() {
        val e = engine()
        e.move(Station.Crate(ItemKind.EGG), Station.Stove(0))
        e.solveTask()
        e.move(Station.Crate(ItemKind.APPLE), Station.Board) // Schälen läuft
        repeat(30) { e.update(DT) }
        val json = KitchenSaves.encode(e.snapshot(), bestAtStart = 5, seed = e.seed)
        val save = KitchenSaves.decode(json)!!
        val copy = KitchenEngine(save.seed, save.snapshot.levelId?.let(KitchenCatalog::level)).also { it.restore(save.snapshot) }
        assertTrue(copy.busy)
        assertEquals(MiniGameKind.PEEL, copy.miniGame?.kind)
        assertEquals(e.pans[0], copy.pans[0])
        assertEquals(e.orders.map { it.dishId }, copy.orders.map { it.dishId })
    }

    @Test
    fun perfectCook_reachesThreeStarsInEveryLevel() {
        // Bot kocht jeweils die älteste Bestellung perfekt – die Ziele müssen erreichbar sein.
        for (level in KitchenCatalog.levels) {
            val e = engine(level.id, seed = 11)
            var guard = 0
            while (!e.isFinished && guard++ < 200_000) {
                val order = e.orders.minByOrNull { it.remaining }
                if (order == null) { e.update(DT); continue }
                cook(e, order.dish)
            }
            println("BOT ${level.id}: score=${e.score} served=${e.served} missed=${e.missed} ziele=${level.starTargets}")
            assertTrue("${level.id}: ${e.score} < ${level.starTargets}", e.score >= level.starTargets[2])
        }
    }

    private var humanDelay = 0f
    private var humanQuality = 1f

    @Test
    fun humanLikeCook_reachesAtLeastOneStar() {
        humanDelay = 1.2f
        humanQuality = 0.75f
        for (level in KitchenCatalog.levels) {
            val e = engine(level.id, seed = 5)
            var guard = 0
            while (!e.isFinished && guard++ < 200_000) {
                val order = e.orders.minByOrNull { it.remaining }
                if (order == null) { e.update(DT); continue }
                cook(e, order.dish)
            }
            println("MENSCH ${level.id}: score=${e.score} served=${e.served} missed=${e.missed} ziele=${level.starTargets}")
        }
        humanDelay = 0f
        humanQuality = 1f
    }

    private fun KitchenEngine.wait(seconds: Float) {
        var t = 0f
        while (t < seconds && !isFinished) { update(DT); t += DT }
    }

    /** Kocht ein Gericht komplett (für den Bot-Test); bricht ab, wenn die Schicht endet. */
    private fun cook(e: KitchenEngine, dish: Dish) {
        val plate = e.plates.indexOfFirst { it.components.isEmpty() }.coerceAtLeast(0)
        if (e.plates[plate].components.isNotEmpty()) e.move(Station.PlateSpot(plate), Station.Trash)
        for (component in dish.components) {
            if (e.isFinished) return
            produce(e, component)
            val source = when {
                e.board?.kind == component -> Station.Board
                e.pans[0].item != null -> Station.Stove(0)
                else -> Station.Bowl
            }
            e.wait(humanDelay)
            e.move(source, Station.PlateSpot(plate))
            e.solveTask()
        }
        if (e.plates[plate].isReady) e.move(Station.PlateSpot(plate), Station.Serve)
    }

    private fun produce(e: KitchenEngine, kind: ItemKind) {
        KitchenCatalog.boardRules.firstOrNull { it.output == kind }?.let {
            e.move(Station.Crate(it.input), Station.Board)
            e.solveTask()
            return
        }
        KitchenCatalog.panRules.firstOrNull { it.output == kind }?.let { rule ->
            if (rule.input.isRaw) {
                e.move(Station.Crate(rule.input), Station.Stove(0))
            } else {
                produce(e, rule.input)
                val from = if (e.board?.kind == rule.input) Station.Board else Station.Bowl
                e.move(from, Station.Stove(0))
            }
            e.solveTask()
            e.cookUntil(0, 1.05f)
            return
        }
        KitchenCatalog.bowlRecipes.firstOrNull { it.output == kind }?.let { r ->
            r.ingredients.forEach { (k, n) -> repeat(n) { e.move(Station.Crate(k), Station.Bowl); e.solveTask() } }
            e.tap(Station.Bowl)
            e.solveTask()
        }
    }
}
