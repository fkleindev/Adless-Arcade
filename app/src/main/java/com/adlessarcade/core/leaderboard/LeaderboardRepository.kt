package com.adlessarcade.core.leaderboard

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.adlessarcade.core.SaveJson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import java.io.IOException

private val Context.leaderboardStore by preferencesDataStore(name = "leaderboards")

/** Lokale Bestenlisten (Top 10) pro Spiel, Modus und ggf. Variante. */
class LeaderboardRepository(private val context: Context) {

    private val serializer = ListSerializer(LeaderboardEntry.serializer())
    private val migratedKey = booleanPreferencesKey("legacy_migrated")

    private fun key(board: LeaderboardKey) = stringPreferencesKey(board.storageKey)
    private fun lastVariantKey(gameId: String) = stringPreferencesKey("last_variant_$gameId")

    /** Zuletzt gespielte Variante (z. B. Klasse) eines Spiels – Vorauswahl in der Bestenliste. */
    fun lastVariant(gameId: String): Flow<String?> =
        context.leaderboardStore.data
            .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
            .map { it[lastVariantKey(gameId)] }

    private fun Preferences.entries(board: LeaderboardKey): List<LeaderboardEntry> =
        this[key(board)]?.let { json -> runCatching { SaveJson.decodeFromString(serializer, json) }.getOrNull() }
            ?: emptyList()

    fun entries(board: LeaderboardKey): Flow<List<LeaderboardEntry>> =
        context.leaderboardStore.data
            .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
            .map { it.entries(board) }

    /** Trägt einen beendeten Lauf ein; liefert den erreichten Platz oder null (nicht in den Top 10). */
    suspend fun submit(board: LeaderboardKey, entry: LeaderboardEntry): Int? {
        var rank: Int? = null
        try {
            context.leaderboardStore.edit { prefs ->
                val (updated, place) = Leaderboards.insert(prefs.entries(board), entry)
                rank = place
                if (place != null) prefs[key(board)] = SaveJson.encodeToString(serializer, updated)
                board.variant?.let { prefs[lastVariantKey(board.gameId)] = it }
            }
        } catch (e: IOException) {
            return null
        }
        return rank
    }

    /**
     * Übernimmt einmalig die bisherigen Rekorde (vor Einführung der Bestenlisten) als ersten Eintrag,
     * damit niemand mit leeren Listen startet.
     */
    suspend fun migrateLegacy(records: Map<LeaderboardKey, Int>) {
        try {
            context.leaderboardStore.edit { prefs ->
                if (prefs[migratedKey] == true) return@edit
                records.forEach { (board, score) ->
                    if (score > 0 && prefs.entries(board).isEmpty()) {
                        val entry = LeaderboardEntry(score, timestamp = 0L, details = "Rekord aus früheren Versionen")
                        prefs[key(board)] = SaveJson.encodeToString(serializer, listOf(entry))
                    }
                }
                prefs[migratedKey] = true
            }
        } catch (e: IOException) {
            // Beim nächsten Start erneut versuchen.
        }
    }
}
