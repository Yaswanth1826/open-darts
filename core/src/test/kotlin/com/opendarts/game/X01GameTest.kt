package com.opendarts.game

import com.opendarts.game.engine.Outcome
import com.opendarts.game.model.DartHit
import com.opendarts.game.model.Player
import com.opendarts.game.x01.InOut
import com.opendarts.game.x01.X01Config
import com.opendarts.game.x01.X01Game
import org.junit.Assert.*
import org.junit.Test

class X01GameTest {
    private val two = listOf(Player(0, "A"), Player(1, "B"))
    private fun X01Game.remaining(i: Int) = gameState.players[i].remaining

    @Test fun threeDartsSubtractAndPassTheTurn() {
        val g = X01Game(two, X01Config(501))
        repeat(3) { g.throwDart(DartHit.treble(20)) }
        assertEquals(321, g.remaining(0))
        assertTrue(g.turnOver)
        g.throwDart(DartHit.single(20))
        assertEquals(1, g.currentPlayerIndex)
        assertEquals(481, g.remaining(1))
    }

    @Test fun roundIncrementsAfterLastPlayer() {
        val g = X01Game(two, X01Config(501))
        assertEquals(1, g.round)
        repeat(3) { g.throwDart(DartHit.MISS) }
        g.endTurn()
        assertEquals(1, g.round)
        repeat(3) { g.throwDart(DartHit.MISS) }
        g.endTurn()
        assertEquals(2, g.round)
        assertEquals(0, g.currentPlayerIndex)
    }

    @Test fun bustRestoresScoreAndEndsTurnImmediately() {
        val g = X01Game(two, X01Config(101))
        g.throwDart(DartHit.treble(20))            // 41
        assertEquals(Outcome.BUST, g.throwDart(DartHit.treble(14)))
        assertEquals(101, g.remaining(0))
        assertTrue(g.turnOver)
        assertEquals(0, g.gameState.players[0].totalPoints)
        g.throwDart(DartHit.single(1))
        assertEquals(1, g.currentPlayerIndex)
    }

    @Test fun leavingOneIsBustWithDoubleOut() {
        val g = X01Game(X01Config(21))
        assertEquals(Outcome.BUST, g.throwDart(DartHit.single(20)))
        assertEquals(21, g.remaining(0))
    }
    @Test fun doubleOutNeedsADouble() {
        val g = X01Game(X01Config(40))
        assertEquals(Outcome.SCORED, g.throwDart(DartHit.single(20)))
        assertEquals(Outcome.BUST, g.throwDart(DartHit.single(20)))
        assertEquals(40, g.remaining(0))
        g.endTurn()
        assertEquals(Outcome.GAME_WON, g.throwDart(DartHit.double(20)))
        assertTrue(g.isFinished)
        assertEquals(listOf(0), g.winners)
    }

    @Test fun masterOutAcceptsTreble() {
        val g = X01Game(X01Config(60, outMode = InOut.MASTER))
        assertEquals(Outcome.GAME_WON, g.throwDart(DartHit.treble(20)))
    }

    @Test fun straightOutAllowsSingleFinish() {
        val g = X01Game(X01Config(20, outMode = InOut.STRAIGHT))
        assertEquals(Outcome.GAME_WON, g.throwDart(DartHit.single(20)))
    }

    @Test fun doubleInNeedsADoubleBeforeScoring() {
        val g = X01Game(X01Config(301, inMode = InOut.DOUBLE, outMode = InOut.STRAIGHT))
        assertEquals(Outcome.NOT_IN, g.throwDart(DartHit.treble(20)))
        assertEquals(301, g.remaining(0))
        assertEquals(Outcome.SCORED, g.throwDart(DartHit.double(10)))
        assertEquals(281, g.remaining(0))
    }

    @Test fun bustDuringDoubleInTurnRestoresNotInState() {
        val g = X01Game(X01Config(40, inMode = InOut.DOUBLE))
        g.throwDart(DartHit.double(10))   // in, 20 left
        assertEquals(Outcome.BUST, g.throwDart(DartHit.treble(20)))
        assertEquals(40, g.remaining(0))
        assertFalse(g.gameState.players[0].isIn)
    }

    @Test fun legsRotateStarterAndResetScores() {
        val g = X01Game(two, X01Config(40, legsToWin = 2))
        assertEquals(Outcome.LEG_WON, g.throwDart(DartHit.double(20)))
        assertFalse(g.isFinished)
        g.throwDart(DartHit.single(1))                  // auto end turn -> new leg, B starts
        assertEquals(1, g.currentPlayerIndex)
        assertEquals(39, g.remaining(1))
        assertEquals(40, g.remaining(0))
        assertEquals(1, g.gameState.players[0].legs)
        assertEquals(1, g.round)
    }

    @Test fun firstToTwoLegsWinsTheMatch() {
        val g = X01Game(two, X01Config(40, legsToWin = 2))
        g.throwDart(DartHit.double(20))                 // A wins leg 1
        g.endTurn()                                      // B starts leg 2
        g.throwDart(DartHit.MISS); g.throwDart(DartHit.MISS); g.throwDart(DartHit.MISS)
        g.endTurn()                                      // A throws
        assertEquals(Outcome.GAME_WON, g.throwDart(DartHit.double(20)))
        assertEquals(listOf(0), g.winners)
    }

    @Test fun setsResetLegCounts() {
        val g = X01Game(X01Config(40, legsToWin = 1, setsToWin = 2))
        assertEquals(Outcome.SET_WON, g.throwDart(DartHit.double(20)))
        g.endTurn()
        assertEquals(0, g.gameState.players[0].legs)
        assertEquals(1, g.gameState.players[0].sets)
        assertEquals(Outcome.GAME_WON, g.throwDart(DartHit.double(20)))
    }

    @Test fun undoRestoresDartTurnAndPlayer() {
        val g = X01Game(two, X01Config(501))
        repeat(3) { g.throwDart(DartHit.treble(20)) }
        g.throwDart(DartHit.single(5))                  // B's first dart
        assertEquals(1, g.currentPlayerIndex)
        assertTrue(g.undo())                             // removes 5
        assertEquals(1, g.currentPlayerIndex)
        assertEquals(501, g.remaining(1))
        assertTrue(g.undo())                             // removes A's 3rd dart
        assertEquals(0, g.currentPlayerIndex)
        assertEquals(2, g.turnHits.size)
        assertEquals(381, g.remaining(0))
    }

    @Test fun undoAfterLegWinReopensTheLeg() {
        val g = X01Game(two, X01Config(40, legsToWin = 2))
        g.throwDart(DartHit.double(20))
        g.endTurn()
        assertTrue(g.undo())
        assertEquals(0, g.gameState.players[0].legs)
        assertEquals(40, g.remaining(0))
    }

    @Test fun averagesTrackPointsAndDarts() {
        val g = X01Game(X01Config(501))
        repeat(3) { g.throwDart(DartHit.treble(20)) }
        assertEquals(180.0, g.gameState.players[0].average, 0.001)
        assertEquals(180, g.gameState.players[0].highestVisit)
    }

    @Test fun throwsAfterGameEndAreIgnored() {
        val g = X01Game(X01Config(40))
        g.throwDart(DartHit.double(20))
        assertEquals(Outcome.IGNORED, g.throwDart(DartHit.single(1)))
    }
}