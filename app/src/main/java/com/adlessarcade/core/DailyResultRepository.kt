package com.adlessarcade.core

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import java.io.IOException

private val Context.dailyResultStore by preferencesDataStore(name = "daily_results")

/** Beendeter Lauf einer Täglichen Challenge. */
@Serializable
data class DailyResult(
    /** Tagesschlüssel, z. B. „20261020“. */
    val day: String,
    val score: Int,
    /** Ergebniszeile für Menü und Teilen, z. B. „🏆 12.340 Punkte · ⏱ 3:42“. */
    val summary: String,
)

/** Merkt sich pro Spiel, Modus und Tag den einen gewerteten Lauf – danach ist die Challenge für den Tag gesperrt. */
class DailyResultRepository(private val context: Context) {

    private fun key(gameId: String, modeId: String, day: String) = stringPreferencesKey("${gameId}_${modeId}_$day")

    private val data = context.dailyResultStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }

    fun result(gameId: String, modeId: String, day: String = DailyChallenge.todayKey()): Flow<DailyResult?> =
        data.map { prefs ->
            prefs[key(gameId, modeId, day)]?.let { json ->
                runCatching { SaveJson.decodeFromString(DailyResult.serializer(), json) }.getOrNull()
            }
        }

    suspend fun hasPlayed(gameId: String, modeId: String, day: String = DailyChallenge.todayKey()): Boolean =
        result(gameId, modeId, day).first() != null

    /** Speichert das Ergebnis; ein bereits gewerteter Lauf desselben Tages bleibt bestehen. */
    suspend fun record(gameId: String, modeId: String, result: DailyResult) {
        try {
            context.dailyResultStore.edit { prefs ->
                val key = key(gameId, modeId, result.day)
                if (prefs[key] == null) prefs[key] = SaveJson.encodeToString(DailyResult.serializer(), result)
            }
        } catch (e: IOException) {
            // Ohne gespeichertes Ergebnis bleibt die Challenge offen – lieber das als ein verlorener Lauf.
        }
    }
}

/** Öffnet den Android-Teilen-Dialog mit dem Ergebnis. */
fun shareDailyResult(context: Context, gameTitle: String, result: DailyResult) {
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_TEXT, DailyChallenge.shareText(gameTitle, result))
    try {
        context.startActivity(Intent.createChooser(send, "Ergebnis teilen"))
    } catch (e: ActivityNotFoundException) {
        // Keine App zum Teilen installiert.
    }
}
