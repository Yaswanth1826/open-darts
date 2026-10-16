package com.opendarts.vision

import com.opendarts.geometry.BoardGeometry
import com.opendarts.geometry.Homography
import com.opendarts.geometry.Point
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * The board as seen by the camera: [boardToPixel] maps board millimetres (y up) to frame pixels.
 * [confidence] is how much stronger the ring and wire edges line up here than anywhere else in the frame.
 */
class BoardFit(val boardToPixel: Homography, val confidence: Double, val width: Int, val height: Int) {
    /** Pixel positions of [BoardGeometry.CALIBRATION_POINTS]. */
    val corners: List<Point> get() = BoardGeometry.CALIBRATION_POINTS.map { boardToPixel.apply(it) }

    /** [corners] as fractions of the frame size, independent of the resolution. */
    val normalisedCorners: List<Point> get() = corners.map { Point(it.x / width, it.y / height) }

    /** Largest distance between corresponding corners of two fits, as a fraction of the frame's shorter side. */
    fun differenceTo(other: BoardFit): Double {
        val a = corners
        val b = other.corners
        val scale = min(width, height).toDouble()
        return a.indices.maxOf { kotlin.math.hypot(a[it].x - b[it].x, a[it].y - b[it].y) } / scale
    }
}

/**
 * Finds a dartboard in a frame without any user input. The board is assumed to hang with the 20 at the top
 * (within half a segment, 9 degrees), and the camera may look at it from a moderate angle.
 *
 * It scores a candidate board model by how well its ring edges (treble, double) and its segment wires line up with
 * edges in the image, searches coarsely over position and size, then refines the best candidates with a
 * perspective model (a full homography) by coordinate descent.
 */
object BoardDetector {
    private const val WORK_WIDTH = 320
    private const val MIN_CONFIDENCE = 2.2
    private const val MIN_RING_CONTRAST = 14.0
    private const val MIN_WIRE_CONTRAST = 18.0
    private val RING_RADII = doubleArrayOf(BoardGeometry.DOUBLE_OUTER_RADIUS, BoardGeometry.DOUBLE_INNER_RADIUS, BoardGeometry.TREBLE_OUTER_RADIUS, BoardGeometry.TREBLE_INNER_RADIUS)
    private const val RING_ANGLES = 96
    private const val EDGE_MM = 3.0
    private const val SHARP_EDGE_MM = 1.2
    private const val WIRE_EPS_DEGREES = 3.2
    private const val SHARP_WIRE_EPS_DEGREES = 1.3

    fun detect(frame: GrayFrame): BoardFit? {
        val full = Image.of(frame).blur()
        val factor = max(1, frame.width / WORK_WIDTH)
        val small = full.shrink(factor)

        val coarse = coarseCandidates(small)
        if (coarse.isEmpty()) return null
        val median = coarse.scores.sorted().let { it[it.size / 2] }.coerceAtLeast(1e-6)

        var best: Model? = null
        var bestScore = -1.0
        for (candidate in coarse.top) {
            for (rotation in doubleArrayOf(-6.0, 0.0, 6.0)) {
                val model = candidate.copy().also { it.rotate(rotation) }
                val refined = descend(small, model, fullScore = false)
                if (refined.second > bestScore) { bestScore = refined.second; best = refined.first }
            }
        }
        val winner = best ?: return null
        // Second pass at full resolution for sub-pixel accuracy.
        val scaled = winner.scaled(factor.toDouble())
        val (middle, _) = descend(full, scaled, fullScore = true, startStep = 0.5)
        val (finalModel, _) = descend(full, middle, fullScore = true, startStep = 0.5, sharp = true)
        val finalScore = score(full, finalModel)
        if (!finalModel.isPlausible(frame.width, frame.height)) return null
        val ring = ringScore(full, finalModel)
        val wire = wireScore(full, finalModel)
        val confidence = finalScore / median
        if (ring < MIN_RING_CONTRAST || wire < MIN_WIRE_CONTRAST || confidence < MIN_CONFIDENCE) return null
        faceUpright(full, finalModel)
        return BoardFit(finalModel.toHomography(), confidence, frame.width, frame.height)
    }

    /**
     * The wires repeat every 18 degrees, so the fit may be a whole number of segments off. The 20 is always a
     * dark segment next to a light 1, which fixes the parity; among the rotations that remain (36 degrees apart)
     * the one closest to hanging upright is taken, so the board may be tilted up to about 18 degrees.
     */
    private fun faceUpright(img: Image, m: Model) {
        if (!twentyIsDark(img, m)) m.rotate(18.0)
        var guard = 0
        while (guard++ < 12) {
            val here = abs(m.upAngle())
            m.rotate(36.0)
            val plus = abs(m.upAngle())
            m.rotate(-72.0)
            val minus = abs(m.upAngle())
            m.rotate(36.0)
            when {
                plus < here && plus <= minus -> m.rotate(36.0)
                minus < here -> m.rotate(-36.0)
                else -> return
            }
        }
    }
    private fun twentyIsDark(img: Image, m: Model): Boolean = segmentBrightness(img, m, 0) < segmentBrightness(img, m, 1)

    private fun segmentBrightness(img: Image, m: Model, index: Int): Double {
        val o = DoubleArray(2)
        var sum = 0.0
        var n = 0
        for (radius in doubleArrayOf(30.0, 50.0, 70.0, 85.0, 120.0, 135.0, 150.0)) {
            for (offset in doubleArrayOf(-3.5, 0.0, 3.5)) {
                val a = Math.toRadians(index * 18.0 + offset)
                m.map(radius * sin(a), radius * cos(a), o)
                if (img.inside(o[0], o[1])) { sum += img.at(o[0], o[1]); n++ }
            }
        }
        return if (n == 0) 0.0 else sum / n
    }

    // ---- model -----------------------------------------------------------------------------------------------

    /** pixel = (A*u + B*v)/w + cx, (C*u + D*v)/w + cy with (u, v) = board mm / 170 and w = 1 + g*u + h*v. */
    private class Model(val p: DoubleArray) {
        fun copy() = Model(p.copyOf())

        /** Clockwise angle on screen, in degrees, of the line from the bull towards the 20. */
        fun upAngle(): Double {
            val a = DoubleArray(2); val b = DoubleArray(2)
            map(0.0, 0.0, a); map(0.0, 100.0, b)
            return Math.toDegrees(kotlin.math.atan2(b[0] - a[0], -(b[1] - a[1])))
        }

        fun map(xMm: Double, yMm: Double, out: DoubleArray) {
            val u = xMm / BoardGeometry.DOUBLE_OUTER_RADIUS
            val v = yMm / BoardGeometry.DOUBLE_OUTER_RADIUS
            val w = 1.0 + p[6] * u + p[7] * v
            out[0] = (p[2] * u + p[3] * v) / w + p[0]
            out[1] = (p[4] * u + p[5] * v) / w + p[1]
        }

        fun rotate(degrees: Double) {
            val a = Math.toRadians(degrees)
            val c = cos(a); val s = sin(a)
            val a0 = p[2]; val b0 = p[3]; val c0 = p[4]; val d0 = p[5]
            // The board turns clockwise on screen: rotate the board plane before the linear part.
            p[2] = a0 * c + b0 * s; p[3] = -a0 * s + b0 * c
            p[4] = c0 * c + d0 * s; p[5] = -c0 * s + d0 * c
            val g0 = p[6]; val h0 = p[7]
            p[6] = g0 * c + h0 * s; p[7] = -g0 * s + h0 * c
        }

        fun scaled(f: Double) = Model(doubleArrayOf(p[0] * f, p[1] * f, p[2] * f, p[3] * f, p[4] * f, p[5] * f, p[6], p[7]))

        fun toHomography(): Homography {
            val src = BoardGeometry.CALIBRATION_POINTS
            val dst = src.map { val o = DoubleArray(2); map(it.x, it.y, o); Point(o[0], o[1]) }
            return Homography.fromFourPoints(src, dst)
        }

        /** Rejects folded-over or absurdly squashed fits. */
        fun isPlausible(width: Int, height: Int): Boolean {
            val det = p[2] * p[5] - p[3] * p[4]
            val size = kotlin.math.sqrt(abs(det))
            if (size < min(width, height) * 0.08 || size > max(width, height) * 0.9) return false
            val o = DoubleArray(2)
            var inside = 0
            for (i in 0 until 8) {
                val a = i * PI / 4
                map(BoardGeometry.DOUBLE_OUTER_RADIUS * sin(a), BoardGeometry.DOUBLE_OUTER_RADIUS * cos(a), o)
                if (o[0] >= -width * 0.05 && o[0] <= width * 1.05 && o[1] >= -height * 0.05 && o[1] <= height * 1.05) inside++
            }
            return inside >= 6 && abs(p[6]) < 0.45 && abs(p[7]) < 0.45
        }
    }

    // ---- scoring ---------------------------------------------------------------------------------------------

    private val ringDirs = Array(RING_ANGLES) { i -> val a = 2 * PI * i / RING_ANGLES; doubleArrayOf(sin(a), cos(a)) }
    private val wireDirs = Array(20) { i -> Math.toRadians(i * 18.0 + 9.0) }

    /** Edge strength of the four rings only; used for the position/size search where rotation does not matter. */
    private fun ringScore(img: Image, m: Model, edgeMm: Double = EDGE_MM): Double {
        val a = DoubleArray(2); val b = DoubleArray(2)
        var sum = 0.0
        var outside = 0
        for (radius in RING_RADII) {
            for (d in ringDirs) {
                m.map(d[0] * (radius + edgeMm), d[1] * (radius + edgeMm), a)
                m.map(d[0] * (radius - edgeMm), d[1] * (radius - edgeMm), b)
                if (!img.inside(a[0], a[1]) || !img.inside(b[0], b[1])) { outside++; continue }
                sum += abs(img.at(a[0], a[1]) - img.at(b[0], b[1]))
            }
        }
        return (sum - outside * 6.0) / (RING_RADII.size * RING_ANGLES)
    }

    /** Edge strength across the 20 segment wires; peaks only when the board is rotated correctly. */
    private fun wireScore(img: Image, m: Model, epsDegrees: Double = WIRE_EPS_DEGREES): Double {
        val a = DoubleArray(2); val b = DoubleArray(2)
        var sum = 0.0
        var count = 0
        val eps = Math.toRadians(epsDegrees)
        for (angle in wireDirs) {
            for (radius in WIRE_RADII) {
                m.map(radius * sin(angle - eps), radius * cos(angle - eps), a)
                m.map(radius * sin(angle + eps), radius * cos(angle + eps), b)
                count++
                if (!img.inside(a[0], a[1]) || !img.inside(b[0], b[1])) continue
                sum += abs(img.at(a[0], a[1]) - img.at(b[0], b[1]))
            }
        }
        return sum / count
    }

    private val WIRE_RADII = doubleArrayOf(35.0, 55.0, 75.0, 90.0, 118.0, 132.0, 146.0)

    private fun score(img: Image, m: Model, sharp: Boolean = false) =
        if (sharp) ringScore(img, m, SHARP_EDGE_MM) + 0.8 * wireScore(img, m, SHARP_WIRE_EPS_DEGREES) else ringScore(img, m) + 0.8 * wireScore(img, m)

    // ---- search ----------------------------------------------------------------------------------------------

    private class Coarse(val top: List<Model>, val scores: List<Double>) { fun isEmpty() = top.isEmpty() }

    private fun coarseCandidates(img: Image): Coarse {
        val w = img.width.toDouble(); val h = img.height.toDouble()
        val shorter = min(w, h)
        val results = ArrayList<Pair<Model, Double>>()
        val sizes = generateSequence(0.16 * shorter) { it * 1.12 }.takeWhile { it <= 0.62 * shorter }.toList()
        val shapes = listOf(1.0 to 1.0, 1.0 to 0.85, 1.0 to 0.7, 0.85 to 1.0, 0.7 to 1.0)
        val stepX = w / 24; val stepY = h / 18
        var y = h * 0.1
        while (y <= h * 0.9) {
            var x = w * 0.1
            while (x <= w * 0.9) {
                for (s in sizes) for ((fx, fy) in shapes) {
                    val m = Model(doubleArrayOf(x, y, s * fx, 0.0, 0.0, -s * fy, 0.0, 0.0))
                    results += m to ringScore(img, m)
                }
                x += stepX
            }
            y += stepY
        }
        val sorted = results.sortedByDescending { it.second }
        val picked = ArrayList<Pair<Model, Double>>()
        for (r in sorted) {
            if (picked.size >= 6) break
            val far = picked.none { kotlin.math.hypot(it.first.p[0] - r.first.p[0], it.first.p[1] - r.first.p[1]) < shorter * 0.08 && abs(it.first.p[2] / r.first.p[2] - 1) < 0.2 }
            if (far) picked += r
        }
        return Coarse(picked.map { it.first }, results.map { it.second })
    }

    /** Coordinate descent on the combined score. Steps are in units of the model's own scale. */
    private fun descend(img: Image, start: Model, fullScore: Boolean, startStep: Double = 1.0, sharp: Boolean = false): Pair<Model, Double> {
        val m = start.copy()
        var best = score(img, m, sharp)
        val size = kotlin.math.sqrt(abs(m.p[2] * m.p[5] - m.p[3] * m.p[4])).coerceAtLeast(1.0)
        var step = startStep * (if (fullScore) 0.04 else 0.08)
        val minStep = if (fullScore) 0.0006 else 0.004
        // Position moves in pixels, linear terms in fractions of size, perspective in absolute units.
        val unit = doubleArrayOf(size, size, size, size, size, size, 1.0, 1.0)
        var rounds = 0
        val rng = java.util.Random(11)
        while (step >= minStep && rounds++ < 400) {
            var improved = false
            for (i in 0 until 8) {
                val delta = step * unit[i] * (if (i >= 6) 0.5 else 1.0)
                for (sign in doubleArrayOf(1.0, -1.0)) {
                    val old = m.p[i]
                    m.p[i] = old + sign * delta
                    val s = score(img, m, sharp)
                    if (s > best + 1e-9) { best = s; improved = true } else m.p[i] = old
                }
            }
            if (fullScore) repeat(24) {
                val old = m.p.copyOf()
                for (i in 0 until 8) m.p[i] += (rng.nextDouble() * 2 - 1) * step * unit[i] * (if (i >= 6) 0.5 else 1.0)
                val s = score(img, m, sharp)
                if (s > best + 1e-9) { best = s; improved = true } else old.copyInto(m.p)
            }
            if (!improved) step *= 0.5
        }
        return m to best
    }

    // ---- image -----------------------------------------------------------------------------------------------

    private class Image(val width: Int, val height: Int, val data: FloatArray) {
        fun inside(x: Double, y: Double) = x >= 0.5 && y >= 0.5 && x < width - 1.5 && y < height - 1.5

        fun at(px: Double, py: Double): Double {
            // Pixel centres are at +0.5, the convention used by the tracker and the calibration points.
            val x = px - 0.5; val y = py - 0.5
            val ix = floor(x).toInt(); val iy = floor(y).toInt()
            val fx = x - ix; val fy = y - iy
            val i = iy * width + ix
            val top = data[i] * (1 - fx) + data[i + 1] * fx
            val bottom = data[i + width] * (1 - fx) + data[i + width + 1] * fx
            return top * (1 - fy) + bottom * fy
        }

        fun blur(): Image {
            val out = FloatArray(data.size)
            for (y in 0 until height) for (x in 0 until width) {
                var sum = 0f; var n = 0
                for (dy in -1..1) for (dx in -1..1) {
                    val xx = x + dx; val yy = y + dy
                    if (xx in 0 until width && yy in 0 until height) { sum += data[yy * width + xx]; n++ }
                }
                out[y * width + x] = sum / n
            }
            return Image(width, height, out)
        }

        fun shrink(f: Int): Image {
            if (f <= 1) return this
            val w = width / f; val h = height / f
            val out = FloatArray(w * h)
            val norm = 1f / (f * f)
            for (y in 0 until h) for (x in 0 until w) {
                var sum = 0f
                for (dy in 0 until f) for (dx in 0 until f) sum += data[(y * f + dy) * width + x * f + dx]
                out[y * w + x] = sum * norm
            }
            return Image(w, h, out)
        }

        companion object {
            fun of(frame: GrayFrame) = Image(frame.width, frame.height, FloatArray(frame.pixels.size) { (frame.pixels[it].toInt() and 0xFF).toFloat() })
        }
    }
}
