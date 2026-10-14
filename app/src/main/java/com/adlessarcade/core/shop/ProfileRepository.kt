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

    fun equip(item: ShopItem) {
        writes.enqueue {
            context.profileStore.edit { prefs ->
                val owned = prefs[ownedKey] ?: emptySet()
                if (ShopLogic.isOwned(owned, item)) prefs[equippedKey(item.category)] = item.id
            }
        }
    }
}
