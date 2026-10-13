package com.adlessarcade.core

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.highscoreStore by preferencesDataStore(name = "highscores")

/** Lokale Highscores pro Spiel und Modus. */
class HighscoreRepository(private val context: Context) {

    private fun key(gameId: String, modeId: String): Preferences.Key<Int> =
        intPreferencesKey("${gameId}_$modeId")

    fun highscore(gameId: String, modeId: String): Flow<Int> =
        context.highscoreStore.data
            .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
            .map { it[key(gameId, modeId)] ?: 0 }

    /** Speichert [score], falls er höher als der bisherige Rekord ist. */
    suspend fun submit(gameId: String, modeId: String, score: Int) {
        context.highscoreStore.edit { prefs ->
            val key = key(gameId, modeId)
            if (score > (prefs[key] ?: 0)) prefs[key] = score
        }
    }
}
