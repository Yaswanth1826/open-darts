package com.opendarts.vision.ml

import com.opendarts.geometry.Point
import kotlin.math.hypot

/** Where the network found the four calibration points (in frame pixels, null if any is missing) and the darts. */
class FrameDetections(val calibration: List<Point>?, val darts: List<Point>)

/**
 * Runs the network twice per frame: once on the whole (letterboxed) frame to find the board, then on a square
 * crop around the board, which is how the network was trained and gives much more precise dart tips and corners.
 */
class TwoPassDetector(
    private val net: DartNet,
    private val dartScore: Float = 0.35f,
    private val calibrationScore: Float = 0.3f,
    private val cropFactor: Double = 2.5,
) {
    fun detect(frame: ColorFrame): FrameDetections {
        val whole = look(frame, frame.width / 2.0, frame.height / 2.0, maxOf(frame.width, frame.height).toDouble())
        val firstCal = whole.calibration ?: return FrameDetections(null, emptyList())

        val centre = Point(firstCal.map { it.x }.average(), firstCal.map { it.y }.average())
        val radius = firstCal.map { hypot(it.x - centre.x, it.y - centre.y) }.average()
        val crop = look(frame, centre.x, centre.y, cropFactor * radius)
        return if (crop.calibration != null) crop else whole
    }

    private fun look(frame: ColorFrame, cx: Double, cy: Double, side: Double): FrameDetections {
        val found = net.run(frame.window(cx, cy, side, net.inputSize))
        fun px(d: Detection) = Point(cx - side / 2 + d.x * side, cy - side / 2 + d.y * side)

        val calibration = (DartClass.CAL_FIRST..DartClass.CAL_LAST).map { cls ->
            found.filter { it.cls == cls && it.score >= calibrationScore }.maxByOrNull { it.score }?.let(::px)
        }
        val darts = found.filter { it.cls == DartClass.DART && it.score >= dartScore }.map(::px)
        return FrameDetections(if (calibration.all { it != null }) calibration.filterNotNull() else null, darts)
    }
}
