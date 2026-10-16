package com.opendarts.vision

import com.opendarts.geometry.Point
import kotlin.math.abs
import kotlin.math.sqrt

/** A connected group of changed pixels, with an estimate of where the dart tip is. */
class DartBlob(
    val area: Int,
    val minX: Int, val minY: Int, val maxX: Int, val maxY: Int,
    val tip: Point,
    val length: Double,
) {
    fun overlapsBox(other: DartBlob, margin: Int = 4): Boolean =
        minX <= other.maxX + margin && maxX >= other.minX - margin && minY <= other.maxY + margin && maxY >= other.minY - margin
}

internal object BlobFinder {
    private const val CELL = 4
    private const val CELL_GAP = 4

    /**
     * Groups set pixels of [mask] into blobs and locates the tip of each blob that is at least [minArea] pixels.
     * Pixels are first bucketed into [CELL]-pixel cells and cells are joined across gaps of up to [CELL_GAP]
     * cells, so a dart whose barrel and flight are separated by low-contrast stretches stays in one piece.
     */
    fun find(mask: BooleanArray, width: Int, height: Int, minArea: Int, maxArea: Int): List<DartBlob> {
        val cw = (width + CELL - 1) / CELL
        val ch = (height + CELL - 1) / CELL
        val cells = IntArray(cw * ch)
        for (i in mask.indices) if (mask[i]) cells[(i / width / CELL) * cw + (i % width) / CELL]++
        val label = IntArray(cells.size)
        val stack = IntArray(cells.size)
        var labels = 0
        val areas = ArrayList<Int>()
        for (start in cells.indices) {
            if (cells[start] == 0 || label[start] != 0) continue
            labels++
            var area = 0
            var sp = 0
            stack[sp++] = start
            label[start] = labels
            while (sp > 0) {
                val c = stack[--sp]
                area += cells[c]
                val cx = c % cw
                val cy = c / cw
                for (dy in -CELL_GAP..CELL_GAP) {
                    val ny = cy + dy
                    if (ny < 0 || ny >= ch) continue
                    for (dx in -CELL_GAP..CELL_GAP) {
                        val nx = cx + dx
                        if (nx < 0 || nx >= cw) continue
                        val n = ny * cw + nx
                        if (cells[n] > 0 && label[n] == 0) {
                            label[n] = labels
                            stack[sp++] = n
                        }
                    }
                }
            }
            areas += area
        }
        val members = Array(labels) { ArrayList<Int>(0) }
        for (i in mask.indices) if (mask[i]) {
            val l = label[(i / width / CELL) * cw + (i % width) / CELL]
            if (areas[l - 1] in minArea..maxArea) members[l - 1] += i
        }
        return members.filter { it.isNotEmpty() }.map { describe(it, width) }
    }
    private fun describe(members: List<Int>, width: Int): DartBlob {
        val n = members.size
        var sx = 0.0; var sy = 0.0
        var minX = Int.MAX_VALUE; var minY = Int.MAX_VALUE; var maxX = -1; var maxY = -1
        for (idx in members) {
            val x = idx % width; val y = idx / width
            sx += x; sy += y
            if (x < minX) minX = x; if (x > maxX) maxX = x
            if (y < minY) minY = y; if (y > maxY) maxY = y
        }
        val cx = sx / n; val cy = sy / n
        var sxx = 0.0; var syy = 0.0; var sxy = 0.0
        for (idx in members) {
            val dx = idx % width - cx; val dy = idx / width - cy
            sxx += dx * dx; syy += dy * dy; sxy += dx * dy
        }
        sxx /= n; syy /= n; sxy /= n
        // principal axis of the pixel cloud
        val trace = sxx + syy
        val det = sxx * syy - sxy * sxy
        val lambda = trace / 2 + sqrt(maxOf(0.0, trace * trace / 4 - det))
        var ax: Double; var ay: Double
        if (abs(sxy) > 1e-9) { ax = lambda - syy; ay = sxy } else if (sxx >= syy) { ax = 1.0; ay = 0.0 } else { ax = 0.0; ay = 1.0 }
        val norm = sqrt(ax * ax + ay * ay)
        ax /= norm; ay /= norm

        var tMin = Double.MAX_VALUE; var tMax = -Double.MAX_VALUE
        val ts = DoubleArray(n)
        for ((i, idx) in members.withIndex()) {
            val t = (idx % width - cx) * ax + (idx / width - cy) * ay
            ts[i] = t
            if (t < tMin) tMin = t; if (t > tMax) tMax = t
        }
        val length = tMax - tMin
        // The tip end is the narrow end: compare the widest cross-sections found in each outer third.
        val zone = length / 3.0
        val bins = IntArray(tMax.toInt() - tMin.toInt() + 2)
        for (t in ts) bins[(t - tMin).toInt()]++
        val third = maxOf(1, (zone).toInt())
        fun peakWidth(range: IntRange) = range.map { bins[it] }.sortedDescending().take(3).average()
        val lowWidth = peakWidth(0 until minOf(third, bins.size))
        val highWidth = peakWidth(maxOf(0, bins.size - third) until bins.size)
        val tipAtMin = lowWidth <= highWidth
        val tEnd = if (tipAtMin) tMin else tMax
        var tx = 0.0; var ty = 0.0; var tn = 0
        for ((i, idx) in members.withIndex()) {
            if (abs(ts[i] - tEnd) <= 1.5) { tx += idx % width; ty += idx / width; tn++ }
        }
        val tip = if (tn > 0) Point(tx / tn + 0.5, ty / tn + 0.5) else Point(cx + 0.5, cy + 0.5)
        return DartBlob(n, minX, minY, maxX, maxY, tip, length)
    }
}