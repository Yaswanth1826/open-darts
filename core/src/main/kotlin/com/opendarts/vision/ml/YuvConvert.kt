package com.opendarts.vision.ml

/** One-pass YUV 4:2:0 to upright RGB conversion with integer maths; the rotation is applied while writing. */
object YuvConvert {
    /**
     * [rotation] is the clockwise rotation (0, 90, 180 or 270) needed to make the picture upright.
     * The planes are the raw camera planes with their row and pixel strides; U and V are half size.
     */
    fun toUprightRgb(
        yPlane: ByteArray, yRowStride: Int, yPixelStride: Int,
        uPlane: ByteArray, uRowStride: Int, uPixelStride: Int,
        vPlane: ByteArray, vRowStride: Int, vPixelStride: Int,
        w: Int, h: Int, rotation: Int,
    ): ColorFrame {
        val out = ByteArray(w * h * 3)
        for (y in 0 until h) {
            val yRow = y * yRowStride
            val uRow = (y shr 1) * uRowStride
            val vRow = (y shr 1) * vRowStride
            var dst: Int
            val step: Int
            when (rotation) {
                90 -> { dst = h - 1 - y; step = h }
                180 -> { dst = (h - 1 - y) * w + (w - 1); step = -1 }
                270 -> { dst = (w - 1) * h + y; step = -h }
                else -> { dst = y * w; step = 1 }
            }
            for (x in 0 until w) {
                val luma = yPlane[yRow + x * yPixelStride].toInt() and 0xFF
                val u = (uPlane[uRow + (x shr 1) * uPixelStride].toInt() and 0xFF) - 128
                val v = (vPlane[vRow + (x shr 1) * vPixelStride].toInt() and 0xFF) - 128
                val r = luma + ((359 * v) shr 8)
                val g = luma - ((88 * u + 183 * v) shr 8)
                val b = luma + ((454 * u) shr 8)
                val o = dst * 3
                out[o] = (if (r < 0) 0 else if (r > 255) 255 else r).toByte()
                out[o + 1] = (if (g < 0) 0 else if (g > 255) 255 else g).toByte()
                out[o + 2] = (if (b < 0) 0 else if (b > 255) 255 else b).toByte()
                dst += step
            }
        }
        return if (rotation == 90 || rotation == 270) ColorFrame(h, w, out) else ColorFrame(w, h, out)
    }

    /** Luminance of an RGB picture, for the few places that still want a grey frame. */
    fun grey(frame: ColorFrame): ByteArray {
        val n = frame.width * frame.height
        val out = ByteArray(n)
        val rgb = frame.rgb
        for (i in 0 until n) {
            val r = rgb[i * 3].toInt() and 0xFF
            val g = rgb[i * 3 + 1].toInt() and 0xFF
            val b = rgb[i * 3 + 2].toInt() and 0xFF
            out[i] = ((77 * r + 150 * g + 29 * b) shr 8).toByte()
        }
        return out
    }
}
