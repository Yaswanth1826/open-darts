package com.opendarts.session

import com.opendarts.game.model.DartHit
import com.opendarts.geometry.Point
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CameraBullOffTest {
    private val inner = DartHit(25, com.opendarts.game.model.Multiplier.DOUBLE)
    private val setup = GameSetup(playerNames = listOf("Ann", "Bo"), input = InputMode.CAMERA)

    private fun session(): GameSession = GameSession().also { assert(it.start(setup)); it.beginBullOff() }

    @Test fun theCloserDartToTheBullCentreThrowsFirst() {
        val s = session()
        s.throwDart(inner, fromCamera = true, spot = Point(9.0, 0.0))
        s.throwDart(inner, fromCamera = true, spot = Point(2.0, 1.0))
        assertEquals(1, s.bullOff!!.winner)
        s.dartsPulled()
        assertNull(s.bullOff)
        assertEquals("Bo", s.game!!.currentPlayer.name)
    }

    @Test fun theGameDoesNotStartUntilTheDartsAreOut() {
        val s = session()
        s.throwDart(inner, fromCamera = true, spot = Point(2.0, 0.0))
        s.dartsPulled()
        assertNotNull(s.bullOff)
        assertNull(s.bullOff!!.winner)
    }

    @Test fun keyedDartsAndTurnButtonsDoNothingDuringTheBullOff() {
        val s = session()
        s.throwDart(inner)
        s.nextPlayer()
        s.undo()
        assertEquals(0, s.bullOff!!.tosses.size)
        assertEquals("Ann", s.game!!.currentPlayer.name)
    }

    @Test fun startingTheGameMakesTheCameraForgetTheBullOffDarts() {
        val s = session()
        val forgotten = mutableListOf<Point>()
        s.onForget = { forgotten += it }
        s.throwDart(inner, fromCamera = true, spot = Point(9.0, 0.0))
        s.throwDart(inner, fromCamera = true, spot = Point(2.0, 1.0))
        s.bullOffStart(forget = true)
        assertEquals(2, forgotten.size)
        assertNull(s.bullOff)
        assertEquals("Bo", s.game!!.currentPlayer.name)
    }

    @Test fun aRematchStartsStraightAway() {
        val s = session()
        s.throwDart(inner, fromCamera = true, spot = Point(9.0, 0.0))
        s.throwDart(inner, fromCamera = true, spot = Point(2.0, 1.0))
        s.bullOffStart(forget = false)
        s.rematch()
        assertNull(s.bullOff)
    }

    @Test fun theBotThrowsItsOwnDart() {
        val s = GameSession().also { it.start(setup.copy(playerNames = listOf("Ann", "Bot Club"), botLevel = 60)); it.beginBullOff() }
        s.throwDart(inner, fromCamera = true, spot = Point(30.0, 0.0))
        s.bullOffBot()
        assertEquals(2, s.bullOff!!.tosses.size)
    }
}
