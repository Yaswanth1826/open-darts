package com.opendarts.vision.ml

import com.opendarts.geometry.BoardGeometry
import com.opendarts.geometry.Homography
import com.opendarts.geometry.Point
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

enum class Placement { GOOD, TOO_FAR, CUT_OFF, TOO_SIDEWAYS }

/**
 * How the camera sees the board. [tiltDegrees] is the angle between the camera's line of sight and the board's
 * normal (0 = straight on, 90 = edge on), [size] the longest width of the board as a share of the longer frame side.
 */
data class BoardView(val tiltDegrees: Double, val size: Double, val placement: Placement)

object PlacementAdvisor {
    const val MAX_TILT = 72.0
    const val MIN_SIZE = 0.30

    fun assess(boardToPixel: Homography, frameWidth: Int, frameHeight: Int): BoardView {
        val ring = (0 until 24).map {
            val a = it * 2 * PI / 24
            boardToPixel.apply(BoardGeometry.DOUBLE_OUTER_RADIUS * sin(a), BoardGeometry.DOUBLE_OUTER_RADIUS * cos(a))
        }
        var widest = 0.0
        for (i in ring.indices) for (j in i + 1 until ring.size) widest = maxOf(widest, hypot(ring[i].x - ring[j].x, ring[i].y - ring[j].y))
        val size = widest / maxOf(frameWidth, frameHeight)
        val tilt = tiltOf(boardToPixel)
        val cutOff = ring.any { it.x < 0 || it.y < 0 || it.x > frameWidth || it.y > frameHeight }
        val placement = when {
            cutOff -> Placement.CUT_OFF
            tilt > MAX_TILT -> Placement.TOO_SIDEWAYS
            size < MIN_SIZE -> Placement.TOO_FAR
            else -> Placement.GOOD
        }
        return BoardView(tilt, size, placement)
    }

    /** Foreshortening at the bull: the ratio of the smallest to the largest stretch equals cos(tilt). */
    private fun tiltOf(boardToPixel: Homography): Double {
        val o = boardToPixel.apply(0.0, 0.0)
        val x = boardToPixel.apply(1.0, 0.0)
        val y = boardToPixel.apply(0.0, 1.0)
        val a = x.x - o.x; val b = y.x - o.x; val c = x.y - o.y; val d = y.y - o.y
        val p = a * a + c * c; val q = a * b + c * d; val r = b * b + d * d
        val mean = (p + r) / 2
        val spread = sqrt(((p - r) / 2) * ((p - r) / 2) + q * q)
        val largest = sqrt(mean + spread)
        val smallest = sqrt(maxOf(mean - spread, 0.0))
        if (largest <= 0.0) return 90.0
        return acos((smallest / largest).coerceIn(0.0, 1.0)) * 180 / PI
    }
}

/**
 * Fills in one missing corner. The four corners sit on a circle 90 degrees apart, so seen from any angle they are
 * the corners of a (nearly) parallelogram; with a [previous] fit the old position is used instead, which is exact
 * while the camera has not moved. Returns null if more than one corner is missing.
 */
fun completeCorners(corners: List<Point?>, previous: Homography? = null): List<Point>? {
    val missing = corners.indices.filter { corners[it] == null }
    if (missing.isEmpty()) return corners.filterNotNull()
    if (missing.size > 1) return null
    val m = missing.single()
    val predicted = if (previous != null) {
        previous.apply(BoardGeometry.MODEL_CALIBRATION_POINTS[m])
    } else {
        // Going around the board the corners are 0, 3, 1, 2; the missing one is its neighbours minus its opposite.
        val ring = intArrayOf(0, 3, 1, 2)
        val at = ring.indexOf(m)
        val before = corners[ring[(at + 3) % 4]]!!
        val after = corners[ring[(at + 1) % 4]]!!
        val across = corners[ring[(at + 2) % 4]]!!
        Point(before.x + after.x - across.x, before.y + after.y - across.y)
    }
    return corners.mapIndexed { i, p -> p ?: predicted.also { check(i == m) } }
}