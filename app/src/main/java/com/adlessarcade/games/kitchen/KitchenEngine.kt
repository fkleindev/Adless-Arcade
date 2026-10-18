package com.adlessarcade.games.kitchen

import kotlin.math.roundToInt
import kotlin.random.Random

sealed class KitchenEvent {
    data class Served(val result: ServeResult) : KitchenEvent()
    data class Missed(val dish: Dish) : KitchenEvent()
    data class NewOrder(val dish: Dish) : KitchenEvent()
    data class StepDone(val kind: MiniGameKind, val accuracy: Float) : KitchenEvent()
    data object MamaHelps : KitchenEvent()
    data class Burnt(val stove: Int) : KitchenEvent()
    data class Rejected(val reason: String) : KitchenEvent()
}

/**
 * Logik einer Küchenschicht: Bestellungen, Stationen, Garzeiten, Minispiele und Punkte. Ohne Android-Abhängigkeiten.
 * [level] = null → Endlos-Modus.
 */
class KitchenEngine(val seed: Long, val level: LevelDef?) {
    var time = 0f
        private set
    var score = 0
        private set
    var strikes = 0
        private set
    var combo = 0
        private set
    var served = 0
        private set
    var missed = 0
        private set
    var bestStreak = 0
        private set
    var streak = 0
        private set
    var coinsEarned = 0
        private set
    var threeStarOrders = 0
        private set
    var isFinished = false
        private set

    val orders = ArrayList<Order>()
    var board: Item? = null
        private set
    val bowl = ArrayList<Item>()
    var bowlProduct: Item? = null
        private set
    val pans = Array(KitchenCatalog.STOVES) { Pan() }
    val plates = Array(KitchenCatalog.PLATES) { Plate() }

    var task: Task? = null
        private set
    var miniGame: MiniGame? = null
        private set
    /** Restzeit der Mama-Animation; > 0 = Mama rettet gerade den Schritt. */
    var mamaTimer = 0f
        private set
    /** Restzeit der Abschluss-Animation eines gelungenen Schritts; < 0 = keine. */
    var finishLeft = -1f
        private set

    /** 0..1 während der Abschluss-Animation (z. B. Pfannkuchen fliegt beim Wenden). */
    val finishProgress: Float get() = if (finishLeft < 0f) 0f else 1f - finishLeft / FINISH_TIME

    /** Seitenverhältnis der Minispiel-Fläche (vom Bildschirm gesetzt). */
    var aspect = 1.8f

    private var nextOrderId = 1
    private var spawnTimer = 1.5f
    private var rngCounter = 0L
    private val events = ArrayList<KitchenEvent>()

    val isEndless: Boolean get() = level == null
    val busy: Boolean get() = task != null

    val dishPool: List<String> get() = level?.dishes ?: KitchenCatalog.dishes.map { it.id }

    /** Schwierigkeit 0..1: im Level fest, im Endlos-Modus mit den servierten Aufträgen steigend. */
    val difficulty: Float get() = level?.difficulty ?: (served / 25f).coerceAtMost(1f)

    private val spawnInterval: Float get() = level?.spawnInterval ?: (24f - served * 0.5f).coerceAtLeast(10f)
    private val orderTime: Float get() = level?.orderTime ?: (85f - served * 1.2f).coerceAtLeast(45f)
    private val maxOrders: Int get() = level?.maxOrders ?: (2 + served / 6).coerceAtMost(4)

    /** Restzeit der Schicht (Level), sonst null. */
    val remainingTime: Float? get() = level?.let { (it.duration - time).coerceAtLeast(0f) }

    private fun nextRandom() = Random(seed * 31 + (rngCounter++) * 7919)

    fun drainEvents(): List<KitchenEvent> {
        if (events.isEmpty()) return emptyList()
        val copy = ArrayList(events)
        events.clear()
        return copy
    }

    // --- Ablauf ---

    fun update(dt: Float) {
        if (isFinished) return
        time += dt
        updateOrders(dt)
        updatePans(dt)
        updateMiniGame(dt)
        level?.let { if (time >= it.duration) isFinished = true }
    }

    private fun updateOrders(dt: Float) {
        val iterator = orders.iterator()
        while (iterator.hasNext()) {
            val order = iterator.next()
            order.remaining -= dt
            if (order.remaining <= 0f) {
                iterator.remove()
                missed++
                combo = 0
                streak = 0
                events += KitchenEvent.Missed(order.dish)
                if (isEndless) {
                    strikes++
                    if (strikes >= MAX_STRIKES) isFinished = true
                } else {
                    score = (score - MISSED_PENALTY).coerceAtLeast(0)
                }
            }
        }
        spawnTimer -= dt
        if (orders.isEmpty()) spawnTimer = minOf(spawnTimer, 2f)
        if (spawnTimer <= 0f && orders.size < maxOrders) {
            spawnOrder()
            spawnTimer = spawnInterval
        }
    }

    private fun spawnOrder() {
        val pool = dishPool
        val rng = nextRandom()
        // Nicht zu oft dasselbe Gericht hintereinander
        val recent = orders.map { it.dishId }.toSet()
        val choices = pool.filter { it !in recent }.ifEmpty { pool }
        val dish = KitchenCatalog.dish(choices[rng.nextInt(choices.size)])
        // Aufwendigere Gerichte bekommen mehr Zeit
        val limit = orderTime * (0.7f + dish.basePoints / 200f)
        orders += Order(nextOrderId++, dish.id, limit, limit)
        events += KitchenEvent.NewOrder(dish)
    }

    private fun updatePans(dt: Float) {
        for (i in pans.indices) {
            val pan = pans[i]
            val item = pan.item ?: continue
            if (item.kind == ItemKind.BURNT) continue
            val rule = pan.rule ?: continue
            val progress = pan.progress + dt / rule.cookTime
            pans[i] = if (progress >= KitchenCatalog.BURN_AT) {
                events += KitchenEvent.Burnt(i)
                pan.copy(item = Item(ItemKind.BURNT), progress = progress)
            } else {
                pan.copy(progress = progress)
            }
        }
    }

    private fun updateMiniGame(dt: Float) {
        val current = task ?: return
        if (mamaTimer > 0f) {
            mamaTimer -= dt
            if (mamaTimer <= 0f) resolve(current, 0f)
            return
        }
        val game = miniGame ?: startMiniGame(current)
        if (finishLeft >= 0f) {
            finishLeft -= dt
            if (finishLeft <= 0f) resolve(current, game.accuracy)
            return
        }
        game.update(dt)
        if (game.done) {
            if (game.failed) {
                mamaTimer = MAMA_TIME
                events += KitchenEvent.MamaHelps
            } else {
                events += KitchenEvent.StepDone(game.kind, game.accuracy)
                finishLeft = FINISH_TIME
            }
        }
    }

    private fun startMiniGame(t: Task): MiniGame {
        val kinds = (t as? Task.Plating)?.let { KitchenCatalog.dish(it.dishId).components } ?: emptyList()
        val game = MiniGame.create(t.kind, difficulty, aspect, seed * 977 + (rngCounter++), kinds)
        task = t
        miniGame = game
        return game
    }

    private fun begin(t: Task) {
        task = t
        miniGame = null
        startMiniGame(t)
    }

    /** Ergebnis eines Minispiels (oder 0 nach Mamas Hilfe) auf die Station anwenden. */
    private fun resolve(t: Task, accuracy: Float) {
        task = null
        miniGame = null
        mamaTimer = 0f
        finishLeft = -1f
        when (t) {
            is Task.BoardPrep -> {
                val item = t.item.with(accuracy)
                val rest = t.remaining.drop(1)
                if (rest.isNotEmpty()) {
                    begin(Task.BoardPrep(item, rest))
                } else {
                    val rule = KitchenCatalog.boardRule(t.item.kind)
                    board = Item(rule?.output ?: t.item.kind, item.scores)
                }
            }
            is Task.BowlAdd -> bowl += t.item.with(accuracy)
            is Task.BowlMix -> {
                bowlProduct = Item(t.output, bowl.flatMap { it.scores } + accuracy)
                bowl.clear()
            }
            is Task.PanStart -> pans[t.stove] = Pan(t.item.with(accuracy))
            is Task.PanFlip -> {
                val pan = pans[t.stove]
                val item = pan.item ?: return
                pans[t.stove] = pan.copy(item = item.with(accuracy), flipped = true)
            }
            is Task.Plating -> {
                val plate = plates[t.plate]
                val rest = t.remaining.drop(1)
                val updated = plate.copy(scores = plate.scores + accuracy)
                plates[t.plate] = if (rest.isEmpty()) updated.copy(dishId = t.dishId) else updated
                if (rest.isNotEmpty()) begin(t.copy(remaining = rest))
            }
        }
    }

    // --- Eingaben in der Küche ---

    /** Kann von hier etwas weggezogen werden? */
    fun canPick(from: Station): Boolean {
        if (busy || isFinished) return false
        return when (from) {
            is Station.Crate -> true
            Station.Board -> board != null
            Station.Bowl -> bowlProduct != null
            is Station.Stove -> pans[from.index].item != null
            is Station.PlateSpot -> plates[from.index].components.isNotEmpty()
            Station.Serve, Station.Trash -> false
        }
    }

    /** Was beim Ziehen am Finger hängt (für die Anzeige). */
    fun carried(from: Station): Item? = when (from) {
        is Station.Crate -> Item(from.kind)
        Station.Board -> board
        Station.Bowl -> bowlProduct
        is Station.Stove -> pans[from.index].let { pan -> pan.item?.let { cookedItem(pan) } }
        else -> null
    }

    /** Verschiebt den Inhalt von [from] nach [to]. Liefert false, wenn das Ziel ihn nicht annimmt. */
    fun move(from: Station, to: Station): Boolean {
        if (!canPick(from) || from == to) return false
        if (from is Station.PlateSpot) return movePlate(from.index, to)
        val item = carried(from) ?: return false
        if (!accept(item, to)) return false
        when (from) {
            Station.Board -> board = null
            Station.Bowl -> bowlProduct = null
            is Station.Stove -> pans[from.index] = Pan()
            else -> Unit
        }
        place(item, to)
        return true
    }

    private fun reject(reason: String): Boolean {
        events += KitchenEvent.Rejected(reason)
        return false
    }

    private fun accept(item: Item, to: Station): Boolean = when (to) {
        Station.Trash -> true
        Station.Board -> when {
            board != null -> reject("Das Brett ist belegt")
            KitchenCatalog.boardRule(item.kind) == null -> reject("${item.kind.title} muss nicht aufs Brett")
            else -> true
        }
        Station.Bowl -> when {
            bowlProduct != null -> reject("Erst die Schüssel leeren")
            !KitchenCatalog.bowlAccepts(bowl.map { it.kind }, item.kind) -> reject("${item.kind.title} passt nicht in die Schüssel")
            else -> true
        }
        is Station.Stove -> when {
            pans[to.index].item != null -> reject("Die Pfanne ist belegt")
            KitchenCatalog.panRule(item.kind) == null -> reject("${item.kind.title} gehört nicht in die Pfanne")
            else -> true
        }
        is Station.PlateSpot -> {
            val plate = plates[to.index]
            when {
                item.kind == ItemKind.BURNT -> reject("Verbranntes gehört in den Müll")
                plate.isReady -> reject("Der Teller ist schon fertig")
                KitchenCatalog.dishesFor(plate.components.map { it.kind } + item.kind, dishPool).isEmpty() ->
                    reject("${item.kind.title} passt auf keinen bestellten Teller")
                else -> true
            }
        }
        is Station.Crate, Station.Serve -> reject("Hier kann nichts abgelegt werden")
    }

    private fun place(item: Item, to: Station) {
        when (to) {
            Station.Trash -> Unit
            Station.Board -> begin(Task.BoardPrep(item, KitchenCatalog.boardRule(item.kind)!!.steps))
            Station.Bowl -> begin(Task.BowlAdd(item, KitchenCatalog.bowlAdd.getValue(item.kind)))
            is Station.Stove -> {
                val rule = KitchenCatalog.panRule(item.kind)!!
                if (rule.onStart != null) begin(Task.PanStart(to.index, item, rule.onStart)) else pans[to.index] = Pan(item)
            }
            is Station.PlateSpot -> {
                val plate = plates[to.index]
                val components = plate.components + item
                plates[to.index] = plate.copy(components = components)
                KitchenCatalog.completedDish(components.map { it.kind }, dishPool)?.let { dish ->
                    begin(Task.Plating(to.index, dish.id, dish.plating))
                }
            }
            else -> Unit
        }
    }

    private fun movePlate(index: Int, to: Station): Boolean {
        val plate = plates[index]
        return when (to) {
            Station.Trash -> {
                plates[index] = Plate()
                true
            }
            Station.Serve -> {
                val dishId = plate.dishId ?: return reject("Der Teller ist noch nicht fertig")
                val order = orders.filter { it.dishId == dishId }.minByOrNull { it.remaining }
                    ?: return reject("Niemand hat ${KitchenCatalog.dish(dishId).name} bestellt")
                serve(order, plate)
                plates[index] = Plate()
                true
            }
            is Station.PlateSpot -> {
                if (plates[to.index].components.isNotEmpty()) return reject("Der Teller ist belegt")
                plates[to.index] = plate
                plates[index] = Plate()
                true
            }
            else -> reject("Teller zur Ausgabe ziehen")
        }
    }

    /** Antippen: Schüssel verrühren, Pfanne wenden. */
    fun tap(station: Station): Boolean {
        if (busy || isFinished) return false
        return when (station) {
            Station.Bowl -> {
                if (bowl.isEmpty() || bowlProduct != null) return false
                val recipe = KitchenCatalog.bowlRecipe(bowl.map { it.kind })
                    ?: return reject("Für den Teig fehlt noch etwas")
                begin(Task.BowlMix(recipe.output))
                true
            }
            is Station.Stove -> {
                if (!pans[station.index].canFlip) return false
                begin(Task.PanFlip(station.index))
                true
            }
            else -> false
        }
    }

    // --- Minispiel-Eingaben ---

    fun gameDown(x: Float, y: Float) { if (mamaTimer <= 0f) miniGame?.down(x, y) }
    fun gameMove(x: Float, y: Float) { if (mamaTimer <= 0f) miniGame?.move(x, y) }
    fun gameUp(x: Float, y: Float) { if (mamaTimer <= 0f) miniGame?.up(x, y) }

    // --- Garen und Servieren ---

    /** Gegartes Teil beim Herausnehmen: Qualität aus dem Garzeitpunkt, verbrannt ab [KitchenCatalog.BURN_AT]. */
    private fun cookedItem(pan: Pan): Item {
        val item = pan.item!!
        if (item.kind == ItemKind.BURNT) return item
        val rule = pan.rule ?: return item
        var score = cookScore(pan.progress)
        if (rule.flip && !pan.flipped) score *= 0.6f
        return Item(rule.output, item.scores + score)
    }

    private fun serve(order: Order, plate: Plate) {
        val scores = plate.components.flatMap { it.scores } + plate.scores
        val quality = if (scores.isEmpty()) 1f else scores.average().toFloat()
        val dish = order.dish
        val stars = stars(quality)
        combo = if (stars == 3) combo + 1 else 0
        val multiplier = 1f + 0.1f * (combo - 1).coerceIn(0, 10)
        val points = ((dish.basePoints * (0.3f + 0.7f * quality) + dish.basePoints * 0.4f * order.timeFraction) * multiplier).roundToInt()
        score += points
        served++
        streak++
        bestStreak = maxOf(bestStreak, streak)
        if (stars == 3) threeStarOrders++
        coinsEarned += 1 + if (stars == 3) 1 else 0
        orders.remove(order)
        events += KitchenEvent.Served(ServeResult(dish, quality, points, stars, combo))
    }

    // --- Spielstand ---

    fun snapshot(): KitchenSnapshot = KitchenSnapshot(
        levelId = level?.id,
        time = time,
        score = score,
        strikes = strikes,
        combo = combo,
        served = served,
        missed = missed,
        streak = streak,
        bestStreak = bestStreak,
        coinsEarned = coinsEarned,
        threeStarOrders = threeStarOrders,
        orders = orders.map { it.copy() },
        board = board,
        bowl = bowl.toList(),
        bowlProduct = bowlProduct,
        pans = pans.toList(),
        plates = plates.toList(),
        task = task,
        nextOrderId = nextOrderId,
        spawnTimer = spawnTimer,
        rngCounter = rngCounter,
    )

    fun restore(s: KitchenSnapshot) {
        time = s.time
        score = s.score
        strikes = s.strikes
        combo = s.combo
        served = s.served
        missed = s.missed
        streak = s.streak
        bestStreak = s.bestStreak
        coinsEarned = s.coinsEarned
        threeStarOrders = s.threeStarOrders
        orders.clear()
        orders += s.orders.filter { KitchenCatalog.findDish(it.dishId) != null }
        board = s.board
        bowl.clear()
        bowl += s.bowl
        bowlProduct = s.bowlProduct
        s.pans.take(pans.size).forEachIndexed { i, p -> pans[i] = p }
        s.plates.take(plates.size).forEachIndexed { i, p -> plates[i] = p }
        nextOrderId = s.nextOrderId
        spawnTimer = s.spawnTimer
        rngCounter = s.rngCounter
        mamaTimer = 0f
        finishLeft = -1f
        miniGame = null
        task = null
        // Ein unterbrochenes Minispiel beginnt von vorn
        s.task?.let(::begin)
    }

    companion object {
        const val MAX_STRIKES = 3
        const val MISSED_PENALTY = 20
        const val MAMA_TIME = 1.8f
        const val FINISH_TIME = 0.55f

        fun stars(quality: Float): Int = when {
            quality >= 0.92f -> 3
            quality >= 0.75f -> 2
            quality >= 0.5f -> 1
            else -> 0
        }

        /** Garqualität: perfekt zwischen 0,95 und 1,3; zu früh linear schlechter, zu lange dunkel. */
        fun cookScore(progress: Float): Float = when {
            progress < 0.95f -> (progress / 0.95f) * 0.7f
            progress <= 1.3f -> 1f
            else -> (1f - (progress - 1.3f) / (KitchenCatalog.BURN_AT - 1.3f) * 0.6f).coerceIn(0.3f, 1f)
        }
    }
}
