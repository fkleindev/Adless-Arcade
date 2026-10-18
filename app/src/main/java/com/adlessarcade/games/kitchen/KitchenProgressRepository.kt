package com.adlessarcade.games.kitchen

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.adlessarcade.core.SaveJson
import com.adlessarcade.core.WriteQueue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import java.io.IOException

private val Context.kitchenStore by preferencesDataStore(name = "kitchen_progress")

/** Beste Sternzahl pro Kampagnen-Level. */
class KitchenProgressRepository(private val context: Context) {
    private val writes = WriteQueue()
    private val starsKey = stringPreferencesKey("stars")
    private val serializer = MapSerializer(String.serializer(), Int.serializer())

    val stars: Flow<Map<String, Int>> = context.kitchenStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs -> prefs[starsKey]?.let { runCatching { SaveJson.decodeFromString(serializer, it) }.getOrNull() } ?: emptyMap() }

    /** Speichert das Ergebnis, wenn es besser ist als das bisherige. */
    fun record(levelId: String, stars: Int) {
        if (stars <= 0) return
        writes.enqueue {
            context.kitchenStore.edit { prefs ->
                val current = prefs[starsKey]?.let { runCatching { SaveJson.decodeFromString(serializer, it) }.getOrNull() } ?: emptyMap()
                if ((current[levelId] ?: 0) < stars) {
                    prefs[starsKey] = SaveJson.encodeToString(serializer, current + (levelId to stars))
                }
            }
        }
    }
}

object KitchenProgress {
    /** Level frei, wenn es das erste ist oder das vorherige mindestens einen Stern hat. */
    fun isUnlocked(level: LevelDef, stars: Map<String, Int>): Boolean {
        if (!level.restaurant.available) return false
        val index = KitchenCatalog.levels.indexOf(level)
        return index <= 0 || (stars[KitchenCatalog.levels[index - 1].id] ?: 0) > 0
    }

    fun totalStars(stars: Map<String, Int>): Int = KitchenCatalog.levels.sumOf { stars[it.id] ?: 0 }
}
