package com.opendarts.vision.ml

import com.opendarts.geometry.BoardGeometry
import com.opendarts.geometry.Homography
import com.opendarts.geometry.Point

/**
 * Where the network found the four calibration points (in frame pixels; null where one is missing) and the darts.
 * The corners are in the order of [BoardGeometry.MODEL_CALIBRATION_POINTS].
 */
class FrameDetections(val corners: List<Point?>, val darts: List<Point>) {
    init { require(corners.size == 4) { "exactly four corners expected" } }

    val found: Int get() = corners.count { it != null }

    /** All four corners, or null if any is missing. */
    val calibration: List<Point>? get() = if (found == 4) corners.filterNotNull() else null

    companion object {
        val NOTHING = FrameDetections(List(4) { null }, emptyList())
    }
}

/**
 * The network was trained on boards that were flattened to a square-on view, but a phone beside the board sees
 * it from the side. So the board is found in the raw frame first (coarse), then the frame is warped square-on
 * using those corners and the network runs again on the flat board, which gives precise corners and dart tips.
 * Once the board is known the first pass is skipped and the previous corners are used to flatten the next frame.
 * Results are mapped back to frame pixels.
 */
class TwoPassDetector(
    private val net: DartNet,
    private val dartScore: Float = 0.35f,
    private val calibrationScore: Float = 0.3f,
) {
    /** Corner positions (share of the image) of the flattened board, the layout the network was trained on. */
    private val flatCorners: List<Point> = BoardGeometry.MODEL_CALIBRATION_POINTS.map {
        Point(net.inputSize * (0.5 + FLAT_SCALE * it.x), net.inputSize * (0.5 - FLAT_SCALE * it.y))
    }

    /** [hint] is where the corners were last time, if known. */
    fun detect(frame: ColorFrame, hint: List<Point>? = null): FrameDetections {
        if (hint != null) {
            val again = flattened(frame, hint)
            if (again != null && again.found >= 3) return again
        }

        val whole = look(frame)
        val rough = completeCorners(whole.corners) ?: return whole
        val refined = flattened(frame, rough)
        return if (refined != null && refined.found >= 3) refined else whole
    }

    private fun look(frame: ColorFrame): FrameDetections {
        val side = maxOf(frame.width, frame.height).toDouble()
        val cx = frame.width / 2.0
        val cy = frame.height / 2.0
        val found = net.run(frame.window(cx, cy, side, net.inputSize))
        return read(found) { Point(cx - side / 2 + it.x * side, cy - side / 2 + it.y * side) }
    }

    private fun flattened(frame: ColorFrame, corners: List<Point>): FrameDetections? {
        val flatToFrame = runCatching { Homography.fromFourPoints(flatCorners, corners) }.getOrNull() ?: return null
        val size = net.inputSize
        val found = net.run(frame.warp(flatToFrame, size))
        return read(found) { flatToFrame.apply(it.x * size, it.y * size) }
    }

    private fun read(found: List<Detection>, toFrame: (Detection) -> Point): FrameDetections {
        val corners = (DartClass.CAL_FIRST..DartClass.CAL_LAST).map { cls ->
            found.filter { it.cls == cls && it.score >= calibrationScore }.maxByOrNull { it.score }?.let(toFrame)
        }
        val darts = found.filter { it.cls == DartClass.DART && it.score >= dartScore }.map(toFrame)
        return FrameDetections(corners, darts)
    }

    companion object {
        /** The flattened training images show the 170 mm outer edge of the board at 0.37628 of their size. */
        const val FLAT_SCALE = 0.37628 / BoardGeometry.DOUBLE_OUTER_RADIUS
    }
}