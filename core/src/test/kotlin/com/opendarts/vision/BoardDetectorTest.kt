package com.opendarts.vision

import com.opendarts.game.model.DartHit
import com.opendarts.game.model.Multiplier
import com.opendarts.geometry.BoardGeometry
import com.opendarts.geometry.Point
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

class BoardDetectorTest {
    private fun worstCorner(fit: BoardFit, scene: SyntheticScene): Double {
        val got = fit.corners
        return got.indices.maxOf { hypot(got[it].x - scene.imageCorners[it].x, got[it].y - scene.imageCorners[it].y) }
    }

    private fun check(name: String, scene: SyntheticScene, tolerancePx: Double) {
        val fit = BoardDetector.detect(scene.render())
        assertNotNull("$name: board not found", fit)
        val worst = worstCorner(fit!!, scene)
        println("$name: worst corner error %.2f px, confidence %.1f".format(worst, fit.confidence))
        assertTrue("$name: corners off by $worst px", worst <= tolerancePx)
    }

    private fun rotated(degrees: Double): List<Point> {
        val a = Math.toRadians(degrees)
        return SyntheticScene().imageCorners.map {
            val dx = it.x - 322.0; val dy = it.y - 240.0
            Point(322.0 + dx * cos(a) - dy * sin(a), 240.0 + dx * sin(a) + dy * cos(a))
        }
    }

    @Test fun findsAFrontOnBoard() = check("front", SyntheticScene(), 3.0)

    @Test fun findsABoardViewedFromTheSide() = check(
        "side",
        SyntheticScene(imageCorners = listOf(Point(300.0, 90.0), Point(520.0, 215.0), Point(335.0, 390.0), Point(110.0, 255.0))),
        3.0,
    )

    @Test fun findsASmallOffCentreBoard() = check(
        "small",
        SyntheticScene(imageCorners = listOf(Point(190.0, 130.0), Point(290.0, 215.0), Point(200.0, 300.0), Point(100.0, 215.0))),
        3.0,
    )

    @Test fun findsABoardAtFullPhoneResolution() = check(
        "hd",
        SyntheticScene(960, 720, listOf(Point(500.0, 90.0), Point(840.0, 370.0), Point(480.0, 640.0), Point(120.0, 350.0))),
        4.0,
    )

    @Test fun findsABoardTiltedBackTowardsTheTop() = check(
        "tilt",
        SyntheticScene(imageCorners = listOf(Point(322.0, 100.0), Point(520.0, 235.0), Point(330.0, 430.0), Point(125.0, 245.0))),
        3.0,
    )

    @Test fun findsABoardHungCrookedByTwelveDegrees() = check("crooked", SyntheticScene(imageCorners = rotated(12.0)), 3.0)

    @Test fun findsABoardHungCrookedTheOtherWay() = check("crooked-", SyntheticScene(imageCorners = rotated(-6.0)), 3.0)

    @Test fun toleratesHeavySensorNoise() = check("noisy", SyntheticScene(noise = 10.0), 4.0)

    @Test fun reportsNothingForAnEmptyWall() {
        val rng = Random(3)
        val pixels = ByteArray(640 * 480) { (120 + rng.nextGaussian() * 3).toInt().coerceIn(0, 255).toByte() }
        assertNull(BoardDetector.detect(GrayFrame(640, 480, pixels)))
    }

    @Test fun detectsTheBoardEvenWithDartsAlreadyInIt() {
        val scene = SyntheticScene()
        val fit = BoardDetector.detect(scene.render(listOf(BoardGeometry.centreOf(DartHit.treble(20)), BoardGeometry.centreOf(DartHit.double(3)))))
        assertNotNull(fit)
        assertTrue(worstCorner(fit!!, scene) <= 3.0)
    }

    @Test fun scoringThroughTheAutoDetectedBoardIsAccurate() {
        val scenes = listOf(
            SyntheticScene(),
            SyntheticScene(imageCorners = listOf(Point(300.0, 90.0), Point(520.0, 215.0), Point(335.0, 390.0), Point(110.0, 255.0))),
            SyntheticScene(imageCorners = rotated(10.0)),
        )
        val targets = buildList {
            for (n in 1..20) for (m in Multiplier.values()) add(DartHit(n, m))
            add(DartHit.OUTER_BULL); add(DartHit.INNER_BULL)
        }
        for (scene in scenes) {
            val fit = BoardDetector.detect(scene.render())!!
            val wrong = ArrayList<String>()
            for (target in targets) {
                val tracker = DartTracker(fit.boardToPixel.inverse(), scene.width, scene.height)
                repeat(8) { tracker.process(scene.render()) }
                val darts = listOf(BoardGeometry.centreOf(target))
                val events = (0 until 8).flatMap { tracker.process(scene.render(darts)) }
                val got = events.filterIsInstance<TrackerEvent.DartLanded>().singleOrNull()?.hit
                if (got != target) wrong += "$target -> $got"
            }
            assertTrue("misread $wrong", wrong.size <= 2)
        }
    }
}