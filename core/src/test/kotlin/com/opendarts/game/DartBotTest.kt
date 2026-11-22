package com.opendarts.game

import com.opendarts.game.bot.DartBot
import com.opendarts.game.cricket.CricketConfig
import com.opendarts.game.cricket.CricketGame
import com.opendarts.game.cricket.CricketMode
import com.opendarts.game.engine.DartGame
import com.opendarts.game.model.DartHit
import com.opendarts.game.model.Player
import com.opendarts.game.party.BaseballGame
import com.opendarts.game.practice.AroundTheClockConfig
import com.opendarts.game.practice.AroundTheClockGame
import com.opendarts.game.practice.CountUpConfig
import com.opendarts.game.practice.CountUpGame
import com.opendarts.game.x01.InOut
import com.opendarts.game.x01.X01Config
import com.opendarts.game.x01.X01Game
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DartBotTest {
    private fun two(vararg bots: Int) = bots.mapIndexed { i, level -> Player(i, "Bot ${i + 1}", level) }

    /** Plays the game to the end with every player a bot, handing over after each visit. Returns the number of visits. */
    private fun playOut(game: DartGame<*>, random: Random, maxVisits: Int = 3000): Int {
        var visits = 0
        while (!game.isFinished && visits < maxVisits) {
            val level = game.currentPlayer.bot
            while (!game.turnOver && !game.isFinished) game.throwDart(DartBot.throwDart(game, level, random))
            if (!game.isFinished) game.endTurn()
            visits++
        }
        return visits
    }

    @Test fun eachLevelAveragesAboutItsNumber() {
        for (level in DartBot.LEVELS) {
            val random = Random(level.average)
            var points = 0L
            var darts = 0L
            repeat(250) {
                val game = X01Game(listOf(Player(0, "Bot", level.average)), X01Config())
                playOut(game, random)
                val s = game.gameState.players[0]
                points += s.totalPoints; darts += s.totalDarts
            }
            val average = points * 3.0 / darts
            assertTrue("${level.label}: wanted ${level.average}, got $average", average in level.average * 0.93..level.average * 1.07)
        }
    }

    @Test fun strongerBotsWinMoreOften() {
        val random = Random(7)
        var strongWins = 0
        repeat(60) {
            val game = X01Game(two(90, 45), X01Config())
            playOut(game, random)
            if (game.winners == listOf(0)) strongWins++
        }
        assertTrue("won only $strongWins of 60", strongWins >= 50)
    }

    @Test fun sigmaGrowsAsTheLevelDrops() {
        val sigmas = (30..100 step 10).map { DartBot.sigma(it) }
        assertEquals(sigmas.sortedDescending(), sigmas)
        assertEquals(DartBot.sigma(30), DartBot.sigma(10), 0.0)
        assertEquals(DartBot.sigma(100), DartBot.sigma(130), 0.0)
    }

    @Test fun aValueBetweenLevelsIsBetweenTheirSigmas() {
        val s = DartBot.sigma(52)
        assertTrue(s < DartBot.sigma(45) && s > DartBot.sigma(60))
    }

    @Test fun x01AimsAtTrebleTwentyWhileFarFromAFinish() {
        val game = X01Game(two(60), X01Config())
        assertEquals(DartHit.treble(20), DartBot.aim(game))
    }

    @Test fun x01AimsAtTheFinishWhenOneIsOnTheBoard() {
        val game = X01Game(two(60), X01Config(startingScore = 170))
        assertEquals(DartHit.treble(20), DartBot.aim(game))
        val forty = X01Game(two(60), X01Config(startingScore = 40))
        assertEquals(DartHit.double(20), DartBot.aim(forty))
        val bull = X01Game(two(60), X01Config(startingScore = 50))
        assertEquals(DartHit.INNER_BULL, DartBot.aim(bull))
    }

    @Test fun x01LeavesADoubleWhenItCannotFinishThisVisit() {
        val game = X01Game(two(60), X01Config(startingScore = 61))
        game.throwDart(DartHit.single(1))
        game.throwDart(DartHit.single(1))
        // 59 with one dart left: no finish, so it sets up a double (for example 19 leaves D20)
        val aim = DartBot.aim(game)!!
        val leave = 59 - aim.points
        assertTrue("aimed at $aim leaving $leave", leave in 2..40 && leave % 2 == 0 || leave == 50)
    }

    @Test fun doubleInGamesStartByAimingAtADouble() {
        val game = X01Game(two(60), X01Config(inMode = InOut.DOUBLE))
        assertTrue(DartBot.aim(game)!!.isDouble)
    }

    @Test fun cricketAimsAtTheHighestNumberItHasNotClosed() {
        val game = CricketGame(two(60, 60), CricketConfig())
        assertEquals(DartHit.treble(20), DartBot.aim(game))
        game.throwDart(DartHit.treble(20))
        assertEquals(DartHit.treble(19), DartBot.aim(game))
    }

    @Test fun cricketCarriesOnWithTheNextNumberAfterClosingThree() {
        val game = CricketGame(two(60, 60), CricketConfig())
        listOf(20, 19, 18).forEach { game.throwDart(DartHit.treble(it)) }
        assertEquals(DartHit.treble(17), DartBot.aim(game))
    }

    @Test fun cricketScoresOnNumbersTheOpponentHasNotClosedOnceItsOwnAreShut() {
        val game = CricketGame(two(60, 60), CricketConfig())
        listOf(20, 19, 18).forEach { game.throwDart(DartHit.treble(it)) }
        game.endTurn(); game.endTurn()
        listOf(17, 16, 15).forEach { game.throwDart(DartHit.treble(it)) }
        game.endTurn(); game.endTurn()
        game.throwDart(DartHit.INNER_BULL); game.throwDart(DartHit.INNER_BULL)
        // Bot 1 has everything closed but Bot 2 has nothing: it now scores on 20
        assertEquals(DartHit.treble(20), DartBot.aim(game))
    }

    @Test fun aroundTheClockAimsAtTheNextNumber() {
        assertEquals(DartHit.single(1), DartBot.aim(AroundTheClockGame(two(60))))
        assertEquals(DartHit.double(1), DartBot.aim(AroundTheClockGame(two(60), AroundTheClockConfig(doublesOnly = true))))
        assertEquals(DartHit.treble(1), DartBot.aim(AroundTheClockGame(two(60), AroundTheClockConfig(skipOnMultiplier = true))))
    }

    @Test fun countUpAimsAtTrebleTwenty() {
        assertEquals(DartHit.treble(20), DartBot.aim(CountUpGame(two(60), CountUpConfig(rounds = 3))))
    }

    @Test fun everySupportedGameCanBePlayedToTheEnd() {
        val random = Random(3)
        listOf<DartGame<*>>(
            X01Game(two(45, 60), X01Config()),
            X01Game(two(45, 60), X01Config(inMode = InOut.DOUBLE, outMode = InOut.MASTER)),
            CricketGame(two(60, 60), CricketConfig(CricketMode.CUT_THROAT)),
            AroundTheClockGame(two(75, 90)),
            CountUpGame(two(60, 75), CountUpConfig(rounds = 4)),
        ).forEach { game ->
            assertTrue(DartBot.supports(game))
            playOut(game, random)
            assertTrue("${game::class.simpleName} did not finish", game.isFinished)
        }
    }

    @Test fun gamesItDoesNotKnowHaveNoAim() {
        val game = BaseballGame(two(60))
        assertFalse(DartBot.supports(game))
        assertNull(DartBot.aim(game))
    }

    @Test fun aDartAimedAtTheBullseyeWithNoErrorHitsIt() {
        assertEquals(DartHit.INNER_BULL, DartBot.land(DartHit.INNER_BULL, 0.0, Random(1)))
        assertNotNull(DartBot.land(DartHit.treble(20), 100, Random(1)))
    }

    @Test fun playerNamesAndBotFlag() {
        assertTrue(Player(0, "Bot", 60).isBot)
        assertFalse(Player(0, "Anna").isBot)
        assertEquals("Club", DartBot.levelName(60))
        assertEquals("Avg 52", DartBot.levelName(52))
    }
}