package com.opendarts.vision.ml

import com.opendarts.geometry.Homography

/** 8-bit RGB image, row-major, three bytes per pixel. */
class ColorFrame(val width: Int, val height: Int, val rgb: ByteArray) {
    init { require(rgb.size == width * height * 3) { "pixel buffer does not match ${width}x$height" } }

    /**
     * Resamples a square window of this frame, centred on ([centreX], [centreY]) with the given [side] in pixels,
     * to an [outSize] x [outSize] image. Parts of the window outside the frame are filled with grey.
     * Pixel centres are at +0.5, so a window covering the whole frame keeps its geometry exactly.
     */
    fun window(centreX: Double, centreY: Double, side: Double, outSize: Int): ColorFrame {
        val out = ByteArray(outSize * outSize * 3)
        val scale = side / outSize
        val x0 = centreX - side / 2
        val y0 = centreY - side / 2
        for (oy in 0 until outSize) {
            val sy = y0 + (oy + 0.5) * scale - 0.5
            for (ox in 0 until outSize) {
                val sx = x0 + (ox + 0.5) * scale - 0.5
                sample(sx, sy, out, (oy * outSize + ox) * 3)
            }
        }
        return ColorFrame(outSize, outSize, out)
    }

    /**
     * Resamples this frame through [outToFrame], which maps pixel coordinates of the [outSize] x [outSize] output
     * to pixel coordinates of this frame (both with the usual top-left origin), so a skewed view of a flat object
     * can be flattened. Samples outside the frame are filled with grey.
     */
    fun warp(outToFrame: Homography, outSize: Int): ColorFrame {
        val out = ByteArray(outSize * outSize * 3)
        for (oy in 0 until outSize) {
            for (ox in 0 until outSize) {
                val src = outToFrame.apply(ox + 0.5, oy + 0.5)
                sample(src.x - 0.5, src.y - 0.5, out, (oy * outSize + ox) * 3)
            }
        }
        return ColorFrame(outSize, outSize, out)
    }

    private fun sample(sx: Double, sy: Double, out: ByteArray, o: Int) {
        if (!(sx >= -1 && sy >= -1 && sx <= width && sy <= height)) {
            out[o] = PAD; out[o + 1] = PAD; out[o + 2] = PAD
            return
        }
        val ix = Math.floor(sx).toInt()
        val iy = Math.floor(sy).toInt()
        val fx = sx - ix
        val fy = sy - iy
        for (c in 0 until 3) {
            val v00 = at(ix, iy, c); val v10 = at(ix + 1, iy, c)
            val v01 = at(ix, iy + 1, c); val v11 = at(ix + 1, iy + 1, c)
            val v = (v00 * (1 - fx) + v10 * fx) * (1 - fy) + (v01 * (1 - fx) + v11 * fx) * fy
            out[o + c] = (v + 0.5).toInt().coerceIn(0, 255).toByte()
        }
    }
    private fun at(x: Int, y: Int, c: Int): Int {
        val cx = x.coerceIn(0, width - 1)
        val cy = y.coerceIn(0, height - 1)
        return rgb[(cy * width + cx) * 3 + c].toInt() and 0xFF
    }

    private companion object {
        const val PAD: Byte = 114
    }
}
