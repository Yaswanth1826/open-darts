package com.opendarts.session

import com.opendarts.game.model.DartHit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SelectPlayerTest {
    private fun session(vararg names: String) = GameSession().also { it.start(GameSetup(playerNames = names.toList(), input = InputMode.MANUAL)) }

    @Test fun tappingTheNextPlayerHandsTheThrowOver() {
        val s = session("Ann", "Bo", "Cy")
        s.throwDart(DartHit.single(20))
        s.selectPlayer(1)
        assertEquals("Bo", s.game!!.currentPlayer.name)
    }

    @Test fun theCurrentPlayerAndPlayersFurtherOnCannotBeTapped() {
        val s = session("Ann", "Bo", "Cy")
        s.throwDart(DartHit.single(20))
        s.selectPlayer(0)
        s.selectPlayer(2)
        assertEquals("Ann", s.game!!.currentPlayer.name)
        assertEquals(1, s.game!!.turnHits.size)
    }

    @Test fun atTheStartOfATurnThePreviousPlayerIsTheWayBackToTheirDarts() {
        val s = session("Ann", "Bo", "Cy")
        s.throwDart(DartHit.single(20)); s.selectPlayer(1)
        s.selectPlayer(0)
        assertEquals("Ann", s.game!!.currentPlayer.name)
        // Handing over closes the visit, so the darts not thrown were recorded as misses; they can be corrected.
        assertEquals(DartHit.single(20), s.game!!.turnHits.first())
    }

    @Test fun withTwoPlayersTheOtherPlayerIsBackAtTheStartOfATurnAndNextOnceDartsAreIn() {
        val s = session("Ann", "Bo")
        s.throwDart(DartHit.single(20)); s.selectPlayer(1)
        s.selectPlayer(0)
        assertEquals("Ann", s.game!!.currentPlayer.name)
        s.selectPlayer(1)
        assertEquals("Bo", s.game!!.currentPlayer.name)
        s.throwDart(DartHit.single(5))
        s.selectPlayer(0)
        assertEquals("Ann", s.game!!.currentPlayer.name)
    }

    @Test fun atTheVeryStartTheOtherPlayerIsSimplyNext() {
        val s = session("Ann", "Bo")
        s.selectPlayer(1)
        assertEquals("Bo", s.game!!.currentPlayer.name)
        assertTrue(s.game!!.canGoBack())
    }
}
