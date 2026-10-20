package com.opendarts.game

import com.opendarts.game.model.DartHit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DartHitTest {
    @Test fun scoresSingleDoubleTrebleAndBullCorrectly() {
        assertEquals(20, DartHit.single(20).points)
        assertEquals(40, DartHit.double(20).points)
        assertEquals(60, DartHit.treble(20).points)
        assertEquals(25, DartHit.OUTER_BULL.points)
        assertEquals(50, DartHit.INNER_BULL.points)
        assertEquals(0, DartHit.MISS.points)
    }

    @Test(expected = IllegalArgumentException::class)
    fun trebleBullIsRejected() { DartHit(25, com.opendarts.game.model.Multiplier.TRIPLE) }

    @Test fun showsASingleAsItsNumberAndDoublesAndTreblesWithADash() {
        assertEquals("1", DartHit.single(1).label)
        assertEquals("20", DartHit.single(20).label)
        assertEquals("D-1", DartHit.double(1).label)
        assertEquals("T-1", DartHit.treble(1).label)
        assertEquals("T-20", DartHit.treble(20).label)
        assertEquals("25", DartHit.OUTER_BULL.label)
        assertEquals("Bull", DartHit.INNER_BULL.label)
        assertEquals("Miss", DartHit.MISS.label)
        assertEquals("S1", DartHit.single(1).toString())
    }

    @Test fun missIsNotADouble() { assertFalse(DartHit.MISS.isDouble) }
}
