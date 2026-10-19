package com.adlessarcade.games.survivors

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.lerp
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Die Heldenfigur einer Klasse – auch für Klassenwahl und Shop-Vorschau.
 * [bodyOnly]: ohne Schatten und Leuchten – für die Outline-Silhouette.
 */
fun DrawScope.drawHeroClass(heroClass: HeroClass, center: Offset, size: Float, time: Float, faceRight: Boolean = true, bodyOnly: Boolean = false) {
    val r = size / 2f
    val bob = sin(time * 8f) * r * 0.05f
    val c = center + Offset(0f, bob)
    val dir = if (faceRight) 1f else -1f
    if (!bodyOnly) drawOval(Color.Black.copy(alpha = 0.3f), center + Offset(-r * 0.8f, r * 0.75f), Size(r * 1.6f, r * 0.4f))
    val b = HeroBody(c, r * 0.82f, dir, time)
    when (heroClass) {
        HeroClass.KNIGHT -> drawKnight(b, time)
        HeroClass.MAGE -> drawMage(b, time, bodyOnly)
        HeroClass.RANGER -> drawRanger(b, time)
        HeroClass.NECROMANCER -> drawNecromancer(b, time, bodyOnly)
        HeroClass.CRYOMANCER -> drawCryomancer(b, time, bodyOnly)
        HeroClass.PALADIN -> drawPaladin(b, time, bodyOnly)
        HeroClass.ASSASSIN -> drawAssassin(b, time)
        HeroClass.STORMCALLER -> drawStormcaller(b, time, bodyOnly)
        HeroClass.PIRATE -> drawPirate(b, time)
    }
}

/** Gemeinsames Gerüst einer Heldenfigur; alle Maße hängen an der Einheit [u]. */
private class HeroBody(val c: Offset, val u: Float, val dir: Float, time: Float) {
    val footY = c.y + u * 1.02f
    val hipY = c.y + u * 0.32f
    val shoulderY = c.y - u * 0.36f
    val head = Offset(c.x, c.y - u * 0.9f)
    val headR = u * 0.42f
    val hand = Offset(c.x + dir * u * 0.64f, c.y + u * 0.06f)
    val offHand = Offset(c.x - dir * u * 0.64f, c.y + u * 0.06f)
    /** Wechselschritt der Beine. */
    val step = sin(time * 8f) * u * 0.05f
}

private fun Color.darker(f: Float = 0.35f) = lerp(this, Color.Black, f)
private fun Color.lighter(f: Float = 0.3f) = lerp(this, Color.White, f)

private val Skin = Color(0xFFF1C9A5)
private val BootBrown = Color(0xFF3F2A1D)
private val Leather = Color(0xFF78350F)
private val HeroGold = Color(0xFFFACC15)

/** Zwei Beine mit Stiefeln; [width] streckt die Spreizung. */
private fun DrawScope.heroLegs(
    b: HeroBody,
    pants: Color,
    boots: Color = BootBrown,
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
        val lift = if (side > 0) b.step else -b.step
        val foot = b.footY - lift.coerceAtLeast(0f)
        drawRoundRect(pants, Offset(x - legW / 2f, b.hipY), Size(legW, foot - b.hipY - u * 0.1f), CornerRadius(legW * 0.4f))
        drawRoundRect(pants.lighter(0.15f), Offset(x - legW / 2f + legW * 0.15f, b.hipY + u * 0.05f), Size(legW * 0.25f, foot - b.hipY - u * 0.25f), CornerRadius(legW * 0.12f))
        knee?.let { drawCircle(it, legW * 0.42f, Offset(x, (b.hipY + foot) / 2f + u * 0.02f)) }
        // Stiefel mit Spitze in Blickrichtung und heller Kappe
        val bootH = u * bootHeight
        drawRoundRect(boots, Offset(x - legW * 0.62f + b.dir * u * 0.03f, foot - bootH), Size(legW * 1.24f, bootH), CornerRadius(u * 0.08f))
        drawLine(boots.lighter(0.2f), Offset(x - legW * 0.55f + b.dir * u * 0.03f, foot - bootH + u * 0.02f), Offset(x + legW * 0.55f + b.dir * u * 0.03f, foot - bootH + u * 0.02f), u * 0.04f, StrokeCap.Round)
    }
}

/** Weibliche Rumpfform: schmale Schultern, Brust, Taille bei [waistY]; endet mit leichter Hüfte bei [bottomY]. */
private fun femaleTorso(b: HeroBody, waistY: Float, bottomY: Float, hip: Float = 0.3f, waist: Float = 0.22f, shoulder: Float = 0.27f, bust: Float = 0.33f): Path {
    val u = b.u
    val c = b.c
    return Path().apply {
        moveTo(c.x - u * shoulder, b.shoulderY - u * 0.04f)
        lineTo(c.x + u * shoulder, b.shoulderY - u * 0.04f)
        quadraticTo(c.x + u * (bust + 0.04f), b.shoulderY + u * 0.06f, c.x + u * bust, b.shoulderY + u * 0.24f)
        quadraticTo(c.x + u * (bust - 0.04f), waistY - u * 0.12f, c.x + u * waist, waistY)
        quadraticTo(c.x + u * hip, (waistY + bottomY) / 2f, c.x + u * hip, bottomY)
        lineTo(c.x - u * hip, bottomY)
        quadraticTo(c.x - u * hip, (waistY + bottomY) / 2f, c.x - u * waist, waistY)
        quadraticTo(c.x - u * (bust - 0.04f), waistY - u * 0.12f, c.x - u * bust, b.shoulderY + u * 0.24f)
        quadraticTo(c.x - u * (bust + 0.04f), b.shoulderY + u * 0.06f, c.x - u * shoulder, b.shoulderY - u * 0.04f)
        close()
    }
}

/** Freundliches Frauengesicht: Augen mit Wimpern, Wangenröte und lächelnder Mund. */
private fun DrawScope.femaleFace(b: HeroBody, brows: Color, skin: Color = Skin, freckles: Boolean = false, pupil: Color = Color(0xFF1F2937)) {
    val h = b.head
    val hr = b.headR
    drawRoundRect(skin.darker(0.06f), Offset(h.x - hr * 0.22f, h.y + hr * 0.6f), Size(hr * 0.44f, b.shoulderY - h.y - hr * 0.4f), CornerRadius(hr * 0.1f))
    drawCircle(skin, hr, h)
    heroEyes(b, pupil = pupil, brows = brows, browTilt = 0f, lashes = Color(0xFF1F2937))
    for (side in listOf(-1f, 1f)) {
        val cheek = Offset(h.x + side * hr * 0.55f + b.dir * hr * 0.06f, h.y + hr * 0.35f)
        drawCircle(Color(0xFFFB7185).copy(alpha = 0.35f), hr * 0.14f, cheek)
        if (freckles) for (i in 0..2) drawCircle(Color(0xFFB45309).copy(alpha = 0.6f), hr * 0.03f, cheek + Offset((i - 1) * hr * 0.09f, -hr * 0.04f + (i % 2) * hr * 0.05f))
    }
    val mouth = Offset(h.x + b.dir * hr * 0.06f, h.y + hr * 0.5f)
    drawArc(Color(0xFFE11D48), 0f, 180f, true, mouth - Offset(hr * 0.16f, hr * 0.1f), Size(hr * 0.32f, hr * 0.2f))
    drawLine(Color(0xFF9F1239), mouth - Offset(hr * 0.16f, 0f), mouth + Offset(hr * 0.16f, 0f), hr * 0.035f, StrokeCap.Round)
}

/** Oberarm von der Schulter zur Hand (wird vom Rumpf überdeckt). */
private fun DrawScope.heroArm(b: HeroBody, hand: Offset, color: Color, width: Float = 0.2f, shoulder: Float = 0.4f) {
    val sx = if (hand.x > b.c.x) b.c.x + b.u * shoulder else b.c.x - b.u * shoulder
    drawLine(color, Offset(sx, b.shoulderY + b.u * 0.1f), hand, b.u * width, StrokeCap.Round)
}

/** Augen mit Pupillen in Blickrichtung und kurzen, entschlossenen Brauen. */
private fun DrawScope.heroEyes(
    b: HeroBody,
    y: Float = b.head.y + b.headR * 0.08f,
    pupil: Color = Color(0xFF1F2937),
    brows: Color? = Color(0xFF3F2A1D),
    /** Positiv: grimmig (innen tiefer), negativ: freundlich. */
    browTilt: Float = 0.04f,
    /** Wimpernschlag am äußeren Augenwinkel. */
    lashes: Color? = null,
) {
    val r = b.headR
    for (side in listOf(-1f, 1f)) {
        val e = Offset(b.head.x + side * r * 0.36f + b.dir * r * 0.06f, y)
        lashes?.let {
            drawArc(it, if (side > 0) 200f else 230f, 110f, false, e - Offset(r * 0.18f, r * 0.18f), Size(r * 0.36f, r * 0.36f), style = Stroke(r * 0.06f, cap = StrokeCap.Round))
            drawLine(it, e + Offset(side * r * 0.13f, -r * 0.1f), e + Offset(side * r * 0.24f, -r * 0.2f), r * 0.05f, StrokeCap.Round)
        }
        drawCircle(Color.White, r * 0.16f, e)
        drawCircle(pupil, r * 0.1f, e + Offset(b.dir * r * 0.05f, r * 0.01f))
        drawCircle(Color.White.copy(alpha = 0.9f), r * 0.035f, e + Offset(b.dir * r * 0.02f - r * 0.03f, -r * 0.04f))
        brows?.let {
            drawLine(it, e + Offset(-r * 0.15f, -r * (0.25f - side * browTilt)), e + Offset(r * 0.15f, -r * (0.25f + side * browTilt)), r * 0.07f, StrokeCap.Round)
        }
    }
}

/** Klinge von [from] nach [to] mit Mittelgrat. */
private fun DrawScope.blade(from: Offset, to: Offset, halfWidth: Float, steel: Color) {
    val d = to - from
    val len = sqrt(d.x * d.x + d.y * d.y)
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
    drawLine(steel.darker(0.25f), from, from + d * 0.82f, halfWidth * 0.35f)
    drawLine(Color.White.copy(alpha = 0.6f), from + n * 0.55f, shoulder + n * 0.55f, halfWidth * 0.25f)
}

// --- Krieger ---

private fun DrawScope.drawKnight(b: HeroBody, time: Float) {
    val u = b.u
    val c = b.c
    val dir = b.dir
    val steel = Color(0xFFC3CAD4)
    val steelDark = Color(0xFF6B7280)
    val blue = Color(0xFF1D4ED8)

    // Umhang hinter dem Körper
    val sway = sin(time * 3f) * u * 0.06f
    val cape = Path().apply {
        moveTo(c.x - u * 0.42f, b.shoulderY)
        lineTo(c.x + u * 0.42f, b.shoulderY)
        lineTo(c.x + u * 0.74f - dir * sway, b.footY - u * 0.1f)
        quadraticTo(c.x, b.footY + u * 0.02f, c.x - u * 0.74f - dir * sway, b.footY - u * 0.1f)
        close()
    }
    drawPath(cape, blue.darker(0.3f))
    for (s in listOf(-1f, 1f)) {
        drawLine(blue.darker(0.55f), Offset(c.x + s * u * 0.42f, b.shoulderY + u * 0.35f), Offset(c.x + s * u * 0.62f - dir * sway, b.footY - u * 0.12f), u * 0.05f, StrokeCap.Round)
    }

    heroLegs(b, steelDark, knee = steel)

    // Waffenrock-Schoß über den Oberschenkeln
    val skirt = Path().apply {
        moveTo(c.x - u * 0.4f, b.hipY - u * 0.05f)
        lineTo(c.x + u * 0.4f, b.hipY - u * 0.05f)
        lineTo(c.x + u * 0.46f, b.hipY + u * 0.27f)
        lineTo(c.x - u * 0.46f, b.hipY + u * 0.27f)
        close()
    }
    drawPath(skirt, blue)
    drawLine(HeroGold, Offset(c.x - u * 0.455f, b.hipY + u * 0.245f), Offset(c.x + u * 0.455f, b.hipY + u * 0.245f), u * 0.05f)
    drawLine(blue.darker(0.3f), Offset(c.x, b.hipY), Offset(c.x, b.hipY + u * 0.25f), u * 0.04f)

    // Arme in Kettenhemd
    heroArm(b, b.hand, steelDark, 0.22f)
    heroArm(b, b.offHand, steelDark, 0.22f)

    // Brustpanzer mit Licht von links oben
    val top = Offset(c.x - u * 0.46f, b.shoulderY - u * 0.06f)
    val torso = Size(u * 0.92f, b.hipY - b.shoulderY + u * 0.12f)
    drawRoundRect(steel, top, torso, CornerRadius(u * 0.24f))
    drawRoundRect(steel.darker(0.15f), top + Offset(torso.width * 0.55f, 0f), Size(torso.width * 0.45f, torso.height), CornerRadius(u * 0.24f))
    drawRoundRect(steel.lighter(0.45f), top + Offset(u * 0.1f, u * 0.08f), Size(u * 0.16f, torso.height * 0.45f), CornerRadius(u * 0.08f))
    // Wappenrock mit goldenem Saum und Wappen
    val tabardTop = Offset(c.x - u * 0.22f, b.shoulderY + u * 0.1f)
    val tabard = Size(u * 0.44f, b.hipY - b.shoulderY)
    drawRoundRect(blue, tabardTop, tabard, CornerRadius(u * 0.06f))
    drawRoundRect(HeroGold, tabardTop, tabard, CornerRadius(u * 0.06f), style = Stroke(u * 0.04f))
    val emblem = Offset(c.x, b.shoulderY + u * 0.33f)
    val diamond = Path().apply {
        moveTo(emblem.x, emblem.y - u * 0.12f)
        lineTo(emblem.x + u * 0.08f, emblem.y)
        lineTo(emblem.x, emblem.y + u * 0.12f)
        lineTo(emblem.x - u * 0.08f, emblem.y)
        close()
    }
    drawPath(diamond, HeroGold)
    // Gürtel mit Schnalle
    drawLine(Leather, Offset(c.x - u * 0.46f, b.hipY), Offset(c.x + u * 0.46f, b.hipY), u * 0.1f)
    drawRoundRect(HeroGold, Offset(c.x - u * 0.07f, b.hipY - u * 0.07f), Size(u * 0.14f, u * 0.14f), CornerRadius(u * 0.03f))
    drawRoundRect(Leather, Offset(c.x - u * 0.035f, b.hipY - u * 0.035f), Size(u * 0.07f, u * 0.07f))

    // Schulterplatten
    for (side in listOf(-1f, 1f)) {
        val p = Offset(c.x + side * u * 0.44f, b.shoulderY + u * 0.04f)
        drawOval(steel, p - Offset(u * 0.22f, u * 0.15f), Size(u * 0.44f, u * 0.3f))
        drawArc(steelDark, 10f, 160f, false, p - Offset(u * 0.2f, u * 0.12f), Size(u * 0.4f, u * 0.26f), style = Stroke(u * 0.04f))
        drawCircle(steel.lighter(0.5f), u * 0.05f, p + Offset(-u * 0.06f, -u * 0.05f))
    }

    // Drachenschild in der zweiten Hand
    val sc = b.offHand + Offset(-dir * u * 0.1f, -u * 0.04f)
    val shield = Path().apply {
        moveTo(sc.x, sc.y - u * 0.5f)
        lineTo(sc.x + u * 0.38f, sc.y - u * 0.38f)
        lineTo(sc.x + u * 0.34f, sc.y + u * 0.12f)
        quadraticTo(sc.x + u * 0.22f, sc.y + u * 0.45f, sc.x, sc.y + u * 0.6f)
        quadraticTo(sc.x - u * 0.22f, sc.y + u * 0.45f, sc.x - u * 0.34f, sc.y + u * 0.12f)
        lineTo(sc.x - u * 0.38f, sc.y - u * 0.38f)
        close()
    }
    drawPath(shield, blue)
    val chevron = Path().apply {
        moveTo(sc.x - u * 0.3f, sc.y - u * 0.02f)
        lineTo(sc.x, sc.y - u * 0.3f)
        lineTo(sc.x + u * 0.3f, sc.y - u * 0.02f)
        lineTo(sc.x + u * 0.3f, sc.y + u * 0.14f)
        lineTo(sc.x, sc.y - u * 0.14f)
        lineTo(sc.x - u * 0.3f, sc.y + u * 0.14f)
        close()
    }
    drawPath(chevron, Color(0xFFF1F5F9))
    drawPath(shield, HeroGold, style = Stroke(u * 0.07f))
    drawCircle(steel, u * 0.08f, sc + Offset(0f, u * 0.25f))

    // Geschlossener Topfhelm mit Sehschlitz, Atemlöchern und Federbusch
    val h = b.head
    val hr = b.headR
    val plume = Path().apply {
        moveTo(h.x, h.y - hr * 1.05f)
        cubicTo(h.x - dir * hr * 0.3f, h.y - hr * 1.9f, h.x - dir * hr * 1.3f, h.y - hr * 1.6f, h.x - dir * hr * 1.45f, h.y - hr * 0.55f + sway)
    }
    drawPath(plume, Color(0xFFB91C1C), style = Stroke(hr * 0.42f, cap = StrokeCap.Round))
    drawPath(plume, Color(0xFFEF4444), style = Stroke(hr * 0.18f, cap = StrokeCap.Round))
    val helmTl = h + Offset(-hr * 1.02f, -hr * 1.12f)
    val helm = Size(hr * 2.04f, hr * 2.1f)
    drawRoundRect(steel, helmTl, helm, CornerRadius(hr * 0.95f, hr * 0.8f))
    // Glanz links oben
    drawArc(steel.lighter(0.55f), 195f, 55f, false, h + Offset(-hr * 0.8f, -hr * 0.95f), Size(hr * 1.6f, hr * 1.5f), style = Stroke(hr * 0.13f, cap = StrokeCap.Round))
    // Mittelgrat und Kinnrand
    drawLine(steel.darker(0.3f), Offset(h.x + dir * hr * 0.08f, h.y - hr * 1.08f), Offset(h.x + dir * hr * 0.08f, h.y + hr * 0.95f), hr * 0.1f)
    drawLine(steel.darker(0.3f), Offset(h.x - hr * 0.75f, h.y + hr * 0.9f), Offset(h.x + hr * 0.75f, h.y + hr * 0.9f), hr * 0.14f, StrokeCap.Round)
    // Sehschlitz mit leisem Augenschimmer
    val slitY = h.y + hr * 0.02f
    drawLine(Color(0xFF111827), Offset(h.x - hr * 0.72f + dir * hr * 0.06f, slitY), Offset(h.x + hr * 0.72f + dir * hr * 0.06f, slitY), hr * 0.22f, StrokeCap.Round)
    for (side in listOf(-1f, 1f)) {
        drawCircle(Color(0xFFBAE6FD).copy(alpha = 0.75f), hr * 0.07f, Offset(h.x + side * hr * 0.34f + dir * hr * 0.14f, slitY))
    }
    // Atemlöcher mittig unter dem Sehschlitz (der Grat endet darüber)
    drawRoundRect(steel, Offset(h.x + dir * hr * 0.08f - hr * 0.42f, h.y + hr * 0.27f), Size(hr * 0.84f, hr * 0.42f), CornerRadius(hr * 0.1f))
    for (row in 0..1) for (i in -1..1) {
        drawCircle(Color(0xFF111827), hr * 0.055f, Offset(h.x + dir * hr * 0.08f + i * hr * 0.2f, h.y + hr * (0.38f + row * 0.2f)))
    }
    drawCircle(HeroGold, hr * 0.14f, Offset(h.x, h.y - hr * 1.05f))

    // Schwert: Klinge, goldene Parierstange, Griff und Knauf
    val hand = b.hand
    val guard = hand + Offset(dir * u * 0.03f, -u * 0.16f)
    blade(guard, hand + Offset(dir * u * 0.32f, -u * 1.25f), u * 0.075f, Color(0xFFE5E7EB))
    drawLine(Color(0xFF4B2E1A), hand + Offset(-dir * u * 0.01f, u * 0.12f), guard, u * 0.08f, StrokeCap.Round)
    drawLine(HeroGold, guard + Offset(-u * 0.21f, u * 0.03f * dir), guard + Offset(u * 0.21f, -u * 0.03f * dir), u * 0.08f, StrokeCap.Round)
    drawCircle(HeroGold, u * 0.065f, hand + Offset(-dir * u * 0.01f, u * 0.14f))
    // Panzerhandschuhe
    drawCircle(steel.darker(0.1f), u * 0.12f, hand)
    drawCircle(steel.lighter(0.3f), u * 0.04f, hand + Offset(-u * 0.04f, -u * 0.04f))
}

// --- Magierin ---

private fun DrawScope.drawMage(b: HeroBody, time: Float, bodyOnly: Boolean) {
    val u = b.u
    val c = b.c
    val dir = b.dir
    val violet = Color(0xFF7C3AED)
    val hair = Color(0xFFC2410C)
    val pink = Color(0xFFF472B6)
    val h = b.head
    val hr = b.headR

    // Lange Haare hinter Kopf und Schultern
    val hairSway = sin(time * 3f) * u * 0.03f
    drawRoundRect(hair, Offset(h.x - hr * 1.12f - hairSway, h.y - hr * 0.4f), Size(hr * 2.24f, b.shoulderY - h.y + u * 0.28f), CornerRadius(hr * 0.9f))
    drawRoundRect(hair.darker(0.2f), Offset(h.x - hr * 0.2f - hairSway, h.y + hr * 0.3f), Size(hr * 0.4f, b.shoulderY - h.y + u * 0.08f), CornerRadius(hr * 0.2f))

    // Spitze Schuhe unter dem Saum
    for (side in listOf(-1f, 1f)) {
        val lift = if (side > 0) b.step else -b.step
        val foot = Offset(c.x + side * u * 0.2f, b.footY - lift.coerceAtLeast(0f) - u * 0.08f)
        val shoe = Path().apply {
            moveTo(foot.x - dir * u * 0.14f, foot.y + u * 0.08f)
            lineTo(foot.x - dir * u * 0.14f, foot.y - u * 0.06f)
            quadraticTo(foot.x + dir * u * 0.1f, foot.y - u * 0.1f, foot.x + dir * u * 0.26f, foot.y - u * 0.02f)
            lineTo(foot.x + u * 0.14f * dir, foot.y + u * 0.08f)
            close()
        }
        drawPath(shoe, Color(0xFF4C1D95))
    }

    // Weite Ärmel
    for (hand in listOf(b.hand, b.offHand)) {
        heroArm(b, hand, violet.darker(0.1f), 0.24f)
        val cuff = Path().apply {
            val s = if (hand.x > c.x) 1f else -1f
            moveTo(hand.x - s * u * 0.18f, hand.y - u * 0.2f)
            lineTo(hand.x + s * u * 0.05f, hand.y - u * 0.16f)
            lineTo(hand.x + s * u * 0.12f, hand.y + u * 0.08f)
            lineTo(hand.x - s * u * 0.22f, hand.y + u * 0.12f)
            close()
        }
        drawPath(cuff, violet.lighter(0.1f))
        drawLine(HeroGold, Offset(hand.x - (if (hand.x > c.x) 1f else -1f) * u * 0.22f, hand.y + u * 0.12f), Offset(hand.x + (if (hand.x > c.x) 1f else -1f) * u * 0.12f, hand.y + u * 0.08f), u * 0.04f, StrokeCap.Round)
    }

    // Kleid: schmale Schultern, Taille und weit fallender Rock mit welligem Saum
    val hemY = b.footY - u * 0.12f
    val waistY = b.hipY - u * 0.06f
    val robe = Path().apply {
        moveTo(c.x - u * 0.27f, b.shoulderY - u * 0.04f)
        lineTo(c.x + u * 0.27f, b.shoulderY - u * 0.04f)
        quadraticTo(c.x + u * 0.37f, b.shoulderY + u * 0.06f, c.x + u * 0.33f, b.shoulderY + u * 0.24f)
        quadraticTo(c.x + u * 0.29f, waistY - u * 0.12f, c.x + u * 0.22f, waistY)
        cubicTo(c.x + u * 0.32f, b.hipY + u * 0.12f, c.x + u * 0.55f, b.hipY + u * 0.3f, c.x + u * 0.68f, hemY)
        quadraticTo(c.x + u * 0.45f, hemY + u * 0.1f, c.x + u * 0.22f, hemY)
        quadraticTo(c.x, hemY + u * 0.1f, c.x - u * 0.22f, hemY)
        quadraticTo(c.x - u * 0.45f, hemY + u * 0.1f, c.x - u * 0.68f, hemY)
        cubicTo(c.x - u * 0.55f, b.hipY + u * 0.3f, c.x - u * 0.32f, b.hipY + u * 0.12f, c.x - u * 0.22f, waistY)
        quadraticTo(c.x - u * 0.29f, waistY - u * 0.12f, c.x - u * 0.33f, b.shoulderY + u * 0.24f)
        quadraticTo(c.x - u * 0.37f, b.shoulderY + u * 0.06f, c.x - u * 0.27f, b.shoulderY - u * 0.04f)
        close()
    }
    drawPath(robe, violet)
    clipPath(robe) {
        drawRect(violet.darker(0.2f), Offset(c.x + u * 0.2f, b.shoulderY - u * 0.1f), Size(u * 0.6f, b.footY - b.shoulderY + u * 0.1f))
    }
    // Mieder mit Brustnähten
    for (side in listOf(-1f, 1f)) {
        drawArc(violet.darker(0.3f), 20f, 140f, false, Offset(c.x + side * u * 0.14f - u * 0.15f, b.shoulderY + u * 0.08f), Size(u * 0.3f, u * 0.22f), style = Stroke(u * 0.03f, cap = StrokeCap.Round))
        drawArc(violet.lighter(0.3f), 200f, 70f, false, Offset(c.x + side * u * 0.14f - u * 0.12f, b.shoulderY + u * 0.1f), Size(u * 0.24f, u * 0.18f), style = Stroke(u * 0.03f, cap = StrokeCap.Round))
    }
    // V-Ausschnitt mit Goldsaum
    val neck = Path().apply {
        moveTo(c.x - u * 0.19f, b.shoulderY - u * 0.04f)
        lineTo(c.x + u * 0.19f, b.shoulderY - u * 0.04f)
        lineTo(c.x, b.shoulderY + u * 0.26f)
        close()
    }
    drawPath(neck, Skin.darker(0.06f))
    drawPath(neck, HeroGold, style = Stroke(u * 0.03f))
    val panel = Path().apply {
        moveTo(c.x - u * 0.08f, waistY)
        lineTo(c.x + u * 0.08f, waistY)
        lineTo(c.x + u * 0.2f, hemY + u * 0.02f)
        quadraticTo(c.x, hemY + u * 0.1f, c.x - u * 0.2f, hemY + u * 0.02f)
        close()
    }
    drawPath(panel, violet.lighter(0.25f))
    drawPath(panel, HeroGold, style = Stroke(u * 0.035f))
    // Sterne auf dem Rock
    for ((sx, sy) in listOf(-0.4f to 0.62f, 0.38f to 0.72f, -0.26f to 0.86f)) {
        drawCircle(Color(0xFFFDE68A), u * 0.035f, Offset(c.x + sx * u, c.y + sy * u))
    }
    // Puffärmel an den Schultern
    for (side in listOf(-1f, 1f)) {
        drawCircle(violet.lighter(0.1f), u * 0.14f, Offset(c.x + side * u * 0.36f, b.shoulderY + u * 0.06f))
        drawArc(violet.darker(0.25f), 30f, 120f, false, Offset(c.x + side * u * 0.36f - u * 0.12f, b.shoulderY - u * 0.04f), Size(u * 0.24f, u * 0.22f), style = Stroke(u * 0.03f))
    }
    // Schärpe um die Taille
    drawLine(pink, Offset(c.x - u * 0.24f, waistY), Offset(c.x + u * 0.24f, waistY), u * 0.1f, StrokeCap.Round)
    drawLine(pink.darker(0.2f), Offset(c.x - dir * u * 0.13f, waistY), Offset(c.x - dir * u * 0.22f, waistY + u * 0.32f), u * 0.07f, StrokeCap.Round)
    drawCircle(HeroGold, u * 0.06f, Offset(c.x - dir * u * 0.13f, waistY))

    // Hals, Gesicht und Pony
    drawRoundRect(Skin.darker(0.06f), Offset(h.x - hr * 0.22f, h.y + hr * 0.6f), Size(hr * 0.44f, b.shoulderY - h.y - hr * 0.4f), CornerRadius(hr * 0.1f))
    drawCircle(Skin, hr, h)
    heroEyes(b, brows = hair.darker(0.3f), browTilt = 0f, lashes = Color(0xFF1F2937))
    drawCircle(Color(0xFFFB7185).copy(alpha = 0.35f), hr * 0.14f, Offset(h.x - hr * 0.55f + b.dir * hr * 0.06f, h.y + hr * 0.35f))
    drawCircle(Color(0xFFFB7185).copy(alpha = 0.35f), hr * 0.14f, Offset(h.x + hr * 0.55f + b.dir * hr * 0.06f, h.y + hr * 0.35f))
    val mouth = Offset(h.x + dir * hr * 0.06f, h.y + hr * 0.5f)
    drawArc(Color(0xFFE11D48), 0f, 180f, true, mouth - Offset(hr * 0.16f, hr * 0.1f), Size(hr * 0.32f, hr * 0.2f))
    drawLine(Color(0xFF9F1239), mouth - Offset(hr * 0.16f, 0f), mouth + Offset(hr * 0.16f, 0f), hr * 0.035f, StrokeCap.Round)
    // Pony hoch auf der Stirn, seitliche Strähnen rahmen das Gesicht
    // Nur unterhalb der Hutkrempe zeichnen, damit nichts oben aus dem Hut ragt
    clipRect(top = h.y - hr * 0.55f) {
        drawArc(hair, 180f, 180f, true, h + Offset(-hr * 1.04f, -hr * 1.04f), Size(hr * 2.08f, hr * 0.92f))
        for (side in listOf(-1f, 1f)) {
            drawRoundRect(hair, Offset(h.x + side * hr * 0.9f - hr * 0.16f, h.y - hr * 0.62f), Size(hr * 0.32f, hr * 1.25f), CornerRadius(hr * 0.16f))
        }
        drawArc(hair.lighter(0.25f), 210f, 50f, false, h + Offset(-hr * 0.8f, -hr * 0.9f), Size(hr * 1.6f, hr * 0.8f), style = Stroke(hr * 0.1f, cap = StrokeCap.Round))
    }

    // Spitzhut mit breiter Krempe, geknickter Spitze und Sternschnalle
    val brimY = h.y - hr * 0.55f
    val hatColor = Color(0xFF5B21B6)
    val tip = Offset(h.x + dir * hr * 1.05f, h.y - hr * 2.15f + sin(time * 2f) * hr * 0.05f)
    val cone = Path().apply {
        moveTo(h.x - dir * hr * 0.8f, brimY)
        quadraticTo(h.x - dir * hr * 0.35f, h.y - hr * 1.6f, h.x + dir * hr * 0.15f, h.y - hr * 1.95f)
        quadraticTo(h.x + dir * hr * 0.6f, h.y - hr * 2.25f, tip.x, tip.y)
        quadraticTo(h.x + dir * hr * 0.5f, h.y - hr * 1.8f, h.x + dir * hr * 0.6f, h.y - hr * 1.2f)
        lineTo(h.x + dir * hr * 0.8f, brimY)
        close()
    }
    drawPath(cone, hatColor)
    drawOval(hatColor.darker(0.2f), Offset(h.x - hr * 1.45f, brimY - hr * 0.22f), Size(hr * 2.9f, hr * 0.5f))
    drawOval(hatColor.lighter(0.15f), Offset(h.x - hr * 1.4f, brimY - hr * 0.24f), Size(hr * 2.8f, hr * 0.36f))
    drawLine(HeroGold, Offset(h.x - hr * 0.78f, brimY - hr * 0.2f), Offset(h.x + hr * 0.78f, brimY - hr * 0.2f), hr * 0.2f)
    star(Offset(h.x + dir * hr * 0.1f, brimY - hr * 0.2f), hr * 0.2f, Color(0xFFFDE68A))
    drawCircle(Color(0xFFFDE68A), hr * 0.06f, Offset(h.x - dir * hr * 0.25f, h.y - hr * 1.35f))
    drawCircle(Color(0xFFFDE68A), hr * 0.05f, Offset(h.x + dir * hr * 0.25f, h.y - hr * 1.7f))

    // Stab mit Krümmung und Zauberkugel
    val hand = b.hand
    val top = hand + Offset(dir * u * 0.04f, -u * 1.3f)
    drawLine(Color(0xFF6B3F1D), hand + Offset(-dir * u * 0.02f, u * 0.78f), top, u * 0.09f, StrokeCap.Round)
    drawLine(Color(0xFF8B5A2B), hand + Offset(-dir * u * 0.04f, u * 0.7f), top + Offset(-u * 0.02f, u * 0.05f), u * 0.03f, StrokeCap.Round)
    val orb = top + Offset(0f, -u * 0.14f)
    drawArc(Color(0xFF6B3F1D), 120f, 300f, false, orb - Offset(u * 0.2f, u * 0.2f), Size(u * 0.4f, u * 0.4f), style = Stroke(u * 0.06f, cap = StrokeCap.Round))
    val pulse = 0.7f + 0.3f * sin(time * 6f)
    if (!bodyOnly) drawCircle(pink.copy(alpha = 0.35f * pulse), u * 0.32f, orb)
    drawCircle(pink, u * 0.15f, orb)
    drawCircle(Color.White.copy(alpha = 0.85f), u * 0.05f, orb + Offset(-u * 0.05f, -u * 0.05f))
    drawCircle(Skin.darker(0.08f), u * 0.11f, hand)

    // Kleine Flamme über der zweiten Hand (Feuerball)
    drawCircle(Skin.darker(0.08f), u * 0.11f, b.offHand)
    if (!bodyOnly) {
        val f = b.offHand + Offset(0f, -u * 0.3f)
        val flicker = sin(time * 14f) * u * 0.04f
        drawCircle(Color(0xFFF97316).copy(alpha = 0.3f), u * 0.22f, f)
        val flame = Path().apply {
            moveTo(f.x - u * 0.12f, f.y + u * 0.06f)
            quadraticTo(f.x - u * 0.12f, f.y - u * 0.1f, f.x + flicker, f.y - u * 0.26f)
            quadraticTo(f.x + u * 0.12f, f.y - u * 0.1f, f.x + u * 0.12f, f.y + u * 0.06f)
            quadraticTo(f.x, f.y + u * 0.16f, f.x - u * 0.12f, f.y + u * 0.06f)
            close()
        }
        drawPath(flame, Color(0xFFF97316))
        drawCircle(Color(0xFFFDE047), u * 0.06f, f + Offset(0f, u * 0.02f))
    }
}

// --- Waldläuferin ---

private fun DrawScope.drawRanger(b: HeroBody, time: Float) {
    val u = b.u
    val c = b.c
    val dir = b.dir
    val h = b.head
    val hr = b.headR
    val cloak = Color(0xFF14532D)
    val tunic = Color(0xFF16A34A)
    val hair = Color(0xFFFCD34D)
    val sway = sin(time * 3f) * u * 0.05f

    // Umhang mit Kapuze hinter dem Körper
    val cape = Path().apply {
        moveTo(c.x - u * 0.36f, b.shoulderY - u * 0.05f)
        lineTo(c.x + u * 0.36f, b.shoulderY - u * 0.05f)
        lineTo(c.x + u * 0.62f - dir * sway, b.footY - u * 0.22f)
        lineTo(c.x + u * 0.3f - dir * sway, b.footY - u * 0.3f)
        lineTo(c.x - dir * sway, b.footY - u * 0.18f)
        lineTo(c.x - u * 0.3f - dir * sway, b.footY - u * 0.3f)
        lineTo(c.x - u * 0.62f - dir * sway, b.footY - u * 0.22f)
        close()
    }
    drawPath(cape, cloak)
    val hoodBack = Path().apply {
        moveTo(h.x - hr * 1.2f, h.y + hr * 0.6f)
        quadraticTo(h.x - hr * 1.35f, h.y - hr * 1.35f, h.x - dir * hr * 0.2f, h.y - hr * 1.3f)
        quadraticTo(h.x - dir * hr * 1.1f, h.y - hr * 1.6f, h.x - dir * hr * 1.6f, h.y - hr * 0.9f)
        quadraticTo(h.x + hr * 1.35f * dir, h.y - hr * 1.2f, h.x + hr * 1.2f, h.y + hr * 0.6f)
        close()
    }
    drawPath(hoodBack, cloak)

    // Köcher mit Pfeilen über der hinteren Schulter
    val qx = c.x - dir * u * 0.3f
    for (i in 0..2) {
        val ax = qx - dir * u * (0.02f + i * 0.08f)
        val tip = Offset(ax - dir * u * 0.1f, b.shoulderY - u * 0.52f + i * u * 0.04f)
        drawLine(Color(0xFFD6D3D1), Offset(ax, b.shoulderY), tip, u * 0.035f)
        val fletch = Path().apply {
            moveTo(tip.x, tip.y)
            lineTo(tip.x - u * 0.07f, tip.y + u * 0.14f)
            lineTo(tip.x + u * 0.07f, tip.y + u * 0.14f)
            close()
        }
        drawPath(fletch, if (i == 1) Color(0xFFF5F5F4) else Color(0xFFDC2626))
    }
    drawRoundRect(Leather, Offset(qx - dir * u * 0.12f - u * 0.11f, b.shoulderY - u * 0.24f), Size(u * 0.22f, u * 0.6f), CornerRadius(u * 0.07f))
    drawLine(Leather.lighter(0.25f), Offset(qx - dir * u * 0.12f - u * 0.11f, b.shoulderY - u * 0.2f), Offset(qx - dir * u * 0.12f + u * 0.11f, b.shoulderY - u * 0.2f), u * 0.04f)

    // Beine in Lederhose mit hohen Stiefeln
    heroLegs(b, Color(0xFF6B4423), Color(0xFF3F2A1D), bootHeight = 0.42f, legWidth = 0.23f)

    // Arme mit Armschiene am Bogenarm
    heroArm(b, b.hand, tunic.darker(0.1f), 0.18f)
    heroArm(b, b.offHand, tunic.darker(0.1f), 0.18f)
    val bracerDir = b.hand - Offset(c.x + dir * u * 0.4f, b.shoulderY + u * 0.1f)
    drawLine(Leather, b.hand - bracerDir * 0.32f, b.hand - bracerDir * 0.08f, u * 0.2f, StrokeCap.Round)

    // Tunika mit Taille und blattförmig gezacktem Saum
    val waistY = b.hipY - u * 0.06f
    drawPath(femaleTorso(b, waistY, b.hipY + u * 0.05f, hip = 0.3f), tunic)
    val skirt = Path().apply {
        moveTo(c.x - u * 0.27f, waistY)
        lineTo(c.x + u * 0.27f, waistY)
        lineTo(c.x + u * 0.44f, b.hipY + u * 0.28f)
        for (i in 0..4) {
            val x0 = c.x + u * 0.44f - i * u * 0.176f
            lineTo(x0 - u * 0.088f, b.hipY + u * 0.4f)
            lineTo(x0 - u * 0.176f, b.hipY + u * 0.28f)
        }
        close()
    }
    drawPath(skirt, tunic)
    clipPath(skirt) { drawRect(tunic.darker(0.18f), Offset(c.x + u * 0.18f, waistY), Size(u * 0.4f, u * 0.6f)) }
    // V-Ausschnitt mit Schnürung darunter
    val neck = Path().apply {
        moveTo(c.x - u * 0.18f, b.shoulderY - u * 0.04f)
        lineTo(c.x + u * 0.18f, b.shoulderY - u * 0.04f)
        lineTo(c.x, b.shoulderY + u * 0.26f)
        close()
    }
    drawPath(neck, Skin.darker(0.06f))
    drawPath(neck, tunic.darker(0.3f), style = Stroke(u * 0.03f))
    for (i in 0..1) {
        val y = b.shoulderY + u * (0.27f + i * 0.1f)
        drawLine(Leather.lighter(0.2f), Offset(c.x - u * 0.06f, y), Offset(c.x + u * 0.06f, y + u * 0.05f), u * 0.025f, StrokeCap.Round)
        drawLine(Leather.lighter(0.2f), Offset(c.x + u * 0.06f, y), Offset(c.x - u * 0.06f, y + u * 0.05f), u * 0.025f, StrokeCap.Round)
    }
    drawLine(tunic.darker(0.35f), Offset(c.x, b.shoulderY + u * 0.26f), Offset(c.x, waistY), u * 0.02f)
    // Umhangschließe an den Schultern
    for (side in listOf(-1f, 1f)) drawCircle(cloak, u * 0.12f, Offset(c.x + side * u * 0.3f, b.shoulderY + u * 0.02f))
    drawCircle(HeroGold, u * 0.06f, Offset(c.x + dir * u * 0.28f, b.shoulderY + u * 0.02f))
    // Gürtel mit Tasche
    drawLine(Leather, Offset(c.x - u * 0.25f, waistY), Offset(c.x + u * 0.25f, waistY), u * 0.09f)
    drawRoundRect(Leather.lighter(0.15f), Offset(c.x - dir * u * 0.2f - u * 0.09f, waistY), Size(u * 0.18f, u * 0.17f), CornerRadius(u * 0.04f))
    drawCircle(HeroGold, u * 0.035f, Offset(c.x, waistY))

    // Gesicht, Haare und Kapuzenrand
    femaleFace(b, brows = hair.darker(0.45f), freckles = true)
    // Haare als eine Form: Kappe über dem Kopf mit seitlich geschwungenem Pony
    val hairShape = Path().apply {
        moveTo(h.x - dir * hr * 0.98f, h.y + hr * 0.05f)
        cubicTo(h.x - dir * hr * 1.08f, h.y - hr * 1.4f, h.x + dir * hr * 1.08f, h.y - hr * 1.4f, h.x + dir * hr * 0.98f, h.y - hr * 0.12f)
        quadraticTo(h.x + dir * hr * 0.6f, h.y - hr * 0.62f, h.x + dir * hr * 0.12f, h.y - hr * 0.58f)
        quadraticTo(h.x - dir * hr * 0.55f, h.y - hr * 0.5f, h.x - dir * hr * 0.82f, h.y - hr * 0.05f)
        close()
    }
    drawPath(hairShape, hair)
    drawPath(Path().apply {
        moveTo(h.x + dir * hr * 0.12f, h.y - hr * 0.6f)
        quadraticTo(h.x - dir * hr * 0.4f, h.y - hr * 0.62f, h.x - dir * hr * 0.7f, h.y - hr * 0.25f)
    }, hair.darker(0.15f), style = Stroke(hr * 0.06f, cap = StrokeCap.Round))
    drawArc(hair.lighter(0.35f), 215f, 45f, false, h + Offset(-hr * 0.75f, -hr * 0.98f), Size(hr * 1.5f, hr * 0.9f), style = Stroke(hr * 0.09f, cap = StrokeCap.Round))
    // Kapuzenrand um das Gesicht
    drawArc(cloak.lighter(0.12f), 175f, 190f, false, h + Offset(-hr * 1.12f, -hr * 1.18f), Size(hr * 2.24f, hr * 2.0f), style = Stroke(hr * 0.26f, cap = StrokeCap.Round))
    // Zopf über die vordere Schulter
    var p = Offset(h.x + dir * hr * 0.8f, h.y + hr * 0.75f)
    for (i in 0..4) {
        drawOval(hair.darker(0.1f * (i % 2)), p - Offset(u * 0.09f, u * 0.08f), Size(u * 0.18f, u * 0.16f))
        p += Offset(dir * u * 0.015f, u * 0.11f)
    }
    drawLine(Color(0xFF16A34A), p - Offset(u * 0.07f, u * 0.06f), p + Offset(u * 0.07f, -u * 0.06f), u * 0.05f, StrokeCap.Round)
    drawOval(hair, p - Offset(u * 0.06f, u * 0.03f), Size(u * 0.12f, u * 0.14f))

    // Langbogen mit geschwungenen Enden und Sehne
    val hand = b.hand
    val grip = hand + Offset(-dir * u * 0.08f, 0f)
    val top = grip + Offset(-dir * u * 0.05f, -u * 0.85f)
    val bottom = grip + Offset(-dir * u * 0.05f, u * 0.85f)
    drawLine(Color(0xFFE7E5E4).copy(alpha = 0.9f), top + Offset(dir * u * 0.04f, 0f), bottom + Offset(dir * u * 0.04f, 0f), u * 0.02f)
    val bow = Path().apply {
        moveTo(top.x - dir * u * 0.06f, top.y - u * 0.06f)
        quadraticTo(top.x + dir * u * 0.02f, top.y + u * 0.02f, top.x + dir * u * 0.06f, top.y + u * 0.06f)
        quadraticTo(grip.x + dir * u * 0.38f, grip.y - u * 0.45f, grip.x + dir * u * 0.12f, grip.y)
        quadraticTo(grip.x + dir * u * 0.38f, grip.y + u * 0.45f, bottom.x + dir * u * 0.06f, bottom.y - u * 0.06f)
        quadraticTo(bottom.x + dir * u * 0.02f, bottom.y - u * 0.02f, bottom.x - dir * u * 0.06f, bottom.y + u * 0.06f)
    }
    drawPath(bow, Color(0xFF7C4A21), style = Stroke(u * 0.09f, cap = StrokeCap.Round))
    drawPath(bow, Color(0xFFB07A45), style = Stroke(u * 0.03f, cap = StrokeCap.Round))
    drawLine(Color(0xFF3F2A1D), grip + Offset(dir * u * 0.12f, -u * 0.1f), grip + Offset(dir * u * 0.12f, u * 0.1f), u * 0.11f)
    drawCircle(Skin.darker(0.08f), u * 0.11f, hand)
    drawCircle(Skin.darker(0.08f), u * 0.11f, b.offHand)
}

// --- Nekromant ---

/** Kleiner Totenschädel mit Augenhöhlen und Zähnen. */
private fun DrawScope.skull(c: Offset, r: Float, bone: Color = Color(0xFFE7E5E4), eyes: Color = Color(0xFF1C1917)) {
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

private fun DrawScope.drawNecromancer(b: HeroBody, time: Float, bodyOnly: Boolean) {
    val u = b.u
    val c = b.c
    val dir = b.dir
    val h = b.head
    val hr = b.headR
    val robe = Color(0xFF1F1B2E)
    val violet = Color(0xFF7C3AED)
    val soul = Color(0xFFA78BFA)
    val bone = Color(0xFFE7E5E4)
    val pulse = 0.6f + 0.4f * sin(time * 4f)

    if (!bodyOnly) drawCircle(soul.copy(alpha = 0.1f + 0.05f * sin(time * 3f)), u * 1.15f, c + Offset(0f, -u * 0.2f))


    // Ärmel mit zerfetzten Enden und knochigen Händen
    for (hand in listOf(b.hand, b.offHand)) {
        heroArm(b, hand, robe.lighter(0.06f), 0.24f)
        val s = if (hand.x > c.x) 1f else -1f
        drawPath(Path().apply {
            moveTo(hand.x - s * u * 0.2f, hand.y - u * 0.18f)
            lineTo(hand.x + s * u * 0.08f, hand.y - u * 0.16f)
            lineTo(hand.x + s * u * 0.1f, hand.y + u * 0.02f)
            lineTo(hand.x, hand.y - u * 0.04f)
            lineTo(hand.x - s * u * 0.08f, hand.y + u * 0.06f)
            lineTo(hand.x - s * u * 0.2f, hand.y - u * 0.02f)
            close()
        }, robe.lighter(0.12f))
    }

    // Lange Robe mit zerrissenem Saum und violetter Bordüre
    val hemY = b.footY - u * 0.04f
    val body = Path().apply {
        moveTo(c.x - u * 0.38f, b.shoulderY - u * 0.05f)
        lineTo(c.x + u * 0.38f, b.shoulderY - u * 0.05f)
        lineTo(c.x + u * 0.62f, hemY - u * 0.08f)
        lineTo(c.x + u * 0.48f, hemY)
        lineTo(c.x + u * 0.36f, hemY - u * 0.1f)
        lineTo(c.x + u * 0.2f, hemY + u * 0.02f)
        lineTo(c.x + u * 0.05f, hemY - u * 0.08f)
        lineTo(c.x - u * 0.1f, hemY + u * 0.02f)
        lineTo(c.x - u * 0.26f, hemY - u * 0.1f)
        lineTo(c.x - u * 0.42f, hemY)
        lineTo(c.x - u * 0.62f, hemY - u * 0.08f)
        close()
    }
    drawPath(body, robe)
    clipPath(body) { drawRect(Color.Black.copy(alpha = 0.25f), Offset(c.x + u * 0.22f, b.shoulderY - u * 0.1f), Size(u * 0.5f, u * 1.6f)) }
    drawLine(violet, Offset(c.x - u * 0.06f, b.shoulderY + u * 0.05f), Offset(c.x - u * 0.12f, hemY - u * 0.05f), u * 0.05f)
    drawLine(violet, Offset(c.x + u * 0.06f, b.shoulderY + u * 0.05f), Offset(c.x + u * 0.12f, hemY - u * 0.05f), u * 0.05f)
    // Runen auf der Vorderbahn
    for (i in 0..2) {
        val y = b.hipY + u * (0.12f + i * 0.17f)
        drawCircle(soul.copy(alpha = if (bodyOnly) 1f else 0.5f + 0.4f * pulse), u * 0.035f, Offset(c.x, y))
    }
    // Gürtel mit Schädelschnalle
    drawLine(Color(0xFF3B2F4A), Offset(c.x - u * 0.42f, b.hipY - u * 0.05f), Offset(c.x + u * 0.42f, b.hipY - u * 0.05f), u * 0.09f)
    skull(Offset(c.x, b.hipY - u * 0.07f), u * 0.09f)
    // Knochenkette
    for (i in -3..3) {
        val p = Offset(c.x + i * u * 0.08f, b.shoulderY + u * 0.1f + (9 - i * i) * u * 0.012f)
        drawOval(bone, p - Offset(u * 0.03f, u * 0.045f), Size(u * 0.06f, u * 0.09f))
    }
    // Schädel-Schulterstück auf der Waffenseite
    val shoulderSkull = Offset(c.x + dir * u * 0.38f, b.shoulderY + u * 0.02f)
    drawOval(Color(0xFF3B2F4A), shoulderSkull - Offset(u * 0.22f, u * 0.12f), Size(u * 0.44f, u * 0.28f))
    skull(shoulderSkull + Offset(0f, -u * 0.06f), u * 0.13f)

    // Bleiches Gesicht im Kapuzenschatten mit glühenden Augen
    drawRoundRect(Color(0xFFC9D1C8), Offset(h.x - hr * 0.2f, h.y + hr * 0.6f), Size(hr * 0.4f, b.shoulderY - h.y - hr * 0.4f))
    drawCircle(Color(0xFFD6DED4), hr, h)
    for (side in listOf(-1f, 1f)) {
        val e = Offset(h.x + side * hr * 0.36f + dir * hr * 0.06f, h.y + hr * 0.08f)
        drawOval(Color(0xFF6B6375), e - Offset(hr * 0.22f, hr * 0.17f), Size(hr * 0.44f, hr * 0.36f))
        if (!bodyOnly) drawCircle(soul.copy(alpha = 0.45f * pulse), hr * 0.2f, e)
        drawCircle(Color(0xFFC4B5FD), hr * 0.1f, e + Offset(dir * hr * 0.03f, 0f))
    }
    // Hohle Wangen und schmaler Mund
    for (side in listOf(-1f, 1f)) drawLine(Color(0xFF9CA59A), Offset(h.x + side * hr * 0.5f, h.y + hr * 0.35f), Offset(h.x + side * hr * 0.38f, h.y + hr * 0.62f), hr * 0.05f, StrokeCap.Round)
    drawLine(Color(0xFF4B4453), Offset(h.x - hr * 0.16f + dir * hr * 0.06f, h.y + hr * 0.58f), Offset(h.x + hr * 0.16f + dir * hr * 0.06f, h.y + hr * 0.55f), hr * 0.06f, StrokeCap.Round)
    // Kurzes weißes Haar mit Spitzenansatz, seitlich bis zu den Ohren
    val whiteHair = Color(0xFFF1F5F9)
    val strand = Color(0xFF94A3B8)
    drawPath(Path().apply {
        moveTo(h.x - hr * 1.04f, h.y + hr * 0.32f)
        cubicTo(h.x - hr * 1.2f, h.y - hr * 1.4f, h.x + hr * 1.2f, h.y - hr * 1.4f, h.x + hr * 1.04f, h.y + hr * 0.32f)
        lineTo(h.x + hr * 0.84f, h.y + hr * 0.22f)
        quadraticTo(h.x + hr * 0.85f, h.y - hr * 0.5f, h.x + hr * 0.35f, h.y - hr * 0.6f)
        lineTo(h.x, h.y - hr * 0.38f)
        lineTo(h.x - hr * 0.35f, h.y - hr * 0.6f)
        quadraticTo(h.x - hr * 0.85f, h.y - hr * 0.5f, h.x - hr * 0.84f, h.y + hr * 0.22f)
        close()
    }, whiteHair)
    for (side in listOf(-1f, 1f)) {
        drawLine(strand, Offset(h.x + side * hr * 0.92f, h.y - hr * 0.3f), Offset(h.x + side * hr * 0.95f, h.y + hr * 0.2f), hr * 0.05f, StrokeCap.Round)
        drawLine(strand, Offset(h.x + side * hr * 0.3f, h.y - hr * 0.55f), Offset(h.x + side * hr * 0.75f, h.y - hr * 0.35f), hr * 0.04f, StrokeCap.Round)
    }
    // Knochenkrone: Zacken aus Rippen und Knochen, Reif mit Wirbeln, violetter Seelenstein
    val crownY = h.y - hr * 0.72f
    for (i in -2..2) {
        val x = h.x + i * hr * 0.36f
        val height = hr * (0.95f - kotlin.math.abs(i) * 0.22f)
        val tipX = x + i * hr * 0.06f
        drawPath(Path().apply {
            moveTo(x - hr * 0.13f, crownY)
            quadraticTo(x - hr * 0.08f, crownY - height * 0.6f, tipX, crownY - height)
            quadraticTo(x + hr * 0.08f, crownY - height * 0.6f, x + hr * 0.13f, crownY)
            close()
        }, bone)
        drawLine(bone.darker(0.2f), Offset(x + hr * 0.04f, crownY - hr * 0.05f), Offset(tipX + hr * 0.02f, crownY - height * 0.8f), hr * 0.04f, StrokeCap.Round)
        drawCircle(bone, hr * 0.07f, Offset(tipX, crownY - height))
    }
    drawRoundRect(bone.darker(0.08f), Offset(h.x - hr * 0.98f, crownY - hr * 0.08f), Size(hr * 1.96f, hr * 0.26f), CornerRadius(hr * 0.12f))
    for (i in -4..4) drawLine(bone.darker(0.3f), Offset(h.x + i * hr * 0.2f, crownY - hr * 0.06f), Offset(h.x + i * hr * 0.2f, crownY + hr * 0.16f), hr * 0.035f)
    if (!bodyOnly) drawCircle(soul.copy(alpha = 0.45f * pulse), hr * 0.28f, Offset(h.x, crownY + hr * 0.05f))
    drawPath(Path().apply {
        moveTo(h.x, crownY - hr * 0.16f)
        lineTo(h.x + hr * 0.12f, crownY + hr * 0.05f)
        lineTo(h.x, crownY + hr * 0.26f)
        lineTo(h.x - hr * 0.12f, crownY + hr * 0.05f)
        close()
    }, violet)
    drawCircle(Color(0xFFDDD6FE), hr * 0.04f, Offset(h.x - hr * 0.03f, crownY - hr * 0.01f))

    // Knochenstab mit Schädel und Seelenflamme
    val hand = b.hand
    val top = hand + Offset(dir * u * 0.04f, -u * 1.2f)
    drawLine(Color(0xFF44403C), hand + Offset(-dir * u * 0.02f, u * 0.78f), top, u * 0.08f, StrokeCap.Round)
    for (i in 0..3) {
        val y = hand.y + u * (0.6f - i * 0.45f)
        drawLine(bone.darker(0.15f), Offset(hand.x - u * 0.06f, y), Offset(hand.x + u * 0.06f, y - u * 0.04f), u * 0.03f, StrokeCap.Round)
    }
    if (!bodyOnly) {
        val flicker = sin(time * 12f) * u * 0.04f
        drawCircle(soul.copy(alpha = 0.3f * pulse), u * 0.34f, top + Offset(0f, -u * 0.1f))
        drawPath(Path().apply {
            moveTo(top.x - u * 0.13f, top.y - u * 0.05f)
            quadraticTo(top.x - u * 0.1f, top.y - u * 0.3f, top.x + flicker, top.y - u * 0.45f)
            quadraticTo(top.x + u * 0.1f, top.y - u * 0.3f, top.x + u * 0.13f, top.y - u * 0.05f)
            close()
        }, soul.copy(alpha = 0.85f))
    }
    skull(top + Offset(0f, -u * 0.04f), u * 0.15f, eyes = if (bodyOnly) Color(0xFF1C1917) else Color(0xFF7C3AED))
    // Knochige Hände
    drawCircle(Color(0xFFC9D1C8), u * 0.1f, hand)
    drawCircle(Color(0xFFC9D1C8), u * 0.1f, b.offHand)
    if (!bodyOnly) {
        // Schwebende Seele über der freien Hand
        val wisp = b.offHand + Offset(sin(time * 3f) * u * 0.05f, -u * 0.32f)
        drawCircle(soul.copy(alpha = 0.3f), u * 0.18f, wisp)
        drawCircle(Color(0xFFDDD6FE), u * 0.09f, wisp)
        drawLine(Color(0xFFDDD6FE).copy(alpha = 0.6f), wisp, wisp + Offset(-dir * u * 0.12f, u * 0.14f), u * 0.05f, StrokeCap.Round)
    }
}

// --- Kryomantin ---

/** Eiskristall als Raute mit Lichtkante. */
private fun DrawScope.iceShard(c: Offset, w: Float, h: Float, color: Color) {
    val path = Path().apply {
        moveTo(c.x, c.y - h)
        lineTo(c.x + w, c.y)
        lineTo(c.x, c.y + h * 0.6f)
        lineTo(c.x - w, c.y)
        close()
    }
    drawPath(path, color)
    drawPath(Path().apply {
        moveTo(c.x, c.y - h)
        lineTo(c.x - w, c.y)
        lineTo(c.x, c.y + h * 0.6f)
        close()
    }, Color.White.copy(alpha = 0.45f))
}

private fun DrawScope.drawCryomancer(b: HeroBody, time: Float, bodyOnly: Boolean) {
    val u = b.u
    val c = b.c
    val dir = b.dir
    val h = b.head
    val hr = b.headR
    val ice = Color(0xFF0EA5E9)
    val frost = Color(0xFFE0F2FE)
    val crystal = Color(0xFFBAE6FD)
    val hair = Color(0xFFDBEAFE)
    val pulse = 0.7f + 0.3f * sin(time * 6f)

    if (!bodyOnly) drawCircle(frost.copy(alpha = 0.1f + 0.05f * sin(time * 2.5f)), u * 1.15f, c + Offset(0f, -u * 0.2f))

    // Haare hinten bis zu den Schultern
    drawRoundRect(hair.darker(0.12f), Offset(h.x - hr * 1.1f, h.y - hr * 0.4f), Size(hr * 2.2f, b.shoulderY - h.y + u * 0.22f), CornerRadius(hr * 0.8f))

    // Schuhe unter dem Saum
    for (side in listOf(-1f, 1f)) {
        val lift = if (side > 0) b.step else -b.step
        val foot = Offset(c.x + side * u * 0.2f + dir * u * 0.04f, b.footY - lift.coerceAtLeast(0f) - u * 0.06f)
        drawOval(Color(0xFF0369A1), foot - Offset(u * 0.15f, u * 0.08f), Size(u * 0.3f, u * 0.15f))
    }

    // Ärmel mit Eiskante
    for (hand in listOf(b.hand, b.offHand)) {
        heroArm(b, hand, ice.darker(0.1f), 0.18f)
        val s = if (hand.x > c.x) 1f else -1f
        val arm = hand - Offset(c.x + s * u * 0.4f, b.shoulderY + u * 0.1f)
        drawLine(frost, hand - arm * 0.22f + Offset(0f, -u * 0.06f), hand - arm * 0.22f + Offset(0f, u * 0.06f), u * 0.06f, StrokeCap.Round)
    }

    // Rock mit Eiszapfen-Saum
    val waistY = b.hipY - u * 0.06f
    val hemY = b.footY - u * 0.12f
    val skirt = Path().apply {
        moveTo(c.x - u * 0.22f, waistY)
        lineTo(c.x + u * 0.22f, waistY)
        cubicTo(c.x + u * 0.32f, b.hipY + u * 0.12f, c.x + u * 0.55f, b.hipY + u * 0.3f, c.x + u * 0.66f, hemY)
        val teeth = 6
        for (i in 0 until teeth) {
            val x0 = c.x + u * 0.66f - i * (u * 1.32f / teeth)
            lineTo(x0 - u * 0.11f, hemY + u * (if (i % 2 == 0) 0.12f else 0.07f))
            lineTo(x0 - u * 0.22f, hemY)
        }
        cubicTo(c.x - u * 0.55f, b.hipY + u * 0.3f, c.x - u * 0.32f, b.hipY + u * 0.12f, c.x - u * 0.22f, waistY)
        close()
    }
    drawPath(skirt, ice)
    clipPath(skirt) {
        drawRect(ice.darker(0.2f), Offset(c.x + u * 0.2f, waistY), Size(u * 0.6f, u))
        // Frostranken am Saum
        for (i in -2..2) {
            val x = c.x + i * u * 0.24f
            drawLine(frost.copy(alpha = 0.7f), Offset(x, hemY + u * 0.05f), Offset(x + u * 0.06f, hemY - u * 0.18f), u * 0.03f, StrokeCap.Round)
            drawLine(frost.copy(alpha = 0.7f), Offset(x + u * 0.03f, hemY - u * 0.07f), Offset(x - u * 0.06f, hemY - u * 0.14f), u * 0.025f, StrokeCap.Round)
        }
    }
    // Weiße Mittelbahn
    drawPath(Path().apply {
        moveTo(c.x - u * 0.06f, waistY)
        lineTo(c.x + u * 0.06f, waistY)
        lineTo(c.x + u * 0.16f, hemY)
        lineTo(c.x - u * 0.16f, hemY)
        close()
    }, frost.copy(alpha = 0.85f))

    // Mieder mit V-Ausschnitt
    val bodice = femaleTorso(b, waistY, waistY + u * 0.02f, hip = 0.22f)
    drawPath(bodice, ice)
    clipPath(bodice) { drawRect(ice.darker(0.2f), Offset(c.x + u * 0.2f, b.shoulderY - u * 0.1f), Size(u * 0.4f, u)) }
    val neck = Path().apply {
        moveTo(c.x - u * 0.19f, b.shoulderY - u * 0.04f)
        lineTo(c.x + u * 0.19f, b.shoulderY - u * 0.04f)
        lineTo(c.x, b.shoulderY + u * 0.27f)
        close()
    }
    drawPath(neck, Skin.darker(0.06f))
    drawPath(neck, frost, style = Stroke(u * 0.035f))
    // Gürtel mit Eiskristall
    drawLine(frost, Offset(c.x - u * 0.23f, waistY), Offset(c.x + u * 0.23f, waistY), u * 0.08f, StrokeCap.Round)
    iceShard(Offset(c.x, waistY), u * 0.07f, u * 0.11f, crystal)
    // Pelzkragen an den Schultern
    for (side in listOf(-1f, 1f)) for (i in 0..2) {
        drawCircle(Color.White, u * (0.11f - i * 0.015f), Offset(c.x + side * u * (0.24f + i * 0.08f), b.shoulderY + u * (0.0f + i * 0.04f)))
    }
    for (side in listOf(-1f, 1f)) drawCircle(Color(0xFFE2E8F0), u * 0.05f, Offset(c.x + side * u * 0.3f, b.shoulderY + u * 0.06f))

    // Gesicht, Haare mit Mittelscheitel und Eisdiadem
    femaleFace(b, brows = Color(0xFF93C5FD), pupil = Color(0xFF0369A1))
    drawPath(Path().apply {
        moveTo(h.x - hr * 1.06f, h.y + hr * 0.5f)
        cubicTo(h.x - hr * 1.2f, h.y - hr * 1.4f, h.x + hr * 1.2f, h.y - hr * 1.4f, h.x + hr * 1.06f, h.y + hr * 0.5f)
        lineTo(h.x + hr * 0.82f, h.y + hr * 0.42f)
        quadraticTo(h.x + hr * 0.85f, h.y - hr * 0.45f, h.x + hr * 0.05f, h.y - hr * 0.62f)
        lineTo(h.x - hr * 0.05f, h.y - hr * 0.62f)
        quadraticTo(h.x - hr * 0.85f, h.y - hr * 0.45f, h.x - hr * 0.82f, h.y + hr * 0.42f)
        close()
    }, hair)
    drawLine(hair.darker(0.2f), Offset(h.x, h.y - hr * 0.62f), Offset(h.x, h.y - hr * 0.95f), hr * 0.05f, StrokeCap.Round)
    for (side in listOf(-1f, 1f)) {
        drawArc(Color.White, if (side < 0) 200f else 290f, 50f, false, Offset(h.x + side * hr * 0.4f - hr * 0.5f, h.y - hr * 0.85f), Size(hr * 1.0f, hr * 1.0f), style = Stroke(hr * 0.07f, cap = StrokeCap.Round))
    }
    val tiaraY = h.y - hr * 0.78f
    drawArc(Color(0xFFE2E8F0), 200f, 140f, false, Offset(h.x - hr * 0.95f, tiaraY - hr * 0.2f), Size(hr * 1.9f, hr * 0.8f), style = Stroke(hr * 0.09f))
    iceShard(Offset(h.x, tiaraY - hr * 0.22f), hr * 0.16f, hr * 0.45f, crystal)
    iceShard(Offset(h.x - hr * 0.42f, tiaraY - hr * 0.1f), hr * 0.1f, hr * 0.26f, crystal)
    iceShard(Offset(h.x + hr * 0.42f, tiaraY - hr * 0.1f), hr * 0.1f, hr * 0.26f, crystal)

    // Kristallstab mit großem Eiskristall
    val hand = b.hand
    val top = hand + Offset(dir * u * 0.04f, -u * 1.25f)
    drawLine(Color(0xFFCBD5E1), hand + Offset(-dir * u * 0.02f, u * 0.78f), top, u * 0.08f, StrokeCap.Round)
    drawLine(Color.White, hand + Offset(-dir * u * 0.04f, u * 0.7f), top + Offset(-u * 0.02f, 0f), u * 0.025f, StrokeCap.Round)
    for (side in listOf(-1f, 1f)) {
        drawLine(Color(0xFFCBD5E1), top + Offset(0f, u * 0.04f), top + Offset(side * u * 0.16f, -u * 0.14f), u * 0.05f, StrokeCap.Round)
    }
    val k = u * (0.2f + 0.02f * sin(time * 6f))
    if (!bodyOnly) drawCircle(Color(0xFF7DD3FC).copy(alpha = 0.35f * pulse), k * 1.8f, top + Offset(0f, -u * 0.2f))
    iceShard(top + Offset(-u * 0.13f, -u * 0.12f), k * 0.3f, k * 0.7f, crystal)
    iceShard(top + Offset(u * 0.13f, -u * 0.12f), k * 0.3f, k * 0.7f, crystal)
    iceShard(top + Offset(0f, -u * 0.18f), k * 0.5f, k * 1.3f, frost)
    drawCircle(Skin.darker(0.08f), u * 0.1f, hand)
    drawCircle(Skin.darker(0.08f), u * 0.1f, b.offHand)

    if (!bodyOnly) {
        // Schwebende Schneeflocke über der freien Hand
        val f = b.offHand + Offset(0f, -u * 0.32f)
        val spin = time * 1.5f
        drawCircle(Color(0xFF7DD3FC).copy(alpha = 0.3f), u * 0.18f, f)
        for (i in 0 until 3) {
            val a = spin + i * (Math.PI.toFloat() / 3f)
            val d = Offset(kotlin.math.cos(a), sin(a)) * (u * 0.14f)
            drawLine(Color.White, f - d, f + d, u * 0.035f, StrokeCap.Round)
        }
        drawCircle(Color.White, u * 0.035f, f)
    }
}

// --- Paladin ---

private fun DrawScope.drawPaladin(b: HeroBody, time: Float, bodyOnly: Boolean) {
    val u = b.u
    val c = b.c
    val dir = b.dir
    val h = b.head
    val hr = b.headR
    val steel = Color(0xFFE2E8F0)
    val steelDark = Color(0xFF94A3B8)
    val gold = HeroGold
    val goldDark = Color(0xFFCA8A04)
    val white = Color(0xFFF8FAFC)

    // Weißer Umhang mit Goldsaum
    val sway = sin(time * 3f) * u * 0.06f
    val cape = Path().apply {
        moveTo(c.x - u * 0.42f, b.shoulderY)
        lineTo(c.x + u * 0.42f, b.shoulderY)
        lineTo(c.x + u * 0.72f - dir * sway, b.footY - u * 0.1f)
        quadraticTo(c.x, b.footY + u * 0.02f, c.x - u * 0.72f - dir * sway, b.footY - u * 0.1f)
        close()
    }
    drawPath(cape, Color(0xFFE5E7EB))
    clipPath(cape) {
        drawRect(Color(0xFFCBD5E1), Offset(c.x + u * 0.3f, b.shoulderY), Size(u, u * 2f))
        drawPath(cape, gold, style = Stroke(u * 0.1f))
    }

    heroLegs(b, steelDark, Color(0xFF64748B), knee = gold)

    // Arme in Plattenrüstung
    heroArm(b, b.hand, steelDark, 0.22f)
    heroArm(b, b.offHand, steelDark, 0.22f)

    // Brustpanzer
    val top = Offset(c.x - u * 0.46f, b.shoulderY - u * 0.06f)
    val torso = Size(u * 0.92f, b.hipY - b.shoulderY + u * 0.12f)
    drawRoundRect(steel, top, torso, CornerRadius(u * 0.24f))
    drawRoundRect(steel.darker(0.12f), top + Offset(torso.width * 0.55f, 0f), Size(torso.width * 0.45f, torso.height), CornerRadius(u * 0.24f))
    drawRoundRect(Color.White, top + Offset(u * 0.1f, u * 0.08f), Size(u * 0.14f, torso.height * 0.45f), CornerRadius(u * 0.07f))
    // Weißer Wappenrock mit goldenem Kreuz, unten in zwei Bahnen
    val tabard = Path().apply {
        moveTo(c.x - u * 0.24f, b.shoulderY + u * 0.08f)
        lineTo(c.x + u * 0.24f, b.shoulderY + u * 0.08f)
        lineTo(c.x + u * 0.26f, b.hipY + u * 0.42f)
        lineTo(c.x, b.hipY + u * 0.32f)
        lineTo(c.x - u * 0.26f, b.hipY + u * 0.42f)
        close()
    }
    drawPath(tabard, white)
    drawPath(tabard, gold, style = Stroke(u * 0.035f))
    drawLine(gold, Offset(c.x, b.shoulderY + u * 0.15f), Offset(c.x, b.hipY - u * 0.08f), u * 0.1f)
    drawLine(gold, Offset(c.x - u * 0.15f, b.shoulderY + u * 0.3f), Offset(c.x + u * 0.15f, b.shoulderY + u * 0.3f), u * 0.1f)
    // Goldgürtel
    drawLine(goldDark, Offset(c.x - u * 0.46f, b.hipY), Offset(c.x + u * 0.46f, b.hipY), u * 0.1f)
    drawCircle(gold, u * 0.07f, Offset(c.x, b.hipY))
    // Schulterplatten mit Goldrand
    for (side in listOf(-1f, 1f)) {
        val p = Offset(c.x + side * u * 0.44f, b.shoulderY + u * 0.04f)
        drawOval(steel, p - Offset(u * 0.24f, u * 0.16f), Size(u * 0.48f, u * 0.32f))
        drawArc(gold, 0f, 180f, false, p - Offset(u * 0.22f, u * 0.13f), Size(u * 0.44f, u * 0.27f), style = Stroke(u * 0.05f))
        drawCircle(Color.White, u * 0.05f, p + Offset(-u * 0.07f, -u * 0.05f))
    }

    // Runder Sonnenschild
    val sc = b.offHand + Offset(-dir * u * 0.08f, u * 0.02f)
    drawCircle(goldDark, u * 0.4f, sc)
    drawCircle(white, u * 0.33f, sc)
    for (i in 0 until 8) {
        val a = i * (Math.PI.toFloat() / 4f)
        val d = Offset(kotlin.math.cos(a), sin(a))
        drawLine(gold, sc + d * (u * 0.12f), sc + d * (u * 0.27f), u * 0.05f, StrokeCap.Round)
    }
    drawCircle(gold, u * 0.11f, sc)
    drawCircle(Color(0xFFFEF3C7), u * 0.05f, sc + Offset(-u * 0.02f, -u * 0.02f))

    // Heiligenschein
    if (!bodyOnly) {
        val glow = 0.55f + 0.2f * sin(time * 3f)
        drawOval(gold.copy(alpha = glow), Offset(h.x - hr * 0.9f, h.y - hr * 1.62f), Size(hr * 1.8f, hr * 0.5f), style = Stroke(hr * 0.14f))
    }
    // Topfhelm mit kleinen Flügeln und goldenem Kreuz-Visier
    for (side in listOf(-1f, 1f)) {
        val base = Offset(h.x + side * hr * 0.95f, h.y - hr * 0.35f)
        val wing = Path().apply {
            moveTo(base.x, base.y + hr * 0.2f)
            quadraticTo(base.x + side * hr * 0.4f, base.y - hr * 0.1f, base.x + side * hr * 0.55f, base.y - hr * 0.85f)
            quadraticTo(base.x + side * hr * 0.2f, base.y - hr * 0.55f, base.x, base.y - hr * 0.3f)
            close()
        }
        drawPath(wing, white)
        for (k in 0..1) drawLine(Color(0xFFCBD5E1), base + Offset(side * hr * 0.08f, -hr * (0.05f + k * 0.18f)), base + Offset(side * hr * (0.35f + k * 0.07f), -hr * (0.35f + k * 0.2f)), hr * 0.04f, StrokeCap.Round)
    }
    val helmTl = h + Offset(-hr * 1.0f, -hr * 1.1f)
    val helm = Size(hr * 2.0f, hr * 2.05f)
    drawRoundRect(steel, helmTl, helm, CornerRadius(hr * 0.9f, hr * 0.7f))
    drawArc(Color.White, 195f, 55f, false, h + Offset(-hr * 0.8f, -hr * 0.95f), Size(hr * 1.6f, hr * 1.5f), style = Stroke(hr * 0.13f, cap = StrokeCap.Round))
    drawLine(gold, Offset(h.x - hr * 0.75f, h.y + hr * 0.88f), Offset(h.x + hr * 0.75f, h.y + hr * 0.88f), hr * 0.14f, StrokeCap.Round)
    // Kreuz als Sehschlitz mit Goldfassung
    val vx = h.x + dir * hr * 0.06f
    drawLine(gold, Offset(vx - hr * 0.68f, h.y + hr * 0.02f), Offset(vx + hr * 0.68f, h.y + hr * 0.02f), hr * 0.34f, StrokeCap.Round)
    drawLine(gold, Offset(vx, h.y - hr * 0.6f), Offset(vx, h.y + hr * 0.7f), hr * 0.3f, StrokeCap.Round)
    drawLine(Color(0xFF1E293B), Offset(vx - hr * 0.58f, h.y + hr * 0.02f), Offset(vx + hr * 0.58f, h.y + hr * 0.02f), hr * 0.16f, StrokeCap.Round)
    drawLine(Color(0xFF1E293B), Offset(vx, h.y - hr * 0.48f), Offset(vx, h.y + hr * 0.58f), hr * 0.12f, StrokeCap.Round)
    for (side in listOf(-1f, 1f)) drawCircle(Color(0xFFFEF3C7).copy(alpha = 0.8f), hr * 0.06f, Offset(vx + side * hr * 0.32f + dir * hr * 0.06f, h.y + hr * 0.02f))

    // Flanschenstreitkolben
    val hand = b.hand
    val head = hand + Offset(dir * u * 0.22f, -u * 0.95f)
    drawLine(Color(0xFF78350F), hand + Offset(-dir * u * 0.02f, u * 0.2f), head, u * 0.08f, StrokeCap.Round)
    drawLine(gold, hand + Offset(dir * u * 0.04f, -u * 0.2f), hand + Offset(dir * u * 0.06f, -u * 0.28f), u * 0.1f)
    val maceTl = Offset(head.x - u * 0.16f, head.y - u * 0.24f)
    drawRoundRect(steel, maceTl, Size(u * 0.32f, u * 0.38f), CornerRadius(u * 0.12f))
    drawRoundRect(steel.darker(0.15f), maceTl + Offset(u * 0.18f, 0f), Size(u * 0.14f, u * 0.38f), CornerRadius(u * 0.1f))
    for (i in -1..1) drawLine(steelDark, Offset(head.x + i * u * 0.09f, head.y - u * 0.2f), Offset(head.x + i * u * 0.09f, head.y + u * 0.1f), u * 0.035f, StrokeCap.Round)
    drawLine(gold, Offset(head.x - u * 0.16f, head.y + u * 0.13f), Offset(head.x + u * 0.16f, head.y + u * 0.13f), u * 0.07f, StrokeCap.Round)
    drawPath(Path().apply {
        moveTo(head.x - u * 0.07f, head.y - u * 0.23f)
        lineTo(head.x, head.y - u * 0.42f)
        lineTo(head.x + u * 0.07f, head.y - u * 0.23f)
        close()
    }, gold)
    drawCircle(gold, u * 0.06f, hand + Offset(-dir * u * 0.02f, u * 0.24f))
    // Panzerhandschuhe
    drawCircle(steel.darker(0.1f), u * 0.12f, hand)
    drawCircle(Color.White, u * 0.04f, hand + Offset(-u * 0.04f, -u * 0.04f))
}

// --- Assassine ---

private fun DrawScope.drawAssassin(b: HeroBody, time: Float) {
    val u = b.u
    val c = b.c
    val dir = b.dir
    val h = b.head
    val hr = b.headR
    val leather = Color(0xFF18181B)
    val hairBlack = Color(0xFF18181B)
    val crimson = Color(0xFF7F1D1D)
    val strap = Color(0xFF27272A)
    val buckle = Color(0xFF71717A)
    val flutter = sin(time * 10f) * u * 0.08f

    // Kurzes, wehendes Schalende hinter dem Körper
    val tail = Path().apply {
        moveTo(c.x - dir * u * 0.1f, b.shoulderY - u * 0.05f)
        cubicTo(c.x - dir * u * 0.45f, b.shoulderY + flutter, c.x - dir * u * 0.55f, b.shoulderY + u * 0.3f - flutter, c.x - dir * u * 0.78f, b.shoulderY + u * 0.22f + flutter)
    }
    drawPath(tail, leather, style = Stroke(u * 0.12f, cap = StrokeCap.Round))
    // Hoher Pferdeschwanz hinter dem Kopf
    val ponySway = sin(time * 6f) * hr * 0.12f
    val pony = Path().apply {
        moveTo(h.x - dir * hr * 0.62f, h.y - hr * 0.88f)
        cubicTo(h.x - dir * hr * 1.5f, h.y - hr * 1.2f, h.x - dir * hr * 1.7f, h.y + hr * 0.2f, h.x - dir * hr * 1.45f + ponySway, h.y + hr * 1.2f)
    }
    drawPath(pony, hairBlack, style = Stroke(hr * 0.45f, cap = StrokeCap.Round))
    drawPath(pony, Color(0xFF09090B), style = Stroke(hr * 0.08f, cap = StrokeCap.Round))

    // Nackte Oberschenkel, Overknee-Stiefel
    heroLegs(b, Skin.darker(0.04f), leather, legWidth = 0.17f, bootHeight = 0.36f, width = 0.86f)
    // Runde Oberschenkel, die sich zum Knie verjüngen
    for (side in listOf(-1f, 1f)) {
        val x = c.x + side * u * 0.18f
        drawPath(Path().apply {
            moveTo(x - u * 0.12f, b.hipY)
            lineTo(x + u * 0.12f, b.hipY)
            quadraticTo(x + u * 0.13f, b.hipY + u * 0.22f, x + u * 0.085f, b.footY - u * 0.36f)
            lineTo(x - u * 0.085f, b.footY - u * 0.36f)
            quadraticTo(x - u * 0.13f, b.hipY + u * 0.22f, x - u * 0.12f, b.hipY)
            close()
        }, Skin.darker(0.04f))
    }
    for (side in listOf(-1f, 1f)) {
        val x = c.x + side * u * 0.18f
        val top = b.footY - u * 0.36f
        drawLine(strap, Offset(x - u * 0.1f, top + u * 0.04f), Offset(x + u * 0.1f, top + u * 0.04f), u * 0.05f, StrokeCap.Round)
        drawCircle(buckle, u * 0.025f, Offset(x + dir * u * 0.06f, top + u * 0.04f))
    }
    // Messerholster am vorderen Oberschenkel
    val thighX = c.x + dir * u * 0.18f
    val thighY = b.hipY + u * 0.24f
    drawLine(strap, Offset(thighX - u * 0.11f, thighY), Offset(thighX + u * 0.11f, thighY), u * 0.06f)
    drawRoundRect(strap, Offset(thighX - dir * u * 0.02f - u * 0.05f, thighY - u * 0.04f), Size(u * 0.1f, u * 0.2f), CornerRadius(u * 0.03f))
    drawLine(buckle, Offset(thighX - dir * u * 0.02f, thighY - u * 0.04f), Offset(thighX - dir * u * 0.02f, thighY - u * 0.13f), u * 0.04f, StrokeCap.Round)

    // Freie Arme mit langen Armschienen
    for (hand in listOf(b.hand, b.offHand)) {
        heroArm(b, hand, Skin.darker(0.04f), 0.13f, shoulder = 0.3f)
        val s = if (hand.x > c.x) 1f else -1f
        val arm = hand - Offset(c.x + s * u * 0.3f, b.shoulderY + u * 0.1f)
        drawLine(leather, hand - arm * 0.45f, hand - arm * 0.05f, u * 0.15f, StrokeCap.Round)
        drawLine(strap.lighter(0.15f), hand - arm * 0.36f, hand - arm * 0.36f + Offset(0f, u * 0.01f), u * 0.16f)
    }

    // Schlanker Oberkörper mit knappem Ledertop (Herzausschnitt, dünne Träger)
    val waistY = b.hipY - u * 0.06f
    val body = femaleTorso(b, waistY, b.hipY + u * 0.1f, hip = 0.34f, waist = 0.18f, shoulder = 0.2f, bust = 0.29f)
    drawPath(body, Skin)
    clipPath(body) { drawRect(Skin.darker(0.05f), Offset(c.x + u * 0.22f, b.shoulderY - u * 0.1f), Size(u * 0.4f, u)) }
    val cropY = b.shoulderY + u * 0.34f
    val top = Path().apply {
        moveTo(c.x - u * 0.34f, b.shoulderY + u * 0.14f)
        quadraticTo(c.x - u * 0.3f, b.shoulderY + u * 0.02f, c.x - u * 0.17f, b.shoulderY + u * 0.04f)
        quadraticTo(c.x - u * 0.05f, b.shoulderY + u * 0.06f, c.x, b.shoulderY + u * 0.17f)
        quadraticTo(c.x + u * 0.05f, b.shoulderY + u * 0.06f, c.x + u * 0.17f, b.shoulderY + u * 0.04f)
        quadraticTo(c.x + u * 0.3f, b.shoulderY + u * 0.02f, c.x + u * 0.34f, b.shoulderY + u * 0.14f)
        lineTo(c.x + u * 0.31f, cropY)
        quadraticTo(c.x + u * 0.15f, cropY + u * 0.05f, c.x, cropY - u * 0.03f)
        quadraticTo(c.x - u * 0.15f, cropY + u * 0.05f, c.x - u * 0.31f, cropY)
        close()
    }
    clipPath(body) { drawPath(top, leather) }
    clipPath(top) { drawRect(Color.Black.copy(alpha = 0.3f), Offset(c.x + u * 0.2f, b.shoulderY - u * 0.1f), Size(u * 0.4f, u)) }
    // Wölbung der Körbchen
    for (side in listOf(-1f, 1f)) {
        drawArc(Color(0xFF3F3F46), 200f, 80f, false, Offset(c.x + side * u * 0.16f - u * 0.12f, b.shoulderY + u * 0.08f), Size(u * 0.24f, u * 0.22f), style = Stroke(u * 0.03f, cap = StrokeCap.Round))
        drawArc(Color.Black.copy(alpha = 0.5f), 20f, 140f, false, Offset(c.x + side * u * 0.16f - u * 0.14f, b.shoulderY + u * 0.12f), Size(u * 0.28f, u * 0.2f), style = Stroke(u * 0.025f, cap = StrokeCap.Round))
        // Dünne Träger zu den Schultern
        drawLine(leather, Offset(c.x + side * u * 0.2f, b.shoulderY + u * 0.06f), Offset(c.x + side * u * 0.24f, b.shoulderY - u * 0.05f), u * 0.035f, StrokeCap.Round)
    }
    drawCircle(Color(0xFF71717A), u * 0.025f, Offset(c.x, b.shoulderY + u * 0.17f))
    drawCircle(Skin.darker(0.15f), u * 0.018f, Offset(c.x, (cropY + waistY) / 2f + u * 0.03f))
    // Kurze Hose mit tiefem Gürtel und Wurfmessern
    val shorts = Path().apply {
        moveTo(c.x - u * 0.2f, waistY + u * 0.02f)
        lineTo(c.x + u * 0.2f, waistY + u * 0.02f)
        quadraticTo(c.x + u * 0.38f, b.hipY + u * 0.02f, c.x + u * 0.37f, b.hipY + u * 0.16f)
        lineTo(c.x + u * 0.06f, b.hipY + u * 0.19f)
        lineTo(c.x, b.hipY + u * 0.12f)
        lineTo(c.x - u * 0.06f, b.hipY + u * 0.19f)
        lineTo(c.x - u * 0.37f, b.hipY + u * 0.16f)
        quadraticTo(c.x - u * 0.38f, b.hipY + u * 0.02f, c.x - u * 0.2f, waistY + u * 0.02f)
        close()
    }
    drawPath(shorts, leather)
    clipPath(shorts) { drawRect(Color.Black.copy(alpha = 0.3f), Offset(c.x + u * 0.18f, waistY), Size(u * 0.4f, u * 0.4f)) }
    val beltY = waistY + u * 0.06f
    drawLine(strap.lighter(0.1f), Offset(c.x - u * 0.3f, beltY + u * 0.04f * dir), Offset(c.x + u * 0.3f, beltY - u * 0.04f * dir), u * 0.07f)
    drawRoundRect(buckle, Offset(c.x - u * 0.05f, beltY - u * 0.045f), Size(u * 0.1f, u * 0.09f), CornerRadius(u * 0.02f))
    for (i in 0..2) {
        val p = Offset(c.x - dir * u * (0.12f + i * 0.07f), beltY + u * 0.04f * dir * (0.12f + i * 0.07f) / 0.3f)
        drawLine(Color(0xFFD4D4D8), p, p + Offset(0f, -u * 0.13f), u * 0.03f, StrokeCap.Round)
    }
    // Dunkelrotes Schärpenende an der Hüfte
    drawLine(crimson, Offset(c.x + dir * u * 0.24f, beltY), Offset(c.x + dir * u * 0.3f, beltY + u * 0.28f + flutter * 0.3f), u * 0.06f, StrokeCap.Round)

    // Gesicht mit schwarzer Maske, dunkelroten Augen, Pony und Kapuze
    drawRoundRect(Skin.darker(0.06f), Offset(h.x - hr * 0.22f, h.y + hr * 0.6f), Size(hr * 0.44f, b.shoulderY - h.y - hr * 0.4f), CornerRadius(hr * 0.1f))
    // Halsband mit dunkelrotem Anhänger
    drawLine(leather, Offset(h.x - hr * 0.24f, b.shoulderY - u * 0.07f), Offset(h.x + hr * 0.24f, b.shoulderY - u * 0.07f), u * 0.05f)
    drawCircle(crimson, u * 0.03f, Offset(h.x, b.shoulderY - u * 0.03f))
    drawCircle(Skin, hr, h)
    // Dunkler Lidschatten
    for (side in listOf(-1f, 1f)) {
        drawOval(Color(0xFF3F3F46).copy(alpha = 0.55f), Offset(h.x + side * hr * 0.36f + dir * hr * 0.06f - hr * 0.24f, h.y - hr * 0.12f), Size(hr * 0.48f, hr * 0.3f))
    }
    heroEyes(b, pupil = Color(0xFFB91C1C), brows = Color(0xFF0C0A0F), browTilt = 0.05f, lashes = Color(0xFF0C0A0F))
    drawPath(Path().apply {
        moveTo(h.x - hr * 1.0f, h.y + hr * 0.36f)
        quadraticTo(h.x + dir * hr * 0.1f, h.y + hr * 0.2f, h.x + hr * 1.0f, h.y + hr * 0.36f)
        quadraticTo(h.x + hr * 0.9f, h.y + hr * 0.95f, h.x, h.y + hr * 1.05f)
        quadraticTo(h.x - hr * 0.9f, h.y + hr * 0.95f, h.x - hr * 1.0f, h.y + hr * 0.36f)
        close()
    }, leather)
    // Schwarzes, eng anliegendes Haar mit seitlichem Pony
    drawPath(Path().apply {
        moveTo(h.x - dir * hr * 1.0f, h.y + hr * 0.25f)
        cubicTo(h.x - dir * hr * 1.12f, h.y - hr * 1.48f, h.x + dir * hr * 1.12f, h.y - hr * 1.48f, h.x + dir * hr * 1.0f, h.y + hr * 0.25f)
        lineTo(h.x + dir * hr * 0.86f, h.y + hr * 0.12f)
        quadraticTo(h.x + dir * hr * 0.6f, h.y - hr * 0.55f, h.x - dir * hr * 0.1f, h.y - hr * 0.5f)
        quadraticTo(h.x - dir * hr * 0.65f, h.y - hr * 0.4f, h.x - dir * hr * 0.86f, h.y + hr * 0.12f)
        close()
    }, hairBlack)
    drawArc(Color(0xFF09090B), 205f, 50f, false, h + Offset(-hr * 0.8f, -hr * 0.98f), Size(hr * 1.6f, hr * 1.0f), style = Stroke(hr * 0.07f, cap = StrokeCap.Round))
    // Dunkelrotes Haarband am Zopfansatz
    drawCircle(crimson, hr * 0.13f, Offset(h.x - dir * hr * 0.62f, h.y - hr * 0.88f))

    // Zwei Dolche im Rückhandgriff
    for (hand in listOf(b.hand, b.offHand)) {
        val s = if (hand.x > c.x) 1f else -1f
        val tip = hand + Offset(s * u * 0.12f, u * 0.5f)
        blade(hand + Offset(s * u * 0.02f, u * 0.1f), tip, u * 0.05f, Color(0xFFD4D4D8))
        drawLine(strap.lighter(0.2f), hand + Offset(-u * 0.1f, u * 0.09f), hand + Offset(u * 0.1f, u * 0.11f), u * 0.05f, StrokeCap.Round)
        drawLine(Color(0xFF3F2A1D), hand + Offset(0f, -u * 0.18f), hand + Offset(0f, u * 0.06f), u * 0.07f, StrokeCap.Round)
        drawCircle(crimson, u * 0.035f, hand + Offset(0f, -u * 0.2f))
        drawCircle(leather.lighter(0.12f), u * 0.1f, hand)
    }
}

// --- Sturmrufer ---

/** Zackiger Blitz von [from] nach [to] mit [kinks] Knicken. */
private fun DrawScope.zigzag(from: Offset, to: Offset, kinks: Int, amp: Float, color: Color, width: Float, seed: Float) {
    val d = to - from
    val len = sqrt(d.x * d.x + d.y * d.y)
    val n = Offset(-d.y / len, d.x / len)
    var prev = from
    for (i in 1..kinks + 1) {
        val t = i / (kinks + 1f)
        val off = if (i == kinks + 1) 0f else sin(seed + i * 2.3f) * amp
        val p = from + d * t + n * off
        drawLine(color, prev, p, width, StrokeCap.Round)
        prev = p
    }
}

private fun DrawScope.drawStormcaller(b: HeroBody, time: Float, bodyOnly: Boolean) {
    val u = b.u
    val c = b.c
    val dir = b.dir
    val h = b.head
    val hr = b.headR
    val coat = Color(0xFF1E3A8A)
    val bolt = Color(0xFFFDE047)
    val cloud = Color(0xFF64748B)
    val spark = Color(0xFFBFDBFE)
    val crackle = (time * 12f).toInt().toFloat()

    if (!bodyOnly) drawCircle(Color(0xFF60A5FA).copy(alpha = 0.1f + 0.06f * sin(time * 9f)), u * 1.15f, c + Offset(0f, -u * 0.2f))

    // Langer Mantel mit Schlitz und Blitzsaum
    val hemY = b.footY - u * 0.2f
    val sway = sin(time * 3f) * u * 0.04f
    val coatPath = Path().apply {
        moveTo(c.x - u * 0.42f, b.shoulderY - u * 0.04f)
        lineTo(c.x + u * 0.42f, b.shoulderY - u * 0.04f)
        lineTo(c.x + u * 0.6f - dir * sway, hemY)
        lineTo(c.x + u * 0.08f, hemY - u * 0.04f)
        lineTo(c.x, b.hipY + u * 0.1f)
        lineTo(c.x - u * 0.08f, hemY - u * 0.04f)
        lineTo(c.x - u * 0.6f - dir * sway, hemY)
        close()
    }
    heroLegs(b, Color(0xFF334155), Color(0xFF1E293B), bootHeight = 0.32f)
    drawPath(coatPath, coat)
    clipPath(coatPath) {
        drawRect(Color.Black.copy(alpha = 0.25f), Offset(c.x + u * 0.22f, b.shoulderY - u * 0.1f), Size(u * 0.6f, u * 2f))
        // Blitz-Zickzack am Saum
        val zig = Path().apply {
            moveTo(c.x - u * 0.7f, hemY - u * 0.04f)
            for (i in 0..13) lineTo(c.x - u * 0.7f + (i + 1) * u * 0.1f, hemY - u * (if (i % 2 == 0) 0.13f else 0.04f))
        }
        drawPath(zig, bolt, style = Stroke(u * 0.04f))
    }

    // Arme im Mantel mit gelben Manschetten
    for (hand in listOf(b.hand, b.offHand)) {
        heroArm(b, hand, coat.lighter(0.06f), 0.22f)
        val s = if (hand.x > c.x) 1f else -1f
        val arm = hand - Offset(c.x + s * u * 0.4f, b.shoulderY + u * 0.1f)
        drawLine(bolt, hand - arm * 0.18f, hand - arm * 0.12f, u * 0.23f)
    }

    // Weste mit Knopfleiste und Blitzemblem
    val vest = Path().apply {
        moveTo(c.x - u * 0.3f, b.shoulderY)
        lineTo(c.x + u * 0.3f, b.shoulderY)
        lineTo(c.x + u * 0.26f, b.hipY + u * 0.08f)
        lineTo(c.x - u * 0.26f, b.hipY + u * 0.08f)
        close()
    }
    drawPath(vest, Color(0xFF1E40AF))
    drawLine(Color(0xFF172554), Offset(c.x, b.shoulderY + u * 0.08f), Offset(c.x, b.hipY + u * 0.08f), u * 0.03f)
    val emblem = Path().apply {
        val e = Offset(c.x - dir * u * 0.13f, b.shoulderY + u * 0.26f)
        moveTo(e.x + u * 0.04f, e.y - u * 0.13f)
        lineTo(e.x - u * 0.07f, e.y + u * 0.02f)
        lineTo(e.x + u * 0.0f, e.y + u * 0.02f)
        lineTo(e.x - u * 0.04f, e.y + u * 0.15f)
        lineTo(e.x + u * 0.08f, e.y - u * 0.02f)
        lineTo(e.x + u * 0.01f, e.y - u * 0.02f)
        close()
    }
    drawPath(emblem, bolt)
    // Gürtel mit runder Schnalle
    drawLine(Color(0xFF0F172A), Offset(c.x - u * 0.3f, b.hipY), Offset(c.x + u * 0.3f, b.hipY), u * 0.09f)
    drawCircle(bolt, u * 0.065f, Offset(c.x, b.hipY))
    drawCircle(Color(0xFF0F172A), u * 0.03f, Offset(c.x, b.hipY))
    // Hoher Kragen
    for (side in listOf(-1f, 1f)) {
        drawPath(Path().apply {
            moveTo(c.x + side * u * 0.08f, b.shoulderY + u * 0.05f)
            lineTo(c.x + side * u * 0.3f, b.shoulderY - u * 0.28f)
            lineTo(c.x + side * u * 0.34f, b.shoulderY + u * 0.02f)
            close()
        }, coat.darker(0.15f))
    }
    // Sturmwolken als Schulterstücke
    for (side in listOf(-1f, 1f)) {
        val p = Offset(c.x + side * u * 0.42f, b.shoulderY + u * 0.02f)
        drawCircle(cloud, u * 0.13f, p + Offset(-side * u * 0.08f, 0f))
        drawCircle(cloud, u * 0.15f, p + Offset(side * u * 0.04f, -u * 0.04f))
        drawCircle(cloud.lighter(0.15f), u * 0.1f, p + Offset(side * u * 0.0f, -u * 0.1f))
        drawCircle(cloud.darker(0.2f), u * 0.08f, p + Offset(side * u * 0.12f, u * 0.06f))
        if (!bodyOnly) zigzag(p + Offset(0f, u * 0.1f), p + Offset(side * u * 0.08f, u * 0.32f), 2, u * 0.04f, bolt, u * 0.03f, crackle + side)
    }

    // Kopf eines Sturmkriegers: kantiges Gesicht, Undercut mit blauem Irokesenkamm, Kinnbart, Blitz-Bemalung
    val stubble = Color(0xFF8B7B6B)
    val crest = Color(0xFF2563EB)
    drawRoundRect(Skin.darker(0.08f), Offset(h.x - hr * 0.3f, h.y + hr * 0.6f), Size(hr * 0.6f, b.shoulderY - h.y - hr * 0.4f), CornerRadius(hr * 0.1f))
    // Kantiger Schädel: runder Oberkopf, breiter Kiefer
    val face = Path().apply {
        moveTo(h.x - hr * 0.98f, h.y - hr * 0.1f)
        cubicTo(h.x - hr * 1.05f, h.y - hr * 1.35f, h.x + hr * 1.05f, h.y - hr * 1.35f, h.x + hr * 0.98f, h.y - hr * 0.1f)
        lineTo(h.x + hr * 0.9f, h.y + hr * 0.55f)
        lineTo(h.x + hr * 0.45f, h.y + hr * 0.98f)
        lineTo(h.x - hr * 0.45f, h.y + hr * 0.98f)
        lineTo(h.x - hr * 0.9f, h.y + hr * 0.55f)
        close()
    }
    drawPath(face, Skin.darker(0.04f))
    // Rasierte Seiten (Undercut)
    clipPath(face) {
        drawArc(stubble.copy(alpha = 0.4f), 180f, 180f, true, h + Offset(-hr * 1.05f, -hr * 1.05f), Size(hr * 2.1f, hr * 1.0f))
    }
    // Irokesenkamm aus Zacken, nach hinten geweht
    val crestPath = Path().apply {
        moveTo(h.x + dir * hr * 0.5f, h.y - hr * 0.62f)
        for (i in 0..4) {
            val x0 = h.x + dir * hr * (0.5f - i * 0.28f)
            val tipX = x0 - dir * hr * 0.3f
            val tipY = h.y - hr * (1.45f + (if (i == 1 || i == 2) 0.25f else 0.05f))
            lineTo(tipX, tipY)
            lineTo(x0 - dir * hr * 0.24f, h.y - hr * 0.95f)
        }
        lineTo(h.x - dir * hr * 0.75f, h.y - hr * 0.55f)
        quadraticTo(h.x, h.y - hr * 1.1f, h.x + dir * hr * 0.5f, h.y - hr * 0.62f)
        close()
    }
    drawPath(crestPath, crest)
    drawPath(crestPath, crest.darker(0.35f), style = Stroke(hr * 0.05f))
    drawLine(Color(0xFF93C5FD), Offset(h.x + dir * hr * 0.35f, h.y - hr * 0.85f), Offset(h.x - dir * hr * 0.35f, h.y - hr * 1.25f), hr * 0.07f, StrokeCap.Round)
    // Ohren
    for (side in listOf(-1f, 1f)) drawOval(Skin.darker(0.1f), Offset(h.x + side * hr * 0.98f - hr * 0.12f, h.y - hr * 0.1f), Size(hr * 0.24f, hr * 0.38f))
    // Augen mit tiefen, grimmigen Brauen
    for (side in listOf(-1f, 1f)) {
        val e = Offset(h.x + side * hr * 0.36f + dir * hr * 0.06f, h.y + hr * 0.05f)
        if (!bodyOnly) drawCircle(Color(0xFF93C5FD).copy(alpha = 0.45f), hr * 0.22f, e)
        drawOval(Color.White, e - Offset(hr * 0.16f, hr * 0.1f), Size(hr * 0.32f, hr * 0.2f))
        drawCircle(Color(0xFF2563EB), hr * 0.08f, e + Offset(dir * hr * 0.04f, 0f))
        drawLine(Color(0xFF1E293B), e + Offset(-hr * 0.2f, -hr * (0.2f - side * 0.06f)), e + Offset(hr * 0.2f, -hr * (0.2f + side * 0.06f)), hr * 0.1f, StrokeCap.Round)
    }
    // Blitz-Kriegsbemalung über dem vorderen Auge
    // Schmaler Blitz von der Stirn über die Wange, seitlich am vorderen Auge vorbei
    val pe = Offset(h.x + dir * hr * 0.66f, h.y + hr * 0.05f)
    drawPath(Path().apply {
        moveTo(pe.x - dir * hr * 0.05f, pe.y - hr * 0.55f)
        lineTo(pe.x - dir * hr * 0.1f, pe.y - hr * 0.05f)
        lineTo(pe.x + dir * hr * 0.03f, pe.y - hr * 0.04f)
        lineTo(pe.x - dir * hr * 0.06f, pe.y + hr * 0.42f)
        lineTo(pe.x + dir * hr * 0.1f, pe.y + hr * 0.02f)
        lineTo(pe.x - dir * hr * 0.02f, pe.y + hr * 0.01f)
        lineTo(pe.x + dir * hr * 0.03f, pe.y - hr * 0.55f)
        close()
    }, bolt.copy(alpha = 0.9f))
    // Nase, schmaler Mund und kurzer, spitzer Kinnbart
    drawLine(Skin.darker(0.2f), Offset(h.x + dir * hr * 0.1f, h.y + hr * 0.15f), Offset(h.x + dir * hr * 0.16f, h.y + hr * 0.38f), hr * 0.06f, StrokeCap.Round)
    drawLine(Color(0xFF7C2D12), Offset(h.x - hr * 0.2f + dir * hr * 0.06f, h.y + hr * 0.56f), Offset(h.x + hr * 0.2f + dir * hr * 0.06f, h.y + hr * 0.54f), hr * 0.05f, StrokeCap.Round)
    drawPath(Path().apply {
        moveTo(h.x - hr * 0.16f + dir * hr * 0.05f, h.y + hr * 0.78f)
        quadraticTo(h.x + dir * hr * 0.05f, h.y + hr * 0.72f, h.x + hr * 0.16f + dir * hr * 0.05f, h.y + hr * 0.78f)
        lineTo(h.x + dir * hr * 0.05f, h.y + hr * 1.2f)
        close()
    }, Color(0xFF6B4423))

    // Gabelstab mit Blitzkugel
    val hand = b.hand
    val top = hand + Offset(dir * u * 0.04f, -u * 1.15f)
    drawLine(Color(0xFF6B7280), hand + Offset(-dir * u * 0.02f, u * 0.78f), top, u * 0.08f, StrokeCap.Round)
    drawLine(Color(0xFFCBD5E1), hand + Offset(-dir * u * 0.04f, u * 0.7f), top, u * 0.025f, StrokeCap.Round)
    for (side in listOf(-1f, 1f)) {
        val fork = Path().apply {
            moveTo(top.x, top.y + u * 0.05f)
            quadraticTo(top.x + side * u * 0.22f, top.y - u * 0.02f, top.x + side * u * 0.16f, top.y - u * 0.3f)
        }
        drawPath(fork, Color(0xFF6B7280), style = Stroke(u * 0.06f, cap = StrokeCap.Round))
    }
    val orb = top + Offset(0f, -u * 0.2f)
    if (!bodyOnly) drawCircle(bolt.copy(alpha = 0.35f + 0.15f * sin(time * 20f)), u * 0.3f, orb)
    drawCircle(bolt, u * 0.12f, orb)
    drawCircle(Color.White, u * 0.05f, orb)
    if (!bodyOnly) {
        for (i in 0 until 3) {
            val a = crackle * 1.7f + i * 2.1f
            zigzag(orb, orb + Offset(kotlin.math.cos(a), sin(a)) * (u * 0.38f), 2, u * 0.05f, Color.White, u * 0.025f, a)
        }
        // Funken zwischen den Fingern der freien Hand
        val f = b.offHand + Offset(0f, -u * 0.18f)
        zigzag(b.offHand + Offset(-u * 0.08f, -u * 0.04f), f + Offset(u * 0.05f, -u * 0.12f), 2, u * 0.05f, spark, u * 0.03f, crackle)
        zigzag(b.offHand + Offset(u * 0.08f, -u * 0.04f), f + Offset(-u * 0.04f, -u * 0.14f), 2, u * 0.05f, bolt, u * 0.03f, crackle + 3f)
    }
    drawCircle(Skin.darker(0.08f), u * 0.11f, hand)
    drawCircle(Skin.darker(0.08f), u * 0.11f, b.offHand)
}

// --- Piratin ---

private fun DrawScope.drawPirate(b: HeroBody, time: Float) {
    val u = b.u
    val c = b.c
    val dir = b.dir
    val h = b.head
    val hr = b.headR
    val coat = Color(0xFF991B1B)
    val gold = HeroGold
    val hair = Color(0xFF4A2511)
    val pants = Color(0xFF292524)
    val boots = Color(0xFF3B2414)
    val sway = sin(time * 3f) * u * 0.05f

    // Lange Haare hinten, gewellt
    val hairBack = Path().apply {
        moveTo(h.x - hr * 1.05f, h.y - hr * 0.4f)
        lineTo(h.x + hr * 1.05f, h.y - hr * 0.4f)
        quadraticTo(h.x + hr * 1.3f, h.y + hr * 0.8f, h.x + hr * 1.1f + sway, b.shoulderY + u * 0.35f)
        quadraticTo(h.x + hr * 0.8f, b.shoulderY + u * 0.45f, h.x + hr * 0.6f, b.shoulderY + u * 0.3f)
        lineTo(h.x - hr * 0.6f, b.shoulderY + u * 0.3f)
        quadraticTo(h.x - hr * 0.8f, b.shoulderY + u * 0.45f, h.x - hr * 1.1f + sway, b.shoulderY + u * 0.35f)
        quadraticTo(h.x - hr * 1.3f, h.y + hr * 0.8f, h.x - hr * 1.05f, h.y - hr * 0.4f)
        close()
    }
    drawPath(hairBack, hair)

    // Mantelschöße hinter dem Körper
    val tails = Path().apply {
        moveTo(c.x - u * 0.3f, b.shoulderY)
        lineTo(c.x + u * 0.3f, b.shoulderY)
        lineTo(c.x + u * 0.36f, b.hipY)
        lineTo(c.x + u * 0.56f - dir * sway, b.footY - u * 0.3f)
        lineTo(c.x + u * 0.2f, b.footY - u * 0.38f)
        lineTo(c.x - u * 0.2f, b.footY - u * 0.38f)
        lineTo(c.x - u * 0.56f - dir * sway, b.footY - u * 0.3f)
        lineTo(c.x - u * 0.36f, b.hipY)
        close()
    }
    drawPath(tails, coat.darker(0.25f))
    drawPath(tails, gold.darker(0.2f), style = Stroke(u * 0.03f))

    // Schlanke Beine in enger Hose, Stulpenstiefel
    heroLegs(b, pants, boots, legWidth = 0.17f, bootHeight = 0.4f, width = 0.86f)
    for (side in listOf(-1f, 1f)) {
        val x = c.x + side * u * 0.18f
        drawPath(Path().apply {
            moveTo(x - u * 0.12f, b.hipY)
            lineTo(x + u * 0.12f, b.hipY)
            quadraticTo(x + u * 0.13f, b.hipY + u * 0.22f, x + u * 0.085f, b.footY - u * 0.36f)
            lineTo(x - u * 0.085f, b.footY - u * 0.36f)
            quadraticTo(x - u * 0.13f, b.hipY + u * 0.22f, x - u * 0.12f, b.hipY)
            close()
        }, pants)
        // Umgeschlagene Stiefelstulpe
        drawPath(Path().apply {
            moveTo(x - u * 0.13f, b.footY - u * 0.44f)
            lineTo(x + u * 0.13f, b.footY - u * 0.44f)
            lineTo(x + u * 0.11f, b.footY - u * 0.32f)
            lineTo(x - u * 0.11f, b.footY - u * 0.32f)
            close()
        }, boots.lighter(0.15f))
    }

    // Ärmel mit Rüschenmanschetten
    for (hand in listOf(b.hand, b.offHand)) {
        heroArm(b, hand, coat, 0.17f, shoulder = 0.3f)
        val s = if (hand.x > c.x) 1f else -1f
        val arm = hand - Offset(c.x + s * u * 0.3f, b.shoulderY + u * 0.1f)
        val cuff = hand - arm * 0.16f
        drawLine(gold.darker(0.1f), cuff - arm * 0.08f, cuff - arm * 0.04f, u * 0.2f)
        for (k in -1..1) drawCircle(Color(0xFFF5F5F4), u * 0.05f, cuff + Offset(k * u * 0.05f, u * 0.03f))
    }

    // Bluse mit tiefem Ausschnitt, Korsett mit Schnürung
    val waistY = b.hipY - u * 0.06f
    val body = femaleTorso(b, waistY, b.hipY + u * 0.1f, hip = 0.33f, waist = 0.19f, shoulder = 0.22f, bust = 0.3f)
    drawPath(body, Color(0xFFF5F5F4))
    clipPath(body) {
        drawRect(Color(0xFFD6D3D1), Offset(c.x + u * 0.18f, b.shoulderY - u * 0.1f), Size(u * 0.4f, u))
        // Korsett ab unter der Brust
        val corset = Path().apply {
            moveTo(c.x - u * 0.4f, b.shoulderY + u * 0.24f)
            quadraticTo(c.x - u * 0.15f, b.shoulderY + u * 0.3f, c.x, b.shoulderY + u * 0.22f)
            quadraticTo(c.x + u * 0.15f, b.shoulderY + u * 0.3f, c.x + u * 0.4f, b.shoulderY + u * 0.24f)
            lineTo(c.x + u * 0.4f, b.hipY + u * 0.2f)
            lineTo(c.x - u * 0.4f, b.hipY + u * 0.2f)
            close()
        }
        drawPath(corset, Color(0xFF44281A))
        for (i in 0..2) {
            val y = b.shoulderY + u * (0.3f + i * 0.08f)
            drawLine(gold, Offset(c.x - u * 0.05f, y), Offset(c.x + u * 0.05f, y + u * 0.04f), u * 0.018f, StrokeCap.Round)
            drawLine(gold, Offset(c.x + u * 0.05f, y), Offset(c.x - u * 0.05f, y + u * 0.04f), u * 0.018f, StrokeCap.Round)
        }
    }
    // Prall gefüllte Bluse über dem Korsett
    val blouse = Color(0xFFF5F5F4)
    for (side in listOf(-1f, 1f)) {
        val p = Offset(c.x + side * u * 0.12f, b.shoulderY + u * 0.17f)
        drawCircle(blouse, u * 0.15f, p)
        drawArc(Color(0xFFD6D3D1), 20f, 140f, false, p - Offset(u * 0.14f, u * 0.14f), Size(u * 0.28f, u * 0.28f), style = Stroke(u * 0.025f, cap = StrokeCap.Round))
        drawCircle(Color.White, u * 0.035f, p + Offset(-u * 0.05f, -u * 0.06f))
    }
    val neck = Path().apply {
        moveTo(c.x - u * 0.15f, b.shoulderY - u * 0.04f)
        lineTo(c.x + u * 0.15f, b.shoulderY - u * 0.04f)
        lineTo(c.x, b.shoulderY + u * 0.15f)
        close()
    }
    drawPath(neck, Skin.darker(0.06f))
    drawLine(Skin.darker(0.18f), Offset(c.x, b.shoulderY + u * 0.1f), Offset(c.x, b.shoulderY + u * 0.2f), u * 0.02f, StrokeCap.Round)
    // Breiter Gürtel mit Goldschnalle, rote Schärpe
    drawLine(Color(0xFF1C1917), Offset(c.x - u * 0.24f, waistY + u * 0.04f), Offset(c.x + u * 0.24f, waistY + u * 0.04f), u * 0.09f)
    drawRoundRect(gold, Offset(c.x - u * 0.06f, waistY - u * 0.01f), Size(u * 0.12f, u * 0.1f), CornerRadius(u * 0.02f))
    drawLine(Color(0xFFDC2626), Offset(c.x - dir * u * 0.18f, waistY + u * 0.06f), Offset(c.x - dir * u * 0.26f, waistY + u * 0.34f), u * 0.07f, StrokeCap.Round)

    // Offener Mantel: Seitenteile mit Revers und Goldknöpfen
    for (side in listOf(-1f, 1f)) {
        val panel = Path().apply {
            moveTo(c.x + side * u * 0.16f, b.shoulderY - u * 0.04f)
            lineTo(c.x + side * u * 0.3f, b.shoulderY - u * 0.04f)
            quadraticTo(c.x + side * u * 0.36f, b.shoulderY + u * 0.2f, c.x + side * u * 0.3f, waistY)
            quadraticTo(c.x + side * u * 0.38f, b.hipY + u * 0.1f, c.x + side * u * 0.4f, b.hipY + u * 0.32f)
            lineTo(c.x + side * u * 0.3f, b.hipY + u * 0.3f)
            quadraticTo(c.x + side * u * 0.26f, waistY, c.x + side * u * 0.27f, b.shoulderY + u * 0.3f)
            close()
        }
        drawPath(panel, coat)
        drawLine(coat.lighter(0.2f), Offset(c.x + side * u * 0.17f, b.shoulderY - u * 0.02f), Offset(c.x + side * u * 0.27f, b.shoulderY + u * 0.28f), u * 0.05f, StrokeCap.Round)
        for (k in 0..1) drawCircle(gold, u * 0.03f, Offset(c.x + side * u * 0.31f, b.shoulderY + u * (0.12f + k * 0.12f)))
    }

    // Gesicht mit Augenklappe und Ohrring
    femaleFace(b, brows = hair.darker(0.2f))
    val patchEye = Offset(h.x - hr * 0.36f + dir * hr * 0.06f, h.y + hr * 0.08f)
    drawLine(Color(0xFF111827), Offset(h.x - hr * 0.98f, h.y - hr * 0.35f), Offset(h.x + hr * 0.95f, h.y - hr * 0.55f), hr * 0.05f)
    drawOval(Color(0xFF111827), patchEye - Offset(hr * 0.22f, hr * 0.18f), Size(hr * 0.44f, hr * 0.38f))
    drawCircle(gold, hr * 0.1f, Offset(h.x + dir * hr * 0.98f, h.y + hr * 0.42f), style = Stroke(hr * 0.05f))
    // Seitliche Strähnen und Pony, nur unter der Hutkrempe
    val brimY = h.y - hr * 0.62f
    clipRect(top = brimY) {
        drawPath(Path().apply {
            moveTo(h.x - dir * hr * 1.06f, h.y + hr * 0.55f)
            cubicTo(h.x - dir * hr * 1.15f, h.y - hr * 1.4f, h.x + dir * hr * 1.15f, h.y - hr * 1.4f, h.x + dir * hr * 1.06f, h.y + hr * 0.55f)
            lineTo(h.x + dir * hr * 0.86f, h.y + hr * 0.3f)
            quadraticTo(h.x + dir * hr * 0.8f, h.y - hr * 0.35f, h.x + dir * hr * 0.2f, h.y - hr * 0.5f)
            quadraticTo(h.x - dir * hr * 0.6f, h.y - hr * 0.45f, h.x - dir * hr * 0.86f, h.y + hr * 0.3f)
            close()
        }, hair)
    }
    // Dreispitz mit Goldrand und Feder
    val feather = Path().apply {
        moveTo(h.x - dir * hr * 0.4f, brimY - hr * 0.3f)
        quadraticTo(h.x - dir * hr * 1.3f, brimY - hr * 1.1f, h.x - dir * hr * 1.8f, brimY - hr * 0.6f + sway)
    }
    drawPath(feather, Color(0xFFF5F5F4), style = Stroke(hr * 0.22f, cap = StrokeCap.Round))
    drawPath(feather, Color(0xFFD6D3D1), style = Stroke(hr * 0.04f, cap = StrokeCap.Round))
    val hat = Path().apply {
        moveTo(h.x - hr * 1.4f, brimY + hr * 0.05f)
        quadraticTo(h.x, brimY + hr * 0.35f, h.x + hr * 1.4f, brimY + hr * 0.05f)
        lineTo(h.x + hr * 0.95f, brimY - hr * 0.55f)
        quadraticTo(h.x + hr * 0.5f, brimY - hr * 0.85f, h.x, brimY - hr * 0.62f)
        quadraticTo(h.x - hr * 0.5f, brimY - hr * 0.85f, h.x - hr * 0.95f, brimY - hr * 0.55f)
        close()
    }
    drawPath(hat, Color(0xFF1F2937))
    drawPath(hat, gold, style = Stroke(hr * 0.08f))
    skull(Offset(h.x, brimY - hr * 0.22f), hr * 0.16f)

    // Anker an der Kette, Kette um den Unterarm gewickelt
    val hand = b.hand
    val chain = Color(0xFF9CA3AF)
    for (k in 0..2) drawOval(chain, hand + Offset(-dir * u * (0.06f + k * 0.07f), -u * (0.08f + k * 0.07f)) - Offset(u * 0.05f, u * 0.035f), Size(u * 0.1f, u * 0.07f), style = Stroke(u * 0.025f))
    val ankerTop = hand + Offset(dir * u * 0.08f, u * 0.1f)
    for (k in 0..1) drawOval(chain, ankerTop + Offset(0f, k * u * 0.08f) - Offset(u * 0.035f, u * 0.05f), Size(u * 0.07f, u * 0.1f), style = Stroke(u * 0.025f))
    val ring = ankerTop + Offset(0f, u * 0.2f)
    val steel = Color(0xFF6B7280)
    drawCircle(steel, u * 0.06f, ring, style = Stroke(u * 0.03f))
    val shankBottom = ring + Offset(0f, u * 0.62f)
    drawLine(steel, ring + Offset(0f, u * 0.05f), shankBottom, u * 0.07f, StrokeCap.Round)
    drawLine(steel.darker(0.2f), ring + Offset(-u * 0.17f, u * 0.13f), ring + Offset(u * 0.17f, u * 0.13f), u * 0.06f, StrokeCap.Round)
    drawArc(steel, 20f, 140f, false, shankBottom - Offset(u * 0.26f, u * 0.3f), Size(u * 0.52f, u * 0.36f), style = Stroke(u * 0.07f, cap = StrokeCap.Round))
    for (side in listOf(-1f, 1f)) {
        // Flunke direkt am Ende des Ankerarms (Bogenende bei 20°/160°)
        val end = shankBottom + Offset(side * u * 0.244f, -u * 0.058f)
        drawPath(Path().apply {
            moveTo(end.x - side * u * 0.05f, end.y + u * 0.03f)
            lineTo(end.x + side * u * 0.09f, end.y + u * 0.01f)
            lineTo(end.x + side * u * 0.03f, end.y - u * 0.15f)
            close()
        }, steel)
    }
    drawLine(Color.White.copy(alpha = 0.4f), ring + Offset(-u * 0.015f, u * 0.1f), shankBottom - Offset(u * 0.015f, u * 0.1f), u * 0.02f)
    drawCircle(Skin.darker(0.08f), u * 0.1f, hand)
    drawCircle(Skin.darker(0.08f), u * 0.1f, b.offHand)
}

/** Fünfzackiger Stern. */
private fun DrawScope.star(c: Offset, r: Float, color: Color) {
    val path = Path()
    for (i in 0 until 10) {
        val a = -Math.PI / 2 + i * Math.PI / 5
        val rr = if (i % 2 == 0) r else r * 0.45f
        val p = Offset(c.x + (kotlin.math.cos(a) * rr).toFloat(), c.y + (sin(a) * rr).toFloat())
        if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
    }
    path.close()
    drawPath(path, color)
}
