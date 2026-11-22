package com.opendarts.game

import com.opendarts.game.engine.VisitCall
import com.opendarts.game.model.DartHit
import com.opendarts.game.model.Player
import com.opendarts.game.practice.CountUpGame
import com.opendarts.game.x01.InOut
import com.opendarts.game.x01.X01Config
import com.opendarts.game.x01.X01Game
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RequirementCallTest {
    private fun game(start: Int = 501) = X01Game(listOf(Player(0, "Ann"), Player(1, "Bo")), X01Config(start, InOut.STRAIGHT, InOut.DOUBLE))

    @Test fun nothingIsSaidFarFromAFinish() {
        val g = game()
        assertNull(VisitCall.requirement(g))
        repeat(3) { g.throwDart(DartHit.single(20)) }
        g.endTurn()
        assertNull(VisitCall.requirement(g))
    }

    @Test fun theNextPlayerHearsTheirScoreWhenInRange() {
        val g = game(170)
        assertEquals("Ann requires 170", VisitCall.requirement(g))
        g.throwDart(DartHit.single(20)); g.endTurn()
        assertEquals("Bo requires 170", VisitCall.requirement(g))
    }

    @Test fun aScoreNobodyCanCheckOutIsNotCalled() {
        assertNull(VisitCall.requirement(game(169)))
        assertNull(VisitCall.requirement(game(168)))
        assertEquals("Ann requires 167", VisitCall.requirement(game(167)))
    }

    @Test fun aBustLeavesTheScoreAsItWas() {
        val g = game(40)
        g.throwDart(DartHit.single(20)); g.throwDart(DartHit.single(20))
        g.endTurn()
        assertEquals("Bo requires 40", VisitCall.requirement(g))
        g.endTurn()
        assertEquals("Ann requires 40", VisitCall.requirement(g))
    }

    @Test fun aPlayerNotYetInIsNotTold() {
        val g = X01Game(listOf(Player(0, "Ann")), X01Config(40, InOut.DOUBLE, InOut.DOUBLE))
        assertNull(VisitCall.requirement(g))
    }

    @Test fun nothingIsRequiredOnceTheLegIsWon() {
        val g = game(40)
        g.throwDart(DartHit.double(20))
        assertNull(VisitCall.requirement(g))
    }

    @Test fun otherGamesHaveNoRequirement() {
        val g = CountUpGame(listOf(Player(0, "Ann")))
        g.throwDart(DartHit.single(20))
        assertNull(VisitCall.requirement(g))
    }
}