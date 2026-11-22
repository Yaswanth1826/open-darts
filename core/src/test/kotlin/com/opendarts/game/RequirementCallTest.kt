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

    @Test fun theCallerNamesTheThrowerAndWhatIsLeft() {
        val g = game()
        repeat(3) { g.throwDart(DartHit.single(20)) }
        assertEquals("Ann, you require 441", VisitCall.requirement(g))
    }

    @Test fun aBustLeavesTheScoreAsItWas() {
        val g = game(40)
        g.throwDart(DartHit.single(20)); g.throwDart(DartHit.single(20))
        assertEquals("Ann, you require 40", VisitCall.requirement(g))
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