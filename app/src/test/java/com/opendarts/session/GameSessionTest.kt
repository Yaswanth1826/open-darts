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
    fun aBustFlashesBustAndAClearedCalloutIsGone() {
        val s = bustedPlayerAtTwoDarts()
        val c = s.callout!!
        assertEquals("BUST", c.text)
        assertEquals(CalloutKind.BUST, c.kind)
        assertEquals(s.game!!.currentPlayer.name, c.player)
        s.clearCallout(c.id + 1)
        assertEquals(c, s.callout)
        s.clearCallout(c.id)
        assertNull(s.callout)
    }

    @Test
    fun aFinishedVisitFlashesItsTotalAndAHandOverWithNoDartsFlashesNoScore() {
        val s = GameSession().also { it.start(GameSetup(input = InputMode.CAMERA)) }
        repeat(3) { s.throwDart(t20, fromCamera = true, spot = spotA) }
        assertEquals("180", s.callout!!.text)
        assertEquals(CalloutKind.GOOD, s.callout!!.kind)
        s.nextPlayer()
        s.throwDart(s5, fromCamera = true, spot = spotA)
        s.nextPlayer()
        assertEquals("5", s.callout!!.text)
        assertEquals(CalloutKind.PLAIN, s.callout!!.kind)
    }

    @Test
    fun theGameShotIsFlashedAsGood() {
        val s = GameSession().also { it.start(GameSetup(input = InputMode.CAMERA, x01 = com.opendarts.game.x01.X01Config(40))) }
        s.throwDart(DartHit.double(20), fromCamera = true, spot = spotA)
        assertEquals("GAME SHOT", s.callout!!.text)
        assertEquals(CalloutKind.GOOD, s.callout!!.kind)
    }

    @Test
    fun dartsThatNeverShowedOnTheBoardCountAsMissesWhenTheTurnIsHandedOver() {
        val s = GameSession().also { it.start(GameSetup(input = InputMode.CAMERA)) }
        s.throwDart(s5, fromCamera = true, spot = spotA)
        s.dartsPulled()
        val first = (s.game as com.opendarts.game.x01.X01Game).gameState.players[0]
        assertEquals(3, first.totalDarts)
        assertEquals(1, s.game!!.currentPlayerIndex)
        assertEquals("5", s.callout!!.text)
    }

    @Test
    fun threeMissesWithNothingOnTheBoardAreANoScoreVisit() {
        val s = GameSession().also { it.start(GameSetup(input = InputMode.CAMERA)) }
        s.nextPlayer()
        assertEquals("NO SCORE", s.callout!!.text)
        assertEquals(3, (s.game as com.opendarts.game.x01.X01Game).gameState.players[0].totalDarts)
        assertEquals(1, s.game!!.currentPlayerIndex)
    }

    @Test
    fun aBustedVisitIsNotPaddedWithMisses() {
        val s = bustedPlayerAtTwoDarts()
        s.cameraDartsRemoved()
        s.dartsPulled()
        assertEquals(2, (s.game as com.opendarts.game.x01.X01Game).gameState.players[0].totalDarts)
    }

    private fun cameraLegGame() = GameSession().also {
        it.start(GameSetup(input = InputMode.CAMERA, x01 = com.opendarts.game.x01.X01Config(40, legsToWin = 2)))
        it.throwDart(DartHit.double(20), fromCamera = true, spot = spotA)
    }

    @Test
    fun aDartTheCameraSeesAfterALegWonWithFewerThanThreeDartsIsNotScoredInTheNextLeg() {
        val s = cameraLegGame()
        s.throwDart(s5, fromCamera = true, spot = spotB)
        assertEquals(listOf(DartHit.double(20)), s.game!!.turnHits)
        assertEquals(0, s.game!!.currentPlayerIndex)
    }

    @Test
    fun onceTheBoardIsClearedTheNextLegStartsWithTheFirstDartCounted() {
        val s = cameraLegGame()
        s.cameraDartsRemoved()
        s.dartsPulled()
        s.throwDart(s5, fromCamera = true, spot = spotB)
        assertEquals(1, s.game!!.currentPlayerIndex)
        assertEquals(listOf(s5), s.game!!.turnHits)
    }

    @Test
    fun undoingACameraDartTellsTheCameraToForgetItButAKeyedInDartHasNothingToForget() {
        val s = session()
        var forgotten: Point? = null
        s.onForget = { forgotten = it }
        s.throwDart(t20, fromCamera = true, spot = spotA)
        s.throwDart(s5)
        s.undo()
        assertNull(forgotten)
        s.undo()
        assertEquals(spotA, forgotten)
    }

    @Test
    fun undoingAHandOverDoesNotTellTheCameraToForgetAnything() {
        val s = session()
        var told = false
        s.onForget = { told = true }
        s.throwDart(t20, fromCamera = true, spot = spotA)
        s.nextPlayer()
        s.undo()
        assertFalse(told)
    }

    @Test
    fun aKeyedInDartAfterABustStillWorks() {
        val s = bustedPlayerAtTwoDarts()
        s.throwDart(s5)
        assertEquals(1, s.game!!.currentPlayerIndex)
    }
}
