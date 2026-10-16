package com.opendarts.game

import com.opendarts.game.cricket.CricketConfig
import com.opendarts.game.cricket.CricketGame
import com.opendarts.game.cricket.CricketMode
import com.opendarts.game.engine.Outcome
import com.opendarts.game.model.DartHit
import com.opendarts.game.model.Player
import org.junit.Assert.*
import org.junit.Test

class CricketGameTest {
    private fun game(mode: CricketMode = CricketMode.STANDARD, n: Int = 2) =
        CricketGame((0 until n).map { Player(it, "P$it") }, CricketConfig(mode))

    private fun CricketGame.pass() { while (!turnOver) throwDart(DartHit.MISS); endTurn() }

    private fun CricketGame.closeAllButBull(lastChunkFirst: Boolean = true) {
        listOf(listOf(20, 19, 18), listOf(17, 16, 15)).forEach { chunk ->
            chunk.forEach { throwDart(DartHit.treble(it)) }
            endTurn(); pass()
        }
    }

    @Test fun nonCricketNumbersDoNothing() {
        val g = game()
        assertEquals(Outcome.NO_SCORE, g.throwDart(DartHit.treble(14)))
        assertTrue(g.gameState.players[0].marks.isEmpty())
    }

    @Test fun trebleClosesInOneDart() {
        val g = game()
        g.throwDart(DartHit.treble(20))
        assertTrue(g.gameState.players[0].isClosed(20))
        assertEquals(0, g.gameState.players[0].points)
    }

    @Test fun standardScoresOnlyWhileOpponentStillOpen() {
        val g = game()
        g.throwDart(DartHit.treble(20)); g.throwDart(DartHit.single(20))
        assertEquals(20, g.gameState.players[0].points)
        g.pass()
        g.throwDart(DartHit.treble(20)); g.pass()      // B closes 20
        g.throwDart(DartHit.single(20))                  // nobody is open on 20 any more
        assertEquals(20, g.gameState.players[0].points)
    }

    @Test fun overflowMarksOnlyScoreTheExcess() {
        val g = game()
        g.throwDart(DartHit.double(19)); g.throwDart(DartHit.double(19))
        assertEquals(19, g.gameState.players[0].points)
    }

    @Test fun cutThroatGivesPointsToOpponentsWhoAreOpen() {
        val g = game(CricketMode.CUT_THROAT, 3)
        g.throwDart(DartHit.treble(20)); g.throwDart(DartHit.single(20))
        val p = g.gameState.players
        assertEquals(0, p[0].points)
        assertEquals(20, p[1].points)
        assertEquals(20, p[2].points)
    }

    @Test fun noScoreModeNeverAwardsPoints() {
        val g = game(CricketMode.NO_SCORE)
        g.throwDart(DartHit.treble(20)); g.throwDart(DartHit.treble(20))
        assertEquals(0, g.gameState.players[0].points)
    }

    @Test fun bullMarksAndDoubleBullCountsTwo() {
        val g = game()
        g.throwDart(DartHit.INNER_BULL); g.throwDart(DartHit.OUTER_BULL)
        assertEquals(3, g.gameState.players[0].marksOn(25))
    }

    @Test fun closingEverythingWhileLevelWins() {
        val g = game()
        g.closeAllButBull()
        g.throwDart(DartHit.INNER_BULL)
        assertEquals(Outcome.GAME_WON, g.throwDart(DartHit.OUTER_BULL))
        assertEquals(listOf(0), g.winners)
    }

    @Test fun closingEverythingWhileBehindDoesNotWin() {
        val g = game()
        g.throwDart(DartHit.treble(20)); g.throwDart(DartHit.treble(20))   // A: 60 points
        g.pass()
        listOf(listOf(20, 19, 18), listOf(17, 16, 15)).forEach { chunk ->
            chunk.forEach { g.throwDart(DartHit.treble(it)) }
            g.endTurn(); g.pass()
        }
        // B has now closed everything but the bull
        g.endTurn()
        assertFalse(g.isFinished)
    }

    @Test fun undoRevertsMarksAndPoints() {
        val g = game()
        g.throwDart(DartHit.treble(20)); g.throwDart(DartHit.single(20))
        g.undo()
        assertEquals(0, g.gameState.players[0].points)
        assertEquals(3, g.gameState.players[0].marksOn(20))
    }
}