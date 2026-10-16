package com.adlessarcade.games.survivors

import kotlin.math.roundToInt

/**
 * Items aus Kisten: beliebig oft stapelbar, jeder weitere Stapel wirkt etwas schwächer
 * (Stapel k bringt [base] / (1 + 0,2·(k−1))). Die Summe wächst unbegrenzt, aber immer langsamer.
 */
enum class ItemKind(val title: String, val icon: String, val base: Float) {
    CRIT_CHANCE("Adlerauge", "🎯", 0.12f),
    CRIT_DAMAGE("Henkersklinge", "💥", 0.25f),
    POISON("Giftphiole", "☠", 0.25f),
    LIFESTEAL("Vampirzahn", "🩸", 0.02f),
    SPARK("Funkenstein", "⚡", 0.06f),
    EMBER("Glutkern", "☄", 0.12f),
    FROST("Frostsplitter", "❄", 0.05f),
    EXECUTE("Henkersmal", "💀", 0.06f),
    DODGE("Schattenumhang", "🪶", 0.05f),
    AMULET("Bollwerk-Amulett", "🧿", 0.08f),
    HOURGLASS("Sanduhr", "⏳", 0.04f),
    GIANT_SLAYER("Riesentöter", "👑", 0.12f),
    BERSERK("Berserkerblut", "😡", 0.5f),
    GOLD("Goldene Gans", "💰", 0.10f);

    /** Wirkung des [k]-ten Stapels (ab 1). */
    fun stackValue(k: Int): Float = base / (1f + DECAY * (k - 1))

    /** Gesamtwirkung bei [stacks] Stapeln. */
    fun total(stacks: Int): Float {
        var sum = 0f
        for (k in 1..stacks) sum += stackValue(k)
        return sum
    }

    /** Beschreibung des nächsten Stapels (bei [owned] vorhandenen). */
    fun description(owned: Int): String {
        val p = percent(stackValue(owned + 1))
        return when (this) {
            CRIT_CHANCE -> "+$p % Krit-Chance. Über 100 %: Chance auf Superkrits (×2)."
            CRIT_DAMAGE -> "+$p % Krit-Schaden."
            POISON -> "Treffer vergiften: +$p % des Schadens als Gift über 3 s."
            LIFESTEAL -> "+$p % Lebensraub."
            SPARK -> "+$p % Chance: Treffer springt als Blitz auf 2 Gegner über."
            EMBER -> if (owned == 0 || HeroStats.EMBER_RADIUS_PER_STACK * (owned - 1) >= HeroStats.EMBER_MAX_RADIUS_BONUS) {
                "+$p % Chance: Getötete Gegner explodieren."
            } else {
                "+$p % Chance: Getötete Gegner explodieren. Explosion ${percent(HeroStats.EMBER_RADIUS_PER_STACK)} % größer."
            }
            FROST -> "+$p % Chance, Gegner 1 s einzufrieren."
            EXECUTE -> "+$p % Hinrichtungsgrenze: Gegner (außer Bosse) darunter sterben sofort."
            DODGE -> "+$p % Ausweichen (höchstens ${percent(MAX_DODGE)} %)."
            AMULET -> "+$p % maximales Leben."
            HOURGLASS -> "−$p % Abklingzeit."
            GIANT_SLAYER -> "+$p % Schaden gegen Bosse und Elite."
            BERSERK -> "Bis zu +$p % Schaden, je weniger Leben du hast."
            GOLD -> "+$p % Coins und Erfahrung."
        }
    }

    companion object {
        const val DECAY = 0.2f
        const val MAX_DODGE = 0.6f
        const val MAX_EXECUTE = 0.3f
        const val MAX_COOLDOWN_REDUCTION = 0.6f

        fun percent(value: Float): String {
            val p = value * 100f
            return if (p < 10f) ((p * 10f).roundToInt() / 10f).toString().replace('.', ',').removeSuffix(",0")
            else p.roundToInt().toString()
        }
    }
}
