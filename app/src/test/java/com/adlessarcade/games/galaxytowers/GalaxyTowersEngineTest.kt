package com.adlessarcade.games.galaxytowers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GalaxyTowersEngineTest {

    private val E = GalaxyTowersEngine

    /** Spiel mit vorgegebenen Turmkarten (Rest egal), Ablage [top] und Nachziehstapel [stock]. */
    private fun state(tower: List<Card?>, top: Card, stock: List<Card> = emptyList(), round: Int = 1) =
        GameState(seed = 1L, round = round, tower = tower, waste = listOf(top), stock = stock)

    /** Nur die Karten an [cards] liegen noch im Turm. */
    private fun towerWith(vararg cards: Pair<Int, Card>): List<Card?> =
        MutableList<Card?>(E.TOWER_SIZE) { null }.also { t -> cards.forEach { (i, c) -> t[i] = c } }

    @Test
    fun newGame_dealsAllCardsExactlyOnce() {
        val g = E.newGame(42L)
        assertEquals(E.TOWER_SIZE, g.tower.size)
        assertEquals(1, g.waste.size)
        assertEquals(E.STOCK_SIZE, g.stock.size)
        val all = g.tower.filterNotNull() + g.waste + g.stock
        assertEquals(52, all.toSet().size)
    }

    @Test
    fun onlyBottomRowIsOpenAtStart() {
        val g = E.newGame(42L)
        val open = (0 until E.TOWER_SIZE).filter { E.isFree(g.tower, it) }
        assertEquals((18 until 28).toList(), open)
    }

    @Test
    fun coverage_matchesThreePeaks() {
        assertEquals(listOf(3, 4), E.coveredBy[0])
        assertEquals(listOf(7, 8), E.coveredBy[2])
        assertEquals(listOf(9, 10), E.coveredBy[3])
        assertEquals(listOf(16, 17), E.coveredBy[8])
        assertEquals(listOf(26, 27), E.coveredBy[17])
        // Jede Karte liegt mittig über ihren beiden Abdeckern
        (0 until 18).forEach { i ->
            val (x, _) = E.slotPosition(i)
            val (a, _) = E.slotPosition(E.coveredBy[i][0])
            val (b, _) = E.slotPosition(E.coveredBy[i][1])
            assertEquals(x, (a + b) / 2f, 0.001f)
        }
    }

    @Test
    fun fits_oneUpOrDown_withWrapAround() {
        assertTrue(E.fits(Card(5, 0), Card(6, 1)))
        assertTrue(E.fits(Card(7, 0), Card(6, 1)))
        assertFalse(E.fits(Card(8, 0), Card(6, 1)))
        assertFalse(E.fits(Card(6, 0), Card(6, 1)))
        assertTrue(E.fits(Card(1, 0), Card(13, 1)))
        assertTrue(E.fits(Card(13, 0), Card(1, 1)))
        assertTrue(E.fits(Card(9, 0), Card.JOKER))
    }

    @Test
    fun play_revealsCoveredCardAndScoresChain() {
        // Karte 9 (Reihe 2) wird von 18 und 19 verdeckt
        val g = state(towerWith(9 to Card(4, 0), 18 to Card(6, 0), 19 to Card(5, 1), 27 to Card(13, 3)), top = Card(7, 2))
        assertFalse(E.isFree(g.tower, 9))
        val first = E.play(g, 18)!!
        assertEquals(100, first.points)
        val second = E.play(first.state, 19)!!
        assertEquals(200, second.points)
        assertTrue(E.isFree(second.state.tower, 9))
        val third = E.play(second.state, 9)!!
        assertEquals(300, third.points)
        assertEquals(3, third.state.streak)
        assertEquals(600, third.state.score)
    }

    @Test
    fun play_rejectsCoveredOrNonMatchingCards() {
        val g = state(towerWith(9 to Card(6, 0), 18 to Card(2, 0), 19 to Card(5, 1)), top = Card(7, 2))
        assertNull(E.play(g, 9)) // verdeckt
        assertNull(E.play(g, 18)) // passt nicht
    }

    @Test
    fun draw_breaksChain() {
        val g = state(towerWith(18 to Card(6, 0), 27 to Card(1, 0)), top = Card(7, 2), stock = listOf(Card(10, 1)))
        val played = E.play(g, 18)!!.state
        assertEquals(1, played.streak)
        val drawn = E.draw(played)!!
        assertEquals(0, drawn.streak)
        assertEquals(Card(10, 1), drawn.top)
        assertTrue(drawn.stock.isEmpty())
        assertNull(E.draw(drawn))
    }

    @Test
    fun peakAndRoundBonus_scaleWithRound() {
        val g = state(towerWith(0 to Card(6, 0)), top = Card(7, 2), stock = listOf(Card(1, 0), Card(2, 0)), round = 3)
        val r = E.play(g, 0)!!
        assertTrue(r.peakCleared)
        assertTrue(r.roundCleared)
        val mult = E.multiplier(3)
        assertEquals(((E.CARD_POINTS + E.PEAK_BONUS) * mult).toInt(), r.points)
        assertEquals(((E.CLEAR_BONUS + 2 * E.STOCK_CARD_BONUS) * mult).toInt(), r.clearBonus)
        assertEquals(E.roundCoins(3), r.coins)
        assertEquals(RoundEnd.CLEARED, r.state.roundOver)

        val next = E.nextRound(r.state)
        assertEquals(4, next.round)
        assertNull(next.roundOver)
        assertEquals(r.state.score, next.score)
        assertEquals(E.TOWER_SIZE, next.tower.filterNotNull().size)
    }

    @Test
    fun stuckAndHopeless() {
        val g = state(towerWith(18 to Card(2, 0)), top = Card(7, 2))
        assertTrue(E.isStuck(g))
        assertFalse(E.isHopeless(g)) // Joker und Warp sind noch da
        val noCharges = g.copy(charges = PowerUp.entries.associateWith { 0 })
        assertTrue(E.isHopeless(noCharges))
    }

    @Test
    fun joker_acceptsAnyCardAndKeepsChain() {
        val g = state(towerWith(18 to Card(2, 0), 19 to Card(8, 0)), top = Card(7, 2))
        val first = E.play(g, 19)!!.state
        val joker = E.use(first, PowerUp.JOKER)!!
        assertEquals(0, joker.charges(PowerUp.JOKER))
        assertTrue(joker.top!!.isJoker)
        val r = E.play(joker, 18)!!
        assertEquals(2, r.state.streak)
        assertNull(E.use(r.state, PowerUp.JOKER)) // keine Ladung mehr
    }

    @Test
    fun undo_restoresPreviousMove() {
        val g = state(towerWith(18 to Card(6, 0), 27 to Card(1, 0)), top = Card(7, 2))
        val played = E.play(g, 18)!!.state
        val undone = E.use(played, PowerUp.UNDO)!!
        assertEquals(g.tower, undone.tower)
        assertEquals(g.waste, undone.waste)
        assertEquals(0, undone.score)
        assertEquals(0, undone.streak)
        assertEquals(0, undone.charges(PowerUp.UNDO))
        assertNull(E.use(g, PowerUp.UNDO)) // ohne Verlauf nicht möglich
    }

    @Test
    fun scan_onlyWithHiddenCards_andEndsOnNextMove() {
        val g = E.newGame(7L)
        val scanned = E.use(g, PowerUp.SCAN)!!
        assertTrue(scanned.scanActive)
        assertNull(E.use(scanned, PowerUp.SCAN))
        assertFalse(E.draw(scanned)!!.scanActive)
        val allOpen = state(towerWith(18 to Card(2, 0)), top = Card(7, 2))
        assertNull(E.use(allOpen, PowerUp.SCAN))
    }

    @Test
    fun warp_addsCardsDeterministically() {
        val g = E.newGame(99L)
        val a = E.use(g, PowerUp.WARP)!!
        val b = E.use(g, PowerUp.WARP)!!
        assertEquals(g.stock.size + E.WARP_CARDS, a.stock.size)
        assertEquals(a.stock, b.stock)
    }

    @Test
    fun streakReward_grantsCharge() {
        // Kette 9 bereits gelegt, die 10. Karte gibt eine Ladung
        val g = state(towerWith(18 to Card(6, 0), 27 to Card(1, 0)), top = Card(7, 2)).copy(streak = E.REWARD_STREAK - 1)
        val r = E.play(g, 18)!!
        assertNotNull(r.reward)
        assertEquals(2, r.state.charges(r.reward!!))
    }

    @Test
    fun sameSeed_dealsSameRounds() {
        assertEquals(E.newGame(20261017L), E.newGame(20261017L))
        val a = E.nextRound(E.newGame(5L).copy(roundOver = RoundEnd.CLEARED))
        val b = E.nextRound(E.newGame(5L).copy(roundOver = RoundEnd.CLEARED))
        assertEquals(a.tower, b.tower)
    }

    @Test
    fun modes_haveRoundsAndShrinkingTime() {
        val classic = E.newGame(1L, GalaxyTowersMode.CLASSIC)
        assertEquals(90f, classic.timeLeft, 0.001f)
        assertEquals(45f, GalaxyTowersMode.CLASSIC.roundTime(10), 0.001f)
        assertEquals(10, GalaxyTowersMode.CLASSIC.rounds)
        assertEquals(1, GalaxyTowersMode.SINGLE.rounds)
        assertEquals(80f, E.newGame(1L, GalaxyTowersMode.SINGLE).timeLeft, 0.001f)
        assertFalse(GalaxyTowersMode.TIMELESS.timed)
        assertFalse(GalaxyTowersMode.DAILY.canBuy)
        val round2 = E.nextRound(E.endRound(classic, RoundEnd.STUCK))
        assertEquals(85f, round2.timeLeft, 0.001f)
    }

    @Test
    fun tick_endsRoundWhenTimeRunsOut_butNotInTimeless() {
        val g = E.newGame(1L, GalaxyTowersMode.SINGLE)
        val half = E.tick(g, 30f)
        assertEquals(50f, half.timeLeft, 0.001f)
        assertNull(half.roundOver)
        val over = E.tick(half, 60f)
        assertEquals(RoundEnd.TIME_UP, over.roundOver)
        assertNull(E.draw(over)) // nach Rundenende keine Züge mehr
        val timeless = E.newGame(1L, GalaxyTowersMode.TIMELESS)
        assertEquals(timeless, E.tick(timeless, 500f))
    }

    @Test
    fun nextRound_worksWithoutClearing_andEndsAfterLastRound() {
        var g = E.newGame(2L, GalaxyTowersMode.CLASSIC)
        repeat(9) {
            g = E.nextRound(E.endRound(g, RoundEnd.STUCK))
            assertFalse(g.isGameOver)
        }
        assertEquals(10, g.round)
        assertTrue(g.isLastRound)
        val end = E.nextRound(E.tick(g, 1000f))
        assertTrue(end.isGameOver)
        assertTrue(E.nextRound(E.endRound(E.newGame(2L, GalaxyTowersMode.SINGLE), RoundEnd.STUCK)).isGameOver)
    }

    @Test
    fun clearing_givesTimeBonus() {
        val g = state(towerWith(0 to Card(6, 0)), top = Card(7, 2)).copy(timeLeft = 12.7f)
        val r = E.play(g, 0)!!
        assertEquals(12 * E.TIME_BONUS_PER_SECOND, r.timeBonus)
        assertEquals(RoundEnd.CLEARED, r.state.roundOver)
        assertEquals(1, r.state.roundsCleared)
    }

    @Test
    fun secondPile_opensAtChainOfThree_drawingFromStock() {
        val g = state(
            towerWith(18 to Card(6, 0), 19 to Card(5, 1), 20 to Card(4, 2), 21 to Card(10, 0), 27 to Card(1, 3)),
            top = Card(7, 2),
            stock = listOf(Card(9, 3), Card(2, 2)),
        )
        val two = E.play(E.play(g, 18)!!.state, 19)!!.state
        assertNull(two.waste2)
        val three = E.play(two, 20)!!
        assertTrue(three.secondPileOpened)
        assertEquals(listOf(Card(9, 3)), three.state.waste2)
        assertEquals(listOf(Card(2, 2)), three.state.stock)
        // Die 10 passt nur auf die zweite Ablage (9), nicht auf die 4
        val onSecond = E.play(three.state, 21)!!
        assertEquals(1, onSecond.pile)
        assertEquals(Card(10, 0), onSecond.state.top2)
        assertEquals(4, onSecond.state.streak)
        // Zeitsprung stellt die zweite Ablage mit her, Nachziehen schließt sie
        assertEquals(three.state.waste2, E.use(onSecond.state, PowerUp.UNDO)!!.waste2)
        assertNull(E.draw(onSecond.state)!!.waste2)
    }

    @Test
    fun secondPile_staysClosedWithoutStock_andMainPileHasPriority() {
        val g = state(towerWith(18 to Card(6, 0), 19 to Card(5, 1), 20 to Card(4, 2), 27 to Card(1, 3)), top = Card(7, 2))
        val three = E.play(E.play(E.play(g, 18)!!.state, 19)!!.state, 20)!!
        assertFalse(three.secondPileOpened)
        assertNull(three.state.waste2)
        // Passt auf beide Ablagen → Hauptablage
        val both = state(towerWith(18 to Card(6, 0)), top = Card(7, 2)).copy(waste2 = listOf(Card(5, 0)))
        assertEquals(0, E.play(both, 18)!!.pile)
    }

    @Test
    fun save_roundTrip() {
        val g = E.play(E.newGame(3L).let { s -> s.copy(waste = listOf(Card.JOKER)) }, 20)!!.state
        val decoded = GalaxyTowersSaves.decode(GalaxyTowersSaves.encode(g, bestAtStart = 1234))!!
        assertEquals(g, decoded.game)
        assertEquals(1234, decoded.bestAtStart)
        assertNull(GalaxyTowersSaves.decode(GalaxyTowersSaves.encode(E.endGame(g), 0)))
        assertNull(GalaxyTowersSaves.decode("kaputt"))
    }
}
