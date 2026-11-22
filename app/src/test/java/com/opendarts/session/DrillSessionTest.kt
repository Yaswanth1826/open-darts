package com.opendarts.session

import com.opendarts.billing.Access
import com.opendarts.billing.EntitlementProvider
import com.opendarts.game.model.DartHit
import com.opendarts.game.practice.TargetDrillGame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DrillSessionTest {
    private fun record(id: Long, doubles: String) = MatchRecord(id, GameKind.X01, "501", InputMode.MANUAL, 5,
        listOf(MatchPlayer("Ann", true, darts = 30, points = 300, detail = ShotDetail(ShotCodes.encode(DartHit.double(16)).toString(), doubles))))

    private fun premium(on: Boolean) = object : EntitlementProvider {
        override fun access() = Access(premium = on, freeLeft = 3)
        override fun useFreeGame() {}
    }

    @Test fun withoutDartsTheDrillUsesTheClassicDoubles() {
        val plan = DrillPlan.plan(emptyList(), "Ann")
        assertEquals(listOf(20, 16, 8, 10, 12, 18), plan.targets)
        assertEquals(0, plan.personal)
        assertEquals(plan.targets, DrillPlan.plan(emptyList(), null).targets)
    }

    @Test fun theWeakestDoublesComeFirstAndTheRestIsFilledUp() {
        val plan = DrillPlan.plan(listOf(record(1, "16-10-1.20-10-9.25-4-1.12-2-0")), "Ann")
        assertEquals(listOf(16, 25, 20, 8, 10, 12), plan.targets)
        assertEquals(3, plan.personal)
        assertEquals(plan.targets.size, plan.targets.toSet().size)
    }

    @Test fun theDrillNeedsPremium() {
        val setup = GameSetup(kind = GameKind.WEAK_SPOTS, playerNames = listOf("Ann"), input = InputMode.MANUAL)
        val free = GameSession().also { it.entitlements = premium(false) }
        assertFalse(free.start(setup))
        assertEquals("weak-spot drills", free.paywallFeature)
        assertTrue(free.paywall)
        assertTrue(GameSession().also { it.entitlements = premium(true) }.start(setup))
    }

    @Test fun theTargetsSurviveBeingSavedAndABadValueFallsBack() {
        val s = GameSetup(kind = GameKind.WEAK_SPOTS, playerNames = listOf("Ann"), drillTargets = listOf(16, 25, 3), input = InputMode.MANUAL)
        assertEquals(s, SetupCodec.decode(SetupCodec.encode(s)))
        val old = SetupCodec.encode(s).lines().filterNot { it.startsWith("drill=") }.joinToString("\n")
        assertEquals(GameSetup().drillTargets, SetupCodec.decode(old)!!.drillTargets)
        val bad = SetupCodec.encode(s).replace("drill=16.25.3", "drill=16.99")
        assertEquals(GameSetup().drillTargets, SetupCodec.decode(bad)!!.drillTargets)
    }

    @Test fun aDrillIsPlayedAndItsDartsFeedTheDoubleFigures() {
        val book = object : MatchHistory {
            val list = ArrayList<MatchRecord>()
            override fun all() = list.toList()
            override fun add(record: MatchRecord) { list.add(0, record) }
            override fun remove(id: Long) {}
            override fun clear() = list.clear()
        }
        val s = GameSession().also { it.history = book; it.start(GameSetup(kind = GameKind.WEAK_SPOTS, playerNames = listOf("Ann"), drillTargets = listOf(16), input = InputMode.MANUAL)) }
        val g = s.game as TargetDrillGame
        s.throwDart(DartHit.double(16)); s.throwDart(DartHit.MISS); s.throwDart(DartHit.MISS)
        s.nextPlayer()
        s.throwDart(DartHit.double(16)); s.throwDart(DartHit.double(16)); s.throwDart(DartHit.MISS)
        assertTrue(g.isFinished)
        val r = book.list.single()
        assertEquals(GameKind.WEAK_SPOTS, r.kind)
        assertEquals("16-6-3", r.players[0].detail.doubles)
        assertEquals(50, book.list.advancedStats("Ann")!!.doubles.single().percent)
    }

    @Test fun theDrillIsAPracticeGameWithoutABot() {
        assertEquals(Category.PRACTICE, GameKind.WEAK_SPOTS.category)
        assertFalse(GameKind.WEAK_SPOTS.canHaveBot)
        assertTrue(GameSetup(kind = GameKind.WEAK_SPOTS).buildGame() is TargetDrillGame)
    }
}