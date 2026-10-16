package com.opendarts.vision

import com.opendarts.game.model.DartHit
import com.opendarts.game.model.Multiplier
import com.opendarts.geometry.BoardGeometry
import com.opendarts.geometry.Point
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DartTrackerTest {
    private val scene = SyntheticScene()

    private fun tracker() = DartTracker(scene.imageToBoard, scene.width, scene.height)

    private fun DartTracker.feed(darts: List<Point>, frames: Int = 8, hand: IntArray? = null): List<TrackerEvent> =
        (0 until frames).flatMap { process(scene.render(darts, hand)) }

    private fun landed(events: List<TrackerEvent>) = events.filterIsInstance<TrackerEvent.DartLanded>()

    @Test fun reportsNothingUntilTheBoardIsSeenEmptyAndCalm() {
        val t = tracker()
        assertTrue(t.feed(emptyList(), frames = 2).isEmpty())
        assertEquals(TrackerState.WAITING_FOR_BOARD, t.state)
        t.feed(emptyList())
        assertEquals(TrackerState.READY, t.state)
    }

    @Test fun detectsEveryScoringAreaFromItsCentre() {
        val failures = ArrayList<String>()
        val targets = buildList {
            for (n in 1..20) for (m in Multiplier.values()) add(DartHit(n, m))
            add(DartHit.OUTER_BULL); add(DartHit.INNER_BULL)
        }
        for (target in targets) {
            val t = tracker()
            t.feed(emptyList())
            val events = landed(t.feed(listOf(BoardGeometry.centreOf(target))))
            val got = events.singleOrNull()?.hit
            if (got != target) failures += "$target -> $got"
        }
        assertTrue("misdetected: $failures", failures.size <= targets.size * 0.03)
    }

    @Test fun detectsThreeDartsOneAfterAnotherAndTheirRemoval() {
        val t = tracker()
        t.feed(emptyList())
        val a = BoardGeometry.centreOf(DartHit.treble(20))
        val b = BoardGeometry.centreOf(DartHit.double(16))
        val c = BoardGeometry.centreOf(DartHit.single(7))
        val hand = intArrayOf(280, 150, 120, 120)

        t.feed(emptyList(), frames = 3, hand = hand)
        assertEquals(DartHit.treble(20), landed(t.feed(listOf(a))).single().hit)
        t.feed(listOf(a), frames = 3, hand = hand)
        assertEquals(DartHit.double(16), landed(t.feed(listOf(a, b))).single().hit)
        t.feed(listOf(a, b), frames = 2, hand = hand)
        assertEquals(DartHit.single(7), landed(t.feed(listOf(a, b, c))).single().hit)
        assertEquals(3, t.darts)

        t.feed(listOf(a, b, c), frames = 3, hand = hand)
        val removal = t.feed(emptyList()).filterIsInstance<TrackerEvent.DartsRemoved>()
        assertEquals(0, removal.last().remaining)
        assertEquals(0, t.darts)
    }

    @Test fun removingOneDartIsNotMistakenForANewDart() {
        val t = tracker()
        t.feed(emptyList())
        val a = BoardGeometry.centreOf(DartHit.treble(20))
        val b = BoardGeometry.centreOf(DartHit.treble(3))
        t.feed(listOf(a)); t.feed(listOf(a, b))
        val events = t.feed(listOf(a))
        assertTrue(landed(events).isEmpty())
        assertEquals(1, (events.single() as TrackerEvent.DartsRemoved).remaining)
        assertEquals(1, t.darts)
    }

    @Test fun ignoresAHandPassingOverTheBoard() {
        val t = tracker()
        t.feed(emptyList())
        val events = ArrayList<TrackerEvent>()
        for (i in 0 until 12) events += t.process(scene.render(emptyList(), intArrayOf(100 + i * 25, 120, 140, 160)))
        events += t.feed(emptyList())
        assertTrue(events.isEmpty())
    }

    @Test fun missOutsideTheDoubleRingScoresZero() {
        val t = tracker()
        t.feed(emptyList())
        val events = landed(t.feed(listOf(Point(0.0, -185.0))))
        assertEquals(DartHit.MISS, events.single().hit)
    }

    @Test fun startsAndScoresOnANoisyCameraFeed() {
        val noisy = SyntheticScene(noise = 9.0)
        val t = DartTracker(noisy.imageToBoard, noisy.width, noisy.height)
        repeat(60) { t.process(noisy.render()) }
        assertEquals(TrackerState.READY, t.state)
        val target = DartHit.treble(19)
        val events = (0 until 10).flatMap { t.process(noisy.render(listOf(BoardGeometry.centreOf(target)))) }
        assertEquals(target, landed(events).singleOrNull()?.hit)
    }

    @Test fun startNowAcceptsTheNextFrameAsTheEmptyBoard() {
        val t = tracker()
        t.process(scene.render(hand = intArrayOf(100, 100, 300, 300)))
        t.startNow()
        t.process(scene.render(hand = intArrayOf(120, 100, 300, 300)))
        assertEquals(TrackerState.READY, t.state)
    }
}