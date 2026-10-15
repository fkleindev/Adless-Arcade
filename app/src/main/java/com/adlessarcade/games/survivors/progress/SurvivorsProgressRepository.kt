package com.adlessarcade.games.survivors.progress

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.adlessarcade.core.SaveJson
import com.adlessarcade.core.WriteQueue
import com.adlessarcade.games.survivors.SurvivorsStage
import com.adlessarcade.games.survivors.WeaponKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import java.io.IOException

private val Context.survivorsStore by preferencesDataStore(name = "survivors_progress")

/** Runen, Freischaltungen der Runen-Schmiede, gewählte Klasse und Zähler für Kriterien. */
class SurvivorsProgressRepository(private val context: Context) {

    private val writes = WriteQueue()
    private val runesKey = intPreferencesKey("runes")
    private val unlockedKey = stringSetPreferencesKey("unlocked")
    private val selectedKey = stringPreferencesKey("selected_class")
    private val stageKey = stringPreferencesKey("selected_stage")
    private val statsKey = stringPreferencesKey("stats")
    private val migratedKey = booleanPreferencesKey("legacy_migrated")
    private val statsSerializer = MapSerializer(String.serializer(), Int.serializer())

    val state: Flow<SurvivorsProgress> = context.survivorsStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it.toProgress() }

    private fun Preferences.toProgress() = SurvivorsProgress(
        runes = this[runesKey] ?: 0,
        unlocked = this[unlockedKey] ?: emptySet(),
        selectedClass = this[selectedKey],
        selectedStage = this[stageKey],
        stats = stats(),
    )

    private fun Preferences.stats(): Map<String, Int> =
        this[statsKey]?.let { runCatching { SaveJson.decodeFromString(statsSerializer, it) }.getOrNull() } ?: emptyMap()

    fun addRunes(amount: Int) {
        if (amount <= 0) return
        writes.enqueue {
            context.survivorsStore.edit { it[runesKey] = (it[runesKey] ?: 0) + amount }
        }
    }

    fun select(classId: String) {
        writes.enqueue {
            context.survivorsStore.edit { it[selectedKey] = classId }
        }
    }

    fun selectStage(stage: SurvivorsStage) {
        writes.enqueue {
            context.survivorsStore.edit { it[stageKey] = stage.id }
        }
    }

    /** Zähler eines beendeten Laufs verbuchen (Sieg, Bosse, Zeit). */
    fun recordRun(victory: Boolean, bossesKilled: Int, seconds: Int, stage: SurvivorsStage = SurvivorsStage.MEADOW) {
        writes.enqueue {
            context.survivorsStore.edit { prefs ->
                val updated = UnlockLogic.afterRun(prefs.stats(), victory, bossesKilled, seconds, stage)
                prefs[statsKey] = SaveJson.encodeToString(statsSerializer, updated)
            }
        }
    }

    /** Kills pro Waffe aufaddieren (Voraussetzung für neue Waffen). */
    fun addWeaponKills(kills: Map<WeaponKind, Int>) {
        if (kills.values.none { it > 0 }) return
        writes.enqueue {
            context.survivorsStore.edit { prefs ->
                val stats = prefs.stats().toMutableMap()
                kills.forEach { (weapon, n) -> if (n > 0) stats.merge(SurvivorsStats.weaponKills(weapon), n, Int::plus) }
                prefs[statsKey] = SaveJson.encodeToString(statsSerializer, stats)
            }
        }
    }

    /** Schaltet [unlock] frei (Runen abziehen, atomar). Liefert false bei zu wenig Runen, offenen Kriterien oder Fehler. */
    suspend fun purchase(unlock: SurvivorsUnlock): Boolean {
        writes.flush() // ausstehende Runen-Gutschriften zuerst verbuchen
        var success = false
        try {
            context.survivorsStore.edit { prefs ->
                val updated = UnlockLogic.buy(prefs.toProgress(), unlock) ?: return@edit
                prefs[runesKey] = updated.runes
                prefs[unlockedKey] = updated.unlocked
                if (unlock.kind == UnlockKind.CLASS) prefs[selectedKey] = unlock.id
                if (unlock.kind == UnlockKind.STAGE) SurvivorsStage.entries.firstOrNull { it.unlockId == unlock.id }?.let { prefs[stageKey] = it.id }
                success = true
            }
        } catch (e: IOException) {
            success = false
        }
        return success
    }

    /** Einmalige Übernahme der früher mit Gold gekauften Klassen; liefert true, wenn sie jetzt durchgeführt wurde. */
    suspend fun migrateLegacy(migration: LegacyMigration): Boolean {
        var applied = false
        context.survivorsStore.edit { prefs ->
            if (prefs[migratedKey] == true) return@edit
            prefs[unlockedKey] = (prefs[unlockedKey] ?: emptySet()) + migration.unlockedClasses
            if (prefs[selectedKey] == null) migration.selectedClass?.let { prefs[selectedKey] = it }
            prefs[migratedKey] = true
            applied = true
        }
        return applied
    }
}
