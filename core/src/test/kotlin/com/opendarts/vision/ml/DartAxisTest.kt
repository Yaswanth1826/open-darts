package com.opendarts.vision.ml

import com.opendarts.geometry.BoardGeometry
import com.opendarts.geometry.Homography
import com.opendarts.geometry.Point
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

class DartAxisTest {
    private val width = 1280
    private val height = 720
    private val focal = 900.0

    /** A pinhole camera 480 mm from the board, turned [yawDegrees] sideways and [pitchDegrees] up, looking at the bull. */
    private inner class Camera(yawDegrees: Double, pitchDegrees: Double) {
        private val y = Math.toRadians(yawDegrees)
        private val p = Math.toRadians(pitchDegrees)

        fun project(x: Double, yy: Double, z: Double): Point {
            // board -> camera: rotate about the vertical axis, then about the horizontal axis, then move away
            val x1 = x * cos(y) + z * sin(y)
            val z1 = -x * sin(y) + z * cos(y)
            val y2 = yy * cos(p) - z1 * sin(p)
            val z2 = yy * sin(p) + z1 * cos(p) + 480.0
            return Point(focal * x1 / z2 + width / 2.0, focal * y2 / z2 + height / 2.0)
        }

        val boardToPixel: Homography = Homography.fromFourPoints(
            BoardGeometry.MODEL_CALIBRATION_POINTS,
            BoardGeometry.MODEL_CALIBRATION_POINTS.map { project(it.x, it.y, 0.0) },
        )

        /** Where a dart's tip (on the board) and a spot [heightMm] up its barrel, towards the camera, show in the picture. */
        fun dart(x: Double, yy: Double, heightMm: Double) = project(x, yy, -heightMm)
    }

    private fun dist(a: Point, b: Point) = hypot(a.x - b.x, a.y - b.y)

    @Test
    fun `the axis of a dart points from its tip towards where a point up the barrel shows`() {
        val camera = Camera(yawDegrees = 40.0, pitchDegrees = 10.0)
        val tip = camera.dart(30.0, -20.0, 0.0)
        val axis = DartAxis.at(camera.boardToPixel, tip, width, height)!!
        val up = camera.dart(30.0, -20.0, 30.0)
        val dx = up.x - tip.x
        val dy = up.y - tip.y
        val cosine = (dx * axis.towardsFlights.x + dy * axis.towardsFlights.y) / hypot(dx, dy)
        assertTrue("cosine $cosine", cosine > 0.99)
    }

    @Test
    fun `a mark on the barrel is dropped when the tip is marked too`() {
        val camera = Camera(yawDegrees = 40.0, pitchDegrees = 10.0)
        val tip = camera.dart(30.0, -20.0, 0.0)
        val barrel = camera.dart(30.0, -20.0, 30.0)
        val kept = DartAxis.tips(listOf(barrel, tip), camera.boardToPixel, width, height)
        assertEquals(1, kept.size)
        assertEquals(0.0, dist(kept.single(), tip), 1e-9)
    }

    @Test
    fun `a barrel mark is dropped even when the axis is a little off`() {
        val camera = Camera(yawDegrees = 40.0, pitchDegrees = 10.0)
        val tip = camera.dart(30.0, -20.0, 0.0)
        val tilted = camera.dart(34.0, -14.0, 30.0)
        val kept = DartAxis.tips(listOf(tilted, tip), camera.boardToPixel, width, height)
        assertEquals(listOf(tip), kept)
    }

    @Test
    fun `darts far apart and a lone mark are left alone`() {
        val camera = Camera(yawDegrees = 40.0, pitchDegrees = 10.0)
        val a = camera.dart(30.0, -20.0, 0.0)
        val b = camera.dart(-70.0, 50.0, 0.0)
        assertEquals(listOf(a, b), DartAxis.tips(listOf(a, b), camera.boardToPixel, width, height))
        assertEquals(listOf(a), DartAxis.tips(listOf(a), camera.boardToPixel, width, height))
    }

    @Test
    fun `two tips side by side across the axis are both kept`() {
        val camera = Camera(yawDegrees = 40.0, pitchDegrees = 10.0)
        val a = camera.dart(30.0, -20.0, 0.0)
        val axis = DartAxis.at(camera.boardToPixel, a, width, height)!!
        val across = Point(a.x - axis.towardsFlights.y * 25, a.y + axis.towardsFlights.x * 25)
        assertEquals(2, DartAxis.tips(listOf(a, across), camera.boardToPixel, width, height).size)
    }

    @Test
    fun `a board seen straight on has no axis and nothing is dropped`() {
        val camera = Camera(yawDegrees = 0.0, pitchDegrees = 0.0)
        val a = camera.dart(30.0, -20.0, 0.0)
        val b = Point(a.x + 10, a.y + 6)
        assertNull(DartAxis.at(camera.boardToPixel, a, width, height))
        assertEquals(2, DartAxis.tips(listOf(a, b), camera.boardToPixel, width, height).size)
    }
}