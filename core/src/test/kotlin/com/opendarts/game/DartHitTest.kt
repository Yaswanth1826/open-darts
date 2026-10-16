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

    @Test fun missIsNotADouble() { assertFalse(DartHit.MISS.isDouble) }
}
