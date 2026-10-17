package com.adlessarcade.core.shop

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.adlessarcade.core.WriteQueue
import com.adlessarcade.core.achievements.Achievement
import com.adlessarcade.core.achievements.Achievements
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.profileStore by preferencesDataStore(name = "profile")

data class ProfileState(
    val coins: Int = 0,
    val owned: Set<String> = emptySet(),
    val equippedIds: Map<ShopCategory, String> = emptyMap(),
) {
    fun isOwned(item: ShopItem): Boolean = ShopLogic.isOwned(owned, item)
    fun equipped(category: ShopCategory): ShopItem = ShopLogic.equipped(category, equippedIds, owned)
    fun hasAchievement(achievement: Achievement): Boolean = achievement.titleId in owned
}

/** Coins, freigeschaltete Shop-Artikel und ausgerüstete Kosmetik. */
class ProfileRepository(private val context: Context) {

    private val writes = WriteQueue()
    private val coinsKey = intPreferencesKey("coins")
    private val ownedKey = stringSetPreferencesKey("owned")
    private fun equippedKey(category: ShopCategory) = stringPreferencesKey("equipped_${category.name}")

    val state: Flow<ProfileState> = context.profileStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it.toProfile() }

    private fun Preferences.toProfile() = ProfileState(
        coins = this[coinsKey] ?: 0,
        owned = this[ownedKey] ?: emptySet(),
        equippedIds = ShopCategory.entries.mapNotNull { c -> this[equippedKey(c)]?.let { c to it } }.toMap(),
    )

    fun addCoins(amount: Int) {
        if (amount <= 0) return
        writes.enqueue {
            context.profileStore.edit { it[coinsKey] = (it[coinsKey] ?: 0) + amount }
        }
    }

    /** Zieht [amount] Coins ab (z. B. für Power-ups im Spiel). Liefert false bei zu wenig Coins oder Speicherfehler. */
    suspend fun spendCoins(amount: Int): Boolean {
        if (amount <= 0) return false
        writes.flush()
        var success = false
        try {
            context.profileStore.edit { prefs ->
                val coins = prefs[coinsKey] ?: 0
                if (coins < amount) return@edit
                prefs[coinsKey] = coins - amount
                success = true
            }
        } catch (e: IOException) {
            success = false
        }
        return success
    }

    /** Kauft [item] und rüstet es direkt aus. Liefert false bei zu wenig Coins oder Speicherfehler. */
    suspend fun purchase(item: ShopItem): Boolean {
        writes.flush() // ausstehende Coin-Gutschriften zuerst verbuchen
        var success = false
        try {
            context.profileStore.edit { prefs ->
                val wallet = Wallet(prefs[coinsKey] ?: 0, prefs[ownedKey] ?: emptySet())
                val updated = ShopLogic.buy(wallet, item) ?: return@edit
                prefs[coinsKey] = updated.coins
                prefs[ownedKey] = updated.owned
                prefs[equippedKey(item.category)] = item.id
                success = true
            }
        } catch (e: IOException) {
            success = false
        }
        return success
    }

    private val _unlocked = MutableSharedFlow<Achievement>(extraBufferCapacity = 16)

    /** Neu freigeschaltete Erfolge (für die Einblendung). */
    val unlocked: SharedFlow<Achievement> = _unlocked

    /**
     * Schaltet einen Erfolg samt Titel frei. Bereits vorhandene Erfolge werden ignoriert; abgeleitete Erfolge
     * (z. B. „Allrounder“) werden im selben Schritt mit geprüft.
     */
    fun unlockAchievement(id: String) {
        val achievement = Achievements.find(id) ?: return
        writes.enqueue {
            val fresh = ArrayList<Achievement>()
            context.profileStore.edit { prefs ->
                fresh.clear()
                val owned = (prefs[ownedKey] ?: emptySet()).toMutableSet()
                if (owned.add(achievement.titleId)) fresh += achievement
                val unlockedIds = Achievements.all.filter { it.titleId in owned }.map { it.id }.toSet()
                Achievements.derived(unlockedIds).forEach { if (owned.add(it.titleId)) fresh += it }
                if (fresh.isNotEmpty()) prefs[ownedKey] = owned
            }
            fresh.forEach { _unlocked.tryEmit(it) }
        }
    }

    /** Besessene Artikel und ausgerüstete IDs direkt aus dem Speicher (für Migrationen). */
    suspend fun rawOwnedAndEquipped(): Pair<Set<String>, Map<String, String>> {
        writes.flush()
        var result: Pair<Set<String>, Map<String, String>> = emptySet<String>() to emptyMap()
        context.profileStore.edit { prefs ->
            val equipped = prefs.asMap().mapNotNull { (key, value) ->
                if (key.name.startsWith("equipped_") && value is String) key.name.removePrefix("equipped_") to value else null
            }.toMap()
            result = (prefs[ownedKey] ?: emptySet()) to equipped
        }
        return result
    }

    /**
     * Entfernt nicht mehr existierende Artikel (z. B. alte Survivors-Klassen und -Welten) samt Ausrüstung und
     * schreibt [refundFor] gekaufte davon gut – in einem Schritt, damit nichts doppelt erstattet wird.
     */
    suspend fun removeItems(ids: Set<String>, equippedCategories: Set<String>, refundFor: Map<String, Int>): Int {
        writes.flush()
        var refunded = 0
        try {
            context.profileStore.edit { prefs ->
                val owned = prefs[ownedKey] ?: emptySet()
                refunded = refundFor.filterKeys { it in owned }.values.sum()
                prefs[ownedKey] = owned - ids
                prefs[coinsKey] = (prefs[coinsKey] ?: 0) + refunded
                equippedCategories.forEach { prefs.remove(stringPreferencesKey("equipped_$it")) }
            }
        } catch (e: IOException) {
            refunded = 0
        }
        return refunded
    }

    fun equip(item: ShopItem) {
        writes.enqueue {
            context.profileStore.edit { prefs ->
                val owned = prefs[ownedKey] ?: emptySet()
                if (ShopLogic.isOwned(owned, item)) prefs[equippedKey(item.category)] = item.id
            }
        }
    }
}
