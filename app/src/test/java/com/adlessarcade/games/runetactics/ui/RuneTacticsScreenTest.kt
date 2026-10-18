package com.adlessarcade.games.runetactics.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelProvider
import com.adlessarcade.games.runetactics.Economy
import com.adlessarcade.games.runetactics.RuneTacticsScreen
import com.adlessarcade.games.runetactics.RuneTacticsViewModel
import org.junit.Assert.assertNotEquals
import android.content.ComponentName
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Rule
import org.junit.rules.TestRule
import org.junit.runners.model.Statement
import org.robolectric.Shadows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Oberflächen-Test: Kaufen, Würfeln und XP müssen Kopf- und Fußleiste sofort aktualisieren. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RuneTacticsScreenTest {
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

    private fun start(): RuneTacticsViewModel {
        rule.mainClock.autoAdvance = false // Der Game-Loop läuft endlos; Zeit wird von Hand vorgespult.
        rule.setContent { RuneTacticsScreen(onExit = {}) }
        val vm = ViewModelProvider(rule.activity)[RuneTacticsViewModel::class.java]
        var guard = 0
        while (!vm.loaded && guard++ < 100) rule.mainClock.advanceTimeBy(100)
        // Frischer Lauf, unabhängig von gespeicherten Ständen früherer Tests
        rule.runOnUiThread { vm.restart() }
        rule.mainClock.advanceTimeBy(300)
        return vm
    }

    private fun tap(text: String) {
        rule.onAllNodesWithText(text)[0].performClick()
        rule.mainClock.advanceTimeBy(300)
    }

    @Test
    fun buyAndReroll_updateGoldAndShop() {
        val vm = start()
        val gold = vm.engine.gold
        rule.onNodeWithText("● $gold").assertExists()
        val cost = vm.engine.shop.filterNotNull().minOf { com.adlessarcade.games.runetactics.RuneTacticsUnits.byId(it).cost }
        tap("● $cost")
        rule.onNodeWithText("● ${gold - cost}").assertExists()
        val shopBefore = vm.engine.shop.toList()
        tap("↻ Würfeln · ${Economy.REROLL_COST}")
        rule.onNodeWithText("● ${gold - cost - Economy.REROLL_COST}").assertExists()
        assertNotEquals(shopBefore, vm.engine.shop.toList())
        vm.engine.shop.filterNotNull().forEach { id ->
            rule.onAllNodesWithText(com.adlessarcade.games.runetactics.RuneTacticsUnits.byId(id).name)[0].assertExists()
        }
    }

    @Test
    fun buyXp_updatesLevel() {
        val vm = start()
        val gold = vm.engine.gold
        val level = vm.engine.level
        tap("▲ XP · ${Economy.XP_COST}")
        rule.onNodeWithText("● ${gold - Economy.XP_COST}").assertExists()
        rule.onNodeWithText("Lv ${vm.engine.level} · ${vm.engine.board.size}/${vm.engine.boardCap}").assertExists()
        assertNotEquals(level, vm.engine.level)
    }
}
