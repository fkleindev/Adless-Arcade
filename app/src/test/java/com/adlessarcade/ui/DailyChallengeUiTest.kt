package com.adlessarcade.ui

import android.content.ComponentName
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.adlessarcade.core.DailyResult
import com.adlessarcade.core.GameRegistry
import com.adlessarcade.games.gaterush.GateRush
import com.adlessarcade.ui.components.GameOverOverlay
import com.adlessarcade.ui.components.ModeCard
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.junit.runners.model.Statement
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/** Oberflächen-Test der Täglichen Challenge: Teilen statt Nochmal spielen, gesperrte Karte im Menü. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DailyChallengeUiTest {
    /** Meldet die leere Test-Activity an, ohne ui-test-manifest (das würde sie ins Debug-APK bringen). */
    @get:Rule(order = 0)
    val registerActivity = TestRule { base, _ ->
        object : Statement() {
            override fun evaluate() {
                val app = ApplicationProvider.getApplicationContext<Context>()
                Shadows.shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
                base.evaluate()
            }
        }
    }

    @get:Rule(order = 1)
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val result = DailyResult("20261020", 12340, "🏆 12.340 Punkte · ⏱ 3:42")

    @Test
    fun gameOver_daily_showsShareChip_withoutPlayAgain() {
        var shared = 0
        rule.setContent {
            GameOverOverlay(
                visible = true,
                score = 12340,
                best = 12340,
                isNewBest = true,
                onPlayAgain = null,
                onMenu = {},
                onShare = { shared++ },
            )
        }
        rule.onNodeWithText("Nochmal spielen").assertDoesNotExist()
        rule.onNodeWithText("Zum Menü").assertExists()
        rule.onNodeWithText("Teilen").performClick()
        assertEquals(1, shared)
    }

    @Test
    fun modeCard_playedToday_showsResultAndCountdown() {
        val game = GameRegistry.find(GateRush.GAME_ID)!!
        val mode = game.modes.first { it.daily }
        var clicks = 0
        rule.setContent {
            ModeCard(mode, game, best = 12340, onClick = { clicks++ }, dailyResult = result, nextChallengeIn = "5 h 12 min")
        }
        rule.onNodeWithText("Heute gespielt · ${result.summary}").assertExists()
        rule.onNodeWithText("Neue Challenge in 5 h 12 min").assertExists()
        rule.onNodeWithText("BEST").assertDoesNotExist()
        rule.onNodeWithText(mode.title).performClick()
        assertEquals(1, clicks) // öffnet im Menü das Ergebnis
    }
}
