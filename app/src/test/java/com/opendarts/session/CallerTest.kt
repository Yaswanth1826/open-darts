package com.opendarts.session

import com.opendarts.billing.Access
import com.opendarts.billing.EntitlementProvider
import com.opendarts.game.model.DartHit
import com.opendarts.game.x01.X01Config
import org.junit.Assert.assertEquals
import org.junit.Test

class CallerTest {
    private fun session(premium: Boolean?, full: Boolean): Pair<GameSession, MutableList<String>> {
        val said = ArrayList<String>()
        val s = GameSession().also {
            if (premium != null) it.entitlements = object : EntitlementProvider {
                override fun access() = Access(premium = premium, freeLeft = 3)
                override fun useFreeGame() {}
            }
            it.fullCaller = full
            it.onVisit = { said += it }
            it.start(GameSetup(playerNames = listOf("Ann", "Bo"), x01 = X01Config(501), input = InputMode.MANUAL))
        }
        return s to said
    }

    private fun visit(s: GameSession) = repeat(3) { s.throwDart(DartHit.single(20)) }

    @Test fun aSubscriberHearsWhatTheyRequire() {
        val (s, said) = session(premium = true, full = true)
        visit(s)
        assertEquals(listOf("60. Ann, you require 441"), said)
    }

    @Test fun theSettingTurnsItOff() {
        val (s, said) = session(premium = true, full = false)
        visit(s)
        assertEquals(listOf("60"), said)
    }

    @Test fun aFreePlayerOnlyHearsTheVisitTotal() {
        val (s, said) = session(premium = false, full = true)
        visit(s)
        assertEquals(listOf("60"), said)
    }

    @Test fun aBuildWithoutAccountsKeepsEveryFeature() {
        val (s, said) = session(premium = null, full = true)
        visit(s)
        assertEquals(listOf("60. Ann, you require 441"), said)
    }
}