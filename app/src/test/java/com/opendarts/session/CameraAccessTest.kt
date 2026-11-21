package com.opendarts.session

import com.opendarts.billing.Access
import com.opendarts.billing.EntitlementProvider
import com.opendarts.billing.FREE_CAMERA_GAMES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraAccessTest {
    private class Fake(var premium: Boolean = false, var used: Int = 0) : EntitlementProvider {
        override fun access() = Access(premium, (FREE_CAMERA_GAMES - used).coerceAtLeast(0))
        override fun useFreeGame() { if (!premium) used++ }
    }

    private fun session(fake: Fake) = GameSession().also { it.entitlements = fake }
    private val camera = GameSetup(input = InputMode.CAMERA)
    private val keypad = GameSetup(input = InputMode.MANUAL)

    @Test fun keypadGamesAreAlwaysFreeAndNeverCounted() {
        val fake = Fake(used = FREE_CAMERA_GAMES)
        val s = session(fake)
        assertTrue(s.start(keypad))
        assertEquals(FREE_CAMERA_GAMES, fake.used)
        assertFalse(s.paywall)
    }

    @Test fun eachCameraGameUsesOneFreeGameThenThePaywallAppears() {
        val fake = Fake()
        val s = session(fake)
        repeat(FREE_CAMERA_GAMES) { assertTrue(s.start(camera)) }
        assertEquals(FREE_CAMERA_GAMES, fake.used)
        assertFalse(s.start(camera))
        assertTrue(s.paywall)
        assertEquals(FREE_CAMERA_GAMES, fake.used)
    }

    @Test fun aRefusedStartLeavesTheRunningGameAlone() {
        val fake = Fake(used = FREE_CAMERA_GAMES)
        val s = session(fake)
        s.start(keypad)
        s.throwDart(com.opendarts.game.model.DartHit(20, com.opendarts.game.model.Multiplier.SINGLE))
        assertFalse(s.start(camera))
        assertEquals(1, s.game!!.turnHits.size)
        assertEquals(InputMode.MANUAL, s.setup.input)
    }

    @Test fun switchingToTheCameraMidGameCountsOnceAndCanBeRefused() {
        val fake = Fake()
        val s = session(fake)
        s.start(keypad)
        s.switchInput(InputMode.CAMERA)
        s.switchInput(InputMode.MANUAL)
        s.switchInput(InputMode.CAMERA)
        assertEquals(1, fake.used)

        val locked = Fake(used = FREE_CAMERA_GAMES)
        val t = session(locked)
        t.start(keypad)
        t.switchInput(InputMode.CAMERA)
        assertEquals(InputMode.MANUAL, t.setup.input)
        assertTrue(t.paywall)
    }

    @Test fun subscribersAreNeverCounted() {
        val fake = Fake(premium = true)
        val s = session(fake)
        repeat(10) { assertTrue(s.start(camera)) }
        assertEquals(0, fake.used)
    }

    @Test fun theCameraNeedsAKnownSignedInPlanWithGamesLeftOrASubscription() {
        assertTrue(Access(premium = false, freeLeft = 1).canUseCamera)
        assertTrue(Access(premium = true, freeLeft = 0).canUseCamera)
        assertFalse(Access(premium = false, freeLeft = 0).canUseCamera)
        assertFalse(Access(premium = true, freeLeft = 3, signedIn = false).canUseCamera)
        assertFalse(Access(premium = true, freeLeft = 3, known = false).canUseCamera)
    }

    @Test fun aSignedOutPlayerGetsThePaywallAndKeepsTheirGame() {
        val s = GameSession().also {
            it.entitlements = object : EntitlementProvider {
                override fun access() = Access(false, FREE_CAMERA_GAMES, signedIn = false)
                override fun useFreeGame() = error("must not count a refused game")
            }
        }
        assertFalse(s.start(camera))
        assertTrue(s.paywall)
        assertTrue(s.start(keypad))
    }
}