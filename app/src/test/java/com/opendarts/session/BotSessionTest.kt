package com.opendarts.session

import com.opendarts.billing.Access
import com.opendarts.billing.EntitlementProvider
import com.opendarts.game.model.DartHit
import com.opendarts.game.x01.X01Config
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BotSessionTest {
    private val setup = GameSetup(playerNames = listOf("Ann", "Bot Club"), botLevel = 60, x01 = X01Config(501), input = InputMode.MANUAL)

    private fun humanVisit(s: GameSession) {
        s.throwDart(DartHit.single(20)); s.throwDart(DartHit.single(5)); s.throwDart(DartHit.single(1))
    }

    private fun started() = GameSession().also { assertTrue(it.start(setup)) }

    private fun entitlements(premium: Boolean) = object : EntitlementProvider {
        override fun access() = Access(premium = premium, freeLeft = 3)
        override fun useFreeGame() {}
    }

    @Test fun theLastPlayerIsTheBot() {
        val g = started().game!!
        assertEquals(listOf(false, true), g.players.map { it.isBot })
        assertEquals(60, g.players[1].bot)
    }

    @Test fun aBotNeedsPremium() {
        val free = GameSession().also { it.entitlements = entitlements(premium = false) }
        assertFalse(free.start(setup))
        assertTrue(free.paywall)
        assertEquals("the dart bot", free.paywallFeature)
        assertFalse(free.isRunning)

        val paying = GameSession().also { it.entitlements = entitlements(premium = true) }
        assertTrue(paying.start(setup))
        assertNull(paying.paywallFeature)
    }

    @Test fun aFreePlayerCanStillPlayAPersonOnTheKeypad() {
        val free = GameSession().also { it.entitlements = entitlements(premium = false) }
        assertTrue(free.start(setup.copy(botLevel = 0, playerNames = listOf("Ann", "Bo"))))
    }

    @Test fun theBotPlaysAVisitAndHandsBack() {
        val s = started()
        humanVisit(s)
        s.nextPlayer()
        val g = s.game!!
        assertTrue(g.currentPlayer.isBot)
        repeat(3) { s.botStep() }
        assertEquals(3, g.turnHits.size)
        assertTrue(g.turnOver)
        s.botStep()
        assertFalse(g.currentPlayer.isBot)
        assertTrue(g.turnHits.isEmpty())
    }

    @Test fun nobodyCanThrowOrHandOverForTheBot() {
        val s = started()
        humanVisit(s)
        s.nextPlayer()
        val g = s.game!!
        s.throwDart(DartHit.treble(20))
        s.throwDart(DartHit.treble(20), fromCamera = true)
        s.editDart(0, DartHit.treble(20))
        s.nextPlayer()
        s.dartsPulled()
        assertTrue(g.currentPlayer.isBot)
        assertTrue(g.turnHits.isEmpty())
    }

    @Test fun aKeypadDartAfterTheVisitIsNotThrownForTheBot() {
        val s = started()
        humanVisit(s)
        s.throwDart(DartHit.treble(20))
        val g = s.game!!
        assertTrue(g.currentPlayer.isBot)
        assertTrue(g.turnHits.isEmpty())
        assertEquals(501, (g.gameState as com.opendarts.game.x01.X01State).players[1].remaining)
    }

    @Test fun aCameraDartSeenAfterTheVisitDoesNotHandOverEarly() {
        val s = started()
        humanVisit(s)
        s.throwDart(DartHit.treble(20), fromCamera = true)
        assertFalse(s.game!!.currentPlayer.isBot)
        s.dartsPulled()
        assertTrue(s.game!!.currentPlayer.isBot)
    }

    @Test fun aRematchKeepsTheBot() {
        val s = started()
        s.rematch()
        assertTrue(s.game!!.players.last().isBot)
    }

    @Test fun theBotLevelSurvivesBeingSaved() {
        assertEquals(setup, SetupCodec.decode(SetupCodec.encode(setup)))
        val old = SetupCodec.encode(setup).lines().filterNot { it.startsWith("bot=") }.joinToString("\n")
        assertEquals(0, SetupCodec.decode(old)!!.botLevel)
    }

    @Test fun aWholeGameAgainstTheBotCanBePlayedToTheEnd() {
        val s = GameSession().also { assertTrue(it.start(setup.copy(x01 = X01Config(61)))) }
        var guard = 0
        while (!s.game!!.isFinished && guard++ < 400) {
            if (s.game!!.currentPlayer.isBot) s.botStep() else { s.throwDart(DartHit.single(1)); s.throwDart(DartHit.single(1)); s.throwDart(DartHit.single(1)); s.nextPlayer() }
        }
        assertTrue(s.game!!.isFinished)
        assertEquals(listOf(1), s.game!!.winners)
    }
}