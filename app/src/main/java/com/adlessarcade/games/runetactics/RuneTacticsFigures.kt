package com.adlessarcade.games.runetactics

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

enum class FigureWeapon { SWORD, GREATSWORD, AXE, HAMMER, SCYTHE, STAFF, BOW, CROSSBOW, DAGGERS, SPEAR, NONE }

enum class FigureHeadgear { NONE, HELMET, HORNED_HELM, HOOD, HAT, CROWN, CIRCLET }

/** Art eines Elementars: bestimmt Körperfarbe und Krone (Flammen, Fels, Funken …). */
enum class Element { FIRE, ROCK, SPARK, STORM, PRIMAL }

/** Aussehen einer Einheit; Körper ergibt sich aus der Herkunft, Ausrüstung aus der Klasse. */
data class FigureStyle(
    val primary: Color,
    val accent: Color,
    val weapon: FigureWeapon,
    val headgear: FigureHeadgear = FigureHeadgear.NONE,
    val hair: Color = Color(0xFF6B4423),
    val shield: Color? = null,
    val cape: Boolean = false,
    val aura: Boolean = false,
    val wings: Boolean = false,
    val element: Element? = null,
)

object RuneTacticsFigures {
    private val styles: Map<String, FigureStyle> = mapOf(
        // 1 Gold
        "skeleton" to FigureStyle(Color(0xFF6B7280), Color(0xFFB91C1C), FigureWeapon.SWORD),
        "bladedancer" to FigureStyle(Color(0xFF15803D), Color(0xFFFDE68A), FigureWeapon.SWORD, FigureHeadgear.CIRCLET, hair = Color(0xFFFDE68A)),
        "dwarfguard" to FigureStyle(Color(0xFF78716C), Color(0xFFB45309), FigureWeapon.HAMMER, FigureHeadgear.HELMET, hair = Color(0xFF92400E), shield = Color(0xFFB45309)),
        "wisp" to FigureStyle(Color(0xFF4C1D95), Color(0xFF22D3EE), FigureWeapon.STAFF, FigureHeadgear.HOOD),
        "apprentice" to FigureStyle(Color(0xFF2563EB), Color(0xFFF472B6), FigureWeapon.STAFF, FigureHeadgear.HAT, hair = Color(0xFFE5E7EB)),
        "imp" to FigureStyle(Color(0xFF1F2937), Color(0xFFF97316), FigureWeapon.DAGGERS),
        "sparkling" to FigureStyle(Color(0xFFFACC15), Color(0xFFFFFBEB), FigureWeapon.BOW, element = Element.SPARK),
        "huntress" to FigureStyle(Color(0xFF166534), Color(0xFFA3E635), FigureWeapon.BOW, FigureHeadgear.HOOD, hair = Color(0xFFFCD34D)),
        "emberling" to FigureStyle(Color(0xFFF97316), Color(0xFFFDE047), FigureWeapon.AXE, element = Element.FIRE),
        // 2 Gold
        "axeman" to FigureStyle(Color(0xFF9A3412), Color(0xFFD6D3D1), FigureWeapon.AXE, FigureHeadgear.HORNED_HELM, hair = Color(0xFFB45309)),
        "bonewarden" to FigureStyle(Color(0xFF57534E), Color(0xFF8B5CF6), FigureWeapon.SWORD, FigureHeadgear.HELMET, shield = Color(0xFFE7E5E4)),
        "warden" to FigureStyle(Color(0xFF0F766E), Color(0xFFFDE68A), FigureWeapon.SPEAR, FigureHeadgear.CIRCLET, hair = Color(0xFFE5E7EB), shield = Color(0xFF65A30D)),
        "runesmith" to FigureStyle(Color(0xFF7C2D12), Color(0xFFF59E0B), FigureWeapon.HAMMER, hair = Color(0xFF9CA3AF)),
        "shadowblade" to FigureStyle(Color(0xFF111827), Color(0xFF8B5CF6), FigureWeapon.DAGGERS, FigureHeadgear.HOOD),
        "crossbow" to FigureStyle(Color(0xFF3F6212), Color(0xFF92400E), FigureWeapon.CROSSBOW, FigureHeadgear.HELMET, hair = Color(0xFFDC2626)),
        "succubus" to FigureStyle(Color(0xFF831843), Color(0xFFF472B6), FigureWeapon.STAFF, hair = Color(0xFF1F1F1F), wings = true),
        "rockwarden" to FigureStyle(Color(0xFF78716C), Color(0xFF84CC16), FigureWeapon.NONE, shield = Color(0xFF57534E), element = Element.ROCK),
        // 3 Gold
        "deathknight" to FigureStyle(Color(0xFF1E1B4B), Color(0xFF22D3EE), FigureWeapon.GREATSWORD, FigureHeadgear.HORNED_HELM, cape = true),
        "thane" to FigureStyle(Color(0xFF71717A), Color(0xFFFACC15), FigureWeapon.HAMMER, FigureHeadgear.HELMET, hair = Color(0xFFF5F5F4), shield = Color(0xFFCA8A04), cape = true),
        "lich" to FigureStyle(Color(0xFF312E81), Color(0xFF7DD3FC), FigureWeapon.STAFF, FigureHeadgear.CROWN, cape = true, aura = true),
        "demonhunter" to FigureStyle(Color(0xFF3F3F46), Color(0xFFEF4444), FigureWeapon.BOW, FigureHeadgear.HOOD),
        "stormspirit" to FigureStyle(Color(0xFF60A5FA), Color(0xFFE0F2FE), FigureWeapon.DAGGERS, element = Element.STORM),
        "nightstalker" to FigureStyle(Color(0xFF4C0519), Color(0xFFE11D48), FigureWeapon.SCYTHE, FigureHeadgear.HOOD),
        // 4 Gold
        "stormmage" to FigureStyle(Color(0xFF1D4ED8), Color(0xFF67E8F9), FigureWeapon.STAFF, FigureHeadgear.CIRCLET, hair = Color(0xFFE0F2FE), cape = true, aura = true),
        "warlord" to FigureStyle(Color(0xFF7F1D1D), Color(0xFFFBBF24), FigureWeapon.AXE, FigureHeadgear.HORNED_HELM, hair = Color(0xFFEA580C), cape = true, aura = true),
        "fireelemental" to FigureStyle(Color(0xFFEA580C), Color(0xFFFDE047), FigureWeapon.BOW, element = Element.FIRE, aura = true),
        "bladedemon" to FigureStyle(Color(0xFF450A0A), Color(0xFFF87171), FigureWeapon.DAGGERS, wings = true, aura = true),
        // 5 Gold
        "queen" to FigureStyle(Color(0xFFE2E8F0), Color(0xFFFACC15), FigureWeapon.SPEAR, FigureHeadgear.CROWN, hair = Color(0xFFFEF3C7), shield = Color(0xFFCBD5E1), cape = true, aura = true),
        "primal" to FigureStyle(Color(0xFF8B5CF6), Color(0xFF22D3EE), FigureWeapon.BOW, FigureHeadgear.CROWN, element = Element.PRIMAL, aura = true),
        "archdemon" to FigureStyle(Color(0xFF1C1917), Color(0xFFEF4444), FigureWeapon.SCYTHE, FigureHeadgear.CROWN, cape = true, aura = true, wings = true),
    )

    fun style(id: String): FigureStyle? = styles[id]

    /** Einheiten ohne eigenes Aussehen (für den Vollständigkeits-Test). */
    fun missing(): List<String> = RuneTacticsUnits.all.map { it.id }.filter { it !in styles }
}

/** Färbt und blendet alle Farben einer Figur einheitlich (Treffer-Blitz, Transparenz beim Ziehen). */
private class Tint(private val alpha: Float, private val flash: Float) {
    operator fun invoke(color: Color, extraAlpha: Float = 1f): Color =
        lerp(color, Color.White, flash).copy(alpha = (color.alpha * alpha * extraAlpha).coerceIn(0f, 1f))
}

private fun Color.darker(f: Float = 0.35f) = lerp(this, Color.Black, f)
private fun Color.lighter(f: Float = 0.3f) = lerp(this, Color.White, f)

private fun skinColor(origin: Trait, style: FigureStyle): Color = when (origin) {
    Trait.UNDEAD -> Color(0xFFE7E5E4)
    Trait.ELF -> Color(0xFFFDE2C4)
    Trait.DWARF -> Color(0xFFF1C27D)
    Trait.DEMON -> Color(0xFFB91C1C)
    Trait.ELEMENTAL -> style.primary
    else -> Color(0xFFE5C49F)
}

/**
 * Zeichnet eine Figur stehend in einem Feld. [center] ist die Feldmitte, [size] die Feldgröße.
 * [dir] = Seite der Waffenhand (+1 rechts, −1 links), [lunge] ein Versatz beim Angriff, [flash] 0..1 für Treffer.
 */
internal fun DrawScope.drawFigure(
    def: UnitDef,
    center: Offset,
    size: Float,
    time: Float,
    dir: Float = 1f,
    lunge: Offset = Offset.Zero,
    flash: Float = 0f,
    alpha: Float = 1f,
    stunned: Boolean = false,
) {
    val tint = Tint(alpha, flash)
    // Phasenversatz pro Einheit, damit nicht alle im Gleichtakt wippen.
    val phase = (def.id.hashCode() and 0xFF) / 40f + center.x * 0.013f
    val bob = sin(time * 3.2f + phase) * size * 0.02f
    val c = center + lunge + Offset(0f, bob)
    val pivot = center + Offset(0f, size * 0.32f)
    rotate(if (stunned) 12f * dir else 0f, pivot) {
        when {
            def.isBoss -> drawBoss(def, c, size * 0.42f, time, tint)
            def.origin == Trait.BEAST -> drawCreature(def, c, size * 0.3f, time, dir, tint)
            else -> {
                val style = RuneTacticsFigures.style(def.id) ?: FigureStyle(originColor(def.origin), costColor(def.cost), FigureWeapon.SWORD)
                drawHumanoid(def, style, c, size * 0.31f * (0.93f + def.cost * 0.025f), time, dir, tint)
            }
        }
    }
}

private fun DrawScope.drawHumanoid(def: UnitDef, s: FigureStyle, c: Offset, u: Float, time: Float, dir: Float, tint: Tint) {
    val origin = def.origin
    val role = def.role
    val dwarf = origin == Trait.DWARF
    val elemental = origin == Trait.ELEMENTAL
    val skin = skinColor(origin, s)
    val robe = role == Trait.MAGE
    val width = if (dwarf) 1.18f else if (origin == Trait.ELF) 0.9f else 1f

    val footY = c.y + u * 1.05f
    val hipY = c.y + u * (if (dwarf) 0.5f else 0.35f)
    val shoulderY = c.y - u * (if (dwarf) 0.1f else 0.35f)
    val head = Offset(c.x, c.y - u * (if (dwarf) 0.55f else 0.85f))
    val headR = u * (if (dwarf) 0.44f else 0.4f)
    val hand = Offset(c.x + dir * u * 0.62f * width, c.y + u * (if (dwarf) 0.3f else 0.08f))
    val offHand = Offset(c.x - dir * u * 0.62f * width, hand.y)

    // --- Hinter dem Körper ---
    if (s.aura) {
        val pulse = 0.18f + 0.07f * sin(time * 2.5f)
        drawCircle(tint(s.accent, pulse), u * 1.45f, c + Offset(0f, -u * 0.25f))
    }
    if (elemental) drawCircle(tint(s.primary, 0.22f + 0.08f * sin(time * 4f)), u * 1.1f, c + Offset(0f, -u * 0.2f))
    if (s.wings) drawWings(c + Offset(0f, shoulderY - c.y + u * 0.15f), u, time, tint(s.primary.darker(0.2f)), tint(s.accent, 0.6f))
    if (s.cape) {
        val sway = sin(time * 2f + c.x) * u * 0.06f
        val cape = Path().apply {
            moveTo(c.x - u * 0.5f * width, shoulderY)
            lineTo(c.x + u * 0.5f * width, shoulderY)
            lineTo(c.x + u * 0.78f * width + sway, footY - u * 0.05f)
            lineTo(c.x - u * 0.78f * width + sway, footY - u * 0.05f)
            close()
        }
        drawPath(cape, tint(s.accent.darker(0.45f)))
    }
    if (origin == Trait.DEMON) {
        val tail = Path().apply {
            moveTo(c.x - dir * u * 0.2f, hipY)
            cubicTo(c.x - dir * u * 1.0f, hipY + u * 0.2f, c.x - dir * u * 0.9f, hipY - u * 0.7f, c.x - dir * u * 1.15f, hipY - u * 0.55f)
        }
        drawPath(tail, tint(skin.darker(0.2f)), style = Stroke(u * 0.1f, cap = StrokeCap.Round))
        drawPath(triangle(Offset(c.x - dir * u * 1.15f, hipY - u * 0.55f), u * 0.16f, -90f), tint(skin.darker(0.2f)))
    }
    if (role == Trait.RANGER && s.weapon == FigureWeapon.BOW) {
        // Köcher über der Schulter
        drawRoundRect(tint(Color(0xFF78350F)), Offset(c.x - dir * u * 0.45f - u * 0.12f, shoulderY - u * 0.35f), Size(u * 0.24f, u * 0.75f), CornerRadius(u * 0.08f))
        for (i in 0..2) {
            val x = c.x - dir * u * 0.45f - u * 0.07f + i * u * 0.07f
            drawLine(tint(Color(0xFFF5F5F4)), Offset(x, shoulderY - u * 0.35f), Offset(x, shoulderY - u * 0.5f), u * 0.04f)
        }
    }
    if (s.headgear == FigureHeadgear.HOOD) drawCircle(tint(s.primary.darker(0.15f)), headR * 1.22f, head + Offset(0f, u * 0.04f))
    if (origin == Trait.ELF && s.headgear != FigureHeadgear.HOOD) {
        drawRoundRect(tint(s.hair), head + Offset(-headR * 1.05f, -headR * 0.6f), Size(headR * 2.1f, headR * 2.3f), CornerRadius(headR))
    }

    // --- Beine ---
    if (!robe) {
        val legW = u * 0.24f * width
        val legColor = if (elemental) s.primary else s.primary.darker(0.4f)
        for (side in listOf(-1f, 1f)) {
            val x = c.x + side * u * 0.2f * width - legW / 2f
            if (elemental) {
                drawPath(triangle(Offset(x + legW / 2f, footY - u * 0.05f), legW * 0.6f, -90f, footY - hipY), tint(legColor.lighter(0.2f), 0.85f))
            } else {
                drawRoundRect(tint(legColor), Offset(x, hipY), Size(legW, footY - hipY), CornerRadius(legW * 0.4f))
                drawRoundRect(tint(Color(0xFF292524)), Offset(x - legW * 0.1f, footY - u * 0.16f), Size(legW * 1.2f, u * 0.18f), CornerRadius(u * 0.06f))
            }
        }
    }

    // --- Rumpf ---
    val torsoW = u * width * when (role) {
        Trait.GUARDIAN -> 1.05f
        Trait.ASSASSIN -> 0.78f
        else -> 0.9f
    }
    val armColor = if (elemental) s.primary else s.primary
    // Arme (Linie Schulter → Hand)
    for (h in listOf(hand, offHand)) {
        val sx = if (h.x > c.x) c.x + torsoW * 0.45f else c.x - torsoW * 0.45f
        drawLine(tint(armColor.darker(0.15f)), Offset(sx, shoulderY + u * 0.1f), h, u * 0.2f, StrokeCap.Round)
    }
    if (robe) {
        val robePath = Path().apply {
            moveTo(c.x - u * 0.38f * width, shoulderY - u * 0.02f)
            lineTo(c.x + u * 0.38f * width, shoulderY - u * 0.02f)
            lineTo(c.x + u * 0.72f * width, footY)
            quadraticTo(c.x, footY + u * 0.12f, c.x - u * 0.72f * width, footY)
            close()
        }
        drawPath(robePath, tint(s.primary))
        drawLine(tint(s.accent), Offset(c.x, shoulderY + u * 0.1f), Offset(c.x, footY), u * 0.08f)
        drawLine(tint(s.accent.darker(0.2f)), Offset(c.x - u * 0.42f * width, hipY - u * 0.15f), Offset(c.x + u * 0.42f * width, hipY - u * 0.15f), u * 0.09f)
    } else if (elemental) {
        drawElementalTorso(s, Offset(c.x, (shoulderY + hipY) / 2f), torsoW, hipY - shoulderY + u * 0.2f, time, tint)
    } else {
        val top = Offset(c.x - torsoW / 2f, shoulderY - u * 0.05f)
        val torsoSize = Size(torsoW, hipY - shoulderY + u * 0.18f)
        drawRoundRect(tint(s.primary), top, torsoSize, CornerRadius(u * 0.22f))
        when (role) {
            Trait.WARRIOR -> {
                drawRoundRect(tint(s.primary.lighter(0.25f)), top + Offset(torsoW * 0.18f, u * 0.08f), Size(torsoW * 0.64f, torsoSize.height * 0.5f), CornerRadius(u * 0.15f))
            }
            Trait.GUARDIAN -> {
                drawCircle(tint(s.primary.lighter(0.3f)), u * 0.23f, Offset(c.x - torsoW * 0.5f, shoulderY + u * 0.05f))
                drawCircle(tint(s.primary.lighter(0.3f)), u * 0.23f, Offset(c.x + torsoW * 0.5f, shoulderY + u * 0.05f))
            }
            Trait.RANGER -> {
                drawLine(tint(Color(0xFF78350F)), Offset(c.x - torsoW * 0.45f, shoulderY), Offset(c.x + torsoW * 0.45f, hipY), u * 0.08f)
            }
            Trait.ASSASSIN -> {
                // Schal mit wehendem Ende
                val flutter = sin(time * 7f + c.x) * u * 0.08f
                drawLine(tint(s.accent), Offset(c.x - torsoW * 0.45f, shoulderY + u * 0.02f), Offset(c.x + torsoW * 0.45f, shoulderY + u * 0.02f), u * 0.14f, StrokeCap.Round)
                drawLine(tint(s.accent), Offset(c.x - dir * torsoW * 0.4f, shoulderY), Offset(c.x - dir * u * 0.95f, shoulderY + u * 0.25f + flutter), u * 0.1f, StrokeCap.Round)
            }
            else -> Unit
        }
        // Gürtel
        drawLine(tint(s.accent.darker(0.3f)), Offset(top.x, hipY), Offset(top.x + torsoW, hipY), u * 0.09f)
        if (origin == Trait.UNDEAD && role != Trait.GUARDIAN) {
            for (i in 0..2) {
                val y = shoulderY + u * (0.12f + i * 0.13f)
                drawLine(tint(Color(0xFFE7E5E4), 0.85f), Offset(c.x - torsoW * 0.22f, y), Offset(c.x + torsoW * 0.22f, y), u * 0.05f, StrokeCap.Round)
            }
        }
    }

    // --- Schild in der zweiten Hand ---
    s.shield?.let { shieldColor ->
        val sc = offHand + Offset(-dir * u * 0.08f, -u * 0.05f)
        val shield = Path().apply {
            moveTo(sc.x, sc.y - u * 0.5f)
            lineTo(sc.x + u * 0.38f, sc.y - u * 0.36f)
            lineTo(sc.x + u * 0.33f, sc.y + u * 0.15f)
            quadraticTo(sc.x + u * 0.2f, sc.y + u * 0.45f, sc.x, sc.y + u * 0.58f)
            quadraticTo(sc.x - u * 0.2f, sc.y + u * 0.45f, sc.x - u * 0.33f, sc.y + u * 0.15f)
            lineTo(sc.x - u * 0.38f, sc.y - u * 0.36f)
            close()
        }
        drawPath(shield, tint(shieldColor))
        drawPath(shield, tint(s.accent), style = Stroke(u * 0.07f))
        drawCircle(tint(s.accent), u * 0.1f, sc)
    }

    // --- Kopf ---
    drawHead(def, s, head, headR, u, time, dir, skin, tint)

    // --- Hände und Waffe ---
    drawCircle(tint(skin.darker(0.1f)), u * 0.11f, hand)
    if (s.shield == null) drawCircle(tint(skin.darker(0.1f)), u * 0.11f, offHand)
    drawWeapon(s, hand, offHand, u, time, dir, tint)
}

private fun DrawScope.drawHead(def: UnitDef, s: FigureStyle, head: Offset, r: Float, u: Float, time: Float, dir: Float, skin: Color, tint: Tint) {
    val origin = def.origin
    // Ohren der Elfen hinter dem Kopf
    if (origin == Trait.ELF) {
        for (side in listOf(-1f, 1f)) {
            val ear = Path().apply {
                moveTo(head.x + side * r * 0.75f, head.y - r * 0.1f)
                lineTo(head.x + side * r * 1.45f, head.y - r * 0.55f)
                lineTo(head.x + side * r * 0.8f, head.y + r * 0.25f)
                close()
            }
            drawPath(ear, tint(skin.darker(0.08f)))
        }
    }
    if (origin == Trait.DEMON) {
        for (side in listOf(-1f, 1f)) {
            val horn = Path().apply {
                moveTo(head.x + side * r * 0.35f, head.y - r * 0.75f)
                quadraticTo(head.x + side * r * 1.2f, head.y - r * 1.1f, head.x + side * r * 0.95f, head.y - r * 1.7f)
                quadraticTo(head.x + side * r * 0.8f, head.y - r * 1.05f, head.x + side * r * 0.7f, head.y - r * 0.5f)
                close()
            }
            drawPath(horn, tint(Color(0xFF292524)))
        }
    }
    drawCircle(tint(skin), r, head)
    if (s.headgear == FigureHeadgear.HOOD) {
        // Schatten der Kapuze über der Stirn
        drawArc(tint(s.primary.darker(0.15f)), 180f, 180f, true, head + Offset(-r * 1.05f, -r * 1.1f), Size(r * 2.1f, r * 1.5f))
    }

    // Gesicht
    val eyeY = head.y + r * 0.05f
    val eyeDx = r * 0.36f
    val look = dir * r * 0.06f
    when (origin) {
        Trait.UNDEAD -> {
            for (side in listOf(-1f, 1f)) {
                drawCircle(tint(Color(0xFF1C1917)), r * 0.24f, Offset(head.x + side * eyeDx, eyeY))
                drawCircle(tint(s.accent, 0.7f + 0.3f * sin(time * 5f)), r * 0.1f, Offset(head.x + side * eyeDx + look, eyeY))
            }
            drawPath(triangle(Offset(head.x, eyeY + r * 0.35f), r * 0.14f, 90f), tint(Color(0xFF1C1917)))
            for (i in -2..2) drawLine(tint(Color(0xFF57534E)), Offset(head.x + i * r * 0.13f, head.y + r * 0.62f), Offset(head.x + i * r * 0.13f, head.y + r * 0.8f), r * 0.05f)
        }
        Trait.DEMON -> {
            for (side in listOf(-1f, 1f)) {
                drawLine(tint(Color(0xFFFDE047)), Offset(head.x + side * eyeDx - r * 0.12f, eyeY - side * r * 0.04f), Offset(head.x + side * eyeDx + r * 0.12f, eyeY + side * r * 0.04f), r * 0.13f, StrokeCap.Round)
            }
            drawLine(tint(Color(0xFF450A0A)), Offset(head.x - r * 0.25f, head.y + r * 0.5f), Offset(head.x + r * 0.25f, head.y + r * 0.45f), r * 0.07f, StrokeCap.Round)
        }
        Trait.ELEMENTAL -> {
            for (side in listOf(-1f, 1f)) drawCircle(tint(Color.White), r * 0.15f, Offset(head.x + side * eyeDx + look, eyeY))
        }
        else -> {
            for (side in listOf(-1f, 1f)) {
                drawCircle(tint(Color.White), r * 0.15f, Offset(head.x + side * eyeDx, eyeY))
                drawCircle(tint(Color(0xFF1F2937)), r * 0.09f, Offset(head.x + side * eyeDx + look, eyeY))
            }
            drawCircle(tint(skin.darker(0.2f)), r * 0.07f, Offset(head.x, eyeY + r * 0.25f))
        }
    }

    if (origin == Trait.DWARF) {
        // Großer Bart mit Schnurrbart
        val beard = Path().apply {
            moveTo(head.x - r * 0.85f, head.y + r * 0.15f)
            quadraticTo(head.x - r * 0.75f, head.y + r * 1.5f, head.x, head.y + r * 1.75f)
            quadraticTo(head.x + r * 0.75f, head.y + r * 1.5f, head.x + r * 0.85f, head.y + r * 0.15f)
            quadraticTo(head.x, head.y + r * 0.55f, head.x - r * 0.85f, head.y + r * 0.15f)
            close()
        }
        drawPath(beard, tint(s.hair))
        drawLine(tint(s.hair.darker(0.2f)), Offset(head.x - r * 0.45f, head.y + r * 0.42f), Offset(head.x + r * 0.45f, head.y + r * 0.42f), r * 0.16f, StrokeCap.Round)
    }
    if (origin == Trait.ELF && s.headgear != FigureHeadgear.HOOD) {
        drawArc(tint(s.hair), 180f, 180f, true, head + Offset(-r * 1.02f, -r * 1.05f), Size(r * 2.04f, r * 1.3f))
    }
    if (origin == Trait.ELEMENTAL) drawElementCrown(s, head, r, time, tint)

    // Kopfbedeckung
    when (s.headgear) {
        FigureHeadgear.HELMET, FigureHeadgear.HORNED_HELM -> {
            val steel = if (s.primary.luminance() < 0.2f) Color(0xFF9CA3AF) else s.primary.lighter(0.35f)
            drawArc(tint(steel), 180f, 180f, true, head + Offset(-r * 1.08f, -r * 1.12f), Size(r * 2.16f, r * 1.75f))
            drawLine(tint(steel.darker(0.25f)), Offset(head.x - r * 1.05f, head.y - r * 0.25f), Offset(head.x + r * 1.05f, head.y - r * 0.25f), r * 0.16f)
            drawLine(tint(steel.darker(0.25f)), Offset(head.x, head.y - r * 0.3f), Offset(head.x, head.y + r * 0.2f), r * 0.14f)
            if (s.headgear == FigureHeadgear.HORNED_HELM) {
                for (side in listOf(-1f, 1f)) {
                    val horn = Path().apply {
                        moveTo(head.x + side * r * 0.75f, head.y - r * 0.55f)
                        quadraticTo(head.x + side * r * 1.6f, head.y - r * 0.7f, head.x + side * r * 1.5f, head.y - r * 1.5f)
                        quadraticTo(head.x + side * r * 1.25f, head.y - r * 0.9f, head.x + side * r * 0.65f, head.y - r * 0.85f)
                        close()
                    }
                    drawPath(horn, tint(Color(0xFFF5F5F4)))
                }
            }
        }
        FigureHeadgear.HAT -> {
            drawLine(tint(s.primary.darker(0.2f)), Offset(head.x - r * 1.3f, head.y - r * 0.6f), Offset(head.x + r * 1.3f, head.y - r * 0.6f), r * 0.22f, StrokeCap.Round)
            val hat = Path().apply {
                moveTo(head.x - r * 0.85f, head.y - r * 0.6f)
                lineTo(head.x + r * 0.85f, head.y - r * 0.6f)
                quadraticTo(head.x + r * 0.2f, head.y - r * 1.5f, head.x + dir * r * 0.9f, head.y - r * 2.2f)
                close()
            }
            drawPath(hat, tint(s.primary.darker(0.1f)))
            drawCircle(tint(s.accent), r * 0.14f, Offset(head.x + dir * r * 0.9f, head.y - r * 2.2f))
        }
        FigureHeadgear.CROWN -> {
            val crown = Path().apply {
                moveTo(head.x - r * 0.75f, head.y - r * 0.7f)
                lineTo(head.x - r * 0.8f, head.y - r * 1.35f)
                lineTo(head.x - r * 0.4f, head.y - r * 1.0f)
                lineTo(head.x, head.y - r * 1.5f)
                lineTo(head.x + r * 0.4f, head.y - r * 1.0f)
                lineTo(head.x + r * 0.8f, head.y - r * 1.35f)
                lineTo(head.x + r * 0.75f, head.y - r * 0.7f)
                close()
            }
            drawPath(crown, tint(Color(0xFFFACC15)))
            drawCircle(tint(s.accent.takeIf { it != Color(0xFFFACC15) } ?: Color(0xFFDC2626)), r * 0.13f, Offset(head.x, head.y - r * 0.95f))
        }
        FigureHeadgear.CIRCLET -> {
            drawArc(tint(s.accent), 200f, 140f, false, head + Offset(-r * 0.95f, -r * 0.85f), Size(r * 1.9f, r * 1.2f), style = Stroke(r * 0.12f))
            drawCircle(tint(Color(0xFF22D3EE)), r * 0.1f, Offset(head.x, head.y - r * 0.85f))
        }
        else -> Unit
    }
}

private fun DrawScope.drawElementalTorso(s: FigureStyle, c: Offset, w: Float, h: Float, time: Float, tint: Tint) {
    val body = Path().apply {
        moveTo(c.x - w * 0.5f, c.y - h * 0.45f)
        lineTo(c.x + w * 0.5f, c.y - h * 0.45f)
        lineTo(c.x + w * 0.32f, c.y + h * 0.5f)
        lineTo(c.x - w * 0.32f, c.y + h * 0.5f)
        close()
    }
    drawPath(body, tint(s.primary))
    when (s.element) {
        Element.ROCK -> {
            drawLine(tint(s.primary.darker(0.4f)), Offset(c.x - w * 0.3f, c.y - h * 0.3f), Offset(c.x + w * 0.05f, c.y + h * 0.1f), w * 0.06f)
            drawLine(tint(s.primary.darker(0.4f)), Offset(c.x + w * 0.05f, c.y + h * 0.1f), Offset(c.x + w * 0.25f, c.y - h * 0.1f), w * 0.06f)
            drawCircle(tint(s.accent), w * 0.12f, Offset(c.x - w * 0.22f, c.y + h * 0.3f))
        }
        else -> {
            // Leuchtender Kern
            val pulse = 0.6f + 0.4f * sin(time * 5f)
            drawCircle(tint(s.accent, 0.5f * pulse), w * 0.42f, c)
            drawCircle(tint(Color.White, 0.8f), w * 0.16f, c)
        }
    }
}

private fun DrawScope.drawElementCrown(s: FigureStyle, head: Offset, r: Float, time: Float, tint: Tint) {
    when (s.element) {
        Element.FIRE -> for (i in -2..2) {
            val flicker = sin(time * 9f + i * 1.7f) * r * 0.15f
            val tip = Offset(head.x + i * r * 0.32f, head.y - r * (1.35f + (2 - abs(i)) * 0.3f) + flicker)
            drawPath(Path().apply {
                moveTo(head.x + i * r * 0.32f - r * 0.22f, head.y - r * 0.6f)
                lineTo(tip.x, tip.y)
                lineTo(head.x + i * r * 0.32f + r * 0.22f, head.y - r * 0.6f)
                close()
            }, tint(if (abs(i) == 2) s.primary else s.accent))
        }
        Element.ROCK -> for (i in -1..1) {
            drawRoundRect(tint(s.primary.darker(0.25f)), Offset(head.x + i * r * 0.55f - r * 0.25f, head.y - r * (1.25f - abs(i) * 0.2f)), Size(r * 0.5f, r * 0.45f), CornerRadius(r * 0.1f))
        }
        Element.SPARK, Element.STORM -> for (i in 0 until 6) {
            val a = time * 2.5f + i * (PI.toFloat() / 3f)
            val p = head + Offset(cos(a) * r * 1.15f, sin(a) * r * 0.45f - r * 0.9f)
            drawCircle(tint(s.accent), r * 0.12f, p)
            drawLine(tint(s.accent, 0.6f), head + Offset(0f, -r * 0.8f), p, r * 0.05f)
        }
        Element.PRIMAL, null -> {
            val colors = listOf(Color(0xFFF97316), Color(0xFF38BDF8), Color(0xFF84CC16), Color(0xFFFACC15))
            colors.forEachIndexed { i, col ->
                val a = time * 1.8f + i * (PI.toFloat() / 2f)
                drawCircle(tint(col), r * 0.2f, head + Offset(cos(a) * r * 1.3f, sin(a) * r * 0.5f - r * 0.2f))
            }
        }
    }
}

private fun DrawScope.drawWings(c: Offset, u: Float, time: Float, membrane: Color, bone: Color) {
    val flap = sin(time * 4f) * u * 0.12f
    for (side in listOf(-1f, 1f)) {
        val wing = Path().apply {
            moveTo(c.x + side * u * 0.2f, c.y)
            lineTo(c.x + side * u * 1.35f, c.y - u * 0.8f - flap)
            lineTo(c.x + side * u * 1.2f, c.y - u * 0.1f)
            lineTo(c.x + side * u * 1.0f, c.y + u * 0.2f)
            lineTo(c.x + side * u * 0.7f, c.y + u * 0.1f)
            lineTo(c.x + side * u * 0.45f, c.y + u * 0.35f)
            close()
        }
        drawPath(wing, membrane)
        drawLine(bone, Offset(c.x + side * u * 0.2f, c.y), Offset(c.x + side * u * 1.35f, c.y - u * 0.8f - flap), u * 0.06f)
    }
}

private fun DrawScope.drawWeapon(s: FigureStyle, hand: Offset, offHand: Offset, u: Float, time: Float, dir: Float, tint: Tint) {
    val steel = Color(0xFFE5E7EB)
    val wood = Color(0xFF92400E)
    when (s.weapon) {
        FigureWeapon.SWORD, FigureWeapon.GREATSWORD -> {
            val len = if (s.weapon == FigureWeapon.GREATSWORD) 1.45f else 1.05f
            val tip = hand + Offset(dir * u * 0.25f, -u * len)
            drawLine(tint(if (s.weapon == FigureWeapon.GREATSWORD) s.accent.lighter(0.4f) else steel), hand + Offset(0f, -u * 0.12f), tip, u * (if (s.weapon == FigureWeapon.GREATSWORD) 0.17f else 0.12f), StrokeCap.Round)
            drawLine(tint(s.accent.darker(0.1f)), hand + Offset(-u * 0.2f, -u * 0.12f), hand + Offset(u * 0.2f, -u * 0.1f), u * 0.09f, StrokeCap.Round)
            if (s.weapon == FigureWeapon.GREATSWORD) drawLine(tint(s.accent, 0.5f + 0.3f * sin(time * 4f)), hand + Offset(0f, -u * 0.3f), tip, u * 0.05f)
        }
        FigureWeapon.AXE -> {
            val top = hand + Offset(dir * u * 0.15f, -u * 0.95f)
            drawLine(tint(wood), hand + Offset(0f, u * 0.2f), top, u * 0.1f, StrokeCap.Round)
            drawArc(tint(steel), if (dir > 0) -80f else 100f, 160f, true, top + Offset(-u * 0.32f + dir * u * 0.12f, -u * 0.25f), Size(u * 0.64f, u * 0.55f))
        }
        FigureWeapon.HAMMER -> {
            val top = hand + Offset(dir * u * 0.1f, -u * 0.85f)
            drawLine(tint(wood), hand + Offset(0f, u * 0.2f), top, u * 0.1f, StrokeCap.Round)
            drawRoundRect(tint(Color(0xFF9CA3AF)), top + Offset(-u * 0.3f, -u * 0.2f), Size(u * 0.6f, u * 0.36f), CornerRadius(u * 0.06f))
            drawLine(tint(s.accent, 0.7f + 0.3f * sin(time * 3f)), top + Offset(-u * 0.15f, -u * 0.02f), top + Offset(u * 0.15f, -u * 0.02f), u * 0.07f)
        }
        FigureWeapon.SCYTHE -> {
            val top = hand + Offset(dir * u * 0.2f, -u * 1.35f)
            drawLine(tint(Color(0xFF292524)), hand + Offset(-dir * u * 0.1f, u * 0.55f), top, u * 0.09f, StrokeCap.Round)
            val blade = Path().apply {
                moveTo(top.x, top.y)
                quadraticTo(top.x - dir * u * 0.6f, top.y - u * 0.15f, top.x - dir * u * 0.95f, top.y + u * 0.45f)
                quadraticTo(top.x - dir * u * 0.45f, top.y + u * 0.05f, top.x, top.y + u * 0.15f)
                close()
            }
            drawPath(blade, tint(s.accent.lighter(0.2f)))
        }
        FigureWeapon.STAFF -> {
            val top = hand + Offset(0f, -u * 1.35f)
            drawLine(tint(wood), hand + Offset(0f, u * 0.75f), top, u * 0.09f, StrokeCap.Round)
            val pulse = 0.7f + 0.3f * sin(time * 4.5f)
            drawCircle(tint(s.accent, 0.35f * pulse), u * 0.32f, top)
            drawCircle(tint(s.accent), u * 0.17f, top)
            drawCircle(tint(Color.White, 0.8f), u * 0.07f, top + Offset(-u * 0.04f, -u * 0.04f))
        }
        FigureWeapon.BOW -> {
            val bowC = hand + Offset(dir * u * 0.12f, -u * 0.25f)
            drawArc(tint(s.accent.takeIf { s.element != null } ?: wood), if (dir > 0) -75f else 105f, 150f, false, bowC + Offset(-u * 0.55f - dir * u * 0.3f, -u * 0.65f), Size(u * 1.1f, u * 1.3f), style = Stroke(u * 0.09f, cap = StrokeCap.Round))
            val bx = bowC.x - dir * u * 0.12f
            drawLine(tint(Color(0xFFF5F5F4), 0.8f), Offset(bx, bowC.y - u * 0.58f), Offset(bx, bowC.y + u * 0.58f), u * 0.03f)
        }
        FigureWeapon.CROSSBOW -> {
            drawLine(tint(wood), hand + Offset(-dir * u * 0.15f, 0f), hand + Offset(dir * u * 0.65f, -u * 0.15f), u * 0.13f, StrokeCap.Round)
            drawArc(tint(Color(0xFF6B7280)), if (dir > 0) 200f else -20f, 140f, false, hand + Offset(dir * u * 0.35f - u * 0.35f, -u * 0.55f), Size(u * 0.7f, u * 0.5f), style = Stroke(u * 0.07f))
        }
        FigureWeapon.DAGGERS -> {
            for (h in listOf(hand, offHand)) {
                val side = if (h == hand) dir else -dir
                drawLine(tint(steel), h, h + Offset(side * u * 0.2f, -u * 0.5f), u * 0.08f, StrokeCap.Round)
                drawLine(tint(s.accent), h + Offset(-u * 0.1f, -u * 0.04f), h + Offset(u * 0.1f, -u * 0.04f), u * 0.06f)
            }
        }
        FigureWeapon.SPEAR -> {
            val top = hand + Offset(dir * u * 0.05f, -u * 1.5f)
            drawLine(tint(wood), hand + Offset(0f, u * 0.6f), top, u * 0.08f, StrokeCap.Round)
            drawPath(triangle(top, u * 0.2f, -90f, -u * 0.4f), tint(steel))
            drawLine(tint(s.accent), top + Offset(-u * 0.12f, u * 0.05f), top + Offset(u * 0.12f, u * 0.05f), u * 0.05f)
        }
        FigureWeapon.NONE -> Unit
    }
}

// --- Kreaturen ---

private fun DrawScope.drawCreature(def: UnitDef, c: Offset, u: Float, time: Float, dir: Float, tint: Tint) {
    when (def.id) {
        "wolf" -> {
            val fur = Color(0xFF6B7280)
            val step = sin(time * 6f) * u * 0.08f
            for (i in 0..3) {
                val x = c.x + (i - 1.5f) * u * 0.42f
                drawLine(tint(fur.darker(0.3f)), Offset(x, c.y + u * 0.3f), Offset(x + if (i % 2 == 0) step else -step, c.y + u * 0.95f), u * 0.17f, StrokeCap.Round)
            }
            val tail = Path().apply {
                moveTo(c.x - dir * u * 0.8f, c.y)
                quadraticTo(c.x - dir * u * 1.4f, c.y - u * 0.2f, c.x - dir * u * 1.3f, c.y - u * 0.7f)
            }
            drawPath(tail, tint(fur), style = Stroke(u * 0.2f, cap = StrokeCap.Round))
            drawOval(tint(fur), Offset(c.x - u * 0.95f, c.y - u * 0.35f), Size(u * 1.9f, u * 0.85f))
            drawOval(tint(fur.lighter(0.25f)), Offset(c.x - u * 0.6f, c.y + u * 0.05f), Size(u * 1.2f, u * 0.35f))
            val head = Offset(c.x + dir * u * 0.85f, c.y - u * 0.45f)
            drawCircle(tint(fur), u * 0.42f, head)
            drawPath(triangle(head + Offset(dir * u * 0.35f, u * 0.1f), u * 0.24f, if (dir > 0) 0f else 180f, u * 0.45f), tint(fur.lighter(0.15f)))
            for (side in listOf(-1f, 1f)) drawPath(triangle(head + Offset(side * u * 0.22f, -u * 0.32f), u * 0.13f, -90f, -u * 0.3f), tint(fur.darker(0.2f)))
            drawCircle(tint(Color(0xFFFACC15)), u * 0.08f, head + Offset(dir * u * 0.15f, -u * 0.05f))
        }
        "golem" -> {
            val stone = Color(0xFF78716C)
            for (side in listOf(-1f, 1f)) {
                drawRoundRect(tint(stone.darker(0.15f)), Offset(c.x + side * u * 0.5f - u * 0.2f, c.y + u * 0.4f), Size(u * 0.4f, u * 0.6f), CornerRadius(u * 0.1f))
                drawRoundRect(tint(stone.darker(0.1f)), Offset(c.x + side * u * 0.95f - u * 0.25f, c.y - u * 0.55f), Size(u * 0.5f, u * 1.05f), CornerRadius(u * 0.15f))
            }
            drawRoundRect(tint(stone), Offset(c.x - u * 0.75f, c.y - u * 0.75f), Size(u * 1.5f, u * 1.3f), CornerRadius(u * 0.3f))
            drawLine(tint(stone.darker(0.4f)), Offset(c.x - u * 0.4f, c.y - u * 0.5f), Offset(c.x, c.y - u * 0.05f), u * 0.06f)
            drawLine(tint(stone.darker(0.4f)), Offset(c.x, c.y - u * 0.05f), Offset(c.x + u * 0.3f, c.y + u * 0.3f), u * 0.06f)
            drawCircle(tint(Color(0xFF84CC16), 0.8f), u * 0.12f, Offset(c.x + u * 0.35f, c.y - u * 0.35f))
            drawRoundRect(tint(stone.lighter(0.1f)), Offset(c.x - u * 0.38f, c.y - u * 1.3f), Size(u * 0.76f, u * 0.6f), CornerRadius(u * 0.15f))
            val glow = 0.7f + 0.3f * sin(time * 3f)
            for (side in listOf(-1f, 1f)) drawCircle(tint(Color(0xFF38BDF8), glow), u * 0.09f, Offset(c.x + side * u * 0.16f, c.y - u * 1.02f))
        }
        "spider" -> {
            val body = Color(0xFF292524)
            for (side in listOf(-1f, 1f)) for (i in 0..3) {
                val wiggle = sin(time * 8f + i) * u * 0.06f
                val knee = Offset(c.x + side * u * (0.65f + i * 0.12f), c.y - u * (0.45f - i * 0.3f) + wiggle)
                drawLine(tint(body.lighter(0.15f)), c + Offset(side * u * 0.2f, 0f), knee, u * 0.09f, StrokeCap.Round)
                drawLine(tint(body.lighter(0.15f)), knee, knee + Offset(side * u * 0.35f, u * 0.55f), u * 0.08f, StrokeCap.Round)
            }
            drawOval(tint(body), Offset(c.x - u * 0.55f, c.y - u * 0.1f), Size(u * 1.1f, u * 0.95f))
            drawOval(tint(Color(0xFF7F1D1D)), Offset(c.x - u * 0.2f, c.y + u * 0.15f), Size(u * 0.4f, u * 0.5f))
            drawCircle(tint(body.lighter(0.1f)), u * 0.38f, c + Offset(0f, -u * 0.35f))
            for (i in 0 until 4) drawCircle(tint(Color(0xFFEF4444)), u * 0.06f, c + Offset((i - 1.5f) * u * 0.13f, -u * (0.42f + (i % 2) * 0.08f)))
        }
        else -> { // Harpyie
            val feather = Color(0xFF7C5E3C)
            val flap = sin(time * 7f) * u * 0.3f
            for (side in listOf(-1f, 1f)) {
                val wing = Path().apply {
                    moveTo(c.x + side * u * 0.25f, c.y - u * 0.35f)
                    lineTo(c.x + side * u * 1.45f, c.y - u * 0.9f - flap)
                    lineTo(c.x + side * u * 1.3f, c.y - u * 0.3f - flap * 0.5f)
                    lineTo(c.x + side * u * 1.1f, c.y + u * 0.1f)
                    lineTo(c.x + side * u * 0.3f, c.y + u * 0.15f)
                    close()
                }
                drawPath(wing, tint(feather))
                drawLine(tint(feather.lighter(0.3f)), Offset(c.x + side * u * 0.3f, c.y - u * 0.3f), Offset(c.x + side * u * 1.3f, c.y - u * 0.75f - flap), u * 0.05f)
            }
            drawOval(tint(feather.lighter(0.15f)), Offset(c.x - u * 0.35f, c.y - u * 0.5f), Size(u * 0.7f, u * 1.1f))
            for (side in listOf(-1f, 1f)) drawLine(tint(Color(0xFFFACC15)), Offset(c.x + side * u * 0.15f, c.y + u * 0.55f), Offset(c.x + side * u * 0.25f, c.y + u * 0.95f), u * 0.08f, StrokeCap.Round)
            val head = c + Offset(0f, -u * 0.8f)
            drawCircle(tint(Color(0xFFE5C49F)), u * 0.3f, head)
            drawArc(tint(Color(0xFF3F2E1E)), 160f, 220f, true, head + Offset(-u * 0.36f, -u * 0.38f), Size(u * 0.72f, u * 0.6f))
            drawPath(triangle(head + Offset(dir * u * 0.22f, u * 0.08f), u * 0.1f, if (dir > 0) 0f else 180f, u * 0.2f), tint(Color(0xFFFACC15)))
            for (side in listOf(-1f, 1f)) drawCircle(tint(Color(0xFFDC2626)), u * 0.05f, head + Offset(side * u * 0.11f, 0f))
        }
    }
}

// --- Bosse ---

private fun DrawScope.drawBoss(def: UnitDef, c: Offset, u: Float, time: Float, tint: Tint) {
    when (def.id) {
        "bonedragon" -> {
            val bone = Color(0xFFE7E5E4)
            val membrane = Color(0xFF4C1D95)
            val flap = sin(time * 2.5f) * u * 0.15f
            for (side in listOf(-1f, 1f)) {
                val wing = Path().apply {
                    moveTo(c.x + side * u * 0.2f, c.y - u * 0.3f)
                    lineTo(c.x + side * u * 1.25f, c.y - u * 1.05f - flap)
                    lineTo(c.x + side * u * 1.45f, c.y - u * 0.2f - flap * 0.4f)
                    lineTo(c.x + side * u * 1.1f, c.y + u * 0.05f)
                    lineTo(c.x + side * u * 0.75f, c.y - u * 0.05f)
                    lineTo(c.x + side * u * 0.5f, c.y + u * 0.25f)
                    close()
                }
                drawPath(wing, tint(membrane, 0.85f))
                for (k in 0..2) {
                    val end = Offset(c.x + side * u * (1.1f + k * 0.12f), c.y - u * (0.95f - k * 0.4f) - flap * (1f - k * 0.3f))
                    drawLine(tint(bone), Offset(c.x + side * u * 0.2f, c.y - u * 0.3f), end, u * 0.05f)
                }
            }
            val tail = Path().apply {
                moveTo(c.x, c.y + u * 0.3f)
                cubicTo(c.x + u * 0.7f, c.y + u * 0.6f, c.x + u * 0.9f, c.y + u * 0.2f, c.x + u * 1.15f, c.y + u * 0.5f)
            }
            drawPath(tail, tint(bone), style = Stroke(u * 0.09f, cap = StrokeCap.Round))
            drawOval(tint(Color(0xFF292524)), Offset(c.x - u * 0.45f, c.y - u * 0.4f), Size(u * 0.9f, u * 0.85f))
            for (i in 0..3) {
                val y = c.y - u * 0.25f + i * u * 0.17f
                drawArc(tint(bone), 200f, 140f, false, Offset(c.x - u * 0.4f, y - u * 0.1f), Size(u * 0.8f, u * 0.3f), style = Stroke(u * 0.05f))
            }
            val glow = 0.6f + 0.4f * sin(time * 4f)
            drawCircle(tint(Color(0xFFA855F7), 0.4f * glow), u * 0.18f, c + Offset(0f, -u * 0.05f))
            val head = c + Offset(0f, -u * 0.75f)
            drawLine(tint(bone), c + Offset(0f, -u * 0.35f), head, u * 0.14f)
            drawOval(tint(bone), Offset(head.x - u * 0.32f, head.y - u * 0.25f), Size(u * 0.64f, u * 0.5f))
            drawRoundRect(tint(bone.darker(0.1f)), Offset(head.x - u * 0.18f, head.y + u * 0.1f), Size(u * 0.36f, u * 0.28f), CornerRadius(u * 0.08f))
            for (side in listOf(-1f, 1f)) {
                drawPath(triangle(head + Offset(side * u * 0.25f, -u * 0.2f), u * 0.08f, -90f, -u * 0.35f), tint(bone.darker(0.2f)))
                drawCircle(tint(Color(0xFFA855F7), glow), u * 0.07f, head + Offset(side * u * 0.13f, -u * 0.02f))
            }
        }
        "stonetitan" -> {
            val stone = Color(0xFF57534E)
            val rune = Color(0xFFFB923C)
            val glow = 0.55f + 0.45f * sin(time * 2.2f)
            for (side in listOf(-1f, 1f)) {
                drawRoundRect(tint(stone.darker(0.15f)), Offset(c.x + side * u * 0.38f - u * 0.2f, c.y + u * 0.35f), Size(u * 0.4f, u * 0.6f), CornerRadius(u * 0.1f))
                drawRoundRect(tint(stone.darker(0.05f)), Offset(c.x + side * u * 0.95f - u * 0.25f, c.y - u * 0.65f), Size(u * 0.5f, u * 0.95f), CornerRadius(u * 0.15f))
                drawCircle(tint(stone.lighter(0.08f)), u * 0.3f, Offset(c.x + side * u * 0.95f, c.y + u * 0.4f))
            }
            val body = Path().apply {
                moveTo(c.x - u * 0.85f, c.y - u * 0.7f)
                lineTo(c.x + u * 0.8f, c.y - u * 0.8f)
                lineTo(c.x + u * 0.65f, c.y + u * 0.45f)
                lineTo(c.x - u * 0.6f, c.y + u * 0.5f)
                close()
            }
            drawPath(body, tint(stone))
            drawLine(tint(rune, glow), Offset(c.x - u * 0.4f, c.y - u * 0.45f), Offset(c.x + u * 0.1f, c.y - u * 0.05f), u * 0.07f, StrokeCap.Round)
            drawLine(tint(rune, glow), Offset(c.x + u * 0.1f, c.y - u * 0.05f), Offset(c.x - u * 0.15f, c.y + u * 0.3f), u * 0.07f, StrokeCap.Round)
            drawLine(tint(rune, glow), Offset(c.x + u * 0.2f, c.y - u * 0.55f), Offset(c.x + u * 0.45f, c.y - u * 0.2f), u * 0.06f, StrokeCap.Round)
            drawCircle(tint(rune, 0.3f * glow), u * 0.28f, c + Offset(0f, -u * 0.1f))
            drawRoundRect(tint(stone.lighter(0.1f)), Offset(c.x - u * 0.3f, c.y - u * 1.2f), Size(u * 0.6f, u * 0.48f), CornerRadius(u * 0.12f))
            for (side in listOf(-1f, 1f)) drawCircle(tint(rune, glow), u * 0.07f, Offset(c.x + side * u * 0.13f, c.y - u * 0.98f))
        }
        else -> { // Sturmhexe
            val robe = Color(0xFF1E3A8A)
            val hover = sin(time * 2f) * u * 0.08f
            val cc = c + Offset(0f, -u * 0.1f + hover)
            drawOval(tint(Color(0xFF60A5FA), 0.25f + 0.1f * sin(time * 3f)), Offset(c.x - u * 0.6f, c.y + u * 0.75f), Size(u * 1.2f, u * 0.3f))
            val robePath = Path().apply {
                moveTo(cc.x - u * 0.3f, cc.y - u * 0.45f)
                lineTo(cc.x + u * 0.3f, cc.y - u * 0.45f)
                lineTo(cc.x + u * 0.65f, cc.y + u * 0.6f)
                lineTo(cc.x + u * 0.3f, cc.y + u * 0.45f)
                lineTo(cc.x, cc.y + u * 0.7f)
                lineTo(cc.x - u * 0.3f, cc.y + u * 0.45f)
                lineTo(cc.x - u * 0.65f, cc.y + u * 0.6f)
                close()
            }
            drawPath(robePath, tint(robe))
            val hairSway = sin(time * 3f) * u * 0.1f
            drawRoundRect(tint(Color(0xFFE5E7EB)), Offset(cc.x - u * 0.38f + hairSway, cc.y - u * 0.85f), Size(u * 0.76f, u * 0.75f), CornerRadius(u * 0.3f))
            val head = cc + Offset(0f, -u * 0.7f)
            drawCircle(tint(Color(0xFFCBD5E1)), u * 0.24f, head)
            for (side in listOf(-1f, 1f)) drawCircle(tint(Color(0xFF7DD3FC)), u * 0.05f, head + Offset(side * u * 0.09f, 0f))
            val hat = Path().apply {
                moveTo(head.x - u * 0.5f, head.y - u * 0.15f)
                lineTo(head.x + u * 0.5f, head.y - u * 0.15f)
                quadraticTo(head.x + u * 0.15f, head.y - u * 0.55f, head.x + u * 0.45f, head.y - u * 0.95f)
                close()
            }
            drawPath(hat, tint(robe.darker(0.3f)))
            // Blitzkugel mit Funken
            val orb = cc + Offset(u * 0.7f, -u * 0.25f)
            drawLine(tint(robe.lighter(0.2f)), cc + Offset(u * 0.25f, -u * 0.25f), orb, u * 0.1f, StrokeCap.Round)
            val pulse = 0.6f + 0.4f * sin(time * 8f)
            drawCircle(tint(Color(0xFF93C5FD), 0.4f * pulse), u * 0.32f, orb)
            drawCircle(tint(Color(0xFFE0F2FE)), u * 0.15f, orb)
            for (i in 0 until 3) {
                val a = time * 5f + i * 2.1f
                val end = orb + Offset(cos(a) * u * 0.45f, sin(a) * u * 0.45f)
                val mid = orb + Offset(cos(a + 0.4f) * u * 0.22f, sin(a + 0.4f) * u * 0.22f)
                drawLine(tint(Color(0xFFE0F2FE)), orb, mid, u * 0.04f)
                drawLine(tint(Color(0xFFE0F2FE)), mid, end, u * 0.04f)
            }
        }
    }
}

/** Dreieck mit Spitze in Richtung [angleDeg] (0 = rechts, −90 = oben); [length] überschreibt die Höhe. */
private fun triangle(base: Offset, half: Float, angleDeg: Float, length: Float = half * 2f): Path {
    val a = angleDeg * PI.toFloat() / 180f
    val dx = cos(a)
    val dy = sin(a)
    val tip = Offset(base.x + dx * abs(length), base.y + dy * abs(length))
    return Path().apply {
        moveTo(base.x - dy * half, base.y + dx * half)
        lineTo(tip.x, tip.y)
        lineTo(base.x + dy * half, base.y - dx * half)
        close()
    }
}

private fun Color.luminance(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue
