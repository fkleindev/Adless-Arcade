package com.adlessarcade

import android.app.Application
import android.content.Context
import com.adlessarcade.core.HighscoreRepository
import com.adlessarcade.core.SaveGameRepository

class AdlessArcadeApp : Application() {
    val highscores: HighscoreRepository by lazy { HighscoreRepository(this) }
    val saves: SaveGameRepository by lazy { SaveGameRepository(this) }
}

val Context.arcadeApp: AdlessArcadeApp get() = applicationContext as AdlessArcadeApp
