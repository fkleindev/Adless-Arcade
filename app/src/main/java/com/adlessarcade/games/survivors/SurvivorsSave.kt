package com.adlessarcade.games.survivors

import com.adlessarcade.core.SaveJson
import kotlinx.serialization.Serializable

/** Ein unterbrochener Lauf inkl. Rekord zu Rundenbeginn. */
@Serializable
class SurvivorsSave(val version: Int, val state: SurvivorsState, val bestAtStart: Int = 0)

object SurvivorsSaves {
    const val VERSION = 2
    /** Ältere Versionen, die dank Standardwerten weiter geladen werden können. */
    private val COMPATIBLE = setOf(1, VERSION)

    fun encode(state: SurvivorsState, bestAtStart: Int): String =
        SaveJson.encodeToString(SurvivorsSave.serializer(), SurvivorsSave(VERSION, state, bestAtStart))

    /** Liefert null bei defekten, inkompatiblen oder bereits beendeten Läufen. */
    fun decode(json: String): SurvivorsSave? {
        val save = runCatching { SaveJson.decodeFromString(SurvivorsSave.serializer(), json) }.getOrNull() ?: return null
        val s = save.state
        return if (save.version in COMPATIBLE && !s.dead && !s.victory && s.hp > 0f) save else null
    }
}
