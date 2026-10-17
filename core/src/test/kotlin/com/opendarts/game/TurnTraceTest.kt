package com.opendarts.game

import com.opendarts.game.engine.DartTrace
import com.opendarts.game.engine.TurnTrace
import com.opendarts.geometry.Point
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TurnTraceTest {
    private val a = Point(10.0, 0.0)
    private val b = Point(0.0, 50.0)

    private fun trace() = TurnTrace().apply {
        landed(1, DartTrace(a))
        landed(2, DartTrace(b))
    }

    @Test
    fun remembersTheOrderAndSpotsOfTheDarts() {
        val t = trace()
        assertEquals(a, t.spotOf(0))
        assertEquals(b, t.spotOf(1))
        assertNull(t.spotOf(2))
    }

    @Test
    fun aNewTurnStartsFromTheFirstDartAgain() {
        val t = trace()
        t.landed(1, DartTrace(b))
        assertEquals(1, t.size)
        assertEquals(b, t.spotOf(0))
    }

    @Test
    fun aDartBehindAnotherIsRememberedUntilTheFrontOneIsRemoved() {
        val t = trace()
        t.landed(3, DartTrace(null, behind = 1))
        assertTrue(t.hasDartBehind(1))
        assertFalse(t.hasDartBehind(0))
        t.removed(1)
        assertEquals(2, t.size)
        assertFalse(t.hasDartBehind(1))
        assertEquals(b, t.spotOf(1))
        assertNull(t.behindOf(1))
    }

    @Test
    fun removingAnEarlierDartMovesBehindReferencesUp() {
        val t = trace()
        t.landed(3, DartTrace(null, behind = 1))
        t.removed(0)
        assertEquals(0, t.behindOf(1))
        assertTrue(t.hasDartBehind(0))
    }

    @Test
    fun syncPadsWithUnknownDartsAndTrims() {
        val t = TurnTrace()
        t.sync(2)
        assertEquals(2, t.size)
        assertNull(t.spotOf(0))
        t.sync(1)
        assertEquals(1, t.size)
    }
}
