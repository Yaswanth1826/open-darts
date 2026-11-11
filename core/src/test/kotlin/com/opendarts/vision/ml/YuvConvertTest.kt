package com.opendarts.vision.ml

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class YuvConvertTest {
    private fun rgbAt(f: ColorFrame, x: Int, y: Int) = IntArray(3) { f.rgb[(y * f.width + x) * 3 + it].toInt() and 0xFF }

    /** The reference conversion the app used before (floating point, per pixel). */
    private fun reference(y: Int, u: Int, v: Int): IntArray {
        val uu = u - 128; val vv = v - 128
        return intArrayOf(
            (y + 1.402f * vv).toInt().coerceIn(0, 255),
            (y - 0.344136f * uu - 0.714136f * vv).toInt().coerceIn(0, 255),
            (y + 1.772f * uu).toInt().coerceIn(0, 255),
        )
    }

    private fun convert(w: Int, h: Int, rotation: Int, yPixelStride: Int = 1, uvPixelStride: Int = 1, rowPad: Int = 0): Pair<ColorFrame, Array<IntArray>> {
        val rnd = java.util.Random(7)
        val yRow = w * yPixelStride + rowPad
        val uvRow = (w / 2) * uvPixelStride + rowPad
        val yPlane = ByteArray(yRow * h)
        val uPlane = ByteArray(uvRow * (h / 2))
        val vPlane = ByteArray(uvRow * (h / 2))
        rnd.nextBytes(yPlane); rnd.nextBytes(uPlane); rnd.nextBytes(vPlane)
        val frame = YuvConvert.toUprightRgb(yPlane, yRow, yPixelStride, uPlane, uvRow, uvPixelStride, vPlane, uvRow, uvPixelStride, w, h, rotation)
        val expected = Array(w * h) { i ->
            val x = i % w; val y = i / w
            reference(yPlane[y * yRow + x * yPixelStride].toInt() and 0xFF, uPlane[(y / 2) * uvRow + (x / 2) * uvPixelStride].toInt() and 0xFF, vPlane[(y / 2) * uvRow + (x / 2) * uvPixelStride].toInt() and 0xFF)
        }
        return frame to expected
    }

    private fun upright(x: Int, y: Int, w: Int, h: Int, rotation: Int): Pair<Int, Int> = when (rotation) {
        90 -> (h - 1 - y) to x
        180 -> (w - 1 - x) to (h - 1 - y)
        270 -> y to (w - 1 - x)
        else -> x to y
    }

    @Test
    fun everyRotationPlacesEachPixelWhereTheClockwiseTurnPutsItAndKeepsTheColour() {
        val w = 12; val h = 8
        for (rotation in listOf(0, 90, 180, 270)) {
            val (frame, expected) = convert(w, h, rotation)
            assertEquals(if (rotation == 90 || rotation == 270) h else w, frame.width)
            assertEquals(if (rotation == 90 || rotation == 270) w else h, frame.height)
            for (y in 0 until h) for (x in 0 until w) {
                val (ux, uy) = upright(x, y, w, h, rotation)
                val got = rgbAt(frame, ux, uy)
                for (c in 0..2) assertTrue("rotation $rotation at $x,$y channel $c: ${got[c]} vs ${expected[y * w + x][c]}", abs(got[c] - expected[y * w + x][c]) <= 2)
            }
        }
    }

    @Test
    fun paddedRowsAndInterleavedChromaAreRead() {
        val (frame, expected) = convert(16, 8, 0, yPixelStride = 1, uvPixelStride = 2, rowPad = 8)
        for (y in 0 until 8) for (x in 0 until 16) {
            val got = rgbAt(frame, x, y)
            for (c in 0..2) assertTrue(abs(got[c] - expected[y * 16 + x][c]) <= 2)
        }
    }
}
