package com.opendarts.geometry

import com.opendarts.game.model.DartHit
import com.opendarts.game.model.Multiplier
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.sin

data class Point(val x: Double, val y: Double)

/**
 * Regulation dartboard in millimetres. Board plane: origin at the bullseye, +x to the right, +y up
 * (towards the 20), so a dart at (0, 100) is a treble 20.
 */
object BoardGeometry {
    const val INNER_BULL_RADIUS = 6.35
    const val OUTER_BULL_RADIUS = 15.9
    const val TREBLE_INNER_RADIUS = 99.0
    const val TREBLE_OUTER_RADIUS = 107.0
    const val DOUBLE_INNER_RADIUS = 162.0
    const val DOUBLE_OUTER_RADIUS = 170.0

    /** Segment numbers clockwise starting from the top. */
    val SEGMENT_ORDER = intArrayOf(20, 1, 18, 4, 13, 6, 10, 15, 2, 17, 3, 19, 7, 16, 8, 11, 14, 9, 12, 5)

    /** Angle (degrees clockwise from the top) of the wire between segment index i and i+1. */
    private fun wireAngle(index: Int) = index * 18.0 + 9.0

    fun hitAt(x: Double, y: Double): DartHit {
        val r = hypot(x, y)
        if (r > DOUBLE_OUTER_RADIUS) return DartHit.MISS
        if (r <= INNER_BULL_RADIUS) return DartHit.INNER_BULL
        if (r <= OUTER_BULL_RADIUS) return DartHit.OUTER_BULL
        val degrees = (Math.toDegrees(atan2(x, y)) + 360.0 + 9.0) % 360.0
        val number = SEGMENT_ORDER[floor(degrees / 18.0).toInt().coerceIn(0, 19)]
        val multiplier = when {
            r > DOUBLE_INNER_RADIUS -> Multiplier.DOUBLE
            r in TREBLE_INNER_RADIUS..TREBLE_OUTER_RADIUS -> Multiplier.TRIPLE
            else -> Multiplier.SINGLE
        }
        return DartHit(number, multiplier)
    }

    fun hitAt(p: Point) = hitAt(p.x, p.y)

    /** Millimetre position of the middle of a scoring area; used for drawing and tests. */
    fun centreOf(hit: DartHit): Point {
        if (hit.base == 0) return Point(0.0, DOUBLE_OUTER_RADIUS + 20.0)
        if (hit.base == 25) return Point(0.0, if (hit.multiplier == Multiplier.DOUBLE) 0.0 else (INNER_BULL_RADIUS + OUTER_BULL_RADIUS) / 2)
        val index = SEGMENT_ORDER.indexOf(hit.base)
        val r = when (hit.multiplier) {
            Multiplier.SINGLE -> (OUTER_BULL_RADIUS + TREBLE_INNER_RADIUS) / 2
            Multiplier.TRIPLE -> (TREBLE_INNER_RADIUS + TREBLE_OUTER_RADIUS) / 2
            Multiplier.DOUBLE -> (DOUBLE_INNER_RADIUS + DOUBLE_OUTER_RADIUS) / 2
        }
        val a = Math.toRadians(index * 18.0)
        return Point(r * sin(a), r * cos(a))
    }

    /**
     * The four calibration targets: where the outer edge of the double ring meets the wires
     * 20|1, 6|10, 3|19 and 11|14 (90 degrees apart).
     */
    val CALIBRATION_LABELS = listOf("20 | 1", "6 | 10", "3 | 19", "11 | 14")
    val CALIBRATION_POINTS: List<Point> = listOf(0, 5, 10, 15).map { i ->
        val a = Math.toRadians(wireAngle(i))
        Point(DOUBLE_OUTER_RADIUS * sin(a), DOUBLE_OUTER_RADIUS * cos(a))
    }

    /**
     * Calibration points the neural network is trained to find (the DeepDarts convention): the outer edge of the
     * double ring at the 5|20, 17|3, 8|11 and 13|6 wires, in the order cal_1..cal_4.
     */
    val MODEL_CALIBRATION_POINTS: List<Point> = listOf(19, 9, 14, 4).map { i ->
        val a = Math.toRadians(wireAngle(i))
        Point(DOUBLE_OUTER_RADIUS * sin(a), DOUBLE_OUTER_RADIUS * cos(a))
    }

    /** Unit of the board outline used for drawing: 2*PI sampled points at the double outer radius. */
    fun outline(radius: Double = DOUBLE_OUTER_RADIUS, steps: Int = 72): List<Point> =
        (0 until steps).map { val a = 2 * PI * it / steps; Point(radius * sin(a), radius * cos(a)) }
}