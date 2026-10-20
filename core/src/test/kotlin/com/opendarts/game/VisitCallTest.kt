package com.opendarts.game

import com.opendarts.game.engine.VisitCall
import com.opendarts.game.model.DartHit
import com.opendarts.game.model.Player
import com.opendarts.game.practice.CountUpConfig
import com.opendarts.game.practice.CountUpGame
import com.opendarts.game.x01.InOut
import com.opendarts.game.x01.X01Config
import com.opendarts.game.x01.X01Game
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VisitCallTest {
    private val two = listOf(Player(0, "A"), Player(1, "B"))

    private fun X01Game.visit(vararg hits: DartHit): String? {
        var outcome: com.opendarts.game.engine.Outcome? = null
        hits.forEach { outcome = throwDart(it) }
        return VisitCall.of(this, outcome)
    }

    @Test fun callsTheTotalOfThreeDarts() {
        assertEquals("100", X01Game(two, X01Config(501)).visit(DartHit.single(20), DartHit.single(20), DartHit.treble(20)))
        assertEquals("57", X01Game(two, X01Config(501)).visit(DartHit.treble(19), DartHit.MISS, DartHit.MISS))
        assertEquals("One hundred and eighty", X01Game(two, X01Config(501)).visit(DartHit.treble(20), DartHit.treble(20), DartHit.treble(20)))
    }

    @Test fun callsNoScoreWhenNothingScored() {
        assertEquals("No score", X01Game(two, X01Config(501)).visit(DartHit.MISS, DartHit.MISS, DartHit.MISS))
    }

    @Test fun callsNoScoreWhenThePlayerLeavesWithoutThrowing() {
        assertEquals("No score", VisitCall.of(X01Game(two, X01Config(501)), null))
    }

    @Test fun callsTheTotalOfAShortVisit() {
        val g = X01Game(two, X01Config(501))
        g.throwDart(DartHit.treble(20))
        g.throwDart(DartHit.single(5))
        assertEquals("65", VisitCall.of(g, null))
    }

    @Test fun callsBustAndGameShot() {
        assertEquals("Bust", X01Game(two, X01Config(101)).visit(DartHit.treble(20), DartHit.treble(20)))
        assertEquals("Game shot", X01Game(two, X01Config(40)).visit(DartHit.double(20)))
    }

    @Test fun doesNotCountDartsThatDoNotCountYet() {
        val g = X01Game(two, X01Config(501, inMode = InOut.DOUBLE))
        assertEquals("No score", g.visit(DartHit.treble(20), DartHit.single(20), DartHit.single(5)))
        val h = X01Game(two, X01Config(501, inMode = InOut.DOUBLE))
        assertEquals("25", h.visit(DartHit.treble(20), DartHit.double(10), DartHit.single(5)))
    }

    @Test fun staleTotalFromThePreviousVisitIsNotCalledForAnEmptyVisit() {
        val g = X01Game(two, X01Config(501))
        repeat(3) { g.throwDart(DartHit.treble(20)) }
        g.endTurn(); g.endTurn()
        assertEquals("No score", VisitCall.of(g, null))
    }

    @Test fun countUpCallsTheVisitTotal() {
        val g = CountUpGame(two, CountUpConfig(rounds = 3))
        g.throwDart(DartHit.treble(20))
        g.throwDart(DartHit.single(1))
        val outcome = g.throwDart(DartHit.MISS)
        assertEquals("61", VisitCall.of(g, outcome))
    }
}
