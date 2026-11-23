package com.opendarts.session

import com.opendarts.game.model.DartHit
import com.opendarts.game.model.Multiplier
import com.opendarts.geometry.Point
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraBullOffTest {
    private val bull = DartHit(25, Multiplier.DOUBLE)
    private val setup = GameSetup(playerNames = listOf("Ann", "Bo"), input = InputMode.CAMERA)

    private fun session(): GameSession = GameSession().also { check(it.start(setup)); it.beginBullOff() }

    private fun GameSession.throwAt(x: Double, y: Double) { throwDart(bull, fromCamera = true, spot = Point(x, y)) }

    @Test fun eachDartIsTakenOutBeforeTheNextPlayerThrowsAndTheClosestStarts() {
        val s = session()
        s.throwAt(9.0, 0.0)
        assertTrue(s.bullOffWait)
        s.dartsPulled()
        assertFalse(s.bullOffWait)
        s.throwAt(2.0, 1.0)
        assertEquals(1, s.bullOff!!.winner)
        assertNotNull(s.bullOff)
        s.dartsPulled()
        assertNull(s.bullOff)
        assertEquals("Bo", s.game!!.currentPlayer.name)
    }

    @Test fun aDartThrownWhileTheLastOneIsStillInTheBoardIsNotCounted() {
        val s = session()
        s.throwAt(9.0, 0.0)
        s.throwAt(2.0, 1.0)
        assertEquals(setOf(0), s.bullOff!!.tosses.keys)
        s.dartsPulled()
        s.throwAt(2.0, 1.0)
        assertEquals(setOf(0, 1), s.bullOff!!.tosses.keys)
    }

    @Test fun theGameDoesNotStartUntilTheLastDartIsOut() {
        val s = session()
        s.throwAt(9.0, 0.0)
        s.dartsPulled()
        s.throwAt(2.0, 0.0)
        assertEquals(1, s.bullOff!!.winner)
        assertNotNull(s.bullOff)
    }

    @Test fun theDartOutButtonWorksWhenTheCameraMissedTheRemoval() {
        val s = session()
        s.throwAt(9.0, 0.0)
        s.bullOffDartOut()
        assertFalse(s.bullOffWait)
        s.throwAt(2.0, 0.0)
        s.bullOffDartOut()
        assertNull(s.bullOff)
        assertEquals("Bo", s.game!!.currentPlayer.name)
    }

    @Test fun keyedDartsAndTurnButtonsDoNothingDuringTheBullOff() {
        val s = session()
        s.throwDart(bull)
        s.nextPlayer()
        s.undo()
        assertEquals(0, s.bullOff!!.tosses.size)
        assertEquals("Ann", s.game!!.currentPlayer.name)
    }

    @Test fun startingTheGameMakesTheCameraForgetADartStillInTheBoard() {
        val s = session()
        val forgotten = mutableListOf<Point>()
        s.onForget = { forgotten += it }
        s.throwAt(9.0, 0.0)
        s.dartsPulled()
        s.throwAt(2.0, 1.0)
        s.bullOffStart(forget = true)
        assertEquals(2, forgotten.size)
        assertNull(s.bullOff)
        assertEquals("Bo", s.game!!.currentPlayer.name)
    }

    @Test fun takingBackTheDartLetsThePlayerThrowAgain() {
        val s = session()
        s.throwAt(9.0, 0.0)
        s.bullOffUndo()
        assertFalse(s.bullOffWait)
        s.throwAt(3.0, 0.0)
        assertEquals(1, s.bullOff!!.tosses.size)
    }

    @Test fun aRematchStartsStraightAway() {
        val s = session()
        s.throwAt(9.0, 0.0)
        s.dartsPulled()
        s.throwAt(2.0, 1.0)
        s.bullOffStart(forget = false)
        s.rematch()
        assertNull(s.bullOff)
    }

    @Test fun theBotThrowsItsOwnDartOnceTheHumansIsOut() {
        val s = GameSession().also { it.start(setup.copy(playerNames = listOf("Ann", "Bot Club"), botLevel = 60)); it.beginBullOff() }
        s.throwAt(30.0, 0.0)
        s.bullOffBot()
        assertEquals(1, s.bullOff!!.tosses.size)
        s.dartsPulled()
        s.bullOffBot()
        assertEquals(2, s.bullOff!!.tosses.size)
    }
}
