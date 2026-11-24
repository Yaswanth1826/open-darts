package com.opendarts.session

import com.opendarts.billing.Access
import com.opendarts.billing.EntitlementProvider
import com.opendarts.game.model.DartHit
import com.opendarts.game.x01.X01Config
import org.junit.Assert.assertEquals
import org.junit.Test

class CallerTest {
    private fun session(premium: Boolean?, full: Boolean, start: Int = 501): Pair<GameSession, MutableList<String>> {
        val said = ArrayList<String>()
        val s = GameSession().also {
            if (premium != null) it.entitlements = object : EntitlementProvider {
                override fun access() = Access(premium = premium, freeLeft = 3)
                override fun useFreeGame() {}
            }
            it.fullCaller = full
            it.onVisit = { said += it }
            it.onTurn = { said += "> $it" }
            it.start(GameSetup(playerNames = listOf("Ann", "Bo"), x01 = X01Config(start), input = InputMode.MANUAL))
        }
        return s to said
    }

    private fun visit(s: GameSession) = repeat(3) { s.throwDart(DartHit.single(20)) }

    @Test fun theVisitCallIsJustTheScore() {
        val (s, said) = session(premium = true, full = true)
        visit(s)
        s.nextPlayer()
        assertEquals(listOf("60"), said)
    }

    @Test fun aSubscriberInRangeHearsWhatTheyRequireAsTheyStepUp() {
        val (s, said) = session(premium = true, full = true, start = 141)
        visit(s)
        assertEquals(listOf("60"), said)
        s.nextPlayer()
        assertEquals(listOf("60", "> You need 141"), said)
        visit(s); s.nextPlayer()
        assertEquals(listOf("60", "> You need 141", "60", "> You need 81"), said)
    }

    @Test fun keyingTheNextScoreWithoutPressingNextStillCalls() {
        val (s, said) = session(premium = true, full = true, start = 141)
        visit(s)
        s.throwDart(DartHit.single(20))
        assertEquals(listOf("60", "> You need 141"), said)
        assertEquals("Bo", s.game!!.currentPlayer.name)
        assertEquals(1, s.game!!.turnHits.size)
    }

    @Test fun theSettingTurnsItOff() {
        val (s, said) = session(premium = true, full = false, start = 141)
        visit(s); s.nextPlayer()
        assertEquals(listOf("60"), said)
    }

    @Test fun aFreePlayerOnlyHearsTheVisitTotal() {
        val (s, said) = session(premium = false, full = true, start = 141)
        visit(s); s.nextPlayer()
        assertEquals(listOf("60"), said)
    }

    @Test fun aBuildWithoutAccountsKeepsEveryFeature() {
        val (s, said) = session(premium = null, full = true, start = 141)
        visit(s); s.nextPlayer()
        assertEquals(listOf("60", "> You need 141"), said)
    }
}