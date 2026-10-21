package com.adlessarcade.games.runetactics

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.sin

/**
 * Zeichnet eine Figur stehend in einem Feld. [center] ist die Feldmitte, [size] die Feldgröße.
 * [dir] = Blickrichtung (+1 rechts, −1 links), [lunge] ein Versatz beim Angriff, [flash] 0..1 für Treffer.
 * Das Aussehen jeder Einheit liegt in den RuneTacticsArt*-Dateien.
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
    // Phasenversatz pro Einheit, damit nicht alle im Gleichtakt wippen.
    val phase = (def.id.hashCode() and 0xFF) / 40f + center.x * 0.013f
    val bob = sin(time * 3.2f + phase) * size * 0.02f
    val c = center + lunge + Offset(0f, bob)
    val pivot = center + Offset(0f, size * 0.32f)
    rotate(if (stunned) 12f * dir else 0f, pivot) {
        drawUnitArt(def, c, size, time, dir, alpha, flash)
    }
}
