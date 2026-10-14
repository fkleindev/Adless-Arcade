package com.adlessarcade.core

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.saveGameStore by preferencesDataStore(name = "savegames")

/** Speichert pro Spiel und Modus einen unterbrochenen Lauf (als JSON) plus dessen Punktestand für die Menüanzeige. */
class SaveGameRepository(private val context: Context) {

    private val writes = WriteQueue()

    private fun jsonKey(gameId: String, modeId: String) = stringPreferencesKey("${gameId}_${modeId}_json")
    private fun scoreKey(gameId: String, modeId: String) = intPreferencesKey("${gameId}_${modeId}_score")

    private val data = context.saveGameStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }

    /** Punktestand des gespeicherten Laufs oder null, wenn keiner existiert. */
    fun savedScore(gameId: String, modeId: String): Flow<Int?> =
        data.map { prefs ->
            if (prefs[jsonKey(gameId, modeId)] == null) null else prefs[scoreKey(gameId, modeId)] ?: 0
        }

    suspend fun load(gameId: String, modeId: String): String? {
        writes.flush() // sonst könnte ein veralteter Stand gelesen werden
        return data.first()[jsonKey(gameId, modeId)]
    }

    fun save(gameId: String, modeId: String, json: String, score: Int) {
        writes.enqueue {
            context.saveGameStore.edit {
                it[jsonKey(gameId, modeId)] = json
                it[scoreKey(gameId, modeId)] = score
            }
        }
    }

    /** Löscht den Spielstand; der zurückgegebene Job ist fertig, sobald die Löschung geschrieben wurde. */
    fun clear(gameId: String, modeId: String): Job = writes.enqueue {
        context.saveGameStore.edit {
            it.remove(jsonKey(gameId, modeId))
            it.remove(scoreKey(gameId, modeId))
        }
    }
}
