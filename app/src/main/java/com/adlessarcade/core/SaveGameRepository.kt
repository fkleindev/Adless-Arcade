package com.adlessarcade.core

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.IOException

private val Context.saveGameStore by preferencesDataStore(name = "savegames")

/**
 * Speichert pro Spiel und Modus einen unterbrochenen Lauf (als JSON) plus dessen Punktestand für die Menüanzeige.
 * Schreibvorgänge laufen in einem App-weiten Scope, damit sie auch beim Schließen eines Screens fertig werden.
 */
class SaveGameRepository(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Schreibvorgänge strikt in Aufrufreihenfolge (z. B. "letzter Zug speichern" vor "bei Game Over löschen"). */
    private val writes = Channel<suspend () -> Unit>(Channel.UNLIMITED).also { channel ->
        scope.launch { for (write in channel) write() }
    }

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
        // Erst alle ausstehenden Schreibvorgänge abwarten, sonst könnte ein veralteter Stand gelesen werden.
        val flushed = Job()
        writes.trySend { flushed.complete() }
        flushed.join()
        return data.first()[jsonKey(gameId, modeId)]
    }

    fun save(gameId: String, modeId: String, json: String, score: Int) {
        enqueue {
            context.saveGameStore.edit {
                it[jsonKey(gameId, modeId)] = json
                it[scoreKey(gameId, modeId)] = score
            }
        }
    }

    /** Löscht den Spielstand; der zurückgegebene Job ist fertig, sobald die Löschung geschrieben wurde. */
    fun clear(gameId: String, modeId: String): Job {
        val done = Job()
        enqueue {
            context.saveGameStore.edit {
                it.remove(jsonKey(gameId, modeId))
                it.remove(scoreKey(gameId, modeId))
            }
            done.complete()
        }
        return done
    }

    private fun enqueue(write: suspend () -> Unit) {
        writes.trySend {
            try {
                write()
            } catch (e: IOException) {
                // Speichern ist "best effort" – ein Fehler darf das Spiel nicht abstürzen lassen.
            }
        }
    }
}
