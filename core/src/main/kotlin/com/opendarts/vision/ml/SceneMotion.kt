package com.opendarts.vision.ml

import kotlin.math.abs

/**
 * Tells a hand or arm in front of the board from a settled scene by comparing a coarse grid of the picture with the
 * one from the previous look. A dart landing changes a few pixels; a hand changes a large part of the board area.
 */
class SceneMotion(private val changeShare: Double, private val step: Int = 8, private val threshold: Int = 28) {
    private var previous: IntArray? = null
    private var width = 0
    private var height = 0

    /** True if more than [changeShare] of the sampled picture differs from the previous call. */
    fun moved(frame: ColorFrame): Boolean {
        val columns = frame.width / step
        val rows = frame.height / step
        val now = IntArray(columns * rows)
        for (r in 0 until rows) for (c in 0 until columns) {
            val i = ((r * step) * frame.width + c * step) * 3
            now[r * columns + c] = ((frame.rgb[i].toInt() and 0xFF) + (frame.rgb[i + 1].toInt() and 0xFF) + (frame.rgb[i + 2].toInt() and 0xFF)) / 3
        }
        val before = previous
        val sameSize = before != null && columns == width && rows == height
        previous = now
        width = columns
        height = rows
        if (!sameSize || before == null || now.isEmpty()) return false
        var changed = 0
        for (i in now.indices) if (abs(now[i] - before[i]) > threshold) changed++
        return changed.toDouble() / now.size > changeShare
    }
}