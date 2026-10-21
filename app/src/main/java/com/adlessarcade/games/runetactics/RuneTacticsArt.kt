package com.adlessarcade.games.runetactics

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Ausgearbeitete Einheitenfiguren (Beine, Hände, Gesichter, Kopfschmuck, Waffen, Schattierung).
 * Jede Figur zeichnet mit festen Farben; Treffer-Blitz und Transparenz legt [drawUnitArt] als Ebene darüber.
 */
internal object RuneTacticsArt {
    private val drawers: Map<String, DrawScope.(ArtBody) -> Unit> = undeadArt + elfArt + dwarfArt + demonArt + elementalArt +
        creatureArt + bossArt

    fun has(id: String): Boolean = id in drawers

    fun drawer(id: String): (DrawScope.(ArtBody) -> Unit)? = drawers[id]

    /** Einheiten, Kreaturen und Bosse ohne eigene Figur (für den Vollständigkeits-Test). */
    fun missing(): List<String> =
        (RuneTacticsUnits.all + RuneTacticsUnits.creatures + RuneTacticsUnits.bosses).map { it.id }.filter { it !in drawers }
}

/** Zeichnet die ausgearbeitete Figur von [def]; false, wenn es keine gibt. */
internal fun DrawScope.drawUnitArt(def: UnitDef, c: Offset, size: Float, time: Float, dir: Float, alpha: Float, flash: Float): Boolean {
    val drawer = RuneTacticsArt.drawer(def.id) ?: return false
    val dwarf = def.origin == Trait.DWARF
    val u = when {
        def.isBoss -> size * 0.42f
        def.origin == Trait.BEAST -> size * 0.3f
        else -> size * 0.31f * (0.93f + def.cost * 0.025f)
    }
    val body = ArtBody(c, u, dir, time, dwarf)
    if (alpha >= 1f && flash <= 0f) {
        drawer(body)
        return true
    }
    // Eine Ebene für die ganze Figur: Blitz hellt alle Farben gleichmäßig auf, Transparenz wirkt auf die Silhouette.
    val paint = Paint().apply {
        this.alpha = alpha.coerceIn(0f, 1f)
        if (flash > 0f) {
            val f = flash.coerceIn(0f, 1f)
            val k = 1f - f
            val o = 255f * f
            colorFilter = ColorFilter.colorMatrix(
                ColorMatrix(floatArrayOf(k, 0f, 0f, 0f, o, 0f, k, 0f, 0f, o, 0f, 0f, k, 0f, o, 0f, 0f, 0f, 1f, 0f)),
            )
        }
    }
    drawContext.canvas.saveLayer(Rect(c - Offset(size * 1.5f, size * 1.5f), Size(size * 3f, size * 3f)), paint)
    drawer(body)
    drawContext.canvas.restore()
    return true
}

/** Gemeinsames Gerüst einer Figur; alle Maße hängen an der Einheit [u]. Zwerge sind gedrungener. */
internal class ArtBody(val c: Offset, val u: Float, val dir: Float, val time: Float, val dwarf: Boolean = false) {
    val footY = c.y + u * 1.05f
    val hipY = c.y + u * (if (dwarf) 0.48f else 0.32f)
    val shoulderY = c.y - u * (if (dwarf) 0.1f else 0.36f)
    val head = Offset(c.x, c.y - u * (if (dwarf) 0.56f else 0.9f))
    val headR = u * (if (dwarf) 0.44f else 0.42f)
    private val reach = if (dwarf) 0.74f else 0.64f
    val hand = Offset(c.x + dir * u * reach, c.y + u * (if (dwarf) 0.3f else 0.06f))
    val offHand = Offset(c.x - dir * u * reach, hand.y)
}

internal fun Color.shade(f: Float = 0.35f) = lerp(this, Color.Black, f)
internal fun Color.bright(f: Float = 0.3f) = lerp(this, Color.White, f)

internal val ArtSkin = Color(0xFFF1C9A5)
internal val ArtBone = Color(0xFFE7E5E4)
internal val ArtBoot = Color(0xFF3F2A1D)
internal val ArtLeather = Color(0xFF78350F)
internal val ArtGold = Color(0xFFFACC15)
internal val ArtSteel = Color(0xFFC3CAD4)
internal val ArtIron = Color(0xFF6B7280)
internal val ArtInk = Color(0xFF1F2937)
internal val ArtWood = Color(0xFF6B3F1D)

/** Zwei Beine mit Stiefeln; [width] streckt die Spreizung. */
internal fun DrawScope.artLegs(
    b: ArtBody,
    pants: Color,
    boots: Color = ArtBoot,
    width: Float = 1f,
    knee: Color? = null,
    /** Stiefelhöhe in Einheiten (0.24 = Halbschuh, 0.45 = Schaftstiefel). */
    bootHeight: Float = 0.24f,
    legWidth: Float = 0.25f,
) {
    val u = b.u
    val legW = u * legWidth
    for (side in listOf(-1f, 1f)) {
        val x = b.c.x + side * u * 0.21f * width
        drawRoundRect(pants, Offset(x - legW / 2f, b.hipY), Size(legW, b.footY - b.hipY - u * 0.1f), CornerRadius(legW * 0.4f))
        drawRoundRect(pants.bright(0.15f), Offset(x - legW / 2f + legW * 0.15f, b.hipY + u * 0.05f), Size(legW * 0.25f, b.footY - b.hipY - u * 0.25f), CornerRadius(legW * 0.12f))
        knee?.let { drawCircle(it, legW * 0.42f, Offset(x, (b.hipY + b.footY) / 2f + u * 0.02f)) }
        // Stiefel mit Spitze in Blickrichtung und heller Kappe
        val bootH = u * bootHeight
        drawRoundRect(boots, Offset(x - legW * 0.62f + b.dir * u * 0.03f, b.footY - bootH), Size(legW * 1.24f, bootH), CornerRadius(u * 0.08f))
        drawLine(boots.bright(0.2f), Offset(x - legW * 0.55f + b.dir * u * 0.03f, b.footY - bootH + u * 0.02f), Offset(x + legW * 0.55f + b.dir * u * 0.03f, b.footY - bootH + u * 0.02f), u * 0.04f, StrokeCap.Round)
    }
}

/** Schlanke, sich verjüngende Frauenbeine mit Stiefeln (Oberschenkel breiter als Knöchel). */
internal fun DrawScope.artFemaleLegs(
    b: ArtBody,
    legs: Color,
    boots: Color,
    bootHeight: Float = 0.42f,
    topY: Float = b.hipY,
    /** Abstand der Beine von der Mitte, halbe Oberschenkel- und Knöchelbreite (in Einheiten). */
    spacing: Float = 0.17f,
    thigh: Float = 0.13f,
    ankle: Float = 0.07f,
    /** Halbe Breite des Stiefelschafts oben. */
    bootWidth: Float = 0.09f,
) {
    val u = b.u
    for (side in listOf(-1f, 1f)) {
        val x = b.c.x + side * u * spacing
        val leg = Path().apply {
            moveTo(x - u * thigh, topY)
            lineTo(x + u * thigh, topY)
            quadraticTo(x + u * (thigh + ankle) * 0.5f, (topY + b.footY) / 2f, x + u * ankle, b.footY - u * 0.06f)
            lineTo(x - u * ankle, b.footY - u * 0.06f)
            quadraticTo(x - u * thigh * 0.92f, (topY + b.footY) / 2f, x - u * thigh, topY)
            close()
        }
        drawPath(leg, legs)
        drawLine(legs.bright(0.2f), Offset(x - u * 0.06f * thigh / 0.13f, topY + u * 0.08f), Offset(x - u * 0.04f * ankle / 0.07f, b.footY - u * 0.2f), u * 0.035f, StrokeCap.Round)
        val bootTop = b.footY - u * bootHeight
        val foot = ankle + 0.01f
        val boot = Path().apply {
            moveTo(x - u * bootWidth, bootTop)
            lineTo(x + u * bootWidth, bootTop)
            lineTo(x + u * foot, b.footY - u * 0.08f)
            quadraticTo(x + b.dir * u * 0.2f, b.footY - u * 0.07f, x + b.dir * u * 0.18f, b.footY)
            lineTo(x - b.dir * u * 0.1f, b.footY)
            lineTo(x - u * foot, b.footY - u * 0.08f)
            close()
        }
        drawPath(boot, boots)
        drawLine(boots.bright(0.25f), Offset(x - u * bootWidth, bootTop + u * 0.02f), Offset(x + u * bootWidth, bootTop + u * 0.02f), u * 0.035f, StrokeCap.Round)
    }
}

/** Weibliche Rumpfform: schmale Schultern, Brust, Taille bei [waistY]; endet mit runder Hüfte bei [bottomY]. */
internal fun femaleTorso(b: ArtBody, waistY: Float, bottomY: Float, hip: Float = 0.31f, waist: Float = 0.2f, shoulder: Float = 0.26f, bust: Float = 0.32f): Path {
    val u = b.u
    val c = b.c
    return Path().apply {
        moveTo(c.x - u * shoulder, b.shoulderY - u * 0.04f)
        lineTo(c.x + u * shoulder, b.shoulderY - u * 0.04f)
        quadraticTo(c.x + u * (bust + 0.04f), b.shoulderY + u * 0.06f, c.x + u * bust, b.shoulderY + u * 0.24f)
        quadraticTo(c.x + u * (bust - 0.04f), waistY - u * 0.12f, c.x + u * waist, waistY)
        quadraticTo(c.x + u * (hip + 0.04f), (waistY + bottomY) / 2f, c.x + u * hip, bottomY)
        lineTo(c.x - u * hip, bottomY)
        quadraticTo(c.x - u * (hip + 0.04f), (waistY + bottomY) / 2f, c.x - u * waist, waistY)
        quadraticTo(c.x - u * (bust - 0.04f), waistY - u * 0.12f, c.x - u * bust, b.shoulderY + u * 0.24f)
        quadraticTo(c.x - u * (bust + 0.04f), b.shoulderY + u * 0.06f, c.x - u * shoulder, b.shoulderY - u * 0.04f)
        close()
    }
}

/** V-Ausschnitt in Hautfarbe mit optionalem Saum. */
internal fun DrawScope.artNeckline(b: ArtBody, skin: Color, trim: Color?, depth: Float = 0.26f, width: Float = 0.18f) {
    val neck = Path().apply {
        moveTo(b.c.x - b.u * width, b.shoulderY - b.u * 0.04f)
        lineTo(b.c.x + b.u * width, b.shoulderY - b.u * 0.04f)
        lineTo(b.c.x, b.shoulderY + b.u * depth)
        close()
    }
    drawPath(neck, skin.shade(0.06f))
    trim?.let { drawPath(neck, it, style = Stroke(b.u * 0.03f)) }
}

/** Hals und Kopf in Hautfarbe. */
internal fun DrawScope.artHead(b: ArtBody, skin: Color) {
    val h = b.head
    val hr = b.headR
    drawRoundRect(skin.shade(0.08f), Offset(h.x - hr * 0.22f, h.y + hr * 0.6f), Size(hr * 0.44f, b.shoulderY - h.y - hr * 0.4f), CornerRadius(hr * 0.1f))
    drawCircle(skin, hr, h)
    // Leichter Schatten auf der abgewandten Seite
    drawArc(skin.shade(0.1f), if (b.dir > 0) 100f else -80f, 160f, false, h - Offset(hr * 0.92f, hr * 0.92f), Size(hr * 1.84f, hr * 1.84f), style = Stroke(hr * 0.12f))
}

/** Augen mit Pupillen in Blickrichtung und kurzen Brauen. */
internal fun DrawScope.artEyes(
    b: ArtBody,
    y: Float = b.head.y + b.headR * 0.08f,
    pupil: Color = ArtInk,
    brows: Color? = Color(0xFF3F2A1D),
    /** Positiv: grimmig (innen tiefer), negativ: freundlich. */
    browTilt: Float = 0.04f,
    /** Wimpernschlag am äußeren Augenwinkel. */
    lashes: Color? = null,
    sclera: Color = Color.White,
) {
    val r = b.headR
    for (side in listOf(-1f, 1f)) {
        val e = Offset(b.head.x + side * r * 0.36f + b.dir * r * 0.06f, y)
        lashes?.let {
            drawArc(it, if (side > 0) 200f else 230f, 110f, false, e - Offset(r * 0.18f, r * 0.18f), Size(r * 0.36f, r * 0.36f), style = Stroke(r * 0.06f, cap = StrokeCap.Round))
            drawLine(it, e + Offset(side * r * 0.13f, -r * 0.1f), e + Offset(side * r * 0.24f, -r * 0.2f), r * 0.05f, StrokeCap.Round)
        }
        drawCircle(sclera, r * 0.16f, e)
        drawCircle(pupil, r * 0.1f, e + Offset(b.dir * r * 0.05f, r * 0.01f))
        drawCircle(Color.White.copy(alpha = 0.9f), r * 0.035f, e + Offset(b.dir * r * 0.02f - r * 0.03f, -r * 0.04f))
        brows?.let {
            drawLine(it, e + Offset(-r * 0.15f, -r * (0.25f - side * browTilt)), e + Offset(r * 0.15f, -r * (0.25f + side * browTilt)), r * 0.07f, StrokeCap.Round)
        }
    }
}

/** Frauengesicht: Augen mit Wimpern, Wangenröte und Mund. */
internal fun DrawScope.artFemaleFace(b: ArtBody, skin: Color, brows: Color, pupil: Color = ArtInk, lips: Color = Color(0xFFE11D48), blush: Boolean = true) {
    artHead(b, skin)
    artEyes(b, pupil = pupil, brows = brows, browTilt = 0f, lashes = ArtInk)
    val h = b.head
    val hr = b.headR
    if (blush) for (side in listOf(-1f, 1f)) {
        drawCircle(Color(0xFFFB7185).copy(alpha = 0.3f), hr * 0.14f, Offset(h.x + side * hr * 0.55f + b.dir * hr * 0.06f, h.y + hr * 0.35f))
    }
    val mouth = Offset(h.x + b.dir * hr * 0.06f, h.y + hr * 0.5f)
    drawArc(lips, 0f, 180f, true, mouth - Offset(hr * 0.15f, hr * 0.1f), Size(hr * 0.3f, hr * 0.2f))
    drawLine(lips.shade(0.35f), mouth - Offset(hr * 0.15f, 0f), mouth + Offset(hr * 0.15f, 0f), hr * 0.035f, StrokeCap.Round)
}

/** Arm von der Schulter zur Hand (wird vom Rumpf überdeckt). */
internal fun DrawScope.artArm(b: ArtBody, hand: Offset, color: Color, width: Float = 0.2f, shoulder: Float = 0.4f) {
    val sx = if (hand.x > b.c.x) b.c.x + b.u * shoulder else b.c.x - b.u * shoulder
    drawLine(color, Offset(sx, b.shoulderY + b.u * 0.1f), hand, b.u * width, StrokeCap.Round)
}

/** Hand mit Glanzpunkt. */
internal fun DrawScope.artHand(b: ArtBody, at: Offset, color: Color, r: Float = 0.11f) {
    drawCircle(color, b.u * r, at)
    drawCircle(color.bright(0.3f), b.u * r * 0.35f, at + Offset(-b.u * r * 0.35f, -b.u * r * 0.35f))
}

/** Knochige Hand: Handballen mit drei Fingerknochen. */
internal fun DrawScope.artBoneHand(b: ArtBody, at: Offset, bone: Color = ArtBone) {
    val u = b.u
    drawCircle(bone, u * 0.08f, at)
    for (i in -1..1) drawLine(bone, at, at + Offset(i * u * 0.06f + b.dir * u * 0.04f, u * 0.13f), u * 0.035f, StrokeCap.Round)
}

/** Klinge von [from] nach [to] mit Mittelgrat und Glanzkante. */
internal fun DrawScope.artBlade(from: Offset, to: Offset, halfWidth: Float, steel: Color) {
    val d = to - from
    val len = sqrt(d.x * d.x + d.y * d.y).coerceAtLeast(0.001f)
    val n = Offset(-d.y / len, d.x / len) * halfWidth
    val shoulder = from + d * 0.84f
    val path = Path().apply {
        moveTo(from.x + n.x, from.y + n.y)
        lineTo(shoulder.x + n.x, shoulder.y + n.y)
        lineTo(to.x, to.y)
        lineTo(shoulder.x - n.x, shoulder.y - n.y)
        lineTo(from.x - n.x, from.y - n.y)
        close()
    }
    drawPath(path, steel)
    drawLine(steel.shade(0.25f), from, from + d * 0.82f, halfWidth * 0.35f)
    drawLine(Color.White.copy(alpha = 0.6f), from + n * 0.55f, shoulder + n * 0.55f, halfWidth * 0.25f)
}

/** Schwert in [hand]: Klinge, Parierstange, Griff, Knauf. */
internal fun DrawScope.artSword(b: ArtBody, hand: Offset, length: Float, steel: Color, guard: Color, halfWidth: Float = 0.075f) {
    val u = b.u
    val dir = b.dir
    val g = hand + Offset(dir * u * 0.03f, -u * 0.16f)
    artBlade(g, hand + Offset(dir * u * 0.3f * length, -u * (0.16f + length)), u * halfWidth, steel)
    drawLine(Color(0xFF4B2E1A), hand + Offset(-dir * u * 0.01f, u * 0.12f), g, u * 0.08f, StrokeCap.Round)
    drawLine(guard, g + Offset(-u * 0.2f, u * 0.03f * dir), g + Offset(u * 0.2f, -u * 0.03f * dir), u * 0.08f, StrokeCap.Round)
    drawCircle(guard, u * 0.06f, hand + Offset(-dir * u * 0.01f, u * 0.14f))
}

/** Schulterplatte mit Rand und Glanzpunkt. */
internal fun DrawScope.artPauldron(b: ArtBody, side: Float, metal: Color, trim: Color, size: Float = 1f) {
    val u = b.u
    val p = Offset(b.c.x + side * u * 0.44f, b.shoulderY + u * 0.04f)
    drawOval(metal, p - Offset(u * 0.23f * size, u * 0.16f * size), Size(u * 0.46f * size, u * 0.32f * size))
    drawArc(trim, 10f, 160f, false, p - Offset(u * 0.21f * size, u * 0.13f * size), Size(u * 0.42f * size, u * 0.28f * size), style = Stroke(u * 0.045f))
    drawCircle(metal.bright(0.45f), u * 0.05f * size, p + Offset(-u * 0.06f, -u * 0.05f))
}

/** Gürtel mit Schnalle. */
internal fun DrawScope.artBelt(b: ArtBody, y: Float, halfWidth: Float, strap: Color = ArtLeather, buckle: Color = ArtGold) {
    val u = b.u
    drawLine(strap, Offset(b.c.x - u * halfWidth, y), Offset(b.c.x + u * halfWidth, y), u * 0.1f)
    drawRoundRect(buckle, Offset(b.c.x - u * 0.07f, y - u * 0.07f), Size(u * 0.14f, u * 0.14f), CornerRadius(u * 0.03f))
    drawRoundRect(strap, Offset(b.c.x - u * 0.035f, y - u * 0.035f), Size(u * 0.07f, u * 0.07f))
}

/** Pulsierendes Leuchten (0..1) mit eigenem Takt. */
internal fun ArtBody.pulse(speed: Float = 4f, phase: Float = 0f): Float = 0.6f + 0.4f * sin(time * speed + phase)

/** Kleiner Totenschädel mit Augenhöhlen und Zähnen. */
internal fun DrawScope.artSkull(c: Offset, r: Float, bone: Color = ArtBone, eyes: Color = Color(0xFF1C1917)) {
    drawCircle(bone, r, c)
    drawRoundRect(bone, Offset(c.x - r * 0.55f, c.y + r * 0.4f), Size(r * 1.1f, r * 0.6f), CornerRadius(r * 0.2f))
    for (side in listOf(-1f, 1f)) drawCircle(eyes, r * 0.28f, Offset(c.x + side * r * 0.38f, c.y + r * 0.05f))
    drawPath(Path().apply {
        moveTo(c.x, c.y + r * 0.3f)
        lineTo(c.x - r * 0.12f, c.y + r * 0.52f)
        lineTo(c.x + r * 0.12f, c.y + r * 0.52f)
        close()
    }, Color(0xFF1C1917))
    for (i in -1..1) drawLine(Color(0xFF57534E), Offset(c.x + i * r * 0.22f, c.y + r * 0.68f), Offset(c.x + i * r * 0.22f, c.y + r * 0.95f), r * 0.07f)
}

