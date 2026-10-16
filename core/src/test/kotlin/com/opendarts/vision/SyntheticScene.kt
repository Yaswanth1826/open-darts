package com.opendarts.vision

import com.opendarts.game.model.Multiplier
import com.opendarts.geometry.BoardGeometry
import com.opendarts.geometry.Homography
import com.opendarts.geometry.Point
import java.util.Random
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Renders a perspective view of a dartboard with darts, enough to drive the tracker in unit tests.
 * Darts are drawn as a thin barrel from the tip (on the board plane) towards the camera, ending in a wider flight.
 */
class SyntheticScene(
    val width: Int = 640,
    val height: Int = 480,
    /** Image positions of the four calibration targets (20|1, 6|10, 3|19, 11|14 wires on the double ring edge). */
    val imageCorners: List<Point> = listOf(Point(331.0, 62.0), Point(558.0, 247.0), Point(321.0, 421.0), Point(78.0, 232.0)),
    private val noise: Double = 2.0,
    private val flightDirection: Point = Point(0.35, -1.0),
    private val seed: Long = 7,
) {
    val boardToImage: Homography = Homography.fromFourPoints(BoardGeometry.CALIBRATION_POINTS, imageCorners)
    val imageToBoard: Homography = boardToImage.inverse()
    private val rng = Random(seed)
    private val boardTexture = ByteArray(width * height)

    init {
        for (y in 0 until height) for (x in 0 until width) {
            val p = imageToBoard.apply(x + 0.5, y + 0.5)
            val r = hypot(p.x, p.y)
            boardTexture[y * width + x] = (if (r > 230) 95 else grey(p)).toByte()
        }
    }

    private fun grey(p: Point): Int {
        val hit = BoardGeometry.hitAt(p)
        if (hit.base == 0) return 25
        if (hit.base == 25) return if (hit.multiplier == Multiplier.DOUBLE) 120 else 90
        val index = BoardGeometry.SEGMENT_ORDER.indexOf(hit.base)
        val dark = index % 2 == 0
        return when (hit.multiplier) {
            Multiplier.SINGLE -> if (dark) 35 else 215
            else -> if (dark) 105 else 80
        }
    }

    /** A frame with darts at the given board positions (mm) and optional hand rectangle. */
    fun render(darts: List<Point> = emptyList(), hand: IntArray? = null): GrayFrame {
        val px = DoubleArray(boardTexture.size) { (boardTexture[it].toInt() and 0xFF).toDouble() }
        for (tip in darts) drawDart(px, boardToImage.apply(tip))
        if (hand != null) {
            for (y in hand[1].coerceAtLeast(0) until (hand[1] + hand[3]).coerceAtMost(height))
                for (x in hand[0].coerceAtLeast(0) until (hand[0] + hand[2]).coerceAtMost(width)) px[y * width + x] = 160.0
        }
        val out = ByteArray(width * height)
        for (i in out.indices) out[i] = (px[i] + rng.nextGaussian() * noise).roundToInt().coerceIn(0, 255).toByte()
        return GrayFrame(width, height, out)
    }

    private fun drawDart(px: DoubleArray, tip: Point) {
        val norm = hypot(flightDirection.x, flightDirection.y)
        val ux = flightDirection.x / norm
        val uy = flightDirection.y / norm
        val total = 62.0
        var s = 0.0
        while (s <= total) {
            val barrel = s < total * 0.62
            val radius = if (barrel) 1.6 else 1.6 + (s - total * 0.62) / (total * 0.38) * 6.5
            val cx = tip.x + ux * s
            val cy = tip.y + uy * s
            for (y in (cy - radius).toInt()..(cy + radius).toInt() + 1) for (x in (cx - radius).toInt()..(cx + radius).toInt() + 1) {
                if (x in 0 until width && y in 0 until height && hypot(x + 0.5 - cx, y + 0.5 - cy) <= radius) px[y * width + x] = if (barrel) 165.0 else if (boardTexture[y * width + x].toInt() and 0xFF < 128) 235.0 else 15.0
            }
            s += 0.5
        }
    }
}