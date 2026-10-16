package com.opendarts.game

import com.opendarts.game.engine.Outcome
import com.opendarts.game.model.DartHit
import com.opendarts.game.model.Player
import com.opendarts.game.practice.AroundTheClockConfig
import com.opendarts.game.practice.AroundTheClockGame
import com.opendarts.game.practice.CountUpConfig
import com.opendarts.game.practice.CountUpGame
import org.junit.Assert.*
import org.junit.Test

class PracticeGamesTest {
    private val two = listOf(Player(0, "A"), Player(1, "B"))

    @Test fun clockAdvancesOnlyOnTheTarget() {
        val g = AroundTheClockGame(two)
        assertEquals(Outcome.NO_SCORE, g.throwDart(DartHit.single(5)))
        assertEquals(Outcome.SCORED, g.throwDart(DartHit.treble(1)))
        assertEquals(2, g.gameState.targets[0])
    }

    @Test fun clockSkipsAheadWhenEnabled() {
        val g = AroundTheClockGame(two, AroundTheClockConfig(skipOnMultiplier = true))
        g.throwDart(DartHit.treble(1))
        assertEquals(4, g.gameState.targets[0])
    }

    @Test fun clockStillNeedsTheBullAfterATrebleTwenty() {
        val g = AroundTheClockGame(listOf(Player(0, "A")), AroundTheClockConfig(skipOnMultiplier = true))
        for (n in 1..19) g.throwDart(DartHit.single(n))
        g.throwDart(DartHit.treble(20))
        assertEquals(21, g.gameState.targets[0])
        assertFalse(g.isFinished)
        g.throwDart(DartHit.OUTER_BULL)
        assertTrue(g.isFinished)
    }

    @Test fun clockDoublesOnlyRejectsSingles() {
        val g = AroundTheClockGame(two, AroundTheClockConfig(doublesOnly = true))
        assertEquals(Outcome.NO_SCORE, g.throwDart(DartHit.single(1)))
        assertEquals(Outcome.SCORED, g.throwDart(DartHit.double(1)))
    }

    @Test fun countUpEndsAfterConfiguredRoundsAndPicksHighest() {
        val g = CountUpGame(two, CountUpConfig(rounds = 2))
        repeat(2) {
            repeat(3) { g.throwDart(DartHit.single(20)) }; g.endTurn()
            repeat(3) { g.throwDart(DartHit.single(1)) }; if (!g.isFinished) g.endTurn()
        }
        assertTrue(g.isFinished)
        assertEquals(listOf(0), g.winners)
        assertEquals(120, g.gameState.scores[0])
    }

    @Test fun countUpTieYieldsMultipleWinners() {
        val g = CountUpGame(two, CountUpConfig(rounds = 1))
        repeat(3) { g.throwDart(DartHit.single(5)) }; g.endTurn()
        repeat(3) { g.throwDart(DartHit.single(5)) }
        assertEquals(listOf(0, 1), g.winners)
    }
}