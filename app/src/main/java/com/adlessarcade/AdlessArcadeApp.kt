package com.adlessarcade

import android.app.Application
import android.content.Context
import com.adlessarcade.core.HighscoreRepository

class AdlessArcadeApp : Application() {
    val highscores: HighscoreRepository by lazy { HighscoreRepository(this) }
}

val Context.arcadeApp: AdlessArcadeApp get() = applicationContext as AdlessArcadeApp
