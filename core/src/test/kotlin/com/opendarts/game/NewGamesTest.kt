package com.opendarts.game

import com.opendarts.game.engine.DartGame
import com.opendarts.game.engine.Outcome
import com.opendarts.game.model.DartHit
import com.opendarts.game.model.Player
import com.opendarts.game.party.BaseballGame
import com.opendarts.game.party.HalveItGame
import com.opendarts.game.party.HalveTarget
import com.opendarts.game.party.KillerGame
import com.opendarts.game.party.ShanghaiGame
import com.opendarts.game.practice.Bobs27Game
import com.opendarts.game.practice.CheckoutLevel
import com.opendarts.game.practice.CheckoutPracticeConfig
import com.opendarts.game.practice.CheckoutPracticeGame
import com.opendarts.game.x01.Checkout
import com.opendarts.game.x01.InOut
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

private fun players(n: Int) = List(n) { Player(it, "P${it + 1}") }

private val MISS = DartHit.MISS

/** Throws the darts and, unless that finished the game, hands over to the next player. */
private fun DartGame<*>.visit(vararg hits: DartHit) {
    hits.forEach { throwDart(it) }
    if (!isFinished) endTurn()
}

class Bobs27Test {
    @Test fun aHitAddsTwiceTheNumberAndAMissCostsIt() {
        val g = Bobs27Game(players(1))
        g.visit(DartHit.double(1), MISS, MISS)
        assertEquals(27 + 2, g.gameState.players[0].score)
        g.visit(DartHit.single(2), DartHit.double(3), MISS)
        assertEquals(29 - 4, g.gameState.players[0].score)
    }

    @Test fun everyHitInTheVisitScores() {
        val g = Bobs27Game(players(1))
        g.visit(DartHit.double(1), DartHit.double(1), MISS)
        assertEquals(27 + 4, g.gameState.players[0].score)
    }

    @Test fun droppingBelowZeroPutsAPlayerOutAndTheOthersCarryOn() {
        val g = Bobs27Game(players(2))
        for (r in 1..5) {
            g.visit(MISS, MISS, MISS) // P1: 27 - 2 - 4 - 6 - 8 - 10 = -3
            g.visit(DartHit.double(r), MISS, MISS)
        }
        assertTrue(g.gameState.players[0].out)
        assertFalse(g.gameState.players[1].out)
        assertFalse(g.isFinished)
        assertEquals(1, g.currentPlayerIndex)
        assertEquals(6, g.round)
    }

    @Test fun theGameEndsAfterRoundTwentyAndTheHighestSurvivorWins() {
        val g = Bobs27Game(players(2))
        assertEquals(1, g.currentTarget)
        for (r in 1..20) {
            g.visit(DartHit.double(r), DartHit.double(r), DartHit.double(r))
            g.visit(DartHit.double(r), MISS, MISS)
        }
        assertTrue(g.isFinished)
        assertEquals(listOf(0), g.winners)
        assertNull(g.currentTarget)
    }

    @Test fun everyoneOutEndsTheGame() {
        val g = Bobs27Game(players(1))
        repeat(5) { g.visit(MISS, MISS, MISS) }
        assertTrue(g.gameState.players[0].out)
        assertTrue(g.isFinished)
        assertEquals(listOf(0), g.winners)
    }
}

class CheckoutPracticeTest {
    private fun game(level: CheckoutLevel = CheckoutLevel.MEDIUM, attempts: Int = 3, seed: Int = 7, n: Int = 1) =
        CheckoutPracticeGame(players(n), CheckoutPracticeConfig(level, attempts, seed))

    @Test fun everyNumberIsInTheLevelAndCanBeCheckedOutInThreeDarts() {
        for (level in CheckoutLevel.entries) {
            val g = game(level, attempts = 30)
            for (round in 1..30) {
                val t = g.targetFor(round)
                assertTrue("$t in ${level.label}", t in level.range)
                assertNotNull("$t", Checkout.suggest(t, InOut.DOUBLE, 3))
            }
        }
    }

    private fun assertNotNull(message: String, value: Any?) = assertTrue(message, value != null)

    @Test fun theSameSeedGivesTheSameNumbersAndAnotherSeedDoesNot() {
        val a = game(seed = 5); val b = game(seed = 5); val c = game(seed = 6)
        assertEquals((1..10).map { a.targetFor(it) }, (1..10).map { b.targetFor(it) })
        assertNotEquals((1..10).map { a.targetFor(it) }, (1..10).map { c.targetFor(it) })
    }

    @Test fun aCheckoutScoresAndEndsTheVisitEarly() {
        val g = game()
        val route = Checkout.suggest(g.targetFor(1), InOut.DOUBLE, 3)!!
        route.dropLast(1).forEach { assertNotEquals(Outcome.LEG_WON, g.throwDart(it)) }
        assertEquals(Outcome.LEG_WON, g.throwDart(route.last()))
        assertTrue(g.turnOver)
        assertEquals(1, g.gameState.checkouts[0])
    }

    @Test fun overshootingIsABustWithoutAPoint() {
        val g = game(CheckoutLevel.HARD)
        var outcome = Outcome.SCORED
        while (!g.turnOver) outcome = g.throwDart(DartHit.treble(20))
        assertEquals(Outcome.BUST, outcome)
        assertEquals(0, g.gameState.checkouts[0])
    }

    @Test fun finishingOnASingleIsABust() {
        val seed = (0..200).first { CheckoutPracticeGame(players(1), CheckoutPracticeConfig(CheckoutLevel.EASY, 3, it)).targetFor(1) in 2..20 }
        val g = game(CheckoutLevel.EASY, seed = seed)
        assertEquals(Outcome.BUST, g.throwDart(DartHit.single(g.targetFor(1))))
        assertEquals(0, g.gameState.checkouts[0])
    }

    @Test fun threeDartsWithoutFinishingEndTheVisitWithoutAPoint() {
        val g = game(CheckoutLevel.HARD)
        repeat(3) { g.throwDart(DartHit.single(1)) }
        assertTrue(g.turnOver)
        assertEquals(0, g.gameState.checkouts[0])
    }

    @Test fun theRemainingNumberShrinksWithEveryDart() {
        val g = game()
        val target = g.targetFor(1)
        assertEquals(target, g.remaining)
        g.throwDart(DartHit.single(1))
        assertEquals(target - 1, g.remaining)
    }

    @Test fun mostCheckoutsAfterTheLastRoundWins() {
        val g = game(attempts = 1, n = 2)
        Checkout.suggest(g.targetFor(1), InOut.DOUBLE, 3)!!.forEach { g.throwDart(it) }
        g.endTurn()
        repeat(3) { g.throwDart(DartHit.single(1)) }
        assertTrue(g.isFinished)
        assertEquals(listOf(0), g.winners)
        assertNull(g.remaining)
    }
}

class ShanghaiBaseballTest {
    @Test fun shanghaiScoresTheFaceValueOfTheRoundsNumberOnly() {
        val g = ShanghaiGame(players(1))
        g.visit(DartHit.double(1), DartHit.single(5), MISS)
        assertEquals(2, g.gameState.scores[0])
        g.visit(DartHit.treble(2), DartHit.double(2), DartHit.treble(3))
        assertEquals(2 + 6 + 4, g.gameState.scores[0])
    }

    @Test fun aSingleDoubleAndTrebleInOneVisitWinsAtOnce() {
        val g = ShanghaiGame(players(2))
        g.visit(MISS, MISS, MISS)
        g.throwDart(DartHit.single(1))
        g.throwDart(DartHit.double(1))
        assertFalse(g.isFinished)
        assertEquals(Outcome.GAME_WON, g.throwDart(DartHit.treble(1)))
        assertEquals(listOf(1), g.winners)
    }

    @Test fun threeTrebles_ofTheNumberIsNotAShanghai() {
        val g = ShanghaiGame(players(1))
        g.visit(DartHit.treble(1), DartHit.treble(1), DartHit.treble(1))
        assertFalse(g.isFinished)
        assertEquals(9, g.gameState.scores[0])
    }

    @Test fun withoutAShanghaiTheHighestTotalAfterTheLastRoundWins() {
        val g = ShanghaiGame(players(2), rounds = 2)
        g.visit(DartHit.single(1), DartHit.single(1), MISS)
        g.visit(DartHit.double(1), MISS, MISS)
        g.visit(DartHit.single(2), MISS, MISS)
        g.visit(MISS, MISS, MISS)
        assertTrue(g.isFinished)
        assertEquals(listOf(0), g.winners)
    }

    @Test fun baseballCountsRunsNotPoints() {
        val g = BaseballGame(players(1))
        g.visit(DartHit.single(1), DartHit.double(1), DartHit.treble(1))
        assertEquals(1 + 2 + 3, g.gameState.scores[0])
        assertFalse(g.isFinished)
    }

    @Test fun baseballRunsNineInningsAndNoSingleVisitWinsEarly() {
        val g = BaseballGame(players(1))
        for (n in 1..9) {
            assertEquals(n, g.currentNumber)
            g.visit(DartHit.single(n), DartHit.double(n), DartHit.treble(n))
        }
        assertTrue(g.isFinished)
        assertEquals(9 * 6, g.gameState.scores[0])
        assertNull(g.currentNumber)
    }
}

class HalveItTest {
    @Test fun aVisitWithoutAHitHalvesTheScore() {
        val g = HalveItGame(players(1))
        g.visit(MISS, DartHit.single(14), MISS)
        assertEquals(20, g.gameState.scores[0])
    }

    @Test fun hitsAddTheirPointsAndSaveTheScore() {
        val g = HalveItGame(players(1))
        g.visit(DartHit.single(15), DartHit.treble(15), MISS)
        assertEquals(40 + 15 + 45, g.gameState.scores[0])
    }

    @Test fun halvingRoundsDown() {
        val g = HalveItGame(players(1))
        g.visit(DartHit.single(15), MISS, MISS) // 55
        g.visit(MISS, MISS, MISS) // round 2 on 16: 27
        assertEquals(27, g.gameState.scores[0])
    }

    @Test fun targetsRunInOrderAndTheSpecialRoundsCountOnlyTheirDarts() {
        val g = HalveItGame(players(1))
        assertEquals(HalveTarget.FIFTEEN, g.currentTarget)
        g.visit(DartHit.single(15), MISS, MISS)
        g.visit(DartHit.single(16), MISS, MISS)
        assertEquals(HalveTarget.ANY_DOUBLE, g.currentTarget)
        val before = g.gameState.scores[0]
        g.visit(DartHit.single(20), DartHit.double(9), MISS)
        assertEquals(before + 18, g.gameState.scores[0])
        g.visit(DartHit.single(17), MISS, MISS)
        g.visit(DartHit.single(18), MISS, MISS)
        assertEquals(HalveTarget.ANY_TREBLE, g.currentTarget)
        val b = g.gameState.scores[0]
        g.visit(DartHit.double(20), DartHit.treble(7), MISS)
        assertEquals(b + 21, g.gameState.scores[0])
    }

    @Test fun theBullRoundTakesBothBullsAndTheGameEndsAfterNineRounds() {
        val g = HalveItGame(players(2))
        repeat(8) {
            g.visit(MISS, MISS, MISS)
            g.visit(MISS, MISS, MISS)
        }
        assertEquals(HalveTarget.BULL, g.currentTarget)
        g.visit(DartHit.OUTER_BULL, DartHit.INNER_BULL, MISS)
        assertEquals(75, g.gameState.scores[0])
        g.visit(MISS, MISS, MISS)
        assertTrue(g.isFinished)
        assertEquals(listOf(0), g.winners)
        assertNull(g.currentTarget)
    }
}

class KillerTest {
    private fun game(n: Int = 3, seed: Int = 1) = KillerGame(players(n), seed)

    @Test fun needsTwoPlayers() {
        assertThrows(IllegalArgumentException::class.java) { KillerGame(players(1)) }
    }

    @Test fun everyPlayerGetsADifferentNumberFromTheSeed() {
        val numbers = game(4, seed = 3).gameState.numbers
        assertEquals(4, numbers.distinct().size)
        assertTrue(numbers.all { it in 1..20 })
        assertEquals(numbers, game(4, seed = 3).gameState.numbers)
    }

    @Test fun onlyYourOwnDoubleMakesYouAKiller() {
        val g = game()
        val me = g.gameState.numbers[0]
        val other = g.gameState.numbers[1]
        g.throwDart(DartHit.single(me))
        g.throwDart(DartHit.double(other))
        assertFalse(g.gameState.killers[0])
        assertEquals(Outcome.SPECIAL, g.throwDart(DartHit.double(me)))
        assertTrue(g.gameState.killers[0])
    }

    @Test fun aPlayerWhoIsNotAKillerYetCannotHurtOthers() {
        val g = game(2)
        g.throwDart(DartHit.double(g.gameState.numbers[1]))
        assertEquals(3, g.gameState.lives[1])
    }

    @Test fun killersTakeLivesAndTheLastPlayerStandingWins() {
        val g = game(2)
        val (a, b) = g.gameState.numbers
        g.visit(DartHit.double(a))
        g.visit(DartHit.double(b))
        assertEquals(3, g.gameState.lives[1])
        g.throwDart(DartHit.double(b)); g.throwDart(DartHit.double(b))
        assertEquals(1, g.gameState.lives[1])
        g.endTurn()
        g.endTurn()
        assertEquals(Outcome.GAME_WON, g.throwDart(DartHit.double(b)))
        assertEquals(listOf(0), g.winners)
    }

    @Test fun aKnockedOutPlayerIsSkipped() {
        val g = game(3)
        val (a, b, _) = g.gameState.numbers
        g.visit(DartHit.double(a)) // P1 becomes a killer
        g.endTurn() // P2 passes
        g.endTurn() // P3 passes
        assertEquals(Outcome.SCORED, g.throwDart(DartHit.double(b)))
        assertEquals(Outcome.SCORED, g.throwDart(DartHit.double(b)))
        assertEquals(Outcome.ELIMINATED, g.throwDart(DartHit.double(b)))
        assertEquals(0, g.gameState.lives[1])
        assertFalse(g.isFinished)
        g.endTurn()
        assertEquals(2, g.currentPlayerIndex)
    }

    @Test fun replayingTheSameThrowsGivesTheSameGame() {
        fun play(): List<Int> {
            val g = game(3, seed = 9)
            val n = g.gameState.numbers
            g.visit(DartHit.double(n[0]))
            g.visit(DartHit.double(n[1]))
            g.visit(DartHit.double(n[2]))
            g.throwDart(DartHit.double(n[1])); g.throwDart(DartHit.double(n[2]))
            return g.gameState.lives
        }
        assertEquals(play(), play())
    }
}
