package com.opendarts.vision.ml

import com.opendarts.geometry.Homography
import com.opendarts.geometry.Point
import kotlin.math.hypot
import kotlin.math.sqrt

/**
 * A dart sticks out of the board towards the camera, so the network often marks a spot on the barrel instead of the
 * tip, and a barrel position converts to the wrong board position. The tip is the end of the dart that touches the
 * board. Seen from the side, a dart that stands square to the board points in a direction that follows from the way
 * the board is skewed in the picture (the focal length comes from the board's own perspective, the principal point is
 * the frame centre). When two marks lie along that direction near each other, they belong to one dart and only the
 * one nearest the board, the tip, is kept.
 */
object DartAxis {
    /** [towardsFlights] is a unit vector in picture pixels from the tip towards the flights; [pixelsPer10Mm] is the picture length of 10 mm of dart. */
    class Axis(val towardsFlights: Point, val pixelsPer10Mm: Double)

    private const val STEP_MM = 10.0
    private const val REACH_STEPS = 6.0
    private const val MIN_ALIGNMENT = 0.8
    private const val MIN_GAP_PIXELS = 4.0

    fun at(boardToPixel: Homography, pixel: Point, width: Int, height: Int): Axis? {
        val m = boardToPixel.matrix
        val cx = width / 2.0
        val cy = height / 2.0
        val longest = maxOf(width, height).toDouble()
        val squared = -((m[0] - cx * m[6]) * (m[1] - cx * m[7]) + (m[3] - cy * m[6]) * (m[4] - cy * m[7])) / (m[6] * m[7])
        if (!squared.isFinite() || squared <= 0.0) return null
        val f = sqrt(squared)
        if (f < 0.3 * longest || f > 8 * longest) return null

        fun column(i: Int) = doubleArrayOf((m[i] - cx * m[6 + i]) / f, (m[3 + i] - cy * m[6 + i]) / f, m[6 + i])
        val c1 = column(0); val c2 = column(1); val c3 = column(2)
        val scale = 1.0 / sqrt(c1[0] * c1[0] + c1[1] * c1[1] + c1[2] * c1[2])
        val flip = if (c3[2] * scale < 0) -scale else scale
        val r1 = c1.map { it * flip }; val r2 = c2.map { it * flip }; val t = c3.map { it * flip }
        val r3 = doubleArrayOf(
            r1[1] * r2[2] - r1[2] * r2[1],
            r1[2] * r2[0] - r1[0] * r2[2],
            r1[0] * r2[1] - r1[1] * r2[0],
        )
        if (kotlin.math.abs(r3[2]) < 0.05) return null
        val towardsCamera = if (r3[2] > 0) -1.0 else 1.0

        val board = boardToPixel.inverse().apply(pixel)
        val base = DoubleArray(3) { r1[it] * board.x + r2[it] * board.y + t[it] }
        val moved = DoubleArray(3) { base[it] + r3[it] * towardsCamera * STEP_MM }
        if (base[2] <= 0 || moved[2] <= 0) return null
        val dx = f * moved[0] / moved[2] - f * base[0] / base[2]
        val dy = f * moved[1] / moved[2] - f * base[1] / base[2]
        val length = hypot(dx, dy)
        if (length < 1.5) return null
        return Axis(Point(dx / length, dy / length), length)
    }

    /** [pixels] without the marks that sit on the barrel of a dart whose tip is also marked. */
    fun tips(pixels: List<Point>, boardToPixel: Homography, width: Int, height: Int): List<Point> {
        if (pixels.size < 2) return pixels
        val axes = pixels.map { at(boardToPixel, it, width, height) }
        return pixels.filterIndexed { j, behind ->
            pixels.indices.none { i ->
                val axis = axes[i]
                if (i == j || axis == null) return@none false
                val dx = behind.x - pixels[i].x
                val dy = behind.y - pixels[i].y
                val gap = hypot(dx, dy)
                gap >= MIN_GAP_PIXELS && gap <= REACH_STEPS * axis.pixelsPer10Mm &&
                    (dx * axis.towardsFlights.x + dy * axis.towardsFlights.y) / gap >= MIN_ALIGNMENT
            }
        }
    }
}