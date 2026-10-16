package com.opendarts.game

import com.opendarts.game.model.DartHit
import com.opendarts.game.model.Player
import com.opendarts.game.practice.JdcChallengeGame
import com.opendarts.game.practice.JdcPart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JdcChallengeTest {
    private fun game(n: Int = 1) = JdcChallengeGame((0 until n).map { Player(it, "P$it") })

    private fun throwAll(g: JdcChallengeGame, vararg hits: DartHit) = hits.forEach { g.throwDart(it) }

    @Test fun targetsFollowTheChallengeOrder() {
        val g = game()
        assertEquals("10", g.targetFor(1, 0).label)
        assertEquals("15", g.targetFor(6, 2).label)
        assertEquals("D1", g.targetFor(7, 0).label)
        assertEquals("D3", g.targetFor(7, 2).label)
        assertEquals("D20", g.targetFor(13, 1).label)
        assertEquals("Bull", g.targetFor(13, 2).label)
        assertEquals("15", g.targetFor(14, 0).label)
        assertEquals("20", g.targetFor(19, 0).label)
        assertEquals(JdcPart.DOUBLES, g.targetFor(10, 0).part)
    }

    @Test fun onlyTheTargetNumberScoresAtFaceValue() {
        val g = game()
        throwAll(g, DartHit.treble(10), DartHit.single(5), DartHit.double(10))
        assertEquals(50, g.gameState.players[0].total)
    }

    @Test fun singleDoubleTrebleInOneVisitIsAShanghaiWorthABonus() {
        val g = game()
        throwAll(g, DartHit.single(10), DartHit.double(10), DartHit.treble(10))
        val p = g.gameState.players[0]
        assertEquals(10 + 20 + 30 + 100, p.total)
        assertEquals(1, p.shanghais)
    }

    @Test fun shanghaiWorksInAnyOrderAndNeedsAllThree() {
        val g = game()
        throwAll(g, DartHit.treble(10), DartHit.single(10), DartHit.single(10))
        assertEquals(50, g.gameState.players[0].total)
        g.endTurn()
        throwAll(g, DartHit.treble(11), DartHit.single(11), DartHit.double(11))
        assertEquals(50 + 33 + 11 + 22 + 100, g.gameState.players[0].total)
    }

    @Test fun doublesRoundScoresFiftyPerDoubleAndOnlyTheRightOne() {
        val g = game()
        repeat(6) { throwAll(g, DartHit.MISS, DartHit.MISS, DartHit.MISS) }
        g.endTurn()
        assertEquals(7, g.round)
        throwAll(g, DartHit.double(1), DartHit.double(5), DartHit.single(3))
        assertEquals(50, g.gameState.players[0].doubles)
        assertEquals("D4", g.currentTarget!!.label)
    }

    @Test fun innerBullIsWorthOneHundredAndOuterBullNothing() {
        val g = game()
        repeat(12) { throwAll(g, DartHit.MISS, DartHit.MISS, DartHit.MISS) }
        throwAll(g, DartHit.MISS, DartHit.MISS, DartHit.INNER_BULL)
        assertEquals(100, g.gameState.players[0].doubles)
        val h = game()
        repeat(12) { throwAll(h, DartHit.MISS, DartHit.MISS, DartHit.MISS) }
        throwAll(h, DartHit.MISS, DartHit.MISS, DartHit.OUTER_BULL)
        assertEquals(0, h.gameState.players[0].doubles)
    }

    @Test fun perfectGameScores3380() {
        val g = game()
        for (round in 1..19) {
            for (d in 0..2) {
                val t = g.targetFor(round, d)
                when {
                    t.part == JdcPart.DOUBLES && t.number == 25 -> g.throwDart(DartHit.INNER_BULL)
                    t.part == JdcPart.DOUBLES -> g.throwDart(DartHit.double(t.number))
                    d == 0 -> g.throwDart(DartHit.single(t.number))
                    d == 1 -> g.throwDart(DartHit.double(t.number))
                    else -> g.throwDart(DartHit.treble(t.number))
                }
            }
        }
        assertTrue(g.isFinished)
        assertEquals(3380, g.gameState.players[0].total)
        assertEquals(listOf(0), g.winners)
        assertNull(g.currentTarget)
    }

    @Test fun multiplayerRoundsAdvanceAndHighestTotalWins() {
        val g = game(2)
        for (round in 1..19) {
            repeat(3) { g.throwDart(if (round == 1) DartHit.single(10) else DartHit.MISS) }
            repeat(3) { g.throwDart(DartHit.MISS) }
        }
        assertTrue(g.isFinished)
        assertEquals(30, g.gameState.players[0].total)
        assertEquals(listOf(0), g.winners)
    }

    @Test fun undoRevertsScoreAndShanghai() {
        val g = game()
        throwAll(g, DartHit.single(10), DartHit.double(10), DartHit.treble(10))
        assertEquals(1, g.gameState.players[0].shanghais)
        g.undo()
        assertEquals(0, g.gameState.players[0].shanghais)
        assertEquals(30, g.gameState.players[0].total)
        assertFalse(g.isFinished)
    }
}