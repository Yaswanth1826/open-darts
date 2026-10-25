package com.opendarts.vision.ml

import com.opendarts.geometry.Point
import kotlin.math.abs
import kotlin.math.roundToInt

/** Average brightness of each [block] x [block] square of the frame (sampled on every other pixel), row by row. */
internal fun blockAverages(frame: ColorFrame, block: Int): IntArray {
    val c = frame.width / block
    val r = frame.height / block
    val out = IntArray(c * r)
    val sample = block / 2
    for (by in 0 until r) for (bx in 0 until c) {
        var sum = 0
        for (dy in 0 until block step 2) for (dx in 0 until block step 2) {
            val i = ((by * block + dy) * frame.width + bx * block + dx) * 3
            sum += (frame.rgb[i].toInt() and 0xFF) + (frame.rgb[i + 1].toInt() and 0xFF) + (frame.rgb[i + 2].toInt() and 0xFF)
        }
        out[by * c + bx] = sum / (3 * sample * sample)
    }
    return out
}

/**
 * The picture of the board from before a throw. A dart that has just landed must show up as a change around its
 * tip, so a detection with such a change can be trusted from a single look, while a mark on a logo or the bezel
 * (which was always there) cannot. Whole-picture brightness changes are removed before comparing.
 */
class ChangeReference(
    private val block: Int = 8,
    private val threshold: Int = 26,
    private val radius: Double = 40.0,
    private val minBlocks: Int = 3,
) {
    private var grid: IntArray? = null
    private var columns = 0
    private var rows = 0

    val isSet: Boolean get() = grid != null

    fun clear() { grid = null }

    fun capture(frame: ColorFrame) {
        grid = blockAverages(frame, block)
        columns = frame.width / block
        rows = frame.height / block
    }

    /** Compares [frame] with the stored picture; null if there is none (or the size differs). */
    fun compare(frame: ColorFrame): Change? {
        val before = grid ?: return null
        if (frame.width / block != columns || frame.height / block != rows) return null
        val now = blockAverages(frame, block)
        val diffs = IntArray(now.size) { now[it] - before[it] }
        val shift = diffs.sortedArray()[diffs.size / 2]
        return Change(BooleanArray(now.size) { abs(diffs[it] - shift) > threshold })
    }

    inner class Change internal constructor(private val changed: BooleanArray) {
        /** True if enough of the picture within a few centimetres of [pixel] is different from before. */
        fun around(pixel: Point): Boolean {
            val span = (radius / block).roundToInt()
            val cx = (pixel.x / block).toInt()
            val cy = (pixel.y / block).toInt()
            var count = 0
            for (y in maxOf(0, cy - span)..minOf(rows - 1, cy + span)) for (x in maxOf(0, cx - span)..minOf(columns - 1, cx + span)) {
                if (changed[y * columns + x]) count++
            }
            return count >= minBlocks
        }
    }
}