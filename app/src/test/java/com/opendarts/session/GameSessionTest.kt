package com.opendarts.session

import com.opendarts.game.model.DartHit
import com.opendarts.game.model.Multiplier
import com.opendarts.geometry.Point
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameSessionTest {
    private val t20 = DartHit(20, Multiplier.TRIPLE)
    private val s5 = DartHit(5, Multiplier.SINGLE)
    private val spotA = Point(5.0, 100.0)
    private val spotB = Point(-40.0, 20.0)

    private fun session() = GameSession().also { it.start(GameSetup(input = InputMode.CAMERA)) }

    @Test
    fun removingACameraDartCanAskTheCameraToLookBehindIt() {
        val s = session()
        s.throwDart(t20, fromCamera = true, spot = spotA)
        s.throwDart(s5, fromCamera = true, spot = spotB)
        assertTrue(s.canLookBehind(1))
        assertEquals(spotB, s.editDart(1, null, lookBehind = true))
        assertEquals(listOf(t20), s.game!!.turnHits)
    }

    @Test
    fun aPlainRemovalDoesNotReleaseTheSpot() {
        val s = session()
        s.throwDart(t20, fromCamera = true, spot = spotA)
        assertNull(s.editDart(0, null))
        assertTrue(s.game!!.turnHits.isEmpty())
    }

    @Test
    fun keyedInDartsHaveNothingToLookBehind() {
        val s = session()
        s.throwDart(t20)
        assertFalse(s.canLookBehind(0))
    }

    @Test
    fun aDartBehindAnotherScoresTheSameAndKeepsTheOrder() {
        val s = session()
        s.throwDart(t20, fromCamera = true, spot = spotA)
        s.throwDart(s5, fromCamera = true, spot = spotB)
        s.dartBehind(2, 1)
        assertEquals(listOf(t20, s5, s5), s.game!!.turnHits)
        assertFalse("the hidden dart is already counted", s.canLookBehind(1))

        s.editDart(1, null)
        assertEquals(listOf(t20, s5), s.game!!.turnHits)
        assertTrue("the hidden dart is now the one the camera tracks", s.canLookBehind(1))
        assertEquals(spotB, s.editDart(1, null, lookBehind = true))
    }

    @Test
    fun aNewTurnForgetsTheSpots() {
        val s = session()
        s.throwDart(t20, fromCamera = true, spot = spotA)
        s.nextPlayer()
        assertFalse(s.canLookBehind(0))
        s.throwDart(s5, fromCamera = true, spot = spotB)
        assertEquals(spotB, s.editDart(0, null, lookBehind = true))
    }

    private fun bustedPlayerAtTwoDarts(): GameSession {
        val s = GameSession().also { it.start(GameSetup(input = InputMode.CAMERA, x01 = com.opendarts.game.x01.X01Config(101))) }
        s.throwDart(t20, fromCamera = true, spot = spotA)
        s.throwDart(t20, fromCamera = true, spot = spotB)
        return s
    }

    @Test
    fun aDartTheCameraSeesAfterABustIsNotScoredForTheNextPlayer() {
        val s = bustedPlayerAtTwoDarts()
        s.throwDart(s5, fromCamera = true, spot = spotA)
        val g = s.game!!
        assertEquals(0, g.currentPlayerIndex)
        assertEquals(2, g.turnHits.size)
    }

    @Test
    fun afterTheBoardIsClearedTheNextPlayersDartsScoreNormally() {
        val s = bustedPlayerAtTwoDarts()
        s.cameraDartsRemoved()
        s.dartsPulled()
        s.throwDart(s5, fromCamera = true, spot = spotA)
        val g = s.game!!
        assertEquals(1, g.currentPlayerIndex)
        assertEquals(listOf(s5), g.turnHits)
    }

    @Test
    fun aKeyedInDartAfterABustStillWorks() {
        val s = bustedPlayerAtTwoDarts()
        s.throwDart(s5)
        assertEquals(1, s.game!!.currentPlayerIndex)
    }
}
