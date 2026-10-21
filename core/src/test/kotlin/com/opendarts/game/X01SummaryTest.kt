package com.opendarts.game

import com.opendarts.game.model.DartHit
import com.opendarts.game.model.Player
import com.opendarts.game.x01.X01Config
import com.opendarts.game.x01.X01Game
import com.opendarts.game.x01.summaries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class X01SummaryTest {
    private val two = listOf(Player(0, "A"), Player(1, "B"))

    @Test fun countsHundredsOneFortiesAndOneEightiesOncePerVisit() {
        val g = X01Game(two, X01Config(501))
        repeat(3) { g.throwDart(DartHit.treble(20)) }
        g.endTurn()
        listOf(DartHit.treble(20), DartHit.treble(20), DartHit.single(20)).forEach { g.throwDart(it) }
        g.endTurn()
        listOf(DartHit.treble(20), DartHit.single(20), DartHit.single(20)).forEach { g.throwDart(it) }
        g.endTurn()
        val (a, b) = g.summaries()
        assertEquals(1, a.oneEighties)
        assertEquals(1, a.oneFortyPlus)
        assertEquals(2, a.hundredPlus)
        assertEquals(0, b.oneEighties)
        assertEquals(1, b.oneFortyPlus)
        assertEquals(1, b.hundredPlus)
    }

    @Test fun aVisitCutShortOrBustedIsCountedForWhatItScored() {
        val g = X01Game(two, X01Config(501))
        g.throwDart(DartHit.treble(20)); g.throwDart(DartHit.treble(20))
        g.endTurn()
        assertEquals(1, g.summaries()[0].hundredPlus)
        val busted = X01Game(two, X01Config(101))
        busted.throwDart(DartHit.treble(20)); busted.throwDart(DartHit.treble(20))
        assertEquals(0, busted.summaries()[0].hundredPlus)
    }

    @Test fun anEditedVisitIsCountedAsItEndedUp() {
        val g = X01Game(two, X01Config(501))
        repeat(3) { g.throwDart(DartHit.treble(20)) }
        g.editDart(2, DartHit.single(1))
        assertEquals(0, g.summaries()[0].oneEighties)
        assertEquals(0, g.summaries()[0].oneFortyPlus)
        assertEquals(1, g.summaries()[0].hundredPlus)
        g.editDart(2, DartHit.treble(20))
        assertEquals(1, g.summaries()[0].oneEighties)
    }

    @Test fun checkoutPercentIsFinishesOverDartsThatCouldHaveFinished() {
        val g = X01Game(two, X01Config(80))
        g.throwDart(DartHit.treble(20))     // 20 left
        g.throwDart(DartHit.single(10))     // 10 left: a double 5 would win
        g.throwDart(DartHit.MISS)           // a dart at a finish, missed
        g.endTurn(); g.endTurn()
        g.throwDart(DartHit.double(5))      // wins from 10
        val a = g.summaries()[0]
        assertEquals(3, g.gameState.players[0].finishingDarts)
        assertEquals(33, a.checkoutPercent)
        assertTrue(a.won)
        assertEquals(1, a.legs)
        assertNull(g.summaries()[1].checkoutPercent)
    }

    @Test fun averageDartsAndBestVisitComeFromTheEngine() {
        val g = X01Game(two, X01Config(501))
        repeat(3) { g.throwDart(DartHit.single(20)) }
        val a = g.summaries()[0]
        assertEquals(60.0, a.average, 0.001)
        assertEquals(3, a.darts)
        assertEquals(60, a.highestVisit)
    }
}
