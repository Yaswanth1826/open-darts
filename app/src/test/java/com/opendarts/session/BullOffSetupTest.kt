package com.opendarts.session

import com.opendarts.game.model.DartHit
import com.opendarts.game.x01.X01Config
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BullOffSetupTest {
    private fun setup(vararg names: String, bot: Int = 0) = GameSetup(playerNames = names.toList(), botLevel = bot, input = InputMode.MANUAL, x01 = X01Config(501))

    @Test fun theWinnerGoesFirstAndTheOthersKeepTheirOrder() {
        val s = setup("Ann", "Bo", "Cy").startingWith(1)
        assertEquals(listOf("Bo", "Cy", "Ann"), s.playerNames)
    }

    @Test fun firstPlayerWinningChangesNothing() {
        val s = setup("Ann", "Bo")
        assertEquals(s, s.startingWith(0))
    }

    @Test fun theBotStaysTheBotWhenItIsMovedToTheFront() {
        val s = setup("Ann", "Bot Club", bot = 60).startingWith(1)
        assertEquals(listOf("Bot Club", "Ann"), s.playerNames)
        assertEquals(0, s.botIndex)
        assertEquals(listOf(true, false), s.players.map { it.isBot })
    }

    @Test fun aBotThatIsLeftBehindIsStillTheBot() {
        val s = setup("Ann", "Bo", "Bot Club", bot = 60).startingWith(1)
        assertEquals(listOf("Bo", "Bot Club", "Ann"), s.playerNames)
        assertEquals(listOf(false, true, false), s.players.map { it.isBot })
    }

    @Test fun withoutABotThereIsNoBotSeat() {
        assertEquals(-1, setup("Ann", "Bo").startingWith(1).botIndex)
        assertTrue(setup("Ann", "Bo").players.none { it.isBot })
    }

    @Test fun theBotSeatSurvivesSavingAndLoading() {
        val s = setup("Ann", "Bot Club", bot = 60).startingWith(1)
        val back = SetupCodec.decode(SetupCodec.encode(s))!!
        assertEquals(0, back.botIndex)
        assertEquals(s.playerNames, back.playerNames)
    }

    @Test fun anOlderSavedGameStillHasItsBotLast() {
        val old = SetupCodec.encode(setup("Ann", "Bot Club", bot = 60)).lines().filterNot { it.startsWith("seat=") }.joinToString("\n")
        assertEquals(1, SetupCodec.decode(old)!!.botIndex)
    }

    @Test fun theFirstThrowerOfTheGameIsTheWinner() {
        val g = setup("Ann", "Bo").startingWith(1).buildGame()
        assertEquals("Bo", g.currentPlayer.name)
    }

    @Test fun practiceHasNoBullOff() {
        assertFalse(GameKind.COUNT_UP.hasBullOff)
        assertTrue(GameKind.X01.hasBullOff && GameKind.CRICKET.hasBullOff && GameKind.KILLER.hasBullOff)
    }

    @Test fun aFreePlayerIsStoppedBeforeTheBullOff() {
        val s = GameSession().also {
            it.entitlements = object : com.opendarts.billing.EntitlementProvider {
                override fun access() = com.opendarts.billing.Access(premium = false, freeLeft = 3)
                override fun useFreeGame() {}
            }
        }
        assertFalse(s.preflight(setup("Ann", "Bot Club", bot = 60)))
        assertTrue(s.paywall)
        s.paywall = false
        assertTrue(s.preflight(setup("Ann", "Bo")))
        assertFalse(s.paywall)
    }
}