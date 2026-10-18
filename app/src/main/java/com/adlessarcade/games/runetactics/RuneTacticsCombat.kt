package com.adlessarcade.games.runetactics

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

/** Ereignisse für Effekte im ViewModel (Projektile, Schadenszahlen …). */
sealed class CombatEvent {
    data class Attack(val from: Int, val to: Int, val ranged: Boolean) : CombatEvent()
    data class Damage(val target: Int, val amount: Int, val magic: Boolean) : CombatEvent()
    data class Dodge(val target: Int) : CombatEvent()
    data class Heal(val target: Int, val amount: Int) : CombatEvent()
    data class Cast(val caster: Int, val ability: Ability) : CombatEvent()
    data class Area(val col: Float, val row: Float, val radius: Float, val kind: AbilityKind) : CombatEvent()
    data class Death(val unit: Int) : CombatEvent()
    data class Revive(val unit: Int) : CombatEvent()
}

/** Laufzeit-Zustand einer Einheit im Kampf. */
class Fighter(
    val id: Int,
    /** 0 = Spieler (unten), 1 = Gegner (oben). */
    val side: Int,
    val def: UnitDef,
    val star: Int,
    var col: Int,
    var row: Int,
    /** Gegner-Skalierung (Aufstieg), 1 für den Spieler. */
    scale: Float,
) {
    val maxHp = def.hp * Stars.stat(star) * scale
    var hp = maxHp
    val ad = def.ad * Stars.stat(star) * scale
    val power = Stars.ability(star) * scale
    var armor = def.armor
    var mr = def.mr
    val maxMana = def.maxMana.toFloat()
    var mana = def.startMana.toFloat()
    var shield = 0f
    var dodge = 0f
    var damageBonus = 0f
    var abilityBonus = 0f
    var revivePct = 0f
    var revived = false
    var alive = true
    var stun = 0f
    var attackCd = 0f
    var target: Fighter? = null
    var damageDealt = 0f

    /** Bewegung von (fromCol, fromRow) zum Feld (col, row); 1 = angekommen. */
    var fromCol = col
    var fromRow = row
    var moveT = 1f

    val visualCol: Float get() = fromCol + (col - fromCol) * moveT
    val visualRow: Float get() = fromRow + (row - fromRow) * moveT
}

/**
 * Deterministische Kampf-Simulation mit festem Takt. Gleicher Seed und gleiche Aufstellung → gleiches Ergebnis.
 */
class RuneTacticsCombat(
    players: List<Pair<UnitInstance, Cell>>,
    enemies: List<PlacedUnit>,
    enemyScale: Float,
    seed: Long,
) {
    val fighters = ArrayList<Fighter>()
    var time = 0f
        private set
    var outcome: Outcome? = null
        private set
    val isOver: Boolean get() = outcome != null

    /** Uid der eigenen Einheit pro Kämpfer-ID (für die Schadensstatistik). */
    val playerUids = HashMap<Int, Long>()

    private val rng = Random(seed)
    private val events = ArrayList<CombatEvent>()

    init {
        var id = 0
        players.forEach { (unit, cell) ->
            val f = Fighter(id++, 0, unit.def, unit.star, cell.col, cell.row, 1f)
            playerUids[f.id] = unit.uid
            fighters += f
        }
        enemies.forEach { fighters += Fighter(id++, 1, it.def, it.star, it.col, it.row, enemyScale) }
        applyTraits(0)
        applyTraits(1)
        // Leicht versetzte erste Angriffe, damit nicht alle im Gleichtakt schlagen.
        fighters.forEach { it.attackCd = rng.nextFloat() * 0.3f }
        if (fighters.none { it.side == 0 }) outcome = Outcome.LOSS
        else if (fighters.none { it.side == 1 }) outcome = Outcome.WIN
    }

    fun drainEvents(): List<CombatEvent> {
        if (events.isEmpty()) return emptyList()
        val copy = ArrayList(events)
        events.clear()
        return copy
    }

    fun find(id: Int): Fighter? = fighters.getOrNull(id)

    fun survivingEnemyStars(): Int = fighters.filter { it.side == 1 && it.alive }.sumOf { it.star }

    /** Simuliert bis zum Ende (für Tests und Fortsetzen). */
    fun runToEnd() {
        while (!isOver) step()
    }

    fun step() {
        if (isOver) return
        time += TICK
        for (f in fighters) {
            if (!f.alive) continue
            update(f)
            if (isOver) return
        }
        checkEnd()
    }

    private fun checkEnd() {
        val playersAlive = fighters.any { it.side == 0 && it.alive }
        val enemiesAlive = fighters.any { it.side == 1 && it.alive }
        outcome = when {
            !playersAlive -> Outcome.LOSS
            !enemiesAlive -> Outcome.WIN
            time >= TIME_LIMIT -> Outcome.DRAW
            else -> null
        }
    }

    private fun applyTraits(side: Int) {
        val team = fighters.filter { it.side == side }
        val levels = Traits.count(team.map { it.def }).mapValues { (trait, n) -> trait.level(n) }
        fun lvl(t: Trait) = levels[t] ?: 0
        for (f in team) {
            val traits = f.def.traits
            if (Trait.UNDEAD in traits) f.revivePct = floatArrayOf(0f, 0.3f, 0.6f)[lvl(Trait.UNDEAD)]
            if (Trait.ELF in traits) f.dodge = floatArrayOf(0f, 0.2f, 0.4f)[lvl(Trait.ELF)]
            if (Trait.DWARF in traits) {
                val bonus = floatArrayOf(0f, 30f, 70f)[lvl(Trait.DWARF)]
                f.armor += bonus
                f.mr += bonus
            }
            if (Trait.WARRIOR in traits) f.damageBonus = floatArrayOf(0f, 0.2f, 0.45f)[lvl(Trait.WARRIOR)]
            if (Trait.MAGE in traits) f.abilityBonus = floatArrayOf(0f, 0.3f, 0.7f)[lvl(Trait.MAGE)]
        }
        val guardShield = floatArrayOf(0f, 150f, 350f)[lvl(Trait.GUARDIAN)]
        if (guardShield > 0f) {
            val guards = team.filter { Trait.GUARDIAN in it.def.traits }
            for (f in team) {
                if (guards.any { dist(it, f) <= 1 }) f.shield = max(f.shield, guardShield)
            }
        }
    }

    private fun update(f: Fighter) {
        f.attackCd = max(0f, f.attackCd - TICK)
        if (f.stun > 0f) {
            f.stun -= TICK
            return
        }
        if (f.moveT < 1f) {
            f.moveT = min(1f, f.moveT + TICK / MOVE_TIME)
            if (f.moveT < 1f) return
        }
        val target = acquireTarget(f) ?: return
        val inRange = dist(f, target) <= f.def.range
        if (f.maxMana > 0f && f.mana >= f.maxMana && (inRange || !f.def.ability.needsTarget)) {
            f.mana = 0f
            cast(f, target)
            return
        }
        if (inRange) {
            if (f.attackCd <= 0f) attack(f, target)
        } else {
            moveTowards(f, target)
        }
    }

    private fun acquireTarget(f: Fighter): Fighter? {
        val current = f.target
        if (current != null && current.alive && dist(f, current) <= f.def.range) return current
        val best = fighters
            .filter { it.alive && it.side != f.side }
            .minWithOrNull(compareBy<Fighter>({ dist(f, it) }, { sq(f, it) }, { it.id }))
        f.target = best
        return best
    }

    private fun attack(f: Fighter, target: Fighter) {
        f.attackCd = 1f / f.def.attackSpeed
        events += CombatEvent.Attack(f.id, target.id, f.def.range > 1)
        gainMana(f, ATTACK_MANA)
        dealDamage(f, target, f.ad, magic = false, canDodge = true)
    }

    private fun cast(f: Fighter, target: Fighter) {
        val ability = f.def.ability
        events += CombatEvent.Cast(f.id, ability)
        val spell = ability.value * f.power * (1f + f.abilityBonus)
        val enemies = fighters.filter { it.alive && it.side != f.side }
        when (ability.kind) {
            AbilityKind.STRIKE -> dealDamage(f, target, f.ad * ability.value, magic = false, canDodge = false)
            AbilityKind.BOLT -> dealDamage(f, target, spell, magic = true)
            AbilityKind.SPLASH -> {
                area(target.col, target.row, 1, ability.kind)
                enemies.filter { dist(it, target) <= 1 }.forEach { dealDamage(f, it, spell, magic = true) }
            }
            AbilityKind.WHIRL -> {
                area(f.col, f.row, 1, ability.kind)
                enemies.filter { dist(it, f) <= 1 }.forEach { dealDamage(f, it, spell, magic = true) }
            }
            AbilityKind.SHIELD_SELF -> f.shield += spell
            AbilityKind.HEAL_ALLY -> {
                val ally = fighters.filter { it.alive && it.side == f.side }.minByOrNull { it.hp / it.maxHp } ?: f
                heal(ally, spell)
            }
            AbilityKind.DRAIN -> {
                val dealt = dealDamage(f, target, spell, magic = true)
                heal(f, dealt * 0.5f)
            }
            AbilityKind.QUAKE -> {
                area(f.col, f.row, 1, ability.kind)
                enemies.filter { dist(it, f) <= 1 }.forEach {
                    dealDamage(f, it, spell, magic = true)
                    stun(it, ability.stun)
                }
            }
            AbilityKind.FROST -> {
                area(target.col, target.row, 1, ability.kind)
                enemies.filter { dist(it, target) <= 1 }.forEach {
                    dealDamage(f, it, spell, magic = true)
                    stun(it, ability.stun)
                }
            }
            AbilityKind.CHAIN -> {
                var current = target
                val hit = HashSet<Int>()
                var amount = spell
                repeat(ability.count) {
                    if (!current.alive && hit.isNotEmpty()) return@repeat
                    hit += current.id
                    events += CombatEvent.Attack(f.id, current.id, ranged = true)
                    dealDamage(f, current, amount, magic = true)
                    amount *= 0.8f
                    val next = fighters
                        .filter { it.alive && it.side != f.side && it.id !in hit }
                        .minWithOrNull(compareBy<Fighter>({ dist(current, it) }, { it.id })) ?: return
                    current = next
                }
            }
            AbilityKind.LEAP -> {
                val far = enemies.maxWithOrNull(compareBy<Fighter>({ dist(f, it) }, { -it.id })) ?: return
                val landing = freeNeighbour(far.col, far.row)
                if (landing != null) {
                    f.fromCol = landing.col
                    f.fromRow = landing.row
                    f.col = landing.col
                    f.row = landing.row
                    f.moveT = 1f
                    f.target = far
                }
                area(f.col, f.row, 1, ability.kind)
                enemies.filter { dist(it, f) <= 1 }.forEach {
                    dealDamage(f, it, spell, magic = true)
                    stun(it, ability.stun)
                }
            }
            AbilityKind.MOON -> {
                fighters.filter { it.alive && it.side == f.side }.forEach { it.shield += spell }
                area(f.col, f.row, 2, ability.kind)
                enemies.filter { dist(it, f) <= 2 }.forEach { dealDamage(f, it, spell, magic = true) }
            }
        }
    }

    private fun area(col: Int, row: Int, radius: Int, kind: AbilityKind) {
        events += CombatEvent.Area(col.toFloat(), row.toFloat(), radius.toFloat(), kind)
    }

    private fun stun(f: Fighter, seconds: Float) {
        if (f.alive) f.stun = max(f.stun, seconds)
    }

    private fun heal(f: Fighter, amount: Float) {
        if (!f.alive || amount <= 0f) return
        val healed = min(amount, f.maxHp - f.hp)
        if (healed <= 0f) return
        f.hp += healed
        events += CombatEvent.Heal(f.id, healed.roundToInt())
    }

    private fun gainMana(f: Fighter, amount: Float) {
        if (f.maxMana > 0f) f.mana = min(f.maxMana, f.mana + amount)
    }

    /** Liefert den tatsächlich verursachten Schaden (inkl. vom Schild absorbiert). */
    private fun dealDamage(src: Fighter, dst: Fighter, raw: Float, magic: Boolean, canDodge: Boolean = false): Float {
        if (!dst.alive) return 0f
        if (canDodge && dst.dodge > 0f && rng.nextFloat() < dst.dodge) {
            events += CombatEvent.Dodge(dst.id)
            return 0f
        }
        val boosted = raw * (1f + src.damageBonus) * overtime()
        val resist = max(0f, if (magic) dst.mr else dst.armor)
        var amount = boosted * 100f / (100f + resist)
        gainMana(dst, min(MAX_DAMAGE_MANA, boosted * DAMAGE_MANA))
        val total = amount
        val absorbed = min(dst.shield, amount)
        dst.shield -= absorbed
        amount -= absorbed
        dst.hp -= amount
        src.damageDealt += total
        events += CombatEvent.Damage(dst.id, total.roundToInt(), magic)
        if (dst.hp <= 0f) kill(dst)
        return total
    }

    private fun kill(f: Fighter) {
        if (f.revivePct > 0f && !f.revived) {
            f.revived = true
            f.hp = f.maxHp * f.revivePct
            f.shield = 0f
            f.mana = 0f
            f.stun = 0.5f
            events += CombatEvent.Revive(f.id)
            return
        }
        f.alive = false
        f.hp = 0f
        events += CombatEvent.Death(f.id)
    }

    private fun overtime(): Float = if (time > OVERTIME_START) 1f + OVERTIME_RATE * (time - OVERTIME_START) else 1f

    /** Ein Schritt Richtung Ziel per Breitensuche über freie Felder. */
    private fun moveTowards(f: Fighter, target: Fighter) {
        val occupied = BooleanArray(Board.COLS * Board.ROWS)
        fighters.forEach { if (it.alive && it !== f) occupied[it.row * Board.COLS + it.col] = true }
        val start = f.row * Board.COLS + f.col
        val parent = IntArray(Board.COLS * Board.ROWS) { -1 }
        parent[start] = start
        val queue = ArrayDeque<Int>()
        queue += start
        var goal = -1
        while (queue.isNotEmpty()) {
            val cur = queue.removeFirst()
            val c = cur % Board.COLS
            val r = cur / Board.COLS
            if (cur != start && chebyshev(c, r, target.col, target.row) <= f.def.range) {
                goal = cur
                break
            }
            for ((dc, dr) in DIRECTIONS) {
                val nc = c + dc
                val nr = r + dr
                if (!Board.inside(nc, nr)) continue
                val idx = nr * Board.COLS + nc
                if (parent[idx] != -1 || occupied[idx]) continue
                parent[idx] = cur
                queue += idx
            }
        }
        if (goal == -1) return
        var step = goal
        while (parent[step] != start) step = parent[step]
        f.fromCol = f.col
        f.fromRow = f.row
        f.col = step % Board.COLS
        f.row = step / Board.COLS
        f.moveT = 0f
    }

    private fun freeNeighbour(col: Int, row: Int): Cell? {
        val occupied = fighters.filter { it.alive }.map { Cell(it.col, it.row) }.toSet()
        return DIRECTIONS
            .map { (dc, dr) -> Cell(col + dc, row + dr) }
            .firstOrNull { Board.inside(it.col, it.row) && it !in occupied }
    }

    companion object {
        const val TICK = 0.05f
        const val MOVE_TIME = 0.45f
        const val ATTACK_MANA = 10f
        const val DAMAGE_MANA = 0.03f
        const val MAX_DAMAGE_MANA = 8f
        const val OVERTIME_START = 30f
        const val OVERTIME_RATE = 0.15f
        const val TIME_LIMIT = 45f

        /** Gerade Richtungen zuerst, damit Einheiten natürlich laufen. */
        private val DIRECTIONS = listOf(
            0 to -1, 0 to 1, -1 to 0, 1 to 0,
            -1 to -1, 1 to -1, -1 to 1, 1 to 1,
        )

        fun chebyshev(c1: Int, r1: Int, c2: Int, r2: Int): Int = max(abs(c1 - c2), abs(r1 - r2))

        private fun dist(a: Fighter, b: Fighter): Int = chebyshev(a.col, a.row, b.col, b.row)

        private fun sq(a: Fighter, b: Fighter): Int =
            (a.col - b.col) * (a.col - b.col) + (a.row - b.row) * (a.row - b.row)
    }
}
