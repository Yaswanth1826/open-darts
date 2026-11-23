package com.opendarts.game

import com.opendarts.game.engine.BullOff
import com.opendarts.game.engine.BullOffState
import com.opendarts.game.engine.Toss
import com.opendarts.game.model.DartHit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class BullOffTest {
    private fun ring(hit: DartHit) = Toss(hit)
    private fun at(x: Double, y: Double) = Toss(com.opendarts.geometry.BoardGeometry.hitAt(x, y), x, y)

    @Test fun theBullBeatsTheOuterBullBeatsAMiss() {
        assertTrue(BullOff.rank(DartHit.INNER_BULL) > BullOff.rank(DartHit.OUTER_BULL))
        assertTrue(BullOff.rank(DartHit.OUTER_BULL) > BullOff.rank(DartHit.MISS))
        assertEquals(BullOff.rank(DartHit.MISS), BullOff.rank(DartHit.treble(20)))
    }

    @Test fun theClosestRingLeadsWhenOnlyRingsAreKnown() {
        assertEquals(listOf(1), BullOff.leaders(mapOf(0 to ring(DartHit.OUTER_BULL), 1 to ring(DartHit.INNER_BULL), 2 to ring(DartHit.MISS))))
    }

    @Test fun equalRingsTie() {
        assertEquals(listOf(0, 2), BullOff.leaders(mapOf(0 to ring(DartHit.OUTER_BULL), 1 to ring(DartHit.MISS), 2 to ring(DartHit.OUTER_BULL))))
    }

    @Test fun exactDistancesDecideWhoWasCloser() {
        // Both are in the outer bull, which the rings alone could not separate.
        val near = at(3.0, 4.0)
        val far = at(-9.0, 8.0)
        assertEquals(5.0, near.distance!!, 1e-9)
        assertEquals(listOf(1), BullOff.leaders(mapOf(0 to far, 1 to near)))
        assertEquals(listOf(0), BullOff.leaders(mapOf(0 to at(0.0, -30.0), 1 to at(40.0, 40.0))))
    }

    @Test fun dartsAtTheSameDistanceTie() {
        assertEquals(listOf(0, 1), BullOff.leaders(mapOf(0 to at(10.0, 0.0), 1 to at(0.0, -10.0))))
    }

    @Test fun aKnownDistanceAndARingFallBackToTheRing() {
        assertEquals(listOf(1), BullOff.leaders(mapOf(0 to at(10.0, 0.0), 1 to ring(DartHit.INNER_BULL))))
    }

    @Test fun noDartsNoLeader() = assertEquals(emptyList<Int>(), BullOff.leaders(emptyMap()))

    @Test fun aBetterBotLandsCloserToTheBull() {
        val random = Random(3)
        fun mean(average: Int) = (1..2000).map { BullOff.botToss(average, random).distance!! }.average()
        assertTrue(mean(100) < mean(30))
    }

    @Test fun theRoundRunsInOrderAndStopsAtTheWinner() {
        var s = BullOffState(3)
        assertEquals(0, s.next)
        s = s.thrown(0, at(20.0, 0.0))
        assertEquals(1, s.next)
        s = s.thrown(1, at(5.0, 0.0)).thrown(2, at(30.0, 0.0))
        assertNull(s.next)
        assertEquals(1, s.winner)
    }

    @Test fun onlyTheNextPlayerCanThrow() {
        val s = BullOffState(2)
        assertEquals(s, s.thrown(1, at(1.0, 1.0)))
    }

    @Test fun aTieIsThrownAgainByTheTiedPlayersOnly() {
        var s = BullOffState(3).thrown(0, ring(DartHit.OUTER_BULL)).thrown(1, ring(DartHit.MISS)).thrown(2, ring(DartHit.OUTER_BULL))
        assertTrue(s.tied)
        assertNull(s.winner)
        s = s.again()
        assertEquals(listOf(0, 2), s.contenders)
        assertEquals(0, s.next)
        s = s.thrown(0, ring(DartHit.MISS)).thrown(2, ring(DartHit.INNER_BULL))
        assertEquals(2, s.winner)
        assertEquals(2, s.round)
    }

    @Test fun thePlayersCanPickWhoWasCloserOnlyAmongTheTied() {
        val s = BullOffState(3).thrown(0, ring(DartHit.OUTER_BULL)).thrown(1, ring(DartHit.MISS)).thrown(2, ring(DartHit.OUTER_BULL))
        assertEquals(s, s.closer(1))
        assertEquals(2, s.closer(2).winner)
    }

    @Test fun theLastDartCanBeTakenBack() {
        val s = BullOffState(2).thrown(0, at(5.0, 0.0)).thrown(1, at(9.0, 0.0))
        assertEquals(0, s.winner)
        val back = s.undone()
        assertEquals(1, back.next)
        assertNull(back.winner)
    }

    @Test fun aNudgedDartReplacesThatPlayersToss() {
        val s = BullOffState(2).thrown(0, at(5.0, 0.0)).thrown(1, at(9.0, 0.0))
        val moved = s.moved(9.0, 0.0, at(2.0, 0.0))
        assertEquals(1, moved.winner)
        assertEquals(s, s.moved(100.0, 100.0, at(0.0, 0.0)))
    }
}