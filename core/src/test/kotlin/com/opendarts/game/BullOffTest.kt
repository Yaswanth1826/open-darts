package com.opendarts.game

import com.opendarts.game.engine.BullOff
import com.opendarts.game.model.DartHit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class BullOffTest {
    @Test fun theBullBeatsTheOuterBullBeatsAMiss() {
        assertTrue(BullOff.rank(DartHit.INNER_BULL) > BullOff.rank(DartHit.OUTER_BULL))
        assertTrue(BullOff.rank(DartHit.OUTER_BULL) > BullOff.rank(DartHit.MISS))
        assertEquals(BullOff.rank(DartHit.MISS), BullOff.rank(DartHit.treble(20)))
    }

    @Test fun theClosestDartLeads() {
        assertEquals(listOf(1), BullOff.leaders(mapOf(0 to DartHit.OUTER_BULL, 1 to DartHit.INNER_BULL, 2 to DartHit.MISS)))
    }

    @Test fun equalDartsTie() {
        assertEquals(listOf(0, 2), BullOff.leaders(mapOf(0 to DartHit.OUTER_BULL, 1 to DartHit.MISS, 2 to DartHit.OUTER_BULL)))
        assertEquals(listOf(0, 1), BullOff.leaders(mapOf(0 to DartHit.MISS, 1 to DartHit.single(5))))
    }

    @Test fun noDartsNoLeader() = assertEquals(emptyList<Int>(), BullOff.leaders(emptyMap()))

    @Test fun aBetterBotHitsTheBullMoreOften() {
        val random = Random(3)
        fun bulls(average: Int) = (1..2000).count { BullOff.rank(BullOff.botDart(average, random)) == 2 }
        assertTrue(bulls(100) > bulls(30))
    }
}