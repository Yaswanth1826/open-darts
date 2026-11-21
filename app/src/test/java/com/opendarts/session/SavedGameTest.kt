package com.opendarts.session

import com.opendarts.game.cricket.CricketMode
import com.opendarts.game.engine.GameLog
import com.opendarts.game.model.DartHit
import com.opendarts.game.practice.AroundTheClockConfig
import com.opendarts.game.practice.CheckoutLevel
import com.opendarts.game.party.KillerGame
import com.opendarts.game.practice.CheckoutPracticeGame
import com.opendarts.game.x01.InOut
import com.opendarts.game.x01.X01Config
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SavedGameTest {
    private class Memory : GameSaver {
        var saved: Pair<GameSetup, GameLog>? = null
        var cleared = 0
        override fun save(setup: GameSetup, log: GameLog) { saved = setup to GameLog.decode(log.encode())!! }
        override fun load() = saved
        override fun clear() { saved = null; cleared++ }
    }

    private val setup = GameSetup(
        kind = GameKind.X01, playerNames = listOf("Ann & Bo", "Cy=Dee", "Ed", "Flo"), x01 = X01Config(301, InOut.DOUBLE, InOut.MASTER, 3, 2),
        cricketMode = CricketMode.CUT_THROAT, clock = AroundTheClockConfig(true, true), countUpRounds = 5, input = InputMode.MANUAL,
    )

    @Test
    fun theSetupSurvivesBeingWrittenAsText() {
        assertEquals(setup, SetupCodec.decode(SetupCodec.encode(setup)))
        assertNull(SetupCodec.decode("kind=NOPE"))
        assertNull(SetupCodec.decode(""))
    }

    @Test
    fun theNewGamesSettingsAndSeedSurviveToo() {
        val s = GameSetup(kind = GameKind.CHECKOUT_PRACTICE, checkoutLevel = CheckoutLevel.HARD, checkoutAttempts = 15, oneTwentyOne = com.opendarts.game.practice.OneTwentyOneConfig(101, 15), shanghaiRounds = 20, seed = -123456)
        assertEquals(s, SetupCodec.decode(SetupCodec.encode(s)))
    }

    @Test
    fun aSaveFromBeforeTheNewGamesStillLoads() {
        val old = SetupCodec.encode(setup).lines().filterNot { it.substringBefore('=') in setOf("checkout", "tries", "shanghai", "seed", "o21start", "o21tries") }.joinToString("\n")
        assertEquals(setup, SetupCodec.decode(old))
    }

    @Test
    fun gamesWithRandomNumbersResumeWithTheSameNumbers() {
        for (kind in listOf(GameKind.KILLER, GameKind.CHECKOUT_PRACTICE)) {
            val memory = Memory()
            val first = GameSession().also { it.attach(memory); it.start(GameSetup(kind = kind, playerNames = listOf("A", "B", "C"), input = InputMode.MANUAL)) }
            first.throwDart(DartHit.single(20)); first.throwDart(DartHit.double(10)); first.nextPlayer(); first.throwDart(DartHit.treble(19))
            val second = GameSession().also { it.attach(memory) }
            assertEquals(first.setup.seed, second.setup.seed)
            assertEquals(first.game!!.gameState, second.game!!.gameState)
            (first.game as? KillerGame)?.let { assertEquals(it.gameState.numbers, (second.game as KillerGame).gameState.numbers) }
            (first.game as? CheckoutPracticeGame)?.let { assertEquals(it.targetFor(1), (second.game as CheckoutPracticeGame).targetFor(1)) }
        }
    }

    @Test
    fun everyKindIsInOneCategoryAndKillerNeedsTwoPlayers() {
        assertEquals(GameKind.entries.toSet(), Category.entries.flatMap { it.kinds }.toSet())
        assertEquals(2, GameKind.KILLER.minPlayers)
        GameKind.entries.filter { it != GameKind.KILLER }.forEach { assertEquals(1, it.minPlayers) }
        GameKind.entries.forEach { k -> GameSetup(kind = k).buildGame() }
    }

    @Test
    fun aGameIsPickedUpWhereItWasLeftByANewSession() {
        val memory = Memory()
        val first = GameSession().also { it.attach(memory); it.start(setup) }
        first.throwDart(DartHit.treble(20)); first.throwDart(DartHit.single(5)); first.throwDart(DartHit.double(10))
        first.nextPlayer()
        first.throwDart(DartHit.single(19))
        first.editDart(0, DartHit.single(18))
        first.throwDart(DartHit.single(1)); first.undo()
        first.previousPlayer()
        first.editDart(1, DartHit.treble(20))
        first.nextPlayer()

        val second = GameSession().also { it.attach(memory) }
        assertTrue(second.isRunning)
        assertEquals(first.setup, second.setup)
        val a = first.game!!
        val b = second.game!!
        assertEquals(a.gameState, b.gameState)
        assertEquals(a.currentPlayerIndex, b.currentPlayerIndex)
        assertEquals(a.turnHits, b.turnHits)
        assertEquals(a.round, b.round)
    }

    @Test
    fun quittingForgetsTheGame() {
        val memory = Memory()
        val s = GameSession().also { it.attach(memory); it.start(setup) }
        s.throwDart(DartHit.single(3))
        assertNotNull(memory.saved)
        s.leave()
        assertNull(memory.saved)
        assertFalse(GameSession().also { it.attach(memory) }.isRunning)
    }

    @Test
    fun aDamagedSaveIsDroppedInsteadOfCrashing() {
        val memory = Memory().also { it.saved = setup.copy(playerNames = emptyList()) to GameLog() }
        val s = GameSession().also { it.attach(memory) }
        assertFalse(s.isRunning)
        assertNull(memory.saved)
    }
}
