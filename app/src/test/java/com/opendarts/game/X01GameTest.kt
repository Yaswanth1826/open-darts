package com.opendarts.game

import com.opendarts.game.model.DartHit
import com.opendarts.game.x01.ThrowStatus
import com.opendarts.game.x01.X01Config
import com.opendarts.game.x01.X01Game
import org.junit.Assert.*
import org.junit.Test

class X01GameTest {
    @Test fun subtractsScoresAndCompletesVisitAfterThreeDarts() {
        val game = X01Game(X01Config(501, doubleOut = true))
        game.throwDart(DartHit.treble(20))
        game.throwDart(DartHit.treble(20))
        val third = game.throwDart(DartHit.treble(20))
        assertEquals(321, game.remaining)
        assertEquals(ThrowStatus.SCORED, third.status)
        assertEquals(0, game.dartsInVisit)
        assertEquals(3, game.dartsThrown)
    }

    @Test fun bustRestoresScoreToStartOfVisit() {
        val game = X01Game(X01Config(101, doubleOut = true))
        game.throwDart(DartHit.treble(20)) // 41 left
        game.throwDart(DartHit.single(1))  // 40 left
        val bust = game.throwDart(DartHit.single(1)) // leaves 39, not a bust
        assertEquals(ThrowStatus.SCORED, bust.status)
        assertEquals(39, game.remaining)
        game.throwDart(DartHit.single(19)) // new visit: 20 left
        game.throwDart(DartHit.MISS)
        game.throwDart(DartHit.MISS) // visit ends at 20
        val bustToOne = game.throwDart(DartHit.single(19)) // 1 left: bust
        assertEquals(ThrowStatus.BUST, bustToOne.status)
        assertEquals(20, game.remaining)
    }

    @Test fun doubleOutRequiresDoubleToFinish() {
        val game = X01Game(X01Config(40, doubleOut = true))
        assertEquals(ThrowStatus.SCORED, game.throwDart(DartHit.single(20)).status)
        assertEquals(20, game.remaining)
        assertEquals(ThrowStatus.BUST, game.throwDart(DartHit.single(20)).status)
        assertEquals(40, game.remaining)
        assertEquals(ThrowStatus.LEG_WON, game.throwDart(DartHit.double(20)).status)
        assertEquals(0, game.remaining)
        assertTrue(game.isFinished)
    }

    @Test fun doubleInRequiresDoubleBeforeScoring() {
        val game = X01Game(X01Config(301, doubleIn = true, doubleOut = false))
        assertEquals(ThrowStatus.NOT_IN_YET, game.throwDart(DartHit.treble(20)).status)
        assertEquals(301, game.remaining)
        assertEquals(ThrowStatus.SCORED, game.throwDart(DartHit.double(10)).status)
        assertEquals(281, game.remaining)
    }

    @Test fun undoRestoresPreviousDartAndVisit() {
        val game = X01Game(X01Config(501, doubleOut = true))
        game.throwDart(DartHit.treble(20))
        game.throwDart(DartHit.single(20))
        assertEquals(421, game.remaining)
        assertTrue(game.undo())
        assertEquals(441, game.remaining)
        assertEquals(1, game.dartsInVisit)
        assertEquals(1, game.dartsThrown)
    }

    @Test fun undoAfterThirdDartRestoresTheFirstTwoDarts() {
        val game = X01Game(X01Config(501, doubleOut = true))
        game.throwDart(DartHit.treble(20))
        game.throwDart(DartHit.treble(20))
        game.throwDart(DartHit.treble(20))
        assertEquals(321, game.remaining)
        assertTrue(game.undo())
        assertEquals(381, game.remaining)
        assertEquals(2, game.dartsInVisit)
    }
}
