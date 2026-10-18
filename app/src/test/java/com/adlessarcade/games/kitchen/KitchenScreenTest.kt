package com.adlessarcade.games.kitchen

import android.content.ComponentName
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.junit.runners.model.Statement
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/** Oberflächen-Test: Zutaten per Ziehen verarbeiten, servieren – Punkte und Einblendung erscheinen. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w800dp-h400dp-land")
class KitchenScreenTest {
    /** Meldet die leere Test-Activity an, ohne ui-test-manifest ins Debug-APK zu bringen. */
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

    private lateinit var vm: KitchenViewModel

    private fun advance(seconds: Float) {
        rule.mainClock.advanceTimeBy((seconds * 1000).toLong())
    }

    private fun start() {
        rule.mainClock.autoAdvance = false // Der Game-Loop läuft endlos; Zeit wird von Hand vorgespult.
        rule.setContent { KitchenScreen(onExit = {}) }
        vm = ViewModelProvider(rule.activity)[KitchenViewModel::class.java]
        // Laden läuft über DataStore im IO-Thread – dafür echte Zeit abwarten
        val deadline = System.currentTimeMillis() + 15_000
        while ((!vm.loaded || vm.layout == null) && System.currentTimeMillis() < deadline) {
            advance(0.05f)
            Thread.sleep(10)
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
        }
        rule.runOnUiThread { vm.startEndless() }
        advance(0.3f)
    }

    private val engine get() = vm.engine!!
    private val layout get() = vm.layout!!

    /** Zieht mit dem Finger von einer Station zur anderen. */
    private fun drag(from: Station, to: Station) {
        val a = layout.rectOf(from).center
        val b = layout.rectOf(to).center
        rule.onNodeWithTag(KITCHEN_CANVAS_TAG).performTouchInput {
            down(a)
            for (i in 1..10) {
                moveTo(a + (b - a) * (i / 10f))
                advanceEventTime(16)
            }
            up()
        }
        advance(0.1f)
    }

    /** Löst das laufende Minispiel mit den Gesten-Helfern aus dem Logik-Test. */
    private fun solveRunning() {
        var guard = 0
        while (engine.busy && guard++ < 20) {
            val game = engine.miniGame ?: run { advance(0.05f); null } ?: continue
            rule.runOnUiThread { solve(game) { engine.update(it) } }
            advance(0.1f)
        }
    }

    @Test
    fun dragIngredients_cookAndServe_updatesScore() {
        start()
        rule.runOnUiThread {
            engine.orders.clear()
            engine.orders += Order(500, "fried_egg", 300f, 300f)
        }
        drag(Station.Crate(ItemKind.EGG), Station.Stove(0))
        assertTrue("Ziehen aufs Herd startet das Aufschlagen", engine.busy && engine.miniGame is Crack)
        solveRunning()
        assertEquals(ItemKind.EGG, engine.pans[0].item?.kind)
        // Garen lassen (6 s Garzeit) und herausnehmen
        var guard = 0
        while (engine.pans[0].progress < 1.05f && guard++ < 200) advance(0.1f)
        drag(Station.Stove(0), Station.PlateSpot(0))
        assertTrue("Teller startet das Würzen", engine.busy)
        solveRunning()
        assertEquals("fried_egg", engine.plates[0].dishId)
        drag(Station.PlateSpot(0), Station.Serve)
        advance(0.2f)
        assertEquals(1, engine.served)
        assertTrue(engine.score > 0)
        rule.onNodeWithText("${engine.score}").assertExists()
        rule.onNodeWithText("Spiegelei serviert!").assertExists()
    }

    @Test
    fun wrongDrop_showsHint() {
        start()
        drag(Station.Crate(ItemKind.MILK), Station.Board)
        rule.onNodeWithText("Milch muss nicht aufs Brett").assertExists()
        assertTrue(!engine.busy)
    }
}
