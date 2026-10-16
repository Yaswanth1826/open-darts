package com.opendarts.ui.camera

import androidx.camera.core.ImageProxy
import com.opendarts.vision.GrayFrame

/** Copies the luminance plane into a tightly packed, upright (rotation applied) greyscale frame. */
fun ImageProxy.toUprightGray(): GrayFrame {
    val plane = planes[0]
    val buffer = plane.buffer
    val rowStride = plane.rowStride
    val w = width
    val h = height
    val src = ByteArray(w * h)
    for (y in 0 until h) {
        buffer.position(y * rowStride)
        buffer.get(src, y * w, w)
    }
    return when (imageInfo.rotationDegrees) {
        90 -> {
            val out = ByteArray(w * h)
            for (y in 0 until h) for (x in 0 until w) out[x * h + (h - 1 - y)] = src[y * w + x]
            GrayFrame(h, w, out)
        }
        180 -> {
            val out = ByteArray(w * h)
            for (i in src.indices) out[src.size - 1 - i] = src[i]
            GrayFrame(w, h, out)
        }
        270 -> {
            val out = ByteArray(w * h)
            for (y in 0 until h) for (x in 0 until w) out[(w - 1 - x) * h + y] = src[y * w + x]
            GrayFrame(h, w, out)
        }
        else -> GrayFrame(w, h, src)
    }
}