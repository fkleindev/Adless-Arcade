package com.adlessarcade.games.runetactics

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import kotlin.math.sin

internal val undeadArt: Map<String, DrawScope.(ArtBody) -> Unit> = mapOf(
    "skeleton" to DrawScope::drawSkeletonWarrior,
    "wisp" to DrawScope::drawGraveLight,
    "bonewarden" to DrawScope::drawBoneWarden,
    "deathknight" to DrawScope::drawDeathKnight,
    "lich" to DrawScope::drawLich,
    "shadowblade" to DrawScope::drawShadowblade,
)

private val BoneShade = Color(0xFFA8A29E)
private val Void = Color(0xFF1C1917)

/** Knochen von [from] nach [to] mit verdickten Gelenkenden. */
private fun DrawScope.bone(from: Offset, to: Offset, w: Float, color: Color = ArtBone) {
    drawLine(color, from, to, w, StrokeCap.Round)
    drawCircle(color, w * 0.75f, from)
    drawCircle(color, w * 0.75f, to)
    drawLine(BoneShade, from + (to - from) * 0.2f + Offset(w * 0.2f, 0f), from + (to - from) * 0.8f + Offset(w * 0.2f, 0f), w * 0.25f, StrokeCap.Round)
}

/** Brustkorb: Wirbelsäule, Brustbein und gebogene Rippen. */
private fun DrawScope.ribcage(b: ArtBody, top: Float, bottom: Float, width: Float, color: Color = ArtBone) {
    val u = b.u
    val c = b.c
    drawOval(Void.copy(alpha = 0.55f), Offset(c.x - u * width, top), Size(u * width * 2f, bottom - top))
    drawLine(color, Offset(c.x, top), Offset(c.x, bottom + u * 0.1f), u * 0.08f, StrokeCap.Round)
    for (i in 0..3) {
        val y = top + (bottom - top) * (0.12f + i * 0.24f)
        val w = u * width * (1f - i * 0.1f)
        drawArc(color, 200f, 140f, false, Offset(c.x - w, y - u * 0.04f), Size(w * 2f, u * 0.3f), style = Stroke(u * 0.055f, cap = StrokeCap.Round))
    }
}

/** Becken als breiter Knochenbogen. */
private fun DrawScope.pelvis(b: ArtBody, y: Float, color: Color = ArtBone) {
    val u = b.u
    val p = Path().apply {
        moveTo(b.c.x - u * 0.3f, y - u * 0.06f)
        quadraticTo(b.c.x, y - u * 0.16f, b.c.x + u * 0.3f, y - u * 0.06f)
        quadraticTo(b.c.x + u * 0.22f, y + u * 0.14f, b.c.x, y + u * 0.12f)
        quadraticTo(b.c.x - u * 0.22f, y + u * 0.14f, b.c.x - u * 0.3f, y - u * 0.06f)
        close()
    }
    drawPath(p, color)
    for (side in listOf(-1f, 1f)) drawCircle(Void, u * 0.05f, Offset(b.c.x + side * u * 0.12f, y + u * 0.01f))
}

/** Knochenbeine mit Knie und Fußknochen. */
private fun DrawScope.boneLegs(b: ArtBody, w: Float = 0.1f, greave: Color? = null) {
    val u = b.u
    for (side in listOf(-1f, 1f)) {
        val hip = Offset(b.c.x + side * u * 0.18f, b.hipY + u * 0.04f)
        val knee = Offset(b.c.x + side * u * 0.22f, (b.hipY + b.footY) / 2f + u * 0.02f)
        val ankle = Offset(b.c.x + side * u * 0.2f, b.footY - u * 0.08f)
        bone(hip, knee, u * w)
        bone(knee, ankle, u * w * 0.9f)
        greave?.let {
            drawRoundRect(it, Offset(ankle.x - u * 0.1f, knee.y + u * 0.06f), Size(u * 0.2f, ankle.y - knee.y - u * 0.06f), CornerRadius(u * 0.05f))
            drawRoundRect(it.bright(0.25f), Offset(ankle.x - u * 0.07f, knee.y + u * 0.1f), Size(u * 0.04f, ankle.y - knee.y - u * 0.16f), CornerRadius(u * 0.02f))
            drawCircle(it.bright(0.1f), u * 0.1f, knee)
        }
        val foot = Path().apply {
            moveTo(ankle.x - u * 0.08f, ankle.y)
            lineTo(ankle.x + b.dir * u * 0.2f, b.footY - u * 0.02f)
            lineTo(ankle.x + b.dir * u * 0.2f, b.footY + u * 0.02f)
            lineTo(ankle.x - u * 0.08f, b.footY + u * 0.02f)
            close()
        }
        drawPath(foot, greave ?: ArtBone)
    }
}

/** Totenkopf in Kopfgröße: Schädel, Wangenknochen, Kiefer, Augenhöhlen mit Glut. */
private fun DrawScope.headSkull(b: ArtBody, glow: Color, jawDrop: Float = 0.04f, bone: Color = ArtBone) {
    val h = b.head
    val hr = b.headR
    val dir = b.dir
    // Kiefer leicht geöffnet
    val jawY = h.y + hr * (0.45f + jawDrop)
    drawRoundRect(bone.shade(0.08f), Offset(h.x - hr * 0.5f + dir * hr * 0.06f, jawY), Size(hr * 1f, hr * 0.42f), CornerRadius(hr * 0.18f))
    drawCircle(bone, hr, h + Offset(0f, -hr * 0.05f))
    // Wangenknochen
    drawOval(bone, Offset(h.x - hr * 0.78f, h.y + hr * 0.05f), Size(hr * 1.56f, hr * 0.55f))
    // Schatten der Schläfe
    drawArc(BoneShade, if (dir > 0) 120f else -60f, 120f, false, h - Offset(hr * 0.9f, hr * 0.95f), Size(hr * 1.8f, hr * 1.8f), style = Stroke(hr * 0.1f))
    // Augenhöhlen mit Glut
    val p = b.pulse(5f)
    for (side in listOf(-1f, 1f)) {
        val e = Offset(h.x + side * hr * 0.36f + dir * hr * 0.08f, h.y + hr * 0.05f)
        drawOval(Void, e - Offset(hr * 0.24f, hr * 0.2f), Size(hr * 0.48f, hr * 0.42f))
        drawCircle(glow.copy(alpha = 0.35f * p), hr * 0.2f, e)
        drawCircle(glow, hr * 0.09f, e + Offset(dir * hr * 0.03f, 0f))
    }
    // Nasenloch
    drawPath(Path().apply {
        moveTo(h.x + dir * hr * 0.08f, h.y + hr * 0.25f)
        lineTo(h.x + dir * hr * 0.08f - hr * 0.1f, h.y + hr * 0.45f)
        lineTo(h.x + dir * hr * 0.08f + hr * 0.1f, h.y + hr * 0.45f)
        close()
    }, Void)
    // Zahnreihen oben und unten
    for (row in 0..1) {
        val y = h.y + hr * (0.52f + row * (0.12f + jawDrop))
        drawLine(Void, Offset(h.x - hr * 0.36f + dir * hr * 0.06f, y), Offset(h.x + hr * 0.36f + dir * hr * 0.06f, y), hr * 0.04f)
        for (i in -2..2) drawLine(Void, Offset(h.x + i * hr * 0.14f + dir * hr * 0.06f, y - hr * 0.07f), Offset(h.x + i * hr * 0.14f + dir * hr * 0.06f, y + hr * 0.07f), hr * 0.03f)
    }
}

// --- Skelettkrieger ---

private fun DrawScope.drawSkeletonWarrior(b: ArtBody) {
    val u = b.u
    val c = b.c
    val dir = b.dir
    val rust = Color(0xFF8B5A3C)
    val iron = Color(0xFF6B6560)
    val red = Color(0xFFB91C1C)

    boneLegs(b)
    // Zerfetzter roter Lendenschurz am rostigen Gürtel
    val cloth = Path().apply {
        moveTo(c.x - u * 0.3f, b.hipY - u * 0.04f)
        lineTo(c.x + u * 0.3f, b.hipY - u * 0.04f)
        lineTo(c.x + u * 0.24f, b.hipY + u * 0.36f)
        lineTo(c.x + u * 0.12f, b.hipY + u * 0.28f)
        lineTo(c.x + u * 0.02f, b.hipY + u * 0.42f)
        lineTo(c.x - u * 0.1f, b.hipY + u * 0.3f)
        lineTo(c.x - u * 0.24f, b.hipY + u * 0.38f)
        close()
    }
    pelvis(b, b.hipY)
    drawPath(cloth, red.shade(0.15f))
    clipPath(cloth) { drawRect(red.shade(0.4f), Offset(c.x + u * 0.08f, b.hipY - u * 0.1f), Size(u * 0.4f, u * 0.6f)) }

    // Knochenarme mit Ellbogen
    for (hand in listOf(b.hand, b.offHand)) {
        val s = if (hand.x > c.x) 1f else -1f
        val shoulder = Offset(c.x + s * u * 0.34f, b.shoulderY + u * 0.06f)
        val elbow = Offset(c.x + s * u * 0.52f, (b.shoulderY + hand.y) / 2f + u * 0.06f)
        bone(shoulder, elbow, u * 0.08f)
        bone(elbow, hand, u * 0.07f)
    }

    ribcage(b, b.shoulderY - u * 0.02f, b.hipY - u * 0.12f, 0.34f)
    // Schlüsselbeine
    for (side in listOf(-1f, 1f)) bone(Offset(c.x, b.shoulderY), Offset(c.x + side * u * 0.36f, b.shoulderY + u * 0.04f), u * 0.06f)
    drawLine(rust.shade(0.2f), Offset(c.x - u * 0.32f, b.hipY - u * 0.08f), Offset(c.x + u * 0.32f, b.hipY - u * 0.08f), u * 0.08f)
    drawRoundRect(iron, Offset(c.x - u * 0.06f, b.hipY - u * 0.14f), Size(u * 0.12f, u * 0.12f), CornerRadius(u * 0.02f))

    // Rostige Schulterplatte auf der Waffenseite
    artPauldron(b, dir, iron, rust)

    // Rundschild mit roter Bemalung und Delle
    val sc = b.offHand + Offset(-dir * u * 0.08f, -u * 0.06f)
    val sr = u * 0.4f
    drawCircle(Color(0xFF7C5A3A), sr, sc)
    drawArc(red.shade(0.1f), -60f, 120f, true, sc - Offset(sr, sr), Size(sr * 2f, sr * 2f))
    drawArc(red.shade(0.1f), 120f, 120f, true, sc - Offset(sr, sr), Size(sr * 2f, sr * 2f))
    for (i in -1..1) drawLine(Color(0xFF5B3F27), sc + Offset(i * sr * 0.45f, -sr * 0.9f), sc + Offset(i * sr * 0.45f, sr * 0.9f), u * 0.025f)
    drawCircle(iron, sr, sc, style = Stroke(u * 0.07f))
    drawCircle(iron.bright(0.2f), sr * 0.28f, sc)
    drawCircle(iron.bright(0.5f), sr * 0.1f, sc + Offset(-sr * 0.08f, -sr * 0.08f))
    drawArc(Void.copy(alpha = 0.5f), 200f, 60f, false, sc + Offset(sr * 0.1f, sr * 0.1f), Size(sr * 0.6f, sr * 0.5f), style = Stroke(u * 0.03f))

    // Schädel mit rostiger Eisenkappe
    headSkull(b, Color(0xFFEF4444))
    val h = b.head
    val hr = b.headR
    val cap = Path().apply {
        moveTo(h.x - hr * 1.08f, h.y - hr * 0.25f)
        cubicTo(h.x - hr * 1.1f, h.y - hr * 1.45f, h.x + hr * 1.1f, h.y - hr * 1.45f, h.x + hr * 1.08f, h.y - hr * 0.25f)
        close()
    }
    drawPath(cap, iron)
    drawLine(rust, Offset(h.x - hr * 1.12f, h.y - hr * 0.28f), Offset(h.x + hr * 1.12f, h.y - hr * 0.28f), hr * 0.16f, StrokeCap.Round)
    for (i in -2..2) drawCircle(iron.bright(0.35f), hr * 0.05f, Offset(h.x + i * hr * 0.42f, h.y - hr * 0.28f))
    drawArc(iron.bright(0.4f), 200f, 50f, false, h + Offset(-hr * 0.8f, -hr * 1.05f), Size(hr * 1.6f, hr * 1.2f), style = Stroke(hr * 0.09f, cap = StrokeCap.Round))

    // Schartiges, rostiges Schwert
    artSword(b, b.hand, 0.95f, Color(0xFFB8B2A7), rust)
    val tip = b.hand + Offset(dir * u * 0.3f * 0.95f, -u * 1.11f)
    for (k in 0..1) {
        val p = b.hand + (tip - b.hand) * (0.45f + k * 0.25f)
        drawCircle(Void, u * 0.035f, p + Offset(dir * u * 0.06f, 0f))
    }
    drawCircle(rust.copy(alpha = 0.7f), u * 0.05f, b.hand + (tip - b.hand) * 0.35f)
    artBoneHand(b, b.hand)
}

// --- Grablicht ---

private fun DrawScope.drawGraveLight(b: ArtBody) {
    val u = b.u
    val c = b.c
    val dir = b.dir
    val time = b.time
    val ghost = Color(0xFF67E8F9)
    val robe = Color(0xFF3B1F6B)
    val pale = Color(0xFFD5F5F8)
    val p = b.pulse(4.5f)
    val hover = sin(time * 2.4f) * u * 0.05f

    // Geisterschein
    drawCircle(ghost.copy(alpha = 0.12f + 0.06f * p), u * 1.15f, c + Offset(0f, -u * 0.2f))

    // Schweif statt Beinen: wehende Fetzen
    val tailBase = b.hipY + hover
    for (i in 0..2) {
        val wave = sin(time * 3f + i * 1.3f) * u * 0.12f
        val x = c.x + (i - 1) * u * 0.22f
        val tail = Path().apply {
            moveTo(x - u * 0.16f, tailBase)
            quadraticTo(x - u * 0.12f + wave, tailBase + u * 0.4f, x + wave * 1.6f - dir * u * 0.1f, b.footY + (if (i == 1) u * 0.02f else -u * 0.1f))
            quadraticTo(x + u * 0.12f + wave, tailBase + u * 0.4f, x + u * 0.16f, tailBase)
            close()
        }
        drawPath(tail, ghost.copy(alpha = 0.45f))
    }

    // Lange, zerschlissene Robe
    val hemY = b.footY - u * 0.3f + hover
    val body = Path().apply {
        moveTo(c.x - u * 0.34f, b.shoulderY - u * 0.04f + hover)
        lineTo(c.x + u * 0.34f, b.shoulderY - u * 0.04f + hover)
        lineTo(c.x + u * 0.5f, hemY - u * 0.05f)
        lineTo(c.x + u * 0.36f, hemY + u * 0.1f)
        lineTo(c.x + u * 0.22f, hemY - u * 0.02f)
        lineTo(c.x + u * 0.06f, hemY + u * 0.14f)
        lineTo(c.x - u * 0.1f, hemY - u * 0.02f)
        lineTo(c.x - u * 0.26f, hemY + u * 0.12f)
        lineTo(c.x - u * 0.38f, hemY - u * 0.02f)
        lineTo(c.x - u * 0.5f, hemY + u * 0.06f)
        close()
    }
    drawPath(body, robe)
    clipPath(body) {
        drawRect(robe.shade(0.35f), Offset(c.x + u * 0.16f, b.shoulderY - u * 0.2f), Size(u * 0.5f, u * 1.8f))
        drawRect(ghost.copy(alpha = 0.25f), Offset(c.x - u * 0.6f, hemY - u * 0.12f), Size(u * 1.2f, u * 0.3f))
    }
    drawLine(ghost.copy(alpha = 0.7f), Offset(c.x, b.shoulderY + u * 0.15f + hover), Offset(c.x, hemY), u * 0.04f)
    // Kette mit Grabschloss
    drawArc(Color(0xFF9CA3AF), 20f, 140f, false, Offset(c.x - u * 0.32f, b.shoulderY + hover - u * 0.06f), Size(u * 0.64f, u * 0.5f), style = Stroke(u * 0.04f))
    drawRoundRect(Color(0xFF9CA3AF), Offset(c.x - u * 0.07f, b.shoulderY + u * 0.38f + hover), Size(u * 0.14f, u * 0.12f), CornerRadius(u * 0.02f))
    drawCircle(Void, u * 0.025f, Offset(c.x, b.shoulderY + u * 0.44f + hover))

    // Weite Ärmel mit bleichen Händen
    val hand = b.hand + Offset(0f, hover)
    val offHand = b.offHand + Offset(0f, hover - u * 0.1f)
    for (hd in listOf(hand, offHand)) {
        val s = if (hd.x > c.x) 1f else -1f
        val sleeve = Path().apply {
            moveTo(c.x + s * u * 0.26f, b.shoulderY + hover)
            lineTo(hd.x + s * u * 0.08f, hd.y - u * 0.16f)
            lineTo(hd.x + s * u * 0.12f, hd.y + u * 0.06f)
            lineTo(hd.x - s * u * 0.18f, hd.y + u * 0.1f)
            lineTo(c.x + s * u * 0.2f, b.shoulderY + u * 0.3f + hover)
            close()
        }
        drawPath(sleeve, robe.bright(0.08f))
        drawLine(ghost.copy(alpha = 0.6f), Offset(hd.x - s * u * 0.18f, hd.y + u * 0.1f), Offset(hd.x + s * u * 0.12f, hd.y + u * 0.06f), u * 0.035f, StrokeCap.Round)
        artHand(b, hd, pale.shade(0.1f), 0.09f)
    }

    // Kopf: hageres Geistergesicht mit Geisterflammen statt Haaren
    val h = b.head + Offset(0f, hover)
    val hr = b.headR
    for (i in -2..2) {
        val flick = sin(time * 9f + i * 1.9f) * hr * 0.15f
        val tipY = h.y - hr * (1.35f + (2 - kotlin.math.abs(i)) * 0.32f) + flick
        val flame = Path().apply {
            moveTo(h.x + i * hr * 0.34f - hr * 0.28f, h.y - hr * 0.3f)
            quadraticTo(h.x + i * hr * 0.4f - hr * 0.2f, tipY + hr * 0.5f, h.x + i * hr * 0.36f + flick * 0.5f, tipY)
            quadraticTo(h.x + i * hr * 0.32f + hr * 0.28f, tipY + hr * 0.5f, h.x + i * hr * 0.34f + hr * 0.28f, h.y - hr * 0.3f)
            close()
        }
        drawPath(flame, ghost.copy(alpha = if (kotlin.math.abs(i) == 2) 0.55f else 0.8f))
    }
    drawCircle(pale.copy(alpha = 0.9f), hr * 0.22f, h + Offset(0f, hr * 0.85f))
    drawCircle(pale, hr, h)
    drawArc(Color(0xFF9ACFD6), if (dir > 0) 100f else -80f, 160f, false, h - Offset(hr * 0.92f, hr * 0.92f), Size(hr * 1.84f, hr * 1.84f), style = Stroke(hr * 0.12f))
    for (side in listOf(-1f, 1f)) {
        val e = Offset(h.x + side * hr * 0.36f + dir * hr * 0.06f, h.y + hr * 0.02f)
        drawOval(Color(0xFF334155), e - Offset(hr * 0.22f, hr * 0.2f), Size(hr * 0.44f, hr * 0.42f))
        drawCircle(ghost.copy(alpha = 0.5f * p), hr * 0.18f, e)
        drawCircle(Color.White, hr * 0.08f, e + Offset(dir * hr * 0.03f, 0f))
        drawLine(Color(0xFF9ACFD6), Offset(h.x + side * hr * 0.5f, h.y + hr * 0.3f), Offset(h.x + side * hr * 0.38f, h.y + hr * 0.6f), hr * 0.05f, StrokeCap.Round)
    }
    drawOval(Color(0xFF334155), Offset(h.x - hr * 0.12f + dir * hr * 0.06f, h.y + hr * 0.42f), Size(hr * 0.24f, hr * 0.3f))

    // Krummer Stab mit hängender Grablaterne
    val top = hand + Offset(dir * u * 0.04f, -u * 1.25f)
    drawLine(ArtWood.shade(0.2f), hand + Offset(-dir * u * 0.02f, u * 0.7f), top, u * 0.08f, StrokeCap.Round)
    val hook = Path().apply {
        moveTo(top.x, top.y)
        quadraticTo(top.x + dir * u * 0.05f, top.y - u * 0.22f, top.x + dir * u * 0.3f, top.y - u * 0.16f)
        lineTo(top.x + dir * u * 0.34f, top.y - u * 0.05f)
    }
    drawPath(hook, ArtWood.shade(0.2f), style = Stroke(u * 0.07f, cap = StrokeCap.Round))
    val swing = sin(time * 2.5f) * u * 0.05f
    val lantern = Offset(top.x + dir * u * 0.34f + swing, top.y + u * 0.2f)
    drawLine(Color(0xFF9CA3AF), Offset(top.x + dir * u * 0.34f, top.y - u * 0.05f), lantern - Offset(0f, u * 0.13f), u * 0.025f)
    drawCircle(ghost.copy(alpha = 0.3f * p), u * 0.32f, lantern)
    drawRoundRect(Color(0xFF475569), lantern - Offset(u * 0.12f, u * 0.14f), Size(u * 0.24f, u * 0.28f), CornerRadius(u * 0.04f))
    drawRoundRect(ghost, lantern - Offset(u * 0.08f, u * 0.09f), Size(u * 0.16f, u * 0.18f), CornerRadius(u * 0.03f))
    drawCircle(Color.White, u * 0.04f, lantern + Offset(0f, u * 0.02f))
    drawLine(Color(0xFF475569), lantern - Offset(0f, u * 0.09f), lantern + Offset(0f, u * 0.09f), u * 0.025f)
    drawPath(triangle3(lantern - Offset(0f, u * 0.14f), u * 0.14f, u * 0.08f), Color(0xFF475569))
}

/** Kleines Dach (Dreieck) mit Grundlinie bei [base]. */
private fun triangle3(base: Offset, halfW: Float, h: Float) = Path().apply {
    moveTo(base.x - halfW, base.y)
    lineTo(base.x, base.y - h)
    lineTo(base.x + halfW, base.y)
    close()
}

// --- Knochenwächter ---

private fun DrawScope.drawBoneWarden(b: ArtBody) {
    val u = b.u
    val c = b.c
    val dir = b.dir
    val iron = Color(0xFF57534E)
    val violet = Color(0xFF8B5CF6)
    val p = b.pulse(4f)

    boneLegs(b, 0.11f, greave = iron)
    pelvis(b, b.hipY)
    // Violetter Wappenrock unter dem Panzer
    val tabard = Path().apply {
        moveTo(c.x - u * 0.2f, b.hipY - u * 0.1f)
        lineTo(c.x + u * 0.2f, b.hipY - u * 0.1f)
        lineTo(c.x + u * 0.16f, b.hipY + u * 0.44f)
        lineTo(c.x, b.hipY + u * 0.36f)
        lineTo(c.x - u * 0.16f, b.hipY + u * 0.44f)
        close()
    }
    drawPath(tabard, violet.shade(0.45f))
    drawLine(violet, Offset(c.x - u * 0.14f, b.hipY + u * 0.36f), Offset(c.x + u * 0.14f, b.hipY + u * 0.36f), u * 0.03f)

    // Knochenarme, Waffenarm mit Armschiene
    for (hand in listOf(b.hand, b.offHand)) {
        val s = if (hand.x > c.x) 1f else -1f
        val shoulder = Offset(c.x + s * u * 0.38f, b.shoulderY + u * 0.06f)
        val elbow = Offset(c.x + s * u * 0.56f, (b.shoulderY + hand.y) / 2f + u * 0.06f)
        bone(shoulder, elbow, u * 0.09f)
        bone(elbow, hand, u * 0.08f)
    }
    drawLine(iron, b.hand - (b.hand - Offset(c.x + dir * u * 0.56f, b.c.y)) * 0.1f, b.hand - (b.hand - Offset(c.x + dir * u * 0.56f, b.c.y)) * 0.6f, u * 0.16f, StrokeCap.Round)

    // Brustpanzer mit eingeprägten Rippen und Runenstein
    val top = Offset(c.x - u * 0.44f, b.shoulderY - u * 0.06f)
    val torso = Size(u * 0.88f, b.hipY - b.shoulderY + u * 0.04f)
    drawRoundRect(iron, top, torso, CornerRadius(u * 0.22f))
    drawRoundRect(iron.shade(0.25f), top + Offset(torso.width * 0.55f, 0f), Size(torso.width * 0.45f, torso.height), CornerRadius(u * 0.22f))
    drawRoundRect(iron.bright(0.35f), top + Offset(u * 0.1f, u * 0.08f), Size(u * 0.12f, torso.height * 0.45f), CornerRadius(u * 0.06f))
    for (i in 0..2) {
        val y = b.shoulderY + u * (0.14f + i * 0.13f)
        val w = u * (0.3f - i * 0.03f)
        drawArc(ArtBone, 200f, 140f, false, Offset(c.x - w, y), Size(w * 2f, u * 0.22f), style = Stroke(u * 0.05f, cap = StrokeCap.Round))
    }
    drawLine(ArtBone, Offset(c.x, b.shoulderY + u * 0.08f), Offset(c.x, b.hipY - u * 0.1f), u * 0.06f)
    drawCircle(violet.copy(alpha = 0.4f * p), u * 0.14f, Offset(c.x, b.shoulderY + u * 0.3f))
    drawCircle(violet, u * 0.07f, Offset(c.x, b.shoulderY + u * 0.3f))
    artBelt(b, b.hipY - u * 0.06f, 0.44f, Color(0xFF292524), ArtBone)

    // Wuchtige Schulterplatten, Waffenseite mit Schädel
    for (side in listOf(-1f, 1f)) {
        artPauldron(b, side, iron.bright(0.08f), ArtBone, 1.2f)
        for (k in 0..1) {
            val base = Offset(c.x + side * u * (0.36f + k * 0.16f), b.shoulderY - u * 0.1f)
            drawPath(triangle3(base, u * 0.05f, u * 0.16f), ArtBone)
        }
    }
    artSkull(Offset(c.x + dir * u * 0.46f, b.shoulderY + u * 0.04f), u * 0.1f)

    // Geschlossener Eisenhelm mit T-Schlitz
    val h = b.head
    val hr = b.headR
    drawRoundRect(iron, h + Offset(-hr * 1.02f, -hr * 1.1f), Size(hr * 2.04f, hr * 2.1f), CornerRadius(hr * 0.95f, hr * 0.8f))
    drawArc(iron.bright(0.4f), 195f, 55f, false, h + Offset(-hr * 0.8f, -hr * 0.95f), Size(hr * 1.6f, hr * 1.5f), style = Stroke(hr * 0.12f, cap = StrokeCap.Round))
    drawLine(Void, Offset(h.x - hr * 0.7f + dir * hr * 0.06f, h.y), Offset(h.x + hr * 0.7f + dir * hr * 0.06f, h.y), hr * 0.22f, StrokeCap.Round)
    drawLine(Void, Offset(h.x + dir * hr * 0.06f, h.y), Offset(h.x + dir * hr * 0.06f, h.y + hr * 0.62f), hr * 0.2f, StrokeCap.Round)
    for (side in listOf(-1f, 1f)) {
        val e = Offset(h.x + side * hr * 0.36f + dir * hr * 0.12f, h.y)
        drawCircle(violet.copy(alpha = 0.5f * p), hr * 0.16f, e)
        drawCircle(violet.bright(0.4f), hr * 0.07f, e)
    }
    drawLine(iron.shade(0.3f), Offset(h.x - hr * 0.75f, h.y + hr * 0.9f), Offset(h.x + hr * 0.75f, h.y + hr * 0.9f), hr * 0.14f, StrokeCap.Round)

    // Turmschild aus Knochenplatten mit Runenzeichen
    val sc = b.offHand + Offset(-dir * u * 0.12f, -u * 0.1f)
    val shield = Path().apply {
        moveTo(sc.x - u * 0.36f, sc.y - u * 0.6f)
        quadraticTo(sc.x, sc.y - u * 0.72f, sc.x + u * 0.36f, sc.y - u * 0.6f)
        lineTo(sc.x + u * 0.34f, sc.y + u * 0.42f)
        quadraticTo(sc.x, sc.y + u * 0.7f, sc.x - u * 0.34f, sc.y + u * 0.42f)
        close()
    }
    drawPath(shield, ArtBone)
    clipPath(shield) {
        drawRect(BoneShade, Offset(sc.x + u * 0.12f, sc.y - u * 0.8f), Size(u * 0.4f, u * 1.6f))
        for (k in 0..2) drawLine(BoneShade.shade(0.15f), Offset(sc.x - u * 0.4f, sc.y - u * 0.25f + k * u * 0.32f), Offset(sc.x + u * 0.4f, sc.y - u * 0.2f + k * u * 0.32f), u * 0.025f)
    }
    drawPath(shield, iron, style = Stroke(u * 0.07f))
    drawLine(violet, sc + Offset(0f, -u * 0.36f), sc + Offset(0f, u * 0.3f), u * 0.05f, StrokeCap.Round)
    drawLine(violet, sc + Offset(-u * 0.16f, -u * 0.18f), sc + Offset(u * 0.16f, u * 0.06f), u * 0.05f, StrokeCap.Round)
    drawLine(violet, sc + Offset(u * 0.16f, -u * 0.18f), sc + Offset(-u * 0.16f, u * 0.06f), u * 0.05f, StrokeCap.Round)
    drawCircle(violet.copy(alpha = 0.3f * p), u * 0.2f, sc + Offset(0f, -u * 0.04f))

    // Breites Schwert mit Knochengriff
    artSword(b, b.hand, 0.9f, Color(0xFFD6D3D1), ArtBone, 0.09f)
    artBoneHand(b, b.hand)
}

// --- Todesritter ---

private fun DrawScope.drawDeathKnight(b: ArtBody) {
    val u = b.u
    val c = b.c
    val dir = b.dir
    val time = b.time
    val plate = Color(0xFF2E2A4F)
    val plateDark = Color(0xFF17142E)
    val frost = Color(0xFF22D3EE)
    val p = b.pulse(3.5f)

    // Zerfetzter Umhang
    val sway = sin(time * 2.2f) * u * 0.06f
    val cape = Path().apply {
        moveTo(c.x - u * 0.44f, b.shoulderY)
        lineTo(c.x + u * 0.44f, b.shoulderY)
        lineTo(c.x + u * 0.8f - dir * sway, b.footY - u * 0.05f)
        lineTo(c.x + u * 0.55f - dir * sway, b.footY - u * 0.2f)
        lineTo(c.x + u * 0.38f - dir * sway, b.footY)
        lineTo(c.x + u * 0.1f - dir * sway, b.footY - u * 0.16f)
        lineTo(c.x - u * 0.15f - dir * sway, b.footY + u * 0.02f)
        lineTo(c.x - u * 0.42f - dir * sway, b.footY - u * 0.18f)
        lineTo(c.x - u * 0.8f - dir * sway, b.footY - u * 0.04f)
        close()
    }
    drawPath(cape, Color(0xFF111827))
    clipPath(cape) { drawRect(frost.copy(alpha = 0.18f), Offset(c.x - u, b.footY - u * 0.3f), Size(u * 2f, u * 0.4f)) }

    artLegs(b, plateDark, plate, knee = plate.bright(0.2f), bootHeight = 0.32f)
    // Kettenschurz mit Plattenschößen
    for (side in listOf(-1f, 1f)) {
        val tasset = Path().apply {
            moveTo(c.x + side * u * 0.04f, b.hipY - u * 0.04f)
            lineTo(c.x + side * u * 0.44f, b.hipY - u * 0.04f)
            lineTo(c.x + side * u * 0.48f, b.hipY + u * 0.3f)
            lineTo(c.x + side * u * 0.08f, b.hipY + u * 0.26f)
            close()
        }
        drawPath(tasset, plate)
        drawPath(tasset, frost.copy(alpha = 0.5f), style = Stroke(u * 0.025f))
    }

    // Arme in Platte
    artArm(b, b.hand, plate.shade(0.1f), 0.24f)
    artArm(b, b.offHand, plate.shade(0.1f), 0.24f)

    // Brustpanzer mit Frostrunen und Schädelwappen
    val top = Offset(c.x - u * 0.46f, b.shoulderY - u * 0.06f)
    val torso = Size(u * 0.92f, b.hipY - b.shoulderY + u * 0.08f)
    drawRoundRect(plate, top, torso, CornerRadius(u * 0.22f))
    drawRoundRect(plateDark, top + Offset(torso.width * 0.55f, 0f), Size(torso.width * 0.45f, torso.height), CornerRadius(u * 0.22f))
    drawRoundRect(plate.bright(0.3f), top + Offset(u * 0.1f, u * 0.08f), Size(u * 0.12f, torso.height * 0.45f), CornerRadius(u * 0.06f))
    for (side in listOf(-1f, 1f)) {
        drawLine(frost.copy(alpha = 0.5f + 0.4f * p), Offset(c.x + side * u * 0.12f, b.shoulderY + u * 0.1f), Offset(c.x + side * u * 0.26f, b.shoulderY + u * 0.3f), u * 0.035f, StrokeCap.Round)
        drawLine(frost.copy(alpha = 0.5f + 0.4f * p), Offset(c.x + side * u * 0.26f, b.shoulderY + u * 0.3f), Offset(c.x + side * u * 0.14f, b.hipY - u * 0.12f), u * 0.035f, StrokeCap.Round)
    }
    artSkull(Offset(c.x, b.shoulderY + u * 0.24f), u * 0.11f, ArtBone, frost.shade(0.4f))
    artBelt(b, b.hipY - u * 0.02f, 0.46f, Color(0xFF0F0D1F), frost.shade(0.2f))

    // Gezackte Schulterplatten
    for (side in listOf(-1f, 1f)) {
        artPauldron(b, side, plate.bright(0.1f), frost.shade(0.2f), 1.25f)
        for (k in 0..2) {
            val base = Offset(c.x + side * u * (0.32f + k * 0.12f), b.shoulderY - u * 0.12f + k * u * 0.02f)
            drawPath(triangle3(base, u * 0.045f, u * (0.2f - k * 0.04f)), Color(0xFF94A3B8))
        }
    }

    // Gehörnter Helm mit Visierschlitz und Frostatem
    val h = b.head
    val hr = b.headR
    for (side in listOf(-1f, 1f)) {
        val horn = Path().apply {
            moveTo(h.x + side * hr * 0.7f, h.y - hr * 0.55f)
            quadraticTo(h.x + side * hr * 1.7f, h.y - hr * 0.6f, h.x + side * hr * 1.35f, h.y - hr * 1.65f)
            quadraticTo(h.x + side * hr * 1.25f, h.y - hr * 0.95f, h.x + side * hr * 0.55f, h.y - hr * 0.95f)
            close()
        }
        drawPath(horn, Color(0xFF1F1D2B))
        drawPath(horn, Color(0xFF4B4766), style = Stroke(hr * 0.06f))
    }
    val helm = Path().apply {
        moveTo(h.x - hr * 1.0f, h.y + hr * 0.6f)
        cubicTo(h.x - hr * 1.15f, h.y - hr * 1.5f, h.x + hr * 1.15f, h.y - hr * 1.5f, h.x + hr * 1.0f, h.y + hr * 0.6f)
        lineTo(h.x + dir * hr * 0.1f, h.y + hr * 1.05f)
        close()
    }
    drawPath(helm, plate)
    drawArc(plate.bright(0.45f), 200f, 50f, false, h + Offset(-hr * 0.8f, -hr * 0.95f), Size(hr * 1.6f, hr * 1.4f), style = Stroke(hr * 0.1f, cap = StrokeCap.Round))
    drawLine(plate.bright(0.2f), Offset(h.x + dir * hr * 0.08f, h.y - hr * 1.1f), Offset(h.x + dir * hr * 0.1f, h.y + hr * 1.0f), hr * 0.09f)
    val slit = Path().apply {
        moveTo(h.x - hr * 0.75f + dir * hr * 0.06f, h.y - hr * 0.1f)
        lineTo(h.x + dir * hr * 0.06f, h.y + hr * 0.12f)
        lineTo(h.x + hr * 0.75f + dir * hr * 0.06f, h.y - hr * 0.1f)
        lineTo(h.x + hr * 0.75f + dir * hr * 0.06f, h.y + hr * 0.08f)
        lineTo(h.x + dir * hr * 0.06f, h.y + hr * 0.3f)
        lineTo(h.x - hr * 0.75f + dir * hr * 0.06f, h.y + hr * 0.08f)
        close()
    }
    drawPath(slit, Color(0xFF05040A))
    for (side in listOf(-1f, 1f)) {
        val e = Offset(h.x + side * hr * 0.38f + dir * hr * 0.12f, h.y + hr * 0.04f)
        drawCircle(frost.copy(alpha = 0.55f * p), hr * 0.18f, e)
        drawCircle(Color(0xFFCFFAFE), hr * 0.07f, e)
    }

    // Runen-Zweihänder mit Frostleuchten
    val hand = b.hand
    val g = hand + Offset(dir * u * 0.02f, -u * 0.18f)
    val tip = hand + Offset(dir * u * 0.42f, -u * 1.62f)
    drawLine(frost.copy(alpha = 0.15f * p), g, tip, u * 0.22f, StrokeCap.Round)
    artBlade(g, tip, u * 0.11f, Color(0xFFB8C7D9))
    for (k in 0..3) {
        val q = g + (tip - g) * (0.18f + k * 0.17f)
        drawLine(frost.copy(alpha = 0.6f + 0.4f * p), q - Offset(u * 0.04f, 0f), q + Offset(u * 0.04f, -u * 0.05f), u * 0.035f, StrokeCap.Round)
    }
    drawLine(Color(0xFF1F1D2B), hand + Offset(0f, u * 0.2f), g, u * 0.09f, StrokeCap.Round)
    val guardPath = Path().apply {
        moveTo(g.x - u * 0.28f, g.y - u * 0.08f)
        quadraticTo(g.x, g.y + u * 0.06f, g.x + u * 0.28f, g.y - u * 0.08f)
        lineTo(g.x + u * 0.22f, g.y + u * 0.04f)
        quadraticTo(g.x, g.y + u * 0.14f, g.x - u * 0.22f, g.y + u * 0.04f)
        close()
    }
    drawPath(guardPath, Color(0xFF4B4766))
    drawCircle(frost, u * 0.06f, hand + Offset(0f, u * 0.22f))
    artHand(b, hand, plate.bright(0.15f), 0.12f)
    artHand(b, b.offHand, plate.bright(0.15f), 0.12f)
}

// --- Lich ---

private fun DrawScope.drawLich(b: ArtBody) {
    val u = b.u
    val c = b.c
    val dir = b.dir
    val time = b.time
    val robe = Color(0xFF312E81)
    val ice = Color(0xFF7DD3FC)
    val p = b.pulse(3f)
    val hover = sin(time * 2f) * u * 0.05f

    // Kälteaura und Nebel am Boden
    drawCircle(ice.copy(alpha = 0.1f + 0.06f * p), u * 1.3f, c + Offset(0f, -u * 0.25f))
    drawOval(ice.copy(alpha = 0.22f), Offset(c.x - u * 0.7f, b.footY - u * 0.12f), Size(u * 1.4f, u * 0.24f))

    // Hoher Fächerkragen hinter dem Kopf
    val h = b.head + Offset(0f, hover)
    val hr = b.headR
    val collar = Path().apply {
        moveTo(c.x - u * 0.3f, b.shoulderY + hover)
        lineTo(h.x - hr * 1.5f, h.y - hr * 1.2f)
        lineTo(h.x - hr * 0.8f, h.y - hr * 0.7f)
        lineTo(h.x, h.y - hr * 1.5f)
        lineTo(h.x + hr * 0.8f, h.y - hr * 0.7f)
        lineTo(h.x + hr * 1.5f, h.y - hr * 1.2f)
        lineTo(c.x + u * 0.3f, b.shoulderY + hover)
        close()
    }
    drawPath(collar, robe.shade(0.35f))
    drawPath(collar, ice.copy(alpha = 0.7f), style = Stroke(u * 0.035f))

    // Schwebende Robe mit zerfranstem Saum
    val hemY = b.footY - u * 0.12f + hover
    val body = Path().apply {
        moveTo(c.x - u * 0.38f, b.shoulderY - u * 0.05f + hover)
        lineTo(c.x + u * 0.38f, b.shoulderY - u * 0.05f + hover)
        lineTo(c.x + u * 0.64f, hemY - u * 0.06f)
        lineTo(c.x + u * 0.46f, hemY + u * 0.06f)
        lineTo(c.x + u * 0.3f, hemY - u * 0.06f)
        lineTo(c.x + u * 0.12f, hemY + u * 0.08f)
        lineTo(c.x - u * 0.06f, hemY - u * 0.06f)
        lineTo(c.x - u * 0.24f, hemY + u * 0.08f)
        lineTo(c.x - u * 0.42f, hemY - u * 0.04f)
        lineTo(c.x - u * 0.64f, hemY + u * 0.02f)
        close()
    }
    drawPath(body, robe)
    clipPath(body) { drawRect(robe.shade(0.35f), Offset(c.x + u * 0.2f, b.shoulderY - u * 0.2f), Size(u * 0.6f, u * 2f)) }
    // Offene Brust mit Rippen
    val chest = Path().apply {
        moveTo(c.x - u * 0.18f, b.shoulderY - u * 0.04f + hover)
        lineTo(c.x + u * 0.18f, b.shoulderY - u * 0.04f + hover)
        lineTo(c.x, b.hipY + hover)
        close()
    }
    drawPath(chest, Void)
    clipPath(chest) {
        for (i in 0..3) {
            val y = b.shoulderY + u * (0.04f + i * 0.12f) + hover
            drawArc(ArtBone, 200f, 140f, false, Offset(c.x - u * 0.2f, y), Size(u * 0.4f, u * 0.18f), style = Stroke(u * 0.04f))
        }
        drawCircle(ice.copy(alpha = 0.6f * p), u * 0.08f, Offset(c.x, b.shoulderY + u * 0.3f + hover))
    }
    drawPath(chest, ice, style = Stroke(u * 0.035f))
    // Eisblaue Borten
    for (side in listOf(-1f, 1f)) drawLine(ice, Offset(c.x + side * u * 0.08f, b.hipY + hover), Offset(c.x + side * u * 0.2f, hemY - u * 0.02f), u * 0.04f)
    drawLine(Color(0xFF1E1B4B), Offset(c.x - u * 0.4f, b.hipY - u * 0.02f + hover), Offset(c.x + u * 0.4f, b.hipY - u * 0.02f + hover), u * 0.08f)
    artSkull(Offset(c.x, b.hipY - u * 0.04f + hover), u * 0.08f)

    // Ärmel mit Knochenhänden
    val hand = b.hand + Offset(0f, hover)
    val offHand = b.offHand + Offset(0f, hover - u * 0.12f)
    for (hd in listOf(hand, offHand)) {
        val s = if (hd.x > c.x) 1f else -1f
        val sleeve = Path().apply {
            moveTo(c.x + s * u * 0.3f, b.shoulderY + hover)
            lineTo(hd.x + s * u * 0.1f, hd.y - u * 0.18f)
            lineTo(hd.x + s * u * 0.14f, hd.y + u * 0.04f)
            lineTo(hd.x - s * u * 0.2f, hd.y + u * 0.12f)
            lineTo(c.x + s * u * 0.22f, b.shoulderY + u * 0.34f + hover)
            close()
        }
        drawPath(sleeve, robe.bright(0.06f))
        drawLine(ice, Offset(hd.x - s * u * 0.2f, hd.y + u * 0.12f), Offset(hd.x + s * u * 0.14f, hd.y + u * 0.04f), u * 0.04f, StrokeCap.Round)
        artBoneHand(b, hd)
    }
    // Knochen-Schulterstücke mit Zacken
    for (side in listOf(-1f, 1f)) {
        val sp = Offset(c.x + side * u * 0.4f, b.shoulderY + hover)
        for (k in 0..2) {
            val base = sp + Offset(side * (k - 1) * u * 0.1f, -u * 0.06f)
            drawPath(triangle3(base, u * 0.045f, u * (0.18f - kotlin.math.abs(k - 1) * 0.05f)), ArtBone.shade(0.1f))
        }
        drawOval(ArtBone, sp - Offset(u * 0.2f, u * 0.12f), Size(u * 0.4f, u * 0.24f))
        drawArc(BoneShade, 10f, 160f, false, sp - Offset(u * 0.18f, u * 0.1f), Size(u * 0.36f, u * 0.2f), style = Stroke(u * 0.035f))
    }

    // Schädel mit Eiskrone
    val head = ArtBody(Offset(c.x, c.y + hover), u, dir, time)
    headSkull(head, ice, jawDrop = 0.06f)
    val crown = Path().apply {
        moveTo(h.x - hr * 0.85f, h.y - hr * 0.5f)
        lineTo(h.x - hr * 0.95f, h.y - hr * 1.25f)
        lineTo(h.x - hr * 0.5f, h.y - hr * 0.9f)
        lineTo(h.x - hr * 0.25f, h.y - hr * 1.5f)
        lineTo(h.x, h.y - hr * 1.0f)
        lineTo(h.x + hr * 0.25f, h.y - hr * 1.5f)
        lineTo(h.x + hr * 0.5f, h.y - hr * 0.9f)
        lineTo(h.x + hr * 0.95f, h.y - hr * 1.25f)
        lineTo(h.x + hr * 0.85f, h.y - hr * 0.5f)
        close()
    }
    drawPath(crown, Color(0xFFB8C7D9))
    drawPath(crown, Color(0xFF64748B), style = Stroke(hr * 0.06f))
    drawLine(Color(0xFF64748B), Offset(h.x - hr * 0.86f, h.y - hr * 0.6f), Offset(h.x + hr * 0.86f, h.y - hr * 0.6f), hr * 0.12f)
    drawCircle(ice, hr * 0.13f, Offset(h.x, h.y - hr * 0.78f))
    drawCircle(Color.White, hr * 0.05f, Offset(h.x - hr * 0.03f, h.y - hr * 0.82f))

    // Stab mit Schädel und Eiskristall
    val top = hand + Offset(dir * u * 0.02f, -u * 1.3f)
    drawLine(Color(0xFF3F3A52), hand + Offset(-dir * u * 0.02f, u * 0.75f), top, u * 0.08f, StrokeCap.Round)
    for (k in 0..2) drawLine(ice.copy(alpha = 0.7f), hand + Offset(0f, -u * (0.3f + k * 0.3f)), hand + Offset(dir * u * 0.04f, -u * (0.38f + k * 0.3f)), u * 0.04f)
    artSkull(top + Offset(0f, u * 0.06f), u * 0.13f, eyes = ice.shade(0.3f))
    val crystal = top + Offset(0f, -u * 0.3f)
    drawCircle(ice.copy(alpha = 0.35f * p), u * 0.28f, crystal)
    val gem = Path().apply {
        moveTo(crystal.x, crystal.y - u * 0.2f)
        lineTo(crystal.x + u * 0.1f, crystal.y)
        lineTo(crystal.x, crystal.y + u * 0.14f)
        lineTo(crystal.x - u * 0.1f, crystal.y)
        close()
    }
    drawPath(gem, ice)
    drawLine(Color.White, crystal + Offset(-u * 0.03f, -u * 0.12f), crystal + Offset(-u * 0.05f, 0f), u * 0.025f)
    // Frostkugel über der zweiten Hand
    val orb = offHand + Offset(0f, -u * 0.32f)
    drawCircle(ice.copy(alpha = 0.3f * p), u * 0.24f, orb)
    drawCircle(ice.bright(0.3f), u * 0.11f, orb)
    for (i in 0 until 3) {
        val a = time * 3f + i * 2.09f
        drawCircle(Color.White.copy(alpha = 0.8f), u * 0.025f, orb + Offset(kotlin.math.cos(a) * u * 0.22f, sin(a) * u * 0.1f))
    }
}

// --- Schattenklinge ---

private fun DrawScope.drawShadowblade(b: ArtBody) {
    val u = b.u
    val c = b.c
    val dir = b.dir
    val time = b.time
    val skin = Color(0xFFC9C4D9)
    val leather = Color(0xFF1A1625)
    val violet = Color(0xFF8B5CF6)
    val hair = Color(0xFFE2E8F0)
    val h = b.head
    val hr = b.headR
    val p = b.pulse(5f)

    // Hoher Pferdeschwanz hinter dem Kopf
    val swing = sin(time * 4f) * u * 0.06f
    val tail = Path().apply {
        moveTo(h.x - dir * hr * 0.3f, h.y - hr * 0.95f)
        quadraticTo(h.x - dir * hr * 1.5f, h.y - hr * 1.1f, h.x - dir * hr * 1.4f + swing, h.y + hr * 0.6f)
        quadraticTo(h.x - dir * hr * 1.6f + swing, h.y + hr * 1.5f, h.x - dir * hr * 1.1f + swing, h.y + hr * 2.1f)
        quadraticTo(h.x - dir * hr * 1.05f, h.y + hr * 0.6f, h.x - dir * hr * 0.75f, h.y - hr * 0.6f)
        close()
    }
    drawPath(tail, hair.shade(0.12f))
    drawPath(Path().apply {
        moveTo(h.x - dir * hr * 1.05f, h.y - hr * 0.6f)
        quadraticTo(h.x - dir * hr * 1.4f + swing, h.y + hr * 0.6f, h.x - dir * hr * 1.2f + swing, h.y + hr * 1.6f)
    }, hair.shade(0.3f), style = Stroke(hr * 0.06f, cap = StrokeCap.Round))

    // Wehender Schal hinter dem Körper
    val flutter = sin(time * 7f) * u * 0.08f
    val scarf = Path().apply {
        moveTo(c.x - dir * u * 0.15f, b.shoulderY)
        quadraticTo(c.x - dir * u * 0.7f, b.shoulderY + u * 0.1f + flutter, c.x - dir * u * 1.05f, b.shoulderY + u * 0.4f + flutter)
        lineTo(c.x - dir * u * 0.9f, b.shoulderY + u * 0.5f + flutter)
        quadraticTo(c.x - dir * u * 0.6f, b.shoulderY + u * 0.25f, c.x - dir * u * 0.1f, b.shoulderY + u * 0.12f)
        close()
    }
    drawPath(scarf, violet.shade(0.25f))

    // Schlanke Beine in Overknee-Stiefeln
    artFemaleLegs(b, skin, leather, bootHeight = 0.55f)
    // Strumpfband-Riemen
    for (side in listOf(-1f, 1f)) {
        val x = c.x + side * u * 0.17f
        drawLine(violet.shade(0.2f), Offset(x - u * 0.1f, b.footY - u * 0.62f), Offset(x + u * 0.1f, b.footY - u * 0.62f), u * 0.04f)
    }

    // Arme mit Wickelbändern
    artArm(b, b.hand, skin.shade(0.05f), 0.15f, 0.3f)
    artArm(b, b.offHand, skin.shade(0.05f), 0.15f, 0.3f)
    for (hd in listOf(b.hand, b.offHand)) {
        val s = if (hd.x > c.x) 1f else -1f
        val elbow = Offset(c.x + s * u * 0.3f, b.shoulderY + u * 0.1f) + (hd - Offset(c.x + s * u * 0.3f, b.shoulderY + u * 0.1f)) * 0.55f
        drawLine(leather, elbow, hd - (hd - elbow) * 0.15f, u * 0.17f, StrokeCap.Round)
        for (k in 0..2) {
            val q = elbow + (hd - elbow) * (0.2f + k * 0.25f)
            drawLine(violet.shade(0.3f), q - Offset(u * 0.07f, -u * 0.03f), q + Offset(u * 0.07f, -u * 0.03f), u * 0.025f)
        }
    }

    // Knapper Rock mit Schlitzen, freier Bauch und Korsage
    val waistY = b.hipY - u * 0.08f
    val skirt = Path().apply {
        moveTo(c.x - u * 0.24f, b.hipY - u * 0.04f)
        lineTo(c.x + u * 0.24f, b.hipY - u * 0.04f)
        lineTo(c.x + u * 0.38f, b.hipY + u * 0.3f)
        lineTo(c.x + u * 0.22f, b.hipY + u * 0.24f)
        lineTo(c.x + u * 0.08f, b.hipY + u * 0.34f)
        lineTo(c.x - u * 0.06f, b.hipY + u * 0.22f)
        lineTo(c.x - u * 0.22f, b.hipY + u * 0.32f)
        lineTo(c.x - u * 0.38f, b.hipY + u * 0.26f)
        close()
    }
    drawPath(femaleTorso(b, waistY, b.hipY + u * 0.02f), skin)
    drawPath(skirt, leather)
    drawLine(violet, Offset(c.x - u * 0.27f, b.hipY - u * 0.02f), Offset(c.x + u * 0.27f, b.hipY - u * 0.02f), u * 0.06f)
    drawCircle(ArtBone, u * 0.045f, Offset(c.x + dir * u * 0.1f, b.hipY - u * 0.02f))
    // Bauchnabel und leichte Taillenschattierung
    drawCircle(skin.shade(0.25f), u * 0.018f, Offset(c.x, b.hipY - u * 0.14f))
    drawLine(skin.shade(0.12f), Offset(c.x + dir * u * 0.17f, waistY - u * 0.12f), Offset(c.x + dir * u * 0.19f, b.hipY - u * 0.05f), u * 0.03f, StrokeCap.Round)
    // Korsage mit Herzausschnitt
    val bodice = Path().apply {
        moveTo(c.x - u * 0.3f, b.shoulderY + u * 0.1f)
        quadraticTo(c.x - u * 0.15f, b.shoulderY + u * 0.02f, c.x, b.shoulderY + u * 0.14f)
        quadraticTo(c.x + u * 0.15f, b.shoulderY + u * 0.02f, c.x + u * 0.3f, b.shoulderY + u * 0.1f)
        lineTo(c.x + u * 0.27f, waistY - u * 0.12f)
        lineTo(c.x - u * 0.27f, waistY - u * 0.12f)
        close()
    }
    drawPath(bodice, leather)
    clipPath(bodice) { drawRect(violet.copy(alpha = 0.25f), Offset(c.x + u * 0.1f, b.shoulderY), Size(u * 0.3f, u * 0.5f)) }
    drawLine(violet, Offset(c.x, b.shoulderY + u * 0.16f), Offset(c.x, waistY - u * 0.12f), u * 0.025f)
    for (k in 0..1) {
        val y = b.shoulderY + u * (0.22f + k * 0.08f)
        drawLine(violet.bright(0.2f), Offset(c.x - u * 0.05f, y), Offset(c.x + u * 0.05f, y + u * 0.04f), u * 0.02f)
    }
    for (side in listOf(-1f, 1f)) drawLine(leather, Offset(c.x + side * u * 0.22f, b.shoulderY + u * 0.08f), Offset(c.x + side * u * 0.17f, b.shoulderY - u * 0.04f), u * 0.035f)

    // Gesicht: bleich, violett glühende Augen, dunkle Lippen
    artHead(b, skin)
    for (side in listOf(-1f, 1f)) {
        val e = Offset(h.x + side * hr * 0.36f + dir * hr * 0.06f, h.y + hr * 0.08f)
        drawArc(ArtInk, if (side > 0) 200f else 230f, 110f, false, e - Offset(hr * 0.18f, hr * 0.18f), Size(hr * 0.36f, hr * 0.36f), style = Stroke(hr * 0.06f, cap = StrokeCap.Round))
        drawLine(ArtInk, e + Offset(side * hr * 0.13f, -hr * 0.1f), e + Offset(side * hr * 0.26f, -hr * 0.18f), hr * 0.05f, StrokeCap.Round)
        drawCircle(violet.copy(alpha = 0.45f * p), hr * 0.2f, e)
        drawCircle(Color(0xFFEDE9FE), hr * 0.14f, e)
        drawCircle(violet.shade(0.2f), hr * 0.09f, e + Offset(dir * hr * 0.04f, 0f))
        drawLine(hair.shade(0.35f), e + Offset(-hr * 0.15f, -hr * (0.27f - side * 0.04f)), e + Offset(hr * 0.15f, -hr * (0.27f + side * 0.04f)), hr * 0.06f, StrokeCap.Round)
    }
    val mouth = Offset(h.x + dir * hr * 0.06f, h.y + hr * 0.5f)
    drawArc(Color(0xFF581C87), 0f, 180f, true, mouth - Offset(hr * 0.14f, hr * 0.08f), Size(hr * 0.28f, hr * 0.16f))
    drawLine(Color(0xFF3B0764), mouth - Offset(hr * 0.14f, 0f), mouth + Offset(hr * 0.14f, 0f), hr * 0.035f, StrokeCap.Round)
    // Haare: eine Form, straff nach hinten mit Seitenscheitel
    val hairShape = Path().apply {
        moveTo(h.x - hr * 1.0f, h.y + hr * 0.1f)
        cubicTo(h.x - hr * 1.12f, h.y - hr * 1.38f, h.x + hr * 1.12f, h.y - hr * 1.38f, h.x + hr * 1.0f, h.y + hr * 0.1f)
        quadraticTo(h.x + hr * 0.9f, h.y - hr * 0.45f, h.x + dir * hr * 0.2f, h.y - hr * 0.6f)
        quadraticTo(h.x - hr * 0.8f, h.y - hr * 0.5f, h.x - hr * 1.0f, h.y + hr * 0.1f)
        close()
    }
    drawPath(hairShape, hair)
    drawArc(hair.shade(0.2f), 220f, 60f, false, h + Offset(-hr * 0.7f, -hr * 0.95f), Size(hr * 1.4f, hr * 0.9f), style = Stroke(hr * 0.05f, cap = StrokeCap.Round))
    // Zopfband am Hinterkopf
    drawRoundRect(violet, Offset(h.x - dir * hr * 0.98f - hr * 0.12f, h.y - hr * 0.78f), Size(hr * 0.24f, hr * 0.3f), CornerRadius(hr * 0.08f))
    // Seitliche Strähne vorne
    drawPath(Path().apply {
        moveTo(h.x + dir * hr * 0.75f, h.y - hr * 0.45f)
        quadraticTo(h.x + dir * hr * 1.0f, h.y + hr * 0.2f, h.x + dir * hr * 0.82f, h.y + hr * 0.7f)
    }, hair, style = Stroke(hr * 0.16f, cap = StrokeCap.Round))

    // Zwei gebogene Dolche mit violetter Schneide
    for (hd in listOf(b.hand, b.offHand)) {
        val s = if (hd == b.hand) dir else -dir
        val tip = hd + Offset(s * u * 0.32f, -u * 0.5f)
        val dagger = Path().apply {
            moveTo(hd.x - u * 0.04f, hd.y - u * 0.08f)
            quadraticTo(hd.x + s * u * 0.32f, hd.y - u * 0.2f, tip.x, tip.y)
            quadraticTo(hd.x + s * u * 0.12f, hd.y - u * 0.26f, hd.x + u * 0.04f, hd.y - u * 0.1f)
            close()
        }
        drawPath(dagger, Color(0xFFD1D5DB))
        drawPath(Path().apply {
            moveTo(hd.x, hd.y - u * 0.1f)
            quadraticTo(hd.x + s * u * 0.3f, hd.y - u * 0.2f, tip.x, tip.y)
        }, violet.copy(alpha = 0.6f + 0.4f * p), style = Stroke(u * 0.03f, cap = StrokeCap.Round))
        drawLine(violet.shade(0.3f), hd + Offset(-u * 0.11f, -u * 0.06f), hd + Offset(u * 0.11f, -u * 0.06f), u * 0.05f, StrokeCap.Round)
        artHand(b, hd, skin.shade(0.08f), 0.09f)
    }
}
