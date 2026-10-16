package com.opendarts.game

import com.opendarts.game.model.DartHit
import com.opendarts.game.x01.Checkout
import com.opendarts.game.x01.InOut
import org.junit.Assert.*
import org.junit.Test

class CheckoutTest {
    @Test fun highestFinishIsTwoTrebleTwentiesAndTheBull() {
        assertEquals(listOf(DartHit.treble(20), DartHit.treble(20), DartHit.INNER_BULL), Checkout.suggest(170))
    }

    @Test fun directDoubleIsPreferred() {
        assertEquals(listOf(DartHit.double(20)), Checkout.suggest(40))
        assertEquals(listOf(DartHit.INNER_BULL), Checkout.suggest(50))
    }

    @Test fun bogeyNumbersHaveNoThreeDartFinish() {
        listOf(169, 168, 166, 165, 163, 162, 159).forEach { assertNull("$it", Checkout.suggest(it)) }
    }

    @Test fun everyRouteAddsUpAndEndsOnADouble() {
        for (score in 2..170) {
            val route = Checkout.suggest(score) ?: continue
            assertEquals("score $score", score, route.sumOf { it.points })
            assertTrue("last dart of $score", route.last().isDouble)
            assertTrue(route.size <= 3)
        }
    }

    @Test fun everyNonBogeyScoreUpTo170CanBeFinished() {
        val bogeys = setOf(169, 168, 166, 165, 163, 162, 159)
        for (score in 2..170) if (score !in bogeys) assertNotNull("score $score", Checkout.suggest(score))
    }

    @Test fun masterOutAllowsTrebleFinish() {
        assertEquals(listOf(DartHit.treble(20)), Checkout.suggest(60, InOut.MASTER))
    }

    @Test fun oneIsNeverFinishableWithDoubleOut() {
        assertNull(Checkout.suggest(1))
    }
}