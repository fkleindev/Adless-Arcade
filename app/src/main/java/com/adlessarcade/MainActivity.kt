package com.adlessarcade

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.adlessarcade.core.shop.ProfileState
import com.adlessarcade.navigation.AppNavHost
import com.adlessarcade.ui.components.AchievementToastHost
import com.adlessarcade.ui.theme.AdlessArcadeTheme
import com.adlessarcade.ui.theme.ArcadeBackground
import com.adlessarcade.ui.theme.CosmeticRegistry
import com.adlessarcade.ui.theme.LocalCosmetics

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        val profileFlow = arcadeApp.profile.state
        setContent {
            val profile: ProfileState? by profileFlow.collectAsState(initial = null)
            val loaded = profile
            if (loaded == null) {
                // Kurz leer lassen, bis das Profil geladen ist – verhindert ein Aufblitzen des Standard-Themes.
                Box(Modifier.fillMaxSize().background(ArcadeBackground))
                return@setContent
            }
            val cosmetics = remember(loaded) { CosmeticRegistry.from(loaded) }
            CompositionLocalProvider(LocalCosmetics provides cosmetics) {
                AdlessArcadeTheme(cosmetics.palette) {
                    Box(Modifier.fillMaxSize()) {
                        AppNavHost()
                        AchievementToastHost(arcadeApp.profile.unlocked)
                    }
                }
            }
        }
    }
}
