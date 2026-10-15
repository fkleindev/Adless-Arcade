package com.adlessarcade.core.achievements

import com.adlessarcade.core.shop.ProfileRepository

/**
 * Meldet Erfolge aus einem laufenden Spiel. Jeder Erfolg wird pro Bildschirm nur einmal an das Profil
 * weitergereicht, damit Bedingungen ruhig jeden Frame geprüft werden können.
 */
class AchievementTracker(private val profile: ProfileRepository) {
    private val reported = HashSet<String>()

    fun unlock(id: String) {
        if (reported.add(id)) profile.unlockAchievement(id)
    }
}
