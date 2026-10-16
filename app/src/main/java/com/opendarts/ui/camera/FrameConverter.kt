package com.opendarts.ui.camera

import androidx.camera.core.ImageProxy
import com.opendarts.vision.GrayFrame
import com.opendarts.vision.ml.ColorFrame

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
/** One camera frame. The colour image is only built when asked for, and only while the camera still holds the frame. */
class CameraFrame(val gray: GrayFrame, private val colour: () -> ColorFrame) {
    fun color(): ColorFrame = colour()
}

/** Converts the YUV frame to an upright RGB image (rotation applied). */
fun ImageProxy.toUprightColor(): ColorFrame {
    val w = width
    val h = height
    val yPlane = planes[0]; val uPlane = planes[1]; val vPlane = planes[2]
    val yBuf = yPlane.buffer; val uBuf = uPlane.buffer; val vBuf = vPlane.buffer
    val rgb = ByteArray(w * h * 3)
    for (y in 0 until h) {
        val uvRow = (y shr 1) * uPlane.rowStride
        for (x in 0 until w) {
            val yy = (yBuf.get(y * yPlane.rowStride + x * yPlane.pixelStride).toInt() and 0xFF)
            val uvIndex = uvRow + (x shr 1) * uPlane.pixelStride
            val u = (uBuf.get(uvIndex).toInt() and 0xFF) - 128
            val v = (vBuf.get((y shr 1) * vPlane.rowStride + (x shr 1) * vPlane.pixelStride).toInt() and 0xFF) - 128
            val o = (y * w + x) * 3
            rgb[o] = (yy + 1.402f * v).toInt().coerceIn(0, 255).toByte()
            rgb[o + 1] = (yy - 0.344136f * u - 0.714136f * v).toInt().coerceIn(0, 255).toByte()
            rgb[o + 2] = (yy + 1.772f * u).toInt().coerceIn(0, 255).toByte()
        }
    }
    return when (imageInfo.rotationDegrees) {
        90 -> {
            val out = ByteArray(rgb.size)
            for (y in 0 until h) for (x in 0 until w) System.arraycopy(rgb, (y * w + x) * 3, out, (x * h + (h - 1 - y)) * 3, 3)
            ColorFrame(h, w, out)
        }
        180 -> {
            val out = ByteArray(rgb.size)
            for (i in 0 until w * h) System.arraycopy(rgb, i * 3, out, (w * h - 1 - i) * 3, 3)
            ColorFrame(w, h, out)
        }
        270 -> {
            val out = ByteArray(rgb.size)
            for (y in 0 until h) for (x in 0 until w) System.arraycopy(rgb, (y * w + x) * 3, out, ((w - 1 - x) * h + y) * 3, 3)
            ColorFrame(h, w, out)
        }
        else -> ColorFrame(w, h, rgb)
    }
}