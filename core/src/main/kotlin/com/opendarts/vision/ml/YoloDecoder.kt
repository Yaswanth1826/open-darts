package com.opendarts.vision.ml

import kotlin.math.hypot

/** Object classes the network predicts. */
object DartClass {
    const val DART = 0
    /** cal_1..cal_4 are class 1..4, see [com.opendarts.geometry.BoardGeometry.MODEL_CALIBRATION_POINTS]. */
    const val CAL_FIRST = 1
    const val CAL_LAST = 4
    const val COUNT = 5
}

/** A detected point (the centre of a small box) with its class and confidence; coordinates are shares of the image (0..1). */
data class Detection(val cls: Int, val score: Float, val x: Double, val y: Double)

/** Anything that can run the trained network on a square, [inputSize] x [inputSize] RGB image. */
interface DartNet {
    val inputSize: Int
    fun run(image: ColorFrame): List<Detection>
}

/**
 * Reads the raw output of a YOLOv8-style detector: per candidate, centre x/y, width/height and one score per class
 * (no separate objectness). Works with either memory layout, [attributes, candidates] or [candidates, attributes],
 * and with boxes given as shares of the image or in input pixels.
 */
object YoloDecoder {
    fun decode(
        output: FloatArray,
        firstDim: Int,
        secondDim: Int,
        inputSize: Int,
        classes: Int = DartClass.COUNT,
        scoreThreshold: Float = 0.25f,
        suppressDistance: Double = 0.012,
    ): List<Detection> {
        val attributes = 4 + classes
        val channelsFirst = when {
            firstDim == attributes -> true
            secondDim == attributes -> false
            else -> error("output shape ${firstDim}x$secondDim does not hold $attributes attributes")
        }
        val candidates = if (channelsFirst) secondDim else firstDim
        fun value(candidate: Int, attribute: Int) =
            if (channelsFirst) output[attribute * candidates + candidate] else output[candidate * attributes + attribute]

        var largest = 0f
        for (i in 0 until candidates) largest = maxOf(largest, value(i, 0), value(i, 1))
        val divisor = if (largest > 2f) inputSize.toDouble() else 1.0

        val raw = ArrayList<Detection>()
        for (i in 0 until candidates) {
            var best = 0
            var bestScore = -1f
            for (c in 0 until classes) {
                val s = value(i, 4 + c)
                if (s > bestScore) { bestScore = s; best = c }
            }
            if (bestScore < scoreThreshold) continue
            raw += Detection(best, bestScore, value(i, 0) / divisor, value(i, 1) / divisor)
        }
        return suppress(raw, suppressDistance)
    }

    /** Keeps the strongest of any same-class detections closer together than [distance]. */
    fun suppress(detections: List<Detection>, distance: Double): List<Detection> {
        val kept = ArrayList<Detection>()
        for (d in detections.sortedByDescending { it.score }) {
            if (kept.none { it.cls == d.cls && hypot(it.x - d.x, it.y - d.y) < distance }) kept += d
        }
        return kept
    }
}
