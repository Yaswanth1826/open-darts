package com.opendarts.game

import com.opendarts.game.model.DartHit
import com.opendarts.game.model.Player
import com.opendarts.game.practice.TargetDrillGame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TargetDrillTest {
    private fun game(vararg targets: Int, players: Int = 1) = TargetDrillGame(List(players) { Player(it, "P$it") }, targets.toList())

    private fun visit(g: TargetDrillGame, vararg hits: DartHit) = hits.forEach { g.throwDart(it) }

    @Test fun onlyTheRoundsDoubleScores() {
        val g = game(16, 8)
        assertEquals(16, g.currentTarget)
        visit(g, DartHit.double(16), DartHit.single(16), DartHit.double(8))
        assertEquals(listOf(1), g.gameState.hits)
        assertEquals(listOf(3), g.gameState.darts)
    }

    @Test fun theBullIsADoubleTarget() {
        val g = game(25)
        visit(g, DartHit.INNER_BULL, DartHit.OUTER_BULL, DartHit.INNER_BULL)
        assertEquals(listOf(2), g.gameState.hits)
    }

    @Test fun theTargetsMoveOnEveryRoundAndRepeatForTwoPasses() {
        val g = game(16, 8)
        assertEquals(4, g.rounds)
        val seen = ArrayList<Int?>()
        repeat(4) { seen += g.currentTarget; repeat(3) { g.throwDart(DartHit.MISS) }; g.endTurn() }
        assertEquals(listOf(16, 8, 16, 8), seen)
        assertTrue(g.isFinished)
        assertNull(g.currentTarget)
    }

    @Test fun theNextTargetShowsAsSoonAsTheLastPlayerIsDone() {
        val g = game(16, 8, players = 2)
        repeat(3) { g.throwDart(DartHit.MISS) }
        assertEquals(16, g.currentTarget)
        repeat(3) { g.throwDart(DartHit.MISS) }
        assertEquals(8, g.currentTarget)
    }

    @Test fun theMostHitsWinAndATieSharesIt() {
        val g = game(20, players = 2)
        // Round 1 and 2 aim at D20; player 0 hits twice, player 1 once.
        visit(g, DartHit.double(20), DartHit.double(20), DartHit.MISS)
        visit(g, DartHit.double(20), DartHit.MISS, DartHit.MISS)
        visit(g, DartHit.MISS, DartHit.MISS, DartHit.MISS)
        visit(g, DartHit.MISS, DartHit.MISS, DartHit.MISS)
        assertTrue(g.isFinished)
        assertEquals(listOf(0), g.winners)

        val tie = game(20)
        repeat(6) { tie.throwDart(DartHit.MISS) }
        assertEquals(listOf(0), tie.winners)
    }

    @Test fun undoTakesBackAHit() {
        val g = game(16)
        g.throwDart(DartHit.double(16))
        g.undo()
        assertEquals(listOf(0), g.gameState.hits)
        assertFalse(g.isFinished)
    }

    @Test fun badTargetsAreRefused() {
        val refused = listOf(emptyList(), listOf(21), listOf(0)).count { runCatching { TargetDrillGame(listOf(Player(0, "A")), it) }.isFailure }
        assertEquals(3, refused)
    }

    @Test fun eachDartRemembersItsRound() {
        val g = game(16, 8)
        repeat(3) { g.throwDart(DartHit.MISS) }
        g.throwDart(DartHit.double(8))
        assertEquals(listOf(1, 1, 1, 2), g.trail.map { it.round })
    }
}