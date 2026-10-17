package com.opendarts.geometry

import kotlin.math.abs

/** 3x3 projective transform: maps one plane to another (camera pixels <-> board millimetres). */
class Homography private constructor(private val m: DoubleArray) {
    /** The nine entries, row by row. */
    val matrix: DoubleArray get() = m.copyOf()

    fun apply(x: Double, y: Double): Point {
        val w = m[6] * x + m[7] * y + m[8]
        return Point((m[0] * x + m[1] * y + m[2]) / w, (m[3] * x + m[4] * y + m[5]) / w)
    }

    fun apply(p: Point) = apply(p.x, p.y)

    fun inverse(): Homography {
        val a = m[0]; val b = m[1]; val c = m[2]; val d = m[3]; val e = m[4]; val f = m[5]; val g = m[6]; val h = m[7]; val i = m[8]
        val det = a * (e * i - f * h) - b * (d * i - f * g) + c * (d * h - e * g)
        require(abs(det) > 1e-12) { "singular homography" }
        return Homography(doubleArrayOf(
            (e * i - f * h) / det, (c * h - b * i) / det, (b * f - c * e) / det,
            (f * g - d * i) / det, (a * i - c * g) / det, (c * d - a * f) / det,
            (d * h - e * g) / det, (b * g - a * h) / det, (a * e - b * d) / det,
        ))
    }

    companion object {
        /** Solves the homography taking each src[i] to dst[i] from exactly four correspondences. */
        fun fromFourPoints(src: List<Point>, dst: List<Point>): Homography {
            require(src.size == 4 && dst.size == 4) { "exactly four point pairs are required" }
            val a = Array(8) { DoubleArray(9) }
            for (i in 0 until 4) {
                val x = src[i].x; val y = src[i].y
                val u = dst[i].x; val v = dst[i].y
                a[2 * i] = doubleArrayOf(x, y, 1.0, 0.0, 0.0, 0.0, -u * x, -u * y, u)
                a[2 * i + 1] = doubleArrayOf(0.0, 0.0, 0.0, x, y, 1.0, -v * x, -v * y, v)
            }
            val solution = solve(a)
            return Homography(doubleArrayOf(solution[0], solution[1], solution[2], solution[3], solution[4], solution[5], solution[6], solution[7], 1.0))
        }

        private fun solve(a: Array<DoubleArray>): DoubleArray {
            val n = 8
            for (col in 0 until n) {
                var pivot = col
                for (row in col + 1 until n) if (abs(a[row][col]) > abs(a[pivot][col])) pivot = row
                require(abs(a[pivot][col]) > 1e-10) { "degenerate point configuration" }
                val tmp = a[col]; a[col] = a[pivot]; a[pivot] = tmp
                for (row in 0 until n) {
                    if (row == col) continue
                    val factor = a[row][col] / a[col][col]
                    for (k in col..n) a[row][k] -= factor * a[col][k]
                }
            }
            return DoubleArray(n) { a[it][n] / a[it][it] }
        }
    }
}