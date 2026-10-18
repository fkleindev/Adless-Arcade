package com.adlessarcade.games.runetactics

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

/** Ereignisse für Effekte im ViewModel (Projektile, Schadenszahlen …). */
sealed class CombatEvent {
    data class Attack(val from: Int, val to: Int, val ranged: Boolean) : CombatEvent()
    data class Damage(val target: Int, val amount: Int, val magic: Boolean, val crit: Boolean = false) : CombatEvent()
    data class Dodge(val target: Int) : CombatEvent()
    data class Heal(val target: Int, val amount: Int) : CombatEvent()
    data class Cast(val caster: Int, val ability: Ability) : CombatEvent()
    data class Area(val col: Float, val row: Float, val radius: Float, val kind: AbilityKind) : CombatEvent()
    /** Angekündigter Bossangriff: Zone, die nach [delay] Sekunden einschlägt. */
    data class Telegraph(val col: Int, val row: Int, val radius: Int, val delay: Float) : CombatEvent()
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
    /** Gegner-Skalierung (Aufstieg, Kreaturen, Bosse), 1 für den Spieler. */
    scale: Float,
    val items: List<ItemDef> = emptyList(),
    /** Zusatz-Leben und -Angriffstempo in Prozent (Segen, Flüche). */
    hpBonus: Float = 0f,
    speedBonus: Float = 0f,
) {
    val effects: Set<ItemEffect> = items.mapNotNull { it.effect }.toSet()
    val maxHp = (def.hp * Stars.stat(star) * scale + items.sumOf { it.hp.toDouble() }.toFloat()) * (1f + hpBonus)
    var hp = maxHp
    val ad = def.ad * Stars.stat(star) * scale + items.sumOf { it.ad.toDouble() }.toFloat()
    val power = Stars.ability(star) * scale
    val attackSpeed = def.attackSpeed * (1f + speedBonus + items.sumOf { it.attackSpeed.toDouble() }.toFloat())
    val range = def.range + if (ItemEffect.RANGE in effects) 1 else 0
    var armor = def.armor + items.sumOf { it.armor.toDouble() }.toFloat()
    var mr = def.mr + items.sumOf { it.mr.toDouble() }.toFloat()
    val maxMana = def.maxMana.toFloat()
    var mana = def.startMana.toFloat()
    var shield = 0f
    var dodge = 0f
    var damageBonus = 0f
    var abilityBonus = items.sumOf { it.ability.toDouble() }.toFloat()
    var revivePct = 0f
    var revived = false
    var alive = true
    var stun = 0f
    var attackCd = 0f
    var target: Fighter? = null
    var damageDealt = 0f

    var attackCount = 0
    var rageStacks = 0
    var titanStacks = 0
    /** Waldläufer: Tempo-Zuwachs pro Angriff (bis zu 10 Stapel). */
    var rangerStep = 0f
    var rangerStacks = 0
    /** Dämonen: Heilung in Prozent des verursachten Schadens. */
    var vamp = 0f
    var critChance = 0f

    /** Rein visuell (ohne Einfluss auf die Logik): Restzeit der Angriffs- und Treffer-Animation. */
    var attackAnim = 0f
    var hitFlash = 0f
    var sundered = false
    var tickTimer = 0f

    /** Bewegung von (fromCol, fromRow) zum Feld (col, row); 1 = angekommen. */
    var fromCol = col
    var fromRow = row
    var moveT = 1f

    val visualCol: Float get() = fromCol + (col - fromCol) * moveT
    val visualRow: Float get() = fromRow + (row - fromRow) * moveT

    companion object {
        const val ATTACK_ANIM = 0.25f
        const val HIT_FLASH = 0.15f
    }

    init {
        if (ItemEffect.DAMAGE in effects) damageBonus += 0.3f
        if (ItemEffect.AP in effects) abilityBonus += 0.5f
        if (ItemEffect.DODGE in effects) dodge += 0.2f
        if (ItemEffect.SHIELD_START in effects) shield += 300f
        if (ItemEffect.MANA_START in effects && maxMana > 0f) mana = min(maxMana, mana + 40f)
    }
}

/** Angekündigter Einschlag eines Bossangriffs. */
private class Strike(val caster: Fighter, val col: Int, val row: Int, val radius: Int, val damage: Float, val stun: Float) {
    var timer = SLAM_DELAY

    companion object {
        const val SLAM_DELAY = 1.3f
    }
}

/**
 * Deterministische Kampf-Simulation mit festem Takt. Gleicher Seed und gleiche Aufstellung → gleiches Ergebnis.
 */
class RuneTacticsCombat(
    players: List<Pair<UnitInstance, Cell>>,
    enemies: List<PlacedUnit>,
    enemyScale: Float,
    seed: Long,
    private val modifiers: CombatModifiers = CombatModifiers(),
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
    private val strikes = ArrayList<Strike>()

    init {
        var id = 0
        val curse = modifiers.curse
        players.forEach { (unit, cell) ->
            val f = Fighter(
                id++, 0, unit.def, unit.star, cell.col, cell.row, 1f, unit.items.map(RuneTacticsItems::byId),
                hpBonus = modifiers.playerHpPct, speedBonus = modifiers.playerAttackSpeed,
            )
            playerUids[f.id] = unit.uid
            fighters += f
        }
        enemies.forEach {
            fighters += Fighter(
                id++, 1, it.def, it.star, it.col, it.row, enemyScale * it.scale, it.items.map(RuneTacticsItems::byId),
                speedBonus = if (curse == Curse.BLOOD_MOON) 0.25f else 0f,
            )
        }
        applyTraits(0)
        applyTraits(1)
        applyModifiers()
        assassinJumps()
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

    fun survivingEnemyStars(): Int = fighters.filter { it.side == 1 && it.alive }.sumOf { if (it.def.isBoss) 5 else it.star }

    /** Simuliert bis zum Ende (für Tests und Fortsetzen). */
    fun runToEnd() {
        while (!isOver) step()
    }

    fun step() {
        if (isOver) return
        time += TICK
        updateStrikes()
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
        val emblems = if (side == 0) modifiers.emblems else emptyMap()
        val levels = Traits.count(team.map { it.def }, emblems).mapValues { (trait, n) -> trait.level(n) }
        fun value(t: Trait) = t.value(levels[t] ?: 0)
        for (f in team) {
            val traits = f.def.traits
            if (Trait.UNDEAD in traits) f.revivePct = value(Trait.UNDEAD)
            if (Trait.ELF in traits) f.dodge += value(Trait.ELF)
            if (Trait.DWARF in traits) {
                f.armor += value(Trait.DWARF)
                f.mr += value(Trait.DWARF)
            }
            if (Trait.DEMON in traits) f.vamp += value(Trait.DEMON)
            if (Trait.ELEMENTAL in traits && f.maxMana > 0f) f.mana = min(f.maxMana, f.mana + value(Trait.ELEMENTAL))
            if (Trait.WARRIOR in traits) f.damageBonus += value(Trait.WARRIOR)
            if (Trait.MAGE in traits) f.abilityBonus += value(Trait.MAGE)
            if (Trait.RANGER in traits) f.rangerStep = value(Trait.RANGER)
            if (Trait.ASSASSIN in traits) f.critChance += value(Trait.ASSASSIN)
        }
        val guardShield = value(Trait.GUARDIAN)
        if (guardShield > 0f) {
            val guards = team.filter { Trait.GUARDIAN in it.def.traits }
            for (f in team) {
                if (guards.any { dist(it, f) <= 1 }) f.shield += guardShield
            }
        }
    }

    /** Aktive Assassinen springen zu Kampfbeginn neben den hintersten Gegner. */
    private fun assassinJumps() {
        for (f in fighters) {
            if (f.critChance <= 0f || Trait.ASSASSIN !in f.def.traits) continue
            val enemies = fighters.filter { it.side != f.side }
            // Hinterste Reihe aus Sicht des Assassinen: Spieler springen nach oben, Gegner nach unten.
            val back = enemies.maxWithOrNull(
                compareBy<Fighter>({ if (f.side == 0) -it.row else it.row }, { -abs(it.col - f.col) }, { -it.id }),
            ) ?: continue
            val landing = freeNeighbour(back.col, back.row) ?: continue
            f.col = landing.col
            f.row = landing.row
            f.fromCol = landing.col
            f.fromRow = landing.row
            f.target = back
        }
    }

    /** Segen für die eigene Seite, Flüche für den Kampfbeginn. */
    private fun applyModifiers() {
        val curse = modifiers.curse
        for (f in fighters) {
            if (f.side == 0) {
                f.damageBonus += modifiers.playerDamage
                f.shield += modifiers.playerShield
                if (f.maxMana > 0f) f.mana = min(f.maxMana, f.mana + modifiers.playerStartMana)
                if (curse == Curse.SILENCE) f.mana = 0f
            } else if (curse == Curse.BONE_ARMOR) {
                f.shield += f.maxHp * 0.15f
            }
        }
        if (curse == Curse.AMBUSH) {
            // Die zwei stärksten Gegner landen in der hintersten eigenen Reihe.
            val ambushers = fighters.filter { it.side == 1 && !it.def.isBoss }
                .sortedWith(compareByDescending<Fighter>({ it.def.cost * Stars.copies(it.star) }).thenBy { it.id })
                .take(2)
            for (f in ambushers) {
                val occupied = fighters.filter { it !== f }.map { Cell(it.col, it.row) }.toSet()
                val cell = (Board.ROWS - 1 downTo Board.PLAYER_FIRST_ROW).firstNotNullOfOrNull { row ->
                    (0 until Board.COLS).sortedBy { abs(it - Board.COLS / 2) }.map { Cell(it, row) }.firstOrNull { it !in occupied }
                } ?: continue
                f.col = cell.col
                f.row = cell.row
                f.fromCol = cell.col
                f.fromRow = cell.row
            }
        }
    }

    private fun update(f: Fighter) {
        f.attackCd = max(0f, f.attackCd - TICK)
        f.attackAnim = max(0f, f.attackAnim - TICK)
        f.hitFlash = max(0f, f.hitFlash - TICK)
        updateItemTimers(f)
        if (!f.alive) return
        if (f.stun > 0f) {
            f.stun -= TICK
            return
        }
        if (f.moveT < 1f) {
            f.moveT = min(1f, f.moveT + TICK / MOVE_TIME)
            if (f.moveT < 1f) return
        }
        val target = acquireTarget(f) ?: return
        val inRange = dist(f, target) <= f.range
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

    /** Regeneration und Brand-Aura laufen auch während Betäubung, einmal pro Sekunde. */
    private fun updateItemTimers(f: Fighter) {
        if (ItemEffect.REGEN !in f.effects && ItemEffect.AURA_BURN !in f.effects) return
        f.tickTimer += TICK
        if (f.tickTimer < 1f) return
        f.tickTimer -= 1f
        if (ItemEffect.REGEN in f.effects) heal(f, f.maxHp * 0.03f)
        if (ItemEffect.AURA_BURN in f.effects) {
            fighters.filter { it.alive && it.side != f.side && dist(it, f) <= 1 }
                .forEach { dealDamage(f, it, 40f, magic = true) }
        }
    }

    private fun updateStrikes() {
        if (strikes.isEmpty()) return
        val iterator = strikes.iterator()
        while (iterator.hasNext()) {
            val s = iterator.next()
            s.timer -= TICK
            if (s.timer > 0f) continue
            iterator.remove()
            if (!s.caster.alive) continue
            events += CombatEvent.Area(s.col.toFloat(), s.row.toFloat(), s.radius.toFloat(), AbilityKind.SLAM)
            fighters.filter { it.alive && it.side != s.caster.side && chebyshev(it.col, it.row, s.col, s.row) <= s.radius }
                .forEach {
                    dealDamage(s.caster, it, s.damage, magic = true)
                    if (s.stun > 0f) stun(it, s.stun)
                }
        }
    }

    private fun acquireTarget(f: Fighter): Fighter? {
        val current = f.target
        if (current != null && current.alive && dist(f, current) <= f.range) return current
        val best = fighters
            .filter { it.alive && it.side != f.side }
            .minWithOrNull(compareBy<Fighter>({ dist(f, it) }, { sq(f, it) }, { it.id }))
        f.target = best
        return best
    }

    private fun attack(f: Fighter, target: Fighter) {
        f.attackCount++
        f.attackAnim = Fighter.ATTACK_ANIM
        if (ItemEffect.RAGE in f.effects && f.rageStacks < 10) f.rageStacks++
        if (f.rangerStep > 0f && f.rangerStacks < 10) f.rangerStacks++
        val speed = f.attackSpeed * (1f + 0.06f * f.rageStacks + f.rangerStep * f.rangerStacks)
        f.attackCd = 1f / speed
        events += CombatEvent.Attack(f.id, target.id, f.range > 1)
        gainMana(f, ATTACK_MANA)
        dealDamage(f, target, f.ad, magic = false, attack = true)
        val third = f.attackCount % 3 == 0
        if (ItemEffect.BURN in f.effects) dealDamage(f, target, target.maxHp * 0.03f, magic = true)
        if (third && ItemEffect.DOUBLE_SHOT in f.effects) dealDamage(f, target, f.ad, magic = false, attack = true)
        if (third && ItemEffect.SPARK in f.effects) {
            fighters.filter { it.alive && it.side != f.side }
                .sortedWith(compareBy<Fighter>({ dist(target, it) }, { it.id }))
                .take(3)
                .forEach {
                    events += CombatEvent.Attack(f.id, it.id, ranged = true)
                    dealDamage(f, it, 90f, magic = true)
                }
        }
    }

    private fun cast(f: Fighter, target: Fighter) {
        val ability = f.def.ability
        events += CombatEvent.Cast(f.id, ability)
        val spell = ability.value * f.power * (1f + f.abilityBonus)
        val enemies = fighters.filter { it.alive && it.side != f.side }
        when (ability.kind) {
            AbilityKind.STRIKE -> dealDamage(f, target, f.ad * ability.value, magic = false)
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
                for (i in 0 until ability.count) {
                    hit += current.id
                    events += CombatEvent.Attack(f.id, current.id, ranged = true)
                    dealDamage(f, current, amount, magic = true)
                    amount *= 0.8f
                    val from = current
                    current = fighters
                        .filter { it.alive && it.side != f.side && it.id !in hit }
                        .minWithOrNull(compareBy<Fighter>({ dist(from, it) }, { it.id })) ?: break
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
            AbilityKind.SLAM -> {
                // Zone dort, wo die meisten Gegner stehen.
                val radius = ability.count
                val center = enemies.maxWithOrNull(
                    compareBy<Fighter>({ c -> enemies.count { dist(it, c) <= radius } }, { -it.id }),
                ) ?: return
                strikes += Strike(f, center.col, center.row, radius, spell, ability.stun)
                events += CombatEvent.Telegraph(center.col, center.row, radius, Strike.SLAM_DELAY)
            }
        }
        if (ItemEffect.CAST_HEAL in f.effects) heal(f, f.maxHp * 0.15f)
    }

    private fun area(col: Int, row: Int, radius: Int, kind: AbilityKind) {
        events += CombatEvent.Area(col.toFloat(), row.toFloat(), radius.toFloat(), kind)
    }

    private fun stun(f: Fighter, seconds: Float) {
        if (f.alive && ItemEffect.STUN_IMMUNE !in f.effects) f.stun = max(f.stun, seconds)
    }

    private fun heal(f: Fighter, amount: Float) {
        if (!f.alive || amount <= 0f) return
        val factor = if (f.side == 0 && modifiers.curse == Curse.ROT) 0.5f else 1f
        val healed = min(amount * factor, f.maxHp - f.hp)
        if (healed <= 0f) return
        f.hp += healed
        events += CombatEvent.Heal(f.id, healed.roundToInt())
    }

    private fun gainMana(f: Fighter, amount: Float) {
        if (f.maxMana > 0f) f.mana = min(f.maxMana, f.mana + amount)
    }

    /**
     * Liefert den tatsächlich verursachten Schaden (inkl. vom Schild absorbiert).
     * [attack] = normaler Angriff: kann ausgewichen werden und löst Angriffs-Effekte aus.
     */
    private fun dealDamage(src: Fighter, dst: Fighter, raw: Float, magic: Boolean, attack: Boolean = false): Float {
        if (!dst.alive) return 0f
        if (attack && dst.dodge > 0f && rng.nextFloat() < dst.dodge) {
            events += CombatEvent.Dodge(dst.id)
            return 0f
        }
        if (attack && ItemEffect.SUNDER in src.effects && !dst.sundered) {
            dst.sundered = true
            dst.armor *= 0.6f
        }
        val crit = attack && src.critChance > 0f && rng.nextFloat() < src.critChance
        val boosted = raw * (1f + src.damageBonus + src.titanStacks * 0.015f) * overtime() * if (crit) CRIT_MULTIPLIER else 1f
        val resist = max(0f, if (magic) dst.mr else dst.armor)
        var amount = boosted * 100f / (100f + resist)
        if (ItemEffect.DMG_REDUCE in dst.effects) amount *= 0.85f
        if (magic && ItemEffect.MAGIC_REDUCE in dst.effects) amount *= 0.75f
        gainMana(dst, min(MAX_DAMAGE_MANA, boosted * DAMAGE_MANA))
        if (ItemEffect.TITAN in dst.effects && dst.titanStacks < 25) dst.titanStacks++
        val total = amount
        val absorbed = min(dst.shield, amount)
        dst.shield -= absorbed
        amount -= absorbed
        dst.hp -= amount
        if (total > 0f) dst.hitFlash = Fighter.HIT_FLASH
        src.damageDealt += total
        events += CombatEvent.Damage(dst.id, total.roundToInt(), magic, crit)
        if (dst.hp <= 0f) kill(dst)
        if (src.alive) {
            if (ItemEffect.OMNIVAMP in src.effects) heal(src, total * 0.2f)
            if (src.vamp > 0f) heal(src, total * src.vamp)
            if (attack && ItemEffect.LIFESTEAL in src.effects) heal(src, total * 0.3f)
        }
        if (attack && ItemEffect.THORNS in dst.effects && src.alive) {
            dealDamage(dst, src, total * 0.3f, magic = true)
        }
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
            if (cur != start && chebyshev(c, r, target.col, target.row) <= f.range) {
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
        const val CRIT_MULTIPLIER = 1.6f

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
